package com.hkust.marketplace.models;

public enum ListingType {
    SELL("Sell"),
    LEND("Lend"),
    GIVEAWAY("Giveaway");

    private final String displayName;

    ListingType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

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
