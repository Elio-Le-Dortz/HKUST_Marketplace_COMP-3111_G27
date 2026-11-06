package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AdminRatingFilterCriteria;
import com.hkust.marketplace.models.AdminRatingOperationResult;
import com.hkust.marketplace.models.AdminRatingOperationStatus;
import com.hkust.marketplace.models.AdminRatingSummaryMetrics;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.SellerRatingInfo;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Headless business logic service for Admin Seller Ratings Overview and moderation
 * (US-A08 / Use Case 3.8: Manage Seller Ratings).
 * <p>
 * Implements:
 * <ul>
 *   <li>Basic Flow: Ordered review listing (flagged first, then newest first)</li>
 *   <li>Subflow S1: Hide review, clear flag, recalculate seller rating, notify seller and buyer</li>
 *   <li>Subflow S2: Unhide review, reinstate in seller rating calculation, notify admin</li>
 *   <li>Subflow S3: Dismiss flag on review, retain visibility, notify seller</li>
 *   <li>Alternative Flow A1: Detection of empty review state</li>
 *   <li>Alternative Flow A2: Multi-criteria filtering by user query, score, state, and date range</li>
 *   <li>Alternative Flow A3: Date range validation (end date before start date)</li>
 *   <li>Special Requirements: Exclusion of hidden reviews from seller ratings; retention for unhiding</li>
 * </ul>
 */
public class AdminRatingService {

    /**
     * Standard comparator for ordering reviews: flagged reviews first, then newest first by date.
     */
    public static final Comparator<Review> REVIEW_ORDER_COMPARATOR = (r1, r2) -> {
        if (r1 == null && r2 == null) {
            return 0;
        }
        if (r1 == null) {
            return 1;
        }
        if (r2 == null) {
            return -1;
        }

        // 1. Flagged reviews first
        int flagCompare = Boolean.compare(r2.isFlagged(), r1.isFlagged());
        if (flagCompare != 0) {
            return flagCompare;
        }

        // 2. Newest first (descending by date)
        if (r1.getDate() == null && r2.getDate() == null) {
            return 0;
        }
        if (r1.getDate() == null) {
            return 1;
        }
        if (r2.getDate() == null) {
            return -1;
        }
        return r2.getDate().compareTo(r1.getDate());
    };

    private final DummyDataService dataService;

    /**
     * Constructs an AdminRatingService with the specified data store.
     *
     * @param dataService the shared data service instance
     */
    public AdminRatingService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService must not be null");
    }

    /**
     * Retrieves all reviews sorted per Basic Flow Step 2: flagged reviews first, then newest first.
     *
     * @return a sorted list of all reviews in the system
     */
    public synchronized List<Review> getOrderedReviews() {
        return dataService.getAllReviews().stream()
                .filter(Objects::nonNull)
                .sorted(REVIEW_ORDER_COMPARATOR)
                .collect(Collectors.toList());
    }

    /**
     * Filters reviews matching the supplied criteria (Alternative Flow A2).
     *
     * @param criteria the filter criteria to evaluate
     * @return filtered reviews ordered with flagged first, then newest first
     * @throws IllegalArgumentException if the date range has an end date before its start date (Flow A3)
     */
    public synchronized List<Review> filterReviews(AdminRatingFilterCriteria criteria) {
        if (criteria != null && !criteria.isValidDateRange()) {
            throw new IllegalArgumentException("Invalid date range: end date cannot be before start date.");
        }

        return dataService.getAllReviews().stream()
                .filter(Objects::nonNull)
                .filter(r -> criteria == null || criteria.matches(r))
                .sorted(REVIEW_ORDER_COMPARATOR)
                .collect(Collectors.toList());
    }

    /**
     * Computes real-time aggregate summary metrics across all reviews in the platform.
     *
     * @return the computed {@link AdminRatingSummaryMetrics}
     */
    public synchronized AdminRatingSummaryMetrics getSummaryMetrics() {
        List<Review> all = dataService.getAllReviews().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int total = all.size();
        int flagged = 0;
        int hidden = 0;
        int visible = 0;
        int sumVisibleScore = 0;
        int validVisibleCount = 0;

        for (Review r : all) {
            if (r.isFlagged()) {
                flagged++;
            }
            if (r.isHidden()) {
                hidden++;
            } else {
                visible++;
                if (r.getRating() >= 1 && r.getRating() <= 5) {
                    sumVisibleScore += r.getRating();
                    validVisibleCount++;
                }
            }
        }

        double averagePlatform = 0.0;
        if (validVisibleCount > 0) {
            averagePlatform = Math.round((sumVisibleScore / (double) validVisibleCount) * 10.0) / 10.0;
        }

        return new AdminRatingSummaryMetrics(total, flagged, hidden, visible, averagePlatform);
    }

    /**
     * Hides an inappropriate review, clears any existing flag, recalculates the seller's average rating,
     * and notifies both the seller and the buyer (Subflow S1).
     *
     * @param reviewId      the identifier of the review to hide
     * @param adminUsername the username of the administrator performing the action
     * @return an {@link AdminRatingOperationResult} detailing the outcome
     */
    public synchronized AdminRatingOperationResult hideReview(String reviewId, String adminUsername) {
        if (!isAdmin(adminUsername)) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.UNAUTHORIZED,
                    "Unauthorized: Only administrators can hide reviews.");
        }

        if (reviewId == null || reviewId.trim().isEmpty()) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.INVALID_OPERATION,
                    "Review ID must not be empty.");
        }

        Review review = findReviewById(reviewId.trim());
        if (review == null) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.REVIEW_NOT_FOUND,
                    "Review not found: " + reviewId);
        }

        if (review.isHidden()) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.ALREADY_HIDDEN,
                    "Review is already hidden.");
        }

        synchronized (review) {
            // S1 Step 3: Mark as hidden and clear flag
            review.setHidden(true);
            review.setFlagged(false);
            dataService.updateReview(review);

            // S1 Step 3: Atomically recalculate seller average rating
            String sellerUsername = review.getSellerUsername();
            SellerRatingInfo ratingInfo = dataService.recalculateSellerRating(sellerUsername);

            // Resolve listing title for friendly notifications
            String listingTitle = resolveListingTitle(review);

            // S1 Step 4: Notify seller that review has been hidden
            if (sellerUsername != null && !sellerUsername.trim().isEmpty()) {
                Notification sellerNotif = new Notification(
                        sellerUsername,
                        "Review Hidden by Admin",
                        "A review from @" + review.getBuyerUsername() + " on '" + listingTitle
                                + "' has been hidden by an administrator and excluded from your average rating.",
                        NotificationCategory.REVIEWS.getDisplayName(),
                        review.getId(),
                        "REVIEW"
                );
                dataService.addNotification(sellerNotif);
            }

            // S1 Step 4: Notify buyer who wrote the review
            String buyerUsername = review.getBuyerUsername();
            if (buyerUsername != null && !buyerUsername.trim().isEmpty()) {
                Notification buyerNotif = new Notification(
                        buyerUsername,
                        "Your Review Was Hidden",
                        "Your review on '" + listingTitle + "' (Seller: @" + sellerUsername
                                + ") was reviewed by an administrator and has been hidden.",
                        NotificationCategory.REVIEWS.getDisplayName(),
                        review.getId(),
                        "REVIEW"
                );
                dataService.addNotification(buyerNotif);
            }

            // Postcondition: clear any pending admin notification for this flagged review
            markAssociatedAdminNotificationsAsRead(review.getId());

            // S1 Step 5: Inform Admin
            return AdminRatingOperationResult.success(
                    "Review " + review.getId() + " has been successfully hidden and excluded from seller ratings.",
                    review,
                    sellerUsername,
                    ratingInfo.getAverageRating(),
                    ratingInfo.getRatingCount()
            );
        }
    }

    /**
     * Unhides a previously hidden review, reinstating it into the seller's average rating calculation (Subflow S2).
     *
     * @param reviewId      the identifier of the review to unhide
     * @param adminUsername the username of the administrator performing the action
     * @return an {@link AdminRatingOperationResult} detailing the outcome
     */
    public synchronized AdminRatingOperationResult unhideReview(String reviewId, String adminUsername) {
        if (!isAdmin(adminUsername)) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.UNAUTHORIZED,
                    "Unauthorized: Only administrators can unhide reviews.");
        }

        if (reviewId == null || reviewId.trim().isEmpty()) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.INVALID_OPERATION,
                    "Review ID must not be empty.");
        }

        Review review = findReviewById(reviewId.trim());
        if (review == null) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.REVIEW_NOT_FOUND,
                    "Review not found: " + reviewId);
        }

        if (!review.isHidden()) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.ALREADY_VISIBLE,
                    "Review is already visible.");
        }

        synchronized (review) {
            // S2 Step 3: Mark as not hidden
            review.setHidden(false);
            dataService.updateReview(review);

            // S2 Step 3: Recalculate seller rating
            String sellerUsername = review.getSellerUsername();
            SellerRatingInfo ratingInfo = dataService.recalculateSellerRating(sellerUsername);

            // S2 Step 4: Inform Admin
            return AdminRatingOperationResult.success(
                    "Review " + review.getId() + " is now visible again and counted in seller ratings.",
                    review,
                    sellerUsername,
                    ratingInfo.getAverageRating(),
                    ratingInfo.getRatingCount()
            );
        }
    }

    /**
     * Dismisses a seller's flag on a review, keeping the review visible and notifying the seller (Subflow S3).
     *
     * @param reviewId      the identifier of the review whose flag is being dismissed
     * @param adminUsername the username of the administrator performing the action
     * @return an {@link AdminRatingOperationResult} detailing the outcome
     */
    public synchronized AdminRatingOperationResult dismissFlag(String reviewId, String adminUsername) {
        if (!isAdmin(adminUsername)) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.UNAUTHORIZED,
                    "Unauthorized: Only administrators can dismiss flags.");
        }

        if (reviewId == null || reviewId.trim().isEmpty()) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.INVALID_OPERATION,
                    "Review ID must not be empty.");
        }

        Review review = findReviewById(reviewId.trim());
        if (review == null) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.REVIEW_NOT_FOUND,
                    "Review not found: " + reviewId);
        }

        if (!review.isFlagged()) {
            return AdminRatingOperationResult.failure(AdminRatingOperationStatus.NOT_FLAGGED,
                    "Review is not currently flagged.");
        }

        synchronized (review) {
            // S3 Step 3: Clear flag; review stays visible
            review.setFlagged(false);
            review.setFlagReason(null);
            dataService.updateReview(review);

            // S3 Step 4: Notify seller that the flag was reviewed and the review is kept
            String sellerUsername = review.getSellerUsername();
            String listingTitle = resolveListingTitle(review);
            if (sellerUsername != null && !sellerUsername.trim().isEmpty()) {
                Notification sellerNotif = new Notification(
                        sellerUsername,
                        "Flag Dismissed: Review Retained",
                        "The flag on review from @" + review.getBuyerUsername() + " on '" + listingTitle
                                + "' was reviewed by an administrator and dismissed. The review remains visible.",
                        NotificationCategory.REVIEWS.getDisplayName(),
                        review.getId(),
                        "REVIEW"
                );
                dataService.addNotification(sellerNotif);
            }

            // Postcondition: clear any pending admin notification for this flagged review
            markAssociatedAdminNotificationsAsRead(review.getId());

            // S3 Step 5: Inform Admin
            return AdminRatingOperationResult.successNoRatingChange(
                    "Flag on review " + review.getId() + " has been dismissed. The review remains visible.",
                    review
            );
        }
    }

    /**
     * Finds a review by its unique ID.
     *
     * @param reviewId the review ID
     * @return the matching {@link Review}, or null if not found
     */
    public synchronized Review findReviewById(String reviewId) {
        if (reviewId == null || reviewId.trim().isEmpty()) {
            return null;
        }
        for (Review r : dataService.getAllReviews()) {
            if (r != null && reviewId.trim().equalsIgnoreCase(r.getId())) {
                return r;
            }
        }
        return null;
    }

    private boolean isAdmin(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        User user = dataService.findUser(username.trim());
        if (user != null) {
            return user.getRole() == UserRole.ADMIN;
        }
        return "admin".equalsIgnoreCase(username.trim());
    }

    private String resolveListingTitle(Review review) {
        if (review == null) {
            return "Item";
        }
        if (review.getListingTitle() != null && !review.getListingTitle().trim().isEmpty()) {
            return review.getListingTitle().trim();
        }
        if (review.getListingId() != null && !review.getListingId().trim().isEmpty()) {
            Listing listing = dataService.findListing(review.getListingId().trim());
            if (listing != null && listing.getTitle() != null && !listing.getTitle().trim().isEmpty()) {
                return listing.getTitle().trim();
            }
        }
        return "Item";
    }

    private void markAssociatedAdminNotificationsAsRead(String reviewId) {
        if (reviewId == null || reviewId.trim().isEmpty()) {
            return;
        }
        List<Notification> adminNotifs = dataService.getNotificationsFor("admin");
        for (Notification n : adminNotifs) {
            if (n != null && "REVIEW".equalsIgnoreCase(n.getRelatedItemType())
                    && reviewId.trim().equalsIgnoreCase(n.getRelatedItemId())) {
                n.setRead(true);
            }
        }
    }
}
