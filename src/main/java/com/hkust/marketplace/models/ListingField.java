package com.hkust.marketplace.models;

/**
 * Fields of the listing form of use case 2.3 "Create Listing" (US-S03).
 *
 * <p>A failed submission reports its errors per field, so that the form can highlight every
 * invalid field (alternative flows A1, A2 and A3). The constants are declared in the order
 * of the form, which is the order in which the errors are reported.</p>
 */
public enum ListingField {

    /** Listing type: Sell, Lend or Give Away (the first choice, which changes the form). */
    TYPE,

    /** Item name, shown as the title of the listing. */
    TITLE,

    /** Description of the item. */
    DESCRIPTION,

    /** Category, chosen from a predefined list. */
    CATEGORY,

    /** Condition: New, Like New, Used or For Parts. */
    CONDITION,

    /** Price in HK$, required only for a Sell listing. */
    PRICE,

    /** Lending duration, required only for a Lend listing. */
    LENDING_DURATION,

    /** Meet-up location, chosen from the predefined HKUST locations. */
    MEETUP_LOCATION,

    /** Photos of the item, from 1 to 6. */
    PHOTOS
}
