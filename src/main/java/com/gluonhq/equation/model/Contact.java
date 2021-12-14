/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class Contact {

    private String name;
    private String uuid;
    private String nr;
    private String avatarPath;
    private byte[] profileKey = new byte[0]; // avoid null
    private boolean active;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Contact() {}
    
    public Contact(String name, String uuid, String nr) {
        this.name = name;
        this.uuid = uuid;
        this.nr = nr;
    }

    public String getName() {
        return name;
    }
    
    public void setName(String v) {
        this.name = v;
    }

    public String getUuid() {
        return uuid;
    }

    public String getNr() {
        return nr;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public boolean isActive() {
        boolean isActive = ((this.profileKey != null) && (this.profileKey.length > 0));
        return isActive;
    }

    public void setProfileKey(byte[] b) {
        this.profileKey = b;
    }
    
    public byte[] getProfileKey() {
        return this.profileKey;
    }
    
    public String getProfileKeyString() {
        return profileKey == null? "" : Base64.getEncoder().encodeToString(profileKey);
    }
    
    public void setProfileKeyString(String pks) {
        this.profileKey = Base64.getDecoder().decode(pks);
    }

    public void setAvatarPath(String b) {
        this.avatarPath = b;
    }
    
    public String toJson() throws JsonProcessingException {
        return objectMapper.writeValueAsString(this);
    }
    
    public static String toJson(List<Contact> list) throws JsonProcessingException {
        return objectMapper.writeValueAsString(list);
    }
    
    public static List<Contact> fromJson(String s) throws JsonProcessingException {
        List<Contact> answer = objectMapper.readValue(s, new TypeReference<LinkedList<Contact>>() {});
        System.err.println("I will return answer from mapper: "+answer);
        return answer;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + Objects.hashCode(this.name);
        hash = 73 * hash + Objects.hashCode(this.uuid);
        hash = 73 * hash + Objects.hashCode(this.nr);
        hash = 73 * hash + Objects.hashCode(this.avatarPath);
        hash = 73 * hash + Arrays.hashCode(this.profileKey);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Contact other = (Contact) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        System.err.println("CONTACTEQUALS check, uuid equals for "+this.uuid);
        if (!Objects.equals(this.nr, other.nr)) {
            return false;
        }
        System.err.println("CONTACTEQUALS check, nr equals for "+this.nr);

        if (!Objects.equals(this.avatarPath, other.avatarPath)) {
            return false;
        }
                System.err.println("CONTACTEQUALS check, avatar equals for "+this.avatarPath);

        if (!Arrays.equals(this.profileKey, other.profileKey)) {
            System.err.println("different key: this = "+this.profileKey+" and other = "+other.profileKey);
            return false;
        }
        System.err.println("CONTACTEQUALS will true");
        return true;
    }
    
    
}
