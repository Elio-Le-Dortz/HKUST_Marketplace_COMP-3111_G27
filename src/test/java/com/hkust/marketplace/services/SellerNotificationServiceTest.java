package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.ActOnListingResult;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.AdminListingOperationResult;
import com.hkust.marketplace.models.AdminRatingOperationResult;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealOperationResult;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReviewResult;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.MyListingResult;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationTargetType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferManagementResult;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.RateSellerResult;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.ReviewFlagReason;
import com.hkust.marketplace.models.ReviewHandlingResult;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SellerNotificationCategory;
import com.hkust.marketplace.models.SellerNotificationFilter;
import com.hkust.marketplace.models.SellerNotificationResult;
import com.hkust.marketplace.models.SellerNotificationStatus;
import com.hkust.marketplace.models.UserManagementResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of use case 2.7 "View Seller Notifications" (US-S07), grouped by the flows of its
 * specification. Acceptance criteria of the Sprint 2 backlog: the board shows the admin's
 * rejection reason, and a notification links to the relevant offer or deal.
 *
 * <p>Most notifications are sent by the real flows of the three portals (listing review and
 * take-down by the admin, offers, chats, deals and ratings by buyers, review moderation and
 * account changes by the admin), so these tests also check that the Seller's board understands
 * what the Buyer and Admin portals write.</p>
 */
@DisplayName("SellerNotificationService: US-S07 View Seller Notifications")
class SellerNotificationServiceTest {

    private static final DummyDataService DATA = DummyDataService.getInstance();
    /** The tests never log in with a password, so the stored hash is never checked. */
    private static final String UNUSED_HASH = "not-checked-by-these-tests";
    private static final String ADMIN = DummyDataService.SEEDED_ADMIN_USERNAME;

    private final SellerNotificationService service = new SellerNotificationService(DATA);

    private Seller seller;
    private Seller otherSeller;
    private Buyer buyer1;
    private Buyer buyer2;

    /**
     * Starts every test from the seeded data, with a fresh Seller without notifications, another
     * Seller, two buyers, and the Seller logged in.
     */
    @BeforeEach
    void setUp() {
        DATA.resetData();
        SessionManager.logout();
        seller = storeSeller("s07_seller", "Sam Seller");
        otherSeller = storeSeller("s07_other", "Olive Other");
        buyer1 = storeBuyer("s07_buyer1", "Alice Buyer");
        buyer2 = storeBuyer("s07_buyer2", "Bob Buyer");
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

    /** Stores a notification for a user, received a given number of minutes ago. */
    private static Notification store(String recipient, String title, String category,
                                      String relatedId, String relatedType, long minutesAgo) {
        Notification n = new Notification(recipient, title, title + " (message)", category, relatedId, relatedType);
        n.setTimestamp(LocalDateTime.now().minusMinutes(minutesAgo));
        DATA.addNotification(n);
        return n;
    }

    /** Stores a notification for the logged-in Seller, without related item. */
    private Notification storeForSeller(String title, String category, long minutesAgo) {
        return store(seller.getUsername(), title, category, null, null, minutesAgo);
    }

    /** Stores a notification for the logged-in Seller about an item. */
    private Notification storeLinked(String relatedId, String relatedType) {
        return store(seller.getUsername(), "Linked to " + relatedType, "Offers", relatedId, relatedType, 1);
    }

    /** Stores a listing of a Seller with a given type and status. */
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

    private Listing liveSellListing() {
        return storeListing(seller, "Desk Lamp", ListingType.SELL, ListingStatus.LIVE);
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
            LocalDateTime start = LocalDateTime.now().plusMinutes(1);
            ActOnListingResult result = DATA.submitBorrowRequest(listing.getId(), buyer.getUsername(), start, start.plusDays(1));
            assertTrue(result.isSuccess(), result.getMessage());
            return result.getOffer();
        });
    }

    /** The logged-in Seller accepts an offer through use case 2.5. */
    private static Deal accept(Offer offer) {
        OfferManagementResult result = new OfferManagementService(DATA).acceptOffer(offer.getId());
        assertTrue(result.isSuccess(), result.getMessage());
        return result.getDeal();
    }

    /**
     * Writes a review through the real buyer flow: the buyer makes an offer on a Live listing,
     * the Seller accepts it, the buyer marks the deal complete and rates the Seller.
     */
    private Review reviewThroughBuyerFlow(Buyer buyer, int rating) {
        Offer offer = priceOffer(liveSellListing(), buyer, 180.0);
        Deal deal = accept(offer);
        DealOperationResult completed = SessionManager.withSession(buyer, () ->
                DATA.markDealComplete(deal.getId(), buyer.getUsername()));
        assertTrue(completed.isSuccess(), completed.getMessage());
        RateSellerResult rated = SessionManager.withSession(buyer, () -> DATA.getBuyerReviewService()
                .submitRating(deal.getId(), buyer.getUsername(), rating, "Smooth deal."));
        assertTrue(rated.isSuccess(), rated.getMessage());
        return rated.getReview();
    }

    /** The only notification of the logged-in Seller with a given title. */
    private Notification onlyNotificationTitled(String title) {
        List<Notification> matching = service.getMyNotifications().stream()
                .filter(n -> title.equals(n.getTitle())).toList();
        assertEquals(1, matching.size(), "Exactly one '" + title + "' notification is expected for the Seller");
        return matching.get(0);
    }

    private static void assertCategory(SellerNotificationCategory expected, Notification notification) {
        assertEquals(expected, SellerNotificationCategory.of(notification),
                "Category of '" + notification.getTitle() + "' (stored as " + notification.getCategory() + ")");
    }

    private static void assertUnavailable(SellerNotificationResult result, NotificationTargetType type, String message) {
        assertEquals(SellerNotificationStatus.ITEM_UNAVAILABLE, result.getStatus(), result.toString());
        assertEquals(type, result.getTargetType());
        assertEquals(message, result.getMessage());
        assertNull(result.getListing());
        assertNull(result.getOffer());
        assertNull(result.getDeal());
        assertNull(result.getReview());
        assertNull(result.getChatThread());
        assertTrue(result.getNotification().isRead(), "A3: the notification is still marked read");
    }

    // ------------------------------------------------------------------
    // Use case 2.2 special requirement: Seller session checked by the service
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Seller session is checked by every action (role-based access control)")
    class SessionChecks {

        private List<Executable> everyAction() {
            return List.of(
                    service::getMyNotifications,
                    () -> service.getMyNotifications(SellerNotificationFilter.all()),
                    service::getUnreadCount,
                    service::getCategoryCounts,
                    service::getUnreadCategoryCounts,
                    () -> service.openNotification("N1"),
                    () -> service.markAsRead("N1"),
                    () -> service.markAsUnread("N1"),
                    service::markAllAsRead);
        }

        private void assertEveryActionRefused() {
            for (Executable action : everyAction()) {
                assertThrows(SecurityException.class, action);
            }
        }

        @Test
        @DisplayName("Without any session, every action throws SecurityException")
        void noSession() {
            SessionManager.logout();
            assertEveryActionRefused();
        }

        @Test
        @DisplayName("A Buyer session cannot read or change the Seller's notifications")
        void buyerSession() {
            SessionManager.login(buyer1);
            assertEveryActionRefused();
        }

        @Test
        @DisplayName("An Admin session cannot read or change the Seller's notifications")
        void adminSession() {
            SessionManager.login(new Admin("s07_admin", "Admin", UNUSED_HASH));
            assertEveryActionRefused();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AccountStatus.class, names = {"DEACTIVATED", "DELETED"})
        @DisplayName("A Seller deactivated or deleted during the session is refused")
        void inactiveSeller(AccountStatus status) {
            seller.setAccountStatus(status);
            assertEveryActionRefused();
        }

        @Test
        @DisplayName("A refused action changes nothing")
        void refusedActionChangesNothing() {
            Notification n = storeForSeller("New Offer Received", "Offer", 1);
            SessionManager.login(buyer1);
            assertThrows(SecurityException.class, service::markAllAsRead);
            assertThrows(SecurityException.class, () -> service.openNotification(n.getId()));
            assertTrue(n.isUnread());
        }

        @Test
        @DisplayName("The service cannot be created without a data store; the default one is the application's")
        void constructors() {
            assertThrows(NullPointerException.class, () -> new SellerNotificationService(null));
            storeForSeller("Hello", "Announcements", 1);
            assertEquals(1, new SellerNotificationService().getMyNotifications().size());
        }
    }

    // ------------------------------------------------------------------
    // Basic flow step 2 and alternative flow A1
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Basic flow step 2: the Seller's notifications, newest first")
    class Board {

        @Test
        @DisplayName("A1: a new Seller has no notifications, nothing unread and empty categories")
        void noNotifications() {
            assertTrue(service.getMyNotifications().isEmpty());
            assertEquals(0, service.getUnreadCount());
            Map<SellerNotificationCategory, Integer> counts = service.getCategoryCounts();
            assertEquals(SellerNotificationCategory.values().length, counts.size());
            counts.values().forEach(count -> assertEquals(0, count));
        }

        @Test
        @DisplayName("Only the logged-in Seller's notifications are listed, whatever the case of the recipient")
        void onlyOwnNotifications() {
            Notification mine = storeForSeller("Listing Approved", "Listings", 3);
            Notification upperCase = store("S07_SELLER", "Listing Rejected", "Listings", null, null, 2);
            store(otherSeller.getUsername(), "Listing Approved", "Listings", null, null, 1);
            store(buyer1.getUsername(), "Offer Accepted", "Offers", null, null, 1);
            store(ADMIN, "New Submission", "Listing Submissions", null, null, 1);

            assertEquals(List.of(upperCase, mine), service.getMyNotifications());
        }

        @Test
        @DisplayName("Notifications are listed newest first")
        void newestFirst() {
            Notification old = storeForSeller("Old", "Offer", 60);
            Notification recent = storeForSeller("Recent", "Offer", 5);
            Notification middle = storeForSeller("Middle", "Offer", 30);

            assertEquals(List.of(recent, middle, old), service.getMyNotifications());
        }

        @Test
        @DisplayName("With the same timestamp, the notification received last comes first")
        void sameTimestamp() {
            LocalDateTime at = LocalDateTime.now().minusMinutes(1);
            Notification first = storeForSeller("First", "Offer", 1);
            Notification second = storeForSeller("Second", "Offer", 1);
            first.setTimestamp(at);
            second.setTimestamp(at);

            assertEquals(List.of(second, first), service.getMyNotifications());
        }

        @Test
        @DisplayName("A notification without timestamp is listed last")
        void withoutTimestampLast() {
            Notification undated = storeForSeller("Undated", "Offer", 1);
            undated.setTimestamp(null);
            Notification dated = storeForSeller("Dated", "Offer", 90);

            assertEquals(List.of(dated, undated), service.getMyNotifications());
        }

        @Test
        @DisplayName("Each notification keeps its timestamp, category, message and unread state")
        void notificationDetails() {
            Notification n = storeForSeller("Listing Approved", "Listings", 10);
            Notification listed = service.getMyNotifications().get(0);

            assertSame(n, listed);
            assertNotNull(listed.getTimestamp());
            assertFalse(listed.getFormattedTimestamp().isBlank());
            assertEquals("Listing Approved (message)", listed.getMessage());
            assertCategory(SellerNotificationCategory.LISTING_REVIEW, listed);
            assertTrue(listed.isUnread(), "Unread notifications are highlighted");
        }

        @Test
        @DisplayName("Reading the board does not mark anything read")
        void readingDoesNotMarkRead() {
            Notification n = storeForSeller("New Offer Received", "Offer", 1);
            service.getMyNotifications();
            service.getUnreadCount();
            service.getCategoryCounts();
            assertTrue(n.isUnread());
        }

        @Test
        @DisplayName("The list cannot be changed by the screen, and a null filter shows everything")
        void unmodifiableList() {
            storeForSeller("Listing Approved", "Listings", 1);
            List<Notification> list = service.getMyNotifications(null);
            assertEquals(1, list.size());
            assertThrows(UnsupportedOperationException.class, () -> list.add(new Notification()));
        }
    }

    // ------------------------------------------------------------------
    // The categories of the specification, sent by the three portals
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Every notification sent to the Seller by the portals lands in the right category")
    class CategoriesFromPortals {

        @Test
        @DisplayName("Listing Review: the admin approves the listing (use case 3.2)")
        void listingApproved() {
            Listing listing = storeListing(seller, "Bike", ListingType.SELL, ListingStatus.PENDING_REVIEW);
            ListingReviewResult approved = DATA.approveListing(listing.getId(), ADMIN);
            assertTrue(approved.isSuccess(), approved.getMessage());

            Notification n = onlyNotificationTitled("Listing Approved");
            assertCategory(SellerNotificationCategory.LISTING_REVIEW, n);
            assertEquals(listing.getId(), n.getRelatedItemId());
            assertNull(SellerNotificationService.adminReasonOf(n), "An approval has no reason");
        }

        @Test
        @DisplayName("Listing Review: the admin rejects the listing, and the board shows the admin's reason (acceptance criterion)")
        void listingRejectedWithReason() {
            Listing listing = storeListing(seller, "Bike", ListingType.SELL, ListingStatus.PENDING_REVIEW);
            ListingReviewResult rejected = DATA.rejectListing(listing.getId(), ADMIN, "The photos are blurry");
            assertTrue(rejected.isSuccess(), rejected.getMessage());

            Notification n = onlyNotificationTitled("Listing Rejected");
            assertCategory(SellerNotificationCategory.LISTING_REVIEW, n);
            assertEquals("The photos are blurry", SellerNotificationService.adminReasonOf(n));
        }

        @Test
        @DisplayName("Listing Review: the admin takes the listing down, with the reason shown (use case 3.7)")
        void listingTakenDown() {
            Listing listing = liveSellListing();
            AdminListingOperationResult takenDown = DATA.takeDownListing(listing.getId(), "Counterfeit item", ADMIN);
            assertTrue(takenDown.isSuccess(), takenDown.getMessage());

            Notification n = onlyNotificationTitled("Listing Taken Down");
            assertCategory(SellerNotificationCategory.LISTING_REVIEW, n);
            assertEquals("Counterfeit item", SellerNotificationService.adminReasonOf(n));
        }

        @Test
        @DisplayName("Offers And Requests: a buyer makes a price offer (use case 1.5)")
        void newPriceOffer() {
            priceOffer(liveSellListing(), buyer1, 150.0);
            assertCategory(SellerNotificationCategory.OFFERS_AND_REQUESTS, onlyNotificationTitled("New Offer Received"));
        }

        @Test
        @DisplayName("Offers And Requests: a buyer asks to borrow an item (use case 1.5)")
        void newBorrowRequest() {
            borrowRequest(storeListing(seller, "Calculator", ListingType.LEND, ListingStatus.LIVE), buyer1);
            assertCategory(SellerNotificationCategory.OFFERS_AND_REQUESTS, onlyNotificationTitled("Borrow Request Received"));
        }

        @Test
        @DisplayName("Offers And Requests: a buyer claims a free item (use case 1.5)")
        void newClaim() {
            Listing giveaway = storeListing(seller, "Hoodie", ListingType.GIVEAWAY, ListingStatus.LIVE);
            ActOnListingResult claim = SessionManager.withSession(buyer1, () ->
                    DATA.submitClaim(giveaway.getId(), buyer1.getUsername()));
            assertTrue(claim.isSuccess(), claim.getMessage());
            assertCategory(SellerNotificationCategory.OFFERS_AND_REQUESTS, onlyNotificationTitled("Giveaway Claim Received"));
        }

        @Test
        @DisplayName("Offers And Requests: a buyer withdraws an offer (use case 1.6)")
        void offerWithdrawn() {
            Offer offer = priceOffer(liveSellListing(), buyer1, 150.0);
            DealOperationResult withdrawn = SessionManager.withSession(buyer1, () ->
                    DATA.withdrawOffer(offer.getId(), buyer1.getUsername()));
            assertTrue(withdrawn.isSuccess(), withdrawn.getMessage());
            assertCategory(SellerNotificationCategory.OFFERS_AND_REQUESTS, onlyNotificationTitled("Offer Withdrawn"));
        }

        @Test
        @DisplayName("Offers And Requests: a buyer sends a chat message (use case 1.5)")
        void chatMessage() {
            Listing listing = liveSellListing();
            ChatThread thread = DATA.getChatService().getOrCreateThread(listing.getId(), buyer1.getUsername());
            assertTrue(DATA.getChatService().sendMessage(thread.getId(), buyer1.getUsername(), "Still available?").isSuccess());
            assertCategory(SellerNotificationCategory.OFFERS_AND_REQUESTS, onlyNotificationTitled("New Chat Message"));
        }

        @Test
        @DisplayName("Reviews: a buyer rates the Seller after a deal, linked to the review (use case 1.10)")
        void newReview() {
            Review review = reviewThroughBuyerFlow(buyer1, 4);

            Notification n = onlyNotificationTitled("New Review Received");
            assertCategory(SellerNotificationCategory.REVIEWS, n);
            assertEquals(review.getId(), n.getRelatedItemId());
        }

        @Test
        @DisplayName("Reviews: the admin hides a review or dismisses its flag (use case 3.8)")
        void reviewModerated() {
            Review hidden = reviewThroughBuyerFlow(buyer1, 1);
            AdminRatingOperationResult hide = DATA.hideReviewByAdmin(hidden.getId(), ADMIN);
            assertTrue(hide.isSuccess(), hide.getMessage());
            Review kept = reviewThroughBuyerFlow(buyer2, 2);
            ReviewHandlingResult flagged = new ReviewHandlingService(DATA)
                    .flagReview(kept.getId(), ReviewFlagReason.OFFENSIVE_LANGUAGE, null);
            assertTrue(flagged.isSuccess(), flagged.getMessage());
            AdminRatingOperationResult dismissed = DATA.dismissReviewFlagByAdmin(kept.getId(), ADMIN);
            assertTrue(dismissed.isSuccess(), dismissed.getMessage());

            assertCategory(SellerNotificationCategory.REVIEWS, onlyNotificationTitled("Review Hidden by Admin"));
            assertCategory(SellerNotificationCategory.REVIEWS, onlyNotificationTitled("Flag Dismissed: Review Retained"));
        }

        @Test
        @DisplayName("Deals: the buyer marks the deal complete (use case 1.6)")
        void dealCompleted() {
            Deal deal = accept(priceOffer(liveSellListing(), buyer1, 180.0));
            DealOperationResult completed = SessionManager.withSession(buyer1, () ->
                    DATA.markDealComplete(deal.getId(), buyer1.getUsername()));
            assertTrue(completed.isSuccess(), completed.getMessage());
            assertCategory(SellerNotificationCategory.DEALS, onlyNotificationTitled("Deal Completed"));
        }

        @Test
        @DisplayName("Deals: the buyer returns a borrowed item, or the loan ends and it is auto-returned (use case 1.6)")
        void itemReturned() {
            Deal returned = accept(borrowRequest(storeListing(seller, "Tent", ListingType.LEND, ListingStatus.LIVE), buyer1));
            assertTrue(SessionManager.withSession(buyer1, () ->
                    DATA.selfReturnItem(returned.getId(), buyer1.getUsername()).isSuccess()));
            Deal expired = accept(borrowRequest(storeListing(seller, "Drill", ListingType.LEND, ListingStatus.LIVE), buyer2));
            expired.setDueDate(LocalDateTime.now().minusSeconds(1));
            assertTrue(DATA.processAutoReturns() >= 1);

            assertCategory(SellerNotificationCategory.DEALS, onlyNotificationTitled("Item Returned"));
            assertCategory(SellerNotificationCategory.DEALS, onlyNotificationTitled("Item Auto-Returned"));
        }

        @Test
        @DisplayName("Announcements: the admin deactivates then reactivates the Seller's account (use case 3.3)")
        void accountChanges() {
            UserManagementResult off = SessionManager.withSession(DATA.findUser(ADMIN), () -> DATA.getUserManagementService()
                    .changeAccountStatus(seller.getUsername(), AccountStatus.DEACTIVATED, ADMIN));
            assertTrue(off.isSuccess(), off.getMessage());
            UserManagementResult on = SessionManager.withSession(DATA.findUser(ADMIN), () -> DATA.getUserManagementService()
                    .changeAccountStatus(seller.getUsername(), AccountStatus.ACTIVE, ADMIN));
            assertTrue(on.isSuccess(), on.getMessage());
            SessionManager.login(seller);

            Notification reactivated = onlyNotificationTitled("Account Reactivated");
            assertCategory(SellerNotificationCategory.ANNOUNCEMENTS, onlyNotificationTitled("Account Deactivated"));
            assertCategory(SellerNotificationCategory.ANNOUNCEMENTS, reactivated);
            assertEquals(SellerNotificationStatus.NO_RELATED_ITEM, service.openNotification(reactivated.getId()).getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Alternative flow A2 and the other filters
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("A2: filter by category, unread only and search")
    class Filters {

        private void storeOneOfEachCategory() {
            storeForSeller("Listing Rejected", "Listings", 5);
            storeForSeller("New Offer Received", "Offer", 4);
            storeForSeller("New Review Received", "Reviews", 3);
            storeForSeller("Deal Completed", "Deal", 2);
            storeForSeller("Account Reactivated", "Announcements", 1);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(SellerNotificationCategory.class)
        @DisplayName("Choosing a category shows only its notifications")
        void byCategory(SellerNotificationCategory category) {
            storeOneOfEachCategory();
            List<Notification> shown = service.getMyNotifications(SellerNotificationFilter.ofCategory(category));
            assertEquals(1, shown.size());
            assertCategory(category, shown.get(0));
        }

        @Test
        @DisplayName("A category without notifications shows an empty list")
        void emptyCategory() {
            storeForSeller("New Offer Received", "Offer", 1);
            assertTrue(service.getMyNotifications(SellerNotificationFilter.ofCategory(SellerNotificationCategory.DEALS)).isEmpty());
        }

        @Test
        @DisplayName("Unread only hides the notifications already read")
        void unreadOnly() {
            Notification read = storeForSeller("Listing Approved", "Listings", 2);
            Notification unread = storeForSeller("New Offer Received", "Offer", 1);
            read.setRead(true);
            assertEquals(List.of(unread), service.getMyNotifications(new SellerNotificationFilter(null, true, null)));
        }

        @Test
        @DisplayName("The search finds notifications by title or message, ignoring case")
        void search() {
            storeOneOfEachCategory();
            assertEquals(1, service.getMyNotifications(new SellerNotificationFilter(null, false, "REJECTED")).size());
            assertEquals(5, service.getMyNotifications(new SellerNotificationFilter(null, false, "(message)")).size());
            assertTrue(service.getMyNotifications(new SellerNotificationFilter(null, false, "nothing like this")).isEmpty());
        }

        @Test
        @DisplayName("Category, unread and search are combined")
        void combined() {
            Notification wanted = storeForSeller("Listing Rejected", "Listings", 3);
            storeForSeller("Listing Approved", "Listings", 2);
            storeForSeller("Rejected offer note", "Offer", 1);
            SellerNotificationFilter filter =
                    new SellerNotificationFilter(SellerNotificationCategory.LISTING_REVIEW, true, "rejected");
            assertEquals(List.of(wanted), service.getMyNotifications(filter));
            wanted.setRead(true);
            assertTrue(service.getMyNotifications(filter).isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // Unread counts
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Unread counts of the sidebar and of the category filter")
    class Counts {

        @Test
        @DisplayName("Only the Seller's unread notifications are counted")
        void unreadCount() {
            storeForSeller("A", "Offer", 3);
            storeForSeller("B", "Listings", 2).setRead(true);
            storeForSeller("C", "Reviews", 1);
            store(otherSeller.getUsername(), "D", "Offer", null, null, 1);
            assertEquals(2, service.getUnreadCount());
        }

        @Test
        @DisplayName("Totals and unread counts are given for every category, zero included")
        void categoryCounts() {
            storeForSeller("Offer 1", "Offer", 4);
            storeForSeller("Offer 2", "Offers", 3).setRead(true);
            storeForSeller("Chat", "Chat", 2);
            storeForSeller("Rejected", "Listings", 1).setRead(true);

            Map<SellerNotificationCategory, Integer> totals = service.getCategoryCounts();
            Map<SellerNotificationCategory, Integer> unread = service.getUnreadCategoryCounts();
            assertEquals(3, totals.get(SellerNotificationCategory.OFFERS_AND_REQUESTS));
            assertEquals(1, totals.get(SellerNotificationCategory.LISTING_REVIEW));
            assertEquals(0, totals.get(SellerNotificationCategory.DEALS));
            assertEquals(2, unread.get(SellerNotificationCategory.OFFERS_AND_REQUESTS));
            assertEquals(0, unread.get(SellerNotificationCategory.LISTING_REVIEW));
            assertEquals(SellerNotificationCategory.values().length, unread.size());
            assertThrows(UnsupportedOperationException.class, () -> totals.put(SellerNotificationCategory.DEALS, 9));
        }

        @Test
        @DisplayName("Opening a notification lowers the unread count by one")
        void openingLowersCount() {
            Notification n = storeForSeller("A", "Offer", 2);
            storeForSeller("B", "Offer", 1);
            service.openNotification(n.getId());
            assertEquals(1, service.getUnreadCount());
            assertEquals(1, service.getUnreadCategoryCounts().get(SellerNotificationCategory.OFFERS_AND_REQUESTS));
        }
    }

    // ------------------------------------------------------------------
    // Basic flow steps 3 and 4: select a notification
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Steps 3-4: selecting a notification marks it read and shows its related item")
    class OpenNotification {

        @Test
        @DisplayName("An approval opens the approved listing")
        void opensListing() {
            Listing listing = storeListing(seller, "Bike", ListingType.SELL, ListingStatus.PENDING_REVIEW);
            DATA.approveListing(listing.getId(), ADMIN);
            Notification n = onlyNotificationTitled("Listing Approved");

            SellerNotificationResult result = service.openNotification(n.getId());

            assertTrue(result.isSuccess(), result.toString());
            assertTrue(n.isRead());
            assertSame(n, result.getNotification());
            assertEquals(SellerNotificationCategory.LISTING_REVIEW, result.getCategory());
            assertEquals(NotificationTargetType.LISTING, result.getTargetType());
            assertSame(listing, result.getListing());
            assertEquals(ListingStatus.LIVE, result.getListing().getStatus());
            assertFalse(result.getMessage().isBlank());
        }

        @Test
        @DisplayName("A rejection opens the rejected listing, which holds the admin's reason (acceptance criterion)")
        void opensRejectedListing() {
            Listing listing = storeListing(seller, "Bike", ListingType.SELL, ListingStatus.PENDING_REVIEW);
            DATA.rejectListing(listing.getId(), ADMIN, "Price is too high for a used item");

            SellerNotificationResult result = service.openNotification(onlyNotificationTitled("Listing Rejected").getId());

            assertTrue(result.isSuccess(), result.toString());
            assertEquals(ListingStatus.REJECTED, result.getListing().getStatus());
            assertEquals("Price is too high for a used item", result.getListing().getAdminReason());
        }

        @Test
        @DisplayName("A take-down opens the listing, still on My Listings with the admin's reason")
        void opensTakenDownListing() {
            Listing listing = liveSellListing();
            DATA.takeDownListing(listing.getId(), "Not allowed on campus", ADMIN);

            SellerNotificationResult result = service.openNotification(onlyNotificationTitled("Listing Taken Down").getId());

            assertTrue(result.isSuccess(), result.toString());
            assertEquals(ListingStatus.REMOVED, result.getListing().getStatus());
            assertFalse(result.getListing().isDeletedBySeller());
        }

        @Test
        @DisplayName("A notification linked to an offer opens that offer (acceptance criterion: deep link to the offer)")
        void deepLinkToOffer() {
            Offer offer = priceOffer(liveSellListing(), buyer1, 150.0);
            Notification n = storeLinked(offer.getId(), NotificationTargetType.OFFER.name());

            SellerNotificationResult result = service.openNotification(n.getId());

            assertTrue(result.isSuccess(), result.toString());
            assertEquals(NotificationTargetType.OFFER, result.getTargetType());
            assertSame(offer, result.getOffer());
            assertEquals(OfferStatus.PENDING, result.getOffer().getStatus());
        }

        @Test
        @DisplayName("An offer that was already handled still opens, with its new status")
        void handledOfferStillOpens() {
            Offer offer = priceOffer(liveSellListing(), buyer1, 150.0);
            new OfferManagementService(DATA).declineOffer(offer.getId(), "Too low");
            Notification n = storeLinked(offer.getId(), "OFFER");

            SellerNotificationResult result = service.openNotification(n.getId());

            assertTrue(result.isSuccess(), result.toString());
            assertEquals(OfferStatus.DECLINED, result.getOffer().getStatus());
            assertEquals("Too low", result.getOffer().getDeclineReason());
        }

        @Test
        @DisplayName("A notification linked to a deal opens that deal (acceptance criterion: deep link to the deal)")
        void deepLinkToDeal() {
            Deal deal = accept(priceOffer(liveSellListing(), buyer1, 180.0));
            Notification n = storeLinked(deal.getId(), NotificationTargetType.DEAL.name());

            SellerNotificationResult result = service.openNotification(n.getId());

            assertTrue(result.isSuccess(), result.toString());
            assertEquals(NotificationTargetType.DEAL, result.getTargetType());
            assertSame(deal, result.getDeal());
            assertEquals(DealType.SALE, result.getDeal().getType());
            assertEquals(buyer1.getUsername(), result.getDeal().getBuyerUsername());
        }

        @Test
        @DisplayName("A new review notification opens the review (use case 1.10)")
        void opensReview() {
            Review review = reviewThroughBuyerFlow(buyer1, 5);

            SellerNotificationResult result = service.openNotification(onlyNotificationTitled("New Review Received").getId());

            assertTrue(result.isSuccess(), result.toString());
            assertEquals(NotificationTargetType.REVIEW, result.getTargetType());
            assertSame(review, result.getReview());
        }

        @Test
        @DisplayName("A notification linked to a chat opens the chat with the buyer")
        void opensChat() {
            ChatThread thread = DATA.getChatService().getOrCreateThread(liveSellListing().getId(), buyer1.getUsername());
            Notification n = storeLinked(thread.getId(), NotificationTargetType.CHAT_THREAD.name());

            SellerNotificationResult result = service.openNotification(n.getId());

            assertTrue(result.isSuccess(), result.toString());
            assertSame(thread, result.getChatThread());
        }

        @Test
        @DisplayName("The related type is read without case, and identifiers are trimmed")
        void lenientIdentifiers() {
            Offer offer = priceOffer(liveSellListing(), buyer1, 150.0);
            Notification n = storeLinked("  " + offer.getId() + " ", "offer");

            SellerNotificationResult result = service.openNotification("  " + n.getId().toLowerCase() + " ");

            assertTrue(result.isSuccess(), result.toString());
            assertSame(offer, result.getOffer());
        }

        @Test
        @DisplayName("A notification without related item is marked read and only its message is shown")
        void noRelatedItem() {
            Notification n = storeForSeller("Plain Notice", "Offer", 1);

            SellerNotificationResult result = service.openNotification(n.getId());

            assertEquals(SellerNotificationStatus.NO_RELATED_ITEM, result.getStatus());
            assertEquals(SellerNotificationService.NO_RELATED_ITEM, result.getMessage());
            assertEquals(SellerNotificationCategory.OFFERS_AND_REQUESTS, result.getCategory());
            assertEquals(NotificationTargetType.NONE, result.getTargetType());
            assertTrue(n.isRead());
        }

        @ParameterizedTest(name = "related type {0}")
        @ValueSource(strings = {"NONE", "ANNOUNCEMENT", "USER", "REPORT"})
        @DisplayName("An announcement, or an item kind the Seller cannot open, only shows the message")
        void typesWithoutItem(String type) {
            Notification n = store(seller.getUsername(), "Notice", "Announcements", "X1", type, 1);
            SellerNotificationResult result = service.openNotification(n.getId());
            assertEquals(SellerNotificationStatus.NO_RELATED_ITEM, result.getStatus());
            assertTrue(n.isRead());
        }

        @Test
        @DisplayName("Opening a notification again keeps it read")
        void openTwice() {
            Notification n = storeForSeller("Notice", "Announcements", 1);
            service.openNotification(n.getId());
            assertEquals(SellerNotificationStatus.NO_RELATED_ITEM, service.openNotification(n.getId()).getStatus());
            assertTrue(n.isRead());
        }
    }

    // ------------------------------------------------------------------
    // Alternative flow A3: related item no longer exists
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("A3: the related item no longer exists or is not the Seller's")
    class RelatedItemUnavailable {

        @Test
        @DisplayName("A listing the Seller deleted (use case 2.4) is no longer available")
        void deletedListing() {
            Listing listing = storeListing(seller, "Bike", ListingType.SELL, ListingStatus.PENDING_REVIEW);
            DATA.approveListing(listing.getId(), ADMIN);
            MyListingResult deleted = new MyListingsService(DATA).deleteListing(listing.getId(), List.of());
            assertTrue(deleted.isSuccess(), deleted.getMessage());

            SellerNotificationResult result = service.openNotification(onlyNotificationTitled("Listing Approved").getId());

            assertUnavailable(result, NotificationTargetType.LISTING, SellerNotificationService.LISTING_UNAVAILABLE);
        }

        @Test
        @DisplayName("A listing that does not exist, or belongs to another Seller, is not shown")
        void missingOrForeignListing() {
            Listing foreign = storeListing(otherSeller, "Their Bike", ListingType.SELL, ListingStatus.LIVE);
            assertUnavailable(service.openNotification(storeLinked("L99999", "LISTING").getId()),
                    NotificationTargetType.LISTING, SellerNotificationService.LISTING_UNAVAILABLE);
            assertUnavailable(service.openNotification(storeLinked(foreign.getId(), "LISTING").getId()),
                    NotificationTargetType.LISTING, SellerNotificationService.LISTING_UNAVAILABLE);
        }

        @Test
        @DisplayName("An offer that does not exist, or was made to another Seller, is not shown")
        void missingOrForeignOffer() {
            Offer foreign = priceOffer(storeListing(otherSeller, "Their Lamp", ListingType.SELL, ListingStatus.LIVE), buyer1, 90.0);
            assertUnavailable(service.openNotification(storeLinked("O99999", "OFFER").getId()),
                    NotificationTargetType.OFFER, SellerNotificationService.OFFER_UNAVAILABLE);
            assertUnavailable(service.openNotification(storeLinked(foreign.getId(), "OFFER").getId()),
                    NotificationTargetType.OFFER, SellerNotificationService.OFFER_UNAVAILABLE);
        }

        @Test
        @DisplayName("A deal that does not exist, or is another Seller's, is not shown")
        void missingOrForeignDeal() {
            Deal foreign = new Deal();
            foreign.setSellerUsername(otherSeller.getUsername());
            foreign.setBuyerUsername(buyer1.getUsername());
            DATA.addDeal(foreign);
            assertUnavailable(service.openNotification(storeLinked("D99999", "DEAL").getId()),
                    NotificationTargetType.DEAL, SellerNotificationService.DEAL_UNAVAILABLE);
            assertUnavailable(service.openNotification(storeLinked(foreign.getId(), "DEAL").getId()),
                    NotificationTargetType.DEAL, SellerNotificationService.DEAL_UNAVAILABLE);
        }

        @Test
        @DisplayName("A review hidden by the admin (use case 3.8) is no longer shown to the Seller")
        void hiddenReview() {
            Review review = reviewThroughBuyerFlow(buyer1, 1);
            DATA.hideReviewByAdmin(review.getId(), ADMIN);

            assertUnavailable(service.openNotification(onlyNotificationTitled("New Review Received").getId()),
                    NotificationTargetType.REVIEW, SellerNotificationService.REVIEW_HIDDEN);
            assertUnavailable(service.openNotification(onlyNotificationTitled("Review Hidden by Admin").getId()),
                    NotificationTargetType.REVIEW, SellerNotificationService.REVIEW_HIDDEN);
        }

        @Test
        @DisplayName("A review that does not exist, or is about another Seller, is not shown")
        void missingOrForeignReview() {
            Review foreign = new Review(otherSeller.getUsername(), buyer1.getUsername(), null, "Lamp", 5, "Great");
            DATA.addReview(foreign);
            assertUnavailable(service.openNotification(storeLinked("R99999", "REVIEW").getId()),
                    NotificationTargetType.REVIEW, SellerNotificationService.REVIEW_UNAVAILABLE);
            assertUnavailable(service.openNotification(storeLinked(foreign.getId(), "REVIEW").getId()),
                    NotificationTargetType.REVIEW, SellerNotificationService.REVIEW_UNAVAILABLE);
        }

        @Test
        @DisplayName("A chat that does not exist, or is another Seller's, is not shown")
        void missingOrForeignChat() {
            Listing theirs = storeListing(otherSeller, "Their Lamp", ListingType.SELL, ListingStatus.LIVE);
            ChatThread foreign = DATA.getChatService().getOrCreateThread(theirs.getId(), buyer1.getUsername());
            assertUnavailable(service.openNotification(storeLinked("T99999", "CHAT_THREAD").getId()),
                    NotificationTargetType.CHAT_THREAD, SellerNotificationService.CHAT_UNAVAILABLE);
            assertUnavailable(service.openNotification(storeLinked(foreign.getId(), "CHAT_THREAD").getId()),
                    NotificationTargetType.CHAT_THREAD, SellerNotificationService.CHAT_UNAVAILABLE);
        }
    }

    // ------------------------------------------------------------------
    // Notification not found
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Selecting a notification that is not on the Seller's board")
    class NotFound {

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("No notification selected")
        void nothingSelected(String id) {
            SellerNotificationResult result = service.openNotification(id);
            assertEquals(SellerNotificationStatus.NOTIFICATION_NOT_FOUND, result.getStatus());
            assertEquals(SellerNotificationService.NOTIFICATION_NOT_SELECTED, result.getMessage());
            assertNull(result.getNotification());
            assertFalse(service.markAsRead(id));
            assertFalse(service.markAsUnread(id));
        }

        @Test
        @DisplayName("An unknown notification is not found")
        void unknownNotification() {
            SellerNotificationResult result = service.openNotification("N99999");
            assertEquals(SellerNotificationStatus.NOTIFICATION_NOT_FOUND, result.getStatus());
            assertEquals(SellerNotificationService.NOTIFICATION_NOT_FOUND, result.getMessage());
        }

        @Test
        @DisplayName("Another user's notification is not found and stays unread")
        void otherUsersNotification() {
            Notification theirs = store(otherSeller.getUsername(), "Listing Approved", "Listings", null, null, 1);
            Notification buyers = store(buyer1.getUsername(), "Offer Accepted", "Offers", null, null, 1);

            assertEquals(SellerNotificationStatus.NOTIFICATION_NOT_FOUND, service.openNotification(theirs.getId()).getStatus());
            assertFalse(service.markAsRead(buyers.getId()));
            assertEquals(0, service.markAllAsRead());
            assertTrue(theirs.isUnread());
            assertTrue(buyers.isUnread());
        }
    }

    // ------------------------------------------------------------------
    // Mark read, unread, all read
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Marking notifications read or unread")
    class MarkRead {

        @Test
        @DisplayName("A notification can be marked read, then unread again, without opening it")
        void markReadAndUnread() {
            Notification n = storeForSeller("New Offer Received", "Offer", 1);
            assertTrue(service.markAsRead(n.getId()));
            assertTrue(n.isRead());
            assertTrue(service.markAsUnread(n.getId()));
            assertTrue(n.isUnread());
            assertFalse(service.markAsRead("N99999"));
        }

        @Test
        @DisplayName("Mark all as read marks every unread notification of the Seller once")
        void markAllAsRead() {
            storeForSeller("A", "Offer", 3);
            storeForSeller("B", "Listings", 2);
            storeForSeller("C", "Reviews", 1).setRead(true);
            Notification others = store(otherSeller.getUsername(), "D", "Offer", null, null, 1);

            assertEquals(2, service.markAllAsRead());
            assertEquals(0, service.getUnreadCount());
            assertEquals(0, service.markAllAsRead(), "Nothing left to mark");
            assertTrue(others.isUnread(), "Other users' notifications are not touched");
        }
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: the admin's reason
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Acceptance criterion: the admin's reason of a Listing Review notification")
    class AdminReason {

        private Notification listingReview(String message) {
            return new Notification(seller.getUsername(), "Listing Rejected", message, "Listings", "L1", "LISTING");
        }

        @Test
        @DisplayName("The reason is read from the message, trimmed, and may span several lines")
        void reasonFromMessage() {
            assertEquals("Blurry photos", SellerNotificationService.adminReasonOf(
                    listingReview("Your listing 'Desk' was rejected. Reason:   Blurry photos  ")));
            assertEquals("Line one\nLine two", SellerNotificationService.adminReasonOf(
                    listingReview("Your listing 'Desk' was rejected. Reason: Line one\nLine two")));
        }

        @Test
        @DisplayName("Only the first 'Reason:' starts the reason, so a reason quoting the word is kept whole")
        void reasonQuotingTheWord() {
            assertEquals("Wrong category. Reason: it is a book", SellerNotificationService.adminReasonOf(
                    listingReview("Your listing 'Desk' was rejected. Reason: Wrong category. Reason: it is a book")));
        }

        @Test
        @DisplayName("No reason is given for a message without one, or with an empty one")
        void noReason() {
            assertNull(SellerNotificationService.adminReasonOf(listingReview("Your listing 'Desk' is now live.")));
            assertNull(SellerNotificationService.adminReasonOf(listingReview("Your listing was rejected. Reason:   ")));
            assertNull(SellerNotificationService.adminReasonOf(listingReview(null)));
        }

        @Test
        @DisplayName("Notifications of other categories have no admin's reason")
        void otherCategories() {
            Notification offer = new Notification(seller.getUsername(), "Offer Declined",
                    "It was declined. Reason: too low", "Offer");
            assertNull(SellerNotificationService.adminReasonOf(offer));
            assertThrows(NullPointerException.class, () -> SellerNotificationService.adminReasonOf(null));
        }
    }

    // ------------------------------------------------------------------
    // Concurrency: the board stays consistent
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrency: the board stays consistent")
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
        @DisplayName("10 buyers send offers while the Seller reads the board: every read is complete, none is lost")
        void offersWhileReading() throws Exception {
            Listing listing = liveSellListing();
            List<Buyer> buyers = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                buyers.add(storeBuyer("s07_concurrent" + i, "Buyer " + i));
            }
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (Buyer buyer : buyers) {
                tasks.add(() -> {
                    priceOffer(listing, buyer, 100.0);
                    return -1;
                });
                tasks.add(() -> service.getMyNotifications().size());
            }

            List<Integer> results = runTogether(tasks);

            results.stream().filter(size -> size >= 0)
                    .forEach(size -> assertTrue(size <= THREADS, "A read never sees more offers than were sent"));
            assertEquals(THREADS, service.getMyNotifications().size());
            assertEquals(THREADS, service.getUnreadCategoryCounts().get(SellerNotificationCategory.OFFERS_AND_REQUESTS));
        }

        @Test
        @Timeout(20)
        @DisplayName("10 'Mark all as read' at the same time: each notification is counted exactly once")
        void markAllAtOnce() throws Exception {
            for (int i = 0; i < 25; i++) {
                storeForSeller("Offer " + i, "Offer", i);
            }
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                tasks.add(service::markAllAsRead);
            }

            List<Integer> marked = runTogether(tasks);

            assertEquals(25, marked.stream().mapToInt(Integer::intValue).sum());
            assertEquals(0, service.getUnreadCount());
        }

        @Test
        @Timeout(20)
        @DisplayName("The same notification opened by 10 threads at once: every opening succeeds and it is read once")
        void openSameNotification() throws Exception {
            Review review = reviewThroughBuyerFlow(buyer1, 4);
            Notification n = onlyNotificationTitled("New Review Received");
            int unreadBefore = service.getUnreadCount();
            List<Callable<SellerNotificationResult>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                tasks.add(() -> service.openNotification(n.getId()));
            }

            List<SellerNotificationResult> results = runTogether(tasks);

            results.forEach(result -> {
                assertTrue(result.isSuccess(), result.toString());
                assertSame(review, result.getReview());
            });
            assertTrue(n.isRead());
            assertEquals(unreadBefore - 1, service.getUnreadCount());
        }
    }

    // =====================================================================
    // Basic flow, step 2: timestamps shown on the board (SB1)
    // =====================================================================

    @Nested
    @DisplayName("Timestamps on the board [SB1]")
    class Timestamps {

        @Test
        @DisplayName("The month is written in English even when the computer's locale is French (IntelliJ in fr_FR)")
        void englishMonthWhateverTheDefaultLocale() {
            Notification notification = new Notification("charlie", "T", "M", "Offer");
            notification.setTimestamp(LocalDateTime.of(2026, 11, 3, 14, 30));
            Locale before = Locale.getDefault();
            try {
                Locale.setDefault(Locale.FRANCE);
                assertEquals("03 Nov 2026 14:30", SellerNotificationService.formatTimestamp(notification));
                Locale.setDefault(Locale.CHINA);
                assertEquals("03 Nov 2026 14:30", SellerNotificationService.formatTimestamp(notification));
            } finally {
                Locale.setDefault(before);
            }
        }

        @Test
        @DisplayName("A notification without timestamp shows nothing")
        void noTimestamp() {
            Notification notification = new Notification("charlie", "T", "M", "Offer");
            notification.setTimestamp(null);
            assertEquals("", SellerNotificationService.formatTimestamp(notification));
        }

        @Test
        @DisplayName("A null notification is refused")
        void nullNotification() {
            assertThrows(NullPointerException.class, () -> SellerNotificationService.formatTimestamp(null));
        }
    }
}
