package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of a listing submission in use case 2.3 "Create Listing" (US-S03).
 *
 * <p>On success it holds the stored listing, with status Pending Review, and the
 * confirmation shown to the Seller (basic flow, step 10). On failure it holds the error
 * messages of every invalid field, in the order of the form, so that the form can
 * highlight them all at once (alternative flows A1, A2 and A3).</p>
 */
public final class CreateListingResult {

    /** The stored listing, {@code null} on failure. */
    private final Listing listing;

    /** The confirmation for the Seller on success, a summary of the errors on failure. */
    private final String message;

    /** The error messages of each invalid field, in form order; empty on success. */
    private final Map<ListingField, List<String>> errors;

    /**
     * Creates a result.
     *
     * @param listing the stored listing, {@code null} on failure
     * @param message the message for the Seller
     * @param errors  the error messages of each invalid field, already unmodifiable
     */
    private CreateListingResult(Listing listing, String message, Map<ListingField, List<String>> errors) {
        this.listing = listing;
        this.message = message;
        this.errors = errors;
    }

    /**
     * Creates the result of a listing that was stored and sent to the admin for review.
     *
     * @param listing the stored listing
     * @param message the confirmation for the Seller
     * @return a successful result
     * @throws NullPointerException if {@code listing} or {@code message} is {@code null}
     */
    public static CreateListingResult success(Listing listing, String message) {
        return new CreateListingResult(Objects.requireNonNull(listing, "listing"),
                Objects.requireNonNull(message, "message"), Collections.emptyMap());
    }

    /**
     * Creates the result of a submission that was refused because some fields are invalid.
     * Nothing was stored.
     *
     * @param errors the error messages of each invalid field; fields without message are ignored
     * @return a failed result
     * @throws NullPointerException     if {@code errors} is {@code null}
     * @throws IllegalArgumentException if no field has an error message
     */
    public static CreateListingResult failure(Map<ListingField, List<String>> errors) {
        Objects.requireNonNull(errors, "errors");
        Map<ListingField, List<String>> copy = new EnumMap<>(ListingField.class);
        errors.forEach((field, messages) -> {
            if (field != null && messages != null && !messages.isEmpty()) {
                copy.put(field, List.copyOf(messages));
            }
        });
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A failed result needs at least one error message.");
        }
        String summary = copy.size() == 1
                ? "Please correct the highlighted field."
                : "Please correct the " + copy.size() + " highlighted fields.";
        return new CreateListingResult(null, summary, Collections.unmodifiableMap(copy));
    }

    /**
     * Tells whether the listing was stored.
     *
     * @return {@code true} if the listing was stored and sent for review
     */
    public boolean isSuccess() {
        return listing != null;
    }

    /**
     * Returns the stored listing.
     *
     * @return the listing, with status Pending Review; {@code null} on failure
     */
    public Listing getListing() {
        return listing;
    }

    /**
     * Returns the message for the Seller.
     *
     * @return the confirmation on success, or a summary of the errors on failure
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the error messages of every invalid field.
     *
     * @return an unmodifiable map from each invalid field to its messages, in form order;
     *         empty on success
     */
    public Map<ListingField, List<String>> getErrors() {
        return errors;
    }

    /**
     * Returns the error messages of one field.
     *
     * @param field the field
     * @return its messages, empty if the field is valid
     */
    public List<String> getErrors(ListingField field) {
        return errors.getOrDefault(field, List.of());
    }

    /**
     * Tells whether a field is invalid.
     *
     * @param field the field
     * @return {@code true} if the field has at least one error message
     */
    public boolean hasError(ListingField field) {
        return errors.containsKey(field);
    }

    /**
     * Returns the first invalid field in form order, which the form gives the focus to.
     *
     * @return the first invalid field, {@code null} on success
     */
    public ListingField getFirstInvalidField() {
        return errors.isEmpty() ? null : errors.keySet().iterator().next();
    }

    /**
     * Returns every error message, in form order.
     *
     * @return the messages, empty on success
     */
    public List<String> getAllMessages() {
        List<String> all = new ArrayList<>();
        errors.values().forEach(all::addAll);
        return Collections.unmodifiableList(all);
    }
}
