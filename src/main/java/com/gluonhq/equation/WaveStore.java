/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation;

import static com.gluonhq.equation.WaveManager.WAVELOG;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.groups.SenderKeyName;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceProtocolStore;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;

/**
 *
 * Simple implementation of the SignalServiceProtocolStore, using standard io.
 * Whenever the entities in the store are updated using API methods of this class,
 * the underlying storage (on Filesystem) is updated as well.
 */
public class WaveStore implements SignalServiceProtocolStore {

    private IdentityKeyPair identityKeyPair;
    Map<Integer, PreKeyRecord> map = new HashMap<>();
    Map<Integer, SignedPreKeyRecord> signedMap = new HashMap<>();
    Map<MySenderKey, SenderKeyRecord> senderKeyMap = new HashMap<>();

    private StaticCredentialsProvider credentialsProvider;

    private boolean initialized;

    private Map<SignalProtocolAddress, byte[]> sessions = new HashMap<>();
    private final Map<SignalProtocolAddress, IdentityKey> trustedKeys = new HashMap<>();

    private int localRegistrationId;

    private int deviceId;
    private String myUuid = "nobody";

    private final static String SIGNAL_FX;
    public final static Path SIGNAL_FX_PATH;
    public final static Path SIGNAL_FX_STORE_PATH;
    private final static File SIGNAL_FX_DIR;
    public final static File SIGNAL_FX_CONTACTS_DIR;

    static {
        SIGNAL_FX = System.getProperty("user.home")
                + File.separator + ".signalfx";
        SIGNAL_FX_DIR = new File(SIGNAL_FX);
        SIGNAL_FX_DIR.mkdirs();
        SIGNAL_FX_PATH = SIGNAL_FX_DIR.toPath();
        SIGNAL_FX_STORE_PATH = SIGNAL_FX_PATH.resolve("store");
        Path contacts = SIGNAL_FX_DIR.toPath().resolve("contacts/");
        SIGNAL_FX_CONTACTS_DIR = contacts.toFile();
        try {
            Files.createDirectories(SIGNAL_FX_STORE_PATH);
            Files.createDirectories(contacts);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static WaveStore instance = new WaveStore();

    public static WaveStore getInstance() {
        return instance;
    }

    private WaveStore() {
        // if we have a credentialsprovider, we assume we are initialized, and
        // the other stored info is retrieved.
        this.initialized = retrieveCredentialsProvider();
        if (this.initialized) {
            try {
                retrieveIdentityKeyPair();
                retrieveSignedPreKeys();
                retrievePreKeys();
                retrieveSessions();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Checks if the store is initialized. Once it has a keypair in persistent
     * storage, we assume it is initialized.
     *
     * @return
     */
    public boolean isInitialized() {
        return initialized;
    }

    public void setIdentityKeyPair(IdentityKeyPair ikp) {
        this.identityKeyPair = ikp;
        persistIdentityKeyPair();
    }

    public void setMyUuid(String v) {
        this.myUuid = v;
    }

    public String getMyUuid() {
        return this.myUuid;
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return this.identityKeyPair;
    }

    public void setDeviceId(int devid) {
        this.deviceId = devid;
    }

    public void setRegistrationId(int regid) {
        this.localRegistrationId = regid;
    }

    @Override
    public int getLocalRegistrationId() {
        return this.localRegistrationId;
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        IdentityKey existing = trustedKeys.get(address);

        if (!identityKey.equals(existing)) {
            trustedKeys.put(address, identityKey);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        IdentityKey trusted = trustedKeys.get(address);
        return (trusted == null || trusted.equals(identityKey));
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        return trustedKeys.get(address);
    }

    @Override
    public PreKeyRecord loadPreKey(int i) throws InvalidKeyIdException {
        return map.get(i);
    }

    @Override
    public void storePreKey(int i, PreKeyRecord pkr) {
        map.put(i, pkr);
        try {
            persistPreKey(i, pkr);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public boolean containsPreKey(int i) {
        return map.containsKey(i);
    }

    @Override
    public void removePreKey(int i) {
        map.remove(i);
        try {
            deletePreKey(i);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int i) throws InvalidKeyIdException {
        return signedMap.get(i);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        List<SignedPreKeyRecord> answer = new ArrayList<>(signedMap.size());
        answer.addAll(signedMap.values());
        return answer;
    }

    @Override
    public void storeSignedPreKey(int i, SignedPreKeyRecord spkr) {
        signedMap.put(i, spkr);
        persistSignedPreKeys();
    }

    @Override
    public boolean containsSignedPreKey(int i) {
        return signedMap.containsKey(i);
    }

    @Override
    public void removeSignedPreKey(int i) {
        signedMap.remove(i);
        persistSignedPreKeys();
    }

    @Override
    public synchronized SessionRecord loadSession(SignalProtocolAddress remoteAddress) {
        WAVELOG.log(Level.DEBUG, "[STORE] loadSession asked for "+remoteAddress);
        try {
            if (containsSession(remoteAddress)) {
                WAVELOG.log(Level.DEBUG, "[STORE] we have that session");
                return new SessionRecord(sessions.get(remoteAddress));
            } else {
                WAVELOG.log(Level.DEBUG, "Not found, sessions = "+sessions);
                WAVELOG.log(Level.DEBUG, "[STORE] need to create new sessionrecord");
                return new SessionRecord();
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public synchronized List<Integer> getSubDeviceSessions(String name) {
        List<Integer> deviceIds = new LinkedList<>();
        for (SignalProtocolAddress key : sessions.keySet()) {
            if (key.getName().equals(name)
                    && !((key.getName().equals(myUuid)) && (key.getDeviceId() == deviceId))) {
                deviceIds.add(key.getDeviceId());
            }
        }
        return deviceIds;
    }

    @Override
    public synchronized void storeSession(SignalProtocolAddress address, SessionRecord record) {
        try {
            sessions.put(address, record.serialize());
            persistSession(address, record);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public synchronized boolean containsSession(SignalProtocolAddress address) {
        boolean answer = sessions.containsKey(address);
        if (answer) {
            byte[] sr = sessions.get(address);
            try {
                SessionRecord sessionRecord = new SessionRecord(sessions.get(address));
                answer = sessionRecord.getSessionState().hasSenderChain();
                int sv = sessionRecord.getSessionState().getSessionVersion();
                if (!answer) {
                    WAVELOG.log(Level.DEBUG, "Invalid session for " + address);
                }

            } catch (IOException ex) {
                ex.printStackTrace();
                answer = false;
            }
        }
        return answer;
    }

    @Override
    public synchronized void deleteSession(SignalProtocolAddress address) {
        sessions.remove(address);
    }

    @Override
    public synchronized void deleteAllSessions(String name) {
        for (SignalProtocolAddress key : sessions.keySet()) {
            if (key.getName().equals(name)) {
                sessions.remove(key);
            }
        }
    }

    @Override
    public void archiveSession(SignalProtocolAddress address) {
        WAVELOG.log(Level.DEBUG, "We need to archive session for " + address);
        byte[] b = sessions.get(address);
        try {
            SessionRecord s = new SessionRecord(b);
            if (s != null) {
                s.archiveCurrentState();
                persistSession(address, s);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void setCredentialsProvider(StaticCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        this.myUuid = credentialsProvider.getUuid().toString();
        this.deviceId = credentialsProvider.getDeviceId();
        persistCredentialsProvider();
    }

    public StaticCredentialsProvider getCredentialsProvider() {
        return this.credentialsProvider;
    }

    private void persistCredentialsProvider() {
        try {
            Path path = SIGNAL_FX_STORE_PATH.resolve("credentials");
            File credFile = path.toFile();
            if (credFile.exists()) {
                credFile.delete();
            }
            UUID uuid = credentialsProvider.getUuid();
            Files.writeString(path, uuid.toString() + "\n", StandardOpenOption.CREATE);
            Files.writeString(path, credentialsProvider.getE164() + "\n", StandardOpenOption.APPEND);
            Files.writeString(path, credentialsProvider.getPassword() + "\n", StandardOpenOption.APPEND);
            Files.writeString(path, Integer.toString(credentialsProvider.getDeviceId()) + "\n", StandardOpenOption.APPEND);
            Files.writeString(path, credentialsProvider.getSignalingKey() + "\n", StandardOpenOption.APPEND);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private boolean retrieveCredentialsProvider() {
        try {
            Path path = SIGNAL_FX_STORE_PATH.resolve("credentials");
            if (!Files.exists(path)) {
                return false;
            }
            List<String> lines = Files.readAllLines(path);
            String uuidString = lines.get(0);
            UUID uuid = UUID.fromString(uuidString);
            String number = lines.get(1);
            String password = lines.get(2);
            this.deviceId = Integer.parseInt(lines.get(3));
            this.myUuid = uuidString;

            this.credentialsProvider = new StaticCredentialsProvider(uuid,
                    number, password, "signalingkey", deviceId);
            return true;

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private void persistIdentityKeyPair() {
        Path path = SIGNAL_FX_STORE_PATH.resolve("identity");
        File f = path.toFile();
        if (f.exists()) {
            f.delete();
        }
        byte[] b = identityKeyPair.serialize();
        try {
            Files.write(path, b);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private boolean retrieveIdentityKeyPair() throws IOException {
        try {
            Path path = SIGNAL_FX_STORE_PATH.resolve("identity");
            byte[] b = Files.readAllBytes(path);
            this.identityKeyPair = new IdentityKeyPair(b);
            return true;
        } catch (InvalidKeyException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private void persistPreKey(int i, PreKeyRecord pkr) throws IOException {
        Path ppath = SIGNAL_FX_STORE_PATH.resolve("prekeys");
        if (!Files.exists(ppath)) {
            Files.createDirectories(ppath);
        }
        Path path = ppath.resolve(Integer.toString(i));
        File f = path.toFile();
        if (f.exists()) {
            f.delete();
        }
        Files.write(path, pkr.serialize());
    }
    
    private void deletePreKey(int i) throws IOException {
        Path ppath = SIGNAL_FX_STORE_PATH.resolve("prekeys").resolve(Integer.toString(i));
        Files.delete(ppath);
    }

    private boolean retrievePreKeys() throws IOException {
        Path ppath = SIGNAL_FX_STORE_PATH.resolve("prekeys");
        if (!Files.exists(ppath)) {
            Files.createDirectories(ppath);
        }
        Files.list(ppath).forEach(path -> {
            try {
                String name = path.getFileName().toString();
                int i = Integer.parseInt(name);
                byte[] b = Files.readAllBytes(path);
                map.put(i, new PreKeyRecord(b));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        return true;
    }

    private void persistSignedPreKeys() {
        Path path = SIGNAL_FX_STORE_PATH.resolve("signedprekeys");
        File f = path.toFile();
        if (f.exists()) {
            f.delete();
        }
        List<String> lines = new LinkedList<>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream daos = new DataOutputStream(baos);
        try {
            daos.writeInt(signedMap.size());
            for (Entry<Integer, SignedPreKeyRecord> entry : signedMap.entrySet()) {
                lines.add(Integer.toString(entry.getKey()));
                byte[] b = entry.getValue().serialize();
                daos.writeInt(entry.getKey());
                daos.writeInt(b.length);
                daos.write(b);
            }
            daos.flush();
            Files.write(path, baos.toByteArray());
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    private boolean retrieveSignedPreKeys() throws IOException {
        Path path = SIGNAL_FX_STORE_PATH.resolve("signedprekeys");
        byte[] b = Files.readAllBytes(path);
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        DataInputStream dis = new DataInputStream(bais);
        int entriesize = dis.readInt();
        signedMap.clear();
        WAVELOG.log(Level.DEBUG, "retrieving signed PK's, size = %s", Integer.toString(entriesize));
        for (int i = 0; i < entriesize; i++) {
            int id = dis.readInt();
            int bs = dis.readInt();
            byte[] spkrb = new byte[bs];
            int read = dis.read(spkrb);
            if (read != bs) {
                throw new RuntimeException("signed prekeys tampered with!");
            }
            WAVELOG.log(Level.DEBUG, "Got id %s with %s bytes, pk = %s", id, b.length, Arrays.toString(b));
            SignedPreKeyRecord spkr = new SignedPreKeyRecord(spkrb);
            signedMap.put(id, spkr);
        }
        return true;
    }

    private void persistSession(SignalProtocolAddress address, SessionRecord record) throws IOException {
        Path ppath = SIGNAL_FX_STORE_PATH.resolve("sessions");
        if (!Files.exists(ppath)) {
            Files.createDirectories(ppath);
        }
        String fname = address.getName()+"_" + address.getDeviceId();
        Path path = ppath.resolve(fname);
        File f = path.toFile();
        if (f.exists()) {
            f.delete();
        }
        Files.write(path, record.serialize());
    }

    private boolean retrieveSessions() throws IOException {
        Path ppath = SIGNAL_FX_STORE_PATH.resolve("sessions");
        if (!Files.exists(ppath)) {
            Files.createDirectories(ppath);
        }
        Files.list(ppath).forEach(path -> {
            try {
                String name = path.getFileName().toString();
                int idx = name.indexOf("_");
                String aname = name.substring(0,idx);
                int i = Integer.parseInt(name.substring(idx+1));
                byte[] b = Files.readAllBytes(path);
                sessions.put(new SignalProtocolAddress(aname, i), b);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        return true;
    }

    @Override
    public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> addressNames) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clearSenderKeySharedWith(Collection<SignalProtocolAddress> addresses) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void storeSenderKey(SignalProtocolAddress sender, UUID distributionId, SenderKeyRecord record) {
        MySenderKey msk = new MySenderKey(sender, distributionId);
        senderKeyMap.put(msk, record);
        System.err.println("stored sender, keymap = "+senderKeyMap);
    }

    @Override
    public SenderKeyRecord loadSenderKey(SignalProtocolAddress sender, UUID distributionId) {
        System.err.println("LSK asked for sender = "+sender);
        System.err.println("senderdvid = "+sender.getDeviceId());
        MySenderKey msk = new MySenderKey(sender, distributionId);
        SenderKeyRecord answer = senderKeyMap.get(msk);
        System.err.println("got answer "+answer+", keymap = "+senderKeyMap);

        if (answer == null) answer = new SenderKeyRecord();
        return answer;
    }

    @Override
    public boolean isMultiDevice() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Transaction beginTransaction() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    static class MySenderKey {

        private final SignalProtocolAddress sender;
        private final UUID distributionId;
        
        MySenderKey(SignalProtocolAddress sender, UUID distributionId) {
            this.sender = sender;
            this.distributionId = distributionId;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 11 * hash + this.sender.hashCode();
            hash = 11 * hash + this.distributionId.hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final MySenderKey other = (MySenderKey) obj;
            if (!Objects.equals(this.sender, other.sender)) {
                return false;
            }
            if (!Objects.equals(this.distributionId, other.distributionId)) {
                return false;
            }
            return true;
        }
        
    }
   
}
