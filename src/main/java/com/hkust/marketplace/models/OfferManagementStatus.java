package com.hkust.marketplace.models;

/**
 * Outcome of an action of use case 2.5 "Manage Offers And Requests" (US-S05), taken by a
 * Seller on an offer, borrow request or claim received on one of their listings.
 */
public enum OfferManagementStatus {

    /** The action succeeded. */
    SUCCESS,

    /** No offer has the given identifier (or the identifier is empty). */
    OFFER_NOT_FOUND,

    /** The offer was made on a listing of another Seller, so this Seller cannot handle it. */
    UNAUTHORIZED,

    /**
     * Alternative flow A2: the offer is no longer pending, because the buyer withdrew it or
     * it was already accepted, declined or countered. Nothing is changed.
     */
    OFFER_NOT_PENDING,

    /**
     * Alternative flow A3: the listing is no longer Live (reserved, lent, deleted or taken
     * down by the admin). The offer is marked as declined and its buyer is notified.
     */
    LISTING_NOT_AVAILABLE,

    /**
     * The buyer's account was deactivated or deleted by the admin, so no deal can be made
     * with them. The offer is marked as declined.
     */
    BUYER_UNAVAILABLE,

    /**
     * The borrow period requested by the buyer has already ended, so the request cannot be
     * accepted as it is. The request stays pending: the Seller can counter it with new
     * dates or decline it.
     */
    BORROW_PERIOD_ENDED,

    /**
     * The counter does not fit the offer: claims on Give Away listings cannot be countered,
     * a price offer is countered with a price and a borrow request with dates.
     */
    COUNTER_NOT_ALLOWED,

    /** Alternative flow A4: the counter price is missing or invalid. Nothing is changed. */
    INVALID_COUNTER_PRICE,

    /** Alternative flow A4: the counter borrow dates are missing or invalid. Nothing is changed. */
    INVALID_COUNTER_DATES,

    /** The reason given for a decline is too long. Nothing is changed. */
    INVALID_REASON
}
