package com.hkust.marketplace.models;

/**
 * Outcome of saving the profile form of use case 2.6 "Manage Seller Profile" (US-S06).
 */
public enum SellerProfileStatus {

    /** The changes were saved (basic flow, steps 7 and 8). */
    SUCCESS,

    /** The form holds the current values of the profile, so there was nothing to save. */
    NO_CHANGES,

    /**
     * At least one field is invalid (alternative flows A1, A2 and A3); nothing was saved and the
     * errors are reported per field.
     */
    INVALID_FIELDS
}
