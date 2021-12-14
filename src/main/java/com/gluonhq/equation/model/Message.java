/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.model;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author johan
 */
public class Message {

    String senderUuid;
    String content;
    long timestamp;
    String receiverUuid;
    Group group;

    List<Path> attachment = new LinkedList<>();
    
    public Message () {
    }

    public void setGroup(Group g) {
        this.group = g;
    }

    public Group getGroup() {
        return this.group;
    }

    public Message senderUuid(String v) {
        this.senderUuid = v;
        return this;
    }
    
    public Message content(String v) {
        this.content = v;
        return this;
    }
    
    public Message receiverUuid(String v) {
        this.receiverUuid = v;
        return this;
    }
    
    public Message timestamp(long v) {
        this.timestamp = v;
        return this;
    }
    
    public Message attachment(Path p) {
        attachment.add(p);
        return this;
    }
    
    public String getSenderUuid() {
        return senderUuid;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getReceiverUuid() {
        return receiverUuid;
    }

    public List<Path> getAttachment() {
        return attachment;
    }
    
}
