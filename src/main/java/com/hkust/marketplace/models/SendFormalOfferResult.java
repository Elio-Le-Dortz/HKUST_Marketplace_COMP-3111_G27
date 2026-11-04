package com.hkust.marketplace.models;

/**
 * Result object for sending a formal offer attached to a chat thread (US-B06 Subflow S2).
 */
public class SendFormalOfferResult {
    private final ChatOperationStatus status;
    private final Offer offer;
    private final Message message;
    private final String confirmationText;
    private final String errorMessage;

    private SendFormalOfferResult(ChatOperationStatus status, Offer offer, Message message,
                                  String confirmationText, String errorMessage) {
        this.status = status;
        this.offer = offer;
        this.message = message;
        this.confirmationText = confirmationText;
        this.errorMessage = errorMessage;
    }

    public static SendFormalOfferResult success(Offer offer, Message message, String confirmationText) {
        return new SendFormalOfferResult(ChatOperationStatus.SUCCESS, offer, message, confirmationText, null);
    }

    public static SendFormalOfferResult failure(ChatOperationStatus status, String errorMessage) {
        return new SendFormalOfferResult(status, null, null, null, errorMessage);
    }

    public boolean isSuccess() {
        return status == ChatOperationStatus.SUCCESS;
    }

    public ChatOperationStatus getStatus() {
        return status;
    }

    public Offer getOffer() {
        return offer;
    }

    public Message getMessage() {
        return message;
    }

    public String getConfirmationText() {
        return confirmationText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
