package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationTargetType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SellerNotificationCategory;
import com.hkust.marketplace.models.SellerNotificationFilter;
import com.hkust.marketplace.models.SellerNotificationResult;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Business logic of use case 2.7 "View Seller Notifications" (US-S07): the Seller reads their
 * notifications (listing approvals and rejections, new offers, borrow requests and claims,
 * reviews received, completed deals and announcements), filters them by category, and selects
 * one to see the listing, offer, deal or review it is about. Acceptance criteria of the Sprint 2
 * backlog: the board shows the admin's rejection reason, and a notification links to the
 * relevant offer or deal.
 *
 * <p><b>Class diagram.</b> A notification is the {@link Notification} received by the Seller
 * ({@code User 1 --> * Notification : Receives}); its {@code category}, {@code message},
 * {@code timestamp} and {@code read} attributes are shown on the board. The board's categories
 * are those of the specification ({@link SellerNotificationCategory}); they are computed from
 * the stored category, so the notifications written by the Buyer and Admin portals are not
 * changed. The related item is found through the notification's {@code relatedItemType} and
 * {@code relatedItemId}: a {@link Listing}, an {@link Offer}, a {@link Deal}, a {@link Review} or
 * a {@link ChatThread}. Nothing is ever deleted (soft-delete policy): a listing the Seller
 * deleted, or a review hidden by the admin, is reported as no longer available (alternative
 * flow A3).</p>
 *
 * <p><b>Access control.</b> Every public instance method first calls
 * {@link SessionManager#requireSeller()}, so it can only be used during an active Seller session
 * (use case 2.2 special requirement). A Seller only sees and changes their own notifications,
 * and a notification only opens an item that belongs to them.</p>
 *
 * <p><b>Atomicity.</b> Reading the board, selecting a notification and marking notifications
 * read run while holding the lock of the data store ({@link DummyDataService}), which every
 * portal also takes when it adds a notification. So the Seller always sees a complete list
 * while buyers send offers, and when the same notifications are marked read at the same time,
 * each one is counted once. Inside the lock, only the data store is called.</p>
 *
 * <p>This class does not use JavaFX, so every flow is unit tested without a display.</p>
 */
public class SellerNotificationService {

    /** Message when no notification is selected. */
    public static final String NOTIFICATION_NOT_SELECTED = "No notification was selected.";
    /** Message when the notification does not exist on the Seller's board. */
    public static final String NOTIFICATION_NOT_FOUND = "This notification is no longer on your board.";
    /** Message of a notification that is not linked to an item. */
    public static final String NO_RELATED_ITEM =
            "This notification is not linked to a listing, offer, deal or review: its message says it all.";
    /** Message when the listing of a notification was deleted (alternative flow A3). */
    public static final String LISTING_UNAVAILABLE = "The listing of this notification is no longer available.";
    /** Message when the offer of a notification no longer exists (alternative flow A3). */
    public static final String OFFER_UNAVAILABLE = "The offer of this notification is no longer available.";
    /** Message when the deal of a notification no longer exists (alternative flow A3). */
    public static final String DEAL_UNAVAILABLE = "The deal of this notification is no longer available.";
    /** Message when the review of a notification no longer exists (alternative flow A3). */
    public static final String REVIEW_UNAVAILABLE = "The review of this notification is no longer available.";
    /** Message when the review of a notification was hidden by the admin (alternative flow A3). */
    public static final String REVIEW_HIDDEN =
            "The admin has hidden this review, so it is no longer shown or counted in your rating.";
    /** Message when the chat of a notification no longer exists (alternative flow A3). */
    public static final String CHAT_UNAVAILABLE = "The chat of this notification is no longer available.";

    /**
     * The admin's reason at the end of a Listing Review notification, as written by the listing
     * review (use case 3.2) and take-down (use case 3.7) of the Admin Portal, for example
     * {@code Your listing 'Desk' was rejected. Reason: Blurry photos}.
     */
    private static final Pattern ADMIN_REASON = Pattern.compile("\\.\\s+Reason:\\s*(.*)", Pattern.DOTALL);

    /** Order of the board: newest first (basic flow, step 2); a notification without timestamp comes last. */
    private static final Comparator<Notification> NEWEST_FIRST = Comparator.comparing(Notification::getTimestamp,
            Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()));

    /** Data store holding the notifications and the items they are about. */
    private final DummyDataService dataService;

    /**
     * Creates the service on the application's data store.
     */
    public SellerNotificationService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Creates the service on a given data store.
     *
     * @param dataService the data store holding the notifications
     * @throws NullPointerException if {@code dataService} is {@code null}
     */
    public SellerNotificationService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
    }

    // =====================================================================
    // Basic flow, step 2: the Seller's notifications
    // =====================================================================

    /**
     * Returns the logged-in Seller's notifications, newest first (basic flow, step 2). Two
     * notifications with the same timestamp are listed newest received first. Reading the board
     * does not mark anything read. An empty list means that the Seller has no notifications
     * (alternative flow A1).
     *
     * @return the Seller's notifications, newest first; the list cannot be changed
     * @throws SecurityException if no active Seller is logged in
     */
    public List<Notification> getMyNotifications() {
        return getMyNotifications(SellerNotificationFilter.all());
    }

    /**
     * Returns the logged-in Seller's notifications that pass a filter, newest first: only one
     * category (alternative flow A2), only the unread ones, or those whose title or message
     * contains a text.
     *
     * @param filter the filter, {@code null} to get every notification
     * @return the matching notifications, newest first; the list cannot be changed
     * @throws SecurityException if no active Seller is logged in
     */
    public List<Notification> getMyNotifications(SellerNotificationFilter filter) {
        Seller seller = SessionManager.requireSeller();
        SellerNotificationFilter applied = filter == null ? SellerNotificationFilter.all() : filter;
        synchronized (dataService) {
            return notificationsOf(seller).stream().filter(applied::matches).toList();
        }
    }

    /**
     * Returns the number of unread notifications of the logged-in Seller, shown next to the
     * Notifications button of the sidebar.
     *
     * @return the number of unread notifications
     * @throws SecurityException if no active Seller is logged in
     */
    public int getUnreadCount() {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            return (int) notificationsOf(seller).stream().filter(Notification::isUnread).count();
        }
    }

    /**
     * Returns how many notifications of the logged-in Seller each category holds, shown in the
     * category filter (alternative flow A2).
     *
     * @return the number of notifications of each category, every category included; the map
     *         cannot be changed
     * @throws SecurityException if no active Seller is logged in
     */
    public Map<SellerNotificationCategory, Integer> getCategoryCounts() {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            return countByCategory(notificationsOf(seller));
        }
    }

    /**
     * Returns how many unread notifications of the logged-in Seller each category holds.
     *
     * @return the number of unread notifications of each category, every category included;
     *         the map cannot be changed
     * @throws SecurityException if no active Seller is logged in
     */
    public Map<SellerNotificationCategory, Integer> getUnreadCategoryCounts() {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            return countByCategory(notificationsOf(seller).stream().filter(Notification::isUnread).toList());
        }
    }

    // =====================================================================
    // Basic flow, steps 3 and 4: select a notification (alternative flow A3)
    // =====================================================================

    /**
     * Selects a notification of the logged-in Seller (basic flow, step 3): marks it read and
     * returns the listing, offer, deal, review or chat it is about (step 4). When the item no
     * longer exists, or can no longer be seen by the Seller (a listing they deleted, a review
     * hidden by the admin, an item of another user), the notification is still marked read and
     * the result says that the item is no longer available (alternative flow A3). A notification
     * that is not linked to an item is marked read and only its message is shown.
     *
     * @param notificationId identifier of the notification
     * @return the result holding the related item, or why it cannot be shown
     * @throws SecurityException if no active Seller is logged in
     */
    public SellerNotificationResult openNotification(String notificationId) {
        Seller seller = SessionManager.requireSeller();
        if (notificationId == null || notificationId.isBlank()) {
            return SellerNotificationResult.notFound(NOTIFICATION_NOT_SELECTED);
        }
        synchronized (dataService) {
            Notification notification = findOwnNotification(notificationId.trim(), seller);
            if (notification == null) {
                return SellerNotificationResult.notFound(NOTIFICATION_NOT_FOUND);
            }
            notification.setRead(true);
            return relatedItemOf(notification, seller);
        }
    }

    /**
     * Marks one notification of the logged-in Seller read, without opening its related item.
     *
     * @param notificationId identifier of the notification
     * @return {@code true} if the notification is on the Seller's board (it is now read)
     * @throws SecurityException if no active Seller is logged in
     */
    public boolean markAsRead(String notificationId) {
        return setRead(notificationId, true);
    }

    /**
     * Marks one notification of the logged-in Seller unread again, so that it is highlighted
     * on the board.
     *
     * @param notificationId identifier of the notification
     * @return {@code true} if the notification is on the Seller's board (it is now unread)
     * @throws SecurityException if no active Seller is logged in
     */
    public boolean markAsUnread(String notificationId) {
        return setRead(notificationId, false);
    }

    /**
     * Marks every notification of the logged-in Seller read.
     *
     * @return the number of notifications that were unread
     * @throws SecurityException if no active Seller is logged in
     */
    public int markAllAsRead() {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            int marked = 0;
            for (Notification notification : notificationsOf(seller)) {
                if (notification.isUnread()) {
                    notification.setRead(true);
                    marked++;
                }
            }
            return marked;
        }
    }

    // =====================================================================
    // Display helper (also used by the Seller dashboard)
    // =====================================================================

    /**
     * Returns the admin's reason given in a Listing Review notification: the reason of a
     * rejection (use case 3.2) or of a take-down (use case 3.7). It is read from the
     * notification's message, so it stays the reason of that decision even after the Seller
     * changes the listing.
     *
     * @param notification the notification
     * @return the reason, trimmed, or {@code null} if the notification is not a Listing Review
     *         notification or gives no reason
     * @throws NullPointerException if {@code notification} is {@code null}
     */
    public static String adminReasonOf(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        if (SellerNotificationCategory.of(notification) != SellerNotificationCategory.LISTING_REVIEW
                || notification.getMessage() == null) {
            return null;
        }
        Matcher matcher = ADMIN_REASON.matcher(notification.getMessage());
        if (!matcher.find()) {
            return null;
        }
        String reason = matcher.group(1).trim();
        return reason.isEmpty() ? null : reason;
    }

    // =====================================================================
    // Internal helpers (always called while holding the data store lock)
    // =====================================================================

    /**
     * Returns the Seller's notifications, newest first; with the same timestamp, the one
     * received last comes first.
     *
     * @param seller the Seller
     * @return a new list of the Seller's notifications
     */
    private List<Notification> notificationsOf(Seller seller) {
        List<Notification> mine = new ArrayList<>();
        for (Notification notification : dataService.getAllNotifications()) {
            if (isRecipient(notification, seller)) {
                mine.add(notification);
            }
        }
        // The data store keeps the order of arrival: reversing it, then sorting stably by
        // timestamp, lists notifications with the same timestamp newest received first.
        Collections.reverse(mine);
        mine.sort(NEWEST_FIRST);
        return mine;
    }

    /**
     * Tells whether a Seller is the recipient of a notification.
     *
     * @param notification the notification
     * @param seller       the Seller
     * @return {@code true} if the notification was sent to the Seller
     */
    private static boolean isRecipient(Notification notification, Seller seller) {
        return notification != null && seller.getUsername().equalsIgnoreCase(notification.getUsername());
    }

    /**
     * Finds a notification of the Seller by its identifier, ignoring case.
     *
     * @param notificationId the trimmed identifier
     * @param seller         the Seller
     * @return the notification, or {@code null} if the Seller has none with this identifier
     */
    private Notification findOwnNotification(String notificationId, Seller seller) {
        for (Notification notification : dataService.getAllNotifications()) {
            if (isRecipient(notification, seller) && notificationId.equalsIgnoreCase(notification.getId())) {
                return notification;
            }
        }
        return null;
    }

    /**
     * Marks one notification of the logged-in Seller read or unread.
     *
     * @param notificationId identifier of the notification
     * @param read           {@code true} to mark it read
     * @return {@code true} if the notification is on the Seller's board
     */
    private boolean setRead(String notificationId, boolean read) {
        Seller seller = SessionManager.requireSeller();
        if (notificationId == null || notificationId.isBlank()) {
            return false;
        }
        synchronized (dataService) {
            Notification notification = findOwnNotification(notificationId.trim(), seller);
            if (notification == null) {
                return false;
            }
            notification.setRead(read);
            return true;
        }
    }

    /**
     * Counts notifications by category on the Seller's board.
     *
     * @param notifications the notifications to count
     * @return the count of every category, zero included
     */
    private static Map<SellerNotificationCategory, Integer> countByCategory(List<Notification> notifications) {
        Map<SellerNotificationCategory, Integer> counts = new EnumMap<>(SellerNotificationCategory.class);
        for (SellerNotificationCategory category : SellerNotificationCategory.values()) {
            counts.put(category, 0);
        }
        for (Notification notification : notifications) {
            counts.merge(SellerNotificationCategory.of(notification), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Finds the item a notification is about, as long as it still exists and belongs to the
     * Seller (basic flow step 4, alternative flow A3).
     *
     * @param notification the selected notification, already marked read
     * @param seller       the Seller
     * @return the result holding the item, or why it cannot be shown
     */
    private SellerNotificationResult relatedItemOf(Notification notification, Seller seller) {
        NotificationTargetType type = NotificationTargetType.fromString(notification.getRelatedItemType());
        String itemId = notification.getRelatedItemId() == null ? "" : notification.getRelatedItemId().trim();
        if (itemId.isEmpty()) {
            return SellerNotificationResult.noRelatedItem(notification, NO_RELATED_ITEM);
        }
        return switch (type) {
            case LISTING -> openListing(notification, itemId, seller);
            case OFFER -> openOffer(notification, itemId, seller);
            case DEAL -> openDeal(notification, itemId, seller);
            case REVIEW -> openReview(notification, itemId, seller);
            case CHAT_THREAD -> openChat(notification, itemId, seller);
            case ANNOUNCEMENT, NONE -> SellerNotificationResult.noRelatedItem(notification, NO_RELATED_ITEM);
        };
    }

    /**
     * Opens the listing of a notification. A listing the Seller deleted is no longer available;
     * a listing rejected or taken down by the admin is still shown, with the admin's reason.
     *
     * @param notification the notification
     * @param listingId    identifier of the listing
     * @param seller       the Seller
     * @return the result
     */
    private SellerNotificationResult openListing(Notification notification, String listingId, Seller seller) {
        Listing listing = dataService.findListing(listingId);
        if (listing == null || listing.isDeletedBySeller()
                || !seller.getUsername().equalsIgnoreCase(listing.getSellerUsername())) {
            return SellerNotificationResult.itemUnavailable(notification, NotificationTargetType.LISTING,
                    LISTING_UNAVAILABLE);
        }
        return SellerNotificationResult.opened(notification, NotificationTargetType.LISTING, listing,
                "Showing your listing \"" + listing.getTitle() + "\".");
    }

    /**
     * Opens the offer, borrow request or claim of a notification, whatever its status.
     *
     * @param notification the notification
     * @param offerId      identifier of the offer
     * @param seller       the Seller
     * @return the result
     */
    private SellerNotificationResult openOffer(Notification notification, String offerId, Seller seller) {
        Offer offer = dataService.findOffer(offerId);
        if (offer == null || !seller.getUsername().equalsIgnoreCase(offer.getSellerUsername())) {
            return SellerNotificationResult.itemUnavailable(notification, NotificationTargetType.OFFER,
                    OFFER_UNAVAILABLE);
        }
        return SellerNotificationResult.opened(notification, NotificationTargetType.OFFER, offer,
                "Showing the " + OfferManagementService.kindOf(offer) + " from @" + offer.getBuyerUsername() + ".");
    }

    /**
     * Opens the deal of a notification.
     *
     * @param notification the notification
     * @param dealId       identifier of the deal
     * @param seller       the Seller
     * @return the result
     */
    private SellerNotificationResult openDeal(Notification notification, String dealId, Seller seller) {
        Deal deal = dataService.findDeal(dealId);
        if (deal == null || !seller.getUsername().equalsIgnoreCase(deal.getSellerUsername())) {
            return SellerNotificationResult.itemUnavailable(notification, NotificationTargetType.DEAL,
                    DEAL_UNAVAILABLE);
        }
        return SellerNotificationResult.opened(notification, NotificationTargetType.DEAL, deal,
                "Showing your deal with @" + deal.getBuyerUsername() + ".");
    }

    /**
     * Opens the review of a notification. A review hidden by the admin is no longer shown to
     * the Seller (use case 3.8).
     *
     * @param notification the notification
     * @param reviewId     identifier of the review
     * @param seller       the Seller
     * @return the result
     */
    private SellerNotificationResult openReview(Notification notification, String reviewId, Seller seller) {
        Review review = null;
        for (Review candidate : dataService.getAllReviews()) {
            if (candidate != null && reviewId.equalsIgnoreCase(candidate.getId())) {
                review = candidate;
                break;
            }
        }
        if (review == null || !seller.getUsername().equalsIgnoreCase(review.getSellerUsername())) {
            return SellerNotificationResult.itemUnavailable(notification, NotificationTargetType.REVIEW,
                    REVIEW_UNAVAILABLE);
        }
        if (review.isHidden()) {
            return SellerNotificationResult.itemUnavailable(notification, NotificationTargetType.REVIEW, REVIEW_HIDDEN);
        }
        return SellerNotificationResult.opened(notification, NotificationTargetType.REVIEW, review,
                "Showing the review from @" + review.getBuyerUsername() + ".");
    }

    /**
     * Opens the chat of a notification, as long as the Seller is its seller.
     *
     * @param notification the notification
     * @param threadId     identifier of the chat thread
     * @param seller       the Seller
     * @return the result
     */
    private SellerNotificationResult openChat(Notification notification, String threadId, Seller seller) {
        ChatThread thread = dataService.findChatThread(threadId);
        if (thread == null || !seller.getUsername().equalsIgnoreCase(thread.getSellerUsername())) {
            return SellerNotificationResult.itemUnavailable(notification, NotificationTargetType.CHAT_THREAD,
                    CHAT_UNAVAILABLE);
        }
        return SellerNotificationResult.opened(notification, NotificationTargetType.CHAT_THREAD, thread,
                "Showing your chat with @" + thread.getBuyerUsername() + ".");
    }
}
