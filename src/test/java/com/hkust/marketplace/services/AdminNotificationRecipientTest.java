package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.AdminNotificationFilterCriteria;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Report;
import com.hkust.marketplace.models.ReportResult;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserEditRequest;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminNotificationRecipientTest (Finding 6 Recipient Username Consistency)")
public class AdminNotificationRecipientTest {

    private DummyDataService dataService;
    private UserManagementService userManagementService;
    private AdminNotificationService adminNotificationService;
    private ListingDetailService listingDetailService;
    private Admin admin;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        SessionManager.logout();

        userManagementService = dataService.getUserManagementService();
        adminNotificationService = dataService.getAdminNotificationService();
        listingDetailService = dataService.getListingDetailService();

        admin = new Admin(DummyDataService.SEEDED_ADMIN_USERNAME, "Platform Admin", "Admin123!");
        admin.setActive(true);
        SessionManager.login(admin);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        dataService.resetData();
    }

    @Test
    @DisplayName("DummyDataService: Seeded admin notifications are addressed to SEEDED_ADMIN_USERNAME")
    void testSeededNotificationsRecipient() {
        List<Notification> adminNotifs = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertFalse(adminNotifs.isEmpty(), "Seeded admin notifications should exist");

        for (Notification n : adminNotifs) {
            assertEquals(DummyDataService.SEEDED_ADMIN_USERNAME, n.getUsername(),
                    "All seeded admin notifications must be addressed to SEEDED_ADMIN_USERNAME");
        }
    }

    @Test
    @DisplayName("UserManagementService.editUser: Dispatches audit log to SEEDED_ADMIN_USERNAME")
    void testUserManagementEditUserAuditRecipient() {
        int countBefore = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME).size();

        UserEditRequest req = UserEditRequest.builder()
                .targetUsername("alice")
                .adminUsername(DummyDataService.SEEDED_ADMIN_USERNAME)
                .newFullName("Alice In Wonderland")
                .residence("Hall 3")
                .build();

        userManagementService.editUser(req);

        List<Notification> adminNotifs = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertEquals(countBefore + 1, adminNotifs.size());
        Notification latest = adminNotifs.get(0);
        assertEquals(DummyDataService.SEEDED_ADMIN_USERNAME, latest.getUsername());
        assertEquals("User Profile Updated", latest.getTitle());
    }

    @Test
    @DisplayName("UserManagementService.changeAccountStatus: Deactivate and Reactivate dispatch audit logs to SEEDED_ADMIN_USERNAME")
    void testUserManagementStatusChangeAuditRecipient() {
        int countBefore = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME).size();

        // Deactivate
        userManagementService.changeAccountStatus("alice", AccountStatus.DEACTIVATED, DummyDataService.SEEDED_ADMIN_USERNAME);
        List<Notification> adminNotifs = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertEquals(countBefore + 1, adminNotifs.size());
        assertEquals("User Deactivated", adminNotifs.get(0).getTitle());
        assertEquals(DummyDataService.SEEDED_ADMIN_USERNAME, adminNotifs.get(0).getUsername());

        // Reactivate
        userManagementService.changeAccountStatus("alice", AccountStatus.ACTIVE, DummyDataService.SEEDED_ADMIN_USERNAME);
        adminNotifs = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertEquals(countBefore + 2, adminNotifs.size());
        assertEquals("User Reactivated", adminNotifs.get(0).getTitle());
        assertEquals(DummyDataService.SEEDED_ADMIN_USERNAME, adminNotifs.get(0).getUsername());
    }

    @Test
    @DisplayName("UserManagementService.deleteUser: Soft-deletion audit log is addressed to SEEDED_ADMIN_USERNAME")
    void testUserManagementDeleteUserAuditRecipient() {
        Buyer cleanBuyer = new Buyer("recipient_buyer", "Clean Recipient Buyer", "Pass1234!", "Hall 2");
        dataService.registerUserIfAbsent(cleanBuyer);

        int countBefore = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME).size();

        userManagementService.deleteUser("recipient_buyer", DummyDataService.SEEDED_ADMIN_USERNAME);

        List<Notification> adminNotifs = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertEquals(countBefore + 1, adminNotifs.size());
        Notification latest = adminNotifs.get(0);
        assertEquals("User Deleted", latest.getTitle());
        assertEquals(DummyDataService.SEEDED_ADMIN_USERNAME, latest.getUsername());
    }

    @Test
    @DisplayName("AdminNotificationService: Submission, Report, and Audit methods use SEEDED_ADMIN_USERNAME")
    void testAdminNotificationServiceDirectMethodsRecipient() {
        int countBefore = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME).size();

        // notifyListingSubmission
        Listing listing = dataService.getAllListings().iterator().next();
        adminNotificationService.notifyListingSubmission(listing);

        // notifyReportFiled
        Report report = dataService.getAllReports().get(0);
        assertNotNull(report);
        adminNotificationService.notifyReportFiled(report);

        // notifyUserAudit
        adminNotificationService.notifyUserAudit("Security Alert", "Suspicious login attempt", "bob");

        List<Notification> adminNotifs = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertEquals(countBefore + 3, adminNotifs.size());

        for (int i = 0; i < 3; i++) {
            assertEquals(DummyDataService.SEEDED_ADMIN_USERNAME, adminNotifs.get(i).getUsername());
        }
    }

    @Test
    @DisplayName("AdminNotificationService.resolveReport: Audit log notification uses SEEDED_ADMIN_USERNAME")
    void testAdminNotificationResolveReportRecipient() {
        Report report = dataService.getAllReports().get(0);
        assertNotNull(report);

        int countBefore = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME).size();

        adminNotificationService.resolveReport(report.getId(), DummyDataService.SEEDED_ADMIN_USERNAME, "Reviewed and verified");

        List<Notification> adminNotifs = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertEquals(countBefore + 1, adminNotifs.size());
        Notification latest = adminNotifs.get(0);
        assertEquals("Report Marked Resolved", latest.getTitle());
        assertEquals(DummyDataService.SEEDED_ADMIN_USERNAME, latest.getUsername());
    }

    @Test
    @DisplayName("ListingDetailService.submitReport: Fallback notification when no admins in list uses SEEDED_ADMIN_USERNAME")
    void testListingDetailReportFallbackRecipient() {
        Buyer buyer = (Buyer) dataService.findUser("alice");
        SessionManager.login(buyer);

        Listing listing = dataService.getAllListings().iterator().next();
        assertNotNull(listing);

        int countBefore = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME).size();

        ReportResult res = listingDetailService.submitReport(listing.getId(), "alice", "Counterfeit item reported");
        assertTrue(res.isSuccess());

        List<Notification> adminNotifs = dataService.getNotificationsFor(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertTrue(adminNotifs.size() > countBefore);
        Notification latest = adminNotifs.get(0);
        assertEquals(DummyDataService.SEEDED_ADMIN_USERNAME, latest.getUsername());
        assertTrue(latest.getTitle().contains("Report Filed"));
    }

    @Test
    @DisplayName("AdminNotificationService.getNotifications: Retrieves all notifications matching SEEDED_ADMIN_USERNAME")
    void testGetNotificationsRetrievesAllSeededAdminNotifs() {
        List<Notification> retrieved = adminNotificationService.getNotifications(
                DummyDataService.SEEDED_ADMIN_USERNAME,
                AdminNotificationFilterCriteria.all()
        );
        assertFalse(retrieved.isEmpty());
        for (Notification n : retrieved) {
            assertTrue(
                    DummyDataService.SEEDED_ADMIN_USERNAME.equalsIgnoreCase(n.getUsername()) ||
                            "admin".equalsIgnoreCase(n.getUsername()),
                    "Retrieved notification should belong to admin username"
            );
        }
    }
}
