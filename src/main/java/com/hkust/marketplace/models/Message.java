package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Domain model for Message as defined in the UML Class Diagram.
 */
public class Message {
    private String id;
    private String senderUsername;
    private String text;
    private LocalDateTime sentDate;

    public Message() {
        this.sentDate = LocalDateTime.now();
    }

    public Message(String senderUsername, String text) {
        this();
        this.senderUsername = senderUsername;
        this.text = text;
    }

    public Message(String id, String senderUsername, String text) {
        this(senderUsername, text);
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public LocalDateTime getSentDate() {
        return sentDate;
    }

    public void setSentDate(LocalDateTime sentDate) {
        this.sentDate = sentDate;
    }
}
