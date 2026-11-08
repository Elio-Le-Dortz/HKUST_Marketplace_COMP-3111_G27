package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActOnListingService Tests (US-B05)")
class ActOnListingServiceTest {

    private DummyDataService dataService;
    private ActOnListingService actService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        actService = new ActOnListingService(dataService);
        SessionManager.login(dataService.findUser("alice"));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Nested
    @DisplayName("Basic Flow: Submit Price Offer (Subflow S1)")
    class BasicFlowPriceOfferTests {

        @Test
        @DisplayName("Successfully submits a price offer on a live SELL listing")
        void testSubmitPriceOfferSuccess() {
            // Find a live sell listing posted by charlie
            Listing sellListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.SELL && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            int initialOfferCount = dataService.getOffersForListing(sellListing.getId()).size();
            int initialNotifCount = dataService.getNotificationsFor(sellListing.getSellerUsername()).size();

            ActOnListingResult result = actService.submitPriceOffer(sellListing.getId(), "alice", 150.0);

            assertTrue(result.isSuccess());
            assertEquals(ActOnListingStatus.SUCCESS, result.getStatus());
            assertNotNull(result.getOffer());

            Offer offer = result.getOffer();
            assertEquals(OfferType.PRICE_OFFER, offer.getType());
            assertEquals(OfferStatus.PENDING, offer.getStatus());
            assertEquals(150.0, offer.getPrice());
            assertEquals("alice", offer.getBuyerUsername());
            assertEquals(sellListing.getSellerUsername(), offer.getSellerUsername());
            assertEquals(sellListing.getId(), offer.getListingId());
            assertNotNull(offer.getCreatedDate());

            // Check DummyDataService persistence
            assertEquals(initialOfferCount + 1, dataService.getOffersForListing(sellListing.getId()).size());

            // Check Seller notification (sorted newest first)
            List<Notification> sellerNotifs = dataService.getNotificationsFor(sellListing.getSellerUsername());
            assertEquals(initialNotifCount + 1, sellerNotifs.size());
            Notification latestNotif = sellerNotifs.get(0);
            assertEquals("New Offer Received", latestNotif.getTitle());
            assertTrue(latestNotif.getMessage().contains("alice"));
            assertTrue(latestNotif.getMessage().contains("150.00"));
            assertEquals(offer.getId(), latestNotif.getRelatedItemId());
            assertEquals(NotificationTargetType.OFFER.name(), latestNotif.getRelatedItemType());
        }

        @Test
        @DisplayName("F20: Seller can open notification from price offer deep link successfully")
        void testSellerDeepLinkFromOfferNotification() {
            Listing sellListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.SELL && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            ActOnListingResult result = actService.submitPriceOffer(sellListing.getId(), "alice", 150.0);
            assertTrue(result.isSuccess());
            Notification notif = dataService.getNotificationsFor(sellListing.getSellerUsername()).get(0);

            SellerNotificationService sellerNotifService = new SellerNotificationService(dataService);
            Seller seller = (Seller) dataService.findUser(sellListing.getSellerUsername());
            SellerNotificationResult openResult = SessionManager.withSession(seller, () ->
                    sellerNotifService.openNotification(notif.getId()));

            assertTrue(openResult.isSuccess());
            assertNotNull(openResult.getOffer());
            assertEquals(result.getOffer().getId(), openResult.getOffer().getId());
        }

        @Test
        @DisplayName("Default constructor instantiates service with singleton DummyDataService")
        void testDefaultConstructor() {
            ActOnListingService defaultService = new ActOnListingService();
            assertNotNull(defaultService);
        }
    }

    @Nested
    @DisplayName("Basic Flow: Submit Borrow Request (Subflow S2)")
    class BasicFlowBorrowRequestTests {

        @Test
        @DisplayName("Successfully submits a borrow request on a live LEND listing with valid duration (>= 5s)")
        void testSubmitBorrowRequestSuccess() {
            // Find a live lend listing
            Listing lendListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.LEND && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            LocalDateTime start = LocalDateTime.now().plusMinutes(1);
            LocalDateTime end = start.plusDays(7);

            int initialOfferCount = dataService.getOffersForListing(lendListing.getId()).size();
            int initialNotifCount = dataService.getNotificationsFor(lendListing.getSellerUsername()).size();

            ActOnListingResult result = actService.submitBorrowRequest(lendListing.getId(), "alice", start, end);

            assertTrue(result.isSuccess());
            assertEquals(ActOnListingStatus.SUCCESS, result.getStatus());
            assertNotNull(result.getOffer());

            Offer offer = result.getOffer();
            assertEquals(OfferType.BORROW_REQUEST, offer.getType());
            assertEquals(OfferStatus.PENDING, offer.getStatus());
            assertEquals(start, offer.getStartDate());
            assertEquals(end, offer.getEndDate());
            assertEquals("alice", offer.getBuyerUsername());
            assertEquals(lendListing.getSellerUsername(), offer.getSellerUsername());

            // Verify persistence and notification
            assertEquals(initialOfferCount + 1, dataService.getOffersForListing(lendListing.getId()).size());
            List<Notification> sellerNotifs = dataService.getNotificationsFor(lendListing.getSellerUsername());
            assertEquals(initialNotifCount + 1, sellerNotifs.size());
            assertEquals("Borrow Request Received", sellerNotifs.get(0).getTitle());
            assertEquals(offer.getId(), sellerNotifs.get(0).getRelatedItemId());
            assertEquals(NotificationTargetType.OFFER.name(), sellerNotifs.get(0).getRelatedItemType());
        }

        @Test
        @DisplayName("Submits borrow request with minimum allowed duration (exactly 5 seconds)")
        void testSubmitBorrowRequestMinimumFiveSeconds() {
            Listing lendListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.LEND)
                    .findFirst()
                    .orElseThrow();

            LocalDateTime start = LocalDateTime.now();
            LocalDateTime end = start.plusSeconds(5);

            ActOnListingResult result = actService.submitBorrowRequest(lendListing.getId(), "alice", start, end);
            assertTrue(result.isSuccess());
            assertEquals(ActOnListingStatus.SUCCESS, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Basic Flow: Atomic Claim Giveaway Item (Subflow S3)")
    class BasicFlowClaimItemTests {

        @Test
        @DisplayName("Successfully claims a live GIVEAWAY listing")
        void testSubmitClaimSuccess() {
            // Find a live giveaway listing
            Listing giveaway = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.GIVEAWAY && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            int initialOfferCount = dataService.getOffersForListing(giveaway.getId()).size();
            int initialNotifCount = dataService.getNotificationsFor(giveaway.getSellerUsername()).size();

            ActOnListingResult result = actService.submitClaim(giveaway.getId(), "alice");

            assertTrue(result.isSuccess());
            assertEquals(ActOnListingStatus.SUCCESS, result.getStatus());
            assertNotNull(result.getOffer());

            Offer offer = result.getOffer();
            assertEquals(OfferType.CLAIM, offer.getType());
            assertEquals(OfferStatus.PENDING, offer.getStatus());
            assertEquals("alice", offer.getBuyerUsername());
            assertEquals(giveaway.getSellerUsername(), offer.getSellerUsername());

            assertEquals(initialOfferCount + 1, dataService.getOffersForListing(giveaway.getId()).size());
            List<Notification> notifs = dataService.getNotificationsFor(giveaway.getSellerUsername());
            assertEquals(initialNotifCount + 1, notifs.size());
            assertEquals(offer.getId(), notifs.get(0).getRelatedItemId());
            assertEquals(NotificationTargetType.OFFER.name(), notifs.get(0).getRelatedItemType());
            assertEquals("Giveaway Claim Received", notifs.get(0).getTitle());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A1: Listing No Longer Available")
    class AlternativeFlowA1Tests {

        @Test
        @DisplayName("Fails when listing ID is null or empty")
        void testListingNotFoundInvalidId() {
            ActOnListingResult r1 = actService.submitPriceOffer(null, "alice", 100.0);
            assertFalse(r1.isSuccess());
            assertEquals(ActOnListingStatus.LISTING_NOT_FOUND, r1.getStatus());

            ActOnListingResult r2 = actService.submitBorrowRequest("", "alice", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
            assertFalse(r2.isSuccess());
            assertEquals(ActOnListingStatus.LISTING_NOT_FOUND, r2.getStatus());

            ActOnListingResult r3 = actService.submitClaim("   ", "alice");
            assertFalse(r3.isSuccess());
            assertEquals(ActOnListingStatus.LISTING_NOT_FOUND, r3.getStatus());
        }

        @Test
        @DisplayName("Fails when listing does not exist in store")
        void testListingNotFoundNonExistent() {
            ActOnListingResult result = actService.submitPriceOffer("NON_EXISTENT_ID", "alice", 100.0);
            assertFalse(result.isSuccess());
            assertEquals(ActOnListingStatus.LISTING_NOT_FOUND, result.getStatus());
        }

        @Test
        @DisplayName("Fails when listing status is not LIVE (e.g., PENDING_REVIEW, RESERVED, SOLD, GIVEN, REMOVED)")
        void testListingNotLiveStatus() {
            Listing listing = dataService.findListing("L101");
            assertNotNull(listing);

            ListingStatus[] nonLiveStatuses = {
                    ListingStatus.PENDING_REVIEW,
                    ListingStatus.RESERVED,
                    ListingStatus.SOLD,
                    ListingStatus.GIVEN,
                    ListingStatus.REMOVED
            };

            for (ListingStatus status : nonLiveStatuses) {
                listing.setStatus(status);

                ActOnListingResult rSell = actService.submitPriceOffer(listing.getId(), "alice", 100.0);
                assertFalse(rSell.isSuccess(), "Should fail when listing is " + status);
                assertEquals(ActOnListingStatus.LISTING_NOT_LIVE, rSell.getStatus());

                ActOnListingResult rLend = actService.submitBorrowRequest(listing.getId(), "alice",
                        LocalDateTime.now(), LocalDateTime.now().plusDays(1));
                assertFalse(rLend.isSuccess());
                assertEquals(ActOnListingStatus.LISTING_NOT_LIVE, rLend.getStatus());

                ActOnListingResult rClaim = actService.submitClaim(listing.getId(), "alice");
                assertFalse(rClaim.isSuccess());
                assertEquals(ActOnListingStatus.LISTING_NOT_LIVE, rClaim.getStatus());
            }
        }
    }

    @Nested
    @DisplayName("Alternative Flow A2: Invalid Offer Price")
    class AlternativeFlowA2Tests {

        @Test
        @DisplayName("Fails when offer price is null, zero, negative, NaN or infinite")
        void testInvalidPrices() {
            Listing sellListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.SELL && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            Double[] invalidPrices = {null, 0.0, -0.01, -100.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

            for (Double price : invalidPrices) {
                ActOnListingResult result = actService.submitPriceOffer(sellListing.getId(), "alice", price);
                assertFalse(result.isSuccess(), "Price " + price + " should be rejected");
                assertEquals(ActOnListingStatus.INVALID_PRICE, result.getStatus());
            }
        }

        @Test
        @DisplayName("Succeeds for smallest positive boundary price ($0.01)")
        void testMinimumPositivePrice() {
            Listing sellListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.SELL && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            ActOnListingResult result = actService.submitPriceOffer(sellListing.getId(), "alice", 0.01);
            assertTrue(result.isSuccess());
            assertEquals(0.01, result.getOffer().getPrice());
        }

        @Test
        @DisplayName("Unit tests for validatePrice helper method")
        void testValidatePriceHelper() {
            assertEquals("Offer price cannot be empty.", actService.validatePrice(null));
            assertEquals("Offer price must be a valid number.", actService.validatePrice(Double.NaN));
            assertEquals("Offer price must be a valid number.", actService.validatePrice(Double.POSITIVE_INFINITY));
            assertEquals("Offer price must be a positive amount.", actService.validatePrice(0.0));
            assertEquals("Offer price must be a positive amount.", actService.validatePrice(-5.0));
            assertNull(actService.validatePrice(10.0));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A3: Invalid Borrow Dates")
    class AlternativeFlowA3Tests {

        @Test
        @DisplayName("Fails when start date or end date is null")
        void testNullBorrowDates() {
            Listing lendListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.LEND && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            ActOnListingResult r1 = actService.submitBorrowRequest(lendListing.getId(), "alice", null, LocalDateTime.now().plusDays(1));
            assertFalse(r1.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_BORROW_DATES, r1.getStatus());

            ActOnListingResult r2 = actService.submitBorrowRequest(lendListing.getId(), "alice", LocalDateTime.now(), null);
            assertFalse(r2.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_BORROW_DATES, r2.getStatus());
        }

        @Test
        @DisplayName("Fails when start date is in the past (> 30 seconds ago)")
        void testStartDateInPast() {
            Listing lendListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.LEND && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            LocalDateTime pastStart = LocalDateTime.now().minusHours(2);
            LocalDateTime futureEnd = LocalDateTime.now().plusDays(1);

            ActOnListingResult result = actService.submitBorrowRequest(lendListing.getId(), "alice", pastStart, futureEnd);
            assertFalse(result.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_BORROW_DATES, result.getStatus());
            assertTrue(result.getMessage().contains("cannot be in the past"));
        }

        @Test
        @DisplayName("Fails when end date is equal to or before start date")
        void testEndDateNotAfterStartDate() {
            Listing lendListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.LEND && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            LocalDateTime start = LocalDateTime.now().plusDays(1);

            // Equal dates
            ActOnListingResult rEqual = actService.submitBorrowRequest(lendListing.getId(), "alice", start, start);
            assertFalse(rEqual.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_BORROW_DATES, rEqual.getStatus());
            assertTrue(rEqual.getMessage().contains("must be after start date"));

            // End date before start date
            ActOnListingResult rBefore = actService.submitBorrowRequest(lendListing.getId(), "alice", start, start.minusHours(1));
            assertFalse(rBefore.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_BORROW_DATES, rBefore.getStatus());
            assertTrue(rBefore.getMessage().contains("must be after start date"));
        }

        @Test
        @DisplayName("Fails when borrow duration is strictly less than 5 seconds")
        void testBorrowDurationLessThanFiveSeconds() {
            Listing lendListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.LEND && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            LocalDateTime start = LocalDateTime.now().plusMinutes(1);

            // 0 seconds, 1 second, 4 seconds
            for (long secs : new long[]{0, 1, 2, 3, 4}) {
                LocalDateTime end = start.plusSeconds(secs);
                ActOnListingResult result = actService.submitBorrowRequest(lendListing.getId(), "alice", start, end);
                assertFalse(result.isSuccess(), "Duration of " + secs + "s should fail");
                assertEquals(ActOnListingStatus.INVALID_BORROW_DATES, result.getStatus());
            }
        }

        @Test
        @DisplayName("Unit tests for validateBorrowDates helper method")
        void testValidateBorrowDatesHelper() {
            LocalDateTime now = LocalDateTime.now();

            assertEquals("Start date and end date are required.", actService.validateBorrowDates(null, now.plusDays(1), now));
            assertEquals("Start date and end date are required.", actService.validateBorrowDates(now, null, now));

            // Past date (tolerance is 30s)
            assertEquals("Borrow start date cannot be in the past.",
                    actService.validateBorrowDates(now.minusSeconds(35), now.plusDays(1), now));

            // End not after start
            assertEquals("Borrow end date must be after start date.",
                    actService.validateBorrowDates(now, now, now));
            assertEquals("Borrow end date must be after start date.",
                    actService.validateBorrowDates(now, now.minusSeconds(1), now));

            // Duration < 5s
            assertEquals("Borrow duration must be at least 5 seconds.",
                    actService.validateBorrowDates(now, now.plusSeconds(4), now));

            // Valid duration >= 5s
            assertNull(actService.validateBorrowDates(now, now.plusSeconds(5), now));
            assertNull(actService.validateBorrowDates(now, now.plusSeconds(10), now));

            // When now is null
            assertNull(actService.validateBorrowDates(now, now.plusSeconds(10), null));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A4: Giveaway Already Claimed & Concurrency")
    class AlternativeFlowA4Tests {

        @Test
        @DisplayName("Sequential claim: first buyer succeeds, second buyer is rejected")
        void testSequentialClaimRejection() {
            Listing giveaway = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.GIVEAWAY && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                    .findFirst()
                    .orElseThrow();

            ActOnListingResult firstClaim = actService.submitClaim(giveaway.getId(), "alice");
            assertTrue(firstClaim.isSuccess());

            SessionManager.login(dataService.findUser("bob"));
            ActOnListingResult secondClaim = actService.submitClaim(giveaway.getId(), "bob");
            assertFalse(secondClaim.isSuccess());
            assertEquals(ActOnListingStatus.ALREADY_CLAIMED, secondClaim.getStatus());
            assertEquals("This giveaway item has already been claimed.", secondClaim.getMessage());
        }

        @Test
        @DisplayName("Concurrent claim race condition: 10 threads claim simultaneously, exactly 1 succeeds")
        void testConcurrentGiveawayClaims() throws InterruptedException {
            Listing giveaway = new Listing();
            giveaway.setTitle("Free Concurrency Item");
            giveaway.setType(ListingType.GIVEAWAY);
            giveaway.setStatus(ListingStatus.LIVE);
            giveaway.setSellerUsername("seller_concurrency");
            dataService.addListing(giveaway);

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);
            List<ActOnListingResult> results = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final String buyer = "buyer_" + i;
                Buyer b = new Buyer(buyer, buyer, "h");
                dataService.registerUserIfAbsent(b);
                executor.submit(() -> {
                    SessionManager.login(b);
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        ActOnListingResult result = actService.submitClaim(giveaway.getId(), buyer);
                        synchronized (results) {
                            results.add(result);
                        }
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else if (result.getStatus() == ActOnListingStatus.ALREADY_CLAIMED) {
                            rejectedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown(); // Release all threads at once
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(1, successCount.get(), "Exactly 1 thread must succeed claiming the giveaway");
            assertEquals(threadCount - 1, rejectedCount.get(), "All other concurrent attempts must be rejected");
            assertEquals(threadCount, results.size());
        }

        @Test
        @DisplayName("isGiveawayClaimed returns true when listing is not live or has accepted claim")
        void testIsGiveawayClaimedVariations() {
            assertFalse(actService.isGiveawayClaimed(null));
            assertFalse(actService.isGiveawayClaimed("NON_EXISTENT"));

            Listing giveaway = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.GIVEAWAY)
                    .findFirst()
                    .orElseThrow();

            assertFalse(actService.isGiveawayClaimed(giveaway.getId()));

            // Add non-claim offer
            Offer priceOffer = new Offer("O901", OfferType.PRICE_OFFER, giveaway.getId(), "alice", "seller");
            priceOffer.setStatus(OfferStatus.PENDING);
            dataService.addOffer(priceOffer);
            assertFalse(actService.isGiveawayClaimed(giveaway.getId()));

            // Add declined claim offer
            Offer declinedClaim = new Offer("O902", OfferType.CLAIM, giveaway.getId(), "alice", "seller");
            declinedClaim.setStatus(OfferStatus.DECLINED);
            dataService.addOffer(declinedClaim);
            assertFalse(actService.isGiveawayClaimed(giveaway.getId()));

            // Add accepted claim offer
            Offer acceptedClaim = new Offer("O903", OfferType.CLAIM, giveaway.getId(), "bob", "seller");
            acceptedClaim.setStatus(OfferStatus.ACCEPTED);
            dataService.addOffer(acceptedClaim);
            assertTrue(actService.isGiveawayClaimed(giveaway.getId()));

            giveaway.setStatus(ListingStatus.GIVEN);
            assertTrue(actService.isGiveawayClaimed(giveaway.getId()));
        }

        @Test
        @DisplayName("Submit offer on listing with null sellerUsername does not throw exception")
        void testListingWithNullSellerUsername() {
            Listing sellListing = new Listing();
            sellListing.setTitle("Anonymous Seller Listing");
            sellListing.setType(ListingType.SELL);
            sellListing.setStatus(ListingStatus.LIVE);
            sellListing.setSellerUsername(null);
            dataService.addListing(sellListing);

            ActOnListingResult result = actService.submitPriceOffer(sellListing.getId(), "alice", 10.0);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Validation, Role & Mismatched Listing Type Tests")
    class ValidationAndEdgeCaseTests {

        @Test
        @DisplayName("Fails when buyer username is empty, null or whitespace")
        void testInvalidBuyerUsername() {
            Listing listing = dataService.getLiveListings().get(0);

            for (String username : new String[]{null, "", "   "}) {
                ActOnListingResult r1 = actService.submitPriceOffer(listing.getId(), username, 100.0);
                assertFalse(r1.isSuccess());
                assertEquals(ActOnListingStatus.INVALID_USER, r1.getStatus());

                ActOnListingResult r2 = actService.submitBorrowRequest(listing.getId(), username,
                        LocalDateTime.now(), LocalDateTime.now().plusDays(1));
                assertFalse(r2.isSuccess());
                assertEquals(ActOnListingStatus.INVALID_USER, r2.getStatus());

                ActOnListingResult r3 = actService.submitClaim(listing.getId(), username);
                assertFalse(r3.isSuccess());
                assertEquals(ActOnListingStatus.INVALID_USER, r3.getStatus());
            }
        }

        @Test
        @DisplayName("Fails when seller attempts to make offer, borrow request, or claim on their own listing")
        void testSelfActionNotAllowed() {
            Listing sellListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.SELL)
                    .findFirst()
                    .orElseThrow();
            sellListing.setSellerUsername("alice");

            ActOnListingResult rOffer = actService.submitPriceOffer(sellListing.getId(),
                    "alice", 100.0);
            assertFalse(rOffer.isSuccess());
            assertEquals(ActOnListingStatus.SELF_ACTION_NOT_ALLOWED, rOffer.getStatus());

            Listing lendListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.LEND)
                    .findFirst()
                    .orElseThrow();
            lendListing.setSellerUsername("alice");

            ActOnListingResult rBorrow = actService.submitBorrowRequest(lendListing.getId(),
                    "alice", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
            assertFalse(rBorrow.isSuccess());
            assertEquals(ActOnListingStatus.SELF_ACTION_NOT_ALLOWED, rBorrow.getStatus());

            Listing giveawayListing = dataService.getLiveListings().stream()
                    .filter(l -> l.getType() == ListingType.GIVEAWAY)
                    .findFirst()
                    .orElseThrow();
            giveawayListing.setSellerUsername("alice");

            ActOnListingResult rClaim = actService.submitClaim(giveawayListing.getId(),
                    "alice");
            assertFalse(rClaim.isSuccess());
            assertEquals(ActOnListingStatus.SELF_ACTION_NOT_ALLOWED, rClaim.getStatus());
        }

        @Test
        @DisplayName("F03 regression: submitting offer enforces active buyer session")
        void testSubmitOfferEnforcesBuyerSession() {
            SessionManager.login(dataService.findUser("diana")); // a Seller session
            assertThrows(SecurityException.class, () -> actService.submitPriceOffer("L105", "alice", 10.0));
            SessionManager.logout();
            assertThrows(SecurityException.class, () -> actService.submitPriceOffer("L105", "alice", 10.0));
        }

        @Test
        @DisplayName("Fails when action does not match listing type")
        void testMismatchedListingType() {
            Listing sell = dataService.getLiveListings().stream().filter(l -> l.getType() == ListingType.SELL).findFirst().orElseThrow();
            Listing lend = dataService.getLiveListings().stream().filter(l -> l.getType() == ListingType.LEND).findFirst().orElseThrow();
            Listing giveaway = dataService.getLiveListings().stream().filter(l -> l.getType() == ListingType.GIVEAWAY).findFirst().orElseThrow();

            // Price offer on LEND or GIVEAWAY
            ActOnListingResult r1 = actService.submitPriceOffer(lend.getId(), "alice", 50.0);
            assertFalse(r1.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_LISTING_TYPE, r1.getStatus());

            ActOnListingResult r2 = actService.submitPriceOffer(giveaway.getId(), "alice", 50.0);
            assertFalse(r2.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_LISTING_TYPE, r2.getStatus());

            // Borrow request on SELL or GIVEAWAY
            ActOnListingResult r3 = actService.submitBorrowRequest(sell.getId(), "alice", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
            assertFalse(r3.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_LISTING_TYPE, r3.getStatus());

            ActOnListingResult r4 = actService.submitBorrowRequest(giveaway.getId(), "alice", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
            assertFalse(r4.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_LISTING_TYPE, r4.getStatus());

            // Claim on SELL or LEND
            ActOnListingResult r5 = actService.submitClaim(sell.getId(), "alice");
            assertFalse(r5.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_LISTING_TYPE, r5.getStatus());

            ActOnListingResult r6 = actService.submitClaim(lend.getId(), "alice");
            assertFalse(r6.isSuccess());
            assertEquals(ActOnListingStatus.INVALID_LISTING_TYPE, r6.getStatus());
        }
    }
}
