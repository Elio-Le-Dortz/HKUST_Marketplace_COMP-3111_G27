package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.ActOnListingResult;
import com.hkust.marketplace.models.AdminListingOperationResult;
import com.hkust.marketplace.models.AdminRatingOperationResult;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealOperationResult;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReviewResult;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.MyListingResult;
import com.hkust.marketplace.models.MyListingStatus;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferManagementResult;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.RateSellerResult;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.ReviewFlagReason;
import com.hkust.marketplace.models.ReviewHandlingResult;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SellerListingStatistics;
import com.hkust.marketplace.models.SellerRatingInfo;
import com.hkust.marketplace.models.SellerStatistics;
import com.hkust.marketplace.models.UserManagementResult;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of use case 2.8 "View Seller Statistics" (US-S08), grouped by the flows of its
 * specification. Acceptance criterion of the Sprint 2 backlog: the JavaFX charts accurately
 * reflect live data, so the data of the charts is checked after the real flows of the three
 * portals: buyers opening listings (use case 1.4) and sending offers, borrow requests and claims
 * (use case 1.5), the Seller handling them (use case 2.5), buyers completing and returning deals
 * (use case 1.6) and rating the Seller (use case 1.10), and the admin reviewing listings and
 * ratings (use cases 3.2, 3.7 and 3.8).
 *
 * <p>The assertions compare numbers, never text formatted with the default locale, so the tests
 * give the same result in IntelliJ IDEA and with Maven, whatever the locale of the computer.</p>
 */
@DisplayName("SellerStatisticsService: US-S08 View Seller Statistics")
class SellerStatisticsServiceTest {

    private static final DummyDataService DATA = DummyDataService.getInstance();
    /** The tests never log in with a password, so the stored hash is never checked. */
    private static final String UNUSED_HASH = "not-checked-by-these-tests";
    private static final String ADMIN = DummyDataService.SEEDED_ADMIN_USERNAME;

    private final SellerStatisticsService service = new SellerStatisticsService(DATA);
    private final OfferManagementService offerService = new OfferManagementService(DATA);

    private Seller seller;
    private Seller otherSeller;
    private Buyer buyer1;
    private Buyer buyer2;
    private Buyer buyer3;

    /**
     * Starts every test from the seeded data, with a fresh Seller without listings, another
     * Seller, three buyers, and the Seller logged in.
     */
    @BeforeEach
    void setUp() {
        DATA.resetData();
        SessionManager.logout();
        seller = storeSeller("s08_seller", "Sam Seller");
        otherSeller = storeSeller("s08_other", "Olive Other");
        buyer1 = storeBuyer("s08_buyer1", "Alice Buyer");
        buyer2 = storeBuyer("s08_buyer2", "Bob Buyer");
        buyer3 = storeBuyer("s08_buyer3", "Cara Buyer");
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

    private static Buyer storeBuyer(String username, String fullName) {
        Buyer b = new Buyer(username, fullName, UNUSED_HASH, "Hall 1");
        assertTrue(DATA.registerUserIfAbsent(b), "Test buyer could not be stored");
        return b;
    }

    /** Stores a listing of a Seller with a given title, type and status. */
    private static Listing storeListing(Seller owner, String title, ListingType type, ListingStatus status) {
        Listing listing = new Listing();
        listing.setTitle(title);
        listing.setDescription("Test listing");
        listing.setCategory("Electronics");
        listing.setCondition("Used");
        listing.setMeetupLocation("Library");
        listing.setType(type);
        if (type == ListingType.SELL) {
            listing.setPrice(200.0);
        } else if (type == ListingType.LEND) {
            listing.setLendingDuration(Duration.ofDays(7));
        }
        listing.setSellerUsername(owner.getUsername());
        listing.setSellerFullName(owner.getFullName());
        listing.setStatus(status);
        DATA.addListing(listing);
        return listing;
    }

    private Listing liveListing(String title, ListingType type) {
        return storeListing(seller, title, type, ListingStatus.LIVE);
    }

    /** Buyers open the detail page of a listing (use case 1.4), which counts one view each time. */
    private static void view(Listing listing, int times) {
        for (int i = 0; i < times; i++) {
            assertTrue(DATA.viewListing(listing.getId(), false).isSuccess());
        }
    }

    /** A buyer makes a price offer through use case 1.5. */
    private static Offer priceOffer(Listing listing, Buyer buyer, double price) {
        return SessionManager.withSession(buyer, () -> {
            ActOnListingResult result = DATA.submitPriceOffer(listing.getId(), buyer.getUsername(), price);
            assertTrue(result.isSuccess(), result.getMessage());
            return result.getOffer();
        });
    }

    /** A buyer asks to borrow a Lend listing through use case 1.5. */
    private static Offer borrowRequest(Listing listing, Buyer buyer) {
        return SessionManager.withSession(buyer, () -> {
            LocalDateTime start = LocalDateTime.now().plusSeconds(2);
            ActOnListingResult result = DATA.submitBorrowRequest(listing.getId(), buyer.getUsername(), start,
                    start.plusMinutes(10));
            assertTrue(result.isSuccess(), result.getMessage());
            return result.getOffer();
        });
    }

    /** A buyer claims a Give Away listing through use case 1.5. */
    private static Offer claim(Listing listing, Buyer buyer) {
        return SessionManager.withSession(buyer, () -> {
            ActOnListingResult result = DATA.submitClaim(listing.getId(), buyer.getUsername());
            assertTrue(result.isSuccess(), result.getMessage());
            return result.getOffer();
        });
    }

    /** The logged-in Seller accepts an offer through use case 2.5. */
    private Deal accept(Offer offer) {
        OfferManagementResult result = offerService.acceptOffer(offer.getId());
        assertTrue(result.isSuccess(), result.getMessage());
        return result.getDeal();
    }

    /** The buyer marks a deal complete through use case 1.6. */
    private static void complete(Deal deal, Buyer buyer) {
        SessionManager.withSession(buyer, () -> {
            DealOperationResult result = DATA.markDealComplete(deal.getId(), buyer.getUsername());
            assertTrue(result.isSuccess(), result.getMessage());
        });
    }

    /** The buyer returns a borrowed item through use case 1.6. */
    private static void giveBack(Deal deal, Buyer buyer) {
        SessionManager.withSession(buyer, () -> {
            DealOperationResult result = DATA.selfReturnItem(deal.getId(), buyer.getUsername());
            assertTrue(result.isSuccess(), result.getMessage());
        });
    }

    /** A Sell listing is sold: offer, acceptance and completion. */
    private Deal sell(Listing listing, Buyer buyer) {
        Deal deal = accept(priceOffer(listing, buyer, 180.0));
        complete(deal, buyer);
        return deal;
    }

    /** A Lend listing is lent and returned: borrow request, acceptance and return. */
    private Deal lendAndReturn(Listing listing, Buyer buyer) {
        Deal deal = accept(borrowRequest(listing, buyer));
        giveBack(deal, buyer);
        return deal;
    }

    /** A Give Away listing is given: claim, acceptance and completion. */
    private Deal giveAway(Listing listing, Buyer buyer) {
        Deal deal = accept(claim(listing, buyer));
        complete(deal, buyer);
        return deal;
    }

    /** Writes a review through the real buyer flow: a sale completed then rated (use case 1.10). */
    private Review review(Buyer buyer, int rating) {
        Deal deal = sell(liveListing("Rated item " + rating + " by " + buyer.getUsername(), ListingType.SELL), buyer);
        RateSellerResult rated = SessionManager.withSession(buyer, () -> DATA.getBuyerReviewService()
                .submitRating(deal.getId(), buyer.getUsername(), rating, "Smooth deal."));
        assertTrue(rated.isSuccess(), rated.getMessage());
        return rated.getReview();
    }

    /** Returns the statistics of one listing, failing if the listing is not counted. */
    private static SellerListingStatistics statsOf(SellerStatistics stats, Listing listing) {
        return stats.getListings().stream()
                .filter(s -> s.listingId().equals(listing.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Listing " + listing.getId() + " is not counted"));
    }

    private static List<String> idsOf(SellerStatistics stats) {
        return stats.getListings().stream().map(SellerListingStatistics::listingId).toList();
    }

    // ------------------------------------------------------------------
    // Access control
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Seller session is checked (role-based access control)")
    class AccessControl {

        @Test
        @DisplayName("Without a session, the statistics are refused")
        void noSession() {
            SessionManager.logout();

            assertThrows(SecurityException.class, service::getMyStatistics);
        }

        @Test
        @DisplayName("A Buyer session cannot read a Seller's statistics")
        void buyerSession() {
            SessionManager.login(buyer1);

            assertThrows(SecurityException.class, service::getMyStatistics);
        }

        @Test
        @DisplayName("An Admin session cannot read a Seller's statistics")
        void adminSession() {
            SessionManager.login(DATA.findUser(ADMIN));

            assertThrows(SecurityException.class, service::getMyStatistics);
        }

        @Test
        @DisplayName("A Seller deactivated by the admin during the session is refused (use case 3.3)")
        void deactivatedByAdmin() {
            UserManagementResult result = DATA.getUserManagementService()
                    .changeAccountStatus(seller.getUsername(), AccountStatus.DEACTIVATED, ADMIN);
            assertTrue(result.isSuccess(), result.getMessage());

            assertThrows(SecurityException.class, service::getMyStatistics);
        }

        @Test
        @DisplayName("A Seller whose account is no longer active, but still in session, is refused")
        void inactiveInSession() {
            seller.setAccountStatus(AccountStatus.DELETED);

            assertThrows(SecurityException.class, service::getMyStatistics);
        }

        @Test
        @DisplayName("A refused call changes nothing: an expired loan is not auto-returned")
        void refusedCallChangesNothing() {
            Deal deal = accept(borrowRequest(liveListing("Tripod", ListingType.LEND), buyer1));
            deal.setDueDate(LocalDateTime.now().minusSeconds(1));
            SessionManager.login(buyer1);

            assertThrows(SecurityException.class, service::getMyStatistics);
            assertEquals(DealStatus.ACTIVE, deal.getStatus());
        }

        @Test
        @DisplayName("A Seller only sees their own listings, never another Seller's")
        void ownListingsOnly() {
            Listing mine = liveListing("Mine", ListingType.SELL);
            Listing theirs = storeListing(otherSeller, "Theirs", ListingType.SELL, ListingStatus.LIVE);
            view(theirs, 4);
            priceOffer(theirs, buyer1, 100.0);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(seller.getUsername(), stats.getSellerUsername());
            assertEquals(List.of(mine.getId()), idsOf(stats));
            assertEquals(0, stats.getTotalViews());
            assertEquals(0, stats.getOffersReceived());
        }
    }

    // ------------------------------------------------------------------
    // Step 2: which listings are counted
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Step 2: the statistics cover the Seller's listings, as on My Listings")
    class ListingsCovered {

        @Test
        @DisplayName("Every status is counted: awaiting review, live, rejected, reserved, sold and taken down")
        void everyStatus() {
            Listing pending = storeListing(seller, "Pending", ListingType.SELL, ListingStatus.PENDING_REVIEW);
            Listing rejected = storeListing(seller, "Rejected", ListingType.SELL, ListingStatus.PENDING_REVIEW);
            ListingReviewResult rejection = DATA.rejectListing(rejected.getId(), ADMIN, "Blurry photos");
            assertTrue(rejection.isSuccess(), rejection.getMessage());
            Listing live = liveListing("Live", ListingType.SELL);
            Listing reserved = liveListing("Reserved", ListingType.SELL);
            accept(priceOffer(reserved, buyer1, 150.0));
            Listing sold = liveListing("Sold", ListingType.SELL);
            sell(sold, buyer2);
            Listing takenDown = liveListing("Taken down", ListingType.SELL);
            AdminListingOperationResult takeDown = DATA.takeDownListing(takenDown.getId(), "Counterfeit", ADMIN);
            assertTrue(takeDown.isSuccess(), takeDown.getMessage());

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(6, stats.getListingCount());
            assertEquals(ListingStatus.PENDING_REVIEW, statsOf(stats, pending).status());
            assertEquals(ListingStatus.REJECTED, statsOf(stats, rejected).status());
            assertEquals(ListingStatus.LIVE, statsOf(stats, live).status());
            assertEquals(ListingStatus.RESERVED, statsOf(stats, reserved).status());
            assertEquals(ListingStatus.SOLD, statsOf(stats, sold).status());
            assertEquals(ListingStatus.REMOVED, statsOf(stats, takenDown).status());
        }

        @Test
        @DisplayName("A listing taken down by the admin keeps its views and offers (use case 3.7)")
        void takenDownKeepsHistory() {
            Listing listing = liveListing("Speaker", ListingType.SELL);
            view(listing, 3);
            priceOffer(listing, buyer1, 90.0);
            DATA.takeDownListing(listing.getId(), "Reported", ADMIN);

            SellerListingStatistics stats = statsOf(service.getMyStatistics(), listing);

            assertEquals(3, stats.views());
            assertEquals(1, stats.offersReceived());
        }

        @Test
        @DisplayName("A listing the Seller deleted is no longer counted, like on My Listings (use case 2.4)")
        void deletedListingNotCounted() {
            Listing kept = liveListing("Kept", ListingType.SELL);
            Listing deleted = liveListing("Deleted", ListingType.SELL);
            view(kept, 1);
            view(deleted, 5);
            priceOffer(deleted, buyer1, 50.0);
            MyListingsService myListings = new MyListingsService(DATA);
            MyListingResult warning = myListings.deleteListing(deleted.getId(), null);
            assertEquals(MyListingStatus.PENDING_OFFERS, warning.getStatus());
            assertTrue(myListings.deleteListing(deleted.getId(), warning.getOfferIds()).isSuccess());

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(List.of(kept.getId()), idsOf(stats));
            assertEquals(1, stats.getTotalViews());
            assertEquals(0, stats.getOffersReceived());
        }

        @Test
        @DisplayName("The listings are in the order of My Listings: newest first")
        void sameOrderAsMyListings() {
            Listing oldest = liveListing("Oldest", ListingType.SELL);
            oldest.setPostedDate(LocalDateTime.now().minusDays(3));
            Listing newest = liveListing("Newest", ListingType.LEND);
            newest.setPostedDate(LocalDateTime.now().minusMinutes(1));
            Listing middle = liveListing("Middle", ListingType.GIVEAWAY);
            middle.setPostedDate(LocalDateTime.now().minusDays(1));

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(List.of(newest.getId(), middle.getId(), oldest.getId()), idsOf(stats));
            List<String> myListingsOrder = new MyListingsService(DATA).getMyListings().stream()
                    .map(Listing::getId).toList();
            assertEquals(myListingsOrder, idsOf(stats));
        }

        @Test
        @DisplayName("Each listing keeps its title and type")
        void titleAndType() {
            Listing lend = liveListing("Graphing Calculator", ListingType.LEND);

            SellerListingStatistics stats = statsOf(service.getMyStatistics(), lend);

            assertEquals("Graphing Calculator", stats.title());
            assertEquals(ListingType.LEND, stats.type());
        }
    }

    // ------------------------------------------------------------------
    // Step 2: views
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Step 2: total views, counted each time a buyer opens a listing (use case 1.4)")
    class Views {

        @Test
        @DisplayName("Opening listings adds views to each listing and to the total")
        void viewsCounted() {
            Listing lamp = liveListing("Lamp", ListingType.SELL);
            Listing tent = liveListing("Tent", ListingType.LEND);
            Listing chair = liveListing("Chair", ListingType.GIVEAWAY);
            view(lamp, 3);
            view(tent, 2);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(3, statsOf(stats, lamp).views());
            assertEquals(2, statsOf(stats, tent).views());
            assertEquals(0, statsOf(stats, chair).views());
            assertEquals(5, stats.getTotalViews());
        }

        @Test
        @DisplayName("A listing that is no longer live is not opened from the marketplace, so its views stay the same")
        void notLiveNotCounted() {
            Listing listing = storeListing(seller, "Pending", ListingType.SELL, ListingStatus.PENDING_REVIEW);

            assertFalse(DATA.viewListing(listing.getId(), true).isSuccess());

            assertEquals(0, service.getMyStatistics().getTotalViews());
        }
    }

    // ------------------------------------------------------------------
    // Step 2: offers received
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Step 2: offers received (offers, borrow requests and claims)")
    class OffersReceived {

        @Test
        @DisplayName("Price offers, borrow requests and claims are all counted")
        void everyKind() {
            Listing sell = liveListing("Bike", ListingType.SELL);
            Listing lend = liveListing("Projector", ListingType.LEND);
            Listing give = liveListing("Plant", ListingType.GIVEAWAY);
            priceOffer(sell, buyer1, 300.0);
            priceOffer(sell, buyer2, 320.0);
            borrowRequest(lend, buyer1);
            claim(give, buyer3);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(2, statsOf(stats, sell).offersReceived());
            assertEquals(1, statsOf(stats, lend).offersReceived());
            assertEquals(1, statsOf(stats, give).offersReceived());
            assertEquals(4, stats.getOffersReceived());
        }

        @Test
        @DisplayName("An offer stays counted whatever its status: declined, countered, withdrawn or accepted")
        void whateverStatus() {
            Listing listing = liveListing("Desk", ListingType.SELL);
            Offer declined = priceOffer(listing, buyer1, 100.0);
            assertTrue(offerService.declineOffer(declined.getId(), "Too low").isSuccess());
            Offer countered = priceOffer(listing, buyer2, 120.0);
            assertTrue(offerService.counterWithPrice(countered.getId(), 150.0).isSuccess());
            Offer withdrawn = priceOffer(listing, buyer3, 130.0);
            assertTrue(SessionManager.withSession(buyer3, () ->
                    DATA.withdrawOffer(withdrawn.getId(), buyer3.getUsername()).isSuccess()));
            Offer accepted = priceOffer(listing, buyer1, 140.0);
            accept(accepted);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(OfferStatus.DECLINED, countered.getStatus(), "declined when another offer was accepted");
            assertEquals(4, stats.getOffersReceived());
        }

        @Test
        @DisplayName("A counter offer is made by the Seller, not received, so it is not counted")
        void counterNotCounted() {
            Listing listing = liveListing("Guitar", ListingType.SELL);
            Offer offer = priceOffer(listing, buyer1, 400.0);
            assertTrue(offerService.counterWithPrice(offer.getId(), 450.0).isSuccess());

            assertEquals(1, service.getMyStatistics().getOffersReceived());
        }

        @Test
        @DisplayName("A formal offer sent from the chat is counted like any other offer (use case 1.5)")
        void formalOfferFromChat() {
            Listing listing = liveListing("Camera", ListingType.SELL);
            String threadId = DATA.getChatService().getOrCreateThread(listing.getId(), buyer1.getUsername()).getId();
            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.getChatService().sendFormalPriceOffer(threadId, buyer1.getUsername(), 500.0).isSuccess()));

            assertEquals(1, service.getMyStatistics().getOffersReceived());
        }
    }

    // ------------------------------------------------------------------
    // Step 2 and step 5: completed deals
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Steps 2 and 5: completed deals, by type (Sold, Lent, Given)")
    class CompletedDeals {

        @Test
        @DisplayName("A sale marked complete by the buyer counts as Sold")
        void sold() {
            Listing listing = liveListing("Textbook", ListingType.SELL);
            sell(listing, buyer1);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(1, stats.getCompletedDeals(DealType.SALE));
            assertEquals(1, stats.getCompletedDeals());
            assertEquals(1, statsOf(stats, listing).completedDeals());
            assertEquals(ListingStatus.SOLD, statsOf(stats, listing).status());
        }

        @Test
        @DisplayName("A giveaway marked complete by the buyer counts as Given")
        void given() {
            Listing listing = liveListing("Hoodie", ListingType.GIVEAWAY);
            giveAway(listing, buyer2);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(1, stats.getCompletedDeals(DealType.GIVEAWAY));
            assertEquals(ListingStatus.GIVEN, statsOf(stats, listing).status());
        }

        @Test
        @DisplayName("A loan returned by the buyer counts as Lent, and the listing is live again")
        void lentAndReturned() {
            Listing listing = liveListing("Calculator", ListingType.LEND);
            lendAndReturn(listing, buyer1);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(1, stats.getCompletedDeals(DealType.LOAN));
            assertEquals(ListingStatus.LIVE, statsOf(stats, listing).status());
        }

        @Test
        @DisplayName("A loan whose period expired is auto-returned first, then counted as Lent (use case 1.6 A4)")
        void autoReturned() {
            Listing listing = liveListing("Tripod", ListingType.LEND);
            Deal deal = accept(borrowRequest(listing, buyer1));
            deal.setDueDate(LocalDateTime.now().minusSeconds(1)); // the borrow period has expired

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(DealStatus.RETURNED, deal.getStatus());
            assertEquals(1, stats.getCompletedDeals(DealType.LOAN));
            assertEquals(ListingStatus.LIVE, statsOf(stats, listing).status());
        }

        @Test
        @DisplayName("A listing lent twice has two completed deals")
        void lentTwice() {
            Listing listing = liveListing("Drone", ListingType.LEND);
            lendAndReturn(listing, buyer1);
            lendAndReturn(listing, buyer2);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(2, statsOf(stats, listing).completedDeals());
            assertEquals(2, stats.getCompletedDeals(DealType.LOAN));
        }

        @Test
        @DisplayName("Every type is counted separately for the pie chart")
        void everyType() {
            sell(liveListing("Sofa", ListingType.SELL), buyer1);
            sell(liveListing("Table", ListingType.SELL), buyer2);
            lendAndReturn(liveListing("Ladder", ListingType.LEND), buyer3);
            giveAway(liveListing("Books", ListingType.GIVEAWAY), buyer1);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(Map.of(DealType.SALE, 2, DealType.LOAN, 1, DealType.GIVEAWAY, 1), stats.getCompletedDealsByType());
            assertEquals(4, stats.getCompletedDeals());
        }

        @Test
        @DisplayName("An active deal is not completed yet: a reserved sale or an item still lent")
        void activeNotCounted() {
            accept(priceOffer(liveListing("Monitor", ListingType.SELL), buyer1, 120.0));
            accept(borrowRequest(liveListing("Tent", ListingType.LEND), buyer2));

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(0, stats.getCompletedDeals());
        }

        @Test
        @DisplayName("Another Seller's completed deals are not counted")
        void otherSellersDeals() {
            Listing theirs = storeListing(otherSeller, "Theirs", ListingType.SELL, ListingStatus.LIVE);
            SessionManager.login(otherSeller);
            sell(theirs, buyer1);
            SessionManager.login(seller);

            assertEquals(0, service.getMyStatistics().getCompletedDeals());
        }

        @Test
        @DisplayName("A deal is only counted when it belongs to the Seller and has a type")
        void inconsistentDealsIgnored() {
            Listing listing = storeListing(seller, "Mug", ListingType.SELL, ListingStatus.SOLD);
            Deal foreign = new Deal();
            foreign.setListingId(listing.getId());
            foreign.setSellerUsername(otherSeller.getUsername());
            foreign.setBuyerUsername(buyer1.getUsername());
            foreign.setStatus(DealStatus.COMPLETED);
            DATA.addDeal(foreign);
            Deal untyped = new Deal();
            untyped.setListingId(listing.getId());
            untyped.setSellerUsername(seller.getUsername());
            untyped.setBuyerUsername(buyer1.getUsername());
            untyped.setType(null);
            untyped.setStatus(DealStatus.COMPLETED);
            DATA.addDeal(untyped);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(0, stats.getCompletedDeals());
            assertEquals(0, statsOf(stats, listing).completedDeals());
        }

        @Test
        @DisplayName("The completed deals of a listing the Seller deleted are no longer counted")
        void deletedListingDeals() {
            Listing listing = liveListing("Projector", ListingType.LEND);
            lendAndReturn(listing, buyer1);
            assertTrue(new MyListingsService(DATA).deleteListing(listing.getId(), null).isSuccess());

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(0, stats.getCompletedDeals());
            assertFalse(stats.hasListings());
        }
    }

    // ------------------------------------------------------------------
    // Step 2: average rating
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Step 2: average rating, without the reviews hidden by the admin")
    class AverageRating {

        @Test
        @DisplayName("Step 3: a Seller without ratings has \"No ratings yet\", although other Sellers have ratings")
        void noRatings() {
            SellerStatistics stats = service.getMyStatistics();

            assertFalse(stats.hasRatings());
            assertEquals(SellerRatingInfo.NO_RATINGS_TEXT, stats.getRating().getDisplayText());
            assertEquals(0, stats.getRating().getRatingCount());
        }

        @Test
        @DisplayName("The average of the buyers' ratings is rounded to one decimal (use case 1.10)")
        void average() {
            review(buyer1, 5);
            review(buyer2, 4);
            review(buyer3, 4);

            SellerRatingInfo rating = service.getMyStatistics().getRating();

            assertTrue(rating.hasRatings());
            assertEquals(4.3, rating.getAverageRating(), 1e-9);
            assertEquals(3, rating.getRatingCount());
        }

        @Test
        @DisplayName("A review hidden by the admin is not counted, and counts again once unhidden (use case 3.8)")
        void hiddenReviewExcluded() {
            review(buyer1, 5);
            Review low = review(buyer2, 1);

            AdminRatingOperationResult hidden = DATA.hideReviewByAdmin(low.getId(), ADMIN);
            assertTrue(hidden.isSuccess(), hidden.getMessage());
            SellerRatingInfo whileHidden = service.getMyStatistics().getRating();
            assertTrue(DATA.unhideReviewByAdmin(low.getId(), ADMIN).isSuccess());
            SellerRatingInfo afterUnhide = service.getMyStatistics().getRating();

            assertEquals(5.0, whileHidden.getAverageRating(), 1e-9);
            assertEquals(1, whileHidden.getRatingCount());
            assertEquals(3.0, afterUnhide.getAverageRating(), 1e-9);
            assertEquals(2, afterUnhide.getRatingCount());
        }

        @Test
        @DisplayName("A flagged review is still counted until the admin decides (use case 2.9)")
        void flaggedStillCounted() {
            review(buyer1, 5);
            Review flagged = review(buyer2, 2);
            ReviewHandlingResult flag = new ReviewHandlingService(DATA)
                    .flagReview(flagged.getId(), ReviewFlagReason.OFFENSIVE_LANGUAGE, null);
            assertTrue(flag.isSuccess(), flag.getMessage());

            SellerRatingInfo rating = service.getMyStatistics().getRating();

            assertEquals(3.5, rating.getAverageRating(), 1e-9);
            assertEquals(2, rating.getRatingCount());
        }

        @Test
        @DisplayName("The rating is the one of the Reviews & Feedback screen")
        void sameAsReviewsScreen() {
            review(buyer1, 4);
            review(buyer2, 3);
            DATA.hideReviewByAdmin(review(buyer3, 1).getId(), ADMIN);

            SellerRatingInfo statistics = service.getMyStatistics().getRating();
            SellerRatingInfo reviewsScreen = new ReviewHandlingService(DATA).getMyRating();

            assertEquals(reviewsScreen.getAverageRating(), statistics.getAverageRating(), 1e-9);
            assertEquals(reviewsScreen.getRatingCount(), statistics.getRatingCount());
        }
    }

    // ------------------------------------------------------------------
    // Step 4: data of the bar chart
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Step 4: data of the bar chart (views and offers received for each listing)")
    class BarChart {

        @Test
        @DisplayName("Each listing has one group of bars, with its views and offers received")
        void oneGroupPerListing() {
            Listing lamp = liveListing("Lamp", ListingType.SELL);
            Listing tent = liveListing("Tent", ListingType.LEND);
            view(lamp, 4);
            priceOffer(lamp, buyer1, 30.0);
            view(tent, 1);
            borrowRequest(tent, buyer2);
            borrowRequest(tent, buyer3);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals(2, stats.getListings().size());
            assertEquals(4, statsOf(stats, lamp).views());
            assertEquals(1, statsOf(stats, lamp).offersReceived());
            assertEquals(1, statsOf(stats, tent).views());
            assertEquals(2, statsOf(stats, tent).offersReceived());
            assertEquals(4, stats.getMaxListingValue());
        }

        @Test
        @DisplayName("Two listings with the same title get two different labels, so the chart keeps both")
        void sameTitleTwoLabels() {
            Listing first = liveListing("Desk Lamp", ListingType.SELL);
            Listing second = liveListing("Desk Lamp", ListingType.SELL);

            SellerStatistics stats = service.getMyStatistics();

            assertEquals("Desk Lamp", statsOf(stats, first).title());
            assertEquals("Desk Lamp", statsOf(stats, second).title());
            assertEquals(Set.of("Desk Lamp", "Desk Lamp (2)"),
                    stats.getListings().stream().map(SellerListingStatistics::chartLabel).collect(Collectors.toSet()));
        }

        @Test
        @DisplayName("A long title is shortened on the chart but kept whole in the breakdown")
        void longTitle() {
            String title = "Barely used IKEA standing desk with cable tray";
            Listing listing = liveListing(title, ListingType.SELL);

            SellerListingStatistics stats = statsOf(service.getMyStatistics(), listing);

            assertEquals(title, stats.title());
            assertTrue(stats.chartLabel().endsWith(SellerStatisticsService.ELLIPSIS));
            assertTrue(stats.chartLabel().length() <= SellerStatisticsService.MAX_CHART_LABEL_LENGTH);
        }

        @Test
        @DisplayName("A listing without title is shown as \"Untitled listing\"")
        void untitled() {
            Listing listing = liveListing(null, ListingType.SELL);

            SellerListingStatistics stats = statsOf(service.getMyStatistics(), listing);

            assertEquals(SellerStatisticsService.UNTITLED, stats.title());
            assertFalse(stats.chartLabel().isBlank());
        }
    }

    // ------------------------------------------------------------------
    // Alternative flows
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("A1: No Listings")
    class NoListings {

        @Test
        @DisplayName("A new Seller gets zero values and no listing, so the screen shows a message instead of the charts")
        void newSeller() {
            SellerStatistics stats = service.getMyStatistics();

            assertFalse(stats.hasListings());
            assertTrue(stats.getListings().isEmpty());
            assertEquals(0, stats.getTotalViews());
            assertEquals(0, stats.getOffersReceived());
            assertEquals(0, stats.getCompletedDeals());
            assertFalse(stats.hasCompletedDeals());
        }

        @Test
        @DisplayName("A Seller whose only listing was deleted is back to A1")
        void onlyListingDeleted() {
            Listing listing = liveListing("Old Phone", ListingType.SELL);
            view(listing, 2);
            assertTrue(new MyListingsService(DATA).deleteListing(listing.getId(), null).isSuccess());

            SellerStatistics stats = service.getMyStatistics();

            assertFalse(stats.hasListings());
            assertEquals(0, stats.getTotalViews());
        }
    }

    @Nested
    @DisplayName("A2: No Completed Deals")
    class NoCompletedDeals {

        @Test
        @DisplayName("Listings with views and offers but no completed deal: the bar chart is shown, not the pie chart")
        void barChartOnly() {
            Listing listing = liveListing("Keyboard", ListingType.SELL);
            view(listing, 3);
            priceOffer(listing, buyer1, 80.0);

            SellerStatistics stats = service.getMyStatistics();

            assertTrue(stats.hasListings());
            assertFalse(stats.hasCompletedDeals());
            assertEquals(Map.of(DealType.SALE, 0, DealType.LOAN, 0, DealType.GIVEAWAY, 0), stats.getCompletedDealsByType());
            assertEquals(3, stats.getTotalViews());
        }

        @Test
        @DisplayName("Accepted deals that are still active do not leave A2")
        void activeDealsStillA2() {
            accept(claim(liveListing("Plant", ListingType.GIVEAWAY), buyer1));

            assertFalse(service.getMyStatistics().hasCompletedDeals());
        }
    }

    // ------------------------------------------------------------------
    // Live data (acceptance criterion)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Acceptance criterion: the charts reflect live data")
    class LiveData {

        @Test
        @DisplayName("Each call computes the statistics again, and an earlier result does not change")
        void recomputedEachTime() {
            Listing listing = liveListing("Scooter", ListingType.SELL);
            SellerStatistics before = service.getMyStatistics();

            view(listing, 2);
            sell(listing, buyer1);
            SellerStatistics after = service.getMyStatistics();

            assertEquals(0, before.getTotalViews());
            assertEquals(0, before.getCompletedDeals());
            assertEquals(2, after.getTotalViews());
            assertEquals(1, after.getOffersReceived());
            assertEquals(1, after.getCompletedDeals());
        }

        @Test
        @DisplayName("The time of the computation is recorded")
        void computedAt() {
            LocalDateTime before = LocalDateTime.now();
            SellerStatistics stats = service.getMyStatistics();
            LocalDateTime after = LocalDateTime.now();

            assertFalse(stats.getComputedAt().isBefore(before));
            assertFalse(stats.getComputedAt().isAfter(after));
        }

        @Test
        @DisplayName("The service can be created on the shared data store, as the dashboard does")
        void defaultConstructor() {
            assertEquals(seller.getUsername(), new SellerStatisticsService().getMyStatistics().getSellerUsername());
        }

        @Test
        @DisplayName("The data store is required")
        void dataStoreRequired() {
            assertThrows(NullPointerException.class, () -> new SellerStatisticsService(null));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("isCompletedDeal(): Completed and Returned deals only")
    class IsCompletedDeal {

        @ParameterizedTest(name = "{0}")
        @EnumSource(DealStatus.class)
        @DisplayName("a deal is completed when it is Completed or Returned")
        void byStatus(DealStatus status) {
            Deal deal = new Deal();
            deal.setStatus(status);

            assertEquals(status != DealStatus.ACTIVE, SellerStatisticsService.isCompletedDeal(deal));
        }

        @Test
        @DisplayName("no deal is not completed")
        void nullDeal() {
            assertFalse(SellerStatisticsService.isCompletedDeal(null));
        }
    }

    @Nested
    @DisplayName("chartLabels(): labels of the bar chart")
    class ChartLabels {

        @Test
        @DisplayName("short titles are kept, trimmed, in the same order")
        void keptAndTrimmed() {
            assertEquals(List.of("Lamp", "Desk"), SellerStatisticsService.chartLabels(List.of("Lamp", "  Desk  ")));
        }

        @Test
        @DisplayName("repeated titles get a number")
        void repeatedTitles() {
            assertEquals(List.of("Lamp", "Lamp (2)", "Lamp (3)"),
                    SellerStatisticsService.chartLabels(List.of("Lamp", "Lamp", "Lamp")));
        }

        @Test
        @DisplayName("a number never clashes with a title that already looks numbered")
        void numberedTitle() {
            List<String> labels = SellerStatisticsService.chartLabels(List.of("Lamp", "Lamp (2)", "Lamp"));

            assertEquals("Lamp", labels.get(0));
            assertEquals("Lamp (2)", labels.get(1));
            assertEquals(3, Set.copyOf(labels).size());
        }

        @Test
        @DisplayName("missing titles become \"Untitled listing\", numbered when repeated")
        void missingTitles() {
            assertEquals(List.of("Untitled listing", "Untitled listing (2)"),
                    SellerStatisticsService.chartLabels(Arrays.asList(null, "   ")));
        }

        @Test
        @DisplayName("a title of exactly the maximum length is kept")
        void maximumLength() {
            String title = "x".repeat(SellerStatisticsService.MAX_CHART_LABEL_LENGTH);

            assertEquals(List.of(title), SellerStatisticsService.chartLabels(List.of(title)));
        }

        @Test
        @DisplayName("a longer title is shortened to the maximum length, ending with an ellipsis")
        void shortened() {
            String label = SellerStatisticsService.chartLabels(List.of("Engineering Notebook & Pen Set")).get(0);

            assertEquals("Engineering Noteb" + SellerStatisticsService.ELLIPSIS, label);
            assertEquals(SellerStatisticsService.MAX_CHART_LABEL_LENGTH, label.codePointCount(0, label.length()));
        }

        @Test
        @DisplayName("an emoji counts as one character and is never cut in half")
        void emoji() {
            String title = "🎸".repeat(30);

            String label = SellerStatisticsService.chartLabels(List.of(title)).get(0);

            assertEquals(SellerStatisticsService.MAX_CHART_LABEL_LENGTH, label.codePointCount(0, label.length()));
            assertEquals("🎸".repeat(SellerStatisticsService.MAX_CHART_LABEL_LENGTH - 1), label.substring(0,
                    label.length() - SellerStatisticsService.ELLIPSIS.length()));
        }

        @Test
        @DisplayName("no titles give no labels, and the list of titles is required")
        void emptyAndNull() {
            assertEquals(List.of(), SellerStatisticsService.chartLabels(List.of()));
            assertThrows(NullPointerException.class, () -> SellerStatisticsService.chartLabels(null));
        }
    }

    // ------------------------------------------------------------------
    // Concurrency
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrency: the statistics are computed from one consistent state")
    class Concurrency {

        private static final int SNAPSHOTS = 60;

        /** Runs tasks at the same time and waits for all of them, failing on any error. */
        private void runConcurrently(List<Callable<Void>> tasks) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<Void>> futures = new ArrayList<>();
                for (Callable<Void> task : tasks) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return task.call();
                    }));
                }
                start.countDown();
                for (Future<Void> future : futures) {
                    future.get(60, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("Statistics computed while buyers open listings never go backwards and end with every view")
        void whileBuyersView() throws Exception {
            Listing lamp = liveListing("Lamp", ListingType.SELL);
            Listing tent = liveListing("Tent", ListingType.LEND);
            int viewers = 6;
            int viewsEach = 40;
            List<String> problems = Collections.synchronizedList(new ArrayList<>());
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int v = 0; v < viewers; v++) {
                tasks.add(() -> {
                    for (int i = 0; i < viewsEach; i++) {
                        DATA.viewListing((i % 2 == 0 ? lamp : tent).getId(), true);
                    }
                    return null;
                });
            }
            tasks.add(() -> {
                int last = 0;
                for (int i = 0; i < SNAPSHOTS; i++) {
                    int total = service.getMyStatistics().getTotalViews();
                    if (total < last || total > viewers * viewsEach) {
                        problems.add("snapshot " + i + ": " + total + " views after " + last);
                    }
                    last = total;
                }
                return null;
            });

            runConcurrently(tasks);

            assertEquals(List.of(), problems);
            SellerStatistics stats = service.getMyStatistics();
            assertEquals(viewers * viewsEach, stats.getTotalViews());
            assertEquals(viewers * viewsEach / 2, statsOf(stats, lamp).views());
        }

        @Test
        @DisplayName("Statistics computed while buyers send offers count every offer exactly once")
        void whileBuyersOffer() throws Exception {
            Listing listing = liveListing("Bike", ListingType.SELL);
            List<Buyer> buyers = List.of(buyer1, buyer2, buyer3);
            int offersEach = 15;
            List<String> problems = Collections.synchronizedList(new ArrayList<>());
            List<Callable<Void>> tasks = new ArrayList<>();
            for (Buyer buyer : buyers) {
                tasks.add(() -> {
                    SessionManager.withSession(buyer, () -> {
                        for (int i = 0; i < offersEach; i++) {
                            DATA.submitPriceOffer(listing.getId(), buyer.getUsername(), 100.0 + i);
                        }
                    });
                    return null;
                });
            }
            tasks.add(() -> {
                int last = 0;
                for (int i = 0; i < SNAPSHOTS; i++) {
                    int offers = service.getMyStatistics().getOffersReceived();
                    if (offers < last) {
                        problems.add("snapshot " + i + ": " + offers + " offers after " + last);
                    }
                    last = offers;
                }
                return null;
            });

            runConcurrently(tasks);

            assertEquals(List.of(), problems);
            assertEquals(buyers.size() * offersEach, service.getMyStatistics().getOffersReceived());
        }

        @Test
        @DisplayName("While buyers complete deals, every snapshot agrees: a listing is Sold exactly when its deal is counted")
        void whileBuyersComplete() throws Exception {
            int count = 8;
            List<Deal> deals = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                deals.add(accept(priceOffer(liveListing("Item " + i, ListingType.SELL), buyer1, 50.0)));
            }
            List<String> problems = Collections.synchronizedList(new ArrayList<>());
            List<Callable<Void>> tasks = new ArrayList<>();
            for (Deal deal : deals) {
                tasks.add(() -> {
                    SessionManager.withSession(buyer1, () -> {
                        DATA.markDealComplete(deal.getId(), buyer1.getUsername());
                    });
                    return null;
                });
            }
            tasks.add(() -> {
                for (int i = 0; i < SNAPSHOTS; i++) {
                    SellerStatistics stats = service.getMyStatistics();
                    for (SellerListingStatistics listing : stats.getListings()) {
                        boolean sold = listing.status() == ListingStatus.SOLD;
                        if (sold != (listing.completedDeals() == 1)) {
                            problems.add("snapshot " + i + ": " + listing.title() + " is " + listing.status()
                                    + " with " + listing.completedDeals() + " completed deal(s)");
                        }
                    }
                    if (stats.getCompletedDeals() != stats.getCompletedDeals(DealType.SALE)) {
                        problems.add("snapshot " + i + ": the pie chart disagrees with the total");
                    }
                }
                return null;
            });

            runConcurrently(tasks);

            assertEquals(List.of(), problems);
            assertEquals(count, service.getMyStatistics().getCompletedDeals(DealType.SALE));
        }

        @Test
        @DisplayName("Statistics computed at the same time by several threads all give the same values")
        void concurrentReaders() throws Exception {
            Listing listing = liveListing("Camera", ListingType.SELL);
            view(listing, 5);
            priceOffer(listing, buyer2, 300.0);
            sell(liveListing("Sofa", ListingType.SELL), buyer1);
            SellerStatistics expected = service.getMyStatistics();
            List<String> problems = Collections.synchronizedList(new ArrayList<>());
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                tasks.add(() -> {
                    for (int i = 0; i < 20; i++) {
                        SellerStatistics stats = service.getMyStatistics();
                        if (stats.getTotalViews() != expected.getTotalViews()
                                || stats.getOffersReceived() != expected.getOffersReceived()
                                || !stats.getCompletedDealsByType().equals(expected.getCompletedDealsByType())
                                || !idsOf(stats).equals(idsOf(expected))) {
                            problems.add("different statistics: " + stats);
                        }
                    }
                    return null;
                });
            }

            runConcurrently(tasks);

            assertEquals(List.of(), problems);
        }
    }
}
