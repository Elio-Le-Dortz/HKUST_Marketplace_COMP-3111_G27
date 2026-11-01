package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Domain model for Report as defined in the UML Class Diagram.
 */
public class Report {
    private String id;
    private String listingId;
    private String listingTitle;
    private String buyerUsername;
    private String reason;
    private LocalDateTime date;
    private ReportStatus status;

    public Report() {
        this.date = LocalDateTime.now();
        this.status = ReportStatus.OPEN;
    }

    public Report(String id, String listingId, String listingTitle, String buyerUsername, String reason) {
        this();
        this.id = id;
        this.listingId = listingId;
        this.listingTitle = listingTitle;
        this.buyerUsername = buyerUsername;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }
}
