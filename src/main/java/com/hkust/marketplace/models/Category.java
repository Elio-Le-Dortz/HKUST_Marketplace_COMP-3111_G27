package com.hkust.marketplace.models;

/**
 * Enumeration of marketplace item categories as defined in the domain specification.
 */
public enum Category {

    /** Textbooks, course readers, and general literature. */
    BOOKS("Books"),

    /** Laptops, calculators, chargers, accessories, and digital devices. */
    ELECTRONICS("Electronics"),

    /** Desk chairs, storage racks, lamps, and dormitory furniture. */
    FURNITURE("Furniture"),

    /** HKUST hoodies, jackets, formal wear, and clothing. */
    CLOTHING("Clothing"),

    /** Notebooks, pens, folders, and academic stationery. */
    STATIONERY("Stationery"),

    /** Gym gear, rackets, sports balls, and outdoor equipment. */
    SPORTS("Sports"),

    /** Miscellaneous campus items, kitchenware, and general goods. */
    OTHER("Other");

    /** Human-readable display name. */
    private final String displayName;

    /**
     * Constructs a category constant with display name.
     *
     * @param displayName formatted category name
     */
    Category(String displayName) {
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
     * Parses a string into the corresponding {@link Category}, with fallback aliases.
     *
     * @param text string to parse
     * @return matching category constant, defaulting to {@link #OTHER}
     */
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
