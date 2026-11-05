package com.hkust.marketplace.models;

/**
 * Status enumeration for Admin Listing Review operations (US-A02, Task 3.2).
 */
public enum ListingReviewStatus {
    /**
     * Listing was successfully approved and transitioned to {@link ListingStatus#LIVE}.
     */
    APPROVED,

    /**
     * Listing was rejected with a mandatory written reason and transitioned to {@link ListingStatus#REJECTED}.
     */
    REJECTED,

    /**
     * Alternative Flow A2: Written rejection reason was omitted or contains only whitespace.
     */
    EMPTY_REASON,

    /**
     * Alternative Flow A3: Listing is no longer in {@link ListingStatus#PENDING_REVIEW} state.
     */
    NOT_PENDING_REVIEW,

    /**
     * Listing was not found in the repository.
     */
    LISTING_NOT_FOUND,

    /**
     * Requesting user is not authenticated or does not hold the {@link UserRole#ADMIN} role.
     */
    UNAUTHORIZED,

    /**
     * Invalid input parameters (e.g., null listing ID or null request).
     */
    INVALID_INPUT
}
