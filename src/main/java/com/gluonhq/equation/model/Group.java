/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.model;

import java.util.List;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class Group {

    private final String name;
    private final byte[] id;
    private final List<SignalServiceAddress> members;

    public Group(String name, byte[] id, List<SignalServiceAddress> members) {
        this.name = name;
        this.id = id;
        this.members = members;

    }
    
    public String getName() {
        return name;
    }

    public byte[] getId() {
        return id;
    }

    public List<SignalServiceAddress> getMembers() {
        return members;
    }

}
