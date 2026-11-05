package com.hkust.marketplace.models;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Categories of the Seller's notification board, as listed by use case 2.7 "View Seller
 * Notifications" (US-S07), in the order of the specification.
 *
 * <p><b>Class diagram.</b> {@link Notification#getCategory() Notification.category} is a free
 * {@code String}, and each portal writes its own words in it (for example {@code "Listings"}
 * for a listing approved by the admin, {@code "Offer"} for a new offer, {@code "Loan"} for a
 * returned item). {@link #of(Notification)} maps these words to the five categories of the
 * Seller Portal without changing the stored notification, so that the Buyer and Admin portals
 * keep their own categories.</p>
 *
 * <p>Chat messages from buyers are listed with {@link #OFFERS_AND_REQUESTS}: in use case 2.5 the
 * Seller opens the chat from the offer it is about, and formal offers are sent from the chat.</p>
 */
public enum SellerNotificationCategory {

    /** Listing approved or rejected by the admin (with the admin's reason), or changed or taken down by the admin. */
    LISTING_REVIEW("Listing Review", "📋",
            Set.of("listing review", "listing reviews", "listings", "listing")),

    /** New offer, borrow request or claim received, an offer withdrawn or answered, or a chat message from a buyer. */
    OFFERS_AND_REQUESTS("Offers And Requests", "🏷️",
            Set.of("offers and requests", "offers & requests", "offers", "offer", "requests", "request",
                    "chat", "message", "messages")),

    /** Rating or review received from a buyer, or a review moderated by the admin. */
    REVIEWS("Reviews", "⭐",
            Set.of("reviews", "review", "rating", "ratings", "feedback")),

    /** A buyer marked a deal complete, or returned a borrowed item. */
    DEALS("Deals", "🤝",
            Set.of("deals", "deal", "loan", "loans", "borrow reminders", "borrow reminder")),

    /** Other important announcements, for example a change to the Seller's account made by the admin. */
    ANNOUNCEMENTS("Announcements", "📢",
            Set.of("announcements", "announcement", "system", "general", "notice"));

    /** Name shown on the notification board. */
    private final String displayName;
    /** Icon shown before the name. */
    private final String icon;
    /** Lower-case words that the portals store in {@code Notification.category} for this category. */
    private final Set<String> rawNames;

    /**
     * Creates a category.
     *
     * @param displayName name shown on the notification board
     * @param icon        icon shown before the name
     * @param rawNames    lower-case words stored in {@code Notification.category} for this category
     */
    SellerNotificationCategory(String displayName, String icon, Set<String> rawNames) {
        this.displayName = displayName;
        this.icon = icon;
        this.rawNames = rawNames;
    }

    /**
     * Returns the name shown on the notification board, as written in use case 2.7.
     *
     * @return the name, for example {@code Listing Review}
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the icon shown before the name.
     *
     * @return the icon
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Returns the category of a notification on the Seller's board.
     *
     * @param notification the notification
     * @return its category, never {@code null}
     * @throws NullPointerException if {@code notification} is {@code null}
     */
    public static SellerNotificationCategory of(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        return fromRaw(notification.getCategory(), notification.getRelatedItemType());
    }

    /**
     * Maps the category stored in a notification to a category of the Seller's board. The
     * stored category is compared without case and surrounding spaces. When it is unknown or
     * empty, the type of the related item decides (a listing is a Listing Review, an offer or a
     * chat is an Offer And Request, a review is a Review, a deal is a Deal); otherwise the
     * notification is an Announcement.
     *
     * @param rawCategory     the category stored in the notification, may be {@code null}
     * @param relatedItemType the type of the related item stored in the notification, may be {@code null}
     * @return the category, never {@code null}
     */
    public static SellerNotificationCategory fromRaw(String rawCategory, String relatedItemType) {
        String raw = rawCategory == null ? "" : rawCategory.trim().toLowerCase(Locale.ROOT);
        for (SellerNotificationCategory category : values()) {
            if (category.rawNames.contains(raw) || category.displayName.toLowerCase(Locale.ROOT).equals(raw)
                    || category.name().toLowerCase(Locale.ROOT).equals(raw)) {
                return category;
            }
        }
        return switch (NotificationTargetType.fromString(relatedItemType)) {
            case LISTING -> LISTING_REVIEW;
            case OFFER, CHAT_THREAD -> OFFERS_AND_REQUESTS;
            case REVIEW -> REVIEWS;
            case DEAL -> DEALS;
            case ANNOUNCEMENT, NONE -> ANNOUNCEMENTS;
        };
    }

    /**
     * Returns the name shown on the notification board.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return displayName;
    }
}
