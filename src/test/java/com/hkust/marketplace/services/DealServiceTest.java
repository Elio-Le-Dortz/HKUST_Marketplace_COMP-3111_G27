package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DealService Unit & Concurrency Tests (US-B07)")
class DealServiceTest {

    private DummyDataService dataService;
    private DealService dealService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        dealService = new DealService(dataService);
    }

    @Nested
    @DisplayName("Subflow S1: Withdraw Offer Tests")
    class WithdrawOfferTests {

        @Test
        @DisplayName("Withdraw pending offer updates status to WITHDRAWN and notifies parties (Subflow S1)")
        void testWithdrawPendingOfferSuccess() {
            // Seed offer O1 is pending on l7 by alice to charlie
            List<Offer> buyerOffers = dataService.getOffersForBuyer("alice");
            Offer pending = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.PENDING)
                    .findFirst()
                    .orElseThrow();

            Listing listing = dataService.findListing(pending.getListingId());
            assertEquals(ListingStatus.LIVE, listing.getStatus());

            int prevNotifCount = dataService.getAllNotifications().size();

            DealOperationResult result = dealService.withdrawOffer(pending.getId(), "alice");

            assertTrue(result.isSuccess());
            assertEquals(DealOperationStatus.SUCCESS, result.getStatus());
            assertEquals(OfferStatus.WITHDRAWN, pending.getStatus());
            assertEquals(ListingStatus.LIVE, listing.getStatus(), "Listing must remain Live");

            // Verify notifications sent to seller and buyer (Subflow S1 Steps 4 & 5)
            List<Notification> newNotifs = dataService.getAllNotifications();
            assertTrue(newNotifs.size() >= prevNotifCount + 2);
            assertTrue(newNotifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("charlie")
                    && n.getTitle().contains("Offer Withdrawn")));
            assertTrue(newNotifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("alice")
                    && n.getTitle().contains("Offer Withdrawn")));
        }

        @Test
        @DisplayName("Alternative Flow A2: Cannot withdraw an offer that is no longer pending")
        void testWithdrawOfferNoLongerPending() {
            List<Offer> buyerOffers = dataService.getOffersForBuyer("alice");
            Offer pending = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.PENDING)
                    .findFirst()
                    .orElseThrow();

            // First withdraw succeeds
            DealOperationResult first = dealService.withdrawOffer(pending.getId(), "alice");
            assertTrue(first.isSuccess());

            // Second withdraw attempt encounters Alternative Flow A2
            DealOperationResult second = dealService.withdrawOffer(pending.getId(), "alice");
            assertFalse(second.isSuccess());
            assertEquals(DealOperationStatus.OFFER_NOT_PENDING, second.getStatus());
            assertTrue(second.getMessage().contains("no longer pending"));
        }

        @Test
        @DisplayName("Exception flows: Offer not found, unauthorized, and empty arguments")
        void testWithdrawOfferExceptions() {
            DealOperationResult missing = dealService.withdrawOffer("NON_EXISTENT", "alice");
            assertFalse(missing.isSuccess());
            assertEquals(DealOperationStatus.OFFER_NOT_FOUND, missing.getStatus());

            List<Offer> buyerOffers = dataService.getOffersForBuyer("alice");
            Offer pending = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.PENDING)
                    .findFirst()
                    .orElseThrow();

            // Unauthorized user
            DealOperationResult unauthorized = dealService.withdrawOffer(pending.getId(), "bob");
            assertFalse(unauthorized.isSuccess());
            assertEquals(DealOperationStatus.UNAUTHORIZED, unauthorized.getStatus());

            // Null/blank inputs
            assertFalse(dealService.withdrawOffer(null, "alice").isSuccess());
            assertFalse(dealService.withdrawOffer("", "alice").isSuccess());
            assertFalse(dealService.withdrawOffer(pending.getId(), null).isSuccess());
            assertFalse(dealService.withdrawOffer(pending.getId(), "   ").isSuccess());
        }
    }

    @Nested
    @DisplayName("Subflow S2: Accept Counter Offer Tests")
    class AcceptCounterOfferTests {

        @Test
        @DisplayName("Accept counter offer for SELL listing creates active Deal and declines competing offers (Subflow S2)")
        void testAcceptCounterOfferSellSuccess() {
            // alice has countered offer o2 on l2 (seller: charlie)
            List<Offer> buyerOffers = dataService.getOffersForBuyer("alice");
            Offer countered = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.COUNTERED)
                    .findFirst()
                    .orElseThrow();

            Listing listing = dataService.findListing(countered.getListingId());
            assertEquals(ListingStatus.LIVE, listing.getStatus());

            // Add a competing pending offer from bob on the same listing
            Offer competing = new Offer();
            competing.setType(OfferType.PRICE_OFFER);
            competing.setListingId(listing.getId());
            competing.setListingTitle(listing.getTitle());
            competing.setBuyerUsername("bob");
            competing.setSellerUsername(listing.getSellerUsername());
            competing.setPrice(190.0);
            competing.setStatus(OfferStatus.PENDING);
            dataService.addOffer(competing);

            DealOperationResult result = dealService.acceptCounterOffer(countered.getId(), "alice");

            assertTrue(result.isSuccess());
            assertEquals(DealOperationStatus.SUCCESS, result.getStatus());
            assertNotNull(result.getDeal());
            assertEquals(DealType.SALE, result.getDeal().getType());
            assertEquals(DealStatus.ACTIVE, result.getDeal().getStatus());
            assertEquals(220.0, result.getDeal().getOfferPrice());
            assertEquals(ListingStatus.RESERVED, listing.getStatus());
            assertEquals(OfferStatus.ACCEPTED, countered.getStatus());

            // Subflow S2 Step 4: Competing pending offer must be declined
            assertEquals(OfferStatus.DECLINED, competing.getStatus());
            assertTrue(competing.getDeclineReason().contains("reserved"));

            // Notifications sent to bob, charlie, and alice
            List<Notification> notifs = dataService.getAllNotifications();
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("bob")
                    && n.getTitle().contains("Offer Declined")));
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("charlie")
                    && n.getTitle().contains("Counter Offer Accepted")));
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("alice")
                    && n.getTitle().contains("Counter Offer Accepted")));
        }

        @Test
        @DisplayName("Accept counter offer for LEND listing sets listing status to LENT and records due date")
        void testAcceptCounterOfferLendSuccess() {
            Listing lendListing = new Listing();
            lendListing.setTitle("Camera Kit");
            lendListing.setType(ListingType.LEND);
            lendListing.setCategory("Electronics");
            lendListing.setSellerUsername("diana");
            lendListing.setStatus(ListingStatus.LIVE);
            dataService.addListing(lendListing);

            Offer lendOffer = new Offer();
            lendOffer.setType(OfferType.BORROW_REQUEST);
            lendOffer.setListingId(lendListing.getId());
            lendOffer.setListingTitle(lendListing.getTitle());
            lendOffer.setBuyerUsername("alice");
            lendOffer.setSellerUsername("diana");
            lendOffer.setStatus(OfferStatus.COUNTERED);

            Offer counterOffer = new Offer();
            counterOffer.setType(OfferType.BORROW_REQUEST);
            LocalDateTime start = LocalDateTime.now();
            LocalDateTime end = start.plusDays(7);
            counterOffer.setStartDate(start);
            counterOffer.setEndDate(end);
            lendOffer.setCounterOffer(counterOffer);
            dataService.addOffer(lendOffer);

            DealOperationResult result = dealService.acceptCounterOffer(lendOffer.getId(), "alice");

            assertTrue(result.isSuccess());
            assertEquals(DealType.LOAN, result.getDeal().getType());
            assertEquals(ListingStatus.LENT, lendListing.getStatus());
            assertEquals(end, result.getDeal().getDueDate());
        }

        @Test
        @DisplayName("Accept counter offer for GIVEAWAY listing sets listing status to RESERVED")
        void testAcceptCounterOfferGiveawaySuccess() {
            Listing giveaway = new Listing();
            giveaway.setTitle("Old Desk");
            giveaway.setType(ListingType.GIVEAWAY);
            giveaway.setCategory("Furniture");
            giveaway.setSellerUsername("diana");
            giveaway.setStatus(ListingStatus.LIVE);
            dataService.addListing(giveaway);

            Offer claimOffer = new Offer();
            claimOffer.setType(OfferType.CLAIM);
            claimOffer.setListingId(giveaway.getId());
            claimOffer.setListingTitle(giveaway.getTitle());
            claimOffer.setBuyerUsername("alice");
            claimOffer.setSellerUsername("diana");
            claimOffer.setStatus(OfferStatus.COUNTERED);
            dataService.addOffer(claimOffer);

            DealOperationResult result = dealService.acceptCounterOffer(claimOffer.getId(), "alice");

            assertTrue(result.isSuccess());
            assertEquals(DealType.GIVEAWAY, result.getDeal().getType());
            assertEquals(ListingStatus.RESERVED, giveaway.getStatus());
        }

        @Test
        @DisplayName("Alternative Flow A3: Cannot accept counter offer if listing is no longer Live")
        void testAcceptCounterOfferListingNotLive() {
            List<Offer> buyerOffers = dataService.getOffersForBuyer("alice");
            Offer countered = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.COUNTERED)
                    .findFirst()
                    .orElseThrow();

            Listing listing = dataService.findListing(countered.getListingId());
            listing.setStatus(ListingStatus.REMOVED);

            DealOperationResult result = dealService.acceptCounterOffer(countered.getId(), "alice");
            assertFalse(result.isSuccess());
            assertEquals(DealOperationStatus.COUNTER_NOT_VALID, result.getStatus());
            assertTrue(result.getMessage().contains("no longer available"));
        }

        @Test
        @DisplayName("Exception flows: Offer not found, unauthorized, and not in countered status")
        void testAcceptCounterExceptions() {
            assertFalse(dealService.acceptCounterOffer("NO_ID", "alice").isSuccess());

            List<Offer> buyerOffers = dataService.getOffersForBuyer("alice");
            Offer countered = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.COUNTERED)
                    .findFirst()
                    .orElseThrow();

            // Unauthorized
            DealOperationResult unauth = dealService.acceptCounterOffer(countered.getId(), "bob");
            assertFalse(unauth.isSuccess());
            assertEquals(DealOperationStatus.UNAUTHORIZED, unauth.getStatus());

            // Offer not in countered status
            Offer pending = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.PENDING)
                    .findFirst()
                    .orElseThrow();
            DealOperationResult notCountered = dealService.acceptCounterOffer(pending.getId(), "alice");
            assertFalse(notCountered.isSuccess());
            assertEquals(DealOperationStatus.COUNTER_NOT_FOUND, notCountered.getStatus());
        }
    }

    @Nested
    @DisplayName("Subflow S3: Decline Counter Offer Tests")
    class DeclineCounterOfferTests {

        @Test
        @DisplayName("Decline counter offer marks offer DECLINED and listing remains Live (Subflow S3)")
        void testDeclineCounterOfferSuccess() {
            List<Offer> buyerOffers = dataService.getOffersForBuyer("alice");
            Offer countered = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.COUNTERED)
                    .findFirst()
                    .orElseThrow();

            Listing listing = dataService.findListing(countered.getListingId());
            assertEquals(ListingStatus.LIVE, listing.getStatus());

            DealOperationResult result = dealService.declineCounterOffer(countered.getId(), "alice");

            assertTrue(result.isSuccess());
            assertEquals(OfferStatus.DECLINED, countered.getStatus());
            assertEquals(OfferStatus.DECLINED, countered.getCounterOffer().getStatus());
            assertEquals(ListingStatus.LIVE, listing.getStatus(), "Listing must remain Live");

            List<Notification> notifs = dataService.getAllNotifications();
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("charlie")
                    && n.getTitle().contains("Counter Offer Declined")));
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("alice")
                    && n.getTitle().contains("Counter Offer Declined")));
        }

        @Test
        @DisplayName("Exception flows: unauthorized, not found, not countered")
        void testDeclineCounterExceptions() {
            assertFalse(dealService.declineCounterOffer("UNKNOWN", "alice").isSuccess());

            List<Offer> buyerOffers = dataService.getOffersForBuyer("alice");
            Offer countered = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.COUNTERED)
                    .findFirst()
                    .orElseThrow();

            assertEquals(DealOperationStatus.UNAUTHORIZED,
                    dealService.declineCounterOffer(countered.getId(), "bob").getStatus());

            Offer pending = buyerOffers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.PENDING)
                    .findFirst()
                    .orElseThrow();
            assertEquals(DealOperationStatus.COUNTER_NOT_FOUND,
                    dealService.declineCounterOffer(pending.getId(), "alice").getStatus());
        }
    }

    @Nested
    @DisplayName("Subflow S4: Self Return Item Tests")
    class SelfReturnItemTests {

        @Test
        @DisplayName("Self return active loan marks deal RETURNED and restores listing to LIVE (Subflow S4)")
        void testSelfReturnSuccess() {
            // Seed deal d2 is active loan on l4 ("Graphing Calculator TI-84") by alice to diana
            List<Deal> buyerDeals = dataService.getDealsForBuyer("alice");
            Deal loanDeal = buyerDeals.stream()
                    .filter(d -> d.getType() == DealType.LOAN && d.getStatus() == DealStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow();

            Listing listing = dataService.findListing(loanDeal.getListingId());
            listing.setStatus(ListingStatus.LENT);

            DealOperationResult result = dealService.selfReturnItem(loanDeal.getId(), "alice");

            assertTrue(result.isSuccess());
            assertEquals(DealStatus.RETURNED, loanDeal.getStatus());
            assertNotNull(loanDeal.getClosedDate());
            assertEquals(ListingStatus.LIVE, listing.getStatus(), "Listing must be restored to Live");

            List<Notification> notifs = dataService.getAllNotifications();
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("diana")
                    && n.getTitle().contains("Item Returned")));
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("alice")
                    && n.getTitle().contains("Item Returned")));
        }

        @Test
        @DisplayName("Cannot self-return a non-loan deal (e.g. Sale deal)")
        void testSelfReturnNonLoanFails() {
            List<Deal> buyerDeals = dataService.getDealsForBuyer("alice");
            Deal saleDeal = buyerDeals.stream()
                    .filter(d -> d.getType() == DealType.SALE && d.getStatus() == DealStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow();

            DealOperationResult result = dealService.selfReturnItem(saleDeal.getId(), "alice");
            assertFalse(result.isSuccess());
            assertEquals(DealOperationStatus.DEAL_NOT_BORROW, result.getStatus());
        }

        @Test
        @DisplayName("Exception flows: deal not found, unauthorized, deal not active")
        void testSelfReturnExceptions() {
            assertFalse(dealService.selfReturnItem("BAD_ID", "alice").isSuccess());

            List<Deal> buyerDeals = dataService.getDealsForBuyer("alice");
            Deal loanDeal = buyerDeals.stream()
                    .filter(d -> d.getType() == DealType.LOAN && d.getStatus() == DealStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow();

            assertEquals(DealOperationStatus.UNAUTHORIZED,
                    dealService.selfReturnItem(loanDeal.getId(), "bob").getStatus());

            loanDeal.setStatus(DealStatus.RETURNED);
            assertEquals(DealOperationStatus.DEAL_NOT_ACTIVE,
                    dealService.selfReturnItem(loanDeal.getId(), "alice").getStatus());
        }
    }

    @Nested
    @DisplayName("Subflow S5: Mark Deal Complete Tests")
    class MarkDealCompleteTests {

        @Test
        @DisplayName("Mark complete on SALE deal updates status to SOLD, completes deal, prompts rating (Subflow S5)")
        void testMarkCompleteSaleSuccess() {
            List<Deal> buyerDeals = dataService.getDealsForBuyer("alice");
            Deal saleDeal = buyerDeals.stream()
                    .filter(d -> d.getType() == DealType.SALE && d.getStatus() == DealStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow();

            Listing listing = dataService.findListing(saleDeal.getListingId());
            listing.setStatus(ListingStatus.RESERVED);

            DealOperationResult result = dealService.markDealComplete(saleDeal.getId(), "alice");

            assertTrue(result.isSuccess());
            assertTrue(result.isPromptRating(), "Must trigger rate-seller prompt (Task 1.10)");
            assertEquals(DealStatus.COMPLETED, saleDeal.getStatus());
            assertNotNull(saleDeal.getClosedDate());
            assertEquals(ListingStatus.SOLD, listing.getStatus());

            List<Notification> notifs = dataService.getAllNotifications();
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("charlie")
                    && n.getTitle().contains("Deal Completed")));
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("alice")
                    && n.getTitle().contains("Deal Completed")));
        }

        @Test
        @DisplayName("Mark complete on GIVEAWAY deal updates listing to GIVEN")
        void testMarkCompleteGiveawaySuccess() {
            Listing giveaway = new Listing();
            giveaway.setTitle("Hoodie");
            giveaway.setType(ListingType.GIVEAWAY);
            giveaway.setCategory("Clothing");
            giveaway.setSellerUsername("diana");
            giveaway.setStatus(ListingStatus.RESERVED);
            dataService.addListing(giveaway);

            Deal giveawayDeal = new Deal();
            giveawayDeal.setListingId(giveaway.getId());
            giveawayDeal.setListingTitle(giveaway.getTitle());
            giveawayDeal.setBuyerUsername("alice");
            giveawayDeal.setSellerUsername("diana");
            giveawayDeal.setType(DealType.GIVEAWAY);
            giveawayDeal.setStatus(DealStatus.ACTIVE);
            dataService.addDeal(giveawayDeal);

            DealOperationResult result = dealService.markDealComplete(giveawayDeal.getId(), "alice");

            assertTrue(result.isSuccess());
            assertEquals(ListingStatus.GIVEN, giveaway.getStatus());
        }

        @Test
        @DisplayName("Exception flows: deal not found, unauthorized, deal not active")
        void testMarkCompleteExceptions() {
            assertFalse(dealService.markDealComplete("NONE", "alice").isSuccess());

            List<Deal> buyerDeals = dataService.getDealsForBuyer("alice");
            Deal saleDeal = buyerDeals.stream()
                    .filter(d -> d.getType() == DealType.SALE && d.getStatus() == DealStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow();

            assertEquals(DealOperationStatus.UNAUTHORIZED,
                    dealService.markDealComplete(saleDeal.getId(), "bob").getStatus());

            saleDeal.setStatus(DealStatus.COMPLETED);
            assertEquals(DealOperationStatus.DEAL_NOT_ACTIVE,
                    dealService.markDealComplete(saleDeal.getId(), "alice").getStatus());
        }
    }

    @Nested
    @DisplayName("Alternative Flow A4: Auto-Return Tests")
    class AutoReturnTests {

        @Test
        @DisplayName("Expired active loans are automatically returned and listings restored to LIVE (Alternative Flow A4)")
        void testAutoReturnExpiredLoan() {
            Deal expiredLoan = new Deal();
            expiredLoan.setListingId("L100");
            expiredLoan.setListingTitle("Borrowed Item");
            expiredLoan.setBuyerUsername("alice");
            expiredLoan.setSellerUsername("charlie");
            expiredLoan.setType(DealType.LOAN);
            expiredLoan.setStatus(DealStatus.ACTIVE);
            expiredLoan.setDueDate(LocalDateTime.now().minusSeconds(10)); // Expired!

            Listing listing = dataService.findListing("L100");
            if (listing == null) {
                listing = new Listing();
                listing.setTitle("Borrowed Item");
                listing.setType(ListingType.LEND);
                listing.setSellerUsername("charlie");
                listing.setStatus(ListingStatus.LENT);
                dataService.addListing(listing);
                expiredLoan.setListingId(listing.getId());
            } else {
                listing.setStatus(ListingStatus.LENT);
            }

            dataService.addDeal(expiredLoan);

            int processed = dealService.processAutoReturns();
            assertTrue(processed >= 1);
            assertEquals(DealStatus.RETURNED, expiredLoan.getStatus());
            assertNotNull(expiredLoan.getClosedDate());
            assertEquals(ListingStatus.LIVE, listing.getStatus(), "Auto-return must reset listing to Live");

            List<Notification> notifs = dataService.getAllNotifications();
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("charlie")
                    && n.getTitle().contains("Item Auto-Returned")));
            assertTrue(notifs.stream().anyMatch(n -> n.getRecipientUsername().equalsIgnoreCase("alice")
                    && n.getTitle().contains("Item Auto-Returned")));
        }

        @Test
        @DisplayName("Active loans with future due dates are not auto-returned")
        void testNonExpiredLoansNotReturned() {
            List<Deal> buyerDeals = dataService.getDealsForBuyer("alice");
            Deal futureLoan = buyerDeals.stream()
                    .filter(d -> d.getType() == DealType.LOAN && d.getStatus() == DealStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow();

            futureLoan.setDueDate(LocalDateTime.now().plusDays(2));
            int processed = dealService.processAutoReturns();
            assertEquals(0, processed);
            assertEquals(DealStatus.ACTIVE, futureLoan.getStatus());
        }
    }

    @Nested
    @DisplayName("Rating Submission Tests (Subflow S5 Step 5 & Task 1.10)")
    class RatingSubmissionTests {

        @Test
        @DisplayName("Submit rating creates Review and marks deal as rated")
        void testSubmitRatingSuccess() {
            List<Deal> buyerDeals = dataService.getDealsForBuyer("alice");
            Deal deal = buyerDeals.get(0);
            deal.setStatus(DealStatus.COMPLETED);
            deal.setRated(false);

            int prevReviews = dataService.getAllReviews().size();

            DealOperationResult result = dealService.submitRating(deal.getId(), "alice", 5, "Great seller, friendly meetup!");

            assertTrue(result.isSuccess());
            assertTrue(deal.isRated());
            assertEquals(prevReviews + 1, dataService.getAllReviews().size());

            Review added = dataService.getAllReviews().get(dataService.getAllReviews().size() - 1);
            assertEquals(5, added.getRating());
            assertEquals("Great seller, friendly meetup!", added.getComment());
            assertEquals("alice", added.getBuyerUsername());
            assertEquals(deal.getSellerUsername(), added.getSellerUsername());
        }

        @Test
        @DisplayName("Exceptions: duplicate rating, invalid stars, unauthorized, active deal")
        void testSubmitRatingExceptions() {
            List<Deal> buyerDeals = dataService.getDealsForBuyer("alice");
            Deal deal = buyerDeals.get(0);
            deal.setStatus(DealStatus.COMPLETED);
            deal.setRated(false);

            // Invalid stars
            assertEquals(DealOperationStatus.INVALID_RATING,
                    dealService.submitRating(deal.getId(), "alice", 0, "Bad").getStatus());
            assertEquals(DealOperationStatus.INVALID_RATING,
                    dealService.submitRating(deal.getId(), "alice", 6, "Bad").getStatus());

            // Unauthorized
            assertEquals(DealOperationStatus.UNAUTHORIZED,
                    dealService.submitRating(deal.getId(), "bob", 5, "Good").getStatus());

            // Active deal
            deal.setStatus(DealStatus.ACTIVE);
            assertEquals(DealOperationStatus.DEAL_NOT_ACTIVE,
                    dealService.submitRating(deal.getId(), "alice", 4, "Good").getStatus());

            // Duplicate rating
            deal.setStatus(DealStatus.COMPLETED);
            deal.setRated(true);
            assertEquals(DealOperationStatus.ALREADY_RATED,
                    dealService.submitRating(deal.getId(), "alice", 5, "Good").getStatus());
        }
    }

    @Nested
    @DisplayName("My Deals View Retrieval & Concurrency Tests")
    class MyDealsRetrievalAndConcurrencyTests {

        @Test
        @DisplayName("getMyDealsForBuyer retrieves all active offers and deals sorted descending")
        void testGetMyDealsForBuyer() {
            List<MyDealsItem> items = dealService.getMyDealsForBuyer("alice");
            assertNotNull(items);
            assertFalse(items.isEmpty());

            // Alternative Flow A1: user with no deals
            List<MyDealsItem> empty = dealService.getMyDealsForBuyer("non_existent_buyer");
            assertNotNull(empty);
            assertTrue(empty.isEmpty());

            assertTrue(dealService.getMyDealsForBuyer(null).isEmpty());
            assertTrue(dealService.getMyDealsForBuyer("   ").isEmpty());
        }

        @Test
        @DisplayName("Concurrent execution of processAutoReturns and selfReturnItem does not crash or corrupt state")
        void testConcurrentAutoReturnAndSelfReturn() throws InterruptedException {
            int threadCount = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger processedCount = new AtomicInteger(0);

            // Create 10 expiring deals
            for (int i = 0; i < 10; i++) {
                Deal d = new Deal();
                d.setType(DealType.LOAN);
                d.setStatus(DealStatus.ACTIVE);
                d.setBuyerUsername("concurrent_buyer_" + i);
                d.setSellerUsername("seller");
                d.setListingTitle("Book " + i);
                d.setDueDate(LocalDateTime.now().minusSeconds(1));
                dataService.addDeal(d);
            }

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        latch.await();
                        if (idx % 2 == 0) {
                            processedCount.addAndGet(dealService.processAutoReturns());
                        } else {
                            dealService.getMyDealsForBuyer("alice");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            latch.countDown();
            pool.shutdown();
            pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

            // Verify all expired deals are now RETURNED
            List<Deal> deals = dataService.getAllDeals();
            long remainingActiveExpired = deals.stream()
                    .filter(d -> d.getBuyerUsername().startsWith("concurrent_buyer_") && d.getStatus() == DealStatus.ACTIVE)
                    .count();
            assertEquals(0, remainingActiveExpired, "All expired deals must have been auto-returned");
        }
    }
}
