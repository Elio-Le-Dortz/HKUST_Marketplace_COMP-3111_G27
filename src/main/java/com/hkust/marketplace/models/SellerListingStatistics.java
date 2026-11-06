package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Statistics of one of the Seller's listings, shown by use case 2.8 "View Seller Statistics"
 * (US-S08): one group of bars of the bar chart (views and offers received, step 4) and one row
 * of the per-listing breakdown.
 *
 * <p><b>Class diagram.</b> Nothing here is stored: the values are read from the attributes and
 * associations of the class diagram when the statistics are computed. {@code views} is
 * {@link Listing#getViewCount() Listing.viewCount}; {@code offersReceived} counts the
 * {@link Offer}s made on the listing ({@code Offer "*" --> "1" Listing : IsFor}), which include
 * the borrow requests and claims; {@code completedDeals} counts the {@link Deal}s of the listing
 * whose {@link DealStatus status} is Completed or Returned.</p>
 *
 * @param listingId      identifier of the listing
 * @param title          title of the listing, as shown in the breakdown
 * @param chartLabel     label of the listing on the bar chart: its title, shortened if needed,
 *                       and unique among the Seller's listings, so that two listings with the
 *                       same title get two groups of bars
 * @param type           type of the listing: Sell, Lend or Give Away
 * @param status         current status of the listing
 * @param views          number of times buyers opened the detail page of the listing
 * @param offersReceived number of offers, borrow requests and claims received on the listing,
 *                       whatever their status now
 * @param completedDeals number of completed deals on the listing: sold, given, or lent and returned
 */
public record SellerListingStatistics(String listingId,
                                      String title,
                                      String chartLabel,
                                      ListingType type,
                                      ListingStatus status,
                                      int views,
                                      int offersReceived,
                                      int completedDeals) {

    /**
     * Checks the values of the statistics.
     *
     * @throws NullPointerException     if the identifier, title, chart label, type or status is
     *                                  {@code null}
     * @throws IllegalArgumentException if the chart label is blank or a count is negative
     */
    public SellerListingStatistics {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(chartLabel, "chartLabel");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        if (chartLabel.isBlank()) {
            throw new IllegalArgumentException("The chart label of a listing cannot be blank.");
        }
        if (views < 0 || offersReceived < 0 || completedDeals < 0) {
            throw new IllegalArgumentException("The counts of a listing cannot be negative.");
        }
    }
}
