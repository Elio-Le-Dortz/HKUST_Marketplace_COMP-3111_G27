package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Domain model for Message as defined in the UML Class Diagram.
 * Supports free-text chat and structured formal offer attachments (US-B06).
 */
public class Message {
    private String id;
    private String senderUsername;
    private String text;
    private LocalDateTime sentDate;
    private String offerId;

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

    public Message(String id, String senderUsername, String text, LocalDateTime sentDate) {
        this.id = id;
        this.senderUsername = senderUsername;
        this.text = text;
        this.sentDate = sentDate != null ? sentDate : LocalDateTime.now();
    }

    public Message(String id, String senderUsername, String text, LocalDateTime sentDate, String offerId) {
        this(id, senderUsername, text, sentDate);
        this.offerId = offerId;
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

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    /**
     * Checks if this message represents a formal offer attachment.
     *
     * @return {@code true} if an offer ID is linked or text starts with formal offer prefix
     */
    public boolean isFormalOffer() {
        return offerId != null || (text != null && text.startsWith("[FORMAL OFFER]"));
    }
}
