package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AdminNotificationCategory;
import com.hkust.marketplace.models.CreateListingRequest;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.LendingDurationUnit;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingField;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.MyListingResult;
import com.hkust.marketplace.models.MyListingStatus;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationTargetType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.Photo;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Business logic of use case 2.4 "Manage My Listings" (US-S04): a Seller views all their
 * listings with their status (and the admin's reason for a rejected or taken-down listing),
 * and edits or deletes a listing.
 *
 * <p><b>Rules.</b></p>
 * <ul>
 *   <li>Editing checks the fields with the same rules as a new listing (use case 2.3, see
 *       {@link CreateListingService#validate}). Editing a Live or Rejected listing sends it
 *       back to Pending Review, so that the admin reviews it again; it leaves the
 *       marketplace until then.</li>
 *   <li>Deleting is a <em>soft delete</em> (team decision, meeting 1, item 2.3): the listing
 *       gets the status {@link ListingStatus#REMOVED} and is marked as
 *       {@linkplain Listing#isDeletedBySeller() deleted by the seller}, so the deals, reviews
 *       and chats about it keep their history. It no longer appears in the marketplace nor
 *       on My Listings.</li>
 *   <li>A Reserved, Sold, Lent or Given listing, or one with an active deal, cannot be edited
 *       or deleted (alternative flow A2). A listing taken down by the admin cannot be edited,
 *       but can be deleted from the Seller's list.</li>
 *   <li>If the listing has open offers (pending offers, borrow requests and claims, and
 *       counter offers waiting for the buyer's answer), the edit or delete stops and returns
 *       them, so that the Seller is warned (alternative flow A3). The Seller confirms by
 *       passing their identifiers back: the offers are then declined and each buyer
 *       notified. An offer received after the warning is never declined without a new
 *       warning.</li>
 *   <li>The deal of a Reserved listing can be marked complete by the Seller (F34, the brief's
 *       "mark as sold"): the listing becomes Sold or Given, as when the buyer completes the
 *       deal (use case 1.6), and the buyer is asked to rate the Seller.</li>
 * </ul>
 *
 * <p><b>Access control.</b> Every public instance method first calls
 * {@link SessionManager#requireSeller()}, so it can only be used during an active Seller
 * session (use case 2.2 special requirement), and a Seller can only change their own
 * listings. The static helpers need no session and change nothing.</p>
 *
 * <p><b>Atomicity.</b> Every check and change runs while holding the lock of the data store
 * ({@link DummyDataService}), which buyer offers and the Seller's offer acceptances (use case
 * 2.5) also take. An edit or delete therefore never interleaves with an acceptance: either
 * the deal is made and the listing can no longer be changed, or the listing is changed and
 * its offers are declined. Inside the lock only the data store is called, so no other lock
 * is taken and no deadlock can occur. The photo files of an edit are read before taking the
 * lock.</p>
 *
 * <p>This class does not use JavaFX, so every flow is unit tested without a display.</p>
 */
public class MyListingsService {

    /** Reason stored on the offers declined because the Seller edited the listing. */
    public static final String REASON_LISTING_EDITED = "The seller changed the listing.";

    /** Reason stored on the offers declined because the Seller deleted the listing. */
    public static final String REASON_LISTING_DELETED = "The seller deleted the listing.";

    /**
     * Statuses of a listing whose deal is in progress or completed, which cannot be edited or
     * deleted (alternative flow A2).
     */
    public static final Set<ListingStatus> DEAL_STATUSES =
            EnumSet.of(ListingStatus.RESERVED, ListingStatus.SOLD, ListingStatus.LENT, ListingStatus.GIVEN);

    /** Category of the notifications sent to buyers, shown under "Offers" on their board. */
    private static final String BUYER_NOTIFICATION_CATEGORY = NotificationCategory.OFFERS.getDisplayName();

    /**
     * Category of the notification sent to the buyer when the Seller completes a deal (F34): the
     * same as the buyer's own deal notifications (use case 1.6), shown under "Offers" on their board.
     */
    private static final String DEAL_NOTIFICATION_CATEGORY = "Deal";

    /** Category of the notifications sent to the admin, shown under "Listing Submissions". */
    private static final String ADMIN_NOTIFICATION_CATEGORY = AdminNotificationCategory.LISTING_SUBMISSIONS.getDisplayName();

    /** Units tried to show a lending duration in the edit form, the largest first. */
    private static final List<LendingDurationUnit> UNITS_LARGEST_FIRST = List.of(LendingDurationUnit.WEEKS,
            LendingDurationUnit.DAYS, LendingDurationUnit.HOURS, LendingDurationUnit.MINUTES, LendingDurationUnit.SECONDS);

    /** Newest listing first; listings posted at the same time by identifier. */
    private static final Comparator<Listing> NEWEST_FIRST =
            Comparator.comparing(Listing::getPostedDate, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                    .thenComparing(Listing::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /** Oldest offer first, the order in which the Seller received them. */
    private static final Comparator<Offer> OLDEST_FIRST =
            Comparator.comparing(Offer::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Offer::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /** The store of listings, offers, deals and notifications. */
    private final DummyDataService dataService;

    /**
     * Creates the service on the application's shared data store.
     */
    public MyListingsService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Creates the service on the given data store (used by the tests).
     *
     * @param dataService the store of listings, offers, deals and notifications
     * @throws NullPointerException if {@code dataService} is {@code null}
     */
    public MyListingsService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
    }

    // =====================================================================
    // Basic flow, step 2: the Seller's listings
    // =====================================================================

    /**
     * Returns the logged-in Seller's listings, newest first (basic flow, step 2): every
     * status, including the listings taken down by the admin, but not the ones the Seller
     * deleted. The admin's reason of a rejected or taken-down listing is
     * {@link Listing#getAdminReason()}.
     *
     * @return the listings; empty if the Seller has none (alternative flow A1)
     * @throws SecurityException if no active Seller is logged in
     */
    public List<Listing> getMyListings() {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            dataService.processAutoReturns();
            return dataService.getListingsBySeller(seller.getUsername()).stream()
                    .filter(listing -> !listing.isDeletedBySeller())
                    .sorted(NEWEST_FIRST)
                    .toList();
        }
    }

    /**
     * Returns the open offers on one of the logged-in Seller's listings: the pending offers,
     * borrow requests and claims, and the countered offers waiting for the buyer's answer.
     * These are the offers that editing or deleting the listing would decline.
     *
     * @param listingId identifier of the listing
     * @return the open offers, oldest first; empty if there are none, or if the listing does
     *         not exist or belongs to another Seller
     * @throws SecurityException if no active Seller is logged in
     */
    public List<Offer> getOpenOffers(String listingId) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findOwnListing(listingId, seller);
            return lookup.failure() != null ? List.of() : openOffersOf(lookup.listing());
        }
    }

    // =====================================================================
    // Subflows S1 and S2, first step: can the listing be changed?
    // =====================================================================

    /**
     * Checks that one of the Seller's listings can be edited (subflow S1, Begin Editing),
     * before the edit form is shown. The edit itself checks again, since the listing may
     * change in the meantime.
     *
     * @param listingId identifier of the listing
     * @return a successful result holding the listing, its open offers and what saving will
     *         do; or a failure: not found, another Seller's listing, a deal in progress or
     *         completed (A2), or taken down by the admin
     * @throws SecurityException if no active Seller is logged in
     */
    public MyListingResult checkCanEdit(String listingId) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findChangeableListing(listingId, seller, true);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Listing listing = lookup.listing();
            return MyListingResult.allowed(listing, openOffersOf(listing), editOutcome(listing));
        }
    }

    /**
     * Checks that one of the Seller's listings can be deleted (subflow S2, Begin Deleting),
     * before the Seller is asked to confirm. The delete itself checks again.
     *
     * @param listingId identifier of the listing
     * @return a successful result holding the listing and its open offers; or a failure: not
     *         found, another Seller's listing, or a deal in progress or completed (A2)
     * @throws SecurityException if no active Seller is logged in
     */
    public MyListingResult checkCanDelete(String listingId) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findChangeableListing(listingId, seller, false);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Listing listing = lookup.listing();
            return MyListingResult.allowed(listing, openOffersOf(listing),
                    quote(listing) + " will no longer appear in the marketplace. Its deals, reviews and chats are kept.");
        }
    }

    // =====================================================================
    // Subflow S1: Edit Listing
    // =====================================================================

    /**
     * Saves the changes to one of the Seller's listings (subflow S1, steps 3 to 6), atomically:
     * <ol>
     *   <li>checks that the listing can still be edited (A2);</li>
     *   <li>checks the fields with the same rules as a new listing (A4);</li>
     *   <li>stops if nothing was changed;</li>
     *   <li>stops if the listing has open offers that are not all in {@code confirmedOfferIds}
     *       (A3), returning them so that the Seller can be warned;</li>
     *   <li>saves the changes; a Live or Rejected listing goes back to Pending Review and its
     *       old rejection reason is cleared;</li>
     *   <li>declines the open offers, notifying each buyer, and notifies the admin.</li>
     * </ol>
     * Nothing is changed unless every check passes. The previous checks are reported first:
     * for example a locked listing is reported as such even if the form is also invalid.
     *
     * @param listingId         identifier of the listing
     * @param request           the values of the edit form
     * @param confirmedOfferIds identifiers of the open offers the Seller agreed to decline,
     *                          as returned by a {@link MyListingStatus#PENDING_OFFERS} result;
     *                          {@code null} or empty if the Seller has not confirmed
     * @return a successful result holding the listing and the declined offers, or a failure
     *         telling why nothing was saved
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws SecurityException    if no active Seller is logged in
     */
    public MyListingResult editListing(String listingId, CreateListingRequest request,
                                       Collection<String> confirmedOfferIds) {
        Objects.requireNonNull(request, "request");
        Seller seller = SessionManager.requireSeller();
        // The photo files are read here, before taking the data store lock.
        Map<ListingField, List<String>> errors = CreateListingService.validate(request);
        synchronized (dataService) {
            Lookup lookup = findChangeableListing(listingId, seller, true);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Listing listing = lookup.listing();
            if (!errors.isEmpty()) {
                return MyListingResult.invalidFields(listing, errors);
            }
            if (isUnchanged(listing, request)) {
                return MyListingResult.failure(MyListingStatus.NO_CHANGES, listing,
                        "You did not change anything, so " + quote(listing) + " was not modified.");
            }
            List<Offer> openOffers = openOffersOf(listing);
            if (!isConfirmed(openOffers, confirmedOfferIds)) {
                return MyListingResult.pendingOffers(listing, openOffers, pendingOffersWarning(listing, openOffers,
                        "Saving your changes"));
            }

            String oldTitle = listing.getTitle();
            ListingStatus oldStatus = listing.getStatus();
            applyChanges(listing, request);
            boolean resubmitted = oldStatus == ListingStatus.LIVE || oldStatus == ListingStatus.REJECTED;
            if (resubmitted) {
                listing.setStatus(ListingStatus.PENDING_REVIEW);
            }
            // A rejection reason no longer applies to the changed listing.
            listing.setAdminReason(null);
            dataService.updateListing(listing);

            declineAll(openOffers, REASON_LISTING_EDITED, offer -> "Your " + OfferManagementService.kindOf(offer)
                    + " for '" + oldTitle + "' was declined because " + seller.getDisplayName()
                    + " changed the listing. It will be back on the marketplace once the admin approves it.");
            notifyAdmin(listing, seller, resubmitted);

            String message = resubmitted
                    ? quote(listing) + " has been updated and sent back to the admin for review."
                            + " Buyers will see it again once it is approved."
                    : quote(listing) + " has been updated. It is still awaiting the admin's review.";
            return MyListingResult.success(listing, openOffers, message + declinedSentence(openOffers.size()));
        }
    }

    // =====================================================================
    // Subflow S2: Delete Listing
    // =====================================================================

    /**
     * Deletes one of the Seller's listings (subflow S2, steps 2 to 4), atomically: checks that
     * the listing can still be deleted (A2), stops if it has open offers that are not all in
     * {@code confirmedOfferIds} (A3), then soft-deletes it (status Removed, marked as deleted
     * by the Seller) and declines its open offers, notifying each buyer.
     *
     * @param listingId         identifier of the listing
     * @param confirmedOfferIds identifiers of the open offers the Seller agreed to decline,
     *                          as returned by a {@link MyListingStatus#PENDING_OFFERS} result;
     *                          {@code null} or empty if the Seller has not confirmed
     * @return a successful result holding the listing and the declined offers, or a failure
     *         telling why it was not deleted
     * @throws SecurityException if no active Seller is logged in
     */
    public MyListingResult deleteListing(String listingId, Collection<String> confirmedOfferIds) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findChangeableListing(listingId, seller, false);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Listing listing = lookup.listing();
            List<Offer> openOffers = openOffersOf(listing);
            if (!isConfirmed(openOffers, confirmedOfferIds)) {
                return MyListingResult.pendingOffers(listing, openOffers, pendingOffersWarning(listing, openOffers,
                        "Deleting the listing"));
            }

            listing.setStatus(ListingStatus.REMOVED);
            listing.setDeletedBySeller(true);
            dataService.updateListing(listing);
            declineAll(openOffers, REASON_LISTING_DELETED, offer -> "Your " + OfferManagementService.kindOf(offer)
                    + " for '" + listing.getTitle() + "' was declined because " + seller.getDisplayName()
                    + " deleted the listing.");

            return MyListingResult.success(listing, openOffers, quote(listing)
                    + " has been deleted. It no longer appears in the marketplace." + declinedSentence(openOffers.size()));
        }
    }

    // =====================================================================
    // Mark a Reserved listing as sold or given (F34)
    // =====================================================================

    /**
     * Checks that the deal of one of the Seller's Reserved listings can be marked complete
     * (F34), before the Seller is asked to confirm. The completion itself checks again.
     *
     * @param listingId identifier of the listing
     * @return a successful result holding the listing and a summary of the deal to confirm; or a
     *         failure: not found, another Seller's listing, not Reserved, or no active deal
     * @throws SecurityException if no active Seller is logged in
     */
    public MyListingResult checkCanCompleteDeal(String listingId) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            DealLookup lookup = findDealToComplete(listingId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Listing listing = lookup.listing();
            Deal deal = lookup.deal();
            return MyListingResult.allowed(listing, List.of(), "Confirm that you handed " + quote(listing)
                    + " over to @" + deal.getBuyerUsername() + (deal.getType() == DealType.SALE && deal.getOfferPrice() != null
                    ? " for " + OfferManagementService.formatPrice(deal.getOfferPrice()) : "")
                    + ". The listing becomes " + completedStatusName(deal) + ", the deal is completed and the buyer"
                    + " is asked to rate you. This cannot be undone.");
        }
    }

    /**
     * Marks the deal of one of the Seller's Reserved listings complete (F34: the brief's "mark as
     * sold"), atomically: checks that the listing is still Reserved with an active sale or
     * giveaway deal, then sets the deal to Completed, the listing to Sold (sale) or Given
     * (giveaway), and notifies the buyer, who is asked to rate the Seller (use case 1.10). This
     * has the same effect as the buyer marking the deal complete (use case 1.6, subflow S5), so
     * a deal the buyer never completes no longer leaves the listing Reserved for ever. When the
     * buyer and the Seller complete the deal at the same time, only the first one succeeds.
     *
     * @param listingId identifier of the listing
     * @return a successful result holding the listing, now Sold or Given; or a failure telling
     *         why nothing was changed: not found, another Seller's listing, not Reserved (for
     *         example already completed), or no active deal
     * @throws SecurityException if no active Seller is logged in
     */
    public MyListingResult completeDeal(String listingId) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            DealLookup lookup = findDealToComplete(listingId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Listing listing = lookup.listing();
            Deal deal = lookup.deal();

            deal.setStatus(DealStatus.COMPLETED);
            deal.setClosedDate(LocalDateTime.now());
            dataService.updateDeal(deal);
            listing.setStatus(deal.getType() == DealType.GIVEAWAY ? ListingStatus.GIVEN : ListingStatus.SOLD);
            dataService.updateListing(listing);

            dataService.addNotification(new Notification(deal.getBuyerUsername(), "Deal Completed",
                    seller.getDisplayName() + " marked the deal for '" + listing.getTitle() + "' as completed ("
                            + completedStatusName(deal).toLowerCase(Locale.ENGLISH) + "). Please take a moment to"
                            + " rate and review the seller.",
                    DEAL_NOTIFICATION_CATEGORY, deal.getId(), NotificationTargetType.DEAL.name()));

            return MyListingResult.success(listing, List.of(), quote(listing) + " is now "
                    + completedStatusName(deal) + ". @" + deal.getBuyerUsername()
                    + " has been notified and can now rate you.");
        }
    }

    // =====================================================================
    // Edit form helpers (no session needed, nothing changed)
    // =====================================================================

    /**
     * Tells whether a listing's deal is in progress or completed (alternative flow A2), from
     * its status alone.
     *
     * @param status the status of the listing, may be {@code null}
     * @return {@code true} for Reserved, Sold, Lent and Given
     */
    public static boolean isDealStatus(ListingStatus status) {
        return status != null && DEAL_STATUSES.contains(status);
    }

    /**
     * Tells whether the admin took a listing down (use case 3.7): it is Removed but was not
     * deleted by the Seller.
     *
     * @param listing the listing
     * @return {@code true} if the listing was taken down by the admin
     * @throws NullPointerException if {@code listing} is {@code null}
     */
    public static boolean isTakenDown(Listing listing) {
        return listing.getStatus() == ListingStatus.REMOVED && !listing.isDeletedBySeller();
    }

    /**
     * Fills the edit form with the current values of a listing (subflow S1, step 1). The
     * price is written without trailing zeros (for example {@code 180} or {@code 1200.5}) and
     * the lending duration in its largest whole unit (for example {@code 2 Weeks} or
     * {@code 10 Seconds}). Photos whose stored path cannot be read as a path are left out.
     *
     * @param listing the listing to edit
     * @return the values of the edit form; saving them unchanged changes nothing
     * @throws NullPointerException if {@code listing} is {@code null}
     */
    public static CreateListingRequest toEditRequest(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        String price = listing.getType() == ListingType.SELL && listing.getPrice() != null
                ? formatPriceInput(listing.getPrice()) : "";
        String lendingAmount = "";
        LendingDurationUnit lendingUnit = LendingDurationUnit.DAYS;
        Duration lending = listing.getLendingDuration();
        if (listing.getType() == ListingType.LEND && lending != null && lending.getSeconds() > 0) {
            long seconds = lending.getSeconds();
            for (LendingDurationUnit unit : UNITS_LARGEST_FIRST) {
                long unitSeconds = unit.getLength().getSeconds();
                if (seconds % unitSeconds == 0) {
                    lendingUnit = unit;
                    lendingAmount = String.valueOf(seconds / unitSeconds);
                    break;
                }
            }
        }
        return new CreateListingRequest(listing.getType(), nullToEmpty(listing.getTitle()),
                nullToEmpty(listing.getDescription()), listing.getCategory(), listing.getCondition(),
                price, lendingAmount, lendingUnit, listing.getMeetupLocation(), photoPaths(listing));
    }

    /**
     * Tells whether an edit form holds exactly the current values of a listing. Only the
     * price of a Sell listing and the lending duration of a Lend listing are compared, as
     * only those are saved; texts are compared once trimmed, prices and durations by value,
     * and photos by file, in order.
     *
     * @param listing the listing
     * @param request the values of the edit form
     * @return {@code true} if saving the form would change nothing
     * @throws NullPointerException if an argument is {@code null}
     */
    public static boolean isUnchanged(Listing listing, CreateListingRequest request) {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(request, "request");
        if (request.type() != listing.getType()
                || !nullToEmpty(request.title()).trim().equals(nullToEmpty(listing.getTitle()).trim())
                || !nullToEmpty(request.description()).trim().equals(nullToEmpty(listing.getDescription()).trim())
                || request.category() != listing.getCategory()
                || request.condition() != listing.getCondition()
                || request.meetupLocation() != listing.getMeetupLocation()) {
            return false;
        }
        if (listing.getType() == ListingType.SELL && !samePrice(request.price(), listing.getPrice())) {
            return false;
        }
        if (listing.getType() == ListingType.LEND
                && !sameLendingDuration(request.lendingAmount(), request.lendingUnit(), listing.getLendingDuration())) {
            return false;
        }
        return normalized(request.photos()).equals(normalized(photoPaths(listing)));
    }

    // =====================================================================
    // Internal helpers (always called while holding the data store lock)
    // =====================================================================

    /**
     * A listing found for the logged-in Seller, or the failure explaining why it was not.
     *
     * @param listing the listing found, {@code null} on failure
     * @param failure the failure, {@code null} if the listing was found
     */
    private record Lookup(Listing listing, MyListingResult failure) {

        /**
         * Builds a failed lookup.
         *
         * @param status  why the lookup failed
         * @param listing the listing concerned, {@code null} to reveal nothing about it
         * @param message message for the Seller
         * @return the failed lookup
         */
        static Lookup failed(MyListingStatus status, Listing listing, String message) {
            return new Lookup(null, MyListingResult.failure(status, listing, message));
        }
    }

    /**
     * Finds one of the Seller's listings that they have not deleted.
     *
     * @param listingId identifier of the listing
     * @param seller    the logged-in Seller
     * @return the listing, or a failure if the identifier is empty or unknown, if the
     *         listing belongs to another Seller, or if the Seller already deleted it
     */
    private Lookup findOwnListing(String listingId, Seller seller) {
        if (listingId == null || listingId.isBlank()) {
            return Lookup.failed(MyListingStatus.LISTING_NOT_FOUND, null, "No listing was selected.");
        }
        Listing listing = dataService.findListing(listingId.trim());
        if (listing == null) {
            return Lookup.failed(MyListingStatus.LISTING_NOT_FOUND, null, "This listing no longer exists.");
        }
        if (!seller.getUsername().equalsIgnoreCase(listing.getSellerUsername())) {
            return Lookup.failed(MyListingStatus.UNAUTHORIZED, null, "This listing is not one of your listings.");
        }
        if (listing.isDeletedBySeller()) {
            return Lookup.failed(MyListingStatus.LISTING_NOT_FOUND, null, "You have already deleted this listing.");
        }
        return new Lookup(listing, null);
    }

    /**
     * Finds one of the Seller's listings that can still be changed (alternative flow A2).
     *
     * @param listingId identifier of the listing
     * @param seller    the logged-in Seller
     * @param editing   {@code true} to edit it, {@code false} to delete it; a listing taken
     *                  down by the admin can be deleted but not edited
     * @return the listing, or a failure as for {@link #findOwnListing}, or
     *         {@link MyListingStatus#DEAL_IN_PROGRESS_OR_COMPLETED}, or
     *         {@link MyListingStatus#TAKEN_DOWN}
     */
    private Lookup findChangeableListing(String listingId, Seller seller, boolean editing) {
        Lookup lookup = findOwnListing(listingId, seller);
        if (lookup.failure() != null) {
            return lookup;
        }
        Listing listing = lookup.listing();
        ListingStatus status = listing.getStatus();
        if (isDealStatus(status) || hasActiveDeal(listing)) {
            boolean completed = status == ListingStatus.SOLD || status == ListingStatus.GIVEN;
            return Lookup.failed(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, listing, quote(listing)
                    + " cannot be " + (editing ? "edited" : "deleted") + " because "
                    + (completed ? "its deal is completed" : "a deal on it is in progress")
                    + " (status: " + listing.getAvailabilityDisplay() + ").");
        }
        if (editing && isTakenDown(listing)) {
            return Lookup.failed(MyListingStatus.TAKEN_DOWN, listing, quote(listing)
                    + " was taken down by the admin and can no longer be edited. You can delete it from your listings.");
        }
        return new Lookup(listing, null);
    }

    /**
     * A Reserved listing of the logged-in Seller with its active deal, or the failure explaining
     * why its deal cannot be marked complete.
     *
     * @param listing the listing, {@code null} on failure
     * @param deal    its active sale or giveaway deal, {@code null} on failure
     * @param failure the failure, {@code null} if the deal can be completed
     */
    private record DealLookup(Listing listing, Deal deal, MyListingResult failure) {
    }

    /**
     * Finds the active deal of one of the Seller's Reserved listings (F34). Called while holding
     * the data store lock.
     *
     * @param listingId identifier of the listing
     * @param seller    the logged-in Seller
     * @return the listing and its deal, or the failure: as for {@link #findOwnListing},
     *         {@link MyListingStatus#NOT_RESERVED} or {@link MyListingStatus#NO_ACTIVE_DEAL}
     */
    private DealLookup findDealToComplete(String listingId, Seller seller) {
        Lookup lookup = findOwnListing(listingId, seller);
        if (lookup.failure() != null) {
            return new DealLookup(null, null, lookup.failure());
        }
        Listing listing = lookup.listing();
        if (listing.getStatus() != ListingStatus.RESERVED) {
            return new DealLookup(null, null, MyListingResult.failure(MyListingStatus.NOT_RESERVED, listing,
                    notReservedMessage(listing)));
        }
        Deal deal = dataService.getAllDeals().stream()
                .filter(d -> d.isActive() && listing.getId().equals(d.getListingId())
                        && seller.getUsername().equalsIgnoreCase(d.getSellerUsername())
                        && (d.getType() == DealType.SALE || d.getType() == DealType.GIVEAWAY))
                .findFirst().orElse(null);
        if (deal == null) {
            return new DealLookup(null, null, MyListingResult.failure(MyListingStatus.NO_ACTIVE_DEAL, listing,
                    quote(listing) + " has no deal in progress with a buyer, so there is nothing to complete."));
        }
        return new DealLookup(listing, deal, null);
    }

    /**
     * Explains why a listing that is not Reserved has no deal to complete.
     *
     * @param listing the listing
     * @return the explanation for the Seller
     */
    private static String notReservedMessage(Listing listing) {
        return switch (listing.getStatus()) {
            case SOLD, GIVEN -> quote(listing) + " is already " + listing.getAvailabilityDisplay()
                    + ": its deal was completed.";
            case LENT -> quote(listing) + " is lent: a loan ends when the buyer returns the item,"
                    + " or automatically when the borrow period ends.";
            default -> "Only a Reserved listing has a deal to complete; " + quote(listing) + " is "
                    + listing.getAvailabilityDisplay() + ".";
        };
    }

    /**
     * Names the status a listing gets when its deal is completed.
     *
     * @param deal the deal
     * @return {@code Given} for a giveaway, {@code Sold} otherwise
     */
    private static String completedStatusName(Deal deal) {
        return deal.getType() == DealType.GIVEAWAY ? "Given" : "Sold";
    }

    /**
     * Tells whether a deal on the listing is still active. A Live listing normally has none,
     * but the check keeps a listing with an active deal locked whatever its status says.
     *
     * @param listing the listing
     * @return {@code true} if a deal on the listing is active
     */
    private boolean hasActiveDeal(Listing listing) {
        return dataService.getAllDeals().stream()
                .anyMatch(deal -> deal.isActive() && listing.getId().equals(deal.getListingId()));
    }

    /**
     * Returns the open offers on a listing: pending, or countered and waiting for the buyer.
     *
     * @param listing the listing
     * @return the open offers, oldest first
     */
    private List<Offer> openOffersOf(Listing listing) {
        return dataService.getOffersForListing(listing.getId()).stream()
                .filter(offer -> offer.isPending() || offer.isCountered())
                .sorted(OLDEST_FIRST)
                .toList();
    }

    /**
     * Tells whether the Seller agreed to decline every open offer (alternative flow A3).
     *
     * @param openOffers        the open offers of the listing
     * @param confirmedOfferIds the identifiers the Seller agreed to decline, may be {@code null}
     * @return {@code true} if there is no open offer, or every one of them was confirmed
     */
    private static boolean isConfirmed(List<Offer> openOffers, Collection<String> confirmedOfferIds) {
        if (openOffers.isEmpty()) {
            return true;
        }
        if (confirmedOfferIds == null || confirmedOfferIds.isEmpty()) {
            return false;
        }
        Set<String> confirmed = new HashSet<>();
        for (String id : confirmedOfferIds) {
            if (id != null) {
                confirmed.add(id.trim());
            }
        }
        return openOffers.stream().allMatch(offer -> confirmed.contains(offer.getId()));
    }

    /**
     * Builds the warning of alternative flow A3.
     *
     * @param listing    the listing
     * @param openOffers its open offers, at least one
     * @param action     what the Seller is doing, for example {@code "Deleting the listing"}
     * @return the warning
     */
    private static String pendingOffersWarning(Listing listing, List<Offer> openOffers, String action) {
        int count = openOffers.size();
        return quote(listing) + " has " + count + (count == 1 ? " open offer, borrow request or claim"
                : " open offers, borrow requests or claims") + ". " + action + " will decline "
                + (count == 1 ? "it" : "them") + " and notify the " + (count == 1 ? "buyer" : "buyers") + ".";
    }

    /**
     * Saves the values of a valid edit form into the listing. Only the value of the chosen
     * type is kept: the price of a Sell listing, the lending duration of a Lend listing.
     *
     * @param listing the listing
     * @param request the valid values of the edit form
     */
    private static void applyChanges(Listing listing, CreateListingRequest request) {
        listing.setType(request.type());
        listing.setTitle(request.title().trim());
        listing.setDescription(request.description().trim());
        listing.setCategory(request.category());
        listing.setCondition(request.condition());
        listing.setPrice(request.type() == ListingType.SELL ? CreateListingService.toPrice(request.price()) : null);
        listing.setLendingDuration(request.type() == ListingType.LEND
                ? CreateListingService.toLendingDuration(request.lendingAmount(), request.lendingUnit()) : null);
        listing.setMeetupLocation(request.meetupLocation());
        listing.setPhotos(CreateListingService.toPhotos(listing.getId(), request.photos()));
    }

    /**
     * Declines offers, with their counter offers, and notifies each buyer.
     *
     * @param offers  the offers to decline
     * @param reason  the reason stored on each offer
     * @param message builds the notification sent to the buyer of an offer
     */
    private void declineAll(List<Offer> offers, String reason, Function<Offer, String> message) {
        for (Offer offer : offers) {
            offer.setStatus(OfferStatus.DECLINED);
            offer.setDeclineReason(reason);
            if (offer.getCounterOffer() != null) {
                offer.getCounterOffer().setStatus(OfferStatus.DECLINED);
            }
            dataService.addNotification(new Notification(offer.getBuyerUsername(), "Offer Declined",
                    message.apply(offer), BUYER_NOTIFICATION_CATEGORY, offer.getId(), NotificationTargetType.OFFER.name()));
        }
    }

    /**
     * Tells the admin that an edited listing is awaiting review, with a link to it.
     *
     * @param listing     the edited listing
     * @param seller      the logged-in Seller
     * @param resubmitted {@code true} if it was Live or Rejected and went back to review
     */
    private void notifyAdmin(Listing listing, Seller seller, boolean resubmitted) {
        String title = (resubmitted ? "Listing Resubmitted: " : "Listing Updated: ") + listing.getTitle();
        String message = "Seller @" + seller.getUsername() + " edited '" + listing.getTitle() + "' ("
                + listing.getTypeDisplay() + ")" + (resubmitted
                ? ". It is back in the review queue."
                : " while it was awaiting review. Please review the latest version.");
        dataService.addNotification(new Notification(DummyDataService.SEEDED_ADMIN_USERNAME, title, message,
                ADMIN_NOTIFICATION_CATEGORY, listing.getId(), NotificationTargetType.LISTING.name()));
    }

    /**
     * Tells the Seller what saving the edit form will do to the listing.
     *
     * @param listing the listing about to be edited
     * @return the explanation, depending on its status
     */
    private static String editOutcome(Listing listing) {
        return switch (listing.getStatus()) {
            case LIVE -> "Saving your changes sends the listing back to the admin for review."
                    + " It leaves the marketplace until it is approved again.";
            case REJECTED -> "Saving your changes sends the listing to the admin for a new review.";
            default -> "The listing is awaiting the admin's review, who will see your changes.";
        };
    }

    /**
     * Builds the sentence telling the Seller how many open offers were declined.
     *
     * @param count number of declined offers
     * @return the sentence, starting with a space, or an empty string if none was declined
     */
    private static String declinedSentence(int count) {
        if (count == 0) {
            return "";
        }
        return count == 1
                ? " 1 open offer was declined and its buyer notified."
                : " " + count + " open offers were declined and their buyers notified.";
    }

    /**
     * Quotes the title of a listing for messages.
     *
     * @param listing the listing
     * @return the title between double quotes
     */
    private static String quote(Listing listing) {
        return "\"" + listing.getTitle() + "\"";
    }

    /**
     * Writes a stored price as the Seller would type it, without trailing zeros.
     *
     * @param price the price in HK$
     * @return for example {@code 180}, {@code 1200.5} or {@code 99.99}
     */
    private static String formatPriceInput(double price) {
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * Tells whether a typed price is the stored price.
     *
     * @param text   the typed price
     * @param stored the stored price, may be {@code null}
     * @return {@code true} if the typed price is valid and equal to the stored one
     */
    private static boolean samePrice(String text, Double stored) {
        if (stored == null || CreateListingService.checkPrice(text) != null) {
            return false;
        }
        return BigDecimal.valueOf(CreateListingService.toPrice(text)).compareTo(BigDecimal.valueOf(stored)) == 0;
    }

    /**
     * Tells whether a typed lending duration is the stored one.
     *
     * @param amount the typed number of units
     * @param unit   the chosen unit
     * @param stored the stored lending duration, may be {@code null}
     * @return {@code true} if the typed duration is valid and equal to the stored one
     */
    private static boolean sameLendingDuration(String amount, LendingDurationUnit unit, Duration stored) {
        if (stored == null || CreateListingService.checkLendingDuration(amount, unit) != null) {
            return false;
        }
        return CreateListingService.toLendingDuration(amount, unit).equals(stored);
    }

    /**
     * Returns the photo files of a listing, in display order.
     *
     * @param listing the listing
     * @return the paths of its photos; photos without a readable path are left out
     */
    private static List<Path> photoPaths(Listing listing) {
        List<Path> paths = new ArrayList<>();
        for (Photo photo : listing.getPhotos()) {
            if (photo == null || photo.getImagePath() == null || photo.getImagePath().isBlank()) {
                continue;
            }
            try {
                paths.add(Path.of(photo.getImagePath()));
            } catch (InvalidPathException e) {
                // Not a file path (for example a placeholder text): it cannot be shown in the form.
            }
        }
        return paths;
    }

    /**
     * Returns the absolute, normalised form of paths, to compare photos by file.
     *
     * @param paths the paths
     * @return the normalised paths, in the same order
     */
    private static List<Path> normalized(List<Path> paths) {
        return paths.stream().map(path -> path.toAbsolutePath().normalize()).toList();
    }

    /**
     * Replaces {@code null} with an empty string.
     *
     * @param text the text, may be {@code null}
     * @return the text, or an empty string
     */
    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
