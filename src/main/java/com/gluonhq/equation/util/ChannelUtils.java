/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gluonhq.equation.util;

import com.gluonhq.equation.model.Contact;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

/**
 *
 * @author johan
 */
public class ChannelUtils {

    private static byte[] senderCertificate;
    private static Contact me;
    
    public static void setMe(Contact c) {
        me = c;
    }
    
    public static ProfileKey getMyProfileKey() {
        return null;
    }

    public static byte[] getSenderCertificate() {
        return senderCertificate;
    }

    public static void setSenderCertificate(byte[] v) {
        senderCertificate = v;
    }

    public static UnidentifiedAccessPair getUnidentifiedAccessPair(Contact c) throws InvalidCertificateException, InvalidInputException {
        return new UnidentifiedAccessPair(getMyUnidentifiedAccess(),getUnidentifiedAccess(c) );
    }
    
    public static UnidentifiedAccess getMyUnidentifiedAccess() throws InvalidCertificateException, InvalidInputException {
        byte[] ourUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(new ProfileKey(me.getProfileKey()));
        byte[] ourUnidentifiedAccessCertificate = senderCertificate;
        UnidentifiedAccess answer = new UnidentifiedAccess(ourUnidentifiedAccessKey, ourUnidentifiedAccessCertificate);
        return answer;
    }

    public static UnidentifiedAccess getUnidentifiedAccess(Contact c) throws InvalidCertificateException, InvalidInputException {
        byte[] theirUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(new ProfileKey(c.getProfileKey()));
        byte[] ourUnidentifiedAccessCertificate = senderCertificate;
        UnidentifiedAccess answer = new UnidentifiedAccess(theirUnidentifiedAccessKey, ourUnidentifiedAccessCertificate);
        return answer;
    }
    
    public List<UnidentifiedAccessPair> getUnidentifiedAccessPairs(List<SignalServiceAddress> address, List<Contact> contacts) {
        return null;
    }

}
