/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.model;

public class Contact {

    private final String name;
    private final String uuid;
    private final String nr;
    private String avatarPath;

    public Contact(String name, String uuid, String nr) {
        this.name = name;
        this.uuid = uuid;
        this.nr = nr;
    }
    
    public String getName() {
        return name;
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
    
    
    public void setAvatarPath(String b) {
        this.avatarPath = b;
    }
}
