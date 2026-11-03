package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BuyerProfileService Unit & Concurrency Tests (US-B08)")
class BuyerProfileServiceTest {

    private DummyDataService dataService;
    private BuyerProfileService profileService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        profileService = new BuyerProfileService(dataService);
    }

    @Nested
    @DisplayName("Basic Flow Tests (UC-B08 Steps 1-9)")
    class BasicFlowTests {

        @Test
        @DisplayName("Update full name and residence without changing password succeeds")
        void testUpdateProfileNameAndResidenceOnly() {
            Buyer alice = profileService.getProfile("alice");
            assertNotNull(alice);
            assertEquals("Alice Chan", alice.getFullName());
            assertEquals("Hall 3", alice.getResidence());
            String initialHash = alice.getPasswordHash();

            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice C. Chan", "Hall 5", "", "", "");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertTrue(result.isSuccess());
            assertEquals(ProfileUpdateStatus.SUCCESS, result.getStatus());
            assertEquals("Alice C. Chan", alice.getFullName());
            assertEquals("Hall 5", alice.getResidence());
            assertEquals("Hall 5", alice.getResidenceHall());
            assertEquals(initialHash, alice.getPasswordHash(), "Password hash must not change");
        }

        @Test
        @DisplayName("Update profile with null or empty residence clears residence")
        void testUpdateProfileClearResidence() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "   ");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertTrue(result.isSuccess());
            Buyer alice = profileService.getProfile("alice");
            assertNull(alice.getResidence());
            assertNull(alice.getResidenceHall());
        }

        @Test
        @DisplayName("Update profile with valid password change hashes new password and succeeds")
        void testUpdateProfileWithValidPasswordChange() {
            Buyer alice = profileService.getProfile("alice");
            assertNotNull(alice);

            // Alice's seed password is "password1"
            assertTrue(PasswordUtil.verifyPassword("password1", alice.getPasswordHash()));

            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan Updated", "Hall 1",
                    "password1", "ValidPass2026", "ValidPass2026");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertTrue(result.isSuccess());
            assertEquals(ProfileUpdateStatus.SUCCESS, result.getStatus());
            assertEquals("Alice Chan Updated", alice.getFullName());
            assertEquals("Hall 1", alice.getResidence());

            // New password verified, old password fails
            assertTrue(PasswordUtil.verifyPassword("ValidPass2026", alice.getPasswordHash()));
            assertFalse(PasswordUtil.verifyPassword("password1", alice.getPasswordHash()));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A1: Empty Full Name")
    class EmptyFullNameTests {

        @Test
        @DisplayName("Null full name fails with EMPTY_FULL_NAME")
        void testNullFullName() {
            ProfileUpdateRequest req = new ProfileUpdateRequest("alice", null, "Hall 3");
            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.EMPTY_FULL_NAME, result.getStatus());
            assertEquals(Set.of("fullName"), result.getAffectedFields());
        }

        @Test
        @DisplayName("Empty string full name fails with EMPTY_FULL_NAME")
        void testEmptyFullName() {
            ProfileUpdateRequest req = new ProfileUpdateRequest("alice", "", "Hall 3");
            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.EMPTY_FULL_NAME, result.getStatus());
        }

        @Test
        @DisplayName("Whitespace only full name fails with EMPTY_FULL_NAME")
        void testWhitespaceOnlyFullName() {
            ProfileUpdateRequest req = new ProfileUpdateRequest("alice", "   \t\n  ", "Hall 3");
            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.EMPTY_FULL_NAME, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A2: Wrong or Missing Current Password")
    class CurrentPasswordTests {

        @Test
        @DisplayName("Missing current password when new password provided fails with CURRENT_PASSWORD_REQUIRED")
        void testMissingCurrentPasswordWithNewPassword() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    null, "NewPass123", "NewPass123");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.CURRENT_PASSWORD_REQUIRED, result.getStatus());
            assertEquals(Set.of("currentPassword"), result.getAffectedFields());
        }

        @Test
        @DisplayName("Empty current password fails with CURRENT_PASSWORD_REQUIRED")
        void testEmptyCurrentPassword() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "", "NewPass123", "NewPass123");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.CURRENT_PASSWORD_REQUIRED, result.getStatus());
        }

        @Test
        @DisplayName("Incorrect current password fails with WRONG_CURRENT_PASSWORD and saves nothing")
        void testWrongCurrentPasswordSavesNothing() {
            Buyer alice = profileService.getProfile("alice");
            String originalHash = alice.getPasswordHash();
            String originalName = alice.getFullName();

            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Hacked Name", "Hall 9",
                    "wrongPasswordAttempt", "NewPass123", "NewPass123");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.WRONG_CURRENT_PASSWORD, result.getStatus());
            assertEquals("Current password is incorrect.", result.getMessage());
            assertEquals(Set.of("currentPassword"), result.getAffectedFields());

            // Verify nothing was saved
            assertEquals(originalName, alice.getFullName());
            assertEquals(originalHash, alice.getPasswordHash());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A3: Invalid New Password")
    class InvalidNewPasswordTests {

        @Test
        @DisplayName("Missing new password when current password provided fails with NEW_PASSWORD_REQUIRED")
        void testMissingNewPassword() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "password1", null, "NewPass123");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.NEW_PASSWORD_REQUIRED, result.getStatus());
            assertEquals(Set.of("newPassword"), result.getAffectedFields());
        }

        @Test
        @DisplayName("Empty new password fails with NEW_PASSWORD_REQUIRED")
        void testEmptyNewPassword() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "password1", "", "NewPass123");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.NEW_PASSWORD_REQUIRED, result.getStatus());
        }

        @Test
        @DisplayName("Missing confirm password fails with CONFIRM_PASSWORD_REQUIRED")
        void testMissingConfirmPassword() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "password1", "NewPass123", null);

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.CONFIRM_PASSWORD_REQUIRED, result.getStatus());
            assertEquals(Set.of("confirmPassword"), result.getAffectedFields());
        }

        @Test
        @DisplayName("Empty confirm password fails with CONFIRM_PASSWORD_REQUIRED")
        void testEmptyConfirmPassword() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "password1", "NewPass123", "");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.CONFIRM_PASSWORD_REQUIRED, result.getStatus());
        }

        @Test
        @DisplayName("New password and confirm password mismatch fails with PASSWORD_CONFIRMATION_MISMATCH")
        void testPasswordConfirmationMismatch() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "password1", "NewPass123", "DifferentPass456");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.PASSWORD_CONFIRMATION_MISMATCH, result.getStatus());
            assertEquals("Passwords do not match.", result.getMessage());
            assertEquals(Set.of("confirmPassword"), result.getAffectedFields());
        }

        @Test
        @DisplayName("New password too short fails with WEAK_NEW_PASSWORD")
        void testWeakPasswordTooShort() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "password1", "Ab1!", "Ab1!");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.WEAK_NEW_PASSWORD, result.getStatus());
            assertTrue(result.getPasswordViolations().contains("at least 8 characters"));
        }

        @Test
        @DisplayName("New password missing uppercase letter fails with WEAK_NEW_PASSWORD")
        void testWeakPasswordMissingUppercase() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "password1", "lowercase123", "lowercase123");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.WEAK_NEW_PASSWORD, result.getStatus());
            assertTrue(result.getPasswordViolations().contains("at least one uppercase letter"));
        }

        @Test
        @DisplayName("New password missing lowercase letter fails with WEAK_NEW_PASSWORD")
        void testWeakPasswordMissingLowercase() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "password1", "UPPERCASE123", "UPPERCASE123");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.WEAK_NEW_PASSWORD, result.getStatus());
            assertTrue(result.getPasswordViolations().contains("at least one lowercase letter"));
        }

        @Test
        @DisplayName("New password missing digit fails with WEAK_NEW_PASSWORD")
        void testWeakPasswordMissingDigit() {
            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    "alice", "Alice Chan", "Hall 3",
                    "password1", "NoDigitsAllowed", "NoDigitsAllowed");

            ProfileUpdateResult result = profileService.updateProfile("alice", req);

            assertFalse(result.isSuccess());
            assertEquals(ProfileUpdateStatus.WEAK_NEW_PASSWORD, result.getStatus());
            assertTrue(result.getPasswordViolations().contains("at least one digit"));
        }
    }

    @Nested
    @DisplayName("User Lookup & Authorization Edge Cases")
    class AuthorizationAndLookupTests {

        @Test
        @DisplayName("Null or whitespace username fails with USER_NOT_FOUND")
        void testNullOrBlankUsername() {
            ProfileUpdateRequest req = new ProfileUpdateRequest("alice", "Alice", "Hall 1");
            ProfileUpdateResult r1 = profileService.updateProfile((String) null, req);
            assertEquals(ProfileUpdateStatus.USER_NOT_FOUND, r1.getStatus());

            ProfileUpdateResult r2 = profileService.updateProfile("   ", req);
            assertEquals(ProfileUpdateStatus.USER_NOT_FOUND, r2.getStatus());
        }

        @Test
        @DisplayName("Unknown username fails with USER_NOT_FOUND")
        void testUnknownUsername() {
            ProfileUpdateRequest req = new ProfileUpdateRequest("unknown", "Unknown", "Hall 1");
            ProfileUpdateResult result = profileService.updateProfile("nonexistent_user", req);
            assertEquals(ProfileUpdateStatus.USER_NOT_FOUND, result.getStatus());
        }

        @Test
        @DisplayName("Non-buyer account (seller/admin) fails with UNAUTHORIZED")
        void testNonBuyerUnauthorized() {
            ProfileUpdateRequest req = new ProfileUpdateRequest("charlie", "Charlie Seller", "Library");
            ProfileUpdateResult r1 = profileService.updateProfile("charlie", req);
            assertEquals(ProfileUpdateStatus.UNAUTHORIZED, r1.getStatus());

            ProfileUpdateResult r2 = profileService.updateProfile("admin", req);
            assertEquals(ProfileUpdateStatus.UNAUTHORIZED, r2.getStatus());
        }

        @Test
        @DisplayName("Deactivated buyer fails with UNAUTHORIZED")
        void testDeactivatedBuyerUnauthorized() {
            Buyer bob = (Buyer) dataService.findUser("bob");
            bob.setAccountStatus(AccountStatus.DEACTIVATED);

            ProfileUpdateRequest req = new ProfileUpdateRequest("bob", "Bob Wong", "Hall 2");
            ProfileUpdateResult result = profileService.updateProfile("bob", req);
            assertEquals(ProfileUpdateStatus.UNAUTHORIZED, result.getStatus());
        }

        @Test
        @DisplayName("Direct updateProfile with null buyer fails with USER_NOT_FOUND")
        void testDirectNullBuyer() {
            ProfileUpdateRequest req = new ProfileUpdateRequest("alice", "Alice", "Hall 1");
            ProfileUpdateResult result = profileService.updateProfile((Buyer) null, req);
            assertEquals(ProfileUpdateStatus.USER_NOT_FOUND, result.getStatus());
        }

        @Test
        @DisplayName("Direct updateProfile with null request fails with EMPTY_FULL_NAME")
        void testDirectNullRequest() {
            Buyer alice = profileService.getProfile("alice");
            ProfileUpdateResult result = profileService.updateProfile(alice, null);
            assertEquals(ProfileUpdateStatus.EMPTY_FULL_NAME, result.getStatus());
        }

        @Test
        @DisplayName("getProfile edge cases")
        void testGetProfileEdgeCases() {
            assertNull(profileService.getProfile(null));
            assertNull(profileService.getProfile("   "));
            assertNull(profileService.getProfile("nonexistent"));
            assertNull(profileService.getProfile("charlie")); // Seller
            assertNull(profileService.getProfile("admin")); // Admin

            Buyer bob = (Buyer) dataService.findUser("bob");
            bob.setAccountStatus(AccountStatus.DELETED);
            assertNull(profileService.getProfile("bob")); // Inactive
        }
    }

    @Nested
    @DisplayName("Concurrency & Stress Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent profile updates from multiple threads remain consistent")
        void testConcurrentProfileUpdates() throws InterruptedException {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        ProfileUpdateRequest req = new ProfileUpdateRequest(
                                "alice", "Alice Version " + index, "Hall " + (index % 9 + 1));
                        ProfileUpdateResult res = profileService.updateProfile("alice", req);
                        if (res.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(threadCount, successCount.get());
            Buyer alice = profileService.getProfile("alice");
            assertNotNull(alice);
            assertTrue(alice.getFullName().startsWith("Alice Version "));
        }
    }
}
