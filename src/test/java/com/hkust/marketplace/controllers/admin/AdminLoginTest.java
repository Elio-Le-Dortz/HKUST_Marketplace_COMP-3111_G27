package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.controllers.admin.AdminLoginController;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.AdminProfileUpdateRequest;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AdminLoginTest {

    private AdminLoginController controller;
    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        SessionManager.logout();
        dataService = DummyDataService.getInstance();
        dataService.resetToSeedData();
        controller = new AdminLoginController();
    }

    @AfterEach
    void tearDown() {
        dataService.resetToSeedData();
        SessionManager.logout();
    }

    @Test
    void testSeededAdminExistsInDummyDataService() {
        User admin = dataService.findUser("admin");
        assertNotNull(admin);
        assertEquals("HKUST Marketplace Admin", admin.getFullName());
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertTrue(admin.isActive());
    }

    @Test
    void testValidSeededAdminLoginSucceeds() {
        String result = controller.authenticateAdmin("admin", "admin123");

        assertEquals("OK", result);
        assertNotNull(SessionManager.getCurrentUser());
        assertEquals("admin", SessionManager.getCurrentUser().getUsername());
        assertEquals(UserRole.ADMIN, SessionManager.getCurrentUser().getRole());
    }

    @Test
    void testValidSeededAdminLoginCaseInsensitiveAndTrimmed() {
        String result = controller.authenticateAdmin("  ADMIN  ", "admin123");

        assertEquals("OK", result);
        assertEquals("admin", SessionManager.getCurrentUser().getUsername());
    }

    @Test
    void testEmptyOrNullCredentialsFail() {
        assertEquals("Please enter credentials.", controller.authenticateAdmin("", "admin123"));
        assertEquals("Please enter credentials.", controller.authenticateAdmin("   ", "admin123"));
        assertEquals("Please enter credentials.", controller.authenticateAdmin(null, "admin123"));
        assertEquals("Please enter credentials.", controller.authenticateAdmin("admin", ""));
        assertEquals("Please enter credentials.", controller.authenticateAdmin("admin", null));
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testUnknownUserCannotLogin() {
        String result = controller.authenticateAdmin("unknown_user", "admin123");
        assertEquals("Invalid admin credentials.", result);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testSeededBuyerCannotLoginToAdminPortal() {
        String result = controller.authenticateAdmin("alice", "password1");
        assertEquals("Invalid admin credentials.", result);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testSeededSellerCannotLoginToAdminPortal() {
        String result = controller.authenticateAdmin("charlie", "password1");
        assertEquals("Invalid admin credentials.", result);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testSeededAdminWrongPasswordFails() {
        String result = controller.authenticateAdmin("admin", "wrong_password");
        assertEquals("Incorrect password.", result);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testDeactivatedAdminCannotLogin() {
        User admin = dataService.findUser("admin");
        admin.setActive(false);

        String result = controller.authenticateAdmin("admin", "admin123");
        assertEquals("Admin account is deactivated.", result);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testAdminRegistrationIsBlocked() {
        Admin rogueAdmin = new Admin("admin2", "Rogue Admin", "Pass1234!");
        assertThrows(IllegalArgumentException.class, () -> dataService.registerUser(rogueAdmin));
        assertNull(dataService.findUser("admin2"));
    }

    @Test
    void testAdminCanLoginWithNewHashedPasswordAfterProfileUpdate() {
        AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                "admin", "Updated Admin",
                "admin123", "BrandNewPass2026", "BrandNewPass2026");
        dataService.getAdminProfileService().updateProfile("admin", req);

        // Old password now fails
        String oldResult = controller.authenticateAdmin("admin", "admin123");
        assertEquals("Incorrect password.", oldResult);

        // New password succeeds
        String newResult = controller.authenticateAdmin("admin", "BrandNewPass2026");
        assertEquals("OK", newResult);
        assertNotNull(SessionManager.getCurrentUser());
        assertEquals("Updated Admin", SessionManager.getCurrentUser().getFullName());
    }
}