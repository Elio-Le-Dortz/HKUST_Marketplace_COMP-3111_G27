package com.hkust.marketplace.models;

/**
 * Enumeration of operation statuses for buyer profile updates (US-B08).
 * Corresponds to the Basic Flow and Alternative Flows in Use Case 1.7.
 */
public enum ProfileUpdateStatus {
    /** Profile successfully updated (Basic Flow step 7-8). */
    SUCCESS,

    /** Full Name field is missing or whitespace only (Alternative Flow A1). */
    EMPTY_FULL_NAME,

    /** Current password was omitted when attempting to change password (Alternative Flow A2). */
    CURRENT_PASSWORD_REQUIRED,

    /** Current password does not match the stored credentials (Alternative Flow A2). */
    WRONG_CURRENT_PASSWORD,

    /** New password was omitted when attempting to change password (Alternative Flow A3). */
    NEW_PASSWORD_REQUIRED,

    /** Confirm password was omitted when attempting to change password (Alternative Flow A3). */
    CONFIRM_PASSWORD_REQUIRED,

    /** New password and confirm password do not match (Alternative Flow A3). */
    PASSWORD_CONFIRMATION_MISMATCH,

    /** New password violates password strength policies (Alternative Flow A3). */
    WEAK_NEW_PASSWORD,

    /** Targeted user does not exist. */
    USER_NOT_FOUND,

    /** Requesting user does not have permission (e.g. not an active Buyer). */
    UNAUTHORIZED
}
