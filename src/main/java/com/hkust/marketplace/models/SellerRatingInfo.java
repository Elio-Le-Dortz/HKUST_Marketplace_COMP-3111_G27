package com.hkust.marketplace.models;

import java.util.Locale;
import java.util.Objects;

/**
 * Encapsulates rating and review statistics for a seller.
 *
 * <p>Supports Use Case 1.4 Basic Flow (Step 3) and Alternative Flow A2 (Seller Has No Rating).
 * Provides formatted star display strings for listing details and seller profile views.</p>
 */
public class SellerRatingInfo {

    /** Default display text when a seller has received no reviews. */
    public static final String NO_RATINGS_TEXT = "No ratings yet";

    /** Username of the rated seller. */
    private final String sellerUsername;

    /** Full display name of the rated seller. */
    private final String sellerFullName;

    /** Whether the seller has at least one active, non-hidden review. */
    private final boolean hasRatings;

    /** Arithmetic mean of all active rating scores (1.0 to 5.0). */
    private final double averageRating;

    /** Total number of active, non-hidden reviews. */
    private final int ratingCount;

    /** Human-readable display string summarizing score and count. */
    private final String displayText;

    /**
     * Constructs a {@link SellerRatingInfo} instance with full metrics.
     *
     * @param sellerUsername username of the seller
     * @param sellerFullName display name of the seller
     * @param hasRatings     whether ratings exist
     * @param averageRating  average rating score
     * @param ratingCount    number of reviews
     * @param displayText    formatted presentation text
     */
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
     *
     * @param sellerUsername username of the seller
     * @param sellerFullName full display name of the seller
     * @return empty rating information instance
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
     *
     * @param sellerUsername username of the seller
     * @param sellerFullName full display name of the seller
     * @param averageRating  calculated average rating score
     * @param ratingCount    total review count
     * @return populated rating information instance
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

    /**
     * Gets the seller's username.
     *
     * @return username
     */
    public String getSellerUsername() {
        return sellerUsername;
    }

    /**
     * Gets the seller's full name.
     *
     * @return full name
     */
    public String getSellerFullName() {
        return sellerFullName;
    }

    /**
     * Checks if the seller has received ratings.
     *
     * @return {@code true} if ratings exist, {@code false} otherwise
     */
    public boolean hasRatings() {
        return hasRatings;
    }

    /**
     * Gets the average rating score.
     *
     * @return average score
     */
    public double getAverageRating() {
        return averageRating;
    }

    /**
     * Gets the total count of reviews.
     *
     * @return review count
     */
    public int getRatingCount() {
        return ratingCount;
    }

    /**
     * Gets the full summary display text.
     *
     * @return summary text
     */
    public String getDisplayText() {
        return displayText;
    }

    /**
     * Gets the formatted average score (e.g. {@code "4.8"}) or {@code "N/A"}.
     *
     * @return formatted score string
     */
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
