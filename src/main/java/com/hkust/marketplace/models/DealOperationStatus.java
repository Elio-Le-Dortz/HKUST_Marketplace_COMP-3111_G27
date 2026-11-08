package com.hkust.marketplace.models;

/**
 * Status codes for operations on Deals and Offers in the Buyer Portal (Use Case 1.6: Manage My Deals / US-B07).
 */
public enum DealOperationStatus {
    /** The deal or offer operation succeeded. */
    SUCCESS,

    /** The target offer was not found. */
    OFFER_NOT_FOUND,

    /** The offer is no longer in PENDING state (Alternative Flow A2). */
    OFFER_NOT_PENDING,

    /** The counter-offer was not found or invalid. */
    COUNTER_NOT_FOUND,

    /** The counter-offer is no longer valid because listing is unavailable (Alternative Flow A3). */
    COUNTER_NOT_VALID,

    /** The associated listing was not found or is no longer live. */
    LISTING_NOT_AVAILABLE,

    /** The target deal was not found. */
    DEAL_NOT_FOUND,

    /** The deal is not in ACTIVE state. */
    DEAL_NOT_ACTIVE,

    /** The deal is not a borrowed loan item (cannot self-return). */
    DEAL_NOT_BORROW,

    /** The deal is a borrowed loan item (cannot mark complete; loans must be returned). */
    DEAL_IS_LOAN,

    /** The user is not authorized to act on this deal or offer. */
    UNAUTHORIZED,

    /** Invalid input parameters or unsupported action. */
    INVALID_ACTION,

    /** Rating value is invalid (must be 1 to 5). */
    INVALID_RATING,

    /** Deal has already been rated by the buyer. */
    ALREADY_RATED
}
