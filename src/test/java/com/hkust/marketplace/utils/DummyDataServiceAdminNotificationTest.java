package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.AdminNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Admin Notification Integration Tests")
class DummyDataServiceAdminNotificationTest {

    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        SessionManager.login(dataService.findUser(DummyDataService.SEEDED_ADMIN_USERNAME));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("AdminNotificationService is properly wired and seeded in DummyDataService")
    void testAdminNotificationServiceWiring() {
        AdminNotificationService service = dataService.getAdminNotificationService();
        assertNotNull(service);

        List<Notification> adminNotifs = service.getNotifications(
                DummyDataService.SEEDED_ADMIN_USERNAME, AdminNotificationFilterCriteria.all());
        assertFalse(adminNotifs.isEmpty());

        // Verify that seeded notifications include all 5 categories
        boolean hasSubmissions = false;
        boolean hasReports = false;
        boolean hasFlags = false;
        boolean hasUserAccounts = false;
        boolean hasAnnouncements = false;

        for (Notification n : adminNotifs) {
            AdminNotificationCategory cat = AdminNotificationCategory.fromRaw(n.getCategory());
            switch (cat) {
                case LISTING_SUBMISSIONS -> hasSubmissions = true;
                case REPORTS -> hasReports = true;
                case FLAGGED_REVIEWS -> hasFlags = true;
                case USER_ACCOUNTS -> hasUserAccounts = true;
                case ANNOUNCEMENTS -> hasAnnouncements = true;
            }
        }

        assertTrue(hasSubmissions, "Seeded data should include Listing Submissions");
        assertTrue(hasReports, "Seeded data should include Reports");
        assertTrue(hasFlags, "Seeded data should include Flagged Reviews");
        assertTrue(hasUserAccounts, "Seeded data should include User Accounts");
        assertTrue(hasAnnouncements, "Seeded data should include Announcements");
    }

    @Test
    @DisplayName("findReport and findReview helper methods on DummyDataService")
    void testFindReportAndReview() {
        List<Report> allReports = dataService.getAllReports();
        assertFalse(allReports.isEmpty());
        Report firstReport = allReports.get(0);

        Report foundReport = dataService.findReport(firstReport.getId());
        assertNotNull(foundReport);
        assertEquals(firstReport.getId(), foundReport.getId());

        assertNull(dataService.findReport(null));
        assertNull(dataService.findReport("   "));
        assertNull(dataService.findReport("REP_NONEXISTENT_9999"));

        List<Review> allReviews = dataService.getAllReviews();
        assertFalse(allReviews.isEmpty());
        Review firstReview = allReviews.get(0);

        Review foundReview = dataService.findReview(firstReview.getId());
        assertNotNull(foundReview);
        assertEquals(firstReview.getId(), foundReview.getId());

        assertNull(dataService.findReview(null));
        assertNull(dataService.findReview("   "));
        assertNull(dataService.findReview("REV_NONEXISTENT_9999"));
    }
}
