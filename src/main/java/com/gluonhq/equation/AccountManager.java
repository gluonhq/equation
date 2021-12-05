package com.gluonhq.equation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageModels;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.RemoteConfigResponse;

/**
 * This class contains functionality that is also on the same class in the AccountManager
 * in the Android implementation.
 * 
 * @author johan
 */
public class AccountManager {

    private static final String SIGNAL_USER_AGENT = "Signal-Desktop/5.14.0 Linux";
    private final PushServiceSocket socket;
    private final GroupsV2Operations groupsV2Operations;
    SignalServiceAccountManager ssam;

    public AccountManager(SignalServiceConfiguration config, 
            CredentialsProvider credentialsProvider) {
        this.groupsV2Operations = new GroupsV2Operations(ClientZkOperations.create(config));
        ssam = new SignalServiceAccountManager(config,
        credentialsProvider, SIGNAL_USER_AGENT, groupsV2Operations, true);
        socket = new PushServiceSocket(config, credentialsProvider, SIGNAL_USER_AGENT, null, true);
    }

    public String setPreKeys(IdentityKey identityKey, SignedPreKeyRecord signedPreKey, List<PreKeyRecord> oneTimePreKeys)
            throws IOException {
        return this.socket.registerPreKeys(identityKey, signedPreKey, oneTimePreKeys);
    }

    public GroupsV2Api getGroupsV2Api() {
         return new GroupsV2Api(socket, groupsV2Operations);
    }

    public void getRemoteConfig() throws IOException {
        RemoteConfigResponse remoteConfig = this.socket.getRemoteConfig();
        System.err.println("Got remoteconfig: "+remoteConfig);
    }

    public Optional<SignalStorageManifest> getStorageManifest(StorageKey storageKey) throws IOException {
        try {
            String authToken = this.socket.getStorageAuth();
            return Optional.of(this.socket.getSignalStorageManifest(authToken, storageKey));
        } catch (InvalidKeyException e) {
            System.err.println("Invalid key! ");
            e.printStackTrace();
            return Optional.empty();
        }
    }
  public List<SignalStorageRecord> readStorageRecords(StorageKey storageKey, List<StorageId> storageKeys) throws IOException, InvalidKeyException {
    return ssam.readStorageRecords(storageKey, storageKeys);
  }


    public PushServiceSocket getSocket() {
        return this.socket;
    }
}
