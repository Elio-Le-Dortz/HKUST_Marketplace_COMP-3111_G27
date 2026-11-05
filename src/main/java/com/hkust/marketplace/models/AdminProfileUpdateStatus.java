package com.hkust.marketplace.models;

/**
 * Status enumeration for Admin Profile updates (US-A04, Task 3.4).
 * Captures all possible outcomes and validation violations specified in Use Case 3.4.
 */
public enum AdminProfileUpdateStatus {
    /** Profile successfully updated (Basic Flow step 8). */
    SUCCESS,

    /** Full name is null, empty, or whitespace-only (Alternative Flow A1). */
    EMPTY_FULL_NAME,

    /** Current password was omitted when attempting to change password (Alternative Flow A2). */
    CURRENT_PASSWORD_REQUIRED,

    /** Current password does not match stored admin password hash (Alternative Flow A2). */
    WRONG_CURRENT_PASSWORD,

    /** New password was omitted when attempting to change password (Alternative Flow A3). */
    NEW_PASSWORD_REQUIRED,

    /** Confirmation password was omitted when attempting to change password (Alternative Flow A3). */
    CONFIRM_PASSWORD_REQUIRED,

    /** New password and confirmation password do not match (Alternative Flow A3). */
    PASSWORD_CONFIRMATION_MISMATCH,

    /** New password fails one or more complexity/length rules (Alternative Flow A3). */
    WEAK_NEW_PASSWORD,

    /** Target admin account does not exist. */
    USER_NOT_FOUND,

    /** Account is not an administrator or is deactivated. */
    UNAUTHORIZED
}
