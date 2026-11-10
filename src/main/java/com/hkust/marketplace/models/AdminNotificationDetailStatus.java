package com.hkust.marketplace.models;

/**
 * Status codes for retrieving the target item linked to an admin notification (Sprint 2 US-A06, Basic Flow &amp; Alternative Flow A4).
 */
public enum AdminNotificationDetailStatus {
    FOUND,
    ITEM_NO_LONGER_AVAILABLE,
    ANNOUNCEMENT_ONLY,
    NOTIFICATION_NOT_FOUND,
    UNAUTHORIZED
}
