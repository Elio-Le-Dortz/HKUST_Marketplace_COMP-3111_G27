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
        String clean = text.trim();
        for (Category c : Category.values()) {
            if (c.displayName.equalsIgnoreCase(clean) || c.name().equalsIgnoreCase(clean)) {
                return c;
            }
        }
        if ("Textbooks".equalsIgnoreCase(clean) || "Textbook".equalsIgnoreCase(clean) || "Book".equalsIgnoreCase(clean)) {
            return BOOKS;
        }
        if ("Sports Equipment".equalsIgnoreCase(clean) || "Sport".equalsIgnoreCase(clean)) {
            return SPORTS;
        }
        if ("Kitchenware".equalsIgnoreCase(clean) || "Others".equalsIgnoreCase(clean)) {
            return OTHER;
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
