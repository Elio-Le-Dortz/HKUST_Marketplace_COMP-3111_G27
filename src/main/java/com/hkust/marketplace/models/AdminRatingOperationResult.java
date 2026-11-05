package com.hkust.marketplace.models;

/**
 * Result returned by {@link com.hkust.marketplace.services.AdminRatingService} operations.
 */
public class AdminRatingOperationResult {
    private final AdminRatingOperationStatus status;
    private final String message;
    private final Review review;
    private final String affectedSellerUsername;
    private final Double updatedAverageRating;
    private final Integer updatedRatingCount;

    public AdminRatingOperationResult(AdminRatingOperationStatus status, String message, Review review,
                                      String affectedSellerUsername, Double updatedAverageRating,
                                      Integer updatedRatingCount) {
        this.status = status;
        this.message = message;
        this.review = review;
        this.affectedSellerUsername = affectedSellerUsername;
        this.updatedAverageRating = updatedAverageRating;
        this.updatedRatingCount = updatedRatingCount;
    }

    public static AdminRatingOperationResult success(String message, Review review, String sellerUsername,
                                                     double updatedAverageRating, int updatedRatingCount) {
        return new AdminRatingOperationResult(AdminRatingOperationStatus.SUCCESS, message, review,
                sellerUsername, updatedAverageRating, updatedRatingCount);
    }

    public static AdminRatingOperationResult successNoRatingChange(String message, Review review) {
        return new AdminRatingOperationResult(AdminRatingOperationStatus.SUCCESS, message, review,
                review != null ? review.getSellerUsername() : null, null, null);
    }

    public static AdminRatingOperationResult failure(AdminRatingOperationStatus status, String message) {
        return new AdminRatingOperationResult(status, message, null, null, null, null);
    }

    public static AdminRatingOperationResult invalidDateRange(String message) {
        return new AdminRatingOperationResult(AdminRatingOperationStatus.INVALID_DATE_RANGE, message, null, null, null, null);
    }

    public boolean isSuccess() {
        return status == AdminRatingOperationStatus.SUCCESS;
    }

    public AdminRatingOperationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Review getReview() {
        return review;
    }

    public String getAffectedSellerUsername() {
        return affectedSellerUsername;
    }

    public Double getUpdatedAverageRating() {
        return updatedAverageRating;
    }

    public Integer getUpdatedRatingCount() {
        return updatedRatingCount;
    }
}
