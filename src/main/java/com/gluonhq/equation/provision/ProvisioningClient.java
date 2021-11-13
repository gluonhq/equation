/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.provision;

/**
 * Implementations of this interface deal with the user-facing flow of provisioning
 * a new device with an existing account.
 */
public interface ProvisioningClient {
    
    /**
     * The ProvisioningManager has a url for us to display
     * @param url a link to an address that, if called, notifies the other side that
     * it has been invoked by us.
     */
    public void gotProvisioningUrl(String url);
    
    /**
     * A provisioning message is available for this client.
     * It is recommended for the client to now invoke ProvisioningManager.createAccount()
     * @param number the number that we are binding to.
     */
    public void gotProvisionMessage(String number);
    
    /**
     * In case an error occurs during provisioning, we will be notified about it.
     * @param msg 
     */
    public default void gotProvisioningError(String msg) {
    }

}
