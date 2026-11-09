package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Domain model for Report as defined in the UML Class Diagram (CLASS_DIAGRAM §3.9: Admin "1" --> "*" Report : Handles).
 */
public class Report {
    private String id;
    private String listingId;
    private String listingTitle;
    private String buyerUsername;
    private String reason;
    private LocalDateTime date;
    private ReportStatus status;
    private String handledBy;
    private LocalDateTime resolvedDate;

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

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    public LocalDateTime getResolvedDate() {
        return resolvedDate;
    }

    public void setResolvedDate(LocalDateTime resolvedDate) {
        this.resolvedDate = resolvedDate;
    }

    @Override
    public String toString() {
        return "Report{" +
                "id='" + id + '\'' +
                ", listingId='" + listingId + '\'' +
                ", listingTitle='" + listingTitle + '\'' +
                ", buyerUsername='" + buyerUsername + '\'' +
                ", reason='" + reason + '\'' +
                ", date=" + date +
                ", status=" + status +
                ", handledBy='" + handledBy + '\'' +
                ", resolvedDate=" + resolvedDate +
                '}';
    }
}
