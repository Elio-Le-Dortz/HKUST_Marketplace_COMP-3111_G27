package com.hkust.marketplace.models;

/**
 * Enumeration of offer types submitted by buyers on marketplace listings.
 */
public enum OfferType {

    /** Proposed monetary purchase price on a sale listing. */
    PRICE_OFFER,

    /** Proposed borrowing duration and dates on a lend listing. */
    BORROW_REQUEST,

    /** Immediate single-action transfer claim on a giveaway listing. */
    CLAIM
}
