package com.hkust.marketplace.models;

/**
 * Operation statuses for Admin Seller Ratings management (US-A08 / Use Case 3.8).
 */
public enum AdminRatingOperationStatus {
    /**
     * Operation executed successfully.
     */
    SUCCESS,

    /**
     * The requested review could not be found.
     */
    REVIEW_NOT_FOUND,

    /**
     * The review is already hidden.
     */
    ALREADY_HIDDEN,

    /**
     * The review is already visible (not hidden).
     */
    ALREADY_VISIBLE,

    /**
     * The review is not currently flagged.
     */
    NOT_FLAGGED,

    /**
     * The supplied date range has an end date strictly before its start date.
     */
    INVALID_DATE_RANGE,

    /**
     * The caller lacks administrative authorization.
     */
    UNAUTHORIZED,

    /**
     * The operation could not be completed due to invalid parameters.
     */
    INVALID_OPERATION
}
