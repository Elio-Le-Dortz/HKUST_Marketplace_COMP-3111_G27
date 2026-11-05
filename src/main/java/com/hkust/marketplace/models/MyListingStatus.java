package com.hkust.marketplace.models;

/**
 * Outcome of an action of use case 2.4 "Manage My Listings" (US-S04), taken by a Seller on
 * one of their listings: checking that it can be edited or deleted, editing it, or deleting it.
 */
public enum MyListingStatus {

    /** The action succeeded, or the listing can be edited or deleted. */
    SUCCESS,

    /**
     * No listing has the given identifier, the identifier is empty, or the Seller already
     * deleted the listing. Nothing is changed.
     */
    LISTING_NOT_FOUND,

    /** The listing belongs to another Seller, so this Seller cannot change it. */
    UNAUTHORIZED,

    /**
     * Alternative flow A2: the listing is Reserved, Sold, Lent or Given, or a deal on it is
     * still active, so it cannot be edited or deleted. Nothing is changed.
     */
    DEAL_IN_PROGRESS_OR_COMPLETED,

    /**
     * The admin took the listing down (use case 3.7), so it can no longer be edited. The
     * Seller can still delete it from their listings.
     */
    TAKEN_DOWN,

    /**
     * Alternative flow A3: the listing has pending offers, borrow requests or claims (or
     * counter offers waiting for the buyer's answer) that the Seller has not agreed to
     * decline. Nothing is changed; the Seller must confirm again.
     */
    PENDING_OFFERS,

    /** Alternative flow A4: some fields of the edited listing are empty or invalid. Nothing is changed. */
    INVALID_FIELDS,

    /** The Seller saved the listing without changing anything, so nothing is changed. */
    NO_CHANGES
}
