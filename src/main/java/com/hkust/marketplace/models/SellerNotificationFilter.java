package com.hkust.marketplace.models;

import java.util.Locale;
import java.util.Objects;

/**
 * Filter of the Seller's notification board (use case 2.7 "View Seller Notifications",
 * US-S07, alternative flow A2 "Filter By Category"). Besides the category of the
 * specification, the board can show only the unread notifications and search the title and
 * message, like the Buyer and Admin notification boards.
 *
 * @param category   category to show, {@code null} for every category
 * @param unreadOnly {@code true} to show only the unread notifications
 * @param search     text to look for in the title or message, ignoring case; {@code null} or
 *                   blank to show every notification. It is stored trimmed, never {@code null}.
 */
public record SellerNotificationFilter(SellerNotificationCategory category, boolean unreadOnly, String search) {

    /**
     * Creates a filter, storing the search text trimmed, or empty when it is {@code null}.
     *
     * @param category   category to show, {@code null} for every category
     * @param unreadOnly {@code true} to show only the unread notifications
     * @param search     text to look for, may be {@code null}
     */
    public SellerNotificationFilter {
        search = search == null ? "" : search.trim();
    }

    /**
     * Returns the filter that shows every notification.
     *
     * @return the filter of every category, read or unread, without search text
     */
    public static SellerNotificationFilter all() {
        return new SellerNotificationFilter(null, false, "");
    }

    /**
     * Returns the filter that shows the notifications of one category (alternative flow A2).
     *
     * @param category category to show, {@code null} for every category
     * @return the filter
     */
    public static SellerNotificationFilter ofCategory(SellerNotificationCategory category) {
        return new SellerNotificationFilter(category, false, "");
    }

    /**
     * Tells whether the filter shows every notification.
     *
     * @return {@code true} if no category, unread or search condition is set
     */
    public boolean isEmpty() {
        return category == null && !unreadOnly && search.isEmpty();
    }

    /**
     * Tells whether a notification passes every condition of the filter.
     *
     * @param notification the notification
     * @return {@code true} if it is shown with this filter
     * @throws NullPointerException if {@code notification} is {@code null}
     */
    public boolean matches(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        if (category != null && SellerNotificationCategory.of(notification) != category) {
            return false;
        }
        if (unreadOnly && notification.isRead()) {
            return false;
        }
        if (search.isEmpty()) {
            return true;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        return contains(notification.getTitle(), needle) || contains(notification.getMessage(), needle);
    }

    /**
     * Tells whether a text contains a lower-case search text, ignoring case.
     *
     * @param text   the text, may be {@code null}
     * @param needle the lower-case search text
     * @return {@code true} if the text contains it
     */
    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }
}
