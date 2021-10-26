/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.internal;

import org.whispersystems.signalservice.api.SignalSessionLock;

public class LockImpl implements SignalSessionLock {

    private static final Lock singleton = () -> {};
    
    @Override
    public Lock acquire() {
        return singleton;
    }
    
}
