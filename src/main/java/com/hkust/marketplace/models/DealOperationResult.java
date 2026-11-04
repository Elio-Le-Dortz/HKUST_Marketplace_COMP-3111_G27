package com.hkust.marketplace.models;

/**
 * Result model encapsulating the outcome of operations on deals and offers
 * (withdraw, accept counter, decline counter, self-return, mark complete, submit rating).
 * Decoupled from UI controllers to guarantee headless testing.
 */
public class DealOperationResult {

    private final boolean success;
    private final DealOperationStatus status;
    private final String message;
    private final Deal deal;
    private final Offer offer;
    private final boolean promptRating;

    public DealOperationResult(boolean success, DealOperationStatus status, String message,
                               Deal deal, Offer offer, boolean promptRating) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.deal = deal;
        this.offer = offer;
        this.promptRating = promptRating;
    }

    public static DealOperationResult success(Deal deal, String message) {
        return new DealOperationResult(true, DealOperationStatus.SUCCESS, message, deal, null, false);
    }

    public static DealOperationResult success(Offer offer, String message) {
        return new DealOperationResult(true, DealOperationStatus.SUCCESS, message, null, offer, false);
    }

    public static DealOperationResult completeWithRatingPrompt(Deal deal, String message) {
        return new DealOperationResult(true, DealOperationStatus.SUCCESS, message, deal, null, true);
    }

    public static DealOperationResult failure(DealOperationStatus status, String message) {
        return new DealOperationResult(false, status, message, null, null, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public DealOperationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Deal getDeal() {
        return deal;
    }

    public Offer getOffer() {
        return offer;
    }

    public boolean isPromptRating() {
        return promptRating;
    }
}
