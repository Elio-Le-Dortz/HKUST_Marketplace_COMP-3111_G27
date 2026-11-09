package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.AdminProfileUpdateRequest;
import com.hkust.marketplace.models.LoginErrorType;
import com.hkust.marketplace.models.LoginResult;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminLoginService: US-A01 Log In As Admin")
class AdminLoginServiceTest {

    private static final String SEED_ADMIN_USERNAME = "admin";
    private static final String SEED_ADMIN_PASSWORD = "admin123";
    private static final String SEED_USER_PASSWORD = "password1";
    private static final Set<String> BOTH_FIELDS =
            Set.of(AdminLoginService.FIELD_USERNAME, AdminLoginService.FIELD_PASSWORD);

    private DummyDataService dataService;
    private AdminLoginService service;

    @BeforeEach
    void setUp() {
        SessionManager.logout();
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        service = new AdminLoginService(dataService);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        dataService.resetData();
    }

    private static void assertFailure(LoginResult result, LoginErrorType type, String message, Set<String> fields) {
        assertFalse(result.isSuccess());
        assertEquals(type, result.getErrorType());
        assertEquals(message, result.getMessage());
        assertEquals(fields, result.getErrorFields());
        assertNull(result.getUser(), "A failed attempt must not expose the user account");
    }

    @Test
    @DisplayName("Constructors: Null data service is rejected, default uses singleton")
    void testConstructors() {
        assertThrows(NullPointerException.class, () -> new AdminLoginService(null));
        assertTrue(new AdminLoginService().authenticate(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD).isSuccess());
    }

    @Nested
    @DisplayName("Basic Flow: Valid Admin Credentials")
    class BasicFlow {

        @Test
        @DisplayName("Seeded admin logs in successfully")
        void seededAdminLogsIn() {
            LoginResult result = service.authenticate(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD);

            assertTrue(result.isSuccess(), result::getMessage);
            assertEquals(LoginErrorType.NONE, result.getErrorType());
            assertEquals(LoginResult.MSG_SUCCESS, result.getMessage());
            assertTrue(result.getErrorFields().isEmpty());
            assertNotNull(result.getUser());
            assertEquals(SEED_ADMIN_USERNAME, result.getUser().getUsername());
            assertEquals(UserRole.ADMIN, result.getUser().getRole());
            assertNotNull(result.getUserAs(Admin.class));
        }

        @Test
        @DisplayName("Username is case-insensitive and trimmed")
        void usernameIsTrimmedAndCaseInsensitive() {
            LoginResult result = service.authenticate("  ADMIN  \t", SEED_ADMIN_PASSWORD);

            assertTrue(result.isSuccess(), result::getMessage);
            assertEquals(SEED_ADMIN_USERNAME, result.getUser().getUsername());
        }

        @ParameterizedTest(name = "password \"{0}\" is rejected")
        @ValueSource(strings = {"Admin123", "ADMIN123", " admin123", "admin123 ", "admin12"})
        @DisplayName("Password is case-sensitive and never trimmed")
        void passwordMustMatchExactly(String password) {
            assertFailure(service.authenticate(SEED_ADMIN_USERNAME, password), LoginErrorType.INVALID_CREDENTIALS,
                    LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("login() starts an Admin session")
        void loginStartsAdminSession() {
            LoginResult result = service.login(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD);

            assertTrue(result.isSuccess(), result::getMessage);
            assertSame(result.getUser(), SessionManager.getCurrentUser());
            assertTrue(SessionManager.isAdmin());
            assertNotNull(SessionManager.requireAdmin());
        }

        @Test
        @DisplayName("login() replaces any existing session")
        void loginReplacesPreviousSession() {
            SessionManager.login(dataService.findUser("alice"));
            assertEquals("alice", SessionManager.getCurrentUser().getUsername());

            assertTrue(service.login(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD).isSuccess());
            assertSame(dataService.findUser(SEED_ADMIN_USERNAME), SessionManager.getCurrentUser());
            assertTrue(SessionManager.isAdmin());
        }

        @Test
        @DisplayName("authenticate() checks credentials without starting a session")
        void authenticateHasNoSessionSideEffect() {
            assertTrue(service.authenticate(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD).isSuccess());
            assertFalse(SessionManager.isLoggedIn());
        }

        @Test
        @DisplayName("Admin can log in after password change in profile")
        void adminCanLoginAfterProfileUpdate() {
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    SEED_ADMIN_USERNAME, "Updated Admin Name",
                    SEED_ADMIN_PASSWORD, "NewAdminPass2026", "NewAdminPass2026");
            dataService.updateAdminProfile(SEED_ADMIN_USERNAME, req);

            assertFailure(service.authenticate(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD),
                    LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);

            LoginResult result = service.login(SEED_ADMIN_USERNAME, "NewAdminPass2026");
            assertTrue(result.isSuccess());
            assertEquals("Updated Admin Name", result.getUser().getFullName());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A1: Empty Fields")
    class EmptyField {

        static Stream<Arguments> bothEmpty() {
            return Stream.of(
                    Arguments.of(null, null),
                    Arguments.of("", ""),
                    Arguments.of("   ", ""),
                    Arguments.of("\t\n", null));
        }

        @ParameterizedTest
        @MethodSource("bothEmpty")
        @DisplayName("Both fields empty highlights both fields")
        void bothFieldsEmpty(String username, String password) {
            assertFailure(service.authenticate(username, password), LoginErrorType.EMPTY_FIELDS,
                    LoginResult.MSG_EMPTY_CREDENTIALS, BOTH_FIELDS);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("Missing username highlights username field only")
        void missingUsername(String username) {
            assertFailure(service.authenticate(username, SEED_ADMIN_PASSWORD), LoginErrorType.EMPTY_FIELDS,
                    LoginResult.MSG_EMPTY_CREDENTIALS, Set.of(AdminLoginService.FIELD_USERNAME));
        }

        @Test
        @DisplayName("Null username highlights username field only")
        void nullUsername() {
            assertFailure(service.authenticate(null, SEED_ADMIN_PASSWORD), LoginErrorType.EMPTY_FIELDS,
                    LoginResult.MSG_EMPTY_CREDENTIALS, Set.of(AdminLoginService.FIELD_USERNAME));
        }

        @Test
        @DisplayName("Empty password highlights password field only")
        void emptyPassword() {
            assertFailure(service.authenticate(SEED_ADMIN_USERNAME, ""), LoginErrorType.EMPTY_FIELDS,
                    LoginResult.MSG_EMPTY_CREDENTIALS, Set.of(AdminLoginService.FIELD_PASSWORD));
        }

        @Test
        @DisplayName("Null password highlights password field only")
        void nullPassword() {
            assertFailure(service.authenticate(SEED_ADMIN_USERNAME, null), LoginErrorType.EMPTY_FIELDS,
                    LoginResult.MSG_EMPTY_CREDENTIALS, Set.of(AdminLoginService.FIELD_PASSWORD));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A2: Invalid Credentials")
    class InvalidCredentials {

        @Test
        @DisplayName("Unknown user receives generic invalid credentials error")
        void unknownUserRejected() {
            assertFailure(service.authenticate("ghost_admin", SEED_ADMIN_PASSWORD),
                    LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("Wrong password on valid admin receives identical error")
        void wrongPasswordRejected() {
            assertFailure(service.authenticate(SEED_ADMIN_USERNAME, "wrong_pass"),
                    LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }

        @Test
        @DisplayName("Wrong password on buyer or seller does not reveal account existence or role")
        void nonAdminWithWrongPasswordDoesNotRevealRole() {
            assertFailure(service.authenticate("alice", "wrong_pass"),
                    LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
            assertFailure(service.authenticate("charlie", "wrong_pass"),
                    LoginErrorType.INVALID_CREDENTIALS, LoginResult.MSG_INVALID_CREDENTIALS, BOTH_FIELDS);
        }
    }

    @Nested
    @DisplayName("Alternative Flow A3: Wrong Account Type")
    class WrongAccountType {

        @Test
        @DisplayName("Buyer with valid credentials cannot log in to Admin Portal")
        void buyerCannotLoginToAdminPortal() {
            assertFailure(service.authenticate("alice", SEED_USER_PASSWORD),
                    LoginErrorType.WRONG_ROLE, AdminLoginService.MSG_WRONG_ROLE_ADMIN,
                    Set.of(AdminLoginService.FIELD_USERNAME));
        }

        @Test
        @DisplayName("Seller with valid credentials cannot log in to Admin Portal")
        void sellerCannotLoginToAdminPortal() {
            assertFailure(service.authenticate("charlie", SEED_USER_PASSWORD),
                    LoginErrorType.WRONG_ROLE, AdminLoginService.MSG_WRONG_ROLE_ADMIN,
                    Set.of(AdminLoginService.FIELD_USERNAME));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A4: Account Deactivated")
    class AccountDeactivated {

        @Test
        @DisplayName("Deactivated admin cannot log in")
        void deactivatedAdminRejected() {
            User admin = dataService.findUser(SEED_ADMIN_USERNAME);
            admin.setActive(false);

            assertFailure(service.authenticate(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD),
                    LoginErrorType.ACCOUNT_DEACTIVATED, LoginResult.MSG_ACCOUNT_DEACTIVATED,
                    Set.of(AdminLoginService.FIELD_USERNAME));
        }

        @Test
        @DisplayName("Deleted admin account cannot log in")
        void deletedAdminRejected() {
            User admin = dataService.findUser(SEED_ADMIN_USERNAME);
            admin.setAccountStatus(AccountStatus.DELETED);

            assertFailure(service.authenticate(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD),
                    LoginErrorType.ACCOUNT_DEACTIVATED, LoginResult.MSG_ACCOUNT_DELETED,
                    Set.of(AdminLoginService.FIELD_USERNAME));
        }
    }

    @Nested
    @DisplayName("Concurrency: Simultaneous Login Attempts")
    class Concurrency {

        @Test
        @DisplayName("Concurrent logins run safely without interference")
        void concurrentLoginsSafe() throws Exception {
            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            List<Callable<LoginResult>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                tasks.add(() -> {
                    latch.countDown();
                    latch.await();
                    if (idx % 2 == 0) {
                        return service.authenticate(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD);
                    } else {
                        return service.authenticate("alice", SEED_USER_PASSWORD);
                    }
                });
            }

            List<Future<LoginResult>> futures = executor.invokeAll(tasks);
            for (int i = 0; i < threads; i++) {
                LoginResult res = futures.get(i).get(5, TimeUnit.SECONDS);
                if (i % 2 == 0) {
                    assertTrue(res.isSuccess());
                } else {
                    assertEquals(LoginErrorType.WRONG_ROLE, res.getErrorType());
                }
            }

            executor.shutdown();
        }
    }
}
