package com.hkust.marketplace.models;

import java.util.Locale;
import java.util.Objects;

/**
 * Encapsulates rating and review statistics for a seller.
 * Supports Use Case 1.4 Basic Flow (Step 3) and Alternative Flow A2 (Seller Has No Rating).
 */
public class SellerRatingInfo {

    public static final String NO_RATINGS_TEXT = "No ratings yet";

    private final String sellerUsername;
    private final String sellerFullName;
    private final boolean hasRatings;
    private final double averageRating;
    private final int ratingCount;
    private final String displayText;

    public SellerRatingInfo(String sellerUsername, String sellerFullName, boolean hasRatings,
                            double averageRating, int ratingCount, String displayText) {
        this.sellerUsername = sellerUsername;
        this.sellerFullName = sellerFullName;
        this.hasRatings = hasRatings;
        this.averageRating = averageRating;
        this.ratingCount = ratingCount;
        this.displayText = displayText;
    }

    /**
     * Creates a {@link SellerRatingInfo} for a seller with no ratings yet (Alternative Flow A2).
     */
    public static SellerRatingInfo noRatings(String sellerUsername, String sellerFullName) {
        return new SellerRatingInfo(
                sellerUsername,
                sellerFullName,
                false,
                0.0,
                0,
                NO_RATINGS_TEXT
        );
    }

    /**
     * Creates a {@link SellerRatingInfo} with computed rating values.
     */
    public static SellerRatingInfo withRatings(String sellerUsername, String sellerFullName,
                                              double averageRating, int ratingCount) {
        String formatted = String.format(Locale.ENGLISH, "%.1f", averageRating);
        String display = String.format(Locale.ENGLISH, "★ %s / 5.0 (%d %s)",
                formatted,
                ratingCount,
                ratingCount == 1 ? "review" : "reviews");

        return new SellerRatingInfo(
                sellerUsername,
                sellerFullName,
                true,
                averageRating,
                ratingCount,
                display
        );
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public String getSellerFullName() {
        return sellerFullName;
    }

    public boolean hasRatings() {
        return hasRatings;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getFormattedScore() {
        return hasRatings ? String.format(Locale.ENGLISH, "%.1f", averageRating) : "N/A";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SellerRatingInfo that = (SellerRatingInfo) o;
        return hasRatings == that.hasRatings
                && Double.compare(that.averageRating, averageRating) == 0
                && ratingCount == that.ratingCount
                && Objects.equals(sellerUsername, that.sellerUsername)
                && Objects.equals(sellerFullName, that.sellerFullName)
                && Objects.equals(displayText, that.displayText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sellerUsername, sellerFullName, hasRatings, averageRating, ratingCount, displayText);
    }

    @Override
    public String toString() {
        return "SellerRatingInfo{" +
                "sellerUsername='" + sellerUsername + '\'' +
                ", hasRatings=" + hasRatings +
                ", averageRating=" + averageRating +
                ", ratingCount=" + ratingCount +
                ", displayText='" + displayText + '\'' +
                '}';
    }
}
