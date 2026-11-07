package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.ActOnListingResult;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealOperationResult;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferManagementResult;
import com.hkust.marketplace.models.OfferManagementStatus;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.OfferType;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SendMessageResult;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of use case 2.5 "Manage Offers And Requests" (US-S05), grouped by the flows of its
 * specification. Acceptance criterion of the Sprint 2 backlog: acceptance atomically
 * reserves the listing and declines the other pending offers.
 *
 * <p>The offers are made through the real buyer actions of {@link DummyDataService}
 * (use case 1.5), and the buyer's answers to counter offers go through use case 1.6, so
 * these tests also check that the Seller and Buyer portals work together.</p>
 */
@DisplayName("OfferManagementService: US-S05 Manage Offers And Requests")
class OfferManagementServiceTest {

    private static final DummyDataService DATA = DummyDataService.getInstance();
    /** The tests never log in with a password, so the stored hash is never checked. */
    private static final String UNUSED_HASH = "not-checked-by-these-tests";
    private static final Duration LENDING_DURATION = Duration.ofDays(7);

    private final OfferManagementService service = new OfferManagementService(DATA);

    private Seller seller;
    private Seller otherSeller;
    private Buyer buyer1;
    private Buyer buyer2;
    private Buyer buyer3;
    private Listing sellListing;
    private Listing lendListing;
    private Listing giveawayListing;

    /**
     * Starts every test from the seeded data, with a fresh Seller who owns one Live listing
     * of each type, three buyers, and the Seller logged in.
     */
    @BeforeEach
    void setUp() {
        DATA.resetData();
        SessionManager.logout();
        seller = storeSeller("s05_seller", "Sam Seller");
        otherSeller = storeSeller("s05_other", "Olive Other");
        buyer1 = storeBuyer("s05_buyer1");
        buyer2 = storeBuyer("s05_buyer2");
        buyer3 = storeBuyer("s05_buyer3");
        sellListing = storeListing(seller, "Desk Chair", ListingType.SELL);
        lendListing = storeListing(seller, "Camping Tent", ListingType.LEND);
        giveawayListing = storeListing(seller, "Old Monitor", ListingType.GIVEAWAY);
        SessionManager.login(seller);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Seller storeSeller(String username, String fullName) {
        Seller s = new Seller(username, fullName, UNUSED_HASH);
        assertTrue(DATA.registerUserIfAbsent(s), "Test seller could not be stored");
        return s;
    }

    private static Buyer storeBuyer(String username) {
        Buyer b = new Buyer(username, "Buyer " + username, UNUSED_HASH, "Hall 1");
        assertTrue(DATA.registerUserIfAbsent(b), "Test buyer could not be stored");
        return b;
    }

    private static Listing storeListing(Seller owner, String title, ListingType type) {
        Listing l = new Listing();
        l.setTitle(title);
        l.setDescription("Test listing");
        l.setCategory("Furniture");
        l.setCondition("Used");
        l.setMeetupLocation("Library");
        l.setType(type);
        if (type == ListingType.SELL) {
            l.setPrice(200.0);
        } else if (type == ListingType.LEND) {
            l.setLendingDuration(LENDING_DURATION);
        }
        l.setSellerUsername(owner.getUsername());
        l.setSellerFullName(owner.getFullName());
        l.setStatus(ListingStatus.LIVE);
        DATA.addListing(l);
        return l;
    }

    /** Makes a price offer through the buyer action of use case 1.5. */
    private static Offer priceOffer(Listing listing, Buyer buyer, double price) {
        return SessionManager.withSession(buyer, () -> {
            ActOnListingResult r = DATA.submitPriceOffer(listing.getId(), buyer.getUsername(), price);
            assertTrue(r.isSuccess(), r.getMessage());
            return r.getOffer();
        });
    }

    /** Makes a borrow request through the buyer action of use case 1.5. */
    private static Offer borrowRequest(Listing listing, Buyer buyer, LocalDateTime start, LocalDateTime end) {
        return SessionManager.withSession(buyer, () -> {
            ActOnListingResult r = DATA.submitBorrowRequest(listing.getId(), buyer.getUsername(), start, end);
            assertTrue(r.isSuccess(), r.getMessage());
            return r.getOffer();
        });
    }

    /** Makes a borrow request of one day starting in one hour. */
    private static Offer borrowRequest(Listing listing, Buyer buyer) {
        LocalDateTime start = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.SECONDS);
        return borrowRequest(listing, buyer, start, start.plusDays(1));
    }

    /** Makes a claim through the buyer action of use case 1.5. */
    private static Offer claim(Listing listing, Buyer buyer) {
        return SessionManager.withSession(buyer, () -> {
            ActOnListingResult r = DATA.submitClaim(listing.getId(), buyer.getUsername());
            assertTrue(r.isSuccess(), r.getMessage());
            return r.getOffer();
        });
    }

    private static List<Deal> dealsFor(Listing listing) {
        return DATA.getAllDeals().stream().filter(d -> listing.getId().equals(d.getListingId())).toList();
    }

    private static List<Notification> notificationsTitled(Buyer buyer, String title) {
        return DATA.getNotificationsFor(buyer.getUsername()).stream()
                .filter(n -> title.equals(n.getTitle())).toList();
    }

    private static Notification latestNotification(Buyer buyer, String title) {
        List<Notification> list = notificationsTitled(buyer, title);
        assertFalse(list.isEmpty(), "No '" + title + "' notification for " + buyer.getUsername());
        return list.get(0);
    }

    private static void assertFailure(OfferManagementResult result, OfferManagementStatus expected) {
        assertFalse(result.isSuccess(), "Expected a failure but got: " + result);
        assertEquals(expected, result.getStatus(), result.getMessage());
        assertFalse(result.getMessage().isBlank(), "A failure must explain itself to the Seller");
    }

    // ------------------------------------------------------------------
    // Use case 2.2 special requirement: Seller session checked by the service
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Seller session is checked by every action (role-based access control)")
    class SessionEnforcement {

        private List<Executable> everyAction(String offerId) {
            LocalDateTime start = LocalDateTime.now().plusHours(1);
            return List.of(
                    service::getPendingOffers,
                    service::getCounteredOffers,
                    () -> service.acceptOffer(offerId),
                    () -> service.declineOffer(offerId, "reason"),
                    () -> service.counterWithPrice(offerId, 150.0),
                    () -> service.counterWithDates(offerId, start, start.plusHours(2)),
                    () -> service.openChat(offerId));
        }

        private void assertEveryActionRefused(String offerId) {
            for (Executable action : everyAction(offerId)) {
                assertThrows(SecurityException.class, action);
            }
        }

        @Test
        @DisplayName("Without any session, every action throws SecurityException")
        void noSession() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            SessionManager.logout();
            assertEveryActionRefused(offer.getId());
            assertEquals(OfferStatus.PENDING, offer.getStatus());
        }

        @Test
        @DisplayName("A Buyer session cannot call the Seller actions, even on a real offer")
        void buyerSession() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            SessionManager.login(buyer1);
            assertEveryActionRefused(offer.getId());
            assertEquals(OfferStatus.PENDING, offer.getStatus());
            assertEquals(ListingStatus.LIVE, sellListing.getStatus());
        }

        @Test
        @DisplayName("An Admin session cannot call the Seller actions")
        void adminSession() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            SessionManager.login(new Admin("s05_admin", "Admin", UNUSED_HASH));
            assertEveryActionRefused(offer.getId());
        }

        @ParameterizedTest(name = "Seller account {0}")
        @EnumSource(value = AccountStatus.class, names = {"DEACTIVATED", "DELETED"})
        @DisplayName("A Seller deactivated or deleted during the session is refused")
        void inactiveSeller(AccountStatus status) {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            seller.setAccountStatus(status);
            assertEveryActionRefused(offer.getId());
            assertEquals(OfferStatus.PENDING, offer.getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Basic flow step 2 and alternative flow A1
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Basic flow step 2: pending offers on the Seller's listings")
    class PendingOffers {

        @Test
        @DisplayName("A1: a Seller without offers has nothing to handle")
        void nothingToHandle() {
            assertTrue(service.getPendingOffers().isEmpty());
            assertTrue(service.getCounteredOffers().isEmpty());
        }

        @Test
        @DisplayName("Lists offers, borrow requests and claims, oldest first")
        void allKindsOldestFirst() {
            Offer newest = priceOffer(sellListing, buyer1, 150.0);
            Offer oldest = borrowRequest(lendListing, buyer2);
            Offer middle = claim(giveawayListing, buyer3);
            LocalDateTime now = LocalDateTime.now();
            oldest.setCreatedDate(now.minusMinutes(30));
            middle.setCreatedDate(now.minusMinutes(20));
            newest.setCreatedDate(now.minusMinutes(10));

            List<Offer> pending = service.getPendingOffers();

            assertEquals(List.of(oldest, middle, newest), pending);
        }

        @Test
        @DisplayName("Only lists the logged-in Seller's own pending offers")
        void onlyOwnPendingOffers() {
            Offer mine = priceOffer(sellListing, buyer1, 150.0);
            Offer withdrawn = priceOffer(sellListing, buyer2, 160.0);
            assertTrue(SessionManager.withSession(buyer2, () ->
                    DATA.withdrawOffer(withdrawn.getId(), buyer2.getUsername()).isSuccess()));
            Listing othersListing = storeListing(otherSeller, "Other Lamp", ListingType.SELL);
            Offer othersOffer = priceOffer(othersListing, buyer1, 50.0);

            List<Offer> pending = service.getPendingOffers();

            assertEquals(List.of(mine), pending);
            assertFalse(pending.contains(othersOffer));
        }

        @Test
        @DisplayName("Countered offers are listed apart, waiting for the buyer")
        void counteredListedApart() {
            Offer countered = priceOffer(sellListing, buyer1, 150.0);
            Offer stillPending = priceOffer(sellListing, buyer2, 160.0);
            assertTrue(service.counterWithPrice(countered.getId(), 180.0).isSuccess());

            assertEquals(List.of(stillPending), service.getPendingOffers());
            assertEquals(List.of(countered), service.getCounteredOffers());
        }
    }

    // ------------------------------------------------------------------
    // Subflow S1: Accept Offer
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Subflow S1: Accept Offer")
    class AcceptOffer {

        @Test
        @DisplayName("Accepting a price offer records a sale deal and reserves the listing")
        void acceptPriceOffer() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertTrue(result.isSuccess(), result.getMessage());
            assertSame(offer, result.getOffer());
            assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
            assertEquals(ListingStatus.RESERVED, sellListing.getStatus());
            Deal deal = result.getDeal();
            assertNotNull(deal.getId());
            assertEquals(List.of(deal), dealsFor(sellListing));
            assertEquals(DealType.SALE, deal.getType());
            assertEquals(DealStatus.ACTIVE, deal.getStatus());
            assertEquals(150.0, deal.getOfferPrice());
            assertEquals(buyer1.getUsername(), deal.getBuyerUsername());
            assertEquals(seller.getUsername(), deal.getSellerUsername());
            assertEquals(offer.getId(), deal.getOfferId());
            assertEquals(sellListing.getTitle(), deal.getListingTitle());
            assertTrue(result.getMessage().contains("Reserved"), result.getMessage());
        }

        @Test
        @DisplayName("The buyer is notified, with a link to the deal")
        void buyerNotified() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            Deal deal = service.acceptOffer(offer.getId()).getDeal();

            Notification n = latestNotification(buyer1, "Offer Accepted");
            assertEquals("Offers", n.getCategory());
            assertEquals(deal.getId(), n.getRelatedItemId());
            assertEquals("DEAL", n.getRelatedItemType());
            assertTrue(n.getMessage().contains("Sam Seller"), n.getMessage());
            assertTrue(n.getMessage().contains("HK$ 150.00"), n.getMessage());
            assertTrue(n.isUnread());
        }

        @Test
        @DisplayName("The deal appears on the buyer's My Deals screen (use case 1.6)")
        void dealOnBuyerMyDeals() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            Deal deal = service.acceptOffer(offer.getId()).getDeal();

            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.getMyDealsForBuyer(buyer1.getUsername())).stream()
                    .anyMatch(item -> deal.getId().equals(item.getId())));
        }

        @Test
        @DisplayName("Accepting a borrow request records a loan and lends the listing")
        void acceptBorrowRequest() {
            LocalDateTime start = LocalDateTime.now().plusHours(2).truncatedTo(ChronoUnit.SECONDS);
            LocalDateTime end = start.plusDays(3);
            Offer offer = borrowRequest(lendListing, buyer1, start, end);

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(ListingStatus.LENT, lendListing.getStatus());
            Deal deal = result.getDeal();
            assertEquals(DealType.LOAN, deal.getType());
            assertEquals(start, deal.getStartDate());
            assertEquals(end, deal.getDueDate());
            assertNull(deal.getOfferPrice());
            assertTrue(result.getMessage().contains("Lent"), result.getMessage());
        }

        @Test
        @DisplayName("A loan whose requested start has passed starts when it is accepted")
        void loanStartsNowIfRequestedStartPassed() {
            LocalDateTime requestedStart = LocalDateTime.now().minusSeconds(10);
            Offer offer = borrowRequest(lendListing, buyer1, requestedStart, requestedStart.plusHours(1));

            Deal deal = service.acceptOffer(offer.getId()).getDeal();

            assertTrue(deal.getStartDate().isAfter(requestedStart));
            assertEquals(offer.getEndDate(), deal.getDueDate());
        }

        @Test
        @DisplayName("Accepting a claim records a giveaway deal and reserves the listing")
        void acceptClaim() {
            Offer offer = claim(giveawayListing, buyer1);

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(ListingStatus.RESERVED, giveawayListing.getStatus());
            assertEquals(DealType.GIVEAWAY, result.getDeal().getType());
            assertEquals(0.0, result.getDeal().getOfferPrice());
        }

        @Test
        @DisplayName("Step 5: every other pending or countered offer on the listing is declined and notified")
        void declinesCompetingOffers() {
            Offer accepted = priceOffer(sellListing, buyer1, 150.0);
            Offer pending = priceOffer(sellListing, buyer2, 140.0);
            Offer countered = priceOffer(sellListing, buyer3, 120.0);
            assertTrue(service.counterWithPrice(countered.getId(), 170.0).isSuccess());
            Offer withdrawn = priceOffer(sellListing, buyer2, 130.0);
            assertTrue(SessionManager.withSession(buyer2, () ->
                    DATA.withdrawOffer(withdrawn.getId(), buyer2.getUsername()).isSuccess()));
            Listing otherListing = storeListing(seller, "Bookshelf", ListingType.SELL);
            Offer elsewhere = priceOffer(otherListing, buyer2, 90.0);

            OfferManagementResult result = service.acceptOffer(accepted.getId());

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(2, result.getOtherOffersDeclined());
            assertEquals(OfferStatus.DECLINED, pending.getStatus());
            assertEquals(OfferManagementService.REASON_OTHER_OFFER_ACCEPTED, pending.getDeclineReason());
            assertEquals(OfferStatus.DECLINED, countered.getStatus());
            assertEquals(OfferStatus.DECLINED, countered.getCounterOffer().getStatus());
            assertEquals(OfferStatus.WITHDRAWN, withdrawn.getStatus(), "A withdrawn offer is left alone");
            assertEquals(OfferStatus.PENDING, elsewhere.getStatus(), "Offers on other listings are left alone");
            assertEquals(ListingStatus.LIVE, otherListing.getStatus());
            assertTrue(latestNotification(buyer2, "Offer Declined").getMessage().contains("accepted another offer"));
            assertTrue(latestNotification(buyer3, "Offer Declined").getMessage().contains("accepted another offer"));
            assertTrue(result.getMessage().contains("2 other pending offers were declined"), result.getMessage());
        }

        @Test
        @DisplayName("The Seller is told when exactly one other offer was declined")
        void oneCompetingOffer() {
            Offer accepted = priceOffer(sellListing, buyer1, 150.0);
            priceOffer(sellListing, buyer2, 140.0);

            OfferManagementResult result = service.acceptOffer(accepted.getId());

            assertEquals(1, result.getOtherOffersDeclined());
            assertTrue(result.getMessage().contains("1 other pending offer was declined"), result.getMessage());
        }

        @Test
        @DisplayName("Without competing offers, no decline is mentioned")
        void noCompetingOffer() {
            Offer accepted = priceOffer(sellListing, buyer1, 150.0);

            OfferManagementResult result = service.acceptOffer(accepted.getId());

            assertEquals(0, result.getOtherOffersDeclined());
            assertFalse(result.getMessage().contains("declined"), result.getMessage());
        }

        @Test
        @DisplayName("Postcondition: a Reserved sale becomes Sold when the buyer completes the deal")
        void reservedBecomesSold() {
            Deal deal = service.acceptOffer(priceOffer(sellListing, buyer1, 150.0).getId()).getDeal();

            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.markDealComplete(deal.getId(), buyer1.getUsername()).isSuccess()));

            assertEquals(ListingStatus.SOLD, sellListing.getStatus());
        }

        @Test
        @DisplayName("Postcondition: a Reserved giveaway becomes Given when the buyer completes the deal")
        void reservedBecomesGiven() {
            Deal deal = service.acceptOffer(claim(giveawayListing, buyer1).getId()).getDeal();

            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.markDealComplete(deal.getId(), buyer1.getUsername()).isSuccess()));

            assertEquals(ListingStatus.GIVEN, giveawayListing.getStatus());
        }

        @Test
        @DisplayName("Postcondition: a Lent listing becomes Live again when the loan expires")
        void lentBecomesLiveAfterAutoReturn() {
            Deal deal = service.acceptOffer(borrowRequest(lendListing, buyer1).getId()).getDeal();
            deal.setDueDate(LocalDateTime.now().minusSeconds(1));

            DATA.processAutoReturns();

            assertEquals(DealStatus.RETURNED, deal.getStatus());
            assertEquals(ListingStatus.LIVE, lendListing.getStatus());
        }

        @Test
        @DisplayName("A price offer stored without price makes a deal at the listing's price")
        void priceOfferWithoutPrice() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            offer.setPrice(null);

            assertEquals(200.0, service.acceptOffer(offer.getId()).getDeal().getOfferPrice());
        }

        @Test
        @DisplayName("The offer identifier is trimmed")
        void trimsOfferId() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            assertTrue(service.acceptOffer("  " + offer.getId() + "  ").isSuccess());
        }
    }

    // ------------------------------------------------------------------
    // Alternative flows of an acceptance
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Acceptance alternative flows")
    class AcceptAlternativeFlows {

        @Test
        @DisplayName("A2: an offer withdrawn by the buyer cannot be accepted")
        void withdrawnOffer() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.withdrawOffer(offer.getId(), buyer1.getUsername()).isSuccess()));

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertFailure(result, OfferManagementStatus.OFFER_NOT_PENDING);
            assertTrue(result.getMessage().contains("withdrawn"), result.getMessage());
            assertSame(offer, result.getOffer());
            assertEquals(OfferStatus.WITHDRAWN, offer.getStatus());
            assertTrue(dealsFor(sellListing).isEmpty());
            assertEquals(ListingStatus.LIVE, sellListing.getStatus());
        }

        @ParameterizedTest(name = "offer already {0}")
        @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "DECLINED", "COUNTERED"})
        @DisplayName("A2: an offer already handled cannot be accepted again")
        void alreadyHandled(OfferStatus status) {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            offer.setStatus(status);

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertFailure(result, OfferManagementStatus.OFFER_NOT_PENDING);
            assertEquals(status, offer.getStatus());
            assertTrue(dealsFor(sellListing).isEmpty());
        }

        @Test
        @DisplayName("A2: an offer without status is reported as not pending")
        void noStatus() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            offer.setStatus(null);

            assertFailure(service.acceptOffer(offer.getId()), OfferManagementStatus.OFFER_NOT_PENDING);
        }

        @ParameterizedTest(name = "listing {0}")
        @EnumSource(value = ListingStatus.class, names = {"LIVE"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("A3: on a listing that is no longer Live, the offer is declined and the buyer notified")
        void listingNoLongerLive(ListingStatus status) {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            sellListing.setStatus(status);

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertFailure(result, OfferManagementStatus.LISTING_NOT_AVAILABLE);
            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertEquals(OfferManagementService.REASON_LISTING_UNAVAILABLE, offer.getDeclineReason());
            assertEquals(status, sellListing.getStatus());
            assertTrue(dealsFor(sellListing).isEmpty());
            Notification n = latestNotification(buyer1, "Offer Declined");
            assertEquals(offer.getId(), n.getRelatedItemId());
            assertEquals("OFFER", n.getRelatedItemType());
        }

        @Test
        @DisplayName("A3: on a deleted listing, the offer is declined")
        void listingDeleted() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            DATA.removeListing(sellListing.getId());

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertFailure(result, OfferManagementStatus.LISTING_NOT_AVAILABLE);
            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertTrue(latestNotification(buyer1, "Offer Declined").getMessage().contains(sellListing.getTitle()));
        }

        @ParameterizedTest(name = "buyer account {0}")
        @EnumSource(value = AccountStatus.class, names = {"DEACTIVATED", "DELETED"})
        @DisplayName("No deal is made with a buyer whose account is no longer active")
        void buyerNoLongerActive(AccountStatus status) {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            buyer1.setAccountStatus(status);

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertFailure(result, OfferManagementStatus.BUYER_UNAVAILABLE);
            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertEquals(OfferManagementService.REASON_BUYER_UNAVAILABLE, offer.getDeclineReason());
            assertEquals(ListingStatus.LIVE, sellListing.getStatus());
            assertTrue(dealsFor(sellListing).isEmpty());
        }

        @Test
        @DisplayName("No deal is made with a buyer account that no longer exists")
        void buyerUnknown() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            offer.setBuyerUsername("s05_ghost");

            assertFailure(service.acceptOffer(offer.getId()), OfferManagementStatus.BUYER_UNAVAILABLE);
        }

        @Test
        @DisplayName("A borrow request whose period has ended stays pending, so it can be countered")
        void borrowPeriodEnded() {
            Offer offer = new Offer(null, OfferType.BORROW_REQUEST, lendListing.getId(),
                    buyer1.getUsername(), seller.getUsername());
            offer.setListingTitle(lendListing.getTitle());
            offer.setStartDate(LocalDateTime.now().minusHours(2));
            offer.setEndDate(LocalDateTime.now().minusHours(1));
            DATA.addOffer(offer);

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertFailure(result, OfferManagementStatus.BORROW_PERIOD_ENDED);
            assertEquals(OfferStatus.PENDING, offer.getStatus());
            assertEquals(ListingStatus.LIVE, lendListing.getStatus());
            assertTrue(dealsFor(lendListing).isEmpty());
        }

        @Test
        @DisplayName("A Seller cannot accept an offer made on another Seller's listing")
        void otherSellersOffer() {
            Listing othersListing = storeListing(otherSeller, "Other Lamp", ListingType.SELL);
            Offer offer = priceOffer(othersListing, buyer1, 50.0);

            OfferManagementResult result = service.acceptOffer(offer.getId());

            assertFailure(result, OfferManagementStatus.UNAUTHORIZED);
            assertNull(result.getOffer(), "Another Seller's offer must not be revealed");
            assertEquals(OfferStatus.PENDING, offer.getStatus());
            assertEquals(ListingStatus.LIVE, othersListing.getStatus());
        }

        @ParameterizedTest(name = "id \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "O99999"})
        @DisplayName("An empty or unknown offer identifier is reported as not found")
        void unknownOffer(String offerId) {
            assertFailure(service.acceptOffer(offerId), OfferManagementStatus.OFFER_NOT_FOUND);
        }
    }

    // ------------------------------------------------------------------
    // Subflow S2: Decline Offer
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Subflow S2: Decline Offer")
    class DeclineOffer {

        @Test
        @DisplayName("Declining with a reason keeps the listing Live and sends the reason to the buyer")
        void declineWithReason() {
            Offer offer = priceOffer(sellListing, buyer1, 50.0);

            OfferManagementResult result = service.declineOffer(offer.getId(), "  Too low, sorry.  ");

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertEquals("Too low, sorry.", offer.getDeclineReason());
            assertEquals(ListingStatus.LIVE, sellListing.getStatus());
            assertTrue(dealsFor(sellListing).isEmpty());
            Notification n = latestNotification(buyer1, "Offer Declined");
            assertTrue(n.getMessage().endsWith("Reason: Too low, sorry."), n.getMessage());
            assertEquals(offer.getId(), n.getRelatedItemId());
        }

        @ParameterizedTest(name = "reason \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("The reason is optional")
        void declineWithoutReason(String reason) {
            Offer offer = priceOffer(sellListing, buyer1, 50.0);

            assertTrue(service.declineOffer(offer.getId(), reason).isSuccess());

            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertNull(offer.getDeclineReason());
            assertFalse(latestNotification(buyer1, "Offer Declined").getMessage().contains("Reason"));
        }

        @Test
        @DisplayName("A reason of exactly the maximum length is accepted")
        void reasonAtMaximumLength() {
            Offer offer = priceOffer(sellListing, buyer1, 50.0);
            String reason = "x".repeat(OfferManagementService.MAX_DECLINE_REASON_LENGTH);

            assertTrue(service.declineOffer(offer.getId(), reason).isSuccess());
            assertEquals(reason, offer.getDeclineReason());
        }

        @Test
        @DisplayName("The length of the reason is counted in characters, not in UTF-16 units")
        void reasonLengthInCharacters() {
            Offer offer = priceOffer(sellListing, buyer1, 50.0);
            String emojis = "😀".repeat(OfferManagementService.MAX_DECLINE_REASON_LENGTH);

            assertTrue(service.declineOffer(offer.getId(), emojis).isSuccess());
        }

        @Test
        @DisplayName("A reason that is too long is refused and nothing changes")
        void reasonTooLong() {
            Offer offer = priceOffer(sellListing, buyer1, 50.0);
            String reason = "x".repeat(OfferManagementService.MAX_DECLINE_REASON_LENGTH + 1);

            OfferManagementResult result = service.declineOffer(offer.getId(), reason);

            assertFailure(result, OfferManagementStatus.INVALID_REASON);
            assertEquals(OfferStatus.PENDING, offer.getStatus());
            assertTrue(notificationsTitled(buyer1, "Offer Declined").isEmpty());
        }

        @Test
        @DisplayName("After a declined claim, another buyer can claim the giveaway (use case 1.5)")
        void declinedClaimFreesTheGiveaway() {
            Offer first = claim(giveawayListing, buyer1);
            assertFalse(SessionManager.withSession(buyer2, () ->
                    DATA.submitClaim(giveawayListing.getId(), buyer2.getUsername()).isSuccess()),
                    "A second claim is blocked while the first is pending");

            assertTrue(service.declineOffer(first.getId(), null).isSuccess());

            assertEquals(ListingStatus.LIVE, giveawayListing.getStatus());
            assertTrue(SessionManager.withSession(buyer2, () ->
                    DATA.submitClaim(giveawayListing.getId(), buyer2.getUsername()).isSuccess()));
        }

        @Test
        @DisplayName("An offer on a listing that is no longer Live can still be declined")
        void declineOnUnavailableListing() {
            Offer offer = priceOffer(sellListing, buyer1, 50.0);
            sellListing.setStatus(ListingStatus.PENDING_REVIEW);

            assertTrue(service.declineOffer(offer.getId(), "Item edited").isSuccess());
            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertEquals(ListingStatus.PENDING_REVIEW, sellListing.getStatus());
        }

        @Test
        @DisplayName("A2: an offer withdrawn by the buyer cannot be declined")
        void withdrawnOffer() {
            Offer offer = priceOffer(sellListing, buyer1, 50.0);
            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.withdrawOffer(offer.getId(), buyer1.getUsername()).isSuccess()));

            assertFailure(service.declineOffer(offer.getId(), null), OfferManagementStatus.OFFER_NOT_PENDING);
            assertEquals(OfferStatus.WITHDRAWN, offer.getStatus());
        }

        @Test
        @DisplayName("A Seller cannot decline an offer made on another Seller's listing")
        void otherSellersOffer() {
            Offer offer = priceOffer(storeListing(otherSeller, "Other Lamp", ListingType.SELL), buyer1, 50.0);

            assertFailure(service.declineOffer(offer.getId(), null), OfferManagementStatus.UNAUTHORIZED);
            assertEquals(OfferStatus.PENDING, offer.getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Subflow S3: Counter Offer (price)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Subflow S3: Counter a price offer")
    class CounterPrice {

        @Test
        @DisplayName("Countering records a counter offer, marks the offer Countered and notifies the buyer")
        void counterPrice() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            OfferManagementResult result = service.counterWithPrice(offer.getId(), 180.0);

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(OfferStatus.COUNTERED, offer.getStatus());
            Offer counter = offer.getCounterOffer();
            assertNotNull(counter);
            assertEquals(offer.getId() + OfferManagementService.COUNTER_ID_SUFFIX, counter.getId());
            assertEquals(OfferType.PRICE_OFFER, counter.getType());
            assertEquals(OfferStatus.COUNTERED, counter.getStatus());
            assertEquals(180.0, counter.getPrice());
            assertEquals(offer.getListingId(), counter.getListingId());
            assertEquals(offer.getListingTitle(), counter.getListingTitle());
            assertEquals(buyer1.getUsername(), counter.getBuyerUsername());
            assertEquals(seller.getUsername(), counter.getSellerUsername());
            assertEquals(150.0, offer.getPrice(), "The buyer's offer is kept");
            assertEquals(ListingStatus.LIVE, sellListing.getStatus());
            assertNull(DATA.findOffer(counter.getId()), "The counter offer is attached, not listed on its own");
            Notification n = latestNotification(buyer1, "Counter-Offer Received");
            assertTrue(n.getMessage().contains("HK$ 180.00"), n.getMessage());
            assertEquals(offer.getId(), n.getRelatedItemId());
            assertTrue(result.getMessage().contains("HK$ 180.00"), result.getMessage());
        }

        @Test
        @DisplayName("The counter price is rounded to the cent")
        void roundedToCent() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            assertTrue(service.counterWithPrice(offer.getId(), 179.995).isSuccess());

            assertEquals(180.0, offer.getCounterOffer().getPrice());
        }

        @ParameterizedTest(name = "price {0}")
        @ValueSource(doubles = {0.01, OfferManagementService.MAX_COUNTER_PRICE})
        @DisplayName("The smallest and largest counter prices are accepted")
        void boundaryPrices(double price) {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            assertTrue(service.counterWithPrice(offer.getId(), price).isSuccess());
            assertEquals(price, offer.getCounterOffer().getPrice());
        }

        @ParameterizedTest(name = "price {0}")
        @ValueSource(doubles = {0.0, -5.0, 0.004, 1_000_000.01, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1e300})
        @DisplayName("A4: an invalid counter price is refused and nothing changes")
        void invalidPrice(double price) {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            OfferManagementResult result = service.counterWithPrice(offer.getId(), price);

            assertFailure(result, OfferManagementStatus.INVALID_COUNTER_PRICE);
            assertEquals(OfferStatus.PENDING, offer.getStatus());
            assertNull(offer.getCounterOffer());
            assertTrue(notificationsTitled(buyer1, "Counter-Offer Received").isEmpty());
        }

        @Test
        @DisplayName("A4: a missing counter price is refused")
        void missingPrice() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            assertFailure(service.counterWithPrice(offer.getId(), null), OfferManagementStatus.INVALID_COUNTER_PRICE);
        }

        @Test
        @DisplayName("A4: countering with the price the buyer offered is refused (accept instead)")
        void samePriceAsOffer() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            OfferManagementResult result = service.counterWithPrice(offer.getId(), 150.001);

            assertFailure(result, OfferManagementStatus.INVALID_COUNTER_PRICE);
            assertTrue(result.getMessage().contains("Accept"), result.getMessage());
        }

        @Test
        @DisplayName("The buyer can accept the counter price (use case 1.6), making a deal at that price")
        void buyerAcceptsCounter() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            assertTrue(service.counterWithPrice(offer.getId(), 180.0).isSuccess());

            DealOperationResult answer = SessionManager.withSession(buyer1, () ->
                    DATA.acceptCounterOffer(offer.getId(), buyer1.getUsername()));

            assertTrue(answer.isSuccess(), answer.getMessage());
            assertEquals(180.0, answer.getDeal().getOfferPrice());
            assertEquals(ListingStatus.RESERVED, sellListing.getStatus());
            assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
        }

        @Test
        @DisplayName("The buyer can decline the counter price (use case 1.6); the listing stays Live")
        void buyerDeclinesCounter() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            assertTrue(service.counterWithPrice(offer.getId(), 180.0).isSuccess());

            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.declineCounterOffer(offer.getId(), buyer1.getUsername()).isSuccess()));

            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertEquals(ListingStatus.LIVE, sellListing.getStatus());
        }

        @Test
        @DisplayName("A2: an offer already countered cannot be countered again before the buyer answers")
        void alreadyCountered() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            assertTrue(service.counterWithPrice(offer.getId(), 180.0).isSuccess());

            OfferManagementResult result = service.counterWithPrice(offer.getId(), 170.0);

            assertFailure(result, OfferManagementStatus.OFFER_NOT_PENDING);
            assertTrue(result.getMessage().contains("already countered"), result.getMessage());
            assertEquals(180.0, offer.getCounterOffer().getPrice());
        }

        @Test
        @DisplayName("A3: countering on a listing that is no longer Live declines the offer")
        void listingNoLongerLive() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            sellListing.setStatus(ListingStatus.REMOVED);

            assertFailure(service.counterWithPrice(offer.getId(), 180.0), OfferManagementStatus.LISTING_NOT_AVAILABLE);
            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertNull(offer.getCounterOffer());
        }

        @Test
        @DisplayName("Countering an offer from a deactivated buyer declines it")
        void buyerDeactivated() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            buyer1.setAccountStatus(AccountStatus.DEACTIVATED);

            assertFailure(service.counterWithPrice(offer.getId(), 180.0), OfferManagementStatus.BUYER_UNAVAILABLE);
            assertEquals(OfferStatus.DECLINED, offer.getStatus());
        }

        @Test
        @DisplayName("A borrow request cannot be countered with a price")
        void borrowRequestNeedsDates() {
            Offer offer = borrowRequest(lendListing, buyer1);

            OfferManagementResult result = service.counterWithPrice(offer.getId(), 50.0);

            assertFailure(result, OfferManagementStatus.COUNTER_NOT_ALLOWED);
            assertTrue(result.getMessage().contains("dates"), result.getMessage());
            assertEquals(OfferStatus.PENDING, offer.getStatus());
        }

        @Test
        @DisplayName("A claim on a Give Away listing cannot be countered")
        void claimCannotBeCountered() {
            Offer offer = claim(giveawayListing, buyer1);

            OfferManagementResult result = service.counterWithPrice(offer.getId(), 10.0);

            assertFailure(result, OfferManagementStatus.COUNTER_NOT_ALLOWED);
            assertTrue(result.getMessage().contains("Give Away"), result.getMessage());
            assertEquals(OfferStatus.PENDING, offer.getStatus());
        }

        @Test
        @DisplayName("A Seller cannot counter an offer made on another Seller's listing")
        void otherSellersOffer() {
            Offer offer = priceOffer(storeListing(otherSeller, "Other Lamp", ListingType.SELL), buyer1, 50.0);

            assertFailure(service.counterWithPrice(offer.getId(), 60.0), OfferManagementStatus.UNAUTHORIZED);
            assertNull(offer.getCounterOffer());
        }
    }

    // ------------------------------------------------------------------
    // Subflow S3: Counter Offer (borrow dates)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Subflow S3: Counter a borrow request with other dates")
    class CounterDates {

        private Offer request;
        private LocalDateTime start;

        @BeforeEach
        void makeRequest() {
            request = borrowRequest(lendListing, buyer1);
            start = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.SECONDS);
        }

        @Test
        @DisplayName("Countering records the counter dates and notifies the buyer")
        void counterDates() {
            LocalDateTime end = start.plusDays(2);

            OfferManagementResult result = service.counterWithDates(request.getId(), start, end);

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(OfferStatus.COUNTERED, request.getStatus());
            Offer counter = request.getCounterOffer();
            assertEquals(OfferType.BORROW_REQUEST, counter.getType());
            assertEquals(start, counter.getStartDate());
            assertEquals(end, counter.getEndDate());
            assertNull(counter.getPrice());
            String terms = OfferManagementService.describeTerms(counter);
            assertTrue(latestNotification(buyer1, "Counter-Offer Received").getMessage().contains(terms));
            assertTrue(result.getMessage().contains(terms), result.getMessage());
        }

        @Test
        @DisplayName("The buyer can accept the counter dates (use case 1.6), making a loan on those dates")
        void buyerAcceptsCounterDates() {
            LocalDateTime end = start.plusDays(2);
            assertTrue(service.counterWithDates(request.getId(), start, end).isSuccess());

            DealOperationResult answer = SessionManager.withSession(buyer1, () ->
                    DATA.acceptCounterOffer(request.getId(), buyer1.getUsername()));

            assertTrue(answer.isSuccess(), answer.getMessage());
            assertEquals(DealType.LOAN, answer.getDeal().getType());
            assertEquals(start, answer.getDeal().getStartDate());
            assertEquals(end, answer.getDeal().getDueDate());
            assertEquals(ListingStatus.LENT, lendListing.getStatus());
        }

        @Test
        @DisplayName("A period of exactly 5 seconds is accepted (demo minimum)")
        void minimumPeriod() {
            assertTrue(service.counterWithDates(request.getId(), start,
                    start.plusSeconds(OfferManagementService.MIN_BORROW_SECONDS)).isSuccess());
        }

        @Test
        @DisplayName("A period exactly as long as the lending duration is accepted")
        void periodEqualToLendingDuration() {
            assertTrue(service.counterWithDates(request.getId(), start, start.plus(LENDING_DURATION)).isSuccess());
        }

        @Test
        @DisplayName("A start a few seconds in the past is tolerated (time spent filling the form)")
        void startJustPassed() {
            LocalDateTime justPassed = LocalDateTime.now().minusSeconds(10);

            assertTrue(service.counterWithDates(request.getId(), justPassed, justPassed.plusHours(1)).isSuccess());
        }

        @Test
        @DisplayName("A listing without lending duration accepts any period length")
        void noLendingDuration() {
            lendListing.setLendingDuration(null);

            assertTrue(service.counterWithDates(request.getId(), start, start.plusDays(60)).isSuccess());
        }

        @Test
        @DisplayName("A zero or negative lending duration is treated as no limit")
        void zeroLendingDuration() {
            lendListing.setLendingDuration(Duration.ZERO);
            assertTrue(service.counterWithDates(request.getId(), start, start.plusDays(60)).isSuccess());

            Offer other = borrowRequest(lendListing, buyer2);
            lendListing.setLendingDuration(Duration.ofDays(-1));
            assertTrue(service.counterWithDates(other.getId(), start, start.plusDays(60)).isSuccess());
        }

        @Test
        @DisplayName("Keeping the buyer's start but changing the end is a valid counter")
        void sameStartOtherEnd() {
            assertTrue(service.counterWithDates(request.getId(), request.getStartDate(),
                    request.getEndDate().plusHours(5)).isSuccess());
        }

        @Test
        @DisplayName("A4: a missing start or end is refused")
        void missingDates() {
            assertFailure(service.counterWithDates(request.getId(), null, start), OfferManagementStatus.INVALID_COUNTER_DATES);
            assertFailure(service.counterWithDates(request.getId(), start, null), OfferManagementStatus.INVALID_COUNTER_DATES);
            assertEquals(OfferStatus.PENDING, request.getStatus());
        }

        @Test
        @DisplayName("A4: a start in the past is refused")
        void startInPast() {
            LocalDateTime past = LocalDateTime.now().minusHours(1);

            OfferManagementResult result = service.counterWithDates(request.getId(), past, past.plusHours(5));

            assertFailure(result, OfferManagementStatus.INVALID_COUNTER_DATES);
            assertTrue(result.getMessage().contains("past"), result.getMessage());
        }

        @Test
        @DisplayName("A4: an end before or at the start is refused")
        void endNotAfterStart() {
            assertFailure(service.counterWithDates(request.getId(), start, start.minusHours(1)),
                    OfferManagementStatus.INVALID_COUNTER_DATES);
            assertFailure(service.counterWithDates(request.getId(), start, start),
                    OfferManagementStatus.INVALID_COUNTER_DATES);
        }

        @Test
        @DisplayName("A4: a period shorter than 5 seconds is refused")
        void periodTooShort() {
            OfferManagementResult result = service.counterWithDates(request.getId(), start,
                    start.plusSeconds(OfferManagementService.MIN_BORROW_SECONDS - 1));

            assertFailure(result, OfferManagementStatus.INVALID_COUNTER_DATES);
            assertTrue(result.getMessage().contains("5 seconds"), result.getMessage());
        }

        @Test
        @DisplayName("A4: a period longer than the lending duration is refused")
        void periodTooLong() {
            OfferManagementResult result = service.counterWithDates(request.getId(), start,
                    start.plus(LENDING_DURATION).plusSeconds(1));

            assertFailure(result, OfferManagementStatus.INVALID_COUNTER_DATES);
            assertTrue(result.getMessage().contains("7 days"), result.getMessage());
            assertNull(request.getCounterOffer());
        }

        @Test
        @DisplayName("A4: countering with the dates the buyer asked for is refused (accept instead)")
        void sameDatesAsRequest() {
            OfferManagementResult result = service.counterWithDates(request.getId(),
                    request.getStartDate(), request.getEndDate());

            assertFailure(result, OfferManagementStatus.INVALID_COUNTER_DATES);
            assertTrue(result.getMessage().contains("Accept"), result.getMessage());
        }

        @Test
        @DisplayName("A price offer cannot be countered with dates")
        void priceOfferNeedsPrice() {
            Offer offer = priceOffer(sellListing, buyer2, 150.0);

            OfferManagementResult result = service.counterWithDates(offer.getId(), start, start.plusHours(1));

            assertFailure(result, OfferManagementStatus.COUNTER_NOT_ALLOWED);
            assertTrue(result.getMessage().contains("price"), result.getMessage());
        }

        @Test
        @DisplayName("A claim cannot be countered with dates")
        void claimCannotBeCountered() {
            Offer offer = claim(giveawayListing, buyer2);

            OfferManagementResult result = service.counterWithDates(offer.getId(), start, start.plusHours(1));

            assertFailure(result, OfferManagementStatus.COUNTER_NOT_ALLOWED);
            assertTrue(result.getMessage().contains("Give Away"), result.getMessage());
        }

        @Test
        @DisplayName("A3: countering dates on a listing that is no longer Live declines the request")
        void listingNoLongerLive() {
            lendListing.setStatus(ListingStatus.LENT);

            assertFailure(service.counterWithDates(request.getId(), start, start.plusHours(1)),
                    OfferManagementStatus.LISTING_NOT_AVAILABLE);
            assertEquals(OfferStatus.DECLINED, request.getStatus());
        }

        @Test
        @DisplayName("A2: a request withdrawn by the buyer cannot be countered")
        void withdrawnRequest() {
            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.withdrawOffer(request.getId(), buyer1.getUsername()).isSuccess()));

            assertFailure(service.counterWithDates(request.getId(), start, start.plusHours(1)),
                    OfferManagementStatus.OFFER_NOT_PENDING);
        }
    }

    // ------------------------------------------------------------------
    // Basic flow step 3.5: Open Chat
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Basic flow step 3.5: Open the chat linked to an offer")
    class OpenChat {

        @Test
        @DisplayName("Opens the private thread of the listing and the buyer, and reuses it afterwards")
        void opensAndReusesThread() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            OfferManagementResult first = service.openChat(offer.getId());
            OfferManagementResult second = service.openChat(offer.getId());

            assertTrue(first.isSuccess(), first.getMessage());
            ChatThread thread = first.getChatThread();
            assertEquals(sellListing.getId(), thread.getListingId());
            assertEquals(buyer1.getUsername(), thread.getBuyerUsername());
            assertEquals(seller.getUsername(), thread.getSellerUsername());
            assertEquals(sellListing.getTitle(), thread.getListingTitle());
            assertEquals(thread.getId(), second.getChatThread().getId());
            assertSame(offer, first.getOffer());
        }

        @Test
        @DisplayName("Opens the same thread the buyer started from the listing (use case 1.5)")
        void sameThreadAsBuyer() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            ChatThread buyerThread = DATA.getChatService().getOrCreateThread(sellListing.getId(), buyer1.getUsername());

            assertEquals(buyerThread.getId(), service.openChat(offer.getId()).getChatThread().getId());
        }

        @Test
        @DisplayName("The Seller can reply in the thread and the buyer is notified")
        void sellerCanReply() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            ChatThread thread = service.openChat(offer.getId()).getChatThread();

            SendMessageResult sent = DATA.getChatService().sendMessage(thread.getId(), seller.getUsername(), "Can you do 170?");

            assertTrue(sent.isSuccess());
            assertEquals("Can you do 170?", thread.getLastMessage().getText());
            assertFalse(notificationsTitled(buyer1, "New Chat Message").isEmpty());
        }

        @Test
        @DisplayName("Works whatever the status of the offer")
        void anyStatus() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            assertTrue(service.declineOffer(offer.getId(), null).isSuccess());

            assertTrue(service.openChat(offer.getId()).isSuccess());
        }

        @Test
        @DisplayName("Keeps the listing title known by the offer when the listing was deleted")
        void deletedListing() {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            DATA.removeListing(sellListing.getId());

            ChatThread thread = service.openChat(offer.getId()).getChatThread();

            assertEquals(offer.getListingTitle(), thread.getListingTitle());
        }

        @Test
        @DisplayName("A Seller cannot open the chat of another Seller's offer")
        void otherSellersOffer() {
            Offer offer = priceOffer(storeListing(otherSeller, "Other Lamp", ListingType.SELL), buyer1, 50.0);

            OfferManagementResult result = service.openChat(offer.getId());

            assertFailure(result, OfferManagementStatus.UNAUTHORIZED);
            assertNull(result.getChatThread());
        }

        @ParameterizedTest(name = "id \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"O99999"})
        @DisplayName("An empty or unknown offer identifier is reported as not found")
        void unknownOffer(String offerId) {
            assertFailure(service.openChat(offerId), OfferManagementStatus.OFFER_NOT_FOUND);
        }
    }

    // ------------------------------------------------------------------
    // Display helpers
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Display helpers used by the Seller dashboard")
    class DisplayHelpers {

        @Test
        @DisplayName("Describes the terms of each kind of offer")
        void describeTerms() {
            Offer price = new Offer("O1", OfferType.PRICE_OFFER, "L1", "b", "s");
            price.setPrice(1250.5);
            Offer borrow = new Offer("O2", OfferType.BORROW_REQUEST, "L1", "b", "s");
            borrow.setStartDate(LocalDateTime.of(2026, 11, 5, 14, 30, 0));
            borrow.setEndDate(LocalDateTime.of(2026, 11, 6, 9, 0, 5));
            Offer claim = new Offer("O3", OfferType.CLAIM, "L1", "b", "s");

            assertEquals("HK$ 1,250.50", OfferManagementService.describeTerms(price));
            assertEquals("05 Nov 2026 14:30:00 to 06 Nov 2026 09:00:05", OfferManagementService.describeTerms(borrow));
            assertEquals("Free claim", OfferManagementService.describeTerms(claim));
        }

        @Test
        @DisplayName("Handles missing values")
        void describeTermsMissingValues() {
            assertEquals("", OfferManagementService.describeTerms(null));
            assertEquals("", OfferManagementService.describeTerms(new Offer()));
            assertEquals("no price", OfferManagementService.describeTerms(new Offer("O1", OfferType.PRICE_OFFER, "L1", "b", "s")));
            assertEquals("? to ?", OfferManagementService.describeTerms(new Offer("O2", OfferType.BORROW_REQUEST, "L1", "b", "s")));
            assertEquals("?", OfferManagementService.formatDateTime(null));
        }

        @Test
        @DisplayName("Formats durations in their largest whole unit")
        void formatDuration() {
            assertEquals("no limit", OfferManagementService.formatDuration(null));
            assertEquals("7 days", OfferManagementService.formatDuration(Duration.ofDays(7)));
            assertEquals("1 day", OfferManagementService.formatDuration(Duration.ofDays(1)));
            assertEquals("2 hours", OfferManagementService.formatDuration(Duration.ofHours(2)));
            assertEquals("1 hour", OfferManagementService.formatDuration(Duration.ofHours(1)));
            assertEquals("30 minutes", OfferManagementService.formatDuration(Duration.ofMinutes(30)));
            assertEquals("90 seconds", OfferManagementService.formatDuration(Duration.ofSeconds(90)));
            assertEquals("1 second", OfferManagementService.formatDuration(Duration.ofSeconds(1)));
            assertEquals("0 seconds", OfferManagementService.formatDuration(Duration.ZERO));
        }

        @Test
        @DisplayName("Names each kind of offer")
        void kindOf() {
            assertEquals("offer", OfferManagementService.kindOf(new Offer("O1", OfferType.PRICE_OFFER, "L", "b", "s")));
            assertEquals("borrow request", OfferManagementService.kindOf(new Offer("O2", OfferType.BORROW_REQUEST, "L", "b", "s")));
            assertEquals("claim", OfferManagementService.kindOf(new Offer("O3", OfferType.CLAIM, "L", "b", "s")));
            assertEquals("offer", OfferManagementService.kindOf(new Offer()));
            assertEquals("offer", OfferManagementService.kindOf(null));
        }

        @Test
        @DisplayName("The service refuses a missing data store")
        void requiresDataStore() {
            assertThrows(NullPointerException.class, () -> new OfferManagementService(null));
            assertNotNull(new OfferManagementService());
        }
    }

    // ------------------------------------------------------------------
    // Special requirement: accepting is atomic
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Special requirement: accepting is atomic under concurrency")
    class Concurrency {

        private static final int THREADS = 10;

        /** Runs the tasks at the same moment and returns their results in order. */
        private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
            try {
                CountDownLatch ready = new CountDownLatch(tasks.size());
                CountDownLatch go = new CountDownLatch(1);
                List<Future<T>> futures = new ArrayList<>();
                for (Callable<T> task : tasks) {
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return task.call();
                    }));
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS), "Threads did not start");
                go.countDown();
                List<T> results = new ArrayList<>();
                for (Future<T> future : futures) {
                    results.add(future.get(10, TimeUnit.SECONDS));
                }
                return results;
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @Timeout(20)
        @DisplayName("10 different offers accepted at the same time on one listing: only the first succeeds")
        void competingAcceptances() throws Exception {
            List<Offer> offers = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                offers.add(priceOffer(sellListing, storeBuyer("s05_racer" + i), 100.0 + i));
            }
            List<Callable<OfferManagementResult>> tasks = new ArrayList<>();
            for (Offer offer : offers) {
                tasks.add(() -> service.acceptOffer(offer.getId()));
            }

            List<OfferManagementResult> results = runTogether(tasks);

            assertEquals(1, results.stream().filter(OfferManagementResult::isSuccess).count());
            results.stream().filter(r -> !r.isSuccess())
                    .forEach(r -> assertEquals(OfferManagementStatus.OFFER_NOT_PENDING, r.getStatus(), r.getMessage()));
            assertEquals(1, dealsFor(sellListing).size(), "Exactly one deal is recorded");
            assertEquals(ListingStatus.RESERVED, sellListing.getStatus());
            assertEquals(1, offers.stream().filter(Offer::isAccepted).count());
            assertEquals(THREADS - 1, offers.stream().filter(Offer::isDeclined).count());
            Offer winner = offers.stream().filter(Offer::isAccepted).findFirst().orElseThrow();
            assertEquals(winner.getId(), dealsFor(sellListing).get(0).getOfferId());
        }

        @Test
        @Timeout(20)
        @DisplayName("The same offer accepted 10 times at once makes a single deal")
        void sameOfferAcceptedTwice() throws Exception {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);
            List<Callable<OfferManagementResult>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                tasks.add(() -> service.acceptOffer(offer.getId()));
            }

            List<OfferManagementResult> results = runTogether(tasks);

            assertEquals(1, results.stream().filter(OfferManagementResult::isSuccess).count());
            assertEquals(1, dealsFor(sellListing).size());
            assertEquals(1, notificationsTitled(buyer1, "Offer Accepted").size(), "The buyer is notified once");
        }

        @Test
        @Timeout(30)
        @DisplayName("Accept racing the buyer's withdrawal: exactly one wins and the state stays consistent")
        void acceptRacingWithdrawal() throws Exception {
            for (int round = 0; round < 25; round++) {
                Listing listing = storeListing(seller, "Race item " + round, ListingType.SELL);
                Offer offer = priceOffer(listing, buyer1, 150.0);

                List<Callable<Boolean>> tasks = List.of(
                        () -> service.acceptOffer(offer.getId()).isSuccess(),
                        () -> SessionManager.withSession(buyer1, () ->
                                DATA.withdrawOffer(offer.getId(), buyer1.getUsername()).isSuccess()));
                List<Boolean> wins = runTogether(tasks);

                assertNotEquals(wins.get(0), wins.get(1), "Exactly one of accept and withdraw must succeed");
                if (wins.get(0)) {
                    assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
                    assertEquals(ListingStatus.RESERVED, listing.getStatus());
                    assertEquals(1, dealsFor(listing).size());
                } else {
                    assertEquals(OfferStatus.WITHDRAWN, offer.getStatus());
                    assertEquals(ListingStatus.LIVE, listing.getStatus());
                    assertTrue(dealsFor(listing).isEmpty());
                }
            }
        }

        @Test
        @Timeout(20)
        @DisplayName("Accept racing decline on the same offer: exactly one wins")
        void acceptRacingDecline() throws Exception {
            Offer offer = priceOffer(sellListing, buyer1, 150.0);

            List<Boolean> wins = runTogether(List.of(
                    () -> service.acceptOffer(offer.getId()).isSuccess(),
                    () -> service.declineOffer(offer.getId(), "Changed my mind").isSuccess()));

            assertNotEquals(wins.get(0), wins.get(1));
            assertEquals(wins.get(0) ? 1 : 0, dealsFor(sellListing).size());
        }

        @Test
        @Timeout(30)
        @DisplayName("Offers made while an acceptance runs never stay pending on a reserved listing")
        void newOffersDuringAcceptance() throws Exception {
            Offer accepted = priceOffer(sellListing, buyer1, 150.0);
            List<Callable<Boolean>> tasks = new ArrayList<>();
            tasks.add(() -> service.acceptOffer(accepted.getId()).isSuccess());
            for (int i = 0; i < THREADS - 1; i++) {
                Buyer late = storeBuyer("s05_late" + i);
                double price = 120.0 + i;
                tasks.add(() -> SessionManager.withSession(late, () ->
                        DATA.submitPriceOffer(sellListing.getId(), late.getUsername(), price).isSuccess()));
            }

            List<Boolean> results = runTogether(tasks);

            assertTrue(results.get(0), "The acceptance itself succeeds");
            assertEquals(ListingStatus.RESERVED, sellListing.getStatus());
            assertTrue(DATA.getOffersForListing(sellListing.getId()).stream().noneMatch(Offer::isPending),
                    "Every offer is either accepted, declined, or was refused because the listing was reserved");
        }
    }
}
