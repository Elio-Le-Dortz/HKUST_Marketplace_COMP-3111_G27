package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Admin Rating Service Tests [US-A08 / UC 3.8]")
class AdminRatingServiceTest {

    private DummyDataService dataService;
    private AdminRatingService ratingService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetToSeedData();
        ratingService = dataService.getAdminRatingService();
    }

    // =========================================================================
    // Basic Flow & Ordering Tests
    // =========================================================================

    @Test
    @DisplayName("Basic Flow: getOrderedReviews sorts flagged first, then newest first")
    void testOrderedReviewsDisplaysFlaggedFirstThenNewestFirst() {
        // Clear existing reviews to test exact ordering deterministically
        dataService.clearReviews();

        LocalDateTime base = LocalDateTime.of(2026, 11, 5, 10, 0);

        Review unflaggedOlder = new Review();
        unflaggedOlder.setId("R_UF_OLD");
        unflaggedOlder.setFlagged(false);
        unflaggedOlder.setDate(base.minusDays(2));

        Review unflaggedNewer = new Review();
        unflaggedNewer.setId("R_UF_NEW");
        unflaggedNewer.setFlagged(false);
        unflaggedNewer.setDate(base.plusDays(1));

        Review flaggedOlder = new Review();
        flaggedOlder.setId("R_FL_OLD");
        flaggedOlder.setFlagged(true);
        flaggedOlder.setDate(base.minusDays(1));

        Review flaggedNewer = new Review();
        flaggedNewer.setId("R_FL_NEW");
        flaggedNewer.setFlagged(true);
        flaggedNewer.setDate(base.plusDays(2));

        dataService.addReview(unflaggedOlder);
        dataService.addReview(unflaggedNewer);
        dataService.addReview(flaggedOlder);
        dataService.addReview(flaggedNewer);

        List<Review> ordered = ratingService.getOrderedReviews();
        assertEquals(4, ordered.size());

        // Flagged reviews come first (newest to oldest)
        assertEquals("R_FL_NEW", ordered.get(0).getId());
        assertEquals("R_FL_OLD", ordered.get(1).getId());

        // Then unflagged reviews (newest to oldest)
        assertEquals("R_UF_NEW", ordered.get(2).getId());
        assertEquals("R_UF_OLD", ordered.get(3).getId());
    }

    @Test
    @DisplayName("REVIEW_ORDER_COMPARATOR null handling")
    void testOrderComparatorHandlesNulls() {
        Review r1 = new Review();
        r1.setId("R1");
        r1.setDate(LocalDateTime.now());

        Review rNullDate = new Review();
        rNullDate.setId("R2");
        rNullDate.setDate(null);

        assertEquals(0, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(null, null));
        assertEquals(1, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(null, r1));
        assertEquals(-1, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(r1, null));

        assertEquals(0, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(rNullDate, rNullDate));
        assertEquals(1, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(rNullDate, r1));
        assertEquals(-1, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(r1, rNullDate));
    }

    @Test
    @DisplayName("Summary metrics accurate calculation")
    void testSummaryMetricsCalculations() {
        dataService.clearReviews();

        Review r1 = new Review(); // visible 5
        r1.setRating(5);
        r1.setHidden(false);
        r1.setFlagged(false);

        Review r2 = new Review(); // visible 4, flagged
        r2.setRating(4);
        r2.setHidden(false);
        r2.setFlagged(true);

        Review r3 = new Review(); // hidden 1 (excluded from average)
        r3.setRating(1);
        r3.setHidden(true);
        r3.setFlagged(false);

        dataService.addReview(r1);
        dataService.addReview(r2);
        dataService.addReview(r3);

        AdminRatingSummaryMetrics metrics = ratingService.getSummaryMetrics();
        assertEquals(3, metrics.getTotalReviews());
        assertEquals(1, metrics.getFlaggedReviews());
        assertEquals(1, metrics.getHiddenReviews());
        assertEquals(2, metrics.getVisibleReviews());
        // Average of visible reviews (5 + 4) / 2 = 4.5
        assertEquals(4.5, metrics.getAveragePlatformRating());
    }

    @Test
    @DisplayName("Summary metrics when no visible reviews exist")
    void testSummaryMetricsNoVisibleReviews() {
        dataService.clearReviews();

        Review r = new Review();
        r.setRating(2);
        r.setHidden(true);
        dataService.addReview(r);

        AdminRatingSummaryMetrics metrics = ratingService.getSummaryMetrics();
        assertEquals(1, metrics.getTotalReviews());
        assertEquals(0, metrics.getVisibleReviews());
        assertEquals(0.0, metrics.getAveragePlatformRating());
    }

    // =========================================================================
    // Subflow S1: Hide Review Tests
    // =========================================================================

    @Test
    @DisplayName("Subflow S1: Hide review updates seller rating, clears flag, and notifies parties")
    void testHideReviewSuccess() {
        // Setup isolated seller with 2 reviews: 5 stars and 1 star
        Seller seller = new Seller("target_seller", "Target Seller", "hash");
        dataService.registerUser(seller);

        Review rGood = new Review("target_seller", "buyer_alice", "D1", "L1", "Math Book", 5, "Great!");
        rGood.setId("R_GOOD");
        rGood.setHidden(false);

        Review rBad = new Review("target_seller", "buyer_bob", "D2", "L2", "Broken Pen", 1, "Terrible condition!");
        rBad.setId("R_BAD");
        rBad.setHidden(false);
        rBad.setFlagged(true);
        rBad.setFlagReason("Unfair feedback");

        dataService.addReview(rGood);
        dataService.addReview(rBad);

        // Before hiding: average = (5 + 1) / 2 = 3.0
        dataService.recalculateSellerRating("target_seller");
        assertEquals(3.0, seller.getAverageRating());
        assertEquals(2, seller.getRatingCount());

        // Add admin notification for flagged review to test automatic read resolution
        Notification adminNotif = new Notification("admin", "Flagged Review", "Msg", "Flagged Reviews", "R_BAD", "REVIEW");
        adminNotif.setRead(false);
        dataService.addNotification(adminNotif);

        // Execute S1: Hide rBad
        AdminRatingOperationResult result = ratingService.hideReview("R_BAD", "admin");
        assertTrue(result.isSuccess());
        assertEquals(AdminRatingOperationStatus.SUCCESS, result.getStatus());
        assertTrue(rBad.isHidden());
        assertFalse(rBad.isFlagged()); // flag cleared per S1 Step 3

        // Seller average rating recalculated excluding hidden review -> 5.0
        assertEquals(5.0, seller.getAverageRating());
        assertEquals(1, seller.getRatingCount());
        assertEquals(5.0, result.getUpdatedAverageRating());
        assertEquals(1, result.getUpdatedRatingCount());

        // Seller notified (S1 Step 4)
        List<Notification> sellerNotifs = dataService.getNotificationsFor("target_seller");
        assertTrue(sellerNotifs.stream().anyMatch(n -> n.getTitle().contains("Review Hidden by Admin")));

        // Buyer notified (S1 Step 4)
        List<Notification> buyerNotifs = dataService.getNotificationsFor("buyer_bob");
        assertTrue(buyerNotifs.stream().anyMatch(n -> n.getTitle().contains("Your Review Was Hidden")));

        // Associated admin notification marked as read
        assertTrue(adminNotif.isRead());
    }

    @Test
    @DisplayName("Subflow S1: Hiding already hidden review returns ALREADY_HIDDEN")
    void testHideReviewAlreadyHidden() {
        Review r = new Review();
        r.setId("R_ALREADY_HIDDEN");
        r.setHidden(true);
        dataService.addReview(r);

        AdminRatingOperationResult res = ratingService.hideReview("R_ALREADY_HIDDEN", "admin");
        assertFalse(res.isSuccess());
        assertEquals(AdminRatingOperationStatus.ALREADY_HIDDEN, res.getStatus());
    }

    @Test
    @DisplayName("Subflow S1: Hiding non-existent review returns REVIEW_NOT_FOUND")
    void testHideReviewNotFound() {
        AdminRatingOperationResult res = ratingService.hideReview("NON_EXISTENT_REVIEW_999", "admin");
        assertFalse(res.isSuccess());
        assertEquals(AdminRatingOperationStatus.REVIEW_NOT_FOUND, res.getStatus());
    }

    @Test
    @DisplayName("Subflow S1: Hiding with invalid review ID returns INVALID_OPERATION")
    void testHideReviewInvalidId() {
        AdminRatingOperationResult res1 = ratingService.hideReview(null, "admin");
        assertFalse(res1.isSuccess());
        assertEquals(AdminRatingOperationStatus.INVALID_OPERATION, res1.getStatus());

        AdminRatingOperationResult res2 = ratingService.hideReview("   ", "admin");
        assertFalse(res2.isSuccess());
        assertEquals(AdminRatingOperationStatus.INVALID_OPERATION, res2.getStatus());
    }

    @Test
    @DisplayName("Subflow S1: Hiding by non-admin returns UNAUTHORIZED")
    void testHideReviewUnauthorized() {
        Buyer buyer = new Buyer("regular_buyer", "Regular", "pass");
        dataService.registerUser(buyer);

        AdminRatingOperationResult res1 = ratingService.hideReview("R1", "regular_buyer");
        assertFalse(res1.isSuccess());
        assertEquals(AdminRatingOperationStatus.UNAUTHORIZED, res1.getStatus());

        AdminRatingOperationResult res2 = ratingService.hideReview("R1", null);
        assertFalse(res2.isSuccess());
        assertEquals(AdminRatingOperationStatus.UNAUTHORIZED, res2.getStatus());
    }

    // =========================================================================
    // Subflow S2: Unhide Review Tests
    // =========================================================================

    @Test
    @DisplayName("Subflow S2: Unhide review reinstates in seller rating calculation")
    void testUnhideReviewSuccess() {
        Seller seller = new Seller("seller_unhide", "Unhide Seller", "hash");
        dataService.registerUser(seller);

        Review r = new Review("seller_unhide", "buyer_sam", "D3", "L3", "Desk Lamp", 4, "Good lamp");
        r.setId("R_UNHIDE_TEST");
        r.setHidden(true);
        dataService.addReview(r);

        // While hidden, rating count is 0
        dataService.recalculateSellerRating("seller_unhide");
        assertEquals(0.0, seller.getAverageRating());
        assertEquals(0, seller.getRatingCount());

        // Execute S2: Unhide
        AdminRatingOperationResult res = ratingService.unhideReview("R_UNHIDE_TEST", "admin");
        assertTrue(res.isSuccess());
        assertEquals(AdminRatingOperationStatus.SUCCESS, res.getStatus());
        assertFalse(r.isHidden());

        // Rating recalculated to include review
        assertEquals(4.0, seller.getAverageRating());
        assertEquals(1, seller.getRatingCount());
    }

    @Test
    @DisplayName("Subflow S2: Unhiding already visible review returns ALREADY_VISIBLE")
    void testUnhideReviewAlreadyVisible() {
        Review r = new Review();
        r.setId("R_VISIBLE");
        r.setHidden(false);
        dataService.addReview(r);

        AdminRatingOperationResult res = ratingService.unhideReview("R_VISIBLE", "admin");
        assertFalse(res.isSuccess());
        assertEquals(AdminRatingOperationStatus.ALREADY_VISIBLE, res.getStatus());
    }

    @Test
    @DisplayName("Subflow S2: Unhide validation failures (not found, invalid id, unauthorized)")
    void testUnhideReviewValidationFailures() {
        assertEquals(AdminRatingOperationStatus.REVIEW_NOT_FOUND,
                ratingService.unhideReview("UNKNOWN_ID", "admin").getStatus());

        assertEquals(AdminRatingOperationStatus.INVALID_OPERATION,
                ratingService.unhideReview("", "admin").getStatus());

        assertEquals(AdminRatingOperationStatus.UNAUTHORIZED,
                ratingService.unhideReview("R1", "not_admin").getStatus());
    }

    // =========================================================================
    // Subflow S3: Dismiss Flag Tests
    // =========================================================================

    @Test
    @DisplayName("Subflow S3: Dismiss flag clears flag and reason, keeps review visible, and notifies seller")
    void testDismissFlagSuccess() {
        Review r = new Review("seller_flag", "buyer_claire", "D4", "L4", "Backpack", 3, "Zipper is tight");
        r.setId("R_DISMISS_TEST");
        r.setFlagged(true);
        r.setFlagReason("Not fair to criticize zipper");
        r.setHidden(false);
        dataService.addReview(r);

        Notification adminNotif = new Notification("admin", "Flagged Review", "Msg", "Flagged Reviews", "R_DISMISS_TEST", "REVIEW");
        adminNotif.setRead(false);
        dataService.addNotification(adminNotif);

        AdminRatingOperationResult res = ratingService.dismissFlag("R_DISMISS_TEST", "admin");
        assertTrue(res.isSuccess());
        assertEquals(AdminRatingOperationStatus.SUCCESS, res.getStatus());
        assertFalse(r.isFlagged());
        assertNull(r.getFlagReason());
        assertFalse(r.isHidden()); // stays visible

        // Notifies seller (S3 Step 4)
        List<Notification> sellerNotifs = dataService.getNotificationsFor("seller_flag");
        assertTrue(sellerNotifs.stream().anyMatch(n -> n.getTitle().contains("Flag Dismissed: Review Retained")));

        // Associated admin notification marked as read
        assertTrue(adminNotif.isRead());
    }

    @Test
    @DisplayName("Subflow S3: Dismiss flag when review is not flagged returns NOT_FLAGGED")
    void testDismissFlagNotFlagged() {
        Review r = new Review();
        r.setId("R_NOT_FLAGGED");
        r.setFlagged(false);
        dataService.addReview(r);

        AdminRatingOperationResult res = ratingService.dismissFlag("R_NOT_FLAGGED", "admin");
        assertFalse(res.isSuccess());
        assertEquals(AdminRatingOperationStatus.NOT_FLAGGED, res.getStatus());
    }

    @Test
    @DisplayName("Subflow S3: Dismiss flag validation failures (not found, invalid id, unauthorized)")
    void testDismissFlagValidationFailures() {
        assertEquals(AdminRatingOperationStatus.REVIEW_NOT_FOUND,
                ratingService.dismissFlag("UNKNOWN_FLAG_ID", "admin").getStatus());

        assertEquals(AdminRatingOperationStatus.INVALID_OPERATION,
                ratingService.dismissFlag(null, "admin").getStatus());

        assertEquals(AdminRatingOperationStatus.UNAUTHORIZED,
                ratingService.dismissFlag("R1", "buyer_user").getStatus());
    }

    // =========================================================================
    // Alternative Flows A1, A2, A3 Tests
    // =========================================================================

    @Test
    @DisplayName("Alternative Flow A1: Empty review state")
    void testAlternativeFlowA1EmptyReviewState() {
        dataService.clearReviews();

        List<Review> ordered = ratingService.getOrderedReviews();
        assertTrue(ordered.isEmpty());

        AdminRatingSummaryMetrics metrics = ratingService.getSummaryMetrics();
        assertEquals(0, metrics.getTotalReviews());
        assertEquals(0, metrics.getFlaggedReviews());
        assertEquals(0, metrics.getHiddenReviews());
        assertEquals(0, metrics.getVisibleReviews());
        assertEquals(0.0, metrics.getAveragePlatformRating());
    }

    @Test
    @DisplayName("Alternative Flow A2: Filter reviews by user query, score, state, and dates")
    void testAlternativeFlowA2Filtering() {
        dataService.clearReviews();

        LocalDateTime date = LocalDateTime.of(2026, 11, 3, 14, 0);

        Review r1 = new Review("seller_david", "buyer_emma", "D10", "L10", "Scientific Calculator", 5, "Awesome");
        r1.setId("R_D1");
        r1.setDate(date);
        r1.setFlagged(true);
        r1.setHidden(false);

        Review r2 = new Review("seller_david", "buyer_frank", "D11", "L11", "Scientific Calculator", 4, "Decent");
        r2.setId("R_D2");
        r2.setDate(date.plusDays(1));
        r2.setFlagged(false);
        r2.setHidden(true);

        Review r3 = new Review("seller_grace", "buyer_henry", "D12", "L12", "Office Chair", 2, "Wobbly");
        r3.setId("R_D3");
        r3.setDate(date.plusDays(2));
        r3.setFlagged(false);
        r3.setHidden(false);

        dataService.addReview(r1);
        dataService.addReview(r2);
        dataService.addReview(r3);

        // Filter by userQuery = "david"
        AdminRatingFilterCriteria c1 = new AdminRatingFilterCriteria();
        c1.setUserQuery("david");
        List<Review> filtered1 = ratingService.filterReviews(c1);
        assertEquals(2, filtered1.size());

        // Filter by score = 5
        AdminRatingFilterCriteria c2 = new AdminRatingFilterCriteria();
        c2.setRatingScore(5);
        List<Review> filtered2 = ratingService.filterReviews(c2);
        assertEquals(1, filtered2.size());
        assertEquals("R_D1", filtered2.get(0).getId());

        // Filter by state = FLAGGED_ONLY
        List<Review> filteredFlagged = ratingService.filterReviews(AdminRatingFilterCriteria.flaggedOnly());
        assertEquals(1, filteredFlagged.size());
        assertEquals("R_D1", filteredFlagged.get(0).getId());

        // Filter by state = HIDDEN_ONLY
        List<Review> filteredHidden = ratingService.filterReviews(AdminRatingFilterCriteria.hiddenOnly());
        assertEquals(1, filteredHidden.size());
        assertEquals("R_D2", filteredHidden.get(0).getId());

        // Filter by state = VISIBLE_ONLY
        List<Review> filteredVisible = ratingService.filterReviews(AdminRatingFilterCriteria.visibleOnly());
        assertEquals(2, filteredVisible.size());

        // Filter with null criteria
        assertEquals(3, ratingService.filterReviews(null).size());
    }

    @Test
    @DisplayName("Alternative Flow A3: Invalid date range throws IllegalArgumentException")
    void testAlternativeFlowA3InvalidDateRangeThrows() {
        AdminRatingFilterCriteria criteria = new AdminRatingFilterCriteria();
        criteria.setStartDate(LocalDate.of(2026, 11, 10));
        criteria.setEndDate(LocalDate.of(2026, 11, 5)); // end before start

        assertThrows(IllegalArgumentException.class, () -> ratingService.filterReviews(criteria));
    }

    // =========================================================================
    // Concurrency & Auxiliary Methods
    // =========================================================================

    @Test
    @DisplayName("findReviewById returns review or null appropriately")
    void testFindReviewById() {
        assertNull(ratingService.findReviewById(null));
        assertNull(ratingService.findReviewById("   "));
        assertNull(ratingService.findReviewById("NON_EXISTENT_ID"));

        Review r = new Review();
        r.setId("R_LOOKUP_100");
        dataService.addReview(r);

        assertNotNull(ratingService.findReviewById("R_LOOKUP_100"));
        assertNotNull(ratingService.findReviewById("r_lookup_100")); // case insensitive
    }

    @Test
    @DisplayName("Listing title fallback resolution when title is missing")
    void testResolveListingTitleFallback() {
        // Create review with listing ID pointing to seeded listing l1 ("Calculus Textbook")
        Review rWithId = new Review();
        rWithId.setId("R_FALLBACK_1");
        rWithId.setListingTitle(null);
        rWithId.setListingId("L100");
        rWithId.setSellerUsername("charlie");
        rWithId.setBuyerUsername("bob");
        rWithId.setHidden(false);
        dataService.addReview(rWithId);

        AdminRatingOperationResult res = ratingService.hideReview("R_FALLBACK_1", "admin");
        assertTrue(res.isSuccess());

        List<Notification> notifs = dataService.getNotificationsFor("charlie");
        assertTrue(notifs.stream().anyMatch(n -> n.getMessage().contains("Calculus Textbook") || n.getMessage().contains("Item")));
    }

    @Test
    @DisplayName("Multi-threaded concurrent execution of review operations")
    void testConcurrentHideAndUnhide() throws InterruptedException, ExecutionException {
        Seller seller = new Seller("concurrent_seller", "Concurrent Seller", "hash");
        dataService.registerUser(seller);

        List<Review> threadReviews = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Review r = new Review("concurrent_seller", "buyer_" + i, "D_" + i, "L_" + i, "Item " + i, 5, "Good");
            r.setId("R_CONC_" + i);
            r.setHidden(false);
            r.setFlagged(i % 2 == 0);
            dataService.addReview(r);
            threadReviews.add(r);
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Callable<AdminRatingOperationResult>> tasks = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            final String rid = "R_CONC_" + i;
            if (i % 2 == 0) {
                tasks.add(() -> ratingService.hideReview(rid, "admin"));
            } else {
                tasks.add(() -> ratingService.dismissFlag(rid, "admin"));
            }
        }

        List<Future<AdminRatingOperationResult>> futures = executor.invokeAll(tasks);
        for (Future<AdminRatingOperationResult> f : futures) {
            AdminRatingOperationResult r = f.get();
            assertNotNull(r);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify summary metrics integrity after concurrent operations
        AdminRatingSummaryMetrics metrics = ratingService.getSummaryMetrics();
        assertTrue(metrics.getTotalReviews() >= 20);
        assertTrue(seller.getAverageRating() >= 0.0);
    }
}
