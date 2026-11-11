package com.hkust.marketplace.services;

import com.hkust.marketplace.database.DatabaseManager;
import com.hkust.marketplace.models.AdminProfileUpdateRequest;
import com.hkust.marketplace.models.AdminProfileUpdateResult;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.LoginResult;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.OfferType;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that every change made by the Admin Portal services is written to the SQLite
 * database (review of the admin part, finding 3), and so is kept when the application
 * restarts.
 *
 * <p>Each test works on its own in-memory SQLite database. It makes a change through an Admin
 * service on a first data store, then opens a second data store on the same database, which
 * loads everything from SQLite as the application does when it starts again, and checks the
 * change is preserved.</p>
 */
@DisplayName("Admin Portal changes are kept in SQLite after a restart (review finding 3)")
class AdminPersistenceTest {

    private DatabaseManager db;
    private DummyDataService data;

    @BeforeEach
    void setUp() {
        SessionManager.logout();
        db = new DatabaseManager("jdbc:sqlite:file:admin_persistence_" + UUID.randomUUID() + "?mode=memory&cache=shared");
        data = new DummyDataService(db);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        db.close();
    }

    private DummyDataService restart() {
        return new DummyDataService(db);
    }

    private void loginAsAdmin() {
        User admin = data.findUser(DummyDataService.SEEDED_ADMIN_USERNAME);
        SessionManager.login(admin);
    }

    @Nested
    @DisplayName("Admin Profile Persistence (US-A04)")
    class AdminProfilePersistence {

        @Test
        @DisplayName("Admin full name update persists across restart")
        void fullNameUpdatePersistsAcrossRestart() {
            loginAsAdmin();
            AdminProfileService service = data.getAdminProfileService();
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest("admin", "Prof. Admin Chief");
            AdminProfileUpdateResult result = service.updateProfile("admin", req);
            assertTrue(result.isSuccess());

            DummyDataService restarted = restart();
            User user = restarted.findUser("admin");
            assertNotNull(user);
            assertEquals("Prof. Admin Chief", user.getFullName());
        }

        @Test
        @DisplayName("Admin password update persists across restart and allows login with new password")
        void passwordUpdatePersistsAcrossRestart() {
            loginAsAdmin();
            AdminProfileService service = data.getAdminProfileService();
            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    "admin",
                    "HKUST Marketplace Admin",
                    "admin123",
                    "NewAdminPass123",
                    "NewAdminPass123"
            );
            AdminProfileUpdateResult result = service.updateProfile("admin", req);
            assertTrue(result.isSuccess());

            DummyDataService restarted = restart();
            AdminLoginService loginService = new AdminLoginService(restarted);

            // Old password should fail
            LoginResult oldFail = loginService.authenticate("admin", "admin123");
            assertFalse(oldFail.isSuccess());

            // New password should succeed
            LoginResult newSuccess = loginService.authenticate("admin", "NewAdminPass123");
            assertTrue(newSuccess.isSuccess());
            assertNotNull(newSuccess.getUser());
        }
    }

    @Nested
    @DisplayName("Admin Notification Persistence (US-A06)")
    class AdminNotificationPersistence {

        @Test
        @DisplayName("Single notification markAsRead persists across restart")
        void markAsReadPersistsAcrossRestart() {
            Notification notif = new Notification(
                    "admin",
                    "Suspicious Listing",
                    "Listing L100 has been flagged.",
                    "Reports",
                    "L100",
                    "LISTING"
            );
            data.addNotification(notif);
            assertFalse(notif.isRead());

            loginAsAdmin();
            AdminNotificationService service = data.getAdminNotificationService();
            boolean marked = service.markAsRead(notif.getId(), "admin");
            assertTrue(marked);

            DummyDataService restarted = restart();
            Optional<Notification> persisted = restarted.getAllNotifications().stream()
                    .filter(n -> notif.getId().equalsIgnoreCase(n.getId()))
                    .findFirst();
            assertTrue(persisted.isPresent());
            assertTrue(persisted.get().isRead());
        }

        @Test
        @DisplayName("markAllAsRead persists across restart")
        void markAllAsReadPersistsAcrossRestart() {
            Notification n1 = new Notification("admin", "Report 1", "Body 1", "Reports");
            Notification n2 = new Notification("admin", "Report 2", "Body 2", "Reports");
            data.addNotification(n1);
            data.addNotification(n2);

            loginAsAdmin();
            AdminNotificationService service = data.getAdminNotificationService();
            int count = service.markAllAsRead("admin");
            assertTrue(count >= 2);

            DummyDataService restarted = restart();
            for (Notification n : restarted.getNotificationsFor("admin")) {
                assertTrue(n.isRead(), "Notification " + n.getId() + " should be marked read");
            }
        }

        @Test
        @DisplayName("getNotificationDetail marks notification as read in SQLite")
        void getNotificationDetailMarksReadInSQLite() {
            Notification notif = new Notification(
                    "admin",
                    "System Announcement",
                    "Server maintenance tonight.",
                    "Announcements"
            );
            data.addNotification(notif);
            assertFalse(notif.isRead());

            loginAsAdmin();
            AdminNotificationService service = data.getAdminNotificationService();
            var detail = service.getNotificationDetail(notif.getId(), "admin");
            assertTrue(detail.isSuccess());

            DummyDataService restarted = restart();
            Optional<Notification> persisted = restarted.getAllNotifications().stream()
                    .filter(n -> notif.getId().equalsIgnoreCase(n.getId()))
                    .findFirst();
            assertTrue(persisted.isPresent());
            assertTrue(persisted.get().isRead());
        }
    }

    @Nested
    @DisplayName("Admin Listing Takedown Persistence (US-A07)")
    class AdminListingTakedownPersistence {

        @Test
        @DisplayName("takeDownListing persists listing REMOVED status and declines open offers in SQLite")
        void takeDownListingDeclinesOffersInSQLite() {
            // Setup a live listing
            Listing listing = new Listing();
            listing.setTitle("Prohibited Item");
            listing.setDescription("Item that breaks rules.");
            listing.setType(ListingType.SELL);
            listing.setCategory(Category.OTHER);
            listing.setCondition(Condition.NEW);
            listing.setMeetupLocation(MeetupLocation.LIBRARY);
            listing.setSellerUsername("charlie");
            listing.setSellerFullName("Charlie Brown");
            listing.setPrice(50.0);
            listing.setStatus(ListingStatus.LIVE);
            data.addListing(listing);

            // Add an open offer on this listing
            Offer offer = new Offer();
            offer.setListingId(listing.getId());
            offer.setListingTitle(listing.getTitle());
            offer.setBuyerUsername("alice");
            offer.setSellerUsername("charlie");
            offer.setPrice(45.0);
            offer.setType(OfferType.PRICE_OFFER);
            offer.setStatus(OfferStatus.PENDING);
            data.addOffer(offer);

            // Add an open countered offer with a counter-offer
            Offer counteredOffer = new Offer();
            counteredOffer.setListingId(listing.getId());
            counteredOffer.setListingTitle(listing.getTitle());
            counteredOffer.setBuyerUsername("bob");
            counteredOffer.setSellerUsername("charlie");
            counteredOffer.setPrice(40.0);
            counteredOffer.setType(OfferType.PRICE_OFFER);
            counteredOffer.setStatus(OfferStatus.COUNTERED);

            Offer counter = new Offer();
            counter.setListingId(listing.getId());
            counter.setBuyerUsername("bob");
            counter.setSellerUsername("charlie");
            counter.setPrice(48.0);
            counter.setType(OfferType.PRICE_OFFER);
            counter.setStatus(OfferStatus.PENDING);
            data.addOffer(counter);
            counteredOffer.setCounterOffer(counter);
            data.addOffer(counteredOffer);

            loginAsAdmin();
            AdminListingService listingService = data.getAdminListingService();
            var result = listingService.takeDownListing(listing.getId(), "Inappropriate content", "admin");
            assertTrue(result.isSuccess());

            DummyDataService restarted = restart();
            Listing reloadedListing = restarted.findListing(listing.getId());
            assertNotNull(reloadedListing);
            assertEquals(ListingStatus.REMOVED, reloadedListing.getStatus());
            assertEquals("Inappropriate content", reloadedListing.getAdminReason());

            Offer reloadedOffer = restarted.findOffer(offer.getId());
            assertNotNull(reloadedOffer);
            assertEquals(OfferStatus.DECLINED, reloadedOffer.getStatus());
            assertTrue(reloadedOffer.getDeclineReason().contains("Inappropriate content"));

            Offer reloadedCountered = restarted.findOffer(counteredOffer.getId());
            assertNotNull(reloadedCountered);
            assertEquals(OfferStatus.DECLINED, reloadedCountered.getStatus());

            Offer reloadedCounter = restarted.findOffer(counter.getId());
            assertNotNull(reloadedCounter);
            assertEquals(OfferStatus.DECLINED, reloadedCounter.getStatus());
        }
    }

    @Nested
    @DisplayName("Admin Rating Moderation Persistence (US-A08)")
    class AdminRatingModerationPersistence {

        @Test
        @DisplayName("Hiding a flagged review persists review hidden state and marks admin notification read across restart")
        void hideReviewPersistsHiddenAndNotificationRead() {
            Review review = new Review("charlie", "alice", "D1", "L1", "Vintage Camera", 1, "Offensive remarks");
            review.setFlagged(true);
            review.setHidden(false);
            data.addReview(review);

            Notification adminNotif = new Notification(
                    "admin",
                    "Review Flagged",
                    "A review has been flagged for Charlie.",
                    "FLAGGED_REVIEWS",
                    review.getId(),
                    "REVIEW"
            );
            data.addNotification(adminNotif);
            assertFalse(adminNotif.isRead());

            loginAsAdmin();
            AdminRatingService ratingService = data.getAdminRatingService();
            var result = ratingService.hideReview(review.getId(), "admin");
            assertTrue(result.isSuccess());

            DummyDataService restarted = restart();
            Review reloadedReview = restarted.findReview(review.getId());
            assertNotNull(reloadedReview);
            assertTrue(reloadedReview.isHidden());
            assertFalse(reloadedReview.isFlagged());

            Optional<Notification> reloadedNotif = restarted.getAllNotifications().stream()
                    .filter(n -> adminNotif.getId().equalsIgnoreCase(n.getId()))
                    .findFirst();
            assertTrue(reloadedNotif.isPresent());
            assertTrue(reloadedNotif.get().isRead());
        }
    }
}
