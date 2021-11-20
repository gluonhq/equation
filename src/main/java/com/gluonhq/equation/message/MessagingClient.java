/*
 * Copyright (C) 2021 Gluon
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.gluonhq.equation.message;

import com.gluonhq.equation.model.Message;
import java.util.List;

/**
 * Callback that needs to be implemented by clients wanting to receive notifications about
 * incoming data messages.
 */
public interface MessagingClient {

    /**
     * Called when a message for the current user is available
     * @param senderUuid the sender of the message
     * @param content the (text) content of the message
     * @param timestamp the timestamp that the server received the message
     */
    void gotMessage(String senderUuid, String content, long timestamp);

    /**
     * Called when a message for the the specified receiver is available.
     * This happens in case the current user uses another device to send a 
     * message to a receiver, and we want to show that message on this device.
     * 
     * @param senderUuid the sender of the message
     * @param content the (text) content of the message
     * @param timestamp the timestamp that the server received the message
     * @param receiverUuid the receiver for this message
     */

    void gotMessage(String senderUuid, String content, long timestamp, String receiverUuid);

    /**
     * Called when a message is available
     * @param msg the incoming message. This can be either for the current user,
     * or it can be a sync message from this user, to a different receiver.
     */
    default void gotMessage(Message msg) {}

    /**
     * Called when a sender is starting to type or stopping to type
     * @param senderUuid the uuid of the sender
     * @param typingStarted true if the sender is starting to type, false otherwise
     * @param typingStopped true if the sender is stopping to type, false otherwise
     */
    public void gotTypingAction(String senderUuid, boolean typingStarted, boolean typingStopped);

    /**
     * Called when a sender did an action on a message we sent.
     * @param senderUuid the sender of the action (the recipient of our message)
     * @param type the type of the receipt action
     * @param timestamps A list of timestamps identifying the messages that this action applies to
     */
    public void gotReceiptMessage(String senderUuid, int type, List<Long> timestamps);

}
