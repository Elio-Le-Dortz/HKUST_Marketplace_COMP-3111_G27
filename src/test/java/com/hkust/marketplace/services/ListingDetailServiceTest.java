package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListingDetailService Tests (US-B04)")
class ListingDetailServiceTest {

    private DummyDataService dataService;
    private ListingDetailService detailService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        detailService = new ListingDetailService(dataService);
        SessionManager.login(dataService.findUser("alice"));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Nested
    @DisplayName("Basic Flow: View Listing Details")
    class BasicFlowTests {

        @Test
        @DisplayName("Successfully view live listing: increments view count and returns full details")
        void testViewLiveListingSuccess() {
            List<Listing> liveListings = dataService.getLiveListings();
            assertFalse(liveListings.isEmpty(), "Seed data should have live listings");

            Listing target = liveListings.get(0);
            String id = target.getId();
            int initialViews = target.getViewCount();

            ListingViewResult result = detailService.viewListing(id, true);

            assertTrue(result.isSuccess());
            assertEquals(ListingViewStatus.SUCCESS, result.getStatus());
            assertNotNull(result.getListing());
            assertEquals(id, result.getListing().getId());
            assertEquals(initialViews + 1, result.getListing().getViewCount(), "View count should increment by 1");

            SellerRatingInfo ratingInfo = result.getSellerRating();
            assertNotNull(ratingInfo);
            assertEquals(target.getSellerUsername(), ratingInfo.getSellerUsername());
        }

        @Test
        @DisplayName("Sequential views continuously increment view count")
        void testSequentialViewsIncrementCount() {
            Listing listing = new Listing();
            listing.setTitle("Test Item");
            listing.setStatus(ListingStatus.LIVE);
            listing.setSellerUsername("charlie");
            listing.setViewCount(0);
            dataService.addListing(listing);

            for (int i = 1; i <= 5; i++) {
                ListingViewResult res = detailService.viewListing(listing.getId(), true);
                assertTrue(res.isSuccess());
                assertEquals(i, res.getListing().getViewCount());
            }
        }

        @Test
        @DisplayName("Default constructor uses DummyDataService singleton")
        void testDefaultConstructor() {
            ListingDetailService defaultService = new ListingDetailService();
            assertNotNull(defaultService);
            List<Listing> live = dataService.getLiveListings();
            if (!live.isEmpty()) {
                ListingViewResult res = defaultService.viewListing(live.get(0).getId(), true);
                assertTrue(res.isSuccess());
            }
        }
    }

    @Nested
    @DisplayName("Alternative Flow A1: Listing No Longer Live")
    class AlternativeFlowA1Tests {

        @Test
        @DisplayName("Attempting to view Reserved listing from browse returns NOT_LIVE")
        void testViewReservedListingNotLive() {
            Listing listing = new Listing();
            listing.setTitle("Reserved Bike");
            listing.setStatus(ListingStatus.RESERVED);
            listing.setSellerUsername("charlie");
            dataService.addListing(listing);

            ListingViewResult result = detailService.viewListing(listing.getId(), true);

            assertFalse(result.isSuccess());
            assertEquals(ListingViewStatus.NOT_LIVE, result.getStatus());
            assertEquals("This listing is no longer available.", result.getMessage());
            assertNull(result.getListing());
        }

        @Test
        @DisplayName("Attempting to view Sold listing from browse returns NOT_LIVE")
        void testViewSoldListingNotLive() {
            Listing listing = new Listing();
            listing.setTitle("Sold Laptop");
            listing.setStatus(ListingStatus.SOLD);
            dataService.addListing(listing);

            ListingViewResult result = detailService.viewListing(listing.getId(), true);

            assertFalse(result.isSuccess());
            assertEquals(ListingViewStatus.NOT_LIVE, result.getStatus());
        }

        @Test
        @DisplayName("Attempting to view Removed listing returns NOT_LIVE")
        void testViewRemovedListingNotLive() {
            Listing listing = new Listing();
            listing.setTitle("Removed Item");
            listing.setStatus(ListingStatus.REMOVED);
            dataService.addListing(listing);

            ListingViewResult result = detailService.viewListing(listing.getId(), true);

            assertFalse(result.isSuccess());
            assertEquals(ListingViewStatus.NOT_LIVE, result.getStatus());
        }

        @Test
        @DisplayName("Viewing non-live listing when requireLive=false succeeds (e.g. from My Deals)")
        void testViewNonLiveListingWhenNotRequired() {
            Listing listing = new Listing();
            listing.setTitle("Past Deal Item");
            listing.setStatus(ListingStatus.SOLD);
            listing.setSellerUsername("charlie");
            listing.setViewCount(5);
            dataService.addListing(listing);

            ListingViewResult result = detailService.viewListing(listing.getId(), false);

            assertTrue(result.isSuccess());
            assertEquals(ListingViewStatus.SUCCESS, result.getStatus());
            assertEquals(6, result.getListing().getViewCount());
        }

        @Test
        @DisplayName("Reopening active and completed deal listings (Reserved, Lent, Given, Sold) succeeds with requireLive=false [F08]")
        void testViewNonLiveStatusesFromMyDealsAndHistory() {
            for (ListingStatus status : List.of(ListingStatus.RESERVED, ListingStatus.LENT, ListingStatus.GIVEN, ListingStatus.SOLD)) {
                Listing item = new Listing();
                item.setTitle("Item in status " + status);
                item.setStatus(status);
                item.setSellerUsername("charlie");
                dataService.addListing(item);

                // Browsing view with requireLive=true fails with NOT_LIVE
                ListingViewResult liveResult = detailService.viewListing(item.getId(), true);
                assertFalse(liveResult.isSuccess(), "Viewing non-live item with requireLive=true must fail");
                assertEquals(ListingViewStatus.NOT_LIVE, liveResult.getStatus());

                // Deals/History view with requireLive=false succeeds
                ListingViewResult dealResult = detailService.viewListing(item.getId(), false);
                assertTrue(dealResult.isSuccess(), "Viewing non-live item from My Deals/History with requireLive=false must succeed for " + status);
                assertEquals(ListingViewStatus.SUCCESS, dealResult.getStatus());
                assertEquals(item.getId(), dealResult.getListing().getId());
            }
        }

        @Test
        @DisplayName("Viewing non-existent listing ID returns NOT_FOUND")
        void testViewNonExistentListing() {
            ListingViewResult result = detailService.viewListing("NON_EXISTENT_999", true);

            assertFalse(result.isSuccess());
            assertEquals(ListingViewStatus.NOT_FOUND, result.getStatus());
            assertTrue(result.getMessage().contains("NON_EXISTENT_999"));
        }

        @Test
        @DisplayName("Viewing with null or empty ID returns NOT_FOUND")
        void testViewNullOrEmptyId() {
            ListingViewResult nullRes = detailService.viewListing(null, true);
            assertFalse(nullRes.isSuccess());
            assertEquals(ListingViewStatus.NOT_FOUND, nullRes.getStatus());

            ListingViewResult emptyRes = detailService.viewListing("   ", true);
            assertFalse(emptyRes.isSuccess());
            assertEquals(ListingViewStatus.NOT_FOUND, emptyRes.getStatus());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A2 & Rating Calculation: Seller Ratings")
    class SellerRatingTests {

        @Test
        @DisplayName("Seller with no ratings returns 'No ratings yet'")
        void testSellerWithNoRatings() {
            Seller newSeller = new Seller("newbie_seller", "New Seller", "HashedPass1!");
            newSeller.setRatingCount(0);
            newSeller.setAverageRating(0.0);
            dataService.registerUser(newSeller);

            SellerRatingInfo ratingInfo = detailService.getSellerRatingInfo("newbie_seller");

            assertNotNull(ratingInfo);
            assertFalse(ratingInfo.hasRatings());
            assertEquals(0.0, ratingInfo.getAverageRating());
            assertEquals(0, ratingInfo.getRatingCount());
            assertEquals("No ratings yet", ratingInfo.getDisplayText());
            assertEquals("N/A", ratingInfo.getFormattedScore());
        }

        @Test
        @DisplayName("Seller with individual reviews calculates average rating accurately")
        void testSellerWithCalculatedReviews() {
            Seller seller = new Seller("rated_seller", "Rated Seller", "HashedPass1!");
            dataService.registerUser(seller);

            Review r1 = new Review();
            r1.setSellerUsername("rated_seller");
            r1.setRating(5);
            dataService.addReview(r1);

            Review r2 = new Review();
            r2.setSellerUsername("rated_seller");
            r2.setRating(4);
            dataService.addReview(r2);

            SellerRatingInfo ratingInfo = detailService.getSellerRatingInfo("rated_seller");

            assertTrue(ratingInfo.hasRatings());
            assertEquals(4.5, ratingInfo.getAverageRating(), 0.01);
            assertEquals(2, ratingInfo.getRatingCount());
            assertEquals("★ 4.5 / 5.0 (2 reviews)", ratingInfo.getDisplayText());
            assertEquals("4.5", ratingInfo.getFormattedScore());
        }

        @Test
        @DisplayName("Hidden reviews are excluded from seller rating calculation")
        void testHiddenReviewsExcluded() {
            Seller seller = new Seller("mod_seller", "Moderated Seller", "HashedPass1!");
            dataService.registerUser(seller);

            Review visibleReview = new Review();
            visibleReview.setSellerUsername("mod_seller");
            visibleReview.setRating(5);
            visibleReview.setHidden(false);
            dataService.addReview(visibleReview);

            Review hiddenReview = new Review();
            hiddenReview.setSellerUsername("mod_seller");
            hiddenReview.setRating(1);
            hiddenReview.setHidden(true); // Should be excluded!
            dataService.addReview(hiddenReview);

            SellerRatingInfo ratingInfo = detailService.getSellerRatingInfo("mod_seller");

            assertTrue(ratingInfo.hasRatings());
            assertEquals(5.0, ratingInfo.getAverageRating(), 0.01);
            assertEquals(1, ratingInfo.getRatingCount(), "Hidden review must be omitted from rating count");
        }

        @Test
        @DisplayName("Reviews with invalid ratings (<1 or >5) are filtered out")
        void testInvalidRatingValuesFiltered() {
            Seller seller = new Seller("edge_seller", "Edge Seller", "HashedPass1!");
            dataService.registerUser(seller);

            Review rZero = new Review();
            rZero.setSellerUsername("edge_seller");
            rZero.setRating(0); // < 1
            dataService.addReview(rZero);

            Review rSix = new Review();
            rSix.setSellerUsername("edge_seller");
            rSix.setRating(6); // > 5
            dataService.addReview(rSix);

            Review rValid = new Review();
            rValid.setSellerUsername("edge_seller");
            rValid.setRating(4);
            dataService.addReview(rValid);

            SellerRatingInfo ratingInfo = detailService.getSellerRatingInfo("edge_seller");
            assertTrue(ratingInfo.hasRatings());
            assertEquals(4.0, ratingInfo.getAverageRating(), 0.01);
            assertEquals(1, ratingInfo.getRatingCount());
        }

        @Test
        @DisplayName("Non-existent seller user or user with null full name")
        void testNonExistentOrNullFullNameSeller() {
            SellerRatingInfo notFound = detailService.getSellerRatingInfo("ghost_user");
            assertFalse(notFound.hasRatings());
            assertEquals("ghost_user", notFound.getSellerFullName());

            Buyer buyer = new Buyer("just_a_buyer", null, "Pass1234!");
            dataService.registerUser(buyer);
            SellerRatingInfo buyerInfo = detailService.getSellerRatingInfo("just_a_buyer");
            assertFalse(buyerInfo.hasRatings());
            assertEquals("just_a_buyer", buyerInfo.getSellerFullName());
        }

        @Test
        @DisplayName("Fallback to seeded Seller ratingCount if no review entities exist")
        void testFallbackToSeededSellerRating() {
            Seller seller = new Seller("seeded_seller", "Seeded Seller", "HashedPass1!");
            seller.setRatingCount(10);
            seller.setAverageRating(4.8);
            dataService.registerUser(seller);

            SellerRatingInfo ratingInfo = detailService.getSellerRatingInfo("seeded_seller");

            assertTrue(ratingInfo.hasRatings());
            assertEquals(4.8, ratingInfo.getAverageRating(), 0.01);
            assertEquals(10, ratingInfo.getRatingCount());
            assertEquals("★ 4.8 / 5.0 (10 reviews)", ratingInfo.getDisplayText());
        }

        @Test
        @DisplayName("Null or empty username returns noRatings info")
        void testNullOrEmptyUsername() {
            SellerRatingInfo nullInfo = detailService.getSellerRatingInfo(null);
            assertFalse(nullInfo.hasRatings());
            assertEquals("No ratings yet", nullInfo.getDisplayText());

            SellerRatingInfo emptyInfo = detailService.getSellerRatingInfo("   ");
            assertFalse(emptyInfo.hasRatings());
        }

        @Test
        @DisplayName("Single review displays singular '1 review' in display text")
        void testSingleReviewSingularText() {
            Seller seller = new Seller("one_review_seller", "One Review", "HashedPass1!");
            dataService.registerUser(seller);

            Review r = new Review();
            r.setSellerUsername("one_review_seller");
            r.setRating(5);
            dataService.addReview(r);

            SellerRatingInfo ratingInfo = detailService.getSellerRatingInfo("one_review_seller");
            assertEquals("★ 5.0 / 5.0 (1 review)", ratingInfo.getDisplayText());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A3: Report Listing")
    class AlternativeFlowA3Tests {

        @Test
        @DisplayName("Successfully report listing: persists report and notifies admins")
        void testSubmitReportSuccess() {
            List<Listing> live = dataService.getLiveListings();
            assertFalse(live.isEmpty());
            Listing listing = live.get(0);

            int initialReportsCount = dataService.getAllReports().size();
            int initialAdminNotifsCount = dataService.getNotificationsFor("admin").size();

            ReportResult result = detailService.submitReport(
                    listing.getId(),
                    "alice",
                    "Suspected counterfeit item not matching description."
            );

            assertTrue(result.isSuccess());
            assertNotNull(result.getReport());
            assertEquals(listing.getId(), result.getReport().getListingId());
            assertEquals(listing.getTitle(), result.getReport().getListingTitle());
            assertEquals("alice", result.getReport().getBuyerUsername());
            assertEquals("Suspected counterfeit item not matching description.", result.getReport().getReason());
            assertEquals(ReportStatus.OPEN, result.getReport().getStatus());
            assertNotNull(result.getReport().getDate());

            // Check persistence
            assertEquals(initialReportsCount + 1, dataService.getAllReports().size());

            // Check admin notification
            List<Notification> adminNotifs = dataService.getNotificationsFor("admin");
            assertEquals(initialAdminNotifsCount + 1, adminNotifs.size());
            Notification latestNotif = adminNotifs.get(0);
            assertEquals("Reports", latestNotif.getCategory());
            assertTrue(latestNotif.getMessage().contains("alice"));
            assertTrue(latestNotif.getMessage().contains(listing.getTitle()));
            assertFalse(latestNotif.isRead());
        }

        @Test
        @DisplayName("Report notification defaults to 'admin' when no active admin accounts exist")
        void testReportAdminNotificationFallback() {
            User admin = dataService.findUser("admin");
            assertNotNull(admin);
            admin.setAccountStatus(AccountStatus.DELETED);

            Listing listing = dataService.getLiveListings().get(0);
            ReportResult res = detailService.submitReport(listing.getId(), "alice", "Listing has spam");
            assertTrue(res.isSuccess());

            List<Notification> adminNotifs = dataService.getNotificationsFor("admin");
            assertFalse(adminNotifs.isEmpty());
            assertTrue(adminNotifs.get(0).getMessage().contains("alice"));
        }

        @Test
        @DisplayName("Report with multiple admins delivers notification to each admin")
        void testReportNotifiesMultipleAdmins() {
            Admin admin2 = new Admin("admin2", "Second Admin", "Pass1234!");
            dataService.registerUserIfAbsent(admin2);

            Listing listing = dataService.getLiveListings().get(0);
            SessionManager.login(dataService.findUser("bob"));
            ReportResult result = detailService.submitReport(listing.getId(), "bob", "Inappropriate images");
            assertTrue(result.isSuccess());

            List<Notification> admin1Notifs = dataService.getNotificationsFor("admin");
            List<Notification> admin2Notifs = dataService.getNotificationsFor("admin2");

            assertFalse(admin1Notifs.isEmpty());
            assertFalse(admin2Notifs.isEmpty());
        }

        @Test
        @DisplayName("Report submission requires active buyer session matching buyerUsername")
        void testReportRbacEnforcement() {
            Listing listing = dataService.getLiveListings().get(0);

            // Mismatched username
            assertThrows(SecurityException.class, () ->
                    detailService.submitReport(listing.getId(), "bob", "Reason"));

            // Logged out
            SessionManager.logout();
            assertThrows(SecurityException.class, () ->
                    detailService.submitReport(listing.getId(), "alice", "Reason"));

            // Non-buyer
            SessionManager.login(dataService.findUser("admin"));
            assertThrows(SecurityException.class, () ->
                    detailService.submitReport(listing.getId(), "alice", "Reason"));
        }

        @Test
        @DisplayName("Report with empty, null, or blank reason fails")
        void testReportInvalidReason() {
            Listing listing = dataService.getLiveListings().get(0);

            ReportResult nullRes = detailService.submitReport(listing.getId(), "alice", null);
            assertFalse(nullRes.isSuccess());
            assertEquals("Please provide a reason for the report.", nullRes.getErrorMessage());

            ReportResult emptyRes = detailService.submitReport(listing.getId(), "alice", "");
            assertFalse(emptyRes.isSuccess());
            assertEquals("Please provide a reason for the report.", emptyRes.getErrorMessage());

            ReportResult blankRes = detailService.submitReport(listing.getId(), "alice", "    \n\t ");
            assertFalse(blankRes.isSuccess());
            assertEquals("Please provide a reason for the report.", blankRes.getErrorMessage());
        }

        @Test
        @DisplayName("Report with null or blank buyer username fails")
        void testReportInvalidBuyerUsername() {
            Listing listing = dataService.getLiveListings().get(0);

            ReportResult nullBuyer = detailService.submitReport(listing.getId(), null, "Spam item");
            assertFalse(nullBuyer.isSuccess());
            assertEquals("Buyer username cannot be null or empty.", nullBuyer.getErrorMessage());

            ReportResult blankBuyer = detailService.submitReport(listing.getId(), "   ", "Spam item");
            assertFalse(blankBuyer.isSuccess());
            assertEquals("Buyer username cannot be null or empty.", blankBuyer.getErrorMessage());
        }

        @Test
        @DisplayName("Report with non-existent or null listing ID fails")
        void testReportInvalidListingId() {
            ReportResult nullId = detailService.submitReport(null, "alice", "Spam item");
            assertFalse(nullId.isSuccess());
            assertEquals("Listing ID cannot be null or empty.", nullId.getErrorMessage());

            ReportResult nonExistent = detailService.submitReport("NON_EXISTENT_ID", "alice", "Spam item");
            assertFalse(nonExistent.isSuccess());
            assertTrue(nonExistent.getErrorMessage().contains("NON_EXISTENT_ID"));
        }
    }

    @Nested
    @DisplayName("Concurrency & Thread-Safety Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent views on the same listing atomically increment view count")
        void testConcurrentListingViews() throws InterruptedException {
            Listing listing = new Listing();
            listing.setTitle("High Demand Textbook");
            listing.setStatus(ListingStatus.LIVE);
            listing.setSellerUsername("charlie");
            listing.setViewCount(0);
            dataService.addListing(listing);

            int threadCount = 40;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        ListingViewResult res = detailService.viewListing(listing.getId(), true);
                        if (res.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All threads should complete within timeout");
            assertEquals(threadCount, successCount.get());
            assertEquals(threadCount, listing.getViewCount(), "View count must match exact number of concurrent views");
        }

        @Test
        @DisplayName("Concurrent report submissions are all persisted without race conditions")
        void testConcurrentReportSubmissions() throws InterruptedException {
            Listing listing = dataService.getLiveListings().get(0);
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(8);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                Buyer b = new Buyer("buyer_" + idx, "Buyer " + idx, "h");
                dataService.registerUserIfAbsent(b);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ReportResult res = SessionManager.withSession(b, () ->
                                detailService.submitReport(
                                        listing.getId(),
                                        "buyer_" + idx,
                                        "Concurrent report reason #" + idx
                                )
                        );
                        if (res.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed);
            assertEquals(threadCount, successCount.get());
            assertTrue(dataService.getAllReports().size() >= threadCount);
        }
    }
}
