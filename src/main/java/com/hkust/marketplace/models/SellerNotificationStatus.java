package com.hkust.marketplace.models;

/**
 * Outcome of selecting a notification on the Seller's board (use case 2.7 "View Seller
 * Notifications", US-S07, basic flow steps 3 and 4).
 */
public enum SellerNotificationStatus {

    /** The notification was marked read and its related listing, offer, deal, review or chat is shown. */
    SUCCESS,

    /** The notification was marked read; it is not linked to an item, so only its message is shown. */
    NO_RELATED_ITEM,

    /**
     * The notification was marked read, but its related item no longer exists or can no longer
     * be seen by the Seller, for example a listing they deleted or a review hidden by the admin
     * (alternative flow A3).
     */
    ITEM_UNAVAILABLE,

    /** No notification was selected, or it does not exist on the Seller's board. Nothing was changed. */
    NOTIFICATION_NOT_FOUND
}
