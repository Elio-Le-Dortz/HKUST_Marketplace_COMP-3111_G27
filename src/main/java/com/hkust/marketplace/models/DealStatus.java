package com.hkust.marketplace.models;

/**
 * Enumeration of transaction deal fulfillment statuses in the ledger.
 */
public enum DealStatus {

    /** Deal in progress (reserved sale, ongoing borrow loan, or pending handover). */
    ACTIVE,

    /** Finalized deal (sale completed, giveaway transferred, or loan fulfilled). */
    COMPLETED,

    /** Borrowed loan returned to seller, either manually or via auto-return timer. */
    RETURNED
}
