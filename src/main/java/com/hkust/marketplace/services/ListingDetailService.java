package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules for viewing listing details, retrieving seller ratings,
 * and reporting listings (Use Case 1.4: View Listing Details).
 *
 * <p>Completely decoupled from JavaFX UI controls to allow exhaustive unit testing in headless CI.
 */
public class ListingDetailService {

    private final DummyDataService dataService;

    public ListingDetailService() {
        this(DummyDataService.getInstance());
    }

    public ListingDetailService(DummyDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Opens and retrieves a listing's detail, incrementing its view count.
     * Enforces Use Case 1.4 Basic Flow (Steps 1-3) and Alternative Flow A1 (Listing No Longer Live).
     *
     * @param listingId  the identifier of the listing to open
     * @param requireLive if true, enforces that the listing must be in {@link ListingStatus#LIVE} status
     *                    (e.g., when accessed from the marketplace browsing view)
     * @return a {@link ListingViewResult} representing the outcome
     */
    public synchronized ListingViewResult viewListing(String listingId, boolean requireLive) {
        dataService.processAutoReturns();
        if (listingId == null || listingId.trim().isEmpty()) {
            return ListingViewResult.notFound("Listing ID cannot be null or empty.");
        }

        Listing listing = dataService.findListing(listingId.trim());
        if (listing == null) {
            return ListingViewResult.notFound("Listing not found with ID: " + listingId);
        }

        // Alternative Flow A1: Listing No Longer Live
        if (requireLive && listing.getStatus() != ListingStatus.LIVE) {
            return ListingViewResult.notLive("This listing is no longer available.");
        }

        // Basic Flow Step 2: Increment view count
        listing.incrementViewCount();

        // Basic Flow Step 3: Retrieve seller rating
        SellerRatingInfo ratingInfo = getSellerRatingInfo(listing.getSellerUsername());

        return ListingViewResult.success(listing, ratingInfo);
    }

    /**
     * Computes the rating information for a given seller.
     * Enforces that hidden reviews are excluded and handles Alternative Flow A2 (Seller Has No Rating).
     *
     * @param sellerUsername the seller's username
     * @return a {@link SellerRatingInfo} object containing rating statistics and display text
     */
    public synchronized SellerRatingInfo getSellerRatingInfo(String sellerUsername) {
        if (sellerUsername == null || sellerUsername.trim().isEmpty()) {
            return SellerRatingInfo.noRatings("", "Unknown Seller");
        }

        String username = sellerUsername.trim();
        User user = dataService.findUser(username);
        String fullName = (user != null && user.getFullName() != null) ? user.getFullName() : username;

        // Retrieve reviews and exclude hidden reviews
        List<Review> validReviews = dataService.getReviewsForSeller(username).stream()
                .filter(r -> !r.isHidden() && r.getRating() >= 1 && r.getRating() <= 5)
                .collect(Collectors.toList());

        if (!validReviews.isEmpty()) {
            int count = validReviews.size();
            double sum = validReviews.stream().mapToInt(Review::getRating).sum();
            double average = Math.round((sum / (double) count) * 10.0) / 10.0;
            return SellerRatingInfo.withRatings(username, fullName, average, count);
        }

        // Fallback to seller entity seeded rating if no individual reviews exist
        if (user instanceof Seller seller && seller.getRatingCount() > 0) {
            double avg = Math.round(seller.getAverageRating() * 10.0) / 10.0;
            return SellerRatingInfo.withRatings(username, fullName, avg, seller.getRatingCount());
        }

        // Alternative Flow A2: Seller has no ratings yet
        return SellerRatingInfo.noRatings(username, fullName);
    }

    /**
     * Submits a report against a listing (Alternative Flow A3: Report Listing).
     * Validates input, persists the report with status {@link ReportStatus#OPEN}, and notifies administrators.
     *
     * @param listingId     the identifier of the listing being reported
     * @param buyerUsername the username of the buyer filing the report
     * @param reason        the written explanation of the report
     * @return a {@link ReportResult} containing success/failure outcome
     */
    public synchronized ReportResult submitReport(String listingId, String buyerUsername, String reason) {
        checkSessionBuyer(buyerUsername);
        if (listingId == null || listingId.trim().isEmpty()) {
            return ReportResult.failure("Listing ID cannot be null or empty.");
        }

        Listing listing = dataService.findListing(listingId.trim());
        if (listing == null) {
            return ReportResult.failure("Listing not found with ID: " + listingId);
        }

        if (buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return ReportResult.failure("Buyer username cannot be null or empty.");
        }

        if (reason == null || reason.trim().isEmpty()) {
            return ReportResult.failure("Please provide a reason for the report.");
        }

        // 1. Create and store Report with status OPEN (Alternative Flow A3 Step 3)
        Report report = new Report();
        report.setListingId(listing.getId());
        report.setListingTitle(listing.getTitle());
        report.setBuyerUsername(buyerUsername.trim());
        report.setReason(reason.trim());
        report.setDate(LocalDateTime.now());
        report.setStatus(ReportStatus.OPEN);

        dataService.addReport(report);

        // 2. Notify admin (Alternative Flow A3 Step 3)
        List<User> admins = dataService.getUsersByRole(UserRole.ADMIN);
        String notifMsg = "Buyer @" + buyerUsername.trim() + " reported listing '" + listing.getTitle() + "'. Reason: " + reason.trim();
        String notifTitle = "Report Filed: " + listing.getTitle();

        if (admins == null || admins.isEmpty()) {
            dataService.addNotification(new Notification("admin", notifTitle, notifMsg, "Reports", report.getId(), "REPORT"));
        } else {
            for (User admin : admins) {
                dataService.addNotification(new Notification(admin.getUsername(), notifTitle, notifMsg, "Reports", report.getId(), "REPORT"));
            }
        }

        return ReportResult.success(report);
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
