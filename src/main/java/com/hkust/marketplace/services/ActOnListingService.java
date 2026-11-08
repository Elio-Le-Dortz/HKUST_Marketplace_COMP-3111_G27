package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Service encapsulating the business rules for acting on listings:
 * Submitting Price Offers, Borrow Requests (minimum 5s duration), and Atomic Giveaway Claims
 * (Use Case 1.5: Act on Listing / US-B05).
 *
 * <p>Completely decoupled from JavaFX UI controls to ensure exhaustive headless unit testing.
 */
public class ActOnListingService {

    public static final long MIN_BORROW_DURATION_SECONDS = 5;

    private final DummyDataService dataService;

    public ActOnListingService() {
        this(DummyDataService.getInstance());
    }

    public ActOnListingService(DummyDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Submits a price offer for a SELL listing (Use Case 1.5 Subflow S1).
     * Enforces listing availability, correct listing type, buyer identity, and positive price amount.
     *
     * @param listingId     the listing ID
     * @param buyerUsername the buyer's username
     * @param price         the proposed offer price in HKD
     * @return an {@link ActOnListingResult} containing the outcome
     */
    public synchronized ActOnListingResult submitPriceOffer(String listingId, String buyerUsername, Double price) {
        checkSessionBuyer(buyerUsername);
        ActOnListingResult preCheck = validateCommonPreconditions(listingId, buyerUsername, ListingType.SELL);
        if (preCheck != null) {
            return preCheck;
        }

        String priceError = validatePrice(price);
        if (priceError != null) {
            return ActOnListingResult.invalidPrice(priceError);
        }

        Listing listing = dataService.findListing(listingId.trim());

        // Create and store Offer with status PENDING (Subflow S1 Step 4)
        Offer offer = new Offer();
        offer.setType(OfferType.PRICE_OFFER);
        offer.setListingId(listing.getId());
        offer.setListingTitle(listing.getTitle());
        offer.setBuyerUsername(buyerUsername.trim());
        offer.setSellerUsername(listing.getSellerUsername());
        offer.setPrice(price);
        offer.setStatus(OfferStatus.PENDING);
        offer.setCreatedDate(LocalDateTime.now());

        dataService.addOffer(offer);

        // Notify Seller (Subflow S1 Step 4)
        String notifMsg = buyerUsername.trim() + " offered HK$" + String.format("%.2f", price) + " for '" + listing.getTitle() + "'.";
        dataService.addNotification(new Notification(listing.getSellerUsername(), "New Offer Received", notifMsg, "Offer",
                offer.getId(), NotificationTargetType.OFFER.name()));

        // Confirm to Buyer (Subflow S1 Step 5)
        String confirmMsg = "Your offer of HK$" + String.format("%.2f", price) + " has been submitted to " + listing.getSellerFullName() + ".";
        return ActOnListingResult.success(offer, confirmMsg);
    }

    /**
     * Submits a borrow request for a LEND listing (Use Case 1.5 Subflow S2).
     * Enforces listing availability, correct listing type, buyer identity, and temporal date rules (>= 5s).
     *
     * @param listingId     the listing ID
     * @param buyerUsername the buyer's username
     * @param startDate     proposed borrow start date/time
     * @param endDate       proposed borrow end date/time
     * @return an {@link ActOnListingResult} containing the outcome
     */
    public synchronized ActOnListingResult submitBorrowRequest(String listingId, String buyerUsername,
                                                               LocalDateTime startDate, LocalDateTime endDate) {
        checkSessionBuyer(buyerUsername);
        ActOnListingResult preCheck = validateCommonPreconditions(listingId, buyerUsername, ListingType.LEND);
        if (preCheck != null) {
            return preCheck;
        }

        String dateError = validateBorrowDates(startDate, endDate, LocalDateTime.now());
        if (dateError != null) {
            return ActOnListingResult.invalidBorrowDates(dateError);
        }

        Listing listing = dataService.findListing(listingId.trim());

        // Create and store borrow request as Offer with status PENDING (Subflow S2 Step 4)
        Offer offer = new Offer();
        offer.setType(OfferType.BORROW_REQUEST);
        offer.setListingId(listing.getId());
        offer.setListingTitle(listing.getTitle());
        offer.setBuyerUsername(buyerUsername.trim());
        offer.setSellerUsername(listing.getSellerUsername());
        offer.setStartDate(startDate);
        offer.setEndDate(endDate);
        offer.setStatus(OfferStatus.PENDING);
        offer.setCreatedDate(LocalDateTime.now());

        dataService.addOffer(offer);

        // Notify Seller (Subflow S2 Step 4)
        String notifMsg = buyerUsername.trim() + " requested to borrow '" + listing.getTitle() + "' from "
                + startDate.toLocalDate() + " to " + endDate.toLocalDate() + ".";
        dataService.addNotification(new Notification(listing.getSellerUsername(), "Borrow Request Received", notifMsg, "Offer",
                offer.getId(), NotificationTargetType.OFFER.name()));

        // Confirm to Buyer (Subflow S2 Step 5)
        String confirmMsg = "Your borrow request for '" + listing.getTitle() + "' has been submitted to " + listing.getSellerFullName() + ".";
        return ActOnListingResult.success(offer, confirmMsg);
    }

    /**
     * Atomically submits a claim for a GIVEAWAY listing (Use Case 1.5 Subflow S3).
     * Strictly synchronized to guarantee that only the first claimant succeeds concurrently.
     *
     * @param listingId     the listing ID
     * @param buyerUsername the buyer's username
     * @return an {@link ActOnListingResult} containing the outcome
     */
    public synchronized ActOnListingResult submitClaim(String listingId, String buyerUsername) {
        checkSessionBuyer(buyerUsername);
        ActOnListingResult preCheck = validateCommonPreconditions(listingId, buyerUsername, ListingType.GIVEAWAY);
        if (preCheck != null) {
            return preCheck;
        }

        Listing listing = dataService.findListing(listingId.trim());

        // Concurrency Check: Alternative Flow A4 (Giveaway Already Claimed)
        if (isGiveawayClaimed(listing.getId())) {
            return ActOnListingResult.alreadyClaimed("This giveaway item has already been claimed.");
        }

        // Store the claim as an Offer with status PENDING (Subflow S3 Step 3)
        Offer offer = new Offer();
        offer.setType(OfferType.CLAIM);
        offer.setListingId(listing.getId());
        offer.setListingTitle(listing.getTitle());
        offer.setBuyerUsername(buyerUsername.trim());
        offer.setSellerUsername(listing.getSellerUsername());
        offer.setStatus(OfferStatus.PENDING);
        offer.setCreatedDate(LocalDateTime.now());

        dataService.addOffer(offer);

        // Notify Seller (Subflow S3 Step 3)
        String notifMsg = buyerUsername.trim() + " claimed your giveaway item: '" + listing.getTitle() + "'.";
        dataService.addNotification(new Notification(listing.getSellerUsername(), "Giveaway Claim Received", notifMsg, "Offer",
                offer.getId(), NotificationTargetType.OFFER.name()));

        // Confirm to Buyer (Subflow S3 Step 4)
        String confirmMsg = "Your claim for '" + listing.getTitle() + "' has been submitted to " + listing.getSellerFullName() + ".";
        return ActOnListingResult.success(offer, confirmMsg);
    }

    /**
     * Checks if a giveaway listing is already claimed or has a pending claim offer.
     *
     * @param listingId the listing ID
     * @return true if already claimed or an active claim offer exists
     */
    public synchronized boolean isGiveawayClaimed(String listingId) {
        if (listingId == null) return false;
        Listing listing = dataService.findListing(listingId.trim());
        if (listing == null) return false;

        // If listing is marked GIVEN or not LIVE, it is already claimed
        if (listing.getStatus() != ListingStatus.LIVE) {
            return true;
        }

        // Check if any claim offer is already pending or accepted
        return dataService.getOffersForListing(listing.getId()).stream()
                .anyMatch(o -> o.getType() == OfferType.CLAIM &&
                        (o.getStatus() == OfferStatus.PENDING || o.getStatus() == OfferStatus.ACCEPTED));
    }

    /**
     * Validates proposed price for a price offer (Alternative Flow A2).
     *
     * @param price the proposed amount
     * @return null if valid, or an error description string
     */
    public String validatePrice(Double price) {
        if (price == null) {
            return "Offer price cannot be empty.";
        }
        if (Double.isNaN(price) || Double.isInfinite(price)) {
            return "Offer price must be a valid number.";
        }
        if (price <= 0.0) {
            return "Offer price must be a positive amount.";
        }
        return null;
    }

    /**
     * Validates borrow dates and durations according to Alternative Flow A3.
     * Enforces:
     * 1. Start and end dates must be provided.
     * 2. Start date cannot be in the past (with 30-second clock tolerance).
     * 3. End date must be strictly after start date.
     * 4. Duration must be at least 5 seconds.
     *
     * @param startDate borrow start date/time
     * @param endDate   borrow end date/time
     * @param now       reference timestamp for past check
     * @return null if valid, or the specific date rule error message
     */
    public String validateBorrowDates(LocalDateTime startDate, LocalDateTime endDate, LocalDateTime now) {
        if (startDate == null || endDate == null) {
            return "Start date and end date are required.";
        }
        if (now != null && startDate.isBefore(now.minusSeconds(30))) {
            return "Borrow start date cannot be in the past.";
        }
        if (!endDate.isAfter(startDate)) {
            return "Borrow end date must be after start date.";
        }
        long durationSeconds = Duration.between(startDate, endDate).toSeconds();
        if (durationSeconds < MIN_BORROW_DURATION_SECONDS) {
            return "Borrow duration must be at least " + MIN_BORROW_DURATION_SECONDS + " seconds.";
        }
        return null;
    }

    /**
     * Validates shared preconditions across all listing actions:
     * - Buyer username validation
     * - Listing existence & LIVE status check (Alternative Flow A1)
     * - Listing type match
     * - Prohibition of acting on own listing
     */
    private ActOnListingResult validateCommonPreconditions(String listingId, String buyerUsername, ListingType expectedType) {
        if (buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return ActOnListingResult.invalidUser("Buyer username cannot be null or empty.");
        }

        if (listingId == null || listingId.trim().isEmpty()) {
            return ActOnListingResult.listingNotFound("Listing ID cannot be null or empty.");
        }

        Listing listing = dataService.findListing(listingId.trim());
        if (listing == null) {
            return ActOnListingResult.listingNotFound("Listing not found with ID: " + listingId);
        }

        // Alternative Flow A1: Listing No Longer Live
        if (listing.getStatus() != ListingStatus.LIVE) {
            return ActOnListingResult.listingNotLive("This listing is no longer available.");
        }

        // Validate Listing Type match
        if (listing.getType() != expectedType) {
            return ActOnListingResult.invalidListingType("Action does not match listing type (" + listing.getType() + "). Expected: " + expectedType);
        }

        // Prohibition of acting on own listing
        if (listing.getSellerUsername() != null && listing.getSellerUsername().equalsIgnoreCase(buyerUsername.trim())) {
            return ActOnListingResult.selfActionNotAllowed("You cannot make an offer or claim on your own listing.");
        }

        return null; // All common preconditions satisfied
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
