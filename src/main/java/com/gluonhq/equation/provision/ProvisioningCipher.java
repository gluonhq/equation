/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.provision;

import com.gluonhq.equation.WaveManager;
import com.gluonhq.equation.internal.DeviceMessages.ProvisionEnvelope;
import com.gluonhq.equation.internal.DeviceMessages.ProvisionMessage;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.kdf.HKDFv3;

/**
 * This class deals with decryption of provisioningmessages.
 * This is specific to clients that allow to be registered as linked
 * devices, hence it is not (yet) in the general libraries.
 * 
 * @author johan
 */
public class ProvisioningCipher {
    
    private final ECKeyPair ourKeyPair;
    private final WaveManager waveManager;

    public ProvisioningCipher(WaveManager w) {
        this.waveManager = w;
        this.ourKeyPair = Curve.generateKeyPair();
    }

    public ECKeyPair getOurKeyPair() {
        return this.ourKeyPair;
    }
    
    /**
     * Decrypt an incoming provisioningEnvelope into a message.
     * Reverse-engineered from the Signal-Desktop typescript code.
     * @param envelope the envelope containing the provisioning message
     * @return the decrypted provisioning message
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws java.security.InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidProtocolBufferException 
     */
    public ProvisionMessage decrypt(ProvisionEnvelope envelope) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, java.security.InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidProtocolBufferException {
        ByteString masterEphemeral = envelope.getPublicKey();
        ECPublicKey ecPub = Curve.decodePoint(masterEphemeral.toByteArray(),0);
     
        ByteString message = envelope.getBody();
        if (message.byteAt(0) != 1 )  {
            throw new RuntimeException("First byte should be 1 in provisioningenvelope");
        }
        int mSize = message.size();
        ByteString iv = message.substring(1, 17);
        ByteString mach = message.substring(mSize-32, mSize);
        ByteString versionAndivAndCipherText = message.substring(0, mSize -32);
        ByteString cipherText = message.substring(17, mSize -32);

        byte[] ecRes = Curve.calculateAgreement(ecPub, ourKeyPair.getPrivateKey());
        byte[] totkeys = new HKDFv3().deriveSecrets(ecRes, "TextSecure Provisioning Message".getBytes(), 64);
        byte[][] keys = new byte[2][32];
        System.arraycopy(totkeys, 0, keys[0], 0, 32);
        System.arraycopy(totkeys, 32, keys[1], 0, 32);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keys[1], "HmacSHA256"));
        byte[] calcMac = mac.doFinal(versionAndivAndCipherText.toByteArray());
        boolean macMatch = Arrays.equals(calcMac, mach.toByteArray());
  
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.toByteArray());
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys[0], "AES"), ivSpec);
        byte[] doFinal = cipher.doFinal(cipherText.toByteArray());
        ProvisionMessage pm = ProvisionMessage.parseFrom(doFinal);
        ECPrivateKey privateKey = Curve.decodePrivatePoint(pm.getIdentityKeyPrivate().toByteArray());
        ECPublicKey publicKey = Curve.createPublicKeyFromPrivateKey(pm.getIdentityKeyPrivate().toByteArray());
     
        ECKeyPair keyPair = new ECKeyPair(publicKey, privateKey);
        IdentityKey identityKey = new IdentityKey(publicKey);
        IdentityKeyPair ikp = new IdentityKeyPair(identityKey, privateKey);
        waveManager.getWaveStore().setIdentityKeyPair(ikp);
        return pm;
    }
    
    
}
