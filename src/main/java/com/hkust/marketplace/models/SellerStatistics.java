package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Statistics of a Seller's listings, as shown by use case 2.8 "View Seller Statistics"
 * (US-S08): the four values of step 3 (total views, offers received, completed deals and
 * average rating), the data of the bar chart of step 4 (views and offers received for each
 * listing) and the data of the pie chart of step 5 (completed deals by type).
 *
 * <p><b>Class diagram.</b> The statistics are not stored: they are computed from the class
 * diagram each time the Seller opens the screen (see
 * {@link com.hkust.marketplace.services.SellerStatisticsService}), so they always reflect the
 * live data. The totals are the sums over {@link #getListings()}, so the values of step 3 always
 * agree with the charts. The average rating excludes the reviews hidden by the admin
 * ({@code Review.hidden}), as the class diagram requires.</p>
 *
 * <p>Instances are immutable.</p>
 */
public final class SellerStatistics {

    /** Username of the Seller the statistics are about. */
    private final String sellerUsername;
    /** Statistics of each listing, in the order of the My Listings screen (newest first). */
    private final List<SellerListingStatistics> listings;
    /** Number of completed deals of each type, every type included. */
    private final Map<DealType, Integer> completedDealsByType;
    /** Average rating of the Seller over the reviews not hidden by the admin. */
    private final SellerRatingInfo rating;
    /** When the statistics were computed. */
    private final LocalDateTime computedAt;

    /**
     * Creates the statistics of a Seller. The list and the map are copied, so later changes to
     * them do not change the statistics.
     *
     * @param sellerUsername       username of the Seller
     * @param listings             statistics of each listing, in display order
     * @param completedDealsByType number of completed deals of each type; a missing type counts
     *                             as zero
     * @param rating               average rating of the Seller, without the hidden reviews
     * @param computedAt           when the statistics were computed
     * @throws NullPointerException     if an argument, a listing, a key or a count is {@code null}
     * @throws IllegalArgumentException if a count of completed deals is negative, if two listings
     *                                  have the same identifier or the same chart label (the bar
     *                                  chart would merge them), or if the completed deals of the
     *                                  listings do not add up to the completed deals by type
     */
    public SellerStatistics(String sellerUsername, List<SellerListingStatistics> listings,
                            Map<DealType, Integer> completedDealsByType, SellerRatingInfo rating,
                            LocalDateTime computedAt) {
        this.sellerUsername = Objects.requireNonNull(sellerUsername, "sellerUsername");
        this.listings = List.copyOf(Objects.requireNonNull(listings, "listings"));
        this.rating = Objects.requireNonNull(rating, "rating");
        this.computedAt = Objects.requireNonNull(computedAt, "computedAt");
        Objects.requireNonNull(completedDealsByType, "completedDealsByType");

        Map<DealType, Integer> counts = new EnumMap<>(DealType.class);
        for (DealType type : DealType.values()) {
            counts.put(type, 0);
        }
        completedDealsByType.forEach((type, count) -> {
            Objects.requireNonNull(type, "deal type");
            Objects.requireNonNull(count, "count of completed deals");
            if (count < 0) {
                throw new IllegalArgumentException("The number of completed deals cannot be negative.");
            }
            counts.put(type, count);
        });
        this.completedDealsByType = Collections.unmodifiableMap(counts);

        Set<String> ids = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (SellerListingStatistics listing : this.listings) {
            if (!ids.add(listing.listingId())) {
                throw new IllegalArgumentException("Listing " + listing.listingId() + " is counted twice.");
            }
            if (!labels.add(listing.chartLabel())) {
                throw new IllegalArgumentException("Two listings have the chart label \"" + listing.chartLabel() + "\".");
            }
        }
        int dealsOfListings = this.listings.stream().mapToInt(SellerListingStatistics::completedDeals).sum();
        if (dealsOfListings != getCompletedDeals()) {
            throw new IllegalArgumentException("The completed deals of the listings (" + dealsOfListings
                    + ") do not add up to the completed deals by type (" + getCompletedDeals() + ").");
        }
    }

    /**
     * Returns the name of the outcome of a completed deal of a given type, as written in use
     * case 2.8 for the pie chart: Sold, Lent or Given.
     *
     * @param type the type of the deal
     * @return {@code Sold} for a sale, {@code Lent} for a loan, {@code Given} for a giveaway
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public static String outcomeName(DealType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case SALE -> "Sold";
            case LOAN -> "Lent";
            case GIVEAWAY -> "Given";
        };
    }

    /**
     * Returns the username of the Seller the statistics are about.
     *
     * @return the Seller's username
     */
    public String getSellerUsername() {
        return sellerUsername;
    }

    /**
     * Returns the statistics of each of the Seller's listings, in the order of the My Listings
     * screen: the data of the bar chart (step 4).
     *
     * @return the statistics of each listing; the list cannot be changed
     */
    public List<SellerListingStatistics> getListings() {
        return listings;
    }

    /**
     * Returns the number of listings the statistics are computed over.
     *
     * @return the number of listings
     */
    public int getListingCount() {
        return listings.size();
    }

    /**
     * Tells whether the Seller has listings. Without listings, the screen shows zero values and a
     * message in place of the charts (alternative flow A1).
     *
     * @return {@code true} if the statistics cover at least one listing
     */
    public boolean hasListings() {
        return !listings.isEmpty();
    }

    /**
     * Returns the total number of views of the Seller's listings (step 2): the number of times
     * buyers opened their detail page.
     *
     * @return the sum of the views of every listing
     */
    public int getTotalViews() {
        return listings.stream().mapToInt(SellerListingStatistics::views).sum();
    }

    /**
     * Returns the number of offers received on the Seller's listings (step 2): offers, borrow
     * requests and claims, whatever their status now.
     *
     * @return the sum of the offers received on every listing
     */
    public int getOffersReceived() {
        return listings.stream().mapToInt(SellerListingStatistics::offersReceived).sum();
    }

    /**
     * Returns the number of completed deals on the Seller's listings (step 2): items sold,
     * given, or lent and returned.
     *
     * @return the number of completed deals of every type
     */
    public int getCompletedDeals() {
        return completedDealsByType.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Returns the number of completed deals of one type: a slice of the pie chart (step 5).
     *
     * @param type the type of the deals
     * @return the number of completed deals of this type, zero if there are none
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public int getCompletedDeals(DealType type) {
        return completedDealsByType.get(Objects.requireNonNull(type, "type"));
    }

    /**
     * Returns the number of completed deals of each type: the data of the pie chart (step 5).
     *
     * @return the number of completed deals of every type, zero included; the map cannot be changed
     */
    public Map<DealType, Integer> getCompletedDealsByType() {
        return completedDealsByType;
    }

    /**
     * Tells whether the Seller has completed deals. Without completed deals, the screen shows a
     * message in place of the pie chart (alternative flow A2).
     *
     * @return {@code true} if at least one deal is completed
     */
    public boolean hasCompletedDeals() {
        return getCompletedDeals() > 0;
    }

    /**
     * Returns the Seller's average rating (step 2), computed without the reviews hidden by the
     * admin. When the Seller has no ratings, the screen shows "No ratings yet" (step 3).
     *
     * @return the average rating and the number of ratings
     */
    public SellerRatingInfo getRating() {
        return rating;
    }

    /**
     * Tells whether the Seller has at least one rating counted in the average.
     *
     * @return {@code true} if the average rating can be shown
     */
    public boolean hasRatings() {
        return rating.hasRatings();
    }

    /**
     * Returns the highest value drawn on the bar chart: the most views or offers received by one
     * listing. The chart uses it to choose the scale of its vertical axis.
     *
     * @return the highest number of views or offers of a listing, zero without listings
     */
    public int getMaxListingValue() {
        return listings.stream()
                .mapToInt(listing -> Math.max(listing.views(), listing.offersReceived()))
                .max()
                .orElse(0);
    }

    /**
     * Returns when the statistics were computed.
     *
     * @return the time of the computation
     */
    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    /**
     * Describes the statistics, for logs and test failures.
     *
     * @return the Seller, the number of listings and the totals
     */
    @Override
    public String toString() {
        return "SellerStatistics[seller=" + sellerUsername
                + ", listings=" + listings.size()
                + ", views=" + getTotalViews()
                + ", offers=" + getOffersReceived()
                + ", completedDeals=" + completedDealsByType
                + ", rating=" + (rating.hasRatings() ? rating.getAverageRating() + " (" + rating.getRatingCount() + ")" : "none")
                + "]";
    }
}
