/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.internal;

import com.gluonhq.equation.WaveManager;
import com.gluonhq.equation.WaveStore;
import java.util.LinkedList;
import java.util.List;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Medium;

/**
 * This class is responsible for generating and storing preKeys and SignedPreKeys.
 * Inspired by the signalapp/Signal-Android application
 */
public class KeyUtil {

    private final WaveManager waveManager;

    public KeyUtil (WaveManager manager) {
        this.waveManager = manager;
    }
    // TODO inject waveStore
    public synchronized List<PreKeyRecord> generatePreKeys(int cnt) {
        WaveStore waveStore = waveManager.getWaveStore();
        List<PreKeyRecord> records = new LinkedList<>();
        int preKeyIdOffset = 1; // TODO 

        for (int i = 0; i < cnt; i++) {
            int preKeyId = (preKeyIdOffset + i) % Medium.MAX_VALUE;
            ECKeyPair keyPair = Curve.generateKeyPair();
            PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

            waveStore.storePreKey(preKeyId, record);
            records.add(record);
        }
        return records;
    }

    private static int activeSignedPreKeyId = 1;
    private static int nextSignedPreKeyId = 2;

    static int getNextSignedPreKeyId() {
        return nextSignedPreKeyId;
    }

    static void setNextSignedPreKeyId(int v) {
        nextSignedPreKeyId = v;
    }

    static void setActiveSignedPreKeyId(int v) {
        activeSignedPreKeyId = v;
    }

    public synchronized SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair, boolean active) {
        try {
            WaveStore waveStore = waveManager.getWaveStore();
            int signedPreKeyId = getNextSignedPreKeyId();
            ECKeyPair keyPair = Curve.generateKeyPair();
            byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
            SignedPreKeyRecord record = new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);

            waveStore.storeSignedPreKey(signedPreKeyId, record);
            setNextSignedPreKeyId((signedPreKeyId + 1) % Medium.MAX_VALUE);

            if (active) {
                setActiveSignedPreKeyId(signedPreKeyId);
            }

            return record;
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }
 
}
