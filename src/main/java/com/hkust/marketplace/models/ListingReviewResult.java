package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Encapsulates the outcome of an administrative listing review action (US-A02, Task 3.2).
 */
public class ListingReviewResult {

    private final boolean success;
    private final ListingReviewStatus status;
    private final String message;
    private final Listing listing;
    private final String rejectionReason;
    private final LocalDateTime reviewedAt;

    public ListingReviewResult(boolean success,
                               ListingReviewStatus status,
                               String message,
                               Listing listing,
                               String rejectionReason,
                               LocalDateTime reviewedAt) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.listing = listing;
        this.rejectionReason = rejectionReason;
        this.reviewedAt = reviewedAt;
    }

    public static ListingReviewResult approved(Listing listing, LocalDateTime reviewedAt) {
        return new ListingReviewResult(
                true,
                ListingReviewStatus.APPROVED,
                "Listing approved successfully and is now Live.",
                listing,
                null,
                reviewedAt
        );
    }

    public static ListingReviewResult rejected(Listing listing, String rejectionReason, LocalDateTime reviewedAt) {
        return new ListingReviewResult(
                true,
                ListingReviewStatus.REJECTED,
                "Listing rejected. Rejection reason has been recorded and seller notified.",
                listing,
                rejectionReason,
                reviewedAt
        );
    }

    public static ListingReviewResult emptyReason(Listing listing) {
        return new ListingReviewResult(
                false,
                ListingReviewStatus.EMPTY_REASON,
                "A written rejection reason is mandatory to reject a listing.",
                listing,
                null,
                null
        );
    }

    public static ListingReviewResult notPending(Listing listing) {
        String currentStatus = listing != null && listing.getStatus() != null
                ? listing.getStatus().name()
                : "UNKNOWN";
        return new ListingReviewResult(
                false,
                ListingReviewStatus.NOT_PENDING_REVIEW,
                "Listing is no longer pending review (current status: " + currentStatus + ").",
                listing,
                null,
                null
        );
    }

    public static ListingReviewResult notFound(String listingId) {
        return new ListingReviewResult(
                false,
                ListingReviewStatus.LISTING_NOT_FOUND,
                "Listing with ID '" + listingId + "' does not exist.",
                null,
                null,
                null
        );
    }

    public static ListingReviewResult unauthorized(String message) {
        return new ListingReviewResult(
                false,
                ListingReviewStatus.UNAUTHORIZED,
                message != null ? message : "Only administrators can review listings.",
                null,
                null,
                null
        );
    }

    public static ListingReviewResult invalidInput(String message) {
        return new ListingReviewResult(
                false,
                ListingReviewStatus.INVALID_INPUT,
                message != null ? message : "Invalid review input parameters.",
                null,
                null,
                null
        );
    }

    public boolean isSuccess() {
        return success;
    }

    public ListingReviewStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Listing getListing() {
        return listing;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }
}
