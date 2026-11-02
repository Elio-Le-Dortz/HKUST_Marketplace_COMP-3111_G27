package com.hkust.marketplace.models;

/**
 * Result object encapsulating the outcome of an action taken on a listing
 * (Use Case 1.5: Act on Listing / US-B05).
 */
public class ActOnListingResult {
    private final boolean success;
    private final ActOnListingStatus status;
    private final Offer offer;
    private final String message;

    public ActOnListingResult(boolean success, ActOnListingStatus status, Offer offer, String message) {
        this.success = success;
        this.status = status;
        this.offer = offer;
        this.message = message;
    }

    public static ActOnListingResult success(Offer offer, String message) {
        return new ActOnListingResult(true, ActOnListingStatus.SUCCESS, offer, message);
    }

    public static ActOnListingResult failure(ActOnListingStatus status, String message) {
        return new ActOnListingResult(false, status, null, message);
    }

    public static ActOnListingResult invalidPrice(String message) {
        return failure(ActOnListingStatus.INVALID_PRICE, message);
    }

    public static ActOnListingResult invalidBorrowDates(String message) {
        return failure(ActOnListingStatus.INVALID_BORROW_DATES, message);
    }

    public static ActOnListingResult listingNotLive(String message) {
        return failure(ActOnListingStatus.LISTING_NOT_LIVE, message);
    }

    public static ActOnListingResult listingNotFound(String message) {
        return failure(ActOnListingStatus.LISTING_NOT_FOUND, message);
    }

    public static ActOnListingResult alreadyClaimed(String message) {
        return failure(ActOnListingStatus.ALREADY_CLAIMED, message);
    }

    public static ActOnListingResult invalidListingType(String message) {
        return failure(ActOnListingStatus.INVALID_LISTING_TYPE, message);
    }

    public static ActOnListingResult selfActionNotAllowed(String message) {
        return failure(ActOnListingStatus.SELF_ACTION_NOT_ALLOWED, message);
    }

    public static ActOnListingResult invalidUser(String message) {
        return failure(ActOnListingStatus.INVALID_USER, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public ActOnListingStatus getStatus() {
        return status;
    }

    public Offer getOffer() {
        return offer;
    }

    public String getMessage() {
        return message;
    }
}
