package com.hkust.marketplace.models;

/**
 * Status resulting from an attempt to view a listing's detail.
 */
public enum ListingViewStatus {
    /** The listing was found and successfully opened (view count incremented). */
    SUCCESS,

    /** The listing could not be found with the specified identifier. */
    NOT_FOUND,

    /**
     * Alternative Flow A1: The listing is no longer live (e.g., reserved, sold, removed, or rejected)
     * when accessed from the live marketplace browsing view.
     */
    NOT_LIVE
}
