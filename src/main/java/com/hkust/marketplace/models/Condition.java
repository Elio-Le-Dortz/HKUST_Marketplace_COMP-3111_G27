package com.hkust.marketplace.models;

/**
 * Enumeration of item physical condition tiers as defined in the domain specification.
 */
public enum Condition {

    /** Brand new, unused in original packaging. */
    NEW("New"),

    /** Pre-owned but in pristine, like-new physical state. */
    LIKE_NEW("Like New"),

    /** Used with visible signs of normal wear but fully functional. */
    USED("Used"),

    /** Defective, broken, or intended only for salvage/parts. */
    FOR_PARTS("For Parts");

    /** Human-readable display name. */
    private final String displayName;

    /**
     * Constructs a condition constant with display name.
     *
     * @param displayName formatted name
     */
    Condition(String displayName) {
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
     * Parses a string into the corresponding {@link Condition}, defaulting to {@link #USED}.
     *
     * @param text string to parse
     * @return matching condition constant
     */
    public static Condition fromString(String text) {
        if (text == null) return USED;
        for (Condition c : Condition.values()) {
            if (c.displayName.equalsIgnoreCase(text) || c.name().equalsIgnoreCase(text.replace(" ", "_"))) {
                return c;
            }
        }
        return USED;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
