package com.hkust.marketplace.models;

public enum Condition {
    NEW("New"),
    LIKE_NEW("Like New"),
    USED("Used"),
    FOR_PARTS("For Parts");

    private final String displayName;

    Condition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

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
