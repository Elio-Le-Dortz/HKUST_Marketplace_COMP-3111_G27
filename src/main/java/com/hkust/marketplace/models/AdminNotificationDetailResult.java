package com.hkust.marketplace.models;

/**
 * Result model wrapping the payload returned when an administrator inspects a notification
 * (Sprint 2 US-A06, Basic Flow Step 4 &amp; Alternative Flow A4).
 */
public class AdminNotificationDetailResult {

    private final AdminNotificationDetailStatus status;
    private final String targetType; // REPORT, LISTING, REVIEW, USER, ANNOUNCEMENT, NONE
    private final Notification notification;
    private final Listing listing;
    private final Report report;
    private final Review review;
    private final User user;
    private final String message;

    private AdminNotificationDetailResult(
            AdminNotificationDetailStatus status,
            String targetType,
            Notification notification,
            Listing listing,
            Report report,
            Review review,
            User user,
            String message) {
        this.status = status;
        this.targetType = targetType;
        this.notification = notification;
        this.listing = listing;
        this.report = report;
        this.review = review;
        this.user = user;
        this.message = message;
    }

    public static AdminNotificationDetailResult forReport(Notification n, Report r, Listing l) {
        return new AdminNotificationDetailResult(
                AdminNotificationDetailStatus.FOUND, "REPORT", n, l, r, null, null, "Report retrieved successfully.");
    }

    public static AdminNotificationDetailResult forListing(Notification n, Listing l) {
        return new AdminNotificationDetailResult(
                AdminNotificationDetailStatus.FOUND, "LISTING", n, l, null, null, null, "Listing retrieved successfully.");
    }

    public static AdminNotificationDetailResult forReview(Notification n, Review r) {
        return new AdminNotificationDetailResult(
                AdminNotificationDetailStatus.FOUND, "REVIEW", n, null, null, r, null, "Review retrieved successfully.");
    }

    public static AdminNotificationDetailResult forUser(Notification n, User u) {
        return new AdminNotificationDetailResult(
                AdminNotificationDetailStatus.FOUND, "USER", n, null, null, null, u, "User account retrieved successfully.");
    }

    public static AdminNotificationDetailResult forAnnouncement(Notification n) {
        return new AdminNotificationDetailResult(
                AdminNotificationDetailStatus.ANNOUNCEMENT_ONLY, "ANNOUNCEMENT", n, null, null, null, null, "Announcement notice.");
    }

    public static AdminNotificationDetailResult itemNoLongerAvailable(Notification n, String targetType, String msg) {
        return new AdminNotificationDetailResult(
                AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, targetType, n, null, null, null, null, msg);
    }

    public static AdminNotificationDetailResult notFound(String msg) {
        return new AdminNotificationDetailResult(
                AdminNotificationDetailStatus.NOTIFICATION_NOT_FOUND, "NONE", null, null, null, null, null, msg);
    }

    public static AdminNotificationDetailResult unauthorized(String msg) {
        return new AdminNotificationDetailResult(
                AdminNotificationDetailStatus.UNAUTHORIZED, "NONE", null, null, null, null, null, msg);
    }

    public boolean isSuccess() {
        return status == AdminNotificationDetailStatus.FOUND || status == AdminNotificationDetailStatus.ANNOUNCEMENT_ONLY;
    }

    public AdminNotificationDetailStatus getStatus() {
        return status;
    }

    public String getTargetType() {
        return targetType;
    }

    public Notification getNotification() {
        return notification;
    }

    public Listing getListing() {
        return listing;
    }

    public Report getReport() {
        return report;
    }

    public Review getReview() {
        return review;
    }

    public User getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "AdminNotificationDetailResult{" +
                "status=" + status +
                ", targetType='" + targetType + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
