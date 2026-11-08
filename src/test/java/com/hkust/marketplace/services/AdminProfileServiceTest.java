package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordPolicy;
import com.hkust.marketplace.utils.PasswordUtil;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminProfileService Unit & Concurrency Tests (US-A04)")
class AdminProfileServiceTest {

    private DummyDataService dataService;
    private AdminProfileService profileService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        profileService = new AdminProfileService(dataService);
        SessionManager.logout();
    }

    @AfterEach
    void tearDown() {
        dataService.resetData();
        SessionManager.logout();
    }

    @Nested
    @DisplayName("Basic Flow Tests (UC 3.4 Steps 1-9)")
    class BasicFlowTests {

        @Test
        @DisplayName("Update full name only without modifying password succeeds")
        void testUpdateFullNameOnly() {
            Admin admin = profileService.getProfile("admin");
            assertNotNull(admin);
            assertEquals("HKUST Marketplace Admin", admin.getFullName());
            String initialHash = admin.getPasswordHash();

            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Chief Platform Administrator", "", "", "");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertTrue(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.SUCCESS, result.getStatus());
            assertEquals("Chief Platform Administrator", admin.getFullName());
            assertEquals(initialHash, admin.getPasswordHash(), "Password hash must remain unchanged");
        }

        @Test
        @DisplayName("Update full name and password with valid credentials hashes new password")
        void testUpdateFullNameAndPasswordValid() {
            Admin admin = profileService.getProfile("admin");
            assertNotNull(admin);
            // Initial seed password is "admin123"
            assertTrue(PasswordUtil.verifyPassword("admin123", admin.getPasswordHash()));

            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Updated Admin Name",
                    "admin123", "SecureAdminPass2026", "SecureAdminPass2026");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertTrue(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.SUCCESS, result.getStatus());
            assertEquals("Updated Admin Name", admin.getFullName());

            // Password is now updated and stored in hashed form
            assertTrue(PasswordUtil.verifyPassword("SecureAdminPass2026", admin.getPasswordHash()));
            assertFalse(PasswordUtil.verifyPassword("admin123", admin.getPasswordHash()));
            assertNotEquals("SecureAdminPass2026", admin.getPasswordHash(), "Password must be stored hashed");
        }

        @Test
        @DisplayName("Synchronizes active session user when session is logged in as the admin")
        void testSessionSynchronizationOnUpdate() {
            Admin admin = profileService.getProfile("admin");
            SessionManager.login(admin);

            assertEquals("HKUST Marketplace Admin", SessionManager.getCurrentUser().getFullName());

            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "New Synced Name",
                    "admin123", "NewPassWord123", "NewPassWord123");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertTrue(result.isSuccess());
            assertEquals("New Synced Name", SessionManager.getCurrentUser().getFullName());
            assertTrue(PasswordUtil.verifyPassword("NewPassWord123", SessionManager.getCurrentUser().getPasswordHash()));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A1: Empty Full Name")
    class EmptyFullNameTests {

        @Test
        @DisplayName("Null full name is rejected")
        void testNullFullNameRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest("admin", null);

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.EMPTY_FULL_NAME, result.getStatus());
            assertTrue(result.getAffectedFields().contains("fullName"));
            assertEquals("HKUST Marketplace Admin", profileService.getProfile("admin").getFullName());
        }

        @Test
        @DisplayName("Empty string full name is rejected")
        void testEmptyFullNameRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest("admin", "");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.EMPTY_FULL_NAME, result.getStatus());
            assertTrue(result.getAffectedFields().contains("fullName"));
        }

        @Test
        @DisplayName("Whitespace-only full name is rejected")
        void testWhitespaceFullNameRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest("admin", "   \t\n  ");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.EMPTY_FULL_NAME, result.getStatus());
            assertTrue(result.getAffectedFields().contains("fullName"));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A2: Wrong Current Password")
    class WrongCurrentPasswordTests {

        @Test
        @DisplayName("Missing current password when changing password is rejected")
        void testMissingCurrentPasswordRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Admin Name",
                    "", "NewValidPass123", "NewValidPass123");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.CURRENT_PASSWORD_REQUIRED, result.getStatus());
            assertTrue(result.getAffectedFields().contains("currentPassword"));
            assertTrue(PasswordUtil.verifyPassword("admin123", profileService.getProfile("admin").getPasswordHash()));
        }

        @Test
        @DisplayName("Incorrect current password is rejected and preserves old password")
        void testIncorrectCurrentPasswordRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Admin Name",
                    "incorrectOldPass", "NewValidPass123", "NewValidPass123");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.WRONG_CURRENT_PASSWORD, result.getStatus());
            assertTrue(result.getAffectedFields().contains("currentPassword"));
            assertTrue(PasswordUtil.verifyPassword("admin123", profileService.getProfile("admin").getPasswordHash()));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A3: Invalid New Password")
    class InvalidNewPasswordTests {

        @Test
        @DisplayName("Missing new password is rejected")
        void testMissingNewPasswordRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Admin Name",
                    "admin123", "", "Confirm123");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.NEW_PASSWORD_REQUIRED, result.getStatus());
            assertTrue(result.getAffectedFields().contains("newPassword"));
        }

        @Test
        @DisplayName("Missing confirm password is rejected")
        void testMissingConfirmPasswordRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Admin Name",
                    "admin123", "ValidPass123", null);

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.CONFIRM_PASSWORD_REQUIRED, result.getStatus());
            assertTrue(result.getAffectedFields().contains("confirmPassword"));
        }

        @Test
        @DisplayName("Password confirmation mismatch is rejected")
        void testConfirmationMismatchRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Admin Name",
                    "admin123", "ValidPass123", "MismatchedPass123");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.PASSWORD_CONFIRMATION_MISMATCH, result.getStatus());
            assertTrue(result.getAffectedFields().contains("confirmPassword"));
        }

        @Test
        @DisplayName("Password too short (<8 chars) is rejected")
        void testPasswordTooShortRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Admin Name",
                    "admin123", "Short1A", "Short1A");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.WEAK_NEW_PASSWORD, result.getStatus());
            assertTrue(result.getPasswordViolations().contains(PasswordPolicy.RULE_MIN_LENGTH));
        }

        @Test
        @DisplayName("Password without uppercase letter is rejected")
        void testPasswordWithoutUppercaseRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Admin Name",
                    "admin123", "lowercase123", "lowercase123");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.WEAK_NEW_PASSWORD, result.getStatus());
            assertTrue(result.getPasswordViolations().contains(PasswordPolicy.RULE_UPPERCASE));
        }

        @Test
        @DisplayName("Password without lowercase letter is rejected")
        void testPasswordWithoutLowercaseRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Admin Name",
                    "admin123", "UPPERCASE123", "UPPERCASE123");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.WEAK_NEW_PASSWORD, result.getStatus());
            assertTrue(result.getPasswordViolations().contains(PasswordPolicy.RULE_LOWERCASE));
        }

        @Test
        @DisplayName("Password without digit is rejected")
        void testPasswordWithoutDigitRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin", "Admin Name",
                    "admin123", "PasswordNoDigit", "PasswordNoDigit");

            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.WEAK_NEW_PASSWORD, result.getStatus());
            assertTrue(result.getPasswordViolations().contains(PasswordPolicy.RULE_DIGIT));
        }
    }

    @Nested
    @DisplayName("Authorization, Null-Safety, and Edge Cases")
    class AuthorizationAndLookupTests {

        @Test
        @DisplayName("Null or empty username is rejected")
        void testNullOrEmptyUsername() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest("admin", "Admin Name");

            AdminProfileUpdateResult r1 = profileService.updateProfile((String) null, req);
            assertFalse(r1.isSuccess());
            assertEquals(AdminProfileUpdateStatus.USER_NOT_FOUND, r1.getStatus());

            AdminProfileUpdateResult r2 = profileService.updateProfile("   ", req);
            assertFalse(r2.isSuccess());
            assertEquals(AdminProfileUpdateStatus.USER_NOT_FOUND, r2.getStatus());

            assertNull(profileService.getProfile(null));
            assertNull(profileService.getProfile("   "));
        }

        @Test
        @DisplayName("Non-existent admin username is rejected")
        void testNonExistentAdmin() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest("unknownAdmin", "Admin Name");

            AdminProfileUpdateResult result = profileService.updateProfile("unknownAdmin", req);
            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.USER_NOT_FOUND, result.getStatus());
            assertNull(profileService.getProfile("unknownAdmin"));
        }

        @Test
        @DisplayName("Non-admin user (buyer/seller) is rejected with UNAUTHORIZED")
        void testNonAdminUserRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest("alice", "Alice Admin");

            AdminProfileUpdateResult result = profileService.updateProfile("alice", req);
            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.UNAUTHORIZED, result.getStatus());
            assertNull(profileService.getProfile("alice"));
        }

        @Test
        @DisplayName("Deactivated admin user is rejected with UNAUTHORIZED")
        void testDeactivatedAdminRejected() {
            Admin admin = profileService.getProfile("admin");
            assertNotNull(admin);
            admin.setActive(false);

            assertNull(profileService.getProfile("admin"));

            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest("admin", "Admin Name");
            AdminProfileUpdateResult result = profileService.updateProfile("admin", req);
            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.UNAUTHORIZED, result.getStatus());
        }

        @Test
        @DisplayName("Direct updateProfile with null Admin entity is rejected")
        void testNullAdminEntityRejected() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest("admin", "Admin Name");
            AdminProfileUpdateResult result = profileService.updateProfile((Admin) null, req);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.USER_NOT_FOUND, result.getStatus());
        }

        @Test
        @DisplayName("Direct updateProfile with null request is rejected")
        void testNullRequestRejected() {
            Admin admin = profileService.getProfile("admin");
            AdminProfileUpdateResult result = profileService.updateProfile(admin, null);

            assertFalse(result.isSuccess());
            assertEquals(AdminProfileUpdateStatus.EMPTY_FULL_NAME, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Concurrency & Thread-Safety Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent profile updates execute safely without race condition")
        void testConcurrentProfileUpdates() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            List<AdminProfileUpdateResult> results = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                                "admin", "Admin Thread " + index);
                        AdminProfileUpdateResult res = profileService.updateProfile("admin", req);
                        if (res.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                        synchronized (results) {
                            results.add(res);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            latch.countDown();
            doneLatch.await();
            executor.shutdown();

            assertEquals(threadCount, successCount.get());
            Admin admin = profileService.getProfile("admin");
            assertNotNull(admin);
            assertTrue(admin.getFullName().startsWith("Admin Thread "));
        }
    }
}
