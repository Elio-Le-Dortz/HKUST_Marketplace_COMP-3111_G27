package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Buyer Browse & Filter Integration Tests (US-B03)")
class BuyerBrowseFilterTest {

    private DummyDataService service;

    @BeforeEach
    void setUp() {
        service = DummyDataService.getInstance();
        service.resetData();
    }

    @Nested
    @DisplayName("DummyDataService Integration with Live Catalog")
    class DummyDataIntegrationTests {

        @Test
        @DisplayName("getFilteredLiveListings returns all seeded live listings when criteria is empty")
        void testDefaultLiveListings() {
            List<Listing> live = service.getLiveListings();
            assertFalse(live.isEmpty(), "Should have seeded live listings");
            assertTrue(live.size() >= 4);

            List<Listing> filtered = service.getFilteredLiveListings(ListingFilterCriteria.all());
            assertEquals(live.size(), filtered.size());
        }

        @Test
        @DisplayName("Search by keyword finds Calculus Stewart textbook")
        void testSearchCalculus() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder()
                    .keyword("Stewart")
                    .build();
            List<Listing> results = service.getFilteredLiveListings(criteria);
            assertFalse(results.isEmpty());
            assertTrue(results.get(0).getTitle().contains("Stewart"));
        }

        @Test
        @DisplayName("Filter by Category BOOKS finds textbooks")
        void testFilterByCategoryBooks() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder()
                    .category(Category.BOOKS)
                    .build();
            List<Listing> results = service.getFilteredLiveListings(criteria);
            assertFalse(results.isEmpty());
            assertTrue(results.stream().allMatch(l -> l.getCategory() == Category.BOOKS));
        }

        @Test
        @DisplayName("Filter by Type GIVEAWAY finds giveaway items")
        void testFilterByTypeGiveaway() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder()
                    .type(ListingType.GIVEAWAY)
                    .build();
            List<Listing> results = service.getFilteredLiveListings(criteria);
            assertFalse(results.isEmpty());
            assertTrue(results.stream().allMatch(l -> l.getType() == ListingType.GIVEAWAY));
        }

        @Test
        @DisplayName("Filter by Type LEND finds lendable graphing calculator")
        void testFilterByTypeLend() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder()
                    .type(ListingType.LEND)
                    .build();
            List<Listing> results = service.getFilteredLiveListings(criteria);
            assertFalse(results.isEmpty());
            assertTrue(results.stream().allMatch(l -> l.getType() == ListingType.LEND));
        }

        @Test
        @DisplayName("Alternative Flow A1: Keyword with zero matches returns empty list")
        void testAlternativeFlowA1EmptyResult() {
            ListingFilterCriteria criteria = ListingFilterCriteria.builder()
                    .keyword("xyz_non_existent_query_999")
                    .build();
            List<Listing> results = service.getFilteredLiveListings(criteria);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Alternative Flow A2: When no live listings exist, returns empty list")
        void testAlternativeFlowA2NoLiveListings() {
            // Remove or update all listings to SOLD
            for (Listing l : service.getLiveListings()) {
                l.setStatus(ListingStatus.SOLD);
            }
            assertTrue(service.getLiveListings().isEmpty());

            List<Listing> results = service.getFilteredLiveListings(ListingFilterCriteria.all());
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Category alias mapping in Category.fromString")
        void testCategoryAliases() {
            assertEquals(Category.BOOKS, Category.fromString("Textbooks"));
            assertEquals(Category.BOOKS, Category.fromString("Textbook"));
            assertEquals(Category.BOOKS, Category.fromString("Book"));
            assertEquals(Category.SPORTS, Category.fromString("Sports Equipment"));
            assertEquals(Category.SPORTS, Category.fromString("Sport"));
            assertEquals(Category.OTHER, Category.fromString("Kitchenware"));
            assertEquals(Category.OTHER, Category.fromString("Others"));
            assertEquals(Category.OTHER, Category.fromString(null));
            assertEquals(Category.OTHER, Category.fromString("completely unknown category"));
        }

        @Test
        @DisplayName("Listing model availability and effective price helpers")
        void testListingHelpers() {
            Listing l = new Listing();
            assertEquals("Pending Review", l.getAvailabilityDisplay());
            assertEquals(0.0, l.getEffectivePrice());
            assertNull(l.getFirstPhoto());
            assertNull(l.getThumbnailPath());

            l.setStatus(ListingStatus.LIVE);
            assertEquals("Live", l.getAvailabilityDisplay());

            l.setType(ListingType.SELL);
            l.setPrice(120.0);
            assertEquals(120.0, l.getEffectivePrice());

            l.setStatus(ListingStatus.RESERVED);
            assertEquals("Reserved", l.getAvailabilityDisplay());

            l.setStatus(ListingStatus.SOLD);
            assertEquals("Sold", l.getAvailabilityDisplay());

            l.setStatus(ListingStatus.LENT);
            assertEquals("Lent", l.getAvailabilityDisplay());

            l.setStatus(ListingStatus.GIVEN);
            assertEquals("Given", l.getAvailabilityDisplay());

            l.setStatus(ListingStatus.REJECTED);
            assertEquals("Rejected", l.getAvailabilityDisplay());

            l.setStatus(ListingStatus.REMOVED);
            assertEquals("Removed", l.getAvailabilityDisplay());

            l.setStatus(null);
            assertEquals("Unknown", l.getAvailabilityDisplay());

            Photo p1 = new Photo("img/test.jpg");
            l.addPhoto(p1);
            assertEquals(p1, l.getFirstPhoto());
            assertEquals("img/test.jpg", l.getThumbnailPath());
        }

        @Test
        @DisplayName("getListingFilterService accessor returns non-null service")
        void testGetListingFilterService() {
            assertNotNull(service.getListingFilterService());
        }
    }

    @Nested
    @DisplayName("Concurrency & Thread Safety Verification")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent multi-threaded browse filtering while mutating catalog")
        void testConcurrentBrowseFiltering() throws InterruptedException, ExecutionException {
            int readerThreads = 8;
            int iterationsPerThread = 100;
            ExecutorService pool = Executors.newFixedThreadPool(readerThreads + 2);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicInteger errorCount = new AtomicInteger(0);

            // 1 Writer thread adding new listings
            Future<?> writerFuture = pool.submit(() -> {
                try {
                    startLatch.await();
                    int count = 200;
                    while (running.get() && count < 250) {
                        Listing l = new Listing();
                        l.setId("CL" + count++);
                        l.setTitle("Concurrent Item " + count);
                        l.setDescription("Concurrent test description");
                        l.setCategory(Category.ELECTRONICS);
                        l.setType(ListingType.SELL);
                        l.setPrice(50.0 + count);
                        l.setStatus(ListingStatus.LIVE);
                        service.addListing(l);
                        Thread.sleep(2);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            });

            // 8 Reader threads applying diverse browse filters simultaneously
            List<Future<?>> readerFutures = new java.util.ArrayList<>();
            for (int t = 0; t < readerThreads; t++) {
                final int threadId = t;
                readerFutures.add(pool.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            ListingFilterCriteria criteria;
                            int mode = (threadId + i) % 4;
                            switch (mode) {
                                case 0 -> criteria = ListingFilterCriteria.builder()
                                        .keyword("Concurrent")
                                        .sortOrder(ListingSortOrder.PRICE_LOW_TO_HIGH)
                                        .build();
                                case 1 -> criteria = ListingFilterCriteria.builder()
                                        .category(Category.ELECTRONICS)
                                        .minPrice(50.0)
                                        .build();
                                case 2 -> criteria = ListingFilterCriteria.builder()
                                        .type(ListingType.SELL)
                                        .sortOrder(ListingSortOrder.NEWEST_FIRST)
                                        .build();
                                default -> criteria = ListingFilterCriteria.all();
                            }

                            List<Listing> results = service.getFilteredLiveListings(criteria);
                            assertNotNull(results);
                            assertTrue(results.stream().allMatch(l -> l.getStatus() == ListingStatus.LIVE));
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }));
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Await reader completion
            for (Future<?> f : readerFutures) {
                f.get();
            }
            running.set(false);
            writerFuture.get();

            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(0, errorCount.get(), "No concurrent modification or race exceptions should occur");
        }
    }
}
