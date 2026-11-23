package com.hkust.marketplace.models;

/**
 * Enumeration of negotiation states for formal price offers and borrow requests.
 */
public enum OfferStatus {

    /** Awaiting review or decision by seller. */
    PENDING,

    /** Accepted by seller, transitioning listing to reserved or deal in progress. */
    ACCEPTED,

    /** Rejected by seller or automatically declined upon listing takedown/competing acceptance. */
    DECLINED,

    /** Seller replied with alternative price or modified borrow dates. */
    COUNTERED,

    /** Withdrawn or cancelled by buyer prior to seller acceptance. */
    WITHDRAWN
}
