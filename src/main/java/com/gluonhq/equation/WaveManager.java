/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation;

import com.gluonhq.equation.internal.LockImpl;
import com.gluonhq.equation.internal.TrustStoreImpl;
import com.gluonhq.equation.log.WaveLogger;
import com.gluonhq.equation.message.MessagingClient;
import com.gluonhq.equation.model.Contact;
import com.gluonhq.equation.provision.ProvisioningClient;
import com.gluonhq.equation.provision.ProvisioningManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.Security;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import okhttp3.Response;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage;
import org.whispersystems.util.Base64;

/**
 * The entry point to Gluon Wave. There is only a single WaveManager in any
 * running VM.
 */
public class WaveManager {

    private static final WaveManager instance = new WaveManager();
    private final WaveStore waveStore;
    private CredentialsProvider credentialsProvider;
    private ClientConnectivityListener cl;

    static final String SIGNAL_SERVICE_URL = "https://textsecure-service.whispersystems.org";
    static final String SIGNAL_USER_AGENT = "Signal-Desktop/5.14.0 Linux";
    static final String SIGNAL_KEY_BACKUP_URL = "https://api.backup.signal.org";
    static final String SIGNAL_STORAGE_URL = "https://storage.signal.org";
    static final String UNIDENTIFIED_SENDER_TRUST_ROOT = "BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF";
    long MAX_FILE_STORAGE = 1024 * 1024 * 4;
    final TrustStore trustStore = new TrustStoreImpl();
    private final LockImpl lock;
    private final ObservableList<Contact> contacts = FXCollections.observableArrayList();

    final SignalServiceConfiguration signalServiceConfiguration;

    public final static File SIGNAL_FX_CONTACTS_DIR;
    
    private CountDownLatch syncContactsLatch;
    
    private LongProperty lastSyncContactRequest = new SimpleLongProperty();
    private LongProperty lastSyncContactResponse = new SimpleLongProperty();
    
    private final String CONTACT_SYNC_ERROR = "CONTACT_SYNC_ERROR";

    static {
        Security.addProvider(new BouncyCastleProvider());
        Security.setProperty("crypto.policy", "unlimited");
        Path contacts = WaveStore.SIGNAL_FX_PATH.resolve("contacts/");
        SIGNAL_FX_CONTACTS_DIR = contacts.toFile();
        try {
            Files.createDirectories(contacts);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public static WaveManager getInstance() {
        return instance;
    }
    private MessagingClient messageListener;
    
    private boolean connected;
    private SignalServiceMessageReceiver receiver;
    private SignalServiceMessageSender sender;
    private SignalServiceMessagePipe messagePipe;
    private SignalServiceMessagePipe unidentifiedMessagePipe;
    
    private SignalServiceAddress signalServiceAddress;
    private boolean contactStorageDirty = true;
    private ProvisioningManager provisioningManager;
    public static WaveLogger WAVELOG;

    private WaveManager() {
        WAVELOG = new WaveLogger();
        WAVELOG.log(Level.INFO, "Starting WaveManager");
        this.lock = new LockImpl();
        this.waveStore = WaveStore.getInstance();
        this.signalServiceConfiguration = createConfiguration();
        this.credentialsProvider = waveStore.getCredentialsProvider();
        if (isProvisioned()) {
            this.signalServiceAddress = new SignalServiceAddress(credentialsProvider.getUuid(), credentialsProvider.getE164());
        }
    }

    /**
     * Retrieve the logger used by this class. This allows callers to use the
     * same loglevels and approach as is used internally
     * @return the (static) WaveLogger
     */
    public WaveLogger getWaveLogger() {
        return WAVELOG;
    }
    
    /**
     * Changes the level for the underlying loggers
     * @param level the minimum level a log message should have in order to be logged.
     */
    public void setLogLevel(Level level) {
        WAVELOG.setLevel(level);
    }
    
    /**
     * Checks if this device has already been provisioned with account
     * information. Provisioning should be done only once for a device.
     *
     * @return
     */
    public boolean isProvisioned() {
        return waveStore.isInitialized();
    }

    /**
     * Returns the UUID (as a String) of the current user. The current user is
     * the one associated with the current credentialsProvider, as obtained in
     * the store.
     *
     * @return
     */
    public String getMyUuid() {
        return this.credentialsProvider.getUuid().toString();
    }

    /**
     * Set the MessagingClient implementation that will deal with incoming
     * messages. When Gluon Wave is notified about an incoming message, it will
     * invoke the gotMessage call on the MessagingClient (after initial
     * processing and decryption)
     *
     * @param mc the client that will handle incoming messages
     */
    public void setMessageListener(MessagingClient mc) {
        this.messageListener = mc;
    }

    /**
     * Make sure we are connected. That is, after this method returns, it is
     * guaranteed that we have a sender and a receiver that can be used to send
     * and receive messages. This methods can be called multiple times, but the
     * connection is only created once.
     */
    public void ensureConnected() throws IOException {
        WAVELOG.log(Level.INFO, "ensure connected? "+connected);
        if (connected) {
            return;
        }
        connect();
    }

    /**
     * Make sure this manager is connected to a valid Signal configuration.
     * After this method is called, a receiver, a sender (and a pipe) are created
     * and ready to use.
     * @throws IOException 
     */
    public void connect() throws IOException {
        WAVELOG.log(Level.INFO, "[WM] connecting");
        this.receiver = createMessageReceiver();
        this.sender = createMessageSender(receiver);
        this.connected = true;
        WAVELOG.log(Level.INFO, "[WM] connected");
    }

    public void initialize() {
            try {
                getWaveLogger().log(Level.DEBUG, "ensure we are connected");
                this.ensureConnected();
                getWaveLogger().log(Level.DEBUG, "we are connected, let's sync");
                syncEverything();
                getWaveLogger().log(Level.DEBUG, "sync requests are sent");
                startListening();
            } catch (IOException ex) {
                ex.printStackTrace();
                System.err.println("We're offline. Not much we can do now!");
            }
    }
    /**
     * Starts listening for incoming messages. At this point, we need to be connected.
     */
    private void startListening() {
        WAVELOG.log(Level.INFO, "[WM] startlistening");
        processMessagePipe(messagePipe);
        processMessagePipe(unidentifiedMessagePipe);
        WAVELOG.log(Level.INFO, "[WM] done startListening");
    }
    
    public void reset() {
        WAVELOG.log(Level.INFO, "RESET called!");
        if (!connected) {
            WAVELOG.log(Level.INFO, "we weren't connected");
            return;
        }
        this.sender.cancelInFlightRequests();
        this.messagePipe.shutdown();
        this.unidentifiedMessagePipe.shutdown();
        this.connected = false;
        WAVELOG.log(Level.INFO, "RESET done");
    }

    public void syncEverything() throws IOException {
        WAVELOG.log(Level.INFO, "[WM] startSyncEverything");
        syncConfiguration();
        syncContacts();
        syncGroups();
        WAVELOG.log(Level.INFO, "[WM] doneSyncEverything");
    }

    /**
     * Send a request to synchronize contacts. We expect an incoming message with 
     * a contact list, but this methods returns immediately and does not deal
     * with the processing of the incoming message.
     * 
     * @throws IOException
     */
    public void syncContacts() throws IOException{
        ensureConnected();
        syncContactsLatch = new CountDownLatch(1);
        SignalServiceProtos.SyncMessage.Request request = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        sendSyncMessage(message);
        this.lastSyncContactRequest.set(System.currentTimeMillis());
        // not used yet
        Thread t = new Thread() {
            @Override public void run() {
                try {
                    boolean res = syncContactsLatch.await(10, TimeUnit.SECONDS);
                    if (res) {
                        WAVELOG.log(Level.INFO, "Ok, we got contacts");
                    } else {
                        reset();
                        
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        };
    }

    public void syncConfiguration() throws IOException {
        WAVELOG.log(Level.INFO, "We will request to sync the configuration");
        ensureConnected();
        System.err.println("We ensured we are connected");
        SyncMessage.Request request = SyncMessage.Request.newBuilder()
                .setType(SyncMessage.Request.Type.CONFIGURATION).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        sendSyncMessage(message);
        System.err.println("We sent syncconfig request");
    }

    public void syncGroups() throws IOException {
        WAVELOG.log(Level.INFO, "We will request to sync groups");
        ensureConnected();
        SignalServiceProtos.SyncMessage.Request request = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        sendSyncMessage(message);
    }

    private void sendSyncMessage(SignalServiceSyncMessage message) throws IOException {
         try {
            WAVELOG.log(Level.DEBUG, "[WM] sendSyncMessage invoked");
            sender.sendMessage(message, Optional.empty());
            WAVELOG.log(Level.DEBUG, "[WM] sendSyncMessage done invoked");
        } catch (UntrustedIdentityException ex) {
            ex.printStackTrace();
            throw new IOException (ex);
        }
    }

    /**
     * Get an ObservableList of available contacts. In case the contacts are not
     * retrieved yet (or not read from persistent storage), this list will initially
     * be empty. When new entries are added to the list, listeners of the 
     * ObservableList will be notified.
     * @return 
     */
    public ObservableList<Contact> getContacts() {

        if (contactStorageDirty) {
            try {
                contacts.clear(); // TODO make this smarter
                contacts.addAll(readContacts());
                contactStorageDirty = false;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return contacts;
    }

    /**
     * Sends a message with the specified text to the contact with the specified uuid.This method returns immediately, and does not wait for confirmation of the
 receiver.
     * @param uuid the UUID (as a String) of the recipient
     * @param text the text of the message
     * @return the timestamp that is added to this message before it is sent. This 
     * timestamp is also an identifier used for e.g. receipt notifications.
     * @throws IOException 
     */
    public long sendMessage(String uuid, String text) throws IOException {
        ensureConnected();
        Contact target = contacts.stream().filter(c -> uuid.equals(c.getUuid())).findFirst().get();
        Optional<SignalServiceAddress> add = SignalServiceAddress.fromRaw(uuid, target.getNr());
        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                .withBody(text).build();
        try {
            SendMessageResult res = sender.sendMessage(add.get(), Optional.empty(), message);
        } catch (UntrustedIdentityException ex) {
            throw new IOException ("Could not send message! ", ex);
        }
        return message.getTimestamp();
    }

    public void sendReadReceipt(long timestamp, String uuid) {
        WAVELOG.log(Level.DEBUG, "Need to send read receipt to "+uuid+" for "+timestamp);
        Optional<Contact> target = getContactByUuid(uuid);
        if (target.isEmpty()) {
            WAVELOG.log(Level.WARNING, "Need to send a read receipt to " + uuid + " but this is not in contact list");
            Thread.dumpStack();
            return;
        }
        Optional<SignalServiceAddress> add = SignalServiceAddress.fromRaw(uuid, target.get().getNr());
        SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ,
                List.of(timestamp), System.currentTimeMillis());
        try {
           sender.sendReceipt(add.get(), Optional.empty(), message);
        } catch (IOException | UntrustedIdentityException ex) {
            WAVELOG.log(Level.WARNING, "Error sending a receipt to " + uuid);
            ex.printStackTrace();
        }
    }

    /**
     * Start the provisioning flow. 
     * This should only be invoked once per device that needs to be paired.
     * @param provisioningClient the caller needs to provide this client as it will
     * have to deal with callbacks from the provisioning system.
     */
    public void startProvisioning(ProvisioningClient provisioningClient) {
        // There might be issues with provisioning where the contact list is not synced.
        monitorSyncRequests(provisioningClient);
        provisioningManager = new ProvisioningManager(this, provisioningClient);
        provisioningManager.start();
    }
    
    /**
     * Creates a local account linked to the provided number. 
     * The work is delegated to the ProvisioningManager which will also
     * update the waveStore. 
     * After the account is created, the credentialsProvider
     * will be retrieved from the updated WaveStore and other methods in this API can be used.
     * @param nr the unique number of the user (the master account)
     * @param deviceName the id this device is given
     * @throws IOException 
     */
    public void createAccount(String nr, String deviceName) throws  IOException {
        provisioningManager.createAccount(nr, deviceName);
        this.credentialsProvider = waveStore.getCredentialsProvider();
        this.signalServiceAddress = new SignalServiceAddress(credentialsProvider.getUuid(), credentialsProvider.getE164());
        provisioningManager.stop();
    }
    
    /**
     * Retrieves the store which is the master for all our data
     * @return the WaveStore (which implements SignalProtocolStore)
     */
    public WaveStore getWaveStore () {
        return waveStore;
    }
    
    /**
     * Returns the SignalServiceConfiguration used in this manager.
     * @return the SignalServiceConfiguration containing URL's etc.
     */
    public SignalServiceConfiguration getSignalServiceConfiguration() {
        TrustStore ts = this.signalServiceConfiguration.getSignalServiceUrls()[0].getTrustStore();
        return this.signalServiceConfiguration;
    }

    public LongProperty lastSyncContactsRequest() {
        return this.lastSyncContactRequest;
    }

    public LongProperty lastSyncContactsResponse() {
        return this.lastSyncContactResponse;
    }

    // PRIVATE 
    private void monitorSyncRequests(ProvisioningClient provisioningClient) {
        System.err.println("MONITOR SYNC");
        this.lastSyncContactRequest.addListener(new InvalidationListener() {
            @Override
            public void invalidated(Observable arg0) {
                long last = lastSyncContactRequest.get();
                System.err.println("SYNCcontactRequest monitored");
                Thread t = new Thread() {
                    @Override public void run() {
                        try {
                            long timeout = Long.parseLong(System.getProperty("com.gluonhq.wave.provisioningTimeout", "30000"));
                            System.err.println("We now allow for "+timeout+"ms to get contacts");
                            Thread.sleep(timeout);
                            if (lastSyncContactResponse.get() < last ) {
                                System.err.println("PROBLEM! no Syncresponse after "+timeout+"ms");
                                provisioningClient.gotProvisioningError(CONTACT_SYNC_ERROR);
                            } else {
                                System.err.println("ALRIGHT! Syncresponse within 30s");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                t.start();
            }
        });
    }

    private  SignalServiceConfiguration createConfiguration() {
        WAVELOG.log(Level.DEBUG, "create SignalServiceConfiguration, truststore = "+trustStore);
        SignalServiceUrl[] urls = {
            new SignalServiceUrl(SIGNAL_SERVICE_URL, trustStore)};
        Map<Integer, SignalCdnUrl[]> cdnMap = new HashMap<>(2);
        cdnMap.put(0, new SignalCdnUrl[]{new SignalCdnUrl("https://cdn.signal.org", trustStore)});
        cdnMap.put(2, new SignalCdnUrl[]{new SignalCdnUrl("https://cdn2.signal.org", trustStore)});
        SignalKeyBackupServiceUrl[] backup = new SignalKeyBackupServiceUrl[]{
            new SignalKeyBackupServiceUrl(SIGNAL_KEY_BACKUP_URL, trustStore)};

        SignalStorageUrl[] storageUrl = new SignalStorageUrl[]{
            new SignalStorageUrl(SIGNAL_STORAGE_URL, trustStore)};
        SignalServiceConfiguration answer = new SignalServiceConfiguration(
                urls, cdnMap,
                new SignalContactDiscoveryUrl[]{new SignalContactDiscoveryUrl("https://api.directory.signal.org", trustStore)},
                backup, storageUrl, new LinkedList(),
                Optional.empty(), Optional.empty(), null
        );

        return answer;
    }

    /**
     * Creates a receiver and wait to return until the receiver has successfully connected.
     * @return the created SignalServiceMessageReceiver
     * @throws IOException in case we can not connect.
     */
    private SignalServiceMessageReceiver createMessageReceiver() throws IOException {
        WAVELOG.log(Level.INFO, "[WM] createMessageReceiver on "+Thread.currentThread());
        // ensure configuration and provider are ok
        if (signalServiceConfiguration == null) {
            throw new IllegalArgumentException("no signalserviceconfiguration");
        }
        if (credentialsProvider == null) {
            throw new IllegalArgumentException("no credentialsProvider");
        }
        cl = new ClientConnectivityListener();
        SleepTimer sleepTimer = m -> {
            WAVELOG.log(Level.DEBUG, "SLEEPTIMER needs to sleep "+m);
            Thread.sleep(m);
        };
        SignalServiceMessageReceiver answer = new SignalServiceMessageReceiver(
                signalServiceConfiguration,
                credentialsProvider,
                SIGNAL_USER_AGENT,
                cl,
                sleepTimer,
                null,
                true);
        return answer;
    }

    /**
     * Make this blocking until the created sender can really be used. This means both the 
     * <code>messagePipe</code> and the <code>unidentifiedMessagePipe</code> should
     * be associated with a <code>WebSocketConnection</code> that is connected.
     * Failing to do so might result in <code>IOException</code> in 
     * <code>WebSocketConnection.sendRequest</code> which will propagate to
     * <code>SignalServiceMessageSender.sendMessage</code>
     * @param receiver
     * @return 
     */
    private SignalServiceMessageSender createMessageSender(SignalServiceMessageReceiver receiver) throws IOException {
        CountDownLatch cdl = new CountDownLatch(2); // we need an ack from both pipes
        Consumer c = (Consumer) (Object a0) -> {
            cdl.countDown();
            WAVELOG.log(Level.DEBUG, "CDL lowered by "+a0);
        };
        messagePipe = receiver.createMessagePipe(c);
        WAVELOG.log(Level.DEBUG, "[WM] create pipe");
        unidentifiedMessagePipe = receiver.createUnidentifiedMessagePipe(c);
        WAVELOG.log(Level.DEBUG, "[WM] created unidentifiedpipe");
        ExecutorService executorService = new ScheduledThreadPoolExecutor(5);
        SignalServiceMessageSender sender = new SignalServiceMessageSender(
                signalServiceConfiguration,
                credentialsProvider,
                waveStore,
                lock,
                SIGNAL_USER_AGENT,
                true,
                Optional.of(messagePipe),
                Optional.of(unidentifiedMessagePipe),
                Optional.empty(),
                null,
                executorService,
                512 * 1024,
                true);
        try {
            boolean await = cdl.await(20, TimeUnit.SECONDS);
            if (! await ) {
                throw new IOException ("No response (after 20 seconds) from server when creating MessageSender");
            }
        } catch (InterruptedException ex) {
            throw new IOException ("Interrupted while creating MessageSender", ex);
        }
        return sender;
    }
    
    // single-threaded processing for now
    void processMessagePipe(SignalServiceMessagePipe pipe) {
        WAVELOG.log(Level.INFO, "[WM] processMessagePipe "+pipe);
        Thread t = new Thread() {
            @Override
            public void run() {
                boolean listen = true;
                while (listen) {
                    try {
                        WAVELOG.log(Level.DEBUG, "[MessagePipe] waiting for envelope...");
                        SignalServiceEnvelope envelope = pipe.read(300, TimeUnit.SECONDS);
                        WAVELOG.log(Level.DEBUG, "[MessagePipe] got envelope " + Objects.hashCode(envelope)+ " and Type = " + envelope.getType());
                        SignalServiceContent content = mydecrypt(envelope);
                        WAVELOG.log(Level.DEBUG, "[MessagePipe] got content: " + content);
                        if (content != null) {
                            if (content.getSyncMessage().isPresent()) {
                                WAVELOG.log(Level.DEBUG,"[MessagePipe] envelope has syncmessage");
                                SignalServiceSyncMessage sssm = content.getSyncMessage().get();
                                processSyncMessage(content.getSender(), sssm);
                            }
                            if (content.getDataMessage().isPresent()) {
                                SignalServiceDataMessage ssdm = content.getDataMessage().get();
                                processDataMessage(content.getSender(), ssdm);
                            }
                            if (content.getTypingMessage().isPresent()) {
                                SignalServiceTypingMessage sstm = content.getTypingMessage().get();
                                processTypingMessage(content.getSender(), sstm);
                            }
                            if (content.getReceiptMessage().isPresent()) {
                                SignalServiceReceiptMessage ssrm = content.getReceiptMessage().get();
                                processReceiptMessage(content.getSender(), ssrm);
                            }
                        }
                    } catch (TimeoutException toe) {
                        WAVELOG.log(Level.DEBUG, "timeout waiting for message, no big deal.");
                    } catch (ProtocolInvalidMessageException invalid) {
                        System.err.println("ProtocolInvalidmessage: "+invalid);
                        invalid.printStackTrace();
                    } catch (InvalidMetadataVersionException invalidm) {
                        System.err.println("Invalid MetadataVersion: "+invalidm);
                        invalidm.printStackTrace();
                    } catch (IOException ioex) {
                        WAVELOG.log(Level.WARNING, "ioexception while reading messages, consider this fatal for now.");
                        listen = false;
                        ioex.printStackTrace();
                    } catch (Exception ex) {
                        // listen = false;
                        ex.printStackTrace();
                        // System.err.println("Due to the above exception, we will stop listening.");
                        System.err.println("Despite the above exception, we continue listening.");

                    }
                }
                WAVELOG.log(Level.INFO, "We stopped listening for incoming messages.");
            }
        };
        t.start();
    }

    private void processEnvelope(SignalServiceEnvelope envelope) throws Exception {
        WAVELOG.log(Level.DEBUG, "WaveManager will process Envelope " + envelope);
        SignalServiceContent content = mydecrypt(envelope);
        WAVELOG.log(Level.DEBUG, "WaveManager got content: " + content);
        if (content != null) {
            if (content.getSyncMessage().isPresent()) {
                WAVELOG.log(Level.DEBUG, "WaveManager envelope has syncmessage");
                SignalServiceSyncMessage sssm = content.getSyncMessage().get();
                processSyncMessage(content.getSender(), sssm);
            }
            if (content.getDataMessage().isPresent()) {
                SignalServiceDataMessage ssdm = content.getDataMessage().get();
                processDataMessage(content.getSender(), ssdm);
            }
        }
    }
    
    SignalServiceContent mydecrypt(SignalServiceEnvelope sse) throws Exception {
        SignalServiceCipher cipher = new SignalServiceCipher(signalServiceAddress,
                waveStore,
                new LockImpl(),
                getCertificateValidator());
        SignalServiceContent content = null;
        try {
            int bl = sse.getContent().length;
            WAVELOG.log(Level.DEBUG, "I need to decrypt " + sse+" with " +bl+" bytes, with id "+Objects.hashCode(sse));
            content = cipher.decrypt(sse);
            WAVELOG.log(Level.DEBUG, "I did to decrypt " + sse+" with id "+Objects.hashCode(sse));
        } catch (ProtocolNoSessionException e) {
            String senderId = e.getSender();
            int senderDevice = e.getSenderDevice();
            WAVELOG.log(Level.DEBUG, "no session for senderid = " + senderId + " and device = " + senderDevice);
            String tuuid = null;
            if (this.waveStore.getMyUuid().equals(senderId)) {
                WAVELOG.log(Level.DEBUG, "WaveManager has message from and for me!");
                tuuid = senderId;
                senderId = "";
            }
            Optional<Contact> contact = getContactByUuid(senderId);
            if (contact.isPresent()) {
                tuuid = contact.get().getUuid();
            }
            if (tuuid == null) {
                contact = getContactByNumber(senderId);
                if (contact.isPresent()) {
                    tuuid = contact.get().getUuid();
                } else {
                    throw new IllegalArgumentException ("Unknown sender: "+senderId);
                }
            }
            SignalServiceAddress addy
                    = new SignalServiceAddress(UUID.fromString(tuuid), senderId);
            WAVELOG.log(Level.INFO, " decrypt will send nullmessage to "+addy);
            sender.sendNullMessage(addy, Optional.empty());
            int bl2 = sse.getContent().length;
            WAVELOG.log(Level.DEBUG, " did send null message, we should have session now for "+bl2+" bytes");

            SignalServiceCipher cipher2 = new SignalServiceCipher(signalServiceAddress,
                waveStore,
                new LockImpl(),
                getCertificateValidator());
            content = cipher2.decrypt(sse);
        }
        WAVELOG.log(Level.DEBUG, " descrypt will return "+content);
        return content;
    }

    void processSyncMessage(SignalServiceAddress sender, SignalServiceSyncMessage sssm) throws InvalidMessageException, IOException {
        WAVELOG.log(Level.INFO, "Process INCOMING SyncMessage: "+sssm);
        if (sssm.getContacts().isPresent()) {
            ContactsMessage msg = sssm.getContacts().get();
            processContactsMessage(msg);
        }
        if (sssm.getSent().isPresent()) {
            SentTranscriptMessage msg = sssm.getSent().get();
            processSentTranscriptMessage(sender, msg);
        }
        if (sssm.getGroups().isPresent()) {
            WAVELOG.log(Level.DEBUG, "WaveManager has groupssyncmessage!");
            SignalServiceAttachment get = sssm.getGroups().get();
        }
    }
    
    void processTypingMessage(SignalServiceAddress sender, SignalServiceTypingMessage sstm) throws InvalidMessageException, IOException {
        WAVELOG.log(Level.INFO, "Process TypingMessage!");
        String uuid = sender.getUuid().get().toString();
        this.messageListener.gotTypingAction(uuid, sstm.isTypingStarted(), sstm.isTypingStopped());
    }

    void processReceiptMessage(SignalServiceAddress sender, SignalServiceReceiptMessage ssrm) throws InvalidMessageException, IOException {
        WAVELOG.log(Level.INFO, "Process TypingMessage!");
        String uuid = sender.getUuid().get().toString();
        this.messageListener.gotReceiptMessage(uuid, ssrm.getType().ordinal(), ssrm.getTimestamps());
    }

    void processDataMessage(SignalServiceAddress sender, SignalServiceDataMessage ssdm) {
        WAVELOG.log(Level.INFO, "Process datamessage");
        if (this.messageListener != null) {
            String uuid = sender.getUuid().get().toString();
            String content = ssdm.getBody().orElse(null);
            if (content != null) {
                this.messageListener.gotMessage(uuid, content, ssdm.getTimestamp());
            }
        }
    }

    void processDataMessage(SignalServiceAddress sender, SignalServiceDataMessage ssdm, 
            SignalServiceAddress receiver) {
        WAVELOG.log(Level.INFO, "Process datamessage");
        if (this.messageListener != null) {
            String uuid = sender.getUuid().get().toString();
            String recuuid = receiver.getUuid().get().toString();
            String content = ssdm.getBody().orElse(null);
            if (content != null) {
                this.messageListener.gotMessage(uuid, content, ssdm.getTimestamp(), recuuid);
            }
        }
    }

    private static CertificateValidator getCertificateValidator() {
        try {
            ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
            return new CertificateValidator(unidentifiedSenderTrustRoot);
        } catch (InvalidKeyException | IOException e) {
            throw new RuntimeException ("Error creating certificateValidator", e);
        }
    }
    
    private Optional<Contact> getContactByNumber(String number) {
        FilteredList<Contact> filtered = contacts.filtered(c -> number.equals(c.getNr()));
        return Optional.ofNullable(filtered.size() > 0 ? filtered.get(0): null);
    }

    private Optional<Contact> getContactByUuid(String uuid) {
        FilteredList<Contact> filtered = contacts.filtered(c -> uuid.equals(c.getUuid()));
        return Optional.ofNullable(filtered.size() > 0 ? filtered.get(0): null);
    }

    private void processSentTranscriptMessage(SignalServiceAddress sender, SentTranscriptMessage msg) {
        WAVELOG.log(Level.DEBUG, "ProcessSentTranscriptMessage with sender = " + sender+" and msg = "+msg.getMessage());
        SignalServiceDataMessage message = msg.getMessage();
        if (msg.getDestination().isPresent()) {
            processDataMessage(sender, msg.getMessage(), msg.getDestination().get());
        } else if (message.isGroupV2Message()) {
            WAVELOG.log(Level.DEBUG, "WaveManager GROUPv2message");
        } else if (message.isGroupV2Update()) {
            WAVELOG.log(Level.DEBUG, "WaveManager GROUPv2Update");
        }
    }

    private void processContactsMessage(ContactsMessage msg) throws IOException {
        this.lastSyncContactResponse.set(System.currentTimeMillis());
        SignalServiceAttachment att = msg.getContactsStream();
        SignalServiceAttachmentPointer pointer = att.asPointer();
        Path output = Files.createTempFile("pre", "post");

        try {
            receiver.retrieveAttachment(pointer, output.toFile(), MAX_FILE_STORAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IOException("Can't retrieve attachment", ex);
        }

        try {
            InputStream ais = AttachmentCipherInputStream.createForAttachment(output.toFile(), pointer.getSize().orElse(0), pointer.getKey(), pointer.getDigest().get());
            Path attPath = Files.createTempFile("att", "bin");
            File attFile = attPath.toFile();
            Files.copy(ais, attPath, StandardCopyOption.REPLACE_EXISTING);

            InputStream ois = new FileInputStream(attFile);
            DeviceContactsInputStream is = new DeviceContactsInputStream(ois);
            DeviceContact dc = is.read();
            contacts.clear();
            while (dc != null) {
                Contact contact = new Contact(dc.getName().orElse("anonymous"),
                        dc.getAddress().getUuid().get().toString(),
                        dc.getAddress().getNumber().orElse("123"));
                if (dc.getAvatar().isPresent()) {
                    SignalServiceAttachmentStream ssas = dc.getAvatar().get();
                    long length = ssas.getLength();
                    InputStream inputStream = ssas.getInputStream();
                    byte[] b = new byte[(int) length];
                    inputStream.read(b);
                    String nr = dc.getAddress().getNumber().get();
                    Path contactsPath = SIGNAL_FX_CONTACTS_DIR.toPath();
                    Path avatarPath = contactsPath.resolve("contact-avatar"+nr);
                    Files.write(avatarPath, b, StandardOpenOption.CREATE);
                    contact.setAvatarPath(avatarPath.toAbsolutePath().toString());
                }

                contacts.add(contact);
                if (ois.available() == 0) {
                    dc = null;
                } else {
                    dc = is.read();
                }
            }
            storeContacts();
        } catch (Exception e) {
            e.printStackTrace();
        }
        WAVELOG.log(Level.INFO, "WaveManager has done reading/sync contacts ");

    }
    
    private List<Contact> readContacts() throws IOException {
        List<Contact> answer = new LinkedList<>();
        Path path = SIGNAL_FX_CONTACTS_DIR.toPath().resolve("contactlist");
        if (Files.exists(path)) {
            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i = i + 4) {
                Contact c = new Contact(lines.get(i), lines.get(i + 1), lines.get(i + 2));
                String avt = lines.get(i + 3);
                c.setAvatarPath(avt);
                answer.add(c);
            }
        }
        return answer;
    }
    
    private void storeContacts() throws IOException {
        WAVELOG.log(Level.INFO, "store contacts");
        Path path = SIGNAL_FX_CONTACTS_DIR.toPath().resolve("contactlist");
        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        Files.deleteIfExists(path);
        List<String> lines = new LinkedList<>();
        for (Contact contact : contacts) {
            lines.add(contact.getName());
            lines.add(contact.getUuid());
            lines.add(contact.getNr());
            lines.add(contact.getAvatarPath());
        }
        Files.write(path, lines, StandardOpenOption.CREATE);
        contactStorageDirty = true;
    }

    class ClientConnectivityListener implements ConnectivityListener {

        private final CountDownLatch connectedLatch = new CountDownLatch(1);
        ClientConnectivityListener() {    
        }
        
        /**
         * Returns when the connection associated with this listener is connected.
         * Wait for some time if needed
         * @param ms the maximum amount of milliseconds we want to wait.
         */
        public void waitConnected(int ms) throws InterruptedException, IOException {
            System.err.println("[WC] " + System.currentTimeMillis()+" Waiting "+ms+" ms to be connected...");
//            connectedLatch.await(ms, TimeUnit.MILLISECONDS);
//            System.err.println("[WC] " + System.currentTimeMillis()+" Waited "+ms+" ms to be connected...");
//
//            if (connectedLatch.getCount() > 0) throw new IOException("Failed to"
//                    + "connect!");
        }
        
        @Override
        public void onConnected() {
            WAVELOG.log(Level.INFO, "[ConnectivityListener] onConnected");
        }

        @Override
        public void onConnecting() {
            WAVELOG.log(Level.INFO, "[ConnectivityListener] onConnecting");
        }

        @Override
        public void onDisconnected() {
            WAVELOG.log(Level.INFO, "[ConnectivityListener] onDisconnected");
        }

        @Override
        public void onAuthenticationFailure() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean onGenericFailure(Response response, Throwable throwable) {
            System.err.println("Generic failure, response = "+response);
            throwable.printStackTrace();
            return false;
        }

    }
}
