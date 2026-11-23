package com.hkust.marketplace.models;

/**
 * Enumeration of transaction modalities supported by marketplace listings.
 */
public enum ListingType {

    /** Direct monetary sale with price and counter-offer negotiation. */
    SELL("Sell"),

    /** Temporary loan with defined duration (seconds/days/weeks/months) and auto-return. */
    LEND("Lend"),

    /** Free item giveaway claimable atomically by the first eligible buyer. */
    GIVEAWAY("Giveaway");

    /** Human-readable display name. */
    private final String displayName;

    /**
     * Constructs a listing type constant with display name.
     *
     * @param displayName formatted modality name
     */
    ListingType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a string into the corresponding {@link ListingType}, defaulting to {@link #SELL}.
     *
     * @param text string to parse
     * @return matching listing type constant
     */
    public static ListingType fromString(String text) {
        if (text == null) return SELL;
        for (ListingType t : ListingType.values()) {
            if (t.displayName.equalsIgnoreCase(text) || t.name().equalsIgnoreCase(text)) {
                return t;
            }
        }
        return SELL;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
