package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules, validation, and domain state transitions
 * for the Admin Listing Review Queue (Sprint 2 US-A02, Task 3.2 in specs_admin.tex).
 *
 * <p>Completely decoupled from JavaFX UI controls to support fast headless testing in CI.
 */
public class ListingReviewService {

    private final DummyDataService dataService;

    public ListingReviewService() {
        this(DummyDataService.getInstance());
    }

    public ListingReviewService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Retrieves all listings currently awaiting review, ordered oldest first (Basic Flow Step 2).
     *
     * @return a list of listings with status {@link ListingStatus#PENDING_REVIEW}, sorted by postedDate ascending.
     */
    public synchronized List<Listing> getPendingListingsOldestFirst() {
        return dataService.getAllListings().stream()
                .filter(l -> l != null && l.getStatus() == ListingStatus.PENDING_REVIEW)
                .sorted((l1, l2) -> {
                    LocalDateTime d1 = l1.getPostedAt();
                    LocalDateTime d2 = l2.getPostedAt();
                    if (d1 == null && d2 == null) return 0;
                    if (d1 == null) return 1;
                    if (d2 == null) return -1;
                    int cmp = d1.compareTo(d2);
                    if (cmp != 0) return cmp;
                    return (l1.getId() != null && l2.getId() != null) ? l1.getId().compareTo(l2.getId()) : 0;
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns the count of listings currently awaiting review.
     *
     * @return the number of pending listings.
     */
    public synchronized int getPendingReviewCount() {
        return getPendingListingsOldestFirst().size();
    }

    /**
     * Retrieves the complete details of a specific listing (Basic Flow Step 3.1).
     *
     * @param listingId the ID of the listing to inspect.
     * @return an {@link Optional} containing the listing if found, or empty otherwise.
     */
    public synchronized Optional<Listing> getListingDetails(String listingId) {
        if (listingId == null || listingId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(dataService.findListing(listingId.trim()));
    }

    /**
     * Approves a pending listing to become {@link ListingStatus#LIVE} (Subflow S1).
     *
     * <p>Enforces:
     * <ul>
     *   <li>Admin authorization: Requesting user must be an active {@link Admin}.</li>
     *   <li>Precondition / A3 check: Listing must exist and currently have status {@link ListingStatus#PENDING_REVIEW}.</li>
     *   <li>State transition: Status updated to {@link ListingStatus#LIVE}, reviewed timestamp recorded, previous rejection reasons cleared.</li>
     *   <li>Notification (Subflow S1 Step 5): Dispatches an approval notification to the seller.</li>
     * </ul>
     *
     * @param listingId the ID of the listing to approve.
     * @param adminUsername the username of the reviewing administrator.
     * @return a {@link ListingReviewResult} detailing the outcome.
     */
    public synchronized ListingReviewResult approveListing(String listingId, String adminUsername) {
        if (listingId == null || listingId.trim().isEmpty()) {
            return ListingReviewResult.invalidInput("Listing ID cannot be null or empty.");
        }
        if (adminUsername == null || adminUsername.trim().isEmpty()) {
            return ListingReviewResult.unauthorized("Admin username is required.");
        }

        // Verify administrator authorization
        User adminUser = dataService.findUser(adminUsername.trim());
        if (adminUser == null || adminUser.getRole() != UserRole.ADMIN || !adminUser.isActive()) {
            return ListingReviewResult.unauthorized("User '" + adminUsername + "' is not an active administrator.");
        }

        // Verify listing exists
        Listing listing = dataService.findListing(listingId.trim());
        if (listing == null) {
            return ListingReviewResult.notFound(listingId.trim());
        }

        // Alternative Flow A3: Listing No Longer Pending
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            return ListingReviewResult.notPending(listing);
        }

        // Perform Subflow S1 state transition
        LocalDateTime now = LocalDateTime.now();
        listing.setStatus(ListingStatus.LIVE);
        listing.setAdminReason(null);
        listing.setReviewedAt(now);
        dataService.updateListing(listing);

        // Subflow S1 Step 5: Notify seller
        String seller = listing.getSellerUsername();
        if (seller != null && !seller.trim().isEmpty()) {
            Notification notif = new Notification(
                    seller.trim(),
                    "Listing Approved",
                    "Your listing '" + listing.getTitle() + "' has been approved and is now live in the marketplace.",
                    "Listings",
                    listing.getId(),
                    "LISTING"
            );
            dataService.addNotification(notif);
        }

        return ListingReviewResult.approved(listing, now);
    }

    /**
     * Rejects a pending listing with a mandatory written reason (Subflow S2).
     *
     * <p>Enforces:
     * <ul>
     *   <li>Admin authorization: Requesting user must be an active {@link Admin}.</li>
     *   <li>Alternative Flow A2: Written rejection reason must not be null, empty, or whitespace.</li>
     *   <li>Precondition / A3 check: Listing must exist and currently have status {@link ListingStatus#PENDING_REVIEW}.</li>
     *   <li>State transition: Status updated to {@link ListingStatus#REJECTED}, reason stored on listing, reviewed timestamp recorded.</li>
     *   <li>Notification (Subflow S2 Step 6): Dispatches a rejection notification containing the written reason to the seller.</li>
     * </ul>
     *
     * @param listingId the ID of the listing to reject.
     * @param adminUsername the username of the reviewing administrator.
     * @param reason the mandatory written rejection reason.
     * @return a {@link ListingReviewResult} detailing the outcome.
     */
    public synchronized ListingReviewResult rejectListing(String listingId, String adminUsername, String reason) {
        if (listingId == null || listingId.trim().isEmpty()) {
            return ListingReviewResult.invalidInput("Listing ID cannot be null or empty.");
        }
        if (adminUsername == null || adminUsername.trim().isEmpty()) {
            return ListingReviewResult.unauthorized("Admin username is required.");
        }

        // Verify administrator authorization
        User adminUser = dataService.findUser(adminUsername.trim());
        if (adminUser == null || adminUser.getRole() != UserRole.ADMIN || !adminUser.isActive()) {
            return ListingReviewResult.unauthorized("User '" + adminUsername + "' is not an active administrator.");
        }

        // Verify listing exists
        Listing listing = dataService.findListing(listingId.trim());
        if (listing == null) {
            return ListingReviewResult.notFound(listingId.trim());
        }

        // Alternative Flow A2: Empty Reason
        if (reason == null || reason.trim().isEmpty()) {
            return ListingReviewResult.emptyReason(listing);
        }

        // Alternative Flow A3: Listing No Longer Pending
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            return ListingReviewResult.notPending(listing);
        }

        // Perform Subflow S2 state transition
        String cleanReason = reason.trim();
        LocalDateTime now = LocalDateTime.now();
        listing.setStatus(ListingStatus.REJECTED);
        listing.setAdminReason(cleanReason);
        listing.setReviewedAt(now);
        dataService.updateListing(listing);

        // Subflow S2 Step 6: Notify seller with written reason
        String seller = listing.getSellerUsername();
        if (seller != null && !seller.trim().isEmpty()) {
            Notification notif = new Notification(
                    seller.trim(),
                    "Listing Rejected",
                    "Your listing '" + listing.getTitle() + "' was rejected. Reason: " + cleanReason,
                    "Listings",
                    listing.getId(),
                    "LISTING"
            );
            dataService.addNotification(notif);
        }

        return ListingReviewResult.rejected(listing, cleanReason, now);
    }

    /**
     * Dispatches a review request (approval or rejection) to the corresponding subflow.
     *
     * @param request the DTO encapsulating the review request.
     * @return a {@link ListingReviewResult} detailing the outcome.
     */
    public synchronized ListingReviewResult reviewListing(ListingReviewRequest request) {
        if (request == null) {
            return ListingReviewResult.invalidInput("Review request cannot be null.");
        }
        if (request.isApprove()) {
            return approveListing(request.getListingId(), request.getAdminUsername());
        } else {
            return rejectListing(request.getListingId(), request.getAdminUsername(), request.getRejectionReason());
        }
    }
}
