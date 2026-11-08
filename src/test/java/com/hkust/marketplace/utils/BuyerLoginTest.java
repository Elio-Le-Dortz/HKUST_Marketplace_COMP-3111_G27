package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-B02: Buyer Login Specification & Flow Tests")
class BuyerLoginTest {

    private DummyDataService service;

    @BeforeEach
    void setUp() {
        service = DummyDataService.getInstance();
        service.resetData();
        SessionManager.logout();
    }

    @Nested
    @DisplayName("Basic Flow: Successful Buyer Authentication")
    class BasicFlowTests {

        @Test
        @DisplayName("Seeded buyer 'alice' logs in with correct password")
        void testLoginSeededBuyerAlice() {
            LoginResult result = service.loginBuyer("alice", "password1");

            assertTrue(result.isSuccess(), "Login should succeed for valid buyer credentials");
            assertEquals(LoginResult.MSG_SUCCESS, result.getMessage());
            assertEquals(LoginErrorType.NONE, result.getErrorType());
            assertTrue(result.getErrorFields().isEmpty());

            Buyer buyer = result.getUserAs(Buyer.class);
            assertNotNull(buyer);
            assertEquals("alice", buyer.getUsername());
            assertEquals("Alice Chan", buyer.getFullName());
            assertEquals(UserRole.BUYER, buyer.getRole());
            assertTrue(buyer.isActive());
        }

        @Test
        @DisplayName("Seeded buyer 'bob' logs in with correct password")
        void testLoginSeededBuyerBob() {
            LoginResult result = service.loginBuyer("bob", "password1");

            assertTrue(result.isSuccess());
            Buyer buyer = result.getUserAs(Buyer.class);
            assertNotNull(buyer);
            assertEquals("bob", buyer.getUsername());
        }

        @Test
        @DisplayName("Newly registered buyer logs in successfully with salted password")
        void testLoginRegisteredBuyer() {
            BuyerRegistrationResult reg = service.registerBuyer(
                    "newbuyer", "New Buyer", "ValidPass123", "ValidPass123", "Hall 1");
            assertTrue(reg.isSuccess());

            LoginResult result = service.loginBuyer("newbuyer", "ValidPass123");
            assertTrue(result.isSuccess());
            Buyer buyer = result.getUserAs(Buyer.class);
            assertNotNull(buyer);
            assertEquals("newbuyer", buyer.getUsername());
        }

        @Test
        @DisplayName("Username is case-insensitive")
        void testUsernameCaseInsensitive() {
            assertTrue(service.loginBuyer("ALICE", "password1").isSuccess());
            assertTrue(service.loginBuyer("Alice", "password1").isSuccess());
            assertTrue(service.loginBuyer("aLiCe", "password1").isSuccess());
        }

        @Test
        @DisplayName("Username leading and trailing whitespace is trimmed")
        void testUsernameWhitespaceTrimmed() {
            LoginResult result = service.loginBuyer("   alice   ", "password1");
            assertTrue(result.isSuccess());
            assertEquals("alice", result.getUser().getUsername());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A1: Empty Fields")
    class EmptyFieldTests {

        @Test
        @DisplayName("Both username and password empty")
        void testBothEmpty() {
            LoginResult result = service.loginBuyer("", "");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_EMPTY_CREDENTIALS, result.getMessage());
            assertEquals(LoginErrorType.EMPTY_FIELDS, result.getErrorType());
            assertTrue(result.hasFieldError("username"));
            assertTrue(result.hasFieldError("password"));
        }

        @Test
        @DisplayName("Both username and password null")
        void testBothNull() {
            LoginResult result = service.loginBuyer(null, null);
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_EMPTY_CREDENTIALS, result.getMessage());
            assertEquals(LoginErrorType.EMPTY_FIELDS, result.getErrorType());
            assertTrue(result.hasFieldError("username"));
            assertTrue(result.hasFieldError("password"));
        }

        @Test
        @DisplayName("Whitespace-only username and empty password")
        void testWhitespaceUsernameAndEmptyPassword() {
            LoginResult result = service.loginBuyer("    ", "");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_EMPTY_CREDENTIALS, result.getMessage());
            assertEquals(LoginErrorType.EMPTY_FIELDS, result.getErrorType());
        }

        @Test
        @DisplayName("Empty username with non-empty password")
        void testEmptyUsername() {
            LoginResult result = service.loginBuyer("", "password1");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_USERNAME_REQUIRED, result.getMessage());
            assertEquals(LoginErrorType.EMPTY_FIELDS, result.getErrorType());
            assertTrue(result.hasFieldError("username"));
            assertFalse(result.hasFieldError("password"));
        }

        @Test
        @DisplayName("Null username with non-empty password")
        void testNullUsername() {
            LoginResult result = service.loginBuyer(null, "password1");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_USERNAME_REQUIRED, result.getMessage());
            assertTrue(result.hasFieldError("username"));
        }

        @Test
        @DisplayName("Non-empty username with empty password")
        void testEmptyPassword() {
            LoginResult result = service.loginBuyer("alice", "");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_PASSWORD_REQUIRED, result.getMessage());
            assertEquals(LoginErrorType.EMPTY_FIELDS, result.getErrorType());
            assertFalse(result.hasFieldError("username"));
            assertTrue(result.hasFieldError("password"));
        }

        @Test
        @DisplayName("Non-empty username with null password")
        void testNullPassword() {
            LoginResult result = service.loginBuyer("alice", null);
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_PASSWORD_REQUIRED, result.getMessage());
            assertTrue(result.hasFieldError("password"));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A2: Invalid Credentials")
    class InvalidCredentialsTests {

        @Test
        @DisplayName("Non-existent username fails with generic invalid credentials message")
        void testNonExistentUser() {
            LoginResult result = service.loginBuyer("nonexistent_user", "password1");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_INVALID_CREDENTIALS, result.getMessage());
            assertEquals(LoginErrorType.INVALID_CREDENTIALS, result.getErrorType());
            assertTrue(result.hasFieldError("username"));
            assertTrue(result.hasFieldError("password"));
        }

        @Test
        @DisplayName("Existing buyer with incorrect password fails with generic message")
        void testIncorrectPassword() {
            LoginResult result = service.loginBuyer("alice", "wrong_password");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_INVALID_CREDENTIALS, result.getMessage());
            assertEquals(LoginErrorType.INVALID_CREDENTIALS, result.getErrorType());
            assertTrue(result.hasFieldError("username"));
            assertTrue(result.hasFieldError("password"));
        }

        @Test
        @DisplayName("Error message does not reveal whether username or password was incorrect")
        void testGenericErrorSecurity() {
            LoginResult wrongUser = service.loginBuyer("unknown_user_123", "password1");
            LoginResult wrongPass = service.loginBuyer("alice", "wrong_pass_123");

            assertEquals(wrongUser.getMessage(), wrongPass.getMessage(),
                    "System must not distinguish between invalid username and invalid password");
            assertEquals(wrongUser.getErrorType(), wrongPass.getErrorType());
            assertEquals(wrongUser.getErrorFields(), wrongPass.getErrorFields());
        }

        @Test
        @DisplayName("Seller username with wrong password reports invalid credentials (no role leak)")
        void testSellerWithWrongPassword() {
            LoginResult result = service.loginBuyer("charlie", "incorrect_password");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_INVALID_CREDENTIALS, result.getMessage());
            assertEquals(LoginErrorType.INVALID_CREDENTIALS, result.getErrorType());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A3: Wrong Account Type")
    class WrongAccountTypeTests {

        @Test
        @DisplayName("Seller account entering Buyer portal is rejected with portal redirect message")
        void testSellerRejected() {
            LoginResult result = service.loginBuyer("charlie", "password1");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_WRONG_ROLE_BUYER, result.getMessage());
            assertEquals(LoginErrorType.WRONG_ROLE, result.getErrorType());
            assertTrue(result.hasFieldError("username"));
        }

        @Test
        @DisplayName("Second seeded seller 'diana' is also rejected")
        void testDianaSellerRejected() {
            LoginResult result = service.loginBuyer("diana", "password1");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_WRONG_ROLE_BUYER, result.getMessage());
            assertEquals(LoginErrorType.WRONG_ROLE, result.getErrorType());
        }

        @Test
        @DisplayName("Admin account entering Buyer portal is rejected with portal redirect message")
        void testAdminRejected() {
            LoginResult result = service.loginBuyer("admin", "admin123");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_WRONG_ROLE_BUYER, result.getMessage());
            assertEquals(LoginErrorType.WRONG_ROLE, result.getErrorType());
            assertTrue(result.hasFieldError("username"));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A4: Account Deactivated or Deleted")
    class AccountDeactivatedTests {

        @Test
        @DisplayName("Deactivated buyer account cannot log in")
        void testDeactivatedBuyer() {
            User alice = service.findUser("alice");
            alice.setAccountStatus(AccountStatus.DEACTIVATED);

            LoginResult result = service.loginBuyer("alice", "password1");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_ACCOUNT_DEACTIVATED, result.getMessage());
            assertEquals(LoginErrorType.ACCOUNT_DEACTIVATED, result.getErrorType());
            assertTrue(result.hasFieldError("username"));
        }

        @Test
        @DisplayName("Soft-deleted buyer account cannot log in")
        void testDeletedBuyer() {
            User bob = service.findUser("bob");
            bob.setAccountStatus(AccountStatus.DELETED);

            LoginResult result = service.loginBuyer("bob", "password1");
            assertFalse(result.isSuccess());
            assertEquals(LoginResult.MSG_ACCOUNT_DELETED, result.getMessage());
            assertEquals(LoginErrorType.ACCOUNT_DEACTIVATED, result.getErrorType());
            assertTrue(result.hasFieldError("username"));
        }
    }

    @Nested
    @DisplayName("Concurrency and Thread-Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent logins for existing users succeed deterministically")
        void testConcurrentLogins() throws InterruptedException {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        String username = (idx % 2 == 0) ? "alice" : "bob";
                        LoginResult res = service.loginBuyer(username, "password1");
                        if (res.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            latch.countDown();
            doneLatch.await();
            executor.shutdown();

            assertEquals(threadCount, successCount.get(), "All concurrent logins should succeed");
        }
    }
}
