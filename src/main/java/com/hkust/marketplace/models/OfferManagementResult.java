package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Result of an action of use case 2.5 "Manage Offers And Requests" (US-S05): accepting,
 * declining or countering an offer, or opening the chat thread linked to it.
 *
 * <p>Instances are immutable and created through the static factory methods. The message
 * is always meant to be shown to the Seller.</p>
 */
public final class OfferManagementResult {

    /** Outcome of the action. */
    private final OfferManagementStatus status;
    /** Message for the Seller, explaining the outcome. */
    private final String message;
    /** The offer the action was taken on, or {@code null} if it was not found. */
    private final Offer offer;
    /** The deal recorded by an acceptance, otherwise {@code null}. */
    private final Deal deal;
    /** Number of other offers on the listing that an acceptance declined. */
    private final int otherOffersDeclined;
    /** The chat thread opened by {@code openChat}, otherwise {@code null}. */
    private final ChatThread chatThread;

    /**
     * Creates a result. Use the static factory methods instead.
     *
     * @param status              outcome of the action
     * @param message             message for the Seller
     * @param offer               the offer concerned, may be {@code null}
     * @param deal                the recorded deal, may be {@code null}
     * @param otherOffersDeclined number of other offers declined by an acceptance
     * @param chatThread          the opened chat thread, may be {@code null}
     */
    private OfferManagementResult(OfferManagementStatus status, String message, Offer offer,
                                  Deal deal, int otherOffersDeclined, ChatThread chatThread) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = message == null ? "" : message;
        this.offer = offer;
        this.deal = deal;
        this.otherOffersDeclined = otherOffersDeclined;
        this.chatThread = chatThread;
    }

    /**
     * Creates the result of a successful acceptance (subflow S1).
     *
     * @param offer               the accepted offer
     * @param deal                the deal recorded for it
     * @param otherOffersDeclined number of other offers on the listing that were declined
     * @param message             message for the Seller
     * @return a successful result holding the offer and the deal
     */
    public static OfferManagementResult accepted(Offer offer, Deal deal, int otherOffersDeclined, String message) {
        return new OfferManagementResult(OfferManagementStatus.SUCCESS, message, offer, deal,
                otherOffersDeclined, null);
    }

    /**
     * Creates the result of a successful decline (subflow S2) or counter offer (subflow S3).
     *
     * @param offer   the declined or countered offer
     * @param message message for the Seller
     * @return a successful result holding the offer
     */
    public static OfferManagementResult success(Offer offer, String message) {
        return new OfferManagementResult(OfferManagementStatus.SUCCESS, message, offer, null, 0, null);
    }

    /**
     * Creates the result of opening the chat thread linked to an offer (basic flow step 3.5).
     *
     * @param offer      the offer whose chat was opened
     * @param chatThread the chat thread between the Seller and the offer's buyer
     * @return a successful result holding the offer and the chat thread
     */
    public static OfferManagementResult chatOpened(Offer offer, ChatThread chatThread) {
        return new OfferManagementResult(OfferManagementStatus.SUCCESS, "Chat opened.", offer, null, 0, chatThread);
    }

    /**
     * Creates a failed result that concerns no particular offer.
     *
     * @param status  why the action failed; must not be {@link OfferManagementStatus#SUCCESS}
     * @param message message for the Seller
     * @return a failed result
     * @throws IllegalArgumentException if {@code status} is {@code SUCCESS}
     */
    public static OfferManagementResult failure(OfferManagementStatus status, String message) {
        return failure(status, null, message);
    }

    /**
     * Creates a failed result about an offer. The offer may have been changed by the
     * failure, for example declined because its listing is no longer available.
     *
     * @param status  why the action failed; must not be {@link OfferManagementStatus#SUCCESS}
     * @param offer   the offer concerned, may be {@code null}
     * @param message message for the Seller
     * @return a failed result
     * @throws IllegalArgumentException if {@code status} is {@code SUCCESS}
     */
    public static OfferManagementResult failure(OfferManagementStatus status, Offer offer, String message) {
        if (status == OfferManagementStatus.SUCCESS) {
            throw new IllegalArgumentException("A failure cannot have the SUCCESS status.");
        }
        return new OfferManagementResult(status, message, offer, null, 0, null);
    }

    /**
     * Tells whether the action succeeded.
     *
     * @return {@code true} if the status is {@link OfferManagementStatus#SUCCESS}
     */
    public boolean isSuccess() {
        return status == OfferManagementStatus.SUCCESS;
    }

    /**
     * Returns the outcome of the action.
     *
     * @return the status, never {@code null}
     */
    public OfferManagementStatus getStatus() {
        return status;
    }

    /**
     * Returns the message for the Seller.
     *
     * @return the message, never {@code null}
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the offer the action was taken on.
     *
     * @return the offer, or {@code null} if it was not found
     */
    public Offer getOffer() {
        return offer;
    }

    /**
     * Returns the deal recorded by a successful acceptance.
     *
     * @return the deal, or {@code null} for any other result
     */
    public Deal getDeal() {
        return deal;
    }

    /**
     * Returns how many other offers on the listing a successful acceptance declined.
     *
     * @return the number of declined offers, {@code 0} for any other result
     */
    public int getOtherOffersDeclined() {
        return otherOffersDeclined;
    }

    /**
     * Returns the chat thread opened by {@code openChat}.
     *
     * @return the chat thread, or {@code null} for any other result
     */
    public ChatThread getChatThread() {
        return chatThread;
    }

    /**
     * Describes the result for logs and test failures.
     *
     * @return the status and the message
     */
    @Override
    public String toString() {
        return "OfferManagementResult[status=" + status + ", message=" + message + "]";
    }
}
