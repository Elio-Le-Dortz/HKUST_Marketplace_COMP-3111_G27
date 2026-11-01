package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Domain model for Offer as defined in the UML Class Diagram.
 */
public class Offer {
    private String id;
    private OfferType type;
    private String listingId;
    private String listingTitle;
    private String buyerUsername;
    private String sellerUsername;
    private Double price;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private OfferStatus status;
    private LocalDateTime createdDate;
    private String declineReason;
    private Offer counterOffer;

    public Offer() {
        this.createdDate = LocalDateTime.now();
        this.status = OfferStatus.PENDING;
    }

    public Offer(String id, OfferType type, String listingId, String buyerUsername, String sellerUsername) {
        this();
        this.id = id;
        this.type = type;
        this.listingId = listingId;
        this.buyerUsername = buyerUsername;
        this.sellerUsername = sellerUsername;
    }

    // Getters & Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public OfferType getType() {
        return type;
    }

    public void setType(OfferType type) {
        this.type = type;
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

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public Offer getCounterOffer() {
        return counterOffer;
    }

    public void setCounterOffer(Offer counterOffer) {
        this.counterOffer = counterOffer;
    }
}
