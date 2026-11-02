package com.hkust.marketplace.models;

/**
 * Status enumeration representing possible outcomes when acting on a listing
 * (Use Case 1.5: Act on Listing / US-B05).
 */
public enum ActOnListingStatus {
    /** Action completed successfully and offer stored. */
    SUCCESS,
    /** Specified listing was not found. */
    LISTING_NOT_FOUND,
    /** Listing is no longer in LIVE status (Alternative Flow A1). */
    LISTING_NOT_LIVE,
    /** Attempted action does not match the listing type (Sell vs Lend vs Giveaway). */
    INVALID_LISTING_TYPE,
    /** Proposed price is null, zero, negative, or invalid (Alternative Flow A2). */
    INVALID_PRICE,
    /** Borrow dates violate temporal rules or minimum duration (Alternative Flow A3). */
    INVALID_BORROW_DATES,
    /** Giveaway item has already been claimed concurrently or previously (Alternative Flow A4). */
    ALREADY_CLAIMED,
    /** User attempted to make an offer or claim on their own listing. */
    SELF_ACTION_NOT_ALLOWED,
    /** Buyer username is null, empty, or does not exist / deactivated. */
    INVALID_USER
}
