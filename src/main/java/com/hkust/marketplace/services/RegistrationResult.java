package com.hkust.marketplace.services;

import com.hkust.marketplace.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of a registration attempt: either the account that was created, or the
 * validation errors grouped by form field, so the screen can highlight every invalid
 * field and show its messages (alternative flows A1 to A4 of the registration use
 * cases). Instances are immutable.
 *
 * @param <U> the type of account being registered
 */
public final class RegistrationResult<U extends User> {

    private final U user;
    private final Map<RegistrationField, List<String>> errors;

    private RegistrationResult(U user, Map<RegistrationField, List<String>> errors) {
        this.user = user;
        this.errors = errors;
    }

    /**
     * Creates the result of a successful registration.
     *
     * @param user the account that was created and stored
     * @param <U>  the type of account
     * @return a successful result holding {@code user}
     * @throws NullPointerException if {@code user} is {@code null}
     */
    public static <U extends User> RegistrationResult<U> success(U user) {
        Objects.requireNonNull(user, "user");
        return new RegistrationResult<>(user, Collections.emptyMap());
    }

    /**
     * Creates the result of a rejected registration. The errors are copied, so later
     * changes to the given map or lists do not affect the result.
     *
     * @param errors the error messages of each invalid field; must not be empty
     * @param <U>    the type of account
     * @return a failed result holding a copy of {@code errors}
     * @throws NullPointerException     if {@code errors}, one of its lists or one of
     *                                  its messages is {@code null}
     * @throws IllegalArgumentException if {@code errors} is empty
     */
    public static <U extends User> RegistrationResult<U> failure(Map<RegistrationField, List<String>> errors) {
        Objects.requireNonNull(errors, "errors");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("A failed registration needs at least one error");
        }
        Map<RegistrationField, List<String>> copy = new EnumMap<>(RegistrationField.class);
        errors.forEach((field, messages) -> copy.put(field, List.copyOf(messages)));
        return new RegistrationResult<>(null, Collections.unmodifiableMap(copy));
    }

    /**
     * Tells whether the account was created.
     *
     * @return {@code true} if the registration succeeded, {@code false} if it was rejected
     */
    public boolean isSuccess() {
        return user != null;
    }

    /**
     * Returns the account that was created.
     *
     * @return the stored account
     * @throws IllegalStateException if the registration was rejected
     */
    public U getUser() {
        if (user == null) {
            throw new IllegalStateException("Registration failed: " + getAllMessages());
        }
        return user;
    }

    /**
     * Returns the error messages of every invalid field, in form order
     * (username, full name, password, confirm password).
     *
     * @return an unmodifiable map from each invalid field to its messages;
     *         empty if the registration succeeded
     */
    public Map<RegistrationField, List<String>> getErrors() {
        return errors;
    }

    /**
     * Returns the error messages of one field.
     *
     * @param field the form field
     * @return an unmodifiable list of the field's messages; empty if the field is valid
     */
    public List<String> getErrorsFor(RegistrationField field) {
        return errors.getOrDefault(field, List.of());
    }

    /**
     * Tells whether a field failed validation.
     *
     * @param field the form field
     * @return {@code true} if the field has at least one error, {@code false} otherwise
     */
    public boolean hasError(RegistrationField field) {
        return errors.containsKey(field);
    }

    /**
     * Returns every error message, in form order.
     *
     * @return an unmodifiable list of all messages; empty if the registration succeeded
     */
    public List<String> getAllMessages() {
        List<String> all = new ArrayList<>();
        errors.values().forEach(all::addAll);
        return Collections.unmodifiableList(all);
    }
}
