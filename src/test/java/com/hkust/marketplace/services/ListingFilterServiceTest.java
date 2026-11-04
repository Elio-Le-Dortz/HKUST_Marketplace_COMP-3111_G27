package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListingFilterService Unit Tests (US-B03 Browse & Filters)")
class ListingFilterServiceTest {

    private ListingFilterService service;
    private List<Listing> sampleListings;

    private Listing lStewart;
    private Listing lMacBook;
    private Listing lLamp;
    private Listing lCalculator;
    private Listing lFridgePending;
    private Listing lSoldBook;

    @BeforeEach
    void setUp() {
        service = new ListingFilterService();
        sampleListings = new ArrayList<>();

        // Live Sell Listing: Books, Like New, $180, posted 2 days ago
        lStewart = new Listing();
        lStewart.setId("L101");
        lStewart.setTitle("Calculus Early Transcendentals (Stewart)");
        lStewart.setDescription("Standard MATH 1013 reference, pristine condition with solutions booklet.");
        lStewart.setCategory(Category.BOOKS);
        lStewart.setType(ListingType.SELL);
        lStewart.setCondition(Condition.LIKE_NEW);
        lStewart.setPrice(180.0);
        lStewart.setStatus(ListingStatus.LIVE);
        lStewart.setPostedDate(LocalDateTime.now().minusDays(2));
        sampleListings.add(lStewart);

        // Live Sell Listing: Electronics, Used, $250, posted 1 day ago
        lMacBook = new Listing();
        lMacBook.setId("L102");
        lMacBook.setTitle("Apple 61W USB-C Power Adapter");
        lMacBook.setDescription("Original charger for MacBook Pro, minor scuffs but 100% functional.");
        lMacBook.setCategory(Category.ELECTRONICS);
        lMacBook.setType(ListingType.SELL);
        lMacBook.setCondition(Condition.USED);
        lMacBook.setPrice(250.0);
        lMacBook.setStatus(ListingStatus.LIVE);
        lMacBook.setPostedDate(LocalDateTime.now().minusDays(1));
        sampleListings.add(lMacBook);

        // Live Giveaway Listing: Furniture, Used, FREE (0.0), posted 3 days ago
        lLamp = new Listing();
        lLamp.setId("L103");
        lLamp.setTitle("IKEA Desk Lamp with LED bulb");
        lLamp.setDescription("Free white study lamp for desk, moving out so giving away.");
        lLamp.setCategory(Category.FURNITURE);
        lLamp.setType(ListingType.GIVEAWAY);
        lLamp.setCondition(Condition.USED);
        lLamp.setPrice(null);
        lLamp.setStatus(ListingStatus.LIVE);
        lLamp.setPostedDate(LocalDateTime.now().minusDays(3));
        sampleListings.add(lLamp);

        // Live Lend Listing: Electronics, Like New, Lend, posted 4 days ago
        lCalculator = new Listing();
        lCalculator.setId("L104");
        lCalculator.setTitle("TI-84 Plus Graphing Calculator");
        lCalculator.setDescription("Can lend for entire semester for engineering courses.");
        lCalculator.setCategory(Category.ELECTRONICS);
        lCalculator.setType(ListingType.LEND);
        lCalculator.setCondition(Condition.LIKE_NEW);
        lCalculator.setPrice(null);
        lCalculator.setLendingDays(90);
        lCalculator.setStatus(ListingStatus.LIVE);
        lCalculator.setPostedDate(LocalDateTime.now().minusDays(4));
        sampleListings.add(lCalculator);

        // Non-live listing: Pending Review
        lFridgePending = new Listing();
        lFridgePending.setId("L105");
        lFridgePending.setTitle("Mini Refrigerator");
        lFridgePending.setDescription("Dorm fridge awaiting admin review.");
        lFridgePending.setStatus(ListingStatus.PENDING_REVIEW);
        sampleListings.add(lFridgePending);

        // Non-live listing: Sold
        lSoldBook = new Listing();
        lSoldBook.setId("L106");
        lSoldBook.setTitle("Physics for Scientists");
        lSoldBook.setStatus(ListingStatus.SOLD);
        sampleListings.add(lSoldBook);
    }

    @Nested
    @DisplayName("Basic Flow & Status Filtering")
    class BasicFlowTests {

        @Test
        @DisplayName("Only LIVE listings are included in results; pending and sold are excluded")
        void testOnlyLiveListingsIncluded() {
            List<Listing> result = service.filterAndSort(sampleListings, ListingFilterCriteria.all());
            assertEquals(4, result.size());
            assertFalse(result.contains(lFridgePending));
            assertFalse(result.contains(lSoldBook));
            assertTrue(result.stream().allMatch(service::isLive));
        }

        @Test
        @DisplayName("Null or empty listings collection returns empty list")
        void testNullOrEmptyInput() {
            assertTrue(service.filterAndSort(null, ListingFilterCriteria.all()).isEmpty());
            assertTrue(service.filterAndSort(Collections.emptyList(), ListingFilterCriteria.all()).isEmpty());
        }

        @Test
        @DisplayName("Null criteria defaults to all live listings sorted newest first")
        void testNullCriteriaDefaults() {
            List<Listing> result = service.filterAndSort(sampleListings, null);
            assertEquals(4, result.size());
            assertEquals(lMacBook.getId(), result.get(0).getId(), "MacBook is newest (1 day ago)");
        }

        @Test
        @DisplayName("isLive edge cases with null or non-live statuses")
        void testIsLiveEdgeCases() {
            assertFalse(service.isLive(null));
            Listing nullStatusListing = new Listing();
            nullStatusListing.setStatus(null);
            assertFalse(service.isLive(nullStatusListing));

            for (ListingStatus status : ListingStatus.values()) {
                Listing l = new Listing();
                l.setStatus(status);
                if (status == ListingStatus.LIVE) {
                    assertTrue(service.isLive(l));
                } else {
                    assertFalse(service.isLive(l));
                }
            }
        }
    }

    @Nested
    @DisplayName("Subflow S1: Keyword Search")
    class KeywordSearchTests {

        @Test
        @DisplayName("Matches title case-insensitively")
        void testSearchMatchesTitle() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder().keyword("calculus").build();
            List<Listing> result = service.filterAndSort(sampleListings, criteria);
            assertEquals(1, result.size());
            assertEquals(lStewart.getId(), result.get(0).getId());
        }

        @Test
        @DisplayName("Matches description case-insensitively")
        void testSearchMatchesDescription() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder().keyword("moving out").build();
            List<Listing> result = service.filterAndSort(sampleListings, criteria);
            assertEquals(1, result.size());
            assertEquals(lLamp.getId(), result.get(0).getId());
        }

        @Test
        @DisplayName("Keyword with leading/trailing whitespace is handled properly")
        void testKeywordWhitespace() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder().keyword("   charger   ").build();
            List<Listing> result = service.filterAndSort(sampleListings, criteria);
            assertEquals(1, result.size());
            assertEquals(lMacBook.getId(), result.get(0).getId());
        }

        @Test
        @DisplayName("Null or empty keyword matches all live listings")
        void testEmptyKeyword() {
            ListingFilterCriteria c1 = ListingFilterCriteria.builder().keyword(null).build();
            ListingFilterCriteria c2 = ListingFilterCriteria.builder().keyword("   ").build();
            assertEquals(4, service.filterAndSort(sampleListings, c1).size());
            assertEquals(4, service.filterAndSort(sampleListings, c2).size());
        }

        @Test
        @DisplayName("Search with null title or null description does not throw NPE")
        void testNullTitleAndDescriptionSafe() {
            Listing noDesc = new Listing();
            noDesc.setTitle("Title Only");
            noDesc.setDescription(null);
            assertTrue(service.matchesKeyword(noDesc, "Title"));
            assertFalse(service.matchesKeyword(noDesc, "Nonexistent"));

            Listing noTitle = new Listing();
            noTitle.setTitle(null);
            noTitle.setDescription("Description Only");
            assertTrue(service.matchesKeyword(noTitle, "Description"));
            assertFalse(service.matchesKeyword(noTitle, "Nonexistent"));

            assertFalse(service.matchesKeyword(null, "Test"));
        }
    }

    @Nested
    @DisplayName("Subflow S2: Filter by Category, Type, Condition, Price")
    class FilterCriteriaTests {

        @Test
        @DisplayName("Filter by Category")
        void testCategoryFilter() {
            ListingFilterCriteria books = ListingFilterCriteria.builder().category(Category.BOOKS).build();
            List<Listing> result = service.filterAndSort(sampleListings, books);
            assertEquals(1, result.size());
            assertEquals(lStewart.getId(), result.get(0).getId());

            ListingFilterCriteria electro = ListingFilterCriteria.builder().category(Category.ELECTRONICS).build();
            assertEquals(2, service.filterAndSort(sampleListings, electro).size());

            ListingFilterCriteria clothing = ListingFilterCriteria.builder().category(Category.CLOTHING).build();
            assertTrue(service.filterAndSort(sampleListings, clothing).isEmpty());
        }

        @Test
        @DisplayName("Filter by Listing Type: SELL, LEND, GIVEAWAY")
        void testTypeFilter() {
            ListingFilterCriteria sell = ListingFilterCriteria.builder().type(ListingType.SELL).build();
            List<Listing> sellResults = service.filterAndSort(sampleListings, sell);
            assertEquals(2, sellResults.size());

            ListingFilterCriteria lend = ListingFilterCriteria.builder().type(ListingType.LEND).build();
            List<Listing> lendResults = service.filterAndSort(sampleListings, lend);
            assertEquals(1, lendResults.size());
            assertEquals(lCalculator.getId(), lendResults.get(0).getId());

            ListingFilterCriteria giveaway = ListingFilterCriteria.builder().type(ListingType.GIVEAWAY).build();
            List<Listing> giveawayResults = service.filterAndSort(sampleListings, giveaway);
            assertEquals(1, giveawayResults.size());
            assertEquals(lLamp.getId(), giveawayResults.get(0).getId());
        }

        @Test
        @DisplayName("Filter by Condition: LIKE_NEW vs USED")
        void testConditionFilter() {
            ListingFilterCriteria likeNew = ListingFilterCriteria.builder().condition(Condition.LIKE_NEW).build();
            List<Listing> likeNewResults = service.filterAndSort(sampleListings, likeNew);
            assertEquals(2, likeNewResults.size());

            ListingFilterCriteria used = ListingFilterCriteria.builder().condition(Condition.USED).build();
            List<Listing> usedResults = service.filterAndSort(sampleListings, used);
            assertEquals(2, usedResults.size());

            ListingFilterCriteria forParts = ListingFilterCriteria.builder().condition(Condition.FOR_PARTS).build();
            assertTrue(service.filterAndSort(sampleListings, forParts).isEmpty());
        }

        @Test
        @DisplayName("Price range filter: min price, max price, and both bounds")
        void testPriceRangeFilter() {
            // Min price: >= 200 (only MacBook at $250 matches)
            ListingFilterCriteria min200 = ListingFilterCriteria.builder().minPrice(200.0).build();
            List<Listing> r1 = service.filterAndSort(sampleListings, min200);
            assertEquals(1, r1.size());
            assertEquals(lMacBook.getId(), r1.get(0).getId());

            // Max price: <= 180 (Stewart book at $180 and Lamp at $0.0 match)
            ListingFilterCriteria max180 = ListingFilterCriteria.builder().maxPrice(180.0).build();
            List<Listing> r2 = service.filterAndSort(sampleListings, max180);
            assertEquals(2, r2.size());
            assertTrue(r2.contains(lStewart));
            assertTrue(r2.contains(lLamp));

            // Price range: 100 to 200 (only Stewart book at $180 matches; Lamp 0.0 is < 100)
            ListingFilterCriteria range100to200 = ListingFilterCriteria.builder()
                    .minPrice(100.0)
                    .maxPrice(200.0)
                    .build();
            List<Listing> r3 = service.filterAndSort(sampleListings, range100to200);
            assertEquals(1, r3.size());
            assertEquals(lStewart.getId(), r3.get(0).getId());
        }

        @Test
        @DisplayName("Price range: Giveaway items have effective price 0.0, Lend items are excluded")
        void testPriceRangeGiveawayAndLendSemantics() {
            // Range 0 to 50: matches giveaway lamp (0.0)
            ListingFilterCriteria freeTo50 = ListingFilterCriteria.builder()
                    .minPrice(0.0)
                    .maxPrice(50.0)
                    .build();
            List<Listing> r1 = service.filterAndSort(sampleListings, freeTo50);
            assertEquals(1, r1.size());
            assertEquals(lLamp.getId(), r1.get(0).getId());

            // Lend items have no monetary price: excluded when price filter is specified
            assertFalse(service.matchesPriceRange(lCalculator, 0.0, 500.0));
        }

        @Test
        @DisplayName("Price range: Inverted min > max matches nothing")
        void testInvertedPriceRange() {
            ListingFilterCriteria inverted = ListingFilterCriteria.builder()
                    .minPrice(500.0)
                    .maxPrice(100.0)
                    .build();
            assertTrue(service.filterAndSort(sampleListings, inverted).isEmpty());
        }

        @Test
        @DisplayName("Sell listing with null price is excluded when price filter is active")
        void testSellListingWithNullPrice() {
            Listing sellNoPrice = new Listing();
            sellNoPrice.setType(ListingType.SELL);
            sellNoPrice.setPrice(null);
            assertFalse(service.matchesPriceRange(sellNoPrice, 0.0, 100.0));
        }

        @Test
        @DisplayName("Combined filter: Category + Type + Condition + Price range + Keyword")
        void testCombinedFilter() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder()
                    .keyword("MacBook")
                    .category(Category.ELECTRONICS)
                    .type(ListingType.SELL)
                    .condition(Condition.USED)
                    .minPrice(200.0)
                    .maxPrice(300.0)
                    .build();

            List<Listing> result = service.filterAndSort(sampleListings, criteria);
            assertEquals(1, result.size());
            assertEquals(lMacBook.getId(), result.get(0).getId());

            // Changing one field to non-matching condition returns empty
            criteria.setCondition(Condition.NEW);
            assertTrue(service.filterAndSort(sampleListings, criteria).isEmpty());
        }
    }

    @Nested
    @DisplayName("Subflow S3: Sort Listings")
    class SortListingsTests {

        @Test
        @DisplayName("NEWEST_FIRST sorts by posted date descending")
        void testSortNewestFirst() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder()
                    .sortOrder(ListingSortOrder.NEWEST_FIRST)
                    .build();
            List<Listing> result = service.filterAndSort(sampleListings, criteria);
            assertEquals(4, result.size());
            // Dates: MacBook (-1d) > Stewart (-2d) > Lamp (-3d) > Calculator (-4d)
            assertEquals(lMacBook.getId(), result.get(0).getId());
            assertEquals(lStewart.getId(), result.get(1).getId());
            assertEquals(lLamp.getId(), result.get(2).getId());
            assertEquals(lCalculator.getId(), result.get(3).getId());
        }

        @Test
        @DisplayName("PRICE_LOW_TO_HIGH sorts by effective price ascending (Giveaway/Lend 0.0 first)")
        void testSortPriceLowToHigh() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder()
                    .sortOrder(ListingSortOrder.PRICE_LOW_TO_HIGH)
                    .build();
            List<Listing> result = service.filterAndSort(sampleListings, criteria);
            assertEquals(4, result.size());

            // Effective prices: Lamp ($0), Calculator ($0) < Stewart ($180) < MacBook ($250)
            assertEquals(180.0, result.get(2).getPrice());
            assertEquals(250.0, result.get(3).getPrice());
            assertEquals(lStewart.getId(), result.get(2).getId());
            assertEquals(lMacBook.getId(), result.get(3).getId());
        }

        @Test
        @DisplayName("PRICE_HIGH_TO_LOW sorts by effective price descending")
        void testSortPriceHighToLow() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder()
                    .sortOrder(ListingSortOrder.PRICE_HIGH_TO_LOW)
                    .build();
            List<Listing> result = service.filterAndSort(sampleListings, criteria);
            assertEquals(4, result.size());

            assertEquals(lMacBook.getId(), result.get(0).getId(), "$250 first");
            assertEquals(lStewart.getId(), result.get(1).getId(), "$180 second");
        }

        @Test
        @DisplayName("Sort handles null postedDate and equal prices gracefully")
        void testSortNullDatesAndEqualPrices() {
            Listing item1 = new Listing();
            item1.setId("A1");
            item1.setStatus(ListingStatus.LIVE);
            item1.setPostedDate(null);
            item1.setPrice(50.0);

            Listing item2 = new Listing();
            item2.setId("A2");
            item2.setStatus(ListingStatus.LIVE);
            item2.setPostedDate(LocalDateTime.now());
            item2.setPrice(50.0);

            List<Listing> list = List.of(item1, item2);

            // Newest first should put item2 (known date) before item1 (null date)
            List<Listing> sorted = service.filterAndSort(list, ListingFilterCriteria.builder().sortOrder(ListingSortOrder.NEWEST_FIRST).build());
            assertEquals("A2", sorted.get(0).getId());
            assertEquals("A1", sorted.get(1).getId());

            // Price equal: tie-breaker uses date
            List<Listing> sortedPrice = service.filterAndSort(list, ListingFilterCriteria.builder().sortOrder(ListingSortOrder.PRICE_LOW_TO_HIGH).build());
            assertEquals("A2", sortedPrice.get(0).getId());
        }
    }

    @Nested
    @DisplayName("Alternative Flows: A1 (No Match) & A2 (No Live Listings)")
    class AlternativeFlowTests {

        @Test
        @DisplayName("Flow A1: Query or filters with no matching live listings returns empty list")
        void testFlowA1NoMatchingListings() {
            ListingFilterCriteria impossibleKeyword = ListingFilterCriteria.builder()
                    .keyword("UnicornSpaceship999")
                    .build();
            List<Listing> result = service.filterAndSort(sampleListings, impossibleKeyword);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Flow A2: Empty live catalog returns empty list")
        void testFlowA2NoLiveListings() {
            List<Listing> nonLiveOnly = List.of(lFridgePending, lSoldBook);
            List<Listing> result = service.filterAndSort(nonLiveOnly, ListingFilterCriteria.all());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Defensive Null & Edge Case Coverage")
    class EdgeCaseTests {

        @Test
        void testMatchesWithNullListing() {
            assertFalse(service.matches(null, ListingFilterCriteria.all()));
        }

        @Test
        void testMatchesWithNullCriteria() {
            assertTrue(service.matches(lStewart, null));
        }

        @Test
        void testMatchesPriceRangeWithNullListing() {
            assertFalse(service.matchesPriceRange(null, 10.0, 50.0));
        }

        @Test
        void testGetEffectivePriceEdgeCases() {
            assertEquals(0.0, service.getEffectivePrice(null));
            Listing l = new Listing();
            l.setType(ListingType.SELL);
            l.setPrice(null);
            assertEquals(0.0, service.getEffectivePrice(l));
        }
    }
}
