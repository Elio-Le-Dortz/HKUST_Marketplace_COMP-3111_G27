package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Outcome of an action of use case 2.4 "Manage My Listings" (US-S04): checking that a
 * listing can be edited or deleted, editing it (subflow S1), or deleting it (subflow S2).
 *
 * <p>Besides the status and the message for the Seller, a result can hold:</p>
 * <ul>
 *   <li>the listing concerned (never another Seller's listing);</li>
 *   <li>offers: the open offers the action <em>would</em> decline (a successful check, or
 *       {@link MyListingStatus#PENDING_OFFERS}), or the offers it <em>has</em> declined
 *       (a successful edit or delete);</li>
 *   <li>the error messages of every invalid field ({@link MyListingStatus#INVALID_FIELDS}),
 *       in the order of the form.</li>
 * </ul>
 */
public final class MyListingResult {

    /** Why the action succeeded or failed. */
    private final MyListingStatus status;

    /** Message for the Seller. */
    private final String message;

    /** The listing concerned, {@code null} if it was not found or belongs to another Seller. */
    private final Listing listing;

    /** The open offers that would be or were declined, oldest first. */
    private final List<Offer> offers;

    /** The error messages of each invalid field, in form order; empty unless the fields are invalid. */
    private final Map<ListingField, List<String>> errors;

    /**
     * Creates a result.
     *
     * @param status  why the action succeeded or failed
     * @param message message for the Seller
     * @param listing the listing concerned, may be {@code null}
     * @param offers  the open offers, already unmodifiable
     * @param errors  the errors of each invalid field, already unmodifiable
     */
    private MyListingResult(MyListingStatus status, String message, Listing listing,
                            List<Offer> offers, Map<ListingField, List<String>> errors) {
        this.status = status;
        this.message = message;
        this.listing = listing;
        this.offers = offers;
        this.errors = errors;
    }

    /**
     * Creates the result of a check telling that the listing can be edited or deleted
     * (subflows S1 and S2, first step).
     *
     * @param listing    the listing, which belongs to the Seller
     * @param openOffers the open offers that editing or deleting it would decline
     * @param message    what will happen, for the Seller
     * @return a successful result
     * @throws NullPointerException if an argument is {@code null} or {@code openOffers} holds
     *                              a {@code null} element
     */
    public static MyListingResult allowed(Listing listing, List<Offer> openOffers, String message) {
        return new MyListingResult(MyListingStatus.SUCCESS, Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(listing, "listing"), copy(openOffers), Collections.emptyMap());
    }

    /**
     * Creates the result of a listing that was edited or deleted.
     *
     * @param listing        the edited or deleted listing
     * @param declinedOffers the open offers declined by the action, empty if none
     * @param message        the confirmation for the Seller
     * @return a successful result
     * @throws NullPointerException if an argument is {@code null} or {@code declinedOffers}
     *                              holds a {@code null} element
     */
    public static MyListingResult success(Listing listing, List<Offer> declinedOffers, String message) {
        return new MyListingResult(MyListingStatus.SUCCESS, Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(listing, "listing"), copy(declinedOffers), Collections.emptyMap());
    }

    /**
     * Creates the result of an edit or a delete stopped because the listing has open offers
     * that the Seller has not agreed to decline (alternative flow A3). Nothing was changed.
     *
     * @param listing    the listing
     * @param openOffers its open offers, which the Seller must agree to decline; at least one
     * @param message    the warning for the Seller
     * @return a failed result
     * @throws NullPointerException     if an argument is {@code null} or {@code openOffers}
     *                                  holds a {@code null} element
     * @throws IllegalArgumentException if {@code openOffers} is empty
     */
    public static MyListingResult pendingOffers(Listing listing, List<Offer> openOffers, String message) {
        List<Offer> offers = copy(openOffers);
        if (offers.isEmpty()) {
            throw new IllegalArgumentException("A pending offers result needs at least one offer.");
        }
        return new MyListingResult(MyListingStatus.PENDING_OFFERS, Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(listing, "listing"), offers, Collections.emptyMap());
    }

    /**
     * Creates the result of an edit refused because some fields are invalid
     * (alternative flow A4). Nothing was changed.
     *
     * @param listing the listing being edited
     * @param errors  the error messages of each invalid field; fields without message are ignored
     * @return a failed result
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if no field has an error message
     */
    public static MyListingResult invalidFields(Listing listing, Map<ListingField, List<String>> errors) {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(errors, "errors");
        Map<ListingField, List<String>> copy = new EnumMap<>(ListingField.class);
        errors.forEach((field, messages) -> {
            if (field != null && messages != null && !messages.isEmpty()) {
                copy.put(field, List.copyOf(messages));
            }
        });
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("An invalid fields result needs at least one error message.");
        }
        String summary = copy.size() == 1
                ? "Please correct the highlighted field."
                : "Please correct the " + copy.size() + " highlighted fields.";
        return new MyListingResult(MyListingStatus.INVALID_FIELDS, summary, listing, List.of(),
                Collections.unmodifiableMap(copy));
    }

    /**
     * Creates the result of an action that was refused for another reason. Nothing was changed.
     *
     * @param status  why the action was refused
     * @param listing the listing concerned, {@code null} if it was not found or belongs to
     *                another Seller
     * @param message the explanation for the Seller
     * @return a failed result
     * @throws NullPointerException     if {@code status} or {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code status} is {@link MyListingStatus#SUCCESS},
     *                                  {@link MyListingStatus#PENDING_OFFERS} or
     *                                  {@link MyListingStatus#INVALID_FIELDS}, which have their
     *                                  own factory methods
     */
    public static MyListingResult failure(MyListingStatus status, Listing listing, String message) {
        Objects.requireNonNull(status, "status");
        if (status == MyListingStatus.SUCCESS || status == MyListingStatus.PENDING_OFFERS
                || status == MyListingStatus.INVALID_FIELDS) {
            throw new IllegalArgumentException("Use the dedicated factory method for status " + status + ".");
        }
        return new MyListingResult(status, Objects.requireNonNull(message, "message"), listing,
                List.of(), Collections.emptyMap());
    }

    /**
     * Tells whether the action succeeded.
     *
     * @return {@code true} if the listing can be changed (check) or was changed
     */
    public boolean isSuccess() {
        return status == MyListingStatus.SUCCESS;
    }

    /**
     * Returns why the action succeeded or failed.
     *
     * @return the status
     */
    public MyListingStatus getStatus() {
        return status;
    }

    /**
     * Returns the message for the Seller.
     *
     * @return the confirmation, warning or explanation
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the listing concerned.
     *
     * @return the listing, {@code null} if it was not found or belongs to another Seller
     */
    public Listing getListing() {
        return listing;
    }

    /**
     * Returns the open offers that the action would decline (successful check,
     * {@link MyListingStatus#PENDING_OFFERS}) or has declined (successful edit or delete).
     *
     * @return an unmodifiable list, oldest first; empty for the other outcomes
     */
    public List<Offer> getOffers() {
        return offers;
    }

    /**
     * Returns the identifiers of {@link #getOffers()}, which the Seller passes back to
     * confirm that these offers can be declined (alternative flow A3).
     *
     * @return an unmodifiable set of identifiers, in the order of the offers
     */
    public Set<String> getOfferIds() {
        Set<String> ids = new LinkedHashSet<>();
        offers.forEach(offer -> ids.add(offer.getId()));
        return Collections.unmodifiableSet(ids);
    }

    /**
     * Returns the error messages of every invalid field.
     *
     * @return an unmodifiable map from each invalid field to its messages, in form order;
     *         empty unless the status is {@link MyListingStatus#INVALID_FIELDS}
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
     * @return the first invalid field, {@code null} if no field is invalid
     */
    public ListingField getFirstInvalidField() {
        return errors.isEmpty() ? null : errors.keySet().iterator().next();
    }

    /**
     * Returns every error message, in form order.
     *
     * @return the messages, empty if no field is invalid
     */
    public List<String> getAllMessages() {
        List<String> all = new ArrayList<>();
        errors.values().forEach(all::addAll);
        return Collections.unmodifiableList(all);
    }

    /**
     * Describes the result for logs and test reports.
     *
     * @return the status, the listing identifier, the number of offers, the invalid fields and the message
     */
    @Override
    public String toString() {
        return "MyListingResult[status=" + status
                + ", listing=" + (listing != null ? listing.getId() : null)
                + ", offers=" + offers.size()
                + ", errors=" + errors.keySet()
                + ", message=" + message + "]";
    }

    /**
     * Copies a list of offers.
     *
     * @param offers the offers
     * @return an unmodifiable copy
     * @throws NullPointerException if {@code offers} is {@code null} or holds a {@code null} element
     */
    private static List<Offer> copy(List<Offer> offers) {
        return List.copyOf(Objects.requireNonNull(offers, "offers"));
    }
}
