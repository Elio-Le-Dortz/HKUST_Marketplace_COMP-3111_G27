package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the types of use case 2.8 "View Seller Statistics" (US-S08): the statistics of one
 * listing and the statistics of a Seller, which hold the four values of step 3 and the data of
 * the bar chart (step 4) and of the pie chart (step 5).
 */
@DisplayName("SellerListingStatistics and SellerStatistics (US-S08)")
class SellerStatisticsModelsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 14, 30);

    private static SellerListingStatistics listing(String id, String label, int views, int offers, int deals) {
        return new SellerListingStatistics(id, "Title of " + id, label, ListingType.SELL, ListingStatus.LIVE,
                views, offers, deals);
    }

    private static SellerRatingInfo noRatings() {
        return SellerRatingInfo.noRatings("sam", "Sam Seller");
    }

    private static SellerStatistics stats(List<SellerListingStatistics> listings, Map<DealType, Integer> deals) {
        return new SellerStatistics("sam", listings, deals, noRatings(), NOW);
    }

    @Nested
    @DisplayName("SellerListingStatistics")
    class ListingStatistics {

        @Test
        @DisplayName("holds the values of one listing")
        void holdsValues() {
            SellerListingStatistics s = new SellerListingStatistics("L1", "Desk Lamp", "Desk Lamp",
                    ListingType.LEND, ListingStatus.LENT, 7, 3, 2);

            assertEquals("L1", s.listingId());
            assertEquals("Desk Lamp", s.title());
            assertEquals("Desk Lamp", s.chartLabel());
            assertEquals(ListingType.LEND, s.type());
            assertEquals(ListingStatus.LENT, s.status());
            assertEquals(7, s.views());
            assertEquals(3, s.offersReceived());
            assertEquals(2, s.completedDeals());
        }

        @Test
        @DisplayName("zero counts are allowed: a new listing has no views, offers or deals")
        void zeroCounts() {
            SellerListingStatistics s = listing("L1", "Lamp", 0, 0, 0);

            assertEquals(0, s.views());
            assertEquals(0, s.offersReceived());
            assertEquals(0, s.completedDeals());
        }

        @Test
        @DisplayName("the identifier, title, chart label, type and status are required")
        void requiredValues() {
            assertThrows(NullPointerException.class, () -> new SellerListingStatistics(null, "T", "T",
                    ListingType.SELL, ListingStatus.LIVE, 0, 0, 0));
            assertThrows(NullPointerException.class, () -> new SellerListingStatistics("L1", null, "T",
                    ListingType.SELL, ListingStatus.LIVE, 0, 0, 0));
            assertThrows(NullPointerException.class, () -> new SellerListingStatistics("L1", "T", null,
                    ListingType.SELL, ListingStatus.LIVE, 0, 0, 0));
            assertThrows(NullPointerException.class, () -> new SellerListingStatistics("L1", "T", "T",
                    null, ListingStatus.LIVE, 0, 0, 0));
            assertThrows(NullPointerException.class, () -> new SellerListingStatistics("L1", "T", "T",
                    ListingType.SELL, null, 0, 0, 0));
        }

        @Test
        @DisplayName("a blank chart label is refused, since the bar chart could not show it")
        void blankLabel() {
            assertThrows(IllegalArgumentException.class, () -> listing("L1", "   ", 0, 0, 0));
        }

        @ParameterizedTest(name = "views={0}, offers={1}, deals={2}")
        @CsvSource({"-1, 0, 0", "0, -1, 0", "0, 0, -1"})
        @DisplayName("a negative count is refused")
        void negativeCounts(int views, int offers, int deals) {
            assertThrows(IllegalArgumentException.class, () -> listing("L1", "Lamp", views, offers, deals));
        }
    }

    @Nested
    @DisplayName("SellerStatistics: values of step 3")
    class Totals {

        @Test
        @DisplayName("the totals are the sums over the listings, so they always agree with the bar chart")
        void totalsAreSums() {
            SellerStatistics s = stats(List.of(listing("L1", "A", 5, 2, 1), listing("L2", "B", 3, 4, 0)),
                    Map.of(DealType.SALE, 1));

            assertEquals(2, s.getListingCount());
            assertTrue(s.hasListings());
            assertEquals(8, s.getTotalViews());
            assertEquals(6, s.getOffersReceived());
            assertEquals(1, s.getCompletedDeals());
            assertEquals(5, s.getMaxListingValue(), "the highest bar is the 5 views of L1");
        }

        @Test
        @DisplayName("A1: without listings every value is zero")
        void noListings() {
            SellerStatistics s = stats(List.of(), Map.of());

            assertFalse(s.hasListings());
            assertEquals(0, s.getListingCount());
            assertEquals(0, s.getTotalViews());
            assertEquals(0, s.getOffersReceived());
            assertEquals(0, s.getCompletedDeals());
            assertFalse(s.hasCompletedDeals());
            assertEquals(0, s.getMaxListingValue());
        }

        @Test
        @DisplayName("the highest bar is the most views or offers of one listing")
        void maxListingValue() {
            SellerStatistics s = stats(List.of(listing("L1", "A", 2, 9, 0), listing("L2", "B", 6, 1, 0)), Map.of());

            assertEquals(9, s.getMaxListingValue());
        }

        @Test
        @DisplayName("the rating, the Seller and the time of computation are kept")
        void ratingAndMetadata() {
            SellerRatingInfo rating = SellerRatingInfo.withRatings("sam", "Sam Seller", 4.5, 2);
            SellerStatistics s = new SellerStatistics("sam", List.of(), Map.of(), rating, NOW);

            assertSame(rating, s.getRating());
            assertTrue(s.hasRatings());
            assertEquals("sam", s.getSellerUsername());
            assertEquals(NOW, s.getComputedAt());
        }

        @Test
        @DisplayName("step 3: without ratings, hasRatings() is false so that the screen shows \"No ratings yet\"")
        void noRatings() {
            SellerStatistics s = stats(List.of(), Map.of());

            assertFalse(s.hasRatings());
            assertEquals(SellerRatingInfo.NO_RATINGS_TEXT, s.getRating().getDisplayText());
        }

        @Test
        @DisplayName("toString() names the Seller and the totals")
        void describes() {
            SellerStatistics none = stats(List.of(listing("L1", "A", 5, 2, 0)), Map.of());
            SellerStatistics rated = new SellerStatistics("sam", List.of(), Map.of(),
                    SellerRatingInfo.withRatings("sam", "Sam Seller", 4.0, 3), NOW);

            assertTrue(none.toString().contains("seller=sam"));
            assertTrue(none.toString().contains("views=5"));
            assertTrue(none.toString().contains("rating=none"));
            assertTrue(rated.toString().contains("rating=4.0 (3)"));
        }
    }

    @Nested
    @DisplayName("SellerStatistics: pie chart of step 5")
    class CompletedDealsByType {

        @Test
        @DisplayName("every type is present, a missing type counting as zero")
        void everyTypePresent() {
            SellerStatistics s = stats(List.of(listing("L1", "A", 0, 0, 2)), Map.of(DealType.LOAN, 2));

            assertEquals(Map.of(DealType.SALE, 0, DealType.LOAN, 2, DealType.GIVEAWAY, 0), s.getCompletedDealsByType());
            assertEquals(0, s.getCompletedDeals(DealType.SALE));
            assertEquals(2, s.getCompletedDeals(DealType.LOAN));
            assertEquals(0, s.getCompletedDeals(DealType.GIVEAWAY));
            assertTrue(s.hasCompletedDeals());
        }

        @Test
        @DisplayName("A2: without completed deals, hasCompletedDeals() is false although there are listings")
        void noCompletedDeals() {
            SellerStatistics s = stats(List.of(listing("L1", "A", 4, 1, 0)), Map.of(DealType.SALE, 0));

            assertTrue(s.hasListings());
            assertFalse(s.hasCompletedDeals());
            assertEquals(0, s.getCompletedDeals());
        }

        @Test
        @DisplayName("the map of deals by type cannot be changed")
        void unmodifiable() {
            SellerStatistics s = stats(List.of(), Map.of());

            assertThrows(UnsupportedOperationException.class, () -> s.getCompletedDealsByType().put(DealType.SALE, 3));
        }

        @Test
        @DisplayName("a type must be given to read its count")
        void nullType() {
            SellerStatistics s = stats(List.of(), Map.of());

            assertThrows(NullPointerException.class, () -> s.getCompletedDeals(null));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(DealType.class)
        @DisplayName("each type has the outcome name of use case 2.8")
        void outcomeNames(DealType type) {
            String expected = switch (type) {
                case SALE -> "Sold";
                case LOAN -> "Lent";
                case GIVEAWAY -> "Given";
            };

            assertEquals(expected, SellerStatistics.outcomeName(type));
        }

        @Test
        @DisplayName("the outcome of no type cannot be named")
        void outcomeOfNull() {
            assertThrows(NullPointerException.class, () -> SellerStatistics.outcomeName(null));
        }
    }

    @Nested
    @DisplayName("SellerStatistics: checks and immutability")
    class Checks {

        @Test
        @DisplayName("later changes to the given list and map do not change the statistics")
        void defensiveCopies() {
            List<SellerListingStatistics> listings = new ArrayList<>(List.of(listing("L1", "A", 1, 1, 1)));
            Map<DealType, Integer> deals = new EnumMap<>(DealType.class);
            deals.put(DealType.SALE, 1);
            SellerStatistics s = stats(listings, deals);

            listings.add(listing("L2", "B", 50, 50, 0));
            deals.put(DealType.SALE, 9);

            assertEquals(1, s.getListingCount());
            assertEquals(1, s.getCompletedDeals(DealType.SALE));
            assertThrows(UnsupportedOperationException.class, () -> s.getListings().clear());
        }

        @Test
        @DisplayName("every argument is required")
        void requiredArguments() {
            List<SellerListingStatistics> none = List.of();
            Map<DealType, Integer> noDeals = Map.of();

            assertThrows(NullPointerException.class, () -> new SellerStatistics(null, none, noDeals, noRatings(), NOW));
            assertThrows(NullPointerException.class, () -> new SellerStatistics("sam", null, noDeals, noRatings(), NOW));
            assertThrows(NullPointerException.class, () -> new SellerStatistics("sam", none, null, noRatings(), NOW));
            assertThrows(NullPointerException.class, () -> new SellerStatistics("sam", none, noDeals, null, NOW));
            assertThrows(NullPointerException.class, () -> new SellerStatistics("sam", none, noDeals, noRatings(), null));
        }

        @Test
        @DisplayName("a listing, a deal type or a count cannot be null")
        void nullElements() {
            List<SellerListingStatistics> withNull = new ArrayList<>();
            withNull.add(null);
            Map<DealType, Integer> nullType = new HashMap<>();
            nullType.put(null, 1);
            Map<DealType, Integer> nullCount = new HashMap<>();
            nullCount.put(DealType.SALE, null);

            assertThrows(NullPointerException.class, () -> stats(withNull, Map.of()));
            assertThrows(NullPointerException.class, () -> stats(List.of(), nullType));
            assertThrows(NullPointerException.class, () -> stats(List.of(), nullCount));
        }

        @Test
        @DisplayName("a negative number of completed deals is refused")
        void negativeDeals() {
            assertThrows(IllegalArgumentException.class, () -> stats(List.of(), Map.of(DealType.GIVEAWAY, -1)));
        }

        @Test
        @DisplayName("a listing counted twice is refused")
        void duplicateListing() {
            assertThrows(IllegalArgumentException.class,
                    () -> stats(List.of(listing("L1", "A", 0, 0, 0), listing("L1", "B", 0, 0, 0)), Map.of()));
        }

        @Test
        @DisplayName("two listings with the same chart label are refused, since the bar chart would merge them")
        void duplicateLabel() {
            assertThrows(IllegalArgumentException.class,
                    () -> stats(List.of(listing("L1", "Lamp", 0, 0, 0), listing("L2", "Lamp", 0, 0, 0)), Map.of()));
        }

        @Test
        @DisplayName("the completed deals of the listings must add up to the deals by type (bar and pie charts agree)")
        void dealsMustAddUp() {
            assertThrows(IllegalArgumentException.class,
                    () -> stats(List.of(listing("L1", "A", 0, 0, 1)), Map.of(DealType.SALE, 2)));
            assertThrows(IllegalArgumentException.class,
                    () -> stats(List.of(listing("L1", "A", 0, 0, 1)), Map.of()));
        }
    }
}
