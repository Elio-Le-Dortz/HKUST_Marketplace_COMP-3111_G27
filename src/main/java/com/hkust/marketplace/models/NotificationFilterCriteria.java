package com.hkust.marketplace.models;

/**
 * Filter parameters for querying notifications on the Buyer Notification Board.
 */
public class NotificationFilterCriteria {

    private NotificationCategory category;
    private Boolean unreadOnly;
    private String searchQuery;

    public NotificationFilterCriteria() {
    }

    public NotificationFilterCriteria(NotificationCategory category, Boolean unreadOnly, String searchQuery) {
        this.category = category;
        this.unreadOnly = unreadOnly;
        this.searchQuery = searchQuery;
    }

    public static NotificationFilterCriteria all() {
        return new NotificationFilterCriteria(null, false, null);
    }

    public static NotificationFilterCriteria forCategory(NotificationCategory category) {
        return new NotificationFilterCriteria(category, false, null);
    }

    public static NotificationFilterCriteria unreadOnly() {
        return new NotificationFilterCriteria(null, true, null);
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public void setCategory(NotificationCategory category) {
        this.category = category;
    }

    public Boolean isUnreadOnly() {
        return Boolean.TRUE.equals(unreadOnly);
    }

    public void setUnreadOnly(Boolean unreadOnly) {
        this.unreadOnly = unreadOnly;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }
}
