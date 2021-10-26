/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.internal;

import java.io.InputStream;
import org.whispersystems.signalservice.api.push.TrustStore;

public class TrustStoreImpl implements TrustStore {

    @Override
    public InputStream getKeyStoreInputStream() {
        return TrustStoreImpl.class.getResourceAsStream("/whisper.store");
    }

    @Override
    public String getKeyStorePassword() {
        return "whisper";
    }
    
}
