/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.model;

import java.util.List;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class Group {

    private final String name;
    private final GroupMasterKey masterKey;
    private final List<SignalServiceAddress> members;

    public Group(String name, GroupMasterKey masterKey, List<SignalServiceAddress> members) {
        this.name = name;
        this.masterKey = masterKey;
        this.members = members;
    }
    
    public String getName() {
        return name;
    }

    public GroupMasterKey getMasterKey() {
        return masterKey;
    }

    public List<SignalServiceAddress> getMembers() {
        return members;
    }

}
