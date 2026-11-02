package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain model for ChatThread as defined in the UML Class Diagram.
 * Represents a private chat thread linked to a listing between a buyer and a seller (US-B06).
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

    public synchronized void addMessage(Message message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        if (message != null) {
            this.messages.add(message);
        }
    }

    /**
     * Checks if this thread has any messages.
     *
     * @return {@code true} if messages list contains at least one message
     */
    public boolean hasMessages() {
        return messages != null && !messages.isEmpty();
    }

    /**
     * Returns the last message in this thread, or {@code null} if empty.
     *
     * @return the most recent {@link Message}, or {@code null}
     */
    public Message getLastMessage() {
        if (!hasMessages()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    /**
     * Checks whether the given username is a participant (buyer or seller) in this chat thread.
     *
     * @param username the username to check (case-insensitive)
     * @return {@code true} if the user is the buyer or seller of this thread
     */
    public boolean isParticipant(String username) {
        if (username == null) {
            return false;
        }
        String normalized = username.trim().toLowerCase();
        return (buyerUsername != null && buyerUsername.trim().toLowerCase().equals(normalized))
                || (sellerUsername != null && sellerUsername.trim().toLowerCase().equals(normalized));
    }

    /**
     * Returns the counterpart participant's username in this thread.
     *
     * @param username the username of one participant
     * @return the username of the counterpart participant, or {@code null} if user not in thread
     */
    public String getOtherParticipant(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim().toLowerCase();
        if (buyerUsername != null && buyerUsername.trim().toLowerCase().equals(normalized)) {
            return sellerUsername;
        }
        if (sellerUsername != null && sellerUsername.trim().toLowerCase().equals(normalized)) {
            return buyerUsername;
        }
        return null;
    }
}
