/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.provision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gluonhq.equation.WaveManager;
import com.gluonhq.equation.WaveStore;
import com.gluonhq.equation.internal.DeviceMessages;
import com.gluonhq.equation.internal.DeviceMessages.ProvisionMessage;
import com.gluonhq.equation.internal.KeyUtil;
import com.gluonhq.equation.internal.TrustStoreImpl;
import com.gluonhq.equation.log.WaveLogger;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import okhttp3.Response;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;

/**
 * This class deals with the provisioning flow, required to pair a device
 * to an existing Signal account.
 */
public class ProvisioningManager {

    private final ProvisioningClient provisioningClient;
    private final WaveManager waveManager;
    private final TrustStoreImpl trustStore;
    private final ProvisioningCipher provisioningCipher;
    private final WaveStore store;
    private static final String USER_AGENT = "OWA";
    private static final String SIGNAL_USER_AGENT = "Signal-Desktop/5.14.0 Linux";
    private static final String DESTINATION = "wss://textsecure-service.whispersystems.org";
    private WebSocketConnection provisioningWebSocket;
    private boolean listen;
    private ProvisionMessage pm;
    private String number;
    private int deviceId;
    private PushServiceSocket accountSocket;
    private StaticCredentialsProvider credentialsProvider;
    private WaveLogger WAVELOG;

    /**
     * Flow: when the start method is invoked, this class will generate a URL
     * A callback is invoked (setProvivisioningUrl), allowing the caller to display the URL as a QR code.
     * When the QR code is scanned by a Signal-registered device, we will get called.
     * We then invoke the gotProvisioningNumber containing the number that scanned 
     * the URL. The caller can then invoke the createAccount(number) method to finalize the
     * registration.
     * @param client 
     */
    public ProvisioningManager(WaveManager wave, ProvisioningClient client) {
        this.provisioningClient = client;
        this.waveManager = wave;
        this.trustStore = new TrustStoreImpl();
        this.provisioningCipher = new ProvisioningCipher(waveManager);
        this.store = wave.getWaveStore();
        this.WAVELOG = wave.getWaveLogger();
    }

    public void start() {
        ConnectivityListener connectivityListener = new ProvisioningConnectivityListener("prov");
        SleepTimer sleepTimer = m -> Thread.sleep(m);
        provisioningWebSocket = new WebSocketConnection(DESTINATION, "provisioning/", trustStore,
                Optional.empty(), USER_AGENT, connectivityListener, sleepTimer,
                new LinkedList(), Optional.empty(), Optional.empty(), null);
        provisioningWebSocket.connect();
        this.listen = true;
        try {
            while (listen) {
                WAVELOG.log(Level.INFO,"[PM] waiting for reqest... ");
                WebSocketRequestMessage request = provisioningWebSocket.readRequest(600000);
                WAVELOG.log(Level.INFO,"[PM] got readrequest that I will handle now: " + request);
                handleRequest(request);
                WAVELOG.log(Level.INFO,"[PM] handled readrequest "+request);
            }
        } catch (Exception ex) {
            System.err.println("[PM] Exception while waiting for incoming request");
            this.listen = false;
            ex.printStackTrace();
        }
    }

    /**
     * Hard stop the provisioning websocket. 
     */
    public void stop() {
        this.listen = false;
        WAVELOG.log(Level.INFO,"[PM] we're asked to disconnect the websocket");
        provisioningWebSocket.disconnect();
        WAVELOG.log(Level.INFO,"[PM] stopped");
    }
    
    /**
     * Link the current device with the provided number. 
     * This will also update the waveStore with information about the new
     * credentials.
     * This method also registers an initial batch of (100) PreKeys.
     * @param nr the unique number of the registered Signal account
     * @param deviceName the devicename that will be used for this device.
     * @throws IOException 
     */
    public void createAccount(String nr, String deviceName) throws IOException {
        WAVELOG.log(Level.INFO,"Creating device " + deviceName+" for number "+this.number);
        if (!nr.equals(this.number)) {
            throw new IllegalArgumentException("Can't create account for " + nr);
        }
        startPreAccountWebSocket();
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String password = new String(b, StandardCharsets.UTF_8);
        password = Base64.getEncoder().encodeToString(password.getBytes());
        password = password.substring(0, password.length() - 2);
        int regid = new SecureRandom().nextInt(16384) & 0x3fff;
        confirmCode(pm.getNumber(), pm.getProvisioningCode(), password,
                regid, deviceName, pm.getUuid());
        UUID uuid = UUID.fromString(pm.getUuid());
        this.credentialsProvider = new StaticCredentialsProvider(uuid,
                pm.getNumber(), password, "signalingkey", deviceId);
        startAccountWebSocket();
        waveManager.getWaveStore().setCredentialsProvider(this.credentialsProvider);
        generateAndRegisterKeys();
    }

    // PRIVATE
    
    private void handleRequest(WebSocketProtos.WebSocketRequestMessage request) {
        String path = request.getPath();
        WAVELOG.log(Level.INFO,"[PM] we need to handle a request for path " + path);
        ByteString data = request.getBody();
        if ("/v1/address".equals(path)) {
            String uuid = "";
            try {
                DeviceMessages.ProvisioningUuid puuid = DeviceMessages.ProvisioningUuid.parseFrom(data);
                uuid = puuid.getUuid();
            } catch (InvalidProtocolBufferException ex) {
                ex.printStackTrace();
            }
            WAVELOG.log(Level.INFO,"[PM] we got a uuid: " + uuid);
            String ourPubKey = Base64.getEncoder().encodeToString(this.provisioningCipher.getOurKeyPair().getPublicKey().serialize());
            ourPubKey = URLEncoder.encode(ourPubKey, StandardCharsets.UTF_8);
            String url = "tsdevice:/?uuid=" + uuid + "&pub_key=" + ourPubKey;
            WAVELOG.log(Level.INFO,"URL = " + url);
            provisioningClient.gotProvisioningUrl(url);
        } else if ("/v1/message".equals(path)) {
            try {
                DeviceMessages.ProvisionEnvelope envelope = DeviceMessages.ProvisionEnvelope.parseFrom(data);
                this.pm = provisioningCipher.decrypt(envelope);
                this.number = pm.getNumber();
                provisioningClient.gotProvisionMessage(pm.getNumber());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            System.err.println("[PM] UNKNOWNPROVISIONINGMESSAGE");
            throw new IllegalArgumentException("UnknownProvisioningMessage");
        }
    }

    // we need to open a websocket without credentials first
    private void startPreAccountWebSocket() {
        SignalServiceConfiguration config = waveManager.getSignalServiceConfiguration();
        CredentialsProvider emptyCredentials = new StaticCredentialsProvider(null, "", "");
        this.accountSocket = new PushServiceSocket(config, emptyCredentials, 
                SIGNAL_USER_AGENT, null, true);
    }

    private void startAccountWebSocket() {
        this.accountSocket.cancelInFlightRequests();
        SignalServiceConfiguration config = waveManager.getSignalServiceConfiguration();
        this.accountSocket = new PushServiceSocket(config, credentialsProvider, 
                SIGNAL_USER_AGENT, null, true);
    }

    private void confirmCode(String number, String code, String pwd,
            int registrationId, String deviceName, String uuid) throws JsonProcessingException {
        WAVELOG.log(Level.INFO,"Confirming code");
        String body = getDeviceMapData(deviceName, registrationId);
        String username = number;
        String authbase = username + ":" + pwd;
        String basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
        try {
            Map<String, String> myHeaders = new HashMap<>(4);
            myHeaders.put("Authorization", "Basic " + basicAuth);
            myHeaders.put("content-type", "application/json;charset=utf-8");
            myHeaders.put("User-Agent", "Signal-Desktop/5.14.0 Linux");
            myHeaders.put("x-signal-agent", "OWD");
            String response = accountSocket.makeServiceRequest("/v1/devices/" + code, "PUT", body, myHeaders);
            int c = response.indexOf(":");
            String did = response.substring(c + 1, response.length() - 1);
            this.deviceId = Integer.parseInt(did);
            waveManager.getWaveStore().setRegistrationId(deviceId);
        } catch (Exception e) {
            System.err.println("confirmcode Got error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ObjectNode createDefaultCapabilities() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode capabilities = mapper.createObjectNode();
        capabilities.put("announcementGroup", true);
        capabilities.put("gv2-3", true);
        capabilities.put("gv1-migration", true);
        capabilities.put("senderKey", true);
        return capabilities;
    }

    private String getDeviceMapData(String name, int registrationId) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode capabilities = createDefaultCapabilities();
        ObjectNode jsonData = mapper.createObjectNode();
        jsonData.set("capabilities", capabilities);
        jsonData.put("fetchesMessages", true);
        jsonData.put("name", name);
        jsonData.put("registrationId", registrationId);
        jsonData.put("supportsSms", false);
        jsonData.put("unrestrictedUnidentifiedAccess", false);
        String answer = mapper.writeValueAsString(jsonData);
        return answer;
    }

    private void generateAndRegisterKeys() throws IOException {
        IdentityKeyPair identityKeypair = waveManager.getWaveStore().getIdentityKeyPair();
        SignedPreKeyRecord signedPreKey = KeyUtil.generateSignedPreKey(identityKeypair, true);
        store.storeSignedPreKey(2, signedPreKey);
  
        List<PreKeyRecord> records = KeyUtil.generatePreKeys(100);
        WAVELOG.log(Level.DEBUG," PM will register keys, ik = "+ identityKeypair+" with pubkey = "+identityKeypair.getPublicKey()+" and spk = "+signedPreKey+" and records = "+records);
        String response = accountSocket.registerPreKeys(identityKeypair.getPublicKey(), signedPreKey, records);
        WAVELOG.log(Level.DEBUG,"Response for generateAndRegisterKeys = "+response);
    }

    class ProvisioningConnectivityListener implements ConnectivityListener {

        private final String name;

        ProvisioningConnectivityListener(String name) {
            this.name = name;
        }

        @Override
        public void onConnected() {
            WAVELOG.log(Level.INFO,"[PM] " + name + " connected");
        }

        @Override
        public void onConnecting() {
            WAVELOG.log(Level.INFO,"[PM] " + name + " connecting");
        }

        @Override
        public void onDisconnected() {
            WAVELOG.log(Level.INFO,"[PM] " + name + " disconnected");
        }

        @Override
        public void onAuthenticationFailure() {
            throw new UnsupportedOperationException("[PM] " + name + " Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean onGenericFailure(Response response, Throwable throwable) {
            System.err.println("onGenericFailure, response = " + response+", throwable = " + throwable);
            throwable.printStackTrace();
            return false;
        }

    }
}
