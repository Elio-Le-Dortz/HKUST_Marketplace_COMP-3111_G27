package com.hkust.marketplace.models;

import java.time.Duration;

/**
 * Unit in which a Seller enters the lending duration of a Lend listing (use case 2.3,
 * US-S03), for example {@code 7 Days}.
 *
 * <p>Seconds and minutes exist so that a loan can be demonstrated and tested in a few
 * seconds: the team set the shortest loan to 5 seconds (Meeting 1, item 2.2).</p>
 */
public enum LendingDurationUnit {

    /** One second. */
    SECONDS("Seconds", Duration.ofSeconds(1)),

    /** One minute. */
    MINUTES("Minutes", Duration.ofMinutes(1)),

    /** One hour. */
    HOURS("Hours", Duration.ofHours(1)),

    /** One day. */
    DAYS("Days", Duration.ofDays(1)),

    /** One week (7 days). */
    WEEKS("Weeks", Duration.ofDays(7));

    /** Name shown in the unit drop-down list. */
    private final String displayName;

    /** Length of one unit. */
    private final Duration length;

    /**
     * Creates a unit.
     *
     * @param displayName name shown in the unit drop-down list
     * @param length      length of one unit
     */
    LendingDurationUnit(String displayName, Duration length) {
        this.displayName = displayName;
        this.length = length;
    }

    /**
     * Returns the name shown in the unit drop-down list.
     *
     * @return the display name, for example {@code Days}
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the length of one unit.
     *
     * @return the length, for example one day for {@link #DAYS}
     */
    public Duration getLength() {
        return length;
    }

    /**
     * Converts an amount of this unit to a duration.
     *
     * @param amount the number of units
     * @return the duration of {@code amount} units
     * @throws ArithmeticException if the duration is too long to be represented
     */
    public Duration toDuration(long amount) {
        return length.multipliedBy(amount);
    }

    /**
     * Returns the display name, so that a drop-down list shows it.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return displayName;
    }
}
