package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.AdminProfileUpdateRequest;
import com.hkust.marketplace.models.LoginResult;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.services.AdminLoginService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.PasswordUtil;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminLogin Tests (US-A01 Log In As Admin)")
public class AdminLoginTest {

    private AdminLoginController controller;
    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        SessionManager.logout();
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        controller = new AdminLoginController();
    }

    @AfterEach
    void tearDown() {
        dataService.resetData();
        SessionManager.logout();
    }

    @Test
    void testSeededAdminExistsInDummyDataService() {
        User admin = dataService.findUser("admin");
        assertNotNull(admin);
        assertEquals("HKUST Marketplace Admin", admin.getFullName());
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertTrue(admin.isActive());
        // F01 regression: Seeded admin password is stored hashed with PBKDF2 and never plaintext
        String stored = admin.getPasswordHash();
        assertNotNull(stored);
        assertNotEquals("admin123", stored, "Admin password must not be stored in plaintext");
        assertTrue(stored.startsWith("pbkdf2_sha256$"), "Admin password hash must use PBKDF2");
        assertTrue(PasswordHasher.verify("admin123", stored));
        assertTrue(PasswordUtil.verifyPassword("admin123", stored));
        assertFalse(PasswordUtil.verifyPassword("secret", "secret"), "Plaintext must not be accepted by PasswordUtil");
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
        assertEquals(LoginResult.MSG_EMPTY_CREDENTIALS, controller.authenticateAdmin("", "admin123"));
        assertEquals(LoginResult.MSG_EMPTY_CREDENTIALS, controller.authenticateAdmin("   ", "admin123"));
        assertEquals(LoginResult.MSG_EMPTY_CREDENTIALS, controller.authenticateAdmin(null, "admin123"));
        assertEquals(LoginResult.MSG_EMPTY_CREDENTIALS, controller.authenticateAdmin("admin", ""));
        assertEquals(LoginResult.MSG_EMPTY_CREDENTIALS, controller.authenticateAdmin("admin", null));
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testUnknownUserCannotLogin() {
        String result = controller.authenticateAdmin("unknown_user", "admin123");
        assertEquals(LoginResult.MSG_INVALID_CREDENTIALS, result);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testSeededBuyerCannotLoginToAdminPortal() {
        String result = controller.authenticateAdmin("alice", "password1");
        assertEquals(AdminLoginService.MSG_WRONG_ROLE_ADMIN, result);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testSeededSellerCannotLoginToAdminPortal() {
        String result = controller.authenticateAdmin("charlie", "password1");
        assertEquals(AdminLoginService.MSG_WRONG_ROLE_ADMIN, result);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testBuyerOrSellerWithWrongPasswordGetsGenericInvalidCredentials() {
        // A2 must take precedence over A3: role is not leaked to unauthenticated users
        String result = controller.authenticateAdmin("alice", "wrong_password");
        assertEquals(LoginResult.MSG_INVALID_CREDENTIALS, result);
        assertNull(SessionManager.getCurrentUser());

        String sellerResult = controller.authenticateAdmin("charlie", "wrong_password");
        assertEquals(LoginResult.MSG_INVALID_CREDENTIALS, sellerResult);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testSeededAdminWrongPasswordFails() {
        String result = controller.authenticateAdmin("admin", "wrong_password");
        assertEquals(LoginResult.MSG_INVALID_CREDENTIALS, result);
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void testDeactivatedAdminCannotLogin() {
        User admin = dataService.findUser("admin");
        admin.setActive(false);

        String result = controller.authenticateAdmin("admin", "admin123");
        assertEquals(LoginResult.MSG_ACCOUNT_DEACTIVATED, result);
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
        dataService.updateAdminProfile("admin", req);

        // Old password now fails
        String oldResult = controller.authenticateAdmin("admin", "admin123");
        assertEquals(LoginResult.MSG_INVALID_CREDENTIALS, oldResult);

        // New password succeeds
        String newResult = controller.authenticateAdmin("admin", "BrandNewPass2026");
        assertEquals("OK", newResult);
        assertNotNull(SessionManager.getCurrentUser());
        assertEquals("Updated Admin", SessionManager.getCurrentUser().getFullName());
    }
}
