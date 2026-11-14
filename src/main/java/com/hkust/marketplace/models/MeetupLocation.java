package com.hkust.marketplace.models;

/**
 * Enumeration of predefined HKUST campus meet-up points for transactions and item handovers.
 */
public enum MeetupLocation {

    /** Main academic building central Atrium. */
    ATRIUM("Atrium"),

    /** Lee Shau Kee Library entrance or lobby. */
    LIBRARY("Library"),

    /** LG1 Student Canteen. */
    LG1_CANTEEN("LG1 Canteen"),

    /** LG7 Food Court / Canteen. */
    LG7_CANTEEN("LG7 Canteen"),

    /** North Bus Station / North Gate entrance. */
    NORTH_GATE("North Gate"),

    /** South Gate entrance. */
    SOUTH_GATE("South Gate"),

    /** Undergraduate or Postgraduate Student Residential Halls. */
    STUDENT_HALLS("Student Halls");

    /** Human-readable display name. */
    private final String displayName;

    /**
     * Constructs a meetup location with display name.
     *
     * @param displayName formatted location name
     */
    MeetupLocation(String displayName) {
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
     * Parses a string into the corresponding {@link MeetupLocation}, defaulting to {@link #LIBRARY}.
     *
     * @param text string to parse
     * @return matching location constant
     */
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
