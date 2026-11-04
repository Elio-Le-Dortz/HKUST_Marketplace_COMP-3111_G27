package com.hkust.marketplace.models;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Result of a user authentication attempt (US-B02).
 * Encapsulates status, user-facing error message, affected field names for UI highlighting,
 * error classification, and the authenticated User upon success.
 */
public class LoginResult {

    public static final String MSG_SUCCESS = "Login successful!";
    public static final String MSG_EMPTY_CREDENTIALS = "Both username and password are required.";
    public static final String MSG_USERNAME_REQUIRED = "Username is required.";
    public static final String MSG_PASSWORD_REQUIRED = "Password is required.";
    public static final String MSG_INVALID_CREDENTIALS = "The username or password you entered is incorrect.";
    public static final String MSG_WRONG_ROLE_BUYER = "This account cannot log in to the Buyer Portal and must use its own portal.";
    public static final String MSG_ACCOUNT_DEACTIVATED = "This account is deactivated. Please contact an administrator.";
    public static final String MSG_ACCOUNT_DELETED = "This account has been deleted. Please contact an administrator.";

    private final boolean success;
    private final String message;
    private final Set<String> errorFields;
    private final LoginErrorType errorType;
    private final User user;

    /**
     * Constructs a LoginResult.
     *
     * @param success     True if authentication succeeded, false otherwise
     * @param message     User-facing status or error message
     * @param errorFields Field names that failed validation for UI feedback
     * @param errorType   Category of error or NONE on success
     * @param user        Authenticated user on success, or null on failure
     */
    public LoginResult(boolean success, String message, Set<String> errorFields,
                       LoginErrorType errorType, User user) {
        this.success = success;
        this.message = message != null ? message : "";
        this.errorFields = errorFields != null
                ? Collections.unmodifiableSet(new HashSet<>(errorFields))
                : Collections.emptySet();
        this.errorType = errorType != null ? errorType : (success ? LoginErrorType.NONE : LoginErrorType.INVALID_CREDENTIALS);
        this.user = user;
    }

    /**
     * Creates a successful login result.
     *
     * @param user The authenticated user
     * @return A successful LoginResult
     */
    public static LoginResult success(User user) {
        Objects.requireNonNull(user, "User cannot be null on successful login.");
        return new LoginResult(true, MSG_SUCCESS, Collections.emptySet(), LoginErrorType.NONE, user);
    }

    /**
     * Creates a failed login result with multiple error fields.
     *
     * @param message     Descriptive error message
     * @param errorFields Set of invalid field names
     * @param errorType   Category of login failure
     * @return A failed LoginResult
     */
    public static LoginResult failure(String message, Set<String> errorFields, LoginErrorType errorType) {
        return new LoginResult(false, message, errorFields, errorType, null);
    }

    /**
     * Creates a failed login result with a single error field.
     *
     * @param message    Descriptive error message
     * @param errorField Single invalid field name
     * @param errorType  Category of login failure
     * @return A failed LoginResult
     */
    public static LoginResult failure(String message, String errorField, LoginErrorType errorType) {
        return new LoginResult(false, message, errorField != null ? Set.of(errorField) : Collections.emptySet(), errorType, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Set<String> getErrorFields() {
        return errorFields;
    }

    public boolean hasFieldError(String fieldName) {
        return errorFields.contains(fieldName);
    }

    public LoginErrorType getErrorType() {
        return errorType;
    }

    public User getUser() {
        return user;
    }

    /**
     * Returns the authenticated user cast to the requested type if compatible.
     *
     * @param <T>   Subtype of User
     * @param clazz Target User class
     * @return The typed User instance, or null if type mismatch or login failed
     */
    @SuppressWarnings("unchecked")
    public <T extends User> T getUserAs(Class<T> clazz) {
        if (clazz != null && clazz.isInstance(user)) {
            return (T) user;
        }
        return null;
    }
}
