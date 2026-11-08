package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.BuyerRegistrationResult;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-B01: Buyer Registration & Password Hashing Tests")
class BuyerRegistrationTest {

    private DummyDataService service;

    @BeforeEach
    void setUp() {
        service = DummyDataService.getInstance();
        service.resetData();
    }

    @Test
    @DisplayName("Basic Flow: Successful buyer registration with residence hall")
    void testRegisterBuyerSuccessWithResidence() {
        BuyerRegistrationResult result = service.registerBuyer(
                "john_doe", "John Doe", "ValidPass123", "ValidPass123", "Hall 1");

        assertTrue(result.isSuccess(), "Registration should succeed for valid inputs");
        assertEquals("Registration successful!", result.getMessage());
        assertTrue(result.getErrorFields().isEmpty());

        Buyer buyer = result.getUserAs(Buyer.class);
        assertNotNull(buyer);
        assertEquals("john_doe", buyer.getUsername());
        assertEquals("John Doe", buyer.getFullName());
        assertEquals("Hall 1", buyer.getResidence());
        assertEquals("Hall 1", buyer.getResidenceHall());
        assertEquals(UserRole.BUYER, buyer.getRole());
        assertEquals(AccountStatus.ACTIVE, buyer.getAccountStatus());
        assertTrue(buyer.isActive());

        // Verify stored in service
        assertTrue(service.usernameExists("john_doe"));
        assertNotNull(service.findUser("john_doe"));
    }

    @Test
    @DisplayName("Basic Flow: Successful buyer registration without optional residence")
    void testRegisterBuyerSuccessWithoutResidence() {
        BuyerRegistrationResult result = service.registerBuyer(
                "jane_doe", "Jane Doe", "ValidPass123", "ValidPass123", null);

        assertTrue(result.isSuccess());
        Buyer buyer = result.getUserAs(Buyer.class);
        assertNotNull(buyer);
        assertNull(buyer.getResidence());
        assertNull(buyer.getResidenceHall());

        // Also test empty string residence
        BuyerRegistrationResult result2 = service.registerBuyer(
                "jack_doe", "Jack Doe", "ValidPass123", "ValidPass123", "   ");
        assertTrue(result2.isSuccess());
        Buyer buyer2 = result2.getUserAs(Buyer.class);
        assertNull(buyer2.getResidence());
    }

    @Test
    @DisplayName("Basic Flow: Whitespace trimming and case-insensitive lowercasing")
    void testRegisterBuyerTrimsWhitespaceAndLowercases() {
        BuyerRegistrationResult result = service.registerBuyer(
                "  NewBuyer  ", "  New Buyer Name  ", "ValidPass123", "ValidPass123", "  Hall 2  ");

        assertTrue(result.isSuccess());
        Buyer buyer = result.getUserAs(Buyer.class);
        assertNotNull(buyer);
        assertEquals("newbuyer", buyer.getUsername());
        assertEquals("New Buyer Name", buyer.getFullName());
        assertEquals("Hall 2", buyer.getResidence());
    }

    @Test
    @DisplayName("Special Requirement: Password is stored in cryptographically hashed form")
    void testPasswordIsHashedNotPlaintext() {
        String plainPassword = "SecretPass123";
        BuyerRegistrationResult result = service.registerBuyer(
                "secureuser", "Secure User", plainPassword, plainPassword, "Hall 3");

        assertTrue(result.isSuccess());
        Buyer buyer = result.getUserAs(Buyer.class);
        assertNotNull(buyer);

        // Password must not match plaintext
        assertNotEquals(plainPassword, buyer.getPassword());
        assertNotEquals(plainPassword, buyer.getPasswordHash());

        // Password hash must use PBKDF2 format
        assertTrue(buyer.getPasswordHash().startsWith("pbkdf2_sha256$"));

        // Must authenticate using PasswordUtil
        assertTrue(PasswordUtil.verifyPassword(plainPassword, buyer.getPasswordHash()));
        assertFalse(PasswordUtil.verifyPassword("WrongPassword123", buyer.getPasswordHash()));
    }

    @Test
    @DisplayName("F02 regression: registered buyer password uses PBKDF2 hash")
    void testRegisteredBuyerUsesPbkdf2Hash() {
        BuyerRegistrationResult r = service.registerBuyer("zed", "Zed", "Abcdefg1", "Abcdefg1", null);
        assertTrue(r.isSuccess());
        assertTrue(r.getUser().getPasswordHash().startsWith("pbkdf2_sha256$"));
    }

    @Test
    @DisplayName("Alternative Flow A1: Rejects duplicate username against existing Buyer")
    void testDuplicateUsernameAgainstBuyer() {
        // "alice" is seeded as a Buyer
        BuyerRegistrationResult result = service.registerBuyer(
                "alice", "Alice Copy", "ValidPass123", "ValidPass123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Username already taken. Please choose another.", result.getMessage());
        assertTrue(result.hasFieldError("username"));
    }

    @Test
    @DisplayName("Alternative Flow A1: Rejects duplicate username against existing Seller")
    void testDuplicateUsernameAgainstSeller() {
        // "charlie" is seeded as a Seller
        BuyerRegistrationResult result = service.registerBuyer(
                "charlie", "Charlie As Buyer", "ValidPass123", "ValidPass123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Username already taken. Please choose another.", result.getMessage());
        assertTrue(result.hasFieldError("username"));
    }

    @Test
    @DisplayName("Alternative Flow A1: Rejects duplicate username against Admin")
    void testDuplicateUsernameAgainstAdmin() {
        // "admin" is seeded as Admin
        BuyerRegistrationResult result = service.registerBuyer(
                "admin", "Fake Admin", "ValidPass123", "ValidPass123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Username already taken. Please choose another.", result.getMessage());
        assertTrue(result.hasFieldError("username"));
    }

    @Test
    @DisplayName("Alternative Flow A1: Duplicate username check is strictly case-insensitive")
    void testDuplicateUsernameCaseInsensitive() {
        BuyerRegistrationResult result = service.registerBuyer(
                "ALICE", "Alice Upper", "ValidPass123", "ValidPass123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Username already taken. Please choose another.", result.getMessage());
        assertTrue(result.hasFieldError("username"));
    }

    @Test
    @DisplayName("Alternative Flow A2: Multiple empty required fields highlight all missing fields")
    void testEmptyRequiredFieldsMultiple() {
        BuyerRegistrationResult result = service.registerBuyer(
                "", "", "", "", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Please fill in all required fields.", result.getMessage());
        assertTrue(result.hasFieldError("username"));
        assertTrue(result.hasFieldError("fullName"));
        assertTrue(result.hasFieldError("password"));
        assertTrue(result.hasFieldError("confirmPassword"));
    }

    @Test
    @DisplayName("Alternative Flow A2: Empty username highlights username field")
    void testEmptyUsernameSingle() {
        BuyerRegistrationResult result = service.registerBuyer(
                "   ", "Valid Name", "ValidPass123", "ValidPass123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Username is required.", result.getMessage());
        assertTrue(result.hasFieldError("username"));
        assertFalse(result.hasFieldError("fullName"));
    }

    @Test
    @DisplayName("Alternative Flow A2: Empty full name highlights fullName field")
    void testEmptyFullNameSingle() {
        BuyerRegistrationResult result = service.registerBuyer(
                "validuser", "   ", "ValidPass123", "ValidPass123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Full Name cannot be empty.", result.getMessage());
        assertTrue(result.hasFieldError("fullName"));
        assertFalse(result.hasFieldError("username"));
    }

    @Test
    @DisplayName("Alternative Flow A2: Empty password highlights password field")
    void testEmptyPasswordSingle() {
        BuyerRegistrationResult result = service.registerBuyer(
                "validuser", "Valid Name", "", "ValidPass123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Password cannot be empty.", result.getMessage());
        assertTrue(result.hasFieldError("password"));
    }

    @Test
    @DisplayName("Alternative Flow A2: Empty confirm password highlights confirmPassword field")
    void testEmptyConfirmPasswordSingle() {
        BuyerRegistrationResult result = service.registerBuyer(
                "validuser", "Valid Name", "ValidPass123", "", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Please confirm your password.", result.getMessage());
        assertTrue(result.hasFieldError("confirmPassword"));
    }

    @Test
    @DisplayName("Username format: rejects spaces in username")
    void testUsernameContainsSpaces() {
        BuyerRegistrationResult result = service.registerBuyer(
                "user name with spaces", "Valid Name", "ValidPass123", "ValidPass123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Username cannot contain spaces.", result.getMessage());
        assertTrue(result.hasFieldError("username"));
    }

    @Test
    @DisplayName("Username format: rejects username shorter than 3 characters")
    void testUsernameTooShort() {
        BuyerRegistrationResult result = service.registerBuyer(
                "ab", "Valid Name", "ValidPass123", "ValidPass123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Username must be at least 3 characters long.", result.getMessage());
        assertTrue(result.hasFieldError("username"));
    }

    @Test
    @DisplayName("Alternative Flow A3: Weak password (< 8 chars)")
    void testPasswordTooShort() {
        BuyerRegistrationResult result = service.registerBuyer(
                "validuser", "Valid Name", "Short1A", "Short1A", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Password must contain at least 8 characters.", result.getMessage());
        assertTrue(result.hasFieldError("password"));
    }

    @Test
    @DisplayName("Alternative Flow A3: Weak password (missing uppercase)")
    void testPasswordMissingUppercase() {
        BuyerRegistrationResult result = service.registerBuyer(
                "validuser", "Valid Name", "lowercaseonly123", "lowercaseonly123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Password must contain at least one uppercase letter.", result.getMessage());
        assertTrue(result.hasFieldError("password"));
    }

    @Test
    @DisplayName("Alternative Flow A3: Weak password (missing lowercase)")
    void testPasswordMissingLowercase() {
        BuyerRegistrationResult result = service.registerBuyer(
                "validuser", "Valid Name", "UPPERCASEONLY123", "UPPERCASEONLY123", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Password must contain at least one lowercase letter.", result.getMessage());
        assertTrue(result.hasFieldError("password"));
    }

    @Test
    @DisplayName("Alternative Flow A3: Weak password (missing digit)")
    void testPasswordMissingDigit() {
        BuyerRegistrationResult result = service.registerBuyer(
                "validuser", "Valid Name", "NoDigitsInThisPassword", "NoDigitsInThisPassword", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Password must contain at least one digit.", result.getMessage());
        assertTrue(result.hasFieldError("password"));
    }

    @Test
    @DisplayName("Alternative Flow A3: Weak password (multiple violations: missing uppercase and digit)")
    void testPasswordMultipleViolations() {
        BuyerRegistrationResult result = service.registerBuyer(
                "validuser", "Valid Name", "abcdefgh", "abcdefgh", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Password must contain at least one uppercase letter and at least one digit.", result.getMessage());
        assertTrue(result.hasFieldError("password"));
    }

    @Test
    @DisplayName("Alternative Flow A4: Passwords do not match")
    void testPasswordsDoNotMatch() {
        BuyerRegistrationResult result = service.registerBuyer(
                "validuser", "Valid Name", "ValidPass123", "DifferentPass456", "Hall 1");

        assertFalse(result.isSuccess());
        assertEquals("Passwords do not match.", result.getMessage());
        assertTrue(result.hasFieldError("confirmPassword"));
    }

    @Test
    @DisplayName("Alternative Flow A2: Null required fields handled safely")
    void testNullRequiredFields() {
        BuyerRegistrationResult res1 = service.registerBuyer(null, "Valid Name", "ValidPass123", "ValidPass123", "Hall 1");
        assertFalse(res1.isSuccess());
        assertTrue(res1.hasFieldError("username"));

        BuyerRegistrationResult res2 = service.registerBuyer("validuser", null, "ValidPass123", "ValidPass123", "Hall 1");
        assertFalse(res2.isSuccess());
        assertTrue(res2.hasFieldError("fullName"));

        BuyerRegistrationResult res3 = service.registerBuyer("validuser", "Valid Name", null, "ValidPass123", "Hall 1");
        assertFalse(res3.isSuccess());
        assertTrue(res3.hasFieldError("password"));

        BuyerRegistrationResult res4 = service.registerBuyer("validuser", "Valid Name", "ValidPass123", null, "Hall 1");
        assertFalse(res4.isSuccess());
        assertTrue(res4.hasFieldError("confirmPassword"));

        BuyerRegistrationResult resAllNull = service.registerBuyer(null, null, null, null, null);
        assertFalse(resAllNull.isSuccess());
        assertEquals("Please fill in all required fields.", resAllNull.getMessage());
        assertEquals(4, resAllNull.getErrorFields().size());
    }

    @Test
    @DisplayName("Concurrency: Concurrent registration for identical username allows exactly one success")
    void testConcurrentRegistrationConflict() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    BuyerRegistrationResult result = service.registerBuyer(
                            "concurrent_user", "Buyer " + index, "StrongPass123", "StrongPass123", "Hall 1");
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in registering the username");
        assertEquals(threadCount - 1, failureCount.get(), "All other concurrent attempts must be rejected as duplicates");
    }
}
