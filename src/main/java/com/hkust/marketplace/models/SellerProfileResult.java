package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Outcome of saving the profile form of use case 2.6 "Manage Seller Profile" (US-S06): the
 * changes were saved (with the fields they changed and the number of listings that now show
 * the Seller's name), there was nothing to save, or some fields are invalid and nothing was
 * saved (with the errors of each field). Instances cannot be changed.
 */
public final class SellerProfileResult {

    /** What happened. */
    private final SellerProfileStatus status;
    /** Message for the Seller. */
    private final String message;
    /** The Seller whose profile was saved or checked. */
    private final Seller seller;
    /** Error messages of each invalid field, in form order; empty unless the fields are invalid. */
    private final Map<SellerProfileField, List<String>> errors;
    /** Fields changed by the save; empty unless it succeeded. */
    private final Set<SellerProfileField> changedFields;
    /** Number of the Seller's listings whose seller name was updated by the save. */
    private final int updatedListingCount;

    /**
     * Creates a result; use the factory methods instead.
     *
     * @param status              what happened
     * @param message             message for the Seller
     * @param seller              the Seller
     * @param errors              unmodifiable errors of each invalid field
     * @param changedFields       unmodifiable fields changed by the save
     * @param updatedListingCount number of listings whose seller name was updated
     */
    private SellerProfileResult(SellerProfileStatus status, String message, Seller seller,
                                Map<SellerProfileField, List<String>> errors,
                                Set<SellerProfileField> changedFields, int updatedListingCount) {
        this.status = status;
        this.message = message;
        this.seller = seller;
        this.errors = errors;
        this.changedFields = changedFields;
        this.updatedListingCount = updatedListingCount;
    }

    /**
     * Creates the result of a successful save (basic flow, steps 7 and 8).
     *
     * @param seller              the Seller, with the saved values
     * @param changedFields       the fields the save changed, at least one; the password is
     *                            {@link SellerProfileField#NEW_PASSWORD}
     * @param updatedListingCount number of the Seller's listings whose seller name was updated
     * @param message             confirmation for the Seller
     * @return the result
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code changedFields} is empty or
     *                                  {@code updatedListingCount} is negative
     */
    public static SellerProfileResult success(Seller seller, Set<SellerProfileField> changedFields,
                                              int updatedListingCount, String message) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(changedFields, "changedFields");
        Objects.requireNonNull(message, "message");
        if (changedFields.isEmpty()) {
            throw new IllegalArgumentException("A successful save changes at least one field; use noChanges instead.");
        }
        if (updatedListingCount < 0) {
            throw new IllegalArgumentException("updatedListingCount cannot be negative.");
        }
        return new SellerProfileResult(SellerProfileStatus.SUCCESS, message, seller, Collections.emptyMap(),
                Collections.unmodifiableSet(EnumSet.copyOf(changedFields)), updatedListingCount);
    }

    /**
     * Creates the result of a save that changed nothing, because the form holds the current
     * values of the profile.
     *
     * @param seller  the Seller
     * @param message message for the Seller
     * @return the result
     * @throws NullPointerException if an argument is {@code null}
     */
    public static SellerProfileResult noChanges(Seller seller, String message) {
        return new SellerProfileResult(SellerProfileStatus.NO_CHANGES, Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(seller, "seller"), Collections.emptyMap(), Collections.emptySet(), 0);
    }

    /**
     * Creates the result of a save refused because some fields are invalid (alternative flows
     * A1, A2 and A3). The errors are copied in form order, without the fields that have no
     * message, so later changes to the given map do not affect the result.
     *
     * @param seller the Seller, whose profile was left unchanged
     * @param errors the error messages of each invalid field
     * @return the result, whose message asks to correct the highlighted fields
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if no field has an error message
     */
    public static SellerProfileResult invalidFields(Seller seller, Map<SellerProfileField, List<String>> errors) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(errors, "errors");
        Map<SellerProfileField, List<String>> copy = new EnumMap<>(SellerProfileField.class);
        errors.forEach((field, messages) -> {
            if (field != null && messages != null && !messages.isEmpty()) {
                copy.put(field, List.copyOf(messages));
            }
        });
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("An invalid fields result needs at least one error message.");
        }
        String summary = "Nothing was saved. " + (copy.size() == 1
                ? "Please correct the highlighted field."
                : "Please correct the " + copy.size() + " highlighted fields.");
        return new SellerProfileResult(SellerProfileStatus.INVALID_FIELDS, summary, seller,
                Collections.unmodifiableMap(copy), Collections.emptySet(), 0);
    }

    /**
     * Tells whether the changes were saved.
     *
     * @return {@code true} for {@link SellerProfileStatus#SUCCESS}
     */
    public boolean isSuccess() {
        return status == SellerProfileStatus.SUCCESS;
    }

    /**
     * Returns what happened.
     *
     * @return the status
     */
    public SellerProfileStatus getStatus() {
        return status;
    }

    /**
     * Returns the message for the Seller: the confirmation of a save, the explanation that
     * nothing changed, or the request to correct the highlighted fields.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the Seller whose profile was saved or checked.
     *
     * @return the Seller
     */
    public Seller getSeller() {
        return seller;
    }

    /**
     * Returns the error messages of every invalid field, in form order.
     *
     * @return an unmodifiable map from each invalid field to its messages; empty unless the
     *         status is {@link SellerProfileStatus#INVALID_FIELDS}
     */
    public Map<SellerProfileField, List<String>> getErrors() {
        return errors;
    }

    /**
     * Returns the error messages of one field.
     *
     * @param field the field
     * @return its messages; empty if the field is valid
     */
    public List<String> getErrors(SellerProfileField field) {
        return errors.getOrDefault(field, List.of());
    }

    /**
     * Tells whether a field is invalid.
     *
     * @param field the field
     * @return {@code true} if the field has at least one error message
     */
    public boolean hasError(SellerProfileField field) {
        return errors.containsKey(field);
    }

    /**
     * Returns the first invalid field in form order, which gets the focus.
     *
     * @return the field, or {@code null} if no field is invalid
     */
    public SellerProfileField getFirstInvalidField() {
        return errors.isEmpty() ? null : errors.keySet().iterator().next();
    }

    /**
     * Returns every error message, in form order.
     *
     * @return an unmodifiable list of the messages; empty if no field is invalid
     */
    public List<String> getAllMessages() {
        List<String> all = new ArrayList<>();
        errors.values().forEach(all::addAll);
        return Collections.unmodifiableList(all);
    }

    /**
     * Returns the fields the save changed, in form order; the password is
     * {@link SellerProfileField#NEW_PASSWORD}.
     *
     * @return an unmodifiable set; empty unless the save succeeded
     */
    public Set<SellerProfileField> getChangedFields() {
        return changedFields;
    }

    /**
     * Tells whether the save changed a field.
     *
     * @param field the field
     * @return {@code true} if the save changed it
     */
    public boolean hasChanged(SellerProfileField field) {
        return changedFields.contains(field);
    }

    /**
     * Tells whether the save changed the password.
     *
     * @return {@code true} if a new password was stored
     */
    public boolean isPasswordChanged() {
        return changedFields.contains(SellerProfileField.NEW_PASSWORD);
    }

    /**
     * Returns the number of the Seller's listings whose seller name was updated by the save
     * (acceptance criterion: updates are reflected across all posted listings).
     *
     * @return the number of listings, {@code 0} unless the save succeeded
     */
    public int getUpdatedListingCount() {
        return updatedListingCount;
    }

    /**
     * Describes the result for debugging; it never holds a password.
     *
     * @return the status, the Seller, the invalid and changed fields and the message
     */
    @Override
    public String toString() {
        return "SellerProfileResult[status=" + status
                + ", seller=" + seller.getUsername()
                + ", errors=" + errors.keySet()
                + ", changedFields=" + changedFields
                + ", updatedListingCount=" + updatedListingCount
                + ", message=" + message + "]";
    }
}
