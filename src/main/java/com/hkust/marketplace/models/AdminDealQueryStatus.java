package com.hkust.marketplace.models;

/**
 * Status indicator for Admin Deal Oversight queries (Sprint 2 US-A05, Use Case 3.5 in specs_admin.tex).
 */
public enum AdminDealQueryStatus {
    /** Deals were successfully retrieved and matched against active filters. */
    SUCCESS,

    /** Alternative Flow A1: No deals exist anywhere in the system yet. */
    NO_DEALS_YET,

    /** Alternative Flow A3: Deals exist, but none match the current filter or search criteria. */
    NO_MATCHING_DEALS,

    /** Alternative Flow A4: The provided date range is invalid (end date precedes start date). */
    INVALID_DATE_RANGE
}
