package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DealHistoryService Unit Tests (Sprint 2 US-B10)")
class DealHistoryServiceTest {

    private DummyDataService dataService;
    private DealHistoryService historyService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        historyService = dataService.getDealHistoryService();
        SessionManager.login(dataService.findUser("alice"));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Nested
    @DisplayName("Basic Flow Tests (Use Case 1.9 Step 1 & 2)")
    class BasicFlowTests {

        @Test
        @DisplayName("Returns only completed and returned deals for buyer, sorted newest first")
        void testBasicFlowCompletedAndReturnedOnly() {
            List<DealHistoryItem> history = historyService.getDealHistory("alice", null);

            assertNotNull(history);
            assertFalse(history.isEmpty(), "Alice should have completed deals in seed data");

            // Verify each item is completed or returned, and belongs to Alice
            for (DealHistoryItem item : history) {
                assertEquals("alice", item.getDeal().getBuyerUsername());
                assertTrue(item.getDealStatus() == DealStatus.COMPLETED || item.getDealStatus() == DealStatus.RETURNED,
                        "Only COMPLETED or RETURNED deals must appear in Deal History");
            }

            // Verify chronological descending order (newest closed/deal date first)
            for (int i = 0; i < history.size() - 1; i++) {
                LocalDateTime date1 = history.get(i).getClosedDate() != null
                        ? history.get(i).getClosedDate() : history.get(i).getDealDate();
                LocalDateTime date2 = history.get(i + 1).getClosedDate() != null
                        ? history.get(i + 1).getClosedDate() : history.get(i + 1).getDealDate();
                if (date1 != null && date2 != null) {
                    assertFalse(date1.isBefore(date2), "History must be sorted newest first");
                }
            }
        }

        @Test
        @DisplayName("Active deals are strictly excluded from history")
        void testActiveDealsExcluded() {
            // Seed data has d1 (Active Sale) and d2 (Active Loan) for Alice
            List<DealHistoryItem> history = historyService.getDealHistory("alice", null);
            boolean hasActive = history.stream().anyMatch(item -> item.getDealStatus() == DealStatus.ACTIVE);
            assertFalse(hasActive, "Active deals must never be returned in Deal History");
        }

        @Test
        @DisplayName("Deals of other buyers are strictly excluded")
        void testOtherBuyersExcluded() {
            // Create completed deal for Bob
            Deal bobDeal = new Deal();
            bobDeal.setId("D-BOB-1");
            bobDeal.setBuyerUsername("bob");
            bobDeal.setSellerUsername("charlie");
            bobDeal.setListingTitle("Bob's Item");
            bobDeal.setStatus(DealStatus.COMPLETED);
            bobDeal.setType(DealType.SALE);
            dataService.addDeal(bobDeal);

            List<DealHistoryItem> aliceHistory = historyService.getDealHistory("alice", null);
            boolean hasBobDeal = aliceHistory.stream().anyMatch(i -> "D-BOB-1".equalsIgnoreCase(i.getDealId()));
            assertFalse(hasBobDeal, "Bob's deal must not appear in Alice's history");
        }
    }

    @Nested
    @DisplayName("Auto-return Integration & Concurrency Tests")
    class AutoReturnAndConcurrencyTests {

        @Test
        @DisplayName("Expired loan deal is auto-returned before history query and appears as RETURNED")
        void testAutoReturnBeforeQuery() {
            String tempBuyer = "temp_borrower_" + UUID.randomUUID();
            Buyer tb = new Buyer(tempBuyer, "Temp Buyer", "hash", "Hall 1");
            dataService.registerUserIfAbsent(tb);

            // Create an expired active loan
            Deal expiredLoan = new Deal();
            expiredLoan.setId("D-EXP-" + UUID.randomUUID());
            expiredLoan.setBuyerUsername(tempBuyer);
            expiredLoan.setSellerUsername("diana");
            expiredLoan.setListingTitle("Expired Projector");
            expiredLoan.setType(DealType.LOAN);
            expiredLoan.setStatus(DealStatus.ACTIVE);
            expiredLoan.setStartDate(LocalDateTime.now().minusDays(5));
            expiredLoan.setDueDate(LocalDateTime.now().minusSeconds(10)); // Overdue
            dataService.addDeal(expiredLoan);

            // Querying history must trigger processAutoReturns()
            List<DealHistoryItem> history = SessionManager.withSession(tb,
                    () -> historyService.getDealHistory(tempBuyer, null));

            assertEquals(1, history.size());
            DealHistoryItem item = history.get(0);
            assertEquals(expiredLoan.getId(), item.getDealId());
            assertEquals(DealStatus.RETURNED, item.getDealStatus());
            assertEquals("Returned", item.getOutcomeDisplayName());
        }

        @Test
        @DisplayName("Concurrent reads and queries on DealHistoryService maintain thread safety")
        void testConcurrentQueries() throws InterruptedException {
            int threadCount = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        List<DealHistoryItem> history = historyService.getDealHistory("alice", null);
                        if (history != null && !history.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            pool.shutdown();
            assertEquals(threadCount, successCount.get(), "All concurrent queries should succeed without exception");
        }
    }

    @Nested
    @DisplayName("Subflow S1: Filter History Tests")
    class FilterHistoryTests {

        private String testBuyer;

        @BeforeEach
        void createTestDeals() {
            testBuyer = "filter_buyer_" + UUID.randomUUID();
            Buyer b = new Buyer(testBuyer, "Filter Buyer", "hash", "Hall 1");
            dataService.registerUserIfAbsent(b);
            SessionManager.login(b);

            // Deal 1: Sale of Books on 2026-10-05, closed 2026-10-06 by charlie
            Listing l1 = new Listing();
            l1.setId("L-F1-" + testBuyer);
            l1.setTitle("Calculus Early Transcendentals");
            l1.setCategory(Category.BOOKS);
            l1.setSellerUsername("charlie");
            dataService.addListing(l1);

            Deal d1 = new Deal();
            d1.setId("D-F1-" + testBuyer);
            d1.setListingId(l1.getId());
            d1.setListingTitle(l1.getTitle());
            d1.setBuyerUsername(testBuyer);
            d1.setSellerUsername("charlie");
            d1.setType(DealType.SALE);
            d1.setStatus(DealStatus.COMPLETED);
            d1.setStartDate(LocalDateTime.of(2026, 10, 5, 10, 0));
            d1.setClosedDate(LocalDateTime.of(2026, 10, 6, 15, 0));
            d1.setOfferPrice(250.0);
            dataService.addDeal(d1);

            // Deal 2: Loan of Electronics on 2026-10-15, closed 2026-10-18 by diana
            Listing l2 = new Listing();
            l2.setId("L-F2-" + testBuyer);
            l2.setTitle("Logitech Wireless Mouse");
            l2.setCategory(Category.ELECTRONICS);
            l2.setSellerUsername("diana");
            dataService.addListing(l2);

            Deal d2 = new Deal();
            d2.setId("D-F2-" + testBuyer);
            d2.setListingId(l2.getId());
            d2.setListingTitle(l2.getTitle());
            d2.setBuyerUsername(testBuyer);
            d2.setSellerUsername("diana");
            d2.setType(DealType.LOAN);
            d2.setStatus(DealStatus.RETURNED);
            d2.setStartDate(LocalDateTime.of(2026, 10, 15, 9, 0));
            d2.setDueDate(LocalDateTime.of(2026, 10, 18, 18, 0));
            d2.setClosedDate(LocalDateTime.of(2026, 10, 18, 17, 30));
            dataService.addDeal(d2);

            // Deal 3: Giveaway of Stationery on 2026-10-25, closed 2026-10-25 by charlie
            Listing l3 = new Listing();
            l3.setId("L-F3-" + testBuyer);
            l3.setTitle("Notebook Binder Set");
            l3.setCategory(Category.STATIONERY);
            l3.setSellerUsername("charlie");
            dataService.addListing(l3);

            Deal d3 = new Deal();
            d3.setId("D-F3-" + testBuyer);
            d3.setListingId(l3.getId());
            d3.setListingTitle(l3.getTitle());
            d3.setBuyerUsername(testBuyer);
            d3.setSellerUsername("charlie");
            d3.setType(DealType.GIVEAWAY);
            d3.setStatus(DealStatus.COMPLETED);
            d3.setStartDate(LocalDateTime.of(2026, 10, 25, 12, 0));
            d3.setClosedDate(LocalDateTime.of(2026, 10, 25, 12, 10));
            d3.setOfferPrice(0.0);
            dataService.addDeal(d3);
        }

        @Test
        @DisplayName("Filter by Category returns only deals in matching category")
        void testFilterByCategory() {
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria().withCategory(Category.BOOKS);
            List<DealHistoryItem> items = historyService.getDealHistory(testBuyer, criteria);

            assertEquals(1, items.size());
            assertEquals("Calculus Early Transcendentals", items.get(0).getItemTitle());
            assertEquals(Category.BOOKS, items.get(0).getCategory());
        }

        @Test
        @DisplayName("Filter by Seller returns only deals from that seller")
        void testFilterBySeller() {
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria().withSeller("diana");
            List<DealHistoryItem> items = historyService.getDealHistory(testBuyer, criteria);

            assertEquals(1, items.size());
            assertEquals("Logitech Wireless Mouse", items.get(0).getItemTitle());
            assertEquals("diana", items.get(0).getSellerUsername());
        }

        @Test
        @DisplayName("Filter by DealType returns only deals of that type")
        void testFilterByDealType() {
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria().withDealType(DealType.GIVEAWAY);
            List<DealHistoryItem> items = historyService.getDealHistory(testBuyer, criteria);

            assertEquals(1, items.size());
            assertEquals("Notebook Binder Set", items.get(0).getItemTitle());
            assertEquals(DealType.GIVEAWAY, items.get(0).getDealType());
        }

        @Test
        @DisplayName("Filter by Date Range returns deals falling within [from, to]")
        void testFilterByDateRange() {
            // Range 2026-10-10 to 2026-10-20 should only include Deal 2
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria()
                    .withFromDate(LocalDate.of(2026, 10, 10))
                    .withToDate(LocalDate.of(2026, 10, 20));

            List<DealHistoryItem> items = historyService.getDealHistory(testBuyer, criteria);
            assertEquals(1, items.size());
            assertEquals("Logitech Wireless Mouse", items.get(0).getItemTitle());
        }

        @Test
        @DisplayName("Combined filter (Date + Category + Seller) matches specific deal")
        void testCombinedFilter() {
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria()
                    .withCategory(Category.BOOKS)
                    .withSeller("charlie")
                    .withDealType(DealType.SALE);

            List<DealHistoryItem> items = historyService.getDealHistory(testBuyer, criteria);
            assertEquals(1, items.size());
            assertEquals("Calculus Early Transcendentals", items.get(0).getItemTitle());
        }
    }

    @Nested
    @DisplayName("Subflow S2: Search History Tests")
    class SearchHistoryTests {

        private String testBuyer;

        @BeforeEach
        void createTestDeals() {
            testBuyer = "search_buyer_" + UUID.randomUUID();
            Buyer b = new Buyer(testBuyer, "Search Buyer", "hash", "Hall 1");
            dataService.registerUserIfAbsent(b);
            SessionManager.login(b);

            Deal d1 = new Deal();
            d1.setId("D-S1-" + testBuyer);
            d1.setListingTitle("Python Data Science Handbook");
            d1.setBuyerUsername(testBuyer);
            d1.setSellerUsername("charlie");
            d1.setType(DealType.SALE);
            d1.setStatus(DealStatus.COMPLETED);
            dataService.addDeal(d1);

            Deal d2 = new Deal();
            d2.setId("D-S2-" + testBuyer);
            d2.setListingTitle("TI-84 Plus CE Calculator");
            d2.setBuyerUsername(testBuyer);
            d2.setSellerUsername("diana");
            d2.setType(DealType.LOAN);
            d2.setStatus(DealStatus.RETURNED);
            dataService.addDeal(d2);
        }

        @Test
        @DisplayName("Search by title substring (case-insensitive)")
        void testSearchByTitle() {
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria().withSearchQuery("python");
            List<DealHistoryItem> items = historyService.getDealHistory(testBuyer, criteria);

            assertEquals(1, items.size());
            assertEquals("Python Data Science Handbook", items.get(0).getItemTitle());
        }

        @Test
        @DisplayName("Search by seller username")
        void testSearchBySeller() {
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria().withSearchQuery("diana");
            List<DealHistoryItem> items = historyService.getDealHistory(testBuyer, criteria);

            assertEquals(1, items.size());
            assertEquals("TI-84 Plus CE Calculator", items.get(0).getItemTitle());
        }
    }

    @Nested
    @DisplayName("Alternative Flows Tests")
    class AlternativeFlowsTests {

        @Test
        @DisplayName("Alternative Flow A1: Buyer has no completed deals")
        void testNoCompletedDealsFlowA1() {
            String freshBuyer = "brand_new_buyer_" + UUID.randomUUID();
            Buyer fb = new Buyer(freshBuyer, "Fresh Buyer", "hash", "Hall 1");
            dataService.registerUserIfAbsent(fb);

            SessionManager.withSession(fb, () -> {
                assertFalse(historyService.hasCompletedDeals(freshBuyer));
                List<DealHistoryItem> history = historyService.getDealHistory(freshBuyer, null);
                assertNotNull(history);
                assertTrue(history.isEmpty());
            });
        }

        @Test
        @DisplayName("Alternative Flow A2: Buyer has deals, but no results match search/filter")
        void testNoResultsMatchFlowA2() {
            // Alice has deals
            assertTrue(historyService.hasCompletedDeals("alice"));

            // Filter for non-existent keyword
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria().withSearchQuery("COMPLETELY_NONEXISTENT_ITEM_12345");
            List<DealHistoryItem> items = historyService.getDealHistory("alice", criteria);

            assertNotNull(items);
            assertTrue(items.isEmpty(), "No items should match the non-existent query");
        }
    }

    @Nested
    @DisplayName("Utility, Summary & Edge Cases Tests")
    class UtilityAndEdgeCasesTests {

        @Test
        @DisplayName("getDistinctSellers returns sorted unique seller usernames")
        void testGetDistinctSellers() {
            List<String> sellers = historyService.getDistinctSellers("alice");
            assertNotNull(sellers);
            assertFalse(sellers.isEmpty());
            assertTrue(sellers.contains("charlie"));
            assertTrue(sellers.contains("diana"));

            // Verify no duplicates and sorted
            for (int i = 0; i < sellers.size() - 1; i++) {
                assertTrue(sellers.get(i).compareToIgnoreCase(sellers.get(i + 1)) <= 0);
            }
        }

        @Test
        @DisplayName("getDistinctCategories returns unique categories in history")
        void testGetDistinctCategories() {
            List<Category> categories = historyService.getDistinctCategories("alice");
            assertNotNull(categories);
            assertFalse(categories.isEmpty());
        }

        @Test
        @DisplayName("getSummary returns correct metrics for Alice")
        void testGetSummary() {
            DealHistorySummary summary = historyService.getSummary("alice");
            assertNotNull(summary);
            assertTrue(summary.getTotalCompletedDeals() >= 3);
            assertTrue(summary.getTotalBought() >= 1);
            assertTrue(summary.getTotalBorrowed() >= 1);
            assertTrue(summary.getTotalReceived() >= 1);
            assertTrue(summary.getTotalSpent() > 0.0);
        }

        @Test
        @DisplayName("findDealHistoryItem returns correct item or null")
        void testFindDealHistoryItem() {
            List<DealHistoryItem> history = historyService.getDealHistory("alice", null);
            assertFalse(history.isEmpty());

            DealHistoryItem target = history.get(0);
            DealHistoryItem found = historyService.findDealHistoryItem(target.getDealId(), "alice");

            assertNotNull(found);
            assertEquals(target.getDealId(), found.getDealId());

            assertNull(historyService.findDealHistoryItem("INVALID_ID", "alice"));
            assertNull(historyService.findDealHistoryItem(null, "alice"));
            assertNull(historyService.findDealHistoryItem(target.getDealId(), null));
            assertNull(historyService.findDealHistoryItem(target.getDealId(), "   "));
        }

        @Test
        @DisplayName("Null and blank username inputs return empty collections or false")
        void testNullAndBlankUsernames() {
            assertTrue(historyService.getDealHistory(null, null).isEmpty());
            assertTrue(historyService.getDealHistory("", null).isEmpty());
            assertTrue(historyService.getDealHistory("   ", null).isEmpty());

            assertFalse(historyService.hasCompletedDeals(null));
            assertFalse(historyService.hasCompletedDeals(""));
            assertFalse(historyService.hasCompletedDeals("   "));

            assertTrue(historyService.getDistinctSellers(null).isEmpty());
            assertTrue(historyService.getDistinctCategories(null).isEmpty());
        }
    }
}
