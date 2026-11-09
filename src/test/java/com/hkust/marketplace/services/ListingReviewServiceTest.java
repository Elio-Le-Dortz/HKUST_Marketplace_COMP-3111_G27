package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListingReviewService Tests (US-A02)")
class ListingReviewServiceTest {

    private DummyDataService dataService;
    private ListingReviewService reviewService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        reviewService = new ListingReviewService(dataService);
        SessionManager.login(dataService.findUser("admin"));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("Default constructor instantiates service with singleton DummyDataService")
    void testDefaultConstructor() {
        ListingReviewService defaultService = new ListingReviewService();
        assertNotNull(defaultService);
    }

    @Nested
    @DisplayName("Basic Flow Tests (Browse Pending Queue & View Details)")
    class BasicFlowTests {

        @Test
        @DisplayName("getPendingListingsOldestFirst returns listings ordered by postedDate ascending")
        void testGetPendingListingsOldestFirst() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            assertFalse(pending.isEmpty(), "Seeded data should contain pending listings");

            for (Listing l : pending) {
                assertEquals(ListingStatus.PENDING_REVIEW, l.getStatus());
            }

            // Verify oldest-first chronological ordering
            for (int i = 0; i < pending.size() - 1; i++) {
                LocalDateTime d1 = pending.get(i).getPostedAt();
                LocalDateTime d2 = pending.get(i + 1).getPostedAt();
                if (d1 != null && d2 != null) {
                    assertTrue(d1.isBefore(d2) || d1.isEqual(d2),
                            "Listings should be sorted oldest first");
                }
            }
        }

        @Test
        @DisplayName("getPendingListingsOldestFirst tie-breaking by ID when postedDates are equal")
        void testOldestFirstTieBreakerById() {
            // Add two listings with identical postedDate
            LocalDateTime sameTime = LocalDateTime.of(2026, 11, 1, 12, 0);

            Listing lA = new Listing();
            lA.setId("L901");
            lA.setTitle("Item A");
            lA.setStatus(ListingStatus.PENDING_REVIEW);
            lA.setPostedAt(sameTime);
            dataService.addListing(lA);

            Listing lB = new Listing();
            lB.setId("L902");
            lB.setTitle("Item B");
            lB.setStatus(ListingStatus.PENDING_REVIEW);
            lB.setPostedAt(sameTime);
            dataService.addListing(lB);

            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            int indexA = -1;
            int indexB = -1;
            for (int i = 0; i < pending.size(); i++) {
                if ("L901".equals(pending.get(i).getId())) indexA = i;
                if ("L902".equals(pending.get(i).getId())) indexB = i;
            }
            assertTrue(indexA >= 0 && indexB >= 0);
            assertTrue(indexA < indexB, "L901 should precede L902 based on secondary tie-breaker");
        }

        @Test
        @DisplayName("getPendingReviewCount matches the size of pending listings list")
        void testGetPendingReviewCount() {
            int count = reviewService.getPendingReviewCount();
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            assertEquals(pending.size(), count);
        }

        @Test
        @DisplayName("getListingDetails returns populated Optional for existing listing, empty for unknown")
        void testGetListingDetails() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing sample = pending.get(0);

            Optional<Listing> found = reviewService.getListingDetails(sample.getId());
            assertTrue(found.isPresent());
            assertEquals(sample.getTitle(), found.get().getTitle());

            // Non-existent, null, and empty IDs
            assertTrue(reviewService.getListingDetails("L999999").isEmpty());
            assertTrue(reviewService.getListingDetails(null).isEmpty());
            assertTrue(reviewService.getListingDetails("   ").isEmpty());
        }
    }

    @Nested
    @DisplayName("Subflow S1: Approve Listing Tests")
    class ApproveListingTests {

        @Test
        @DisplayName("Approve pending listing sets status to LIVE, records timestamp, and notifies seller")
        void testApprovePendingListingSuccess() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);
            String sellerUsername = target.getSellerUsername();
            int initialSellerNotifs = dataService.getNotificationsFor(sellerUsername).size();

            ListingReviewResult result = reviewService.approveListing(target.getId(), "admin");

            assertTrue(result.isSuccess());
            assertEquals(ListingReviewStatus.APPROVED, result.getStatus());
            assertNotNull(result.getReviewedAt());

            // Verify listing state
            Listing updated = dataService.findListing(target.getId());
            assertNotNull(updated);
            assertEquals(ListingStatus.LIVE, updated.getStatus());
            assertNull(updated.getAdminReason());
            assertNotNull(updated.getReviewedAt());

            // Listing should no longer be in pending review queue
            assertFalse(reviewService.getPendingListingsOldestFirst().stream()
                    .anyMatch(l -> l.getId().equals(target.getId())));

            // Seller notification dispatched (Subflow S1 Step 5)
            List<Notification> sellerNotifs = dataService.getNotificationsFor(sellerUsername);
            assertEquals(initialSellerNotifs + 1, sellerNotifs.size());
            Notification notif = sellerNotifs.get(0);
            assertEquals("Listing Approved", notif.getTitle());
            assertEquals("Listings", notif.getCategory());
            assertTrue(notif.getMessage().contains(target.getTitle()));
            assertEquals(target.getId(), notif.getRelatedItemId());
        }

        @Test
        @DisplayName("reviewListing with approve request delegates to approveListing successfully")
        void testApproveViaRequestDTO() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);

            ListingReviewRequest req = ListingReviewRequest.approveRequest(target.getId(), "admin");
            ListingReviewResult result = reviewService.reviewListing(req);

            assertTrue(result.isSuccess());
            assertEquals(ListingReviewStatus.APPROVED, result.getStatus());
            assertEquals(ListingStatus.LIVE, dataService.findListing(target.getId()).getStatus());
        }
    }

    @Nested
    @DisplayName("Subflow S2: Reject Listing Tests")
    class RejectListingTests {

        @Test
        @DisplayName("Reject pending listing sets status to REJECTED, stores written reason, and notifies seller")
        void testRejectPendingListingSuccess() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);
            String sellerUsername = target.getSellerUsername();
            int initialSellerNotifs = dataService.getNotificationsFor(sellerUsername).size();

            String reason = "Images are blurry and price does not conform to student market guidelines.";
            ListingReviewResult result = reviewService.rejectListing(target.getId(), "admin", reason);

            assertTrue(result.isSuccess());
            assertEquals(ListingReviewStatus.REJECTED, result.getStatus());
            assertEquals(reason, result.getRejectionReason());
            assertNotNull(result.getReviewedAt());

            // Verify listing state
            Listing updated = dataService.findListing(target.getId());
            assertNotNull(updated);
            assertEquals(ListingStatus.REJECTED, updated.getStatus());
            assertEquals(reason, updated.getAdminReason());

            // Listing removed from pending queue
            assertFalse(reviewService.getPendingListingsOldestFirst().stream()
                    .anyMatch(l -> l.getId().equals(target.getId())));

            // Seller notified with written reason (Subflow S2 Step 6)
            List<Notification> sellerNotifs = dataService.getNotificationsFor(sellerUsername);
            assertEquals(initialSellerNotifs + 1, sellerNotifs.size());
            Notification notif = sellerNotifs.get(0);
            assertEquals("Listing Rejected", notif.getTitle());
            assertEquals("Listings", notif.getCategory());
            assertTrue(notif.getMessage().contains(reason));
        }

        @Test
        @DisplayName("reviewListing with reject request delegates to rejectListing successfully")
        void testRejectViaRequestDTO() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);

            ListingReviewRequest req = ListingReviewRequest.rejectRequest(target.getId(), "admin", "Missing dimensions");
            ListingReviewResult result = reviewService.reviewListing(req);

            assertTrue(result.isSuccess());
            assertEquals(ListingReviewStatus.REJECTED, result.getStatus());
            assertEquals(ListingStatus.REJECTED, dataService.findListing(target.getId()).getStatus());
            assertEquals("Missing dimensions", dataService.findListing(target.getId()).getAdminReason());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A1: No Pending Listings")
    class AlternativeFlowA1Tests {

        @Test
        @DisplayName("When all pending listings are decided, review queue is empty")
        void testQueueEmptyAfterAllReviewed() {
            List<Listing> pending = new ArrayList<>(reviewService.getPendingListingsOldestFirst());
            for (Listing l : pending) {
                reviewService.approveListing(l.getId(), "admin");
            }

            assertEquals(0, reviewService.getPendingReviewCount());
            assertTrue(reviewService.getPendingListingsOldestFirst().isEmpty());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A2: Empty Written Rejection Reason")
    class AlternativeFlowA2Tests {

        @Test
        @DisplayName("Rejecting with null reason fails with EMPTY_REASON and does not change listing")
        void testRejectNullReasonFails() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);

            ListingReviewResult result = reviewService.rejectListing(target.getId(), "admin", null);

            assertFalse(result.isSuccess());
            assertEquals(ListingReviewStatus.EMPTY_REASON, result.getStatus());
            assertEquals(ListingStatus.PENDING_REVIEW, dataService.findListing(target.getId()).getStatus());
        }

        @Test
        @DisplayName("Rejecting with empty string fails with EMPTY_REASON")
        void testRejectEmptyStringFails() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);

            ListingReviewResult result = reviewService.rejectListing(target.getId(), "admin", "");

            assertFalse(result.isSuccess());
            assertEquals(ListingReviewStatus.EMPTY_REASON, result.getStatus());
            assertEquals(ListingStatus.PENDING_REVIEW, dataService.findListing(target.getId()).getStatus());
        }

        @Test
        @DisplayName("Rejecting with whitespace only fails with EMPTY_REASON")
        void testRejectWhitespaceOnlyFails() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);

            ListingReviewResult result = reviewService.rejectListing(target.getId(), "admin", "   \t\n  ");

            assertFalse(result.isSuccess());
            assertEquals(ListingReviewStatus.EMPTY_REASON, result.getStatus());
            assertEquals(ListingStatus.PENDING_REVIEW, dataService.findListing(target.getId()).getStatus());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A3: Listing No Longer Pending")
    class AlternativeFlowA3Tests {

        @Test
        @DisplayName("Approving a listing that is already LIVE returns NOT_PENDING_REVIEW")
        void testApproveAlreadyLiveFails() {
            // L100 is live in seed data
            ListingReviewResult result = reviewService.approveListing("L100", "admin");
            assertFalse(result.isSuccess());
            assertEquals(ListingReviewStatus.NOT_PENDING_REVIEW, result.getStatus());
        }

        @Test
        @DisplayName("Rejecting a listing that is already REJECTED returns NOT_PENDING_REVIEW")
        void testRejectAlreadyRejectedFails() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);
            reviewService.rejectListing(target.getId(), "admin", "First rejection");

            ListingReviewResult secondResult = reviewService.rejectListing(target.getId(), "admin", "Second rejection");
            assertFalse(secondResult.isSuccess());
            assertEquals(ListingReviewStatus.NOT_PENDING_REVIEW, secondResult.getStatus());
        }

        @Test
        @DisplayName("Approving or rejecting a non-existent listing returns LISTING_NOT_FOUND")
        void testListingNotFound() {
            ListingReviewResult approveResult = reviewService.approveListing("NON_EXISTENT_ID", "admin");
            assertFalse(approveResult.isSuccess());
            assertEquals(ListingReviewStatus.LISTING_NOT_FOUND, approveResult.getStatus());

            ListingReviewResult rejectResult = reviewService.rejectListing("NON_EXISTENT_ID", "admin", "Reason");
            assertFalse(rejectResult.isSuccess());
            assertEquals(ListingReviewStatus.LISTING_NOT_FOUND, rejectResult.getStatus());
        }
    }

    @Nested
    @DisplayName("Authorization & Role Validation Tests")
    class AuthorizationTests {

        @Test
        @DisplayName("Non-admin user (Buyer) cannot approve or reject listings")
        void testBuyerCannotReview() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);

            ListingReviewResult appResult = reviewService.approveListing(target.getId(), "alice");
            assertFalse(appResult.isSuccess());
            assertEquals(ListingReviewStatus.UNAUTHORIZED, appResult.getStatus());

            ListingReviewResult rejResult = reviewService.rejectListing(target.getId(), "alice", "Buyer rejection");
            assertFalse(rejResult.isSuccess());
            assertEquals(ListingReviewStatus.UNAUTHORIZED, rejResult.getStatus());
        }

        @Test
        @DisplayName("Non-admin user (Seller) cannot approve or reject listings")
        void testSellerCannotReview() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);

            ListingReviewResult appResult = reviewService.approveListing(target.getId(), "charlie");
            assertFalse(appResult.isSuccess());
            assertEquals(ListingReviewStatus.UNAUTHORIZED, appResult.getStatus());

            ListingReviewResult rejResult = reviewService.rejectListing(target.getId(), "charlie", "Seller rejection");
            assertFalse(rejResult.isSuccess());
            assertEquals(ListingReviewStatus.UNAUTHORIZED, rejResult.getStatus());
        }

        @Test
        @DisplayName("Unknown username returns UNAUTHORIZED")
        void testUnknownUserCannotReview() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);

            ListingReviewResult result = reviewService.approveListing(target.getId(), "ghost_admin");
            assertFalse(result.isSuccess());
            assertEquals(ListingReviewStatus.UNAUTHORIZED, result.getStatus());
        }

        @Test
        @DisplayName("Deactivated admin cannot approve or reject listings")
        void testDeactivatedAdminCannotReview() {
            User admin = dataService.findUser("admin");
            admin.setActive(false);

            assertThrows(SecurityException.class, () -> reviewService.getPendingListingsOldestFirst());
            assertThrows(SecurityException.class, () -> reviewService.approveListing("L100", "admin"));
            assertThrows(SecurityException.class, () -> reviewService.rejectListing("L100", "admin", "Valid reason"));
        }

        @Test
        @DisplayName("Null or blank admin username returns UNAUTHORIZED")
        void testNullOrBlankAdminUsername() {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);

            ListingReviewResult r1 = reviewService.approveListing(target.getId(), null);
            assertEquals(ListingReviewStatus.UNAUTHORIZED, r1.getStatus());

            ListingReviewResult r2 = reviewService.approveListing(target.getId(), "   ");
            assertEquals(ListingReviewStatus.UNAUTHORIZED, r2.getStatus());

            ListingReviewResult r3 = reviewService.rejectListing(target.getId(), null, "Reason");
            assertEquals(ListingReviewStatus.UNAUTHORIZED, r3.getStatus());

            ListingReviewResult r4 = reviewService.rejectListing(target.getId(), "   ", "Reason");
            assertEquals(ListingReviewStatus.UNAUTHORIZED, r4.getStatus());
        }
    }

    @Nested
    @DisplayName("Input Validation & Null Safety Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Null or empty listing ID returns INVALID_INPUT")
        void testNullOrEmptyListingId() {
            ListingReviewResult r1 = reviewService.approveListing(null, "admin");
            assertEquals(ListingReviewStatus.INVALID_INPUT, r1.getStatus());

            ListingReviewResult r2 = reviewService.approveListing("  ", "admin");
            assertEquals(ListingReviewStatus.INVALID_INPUT, r2.getStatus());

            ListingReviewResult r3 = reviewService.rejectListing(null, "admin", "Reason");
            assertEquals(ListingReviewStatus.INVALID_INPUT, r3.getStatus());

            ListingReviewResult r4 = reviewService.rejectListing("  ", "admin", "Reason");
            assertEquals(ListingReviewStatus.INVALID_INPUT, r4.getStatus());
        }

        @Test
        @DisplayName("reviewListing with null request returns INVALID_INPUT")
        void testReviewListingNullRequest() {
            ListingReviewResult result = reviewService.reviewListing(null);
            assertFalse(result.isSuccess());
            assertEquals(ListingReviewStatus.INVALID_INPUT, result.getStatus());
        }

        @Test
        @DisplayName("Listing with null seller username approves cleanly without notification crash")
        void testListingWithNullSellerUsername() {
            Listing noSeller = new Listing();
            noSeller.setId("L990");
            noSeller.setTitle("Item Without Seller");
            noSeller.setStatus(ListingStatus.PENDING_REVIEW);
            noSeller.setSellerUsername(null);
            dataService.addListing(noSeller);

            ListingReviewResult result = reviewService.approveListing("L990", "admin");
            assertTrue(result.isSuccess());
            assertEquals(ListingStatus.LIVE, dataService.findListing("L990").getStatus());
        }
    }

    @Nested
    @DisplayName("Concurrency & Race Condition Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Two concurrent review attempts on the same listing: exactly one succeeds, other receives NOT_PENDING_REVIEW")
        void testConcurrentReviewOnSameListing() throws InterruptedException, ExecutionException {
            List<Listing> pending = reviewService.getPendingListingsOldestFirst();
            Listing target = pending.get(0);
            String targetId = target.getId();

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            Callable<ListingReviewResult> approveTask = () -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                return reviewService.approveListing(targetId, "admin");
            };

            Callable<ListingReviewResult> rejectTask = () -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                return reviewService.rejectListing(targetId, "admin", "Concurrent rejection test");
            };

            Future<ListingReviewResult> f1 = executor.submit(approveTask);
            Future<ListingReviewResult> f2 = executor.submit(rejectTask);

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown(); // fire simultaneously

            ListingReviewResult r1 = f1.get();
            ListingReviewResult r2 = f2.get();
            executor.shutdown();

            // Exactly one must succeed, one must fail with NOT_PENDING_REVIEW
            boolean oneApproved = r1.getStatus() == ListingReviewStatus.APPROVED || r2.getStatus() == ListingReviewStatus.APPROVED;
            boolean oneRejected = r1.getStatus() == ListingReviewStatus.REJECTED || r2.getStatus() == ListingReviewStatus.REJECTED;
            boolean exactlyOneSucceeded = (r1.isSuccess() && !r2.isSuccess()) || (!r1.isSuccess() && r2.isSuccess());

            assertTrue(exactlyOneSucceeded, "Exactly one concurrent review action must succeed");
            assertTrue(oneApproved ^ oneRejected, "One action was approved XOR rejected");

            ListingReviewResult failedResult = r1.isSuccess() ? r2 : r1;
            assertEquals(ListingReviewStatus.NOT_PENDING_REVIEW, failedResult.getStatus());
        }

        @Test
        @DisplayName("Concurrent approval of multiple distinct listings completes atomically with 0 remaining in queue")
        void testConcurrentApprovalOfMultipleListings() throws InterruptedException {
            // Seed 10 new pending listings
            int numListings = 10;
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < numListings; i++) {
                Listing l = new Listing();
                l.setId("LCONCUR_" + i);
                l.setTitle("Concurrent Item " + i);
                l.setStatus(ListingStatus.PENDING_REVIEW);
                l.setSellerUsername("diana");
                dataService.addListing(l);
                ids.add(l.getId());
            }

            ExecutorService executor = Executors.newFixedThreadPool(numListings);
            CountDownLatch latch = new CountDownLatch(numListings);
            AtomicInteger successCount = new AtomicInteger(0);

            for (String id : ids) {
                executor.submit(() -> {
                    try {
                        ListingReviewResult res = reviewService.approveListing(id, "admin");
                        if (res.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(numListings, successCount.get());
            for (String id : ids) {
                assertEquals(ListingStatus.LIVE, dataService.findListing(id).getStatus());
            }
        }
    }

    @Test
    @DisplayName("RBAC: Unauthenticated caller throws SecurityException")
    void testUnauthenticatedAccessThrowsSecurityException() {
        SessionManager.logout();
        assertThrows(SecurityException.class, () -> reviewService.getPendingListingsOldestFirst());
        assertThrows(SecurityException.class, () -> reviewService.getListingDetails("L100"));
        assertThrows(SecurityException.class, () -> reviewService.approveListing("L100", "admin"));
        assertThrows(SecurityException.class, () -> reviewService.rejectListing("L100", "admin", "Reason"));
        assertThrows(SecurityException.class, () -> reviewService.reviewListing(ListingReviewRequest.approveRequest("L100", "admin")));
    }

    @Test
    @DisplayName("RBAC: Non-admin caller throws SecurityException")
    void testNonAdminAccessThrowsSecurityException() {
        SessionManager.login(dataService.findUser("alice"));
        assertThrows(SecurityException.class, () -> reviewService.getPendingListingsOldestFirst());
        assertThrows(SecurityException.class, () -> reviewService.getListingDetails("L100"));
        assertThrows(SecurityException.class, () -> reviewService.approveListing("L100", "admin"));
        assertThrows(SecurityException.class, () -> reviewService.rejectListing("L100", "admin", "Reason"));
        assertThrows(SecurityException.class, () -> reviewService.reviewListing(ListingReviewRequest.approveRequest("L100", "admin")));
    }
}
