/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation;

import com.gluonhq.equation.internal.KeyUtil;
import com.gluonhq.equation.internal.LockImpl;
import com.gluonhq.equation.internal.TrustStoreImpl;
import com.gluonhq.equation.log.WaveLogger;
import com.gluonhq.equation.message.MessagingClient;
import com.gluonhq.equation.model.Contact;
import com.gluonhq.equation.model.Group;
import com.gluonhq.equation.model.Message;
import com.gluonhq.equation.provision.ProvisioningClient;
import com.gluonhq.equation.provision.ProvisioningManager;
import com.gluonhq.equation.util.ChannelUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.Security;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidRegistrationIdException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.protocol.SenderKeyDistributionMessage;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.*;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.SignalGroupSessionBuilder;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import org.whispersystems.util.Base64;

/**
 * The entry point to Gluon Wave. 
 */
public class WaveManager {


    static final String SIGNAL_SERVICE_URL = "https://textsecure-service.whispersystems.org";
    static final String SIGNAL_USER_AGENT = "Signal-Desktop/5.14.0 Linux";
    static final String SIGNAL_KEY_BACKUP_URL = "https://api.backup.signal.org";
    static final String SIGNAL_STORAGE_URL = "https://storage.signal.org";
    static final String UNIDENTIFIED_SENDER_TRUST_ROOT = "BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF";
    static final String ZKGROUP_SERVER_PUBLIC_PARAMS = "AMhf5ywVwITZMsff/eCyudZx9JDmkkkbV6PInzG4p8x3VqVJSFiMvnvlEKWuRob/1eaIetR31IYeAbm0NdOuHH8Qi+Rexi1wLlpzIo1gstHWBfZzy1+qHRV5A4TqPp15YzBPm0WSggW6PbSn+F4lf57VCnHF7p8SvzAA2ZZJPYJURt8X7bbg+H3i+PEjH9DXItNEqs2sNcug37xZQDLm7X36nOoGPs54XsEGzPdEV+itQNGUFEjY6X9Uv+Acuks7NpyGvCoKxGwgKgE5XyJ+nNKlyHHOLb6N1NuHyBrZrgtY";
    public static WaveLogger WAVELOG;
    static {
        Security.addProvider(new BouncyCastleProvider());
        Security.setProperty("crypto.policy", "unlimited");
        
    }
    private static CertificateValidator getCertificateValidator() {
        try {
            ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
            return new CertificateValidator(unidentifiedSenderTrustRoot);
        } catch (InvalidKeyException | IOException e) {
            throw new RuntimeException ("Error creating certificateValidator", e);
        }
    }
    private WaveStore waveStore;
    private CredentialsProvider credentialsProvider;
    private ClientConnectivityListener cl;
    long MAX_FILE_STORAGE = 1024 * 1024 * 4;
    final TrustStore trustStore = new TrustStoreImpl();
    private final LockImpl lock;
    private final ObservableList<Contact> contacts = FXCollections.observableArrayList();
    private final ObservableList<Group> groups = FXCollections.observableArrayList();
    
    private Contact me;
    
    private final Map<String, Group> groupMap = new HashMap<>();

    SignalServiceConfiguration signalServiceConfiguration;

    public File SIGNAL_FX_CONTACTS_DIR;
    
    private CountDownLatch syncContactsLatch;
    
    private LongProperty lastSyncContactRequest = new SimpleLongProperty();
    private LongProperty lastSyncContactResponse = new SimpleLongProperty();
    
    private final String CONTACT_SYNC_ERROR = "CONTACT_SYNC_ERROR";
    
    private boolean firstRun = false;
    StorageKey storageKey = null;

    
    private MessagingClient messageListener;
    
    private boolean connected;
    private SignalServiceMessageReceiver receiver;
    private SignalServiceMessageSender sender;
    private SignalServiceMessagePipe messagePipe;
    private SignalServiceMessagePipe unidentifiedMessagePipe;
    
    private SignalServiceAddress signalServiceAddress;
    private boolean contactStorageDirty = true;
    private boolean groupStorageDirty = true;
    private ProvisioningManager provisioningManager;
    private AccountManager accountManager;
    HashMap<Integer, AuthCredentialResponse> groupCredentials;
    private Supplier<Boolean> fatalErrorSupplier;
    private Consumer<String> restartRequestConsumer;

    public WaveManager() { 
        WAVELOG = new WaveLogger();
        WAVELOG.log(Level.INFO, "Starting WaveManager");
        this.waveStore = new WaveStore();
        Path contacts = waveStore.SIGNAL_FX_PATH.resolve("contacts/");
        SIGNAL_FX_CONTACTS_DIR = contacts.toFile();
        try {
            Files.createDirectories(contacts);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        this.lock = new LockImpl();
        postInit();
    }
    
    /**
     * This method is invoked by the constructor and after a reset of the
     * configuration/storage directory happened.
     */
    private void postInit() {
        this.waveStore = new WaveStore();
        this.signalServiceConfiguration = createConfiguration();
        this.credentialsProvider = waveStore.getCredentialsProvider();
        if (isProvisioned()) {
            this.signalServiceAddress = new SignalServiceAddress(credentialsProvider.getUuid(), credentialsProvider.getE164());
        }
        this.contactStorageDirty = true;
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
        System.err.println(Thread.currentThread() + " WAVEMANAGER initialize will now be called. FirstRUn? " + firstRun);
        if (this.accountManager == null) {
            this.accountManager = new AccountManager(getSignalServiceConfiguration(), credentialsProvider);
        }
        try {
            getWaveLogger().log(Level.DEBUG, "ensure we are connected");
            this.ensureConnected();
            accountManager.getRemoteConfig();
            if (firstRun) {
                System.err.println("FIRSTRUN");
            }
            // get group certificate");
            long days = LocalDate.now().toEpochDay();
            groupCredentials = this.accountManager.getGroupsV2Api().getCredentials((int) days);
            syncKeys();
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
        WAVELOG.log(Level.INFO, "[WM] doneSyncEverything");
    }
    
    public void syncKeys() throws IOException {
        SignalServiceProtos.SyncMessage.Request request = SignalServiceProtos.SyncMessage.Request.newBuilder()
                .setType(SignalServiceProtos.SyncMessage.Request.Type.KEYS).build();
                RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        System.err.println("[WM] will sendSyncMessage for keys");
        sendSyncMessage(message);
    }

    /**
     * Send a request to synchronize contacts. We expect an sincoming message with 
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
                .setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS)
                .build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        sendSyncMessage(message);
    }

    // TODO: the following callback methods could be on an interface
    public void setOnFatalError(Supplier<Boolean> sup) {
        this.fatalErrorSupplier = sup;
    }
    
    public void setOnRestartRequest(Consumer<String> p) {
        this.restartRequestConsumer = p;
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
        System.err.println("[WM] getContacts asked, csd = "+contactStorageDirty);
        if (contactStorageDirty) {
            try {
                contacts.clear(); // TODO make this smarter
                contacts.addAll(readContacts());
                contactStorageDirty = false;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        System.err.println("[WM] getContacts asked for "+Objects.hash(contacts)+" = "+ contacts);
        System.err.println("#contacts = " + contacts.size());
        return contacts;
    }

    public ObservableList<Group> getGroups() {
        System.err.println("[WM] getGroups asked, csd = "+groupStorageDirty);
        if (groupStorageDirty) {
            try {
                groups.clear(); // TODO make this smarter
                groups.addAll(readGroups());
                for (Group g : groups) {
                    groupMap.put(g.getName(), g);
                }
                groupStorageDirty = false;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        System.err.println("[WM] getContacts asked for "+Objects.hash(contacts)+" = "+ contacts);
        System.err.println("#contacts = " + contacts.size());
        return groups;
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
        return sendMessage(uuid, text, List.of());
    }

    private List<SignalServiceAttachment> uploadAttachments(List<Path> attachment) throws IOException {
        List<SignalServiceAttachment> ssa = new LinkedList<>();
        for (Path path : attachment) {
            SignalServiceAttachmentPointer ptr = uploadAttachment(path);
            ssa.add(ptr);
        }
        return ssa;
    }
    
    public long sendMessage(String uuid, String text, List<Path> attachment) throws IOException {
        ensureConnected();
        List<SignalServiceAttachment> ssa = uploadAttachments(attachment);
        Contact target = contacts.stream().filter(c -> uuid.equals(c.getUuid())).findFirst().get();
        Optional<SignalServiceAddress> add = SignalServiceAddress.fromRaw(uuid, target.getNr());
        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                .withAttachments(ssa)
                .withBody(text).build();
        try {
            SendMessageResult res = sender.sendMessage(add.get(), Optional.empty(), message);
        } catch (UntrustedIdentityException ex) {
            throw new IOException ("Could not send message to "+add.get(), ex);
        }
        return message.getTimestamp();
    }

    public long sendGroupMessage(String uuid, String text, List<Path> attachment) throws IOException, 
            InvalidCertificateException, InvalidInputException, UntrustedIdentityException, 
            NoSessionException, InvalidKeyException, InvalidRegistrationIdException {
        ensureConnected();
        System.err.println("SENDGROUPMESSAGE, uuid = "+uuid+" and gm = "+groupMap);
        Group mygroup = groupMap.get(uuid);
        GroupMasterKey masterKey = mygroup.getMasterKey();
        SignalServiceGroupV2 group = SignalServiceGroupV2.newBuilder(masterKey).build();
        List<SignalServiceAttachment> ssa = uploadAttachments(attachment);
        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                .withAttachments(ssa)
                .asGroupMessage(group)
                .withBody(text).build();
        System.err.println("Send group!!");
        System.err.println("Members = "+ mygroup.getMembers());
        List<SignalServiceAddress> recipients = mygroup.getMembers()
                .stream().filter(a -> !(getMyUuid().equals(a.getUuid().get().toString())))
                .collect(Collectors.toList());
                        
        System.err.println("recip = "+ recipients);
        String distributionId = mygroup.getDistributionName();
        if ((distributionId == null) || distributionId.isEmpty()) {
            distributionId = UUID.randomUUID().toString();
            mygroup.setDistributionName(distributionId);
            storeGroups();
        }
        DistributionId distribution = DistributionId.from(distributionId);
        List<UnidentifiedAccess> ua = new LinkedList();
        for (SignalServiceAddress address : mygroup.getMembers()) {
            System.err.println("addy = " + address.getUuid());
            contacts.stream()
                    .filter(c -> !(c.getUuid().equals(getMyUuid())))
                    .filter(c -> c.getUuid().equals(address.getUuid().get().toString()))
                    .findFirst()
                    .ifPresent(cnt -> {
                        try {
                            System.err.println("Adding to ua-list: "+cnt);
                            ua.add(ChannelUtils.getUnidentifiedAccess(cnt));
                        } catch (InvalidCertificateException ex) {
                            Logger.getLogger(WaveManager.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                        } catch (InvalidInputException ex) {
                            Logger.getLogger(WaveManager.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                        }
                    });

       //     ua.add(ChannelUtils.getUnidentifiedAccess(them));
        }
        sender.sendGroupDataMessage(distribution, recipients, ua, connected, ContentHint.DEFAULT, message, SignalServiceMessageSender.SenderKeyGroupEvents.EMPTY);

//        System.err.println("Sending to "+recipients+" and ua size = "+ua.size());
//        try {
//            List<SendMessageResult> res = sender.sendMessage(recipients,ua, false, message);
//            for (SendMessageResult smr :  res) {
//                System.err.println("RESULT = "+smr);
//            }
//        } catch (UntrustedIdentityException ex) {
//            Logger.getLogger(WaveManager.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        }

        return 0;
    }

    public SignalServiceAttachmentPointer uploadAttachment(Path p) throws IOException {
        InputStream inputStream = Files.newInputStream(p);
        int av = inputStream.available();
        SignalServiceAttachment.Builder builder = SignalServiceAttachment.newStreamBuilder().withStream(inputStream)
                .withContentType("image/*")
                .withLength(av)
                .withUploadTimestamp(System.currentTimeMillis());
        SignalServiceAttachmentStream stream = builder.build();
        SignalServiceAttachmentPointer ptr = sender.uploadAttachment(stream);
        return ptr;
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
        this.firstRun = true;
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
        this.accountManager = new AccountManager(getSignalServiceConfiguration(), credentialsProvider);
        waveStore.setCredentialsProvider((StaticCredentialsProvider) this.credentialsProvider);
        generateAndRegisterKeys();
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
    
    private void generateAndRegisterKeys() throws IOException {
        KeyUtil keyUtil = new KeyUtil(this);
        IdentityKeyPair identityKeypair = getWaveStore().getIdentityKeyPair();
        SignedPreKeyRecord signedPreKey = keyUtil.generateSignedPreKey(identityKeypair, true);
        waveStore.storeSignedPreKey(2, signedPreKey);
  
        List<PreKeyRecord> records = keyUtil.generatePreKeys(100);
        WAVELOG.log(Level.DEBUG," PM will register keys, ik = "+ identityKeypair+" with pubkey = "+identityKeypair.getPublicKey()+" and spk = "+signedPreKey+" and records = "+records);
        String response = accountManager.setPreKeys(identityKeypair.getPublicKey(), signedPreKey, records);
        WAVELOG.log(Level.DEBUG,"Response for generateAndRegisterKeys = "+response);
    }

    
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
        byte[] zkGroupServerPublicParams = new byte[0];
        try {
            zkGroupServerPublicParams = Base64.decode(ZKGROUP_SERVER_PUBLIC_PARAMS);
        } catch (IOException ex) {
            Logger.getLogger(WaveManager.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        SignalServiceConfiguration answer = new SignalServiceConfiguration(
                urls, cdnMap,
                new SignalContactDiscoveryUrl[]{new SignalContactDiscoveryUrl("https://api.directory.signal.org", trustStore)},
                backup, storageUrl, new LinkedList(),
                Optional.empty(), Optional.empty(), zkGroupServerPublicParams);

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
                false);
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
                                WAVELOG.log(Level.DEBUG,"[MessagePipe] envelope has datamessage");
                                SignalServiceDataMessage ssdm = content.getDataMessage().get();
                                processDataMessage(content.getSender(), ssdm);
                            }
                            if (content.getTypingMessage().isPresent()) {
                                WAVELOG.log(Level.DEBUG,"[MessagePipe] envelope has typingmessage");
                                SignalServiceTypingMessage sstm = content.getTypingMessage().get();
                                processTypingMessage(content.getSender(), sstm);
                            }
                            if (content.getReceiptMessage().isPresent()) {
                                WAVELOG.log(Level.DEBUG,"[MessagePipe] envelope has receiptmessage");
                                SignalServiceReceiptMessage ssrm = content.getReceiptMessage().get();
                                processReceiptMessage(content.getSender(), ssrm);
                            }
                            if (content.getSenderKeyDistributionMessage().isPresent()) {
                                WAVELOG.log(Level.DEBUG,"[MessagePipe] envelope has senderkeydistmessage");
                                SenderKeyDistributionMessage sskdm = content.getSenderKeyDistributionMessage().get();
                                processSenderKeyDistributionMessage(content.getSender(), content.getSenderDevice(), sskdm);
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
            WAVELOG.log(Level.DEBUG, "I need to decrypt " + sse + " with " + bl 
                    + " bytes, with id " + Objects.hashCode(sse));
            content = cipher.decrypt(sse);
            WAVELOG.log(Level.DEBUG, "I did to decrypt " + sse + " with id " + Objects.hashCode(sse));
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
                    throw new IllegalArgumentException("Unknown sender: " + senderId);
                }
            }
            SignalServiceAddress addy
                    = new SignalServiceAddress(UUID.fromString(tuuid), senderId);
            WAVELOG.log(Level.INFO, " decrypt will send nullmessage to " + addy);
            sender.sendNullMessage(addy, Optional.empty());
            if (sse.isUnidentifiedSender()) {
                Optional<byte[]> optGroupId = e.getGroupId();
                byte[] groupId = optGroupId.get();
                if (groupId.length == 32) {
                    System.err.println("GroupV2");
                    String encodedId = "__signal_group__v2__!" + Hex.toStringCondensed(groupId);
                    byte[] originalContent = sse.getContent();
                    int envelopeType = sse.getType();
//                    DecryptionErrorMessage decryptionErrorMessage = DecryptionErrorMessage.newBuilder().build();
  //                  sender.sendRetryReceipt(addy, Optional.empty(), Optional.of(groupId), decryptionErrorMessage);

                } else {
                    System.err.println("groupv1?");
                }
            }
            int bl2 = sse.getContent().length;
            WAVELOG.log(Level.DEBUG, " did send null message, we should have session now for " + bl2 + " bytes");
            SignalServiceCipher cipher2 = new SignalServiceCipher(signalServiceAddress,
                    waveStore, new LockImpl(), getCertificateValidator());
            content = cipher2.decrypt(sse);
        }
        WAVELOG.log(Level.DEBUG, " descrypt will return " + content);
        return content;
    }

    void processSyncMessage(SignalServiceAddress sender, SignalServiceSyncMessage sssm) throws InvalidMessageException, IOException {
        WAVELOG.log(Level.INFO, "[WM]Processing INCOMING SyncMessage: "+sssm);
        if (sssm.getContacts().isPresent()) {
            System.err.println("INSYNC Contacts!");
            ContactsMessage msg = sssm.getContacts().get();
            processContactsMessage(msg);
        }
        if (sssm.getSent().isPresent()) {
            System.err.println("INSYNC sent!");
            SentTranscriptMessage msg = sssm.getSent().get();
            processSentTranscriptMessage(sender, msg);
        }
        if (sssm.getGroups().isPresent()) {
            System.err.println("INSYNC groups!");
            WAVELOG.log(Level.DEBUG, "WaveManager has groupssyncmessage!");
            SignalServiceAttachment get = sssm.getGroups().get();
            throw new RuntimeException("we don't expect v1 groups anymore");
     //       processGroupsMessage(get);
        }
        if (sssm.getKeys().isPresent()) {
            System.err.println("INSYNC KEYS");
            KeysMessage keysMessage = sssm.getKeys().get();
            processKeysMessage(keysMessage);
        }
         WAVELOG.log(Level.INFO, "Processed INCOMING SyncMessage: "+sssm);

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
        if (ssdm.getProfileKey().isPresent()) {
            System.err.println("I NEED TO HANDLE A PROFILE with key ");
           // handleProfileKey(ssdm);
        }
        Message msg = new Message();
        Optional<List<SignalServiceAttachment>> attachmentsOpt = ssdm.getAttachments();
        if (attachmentsOpt.isPresent()) {
            List<SignalServiceAttachment> attList = attachmentsOpt.get();
            for (SignalServiceAttachment ssa : attList) {
                if (ssa.isStream()) {
                    SignalServiceAttachmentStream stream = ssa.asStream();
                    InputStream is = stream.getInputStream();
                    try {
                        int len = is.available();
                        byte[] b = new byte[len];
                        is.read(b);
                        Path tf = Files.createTempFile("att", "");
                        Files.write(tf, b);
                    } catch (IOException ex) {
                        Logger.getLogger(WaveManager.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                    }
                } else if (ssa.isPointer()) {
                    try {
                        SignalServiceAttachmentPointer pointer = ssa.asPointer();
                        Path output = Files.createTempFile("att", "");
                        InputStream is = receiver.retrieveAttachment(pointer, output.toFile(), Integer.MAX_VALUE);
                        Path tf2 = Files.createTempFile("a2t", "");
                        FileOutputStream fos = new FileOutputStream(tf2.toFile());
                        byte[] b = new byte[4096];
                        int len = is.read(b);
                        while (len > 0) {
                            fos.write(b, 0, len);
                            len = is.read(b);
                        }
                        fos.close();
                        msg.attachment(tf2);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                }
            }
        }
        
        Optional<SignalServiceGroupContext> groupContext = ssdm.getGroupContext();
        if (groupContext.isPresent()) {
            SignalServiceGroupContext groupCon = groupContext.get();
            GroupMasterKey masterKey = groupCon.getGroupV2().get().getMasterKey();
            System.err.println("GROUPMESSAGE for masterKey "+masterKey);
            System.err.println("serial version of masterkey = "+
            Arrays.toString(masterKey.serialize()));
            Group g = getGroupByMasterKey(masterKey.serialize());
            if (g == null) {
                System.err.println("ERROR! message for unknown group");
                Thread.dumpStack();
                return;
            }
            System.err.println("Got message for group "+g.getName());
            msg.setGroup(g);
        }
        if (this.messageListener != null) {
            String uuid = sender.getUuid().get().toString();
            String content = ssdm.getBody().orElse(null);
            if (content != null) {
                msg.senderUuid(uuid).content(content).timestamp(ssdm.getTimestamp());
                this.messageListener.gotMessage(msg);
            }
        }
    }
    void processDataMessage(SignalServiceAddress sender, SignalServiceDataMessage ssdm, 
            SignalServiceAddress receiver) {
        processDataMessage(sender, ssdm, receiver, false);
    }
    
    /**
     * Pass an incoming data message to the client.
     * @param sender
     * @param ssdm
     * @param receiver
     * @param mySync true in case this message is sent from one of our other devices. This 
     * is important since in that case, the client wants to show it in the channel of the receiver
     */
    void processDataMessage(SignalServiceAddress sender, SignalServiceDataMessage ssdm, 
            SignalServiceAddress receiver, boolean mySync) {
        WAVELOG.log(Level.INFO, "Process datamessage");
        if (this.messageListener != null) {
            String uuid = sender.getUuid().get().toString();
            String recuuid = receiver.getUuid().get().toString();
            String content = ssdm.getBody().orElse(null);
            if (content != null) {
                Message message = new Message();
                message.senderUuid(uuid).content(content).timestamp(ssdm.getTimestamp())
                        .receiverUuid(recuuid).mySync(mySync);
                this.messageListener.gotMessage(message);

//                this.messageListener.gotMessage(uuid, content, ssdm.getTimestamp(), recuuid);
            }
        }
    }

    private void processSenderKeyDistributionMessage(SignalServiceAddress senderAddress, int deviceId, SenderKeyDistributionMessage msg) {
        SignalProtocolAddress addy = new SignalProtocolAddress(senderAddress.getIdentifier(), deviceId);
        System.err.println("WM process senderkeydistributionmessage for addy = "+addy+", senderadd = "+senderAddress+", sai = "+senderAddress.getIdentifier()+", devid = "+deviceId);
        sender.processSenderKeyDistributionMessage(addy, msg);
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
            processDataMessage(sender, msg.getMessage(), msg.getDestination().get(), true);
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

            InputStream ais = AttachmentCipherInputStream.createForAttachment(output.toFile(), pointer.getSize().orElse(0), pointer.getKey(), pointer.getDigest().get());
            Path attPath = Files.createTempFile("att", "bin");
            File attFile = attPath.toFile();
            Files.copy(ais, attPath, StandardCopyOption.REPLACE_EXISTING);

            InputStream ois = new FileInputStream(attFile);
            DeviceContactsInputStream is = new DeviceContactsInputStream(ois);
            DeviceContact dc = is.read();
            System.err.println("Don't clear contacts at this point");
        //    contacts.clear();
            int cnt = 0;
            int pcnt = 0;
            while (dc != null) {
                Contact contact = new Contact(dc.getName().orElse("anonymous"),
                        dc.getAddress().getUuid().get().toString(),
                        dc.getAddress().getNumber().orElse("123"));
                ProfileKey profileKey = dc.getProfileKey().orElse(null);
                if (profileKey != null) {
                    contact.setProfileKey(profileKey.serialize());
                    UUID uuid = dc.getAddress().getUuid().get();
                    Future<SignalServiceProfile> fut = accountManager.getSocket().retrieveVersionedProfile(uuid, profileKey, Optional.empty());
                    SignalServiceProfile ssp = fut.get(10, TimeUnit.SECONDS);
                    ProfileCipher pc = new ProfileCipher(profileKey);
                    byte[] decryptName = pc.decryptName(Base64.decode(ssp.getName()));
                    String realName = new String(decryptName);
                    if (contact.getName().isEmpty()) {
                        System.err.println("Replace "+contact.getNr()+" with "+realName);
                        contact.setName(realName);
                    }
                    System.err.println("GOT PROFILE: "+ssp+" with key "+Arrays.toString(profileKey.serialize()));
                    System.err.println("profile["+pcnt+ "]= "+ssp.getName()+", "+realName+", "+ssp.getAvatar());
                    pcnt++;
                }
                System.err.println("contact[cnt] = "+contact.getName());
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
                System.err.println("contact uuid = "+contact.getUuid()+" and I am "+waveStore.getMyUuid());
                if (contact.getUuid().equals(waveStore.getMyUuid())) {
                    this.me = contact;
                    ChannelUtils.setMe(contact);
                }
                if (!contacts.contains(contact)) {
                    System.err.println("New contact: "+contact.getName());
                    Optional<Contact> oldOne = getContactByUuid(contact.getUuid());
                    if (oldOne.isPresent()) {
                        Contact old = oldOne.get();
                        contacts.remove(old);
                    }
                    contacts.add(contact);
                } else {
                    System.err.println("We already had this contact: "+contact.getName());
                }
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
        System.err.println("[WM] contacts is now " + Objects.hash(contacts)+" = "+ contacts +" with size = " + contacts.size() );
    }

    private List<Contact> readContacts() throws IOException {
        System.err.println("[WM] READCONTACTS");
        List<Contact> answer = new LinkedList<>();
        Path path = SIGNAL_FX_CONTACTS_DIR.toPath().resolve("contactlist");
        if (Files.exists(path)) {
            String line = Files.readString(path);
            answer = Contact.fromJson(line);
        }
        System.err.println("WM did read: "+answer+"\n with "+answer.size()+" elements");
        return answer;
    }
    
    private void storeContacts() throws IOException {
        WAVELOG.log(Level.INFO, "store contacts");
        Path path = SIGNAL_FX_CONTACTS_DIR.toPath().resolve("contactlist");
        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        Files.deleteIfExists(path);
        String json = Contact.toJson(contacts);
        Files.writeString(path, json, StandardOpenOption.CREATE);
        contactStorageDirty = true;
    }
    
    private List<Group> readGroups() throws IOException {
        System.err.println("[WM] READGROUPS");
        List<Group> answer = new LinkedList<>();
        Path path = SIGNAL_FX_CONTACTS_DIR.toPath().resolve("grouplist");
        if (Files.exists(path)) {
            String line = Files.readString(path);
            answer = Group.fromJson(line);
        }
        System.err.println("WM did read: "+answer+"\n with "+answer.size()+" elements");
        return answer;
    }
    
    private void storeGroups() throws IOException {
        WAVELOG.log(Level.INFO, "store groups");
        Path path = SIGNAL_FX_CONTACTS_DIR.toPath().resolve("grouplist");
        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        Files.deleteIfExists(path);
        String json = Group.toJson(groups);
        Files.writeString(path, json, StandardOpenOption.CREATE);
        groupStorageDirty = true;
    }

    // This seems to return groupv1 groups only, hence not used atm
//    private void processGroupsMessage(SignalServiceAttachment ssa) throws IOException {
//        System.err.println("Processing groupsMessage, pointer? "+ssa.isPointer()
//        +", stream? "+ssa.isStream());
//        SignalServiceAttachmentPointer pointer = ssa.asPointer();
//        Path output = Files.createTempFile("pre", "post");
//            Path mattPath = Files.createTempFile("r", "bin");
//
//        try {
//            InputStream is = receiver.retrieveAttachment(pointer, output.toFile(), MAX_FILE_STORAGE);
//            File attFile = mattPath.toFile();
//            Files.copy(is, mattPath, StandardCopyOption.REPLACE_EXISTING);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            throw new IOException("Can't retrieve attachment", ex);
//        }
//        System.err.println("MATTPATH = "+mattPath+", OUT = "+output);
//        try {
//            byte[] digest = pointer.getDigest().get();
//            InputStream ais = AttachmentCipherInputStream.createForAttachment(output.toFile(), pointer.getSize().orElse(0), pointer.getKey(), pointer.getDigest().get());
//                      
//            Path attPath = Files.createTempFile("agg", "bin");
//            File attFile = attPath.toFile();
//            Files.copy(ais, attPath, StandardCopyOption.REPLACE_EXISTING);
//
//            InputStream ois = new FileInputStream(attFile);
//            DeviceGroupsInputStream is = new DeviceGroupsInputStream(ois);
//            DeviceGroup dg = is.read();
//            groups.clear();
////            while (dg != null) {
////                try {
////                Group g = new Group(dg.getName().orElse("anonymous group"), dg.getId(), dg.getMembers());
////                System.err.println("Adding group with name "+g.getName()+" and members "+g.getMembers()+" and mkb "+Arrays.asList(g.getMasterKeyBytes()));
////                groups.add(g);
////                } catch (Exception e) {
////                    e.printStackTrace();
////                }
////                dg = is.read();
////            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//            
//    }

    private void processKeysMessage(KeysMessage keysMessage) {
        this.storageKey = keysMessage.getStorageService().get();
        syncStorage();
        try {
            byte[] senderCertificate = this.accountManager.getSenderCertificate();
            ChannelUtils.setSenderCertificate(senderCertificate);
        } catch (IOException ex) {
            Logger.getLogger(WaveManager.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
    }

    private void syncStorage() {
        System.err.println(Thread.currentThread() + " We got a storage key, use this thread to read manifest");
        try {
            Optional<SignalStorageManifest> storageManifest = accountManager.getStorageManifest(storageKey);
            SignalStorageManifest ssm = storageManifest.get();
            System.err.println("IDS = " + storageManifest.get().getStorageIds());
            Optional<StorageId> accountStorageId = ssm.getAccountStorageId();
            ssm.getStorageIds().forEach(si -> {
                System.err.println("SI = " + si.hashCode() + " with type " + si.getType() + " and bl = " + si.getRaw().length + " and si = "
                        + Arrays.toString(si.getRaw()));
            });
            List<SignalStorageRecord> records = accountManager.readStorageRecords(storageKey, ssm.getStorageIds());
            for (SignalStorageRecord record: records) {
                System.err.println("Record "+record+" with type "+record.getType());
try {
                if (record.getGroupV2().isPresent()) {
                    SignalGroupV2Record gr = record.getGroupV2().get();
                    System.err.println("GROUP: "+gr.getId()+" with master "+Arrays.toString(gr.getMasterKeyBytes()));
                    GroupMasterKey groupMasterKey = gr.getMasterKeyOrThrow();
                    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
                    int days = (int)LocalDate.now().toEpochDay();

                    GroupsV2AuthorizationString authorization = accountManager.getGroupsV2Api().getGroupsV2AuthorizationString(
                            this.credentialsProvider.getUuid(), 
                            days, groupSecretParams,groupCredentials.get(days));
                    DecryptedGroup dgroup = accountManager.getGroupsV2Api().getGroup(groupSecretParams, authorization);
                    String title = dgroup.getTitle();
                    GroupIdentifier groupIdentifier = groupSecretParams.getPublicParams().getGroupIdentifier();

                    System.err.println("GroupTitle = "+title+", id = "+Arrays.toString(groupIdentifier.serialize()));
                    System.err.println("#members = "+  dgroup.getMembersCount());
                    System.err.println("members = "+dgroup.getMembersList());
                    List<SignalServiceAddress> memberList = new LinkedList<>();
                    for (DecryptedMember member:  dgroup.getMembersList()) {
                        UUID uuid = UuidUtil.fromByteString(member.getUuid());
                        SignalServiceAddress add = new SignalServiceAddress(Optional.of(uuid), Optional.empty());
                        memberList.add(add);
                    }
                    Group group = new Group(title, groupMasterKey, groupIdentifier, memberList);
                    Optional<Group> exists = groups.stream().filter(g -> 
                            Arrays.equals(g.getMasterKey().serialize(), group.getMasterKey().serialize()))
                            .findFirst();
                    if (exists.isPresent()) {
                        System.err.println("GROUP "+title+" exists!");
                        exists.get().update(group);
                    } else {
                        System.err.println("GROUP "+title+" is new!");
                        groups.add(group);
                        groupMap.put(title, group);
                    }
                }
} catch (Exception e) {
System.err.println("ERR!!! " + e);
}
            }
            storeGroups();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (InvalidKeyException ex) {
            ex.printStackTrace();
        }
    }
    
    private Group getGroupByMasterKey(byte[] keybytes) {
        Group answer = groups.stream().filter(g -> Arrays.equals(g.getMasterKey().serialize(), keybytes))
                .findFirst().orElse(null);
        return answer;
    }

    private void fatalError(String authError) {
        Thread.dumpStack();
        if (this.fatalErrorSupplier != null) {
            if (fatalErrorSupplier.get()) {
                System.err.println("LETS REMOVE THIS!");
                try {
                    waveStore.moveOldStore();
                    postInit();
                    restartRequestConsumer.accept("Configuration moved");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } else {
                System.err.println("LETS KEEP THIS!");
            }
        }
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
            WaveManager.this.messagePipe.shutdown();
            WaveManager.this.unidentifiedMessagePipe.shutdown();
            WaveManager.this.fatalError("AuthError");
        }

        @Override
        public boolean onGenericFailure(Response response, Throwable throwable) {
            System.err.println("Generic failure, response = "+response);
            throwable.printStackTrace();
            return false;
        }

    }
}
