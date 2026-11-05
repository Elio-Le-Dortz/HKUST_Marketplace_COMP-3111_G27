package com.hkust.marketplace.models;

/**
 * Enumeration representing the possible outcomes of administrative listing operations
 * (US-A07: Manage All Listings).
 */
public enum AdminListingOperationStatus {
    /**
     * Operation completed successfully.
     */
    SUCCESS,

    /**
     * Selected seller is not found, not a seller, or inactive/deactivated/deleted (Alternative Flow A2).
     */
    SELLER_NOT_ACTIVE,

    /**
     * Required field is missing, empty, or fails domain validation (Alternative Flow A3).
     */
    INVALID_FIELD,

    /**
     * Modification or take-down blocked because a deal is in progress or completed (Alternative Flow A4).
     */
    DEAL_IN_PROGRESS_OR_COMPLETED,

    /**
     * Take-down blocked because the listing is not currently Live (Alternative Flow A5).
     */
    LISTING_NOT_LIVE,

    /**
     * Take-down blocked because the mandatory reason was empty or blank (Alternative Flow A6).
     */
    EMPTY_REASON,

    /**
     * Target listing was not found.
     */
    LISTING_NOT_FOUND,

    /**
     * User performing the action is not an authorized active administrator.
     */
    UNAUTHORIZED
}
