package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationFilterCriteria;
import com.hkust.marketplace.models.NotificationTargetResult;
import com.hkust.marketplace.models.NotificationTargetStatus;
import com.hkust.marketplace.models.NotificationTargetType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.utils.DummyDataService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service encapsulating business rules and data operations for the Buyer Notification Board
 * (Sprint 2 US-B09, Use Case 1.8 in specs_buyer.tex).
 * Decoupled from UI controllers for automated JUnit 5 and JaCoCo verification.
 */
public class BuyerNotificationService {

    private final DummyDataService dataService;

    public BuyerNotificationService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Retrieves filtered notifications for a given user, sorted newest first (Basic Flow step 2).
     *
     * @param username username of the buyer
     * @param criteria filter parameters (category, unreadOnly, search query)
     * @return list of matching notifications sorted by timestamp descending
     */
    public synchronized List<Notification> getNotifications(String username, NotificationFilterCriteria criteria) {
        if (username == null || username.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String targetUser = username.trim();
        List<Notification> userNotifs = dataService.getNotificationsFor(targetUser);

        if (criteria == null) {
            return userNotifs;
        }

        return userNotifs.stream()
                .filter(n -> {
                    // Category filter (Alternative Flow A2)
                    if (criteria.getCategory() != null) {
                        if (!criteria.getCategory().matches(n.getCategory())) {
                            return false;
                        }
                    }
                    // Unread filter
                    if (criteria.isUnreadOnly() && n.isRead()) {
                        return false;
                    }
                    // Keyword search filter (title or message)
                    if (criteria.getSearchQuery() != null && !criteria.getSearchQuery().trim().isEmpty()) {
                        String query = criteria.getSearchQuery().trim().toLowerCase();
                        String title = n.getTitle() != null ? n.getTitle().toLowerCase() : "";
                        String msg = n.getMessage() != null ? n.getMessage().toLowerCase() : "";
                        if (!title.contains(query) && !msg.contains(query)) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Notification::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Overload to get notifications by specific category.
     */
    public synchronized List<Notification> getNotifications(String username, NotificationCategory category) {
        return getNotifications(username, new NotificationFilterCriteria(category, false, null));
    }

    /**
     * Overload to get all notifications for a user, newest first.
     */
    public synchronized List<Notification> getNotifications(String username) {
        return getNotifications(username, NotificationFilterCriteria.all());
    }

    /**
     * Returns total count of unread notifications for a user.
     */
    public synchronized int getUnreadCount(String username) {
        if (username == null || username.trim().isEmpty()) {
            return 0;
        }
        return (int) dataService.getNotificationsFor(username.trim()).stream()
                .filter(Notification::isUnread)
                .count();
    }

    /**
     * Computes unread count grouped by category for quick badge display.
     */
    public synchronized Map<NotificationCategory, Long> getUnreadCountsByCategory(String username) {
        Map<NotificationCategory, Long> map = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory cat : NotificationCategory.values()) {
            map.put(cat, 0L);
        }
        if (username == null || username.trim().isEmpty()) {
            return map;
        }

        List<Notification> notifs = dataService.getNotificationsFor(username.trim());
        for (Notification n : notifs) {
            if (n.isUnread()) {
                NotificationCategory cat = NotificationCategory.fromRaw(n.getCategory());
                map.put(cat, map.getOrDefault(cat, 0L) + 1);
            }
        }
        return map;
    }

    /**
     * Computes total notification count grouped by category.
     */
    public synchronized Map<NotificationCategory, Long> getTotalCountsByCategory(String username) {
        Map<NotificationCategory, Long> map = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory cat : NotificationCategory.values()) {
            map.put(cat, 0L);
        }
        if (username == null || username.trim().isEmpty()) {
            return map;
        }

        List<Notification> notifs = dataService.getNotificationsFor(username.trim());
        for (Notification n : notifs) {
            NotificationCategory cat = NotificationCategory.fromRaw(n.getCategory());
            map.put(cat, map.getOrDefault(cat, 0L) + 1);
        }
        return map;
    }

    /**
     * Marks a specific notification as read.
     *
     * @param username       owner of the notification
     * @param notificationId ID of the notification
     * @return true if updated, false if not found
     */
    public synchronized boolean markAsRead(String username, String notificationId) {
        Notification notif = findNotification(username, notificationId);
        if (notif != null) {
            notif.setRead(true);
            return true;
        }
        return false;
    }

    /**
     * Marks a specific notification as unread.
     */
    public synchronized boolean markAsUnread(String username, String notificationId) {
        Notification notif = findNotification(username, notificationId);
        if (notif != null) {
            notif.setRead(false);
            return true;
        }
        return false;
    }

    /**
     * Marks all notifications for a user as read.
     *
     * @param username user whose notifications to mark
     * @return count of unread notifications that were changed to read
     */
    public synchronized int markAllAsRead(String username) {
        if (username == null || username.trim().isEmpty()) {
            return 0;
        }
        List<Notification> notifs = dataService.getNotificationsFor(username.trim());
        int count = 0;
        for (Notification n : notifs) {
            if (n.isUnread()) {
                n.setRead(true);
                count++;
            }
        }
        return count;
    }

    /**
     * Deletes a single notification.
     */
    public synchronized boolean deleteNotification(String username, String notificationId) {
        if (username == null || notificationId == null) {
            return false;
        }
        Notification notif = findNotification(username, notificationId);
        if (notif != null) {
            return dataService.removeNotification(notif.getId());
        }
        return false;
    }

    /**
     * Clears all notifications for a user.
     */
    public synchronized int clearAllNotifications(String username) {
        if (username == null || username.trim().isEmpty()) {
            return 0;
        }
        return dataService.removeNotificationsFor(username.trim());
    }

    /**
     * Resolves the linked item when a user selects a notification (Basic Flow step 4 & Alternative Flow A3).
     * Marks the notification as read, and resolves the related entity.
     * If the related item is no longer available, returns ITEM_DELETED status.
     *
     * @param username       logged-in buyer
     * @param notificationId ID of the selected notification
     * @return NotificationTargetResult detailing the resolved item or deleted state
     */
    public synchronized NotificationTargetResult resolveNotificationTarget(String username, String notificationId) {
        Notification notif = findNotification(username, notificationId);
        if (notif == null) {
            return NotificationTargetResult.notFound("Notification not found.");
        }

        // Always mark as read upon selection (Basic Flow step 4 & Alternative Flow A3 step 1)
        notif.setRead(true);

        NotificationTargetType targetType = NotificationTargetType.fromString(notif.getRelatedItemType());
        if (targetType == NotificationTargetType.NONE) {
            NotificationCategory cat = NotificationCategory.fromRaw(notif.getCategory());
            switch (cat) {
                case OFFERS:
                    targetType = NotificationTargetType.OFFER;
                    break;
                case CHAT:
                    targetType = NotificationTargetType.CHAT_THREAD;
                    break;
                case BORROW_REMINDERS:
                    targetType = NotificationTargetType.DEAL;
                    break;
                case LISTINGS:
                    targetType = NotificationTargetType.LISTING;
                    break;
                case REVIEWS:
                    targetType = NotificationTargetType.REVIEW;
                    break;
                default:
                    targetType = NotificationTargetType.ANNOUNCEMENT;
                    break;
            }
        }

        String itemId = notif.getRelatedItemId();

        switch (targetType) {
            case OFFER:
                if (itemId != null) {
                    Offer offer = dataService.getAllOffers().stream()
                            .filter(o -> o.getId().equalsIgnoreCase(itemId))
                            .findFirst().orElse(null);
                    if (offer != null) {
                        return NotificationTargetResult.success(notif, NotificationTargetType.OFFER, offer);
                    }
                }
                return NotificationTargetResult.itemDeleted(notif, NotificationTargetType.OFFER,
                        "The related offer is no longer available.");

            case CHAT_THREAD:
                if (itemId != null) {
                    ChatThread thread = dataService.getAllChatThreads().stream()
                            .filter(t -> t.getId().equalsIgnoreCase(itemId))
                            .findFirst().orElse(null);
                    if (thread != null) {
                        Listing listing = dataService.getListing(thread.getListingId());
                        if (listing != null) {
                            return NotificationTargetResult.success(notif, NotificationTargetType.CHAT_THREAD, thread);
                        }
                    }
                }
                return NotificationTargetResult.itemDeleted(notif, NotificationTargetType.CHAT_THREAD,
                        "The related chat thread or its listing is no longer available.");

            case DEAL:
                if (itemId != null) {
                    Deal deal = dataService.getAllDeals().stream()
                            .filter(d -> d.getId().equalsIgnoreCase(itemId))
                            .findFirst().orElse(null);
                    if (deal != null) {
                        return NotificationTargetResult.success(notif, NotificationTargetType.DEAL, deal);
                    }
                }
                return NotificationTargetResult.itemDeleted(notif, NotificationTargetType.DEAL,
                        "The related deal or borrowed item record is no longer available.");

            case LISTING:
                if (itemId != null) {
                    Listing listing = dataService.getListing(itemId);
                    if (listing != null && listing.getStatus() != ListingStatus.REJECTED && listing.getStatus() != ListingStatus.REMOVED) {
                        return NotificationTargetResult.success(notif, NotificationTargetType.LISTING, listing);
                    }
                }
                return NotificationTargetResult.itemDeleted(notif, NotificationTargetType.LISTING,
                        "The related listing is no longer available.");

            case REVIEW:
                if (itemId != null) {
                    Review review = dataService.getAllReviews().stream()
                            .filter(r -> r.getId().equalsIgnoreCase(itemId) && !r.isHidden())
                            .findFirst().orElse(null);
                    if (review != null) {
                        return NotificationTargetResult.success(notif, NotificationTargetType.REVIEW, review);
                    }
                }
                return NotificationTargetResult.itemDeleted(notif, NotificationTargetType.REVIEW,
                        "The related review is no longer available.");

            case ANNOUNCEMENT:
            case NONE:
            default:
                return NotificationTargetResult.noLinkedItem(notif,
                        "This notification is an informational announcement with no separate entity link.");
        }
    }

    /**
     * Checks all active borrowed items for a buyer and generates due-date reminder notifications
     * if approaching or overdue (Task 1.8 requirement).
     *
     * @param username buyer username
     * @return list of newly generated reminder notifications
     */
    public synchronized List<Notification> checkAndGenerateBorrowReminders(String username) {
        List<Notification> generated = new ArrayList<>();
        if (username == null || username.trim().isEmpty()) {
            return generated;
        }

        List<Deal> buyerDeals = dataService.getDealsForBuyer(username.trim());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
        LocalDateTime now = LocalDateTime.now();

        for (Deal deal : buyerDeals) {
            if (deal.isLoan() && deal.isActive() && deal.getDueDate() != null) {
                // If due within 48 hours or overdue
                boolean dueSoonOrOverdue = deal.getDueDate().isBefore(now.plusHours(48));
                if (dueSoonOrOverdue) {
                    // Avoid duplicate reminder if one was already issued for this deal
                    boolean alreadyNotified = dataService.getNotificationsFor(username.trim()).stream()
                            .anyMatch(n -> NotificationCategory.BORROW_REMINDERS.matches(n.getCategory())
                                    && deal.getId().equalsIgnoreCase(n.getRelatedItemId()));

                    if (!alreadyNotified) {
                        boolean isOverdue = deal.isExpired();
                        String title = isOverdue ? "Item Return Overdue" : "Borrow Due-Date Reminder";
                        String msg = isOverdue
                                ? "Your borrowed item '" + deal.getListingTitle() + "' is overdue (was due on " + deal.getDueDate().format(formatter) + "). Please return it as soon as possible."
                                : "Reminder: Borrowed item '" + deal.getListingTitle() + "' is due on " + deal.getDueDate().format(formatter) + ". Please prepare to return it.";

                        Notification notif = new Notification(
                                username.trim(),
                                title,
                                msg,
                                NotificationCategory.BORROW_REMINDERS.getDisplayName(),
                                deal.getId(),
                                "DEAL"
                        );
                        dataService.addNotification(notif);
                        generated.add(notif);
                    }
                }
            }
        }
        return generated;
    }

    /**
     * Notifies all buyers who held an open/active offer when an administrator removes a listing
     * (Task 1.8 / Task 3.7 specification).
     *
     * @param listing       the removed listing
     * @param removalReason optional administrative reason
     */
    public synchronized void notifyBuyersOfListingRemoval(Listing listing, String removalReason) {
        if (listing == null) return;

        List<Offer> activeOffers = dataService.getOffersForListing(listing.getId()).stream()
                .filter(o -> o.getStatus() == OfferStatus.PENDING || o.getStatus() == OfferStatus.COUNTERED)
                .collect(Collectors.toList());

        List<String> buyerUsernames = activeOffers.stream()
                .map(Offer::getBuyerUsername)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String reasonSuffix = (removalReason != null && !removalReason.isBlank())
                ? " Reason: " + removalReason.trim()
                : "";

        for (String buyer : buyerUsernames) {
            Notification notif = new Notification(
                    buyer,
                    "Listing Removed by Admin",
                    "A listing you had an open offer on ('" + listing.getTitle() + "') was removed by the administrator." + reasonSuffix,
                    NotificationCategory.LISTINGS.getDisplayName(),
                    listing.getId(),
                    "LISTING"
            );
            dataService.addNotification(notif);
        }
    }

    /**
     * Notifies the buyer when a seller replies to their review (Task 1.8 / Use Case 1.8).
     *
     * @param review the review containing seller's reply
     */
    public synchronized void notifyReviewReply(Review review) {
        if (review == null || review.getBuyerUsername() == null || review.getReply() == null) {
            return;
        }

        Notification notif = new Notification(
                review.getBuyerUsername(),
                "Seller Replied to Review",
                review.getSellerUsername() + " replied to your review on '" + review.getListingTitle() + "': \"" + review.getReply().trim() + "\"",
                NotificationCategory.REVIEWS.getDisplayName(),
                review.getId(),
                "REVIEW"
        );
        dataService.addNotification(notif);
    }

    private Notification findNotification(String username, String notificationId) {
        if (username == null || notificationId == null) {
            return null;
        }
        return dataService.getNotificationsFor(username.trim()).stream()
                .filter(n -> n.getId().equalsIgnoreCase(notificationId.trim()))
                .findFirst().orElse(null);
    }
}
