package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.AdminProfileUpdateRequest;
import com.hkust.marketplace.models.AdminProfileUpdateResult;
import com.hkust.marketplace.models.AdminProfileUpdateStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Admin Profile Integration Tests (US-A04)")
class DummyDataServiceAdminProfileTest {

    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        SessionManager.logout();
    }

    @AfterEach
    void tearDown() {
        dataService.resetData();
        SessionManager.logout();
    }

    @Test
    @DisplayName("DummyDataService exposes AdminProfileService")
    void testGetAdminProfileService() {
        assertNotNull(dataService.getAdminProfileService());
    }

    @Test
    @DisplayName("DummyDataService getAdminProfile finds seeded admin")
    void testGetAdminProfile() {
        Admin admin = dataService.getAdminProfile(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertNotNull(admin);
        assertEquals(DummyDataService.SEEDED_ADMIN_FULLNAME, admin.getFullName());
        assertTrue(admin.isActive());
    }

    @Test
    @DisplayName("DummyDataService updateAdminProfile successfully delegates and updates stored admin")
    void testUpdateAdminProfileDelegation() {
        AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                DummyDataService.SEEDED_ADMIN_USERNAME, "Updated Dummy Admin Name");

        AdminProfileUpdateResult result = dataService.updateAdminProfile(
                DummyDataService.SEEDED_ADMIN_USERNAME, req);

        assertTrue(result.isSuccess());
        assertEquals(AdminProfileUpdateStatus.SUCCESS, result.getStatus());

        Admin updated = dataService.getAdminProfile(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertNotNull(updated);
        assertEquals("Updated Dummy Admin Name", updated.getFullName());
    }

    @Test
    @DisplayName("Resetting data restores seeded admin credentials and full name")
    void testResetRestoresSeededAdmin() {
        AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                DummyDataService.SEEDED_ADMIN_USERNAME, "Temporary Name",
                DummyDataService.SEEDED_ADMIN_PASSWORD, "NewTempPass123", "NewTempPass123");

        dataService.updateAdminProfile(DummyDataService.SEEDED_ADMIN_USERNAME, req);
        assertEquals("Temporary Name", dataService.getAdminProfile(DummyDataService.SEEDED_ADMIN_USERNAME).getFullName());

        dataService.resetData();
        Admin restored = dataService.getAdminProfile(DummyDataService.SEEDED_ADMIN_USERNAME);
        assertEquals(DummyDataService.SEEDED_ADMIN_FULLNAME, restored.getFullName());
        assertTrue(PasswordUtil.verifyPassword(DummyDataService.SEEDED_ADMIN_PASSWORD, restored.getPasswordHash()));
    }
}
