/*
 * Copyright (C) 2021, 2022 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class Group {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String name;
    
    @JsonIgnore
    private GroupMasterKey masterKey;
    
    @JsonIgnore
    private GroupIdentifier groupIdentifier;
    private byte[] mkb;
    private byte[] gib;
    private String distributionName;
    
    public Group() {}
    
    @JsonIgnore
    public GroupIdentifier getIdentifier() {
        return groupIdentifier;
    }
    
    @JsonIgnore
    public void setIdentifier(GroupIdentifier identifier) {
        this.groupIdentifier = identifier;
    }

    public String getDistributionName() {
        return distributionName;
    }

    public void setDistributionName(String distributionName) {
        this.distributionName = distributionName;
    }

    public byte[] getMkb() {
        return mkb;
    }

    public void setMkb(byte[] mkb) {
        this.mkb = mkb;
    }
    
    public byte[] getGib() {
        return gib;
    }

    public void setGib(byte[] gib) {
        this.gib = gib;
    }
    
    @JsonIgnore
    private List<SignalServiceAddress> members = new LinkedList<>();
    
    public Group(String name, GroupMasterKey masterKey, GroupIdentifier identifier, List<SignalServiceAddress> members) {
        this.name = name;
        this.masterKey = masterKey;
        this.groupIdentifier = identifier;
        this.mkb = masterKey.serialize();
        this.gib = identifier.serialize();
        this.members = members;
    }
    
    public String getName() {
        return name;
    }

    public GroupMasterKey getMasterKey() {
        if (masterKey == null) try {
            masterKey = new GroupMasterKey(mkb);
        } catch (InvalidInputException ex) {
            ex.printStackTrace();
        }
        return masterKey;
    }
    
    public GroupIdentifier getGroupIdentifier() {
        if (groupIdentifier == null) {
            try {
                groupIdentifier = new GroupIdentifier(gib);
            } catch (InvalidInputException ex) {
                ex.printStackTrace();
            }
        }
        return groupIdentifier;
    }

    public List<SignalServiceAddress> getMembers() {
        return members;
    }
    
    /** 
     * Update the internals of the group with this masterkey to the values of the
     * provided group.
     * @param next 
     */
    public void update(Group next) {
        this.members.clear();
        this.members.addAll(next.getMembers());
    }
    
    public static String toJson(List<Group> list) throws JsonProcessingException {
        return objectMapper.writeValueAsString(list);
    }
    
    public static List<Group> fromJson(String s) throws JsonProcessingException {
        List<Group> answer = objectMapper.readValue(s, new TypeReference<LinkedList<Group>>() {});
        System.err.println("I will return answer from mapper: "+answer);
        return answer;
    }
}
