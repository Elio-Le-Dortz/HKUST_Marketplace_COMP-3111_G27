package com.hkust.marketplace.models;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Result of a buyer registration attempt containing status, error messages,
 * affected field names for UI highlighting, and the created User on success.
 */
public class BuyerRegistrationResult {

    private final boolean success;
    private final String message;
    private final Set<String> errorFields;
    private final User user;

    /**
     * Constructs a BuyerRegistrationResult.
     *
     * @param success     True if registration succeeded, false otherwise
     * @param message     Human-readable message or error description
     * @param errorFields Set of field names that triggered validation errors
     * @param user        Created user instance on success, or null on failure
     */
    public BuyerRegistrationResult(boolean success, String message, Set<String> errorFields, User user) {
        this.success = success;
        this.message = message;
        this.errorFields = errorFields != null ? Collections.unmodifiableSet(new HashSet<>(errorFields)) : Collections.emptySet();
        this.user = user;
    }

    /**
     * Creates a successful registration result.
     *
     * @param user The newly registered user
     * @return A successful BuyerRegistrationResult
     */
    public static BuyerRegistrationResult success(User user) {
        return new BuyerRegistrationResult(true, "Registration successful!", Collections.emptySet(), user);
    }

    /**
     * Creates a failed registration result with multiple error fields.
     *
     * @param message     Error message explaining the failure
     * @param errorFields Set of invalid field names to highlight in the UI
     * @return A failed BuyerRegistrationResult
     */
    public static BuyerRegistrationResult failure(String message, Set<String> errorFields) {
        return new BuyerRegistrationResult(false, message, errorFields, null);
    }

    /**
     * Creates a failed registration result with a single error field.
     *
     * @param message    Error message explaining the failure
     * @param errorField Single invalid field name to highlight in the UI
     * @return A failed BuyerRegistrationResult
     */
    public static BuyerRegistrationResult failure(String message, String errorField) {
        return new BuyerRegistrationResult(false, message, Set.of(errorField), null);
    }

    /**
     * Returns whether the registration succeeded.
     *
     * @return true if registration succeeded, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the status or error message.
     *
     * @return Human-readable status/error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the set of field names that caused validation failures.
     *
     * @return Unmodifiable set of error field names
     */
    public Set<String> getErrorFields() {
        return errorFields;
    }

    /**
     * Checks if a specific field triggered an error.
     *
     * @param fieldName Name of the field to check
     * @return true if the field is in the error set, false otherwise
     */
    public boolean hasFieldError(String fieldName) {
        return errorFields.contains(fieldName);
    }

    /**
     * Returns the registered User instance if successful.
     *
     * @return The User instance, or null if registration failed
     */
    public User getUser() {
        return user;
    }

    /**
     * Casts and returns the registered user if it matches the expected class.
     *
     * @param <T>   Subtype of User
     * @param clazz Target class type
     * @return Typed user instance, or null if type mismatch or registration failed
     */
    @SuppressWarnings("unchecked")
    public <T extends User> T getUserAs(Class<T> clazz) {
        if (clazz != null && clazz.isInstance(user)) {
            return (T) user;
        }
        return null;
    }
}
