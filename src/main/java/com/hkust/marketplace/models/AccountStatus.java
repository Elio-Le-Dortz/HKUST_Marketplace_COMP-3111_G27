package com.hkust.marketplace.models;

/**
 * Enumeration of account lifecycle statuses as defined in the domain model.
 */
public enum AccountStatus {

    /** Normal operational status allowing login and marketplace participation. */
    ACTIVE,

    /** Temporarily suspended by administrator; login is blocked with explanatory notice. */
    DEACTIVATED,

    /** Soft-deleted account; record retained for audit trails but login is permanently barred. */
    DELETED
}
