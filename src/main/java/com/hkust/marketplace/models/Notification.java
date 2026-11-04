package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Domain model for Notification as defined in the UML Class Diagram.
 */
public class Notification {
    private String id;
    private String username; // Recipient username
    private String title;
    private String message;
    private String category; // Offer, Chat, System, Rating, etc.
    private LocalDateTime timestamp;
    private boolean read;
    private String relatedItemId;
    private String relatedItemType; // OFFER, CHAT_THREAD, DEAL, LISTING, REVIEW, ANNOUNCEMENT

    public Notification() {
        this.timestamp = LocalDateTime.now();
        this.read = false;
    }

    public Notification(String username, String title, String message, String category) {
        this();
        this.username = username;
        this.title = title;
        this.message = message;
        this.category = category;
    }

    public Notification(String username, String title, String message, String category,
                        String relatedItemId, String relatedItemType) {
        this(username, title, message, category);
        this.relatedItemId = relatedItemId;
        this.relatedItemType = relatedItemType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRecipientUsername() {
        return username;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.username = recipientUsername;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public boolean isUnread() {
        return !read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getRelatedItemId() {
        return relatedItemId;
    }

    public void setRelatedItemId(String relatedItemId) {
        this.relatedItemId = relatedItemId;
    }

    public String getRelatedItemType() {
        return relatedItemType;
    }

    public void setRelatedItemType(String relatedItemType) {
        this.relatedItemType = relatedItemType;
    }

    public String getFormattedTimestamp() {
        if (timestamp == null) {
            return "";
        }
        return timestamp.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
    }
}
