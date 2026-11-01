package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests of use case 2.1 "Register As Seller", grouped by the flows of its specification.
 * Alternative flow A5 (cancel) is a screen action that never calls the service, so it
 * has no test here.
 */
@DisplayName("SellerRegistrationService: US-S01 Register As Seller")
class SellerRegistrationServiceTest {

    private static final String VALID_PASSWORD = "StrongPass123";

    private final DummyDataService data = DummyDataService.getInstance();
    private final SellerRegistrationService service = new SellerRegistrationService(data);

    /** The service writes to the shared in-memory store, so every test uses its own username. */
    private static String uniqueUsername() {
        return "s01_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static SellerRegistrationRequest request(String username, String fullName,
                                                     String password, String confirmPassword) {
        return new SellerRegistrationRequest(username, fullName, password, confirmPassword, List.of(), null);
    }

    private static SellerRegistrationRequest validRequest(String username) {
        return new SellerRegistrationRequest(username, "Antoine Lepin", VALID_PASSWORD, VALID_PASSWORD,
                List.of(MeetupLocation.LIBRARY, MeetupLocation.ATRIUM), "Selling my old textbooks.");
    }

    @Test
    @DisplayName("Null request or data service is rejected")
    void nullArguments() {
        assertThrows(NullPointerException.class, () -> service.register(null));
        assertThrows(NullPointerException.class, () -> new SellerRegistrationService(null));
    }

    @Nested
    @DisplayName("Basic flow: valid registration")
    class BasicFlow {

        @Test
        @DisplayName("Creates an active Seller account and stores it")
        void createsAndStoresSeller() {
            String username = uniqueUsername();
            RegistrationResult<Seller> result = service.register(validRequest(username));

            assertTrue(result.isSuccess(), () -> "Unexpected errors: " + result.getAllMessages());
            Seller seller = result.getUser();
            assertEquals(username, seller.getUsername());
            assertEquals("Antoine Lepin", seller.getFullName());
            assertEquals(UserRole.SELLER, seller.getRole());
            assertEquals(AccountStatus.ACTIVE, seller.getAccountStatus());
            assertSame(seller, data.findUser(username), "Account must be stored so the seller can log in");
        }

        @Test
        @DisplayName("Stores the password hashed, never in plaintext")
        void storesHashedPassword() {
            Seller seller = service.register(validRequest(uniqueUsername())).getUser();

            assertNotEquals(VALID_PASSWORD, seller.getPasswordHash());
            assertFalse(seller.getPasswordHash().contains(VALID_PASSWORD));
            assertTrue(PasswordHasher.verify(VALID_PASSWORD, seller.getPasswordHash()));
            assertFalse(PasswordHasher.verify("WrongPass123", seller.getPasswordHash()));
        }

        @Test
        @DisplayName("Stores the username in lower case and trims text fields, but not passwords")
        void normalisesInput() {
            String username = uniqueUsername();
            String paddedPassword = " " + VALID_PASSWORD + " ";
            SellerRegistrationRequest request = new SellerRegistrationRequest("  " + username.toUpperCase() + "  ",
                    "  Antoine Lepin  ", paddedPassword, paddedPassword, null, "  Hello!  ");

            Seller seller = service.register(request).getUser();

            assertEquals(username, seller.getUsername());
            assertEquals("Antoine Lepin", seller.getFullName());
            assertEquals("Hello!", seller.getBio());
            assertTrue(PasswordHasher.verify(paddedPassword, seller.getPasswordHash()));
            assertFalse(PasswordHasher.verify(VALID_PASSWORD, seller.getPasswordHash()));
        }

        @Test
        @DisplayName("Keeps the bio and the preferred meet-up locations")
        void keepsOptionalDetails() {
            Seller seller = service.register(validRequest(uniqueUsername())).getUser();

            assertEquals("Selling my old textbooks.", seller.getBio());
            assertEquals(List.of(MeetupLocation.ATRIUM, MeetupLocation.LIBRARY), seller.getPreferredMeetups());
        }

        @Test
        @DisplayName("Removes duplicate and empty meet-up selections")
        void removesDuplicateMeetups() {
            SellerRegistrationRequest request = new SellerRegistrationRequest(uniqueUsername(), "Antoine Lepin",
                    VALID_PASSWORD, VALID_PASSWORD,
                    Arrays.asList(MeetupLocation.NORTH_GATE, null, MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE),
                    null);

            Seller seller = service.register(request).getUser();

            assertEquals(List.of(MeetupLocation.LIBRARY, MeetupLocation.NORTH_GATE), seller.getPreferredMeetups());
        }

        @ParameterizedTest(name = "bio \"{0}\" is stored as no bio")
        @ValueSource(strings = {"", "   ", "\n\t"})
        @DisplayName("Blank bio is stored as no bio")
        void blankBioIsNoBio(String bio) {
            SellerRegistrationRequest request = new SellerRegistrationRequest(uniqueUsername(), "Antoine Lepin",
                    VALID_PASSWORD, VALID_PASSWORD, List.of(), bio);

            assertNull(service.register(request).getUser().getBio());
        }

        @Test
        @DisplayName("Optional bio and meet-ups can be left out")
        void optionalFieldsOmitted() {
            SellerRegistrationRequest request = new SellerRegistrationRequest(uniqueUsername(), "Antoine Lepin",
                    VALID_PASSWORD, VALID_PASSWORD, null, null);

            RegistrationResult<Seller> result = service.register(request);

            assertTrue(result.isSuccess());
            assertNull(result.getUser().getBio());
            assertTrue(result.getUser().getPreferredMeetups().isEmpty());
        }

        @Test
        @DisplayName("Stored meet-up list stays editable for the profile screen")
        void meetupsRemainEditable() {
            Seller seller = service.register(validRequest(uniqueUsername())).getUser();

            seller.getPreferredMeetups().add(MeetupLocation.SOUTH_GATE);
            assertTrue(seller.getPreferredMeetups().contains(MeetupLocation.SOUTH_GATE));
        }

        @Test
        @DisplayName("Default constructor stores accounts in the shared DummyDataService")
        void defaultConstructorUsesSharedStore() {
            String username = uniqueUsername();
            Seller seller = new SellerRegistrationService().register(validRequest(username)).getUser();

            assertSame(seller, DummyDataService.getInstance().findUser(username));
        }
    }

    @Nested
    @DisplayName("A1: Duplicate username")
    class DuplicateUsername {

        @ParameterizedTest(name = "username of seeded account \"{0}\" is rejected")
        @ValueSource(strings = {"alice", "charlie", "admin"})
        @DisplayName("Username of an existing buyer, seller or admin is rejected")
        void usernameTakenByAnyRole(String existing) {
            User before = data.findUser(existing);
            RegistrationResult<Seller> result = service.register(validRequest(existing));

            assertFalse(result.isSuccess());
            assertEquals(List.of(SellerRegistrationService.USERNAME_TAKEN),
                    result.getErrorsFor(RegistrationField.USERNAME));
            assertSame(before, data.findUser(existing), "Existing account must not be replaced");
        }

        @Test
        @DisplayName("Uniqueness ignores case and surrounding spaces")
        void uniquenessIgnoresCase() {
            RegistrationResult<Seller> result = service.register(validRequest("  ALICE "));

            assertTrue(result.hasError(RegistrationField.USERNAME));
            assertEquals(UserRole.BUYER, data.findUser("alice").getRole());
        }

        @Test
        @DisplayName("Second registration with the same username is rejected; the first account is kept")
        void secondRegistrationRejected() {
            String username = uniqueUsername();
            Seller first = service.register(validRequest(username)).getUser();

            RegistrationResult<Seller> second = service.register(validRequest(username));

            assertFalse(second.isSuccess());
            assertTrue(second.hasError(RegistrationField.USERNAME));
            assertSame(first, data.findUser(username));
        }

        @Test
        @DisplayName("Username of a soft-deleted account stays reserved")
        void deletedAccountKeepsUsername() {
            String username = uniqueUsername();
            service.register(validRequest(username)).getUser().setAccountStatus(AccountStatus.DELETED);

            assertTrue(service.register(validRequest(username)).hasError(RegistrationField.USERNAME));
        }

        @Test
        @DisplayName("Username taken by a concurrent registration after the check is reported as taken")
        void usernameLostToConcurrentRegistration() {
            DummyDataService racingStore = mock(DummyDataService.class);
            when(racingStore.usernameExists(anyString())).thenReturn(false);
            when(racingStore.registerUserIfAbsent(any())).thenReturn(false);

            RegistrationResult<Seller> result =
                    new SellerRegistrationService(racingStore).register(validRequest(uniqueUsername()));

            assertFalse(result.isSuccess());
            assertEquals(List.of(SellerRegistrationService.USERNAME_TAKEN),
                    result.getErrorsFor(RegistrationField.USERNAME));
        }
    }

    @Nested
    @DisplayName("A2: Empty required field")
    class EmptyRequiredField {

        @Test
        @DisplayName("Every empty required field is reported together")
        void allEmptyFieldsReported() {
            RegistrationResult<Seller> result = service.register(request("", "", "", ""));

            assertFalse(result.isSuccess());
            assertEquals(List.of(SellerRegistrationService.USERNAME_REQUIRED),
                    result.getErrorsFor(RegistrationField.USERNAME));
            assertEquals(List.of(SellerRegistrationService.FULL_NAME_REQUIRED),
                    result.getErrorsFor(RegistrationField.FULL_NAME));
            assertEquals(List.of(SellerRegistrationService.PASSWORD_REQUIRED),
                    result.getErrorsFor(RegistrationField.PASSWORD));
            assertFalse(result.hasError(RegistrationField.CONFIRM_PASSWORD),
                    "No mismatch is reported when no password was entered");
        }

        @Test
        @DisplayName("Null fields are treated as empty")
        void nullFieldsAreEmpty() {
            RegistrationResult<Seller> result = service.register(request(null, null, null, null));

            assertTrue(result.hasError(RegistrationField.USERNAME));
            assertTrue(result.hasError(RegistrationField.FULL_NAME));
            assertTrue(result.hasError(RegistrationField.PASSWORD));
        }

        @Test
        @DisplayName("Whitespace-only username or full name counts as empty")
        void whitespaceIsEmpty() {
            RegistrationResult<Seller> result = service.register(request("   ", "\t", VALID_PASSWORD, VALID_PASSWORD));

            assertEquals(List.of(SellerRegistrationService.USERNAME_REQUIRED),
                    result.getErrorsFor(RegistrationField.USERNAME));
            assertEquals(List.of(SellerRegistrationService.FULL_NAME_REQUIRED),
                    result.getErrorsFor(RegistrationField.FULL_NAME));
            assertFalse(result.hasError(RegistrationField.PASSWORD));
        }

        @Test
        @DisplayName("Nothing is stored when only the full name is missing")
        void nothingStoredOnFailure() {
            String username = uniqueUsername();
            RegistrationResult<Seller> result = service.register(request(username, "", VALID_PASSWORD, VALID_PASSWORD));

            assertEquals(List.of(RegistrationField.FULL_NAME), List.copyOf(result.getErrors().keySet()));
            assertFalse(data.usernameExists(username));
        }
    }

    @Nested
    @DisplayName("A3: Weak password")
    class WeakPassword {

        @Test
        @DisplayName("Lists every password rule that is not met")
        void listsEveryBrokenRule() {
            RegistrationResult<Seller> result = service.register(request(uniqueUsername(), "Antoine", "abc", "abc"));

            assertEquals(List.of("Password must contain at least 8 characters, at least one uppercase letter"
                            + " and at least one digit."),
                    result.getErrorsFor(RegistrationField.PASSWORD));
        }

        @Test
        @DisplayName("Single broken rule is named")
        void singleBrokenRule() {
            RegistrationResult<Seller> result =
                    service.register(request(uniqueUsername(), "Antoine", "strongpass123", "strongpass123"));

            assertEquals(List.of("Password must contain at least one uppercase letter."),
                    result.getErrorsFor(RegistrationField.PASSWORD));
        }

        @Test
        @DisplayName("Two broken rules are joined with \"and\"")
        void twoBrokenRules() {
            RegistrationResult<Seller> result =
                    service.register(request(uniqueUsername(), "Antoine", "STRONGPASSWORD", "STRONGPASSWORD"));

            assertEquals(List.of(SellerRegistrationService.WEAK_PASSWORD_PREFIX + PasswordPolicy.RULE_LOWERCASE
                            + " and " + PasswordPolicy.RULE_DIGIT + "."),
                    result.getErrorsFor(RegistrationField.PASSWORD));
        }

        @ParameterizedTest(name = "\"{0}\" is rejected")
        @ValueSource(strings = {"Abcdef1", "alllowercase1", "ALLUPPERCASE1", "NoDigitsHere", "12345678", "        "})
        @DisplayName("Weak passwords are rejected and nothing is stored")
        void weakPasswordsRejected(String weak) {
            String username = uniqueUsername();
            RegistrationResult<Seller> result = service.register(request(username, "Antoine", weak, weak));

            assertEquals(List.of(RegistrationField.PASSWORD), List.copyOf(result.getErrors().keySet()));
            assertFalse(data.usernameExists(username));
        }
    }

    @Nested
    @DisplayName("A4: Passwords do not match")
    class PasswordsDoNotMatch {

        @ParameterizedTest(name = "confirm \"{0}\" does not match")
        @ValueSource(strings = {"", "StrongPass124", "strongpass123", "StrongPass123 "})
        @DisplayName("Confirm password must be exactly the same as the password")
        void mismatchRejected(String confirm) {
            String username = uniqueUsername();
            RegistrationResult<Seller> result = service.register(request(username, "Antoine", VALID_PASSWORD, confirm));

            assertEquals(List.of(RegistrationField.CONFIRM_PASSWORD), List.copyOf(result.getErrors().keySet()));
            assertEquals(List.of(SellerRegistrationService.PASSWORDS_DO_NOT_MATCH),
                    result.getErrorsFor(RegistrationField.CONFIRM_PASSWORD));
            assertFalse(data.usernameExists(username));
        }

        @Test
        @DisplayName("Weak and mismatched password reports both problems")
        void weakAndMismatched() {
            RegistrationResult<Seller> result = service.register(request(uniqueUsername(), "Antoine", "weak", "other"));

            assertTrue(result.hasError(RegistrationField.PASSWORD));
            assertTrue(result.hasError(RegistrationField.CONFIRM_PASSWORD));
        }
    }

    @Nested
    @DisplayName("Concurrency: simultaneous registrations")
    class Concurrency {

        private static final int ATTEMPTS = 10;

        @Test
        @Timeout(60)
        @DisplayName("10 simultaneous registrations of one username: exactly one succeeds")
        void onlyOneConcurrentRegistrationSucceeds() throws Exception {
            String username = uniqueUsername();
            ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
            CountDownLatch ready = new CountDownLatch(ATTEMPTS);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<RegistrationResult<Seller>>> futures = new ArrayList<>();
                for (int i = 0; i < ATTEMPTS; i++) {
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        return service.register(validRequest(username));
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS), "All threads should be ready");
                start.countDown();

                int successes = 0;
                for (Future<RegistrationResult<Seller>> future : futures) {
                    RegistrationResult<Seller> result = future.get(30, TimeUnit.SECONDS);
                    if (result.isSuccess()) {
                        successes++;
                        assertSame(result.getUser(), data.findUser(username), "The winner must be the stored account");
                    } else {
                        assertEquals(List.of(SellerRegistrationService.USERNAME_TAKEN),
                                result.getErrorsFor(RegistrationField.USERNAME));
                    }
                }
                assertEquals(1, successes, "Exactly one registration may win the username");
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
