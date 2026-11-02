package com.hkust.marketplace.models;

/**
 * Enumeration representing the supported sort orders for listings (Subflow S3: Sort Listings).
 */
public enum ListingSortOrder {
    NEWEST_FIRST("Newest First"),
    PRICE_LOW_TO_HIGH("Price: Low to High"),
    PRICE_HIGH_TO_LOW("Price: High to Low");

    private final String displayName;

    ListingSortOrder(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ListingSortOrder fromString(String text) {
        if (text == null) return NEWEST_FIRST;
        String clean = text.trim();
        for (ListingSortOrder order : ListingSortOrder.values()) {
            if (order.displayName.equalsIgnoreCase(clean)
                    || order.name().equalsIgnoreCase(clean.replace(" ", "_").replace(":", ""))) {
                return order;
            }
        }
        return NEWEST_FIRST;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
