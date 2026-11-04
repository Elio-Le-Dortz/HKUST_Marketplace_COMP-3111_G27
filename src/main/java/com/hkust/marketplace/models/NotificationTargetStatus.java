package com.hkust.marketplace.models;

/**
 * Status resulting from selecting a notification and looking up its linked item.
 * Directly maps to Basic Flow step 4 and Alternative Flow A3 in specs_buyer.tex.
 */
public enum NotificationTargetStatus {
    SUCCESS,
    ITEM_DELETED,
    NO_LINKED_ITEM,
    NOTIFICATION_NOT_FOUND
}
