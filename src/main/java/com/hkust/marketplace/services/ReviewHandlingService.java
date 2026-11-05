package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AdminNotificationCategory;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationTargetType;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.ReviewFlagReason;
import com.hkust.marketplace.models.ReviewHandlingResult;
import com.hkust.marketplace.models.ReviewHandlingStatus;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SellerRatingInfo;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Business logic of use case 2.9 "Handle Reviews" (US-S09): a Seller reads the reviews and
 * ratings that buyers gave them, replies to a review, or flags an inappropriate review for the
 * admin. Acceptance criteria of the Sprint 2 backlog: a flagged review is notified to the
 * admin, and only one reply is allowed per review.
 *
 * <p><b>Class diagram.</b> Everything is stored in the attributes of {@link Review}: the
 * reply in {@code reply} (0..1, so a review has at most one reply, which cannot be changed),
 * the flag in {@code flagged} and its reason in {@code flagReason}. A review whose
 * {@code hidden} attribute was set by the admin (use case 3.8) is neither shown to the Seller
 * nor counted in the average rating shown to them, and can no longer be answered or flagged.
 * A flagged review stays visible, and counted, until the admin decides.</p>
 *
 * <p><b>Access control.</b> Every public instance method first calls
 * {@link SessionManager#requireSeller()}, so it can only be used during an active Seller
 * session (use case 2.2 special requirement). A Seller can only handle the reviews written
 * about them.</p>
 *
 * <p><b>Atomicity.</b> Replying and flagging run while holding the lock of the data store
 * ({@link DummyDataService}), so that the check (alternative flows A3 and A4) and the change
 * happen as one unit: when two replies, or two flags, on the same review are sent at the same
 * time, only the first one succeeds, and only one notification is sent. Inside the lock, only
 * the data store is called. The buyer is notified of a reply after the lock is released,
 * through {@link BuyerNotificationService#notifyReviewReply(Review)}, whose own lock is always
 * taken before the data store's: taking them in the other order could deadlock.</p>
 *
 * <p><b>Notifications.</b> The buyer receives the notification that the Buyer Portal expects
 * for a reply (use case 1.8). The admin receives a "Flagged Reviews" notification linked to
 * the review, in the same form as {@link AdminNotificationService#flagReview}, so that the
 * Admin Portal shows it on its notification board (use case 3.6) and marks it read once the
 * flag is handled (use case 3.8).</p>
 *
 * <p>This class does not use JavaFX, so every flow is unit tested without a display.</p>
 */
public class ReviewHandlingService {

    /** Maximum length of a reply, in characters. */
    public static final int MAX_REPLY_LENGTH = 500;
    /** Maximum length of the details of a flag, in characters. */
    public static final int MAX_FLAG_DETAILS_LENGTH = 300;
    /** Start of the title of the admin notification sent for a flag, followed by the Seller's username. */
    public static final String FLAG_NOTIFICATION_TITLE_PREFIX = "Flagged Review for @";

    /** Message when no review identifier is given. */
    public static final String REVIEW_NOT_SELECTED = "No review was selected.";
    /** Message when the review does not exist. */
    public static final String REVIEW_NOT_FOUND = "This review no longer exists.";
    /** Message when the review is about another Seller. */
    public static final String NOT_YOUR_REVIEW = "This review was not written about you.";
    /** Message when the admin has hidden the review. */
    public static final String REVIEW_HIDDEN =
            "The admin has hidden this review, so it can no longer be answered or flagged.";
    /** Message of alternative flow A2. */
    public static final String EMPTY_REPLY = "The reply cannot be empty.";
    /** Message when the reply is too long. */
    public static final String REPLY_TOO_LONG =
            "The reply cannot be longer than " + MAX_REPLY_LENGTH + " characters.";
    /** Message of alternative flow A3. */
    public static final String ALREADY_REPLIED =
            "You have already replied to this review. Each review can have only one reply.";
    /** Message of alternative flow A4. */
    public static final String ALREADY_FLAGGED =
            "You have already flagged this review. It is awaiting the admin's decision.";
    /** Message when no flag reason is chosen. */
    public static final String FLAG_REASON_REQUIRED = "Choose why you are flagging this review.";
    /** Message when the reason "Other" is chosen without details. */
    public static final String FLAG_DETAILS_REQUIRED = "Explain why you are flagging this review.";
    /** Message when the details of a flag are too long. */
    public static final String FLAG_DETAILS_TOO_LONG =
            "The details cannot be longer than " + MAX_FLAG_DETAILS_LENGTH + " characters.";

    /** Order of the reviews on the screen: newest first (basic flow, step 2). */
    private static final Comparator<Review> NEWEST_FIRST =
            Comparator.comparing(Review::getDate, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                    .thenComparing(Review::getId, Comparator.nullsLast(Comparator.<String>reverseOrder()));

    /** Data store holding the reviews and the notifications. */
    private final DummyDataService dataService;

    /**
     * Creates the service on the application's data store.
     */
    public ReviewHandlingService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Creates the service on a given data store.
     *
     * @param dataService the data store holding the reviews
     * @throws NullPointerException if {@code dataService} is {@code null}
     */
    public ReviewHandlingService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
    }

    // =====================================================================
    // Basic flow, step 2: the Seller's reviews and average rating
    // =====================================================================

    /**
     * Returns the reviews written about the logged-in Seller, newest first (basic flow,
     * step 2). Reviews hidden by the admin are not included. An empty list means that the
     * Seller has no reviews yet (alternative flow A1).
     *
     * @return the Seller's visible reviews, newest first
     * @throws SecurityException if no active Seller is logged in
     */
    public List<Review> getMyReviews() {
        Seller seller = SessionManager.requireSeller();
        return visibleReviewsOf(seller);
    }

    /**
     * Returns the logged-in Seller's average rating (basic flow, step 2), computed over the
     * same reviews as {@link #getMyReviews()}: reviews hidden by the admin are not counted,
     * as the class diagram requires. The average is rounded to one decimal.
     *
     * @return the average and the number of ratings, or "No ratings yet" if there are none
     * @throws SecurityException if no active Seller is logged in
     */
    public SellerRatingInfo getMyRating() {
        Seller seller = SessionManager.requireSeller();
        List<Review> rated = visibleReviewsOf(seller).stream().filter(Review::isStarValid).toList();
        if (rated.isEmpty()) {
            return SellerRatingInfo.noRatings(seller.getUsername(), seller.getFullName());
        }
        double sum = rated.stream().mapToInt(Review::getRating).sum();
        double average = Math.round(sum / rated.size() * 10.0) / 10.0;
        return SellerRatingInfo.withRatings(seller.getUsername(), seller.getFullName(), average, rated.size());
    }

    // =====================================================================
    // Subflow S1: Reply To Review
    // =====================================================================

    /**
     * Checks, when the Seller chooses to reply (step "Begin Reply"), that the review can
     * still be answered. Nothing is changed.
     *
     * @param reviewId identifier of the review
     * @return a successful result holding the review, or a failure: the review was already
     *         answered (alternative flow A3), hidden by the admin, is about another Seller,
     *         or does not exist
     * @throws SecurityException if no active Seller is logged in
     */
    public ReviewHandlingResult checkCanReply(String reviewId) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findReviewToReply(reviewId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Review review = lookup.review();
            return ReviewHandlingResult.success(review, "Write your reply to @" + review.getBuyerUsername()
                    + ". A review can have only one reply, and it cannot be changed afterwards.");
        }
    }

    /**
     * Posts the Seller's reply under a review (subflow S1, steps "Save Reply"), atomically:
     * the review must still be visible and without reply (alternative flow A3), and the reply
     * must not be empty (alternative flow A2) nor longer than {@value #MAX_REPLY_LENGTH}
     * characters. The reply is stored trimmed, then the buyer who wrote the review is notified.
     *
     * @param reviewId identifier of the review
     * @param reply    the reply typed by the Seller
     * @return a successful result holding the answered review, or a failure telling why the
     *         reply was not posted, in which case nothing changed
     * @throws SecurityException if no active Seller is logged in
     */
    public ReviewHandlingResult replyToReview(String reviewId, String reply) {
        Seller seller = SessionManager.requireSeller();
        String cleanReply = reply == null ? "" : reply.trim();
        Review review;
        synchronized (dataService) {
            Lookup lookup = findReviewToReply(reviewId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            review = lookup.review();
            if (cleanReply.isEmpty()) {
                return ReviewHandlingResult.failure(ReviewHandlingStatus.EMPTY_REPLY, review, EMPTY_REPLY);
            }
            if (lengthOf(cleanReply) > MAX_REPLY_LENGTH) {
                return ReviewHandlingResult.failure(ReviewHandlingStatus.REPLY_TOO_LONG, review, REPLY_TOO_LONG);
            }
            review.setReply(cleanReply);
        }
        // Step 3: notify the buyer, after releasing the data store lock (see the class comment).
        dataService.getBuyerNotificationService().notifyReviewReply(review);
        return ReviewHandlingResult.success(review, "Your reply has been posted under the review of @"
                + review.getBuyerUsername() + ", who has been notified.");
    }

    // =====================================================================
    // Subflow S2: Flag Review
    // =====================================================================

    /**
     * Checks, when the Seller chooses to flag a review (step "Begin Flag"), that the review
     * can still be flagged. Nothing is changed.
     *
     * @param reviewId identifier of the review
     * @return a successful result holding the review, or a failure: the review is already
     *         flagged (alternative flow A4), hidden by the admin, is about another Seller, or
     *         does not exist
     * @throws SecurityException if no active Seller is logged in
     */
    public ReviewHandlingResult checkCanFlag(String reviewId) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findReviewToFlag(reviewId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            return ReviewHandlingResult.success(lookup.review(), "Choose why this review is inappropriate."
                    + " The admin will decide whether to hide it; it stays visible until then.");
        }
    }

    /**
     * Flags a review for the admin (subflow S2, steps "Confirm Flag"), atomically: the review
     * must still be visible and not already flagged (alternative flow A4), and a reason must
     * be chosen, with details for {@link ReviewFlagReason#OTHER}. The review is marked as
     * flagged with the reason's text, and the admin is notified. The review stays visible, and
     * counted in the average rating, until the admin decides (use case 3.8).
     *
     * @param reviewId identifier of the review
     * @param reason   why the review is inappropriate
     * @param details  optional explanation, required for {@link ReviewFlagReason#OTHER}, at most
     *                 {@value #MAX_FLAG_DETAILS_LENGTH} characters
     * @return a successful result holding the flagged review, or a failure telling why it was
     *         not flagged, in which case nothing changed
     * @throws SecurityException if no active Seller is logged in
     */
    public ReviewHandlingResult flagReview(String reviewId, ReviewFlagReason reason, String details) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findReviewToFlag(reviewId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Review review = lookup.review();
            String reasonError = flagReasonError(reason, details);
            if (reasonError != null) {
                return ReviewHandlingResult.failure(ReviewHandlingStatus.INVALID_FLAG_REASON, review, reasonError);
            }
            String flagReason = reason.describe(details);
            review.setFlagged(true);
            review.setFlagReason(flagReason);
            notifyAdmin(review, seller, flagReason);
            return ReviewHandlingResult.success(review, "The review of @" + review.getBuyerUsername()
                    + " has been flagged (" + flagReason + "). The admin has been notified; the review"
                    + " stays visible until the admin decides.");
        }
    }

    // =====================================================================
    // Validation helpers (no session needed, nothing changed)
    // =====================================================================

    /**
     * Checks the reason and details of a flag, so that the flag dialog can report a problem
     * before sending.
     *
     * @param reason  the chosen reason, may be {@code null}
     * @param details the details typed by the Seller, may be {@code null}
     * @return {@code null} if they are valid, otherwise the message for the Seller
     */
    public static String flagReasonError(ReviewFlagReason reason, String details) {
        if (reason == null) {
            return FLAG_REASON_REQUIRED;
        }
        String cleanDetails = details == null ? "" : details.trim();
        if (reason.isDetailsRequired() && cleanDetails.isEmpty()) {
            return FLAG_DETAILS_REQUIRED;
        }
        if (lengthOf(cleanDetails) > MAX_FLAG_DETAILS_LENGTH) {
            return FLAG_DETAILS_TOO_LONG;
        }
        return null;
    }

    /**
     * Returns the length of a text once trimmed, counted in characters as the Seller sees
     * them (an emoji counts as one), which is how the length limits are applied.
     *
     * @param text the text, may be {@code null}
     * @return the number of characters, {@code 0} for {@code null}
     */
    public static int lengthOf(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        return trimmed.codePointCount(0, trimmed.length());
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================

    /**
     * A review found for an action, or the failure explaining why the action is not possible.
     *
     * @param review  the review, {@code null} on failure
     * @param failure the failure, {@code null} if the review was found
     */
    private record Lookup(Review review, ReviewHandlingResult failure) {

        /**
         * Creates a failed lookup.
         *
         * @param status  why the review cannot be handled
         * @param review  the review concerned, may be {@code null}
         * @param message message for the Seller
         * @return the failed lookup
         */
        static Lookup failed(ReviewHandlingStatus status, Review review, String message) {
            return new Lookup(null, ReviewHandlingResult.failure(status, review, message));
        }
    }

    /**
     * Returns the visible reviews of a Seller, newest first.
     *
     * @param seller the Seller
     * @return the reviews not hidden by the admin
     */
    private List<Review> visibleReviewsOf(Seller seller) {
        return dataService.getReviewsForSeller(seller.getUsername()).stream()
                .filter(Objects::nonNull)
                .filter(review -> !review.isHidden())
                .sorted(NEWEST_FIRST)
                .toList();
    }

    /**
     * Finds one of the Seller's reviews that is still visible. Called while holding the data
     * store lock.
     *
     * @param reviewId identifier of the review, trimmed
     * @param seller   the logged-in Seller
     * @return the review, or the failure if it does not exist, is about another Seller, or
     *         was hidden by the admin
     */
    private Lookup findVisibleReview(String reviewId, Seller seller) {
        if (reviewId == null || reviewId.isBlank()) {
            return Lookup.failed(ReviewHandlingStatus.REVIEW_NOT_FOUND, null, REVIEW_NOT_SELECTED);
        }
        Review review = dataService.findReview(reviewId.trim());
        if (review == null) {
            return Lookup.failed(ReviewHandlingStatus.REVIEW_NOT_FOUND, null, REVIEW_NOT_FOUND);
        }
        if (!seller.getUsername().equalsIgnoreCase(review.getSellerUsername())) {
            return Lookup.failed(ReviewHandlingStatus.UNAUTHORIZED, null, NOT_YOUR_REVIEW);
        }
        if (review.isHidden()) {
            // A hidden review is no longer shown to the Seller, so it is not returned.
            return Lookup.failed(ReviewHandlingStatus.REVIEW_HIDDEN, null, REVIEW_HIDDEN);
        }
        return new Lookup(review, null);
    }

    /**
     * Finds one of the Seller's reviews that can be answered (alternative flow A3). Called
     * while holding the data store lock.
     *
     * @param reviewId identifier of the review
     * @param seller   the logged-in Seller
     * @return the review, or the failure explaining why it cannot be answered
     */
    private Lookup findReviewToReply(String reviewId, Seller seller) {
        Lookup lookup = findVisibleReview(reviewId, seller);
        if (lookup.failure() == null && lookup.review().hasReply()) {
            return Lookup.failed(ReviewHandlingStatus.ALREADY_REPLIED, lookup.review(), ALREADY_REPLIED);
        }
        return lookup;
    }

    /**
     * Finds one of the Seller's reviews that can be flagged (alternative flow A4). Called
     * while holding the data store lock.
     *
     * @param reviewId identifier of the review
     * @param seller   the logged-in Seller
     * @return the review, or the failure explaining why it cannot be flagged
     */
    private Lookup findReviewToFlag(String reviewId, Seller seller) {
        Lookup lookup = findVisibleReview(reviewId, seller);
        if (lookup.failure() == null && lookup.review().isFlagged()) {
            return Lookup.failed(ReviewHandlingStatus.ALREADY_FLAGGED, lookup.review(), ALREADY_FLAGGED);
        }
        return lookup;
    }

    /**
     * Notifies the admin that a review was flagged (subflow S2, step 3), with a link to the
     * review. Called while holding the data store lock.
     *
     * @param review     the flagged review
     * @param seller     the Seller who flagged it
     * @param flagReason the reason stored in the review
     */
    private void notifyAdmin(Review review, Seller seller, String flagReason) {
        String message = "Seller @" + seller.getUsername() + " flagged the " + review.getRating()
                + "-star review by @" + review.getBuyerUsername() + " on '" + titleOf(review)
                + "'. Reason: " + flagReason;
        dataService.addNotification(new Notification(DummyDataService.SEEDED_ADMIN_USERNAME,
                FLAG_NOTIFICATION_TITLE_PREFIX + seller.getUsername(), message,
                AdminNotificationCategory.FLAGGED_REVIEWS.getDisplayName(), review.getId(),
                NotificationTargetType.REVIEW.name()));
    }

    /**
     * Returns the title of the listing a review is about, preferring the one stored in the
     * review, which stays known after the listing is deleted.
     *
     * @param review the review
     * @return the listing title, or {@code a deal} if it is unknown
     */
    private String titleOf(Review review) {
        if (review.getListingTitle() != null && !review.getListingTitle().isBlank()) {
            return review.getListingTitle();
        }
        Listing listing = review.getListingId() != null ? dataService.findListing(review.getListingId()) : null;
        return listing != null && listing.getTitle() != null ? listing.getTitle() : "a deal";
    }
}
