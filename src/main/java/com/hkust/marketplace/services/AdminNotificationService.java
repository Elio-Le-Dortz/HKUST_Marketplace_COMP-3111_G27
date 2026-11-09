package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules, filtering, report resolution,
 * and target item resolution for Admin Notifications (Sprint 2 US-A06, specs_admin.tex UC 3.6).
 *
 * <p>Completely decoupled from JavaFX UI controls to allow full unit testing in headless CI.
 */
public class AdminNotificationService {

    private final DummyDataService dataService;

    public AdminNotificationService() {
        this(DummyDataService.getInstance());
    }

    public AdminNotificationService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Checks if the given username corresponds to an active administrator.
     *
     * @param username the username to verify
     * @return true if active admin, false otherwise
     */
    public boolean isAdminAuthorized(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        if (!SessionManager.isAdmin()) {
            return false;
        }
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin == null || !admin.isActive()) {
            return false;
        }
        return admin.getUsername().equalsIgnoreCase(username.trim());
    }

    /**
     * Retrieves all notifications addressed to the administrator, filtered by the given criteria
     * and ordered newest first (Basic Flow Step 2 & Alternative Flow A2).
     *
     * @param adminUsername the requesting administrator's username
     * @param criteria      filter and search criteria (or null for all)
     * @return list of matching notifications sorted by timestamp descending
     */
    public synchronized List<Notification> getNotifications(String adminUsername, AdminNotificationFilterCriteria criteria) {
        SessionManager.requireAdmin();
        AdminNotificationFilterCriteria effectiveCriteria = criteria != null ? criteria : AdminNotificationFilterCriteria.all();

        // Admin notifications are addressed to "admin" or to specific admin usernames (e.g., admin@ust.hk)
        Set<String> adminAliases = new HashSet<>();
        adminAliases.add("admin");
        adminAliases.add(DummyDataService.SEEDED_ADMIN_USERNAME.toLowerCase());
        if (adminUsername != null && !adminUsername.trim().isEmpty()) {
            adminAliases.add(adminUsername.trim().toLowerCase());
        }

        return dataService.getAllNotifications().stream()
                .filter(n -> n != null && n.getUsername() != null && adminAliases.contains(n.getUsername().toLowerCase()))
                .filter(effectiveCriteria::matches)
                .sorted((n1, n2) -> {
                    LocalDateTime t1 = n1.getTimestamp();
                    LocalDateTime t2 = n2.getTimestamp();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    int cmp = t2.compareTo(t1);
                    if (cmp != 0) return cmp;
                    return (n2.getId() != null && n1.getId() != null) ? n2.getId().compareTo(n1.getId()) : 0;
                })
                .collect(Collectors.toList());
    }

    /**
     * Overload to retrieve notifications for a specific category.
     *
     * @param adminUsername requesting administrator username
     * @param category      target category
     * @return filtered list
     */
    public synchronized List<Notification> getNotifications(String adminUsername, AdminNotificationCategory category) {
        return getNotifications(adminUsername, AdminNotificationFilterCriteria.byCategory(category));
    }

    /**
     * Retrieves a single notification by identifier.
     *
     * @param notificationId notification ID
     * @return Optional containing notification if found
     */
    public synchronized Optional<Notification> getNotification(String notificationId) {
        SessionManager.requireAdmin();
        if (notificationId == null || notificationId.trim().isEmpty()) {
            return Optional.empty();
        }
        return dataService.getAllNotifications().stream()
                .filter(n -> n != null && notificationId.trim().equalsIgnoreCase(n.getId()))
                .findFirst();
    }

    /**
     * Marks a specific notification as read.
     *
     * @param notificationId notification identifier
     * @param adminUsername  requesting administrator username
     * @return true if marked as read, false if notification not found or unauthorized
     */
    public synchronized boolean markAsRead(String notificationId, String adminUsername) {
        SessionManager.requireAdmin();
        if (!isAdminAuthorized(adminUsername)) {
            return false;
        }
        Optional<Notification> opt = getNotification(notificationId);
        if (opt.isPresent()) {
            opt.get().setRead(true);
            return true;
        }
        return false;
    }

    /**
     * Marks all notifications addressed to the administrator as read.
     *
     * @param adminUsername requesting administrator username
     * @return count of notifications updated to read
     */
    public synchronized int markAllAsRead(String adminUsername) {
        SessionManager.requireAdmin();
        if (!isAdminAuthorized(adminUsername)) {
            return 0;
        }
        List<Notification> notifs = getNotifications(adminUsername, AdminNotificationFilterCriteria.unreadOnly());
        int count = 0;
        for (Notification n : notifs) {
            n.setRead(true);
            count++;
        }
        return count;
    }

    /**
     * Returns total count of unread notifications for the administrator.
     *
     * @param adminUsername requesting administrator username
     * @return unread notification count
     */
    public synchronized int getUnreadCount(String adminUsername) {
        SessionManager.requireAdmin();
        return (int) getNotifications(adminUsername, AdminNotificationFilterCriteria.all()).stream()
                .filter(Notification::isUnread)
                .count();
    }

    /**
     * Computes the total notification count grouped by {@link AdminNotificationCategory}.
     *
     * @param adminUsername requesting administrator username
     * @return map from category to total count
     */
    public synchronized Map<AdminNotificationCategory, Long> getCategoryCounts(String adminUsername) {
        SessionManager.requireAdmin();
        Map<AdminNotificationCategory, Long> map = new EnumMap<>(AdminNotificationCategory.class);
        for (AdminNotificationCategory cat : AdminNotificationCategory.values()) {
            map.put(cat, 0L);
        }
        List<Notification> all = getNotifications(adminUsername, AdminNotificationFilterCriteria.all());
        for (Notification n : all) {
            AdminNotificationCategory cat = AdminNotificationCategory.fromRaw(n.getCategory());
            map.put(cat, map.getOrDefault(cat, 0L) + 1);
        }
        return map;
    }

    /**
     * Computes the unread notification count grouped by {@link AdminNotificationCategory}.
     *
     * @param adminUsername requesting administrator username
     * @return map from category to unread count
     */
    public synchronized Map<AdminNotificationCategory, Long> getCategoryUnreadCounts(String adminUsername) {
        SessionManager.requireAdmin();
        Map<AdminNotificationCategory, Long> map = new EnumMap<>(AdminNotificationCategory.class);
        for (AdminNotificationCategory cat : AdminNotificationCategory.values()) {
            map.put(cat, 0L);
        }
        List<Notification> all = getNotifications(adminUsername, AdminNotificationFilterCriteria.all());
        for (Notification n : all) {
            if (n.isUnread()) {
                AdminNotificationCategory cat = AdminNotificationCategory.fromRaw(n.getCategory());
                map.put(cat, map.getOrDefault(cat, 0L) + 1);
            }
        }
        return map;
    }

    /**
     * Inspects a notification: marks it as read and retrieves its related domain object
     * (Basic Flow Step 3 & 4 and Alternative Flow A4).
     *
     * @param notificationId identifier of the notification
     * @param adminUsername  requesting administrator username
     * @return {@link AdminNotificationDetailResult} containing status and resolved domain model
     */
    public synchronized AdminNotificationDetailResult getNotificationDetail(String notificationId, String adminUsername) {
        SessionManager.requireAdmin();
        if (!isAdminAuthorized(adminUsername)) {
            return AdminNotificationDetailResult.unauthorized("User '" + adminUsername + "' is not an active administrator.");
        }
        if (notificationId == null || notificationId.trim().isEmpty()) {
            return AdminNotificationDetailResult.notFound("Notification ID cannot be null or empty.");
        }

        Notification notification = getNotification(notificationId).orElse(null);
        if (notification == null) {
            return AdminNotificationDetailResult.notFound("Notification not found with ID: " + notificationId);
        }

        // Basic Flow Step 4: Mark notification as read
        notification.setRead(true);

        String rawType = notification.getRelatedItemType();
        String relatedId = notification.getRelatedItemId();
        AdminNotificationCategory cat = AdminNotificationCategory.fromRaw(notification.getCategory());

        // 1. Check for Report
        if ("REPORT".equalsIgnoreCase(rawType) || cat == AdminNotificationCategory.REPORTS) {
            Report report = null;
            if (relatedId != null && !relatedId.trim().isEmpty()) {
                report = dataService.findReport(relatedId);
            }
            if (report == null) {
                return AdminNotificationDetailResult.itemNoLongerAvailable(
                        notification, "REPORT", "The reported listing or report record is no longer available.");
            }
            Listing listing = report.getListingId() != null ? dataService.findListing(report.getListingId()) : null;
            return AdminNotificationDetailResult.forReport(notification, report, listing);
        }

        // 2. Check for Listing
        if ("LISTING".equalsIgnoreCase(rawType) || cat == AdminNotificationCategory.LISTING_SUBMISSIONS) {
            Listing listing = null;
            if (relatedId != null && !relatedId.trim().isEmpty()) {
                listing = dataService.findListing(relatedId);
            }
            if (listing == null || listing.getStatus() == ListingStatus.REMOVED) {
                return AdminNotificationDetailResult.itemNoLongerAvailable(
                        notification, "LISTING", "The referenced listing has been deleted or removed.");
            }
            return AdminNotificationDetailResult.forListing(notification, listing);
        }

        // 3. Check for Review
        if ("REVIEW".equalsIgnoreCase(rawType) || cat == AdminNotificationCategory.FLAGGED_REVIEWS) {
            Review review = null;
            if (relatedId != null && !relatedId.trim().isEmpty()) {
                review = dataService.findReview(relatedId);
            }
            if (review == null) {
                return AdminNotificationDetailResult.itemNoLongerAvailable(
                        notification, "REVIEW", "The flagged review is no longer available.");
            }
            return AdminNotificationDetailResult.forReview(notification, review);
        }

        // 4. Check for User Account (Audit Log)
        if ("USER".equalsIgnoreCase(rawType) || cat == AdminNotificationCategory.USER_ACCOUNTS) {
            User targetUser = null;
            if (relatedId != null && !relatedId.trim().isEmpty()) {
                targetUser = dataService.findUser(relatedId);
            }
            if (targetUser == null || targetUser.isDeleted()) {
                return AdminNotificationDetailResult.itemNoLongerAvailable(
                        notification, "USER", "The user account has been deleted or no longer exists.");
            }
            return AdminNotificationDetailResult.forUser(notification, targetUser);
        }

        // 5. General Announcement / System message
        return AdminNotificationDetailResult.forAnnouncement(notification);
    }

    /**
     * Resolves an open report submitted by a buyer (specs_admin.tex Alternative Flow A3).
     *
     * <p>Enforces:
     * <ul>
     *   <li>Admin authorization: Requester must be an active administrator.</li>
     *   <li>Report existence: Must be present in data store.</li>
     *   <li>Status check: Must currently have {@link ReportStatus#OPEN}; if already resolved, returns ALREADY_RESOLVED.</li>
     *   <li>State transition: Status set to {@link ReportStatus#RESOLVED}.</li>
     *   <li>Buyer notification (A3 Step 3): Dispatches notification to the reporting buyer.</li>
     *   <li>Audit log: Records resolution in admin notifications board.</li>
     * </ul>
     *
     * @param reportId       the identifier of the report to resolve
     * @param adminUsername  requesting administrator username
     * @param resolutionNote optional resolution note
     * @return outcome of the resolution operation
     */
    public synchronized AdminReportResolutionResult resolveReport(String reportId, String adminUsername, String resolutionNote) {
        SessionManager.requireAdmin();
        if (reportId == null || reportId.trim().isEmpty()) {
            return AdminReportResolutionResult.failure(AdminReportResolutionStatus.INVALID_INPUT, "Report ID cannot be empty.");
        }
        if (!isAdminAuthorized(adminUsername)) {
            return AdminReportResolutionResult.failure(AdminReportResolutionStatus.UNAUTHORIZED,
                    "User '" + adminUsername + "' is not an active administrator.");
        }

        Report report = dataService.findReport(reportId.trim());
        if (report == null) {
            return AdminReportResolutionResult.failure(AdminReportResolutionStatus.REPORT_NOT_FOUND,
                    "Report with ID '" + reportId + "' was not found.");
        }

        if (report.getStatus() == ReportStatus.RESOLVED) {
            return AdminReportResolutionResult.failure(AdminReportResolutionStatus.ALREADY_RESOLVED,
                    "Report '" + reportId + "' has already been marked as Resolved.");
        }

        // Subflow A3 Step 3: Set status to Resolved
        report.setStatus(ReportStatus.RESOLVED);
        report.setHandledBy(adminUsername.trim());
        report.setResolvedDate(LocalDateTime.now());
        dataService.updateReport(report);

        // Subflow A3 Step 3: Notify buyer who filed it
        if (report.getBuyerUsername() != null && !report.getBuyerUsername().trim().isEmpty()) {
            String noteText = (resolutionNote != null && !resolutionNote.trim().isEmpty())
                    ? " Moderator note: " + resolutionNote.trim()
                    : "";
            Notification buyerNotif = new Notification(
                    report.getBuyerUsername(),
                    "Report Resolved",
                    "Your report regarding listing '" + report.getListingTitle() + "' has been investigated and resolved by an administrator." + noteText,
                    "Announcements",
                    report.getId(),
                    "REPORT"
            );
            dataService.addNotification(buyerNotif);
        }

        // Record resolution in Admin audit log
        Notification auditNotif = new Notification(
                "admin",
                "Report Marked Resolved",
                "Admin @" + adminUsername.trim() + " resolved report " + report.getId() + " on '" + report.getListingTitle() + "'.",
                AdminNotificationCategory.REPORTS.getDisplayName(),
                report.getId(),
                "REPORT"
        );
        dataService.addNotification(auditNotif);

        return AdminReportResolutionResult.success("Report " + report.getId() + " has been successfully resolved.", report);
    }

    /**
     * Flags a review on behalf of a seller and dispatches an admin notification
     * under {@link AdminNotificationCategory#FLAGGED_REVIEWS}.
     *
     * @param reviewId       the identifier of the review
     * @param sellerUsername the username of the seller flagging the review
     * @param reason         the written reason for flagging
     * @return true if flagged successfully, false otherwise
     */
    public synchronized boolean flagReview(String reviewId, String sellerUsername, String reason) {
        if (reviewId == null || sellerUsername == null || reason == null || reason.trim().isEmpty()) {
            return false;
        }
        Review review = dataService.findReview(reviewId.trim());
        if (review == null) {
            return false;
        }

        review.setFlagged(true);
        review.setFlagReason(reason.trim());

        Notification notif = new Notification(
                "admin",
                "Flagged Review for @" + sellerUsername.trim(),
                "Seller @" + sellerUsername.trim() + " flagged review " + review.getId() + " by @" + review.getBuyerUsername() + ": \"" + reason.trim() + "\"",
                AdminNotificationCategory.FLAGGED_REVIEWS.getDisplayName(),
                review.getId(),
                "REVIEW"
        );
        dataService.addNotification(notif);
        return true;
    }

    /**
     * Dispatches an admin notification when a new listing is submitted for review.
     *
     * @param listing the submitted listing
     */
    public synchronized void notifyListingSubmission(Listing listing) {
        if (listing == null) return;
        String typeStr = listing.getType() != null ? listing.getType().toString() : "Listing";
        Notification notif = new Notification(
                "admin",
                "New Submission: " + listing.getTitle(),
                "Seller @" + listing.getSellerUsername() + " submitted '" + listing.getTitle() + "' (" + typeStr + ") awaiting review.",
                AdminNotificationCategory.LISTING_SUBMISSIONS.getDisplayName(),
                listing.getId(),
                "LISTING"
        );
        dataService.addNotification(notif);
    }

    /**
     * Dispatches an admin notification when a report is filed by a buyer.
     *
     * @param report the filed report
     */
    public synchronized void notifyReportFiled(Report report) {
        if (report == null) return;
        Notification notif = new Notification(
                "admin",
                "Report Filed: " + report.getListingTitle(),
                "Buyer @" + report.getBuyerUsername() + " reported '" + report.getListingTitle() + "': " + report.getReason(),
                AdminNotificationCategory.REPORTS.getDisplayName(),
                report.getId(),
                "REPORT"
        );
        dataService.addNotification(notif);
    }

    /**
     * Dispatches an audit log notification to the administrator board.
     *
     * @param title          event title
     * @param message        event details
     * @param targetUsername username of user concerned
     */
    public synchronized void notifyUserAudit(String title, String message, String targetUsername) {
        Notification notif = new Notification(
                "admin",
                title != null ? title : "User Audit Event",
                message != null ? message : "",
                AdminNotificationCategory.USER_ACCOUNTS.getDisplayName(),
                targetUsername,
                "USER"
        );
        dataService.addNotification(notif);
    }
}
