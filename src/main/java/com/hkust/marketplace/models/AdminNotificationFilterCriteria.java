package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Filter and search criteria for the Admin Notification Board (Sprint 2 US-A06, specs_admin.tex UC 3.6).
 */
public class AdminNotificationFilterCriteria {

    private final AdminNotificationCategory category;
    private final boolean unreadOnly;
    private final String searchQuery;

    public AdminNotificationFilterCriteria(AdminNotificationCategory category, boolean unreadOnly, String searchQuery) {
        this.category = category;
        this.unreadOnly = unreadOnly;
        this.searchQuery = searchQuery != null && !searchQuery.trim().isEmpty() ? searchQuery.trim() : null;
    }

    public static AdminNotificationFilterCriteria all() {
        return new AdminNotificationFilterCriteria(null, false, null);
    }

    public static AdminNotificationFilterCriteria byCategory(AdminNotificationCategory category) {
        return new AdminNotificationFilterCriteria(category, false, null);
    }

    public static AdminNotificationFilterCriteria unreadOnly() {
        return new AdminNotificationFilterCriteria(null, true, null);
    }

    public static AdminNotificationFilterCriteria withSearch(String searchQuery) {
        return new AdminNotificationFilterCriteria(null, false, searchQuery);
    }

    public AdminNotificationCategory getCategory() {
        return category;
    }

    public boolean isUnreadOnly() {
        return unreadOnly;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    /**
     * Checks if a notification matches these criteria.
     *
     * @param notification the notification to evaluate
     * @return true if the notification satisfies all active criteria
     */
    public boolean matches(Notification notification) {
        if (notification == null) {
            return false;
        }

        // 1. Category check (Alternative Flow A2)
        if (category != null && !category.matches(notification.getCategory())) {
            return false;
        }

        // 2. Unread check
        if (unreadOnly && notification.isRead()) {
            return false;
        }

        // 3. Keyword search
        if (searchQuery != null) {
            String query = searchQuery.toLowerCase();
            String title = notification.getTitle() != null ? notification.getTitle().toLowerCase() : "";
            String message = notification.getMessage() != null ? notification.getMessage().toLowerCase() : "";
            String relatedId = notification.getRelatedItemId() != null ? notification.getRelatedItemId().toLowerCase() : "";
            String rawCat = notification.getCategory() != null ? notification.getCategory().toLowerCase() : "";

            if (!title.contains(query) && !message.contains(query) && !relatedId.contains(query) && !rawCat.contains(query)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdminNotificationFilterCriteria that)) return false;
        return unreadOnly == that.unreadOnly &&
                category == that.category &&
                Objects.equals(searchQuery, that.searchQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, unreadOnly, searchQuery);
    }

    @Override
    public String toString() {
        return "AdminNotificationFilterCriteria{" +
                "category=" + category +
                ", unreadOnly=" + unreadOnly +
                ", searchQuery='" + searchQuery + '\'' +
                '}';
    }
}
