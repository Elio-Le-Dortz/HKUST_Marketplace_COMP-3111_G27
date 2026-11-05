package com.hkust.marketplace.models;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Categorization of notifications for the Admin Notification Board (Sprint 2 US-A06).
 *
 * <p>Directly maps to the five categories specified in {@code specs_admin.tex} Use Case 3.6:
 * <ul>
 *   <li><b>Listing Submissions</b>: new or edited listings awaiting review</li>
 *   <li><b>Reports</b>: listings reported by buyers, requiring moderation</li>
 *   <li><b>Flagged Reviews</b>: reviews flagged by sellers as inappropriate</li>
 *   <li><b>User Accounts</b>: user accounts changed, deactivated, reactivated, or deleted (audit log)</li>
 *   <li><b>Announcements</b>: other system announcements and operational alerts</li>
 * </ul>
 */
public enum AdminNotificationCategory {
    LISTING_SUBMISSIONS("Listing Submissions", "📦", Arrays.asList(
            "Listing Submissions", "Listing Submission", "Submissions", "Submission", "Pending Review", "Listings", "Listing"
    )),
    REPORTS("Reports", "🚩", Arrays.asList(
            "Reports", "Report", "Reported Listings", "Listing Report", "Moderation"
    )),
    FLAGGED_REVIEWS("Flagged Reviews", "⚠️", Arrays.asList(
            "Flagged Reviews", "Flagged Review", "Review Flags", "Flagged", "Reviews", "Review"
    )),
    USER_ACCOUNTS("User Accounts", "👤", Arrays.asList(
            "User Accounts", "User Account", "User Audit Log", "Audit Log", "Users", "User", "Accounts"
    )),
    ANNOUNCEMENTS("Announcements", "📢", Arrays.asList(
            "Announcements", "Announcement", "System", "General", "Notice", "Platform"
    ));

    private final String displayName;
    private final String icon;
    private final List<String> aliases;

    AdminNotificationCategory(String displayName, String icon, List<String> aliases) {
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
     * Resolves a raw string category (case-insensitive) into a matching {@link AdminNotificationCategory}.
     * Defaults to {@link #ANNOUNCEMENTS} if unrecognized or empty.
     *
     * @param raw the raw category string
     * @return the resolved category, or {@link #ANNOUNCEMENTS} if no specific match
     */
    public static AdminNotificationCategory fromRaw(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ANNOUNCEMENTS;
        }
        String clean = raw.trim().toLowerCase();

        for (AdminNotificationCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(clean) || cat.displayName.equalsIgnoreCase(clean)) {
                return cat;
            }
            for (String alias : cat.aliases) {
                if (alias.equalsIgnoreCase(clean)) {
                    return cat;
                }
            }
        }

        // Substring fallback
        if (clean.contains("report")) return REPORTS;
        if (clean.contains("flag")) return FLAGGED_REVIEWS;
        if (clean.contains("submission") || clean.contains("pending")) return LISTING_SUBMISSIONS;
        if (clean.contains("user") || clean.contains("account") || clean.contains("audit")) return USER_ACCOUNTS;
        if (clean.contains("review") || clean.contains("rating")) return FLAGGED_REVIEWS;
        if (clean.contains("listing")) return LISTING_SUBMISSIONS;

        return ANNOUNCEMENTS;
    }

    /**
     * Checks if this category matches a raw category string.
     *
     * @param rawCategory raw category string
     * @return true if matches this category
     */
    public boolean matches(String rawCategory) {
        return fromRaw(rawCategory) == this;
    }
}
