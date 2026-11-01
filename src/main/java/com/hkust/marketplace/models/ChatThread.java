package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain model for ChatThread as defined in the UML Class Diagram.
 */
public class ChatThread {
    private String id;
    private String listingId;
    private String listingTitle;
    private String buyerUsername;
    private String sellerUsername;
    private LocalDateTime createdDate;
    private List<Message> messages = new ArrayList<>();

    public ChatThread() {
        this.createdDate = LocalDateTime.now();
    }

    public ChatThread(String id, String listingId, String buyerUsername, String sellerUsername) {
        this();
        this.id = id;
        this.listingId = listingId;
        this.buyerUsername = buyerUsername;
        this.sellerUsername = sellerUsername;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getListingId() {
        return listingId;
    }

    public void setListingId(String listingId) {
        this.listingId = listingId;
    }

    public String getListingTitle() {
        return listingTitle;
    }

    public void setListingTitle(String listingTitle) {
        this.listingTitle = listingTitle;
    }

    public String getBuyerUsername() {
        return buyerUsername;
    }

    public void setBuyerUsername(String buyerUsername) {
        this.buyerUsername = buyerUsername;
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public void setSellerUsername(String sellerUsername) {
        this.sellerUsername = sellerUsername;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public void addMessage(Message message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }
}
