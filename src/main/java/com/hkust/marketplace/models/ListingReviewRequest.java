package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Data Transfer Object representing an administrator review submission (US-A02, Task 3.2).
 */
public class ListingReviewRequest {

    private String listingId;
    private String adminUsername;
    private String rejectionReason;
    private boolean approve;

    public ListingReviewRequest() {
    }

    public static ListingReviewRequest approveRequest(String listingId, String adminUsername) {
        ListingReviewRequest req = new ListingReviewRequest();
        req.setListingId(listingId);
        req.setAdminUsername(adminUsername);
        req.setApprove(true);
        return req;
    }

    public static ListingReviewRequest rejectRequest(String listingId, String adminUsername, String rejectionReason) {
        ListingReviewRequest req = new ListingReviewRequest();
        req.setListingId(listingId);
        req.setAdminUsername(adminUsername);
        req.setRejectionReason(rejectionReason);
        req.setApprove(false);
        return req;
    }

    public String getListingId() {
        return listingId;
    }

    public void setListingId(String listingId) {
        this.listingId = listingId;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public boolean isApprove() {
        return approve;
    }

    public void setApprove(boolean approve) {
        this.approve = approve;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListingReviewRequest that)) return false;
        return approve == that.approve &&
                Objects.equals(listingId, that.listingId) &&
                Objects.equals(adminUsername, that.adminUsername) &&
                Objects.equals(rejectionReason, that.rejectionReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listingId, adminUsername, rejectionReason, approve);
    }
}
