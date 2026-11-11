package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules and domain logic for reviewing and rating sellers
 * after completed or returned deals (Sprint 2 US-B11, Use Case 1.10 in specs_buyer.tex).
 *
 * <p>Completely decoupled from JavaFX UI controls to guarantee headless unit testing in CI.
 */
public class BuyerReviewService {

    private final DummyDataService dataService;

    public BuyerReviewService() {
        this(DummyDataService.getInstance());
    }

    public BuyerReviewService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Submits a rating (1 to 5 stars) and optional written review for a completed or returned deal
     * (Use Case 1.10 Basic Flow &amp; Alternative Flows A1 and A2).
     *
     * <p>Enforces:
     * <ul>
     *   <li>Basic Flow Step 5 &amp; Alternative Flow A1: Star rating must be provided and between 1 and 5.</li>
     *   <li>Alternative Flow A2: Duplicate review for the same deal is strictly rejected.</li>
     *   <li>Deal eligibility: Deal must exist, belong to requesting buyer, and be completed or returned.</li>
     *   <li>Basic Flow Step 6: Persists review and atomically recalculates seller's average rating and count.</li>
     *   <li>Basic Flow Step 7: Dispatches notification to the seller informing them of the new review.</li>
     * </ul>
     *
     * @param request the rating request containing deal ID, buyer username, rating, and optional comment
     * @return a {@link RateSellerResult} detailing the outcome
     */
    public synchronized RateSellerResult submitRating(RateSellerRequest request) {
        if (request != null) {
            checkSessionBuyer(request.getBuyerUsername());
        } else {
            SessionManager.requireBuyer();
        }
        if (request == null) {
            return RateSellerResult.failure(RateSellerStatus.INVALID_INPUT, "Rating request cannot be null.");
        }

        String dealId = request.getDealId();
        String buyerUsername = request.getBuyerUsername();

        if (dealId == null || dealId.isEmpty() || buyerUsername == null || buyerUsername.isEmpty()) {
            return RateSellerResult.failure(RateSellerStatus.INVALID_INPUT, "Deal ID and buyer username are required.");
        }

        // Alternative Flow A1: No Star Rating Selected / Invalid Rating
        Integer rating = request.getRating();
        if (rating == null || rating < 1 || rating > 5) {
            return RateSellerResult.noStarRating("A star rating between 1 and 5 is required.");
        }

        Deal deal = dataService.findDeal(dealId);
        if (deal == null) {
            return RateSellerResult.failure(RateSellerStatus.DEAL_NOT_FOUND, "Deal not found with ID: " + dealId);
        }

        // Authorization check
        if (!deal.getBuyerUsername().equalsIgnoreCase(buyerUsername)) {
            return RateSellerResult.failure(RateSellerStatus.UNAUTHORIZED, "You are not authorized to rate this deal.");
        }

        // Deal status check (deal must be completed or returned)
        if (deal.getStatus() != DealStatus.COMPLETED && deal.getStatus() != DealStatus.RETURNED) {
            return RateSellerResult.failure(RateSellerStatus.DEAL_NOT_ELIGIBLE,
                    "Ratings can only be submitted for completed or returned deals.");
        }

        // Alternative Flow A2: Already Rated This Deal
        if (deal.isRated() || getReviewForDeal(deal.getId()).isPresent()) {
            return RateSellerResult.alreadyRated(deal, "This deal has already been reviewed.");
        }

        // Basic Flow Step 6: Store the review
        Review review = new Review();
        review.setDealId(deal.getId());
        review.setListingId(deal.getListingId());
        review.setListingTitle(deal.getListingTitle());
        review.setBuyerUsername(buyerUsername);
        review.setSellerUsername(deal.getSellerUsername());
        review.setRating(rating);
        review.setComment(request.getComment());
        review.setDate(LocalDateTime.now());

        dataService.addReview(review);
        deal.setRated(true);

        // Recalculate seller's average rating
        SellerRatingInfo ratingInfo = dataService.recalculateSellerRating(deal.getSellerUsername());

        // Basic Flow Step 7: Notify the seller
        String sellerMsg = buyerUsername + " submitted a " + rating + "-star review for '" + deal.getListingTitle() + "'.";
        Notification notif = new Notification(
                deal.getSellerUsername(),
                "New Review Received",
                sellerMsg,
                "Reviews",
                review.getId(),
                "REVIEW"
        );
        dataService.addNotification(notif);

        // Basic Flow Step 8: Confirm to buyer
        String confirmMsg = "Thank you! Your " + rating + "-star review has been submitted.";
        return RateSellerResult.success(review, deal, ratingInfo, confirmMsg);
    }

    /**
     * Overload for convenience.
     */
    public synchronized RateSellerResult submitRating(String dealId, String buyerUsername, Integer rating, String comment) {
        return submitRating(RateSellerRequest.of(dealId, buyerUsername, rating, comment));
    }

    /**
     * Alternative Flow A3: Skip Rating prompt.
     * Dismisses the prompt without storing any rating or modifying seller scores.
     *
     * @param dealId        the deal ID
     * @param buyerUsername the buyer's username
     * @return a {@link RateSellerResult} in SKIPPED status
     */
    public synchronized RateSellerResult skipRating(String dealId, String buyerUsername) {
        checkSessionBuyer(buyerUsername);
        if (dealId == null || dealId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return RateSellerResult.failure(RateSellerStatus.INVALID_INPUT, "Deal ID and buyer username are required.");
        }

        Deal deal = dataService.findDeal(dealId.trim());
        if (deal == null) {
            return RateSellerResult.failure(RateSellerStatus.DEAL_NOT_FOUND, "Deal not found with ID: " + dealId);
        }

        if (!deal.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
            return RateSellerResult.failure(RateSellerStatus.UNAUTHORIZED, "You are not authorized for this deal.");
        }

        return RateSellerResult.skipped(deal, "Rating skipped. You can rate this transaction later from Deal History.");
    }

    /**
     * Alternative Flow A4: Cancel Rating.
     * Cancels the active rating dialog without storing any review or modifying seller scores.
     *
     * @param dealId        the deal ID
     * @param buyerUsername the buyer's username
     * @return a {@link RateSellerResult} in CANCELLED status
     */
    public synchronized RateSellerResult cancelRating(String dealId, String buyerUsername) {
        checkSessionBuyer(buyerUsername);
        if (dealId == null || dealId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return RateSellerResult.failure(RateSellerStatus.INVALID_INPUT, "Deal ID and buyer username are required.");
        }

        Deal deal = dataService.findDeal(dealId.trim());
        if (deal == null) {
            return RateSellerResult.failure(RateSellerStatus.DEAL_NOT_FOUND, "Deal not found with ID: " + dealId);
        }

        if (!deal.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
            return RateSellerResult.failure(RateSellerStatus.UNAUTHORIZED, "You are not authorized for this deal.");
        }

        return RateSellerResult.cancelled(deal, "Rating cancelled. No review was stored.");
    }

    /**
     * Checks whether a buyer is eligible to rate a specific deal.
     * A deal is rateable if it exists, belongs to the buyer, is in COMPLETED or RETURNED status,
     * and has not already been rated.
     *
     * @param dealId        the deal ID
     * @param buyerUsername the buyer's username
     * @return true if the deal can be rated
     */
    public synchronized boolean canRateDeal(String dealId, String buyerUsername) {
        if (dealId == null || dealId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return false;
        }

        Deal deal = dataService.findDeal(dealId.trim());
        return canRateDeal(deal, buyerUsername);
    }

    /**
     * Checks whether a buyer is eligible to rate a deal instance.
     *
     * @param deal          the deal instance
     * @param buyerUsername the buyer's username
     * @return true if the deal can be rated
     */
    public synchronized boolean canRateDeal(Deal deal, String buyerUsername) {
        if (deal == null || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return false;
        }

        if (!deal.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
            return false;
        }

        if (deal.getStatus() != DealStatus.COMPLETED && deal.getStatus() != DealStatus.RETURNED) {
            return false;
        }

        if (deal.isRated()) {
            return false;
        }

        return getReviewForDeal(deal.getId()).isEmpty();
    }

    /**
     * Finds the review associated with a specific deal, if one exists.
     *
     * @param dealId the deal ID
     * @return optional containing the review, or empty if not rated
     */
    public synchronized Optional<Review> getReviewForDeal(String dealId) {
        if (dealId == null || dealId.trim().isEmpty()) {
            return Optional.empty();
        }

        return dataService.getAllReviews().stream()
                .filter(r -> r != null && dealId.trim().equalsIgnoreCase(r.getDealId()))
                .findFirst();
    }

    /**
     * Retrieves all valid (non-hidden) reviews for a seller.
     *
     * @param sellerUsername the seller's username
     * @return list of non-hidden reviews
     */
    public synchronized List<Review> getReviewsForSeller(String sellerUsername) {
        if (sellerUsername == null || sellerUsername.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return dataService.getReviewsForSeller(sellerUsername.trim());
    }

    /**
     * Retrieves all reviews submitted by a specific buyer (Task 1.10: Keep a record of every rating/review).
     *
     * @param buyerUsername the buyer's username
     * @return list of reviews submitted by this buyer
     */
    public synchronized List<Review> getReviewsByBuyer(String buyerUsername) {
        if (buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String username = buyerUsername.trim();
        return dataService.getAllReviews().stream()
                .filter(r -> r != null && username.equalsIgnoreCase(r.getBuyerUsername()))
                .collect(Collectors.toList());
    }

    /**
     * Computes or retrieves current rating statistics for a seller.
     *
     * @param sellerUsername the seller's username
     * @return current {@link SellerRatingInfo}
     */
    public synchronized SellerRatingInfo getSellerRating(String sellerUsername) {
        return dataService.getSellerRatingInfo(sellerUsername);
    }

    /**
     * Enforces that an active Buyer session is present and matches the requested username.
     *
     * @param buyerUsername requested buyer username
     * @throws SecurityException if no buyer session is active or usernames mismatch
     */
    private void checkSessionBuyer(String buyerUsername) {
        Buyer buyer = SessionManager.requireBuyer();
        if (buyerUsername != null && !buyerUsername.trim().isEmpty()
                && !buyer.getUsername().equalsIgnoreCase(buyerUsername.trim())) {
            throw new SecurityException("Unauthorized: Session buyer does not match requested username.");
        }
    }
}
