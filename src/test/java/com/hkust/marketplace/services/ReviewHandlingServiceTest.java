package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.ActOnListingResult;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.AdminNotificationCategory;
import com.hkust.marketplace.models.AdminNotificationDetailResult;
import com.hkust.marketplace.models.AdminRatingOperationResult;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.DealOperationResult;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationTargetResult;
import com.hkust.marketplace.models.NotificationTargetType;
import com.hkust.marketplace.models.OfferManagementResult;
import com.hkust.marketplace.models.RateSellerResult;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.ReviewFlagReason;
import com.hkust.marketplace.models.ReviewHandlingResult;
import com.hkust.marketplace.models.ReviewHandlingStatus;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SellerRatingInfo;
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

import java.time.LocalDateTime;
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
 * Tests of use case 2.9 "Handle Reviews" (US-S09), grouped by the flows of its specification.
 * Acceptance criteria of the Sprint 2 backlog: a flagged review is notified to the admin, and
 * only one reply is allowed per review.
 *
 * <p>Besides reviews stored directly, some reviews are written through the real buyer flow
 * (offer, acceptance, completed deal and rating, use cases 1.5, 2.5, 1.6 and 1.10), and the
 * admin's moderation goes through use cases 3.6 and 3.8, so these tests also check that the
 * Seller, Buyer and Admin portals work together.</p>
 */
@DisplayName("ReviewHandlingService: US-S09 Handle Reviews")
class ReviewHandlingServiceTest {

    private static final DummyDataService DATA = DummyDataService.getInstance();
    /** The tests never log in with a password, so the stored hash is never checked. */
    private static final String UNUSED_HASH = "not-checked-by-these-tests";
    private static final String ADMIN = DummyDataService.SEEDED_ADMIN_USERNAME;
    private static final String REPLY_TITLE = "Seller Replied to Review";

    private final ReviewHandlingService service = new ReviewHandlingService(DATA);

    private Seller seller;
    private Seller otherSeller;
    private Buyer buyer1;
    private Buyer buyer2;

    /**
     * Starts every test from the seeded data, with a fresh Seller without reviews, another
     * Seller, two buyers, and the Seller logged in.
     */
    @BeforeEach
    void setUp() {
        DATA.resetData();
        SessionManager.logout();
        seller = storeSeller("s09_seller", "Sam Seller");
        otherSeller = storeSeller("s09_other", "Olive Other");
        buyer1 = storeBuyer("s09_buyer1", "Alice Buyer");
        buyer2 = storeBuyer("s09_buyer2", "Bob Buyer");
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

    /** Stores a review written by a buyer about a Seller, dated a given number of hours ago. */
    private static Review storeReview(Seller about, Buyer by, int rating, String comment, long hoursAgo) {
        Review review = new Review(about.getUsername(), by.getUsername(), null, null, "Desk Chair", rating, comment);
        review.setDate(LocalDateTime.now().minusHours(hoursAgo));
        DATA.addReview(review);
        return review;
    }

    /** Stores a 4-star review of the logged-in Seller by the first buyer. */
    private Review storeReview() {
        return storeReview(seller, buyer1, 4, "Good deal, a bit late to the meet-up.", 1);
    }

    /**
     * Writes a review through the real buyer flow: the buyer makes an offer on a Live listing,
     * the Seller accepts it, the buyer marks the deal complete and rates the Seller.
     */
    private Review reviewThroughBuyerFlow(Buyer buyer, int rating, String comment) {
        Listing listing = new Listing();
        listing.setTitle("Graphing Calculator");
        listing.setDescription("Test listing");
        listing.setCategory("Electronics");
        listing.setCondition("Used");
        listing.setMeetupLocation("Library");
        listing.setType(ListingType.SELL);
        listing.setPrice(300.0);
        listing.setSellerUsername(seller.getUsername());
        listing.setSellerFullName(seller.getFullName());
        listing.setStatus(ListingStatus.LIVE);
        DATA.addListing(listing);

        ActOnListingResult offer = SessionManager.withSession(buyer, () ->
                DATA.submitPriceOffer(listing.getId(), buyer.getUsername(), 280.0));
        assertTrue(offer.isSuccess(), offer.getMessage());
        OfferManagementResult accepted = new OfferManagementService(DATA).acceptOffer(offer.getOffer().getId());
        assertTrue(accepted.isSuccess(), accepted.getMessage());
        DealOperationResult completed = SessionManager.withSession(buyer, () ->
                DATA.markDealComplete(accepted.getDeal().getId(), buyer.getUsername()));
        assertTrue(completed.isSuccess(), completed.getMessage());
        RateSellerResult rated = SessionManager.withSession(buyer, () -> DATA.getBuyerReviewService()
                .submitRating(accepted.getDeal().getId(), buyer.getUsername(), rating, comment));
        assertTrue(rated.isSuccess(), rated.getMessage());
        return rated.getReview();
    }

    /** Notifications telling a buyer that the Seller replied to a review. */
    private static List<Notification> replyNotifications(Buyer buyer, Review review) {
        return DATA.getNotificationsFor(buyer.getUsername()).stream()
                .filter(n -> REPLY_TITLE.equals(n.getTitle()) && review.getId().equals(n.getRelatedItemId()))
                .toList();
    }

    /** Notifications telling the admin that a review was flagged by a Seller. */
    private static List<Notification> flagNotifications(Review review) {
        return DATA.getNotificationsFor(ADMIN).stream()
                .filter(n -> n.getTitle() != null
                        && n.getTitle().startsWith(ReviewHandlingService.FLAG_NOTIFICATION_TITLE_PREFIX)
                        && review.getId().equals(n.getRelatedItemId()))
                .toList();
    }

    private static Notification onlyFlagNotification(Review review) {
        List<Notification> list = flagNotifications(review);
        assertEquals(1, list.size(), "Exactly one flag notification is expected for the admin");
        return list.get(0);
    }

    private static void assertFailure(ReviewHandlingResult result, ReviewHandlingStatus expected) {
        assertFalse(result.isSuccess(), "Expected a failure but got: " + result);
        assertEquals(expected, result.getStatus(), result.getMessage());
        assertFalse(result.getMessage().isBlank(), "A failure must explain itself to the Seller");
    }

    private static void assertUnchanged(Review review) {
        assertFalse(review.hasReply(), "The review must not have a reply");
        assertFalse(review.isFlagged(), "The review must not be flagged");
        assertNull(review.getFlagReason(), "The review must not have a flag reason");
    }

    // ------------------------------------------------------------------
    // Use case 2.2 special requirement: Seller session checked by the service
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Seller session is checked by every action (role-based access control)")
    class SessionEnforcement {

        private List<Executable> everyAction(String reviewId) {
            return List.of(
                    service::getMyReviews,
                    service::getMyRating,
                    () -> service.checkCanReply(reviewId),
                    () -> service.replyToReview(reviewId, "Thanks!"),
                    () -> service.checkCanFlag(reviewId),
                    () -> service.flagReview(reviewId, ReviewFlagReason.SPAM, null));
        }

        private void assertEveryActionRefused(Review review) {
            for (Executable action : everyAction(review.getId())) {
                assertThrows(SecurityException.class, action);
            }
            assertUnchanged(review);
            assertTrue(replyNotifications(buyer1, review).isEmpty());
            assertTrue(flagNotifications(review).isEmpty());
        }

        @Test
        @DisplayName("Without any session, every action throws SecurityException")
        void noSession() {
            Review review = storeReview();
            SessionManager.logout();
            assertEveryActionRefused(review);
        }

        @Test
        @DisplayName("A Buyer session cannot call the Seller actions, even the buyer who wrote the review")
        void buyerSession() {
            Review review = storeReview();
            SessionManager.login(buyer1);
            assertEveryActionRefused(review);
        }

        @Test
        @DisplayName("An Admin session cannot call the Seller actions")
        void adminSession() {
            Review review = storeReview();
            SessionManager.login(new Admin("s09_admin", "Admin", UNUSED_HASH));
            assertEveryActionRefused(review);
        }

        @ParameterizedTest(name = "Seller account {0}")
        @EnumSource(value = AccountStatus.class, names = {"DEACTIVATED", "DELETED"})
        @DisplayName("A Seller deactivated or deleted during the session is refused")
        void inactiveSeller(AccountStatus status) {
            Review review = storeReview();
            seller.setAccountStatus(status);
            assertEveryActionRefused(review);
        }
    }

    // ------------------------------------------------------------------
    // Basic flow step 2 and alternative flow A1
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Basic flow step 2: the Seller's reviews and average rating")
    class ReviewList {

        @Test
        @DisplayName("A1: a Seller without reviews has an empty list and 'No ratings yet'")
        void noReviews() {
            assertTrue(service.getMyReviews().isEmpty());

            SellerRatingInfo rating = service.getMyRating();
            assertFalse(rating.hasRatings());
            assertEquals(0, rating.getRatingCount());
            assertEquals(SellerRatingInfo.NO_RATINGS_TEXT, rating.getDisplayText());
        }

        @Test
        @DisplayName("Lists only the logged-in Seller's reviews, newest first")
        void ownReviewsNewestFirst() {
            Review old = storeReview(seller, buyer1, 3, "Old", 48);
            Review newest = storeReview(seller, buyer2, 5, "Newest", 1);
            Review middle = storeReview(seller, buyer1, 4, "Middle", 5);
            storeReview(otherSeller, buyer1, 1, "About someone else", 0);

            assertEquals(List.of(newest, middle, old), service.getMyReviews());
        }

        @Test
        @DisplayName("Each review keeps the buyer, the listing, the stars, the comment, the date and the reply")
        void reviewDetails() {
            Review review = storeReview();
            review.setReply("Thanks for your patience!");

            Review shown = service.getMyReviews().get(0);

            assertEquals(buyer1.getUsername(), shown.getBuyerUsername());
            assertEquals("Desk Chair", shown.getListingTitle());
            assertEquals(4, shown.getRating());
            assertEquals("Good deal, a bit late to the meet-up.", shown.getComment());
            assertNotNull(shown.getDate());
            assertEquals("Thanks for your patience!", shown.getReply());
        }

        @Test
        @DisplayName("A review without date is listed last")
        void undatedReviewLast() {
            Review undated = storeReview(seller, buyer1, 4, "No date", 0);
            undated.setDate(null);
            Review dated = storeReview(seller, buyer2, 5, "Dated", 10);

            assertEquals(List.of(dated, undated), service.getMyReviews());
        }

        @Test
        @DisplayName("The average rating is computed over the visible reviews and rounded to one decimal")
        void averageRating() {
            storeReview(seller, buyer1, 5, "Great", 3);
            storeReview(seller, buyer2, 4, "Good", 2);
            storeReview(seller, buyer1, 4, "Good again", 1);
            storeReview(otherSeller, buyer1, 1, "Not counted", 1);

            SellerRatingInfo rating = service.getMyRating();

            assertTrue(rating.hasRatings());
            assertEquals(4.3, rating.getAverageRating(), 1e-9);
            assertEquals(3, rating.getRatingCount());
            assertEquals(seller.getUsername(), rating.getSellerUsername());
        }

        @Test
        @DisplayName("Class diagram rule 4: a review hidden by the admin is neither shown nor counted")
        void hiddenReviewExcluded() {
            Review visible = storeReview(seller, buyer1, 5, "Visible", 2);
            Review hidden = storeReview(seller, buyer2, 1, "Rude", 1);
            hidden.setHidden(true);

            assertEquals(List.of(visible), service.getMyReviews());
            SellerRatingInfo rating = service.getMyRating();
            assertEquals(5.0, rating.getAverageRating(), 1e-9);
            assertEquals(1, rating.getRatingCount());
        }

        @Test
        @DisplayName("When every review is hidden, the Seller has 'No ratings yet'")
        void everyReviewHidden() {
            storeReview().setHidden(true);

            assertTrue(service.getMyReviews().isEmpty());
            assertFalse(service.getMyRating().hasRatings());
        }

        @Test
        @DisplayName("A review with an invalid star value is shown but not counted in the average")
        void invalidStarsNotCounted() {
            storeReview(seller, buyer1, 4, "Valid", 2);
            storeReview(seller, buyer2, 0, "Invalid stars", 1);

            assertEquals(2, service.getMyReviews().size());
            assertEquals(4.0, service.getMyRating().getAverageRating(), 1e-9);
            assertEquals(1, service.getMyRating().getRatingCount());
        }

        @Test
        @DisplayName("The list cannot be changed by the screen")
        void unmodifiableList() {
            storeReview();
            List<Review> reviews = service.getMyReviews();
            assertThrows(UnsupportedOperationException.class, () -> reviews.add(new Review()));
        }
    }

    // ------------------------------------------------------------------
    // Subflow S1: Reply To Review
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Subflow S1: Reply To Review")
    class ReplyToReview {

        @Test
        @DisplayName("Begin Reply: a review without reply can be answered, and nothing changes")
        void checkCanReply() {
            Review review = storeReview();

            ReviewHandlingResult result = service.checkCanReply(review.getId());

            assertTrue(result.isSuccess(), result.getMessage());
            assertSame(review, result.getReview());
            assertUnchanged(review);
        }

        @Test
        @DisplayName("The reply is saved trimmed under the review and the Seller is told it was posted")
        void replySaved() {
            Review review = storeReview();

            ReviewHandlingResult result = service.replyToReview(review.getId(), "  Sorry for the delay, thanks!  \n");

            assertTrue(result.isSuccess(), result.getMessage());
            assertSame(review, result.getReview());
            assertEquals("Sorry for the delay, thanks!", review.getReply());
            assertTrue(result.getMessage().contains(buyer1.getUsername()));
            assertFalse(review.isFlagged(), "Replying does not flag the review");
        }

        @Test
        @DisplayName("Step 3: the buyer who wrote the review is notified, with a link to the review")
        void buyerNotified() {
            Review review = storeReview();

            service.replyToReview(review.getId(), "Thanks for buying!");

            List<Notification> notifications = replyNotifications(buyer1, review);
            assertEquals(1, notifications.size());
            Notification notification = notifications.get(0);
            assertEquals(NotificationCategory.REVIEWS.getDisplayName(), notification.getCategory());
            assertEquals(NotificationTargetType.REVIEW.name(), notification.getRelatedItemType());
            assertTrue(notification.getMessage().contains("Thanks for buying!"));
            assertTrue(notification.isUnread());
            assertTrue(replyNotifications(buyer2, review).isEmpty(), "Other buyers are not notified");
        }

        @Test
        @DisplayName("The buyer's notification opens the review with the reply (use case 1.8)")
        void notificationOpensReview() {
            Review review = storeReview();
            service.replyToReview(review.getId(), "Thanks for buying!");
            Notification notification = replyNotifications(buyer1, review).get(0);

            NotificationTargetResult target = SessionManager.withSession(buyer1, () ->
                    DATA.getBuyerNotificationService()
                            .resolveNotificationTarget(buyer1.getUsername(), notification.getId()));

            assertTrue(target.isSuccess(), target.getMessage());
            assertEquals(NotificationTargetType.REVIEW, target.getTargetType());
            assertSame(review, target.getTargetObject());
        }

        @Test
        @DisplayName("Buyers see the reply under the review (use case 1.4)")
        void replyVisibleToBuyers() {
            Review review = storeReview();
            service.replyToReview(review.getId(), "Thanks for buying!");

            Review seenByBuyers = DATA.getBuyerReviewService().getReviewsForSeller(seller.getUsername()).get(0);

            assertSame(review, seenByBuyers);
            assertEquals("Thanks for buying!", seenByBuyers.getReply());
        }

        @Test
        @DisplayName("A flagged review can still be answered")
        void replyToFlaggedReview() {
            Review review = storeReview();
            assertTrue(service.flagReview(review.getId(), ReviewFlagReason.FALSE_INFORMATION, null).isSuccess());

            ReviewHandlingResult result = service.replyToReview(review.getId(), "I was on time, see our chat.");

            assertTrue(result.isSuccess(), result.getMessage());
            assertTrue(review.isFlagged(), "The flag is kept");
        }

        @Test
        @DisplayName("A reply of exactly the maximum length is accepted")
        void maximumLength() {
            Review review = storeReview();
            String reply = "a".repeat(ReviewHandlingService.MAX_REPLY_LENGTH);

            assertTrue(service.replyToReview(review.getId(), reply).isSuccess());
            assertEquals(reply, review.getReply());
        }

        @Test
        @DisplayName("The length of a reply is counted in characters, not in UTF-16 units")
        void lengthInCharacters() {
            Review review = storeReview();
            String reply = "🙏".repeat(ReviewHandlingService.MAX_REPLY_LENGTH);

            assertTrue(service.replyToReview(review.getId(), reply).isSuccess());
        }

        @Test
        @DisplayName("The review identifier is trimmed")
        void identifierTrimmed() {
            Review review = storeReview();

            assertTrue(service.replyToReview("  " + review.getId() + " ", "Thanks!").isSuccess());
            assertEquals("Thanks!", review.getReply());
        }
    }

    @Nested
    @DisplayName("Reply alternative flows")
    class ReplyAlternativeFlows {

        @ParameterizedTest(name = "reply \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\n\t  "})
        @DisplayName("A2: an empty reply is refused, nothing is saved and nobody is notified")
        void emptyReply(String reply) {
            Review review = storeReview();

            ReviewHandlingResult result = service.replyToReview(review.getId(), reply);

            assertFailure(result, ReviewHandlingStatus.EMPTY_REPLY);
            assertEquals(ReviewHandlingService.EMPTY_REPLY, result.getMessage());
            assertSame(review, result.getReview(), "The dialog stays on the same review");
            assertUnchanged(review);
            assertTrue(replyNotifications(buyer1, review).isEmpty());
        }

        @Test
        @DisplayName("A reply that is too long is refused and nothing is saved")
        void replyTooLong() {
            Review review = storeReview();

            ReviewHandlingResult result = service.replyToReview(review.getId(),
                    "a".repeat(ReviewHandlingService.MAX_REPLY_LENGTH + 1));

            assertFailure(result, ReviewHandlingStatus.REPLY_TOO_LONG);
            assertUnchanged(review);
            assertTrue(replyNotifications(buyer1, review).isEmpty());
        }

        @Test
        @DisplayName("A3: a second reply is refused, the first one is kept and the buyer is notified once")
        void secondReply() {
            Review review = storeReview();
            assertTrue(service.replyToReview(review.getId(), "First reply").isSuccess());

            ReviewHandlingResult result = service.replyToReview(review.getId(), "Second reply");

            assertFailure(result, ReviewHandlingStatus.ALREADY_REPLIED);
            assertEquals(ReviewHandlingService.ALREADY_REPLIED, result.getMessage());
            assertEquals("First reply", review.getReply());
            assertEquals(1, replyNotifications(buyer1, review).size());
        }

        @Test
        @DisplayName("A3 at Begin Reply: an answered review cannot be answered again")
        void checkCanReplyAnswered() {
            Review review = storeReview();
            review.setReply("Already answered");

            ReviewHandlingResult result = service.checkCanReply(review.getId());

            assertFailure(result, ReviewHandlingStatus.ALREADY_REPLIED);
            assertSame(review, result.getReview());
        }

        @Test
        @DisplayName("A3 is reported before an empty reply")
        void alreadyRepliedBeforeEmpty() {
            Review review = storeReview();
            review.setReply("Already answered");

            assertFailure(service.replyToReview(review.getId(), "  "), ReviewHandlingStatus.ALREADY_REPLIED);
        }

        @Test
        @DisplayName("A Seller cannot answer a review written about another Seller")
        void otherSellersReview() {
            Review review = storeReview(otherSeller, buyer1, 2, "Not about you", 1);

            ReviewHandlingResult check = service.checkCanReply(review.getId());
            ReviewHandlingResult reply = service.replyToReview(review.getId(), "Hello");

            assertFailure(check, ReviewHandlingStatus.UNAUTHORIZED);
            assertFailure(reply, ReviewHandlingStatus.UNAUTHORIZED);
            assertNull(reply.getReview(), "Another Seller's review is not revealed");
            assertUnchanged(review);
        }

        @Test
        @DisplayName("A review hidden by the admin can no longer be answered, and is not revealed")
        void hiddenReview() {
            Review review = storeReview();
            review.setHidden(true);

            ReviewHandlingResult check = service.checkCanReply(review.getId());
            ReviewHandlingResult reply = service.replyToReview(review.getId(), "Hello");

            assertFailure(check, ReviewHandlingStatus.REVIEW_HIDDEN);
            assertFailure(reply, ReviewHandlingStatus.REVIEW_HIDDEN);
            assertNull(reply.getReview());
            assertUnchanged(review);
        }

        @ParameterizedTest(name = "id \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "R-does-not-exist"})
        @DisplayName("An empty or unknown review identifier is reported as not found")
        void unknownReview(String reviewId) {
            assertFailure(service.checkCanReply(reviewId), ReviewHandlingStatus.REVIEW_NOT_FOUND);
            assertFailure(service.replyToReview(reviewId, "Hello"), ReviewHandlingStatus.REVIEW_NOT_FOUND);
        }
    }

    // ------------------------------------------------------------------
    // Subflow S2: Flag Review
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Subflow S2: Flag Review")
    class FlagReview {

        @Test
        @DisplayName("Begin Flag: a review not flagged can be flagged, and nothing changes")
        void checkCanFlag() {
            Review review = storeReview();

            ReviewHandlingResult result = service.checkCanFlag(review.getId());

            assertTrue(result.isSuccess(), result.getMessage());
            assertSame(review, result.getReview());
            assertUnchanged(review);
        }

        @Test
        @DisplayName("Step 3: the review is marked as flagged with the reason, and the Seller is told")
        void reviewFlagged() {
            Review review = storeReview();

            ReviewHandlingResult result = service.flagReview(review.getId(), ReviewFlagReason.OFFENSIVE_LANGUAGE, null);

            assertTrue(result.isSuccess(), result.getMessage());
            assertSame(review, result.getReview());
            assertTrue(review.isFlagged());
            assertEquals("Offensive language", review.getFlagReason());
            assertFalse(review.isHidden(), "Only the admin can hide a review");
            assertTrue(result.getMessage().contains("Offensive language"));
        }

        @Test
        @DisplayName("The Seller's details are stored after the reason")
        void detailsStored() {
            Review review = storeReview();

            service.flagReview(review.getId(), ReviewFlagReason.PERSONAL_INFORMATION, "  Gives my room number. ");

            assertEquals("Shares personal information: Gives my room number.", review.getFlagReason());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ReviewFlagReason.class)
        @DisplayName("Every reason can be chosen")
        void everyReason(ReviewFlagReason reason) {
            Review review = storeReview();

            ReviewHandlingResult result = service.flagReview(review.getId(), reason, "Details");

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(reason.describe("Details"), review.getFlagReason());
        }

        @Test
        @DisplayName("Backlog criterion: the admin is notified, with the reason and a link to the review")
        void adminNotified() {
            Review review = storeReview();

            service.flagReview(review.getId(), ReviewFlagReason.NOT_RELATED_TO_DEAL, "Talks about another item");

            Notification notification = onlyFlagNotification(review);
            assertEquals(ADMIN, notification.getUsername());
            assertEquals(ReviewHandlingService.FLAG_NOTIFICATION_TITLE_PREFIX + seller.getUsername(),
                    notification.getTitle());
            assertEquals(AdminNotificationCategory.FLAGGED_REVIEWS, AdminNotificationCategory.fromRaw(notification.getCategory()));
            assertEquals(NotificationTargetType.REVIEW.name(), notification.getRelatedItemType());
            assertTrue(notification.getMessage().contains("Not related to the deal: Talks about another item"));
            assertTrue(notification.getMessage().contains(buyer1.getUsername()));
            assertTrue(notification.isUnread());
        }

        @Test
        @DisplayName("Step 3: the flagged review stays visible to the Seller and to buyers, and still counts")
        void flaggedReviewStaysVisible() {
            Review review = storeReview();

            service.flagReview(review.getId(), ReviewFlagReason.SPAM, null);

            assertEquals(List.of(review), service.getMyReviews());
            assertEquals(1, service.getMyRating().getRatingCount());
            assertTrue(DATA.getBuyerReviewService().getReviewsForSeller(seller.getUsername()).contains(review));
        }

        @Test
        @DisplayName("An answered review can still be flagged")
        void flagAnsweredReview() {
            Review review = storeReview();
            review.setReply("Thanks!");

            assertTrue(service.flagReview(review.getId(), ReviewFlagReason.SPAM, null).isSuccess());
            assertEquals("Thanks!", review.getReply());
        }

        @Test
        @DisplayName("Details of exactly the maximum length are accepted")
        void maximumDetails() {
            Review review = storeReview();
            String details = "d".repeat(ReviewHandlingService.MAX_FLAG_DETAILS_LENGTH);

            assertTrue(service.flagReview(review.getId(), ReviewFlagReason.OTHER, details).isSuccess());
        }

        @Test
        @DisplayName("The admin notification names the listing, even when the review only stores its identifier")
        void notificationNamesListing() {
            Listing listing = new Listing();
            listing.setTitle("Desk Lamp");
            listing.setSellerUsername(seller.getUsername());
            DATA.addListing(listing);
            Review review = new Review(seller.getUsername(), buyer1.getUsername(), null, listing.getId(), null, 2, "Meh");
            DATA.addReview(review);

            service.flagReview(review.getId(), ReviewFlagReason.SPAM, null);

            assertTrue(onlyFlagNotification(review).getMessage().contains("'Desk Lamp'"));
        }

        @Test
        @DisplayName("A review whose listing is unknown is described as 'a deal' to the admin")
        void notificationWithoutListing() {
            Review review = new Review(seller.getUsername(), buyer1.getUsername(), null, "L-unknown", " ", 2, "Meh");
            DATA.addReview(review);
            Review noListing = new Review(seller.getUsername(), buyer2.getUsername(), null, null, null, 3, "Hmm");
            DATA.addReview(noListing);

            service.flagReview(review.getId(), ReviewFlagReason.SPAM, null);
            service.flagReview(noListing.getId(), ReviewFlagReason.SPAM, null);

            assertTrue(onlyFlagNotification(review).getMessage().contains("'a deal'"));
            assertTrue(onlyFlagNotification(noListing).getMessage().contains("'a deal'"));
        }
    }

    // ------------------------------------------------------------------
    // Construction and validation helpers used by the dialogs
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Construction and validation helpers")
    class Helpers {

        @Test
        @DisplayName("The service works on the application's data store by default, and needs one")
        void construction() {
            assertTrue(new ReviewHandlingService().getMyReviews().isEmpty());
            assertThrows(NullPointerException.class, () -> new ReviewHandlingService(null));
        }

        @Test
        @DisplayName("lengthOf() trims the text and counts characters as the Seller sees them")
        void lengthOf() {
            assertEquals(0, ReviewHandlingService.lengthOf(null));
            assertEquals(0, ReviewHandlingService.lengthOf("   "));
            assertEquals(3, ReviewHandlingService.lengthOf("  abc \n"));
            assertEquals(2, ReviewHandlingService.lengthOf("👍🙏"));
        }

        @Test
        @DisplayName("flagReasonError() needs no session and reports every problem")
        void flagReasonError() {
            SessionManager.logout();
            assertEquals(ReviewHandlingService.FLAG_REASON_REQUIRED, ReviewHandlingService.flagReasonError(null, "x"));
            assertEquals(ReviewHandlingService.FLAG_DETAILS_REQUIRED,
                    ReviewHandlingService.flagReasonError(ReviewFlagReason.OTHER, " "));
            assertNull(ReviewHandlingService.flagReasonError(ReviewFlagReason.SPAM, null));
            assertNull(ReviewHandlingService.flagReasonError(ReviewFlagReason.OTHER, "Explained"));
        }
    }

    @Nested
    @DisplayName("Flag alternative flows")
    class FlagAlternativeFlows {

        @Test
        @DisplayName("No reason chosen: the review is not flagged")
        void noReason() {
            Review review = storeReview();

            ReviewHandlingResult result = service.flagReview(review.getId(), null, "Some details");

            assertFailure(result, ReviewHandlingStatus.INVALID_FLAG_REASON);
            assertEquals(ReviewHandlingService.FLAG_REASON_REQUIRED, result.getMessage());
            assertUnchanged(review);
            assertTrue(flagNotifications(review).isEmpty());
        }

        @ParameterizedTest(name = "details \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("'Other' without details: the review is not flagged")
        void otherWithoutDetails(String details) {
            Review review = storeReview();

            ReviewHandlingResult result = service.flagReview(review.getId(), ReviewFlagReason.OTHER, details);

            assertFailure(result, ReviewHandlingStatus.INVALID_FLAG_REASON);
            assertEquals(ReviewHandlingService.FLAG_DETAILS_REQUIRED, result.getMessage());
            assertUnchanged(review);
        }

        @Test
        @DisplayName("Details that are too long: the review is not flagged")
        void detailsTooLong() {
            Review review = storeReview();

            ReviewHandlingResult result = service.flagReview(review.getId(), ReviewFlagReason.SPAM,
                    "d".repeat(ReviewHandlingService.MAX_FLAG_DETAILS_LENGTH + 1));

            assertFailure(result, ReviewHandlingStatus.INVALID_FLAG_REASON);
            assertEquals(ReviewHandlingService.FLAG_DETAILS_TOO_LONG, result.getMessage());
            assertUnchanged(review);
        }

        @Test
        @DisplayName("A4: a review already flagged cannot be flagged again, the reason is kept and the admin notified once")
        void alreadyFlagged() {
            Review review = storeReview();
            assertTrue(service.flagReview(review.getId(), ReviewFlagReason.SPAM, null).isSuccess());

            ReviewHandlingResult result = service.flagReview(review.getId(), ReviewFlagReason.OFFENSIVE_LANGUAGE, null);

            assertFailure(result, ReviewHandlingStatus.ALREADY_FLAGGED);
            assertEquals(ReviewHandlingService.ALREADY_FLAGGED, result.getMessage());
            assertEquals("Spam or advertising", review.getFlagReason());
            onlyFlagNotification(review);
        }

        @Test
        @DisplayName("A4 at Begin Flag: a flagged review is awaiting the admin's decision")
        void checkCanFlagFlagged() {
            Review review = storeReview();
            review.setFlagged(true);

            ReviewHandlingResult result = service.checkCanFlag(review.getId());

            assertFailure(result, ReviewHandlingStatus.ALREADY_FLAGGED);
            assertSame(review, result.getReview());
        }

        @Test
        @DisplayName("A4 is reported before a missing reason")
        void alreadyFlaggedBeforeReason() {
            Review review = storeReview();
            review.setFlagged(true);

            assertFailure(service.flagReview(review.getId(), null, null), ReviewHandlingStatus.ALREADY_FLAGGED);
        }

        @Test
        @DisplayName("A Seller cannot flag a review written about another Seller")
        void otherSellersReview() {
            Review review = storeReview(otherSeller, buyer1, 1, "Not about you", 1);

            assertFailure(service.checkCanFlag(review.getId()), ReviewHandlingStatus.UNAUTHORIZED);
            ReviewHandlingResult result = service.flagReview(review.getId(), ReviewFlagReason.SPAM, null);

            assertFailure(result, ReviewHandlingStatus.UNAUTHORIZED);
            assertNull(result.getReview());
            assertUnchanged(review);
            assertTrue(flagNotifications(review).isEmpty());
        }

        @Test
        @DisplayName("A review hidden by the admin can no longer be flagged")
        void hiddenReview() {
            Review review = storeReview();
            review.setHidden(true);

            assertFailure(service.checkCanFlag(review.getId()), ReviewHandlingStatus.REVIEW_HIDDEN);
            assertFailure(service.flagReview(review.getId(), ReviewFlagReason.SPAM, null), ReviewHandlingStatus.REVIEW_HIDDEN);
            assertUnchanged(review);
        }

        @ParameterizedTest(name = "id \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "R-does-not-exist"})
        @DisplayName("An empty or unknown review identifier is reported as not found")
        void unknownReview(String reviewId) {
            assertFailure(service.checkCanFlag(reviewId), ReviewHandlingStatus.REVIEW_NOT_FOUND);
            assertFailure(service.flagReview(reviewId, ReviewFlagReason.SPAM, null), ReviewHandlingStatus.REVIEW_NOT_FOUND);
        }
    }

    // ------------------------------------------------------------------
    // Integration with the Buyer and Admin portals
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Integration with the buyer's rating (use case 1.10)")
    class BuyerIntegration {

        @Test
        @DisplayName("A review written after a real completed deal is shown, answered, and the reply reaches the buyer")
        void endToEnd() {
            Review review = reviewThroughBuyerFlow(buyer1, 5, "Exactly as described, thank you!");

            List<Review> reviews = service.getMyReviews();
            assertEquals(List.of(review), reviews);
            assertEquals("Graphing Calculator", review.getListingTitle());
            assertNotNull(review.getDealId(), "The review is linked to its deal (class diagram: Deal 1 -> 0..1 Review)");
            assertEquals(5.0, service.getMyRating().getAverageRating(), 1e-9);

            assertTrue(service.replyToReview(review.getId(), "Enjoy the calculator!").isSuccess());

            assertEquals(1, replyNotifications(buyer1, review).size());
            Review seenByBuyer = DATA.getBuyerReviewService().getReviewsByBuyer(buyer1.getUsername()).get(0);
            assertEquals("Enjoy the calculator!", seenByBuyer.getReply());
        }
    }

    @Nested
    @DisplayName("Integration with the admin's moderation (use cases 3.6 and 3.8)")
    class AdminIntegration {

        @Test
        @DisplayName("The flagged review comes before the reviews not flagged on the admin's Seller Ratings screen")
        void flaggedFirstForAdmin() {
            Review older = storeReview(seller, buyer1, 2, "Older", 10);
            Review newer = storeReview(seller, buyer2, 5, "Newer, not flagged", 1);
            List<Review> before = SessionManager.withSession(DATA.findUser(ADMIN), () -> DATA.getAdminRatingService().getOrderedReviews());
            assertTrue(before.indexOf(newer) < before.indexOf(older), "Newest first while nothing is flagged");

            service.flagReview(older.getId(), ReviewFlagReason.OFFENSIVE_LANGUAGE, null);

            List<Review> after = SessionManager.withSession(DATA.findUser(ADMIN), () -> DATA.getAdminRatingService().getOrderedReviews());
            assertTrue(after.indexOf(older) < after.indexOf(newer), "Flagged reviews come first");
            assertEquals("Offensive language", after.get(after.indexOf(older)).getFlagReason());
        }

        @Test
        @DisplayName("The admin's notification opens the flagged review on the admin board")
        void notificationOpensReview() {
            Review review = storeReview();
            service.flagReview(review.getId(), ReviewFlagReason.SPAM, null);
            Notification notification = onlyFlagNotification(review);

            AdminNotificationDetailResult detail = SessionManager.withSession(DATA.findUser(ADMIN), () ->
                    DATA.getAdminNotificationService().getNotificationDetail(notification.getId(), ADMIN));

            assertTrue(detail.isSuccess(), detail.getMessage());
            assertSame(review, detail.getReview());
        }

        @Test
        @DisplayName("Once the admin hides the flagged review, the Seller no longer sees, counts or handles it")
        void adminHides() {
            Review kept = storeReview(seller, buyer2, 5, "Kept", 2);
            Review review = storeReview(seller, buyer1, 1, "Rude review", 1);
            service.flagReview(review.getId(), ReviewFlagReason.OFFENSIVE_LANGUAGE, null);

            AdminRatingOperationResult hidden = DATA.hideReviewByAdmin(review.getId(), ADMIN);
            assertTrue(hidden.isSuccess(), hidden.getMessage());

            assertEquals(List.of(kept), service.getMyReviews());
            assertEquals(5.0, service.getMyRating().getAverageRating(), 1e-9);
            assertFailure(service.replyToReview(review.getId(), "Hello"), ReviewHandlingStatus.REVIEW_HIDDEN);
            assertFailure(service.flagReview(review.getId(), ReviewFlagReason.SPAM, null), ReviewHandlingStatus.REVIEW_HIDDEN);
            assertFalse(onlyFlagNotification(review).isUnread(), "The admin's flag notification is handled");
        }

        @Test
        @DisplayName("Once the admin unhides a review, the Seller sees and counts it again")
        void adminUnhides() {
            Review review = storeReview();
            DATA.hideReviewByAdmin(review.getId(), ADMIN);
            assertTrue(service.getMyReviews().isEmpty());

            DATA.unhideReviewByAdmin(review.getId(), ADMIN);

            assertEquals(List.of(review), service.getMyReviews());
            assertTrue(service.replyToReview(review.getId(), "Thanks!").isSuccess());
        }

        @Test
        @DisplayName("Once the admin dismisses the flag, the review stays visible and can be flagged again")
        void adminDismissesFlag() {
            Review review = storeReview();
            service.flagReview(review.getId(), ReviewFlagReason.SPAM, null);

            AdminRatingOperationResult dismissed = DATA.dismissReviewFlagByAdmin(review.getId(), ADMIN);
            assertTrue(dismissed.isSuccess(), dismissed.getMessage());

            assertEquals(List.of(review), service.getMyReviews());
            assertFalse(review.isFlagged());
            assertTrue(service.checkCanFlag(review.getId()).isSuccess());
            assertTrue(service.flagReview(review.getId(), ReviewFlagReason.FALSE_INFORMATION, null).isSuccess());
            assertEquals(2, flagNotifications(review).size(), "The admin is notified of the new flag");
        }
    }

    // ------------------------------------------------------------------
    // Concurrency: one reply and one flag per review
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrency: replies and flags are atomic")
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
        @DisplayName("10 replies sent at the same time on one review: only one is saved and notified")
        void competingReplies() throws Exception {
            Review review = storeReview();
            List<Callable<ReviewHandlingResult>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String reply = "Reply number " + i;
                tasks.add(() -> service.replyToReview(review.getId(), reply));
            }

            List<ReviewHandlingResult> results = runTogether(tasks);

            assertEquals(1, results.stream().filter(ReviewHandlingResult::isSuccess).count());
            results.stream().filter(r -> !r.isSuccess())
                    .forEach(r -> assertEquals(ReviewHandlingStatus.ALREADY_REPLIED, r.getStatus(), r.getMessage()));
            int winner = results.indexOf(results.stream().filter(ReviewHandlingResult::isSuccess).findFirst().orElseThrow());
            assertEquals("Reply number " + winner, review.getReply(), "The saved reply is the winner's");
            assertEquals(1, replyNotifications(buyer1, review).size(), "The buyer is notified exactly once");
        }

        @Test
        @Timeout(20)
        @DisplayName("10 flags sent at the same time on one review: only one is recorded and notified")
        void competingFlags() throws Exception {
            Review review = storeReview();
            ReviewFlagReason[] reasons = ReviewFlagReason.values();
            List<Callable<ReviewHandlingResult>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                ReviewFlagReason reason = reasons[i % reasons.length];
                tasks.add(() -> service.flagReview(review.getId(), reason, "Thread details"));
            }

            List<ReviewHandlingResult> results = runTogether(tasks);

            assertEquals(1, results.stream().filter(ReviewHandlingResult::isSuccess).count());
            results.stream().filter(r -> !r.isSuccess())
                    .forEach(r -> assertEquals(ReviewHandlingStatus.ALREADY_FLAGGED, r.getStatus(), r.getMessage()));
            assertTrue(review.isFlagged());
            onlyFlagNotification(review);
        }

        @Test
        @Timeout(20)
        @DisplayName("Replies and flags at the same time on one review: one of each succeeds")
        void repliesAndFlagsTogether() throws Exception {
            Review review = storeReview();
            List<Callable<ReviewHandlingResult>> tasks = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                if (i % 2 == 0) {
                    tasks.add(() -> service.replyToReview(review.getId(), "Thanks!"));
                } else {
                    tasks.add(() -> service.flagReview(review.getId(), ReviewFlagReason.SPAM, null));
                }
            }

            List<ReviewHandlingResult> results = runTogether(tasks);

            assertEquals(2, results.stream().filter(ReviewHandlingResult::isSuccess).count());
            assertEquals("Thanks!", review.getReply());
            assertTrue(review.isFlagged());
            assertEquals(1, replyNotifications(buyer1, review).size());
            onlyFlagNotification(review);
        }

        @Test
        @Timeout(20)
        @DisplayName("Replies to 10 different reviews at the same time all succeed")
        void differentReviews() throws Exception {
            List<Review> reviews = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                reviews.add(storeReview(seller, storeBuyer("s09_racer" + i, "Racer " + i), 4, "Fine", i));
            }
            List<Callable<ReviewHandlingResult>> tasks = new ArrayList<>();
            for (Review review : reviews) {
                tasks.add(() -> service.replyToReview(review.getId(), "Thanks " + review.getBuyerUsername()));
            }

            List<ReviewHandlingResult> results = runTogether(tasks);

            assertTrue(results.stream().allMatch(ReviewHandlingResult::isSuccess));
            for (Review review : reviews) {
                assertEquals("Thanks " + review.getBuyerUsername(), review.getReply());
            }
        }
    }
}
