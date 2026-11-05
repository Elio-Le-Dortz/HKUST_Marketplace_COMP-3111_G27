package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Result of an action of use case 2.9 "Handle Reviews" (US-S09): checking whether a review
 * can be answered or flagged, replying to it, or flagging it for the admin.
 *
 * <p>Instances are immutable and created through the static factory methods. The message
 * is always meant to be shown to the Seller.</p>
 */
public final class ReviewHandlingResult {

    /** Outcome of the action. */
    private final ReviewHandlingStatus status;
    /** Message for the Seller, explaining the outcome. */
    private final String message;
    /** The review the action was taken on, or {@code null} if it is not one the Seller may see. */
    private final Review review;

    /**
     * Creates a result. Use the static factory methods instead.
     *
     * @param status  outcome of the action
     * @param message message for the Seller
     * @param review  the review concerned, may be {@code null}
     */
    private ReviewHandlingResult(ReviewHandlingStatus status, String message, Review review) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = message == null ? "" : message;
        this.review = review;
    }

    /**
     * Creates the result of a successful action.
     *
     * @param review  the review the action was taken on
     * @param message message for the Seller
     * @return a successful result holding the review
     */
    public static ReviewHandlingResult success(Review review, String message) {
        return new ReviewHandlingResult(ReviewHandlingStatus.SUCCESS, message, review);
    }

    /**
     * Creates a failed result that reveals no review, for example because the review does
     * not exist or is about another Seller.
     *
     * @param status  why the action failed; must not be {@link ReviewHandlingStatus#SUCCESS}
     * @param message message for the Seller
     * @return a failed result
     * @throws IllegalArgumentException if {@code status} is {@code SUCCESS}
     */
    public static ReviewHandlingResult failure(ReviewHandlingStatus status, String message) {
        return failure(status, null, message);
    }

    /**
     * Creates a failed result about one of the Seller's reviews, which was left unchanged.
     *
     * @param status  why the action failed; must not be {@link ReviewHandlingStatus#SUCCESS}
     * @param review  the review concerned, may be {@code null}
     * @param message message for the Seller
     * @return a failed result
     * @throws IllegalArgumentException if {@code status} is {@code SUCCESS}
     */
    public static ReviewHandlingResult failure(ReviewHandlingStatus status, Review review, String message) {
        if (status == ReviewHandlingStatus.SUCCESS) {
            throw new IllegalArgumentException("A failure cannot have the SUCCESS status.");
        }
        return new ReviewHandlingResult(status, message, review);
    }

    /**
     * Tells whether the action succeeded.
     *
     * @return {@code true} if the status is {@link ReviewHandlingStatus#SUCCESS}
     */
    public boolean isSuccess() {
        return status == ReviewHandlingStatus.SUCCESS;
    }

    /**
     * Returns the outcome of the action.
     *
     * @return the status, never {@code null}
     */
    public ReviewHandlingStatus getStatus() {
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
     * Returns the review the action was taken on.
     *
     * @return the review, or {@code null} if it is not one the Seller may see
     */
    public Review getReview() {
        return review;
    }

    /**
     * Describes the result for logs and test failures.
     *
     * @return the status and the message
     */
    @Override
    public String toString() {
        return "ReviewHandlingResult[status=" + status + ", message=" + message + "]";
    }
}
