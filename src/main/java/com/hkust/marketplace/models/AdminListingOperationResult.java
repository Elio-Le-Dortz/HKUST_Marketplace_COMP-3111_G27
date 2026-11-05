package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Result object encapsulating the outcome of administrative listing management operations
 * (US-A07: Manage All Listings).
 */
public class AdminListingOperationResult {

    private final AdminListingOperationStatus status;
    private final String message;
    private final Listing listing;
    private final String invalidFieldName;
    private final int affectedOffersCount;

    private AdminListingOperationResult(AdminListingOperationStatus status, String message,
                                       Listing listing, String invalidFieldName, int affectedOffersCount) {
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.message = message;
        this.listing = listing;
        this.invalidFieldName = invalidFieldName;
        this.affectedOffersCount = affectedOffersCount;
    }

    public static AdminListingOperationResult success(Listing listing, String message) {
        return new AdminListingOperationResult(AdminListingOperationStatus.SUCCESS, message, listing, null, 0);
    }

    public static AdminListingOperationResult success(Listing listing, String message, int affectedOffersCount) {
        return new AdminListingOperationResult(AdminListingOperationStatus.SUCCESS, message, listing, null, affectedOffersCount);
    }

    public static AdminListingOperationResult sellerNotActive(String message) {
        return new AdminListingOperationResult(AdminListingOperationStatus.SELLER_NOT_ACTIVE, message, null, null, 0);
    }

    public static AdminListingOperationResult invalidField(String fieldName, String message) {
        return new AdminListingOperationResult(AdminListingOperationStatus.INVALID_FIELD, message, null, fieldName, 0);
    }

    public static AdminListingOperationResult dealInProgressOrCompleted(String message) {
        return new AdminListingOperationResult(AdminListingOperationStatus.DEAL_IN_PROGRESS_OR_COMPLETED, message, null, null, 0);
    }

    public static AdminListingOperationResult listingNotLive(String message) {
        return new AdminListingOperationResult(AdminListingOperationStatus.LISTING_NOT_LIVE, message, null, null, 0);
    }

    public static AdminListingOperationResult emptyReason(String message) {
        return new AdminListingOperationResult(AdminListingOperationStatus.EMPTY_REASON, message, null, null, 0);
    }

    public static AdminListingOperationResult listingNotFound(String message) {
        return new AdminListingOperationResult(AdminListingOperationStatus.LISTING_NOT_FOUND, message, null, null, 0);
    }

    public static AdminListingOperationResult unauthorized(String message) {
        return new AdminListingOperationResult(AdminListingOperationStatus.UNAUTHORIZED, message, null, null, 0);
    }

    public boolean isSuccess() {
        return status == AdminListingOperationStatus.SUCCESS;
    }

    public AdminListingOperationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Listing getListing() {
        return listing;
    }

    public String getInvalidFieldName() {
        return invalidFieldName;
    }

    public int getAffectedOffersCount() {
        return affectedOffersCount;
    }

    @Override
    public String toString() {
        return "AdminListingOperationResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", listing=" + (listing != null ? listing.getId() : "null") +
                ", invalidFieldName='" + invalidFieldName + '\'' +
                ", affectedOffersCount=" + affectedOffersCount +
                '}';
    }
}
