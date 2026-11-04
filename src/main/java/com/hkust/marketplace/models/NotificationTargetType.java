package com.hkust.marketplace.models;

/**
 * Type of domain entity linked to a notification (US-B09 / UC-B09 Basic Flow step 4).
 */
public enum NotificationTargetType {
    OFFER,
    CHAT_THREAD,
    DEAL,
    LISTING,
    REVIEW,
    ANNOUNCEMENT,
    NONE;

    public static NotificationTargetType fromString(String val) {
        if (val == null || val.trim().isEmpty()) {
            return NONE;
        }
        try {
            return NotificationTargetType.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String lower = val.trim().toLowerCase();
            if (lower.contains("offer")) return OFFER;
            if (lower.contains("chat")) return CHAT_THREAD;
            if (lower.contains("deal") || lower.contains("loan")) return DEAL;
            if (lower.contains("list")) return LISTING;
            if (lower.contains("review") || lower.contains("rat")) return REVIEW;
            return NONE;
        }
    }
}
