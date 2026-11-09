package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.LoginErrorType;
import com.hkust.marketplace.models.LoginResult;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of use case 2.2 "Log In As Seller" (US-S02), grouped by the flows of its
 * specification. Acceptance criterion of the Sprint 2 backlog: buyer or admin
 * credentials cannot log into the Seller portal (alternative flow A3).
 */
@DisplayName("SellerLoginService: US-S02 Log In As Seller")
class SellerLoginServiceTest {

    /** Password of the seeded accounts charlie, diana, alice and bob. */
    private static final String SEED_PASSWORD = "password1";
    /** Password of the seeded admin account. */
    private static final String SEED_ADMIN_PASSWORD = "admin123";
    /** Password of the accounts created by this test class. */
    private static final String PASSWORD = "SellerPass123";
    private static final String WRONG_PASSWORD = "WrongPass999";
    private static final Set<String> BOTH_FIELDS =
            Set.of(SellerLoginService.FIELD_USERNAME, SellerLoginService.FIELD_PASSWORD);

    private static final DummyDataService DATA = DummyDataService.getInstance();
    private final SellerLoginService service = new SellerLoginService(DATA);

    private static Seller activeSeller;
    private static Seller deactivatedSeller;
    private static Seller deletedSeller;

    /**
     * Starts from the seeded data and creates the accounts shared by the tests. Hashing a
     * password is slow (PBKDF2), so the accounts are created once for the whole class.
     */
    @BeforeAll
    static void createAccounts() {
        DATA.resetData();
        activeSeller = storeSeller("s02_active", AccountStatus.ACTIVE);
        deactivatedSeller = storeSeller("s02_deactivated", AccountStatus.DEACTIVATED);
        deletedSeller = storeSeller("s02_deleted", AccountStatus.DELETED);
    }

    @BeforeEach
    @AfterEach
    void endSession() {
        SessionManager.logout();
    }

    /** The data store is shared by every test class, so each account gets its own username. */
    private static String uniqueUsername(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Seller storeSeller(String prefix, AccountStatus status) {
        Seller seller = new Seller(uniqueUsername(prefix), "Test Seller", PasswordHasher.hash(PASSWORD));
        seller.setAccountStatus(status);
        assertTrue(DATA.registerUserIfAbsent(seller), "Test account could not be stored");
        return seller;
    }

    private static void assertFailure(LoginResult result, LoginErrorType type, String message, Set<String> fields) {
        assertFalse(result.isSuccess());
        assertEquals(type, result.getErrorType());
        assertEquals(message, result.getMessage());
        assertEquals(fields, result.getErrorFields());
        assertNull(result.getUser(), "A failed attempt must not expose the account");
    }

    @Test
    @DisplayName("Null data service is rejected")
    void nullDataService() {
        assertThrows(NullPointerException.class, () -> new SellerLoginService(null));
    }

    @Test
    @DisplayName("Default constructor uses the shared data store")
    void defaultConstructorUsesSharedStore() {
        assertTrue(new SellerLoginService().authenticate("charlie", SEED_PASSWORD).isSuccess());
    }

    @Nested
    @DisplayName("Basic flow: valid seller credentials")
    class BasicFlow {

        @Test
        @DisplayName("Seeded seller logs in and gets the stored Seller account")
        void seededSellerLogsIn() {
            LoginResult result = service.authenticate("charlie", SEED_PASSWORD);

            assertTrue(result.isSuccess(), result::getMessage);
            assertEquals(LoginErrorType.NONE, result.getErrorType());
            assertEquals(LoginResult.MSG_SUCCESS, result.getMessage());
            assertTrue(result.getErrorFields().isEmpty());
            assertSame(DATA.findUser("charlie"), result.getUser());
            assertNotNull(result.getUserAs(Seller.class));
        }

        @Test
        @DisplayName("Seller registered through US-S01 can log in with the same password")
        void registeredSellerLogsIn() {
            String username = uniqueUsername("s02_registered");
            RegistrationResult<Seller> registration = new SellerRegistrationService(DATA).register(
                    new SellerRegistrationRequest(username, "New Seller", PASSWORD, PASSWORD, List.of(), null));
            assertTrue(registration.isSuccess(), () -> "Registration failed: " + registration.getAllMessages());

            LoginResult result = service.authenticate(username, PASSWORD);

            assertTrue(result.isSuccess(), result::getMessage);
            assertSame(registration.getUser(), result.getUser());
        }

        @Test
        @DisplayName("Username ignores surrounding spaces and case")
        void usernameIsTrimmedAndCaseInsensitive() {
            LoginResult result = service.authenticate("  CHARLIE \t", SEED_PASSWORD);

            assertTrue(result.isSuccess(), result::getMessage);
            assertSame(DATA.findUser("charlie"), result.getUser());
        }

        @ParameterizedTest(name = "password \"{0}\" is rejected")
        @ValueSource(strings = {"Password1", "PASSWORD1", " password1", "password1 ", "password"})
        @DisplayName("Password is case-sensitive and never trimmed")
        void passwordMustMatchExactly(String password) {
            assertFailure(service.authenticate("charlie", password), LoginErrorType.INVALID_CREDENTIALS,
                    LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("login() starts a Seller session for the stored account (step 6)")
        void loginStartsSellerSession() {
            LoginResult result = service.login(activeSeller.getUsername(), PASSWORD);

            assertTrue(result.isSuccess(), result::getMessage);
            assertSame(activeSeller, SessionManager.getCurrentUser());
            assertTrue(SessionManager.isSeller());
            assertSame(activeSeller, SessionManager.requireSeller());
        }

        @Test
        @DisplayName("login() replaces a previous session")
        void loginReplacesPreviousSession() {
            SessionManager.login(DATA.findUser("alice"));

            assertTrue(service.login("diana", SEED_PASSWORD).isSuccess());
            assertSame(DATA.findUser("diana"), SessionManager.getCurrentUser());
        }

        @Test
        @DisplayName("authenticate() checks credentials without starting a session")
        void authenticateHasNoSessionSideEffect() {
            assertTrue(service.authenticate("charlie", SEED_PASSWORD).isSuccess());
            assertFalse(SessionManager.isLoggedIn());
        }
    }

    @Nested
    @DisplayName("A1: Empty field")
    class EmptyField {

        static Stream<Arguments> bothEmpty() {
            return Stream.of(
                    Arguments.of(null, null),
                    Arguments.of("", ""),
                    Arguments.of("   ", ""),
                    Arguments.of("\t\n", null));
        }

        @ParameterizedTest(name = "username={0}, password={1}")
        @MethodSource("bothEmpty")
        @DisplayName("Both fields empty: both are highlighted")
        void bothFieldsEmpty(String username, String password) {
            assertFailure(service.authenticate(username, password), LoginErrorType.EMPTY_FIELDS,
                    LoginResult.MSG_EMPTY_CREDENTIALS, BOTH_FIELDS);
        }

        @ParameterizedTest(name = "username=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("Only the username is empty: the specification's single message, username highlighted")
        void onlyUsernameEmpty(String username) {
            assertFailure(service.authenticate(username, SEED_PASSWORD), LoginErrorType.EMPTY_FIELDS,
                    LoginResult.MSG_EMPTY_CREDENTIALS, Set.of(SellerLoginService.FIELD_USERNAME));
        }

        @ParameterizedTest(name = "password=\"{0}\"")
        @NullAndEmptySource
        @DisplayName("Only the password is empty: password highlighted")
        void onlyPasswordEmpty(String password) {
            assertFailure(service.authenticate("charlie", password), LoginErrorType.EMPTY_FIELDS,
                    LoginResult.MSG_EMPTY_CREDENTIALS, Set.of(SellerLoginService.FIELD_PASSWORD));
        }

        @Test
        @DisplayName("A password of spaces is not empty: it is checked and rejected")
        void spacesPasswordIsChecked() {
            assertFailure(service.authenticate("charlie", "   "), LoginErrorType.INVALID_CREDENTIALS,
                    LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("No session is started")
        void noSession() {
            service.login(null, null);
            assertFalse(SessionManager.isLoggedIn());
        }
    }

    @Nested
    @DisplayName("A2: Invalid credentials")
    class InvalidCredentials {

        @Test
        @DisplayName("Unknown username is rejected with the generic message")
        void unknownUsername() {
            assertFailure(service.authenticate(uniqueUsername("nobody"), PASSWORD),
                    LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("Wrong password is rejected with the generic message")
        void wrongPassword() {
            assertFailure(service.authenticate(activeSeller.getUsername(), WRONG_PASSWORD),
                    LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("Unknown username and wrong password get exactly the same answer")
        void doesNotRevealWhichFieldIsWrong() {
            LoginResult unknownUser = service.authenticate(uniqueUsername("nobody"), PASSWORD);
            LoginResult wrongPassword = service.authenticate(activeSeller.getUsername(), WRONG_PASSWORD);

            assertEquals(unknownUser.getErrorType(), wrongPassword.getErrorType());
            assertEquals(unknownUser.getMessage(), wrongPassword.getMessage());
            assertEquals(unknownUser.getErrorFields(), wrongPassword.getErrorFields());
        }

        @ParameterizedTest(name = "{0} with a wrong password")
        @ValueSource(strings = {"alice", "bob", "admin"})
        @DisplayName("Buyer or admin with a wrong password: the role is not revealed")
        void roleNotRevealedWithoutPassword(String username) {
            assertFailure(service.authenticate(username, WRONG_PASSWORD), LoginErrorType.INVALID_CREDENTIALS,
                    LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("Deactivated or deleted seller with a wrong password: the status is not revealed")
        void statusNotRevealedWithoutPassword() {
            for (Seller seller : List.of(deactivatedSeller, deletedSeller)) {
                assertFailure(service.authenticate(seller.getUsername(), WRONG_PASSWORD),
                        LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
            }
        }

        @Test
        @DisplayName("The stored hash itself is not accepted as a password")
        void storedHashIsNotAPassword() {
            assertFailure(service.authenticate(activeSeller.getUsername(), activeSeller.getPasswordHash()),
                    LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("An account without a stored password cannot log in")
        void accountWithoutPassword() {
            Seller noPassword = new Seller(uniqueUsername("s02_nopassword"), "No Password", null);
            assertTrue(DATA.registerUserIfAbsent(noPassword));

            assertFailure(service.authenticate(noPassword.getUsername(), PASSWORD),
                    LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("No session is started")
        void noSession() {
            service.login(activeSeller.getUsername(), WRONG_PASSWORD);
            assertFalse(SessionManager.isLoggedIn());
        }
    }

    @Nested
    @DisplayName("A3: Wrong account type (backlog acceptance criterion)")
    class WrongAccountType {

        @ParameterizedTest(name = "seeded buyer {0}")
        @ValueSource(strings = {"alice", "bob"})
        @DisplayName("Seeded buyer with the correct password is sent to its own portal")
        void seededBuyerRejected(String username) {
            assertFailure(service.authenticate(username, SEED_PASSWORD), LoginErrorType.WRONG_ROLE,
                    SellerLoginService.MSG_WRONG_ROLE_SELLER, Set.of(SellerLoginService.FIELD_USERNAME));
        }

        @Test
        @DisplayName("Buyer registered through the Buyer portal (other hash format) is sent to its own portal")
        void registeredBuyerRejected() {
            String username = uniqueUsername("s02_buyer");
            assertTrue(DATA.registerBuyer(username, "Some Buyer", PASSWORD, PASSWORD, null).isSuccess());

            assertFailure(service.authenticate(username, PASSWORD), LoginErrorType.WRONG_ROLE,
                    SellerLoginService.MSG_WRONG_ROLE_SELLER, Set.of(SellerLoginService.FIELD_USERNAME));
        }

        @Test
        @DisplayName("Seeded admin with the correct password is sent to its own portal")
        void adminRejected() {
            assertFailure(service.authenticate("admin", SEED_ADMIN_PASSWORD), LoginErrorType.WRONG_ROLE,
                    SellerLoginService.MSG_WRONG_ROLE_SELLER, Set.of(SellerLoginService.FIELD_USERNAME));
        }

        @Test
        @DisplayName("The account type is checked before the status: a deactivated buyer is sent to its own portal")
        void typeCheckedBeforeStatus() {
            String username = uniqueUsername("s02_oldbuyer");
            assertTrue(DATA.registerBuyer(username, "Old Buyer", PASSWORD, PASSWORD, null).isSuccess());
            DATA.findUser(username).setAccountStatus(AccountStatus.DEACTIVATED);

            assertEquals(LoginErrorType.WRONG_ROLE, service.authenticate(username, PASSWORD).getErrorType());
        }

        @Test
        @DisplayName("No session is started for a buyer or an admin")
        void noSession() {
            service.login("alice", SEED_PASSWORD);
            service.login("admin", SEED_ADMIN_PASSWORD);
            assertFalse(SessionManager.isLoggedIn());
        }
    }

    @Nested
    @DisplayName("A4: Account deactivated")
    class AccountDeactivated {

        @Test
        @DisplayName("Deactivated seller is told the account is deactivated")
        void deactivatedSeller() {
            assertFailure(service.authenticate(deactivatedSeller.getUsername(), PASSWORD),
                    LoginErrorType.ACCOUNT_DEACTIVATED, LoginResult.MSG_ACCOUNT_DEACTIVATED,
                    Set.of(SellerLoginService.FIELD_USERNAME));
        }

        @Test
        @DisplayName("Soft-deleted seller is told the account is deleted")
        void deletedSeller() {
            assertFailure(service.authenticate(deletedSeller.getUsername(), PASSWORD),
                    LoginErrorType.ACCOUNT_DEACTIVATED, LoginResult.MSG_ACCOUNT_DELETED,
                    Set.of(SellerLoginService.FIELD_USERNAME));
        }

        @Test
        @DisplayName("No session is started")
        void noSession() {
            service.login(deactivatedSeller.getUsername(), PASSWORD);
            service.login(deletedSeller.getUsername(), PASSWORD);
            assertFalse(SessionManager.isLoggedIn());
        }

        @Test
        @DisplayName("A seller deactivated then reactivated by the admin (US-A03) can log in again")
        void reactivatedSellerLogsIn() {
            Seller seller = storeSeller("s02_reactivated", AccountStatus.ACTIVE);
            UserManagementService admin = new UserManagementService(DATA);

            assertTrue(SessionManager.withSession(DATA.findUser(DummyDataService.SEEDED_ADMIN_USERNAME), () -> admin.changeAccountStatus(seller.getUsername(), AccountStatus.DEACTIVATED, "admin").isSuccess()));
            assertEquals(LoginErrorType.ACCOUNT_DEACTIVATED,
                    service.authenticate(seller.getUsername(), PASSWORD).getErrorType());

            assertTrue(SessionManager.withSession(DATA.findUser(DummyDataService.SEEDED_ADMIN_USERNAME), () -> admin.changeAccountStatus(seller.getUsername(), AccountStatus.ACTIVE, "admin").isSuccess()));
            assertTrue(service.authenticate(seller.getUsername(), PASSWORD).isSuccess());
        }
    }

    @Nested
    @DisplayName("Special requirement: Seller-only functions check the session on the service side")
    class SessionCheck {

        @Test
        @DisplayName("A buyer session cannot pass the Seller check")
        void buyerSessionRefused() {
            SessionManager.login(DATA.findUser("alice"));
            assertThrows(SecurityException.class, SessionManager::requireSeller);
        }

        @Test
        @DisplayName("A seller deactivated by the admin while logged in loses access")
        void deactivationEndsAccess() {
            Seller seller = storeSeller("s02_kicked", AccountStatus.ACTIVE);
            assertTrue(service.login(seller.getUsername(), PASSWORD).isSuccess());
            assertSame(seller, SessionManager.requireSeller());

            SessionManager.withSession(DATA.findUser(DummyDataService.SEEDED_ADMIN_USERNAME), () ->
                    new UserManagementService(DATA).changeAccountStatus(seller.getUsername(), AccountStatus.DEACTIVATED, "admin"));

            assertThrows(SecurityException.class, SessionManager::requireSeller);
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        private static final int THREADS = 8;

        /** Runs all the tasks at the same moment and returns their results in order. */
        private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<T>> futures = new ArrayList<>();
                for (Callable<T> task : tasks) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return task.call();
                    }));
                }
                start.countDown();
                List<T> results = new ArrayList<>();
                for (Future<T> future : futures) {
                    results.add(future.get(60, TimeUnit.SECONDS));
                }
                return results;
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @Timeout(120)
        @DisplayName("Simultaneous attempts on one account each get the right answer")
        void simultaneousAttemptsOnSameAccount() throws Exception {
            List<Callable<LoginResult>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                tasks.add(() -> service.authenticate(activeSeller.getUsername(), PASSWORD));
                tasks.add(() -> service.authenticate(activeSeller.getUsername(), WRONG_PASSWORD));
            }

            List<LoginResult> results = runTogether(tasks);

            for (int i = 0; i < results.size(); i++) {
                LoginResult result = results.get(i);
                if (i % 2 == 0) {
                    assertTrue(result.isSuccess(), "Correct password rejected: " + result.getMessage());
                    assertSame(activeSeller, result.getUser());
                } else {
                    assertEquals(LoginErrorType.INVALID_CREDENTIALS, result.getErrorType());
                }
            }
        }

        @Test
        @Timeout(120)
        @DisplayName("Logins stay correct while other sellers register at the same time")
        void loginsDuringRegistrations() throws Exception {
            SellerRegistrationService registration = new SellerRegistrationService(DATA);
            List<String> newUsernames = new ArrayList<>();
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String username = uniqueUsername("s02_concurrent");
                newUsernames.add(username);
                tasks.add(() -> service.authenticate("charlie", SEED_PASSWORD).isSuccess());
                tasks.add(() -> registration.register(new SellerRegistrationRequest(
                        username, "Concurrent Seller", PASSWORD, PASSWORD, List.of(), null)).isSuccess());
            }

            List<Boolean> results = runTogether(tasks);

            assertFalse(results.contains(false), "A login or a registration failed: " + results);
            for (String username : newUsernames) {
                User stored = DATA.findUser(username);
                assertInstanceOf(Seller.class, stored, "Registered seller missing: " + username);
            }
        }
    }
}
