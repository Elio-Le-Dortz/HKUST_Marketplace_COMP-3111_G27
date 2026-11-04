package com.hkust.marketplace.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain model for Deal as defined in the UML Class Diagram.
 */
public class Deal {
    private String id;
    private String listingId;
    private String listingTitle;
    private String buyerUsername;
    private String sellerUsername;
    private DealType type;
    private DealStatus status;
    private LocalDateTime startDate;
    private LocalDateTime dueDate;
    private LocalDateTime closedDate;
    private String offerId;
    private Double offerPrice;
    private boolean rated;

    public Deal() {
        this.startDate = LocalDateTime.now();
        this.status = DealStatus.ACTIVE;
        this.type = DealType.SALE;
        this.rated = false;
    }

    // Getters & Setters
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

    public DealType getType() {
        return type;
    }

    public void setType(DealType type) {
        this.type = type;
    }

    // Backward-compatible listing type getter/setter
    public ListingType getDealType() {
        if (type == DealType.LOAN) return ListingType.LEND;
        if (type == DealType.GIVEAWAY) return ListingType.GIVEAWAY;
        return ListingType.SELL;
    }

    public void setDealType(ListingType listingType) {
        if (listingType == ListingType.LEND) this.type = DealType.LOAN;
        else if (listingType == ListingType.GIVEAWAY) this.type = DealType.GIVEAWAY;
        else this.type = DealType.SALE;
    }

    public DealStatus getStatus() {
        return status;
    }

    public void setStatus(DealStatus status) {
        this.status = status;
    }

    // Backward-compatible status string getter/setter
    public String getStatusString() {
        return status != null ? status.name() : "ACTIVE";
    }

    public void setStatus(String statusStr) {
        if (statusStr != null) {
            try {
                this.status = DealStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                if (statusStr.equalsIgnoreCase("Pending") || statusStr.equalsIgnoreCase("Accepted")) {
                    this.status = DealStatus.ACTIVE;
                } else if (statusStr.equalsIgnoreCase("Completed")) {
                    this.status = DealStatus.COMPLETED;
                } else if (statusStr.equalsIgnoreCase("Returned")) {
                    this.status = DealStatus.RETURNED;
                } else {
                    this.status = DealStatus.ACTIVE;
                }
            }
        }
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(LocalDateTime closedDate) {
        this.closedDate = closedDate;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public Double getOfferPrice() {
        return offerPrice;
    }

    public void setOfferPrice(Double offerPrice) {
        this.offerPrice = offerPrice;
    }

    public boolean isRated() {
        return rated;
    }

    public void setRated(boolean rated) {
        this.rated = rated;
    }

    // Backward-compatible borrow dates
    public LocalDate getBorrowStart() {
        return startDate != null ? startDate.toLocalDate() : null;
    }

    public void setBorrowStart(LocalDate start) {
        if (start != null) this.startDate = start.atStartOfDay();
    }

    public LocalDate getBorrowEnd() {
        return dueDate != null ? dueDate.toLocalDate() : null;
    }

    public void setBorrowEnd(LocalDate end) {
        if (end != null) this.dueDate = end.atTime(23, 59, 59);
    }

    public LocalDateTime getCreatedAt() {
        return startDate;
    }

    public void setCreatedAt(LocalDateTime dt) {
        this.startDate = dt;
    }

    public LocalDateTime getCompletedAt() {
        return closedDate;
    }

    public void setCompletedAt(LocalDateTime dt) {
        this.closedDate = dt;
    }

    public boolean isExpired(LocalDateTime now) {
        return type == DealType.LOAN && dueDate != null && now != null && now.isAfter(dueDate) && status == DealStatus.ACTIVE;
    }

    public boolean isExpired() {
        return isExpired(LocalDateTime.now());
    }

    public boolean isLoan() {
        return type == DealType.LOAN;
    }

    public boolean isCompleted() {
        return status == DealStatus.COMPLETED;
    }

    public boolean isReturned() {
        return status == DealStatus.RETURNED;
    }

    public boolean isActive() {
        return status == DealStatus.ACTIVE;
    }
}
