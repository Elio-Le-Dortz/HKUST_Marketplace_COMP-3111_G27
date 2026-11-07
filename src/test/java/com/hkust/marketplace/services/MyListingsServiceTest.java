package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.ActOnListingResult;
import com.hkust.marketplace.models.ActOnListingStatus;
import com.hkust.marketplace.models.AdminListingOperationResult;
import com.hkust.marketplace.models.AdminNotificationCategory;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.CreateListingRequest;
import com.hkust.marketplace.models.CreateListingResult;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.LendingDurationUnit;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingField;
import com.hkust.marketplace.models.ListingReviewResult;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.MyListingResult;
import com.hkust.marketplace.models.MyListingStatus;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationTargetType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferManagementResult;
import com.hkust.marketplace.models.OfferManagementStatus;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.Photo;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of use case 2.4 "Manage My Listings" (US-S04), grouped by the flows of its
 * specification. Acceptance criteria of the Sprint 2 backlog: editing a Live listing sends it
 * back to Pending Review, and deleting a listing warns the Seller if offers are pending.
 *
 * <p>The photos are real files written in a temporary folder. The tests also check that
 * My Listings works with the rest of the system: the admin's review and take-down (use cases
 * 3.2 and 3.7), buyer offers (use case 1.5), the Seller's offer handling (use case 2.5) and
 * the buyer's answer to a counter offer (use case 1.6).</p>
 */
@DisplayName("MyListingsService: US-S04 Manage My Listings")
class MyListingsServiceTest {

    private static final DummyDataService DATA = DummyDataService.getInstance();
    /** The tests never log in with a password, so the stored hash is never checked. */
    private static final String UNUSED_HASH = "not-checked-by-these-tests";
    private static final String ADMIN = DummyDataService.SEEDED_ADMIN_USERNAME;
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @TempDir
    Path photoDir;

    private final MyListingsService service = new MyListingsService(DATA);
    private final OfferManagementService offerService = new OfferManagementService(DATA);

    private Seller seller;
    private Seller otherSeller;
    private Buyer buyer1;
    private Buyer buyer2;
    private Path photo1;
    private Path photo2;
    private Path photo3;
    private int titleCounter;

    /**
     * Starts every test from the seeded data, with a fresh Seller logged in, another Seller,
     * two buyers and three photos on disk.
     */
    @BeforeEach
    void setUp() throws IOException {
        DATA.resetData();
        SessionManager.logout();
        seller = storeSeller("s04_seller", "Sam Seller");
        otherSeller = storeSeller("s04_other", "Olive Other");
        buyer1 = storeBuyer("s04_buyer1", "Bea Buyer");
        buyer2 = storeBuyer("s04_buyer2", "Ben Buyer");
        photo1 = writePng("lamp-front.png");
        photo2 = writePng("lamp-side.png");
        photo3 = writePng("lamp-box.png");
        SessionManager.login(seller);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private Seller storeSeller(String username, String fullName) {
        Seller s = new Seller(username, fullName, UNUSED_HASH, "Final year student");
        s.setPreferredMeetups(List.of(MeetupLocation.LIBRARY));
        assertTrue(DATA.registerUserIfAbsent(s), "Test seller could not be stored");
        return s;
    }

    private Buyer storeBuyer(String username, String fullName) {
        Buyer b = new Buyer(username, fullName, UNUSED_HASH, "Hall 1");
        assertTrue(DATA.registerUserIfAbsent(b), "Test buyer could not be stored");
        return b;
    }

    private Path writePng(String name) throws IOException {
        return Files.write(photoDir.resolve(name), PNG_HEADER).toAbsolutePath().normalize();
    }

    /** Stores a valid listing of a Seller, with one photo, directly in the data store. */
    private Listing store(Seller owner, ListingType type, ListingStatus status) {
        Listing listing = new Listing();
        listing.setType(type);
        listing.setTitle("Desk Lamp " + (++titleCounter));
        listing.setDescription("LED desk lamp with a USB port. Works perfectly.");
        listing.setCategory(Category.FURNITURE);
        listing.setCondition(Condition.LIKE_NEW);
        listing.setPrice(type == ListingType.SELL ? 120.0 : null);
        listing.setLendingDuration(type == ListingType.LEND ? Duration.ofDays(7) : null);
        listing.setMeetupLocation(MeetupLocation.LIBRARY);
        listing.setStatus(status);
        listing.setPostedDate(LocalDateTime.now().minusHours(1));
        listing.setSellerUsername(owner.getUsername());
        listing.setSellerFullName(owner.getFullName());
        DATA.addListing(listing);
        listing.setPhotos(CreateListingService.toPhotos(listing.getId(), List.of(photo1)));
        return listing;
    }

    private Listing store(ListingType type, ListingStatus status) {
        return store(seller, type, status);
    }

    private Listing liveSell() {
        return store(ListingType.SELL, ListingStatus.LIVE);
    }

    private Offer priceOffer(Listing listing, Buyer buyer, double price) {
        return SessionManager.withSession(buyer, () -> {
            ActOnListingResult result = DATA.submitPriceOffer(listing.getId(), buyer.getUsername(), price);
            assertTrue(result.isSuccess(), result::getMessage);
            return result.getOffer();
        });
    }

    private Offer borrowRequest(Listing listing, Buyer buyer) {
        return SessionManager.withSession(buyer, () -> {
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            ActOnListingResult result = DATA.submitBorrowRequest(listing.getId(), buyer.getUsername(), start, start.plusDays(2));
            assertTrue(result.isSuccess(), result::getMessage);
            return result.getOffer();
        });
    }

    private Offer claim(Listing listing, Buyer buyer) {
        return SessionManager.withSession(buyer, () -> {
            ActOnListingResult result = DATA.submitClaim(listing.getId(), buyer.getUsername());
            assertTrue(result.isSuccess(), result::getMessage);
            return result.getOffer();
        });
    }

    private Deal storeDeal(Listing listing, Buyer buyer, DealStatus status) {
        Deal deal = new Deal();
        deal.setListingId(listing.getId());
        deal.setListingTitle(listing.getTitle());
        deal.setBuyerUsername(buyer.getUsername());
        deal.setSellerUsername(listing.getSellerUsername());
        deal.setDealType(listing.getType());
        deal.setStatus(status);
        deal.setStartDate(LocalDateTime.now().minusDays(3));
        DATA.addDeal(deal);
        return deal;
    }

    private Form formOf(Listing listing) {
        return new Form(MyListingsService.toEditRequest(listing));
    }

    private MyListingResult edit(Listing listing, CreateListingRequest request) {
        return service.editListing(listing.getId(), request, null);
    }

    private MyListingResult editConfirmed(Listing listing, CreateListingRequest request) {
        MyListingResult result = service.editListing(listing.getId(), request, null);
        if (result.getStatus() == MyListingStatus.PENDING_OFFERS) {
            result = service.editListing(listing.getId(), request, result.getOfferIds());
        }
        assertTrue(result.isSuccess(), result::toString);
        return result;
    }

    private MyListingResult deleteConfirmed(Listing listing) {
        MyListingResult result = service.deleteListing(listing.getId(), null);
        if (result.getStatus() == MyListingStatus.PENDING_OFFERS) {
            result = service.deleteListing(listing.getId(), result.getOfferIds());
        }
        assertTrue(result.isSuccess(), result::toString);
        return result;
    }

    private List<Notification> notificationsSince(String username, int before) {
        List<Notification> all = DATA.getNotificationsFor(username);
        return all.subList(0, all.size() - before);
    }

    private static List<Path> photoPathsOf(Listing listing) {
        return listing.getPhotos().stream().map(photo -> Path.of(photo.getImagePath())).toList();
    }

    /** Values of the listing form, filled from a listing and changed one at a time by the tests. */
    private static final class Form {
        private ListingType type;
        private String title;
        private String description;
        private Category category;
        private Condition condition;
        private String price;
        private String lendingAmount;
        private LendingDurationUnit lendingUnit;
        private MeetupLocation meetup;
        private List<Path> photos;

        Form(CreateListingRequest request) {
            type = request.type();
            title = request.title();
            description = request.description();
            category = request.category();
            condition = request.condition();
            price = request.price();
            lendingAmount = request.lendingAmount();
            lendingUnit = request.lendingUnit();
            meetup = request.meetupLocation();
            photos = new ArrayList<>(request.photos());
        }

        Form type(ListingType value) { type = value; return this; }
        Form title(String value) { title = value; return this; }
        Form description(String value) { description = value; return this; }
        Form category(Category value) { category = value; return this; }
        Form condition(Condition value) { condition = value; return this; }
        Form price(String value) { price = value; return this; }
        Form lending(String amount, LendingDurationUnit unit) { lendingAmount = amount; lendingUnit = unit; return this; }
        Form meetup(MeetupLocation value) { meetup = value; return this; }
        Form photos(List<Path> value) { photos = value; return this; }

        CreateListingRequest build() {
            return new CreateListingRequest(type, title, description, category, condition,
                    price, lendingAmount, lendingUnit, meetup, photos);
        }
    }

    // ------------------------------------------------------------------
    // Access control
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Seller session is checked by every action (role-based access control)")
    class AccessControl {

        private Listing listing;
        private CreateListingRequest request;

        @BeforeEach
        void createListing() {
            listing = liveSell();
            request = formOf(listing).title("Changed title").build();
        }

        private void assertEveryActionRefused() {
            List<Executable> actions = List.of(
                    service::getMyListings,
                    () -> service.getOpenOffers(listing.getId()),
                    () -> service.checkCanEdit(listing.getId()),
                    () -> service.checkCanDelete(listing.getId()),
                    () -> service.editListing(listing.getId(), request, null),
                    () -> service.deleteListing(listing.getId(), null));
            for (Executable action : actions) {
                assertThrows(SecurityException.class, action);
            }
            assertEquals(ListingStatus.LIVE, listing.getStatus(), "Nothing may change");
            assertFalse(listing.isDeletedBySeller());
            assertNotEquals("Changed title", listing.getTitle());
        }

        @Test
        @DisplayName("Without any session, every action throws SecurityException")
        void noSession() {
            SessionManager.logout();
            assertEveryActionRefused();
        }

        @Test
        @DisplayName("A Buyer session cannot call the Seller actions")
        void buyerSession() {
            SessionManager.login(buyer1);
            assertEveryActionRefused();
        }

        @Test
        @DisplayName("An Admin session cannot call the Seller actions")
        void adminSession() {
            SessionManager.login(DATA.findUser(ADMIN));
            assertEveryActionRefused();
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = {"DEACTIVATED", "DELETED"})
        @DisplayName("A Seller deactivated or deleted during the session is refused")
        void inactiveSeller(AccountStatus status) {
            seller.setAccountStatus(status);
            assertEveryActionRefused();
        }

        @Test
        @DisplayName("Editing without a form is a programming error")
        void nullRequest() {
            assertThrows(NullPointerException.class, () -> service.editListing(listing.getId(), null, null));
        }

        @Test
        @DisplayName("The data store is required")
        void nullDataService() {
            assertThrows(NullPointerException.class, () -> new MyListingsService(null));
            assertNotNull(new MyListingsService());
        }
    }

    // ------------------------------------------------------------------
    // Basic flow, step 2
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Basic flow step 2: the Seller's listings")
    class MyListings {

        @Test
        @DisplayName("A1: a Seller without listings has none to show")
        void noListings() {
            assertTrue(service.getMyListings().isEmpty());
        }

        @Test
        @DisplayName("Every status is listed, newest first")
        void everyStatusNewestFirst() {
            List<Listing> expected = new ArrayList<>();
            ListingStatus[] statuses = ListingStatus.values();
            for (int i = 0; i < statuses.length; i++) {
                Listing listing = store(ListingType.SELL, statuses[i]);
                listing.setPostedDate(LocalDateTime.now().minusDays(i));
                expected.add(listing);
            }

            assertEquals(expected, service.getMyListings());
        }

        @Test
        @DisplayName("Only the logged-in Seller's listings are listed")
        void onlyOwnListings() {
            Listing mine = liveSell();
            store(otherSeller, ListingType.SELL, ListingStatus.LIVE);

            assertEquals(List.of(mine), service.getMyListings());
        }

        @Test
        @DisplayName("The listings the Seller deleted are not listed")
        void deletedListingsHidden() {
            Listing kept = liveSell();
            Listing deleted = liveSell();
            deleteConfirmed(deleted);

            assertEquals(List.of(kept), service.getMyListings());
        }

        @Test
        @DisplayName("Listings posted at the same time are ordered by identifier, those without date last")
        void sameDateAndNoDate() {
            LocalDateTime posted = LocalDateTime.now().minusDays(1);
            Listing first = liveSell();
            Listing second = liveSell();
            Listing undated = liveSell();
            first.setPostedDate(posted);
            second.setPostedDate(posted);
            undated.setPostedDate(null);

            assertEquals(List.of(first, second, undated), service.getMyListings());
        }

        @Test
        @DisplayName("The admin's reason of a rejected listing is shown (use case 3.2)")
        void rejectionReasonShown() {
            Listing listing = store(ListingType.SELL, ListingStatus.PENDING_REVIEW);
            ListingReviewResult rejected = DATA.rejectListing(listing.getId(), ADMIN, "The photos are blurry.");
            assertTrue(rejected.isSuccess(), rejected::getMessage);

            Listing shown = service.getMyListings().get(0);
            assertEquals(ListingStatus.REJECTED, shown.getStatus());
            assertEquals("The photos are blurry.", shown.getAdminReason());
        }

        @Test
        @DisplayName("A listing taken down by the admin stays listed with the reason (use case 3.7)")
        void takenDownShown() {
            Listing listing = liveSell();
            AdminListingOperationResult takenDown =
                    DATA.getAdminListingService().takeDownListing(listing.getId(), "Prohibited item.", ADMIN);
            assertTrue(takenDown.isSuccess(), takenDown::getMessage);

            Listing shown = service.getMyListings().get(0);
            assertSame(listing, shown);
            assertTrue(MyListingsService.isTakenDown(shown));
            assertEquals("Prohibited item.", shown.getAdminReason());
        }

        @Test
        @DisplayName("The list cannot be changed by the caller")
        void unmodifiable() {
            liveSell();
            List<Listing> listings = service.getMyListings();
            assertThrows(UnsupportedOperationException.class, () -> listings.add(new Listing()));
        }

        @Test
        @DisplayName("The seeded listing taken down by the admin is shown to its seller")
        void seededTakenDownListing() {
            SessionManager.login(DATA.findUser("charlie"));
            Listing keyboard = DATA.findListing("L999");

            assertTrue(service.getMyListings().contains(keyboard));
            assertTrue(MyListingsService.isTakenDown(keyboard));
        }
    }

    @Nested
    @DisplayName("Open offers of a listing")
    class OpenOffers {

        @Test
        @DisplayName("Pending and countered offers are open, oldest first; handled ones are not")
        void openOffersOldestFirst() {
            Listing listing = liveSell();
            Offer later = priceOffer(listing, buyer1, 90.0);
            Offer earlier = priceOffer(listing, buyer2, 95.0);
            Buyer buyer3 = storeBuyer("s04_buyer3", "Bob Third");
            Offer countered = priceOffer(listing, buyer3, 80.0);
            Buyer buyer4 = storeBuyer("s04_buyer4", "Bill Fourth");
            Offer withdrawn = priceOffer(listing, buyer4, 70.0);
            later.setCreatedDate(LocalDateTime.now().minusMinutes(1));
            earlier.setCreatedDate(LocalDateTime.now().minusMinutes(3));
            countered.setCreatedDate(LocalDateTime.now().minusMinutes(2));
            assertTrue(offerService.counterWithPrice(countered.getId(), 110.0).isSuccess());
            assertTrue(SessionManager.withSession(buyer4, () ->
                    DATA.withdrawOffer(withdrawn.getId(), buyer4.getUsername()).isSuccess()));

            assertEquals(List.of(earlier, countered, later), service.getOpenOffers(" " + listing.getId() + " "));
        }

        @Test
        @DisplayName("Another Seller's listing, an unknown or an empty identifier has no open offer")
        void notOwnListing() {
            Listing other = store(otherSeller, ListingType.SELL, ListingStatus.LIVE);
            priceOffer(other, buyer1, 90.0);

            assertTrue(service.getOpenOffers(other.getId()).isEmpty());
            assertTrue(service.getOpenOffers("L-unknown").isEmpty());
            assertTrue(service.getOpenOffers(null).isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // Checks before editing or deleting (alternative flow A2)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Begin Editing / Begin Deleting: can the listing be changed? (A2)")
    class Checks {

        @ParameterizedTest
        @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "LIVE", "REJECTED"})
        @DisplayName("A Pending Review, Live or Rejected listing can be edited and deleted")
        void changeable(ListingStatus status) {
            Listing listing = store(ListingType.SELL, status);

            MyListingResult edit = service.checkCanEdit(listing.getId());
            MyListingResult delete = service.checkCanDelete(listing.getId());

            assertTrue(edit.isSuccess(), edit::toString);
            assertTrue(delete.isSuccess(), delete::toString);
            assertSame(listing, edit.getListing());
            assertSame(listing, delete.getListing());
            assertTrue(delete.getMessage().contains("will no longer appear in the marketplace"));
        }

        @Test
        @DisplayName("The edit check tells what saving will do, depending on the status")
        void editOutcomeMessages() {
            assertTrue(service.checkCanEdit(liveSell().getId()).getMessage().contains("back to the admin for review"));
            assertTrue(service.checkCanEdit(store(ListingType.SELL, ListingStatus.REJECTED).getId())
                    .getMessage().contains("new review"));
            assertTrue(service.checkCanEdit(store(ListingType.SELL, ListingStatus.PENDING_REVIEW).getId())
                    .getMessage().contains("awaiting the admin's review"));
        }

        @ParameterizedTest
        @EnumSource(value = ListingStatus.class, names = {"RESERVED", "SOLD", "LENT", "GIVEN"})
        @DisplayName("A2: a Reserved, Sold, Lent or Given listing can be neither edited nor deleted")
        void dealInProgressOrCompleted(ListingStatus status) {
            Listing listing = store(ListingType.SELL, status);
            boolean completed = status == ListingStatus.SOLD || status == ListingStatus.GIVEN;

            for (MyListingResult result : List.of(service.checkCanEdit(listing.getId()),
                    service.checkCanDelete(listing.getId()))) {
                assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, result.getStatus());
                assertSame(listing, result.getListing());
                assertTrue(result.getMessage().contains(completed ? "deal is completed" : "deal on it is in progress"),
                        result.getMessage());
            }
            assertTrue(service.checkCanEdit(listing.getId()).getMessage().contains("cannot be edited"));
            assertTrue(service.checkCanDelete(listing.getId()).getMessage().contains("cannot be deleted"));
        }

        @Test
        @DisplayName("A2: a Live listing with an active deal is locked until the deal is over")
        void liveWithActiveDeal() {
            Listing listing = store(ListingType.LEND, ListingStatus.LIVE);
            Deal deal = storeDeal(listing, buyer1, DealStatus.ACTIVE);

            assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, service.checkCanEdit(listing.getId()).getStatus());
            assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, service.checkCanDelete(listing.getId()).getStatus());

            deal.setStatus(DealStatus.RETURNED);
            assertTrue(service.checkCanEdit(listing.getId()).isSuccess());
            assertTrue(service.checkCanDelete(listing.getId()).isSuccess());
        }

        @Test
        @DisplayName("The seeded Live listing with an active deal cannot be changed by its seller")
        void seededActiveDeal() {
            SessionManager.login(DATA.findUser("charlie"));
            Listing calculus = DATA.getAllListings().stream()
                    .filter(l -> l.getTitle().startsWith("Calculus Textbook")).findFirst().orElseThrow();

            assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, service.checkCanEdit(calculus.getId()).getStatus());
        }

        @Test
        @DisplayName("A listing taken down by the admin cannot be edited, but can be deleted")
        void takenDown() {
            Listing listing = store(ListingType.SELL, ListingStatus.REMOVED);
            listing.setAdminReason("Prohibited item.");

            MyListingResult edit = service.checkCanEdit(listing.getId());
            assertEquals(MyListingStatus.TAKEN_DOWN, edit.getStatus());
            assertTrue(edit.getMessage().contains("taken down by the admin"));
            assertTrue(service.checkCanDelete(listing.getId()).isSuccess());
        }

        @Test
        @DisplayName("Another Seller's listing is refused without revealing it")
        void anotherSellersListing() {
            Listing other = store(otherSeller, ListingType.SELL, ListingStatus.LIVE);

            for (MyListingResult result : List.of(service.checkCanEdit(other.getId()),
                    service.checkCanDelete(other.getId()))) {
                assertEquals(MyListingStatus.UNAUTHORIZED, result.getStatus());
                assertNull(result.getListing());
            }
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "L-unknown"})
        @DisplayName("An empty or unknown identifier is reported as not found")
        void notFound(String id) {
            assertEquals(MyListingStatus.LISTING_NOT_FOUND, service.checkCanEdit(id).getStatus());
            assertEquals(MyListingStatus.LISTING_NOT_FOUND, service.checkCanDelete(id).getStatus());
            assertNull(service.checkCanEdit(id).getListing());
        }

        @Test
        @DisplayName("The identifier is trimmed")
        void trimmedIdentifier() {
            Listing listing = liveSell();
            assertTrue(service.checkCanEdit("  " + listing.getId() + " ").isSuccess());
        }

        @Test
        @DisplayName("A listing the Seller already deleted is reported as not found")
        void alreadyDeleted() {
            Listing listing = liveSell();
            deleteConfirmed(listing);

            MyListingResult result = service.checkCanEdit(listing.getId());
            assertEquals(MyListingStatus.LISTING_NOT_FOUND, result.getStatus());
            assertTrue(result.getMessage().contains("already deleted"));
        }

        @Test
        @DisplayName("The checks return the open offers that the change would decline")
        void checksReturnOpenOffers() {
            Listing listing = liveSell();
            Offer offer = priceOffer(listing, buyer1, 100.0);

            assertEquals(List.of(offer), service.checkCanEdit(listing.getId()).getOffers());
            assertEquals(List.of(offer), service.checkCanDelete(listing.getId()).getOffers());
            assertTrue(offer.isPending(), "A check changes nothing");
        }
    }

    // ------------------------------------------------------------------
    // Subflow S1: Edit Listing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Subflow S1: Edit Listing")
    class EditListing {

        @Test
        @DisplayName("Acceptance: editing a Live listing saves it and sends it back to Pending Review")
        void liveBackToPendingReview() {
            Listing listing = liveSell();
            listing.setViewCount(42);
            LocalDateTime posted = listing.getPostedDate();
            String id = listing.getId();

            MyListingResult result = edit(listing, formOf(listing)
                    .title("  Desk Lamp with new bulb  ")
                    .description("  Now with a new LED bulb.  ")
                    .category(Category.ELECTRONICS)
                    .condition(Condition.USED)
                    .price("HK$ 1,200.50")
                    .meetup(MeetupLocation.NORTH_GATE)
                    .build());

            assertTrue(result.isSuccess(), result::toString);
            assertSame(listing, result.getListing());
            assertTrue(result.getOffers().isEmpty());
            assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
            assertEquals("Desk Lamp with new bulb", listing.getTitle());
            assertEquals("Now with a new LED bulb.", listing.getDescription());
            assertEquals(Category.ELECTRONICS, listing.getCategory());
            assertEquals(Condition.USED, listing.getCondition());
            assertEquals(1200.5, listing.getPrice());
            assertEquals(MeetupLocation.NORTH_GATE, listing.getMeetupLocation());
            assertEquals(id, listing.getId(), "The listing keeps its identifier");
            assertEquals(seller.getUsername(), listing.getSellerUsername());
            assertEquals(42, listing.getViewCount(), "The view count is kept");
            assertEquals(posted, listing.getPostedDate(), "The posted date is kept");
            assertTrue(result.getMessage().contains("sent back to the admin for review"), result.getMessage());
        }

        @Test
        @DisplayName("A Live listing that was edited leaves the marketplace until the admin approves it")
        void leavesMarketplaceUntilApproved() {
            Listing listing = liveSell();
            editConfirmed(listing, formOf(listing).title("Better title").build());

            assertFalse(DATA.getLiveListings().contains(listing));
            assertTrue(DATA.getPendingListings().contains(listing), "It is back in the admin's review queue");
            ActOnListingResult offer = SessionManager.withSession(buyer1, () ->
                    DATA.submitPriceOffer(listing.getId(), buyer1.getUsername(), 100.0));
            assertEquals(ActOnListingStatus.LISTING_NOT_LIVE, offer.getStatus());

            assertTrue(DATA.approveListing(listing.getId(), ADMIN).isSuccess());
            assertEquals(ListingStatus.LIVE, listing.getStatus());
            assertEquals("Better title", listing.getTitle());
            assertTrue(DATA.getLiveListings().contains(listing));
        }

        @Test
        @DisplayName("Editing a Rejected listing sends it to a new review and clears the old reason")
        void rejectedBackToPendingReview() {
            Listing listing = store(ListingType.SELL, ListingStatus.PENDING_REVIEW);
            assertTrue(DATA.rejectListing(listing.getId(), ADMIN, "Price too high.").isSuccess());

            MyListingResult result = edit(listing, formOf(listing).price("80").build());

            assertTrue(result.isSuccess(), result::toString);
            assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
            assertNull(listing.getAdminReason());
            assertEquals(80.0, listing.getPrice());
        }

        @Test
        @DisplayName("Editing a listing awaiting review keeps it Pending Review")
        void pendingStaysPending() {
            Listing listing = store(ListingType.SELL, ListingStatus.PENDING_REVIEW);

            MyListingResult result = edit(listing, formOf(listing).title("Fixed a typo").build());

            assertTrue(result.isSuccess());
            assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
            assertTrue(result.getMessage().contains("still awaiting the admin's review"), result.getMessage());
        }

        @Test
        @DisplayName("The admin is told that a Live or Rejected listing was resubmitted, with a link to it")
        void adminNotifiedOfResubmission() {
            Listing listing = liveSell();
            int before = DATA.getNotificationsFor(ADMIN).size();

            edit(listing, formOf(listing).title("Resubmitted lamp").build());

            List<Notification> sent = notificationsSince(ADMIN, before);
            assertEquals(1, sent.size());
            Notification notification = sent.get(0);
            assertEquals("Listing Resubmitted: Resubmitted lamp", notification.getTitle());
            assertTrue(notification.getMessage().contains("@" + seller.getUsername()));
            assertTrue(notification.getMessage().contains("back in the review queue"));
            assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS,
                    AdminNotificationCategory.fromRaw(notification.getCategory()));
            assertEquals(listing.getId(), notification.getRelatedItemId());
            assertEquals(NotificationTargetType.LISTING.name(), notification.getRelatedItemType());
        }

        @Test
        @DisplayName("The admin is told that a listing awaiting review was updated")
        void adminNotifiedOfUpdate() {
            Listing listing = store(ListingType.GIVEAWAY, ListingStatus.PENDING_REVIEW);
            int before = DATA.getNotificationsFor(ADMIN).size();

            edit(listing, formOf(listing).title("Updated giveaway").build());

            Notification notification = notificationsSince(ADMIN, before).get(0);
            assertEquals("Listing Updated: Updated giveaway", notification.getTitle());
            assertTrue(notification.getMessage().contains("while it was awaiting review"));
        }

        @Test
        @DisplayName("Changing the type keeps only the value of the new type (use case 2.3, A4)")
        void changingType() {
            Listing listing = liveSell();

            editConfirmed(listing, formOf(listing).type(ListingType.LEND).lending("2", LendingDurationUnit.WEEKS).build());
            assertEquals(ListingType.LEND, listing.getType());
            assertNull(listing.getPrice(), "The price of a Lend listing is discarded");
            assertEquals(Duration.ofDays(14), listing.getLendingDuration());

            editConfirmed(listing, formOf(listing).type(ListingType.GIVEAWAY).price("50").build());
            assertEquals(ListingType.GIVEAWAY, listing.getType());
            assertNull(listing.getPrice());
            assertNull(listing.getLendingDuration(), "The lending duration of a Give Away listing is discarded");

            editConfirmed(listing, formOf(listing).type(ListingType.SELL).price("75.5")
                    .lending("3", LendingDurationUnit.DAYS).build());
            assertEquals(ListingType.SELL, listing.getType());
            assertEquals(75.5, listing.getPrice());
            assertNull(listing.getLendingDuration());
        }

        @Test
        @DisplayName("A 10 second lending duration can be set, so a loan is quick to demonstrate")
        void demoLendingDuration() {
            Listing listing = store(ListingType.LEND, ListingStatus.LIVE);

            editConfirmed(listing, formOf(listing).lending("10", LendingDurationUnit.SECONDS).build());

            assertEquals(Duration.ofSeconds(10), listing.getLendingDuration());
        }

        @Test
        @DisplayName("The photos are replaced, renumbered in display order")
        void photosReplaced() {
            Listing listing = liveSell();

            editConfirmed(listing, formOf(listing).photos(List.of(photo3, photo2)).build());

            List<Photo> photos = listing.getPhotos();
            assertEquals(2, photos.size());
            assertEquals(listing.getId() + CreateListingService.PHOTO_ID_SEPARATOR + "1", photos.get(0).getId());
            assertEquals(listing.getId() + CreateListingService.PHOTO_ID_SEPARATOR + "2", photos.get(1).getId());
            assertEquals(List.of(photo3, photo2), photoPathsOf(listing));
            assertEquals(photo3.toString(), listing.getThumbnailPath(), "The first photo is the new cover");
        }

        @Test
        @DisplayName("Six photos are accepted")
        void sixPhotos() throws IOException {
            Listing listing = liveSell();
            List<Path> six = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                six.add(writePng("six-" + i + ".png"));
            }

            editConfirmed(listing, formOf(listing).photos(six).build());

            assertEquals(6, listing.getPhotos().size());
        }
    }

    @Nested
    @DisplayName("Edit alternative flows (A2, A3, A4) and unchanged forms")
    class EditAlternativeFlows {

        @Test
        @DisplayName("A4: invalid fields are all reported, and nothing changes")
        void invalidFields() {
            Listing listing = liveSell();
            String title = listing.getTitle();
            int adminBefore = DATA.getNotificationsFor(ADMIN).size();

            MyListingResult result = edit(listing, formOf(listing).title("  ").price("-5").photos(List.of()).build());

            assertEquals(MyListingStatus.INVALID_FIELDS, result.getStatus());
            assertSame(listing, result.getListing());
            assertEquals(List.of(CreateListingService.TITLE_REQUIRED), result.getErrors(ListingField.TITLE));
            assertEquals(List.of(CreateListingService.PRICE_NOT_POSITIVE), result.getErrors(ListingField.PRICE));
            assertEquals(List.of(CreateListingService.PHOTOS_REQUIRED), result.getErrors(ListingField.PHOTOS));
            assertEquals(ListingField.TITLE, result.getFirstInvalidField());
            assertEquals("Please correct the 3 highlighted fields.", result.getMessage());
            assertEquals(title, listing.getTitle());
            assertEquals(ListingStatus.LIVE, listing.getStatus());
            assertEquals(List.of(photo1), photoPathsOf(listing));
            assertEquals(adminBefore, DATA.getNotificationsFor(ADMIN).size(), "The admin is not notified");
        }

        @Test
        @DisplayName("A4: a lending duration under 5 seconds is refused")
        void lendingTooShort() {
            Listing listing = store(ListingType.LEND, ListingStatus.LIVE);

            MyListingResult result = edit(listing, formOf(listing).lending("3", LendingDurationUnit.SECONDS).build());

            assertEquals(List.of(CreateListingService.LENDING_TOO_SHORT), result.getErrors(ListingField.LENDING_DURATION));
            assertEquals(Duration.ofDays(7), listing.getLendingDuration());
        }

        @Test
        @DisplayName("A4: more than 6 photos are refused")
        void tooManyPhotos() throws IOException {
            Listing listing = liveSell();
            List<Path> seven = new ArrayList<>();
            for (int i = 1; i <= 7; i++) {
                seven.add(writePng("seven-" + i + ".png"));
            }

            MyListingResult result = edit(listing, formOf(listing).photos(seven).build());

            assertTrue(result.getErrors(ListingField.PHOTOS).contains(CreateListingService.PHOTOS_TOO_MANY));
        }

        @Test
        @DisplayName("A4: a photo whose file was deleted since the listing was posted must be replaced")
        void photoFileDeleted() throws IOException {
            Listing listing = liveSell();
            Files.delete(photo1);

            MyListingResult result = edit(listing, formOf(listing).title("New title").build());

            assertEquals(MyListingStatus.INVALID_FIELDS, result.getStatus());
            assertEquals(List.of("lamp-front.png " + CreateListingService.PHOTO_NOT_FOUND),
                    result.getErrors(ListingField.PHOTOS));

            MyListingResult replaced = edit(listing, formOf(listing).title("New title").photos(List.of(photo2)).build());
            assertTrue(replaced.isSuccess(), replaced::toString);
        }

        @Test
        @DisplayName("A2 is reported before A4: a locked listing with an invalid form is reported as locked")
        void lockedBeforeInvalid() {
            Listing listing = store(ListingType.SELL, ListingStatus.RESERVED);

            MyListingResult result = edit(listing, formOf(listing).title("").build());

            assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, result.getStatus());
            assertTrue(result.getErrors().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(value = ListingStatus.class, names = {"RESERVED", "SOLD", "LENT", "GIVEN"})
        @DisplayName("A2: a listing whose deal is in progress or completed cannot be edited")
        void lockedListing(ListingStatus status) {
            Listing listing = store(ListingType.SELL, status);

            MyListingResult result = edit(listing, formOf(listing).title("Changed").build());

            assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, result.getStatus());
            assertNotEquals("Changed", listing.getTitle());
            assertEquals(status, listing.getStatus());
        }

        @Test
        @DisplayName("A listing taken down by the admin cannot be edited")
        void takenDownCannotBeEdited() {
            Listing listing = store(ListingType.SELL, ListingStatus.REMOVED);

            assertEquals(MyListingStatus.TAKEN_DOWN, edit(listing, formOf(listing).title("Changed").build()).getStatus());
            assertEquals(ListingStatus.REMOVED, listing.getStatus());
        }

        @Test
        @DisplayName("Another Seller's listing cannot be edited")
        void anotherSellersListing() {
            Listing other = store(otherSeller, ListingType.SELL, ListingStatus.LIVE);

            MyListingResult result = edit(other, formOf(other).title("Hijacked").build());

            assertEquals(MyListingStatus.UNAUTHORIZED, result.getStatus());
            assertNotEquals("Hijacked", other.getTitle());
        }

        @Test
        @DisplayName("Saving without changing anything changes nothing")
        void noChanges() {
            Listing listing = liveSell();
            Offer offer = priceOffer(listing, buyer1, 100.0);
            int adminBefore = DATA.getNotificationsFor(ADMIN).size();

            MyListingResult result = edit(listing, formOf(listing).build());

            assertEquals(MyListingStatus.NO_CHANGES, result.getStatus());
            assertEquals(ListingStatus.LIVE, listing.getStatus(), "The listing is not sent back to review");
            assertTrue(offer.isPending(), "The offers are kept");
            assertEquals(adminBefore, DATA.getNotificationsFor(ADMIN).size());
        }

        @Test
        @DisplayName("Spaces around the texts and an equal price written differently are not changes")
        void equivalentValuesAreNotChanges() {
            Listing listing = liveSell();

            MyListingResult result = edit(listing, formOf(listing)
                    .title("  " + listing.getTitle() + " ").price("HK$ 120.00").build());

            assertEquals(MyListingStatus.NO_CHANGES, result.getStatus());
        }

        @Test
        @DisplayName("A3: with open offers, the edit stops and lists them, oldest first, changing nothing")
        void pendingOffersWarning() {
            Listing listing = liveSell();
            Offer first = priceOffer(listing, buyer1, 100.0);
            Offer second = priceOffer(listing, buyer2, 110.0);
            first.setCreatedDate(LocalDateTime.now().minusMinutes(2));
            second.setCreatedDate(LocalDateTime.now().minusMinutes(1));
            String title = listing.getTitle();

            MyListingResult result = edit(listing, formOf(listing).title("Changed").build());

            assertEquals(MyListingStatus.PENDING_OFFERS, result.getStatus());
            assertEquals(List.of(first, second), result.getOffers());
            assertEquals(List.of(first.getId(), second.getId()), new ArrayList<>(result.getOfferIds()));
            assertTrue(result.getMessage().contains("2 open offers, borrow requests or claims"), result.getMessage());
            assertTrue(result.getMessage().contains("Saving your changes will decline them and notify the buyers"));
            assertEquals(title, listing.getTitle());
            assertEquals(ListingStatus.LIVE, listing.getStatus());
            assertTrue(first.isPending() && second.isPending());
        }

        @Test
        @DisplayName("A3: once confirmed, the open offers are declined and each buyer is notified")
        void pendingOffersConfirmed() {
            Listing listing = liveSell();
            String oldTitle = listing.getTitle();
            Offer first = priceOffer(listing, buyer1, 100.0);
            Offer second = priceOffer(listing, buyer2, 110.0);
            int buyer1Before = DATA.getNotificationsFor(buyer1.getUsername()).size();

            MyListingResult warning = edit(listing, formOf(listing).title("Changed").build());
            MyListingResult result = service.editListing(listing.getId(), formOf(listing).title("Changed").build(),
                    warning.getOfferIds());

            assertTrue(result.isSuccess(), result::toString);
            assertEquals(List.of(first, second), result.getOffers());
            assertTrue(result.getMessage().contains("2 open offers were declined and their buyers notified"));
            for (Offer offer : List.of(first, second)) {
                assertEquals(OfferStatus.DECLINED, offer.getStatus());
                assertEquals(MyListingsService.REASON_LISTING_EDITED, offer.getDeclineReason());
            }
            Notification notification = notificationsSince(buyer1.getUsername(), buyer1Before).get(0);
            assertEquals("Offer Declined", notification.getTitle());
            assertTrue(notification.getMessage().contains("'" + oldTitle + "'"), "The buyer knows the old title");
            assertTrue(notification.getMessage().contains("changed the listing"));
            assertEquals(NotificationCategory.OFFERS, NotificationCategory.fromRaw(notification.getCategory()));
            assertEquals(first.getId(), notification.getRelatedItemId());
            assertEquals(NotificationTargetType.OFFER.name(), notification.getRelatedItemType());
        }

        @Test
        @DisplayName("A3: a single open offer is mentioned in the singular")
        void singleOpenOffer() {
            Listing listing = liveSell();
            priceOffer(listing, buyer1, 100.0);

            MyListingResult warning = edit(listing, formOf(listing).title("Changed").build());
            assertTrue(warning.getMessage().contains("1 open offer, borrow request or claim"));
            assertTrue(warning.getMessage().contains("decline it and notify the buyer"));

            MyListingResult result = service.editListing(listing.getId(), formOf(listing).title("Changed").build(),
                    warning.getOfferIds());
            assertTrue(result.getMessage().contains("1 open offer was declined and its buyer notified"));
        }

        @Test
        @DisplayName("A3: a countered offer is declined too, so the buyer can no longer accept the counter")
        void counteredOfferDeclined() {
            Listing listing = liveSell();
            Offer offer = priceOffer(listing, buyer1, 90.0);
            assertTrue(offerService.counterWithPrice(offer.getId(), 110.0).isSuccess());

            editConfirmed(listing, formOf(listing).title("Changed").build());

            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertEquals(OfferStatus.DECLINED, offer.getCounterOffer().getStatus());
            assertFalse(SessionManager.withSession(buyer1, () ->
                    DATA.acceptCounterOffer(offer.getId(), buyer1.getUsername()).isSuccess()));
        }

        @Test
        @DisplayName("A3: an offer received after the warning is not declined without a new warning")
        void offerAfterWarning() {
            Listing listing = liveSell();
            Offer first = priceOffer(listing, buyer1, 100.0);
            CreateListingRequest request = formOf(listing).title("Changed").build();
            MyListingResult warning = service.editListing(listing.getId(), request, null);

            Offer late = priceOffer(listing, buyer2, 115.0);
            MyListingResult again = service.editListing(listing.getId(), request, warning.getOfferIds());

            assertEquals(MyListingStatus.PENDING_OFFERS, again.getStatus());
            assertEquals(List.of(first, late), again.getOffers());
            assertTrue(first.isPending() && late.isPending(), "Nothing is declined");
            assertEquals(ListingStatus.LIVE, listing.getStatus());

            MyListingResult result = service.editListing(listing.getId(), request, again.getOfferIds());
            assertTrue(result.isSuccess());
            assertTrue(first.isDeclined() && late.isDeclined());
        }

        @Test
        @DisplayName("A3: an offer withdrawn after the warning is left as withdrawn")
        void offerWithdrawnAfterWarning() {
            Listing listing = liveSell();
            Offer kept = priceOffer(listing, buyer1, 100.0);
            Offer withdrawn = priceOffer(listing, buyer2, 105.0);
            CreateListingRequest request = formOf(listing).title("Changed").build();
            MyListingResult warning = service.editListing(listing.getId(), request, null);
            assertTrue(SessionManager.withSession(buyer2, () ->
                    DATA.withdrawOffer(withdrawn.getId(), buyer2.getUsername()).isSuccess()));

            MyListingResult result = service.editListing(listing.getId(), request, warning.getOfferIds());

            assertTrue(result.isSuccess());
            assertEquals(List.of(kept), result.getOffers());
            assertEquals(OfferStatus.WITHDRAWN, withdrawn.getStatus());
        }

        @Test
        @DisplayName("A3: the confirmed identifiers are trimmed, and unknown or null ones are ignored")
        void confirmedIdentifiersCleaned() {
            Listing listing = liveSell();
            Offer offer = priceOffer(listing, buyer1, 100.0);

            MyListingResult result = service.editListing(listing.getId(), formOf(listing).title("Changed").build(),
                    Arrays.asList(null, "  " + offer.getId() + " ", "O-unknown"));

            assertTrue(result.isSuccess(), result::toString);
            assertTrue(offer.isDeclined());
        }

        @Test
        @DisplayName("A3: an empty confirmation is not a confirmation")
        void emptyConfirmation() {
            Listing listing = liveSell();
            priceOffer(listing, buyer1, 100.0);

            assertEquals(MyListingStatus.PENDING_OFFERS, service.editListing(listing.getId(),
                    formOf(listing).title("Changed").build(), Set.of()).getStatus());
        }

        @Test
        @DisplayName("A3: borrow requests on a Lend listing and claims on a Give Away listing are declined too")
        void borrowRequestsAndClaims() {
            Listing lend = store(ListingType.LEND, ListingStatus.LIVE);
            Offer request = borrowRequest(lend, buyer1);
            Listing giveaway = store(ListingType.GIVEAWAY, ListingStatus.LIVE);
            Offer claimed = claim(giveaway, buyer2);

            editConfirmed(lend, formOf(lend).lending("3", LendingDurationUnit.DAYS).build());
            editConfirmed(giveaway, formOf(giveaway).title("Free lamp").build());

            assertTrue(request.isDeclined());
            assertTrue(claimed.isDeclined());
        }
    }

    // ------------------------------------------------------------------
    // Subflow S2: Delete Listing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Subflow S2: Delete Listing (soft delete)")
    class DeleteListing {

        @Test
        @DisplayName("Deleting a Live listing soft-deletes it: Removed, hidden, but kept in the store")
        void softDelete() {
            Listing listing = liveSell();

            MyListingResult result = service.deleteListing(listing.getId(), null);

            assertTrue(result.isSuccess(), result::toString);
            assertSame(listing, result.getListing());
            assertEquals(ListingStatus.REMOVED, listing.getStatus());
            assertTrue(listing.isDeletedBySeller());
            assertFalse(MyListingsService.isTakenDown(listing));
            assertSame(listing, DATA.findListing(listing.getId()), "The listing is kept for the history");
            assertFalse(DATA.getLiveListings().contains(listing), "It no longer appears in the marketplace");
            assertFalse(service.getMyListings().contains(listing));
            assertTrue(result.getMessage().contains("has been deleted"), result.getMessage());
        }

        @ParameterizedTest
        @EnumSource(value = ListingStatus.class, names = {"PENDING_REVIEW", "REJECTED"})
        @DisplayName("A listing awaiting review or rejected can be deleted, and leaves the review queue")
        void deletePendingOrRejected(ListingStatus status) {
            Listing listing = store(ListingType.SELL, status);

            deleteConfirmed(listing);

            assertEquals(ListingStatus.REMOVED, listing.getStatus());
            assertFalse(DATA.getPendingListings().contains(listing));
        }

        @Test
        @DisplayName("The deals and chats about a deleted listing keep their history")
        void historyKept() {
            Listing listing = store(ListingType.LEND, ListingStatus.LIVE);
            Deal pastLoan = storeDeal(listing, buyer1, DealStatus.RETURNED);
            pastLoan.setType(DealType.LOAN);
            ChatThread thread = DATA.getOrCreateChatThread(listing.getId(), buyer1.getUsername(), seller.getUsername());
            thread.addMessage(new Message("M-s04", buyer1.getUsername(), "Thanks for the loan!"));

            deleteConfirmed(listing);

            assertSame(pastLoan, DATA.findDeal(pastLoan.getId()));
            assertEquals(listing.getId(), pastLoan.getListingId());
            assertTrue(DATA.getChatThreadsForUser(buyer1.getUsername()).contains(thread));
            assertEquals(1, thread.getMessages().size());
        }

        @Test
        @DisplayName("Acceptance: deleting a listing with pending offers warns the Seller first")
        void pendingOffersWarning() {
            Listing listing = liveSell();
            Offer offer = priceOffer(listing, buyer1, 100.0);

            MyListingResult result = service.deleteListing(listing.getId(), null);

            assertEquals(MyListingStatus.PENDING_OFFERS, result.getStatus());
            assertEquals(List.of(offer), result.getOffers());
            assertTrue(result.getMessage().contains("Deleting the listing will decline it"), result.getMessage());
            assertEquals(ListingStatus.LIVE, listing.getStatus(), "Nothing is deleted before the confirmation");
            assertFalse(listing.isDeletedBySeller());
            assertTrue(offer.isPending());
        }

        @Test
        @DisplayName("A3: once confirmed, the open offers are declined and each buyer is notified")
        void pendingOffersConfirmed() {
            Listing listing = liveSell();
            Offer offer = priceOffer(listing, buyer1, 100.0);
            int before = DATA.getNotificationsFor(buyer1.getUsername()).size();

            MyListingResult result = deleteConfirmed(listing);

            assertEquals(List.of(offer), result.getOffers());
            assertEquals(OfferStatus.DECLINED, offer.getStatus());
            assertEquals(MyListingsService.REASON_LISTING_DELETED, offer.getDeclineReason());
            Notification notification = notificationsSince(buyer1.getUsername(), before).get(0);
            assertEquals("Offer Declined", notification.getTitle());
            assertTrue(notification.getMessage().contains("deleted the listing"));
            assertTrue(offerService.getPendingOffers().isEmpty(), "The offer is no longer to be handled (use case 2.5)");
            assertEquals(OfferManagementStatus.OFFER_NOT_PENDING, offerService.acceptOffer(offer.getId()).getStatus());
        }

        @Test
        @DisplayName("A buyer can no longer make an offer on a deleted listing (use case 1.5)")
        void noOfferAfterDelete() {
            Listing listing = liveSell();
            deleteConfirmed(listing);

            assertEquals(ActOnListingStatus.LISTING_NOT_LIVE,
                    SessionManager.withSession(buyer1, () ->
                            DATA.submitPriceOffer(listing.getId(), buyer1.getUsername(), 100.0).getStatus()));
        }

        @Test
        @DisplayName("A listing cannot be deleted twice")
        void deleteTwice() {
            Listing listing = liveSell();
            deleteConfirmed(listing);

            MyListingResult again = service.deleteListing(listing.getId(), null);

            assertEquals(MyListingStatus.LISTING_NOT_FOUND, again.getStatus());
        }

        @Test
        @DisplayName("A listing taken down by the admin can be deleted from the Seller's list, keeping the reason")
        void deleteTakenDown() {
            Listing listing = liveSell();
            assertTrue(DATA.getAdminListingService().takeDownListing(listing.getId(), "Prohibited item.", ADMIN).isSuccess());

            deleteConfirmed(listing);

            assertEquals(ListingStatus.REMOVED, listing.getStatus());
            assertTrue(listing.isDeletedBySeller());
            assertEquals("Prohibited item.", listing.getAdminReason());
            assertFalse(service.getMyListings().contains(listing));
        }

        @ParameterizedTest
        @EnumSource(value = ListingStatus.class, names = {"RESERVED", "SOLD", "LENT", "GIVEN"})
        @DisplayName("A2: a listing whose deal is in progress or completed cannot be deleted")
        void lockedListing(ListingStatus status) {
            Listing listing = store(ListingType.SELL, status);

            MyListingResult result = service.deleteListing(listing.getId(), null);

            assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, result.getStatus());
            assertEquals(status, listing.getStatus());
            assertFalse(listing.isDeletedBySeller());
        }

        @Test
        @DisplayName("Another Seller's listing cannot be deleted")
        void anotherSellersListing() {
            Listing other = store(otherSeller, ListingType.SELL, ListingStatus.LIVE);

            assertEquals(MyListingStatus.UNAUTHORIZED, service.deleteListing(other.getId(), null).getStatus());
            assertEquals(ListingStatus.LIVE, other.getStatus());
        }

        @Test
        @DisplayName("An accepted offer locks the listing: it can no longer be deleted (use case 2.5)")
        void acceptedOfferLocksListing() {
            Listing listing = liveSell();
            Offer offer = priceOffer(listing, buyer1, 100.0);
            assertTrue(offerService.acceptOffer(offer.getId()).isSuccess());

            assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED,
                    service.deleteListing(listing.getId(), Set.of(offer.getId())).getStatus());
            assertEquals(ListingStatus.RESERVED, listing.getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Edit form helpers
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Edit form helpers: toEditRequest, isUnchanged, isDealStatus, isTakenDown")
    class FormHelpers {

        @Test
        @DisplayName("Subflow S1 step 1: the form is filled with the listing's details")
        void sellListingForm() {
            Listing listing = liveSell();
            listing.setPhotos(CreateListingService.toPhotos(listing.getId(), List.of(photo1, photo2)));

            CreateListingRequest form = MyListingsService.toEditRequest(listing);

            assertEquals(ListingType.SELL, form.type());
            assertEquals(listing.getTitle(), form.title());
            assertEquals(listing.getDescription(), form.description());
            assertEquals(Category.FURNITURE, form.category());
            assertEquals(Condition.LIKE_NEW, form.condition());
            assertEquals("120", form.price());
            assertEquals("", form.lendingAmount());
            assertEquals(LendingDurationUnit.DAYS, form.lendingUnit());
            assertEquals(MeetupLocation.LIBRARY, form.meetupLocation());
            assertEquals(List.of(photo1, photo2), form.photos());
        }

        static Stream<Arguments> prices() {
            return Stream.of(
                    Arguments.of(180.0, "180"),
                    Arguments.of(1200.5, "1200.5"),
                    Arguments.of(99.99, "99.99"),
                    Arguments.of(0.1, "0.1"),
                    Arguments.of(1_000_000.0, "1000000"));
        }

        @ParameterizedTest(name = "{0} is written {1}")
        @MethodSource("prices")
        @DisplayName("The price is written without trailing zeros")
        void priceText(double price, String expected) {
            Listing listing = liveSell();
            listing.setPrice(price);

            assertEquals(expected, MyListingsService.toEditRequest(listing).price());
            assertNull(CreateListingService.checkPrice(expected), "The written price is valid");
        }

        static Stream<Arguments> durations() {
            return Stream.of(
                    Arguments.of(Duration.ofSeconds(10), "10", LendingDurationUnit.SECONDS),
                    Arguments.of(Duration.ofSeconds(90), "90", LendingDurationUnit.SECONDS),
                    Arguments.of(Duration.ofMinutes(2), "2", LendingDurationUnit.MINUTES),
                    Arguments.of(Duration.ofMinutes(90), "90", LendingDurationUnit.MINUTES),
                    Arguments.of(Duration.ofHours(1), "1", LendingDurationUnit.HOURS),
                    Arguments.of(Duration.ofHours(25), "25", LendingDurationUnit.HOURS),
                    Arguments.of(Duration.ofDays(90), "90", LendingDurationUnit.DAYS),
                    Arguments.of(Duration.ofDays(14), "2", LendingDurationUnit.WEEKS));
        }

        @ParameterizedTest(name = "{0} is written {1} {2}")
        @MethodSource("durations")
        @DisplayName("The lending duration is written in its largest whole unit")
        void lendingText(Duration duration, String amount, LendingDurationUnit unit) {
            Listing listing = store(ListingType.LEND, ListingStatus.LIVE);
            listing.setLendingDuration(duration);

            CreateListingRequest form = MyListingsService.toEditRequest(listing);

            assertEquals(amount, form.lendingAmount());
            assertEquals(unit, form.lendingUnit());
            assertEquals("", form.price());
            assertEquals(duration, CreateListingService.toLendingDuration(amount, unit));
        }

        @Test
        @DisplayName("A Lend listing without duration and a Give Away listing have empty price and duration")
        void emptyValues() {
            Listing lend = store(ListingType.LEND, ListingStatus.LIVE);
            lend.setLendingDuration(null);
            Listing zero = store(ListingType.LEND, ListingStatus.LIVE);
            zero.setLendingDuration(Duration.ZERO);
            Listing giveaway = store(ListingType.GIVEAWAY, ListingStatus.LIVE);
            Listing sellWithoutPrice = liveSell();
            sellWithoutPrice.setPrice(null);

            for (Listing listing : List.of(lend, zero, giveaway, sellWithoutPrice)) {
                CreateListingRequest form = MyListingsService.toEditRequest(listing);
                assertEquals("", form.price());
                assertEquals("", form.lendingAmount());
                assertEquals(LendingDurationUnit.DAYS, form.lendingUnit());
            }
        }

        @Test
        @DisplayName("Photos without a usable path are left out, and missing texts become empty")
        void unusablePhotosAndMissingTexts() {
            Listing listing = liveSell();
            listing.setPhotos(Arrays.asList(new Photo("P1", photo1.toString()), null, new Photo("P2", null),
                    new Photo("P3", "  "), new Photo("P4", "bad\0path")));
            listing.setTitle(null);
            listing.setDescription(null);

            CreateListingRequest form = MyListingsService.toEditRequest(listing);

            assertEquals(List.of(photo1), form.photos());
            assertEquals("", form.title());
            assertEquals("", form.description());
        }

        @ParameterizedTest
        @EnumSource(ListingType.class)
        @DisplayName("An unchanged form is recognised for every type")
        void unchangedRoundTrip(ListingType type) {
            Listing listing = store(type, ListingStatus.LIVE);

            assertTrue(MyListingsService.isUnchanged(listing, MyListingsService.toEditRequest(listing)));
        }

        @Test
        @DisplayName("Every field change is recognised")
        void everyChangeRecognised() {
            Listing sell = liveSell();
            Listing lend = store(ListingType.LEND, ListingStatus.LIVE);
            List<CreateListingRequest> changed = List.of(
                    formOf(sell).type(ListingType.GIVEAWAY).build(),
                    formOf(sell).title("Other").build(),
                    formOf(sell).description("Other description").build(),
                    formOf(sell).category(Category.BOOKS).build(),
                    formOf(sell).condition(Condition.NEW).build(),
                    formOf(sell).meetup(MeetupLocation.ATRIUM).build(),
                    formOf(sell).price("121").build(),
                    formOf(sell).price("not a price").build(),
                    formOf(sell).photos(List.of(photo1, photo2)).build(),
                    formOf(sell).photos(List.of()).build(),
                    formOf(sell).photos(List.of(photo2)).build());
            for (CreateListingRequest request : changed) {
                assertFalse(MyListingsService.isUnchanged(sell, request), request::toString);
            }
            assertFalse(MyListingsService.isUnchanged(lend, formOf(lend).lending("8", LendingDurationUnit.DAYS).build()));
            assertTrue(MyListingsService.isUnchanged(lend, formOf(lend).lending("1", LendingDurationUnit.WEEKS).build()),
                    "1 week is the stored 7 days: not a change");
            assertFalse(MyListingsService.isUnchanged(lend, formOf(lend).lending("", LendingDurationUnit.DAYS).build()));
        }

        @Test
        @DisplayName("Photos are compared by file and in order")
        void photoOrderMatters() {
            Listing listing = liveSell();
            listing.setPhotos(CreateListingService.toPhotos(listing.getId(), List.of(photo1, photo2)));

            assertFalse(MyListingsService.isUnchanged(listing, formOf(listing).photos(List.of(photo2, photo1)).build()));
            Path sameFile = photoDir.resolve("sub").resolve("..").resolve("lamp-front.png");
            assertTrue(MyListingsService.isUnchanged(listing, formOf(listing).photos(List.of(sameFile, photo2)).build()));
        }

        @Test
        @DisplayName("For a Give Away listing, the price and lending texts of the form are ignored")
        void giveawayIgnoresPriceAndLending() {
            Listing giveaway = store(ListingType.GIVEAWAY, ListingStatus.LIVE);

            assertTrue(MyListingsService.isUnchanged(giveaway, formOf(giveaway).price("50")
                    .lending("3", LendingDurationUnit.DAYS).build()));
        }

        @Test
        @DisplayName("A Sell listing stored without price, or a Lend listing without duration, is never unchanged")
        void storedWithoutPriceOrDuration() {
            Listing sell = liveSell();
            sell.setPrice(null);
            Listing lend = store(ListingType.LEND, ListingStatus.LIVE);
            lend.setLendingDuration(null);

            assertFalse(MyListingsService.isUnchanged(sell, formOf(sell).price("120").build()));
            assertFalse(MyListingsService.isUnchanged(lend, formOf(lend).lending("7", LendingDurationUnit.DAYS).build()));
        }

        @Test
        @DisplayName("The helpers need their arguments")
        void nullArguments() {
            Listing listing = liveSell();
            CreateListingRequest request = MyListingsService.toEditRequest(listing);
            assertThrows(NullPointerException.class, () -> MyListingsService.toEditRequest(null));
            assertThrows(NullPointerException.class, () -> MyListingsService.isUnchanged(null, request));
            assertThrows(NullPointerException.class, () -> MyListingsService.isUnchanged(listing, null));
            assertThrows(NullPointerException.class, () -> MyListingsService.isTakenDown(null));
        }

        @ParameterizedTest
        @EnumSource(ListingStatus.class)
        @DisplayName("Only Reserved, Sold, Lent and Given are deal statuses")
        void dealStatuses(ListingStatus status) {
            boolean expected = status == ListingStatus.RESERVED || status == ListingStatus.SOLD
                    || status == ListingStatus.LENT || status == ListingStatus.GIVEN;
            assertEquals(expected, MyListingsService.isDealStatus(status));
            assertFalse(MyListingsService.isDealStatus(null));
        }

        @Test
        @DisplayName("Only a Removed listing that the Seller did not delete was taken down")
        void takenDown() {
            Listing listing = liveSell();
            assertFalse(MyListingsService.isTakenDown(listing));
            listing.setStatus(ListingStatus.REMOVED);
            assertTrue(MyListingsService.isTakenDown(listing));
            listing.setDeletedBySeller(true);
            assertFalse(MyListingsService.isTakenDown(listing));
        }
    }

    // ------------------------------------------------------------------
    // Integration with the other use cases
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Integration: create, review, edit, offer and delete")
    class Integration {

        @Test
        @DisplayName("A rejected listing is corrected, approved, receives an offer, then is deleted")
        void fullLifeCycle() {
            CreateListingResult created = new CreateListingService(DATA).createListing(new CreateListingRequest(
                    ListingType.SELL, "Rice cooker", "Small rice cooker, 3 cups.", Category.OTHER, Condition.USED,
                    "500", null, null, MeetupLocation.STUDENT_HALLS, List.of(photo1)));
            assertTrue(created.isSuccess(), created::getMessage);
            Listing listing = created.getListing();
            assertTrue(DATA.rejectListing(listing.getId(), ADMIN, "Price too high for a used cooker.").isSuccess());
            assertEquals("Price too high for a used cooker.", service.getMyListings().get(0).getAdminReason());

            editConfirmed(listing, formOf(listing).price("150").build());
            assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
            assertNull(listing.getAdminReason());

            assertTrue(DATA.approveListing(listing.getId(), ADMIN).isSuccess());
            Offer offer = priceOffer(listing, buyer1, 140.0);
            assertEquals(List.of(offer), service.getOpenOffers(listing.getId()));

            MyListingResult deleted = deleteConfirmed(listing);
            assertEquals(List.of(offer), deleted.getOffers());
            assertTrue(offer.isDeclined());
            assertTrue(service.getMyListings().isEmpty());
        }

        @Test
        @DisplayName("After an edit, the buyer's declined offer shows on My Deals (use case 1.6)")
        void buyerSeesDeclinedOffer() {
            Listing listing = liveSell();
            Offer offer = priceOffer(listing, buyer1, 100.0);

            editConfirmed(listing, formOf(listing).title("Changed").build());

            assertTrue(SessionManager.withSession(buyer1, () -> DATA.getMyDealsForBuyer(buyer1.getUsername())).stream()
                    .anyMatch(item -> offer.getId().equals(item.getId()) && "Declined".equals(item.getStatusDisplay())),
                    "The buyer sees the offer as declined");
        }

        @Test
        @DisplayName("A Lent listing returned by the buyer can be edited again (use case 1.6)")
        void editableAfterReturn() {
            Listing listing = store(ListingType.LEND, ListingStatus.LIVE);
            Offer request = borrowRequest(listing, buyer1);
            OfferManagementResult accepted = offerService.acceptOffer(request.getId());
            assertTrue(accepted.isSuccess(), accepted::getMessage);
            assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, service.checkCanEdit(listing.getId()).getStatus());

            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.selfReturnItem(accepted.getDeal().getId(), buyer1.getUsername()).isSuccess()));

            assertEquals(ListingStatus.LIVE, listing.getStatus());
            assertTrue(service.checkCanEdit(listing.getId()).isSuccess());
        }

        @Test
        @DisplayName("Another Seller's session sees none of the Seller's listings")
        void otherSellerSession() {
            liveSell();
            SessionManager.login(otherSeller);
            assertTrue(service.getMyListings().isEmpty());
            User current = SessionManager.getCurrentUser();
            assertEquals(otherSeller.getUsername(), current.getUsername());
        }
    }

    // ------------------------------------------------------------------
    // Concurrency
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrency: edits and deletes are atomic")
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
        @DisplayName("The same listing deleted 10 times at once is deleted once")
        void concurrentDeletes() throws Exception {
            Listing listing = liveSell();
            List<Callable<MyListingResult>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                tasks.add(() -> service.deleteListing(listing.getId(), null));
            }

            List<MyListingResult> results = runTogether(tasks);

            assertEquals(1, results.stream().filter(MyListingResult::isSuccess).count());
            results.stream().filter(r -> !r.isSuccess())
                    .forEach(r -> assertEquals(MyListingStatus.LISTING_NOT_FOUND, r.getStatus(), r::toString));
            assertEquals(ListingStatus.REMOVED, listing.getStatus());
        }

        @Test
        @Timeout(30)
        @DisplayName("Accepting an offer and deleting its listing at once: exactly one of them wins")
        void acceptVersusDelete() throws Exception {
            for (int round = 0; round < 25; round++) {
                Listing listing = liveSell();
                Offer offer = priceOffer(listing, buyer1, 100.0);
                List<Callable<Object>> tasks = List.of(
                        () -> offerService.acceptOffer(offer.getId()),
                        () -> service.deleteListing(listing.getId(), Set.of(offer.getId())));

                List<Object> results = runTogether(tasks);
                OfferManagementResult accepted = (OfferManagementResult) results.get(0);
                MyListingResult deleted = (MyListingResult) results.get(1);

                assertNotEquals(accepted.isSuccess(), deleted.isSuccess(), "Exactly one action succeeds");
                long deals = DATA.getAllDeals().stream().filter(d -> listing.getId().equals(d.getListingId())).count();
                if (accepted.isSuccess()) {
                    assertEquals(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, deleted.getStatus());
                    assertEquals(ListingStatus.RESERVED, listing.getStatus());
                    assertFalse(listing.isDeletedBySeller());
                    assertEquals(1, deals);
                } else {
                    assertEquals(OfferManagementStatus.OFFER_NOT_PENDING, accepted.getStatus());
                    assertEquals(ListingStatus.REMOVED, listing.getStatus());
                    assertTrue(offer.isDeclined());
                    assertEquals(0, deals);
                }
            }
        }

        @Test
        @Timeout(30)
        @DisplayName("Buyers making offers while the Seller edits: no offer is left pending on the edited listing")
        void offersDuringEdit() throws Exception {
            Listing listing = liveSell();
            List<Buyer> buyers = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                buyers.add(storeBuyer("s04_racer" + i, "Racer " + i));
            }
            CreateListingRequest request = formOf(listing).title("Edited while busy").build();
            List<Callable<Object>> tasks = new ArrayList<>();
            for (Buyer buyer : buyers) {
                tasks.add(() -> SessionManager.withSession(buyer, () ->
                        DATA.submitPriceOffer(listing.getId(), buyer.getUsername(), 100.0)));
            }
            tasks.add(() -> {
                Set<String> confirmed = Set.of();
                for (int attempt = 0; attempt <= THREADS + 1; attempt++) {
                    MyListingResult result = service.editListing(listing.getId(), request, confirmed);
                    if (result.getStatus() != MyListingStatus.PENDING_OFFERS) {
                        return result;
                    }
                    confirmed = result.getOfferIds();
                }
                return fail("The edit never went through");
            });

            List<Object> results = runTogether(tasks);

            MyListingResult edited = (MyListingResult) results.get(THREADS);
            assertTrue(edited.isSuccess(), edited::toString);
            assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
            List<Offer> offers = DATA.getOffersForListing(listing.getId());
            assertTrue(offers.stream().allMatch(Offer::isDeclined), "Every stored offer was declined by the edit");
            assertEquals(offers.size(), edited.getOffers().size());
            for (Object result : results.subList(0, THREADS)) {
                ActOnListingResult offer = (ActOnListingResult) result;
                assertTrue(offer.isSuccess() || offer.getStatus() == ActOnListingStatus.LISTING_NOT_LIVE, offer::getMessage);
            }
        }

        @Test
        @Timeout(20)
        @DisplayName("10 edits at once never mix the values of two forms")
        void concurrentEdits() throws Exception {
            Listing listing = liveSell();
            int adminBefore = DATA.getNotificationsFor(ADMIN).size();
            List<Callable<MyListingResult>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                CreateListingRequest request = formOf(listing).title("Title " + i).description("Description " + i).build();
                tasks.add(() -> service.editListing(listing.getId(), request, null));
            }

            List<MyListingResult> results = runTogether(tasks);

            assertTrue(results.stream().allMatch(MyListingResult::isSuccess));
            String index = listing.getTitle().substring("Title ".length());
            assertEquals("Description " + index, listing.getDescription());
            assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
            List<Notification> sent = notificationsSince(ADMIN, adminBefore);
            assertEquals(THREADS, sent.size());
            assertEquals(1, sent.stream().filter(n -> n.getTitle().startsWith("Listing Resubmitted")).count(),
                    "Only the first edit resubmits the Live listing");
        }
    }
}
