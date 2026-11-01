package com.hkust.marketplace.models;

public enum MeetupLocation {
    ATRIUM("Atrium"),
    LIBRARY("Library"),
    LG1_CANTEEN("LG1 Canteen"),
    LG7_CANTEEN("LG7 Canteen"),
    NORTH_GATE("North Gate"),
    SOUTH_GATE("South Gate"),
    STUDENT_HALLS("Student Halls");

    private final String displayName;

    MeetupLocation(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static MeetupLocation fromString(String text) {
        if (text == null) return LIBRARY;
        for (MeetupLocation m : MeetupLocation.values()) {
            if (m.displayName.equalsIgnoreCase(text)
                    || m.name().equalsIgnoreCase(text.replace(" ", "_"))
                    || text.toLowerCase().contains(m.displayName.toLowerCase())) {
                return m;
            }
        }
        return LIBRARY;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
