package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DealHistory Domain Models and DTOs Test")
class DealHistoryModelsTest {

    @Nested
    @DisplayName("DealHistoryItem Tests")
    class DealHistoryItemTests {

        @Test
        @DisplayName("Factory method fromDeal builds correctly with all entities present")
        void testFromDealFull() {
            Deal deal = new Deal();
            deal.setId("D100");
            deal.setListingId("L1");
            deal.setListingTitle("Discrete Math Textbook");
            deal.setBuyerUsername("alice");
            deal.setSellerUsername("charlie");
            deal.setType(DealType.SALE);
            deal.setStatus(DealStatus.COMPLETED);
            deal.setStartDate(LocalDateTime.of(2026, 11, 1, 10, 0));
            deal.setClosedDate(LocalDateTime.of(2026, 11, 2, 14, 30));
            deal.setDueDate(null);
            deal.setOfferPrice(150.0);
            deal.setRated(true);

            Listing listing = new Listing();
            listing.setId("L1");
            listing.setTitle("Discrete Math Textbook (Updated)");
            listing.setCategory(Category.BOOKS);

            Seller seller = new Seller();
            seller.setUsername("charlie");
            seller.setFullName("Charlie Lee");

            DealHistoryItem item = DealHistoryItem.fromDeal(deal, listing, seller);

            assertNotNull(item);
            assertEquals("D100", item.getDealId());
            assertEquals("L1", item.getListingId());
            assertEquals("Discrete Math Textbook (Updated)", item.getItemTitle());
            assertEquals("charlie", item.getSellerUsername());
            assertEquals("Charlie Lee", item.getSellerFullName());
            assertEquals(Category.BOOKS, item.getCategory());
            assertEquals(DealType.SALE, item.getDealType());
            assertEquals(DealStatus.COMPLETED, item.getDealStatus());
            assertEquals(150.0, item.getPrice());
            assertTrue(item.isRated());
            assertFalse(item.canRate());
            assertEquals("Sale", item.getTypeDisplayName());
            assertEquals("Bought", item.getActionTypeDisplayName());
            assertEquals("Completed", item.getOutcomeDisplayName());
            assertEquals("HK$ 150.00", item.getFormattedPrice());
            assertEquals("2026-11-01 10:00", item.getFormattedDealDate());
            assertEquals("2026-11-01", item.getFormattedDealDateShort());
            assertEquals("2026-11-02 14:30", item.getFormattedClosedDate());
            assertEquals("2026-11-02", item.getFormattedClosedDateShort());
            assertSame(deal, item.getDeal());
        }

        @Test
        @DisplayName("Factory method handles null deal, null listing, and null seller gracefully")
        void testFromDealNulls() {
            assertNull(DealHistoryItem.fromDeal(null, null, null));

            Deal deal = new Deal();
            deal.setId("D101");
            deal.setListingTitle("TI-84 Plus");
            deal.setSellerUsername("diana");
            deal.setType(DealType.LOAN);
            deal.setStatus(DealStatus.RETURNED);
            deal.setStartDate(null);
            deal.setClosedDate(null);
            deal.setDueDate(null);
            deal.setOfferPrice(null);
            deal.setRated(false);

            DealHistoryItem item = DealHistoryItem.fromDeal(deal, null, null);
            assertNotNull(item);
            assertEquals("TI-84 Plus", item.getItemTitle());
            assertEquals("diana", item.getSellerUsername());
            assertEquals("diana", item.getSellerFullName());
            assertEquals(Category.OTHER, item.getCategory());
            assertEquals(DealType.LOAN, item.getDealType());
            assertEquals(DealStatus.RETURNED, item.getDealStatus());
            assertEquals("Loan", item.getTypeDisplayName());
            assertEquals("Borrowed", item.getActionTypeDisplayName());
            assertEquals("Returned", item.getOutcomeDisplayName());
            assertEquals("Loan", item.getFormattedPrice());
            assertTrue(item.canRate());
            assertEquals("—", item.getFormattedDealDate());
            assertEquals("—", item.getFormattedDealDateShort());
            assertEquals("—", item.getFormattedClosedDate());
            assertEquals("—", item.getFormattedClosedDateShort());
        }

        @Test
        @DisplayName("Giveaway item formatting and displays")
        void testGiveawayItemDisplays() {
            Deal deal = new Deal();
            deal.setId("D102");
            deal.setListingTitle("Free Hoodie");
            deal.setSellerUsername("bob");
            deal.setType(DealType.GIVEAWAY);
            deal.setStatus(DealStatus.COMPLETED);
            deal.setStartDate(LocalDateTime.of(2026, 10, 15, 9, 0));
            deal.setClosedDate(LocalDateTime.of(2026, 10, 15, 9, 30));
            deal.setRated(false);

            DealHistoryItem item = DealHistoryItem.fromDeal(deal, null, null);
            assertNotNull(item);
            assertEquals("Giveaway", item.getTypeDisplayName());
            assertEquals("Received", item.getActionTypeDisplayName());
            assertEquals("Free", item.getFormattedPrice());
            assertTrue(item.canRate());
        }

        @Test
        @DisplayName("canRate returns false if status is active or deal is already rated")
        void testCanRateRules() {
            Deal d1 = new Deal();
            d1.setId("D1");
            d1.setStatus(DealStatus.ACTIVE);
            d1.setRated(false);
            DealHistoryItem i1 = DealHistoryItem.fromDeal(d1, null, null);
            assertFalse(i1.canRate(), "Active deals cannot be rated in history");

            Deal d2 = new Deal();
            d2.setId("D2");
            d2.setStatus(DealStatus.COMPLETED);
            d2.setRated(true);
            DealHistoryItem i2 = DealHistoryItem.fromDeal(d2, null, null);
            assertFalse(i2.canRate(), "Already rated deals cannot be rated again");

            Deal d3 = new Deal();
            d3.setId("D3");
            d3.setStatus(DealStatus.RETURNED);
            d3.setRated(false);
            DealHistoryItem i3 = DealHistoryItem.fromDeal(d3, null, null);
            assertTrue(i3.canRate(), "Unrated returned deals can be rated");
        }

        @Test
        @DisplayName("equals, hashCode, and toString contract")
        void testEqualsHashCodeToString() {
            Deal d1 = new Deal();
            d1.setId("D1");
            Deal d2 = new Deal();
            d2.setId("D1");
            Deal d3 = new Deal();
            d3.setId("D2");

            DealHistoryItem item1 = DealHistoryItem.fromDeal(d1, null, null);
            DealHistoryItem item2 = DealHistoryItem.fromDeal(d2, null, null);
            DealHistoryItem item3 = DealHistoryItem.fromDeal(d3, null, null);

            assertEquals(item1, item1);
            assertEquals(item1, item2);
            assertNotEquals(item1, item3);
            assertNotEquals(item1, null);
            assertNotEquals(item1, "String");
            assertEquals(item1.hashCode(), item2.hashCode());
            assertTrue(item1.toString().contains("D1"));

            // Price edge cases
            Deal dSaleNoPrice = new Deal();
            dSaleNoPrice.setType(DealType.SALE);
            dSaleNoPrice.setOfferPrice(null);
            assertEquals("—", DealHistoryItem.fromDeal(dSaleNoPrice, null, null).getFormattedPrice());
        }
    }

    @Nested
    @DisplayName("DealHistoryFilterCriteria Tests")
    class DealHistoryFilterCriteriaTests {

        private DealHistoryItem createSampleItem(String title,
                                                String seller,
                                                String sellerName,
                                                Category category,
                                                DealType type,
                                                LocalDate dealDate,
                                                LocalDate closedDate) {
            Deal deal = new Deal();
            deal.setId("D-" + title);
            deal.setListingTitle(title);
            deal.setSellerUsername(seller);
            deal.setType(type);
            deal.setStatus(DealStatus.COMPLETED);
            deal.setStartDate(dealDate != null ? dealDate.atTime(10, 0) : null);
            deal.setClosedDate(closedDate != null ? closedDate.atTime(12, 0) : null);

            Listing l = new Listing();
            l.setTitle(title);
            l.setCategory(category);

            Seller s = new Seller();
            s.setUsername(seller);
            s.setFullName(sellerName);

            return DealHistoryItem.fromDeal(deal, l, s);
        }

        @Test
        @DisplayName("Empty criteria matches everything and isEmpty() returns true")
        void testEmptyCriteria() {
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria();
            assertTrue(criteria.isEmpty());

            DealHistoryItem item = createSampleItem("Java Textbook", "charlie", "Charlie Lee",
                    Category.BOOKS, DealType.SALE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));

            assertTrue(criteria.matches(item));
            assertFalse(criteria.matches(null));
        }

        @Test
        @DisplayName("Date range filtering: fromDate, toDate, and combined")
        void testDateRangeFiltering() {
            DealHistoryItem item = createSampleItem("Desk Lamp", "diana", "Diana Ng",
                    Category.FURNITURE, DealType.SALE, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 20));

            // fromDate before or on dealDate -> matches
            assertTrue(new DealHistoryFilterCriteria().withFromDate(LocalDate.of(2026, 10, 1)).matches(item));
            assertTrue(new DealHistoryFilterCriteria().withFromDate(LocalDate.of(2026, 10, 15)).matches(item));
            // fromDate after closedDate -> does not match
            assertFalse(new DealHistoryFilterCriteria().withFromDate(LocalDate.of(2026, 10, 21)).matches(item));

            // toDate on or after closedDate -> matches
            assertTrue(new DealHistoryFilterCriteria().withToDate(LocalDate.of(2026, 10, 20)).matches(item));
            assertTrue(new DealHistoryFilterCriteria().withToDate(LocalDate.of(2026, 10, 25)).matches(item));
            // toDate before dealDate -> does not match
            assertFalse(new DealHistoryFilterCriteria().withToDate(LocalDate.of(2026, 10, 10)).matches(item));

            // combined range
            assertTrue(new DealHistoryFilterCriteria()
                    .withFromDate(LocalDate.of(2026, 10, 10))
                    .withToDate(LocalDate.of(2026, 10, 25))
                    .matches(item));

            assertFalse(new DealHistoryFilterCriteria()
                    .withFromDate(LocalDate.of(2026, 10, 22))
                    .withToDate(LocalDate.of(2026, 10, 30))
                    .matches(item));

            // Null dealDate, valid closedDate
            DealHistoryItem itemNullDealDate = createSampleItem("Desk", "diana", "Diana Ng",
                    Category.FURNITURE, DealType.SALE, null, LocalDate.of(2026, 10, 20));
            assertTrue(new DealHistoryFilterCriteria().withFromDate(LocalDate.of(2026, 10, 15)).matches(itemNullDealDate));
            assertTrue(new DealHistoryFilterCriteria().withToDate(LocalDate.of(2026, 10, 25)).matches(itemNullDealDate));
            assertFalse(new DealHistoryFilterCriteria().withFromDate(LocalDate.of(2026, 10, 25)).matches(itemNullDealDate));
            assertFalse(new DealHistoryFilterCriteria().withToDate(LocalDate.of(2026, 10, 15)).matches(itemNullDealDate));

            // Null closedDate, valid dealDate
            DealHistoryItem itemNullClosedDate = createSampleItem("Desk", "diana", "Diana Ng",
                    Category.FURNITURE, DealType.SALE, LocalDate.of(2026, 10, 15), null);
            assertTrue(new DealHistoryFilterCriteria().withFromDate(LocalDate.of(2026, 10, 10)).matches(itemNullClosedDate));
            assertTrue(new DealHistoryFilterCriteria().withToDate(LocalDate.of(2026, 10, 20)).matches(itemNullClosedDate));

            // Both null
            DealHistoryItem itemBothNull = createSampleItem("Desk", "diana", "Diana Ng",
                    Category.FURNITURE, DealType.SALE, null, null);
            assertFalse(new DealHistoryFilterCriteria().withFromDate(LocalDate.of(2026, 10, 10)).matches(itemBothNull));
            assertFalse(new DealHistoryFilterCriteria().withToDate(LocalDate.of(2026, 10, 20)).matches(itemBothNull));
        }

        @Test
        @DisplayName("Category filtering matches only selected category")
        void testCategoryFiltering() {
            DealHistoryItem item = createSampleItem("Math Textbook", "charlie", "Charlie Lee",
                    Category.BOOKS, DealType.SALE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));

            assertTrue(new DealHistoryFilterCriteria().withCategory(Category.BOOKS).matches(item));
            assertFalse(new DealHistoryFilterCriteria().withCategory(Category.ELECTRONICS).matches(item));
        }

        @Test
        @DisplayName("DealType filtering matches only selected type")
        void testDealTypeFiltering() {
            DealHistoryItem saleItem = createSampleItem("USB Cable", "charlie", "Charlie Lee",
                    Category.ELECTRONICS, DealType.SALE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
            DealHistoryItem loanItem = createSampleItem("Calculator", "diana", "Diana Ng",
                    Category.ELECTRONICS, DealType.LOAN, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));

            assertTrue(new DealHistoryFilterCriteria().withDealType(DealType.SALE).matches(saleItem));
            assertFalse(new DealHistoryFilterCriteria().withDealType(DealType.LOAN).matches(saleItem));
            assertTrue(new DealHistoryFilterCriteria().withDealType(DealType.LOAN).matches(loanItem));
        }

        @Test
        @DisplayName("Seller filtering matches username or full name case-insensitively")
        void testSellerFiltering() {
            DealHistoryItem item = createSampleItem("Organic Chemistry Notes", "charlie", "Charlie Lee",
                    Category.BOOKS, DealType.GIVEAWAY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));

            assertTrue(new DealHistoryFilterCriteria().withSeller("charlie").matches(item));
            assertTrue(new DealHistoryFilterCriteria().withSeller("CHARLIE").matches(item));
            assertTrue(new DealHistoryFilterCriteria().withSeller("Lee").matches(item));
            assertFalse(new DealHistoryFilterCriteria().withSeller("diana").matches(item));
        }

        @Test
        @DisplayName("Keyword search query matches title, seller username, or seller full name")
        void testSearchQueryMatching() {
            DealHistoryItem item = createSampleItem("Organic Chemistry Textbook", "charlie", "Charlie Lee",
                    Category.BOOKS, DealType.SALE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));

            assertTrue(new DealHistoryFilterCriteria().withSearchQuery("Organic").matches(item));
            assertTrue(new DealHistoryFilterCriteria().withSearchQuery("textbook").matches(item));
            assertTrue(new DealHistoryFilterCriteria().withSearchQuery("charlie").matches(item));
            assertTrue(new DealHistoryFilterCriteria().withSearchQuery("Lee").matches(item));
            assertFalse(new DealHistoryFilterCriteria().withSearchQuery("Physics").matches(item));
        }

        @Test
        @DisplayName("Combined multi-criteria filtering")
        void testCombinedFiltering() {
            DealHistoryItem item = createSampleItem("Algorithms Textbook", "charlie", "Charlie Lee",
                    Category.BOOKS, DealType.SALE, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12));

            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria()
                    .withFromDate(LocalDate.of(2026, 10, 1))
                    .withToDate(LocalDate.of(2026, 10, 15))
                    .withCategory(Category.BOOKS)
                    .withSeller("charlie")
                    .withDealType(DealType.SALE)
                    .withSearchQuery("Algorithms");

            assertTrue(criteria.matches(item));

            // Alter one condition -> no match
            assertFalse(criteria.withSearchQuery("Database").matches(item));
        }

        @Test
        @DisplayName("Getters, setters, equals and hashCode")
        void testGettersSettersEqualsHashCode() {
            DealHistoryFilterCriteria c1 = new DealHistoryFilterCriteria(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31),
                    Category.BOOKS,
                    "charlie",
                    DealType.SALE,
                    "math"
            );

            DealHistoryFilterCriteria c2 = new DealHistoryFilterCriteria();
            c2.setFromDate(LocalDate.of(2026, 1, 1));
            c2.setToDate(LocalDate.of(2026, 1, 31));
            c2.setCategory(Category.BOOKS);
            c2.setSellerUsername("charlie");
            c2.setDealType(DealType.SALE);
            c2.setSearchQuery("math");

            assertEquals(c1, c2);
            assertEquals(c1.hashCode(), c2.hashCode());
            assertEquals(LocalDate.of(2026, 1, 1), c2.getFromDate());
            assertEquals(LocalDate.of(2026, 1, 31), c2.getToDate());
            assertEquals(Category.BOOKS, c2.getCategory());
            assertEquals("charlie", c2.getSellerUsername());
            assertEquals(DealType.SALE, c2.getDealType());
            assertEquals("math", c2.getSearchQuery());
            assertFalse(c2.isEmpty());
        }
    }

    @Nested
    @DisplayName("DealHistorySummary Tests")
    class DealHistorySummaryTests {

        @Test
        @DisplayName("Null and empty items return zero summary")
        void testEmptySummary() {
            DealHistorySummary s1 = DealHistorySummary.fromItems(null);
            assertEquals(0, s1.getTotalCompletedDeals());
            assertEquals(0, s1.getTotalBought());
            assertEquals(0, s1.getTotalBorrowed());
            assertEquals(0, s1.getTotalReceived());
            assertEquals(0.0, s1.getTotalSpent());
            assertEquals("HK$ 0.00", s1.getFormattedTotalSpent());

            DealHistorySummary s2 = DealHistorySummary.fromItems(Collections.emptyList());
            assertEquals(0, s2.getTotalCompletedDeals());
        }

        @Test
        @DisplayName("Aggregates bought, borrowed, received and total spent correctly")
        void testAggregateSummary() {
            Deal d1 = new Deal();
            d1.setId("D1");
            d1.setType(DealType.SALE);
            d1.setOfferPrice(120.50);
            DealHistoryItem i1 = DealHistoryItem.fromDeal(d1, null, null);

            Deal d2 = new Deal();
            d2.setId("D2");
            d2.setType(DealType.SALE);
            d2.setOfferPrice(79.50);
            DealHistoryItem i2 = DealHistoryItem.fromDeal(d2, null, null);

            Deal d3 = new Deal();
            d3.setId("D3");
            d3.setType(DealType.LOAN);
            DealHistoryItem i3 = DealHistoryItem.fromDeal(d3, null, null);

            Deal d4 = new Deal();
            d4.setId("D4");
            d4.setType(DealType.GIVEAWAY);
            DealHistoryItem i4 = DealHistoryItem.fromDeal(d4, null, null);

            List<DealHistoryItem> items = Arrays.asList(i1, i2, i3, i4);
            DealHistorySummary summary = DealHistorySummary.fromItems(items);

            assertEquals(4, summary.getTotalCompletedDeals());
            assertEquals(2, summary.getTotalBought());
            assertEquals(1, summary.getTotalBorrowed());
            assertEquals(1, summary.getTotalReceived());
            assertEquals(200.0, summary.getTotalSpent(), 0.001);
            assertEquals("HK$ 200.00", summary.getFormattedTotalSpent());
        }
    }
}
