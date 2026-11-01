package com.hkust.marketplace.models;

public enum Category {
    BOOKS("Books"),
    ELECTRONICS("Electronics"),
    FURNITURE("Furniture"),
    CLOTHING("Clothing"),
    STATIONERY("Stationery"),
    SPORTS("Sports"),
    OTHER("Other");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Category fromString(String text) {
        if (text == null) return OTHER;
        for (Category c : Category.values()) {
            if (c.displayName.equalsIgnoreCase(text) || c.name().equalsIgnoreCase(text)) {
                return c;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
