package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Domain model for Review as defined in the UML Class Diagram.
 */
public class Review {
    private String id;
    private String sellerUsername;
    private String buyerUsername;
    private String listingTitle;
    private String dealId;
    private int rating; // 1-5
    private String comment;
    private String reply;
    private boolean flagged;
    private String flagReason;
    private boolean hidden; // admin can hide
    private LocalDateTime date;

    public Review() {
        this.date = LocalDateTime.now();
        this.hidden = false;
        this.flagged = false;
    }

    // Getters & Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public void setSellerUsername(String sellerUsername) {
        this.sellerUsername = sellerUsername;
    }

    public String getBuyerUsername() {
        return buyerUsername;
    }

    public void setBuyerUsername(String buyerUsername) {
        this.buyerUsername = buyerUsername;
    }

    public String getListingTitle() {
        return listingTitle;
    }

    public void setListingTitle(String listingTitle) {
        this.listingTitle = listingTitle;
    }

    public String getDealId() {
        return dealId;
    }

    public void setDealId(String dealId) {
        this.dealId = dealId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    // Backward-compatible alias
    public String getSellerReply() {
        return reply;
    }

    public void setSellerReply(String sellerReply) {
        this.reply = sellerReply;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public String getFlagReason() {
        return flagReason;
    }

    public void setFlagReason(String flagReason) {
        this.flagReason = flagReason;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    // Backward-compatible alias
    public LocalDateTime getCreatedAt() {
        return date;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.date = createdAt;
    }
}
