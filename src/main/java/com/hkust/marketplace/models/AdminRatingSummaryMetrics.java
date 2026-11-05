package com.hkust.marketplace.models;

/**
 * Summary metrics for the Admin Seller Ratings Overview dashboard (US-A08).
 */
public class AdminRatingSummaryMetrics {
    private final int totalReviews;
    private final int flaggedReviews;
    private final int hiddenReviews;
    private final int visibleReviews;
    private final double averagePlatformRating;

    public AdminRatingSummaryMetrics(int totalReviews, int flaggedReviews, int hiddenReviews,
                                     int visibleReviews, double averagePlatformRating) {
        this.totalReviews = totalReviews;
        this.flaggedReviews = flaggedReviews;
        this.hiddenReviews = hiddenReviews;
        this.visibleReviews = visibleReviews;
        this.averagePlatformRating = averagePlatformRating;
    }

    public int getTotalReviews() {
        return totalReviews;
    }

    public int getFlaggedReviews() {
        return flaggedReviews;
    }

    public int getHiddenReviews() {
        return hiddenReviews;
    }

    public int getVisibleReviews() {
        return visibleReviews;
    }

    public double getAveragePlatformRating() {
        return averagePlatformRating;
    }
}
