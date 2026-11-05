package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Result of selecting a notification on the Seller's board (use case 2.7 "View Seller
 * Notifications", US-S07, basic flow steps 3 and 4, alternative flow A3): the notification,
 * its category on the board, and the related item to display.
 *
 * <p>The related item is one of the classes of the class diagram that a notification can be
 * about: a {@link Listing}, an {@link Offer}, a {@link Deal}, a {@link Review} or a
 * {@link ChatThread}. Its kind is given by {@link #getTargetType()}, and the typed getters
 * return it, or {@code null} when the item is of another kind.</p>
 *
 * <p>Instances are immutable and created through the static factory methods. The message is
 * always meant to be shown to the Seller.</p>
 */
public final class SellerNotificationResult {

    /** Outcome of the selection. */
    private final SellerNotificationStatus status;
    /** Message for the Seller, explaining the outcome. */
    private final String message;
    /** The selected notification, {@code null} if it was not found. */
    private final Notification notification;
    /** Kind of the related item, {@link NotificationTargetType#NONE} if there is none. */
    private final NotificationTargetType targetType;
    /** The related item, {@code null} if there is none or it is no longer available. */
    private final Object target;

    /**
     * Creates a result. Use the static factory methods instead.
     *
     * @param status       outcome of the selection
     * @param message      message for the Seller
     * @param notification the selected notification, may be {@code null}
     * @param targetType   kind of the related item
     * @param target       the related item, may be {@code null}
     */
    private SellerNotificationResult(SellerNotificationStatus status, String message, Notification notification,
                                     NotificationTargetType targetType, Object target) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = message == null ? "" : message;
        this.notification = notification;
        this.targetType = targetType == null ? NotificationTargetType.NONE : targetType;
        this.target = target;
    }

    /**
     * Creates the result of a notification whose related item is shown (basic flow, step 4).
     *
     * @param notification the selected notification
     * @param targetType   kind of the related item
     * @param target       the related item
     * @param message      message for the Seller
     * @return a successful result holding the related item
     * @throws NullPointerException if an argument other than {@code message} is {@code null}
     */
    public static SellerNotificationResult opened(Notification notification, NotificationTargetType targetType,
                                                  Object target, String message) {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(target, "target");
        return new SellerNotificationResult(SellerNotificationStatus.SUCCESS, message, notification, targetType, target);
    }

    /**
     * Creates the result of a notification that is not linked to an item, so that only its
     * message is shown.
     *
     * @param notification the selected notification
     * @param message      message for the Seller
     * @return the result
     * @throws NullPointerException if {@code notification} is {@code null}
     */
    public static SellerNotificationResult noRelatedItem(Notification notification, String message) {
        Objects.requireNonNull(notification, "notification");
        return new SellerNotificationResult(SellerNotificationStatus.NO_RELATED_ITEM, message, notification,
                NotificationTargetType.NONE, null);
    }

    /**
     * Creates the result of a notification whose related item no longer exists or can no
     * longer be seen by the Seller (alternative flow A3). The item itself is not given.
     *
     * @param notification the selected notification
     * @param targetType   kind of the related item
     * @param message      message for the Seller, saying that the item is no longer available
     * @return the result
     * @throws NullPointerException if {@code notification} or {@code targetType} is {@code null}
     */
    public static SellerNotificationResult itemUnavailable(Notification notification,
                                                           NotificationTargetType targetType, String message) {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(targetType, "targetType");
        return new SellerNotificationResult(SellerNotificationStatus.ITEM_UNAVAILABLE, message, notification,
                targetType, null);
    }

    /**
     * Creates the result of a selection that matches no notification on the Seller's board.
     *
     * @param message message for the Seller
     * @return the result, holding no notification
     */
    public static SellerNotificationResult notFound(String message) {
        return new SellerNotificationResult(SellerNotificationStatus.NOTIFICATION_NOT_FOUND, message, null,
                NotificationTargetType.NONE, null);
    }

    /**
     * Tells whether the related item is shown.
     *
     * @return {@code true} if the status is {@link SellerNotificationStatus#SUCCESS}
     */
    public boolean isSuccess() {
        return status == SellerNotificationStatus.SUCCESS;
    }

    /**
     * Returns the outcome of the selection.
     *
     * @return the status, never {@code null}
     */
    public SellerNotificationStatus getStatus() {
        return status;
    }

    /**
     * Returns the message for the Seller.
     *
     * @return the message, never {@code null}
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the selected notification.
     *
     * @return the notification, or {@code null} if it was not found
     */
    public Notification getNotification() {
        return notification;
    }

    /**
     * Returns the category of the selected notification on the Seller's board.
     *
     * @return the category, or {@code null} if the notification was not found
     */
    public SellerNotificationCategory getCategory() {
        return notification == null ? null : SellerNotificationCategory.of(notification);
    }

    /**
     * Returns the kind of the related item.
     *
     * @return the kind, {@link NotificationTargetType#NONE} if the notification has no related item
     */
    public NotificationTargetType getTargetType() {
        return targetType;
    }

    /**
     * Returns the related listing.
     *
     * @return the listing, or {@code null} if the related item is not an available listing
     */
    public Listing getListing() {
        return target instanceof Listing listing ? listing : null;
    }

    /**
     * Returns the related offer, borrow request or claim.
     *
     * @return the offer, or {@code null} if the related item is not an available offer
     */
    public Offer getOffer() {
        return target instanceof Offer offer ? offer : null;
    }

    /**
     * Returns the related deal.
     *
     * @return the deal, or {@code null} if the related item is not an available deal
     */
    public Deal getDeal() {
        return target instanceof Deal deal ? deal : null;
    }

    /**
     * Returns the related review.
     *
     * @return the review, or {@code null} if the related item is not an available review
     */
    public Review getReview() {
        return target instanceof Review review ? review : null;
    }

    /**
     * Returns the related chat thread.
     *
     * @return the chat thread, or {@code null} if the related item is not an available chat
     */
    public ChatThread getChatThread() {
        return target instanceof ChatThread thread ? thread : null;
    }

    /**
     * Describes the result for logs and test failures.
     *
     * @return the status, the kind of the related item and the message
     */
    @Override
    public String toString() {
        return "SellerNotificationResult[status=" + status + ", targetType=" + targetType + ", message=" + message + "]";
    }
}
