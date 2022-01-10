/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gluonhq.equation.model;

import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

/**
 *
 * @author johan
 */
public class Recipient {
    
  public static Recipient externalHighTrustPush(SignalServiceAddress signalServiceAddress){
    return externalPush(signalServiceAddress.getAci(), signalServiceAddress.getNumber().orElse(null), true);
  }
  public static  Recipient externalPush(SignalServiceAddress signalServiceAddress) {
    return externalPush(signalServiceAddress.getAci(), signalServiceAddress.getNumber().orElse(null), false);
  }
  
  public static Recipient externalPush(ACI aci, String e164, boolean highTrust) {
    if (UuidUtil.UNKNOWN_UUID.equals(aci)) {
      throw new AssertionError();
    }
//
//    RecipientDatabase db          = SignalDatabase.recipients();
//    RecipientId       recipientId = db.getAndPossiblyMerge(aci, e164, highTrust);
//
//    Recipient resolved = resolved(recipientId);
//
//    if (highTrust && !resolved.isRegistered() && aci != null) {
//      Log.w(TAG, "External high-trust push was locally marked unregistered. Marking as registered.");
//      db.markRegistered(recipientId, aci);
//    } else if (highTrust && !resolved.isRegistered()) {
//      Log.w(TAG, "External high-trust push was locally marked unregistered, but we don't have a UUID, so we can't do anything.", new Throwable());
//    }

//    return resolved;
    return null;
  }

}
