package com.hkust.marketplace.models;

/**
 * Enumeration of listing lifecycle statuses as specified in the marketplace state diagram.
 */
public enum ListingStatus {

    /** Newly posted or edited listing awaiting administrator moderation. */
    PENDING_REVIEW,

    /** Approved and active listing visible to buyers across the marketplace. */
    LIVE,

    /** Rejected by administrator during moderation review with mandatory written reason. */
    REJECTED,

    /** Active offer accepted; item held exclusively pending physical exchange or payment. */
    RESERVED,

    /** Finalized monetary transaction; item transferred to buyer. */
    SOLD,

    /** Item physically lent to buyer under active borrow loan agreement. */
    LENT,

    /** Free giveaway claimed and transferred to recipient. */
    GIVEN,

    /** Taken down by administrator or soft-deleted by seller. */
    REMOVED
}
