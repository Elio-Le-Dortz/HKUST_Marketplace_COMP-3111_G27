package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.LoginErrorType;
import com.hkust.marketplace.models.LoginResult;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.PasswordUtil;
import com.hkust.marketplace.utils.SessionManager;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic of use case 2.2 "Log In As Seller" (US-S02).
 *
 * <p>The checks follow the order of the specification, so that each answer reveals no
 * more than the previous steps allow:</p>
 * <ol>
 *   <li>A1 Empty Field: the username or the password is empty.</li>
 *   <li>A2 Invalid Credentials: the username does not exist or the password is wrong. Both
 *       cases get the same answer, so the user cannot tell which one was wrong.</li>
 *   <li>A3 Wrong Account Type: the credentials are correct but belong to a buyer or admin
 *       account. Only checked once the password is correct, so the role of an account is
 *       never revealed to someone who does not know its password.</li>
 *   <li>A4 Account Deactivated: the seller account was deactivated or deleted by the admin.</li>
 * </ol>
 *
 * <p>This class does not use JavaFX, so every flow is unit tested without a display.</p>
 */
public class SellerLoginService {

    /** Name of the username field, used to tell the screen which field to highlight. */
    public static final String FIELD_USERNAME = "username";

    /** Name of the password field, used to tell the screen which field to highlight. */
    public static final String FIELD_PASSWORD = "password";

    /** Message of alternative flow A3, shown when a buyer or admin account tries to log in. */
    public static final String MSG_WRONG_ROLE_SELLER =
            "This account cannot log in to the Seller Portal and must use its own portal.";

    /** The store of user accounts. */
    private final DummyDataService dataService;

    /**
     * Creates the service on the application's shared data store.
     */
    public SellerLoginService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Creates the service on the given data store (used by the tests).
     *
     * @param dataService the store of user accounts
     * @throws NullPointerException if {@code dataService} is {@code null}
     */
    public SellerLoginService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
    }

    /**
     * Logs a seller in (basic flow): checks the credentials with
     * {@link #authenticate(String, String)} and, if they are accepted, starts the Seller
     * session (step 6). A failed attempt leaves the current session unchanged.
     *
     * @param username the entered username; surrounding spaces and case are ignored
     * @param password the entered password, used exactly as typed
     * @return the result of the attempt, holding the Seller on success
     */
    public LoginResult login(String username, String password) {
        LoginResult result = authenticate(username, password);
        if (result.isSuccess()) {
            SessionManager.login(result.getUser());
        }
        return result;
    }

    /**
     * Checks the credentials of a login attempt without starting a session (steps 4 and 5).
     *
     * <p>Thread safe: the account is read under the data store's lock, and the slow password
     * check runs outside of it, so concurrent logins neither block the store nor see a
     * half-registered account.</p>
     *
     * @param username the entered username; surrounding spaces and case are ignored
     * @param password the entered password, used exactly as typed (never trimmed)
     * @return a successful result holding the active Seller, or a failure telling which
     *         alternative flow applies and which fields to highlight
     */
    public LoginResult authenticate(String username, String password) {
        String cleanUsername = username == null ? "" : username.trim();
        String rawPassword = password == null ? "" : password;

        // A1 Empty Field: the specification asks for a single "both fields are required"
        // message; the empty fields are still listed so that only they are highlighted.
        Set<String> emptyFields = new LinkedHashSet<>();
        if (cleanUsername.isEmpty()) {
            emptyFields.add(FIELD_USERNAME);
        }
        if (rawPassword.isEmpty()) {
            emptyFields.add(FIELD_PASSWORD);
        }
        if (!emptyFields.isEmpty()) {
            return LoginResult.failure(LoginResult.MSG_EMPTY_CREDENTIALS, emptyFields, LoginErrorType.EMPTY_FIELDS);
        }

        // A2 Invalid Credentials. PasswordUtil understands every hash format stored in the
        // system (sellers use PBKDF2), so that a buyer or admin with the right password
        // reaches A3 instead. An unknown username is checked against a dummy hash so that it
        // takes as long to answer as a wrong password.
        User user = findAccount(cleanUsername);
        String storedHash = user != null ? user.getPasswordHash() : DummyHash.VALUE;
        boolean passwordMatches = PasswordUtil.verifyPassword(rawPassword, storedHash);
        if (user == null || !passwordMatches) {
            return LoginResult.failure(LoginResult.MSG_INVALID_CREDENTIALS,
                    Set.of(FIELD_USERNAME, FIELD_PASSWORD), LoginErrorType.INVALID_CREDENTIALS);
        }

        // A3 Wrong Account Type: the account exists and the password is correct.
        if (user.getRole() != UserRole.SELLER) {
            return LoginResult.failure(MSG_WRONG_ROLE_SELLER, FIELD_USERNAME, LoginErrorType.WRONG_ROLE);
        }

        // A4 Account Deactivated (or soft-deleted) by the admin.
        if (user.getAccountStatus() == AccountStatus.DELETED) {
            return LoginResult.failure(LoginResult.MSG_ACCOUNT_DELETED, FIELD_USERNAME,
                    LoginErrorType.ACCOUNT_DEACTIVATED);
        }
        if (!user.isActive()) {
            return LoginResult.failure(LoginResult.MSG_ACCOUNT_DEACTIVATED, FIELD_USERNAME,
                    LoginErrorType.ACCOUNT_DEACTIVATED);
        }

        return LoginResult.success(user);
    }

    /**
     * Looks up an account by username. The data store guards its accounts with its own
     * lock (its methods are {@code synchronized}); taking that lock gives a consistent read
     * while another thread registers an account. No other lock is held, so this cannot
     * deadlock.
     *
     * @param username the trimmed, non-empty username
     * @return the account, or {@code null} if no account uses this username
     */
    private User findAccount(String username) {
        synchronized (dataService) {
            return dataService.findUser(username);
        }
    }

    /**
     * Holds the hash checked for unknown usernames. It is computed on first use only,
     * so opening the login screen does not pay for a password hash.
     */
    private static final class DummyHash {

        /** A PBKDF2 hash of a random password, as costly to check as a real seller hash. */
        static final String VALUE = PasswordHasher.hash(UUID.randomUUID().toString());

        /** Not instantiable. */
        private DummyHash() {
        }
    }
}
