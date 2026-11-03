package com.hkust.marketplace.models;

/**
 * Status outcomes for rating a seller after a completed deal (US-B11 / Use Case 1.10).
 */
public enum RateSellerStatus {
    /** Rating and optional review submitted successfully (Basic Flow Step 8). */
    SUCCESS,

    /** Alternative Flow A1: No star rating selected or invalid rating. */
    NO_STAR_RATING,

    /** Alternative Flow A2: This deal has already been rated by the buyer. */
    ALREADY_RATED,

    /** Alternative Flow A3: Buyer skipped the rating prompt. */
    SKIPPED,

    /** Alternative Flow A4: Buyer cancelled the rating dialog. */
    CANCELLED,

    /** Specified deal does not exist in the system. */
    DEAL_NOT_FOUND,

    /** Requesting buyer is not the buyer associated with the deal. */
    UNAUTHORIZED,

    /** Deal is not in a completed or returned state and cannot be rated yet. */
    DEAL_NOT_ELIGIBLE,

    /** Seller associated with the deal does not exist. */
    SELLER_NOT_FOUND,

    /** Request parameters (e.g. deal ID or buyer username) are null or empty. */
    INVALID_INPUT
}
