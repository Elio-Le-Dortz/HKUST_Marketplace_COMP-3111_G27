package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-A06 Admin Notifications Models Test Suite")
class AdminNotificationModelsTest {

    @Test
    @DisplayName("AdminNotificationCategory enum coverage and aliases")
    void testAdminNotificationCategoryEnum() {
        assertEquals(5, AdminNotificationCategory.values().length);
        assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS, AdminNotificationCategory.valueOf("LISTING_SUBMISSIONS"));
        assertEquals(AdminNotificationCategory.REPORTS, AdminNotificationCategory.valueOf("REPORTS"));
        assertEquals(AdminNotificationCategory.FLAGGED_REVIEWS, AdminNotificationCategory.valueOf("FLAGGED_REVIEWS"));
        assertEquals(AdminNotificationCategory.USER_ACCOUNTS, AdminNotificationCategory.valueOf("USER_ACCOUNTS"));
        assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.valueOf("ANNOUNCEMENTS"));

        for (AdminNotificationCategory cat : AdminNotificationCategory.values()) {
            assertNotNull(cat.getDisplayName());
            assertNotNull(cat.getIcon());
            assertNotNull(cat.getAliases());
            assertFalse(cat.getAliases().isEmpty());
        }

        // Test fromRaw matching
        assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw(null));
        assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw(""));
        assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw("   "));
        assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw("unknown_custom_xyz"));

        assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS, AdminNotificationCategory.fromRaw("Listing Submissions"));
        assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS, AdminNotificationCategory.fromRaw("submissions"));
        assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS, AdminNotificationCategory.fromRaw("pending review"));
        assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS, AdminNotificationCategory.fromRaw("new listing posted"));

        assertEquals(AdminNotificationCategory.REPORTS, AdminNotificationCategory.fromRaw("Reports"));
        assertEquals(AdminNotificationCategory.REPORTS, AdminNotificationCategory.fromRaw("report"));
        assertEquals(AdminNotificationCategory.REPORTS, AdminNotificationCategory.fromRaw("reported listings"));
        assertEquals(AdminNotificationCategory.REPORTS, AdminNotificationCategory.fromRaw("listing report filed"));

        assertEquals(AdminNotificationCategory.FLAGGED_REVIEWS, AdminNotificationCategory.fromRaw("Flagged Reviews"));
        assertEquals(AdminNotificationCategory.FLAGGED_REVIEWS, AdminNotificationCategory.fromRaw("review flags"));
        assertEquals(AdminNotificationCategory.FLAGGED_REVIEWS, AdminNotificationCategory.fromRaw("flagged"));
        assertEquals(AdminNotificationCategory.FLAGGED_REVIEWS, AdminNotificationCategory.fromRaw("rating dispute"));

        assertEquals(AdminNotificationCategory.USER_ACCOUNTS, AdminNotificationCategory.fromRaw("User Accounts"));
        assertEquals(AdminNotificationCategory.USER_ACCOUNTS, AdminNotificationCategory.fromRaw("audit log"));
        assertEquals(AdminNotificationCategory.USER_ACCOUNTS, AdminNotificationCategory.fromRaw("user account"));
        assertEquals(AdminNotificationCategory.USER_ACCOUNTS, AdminNotificationCategory.fromRaw("account deactivated"));

        assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw("Announcements"));
        assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw("general"));
        assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw("system notice"));

        // Test matches method
        assertTrue(AdminNotificationCategory.REPORTS.matches("Reports"));
        assertTrue(AdminNotificationCategory.REPORTS.matches("report"));
        assertFalse(AdminNotificationCategory.REPORTS.matches("User Accounts"));
    }

    @Test
    @DisplayName("AdminNotificationFilterCriteria creation and matching")
    void testFilterCriteria() {
        AdminNotificationFilterCriteria all = AdminNotificationFilterCriteria.all();
        assertNull(all.getCategory());
        assertFalse(all.isUnreadOnly());
        assertNull(all.getSearchQuery());

        AdminNotificationFilterCriteria byCat = AdminNotificationFilterCriteria.byCategory(AdminNotificationCategory.REPORTS);
        assertEquals(AdminNotificationCategory.REPORTS, byCat.getCategory());
        assertFalse(byCat.isUnreadOnly());

        AdminNotificationFilterCriteria unread = AdminNotificationFilterCriteria.unreadOnly();
        assertTrue(unread.isUnreadOnly());
        assertNull(unread.getCategory());

        AdminNotificationFilterCriteria search = AdminNotificationFilterCriteria.withSearch("charger");
        assertEquals("charger", search.getSearchQuery());

        // Criteria matching tests
        assertFalse(all.matches(null));

        Notification n1 = new Notification("admin", "New Listing", "Diana submitted a phone", "Listing Submissions", "L101", "LISTING");
        n1.setRead(false);

        Notification n2 = new Notification("admin", "Report Filed", "Buyer reported counterfeit item", "Reports", "REP301", "REPORT");
        n2.setRead(true);

        assertTrue(all.matches(n1));
        assertTrue(all.matches(n2));

        assertTrue(byCat.matches(n2));
        assertFalse(byCat.matches(n1));

        assertTrue(unread.matches(n1));
        assertFalse(unread.matches(n2));

        AdminNotificationFilterCriteria searchCriteria = AdminNotificationFilterCriteria.withSearch("counterfeit");
        assertTrue(searchCriteria.matches(n2));
        assertFalse(searchCriteria.matches(n1));

        AdminNotificationFilterCriteria idSearch = AdminNotificationFilterCriteria.withSearch("L101");
        assertTrue(idSearch.matches(n1));
        assertFalse(idSearch.matches(n2));

        AdminNotificationFilterCriteria catSearch = AdminNotificationFilterCriteria.withSearch("reports");
        assertTrue(catSearch.matches(n2));

        // Equals, hashCode, toString
        AdminNotificationFilterCriteria c1 = new AdminNotificationFilterCriteria(AdminNotificationCategory.REPORTS, true, "query");
        AdminNotificationFilterCriteria c2 = new AdminNotificationFilterCriteria(AdminNotificationCategory.REPORTS, true, "query");
        AdminNotificationFilterCriteria c3 = new AdminNotificationFilterCriteria(AdminNotificationCategory.ANNOUNCEMENTS, false, null);

        assertEquals(c1, c2);
        assertNotEquals(c1, c3);
        assertNotEquals(c1, null);
        assertNotEquals(c1, "other");
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotNull(c1.toString());
    }

    @Test
    @DisplayName("AdminReportResolutionStatus and AdminReportResolutionResult coverage")
    void testReportResolutionModels() {
        assertEquals(5, AdminReportResolutionStatus.values().length);
        assertEquals(AdminReportResolutionStatus.SUCCESS, AdminReportResolutionStatus.valueOf("SUCCESS"));
        assertEquals(AdminReportResolutionStatus.REPORT_NOT_FOUND, AdminReportResolutionStatus.valueOf("REPORT_NOT_FOUND"));
        assertEquals(AdminReportResolutionStatus.ALREADY_RESOLVED, AdminReportResolutionStatus.valueOf("ALREADY_RESOLVED"));
        assertEquals(AdminReportResolutionStatus.UNAUTHORIZED, AdminReportResolutionStatus.valueOf("UNAUTHORIZED"));
        assertEquals(AdminReportResolutionStatus.INVALID_INPUT, AdminReportResolutionStatus.valueOf("INVALID_INPUT"));

        Report rep = new Report("REP99", "L10", "Old Bike", "alice", "Broken frame");
        rep.setStatus(ReportStatus.RESOLVED);

        AdminReportResolutionResult successResult = AdminReportResolutionResult.success("Report resolved successfully.", rep);
        assertTrue(successResult.isSuccess());
        assertEquals(AdminReportResolutionStatus.SUCCESS, successResult.getStatus());
        assertEquals("Report resolved successfully.", successResult.getMessage());
        assertEquals(rep, successResult.getReport());
        assertNotNull(successResult.toString());

        AdminReportResolutionResult failResult = AdminReportResolutionResult.failure(
                AdminReportResolutionStatus.ALREADY_RESOLVED, "Already marked resolved.");
        assertFalse(failResult.isSuccess());
        assertEquals(AdminReportResolutionStatus.ALREADY_RESOLVED, failResult.getStatus());
        assertEquals("Already marked resolved.", failResult.getMessage());
        assertNull(failResult.getReport());
        assertNotNull(failResult.toString());
    }

    @Test
    @DisplayName("AdminNotificationDetailStatus and AdminNotificationDetailResult coverage")
    void testNotificationDetailModels() {
        assertEquals(5, AdminNotificationDetailStatus.values().length);
        assertEquals(AdminNotificationDetailStatus.FOUND, AdminNotificationDetailStatus.valueOf("FOUND"));
        assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, AdminNotificationDetailStatus.valueOf("ITEM_NO_LONGER_AVAILABLE"));
        assertEquals(AdminNotificationDetailStatus.ANNOUNCEMENT_ONLY, AdminNotificationDetailStatus.valueOf("ANNOUNCEMENT_ONLY"));
        assertEquals(AdminNotificationDetailStatus.NOTIFICATION_NOT_FOUND, AdminNotificationDetailStatus.valueOf("NOTIFICATION_NOT_FOUND"));
        assertEquals(AdminNotificationDetailStatus.UNAUTHORIZED, AdminNotificationDetailStatus.valueOf("UNAUTHORIZED"));

        Notification n = new Notification("admin", "Test", "Message", "Reports");
        Report r = new Report("REP1", "L1", "Title", "buyer", "Reason");
        Listing l = new Listing();
        l.setId("L1");
        l.setTitle("Title");
        Review rev = new Review();
        rev.setId("REV1");
        Buyer user = new Buyer("alice", "Alice", "pass");

        AdminNotificationDetailResult resReport = AdminNotificationDetailResult.forReport(n, r, l);
        assertTrue(resReport.isSuccess());
        assertEquals(AdminNotificationDetailStatus.FOUND, resReport.getStatus());
        assertEquals("REPORT", resReport.getTargetType());
        assertEquals(r, resReport.getReport());
        assertEquals(l, resReport.getListing());
        assertEquals(n, resReport.getNotification());
        assertNotNull(resReport.toString());

        AdminNotificationDetailResult resListing = AdminNotificationDetailResult.forListing(n, l);
        assertTrue(resListing.isSuccess());
        assertEquals("LISTING", resListing.getTargetType());
        assertEquals(l, resListing.getListing());

        AdminNotificationDetailResult resReview = AdminNotificationDetailResult.forReview(n, rev);
        assertTrue(resReview.isSuccess());
        assertEquals("REVIEW", resReview.getTargetType());
        assertEquals(rev, resReview.getReview());

        AdminNotificationDetailResult resUser = AdminNotificationDetailResult.forUser(n, user);
        assertTrue(resUser.isSuccess());
        assertEquals("USER", resUser.getTargetType());
        assertEquals(user, resUser.getUser());

        AdminNotificationDetailResult resAnn = AdminNotificationDetailResult.forAnnouncement(n);
        assertTrue(resAnn.isSuccess());
        assertEquals(AdminNotificationDetailStatus.ANNOUNCEMENT_ONLY, resAnn.getStatus());

        AdminNotificationDetailResult resUnavailable = AdminNotificationDetailResult.itemNoLongerAvailable(n, "LISTING", "Item deleted.");
        assertFalse(resUnavailable.isSuccess());
        assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, resUnavailable.getStatus());
        assertEquals("Item deleted.", resUnavailable.getMessage());

        AdminNotificationDetailResult resNotFound = AdminNotificationDetailResult.notFound("Notification missing.");
        assertFalse(resNotFound.isSuccess());
        assertEquals(AdminNotificationDetailStatus.NOTIFICATION_NOT_FOUND, resNotFound.getStatus());

        AdminNotificationDetailResult resUnauth = AdminNotificationDetailResult.unauthorized("Unauthorized access.");
        assertFalse(resUnauth.isSuccess());
        assertEquals(AdminNotificationDetailStatus.UNAUTHORIZED, resUnauth.getStatus());
    }
}
