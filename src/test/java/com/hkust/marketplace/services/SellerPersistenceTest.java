package com.hkust.marketplace.services;

import com.hkust.marketplace.database.DatabaseManager;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.CreateListingRequest;
import com.hkust.marketplace.models.CreateListingResult;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.LendingDurationUnit;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.MyListingResult;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferManagementResult;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.Photo;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.ReviewFlagReason;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SellerProfileResult;
import com.hkust.marketplace.models.SellerProfileUpdateRequest;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that every change made by the Seller Portal services is written to the SQLite
 * database (review of the seller part, finding 1), and so is kept when the application
 * restarts.
 *
 * <p>Each test works on its own in-memory SQLite database. It makes a change through a Seller
 * service on a first data store, then opens a second data store on the same database, which
 * loads everything from SQLite as the application does when it starts again, and checks the
 * change is there.</p>
 */
@DisplayName("Seller Portal changes are kept in SQLite after a restart (review finding 1)")
class SellerPersistenceTest {

    /** Signature of a PNG file, enough for the photo checks of use case 2.3. */
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private DummyDataService data;

    @BeforeEach
    void setUp() {
        SessionManager.logout();
        db = new DatabaseManager("jdbc:sqlite:file:seller_persistence_" + UUID.randomUUID() + "?mode=memory&cache=shared");
        data = new DummyDataService(db);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        db.close();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Opens a new data store on the same database, as the application does when it restarts.
     *
     * @return the data store loaded from SQLite
     */
    private DummyDataService restart() {
        return new DummyDataService(db);
    }

    /** Logs in a seeded user of the first data store. */
    private void loginAs(String username) {
        SessionManager.login(data.findUser(username));
    }

    /** Runs a buyer action in the session of a seeded buyer, then goes back to the current session. */
    private <T> T asBuyer(String buyer, Supplier<T> action) {
        return SessionManager.withSession(data.findUser(buyer), action::get);
    }

    /** Adds a Live listing of a seeded seller, with one photo; a Lend listing can be lent for 7 days. */
    private Listing liveListing(String seller, ListingType type) {
        Listing listing = new Listing();
        listing.setTitle("Test " + type);
        listing.setDescription("A listing used by the persistence tests.");
        listing.setType(type);
        listing.setCategory(Category.OTHER);
        listing.setCondition(Condition.USED);
        listing.setMeetupLocation(MeetupLocation.LIBRARY);
        listing.setSellerUsername(seller);
        listing.setSellerFullName(data.findUser(seller).getFullName());
        if (type == ListingType.SELL) {
            listing.setPrice(100.0);
        }
        if (type == ListingType.LEND) {
            listing.setLendingDuration(Duration.ofDays(7));
        }
        listing.setStatus(ListingStatus.LIVE);
        listing.setPhotos(List.of(new Photo("p.png")));
        data.addListing(listing);
        return listing;
    }

    /** Makes a price offer as a seeded buyer. */
    private Offer priceOffer(String listingId, String buyer, double price) {
        return asBuyer(buyer, () -> data.submitPriceOffer(listingId, buyer, price).getOffer());
    }

    /** Writes a small PNG file in the temporary folder. */
    private Path png(String name) throws IOException {
        return Files.write(tempDir.resolve(name), PNG_HEADER);
    }

    // =====================================================================
    // Use case 2.3: Create Listing
    // =====================================================================

    @Nested
    @DisplayName("Create Listing (UC 2.3)")
    class CreateListing {

        @Test
        @DisplayName("A submitted listing is reloaded with its photos, in order")
        void listingIsReloadedWithItsPhotos() throws IOException {
            Path front = png("front.png");
            Path back = png("back.png");
            loginAs("charlie");
            CreateListingResult result = new CreateListingService(data).createListing(new CreateListingRequest(
                    ListingType.SELL, "Desk lamp", "Works well.", Category.FURNITURE, Condition.USED,
                    "80", "", LendingDurationUnit.DAYS, MeetupLocation.LIBRARY, List.of(front, back)));
            assertTrue(result.isSuccess());

            Listing reloaded = restart().findListing(result.getListing().getId());

            assertNotNull(reloaded);
            assertEquals(ListingStatus.PENDING_REVIEW, reloaded.getStatus());
            assertEquals(List.of(front.toAbsolutePath().normalize().toString(), back.toAbsolutePath().normalize().toString()),
                    reloaded.getPhotoPaths());
        }
    }

    // =====================================================================
    // Use case 2.4: Manage My Listings
    // =====================================================================

    @Nested
    @DisplayName("Manage My Listings (UC 2.4)")
    class ManageMyListings {

        @Test
        @DisplayName("Editing a listing with open offers keeps the declined offers declined")
        void editDeclinesOffersDurably() throws IOException {
            Listing listing = liveListing("charlie", ListingType.SELL);
            listing.setPhotos(List.of(new Photo(png("lamp.png").toString())));
            Offer offer = priceOffer(listing.getId(), "alice", 60.0);
            loginAs("charlie");
            MyListingsService service = new MyListingsService(data);
            CreateListingRequest edit = MyListingsService.toEditRequest(listing);
            edit = new CreateListingRequest(edit.type(), "Renamed lamp", edit.description(), edit.category(),
                    edit.condition(), edit.price(), edit.lendingAmount(), edit.lendingUnit(), edit.meetupLocation(),
                    edit.photos());

            MyListingResult result = service.editListing(listing.getId(), edit, List.of(offer.getId()));
            assertTrue(result.isSuccess(), result.getMessage());

            DummyDataService reloaded = restart();
            Offer reloadedOffer = reloaded.findOffer(offer.getId());
            assertEquals(OfferStatus.DECLINED, reloadedOffer.getStatus());
            assertEquals(MyListingsService.REASON_LISTING_EDITED, reloadedOffer.getDeclineReason());
            Listing reloadedListing = reloaded.findListing(listing.getId());
            assertEquals("Renamed lamp", reloadedListing.getTitle());
            assertEquals(ListingStatus.PENDING_REVIEW, reloadedListing.getStatus());
        }

        @Test
        @DisplayName("Deleting a listing with a countered offer keeps the offer and its counter declined")
        void deleteDeclinesOffersDurably() {
            Listing listing = liveListing("charlie", ListingType.SELL);
            Offer offer = priceOffer(listing.getId(), "alice", 60.0);
            loginAs("charlie");
            assertTrue(new OfferManagementService(data).counterWithPrice(offer.getId(), 90.0).isSuccess());

            MyListingResult result = new MyListingsService(data).deleteListing(listing.getId(), List.of(offer.getId()));
            assertTrue(result.isSuccess(), result.getMessage());

            DummyDataService reloaded = restart();
            Offer reloadedOffer = reloaded.findOffer(offer.getId());
            assertEquals(OfferStatus.DECLINED, reloadedOffer.getStatus());
            assertEquals(MyListingsService.REASON_LISTING_DELETED, reloadedOffer.getDeclineReason());
            assertEquals(OfferStatus.DECLINED, reloadedOffer.getCounterOffer().getStatus());
            Listing reloadedListing = reloaded.findListing(listing.getId());
            assertEquals(ListingStatus.REMOVED, reloadedListing.getStatus());
            assertTrue(reloadedListing.isDeletedBySeller());
        }
    }

    // =====================================================================
    // Use case 2.5: Manage Offers And Requests
    // =====================================================================

    @Nested
    @DisplayName("Manage Offers And Requests (UC 2.5)")
    class ManageOffers {

        @Test
        @DisplayName("Accepting a price offer keeps the deal, the Reserved listing and the declined competing offer")
        void acceptIsKept() {
            Listing listing = liveListing("charlie", ListingType.SELL);
            Offer accepted = priceOffer(listing.getId(), "alice", 70.0);
            Offer competing = priceOffer(listing.getId(), "bob", 65.0);
            loginAs("charlie");

            OfferManagementResult result = new OfferManagementService(data).acceptOffer(accepted.getId());
            assertTrue(result.isSuccess(), result.getMessage());

            DummyDataService reloaded = restart();
            assertEquals(OfferStatus.ACCEPTED, reloaded.findOffer(accepted.getId()).getStatus());
            assertEquals(ListingStatus.RESERVED, reloaded.findListing(listing.getId()).getStatus());
            Offer reloadedCompeting = reloaded.findOffer(competing.getId());
            assertEquals(OfferStatus.DECLINED, reloadedCompeting.getStatus());
            assertEquals(OfferManagementService.REASON_OTHER_OFFER_ACCEPTED, reloadedCompeting.getDeclineReason());
            assertEquals(DealStatus.ACTIVE, reloaded.findDeal(result.getDeal().getId()).getStatus());
        }

        @Test
        @DisplayName("Accepting a borrow request keeps the listing Lent")
        void acceptBorrowRequestIsKept() {
            Listing listing = liveListing("charlie", ListingType.LEND);
            LocalDateTime start = LocalDateTime.now().plusMinutes(1);
            Offer request = asBuyer("alice", () -> data.submitBorrowRequest(listing.getId(), "alice",
                    start, start.plusHours(1)).getOffer());
            loginAs("charlie");

            assertTrue(new OfferManagementService(data).acceptOffer(request.getId()).isSuccess());

            DummyDataService reloaded = restart();
            assertEquals(OfferStatus.ACCEPTED, reloaded.findOffer(request.getId()).getStatus());
            assertEquals(ListingStatus.LENT, reloaded.findListing(listing.getId()).getStatus());
        }

        @Test
        @DisplayName("Declining an offer keeps the decline and its reason")
        void declineIsKept() {
            Listing listing = liveListing("charlie", ListingType.SELL);
            Offer offer = priceOffer(listing.getId(), "alice", 10.0);
            loginAs("charlie");

            assertTrue(new OfferManagementService(data).declineOffer(offer.getId(), "Too low").isSuccess());

            Offer reloaded = restart().findOffer(offer.getId());
            assertEquals(OfferStatus.DECLINED, reloaded.getStatus());
            assertEquals("Too low", reloaded.getDeclineReason());
            assertEquals(ListingStatus.LIVE, restart().findListing(listing.getId()).getStatus());
        }

        @Test
        @DisplayName("An offer declined because its listing is no longer Live stays declined")
        void automaticDeclineIsKept() {
            Listing listing = liveListing("charlie", ListingType.SELL);
            Offer offer = priceOffer(listing.getId(), "alice", 50.0);
            listing.setStatus(ListingStatus.REMOVED);
            data.updateListing(listing);
            loginAs("charlie");

            OfferManagementResult result = new OfferManagementService(data).acceptOffer(offer.getId());
            assertFalse(result.isSuccess());

            Offer reloaded = restart().findOffer(offer.getId());
            assertEquals(OfferStatus.DECLINED, reloaded.getStatus());
            assertEquals(OfferManagementService.REASON_LISTING_UNAVAILABLE, reloaded.getDeclineReason());
        }

        @Test
        @DisplayName("A counter price is reloaded with the countered offer")
        void counterPriceIsKept() {
            Listing listing = liveListing("charlie", ListingType.SELL);
            Offer offer = priceOffer(listing.getId(), "alice", 10.0);
            loginAs("charlie");

            assertTrue(new OfferManagementService(data).counterWithPrice(offer.getId(), 20.0).isSuccess());

            Offer reloaded = restart().findOffer(offer.getId());
            assertEquals(OfferStatus.COUNTERED, reloaded.getStatus());
            assertNotNull(reloaded.getCounterOffer());
            assertEquals(20.0, reloaded.getCounterOffer().getPrice(), 0.001);
            assertEquals(OfferStatus.COUNTERED, reloaded.getCounterOffer().getStatus());
        }

        @Test
        @DisplayName("Counter borrow dates are reloaded with the countered request")
        void counterDatesAreKept() {
            Listing listing = liveListing("charlie", ListingType.LEND);
            LocalDateTime start = LocalDateTime.now().plusMinutes(1).withNano(0);
            Offer request = asBuyer("alice", () -> data.submitBorrowRequest(listing.getId(), "alice",
                    start, start.plusHours(1)).getOffer());
            loginAs("charlie");
            LocalDateTime counterStart = start.plusHours(2);
            LocalDateTime counterEnd = counterStart.plusHours(3);

            assertTrue(new OfferManagementService(data).counterWithDates(request.getId(), counterStart, counterEnd).isSuccess());

            Offer reloaded = restart().findOffer(request.getId());
            assertEquals(OfferStatus.COUNTERED, reloaded.getStatus());
            assertEquals(counterStart, reloaded.getCounterOffer().getStartDate());
            assertEquals(counterEnd, reloaded.getCounterOffer().getEndDate());
        }

        @Test
        @DisplayName("Opening the chat of an offer keeps the thread")
        void chatThreadIsKept() {
            Listing listing = liveListing("charlie", ListingType.SELL);
            Offer offer = priceOffer(listing.getId(), "alice", 10.0);
            loginAs("charlie");

            ChatThread thread = new OfferManagementService(data).openChat(offer.getId()).getChatThread();

            ChatThread reloaded = restart().findChatThread(thread.getId());
            assertNotNull(reloaded);
            assertEquals("alice", reloaded.getBuyerUsername());
            assertEquals(listing.getTitle(), reloaded.getListingTitle());
        }
    }

    // =====================================================================
    // Use case 2.6: Manage Seller Profile
    // =====================================================================

    @Nested
    @DisplayName("Manage Seller Profile (UC 2.6)")
    class ManageProfile {

        @Test
        @DisplayName("The new name, bio, meet-up locations, password and renamed listings are kept")
        void profileIsKept() {
            Listing listing = liveListing("charlie", ListingType.SELL);
            loginAs("charlie");
            SellerProfileResult result = new SellerProfileService(data).updateProfile(new SellerProfileUpdateRequest(
                    "Charlie Lee Junior", "Selling my old books.", List.of(MeetupLocation.ATRIUM, MeetupLocation.NORTH_GATE),
                    "password1", "NewPassw0rd", "NewPassw0rd"));
            assertTrue(result.isSuccess());

            DummyDataService reloaded = restart();
            User user = reloaded.findUser("charlie");
            Seller seller = (Seller) user;
            assertEquals("Charlie Lee Junior", seller.getFullName());
            assertEquals("Selling my old books.", seller.getBio());
            assertEquals(List.of(MeetupLocation.ATRIUM, MeetupLocation.NORTH_GATE), seller.getPreferredMeetups());
            assertTrue(PasswordHasher.verify("NewPassw0rd", seller.getPasswordHash()));
            assertFalse(PasswordHasher.verify("password1", seller.getPasswordHash()));
            assertEquals("Charlie Lee Junior", reloaded.findListing(listing.getId()).getSellerFullName());
            reloaded.getListingsBySeller("charlie").forEach(l -> assertEquals("Charlie Lee Junior", l.getSellerFullName()));
        }
    }

    // =====================================================================
    // Use case 2.7: View Seller Notifications
    // =====================================================================

    @Nested
    @DisplayName("View Seller Notifications (UC 2.7)")
    class Notifications {

        private Notification notification() {
            Notification notification = new Notification("charlie", "Test", "A notification.", "Announcements");
            data.addNotification(notification);
            return notification;
        }

        @Test
        @DisplayName("Opening a notification keeps it read")
        void openedNotificationStaysRead() {
            Notification notification = notification();
            loginAs("charlie");

            new SellerNotificationService(data).openNotification(notification.getId());

            assertTrue(findIn(restart(), notification.getId()).isRead());
        }

        @Test
        @DisplayName("Marking a notification read, then unread again, is kept")
        void readAndUnreadAreKept() {
            Notification notification = notification();
            loginAs("charlie");
            SellerNotificationService service = new SellerNotificationService(data);

            assertTrue(service.markAsRead(notification.getId()));
            assertTrue(findIn(restart(), notification.getId()).isRead());

            assertTrue(service.markAsUnread(notification.getId()));
            assertFalse(findIn(restart(), notification.getId()).isRead());
        }

        @Test
        @DisplayName("Marking every notification read is kept")
        void markAllAsReadIsKept() {
            notification();
            notification();
            loginAs("charlie");

            assertTrue(new SellerNotificationService(data).markAllAsRead() >= 2);

            assertTrue(restart().getNotificationsFor("charlie").stream().allMatch(Notification::isRead));
        }

        private Notification findIn(DummyDataService store, String id) {
            return store.getNotificationsFor("charlie").stream()
                    .filter(n -> id.equals(n.getId())).findFirst().orElseThrow();
        }
    }

    // =====================================================================
    // Use case 2.9: Handle Reviews
    // =====================================================================

    @Nested
    @DisplayName("Handle Reviews (UC 2.9)")
    class HandleReviews {

        private Review review() {
            Review review = new Review("charlie", "alice", null, "Test item", 2, "Not as described.");
            data.addReview(review);
            return review;
        }

        @Test
        @DisplayName("A reply is kept under the review")
        void replyIsKept() {
            Review review = review();
            loginAs("charlie");

            assertTrue(new ReviewHandlingService(data).replyToReview(review.getId(), "Sorry, it was a mistake.").isSuccess());

            assertEquals("Sorry, it was a mistake.", restart().findReview(review.getId()).getReply());
        }

        @Test
        @DisplayName("A flag and its reason are kept for the admin")
        void flagIsKept() {
            Review review = review();
            loginAs("charlie");

            assertTrue(new ReviewHandlingService(data).flagReview(review.getId(), ReviewFlagReason.SPAM, null).isSuccess());

            Review reloaded = restart().findReview(review.getId());
            assertTrue(reloaded.isFlagged());
            assertEquals(ReviewFlagReason.SPAM.describe(null), reloaded.getFlagReason());
            assertNull(reloaded.getReply());
        }
    }
}
