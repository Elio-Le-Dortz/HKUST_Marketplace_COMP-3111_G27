package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Result model encapsulating the outcome of rating a seller after a deal (US-B11 / Use Case 1.10).
 * Completely decoupled from JavaFX UI controls to ensure exhaustive headless unit testing.
 */
public class RateSellerResult {

    private final boolean success;
    private final RateSellerStatus status;
    private final String message;
    private final Review review;
    private final Deal deal;
    private final SellerRatingInfo sellerRatingInfo;

    public RateSellerResult(boolean success, RateSellerStatus status, String message,
                            Review review, Deal deal, SellerRatingInfo sellerRatingInfo) {
        this.success = success;
        this.status = status;
        this.message = message != null ? message : "";
        this.review = review;
        this.deal = deal;
        this.sellerRatingInfo = sellerRatingInfo;
    }

    public static RateSellerResult success(Review review, Deal deal, SellerRatingInfo sellerRatingInfo, String message) {
        return new RateSellerResult(true, RateSellerStatus.SUCCESS, message, review, deal, sellerRatingInfo);
    }

    public static RateSellerResult noStarRating(String message) {
        return new RateSellerResult(false, RateSellerStatus.NO_STAR_RATING, message, null, null, null);
    }

    public static RateSellerResult alreadyRated(Deal deal, String message) {
        return new RateSellerResult(false, RateSellerStatus.ALREADY_RATED, message, null, deal, null);
    }

    public static RateSellerResult skipped(Deal deal, String message) {
        return new RateSellerResult(false, RateSellerStatus.SKIPPED, message, null, deal, null);
    }

    public static RateSellerResult cancelled(Deal deal, String message) {
        return new RateSellerResult(false, RateSellerStatus.CANCELLED, message, null, deal, null);
    }

    public static RateSellerResult failure(RateSellerStatus status, String message) {
        return new RateSellerResult(false, status, message, null, null, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public RateSellerStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Review getReview() {
        return review;
    }

    public Deal getDeal() {
        return deal;
    }

    public SellerRatingInfo getSellerRatingInfo() {
        return sellerRatingInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateSellerResult that = (RateSellerResult) o;
        return success == that.success
                && status == that.status
                && Objects.equals(message, that.message)
                && Objects.equals(review, that.review)
                && Objects.equals(deal, that.deal)
                && Objects.equals(sellerRatingInfo, that.sellerRatingInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, status, message, review, deal, sellerRatingInfo);
    }

    @Override
    public String toString() {
        return "RateSellerResult{" +
                "success=" + success +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", review=" + review +
                ", deal=" + deal +
                ", sellerRatingInfo=" + sellerRatingInfo +
                '}';
    }
}
