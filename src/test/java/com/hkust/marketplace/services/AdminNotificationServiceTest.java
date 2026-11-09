package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-A06 Admin Notification Service Test Suite")
class AdminNotificationServiceTest {

    private DummyDataService dataService;
    private AdminNotificationService service;
    private final String adminUsername = DummyDataService.SEEDED_ADMIN_USERNAME;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        service = new AdminNotificationService(dataService);
        SessionManager.login(dataService.findUser(adminUsername));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Nested
    @DisplayName("Basic Flow & Retrievals (UC 3.6 Basic Flow)")
    class BasicFlowTests {

        @Test
        @DisplayName("Admin receives notifications ordered newest first")
        void testGetNotificationsNewestFirst() {
            List<Notification> notifs = service.getNotifications(adminUsername, AdminNotificationFilterCriteria.all());
            assertFalse(notifs.isEmpty(), "Seeded admin notifications should not be empty.");

            for (int i = 0; i < notifs.size() - 1; i++) {
                Notification curr = notifs.get(i);
                Notification next = notifs.get(i + 1);
                if (curr.getTimestamp() != null && next.getTimestamp() != null) {
                    assertTrue(curr.getTimestamp().isAfter(next.getTimestamp()) || curr.getTimestamp().isEqual(next.getTimestamp()),
                            "Notifications must be sorted newest first.");
                }
            }
        }

        @Test
        @DisplayName("Default constructor and sorting edge cases")
        void testDefaultConstructorAndSorting() {
            AdminNotificationService defaultService = new AdminNotificationService();
            assertNotNull(defaultService);

            Notification nNull1 = new Notification("admin", "Null Time 1", "Msg", "Announcements");
            nNull1.setTimestamp(null);
            nNull1.setId("N_NULL_1");

            Notification nNull2 = new Notification("admin", "Null Time 2", "Msg", "Announcements");
            nNull2.setTimestamp(null);
            nNull2.setId("N_NULL_2");

            LocalDateTime sameTime = LocalDateTime.of(2026, 11, 5, 12, 0);
            Notification nSame1 = new Notification("admin", "Same Time 1", "Msg", "Announcements");
            nSame1.setTimestamp(sameTime);
            nSame1.setId("N_SAME_1");

            Notification nSame2 = new Notification("admin", "Same Time 2", "Msg", "Announcements");
            nSame2.setTimestamp(sameTime);
            nSame2.setId("N_SAME_2");

            dataService.addNotification(nNull1);
            dataService.addNotification(nNull2);
            dataService.addNotification(nSame1);
            dataService.addNotification(nSame2);

            List<Notification> sorted = service.getNotifications(adminUsername, AdminNotificationFilterCriteria.all());
            assertTrue(sorted.contains(nNull1));
            assertTrue(sorted.contains(nNull2));
            assertTrue(sorted.contains(nSame1));
            assertTrue(sorted.contains(nSame2));
        }

        @Test
        @DisplayName("Admin authorization check")
        void testAdminAuthorization() {
            assertTrue(service.isAdminAuthorized(adminUsername));
            assertTrue(service.isAdminAuthorized("admin"));
            assertFalse(service.isAdminAuthorized("alice")); // Buyer
            assertFalse(service.isAdminAuthorized("charlie")); // Seller
            assertFalse(service.isAdminAuthorized(null));
            assertFalse(service.isAdminAuthorized(""));
            assertFalse(service.isAdminAuthorized("nonexistent_user"));

            // Deactivated admin is unauthorized
            User adminUser = dataService.findUser(adminUsername);
            adminUser.setActive(false);
            assertFalse(service.isAdminAuthorized(adminUsername));
        }

        @Test
        @DisplayName("Mark a single notification as read")
        void testMarkAsRead() {
            List<Notification> unread = service.getNotifications(adminUsername, AdminNotificationFilterCriteria.unreadOnly());
            assertFalse(unread.isEmpty(), "Should have seeded unread notifications.");
            Notification target = unread.get(0);
            assertTrue(target.isUnread());

            boolean success = service.markAsRead(target.getId(), adminUsername);
            assertTrue(success);
            assertTrue(target.isRead());

            // Unauthorized attempt fails
            boolean failAuth = service.markAsRead(target.getId(), "alice");
            assertFalse(failAuth);

            // Non-existent notification fails
            boolean failMissing = service.markAsRead("N_INVALID_9999", adminUsername);
            assertFalse(failMissing);
        }

        @Test
        @DisplayName("Mark all notifications as read")
        void testMarkAllAsRead() {
            int unreadBefore = service.getUnreadCount(adminUsername);
            assertTrue(unreadBefore > 0);

            int updated = service.markAllAsRead(adminUsername);
            assertEquals(unreadBefore, updated);

            int unreadAfter = service.getUnreadCount(adminUsername);
            assertEquals(0, unreadAfter);

            // Unauthorized mark all returns 0
            assertEquals(0, service.markAllAsRead("charlie"));
        }

        @Test
        @DisplayName("Category counts and unread counts summary")
        void testCategoryCounts() {
            Map<AdminNotificationCategory, Long> totalMap = service.getCategoryCounts(adminUsername);
            Map<AdminNotificationCategory, Long> unreadMap = service.getCategoryUnreadCounts(adminUsername);

            assertEquals(5, totalMap.size());
            assertEquals(5, unreadMap.size());

            for (AdminNotificationCategory cat : AdminNotificationCategory.values()) {
                assertTrue(totalMap.containsKey(cat));
                assertTrue(unreadMap.containsKey(cat));
                assertTrue(totalMap.get(cat) >= unreadMap.get(cat));
            }
        }
    }

    @Nested
    @DisplayName("Category & Filter Criteria (Alternative Flow A2)")
    class FilteringTests {

        @Test
        @DisplayName("Filter notifications by category")
        void testFilterByCategory() {
            for (AdminNotificationCategory cat : AdminNotificationCategory.values()) {
                List<Notification> notifs = service.getNotifications(adminUsername, cat);
                for (Notification n : notifs) {
                    assertTrue(cat.matches(n.getCategory()),
                            "Notification category '" + n.getCategory() + "' should match " + cat);
                }
            }
        }

        @Test
        @DisplayName("Filter by unread only")
        void testFilterByUnreadOnly() {
            List<Notification> unread = service.getNotifications(adminUsername, AdminNotificationFilterCriteria.unreadOnly());
            for (Notification n : unread) {
                assertTrue(n.isUnread());
            }
        }

        @Test
        @DisplayName("Filter by keyword search")
        void testFilterBySearchQuery() {
            AdminNotificationFilterCriteria criteria = AdminNotificationFilterCriteria.withSearch("Mini Fridge");
            List<Notification> results = service.getNotifications(adminUsername, criteria);
            assertFalse(results.isEmpty());
            for (Notification n : results) {
                String fullText = (n.getTitle() + " " + n.getMessage() + " " + n.getRelatedItemId()).toLowerCase();
                assertTrue(fullText.contains("mini fridge") || fullText.contains("l5") || fullText.contains("l104"));
            }
        }

        @Test
        @DisplayName("Alternative Flow A1: No notifications match returns empty list")
        void testNoNotificationsMatch() {
            AdminNotificationFilterCriteria criteria = AdminNotificationFilterCriteria.withSearch("nonexistent_random_phrase_12345");
            List<Notification> results = service.getNotifications(adminUsername, criteria);
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("Report Resolution (Alternative Flow A3)")
    class ReportResolutionTests {

        @Test
        @DisplayName("A3 Happy Path: Successfully mark an open report as resolved")
        void testResolveReportSuccess() {
            // Find seeded open report
            Report openReport = dataService.getAllReports().stream()
                    .filter(r -> r.getStatus() == ReportStatus.OPEN)
                    .findFirst()
                    .orElse(null);
            assertNotNull(openReport, "Open report should be seeded.");

            int buyerNotifsBefore = dataService.getNotificationsFor(openReport.getBuyerUsername()).size();
            int adminNotifsBefore = service.getNotifications(adminUsername, AdminNotificationFilterCriteria.all()).size();

            AdminReportResolutionResult result = service.resolveReport(
                    openReport.getId(), adminUsername, "Item verified by administration.");

            assertTrue(result.isSuccess());
            assertEquals(AdminReportResolutionStatus.SUCCESS, result.getStatus());
            assertEquals(ReportStatus.RESOLVED, openReport.getStatus());

            // A3 Step 3: Check buyer notification
            List<Notification> buyerNotifs = dataService.getNotificationsFor(openReport.getBuyerUsername());
            assertEquals(buyerNotifsBefore + 1, buyerNotifs.size());
            Notification buyerAlert = buyerNotifs.get(0);
            assertTrue(buyerAlert.getTitle().contains("Report Resolved"));
            assertTrue(buyerAlert.getMessage().contains("Item verified by administration"));

            // Check admin audit log entry created
            List<Notification> adminNotifs = service.getNotifications(adminUsername, AdminNotificationFilterCriteria.all());
            assertEquals(adminNotifsBefore + 1, adminNotifs.size());
            Notification auditAlert = adminNotifs.get(0);
            assertEquals("User Accounts", auditAlert.getCategory());
            assertTrue(auditAlert.getTitle().contains("Report Marked Resolved"));
        }

        @Test
        @DisplayName("A3 Exception: Cannot resolve already resolved report")
        void testResolveAlreadyResolvedReport() {
            Report openReport = dataService.getAllReports().stream()
                    .filter(r -> r.getStatus() == ReportStatus.OPEN)
                    .findFirst()
                    .orElse(null);
            assertNotNull(openReport);

            // First resolution succeeds
            AdminReportResolutionResult res1 = service.resolveReport(openReport.getId(), adminUsername, null);
            assertTrue(res1.isSuccess());

            // Second resolution attempt fails with ALREADY_RESOLVED
            AdminReportResolutionResult res2 = service.resolveReport(openReport.getId(), adminUsername, null);
            assertFalse(res2.isSuccess());
            assertEquals(AdminReportResolutionStatus.ALREADY_RESOLVED, res2.getStatus());
        }

        @Test
        @DisplayName("A3 Exception: Report not found")
        void testResolveReportNotFound() {
            AdminReportResolutionResult result = service.resolveReport("REP_NONEXISTENT", adminUsername, null);
            assertFalse(result.isSuccess());
            assertEquals(AdminReportResolutionStatus.REPORT_NOT_FOUND, result.getStatus());
        }

        @Test
        @DisplayName("A3 Exception: Unauthorized user cannot resolve report")
        void testResolveReportUnauthorized() {
            Report openReport = dataService.getAllReports().stream()
                    .filter(r -> r.getStatus() == ReportStatus.OPEN)
                    .findFirst()
                    .orElse(null);
            assertNotNull(openReport);

            AdminReportResolutionResult resBuyer = service.resolveReport(openReport.getId(), "alice", null);
            assertFalse(resBuyer.isSuccess());
            assertEquals(AdminReportResolutionStatus.UNAUTHORIZED, resBuyer.getStatus());

            AdminReportResolutionResult resNull = service.resolveReport(openReport.getId(), null, null);
            assertFalse(resNull.isSuccess());
            assertEquals(AdminReportResolutionStatus.UNAUTHORIZED, resNull.getStatus());
        }

        @Test
        @DisplayName("A3 Exception: Invalid input (null or empty report ID)")
        void testResolveReportInvalidInput() {
            AdminReportResolutionResult resNull = service.resolveReport(null, adminUsername, null);
            assertFalse(resNull.isSuccess());
            assertEquals(AdminReportResolutionStatus.INVALID_INPUT, resNull.getStatus());

            AdminReportResolutionResult resEmpty = service.resolveReport("   ", adminUsername, null);
            assertFalse(resEmpty.isSuccess());
            assertEquals(AdminReportResolutionStatus.INVALID_INPUT, resEmpty.getStatus());
        }
    }

    @Nested
    @DisplayName("Inspect Notification & Target Items (UC 3.6 Step 4 & Alt Flow A4)")
    class NotificationDetailTests {

        @Test
        @DisplayName("Inspect Report notification: returns report and listing, marks read")
        void testInspectReportNotification() {
            List<Notification> reportNotifs = service.getNotifications(adminUsername, AdminNotificationCategory.REPORTS);
            assertFalse(reportNotifs.isEmpty());
            Notification notif = reportNotifs.get(0);

            AdminNotificationDetailResult detail = service.getNotificationDetail(notif.getId(), adminUsername);
            assertTrue(detail.isSuccess());
            assertEquals("REPORT", detail.getTargetType());
            assertNotNull(detail.getReport());
            assertTrue(notif.isRead(), "Notification should be marked as read upon inspection.");
        }

        @Test
        @DisplayName("Inspect Listing notification: returns listing")
        void testInspectListingNotification() {
            List<Notification> listingNotifs = service.getNotifications(adminUsername, AdminNotificationCategory.LISTING_SUBMISSIONS);
            assertFalse(listingNotifs.isEmpty());
            Notification notif = listingNotifs.get(0);

            AdminNotificationDetailResult detail = service.getNotificationDetail(notif.getId(), adminUsername);
            assertTrue(detail.isSuccess());
            assertEquals("LISTING", detail.getTargetType());
            assertNotNull(detail.getListing());
        }

        @Test
        @DisplayName("Inspect Review notification: returns review")
        void testInspectReviewNotification() {
            List<Notification> reviewNotifs = service.getNotifications(adminUsername, AdminNotificationCategory.FLAGGED_REVIEWS);
            assertFalse(reviewNotifs.isEmpty());
            Notification notif = reviewNotifs.get(0);

            AdminNotificationDetailResult detail = service.getNotificationDetail(notif.getId(), adminUsername);
            assertTrue(detail.isSuccess());
            assertEquals("REVIEW", detail.getTargetType());
            assertNotNull(detail.getReview());
        }

        @Test
        @DisplayName("Inspect User Account notification: returns user")
        void testInspectUserAccountNotification() {
            List<Notification> userNotifs = service.getNotifications(adminUsername, AdminNotificationCategory.USER_ACCOUNTS);
            assertFalse(userNotifs.isEmpty());
            Notification notif = userNotifs.get(0);

            AdminNotificationDetailResult detail = service.getNotificationDetail(notif.getId(), adminUsername);
            assertTrue(detail.isSuccess());
            assertEquals("USER", detail.getTargetType());
            assertNotNull(detail.getUser());
        }

        @Test
        @DisplayName("Inspect Announcement notification: returns announcement")
        void testInspectAnnouncementNotification() {
            List<Notification> annNotifs = service.getNotifications(adminUsername, AdminNotificationCategory.ANNOUNCEMENTS);
            assertFalse(annNotifs.isEmpty());
            Notification notif = annNotifs.get(0);

            AdminNotificationDetailResult detail = service.getNotificationDetail(notif.getId(), adminUsername);
            assertTrue(detail.isSuccess());
            assertEquals(AdminNotificationDetailStatus.ANNOUNCEMENT_ONLY, detail.getStatus());
        }

        @Test
        @DisplayName("Alternative Flow A4: Related listing deleted/removed returns ITEM_NO_LONGER_AVAILABLE")
        void testA4RelatedListingRemoved() {
            Listing l = new Listing();
            l.setId("L_DEL");
            l.setTitle("Deleted Item");
            l.setStatus(ListingStatus.REMOVED);
            dataService.addListing(l);

            Notification n = new Notification("admin", "Submission", "Listing removed", "Listing Submissions", "L_DEL", "LISTING");
            dataService.addNotification(n);

            AdminNotificationDetailResult detail = service.getNotificationDetail(n.getId(), adminUsername);
            assertFalse(detail.isSuccess());
            assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, detail.getStatus());
            assertTrue(n.isRead(), "Notification must still be marked as read per A4.");
        }

        @Test
        @DisplayName("Alternative Flow A4: Related report missing returns ITEM_NO_LONGER_AVAILABLE")
        void testA4RelatedReportMissing() {
            Notification n = new Notification("admin", "Report", "Report missing", "Reports", "REP_NONEXISTENT", "REPORT");
            dataService.addNotification(n);

            AdminNotificationDetailResult detail = service.getNotificationDetail(n.getId(), adminUsername);
            assertFalse(detail.isSuccess());
            assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, detail.getStatus());
        }

        @Test
        @DisplayName("Alternative Flow A4: Related review missing returns ITEM_NO_LONGER_AVAILABLE")
        void testA4RelatedReviewMissing() {
            Notification n = new Notification("admin", "Review", "Review missing", "Flagged Reviews", "R_MISSING", "REVIEW");
            dataService.addNotification(n);

            AdminNotificationDetailResult detail = service.getNotificationDetail(n.getId(), adminUsername);
            assertFalse(detail.isSuccess());
            assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, detail.getStatus());
        }

        @Test
        @DisplayName("Alternative Flow A4: Related user soft-deleted returns ITEM_NO_LONGER_AVAILABLE")
        void testA4RelatedUserDeleted() {
            Buyer deletedBuyer = new Buyer("deleted_user_xyz", "Deleted", "pass");
            deletedBuyer.setAccountStatus(AccountStatus.DELETED);
            dataService.registerUserIfAbsent(deletedBuyer);

            Notification n = new Notification("admin", "User Audit", "Deleted user", "User Accounts", "deleted_user_xyz", "USER");
            dataService.addNotification(n);

            AdminNotificationDetailResult detail = service.getNotificationDetail(n.getId(), adminUsername);
            assertFalse(detail.isSuccess());
            assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, detail.getStatus());
        }

        @Test
        @DisplayName("Notification detail error handling: not found and unauthorized")
        void testDetailErrors() {
            AdminNotificationDetailResult resUnauth = service.getNotificationDetail("N1", "alice");
            assertFalse(resUnauth.isSuccess());
            assertEquals(AdminNotificationDetailStatus.UNAUTHORIZED, resUnauth.getStatus());

            AdminNotificationDetailResult resMissing = service.getNotificationDetail("N_DOES_NOT_EXIST", adminUsername);
            assertFalse(resMissing.isSuccess());
            assertEquals(AdminNotificationDetailStatus.NOTIFICATION_NOT_FOUND, resMissing.getStatus());

            AdminNotificationDetailResult resEmpty = service.getNotificationDetail(null, adminUsername);
            assertFalse(resEmpty.isSuccess());
            assertEquals(AdminNotificationDetailStatus.NOTIFICATION_NOT_FOUND, resEmpty.getStatus());
        }
    }

    @Nested
    @DisplayName("Notification Dispatching Helpers")
    class DispatchingTests {

        @Test
        @DisplayName("Seller flags review: updates review and dispatches admin notification")
        void testFlagReview() {
            Review rev = dataService.getAllReviews().stream()
                    .filter(r -> !r.isFlagged())
                    .findFirst()
                    .orElse(null);
            assertNotNull(rev);
            assertFalse(rev.isFlagged());

            int notifsBefore = service.getNotifications(adminUsername, AdminNotificationCategory.FLAGGED_REVIEWS).size();

            boolean flagged = service.flagReview(rev.getId(), rev.getSellerUsername(), "Spam review");
            assertTrue(flagged);
            assertTrue(rev.isFlagged());
            assertEquals("Spam review", rev.getFlagReason());

            List<Notification> adminNotifs = service.getNotifications(adminUsername, AdminNotificationCategory.FLAGGED_REVIEWS);
            assertEquals(notifsBefore + 1, adminNotifs.size());
            Notification notif = adminNotifs.get(0);
            assertEquals("REVIEW", notif.getRelatedItemType());
            assertEquals(rev.getId(), notif.getRelatedItemId());

            // Error cases
            assertFalse(service.flagReview(null, "charlie", "reason"));
            assertFalse(service.flagReview("INVALID_REV", "charlie", "reason"));
            assertFalse(service.flagReview(rev.getId(), null, "reason"));
            assertFalse(service.flagReview(rev.getId(), "charlie", "   "));
        }

        @Test
        @DisplayName("Dispatch notifications for listing submission, report, and audit")
        void testNotificationHelpers() {
            Listing l = new Listing();
            l.setId("L_NEW");
            l.setTitle("Calculus Notes");
            l.setType(ListingType.SELL);
            l.setSellerUsername("diana");

            int subsBefore = service.getNotifications(adminUsername, AdminNotificationCategory.LISTING_SUBMISSIONS).size();
            service.notifyListingSubmission(l);
            assertEquals(subsBefore + 1, service.getNotifications(adminUsername, AdminNotificationCategory.LISTING_SUBMISSIONS).size());

            Report rep = new Report("REP_NEW", "L_NEW", "Calculus Notes", "alice", "Copyright violation");
            int repsBefore = service.getNotifications(adminUsername, AdminNotificationCategory.REPORTS).size();
            service.notifyReportFiled(rep);
            assertEquals(repsBefore + 1, service.getNotifications(adminUsername, AdminNotificationCategory.REPORTS).size());

            int auditsBefore = service.getNotifications(adminUsername, AdminNotificationCategory.USER_ACCOUNTS).size();
            service.notifyUserAudit("Password Reset", "Admin reset password for bob", "bob");
            assertEquals(auditsBefore + 1, service.getNotifications(adminUsername, AdminNotificationCategory.USER_ACCOUNTS).size());

            // Null safety
            service.notifyListingSubmission(null);
            service.notifyReportFiled(null);
            service.notifyUserAudit(null, null, null);
        }
    }

    @Nested
    @DisplayName("Concurrency & Stress Verification")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent report resolution: exactly one succeeds, others fail atomically")
        void testConcurrentReportResolution() throws Exception {
            Report rep = new Report("REP_CONCURRENT", "L101", "MacBook Charger", "alice", "Damaged cable");
            rep.setStatus(ReportStatus.OPEN);
            dataService.addReport(rep);

            int numThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger alreadyResolvedCount = new AtomicInteger(0);

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        AdminReportResolutionResult res = service.resolveReport(rep.getId(), adminUsername, "Resolved concurrently");
                        if (res.isSuccess()) {
                            successCount.incrementAndGet();
                        } else if (res.getStatus() == AdminReportResolutionStatus.ALREADY_RESOLVED) {
                            alreadyResolvedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(1, successCount.get(), "Exactly one administrator thread must succeed in resolving the report.");
            assertEquals(numThreads - 1, alreadyResolvedCount.get(), "All other threads must fail with ALREADY_RESOLVED.");
            assertEquals(ReportStatus.RESOLVED, rep.getStatus());
        }

        @Test
        @DisplayName("Concurrent markAllAsRead and getNotifications execution")
        void testConcurrentMarkAndRead() throws Exception {
            int numThreads = 8;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(1);

            for (int i = 0; i < numThreads; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        if (idx % 2 == 0) {
                            service.markAllAsRead(adminUsername);
                        } else {
                            List<Notification> notifs = service.getNotifications(adminUsername, AdminNotificationFilterCriteria.all());
                            assertNotNull(notifs);
                        }
                    } catch (Exception e) {
                        fail("Exception during concurrent execution: " + e.getMessage());
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("RBAC: Unauthenticated caller throws SecurityException")
    void testUnauthenticatedAccessThrowsSecurityException() {
        SessionManager.logout();
        assertThrows(SecurityException.class, () -> service.getNotifications(adminUsername, AdminNotificationFilterCriteria.all()));
        assertThrows(SecurityException.class, () -> service.getNotification("N1"));
        assertThrows(SecurityException.class, () -> service.markAsRead("N1", adminUsername));
        assertThrows(SecurityException.class, () -> service.markAllAsRead(adminUsername));
        assertThrows(SecurityException.class, () -> service.getUnreadCount(adminUsername));
        assertThrows(SecurityException.class, () -> service.getCategoryCounts(adminUsername));
        assertThrows(SecurityException.class, () -> service.getCategoryUnreadCounts(adminUsername));
        assertThrows(SecurityException.class, () -> service.getNotificationDetail("N1", adminUsername));
        assertThrows(SecurityException.class, () -> service.resolveReport("REP1", adminUsername, "Resolved"));
    }

    @Test
    @DisplayName("RBAC: Non-admin caller throws SecurityException")
    void testNonAdminAccessThrowsSecurityException() {
        SessionManager.login(dataService.findUser("alice"));
        assertThrows(SecurityException.class, () -> service.getNotifications(adminUsername, AdminNotificationFilterCriteria.all()));
        assertThrows(SecurityException.class, () -> service.getNotification("N1"));
        assertThrows(SecurityException.class, () -> service.markAsRead("N1", adminUsername));
        assertThrows(SecurityException.class, () -> service.markAllAsRead(adminUsername));
        assertThrows(SecurityException.class, () -> service.getUnreadCount(adminUsername));
        assertThrows(SecurityException.class, () -> service.getCategoryCounts(adminUsername));
        assertThrows(SecurityException.class, () -> service.getCategoryUnreadCounts(adminUsername));
        assertThrows(SecurityException.class, () -> service.getNotificationDetail("N1", adminUsername));
        assertThrows(SecurityException.class, () -> service.resolveReport("REP1", adminUsername, "Resolved"));
    }
}
