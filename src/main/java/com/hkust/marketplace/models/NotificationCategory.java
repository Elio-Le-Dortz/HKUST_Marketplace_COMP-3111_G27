package com.hkust.marketplace.models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Categorization of notifications for Buyer Notification Board (US-B09 / UC-B09).
 * Directly maps to specifications in specs_buyer.tex Section 1.8.
 */
public enum NotificationCategory {
    OFFERS("Offers", "🏷️", Arrays.asList("Offers", "Offer", "Deal")),
    CHAT("Chat", "💬", Arrays.asList("Chat", "Message")),
    BORROW_REMINDERS("Borrow Reminders", "⏰", Arrays.asList("Borrow Reminders", "Borrow Reminder", "Borrow", "Loan", "Due Date")),
    LISTINGS("Listings", "📦", Arrays.asList("Listings", "Listing", "Item")),
    REVIEWS("Reviews", "⭐", Arrays.asList("Reviews", "Review", "Rating", "Feedback")),
    ANNOUNCEMENTS("Announcements", "📢", Arrays.asList("Announcements", "Announcement", "System", "Reports", "General", "Notice"));

    private final String displayName;
    private final String icon;
    private final List<String> aliases;

    NotificationCategory(String displayName, String icon, List<String> aliases) {
        this.displayName = displayName;
        this.icon = icon;
        this.aliases = aliases;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public List<String> getAliases() {
        return Collections.unmodifiableList(aliases);
    }

    /**
     * Resolves a raw string category (case-insensitive) into a matching NotificationCategory.
     * Defaults to ANNOUNCEMENTS if unrecognized or null/empty.
     *
     * @param raw the raw category string
     * @return matching NotificationCategory or ANNOUNCEMENTS
     */
    public static NotificationCategory fromRaw(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ANNOUNCEMENTS;
        }
        String clean = raw.trim().toLowerCase();

        for (NotificationCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(clean) || cat.displayName.equalsIgnoreCase(clean)) {
                return cat;
            }
            for (String alias : cat.aliases) {
                if (alias.equalsIgnoreCase(clean)) {
                    return cat;
                }
            }
        }

        // Partial match fallback
        if (clean.contains("offer")) return OFFERS;
        if (clean.contains("chat") || clean.contains("message")) return CHAT;
        if (clean.contains("borrow") || clean.contains("loan") || clean.contains("due")) return BORROW_REMINDERS;
        if (clean.contains("list")) return LISTINGS;
        if (clean.contains("review") || clean.contains("rate") || clean.contains("rating")) return REVIEWS;

        return ANNOUNCEMENTS;
    }

    /**
     * Checks if this category matches a notification's raw category string.
     *
     * @param rawCategory raw category string
     * @return true if matches this category
     */
    public boolean matches(String rawCategory) {
        return fromRaw(rawCategory) == this;
    }
}
