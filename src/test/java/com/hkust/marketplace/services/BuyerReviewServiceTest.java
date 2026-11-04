package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-B11: BuyerReviewService Unit & Concurrency Tests")
class BuyerReviewServiceTest {

    private DummyDataService dataService;
    private BuyerReviewService reviewService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        reviewService = new BuyerReviewService(dataService);
    }

    @Nested
    @DisplayName("Basic Flow Tests (Use Case 1.10 Steps 1-9)")
    class BasicFlowTests {

        @Test
        @DisplayName("Submit 5-star rating with comment for completed deal updates deal, persists review, and notifies seller")
        void testSubmitRatingHappyPath() {
            // Find or setup completed unrated deal for alice and charlie
            Deal deal = new Deal();
            deal.setId("D_TEST_1");
            deal.setListingId("L100");
            deal.setListingTitle("Calculus Textbook");
            deal.setBuyerUsername("alice");
            deal.setSellerUsername("charlie");
            deal.setType(DealType.SALE);
            deal.setStatus(DealStatus.COMPLETED);
            deal.setRated(false);
            dataService.addDeal(deal);

            int prevReviews = dataService.getAllReviews().size();
            int prevSellerNotifs = dataService.getNotificationsFor("charlie").size();

            RateSellerRequest req = RateSellerRequest.of("D_TEST_1", "alice", 5, "Excellent seller, very smooth transaction!");
            RateSellerResult result = reviewService.submitRating(req);

            assertTrue(result.isSuccess());
            assertEquals(RateSellerStatus.SUCCESS, result.getStatus());
            assertTrue(result.getMessage().contains("5-star"));
            assertNotNull(result.getReview());
            assertEquals(5, result.getReview().getRating());
            assertEquals("Excellent seller, very smooth transaction!", result.getReview().getComment());
            assertEquals("alice", result.getReview().getBuyerUsername());
            assertEquals("charlie", result.getReview().getSellerUsername());
            assertEquals("D_TEST_1", result.getReview().getDealId());
            assertEquals("L100", result.getReview().getListingId());
            assertEquals("Calculus Textbook", result.getReview().getListingTitle());

            // Check deal state
            assertTrue(deal.isRated());

            // Check review count incremented
            assertEquals(prevReviews + 1, dataService.getAllReviews().size());

            // Check notification dispatched to seller
            List<Notification> sellerNotifs = dataService.getNotificationsFor("charlie");
            assertEquals(prevSellerNotifs + 1, sellerNotifs.size());
            Notification latest = sellerNotifs.get(0); // newest first
            assertEquals("New Review Received", latest.getTitle());
            assertEquals("Reviews", latest.getCategory());
            assertTrue(latest.getMessage().contains("alice"));
            assertTrue(latest.getMessage().contains("5-star"));

            // Check seller rating updated
            SellerRatingInfo info = result.getSellerRatingInfo();
            assertNotNull(info);
            assertTrue(info.hasRatings());
            assertEquals("charlie", info.getSellerUsername());

            User sellerUser = dataService.findUser("charlie");
            assertTrue(sellerUser instanceof Seller);
            Seller seller = (Seller) sellerUser;
            assertEquals(info.getAverageRating(), seller.getAverageRating());
            assertEquals(info.getRatingCount(), seller.getRatingCount());
        }

        @Test
        @DisplayName("Submit 1-star rating without comment for returned deal")
        void testSubmitRatingReturnedDealNoComment() {
            Deal deal = new Deal();
            deal.setId("D_TEST_RETURNED");
            deal.setListingId("L105");
            deal.setListingTitle("Scientific Calculator");
            deal.setBuyerUsername("bob");
            deal.setSellerUsername("diana");
            deal.setType(DealType.LOAN);
            deal.setStatus(DealStatus.RETURNED);
            deal.setRated(false);
            dataService.addDeal(deal);

            RateSellerResult result = reviewService.submitRating("D_TEST_RETURNED", "bob", 1, null);

            assertTrue(result.isSuccess());
            assertEquals(RateSellerStatus.SUCCESS, result.getStatus());
            assertEquals(1, result.getReview().getRating());
            assertEquals("", result.getReview().getComment());
            assertFalse(result.getReview().hasComment());
            assertTrue(deal.isRated());

            // Diana had no prior individual reviews, so she should now have 1 review with average 1.0
            Seller seller = (Seller) dataService.findUser("diana");
            assertEquals(1.0, seller.getAverageRating());
            assertEquals(1, seller.getRatingCount());
        }

        @Test
        @DisplayName("Recalculation correctly calculates average across multiple reviews")
        void testRecalculateAverageRatingMultipleReviews() {
            Seller testSeller = new Seller("test_seller", "Test Seller", "Password123!");
            dataService.registerUser(testSeller);

            Deal d1 = new Deal();
            d1.setId("D_S1");
            d1.setListingTitle("Item 1");
            d1.setBuyerUsername("alice");
            d1.setSellerUsername("test_seller");
            d1.setStatus(DealStatus.COMPLETED);
            dataService.addDeal(d1);

            Deal d2 = new Deal();
            d2.setId("D_S2");
            d2.setListingTitle("Item 2");
            d2.setBuyerUsername("bob");
            d2.setSellerUsername("test_seller");
            d2.setStatus(DealStatus.COMPLETED);
            dataService.addDeal(d2);

            // Alice rates 5 stars
            RateSellerResult r1 = reviewService.submitRating("D_S1", "alice", 5, "Super");
            assertTrue(r1.isSuccess());
            assertEquals(5.0, testSeller.getAverageRating());
            assertEquals(1, testSeller.getRatingCount());

            // Bob rates 3 stars -> (5 + 3) / 2 = 4.0
            RateSellerResult r2 = reviewService.submitRating("D_S2", "bob", 3, "Average");
            assertTrue(r2.isSuccess());
            assertEquals(4.0, testSeller.getAverageRating());
            assertEquals(2, testSeller.getRatingCount());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A1: No Star Rating Selected / Invalid Rating")
    class AlternativeFlowA1Tests {

        @Test
        @DisplayName("Null rating returns NO_STAR_RATING")
        void testNullRating() {
            Deal deal = createSampleCompletedDeal("D_A1_1", "alice", "charlie");
            RateSellerRequest req = RateSellerRequest.of("D_A1_1", "alice", null, "No rating provided");
            RateSellerResult res = reviewService.submitRating(req);

            assertFalse(res.isSuccess());
            assertEquals(RateSellerStatus.NO_STAR_RATING, res.getStatus());
            assertTrue(res.getMessage().contains("star rating between 1 and 5 is required"));
            assertFalse(deal.isRated());
        }

        @Test
        @DisplayName("Zero or negative rating returns NO_STAR_RATING")
        void testZeroOrNegativeRating() {
            Deal deal = createSampleCompletedDeal("D_A1_2", "alice", "charlie");

            RateSellerResult resZero = reviewService.submitRating("D_A1_2", "alice", 0, "Bad");
            assertFalse(resZero.isSuccess());
            assertEquals(RateSellerStatus.NO_STAR_RATING, resZero.getStatus());

            RateSellerResult resNeg = reviewService.submitRating("D_A1_2", "alice", -2, "Bad");
            assertFalse(resNeg.isSuccess());
            assertEquals(RateSellerStatus.NO_STAR_RATING, resNeg.getStatus());
            assertFalse(deal.isRated());
        }

        @Test
        @DisplayName("Rating greater than 5 returns NO_STAR_RATING")
        void testRatingGreaterThanFive() {
            Deal deal = createSampleCompletedDeal("D_A1_3", "alice", "charlie");

            RateSellerResult resSix = reviewService.submitRating("D_A1_3", "alice", 6, "Too good");
            assertFalse(resSix.isSuccess());
            assertEquals(RateSellerStatus.NO_STAR_RATING, resSix.getStatus());
            assertFalse(deal.isRated());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A2: Already Rated This Deal")
    class AlternativeFlowA2Tests {

        @Test
        @DisplayName("Attempting to rate an already rated deal returns ALREADY_RATED")
        void testAlreadyRated() {
            Deal deal = createSampleCompletedDeal("D_A2_1", "alice", "charlie");

            RateSellerResult first = reviewService.submitRating("D_A2_1", "alice", 5, "First review");
            assertTrue(first.isSuccess());
            assertTrue(deal.isRated());

            int countBeforeDuplicate = dataService.getAllReviews().size();

            // Attempt second rating on same deal
            RateSellerResult second = reviewService.submitRating("D_A2_1", "alice", 4, "Second review attempt");
            assertFalse(second.isSuccess());
            assertEquals(RateSellerStatus.ALREADY_RATED, second.getStatus());
            assertTrue(second.getMessage().contains("already been reviewed"));
            assertEquals(deal, second.getDeal());

            // Ensure no duplicate review added
            assertEquals(countBeforeDuplicate, dataService.getAllReviews().size());
        }

        @Test
        @DisplayName("Deal with rated flag pre-set returns ALREADY_RATED")
        void testDealFlagPreSet() {
            Deal deal = createSampleCompletedDeal("D_A2_2", "alice", "charlie");
            deal.setRated(true);

            RateSellerResult res = reviewService.submitRating("D_A2_2", "alice", 5, "Try rating");
            assertFalse(res.isSuccess());
            assertEquals(RateSellerStatus.ALREADY_RATED, res.getStatus());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A3: Skip Rating")
    class AlternativeFlowA3Tests {

        @Test
        @DisplayName("Skip rating returns SKIPPED and does not store review")
        void testSkipRating() {
            Deal deal = createSampleCompletedDeal("D_A3_1", "alice", "charlie");
            int prevReviews = dataService.getAllReviews().size();

            RateSellerResult res = reviewService.skipRating("D_A3_1", "alice");
            assertFalse(res.isSuccess());
            assertEquals(RateSellerStatus.SKIPPED, res.getStatus());
            assertTrue(res.getMessage().contains("skipped"));
            assertFalse(deal.isRated());
            assertEquals(prevReviews, dataService.getAllReviews().size());
        }

        @Test
        @DisplayName("Skip rating validations: deal not found and unauthorized")
        void testSkipRatingValidation() {
            RateSellerResult nullInput = reviewService.skipRating(null, "alice");
            assertEquals(RateSellerStatus.INVALID_INPUT, nullInput.getStatus());

            RateSellerResult notFound = reviewService.skipRating("GHOST_DEAL", "alice");
            assertEquals(RateSellerStatus.DEAL_NOT_FOUND, notFound.getStatus());

            createSampleCompletedDeal("D_A3_2", "alice", "charlie");
            RateSellerResult unauth = reviewService.skipRating("D_A3_2", "bob");
            assertEquals(RateSellerStatus.UNAUTHORIZED, unauth.getStatus());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A4: Cancel Rating")
    class AlternativeFlowA4Tests {

        @Test
        @DisplayName("Cancel rating returns CANCELLED and does not modify deal")
        void testCancelRating() {
            Deal deal = createSampleCompletedDeal("D_A4_1", "alice", "charlie");
            int prevReviews = dataService.getAllReviews().size();

            RateSellerResult res = reviewService.cancelRating("D_A4_1", "alice");
            assertFalse(res.isSuccess());
            assertEquals(RateSellerStatus.CANCELLED, res.getStatus());
            assertTrue(res.getMessage().contains("cancelled"));
            assertFalse(deal.isRated());
            assertEquals(prevReviews, dataService.getAllReviews().size());
        }

        @Test
        @DisplayName("Cancel rating validations: invalid input, not found, unauthorized")
        void testCancelRatingValidation() {
            RateSellerResult nullDeal = reviewService.cancelRating("", "alice");
            assertEquals(RateSellerStatus.INVALID_INPUT, nullDeal.getStatus());

            RateSellerResult notFound = reviewService.cancelRating("UNKNOWN_D", "alice");
            assertEquals(RateSellerStatus.DEAL_NOT_FOUND, notFound.getStatus());

            createSampleCompletedDeal("D_A4_2", "alice", "charlie");
            RateSellerResult unauth = reviewService.cancelRating("D_A4_2", "bob");
            assertEquals(RateSellerStatus.UNAUTHORIZED, unauth.getStatus());
        }
    }

    @Nested
    @DisplayName("Exception & Validation Tests")
    class ExceptionTests {

        @Test
        @DisplayName("Null request payload returns INVALID_INPUT")
        void testNullRequest() {
            RateSellerResult res = reviewService.submitRating((RateSellerRequest) null);
            assertFalse(res.isSuccess());
            assertEquals(RateSellerStatus.INVALID_INPUT, res.getStatus());
        }

        @Test
        @DisplayName("Null or empty deal ID returns INVALID_INPUT")
        void testEmptyDealId() {
            RateSellerResult res1 = reviewService.submitRating(RateSellerRequest.of(null, "alice", 5, "OK"));
            assertEquals(RateSellerStatus.INVALID_INPUT, res1.getStatus());

            RateSellerResult res2 = reviewService.submitRating(RateSellerRequest.of("  ", "alice", 5, "OK"));
            assertEquals(RateSellerStatus.INVALID_INPUT, res2.getStatus());
        }

        @Test
        @DisplayName("Null or empty buyer username returns INVALID_INPUT")
        void testEmptyBuyer() {
            RateSellerResult res1 = reviewService.submitRating(RateSellerRequest.of("D1", null, 5, "OK"));
            assertEquals(RateSellerStatus.INVALID_INPUT, res1.getStatus());

            RateSellerResult res2 = reviewService.submitRating(RateSellerRequest.of("D1", "  ", 5, "OK"));
            assertEquals(RateSellerStatus.INVALID_INPUT, res2.getStatus());
        }

        @Test
        @DisplayName("Non-existent deal ID returns DEAL_NOT_FOUND")
        void testDealNotFound() {
            RateSellerResult res = reviewService.submitRating("DEAL_9999", "alice", 5, "Good");
            assertFalse(res.isSuccess());
            assertEquals(RateSellerStatus.DEAL_NOT_FOUND, res.getStatus());
        }

        @Test
        @DisplayName("Unauthorized buyer returns UNAUTHORIZED")
        void testUnauthorizedBuyer() {
            createSampleCompletedDeal("D_UNAUTH", "alice", "charlie");
            RateSellerResult res = reviewService.submitRating("D_UNAUTH", "bob", 5, "Hacking rating");
            assertFalse(res.isSuccess());
            assertEquals(RateSellerStatus.UNAUTHORIZED, res.getStatus());
        }

        @Test
        @DisplayName("Deal in ACTIVE status returns DEAL_NOT_ELIGIBLE")
        void testDealNotEligibleActive() {
            Deal deal = new Deal();
            deal.setId("D_ACTIVE_1");
            deal.setListingTitle("Active Item");
            deal.setBuyerUsername("alice");
            deal.setSellerUsername("charlie");
            deal.setStatus(DealStatus.ACTIVE);
            dataService.addDeal(deal);

            RateSellerResult res = reviewService.submitRating("D_ACTIVE_1", "alice", 5, "Too early");
            assertFalse(res.isSuccess());
            assertEquals(RateSellerStatus.DEAL_NOT_ELIGIBLE, res.getStatus());
            assertTrue(res.getMessage().contains("completed or returned"));
        }
    }

    @Nested
    @DisplayName("Eligibility & Query Methods Tests")
    class EligibilityAndQueryTests {

        @Test
        @DisplayName("canRateDeal returns true only for completed/returned unrated deals belonging to user")
        void testCanRateDeal() {
            Deal completed = createSampleCompletedDeal("D_CAN_1", "alice", "charlie");
            assertTrue(reviewService.canRateDeal("D_CAN_1", "alice"));
            assertTrue(reviewService.canRateDeal(completed, "alice"));

            // Other user
            assertFalse(reviewService.canRateDeal("D_CAN_1", "bob"));
            assertFalse(reviewService.canRateDeal(completed, "bob"));

            // After rating
            completed.setRated(true);
            assertFalse(reviewService.canRateDeal("D_CAN_1", "alice"));
            assertFalse(reviewService.canRateDeal(completed, "alice"));

            // Null checks
            assertFalse(reviewService.canRateDeal((String) null, "alice"));
            assertFalse(reviewService.canRateDeal("D_CAN_1", null));
            assertFalse(reviewService.canRateDeal("D_CAN_1", "  "));
            assertFalse(reviewService.canRateDeal((Deal) null, "alice"));
        }

        @Test
        @DisplayName("getReviewForDeal returns optional review or empty")
        void testGetReviewForDeal() {
            createSampleCompletedDeal("D_REV_LOOKUP", "alice", "charlie");
            assertTrue(reviewService.getReviewForDeal("D_REV_LOOKUP").isEmpty());

            reviewService.submitRating("D_REV_LOOKUP", "alice", 5, "Awesome");
            Optional<Review> rev = reviewService.getReviewForDeal("D_REV_LOOKUP");
            assertTrue(rev.isPresent());
            assertEquals("Awesome", rev.get().getComment());

            assertTrue(reviewService.getReviewForDeal(null).isEmpty());
            assertTrue(reviewService.getReviewForDeal("   ").isEmpty());
        }

        @Test
        @DisplayName("getReviewsByBuyer returns all reviews submitted by buyer")
        void testGetReviewsByBuyer() {
            List<Review> aliceReviews = reviewService.getReviewsByBuyer("alice");
            assertNotNull(aliceReviews);
            assertFalse(aliceReviews.isEmpty()); // Alice has seeded review r2

            createSampleCompletedDeal("D_BUYER_REC", "alice", "charlie");
            reviewService.submitRating("D_BUYER_REC", "alice", 4, "Another good one");

            List<Review> updated = reviewService.getReviewsByBuyer("alice");
            assertEquals(aliceReviews.size() + 1, updated.size());

            assertTrue(reviewService.getReviewsByBuyer(null).isEmpty());
            assertTrue(reviewService.getReviewsByBuyer("   ").isEmpty());
        }

        @Test
        @DisplayName("getReviewsForSeller returns valid non-hidden reviews")
        void testGetReviewsForSeller() {
            List<Review> charlieRev = reviewService.getReviewsForSeller("charlie");
            assertNotNull(charlieRev);
            assertFalse(charlieRev.isEmpty());

            assertTrue(reviewService.getReviewsForSeller(null).isEmpty());
            assertTrue(reviewService.getReviewsForSeller("  ").isEmpty());
        }

        @Test
        @DisplayName("getSellerRating returns current seller rating statistics")
        void testGetSellerRating() {
            SellerRatingInfo info = reviewService.getSellerRating("charlie");
            assertNotNull(info);
            assertTrue(info.hasRatings());
            assertTrue(info.getAverageRating() > 0);
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent rating attempts on the same deal: exactly one succeeds, others get ALREADY_RATED")
        void testConcurrentRatingOnSameDeal() throws InterruptedException {
            Deal deal = createSampleCompletedDeal("D_CONCURRENT_SAME", "alice", "charlie");

            int threads = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threads);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger alreadyRatedCount = new AtomicInteger(0);

            for (int i = 0; i < threads; i++) {
                final int rating = (i % 5) + 1;
                executor.submit(() -> {
                    try {
                        latch.await();
                        RateSellerResult res = reviewService.submitRating("D_CONCURRENT_SAME", "alice", rating, "Thread rating");
                        if (res.isSuccess()) {
                            successCount.incrementAndGet();
                        } else if (res.getStatus() == RateSellerStatus.ALREADY_RATED) {
                            alreadyRatedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            latch.countDown();
            doneLatch.await();
            executor.shutdown();

            assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed");
            assertEquals(threads - 1, alreadyRatedCount.get(), "All other concurrent threads must receive ALREADY_RATED");
            assertTrue(deal.isRated());
        }

        @Test
        @DisplayName("Concurrent ratings on different deals for the same seller recalculate average safely")
        void testConcurrentRatingsDifferentDealsSameSeller() throws InterruptedException {
            Seller seller = new Seller("concurrent_seller", "Concurrent Seller", "Pass1234!");
            dataService.registerUser(seller);

            int dealCount = 10;
            List<Deal> deals = new ArrayList<>();
            for (int i = 0; i < dealCount; i++) {
                Deal d = new Deal();
                d.setId("D_CONC_DIFF_" + i);
                d.setListingTitle("Item " + i);
                d.setBuyerUsername("buyer_" + i);
                d.setSellerUsername("concurrent_seller");
                d.setStatus(DealStatus.COMPLETED);
                dataService.addDeal(d);
                deals.add(d);
            }

            ExecutorService executor = Executors.newFixedThreadPool(dealCount);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(dealCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < dealCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        // All rate 5 stars
                        RateSellerResult res = reviewService.submitRating(
                                "D_CONC_DIFF_" + idx,
                                "buyer_" + idx,
                                5,
                                "Great transaction " + idx
                        );
                        if (res.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            latch.countDown();
            doneLatch.await();
            executor.shutdown();

            assertEquals(dealCount, successCount.get());
            assertEquals(5.0, seller.getAverageRating());
            assertEquals(dealCount, seller.getRatingCount());
        }
    }

    @Test
    @DisplayName("Default constructor uses DummyDataService singleton")
    void testDefaultConstructor() {
        BuyerReviewService defaultService = new BuyerReviewService();
        assertNotNull(defaultService);
    }

    private Deal createSampleCompletedDeal(String dealId, String buyerUsername, String sellerUsername) {
        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setListingId("L_TEST");
        deal.setListingTitle("Test Listing");
        deal.setBuyerUsername(buyerUsername);
        deal.setSellerUsername(sellerUsername);
        deal.setType(DealType.SALE);
        deal.setStatus(DealStatus.COMPLETED);
        deal.setRated(false);
        dataService.addDeal(deal);
        return deal;
    }
}
