package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules for managing deals, active offers, counter-offers,
 * self-returns, deal completion, and auto-returns when borrowing expires
 * (Use Case 1.6: Manage My Deals / US-B07).
 *
 * <p>Completely decoupled from JavaFX UI controls to ensure exhaustive headless unit testing.
 */
public class DealService {

    private final DummyDataService dataService;

    public DealService() {
        this(DummyDataService.getInstance());
    }

    public DealService(DummyDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Retrieves all active offers, countered offers, and deals (active, completed, returned)
     * for a given buyer, sorted by timestamp descending.
     * Automatically triggers auto-return evaluation for any expired loans.
     *
     * @param buyerUsername the buyer's username
     * @return list of {@link MyDealsItem} representing the buyer's deals and offers
     */
    public synchronized List<MyDealsItem> getMyDealsForBuyer(String buyerUsername) {
        checkSessionBuyer(buyerUsername);
        if (buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Alternative Flow A4: Auto-return expired loans before assembling view
        processAutoReturns();

        String normalizedUser = buyerUsername.trim();
        List<MyDealsItem> items = new ArrayList<>();

        // 1. Collect relevant offers (Pending, Countered, Accepted, Declined, Withdrawn)
        List<Offer> offers = dataService.getOffersForBuyer(normalizedUser);
        for (Offer offer : offers) {
            MyDealsItem item = MyDealsItem.fromOffer(offer);
            if (item != null) {
                items.add(item);
            }
        }

        // 2. Collect deals (Active, Completed, Returned)
        List<Deal> deals = dataService.getDealsForBuyer(normalizedUser);
        for (Deal deal : deals) {
            MyDealsItem item = MyDealsItem.fromDeal(deal);
            if (item != null) {
                items.add(item);
            }
        }

        // Sort chronologically descending (newest first)
        items.sort(Comparator.comparing(MyDealsItem::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        return items;
    }

    /**
     * Subflow S1: Withdraw Offer.
     * Allows a buyer to withdraw their pending offer. The listing remains Live.
     * Enforces Alternative Flow A2 if the offer is no longer in PENDING status.
     *
     * @param offerId       the offer ID
     * @param buyerUsername the requesting buyer's username
     * @return a {@link DealOperationResult} describing the outcome
     */
    public synchronized DealOperationResult withdrawOffer(String offerId, String buyerUsername) {
        checkSessionBuyer(buyerUsername);
        if (offerId == null || offerId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return DealOperationResult.failure(DealOperationStatus.INVALID_ACTION, "Offer ID and buyer username are required.");
        }

        Offer offer = dataService.findOffer(offerId.trim());
        if (offer == null) {
            return DealOperationResult.failure(DealOperationStatus.OFFER_NOT_FOUND, "Offer not found.");
        }

        if (!offer.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
            return DealOperationResult.failure(DealOperationStatus.UNAUTHORIZED, "You are not authorized to withdraw this offer.");
        }

        // Alternative Flow A2: Offer no longer pending
        if (offer.getStatus() != OfferStatus.PENDING) {
            return DealOperationResult.failure(DealOperationStatus.OFFER_NOT_PENDING,
                    "The offer is no longer pending (current status: " + offer.getStatus() + ") and cannot be withdrawn.");
        }

        offer.setStatus(OfferStatus.WITHDRAWN);
        dataService.updateOffer(offer);

        // Notify Seller (Subflow S1 Step 4)
        String sellerMsg = buyerUsername.trim() + " withdrew their offer on '" + offer.getListingTitle() + "'.";
        dataService.addNotification(new Notification(offer.getSellerUsername(), "Offer Withdrawn", sellerMsg, "Offer",
                offer.getId(), NotificationTargetType.OFFER.name()));

        // Notify Buyer (Subflow S1 Step 5)
        String buyerMsg = "You withdrew your offer on '" + offer.getListingTitle() + "'.";
        dataService.addNotification(new Notification(buyerUsername.trim(), "Offer Withdrawn", buyerMsg, "Offer",
                offer.getId(), NotificationTargetType.OFFER.name()));

        return DealOperationResult.success(offer, "Offer successfully withdrawn.");
    }

    /**
     * Subflow S2: Accept Counter Offer.
     * Accepts a seller's counter-offer, records an active Deal, updates listing status
     * to Reserved (Sale/Giveaway) or Lent (Lend), and declines all other pending offers on the listing.
     *
     * @param offerId       the offer ID
     * @param buyerUsername the requesting buyer's username
     * @return a {@link DealOperationResult} describing the outcome
     */
    public synchronized DealOperationResult acceptCounterOffer(String offerId, String buyerUsername) {
        checkSessionBuyer(buyerUsername);
        if (offerId == null || offerId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return DealOperationResult.failure(DealOperationStatus.INVALID_ACTION, "Offer ID and buyer username are required.");
        }

        Offer offer = dataService.findOffer(offerId.trim());
        if (offer == null) {
            return DealOperationResult.failure(DealOperationStatus.OFFER_NOT_FOUND, "Offer not found.");
        }

        if (!offer.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
            return DealOperationResult.failure(DealOperationStatus.UNAUTHORIZED, "You are not authorized to accept this counter-offer.");
        }

        if (offer.getStatus() != OfferStatus.COUNTERED) {
            return DealOperationResult.failure(DealOperationStatus.COUNTER_NOT_FOUND,
                    "The offer is not in Countered status (current status: " + offer.getStatus() + ").");
        }

        Listing listing = dataService.findListing(offer.getListingId());
        // Alternative Flow A3: Listing is no longer Live
        if (listing == null || listing.getStatus() != ListingStatus.LIVE) {
            return DealOperationResult.failure(DealOperationStatus.COUNTER_NOT_VALID,
                    "The counter offer can no longer be accepted because the listing is no longer available.");
        }

        Offer counter = offer.getCounterOffer() != null ? offer.getCounterOffer() : offer;

        // Subflow S2 Step 3: Record the deal
        Deal deal = new Deal();
        deal.setListingId(listing.getId());
        deal.setListingTitle(listing.getTitle());
        deal.setBuyerUsername(buyerUsername.trim());
        deal.setSellerUsername(offer.getSellerUsername());
        deal.setOfferId(offer.getId());
        deal.setStartDate(LocalDateTime.now());
        deal.setStatus(DealStatus.ACTIVE);

        if (listing.getType() == ListingType.LEND) {
            deal.setType(DealType.LOAN);
            deal.setStartDate(counter.getStartDate() != null ? counter.getStartDate() : LocalDateTime.now());
            deal.setDueDate(counter.getEndDate() != null ? counter.getEndDate() : offer.getEndDate());
            listing.setStatus(ListingStatus.LENT);
        } else if (listing.getType() == ListingType.GIVEAWAY) {
            deal.setType(DealType.GIVEAWAY);
            listing.setStatus(ListingStatus.RESERVED);
        } else {
            deal.setType(DealType.SALE);
            deal.setOfferPrice(counter.getPrice() != null ? counter.getPrice() : offer.getPrice());
            listing.setStatus(ListingStatus.RESERVED);
        }

        dataService.addDeal(deal);
        dataService.updateListing(listing);

        // Mark offers accepted
        offer.setStatus(OfferStatus.ACCEPTED);
        dataService.updateOffer(offer);
        if (offer.getCounterOffer() != null) {
            offer.getCounterOffer().setStatus(OfferStatus.ACCEPTED);
            dataService.updateOffer(offer.getCounterOffer());
        }

        // Subflow S2 Step 4: Decline all other pending and countered offers on this listing
        List<Offer> allOffersOnListing = dataService.getOffersForListing(listing.getId());
        for (Offer other : allOffersOnListing) {
            if (!Objects.equals(other.getId(), offer.getId()) && (other.isPending() || other.isCountered())) {
                other.setStatus(OfferStatus.DECLINED);
                other.setDeclineReason("Listing was reserved by another accepted offer.");
                if (other.getCounterOffer() != null) {
                    other.getCounterOffer().setStatus(OfferStatus.DECLINED);
                    dataService.updateOffer(other.getCounterOffer());
                }
                dataService.updateOffer(other);
                dataService.addNotification(new Notification(
                        other.getBuyerUsername(),
                        "Offer Declined",
                        "Your offer for '" + listing.getTitle() + "' was declined because the item was reserved.",
                        "Offer",
                        other.getId(),
                        NotificationTargetType.OFFER.name()
                ));
            }
        }

        // Subflow S2 Step 5: Notify seller
        String sellerMsg = buyerUsername.trim() + " accepted your counter offer for '" + listing.getTitle() + "'.";
        dataService.addNotification(new Notification(listing.getSellerUsername(), "Counter Offer Accepted", sellerMsg, "Offer",
                deal.getId(), NotificationTargetType.DEAL.name()));

        // Subflow S2 Step 6: Inform Buyer
        String buyerMsg = "You accepted the counter offer for '" + listing.getTitle() + "'. Deal is now active!";
        dataService.addNotification(new Notification(buyerUsername.trim(), "Counter Offer Accepted", buyerMsg, "Offer",
                deal.getId(), NotificationTargetType.DEAL.name()));

        return DealOperationResult.success(deal, buyerMsg);
    }

    /**
     * Subflow S3: Decline Counter Offer.
     * Allows a buyer to decline a counter-offer. The listing remains Live.
     *
     * @param offerId       the offer ID
     * @param buyerUsername the requesting buyer's username
     * @return a {@link DealOperationResult} describing the outcome
     */
    public synchronized DealOperationResult declineCounterOffer(String offerId, String buyerUsername) {
        checkSessionBuyer(buyerUsername);
        if (offerId == null || offerId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return DealOperationResult.failure(DealOperationStatus.INVALID_ACTION, "Offer ID and buyer username are required.");
        }

        Offer offer = dataService.findOffer(offerId.trim());
        if (offer == null) {
            return DealOperationResult.failure(DealOperationStatus.OFFER_NOT_FOUND, "Offer not found.");
        }

        if (!offer.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
            return DealOperationResult.failure(DealOperationStatus.UNAUTHORIZED, "You are not authorized to decline this counter-offer.");
        }

        if (offer.getStatus() != OfferStatus.COUNTERED) {
            return DealOperationResult.failure(DealOperationStatus.COUNTER_NOT_FOUND,
                    "The offer is not in Countered status (current status: " + offer.getStatus() + ").");
        }

        offer.setStatus(OfferStatus.DECLINED);
        dataService.updateOffer(offer);
        if (offer.getCounterOffer() != null) {
            offer.getCounterOffer().setStatus(OfferStatus.DECLINED);
            dataService.updateOffer(offer.getCounterOffer());
        }

        // Subflow S3 Step 4: Notify seller
        String sellerMsg = buyerUsername.trim() + " declined your counter offer for '" + offer.getListingTitle() + "'.";
        dataService.addNotification(new Notification(offer.getSellerUsername(), "Counter Offer Declined", sellerMsg, "Offer",
                offer.getId(), NotificationTargetType.OFFER.name()));

        // Subflow S3 Step 5: Inform buyer
        String buyerMsg = "You declined the counter offer for '" + offer.getListingTitle() + "'.";
        dataService.addNotification(new Notification(buyerUsername.trim(), "Counter Offer Declined", buyerMsg, "Offer",
                offer.getId(), NotificationTargetType.OFFER.name()));

        return DealOperationResult.success(offer, buyerMsg);
    }

    /**
     * Subflow S4: Self Return Item.
     * Allows a buyer to return an active borrowed item before or at due date.
     * Sets deal status to RETURNED and restores listing status back to LIVE.
     *
     * @param dealId        the deal ID
     * @param buyerUsername the requesting buyer's username
     * @return a {@link DealOperationResult} describing the outcome
     */
    public synchronized DealOperationResult selfReturnItem(String dealId, String buyerUsername) {
        checkSessionBuyer(buyerUsername);
        if (dealId == null || dealId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return DealOperationResult.failure(DealOperationStatus.INVALID_ACTION, "Deal ID and buyer username are required.");
        }

        Deal deal = dataService.findDeal(dealId.trim());
        if (deal == null) {
            return DealOperationResult.failure(DealOperationStatus.DEAL_NOT_FOUND, "Deal not found.");
        }

        if (!deal.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
            return DealOperationResult.failure(DealOperationStatus.UNAUTHORIZED, "You are not authorized to return this item.");
        }

        if (deal.getStatus() != DealStatus.ACTIVE) {
            return DealOperationResult.failure(DealOperationStatus.DEAL_NOT_ACTIVE,
                    "Deal is not active (current status: " + deal.getStatus() + ").");
        }

        if (deal.getType() != DealType.LOAN) {
            return DealOperationResult.failure(DealOperationStatus.DEAL_NOT_BORROW,
                    "Only borrowed loan items can be returned.");
        }

        deal.setStatus(DealStatus.RETURNED);
        deal.setClosedDate(LocalDateTime.now());
        dataService.updateDeal(deal);

        // Update listing status back to Live (Subflow S4 Step 3)
        Listing listing = dataService.findListing(deal.getListingId());
        if (listing != null) {
            listing.setStatus(ListingStatus.LIVE);
            dataService.updateListing(listing);
        }

        // Subflow S4 Step 4: Notify seller
        String sellerMsg = buyerUsername.trim() + " returned your borrowed item: " + deal.getListingTitle() + ".";
        dataService.addNotification(new Notification(deal.getSellerUsername(), "Item Returned", sellerMsg, "Loan",
                deal.getId(), NotificationTargetType.DEAL.name()));

        // Subflow S4 Step 5: Inform buyer
        String buyerMsg = "Return recorded for '" + deal.getListingTitle() + "'. The item has been returned to the seller.";
        dataService.addNotification(new Notification(buyerUsername.trim(), "Item Returned", buyerMsg, "Loan",
                deal.getId(), NotificationTargetType.DEAL.name()));

        return DealOperationResult.success(deal, buyerMsg);
    }

    /**
     * Subflow S5: Mark Deal Complete.
     * Allows a buyer to mark an active purchase or giveaway claim complete.
     * Sets deal status to COMPLETED, updates listing status to SOLD or GIVEN,
     * and prompts the buyer to rate the seller (Task 1.10).
     *
     * @param dealId        the deal ID
     * @param buyerUsername the requesting buyer's username
     * @return a {@link DealOperationResult} with promptRating enabled
     */
    public synchronized DealOperationResult markDealComplete(String dealId, String buyerUsername) {
        checkSessionBuyer(buyerUsername);
        if (dealId == null || dealId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return DealOperationResult.failure(DealOperationStatus.INVALID_ACTION, "Deal ID and buyer username are required.");
        }

        Deal deal = dataService.findDeal(dealId.trim());
        if (deal == null) {
            return DealOperationResult.failure(DealOperationStatus.DEAL_NOT_FOUND, "Deal not found.");
        }

        if (!deal.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
            return DealOperationResult.failure(DealOperationStatus.UNAUTHORIZED, "You are not authorized to complete this deal.");
        }

        if (deal.getStatus() != DealStatus.ACTIVE) {
            return DealOperationResult.failure(DealOperationStatus.DEAL_NOT_ACTIVE,
                    "Deal is not active (current status: " + deal.getStatus() + ").");
        }

        deal.setStatus(DealStatus.COMPLETED);
        deal.setClosedDate(LocalDateTime.now());
        dataService.updateDeal(deal);

        // Subflow S5 Step 3: Update listing status
        Listing listing = dataService.findListing(deal.getListingId());
        if (listing != null) {
            if (deal.getType() == DealType.GIVEAWAY) {
                listing.setStatus(ListingStatus.GIVEN);
            } else {
                listing.setStatus(ListingStatus.SOLD);
            }
            dataService.updateListing(listing);
        }

        // Subflow S5 Step 4: Notify seller
        String sellerMsg = buyerUsername.trim() + " marked the deal for '" + deal.getListingTitle() + "' as completed.";
        dataService.addNotification(new Notification(deal.getSellerUsername(), "Deal Completed", sellerMsg, "Deal",
                deal.getId(), NotificationTargetType.DEAL.name()));

        // Subflow S5 Step 5: Prompt buyer to rate seller
        String buyerMsg = "Deal completed successfully! Please take a moment to rate and review the seller.";
        dataService.addNotification(new Notification(buyerUsername.trim(), "Deal Completed", buyerMsg, "Deal",
                deal.getId(), NotificationTargetType.DEAL.name()));

        return DealOperationResult.completeWithRatingPrompt(deal, buyerMsg);
    }

    /**
     * Alternative Flow A4: Auto Return.
     * Evaluates all active loan deals. If the borrow due date has expired,
     * automatically marks the deal as RETURNED, restores the listing to LIVE,
     * and sends notifications to both seller and buyer.
     *
     * @return number of deals auto-returned during this execution
     */
    public synchronized int processAutoReturns() {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;

        List<Deal> allDeals = dataService.getAllDeals();
        for (Deal deal : allDeals) {
            if (deal.getType() == DealType.LOAN
                    && deal.getStatus() == DealStatus.ACTIVE
                    && deal.getDueDate() != null
                    && deal.getDueDate().isBefore(now)) {

                deal.setStatus(DealStatus.RETURNED);
                deal.setClosedDate(now);
                dataService.updateDeal(deal);

                Listing listing = dataService.findListing(deal.getListingId());
                if (listing != null) {
                    listing.setStatus(ListingStatus.LIVE);
                    dataService.updateListing(listing);
                }

                // Step 2: Notify seller
                String sellerMsg = "The borrow period for '" + deal.getListingTitle() + "' has expired and the item was auto-returned to Live.";
                dataService.addNotification(new Notification(deal.getSellerUsername(), "Item Auto-Returned", sellerMsg, "Loan",
                        deal.getId(), NotificationTargetType.DEAL.name()));

                // Step 3: Notify buyer
                String buyerMsg = "Your borrow period for '" + deal.getListingTitle() + "' has expired and has been auto-returned.";
                dataService.addNotification(new Notification(deal.getBuyerUsername(), "Item Auto-Returned", buyerMsg, "Loan",
                        deal.getId(), NotificationTargetType.DEAL.name()));

                count++;
            }
        }

        return count;
    }

    /**
     * Submits a rating and optional written review for a completed deal (Subflow S5 Step 5 & Task 1.10).
     *
     * @param dealId        the deal ID
     * @param buyerUsername the buyer's username
     * @param rating         the star rating (1 to 5)
     * @param comment       optional review text
     * @return a {@link DealOperationResult} describing the outcome
     */
    public synchronized DealOperationResult submitRating(String dealId, String buyerUsername, int rating, String comment) {
        checkSessionBuyer(buyerUsername);
        RateSellerResult res = dataService.getBuyerReviewService().submitRating(dealId, buyerUsername, rating, comment);
        if (res.isSuccess()) {
            return DealOperationResult.success(res.getDeal(), res.getMessage());
        }
        DealOperationStatus opStatus = switch (res.getStatus()) {
            case NO_STAR_RATING -> DealOperationStatus.INVALID_RATING;
            case ALREADY_RATED -> DealOperationStatus.ALREADY_RATED;
            case DEAL_NOT_FOUND -> DealOperationStatus.DEAL_NOT_FOUND;
            case UNAUTHORIZED -> DealOperationStatus.UNAUTHORIZED;
            case DEAL_NOT_ELIGIBLE -> DealOperationStatus.DEAL_NOT_ACTIVE;
            default -> DealOperationStatus.INVALID_ACTION;
        };
        return DealOperationResult.failure(opStatus, res.getMessage());
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
