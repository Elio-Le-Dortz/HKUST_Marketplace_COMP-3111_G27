package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.NotificationTargetType;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.OfferManagementResult;
import com.hkust.marketplace.models.OfferManagementStatus;
import com.hkust.marketplace.models.OfferStatus;
import com.hkust.marketplace.models.OfferType;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Business logic of use case 2.5 "Manage Offers And Requests" (US-S05): a Seller lists the
 * pending offers, borrow requests and claims received on their listings, and accepts,
 * declines or counters them, or opens the chat with the buyer.
 *
 * <p><b>Access control.</b> Every public method first calls
 * {@link SessionManager#requireSeller()}, so it can only be used during an active Seller
 * session (use case 2.2 special requirement). A Seller can only handle the offers made on
 * their own listings.</p>
 *
 * <p><b>Atomicity.</b> Every action runs while holding the lock of the data store
 * ({@link DummyDataService}), which the buyer actions (making, withdrawing an offer,
 * answering a counter offer) also take. An action therefore sees and changes the offers,
 * the listing and the deals as one unit: when two acceptances on the same listing happen at
 * the same time, only the first one succeeds (special requirement of use case 2.5). Inside
 * the lock, only the data store is called, so no other lock is taken and no deadlock can
 * occur.</p>
 *
 * <p><b>Counter offers.</b> A counter offer is stored, as the buyer side expects it, as a
 * new {@link Offer} attached to the original one with {@link Offer#setCounterOffer}, and the
 * original offer gets the status {@link OfferStatus#COUNTERED}. The buyer then accepts or
 * declines it from their My Deals screen (use case 1.6).</p>
 *
 * <p>This class does not use JavaFX, so every flow is unit tested without a display.</p>
 */
public class OfferManagementService {

    /** Longest reason, in characters, that a Seller can give when declining an offer. */
    public static final int MAX_DECLINE_REASON_LENGTH = 300;

    /** Highest counter price, in HK$, a Seller can propose. */
    public static final double MAX_COUNTER_PRICE = 1_000_000.0;

    /** Shortest counter borrow period, in seconds (the same minimum as a buyer's request). */
    public static final long MIN_BORROW_SECONDS = ActOnListingService.MIN_BORROW_DURATION_SECONDS;

    /**
     * How many seconds in the past the start of a counter borrow period may be, so that the
     * time the Seller spends filling in the form does not make the dates invalid.
     */
    public static final long PAST_START_TOLERANCE_SECONDS = 30;

    /** Suffix added to the identifier of an offer to build the identifier of its counter offer. */
    public static final String COUNTER_ID_SUFFIX = "-C";

    /** Reason stored on the offers declined because another offer on the listing was accepted. */
    public static final String REASON_OTHER_OFFER_ACCEPTED = "Another offer on this listing was accepted.";

    /** Reason stored on the offers declined because their listing is no longer Live. */
    public static final String REASON_LISTING_UNAVAILABLE = "The listing is no longer available.";

    /** Reason stored on the offers declined because their buyer's account is no longer active. */
    public static final String REASON_BUYER_UNAVAILABLE = "The buyer's account is no longer active.";

    /** Category of the notifications sent to buyers, shown under "Offers" on their board. */
    private static final String NOTIFICATION_CATEGORY = NotificationCategory.OFFERS.getDisplayName();

    /** Format of the dates shown to the users, with seconds because a loan can last 5 seconds. */
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.ENGLISH);

    /** Oldest offer first, so that the first buyer to ask is the first one the Seller sees. */
    private static final Comparator<Offer> OLDEST_FIRST =
            Comparator.comparing(Offer::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Offer::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /** The store of offers, listings, deals and notifications. */
    private final DummyDataService dataService;

    /**
     * Creates the service on the application's shared data store.
     */
    public OfferManagementService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Creates the service on the given data store (used by the tests).
     *
     * @param dataService the store of offers, listings, deals and notifications
     * @throws NullPointerException if {@code dataService} is {@code null}
     */
    public OfferManagementService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
    }

    // =====================================================================
    // Basic flow, step 2: offers to handle
    // =====================================================================

    /**
     * Returns the pending offers, borrow requests and claims received on the logged-in
     * Seller's listings (basic flow, step 2), oldest first.
     *
     * @return the pending offers; empty if there is nothing to handle (alternative flow A1)
     * @throws SecurityException if no active Seller is logged in
     */
    public List<Offer> getPendingOffers() {
        Seller seller = SessionManager.requireSeller();
        return offersWithStatus(seller, OfferStatus.PENDING);
    }

    /**
     * Returns the offers the logged-in Seller has countered and that are waiting for the
     * buyer's answer, oldest first. They cannot be handled again until the buyer answers.
     *
     * @return the countered offers, each holding its counter offer
     * @throws SecurityException if no active Seller is logged in
     */
    public List<Offer> getCounteredOffers() {
        Seller seller = SessionManager.requireSeller();
        return offersWithStatus(seller, OfferStatus.COUNTERED);
    }

    // =====================================================================
    // Subflow S1: Accept Offer
    // =====================================================================

    /**
     * Accepts a pending offer, borrow request or claim (subflow S1), atomically:
     * <ol>
     *   <li>checks that the offer is still pending (A2) and its listing still Live (A3);</li>
     *   <li>records an active deal, which appears on the buyer's My Deals screen;</li>
     *   <li>sets the listing to Reserved (Sell, Give Away) or Lent (Lend);</li>
     *   <li>declines every other pending or countered offer on the listing and notifies those
     *       buyers;</li>
     *   <li>notifies the buyer that the offer was accepted.</li>
     * </ol>
     *
     * <p>If the listing is no longer Live, or the buyer's account is no longer active, the
     * offer is declined instead and the buyer notified. A borrow request whose period has
     * already ended is not accepted but stays pending, so that the Seller can counter it.</p>
     *
     * @param offerId identifier of the offer to accept
     * @return a successful result holding the offer, the deal and the number of other offers
     *         declined, or a failure telling why the offer could not be accepted
     * @throws SecurityException if no active Seller is logged in
     */
    public OfferManagementResult acceptOffer(String offerId) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findPendingOffer(offerId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Offer offer = lookup.offer();
            Listing listing = dataService.findListing(offer.getListingId());
            OfferManagementResult unavailable = declineIfImpossible(offer, listing, seller);
            if (unavailable != null) {
                return unavailable;
            }
            LocalDateTime now = LocalDateTime.now();
            if (offer.getType() == OfferType.BORROW_REQUEST && offer.getEndDate() != null
                    && !offer.getEndDate().isAfter(now)) {
                return OfferManagementResult.failure(OfferManagementStatus.BORROW_PERIOD_ENDED, offer,
                        "The borrow period requested by @" + offer.getBuyerUsername()
                                + " has already ended. Counter the request with new dates or decline it.");
            }

            Deal deal = recordDeal(offer, listing, now);
            offer.setStatus(OfferStatus.ACCEPTED);
            ListingStatus newStatus = listing.getType() == ListingType.LEND ? ListingStatus.LENT : ListingStatus.RESERVED;
            listing.setStatus(newStatus);
            int othersDeclined = declineCompetingOffers(listing, offer, seller);

            notifyBuyer(offer, "Offer Accepted", seller.getDisplayName() + " accepted your " + kindOf(offer)
                            + " for '" + titleOf(offer, listing) + "' (" + describeTerms(offer)
                            + "). The deal is now on your My Deals screen.",
                    deal.getId(), NotificationTargetType.DEAL);

            String message = "You accepted the " + kindOf(offer) + " from @" + offer.getBuyerUsername()
                    + " for '" + titleOf(offer, listing) + "' (" + describeTerms(offer) + "). The listing is now "
                    + listing.getAvailabilityDisplay() + "." + othersDeclinedSentence(othersDeclined);
            return OfferManagementResult.accepted(offer, deal, othersDeclined, message);
        }
    }

    // =====================================================================
    // Subflow S2: Decline Offer
    // =====================================================================

    /**
     * Declines a pending offer, borrow request or claim (subflow S2). The listing keeps its
     * status and the buyer is notified, with the reason if one is given.
     *
     * @param offerId identifier of the offer to decline
     * @param reason  optional reason for the buyer; {@code null} or blank means no reason.
     *                At most {@value #MAX_DECLINE_REASON_LENGTH} characters once trimmed
     * @return a successful result holding the declined offer, or a failure telling why it
     *         could not be declined
     * @throws SecurityException if no active Seller is logged in
     */
    public OfferManagementResult declineOffer(String offerId, String reason) {
        Seller seller = SessionManager.requireSeller();
        String cleanReason = reason == null ? "" : reason.trim();
        synchronized (dataService) {
            Lookup lookup = findPendingOffer(offerId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Offer offer = lookup.offer();
            if (cleanReason.codePointCount(0, cleanReason.length()) > MAX_DECLINE_REASON_LENGTH) {
                return OfferManagementResult.failure(OfferManagementStatus.INVALID_REASON, offer,
                        "The reason cannot be longer than " + MAX_DECLINE_REASON_LENGTH + " characters.");
            }

            markDeclined(offer, cleanReason.isEmpty() ? null : cleanReason);
            Listing listing = dataService.findListing(offer.getListingId());
            notifyBuyer(offer, "Offer Declined", seller.getDisplayName() + " declined your " + kindOf(offer)
                            + " for '" + titleOf(offer, listing) + "'."
                            + (cleanReason.isEmpty() ? "" : " Reason: " + cleanReason),
                    offer.getId(), NotificationTargetType.OFFER);
            return OfferManagementResult.success(offer, "You declined the " + kindOf(offer) + " from @"
                    + offer.getBuyerUsername() + " for '" + titleOf(offer, listing) + "'. The buyer has been notified.");
        }
    }

    // =====================================================================
    // Subflow S3: Counter Offer
    // =====================================================================

    /**
     * Counters a pending price offer on a Sell listing with another price (subflow S3).
     * The price is rounded to the cent. The offer becomes Countered and the buyer is
     * notified, and can then accept or decline the counter offer.
     *
     * @param offerId identifier of the price offer to counter
     * @param price   the counter price in HK$: positive, at most {@value #MAX_COUNTER_PRICE},
     *                and different from the price the buyer offered
     * @return a successful result holding the countered offer, or a failure telling why it
     *         could not be countered (A4 for an invalid price)
     * @throws SecurityException if no active Seller is logged in
     */
    public OfferManagementResult counterWithPrice(String offerId, Double price) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findPendingOffer(offerId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Offer offer = lookup.offer();
            if (offer.getType() != OfferType.PRICE_OFFER) {
                return counterNotAllowed(offer, "A borrow request is countered with new borrow dates, not with a price.");
            }
            Listing listing = dataService.findListing(offer.getListingId());
            OfferManagementResult unavailable = declineIfImpossible(offer, listing, seller);
            if (unavailable != null) {
                return unavailable;
            }
            String error = counterPriceError(price, offer.getPrice());
            if (error != null) {
                return OfferManagementResult.failure(OfferManagementStatus.INVALID_COUNTER_PRICE, offer, error);
            }

            Offer counter = recordCounter(offer, roundToCents(price), null, null);
            notifyBuyer(offer, "Counter-Offer Received", seller.getDisplayName() + " countered your offer of "
                            + describeTerms(offer) + " for '" + titleOf(offer, listing) + "' with "
                            + describeTerms(counter) + ". Accept or decline it on your My Deals screen.",
                    offer.getId(), NotificationTargetType.OFFER);
            return OfferManagementResult.success(offer, "Counter offer of " + describeTerms(counter)
                    + " sent to @" + offer.getBuyerUsername() + ".");
        }
    }

    /**
     * Counters a pending borrow request on a Lend listing with other borrow dates
     * (subflow S3). The request becomes Countered and the buyer is notified, and can then
     * accept or decline the counter offer.
     *
     * @param offerId identifier of the borrow request to counter
     * @param start   start of the counter borrow period; not in the past (a tolerance of
     *                {@value #PAST_START_TOLERANCE_SECONDS} seconds is allowed)
     * @param end     end of the counter borrow period: at least {@value #MIN_BORROW_SECONDS}
     *                seconds after the start, and not longer than the listing's lending duration
     * @return a successful result holding the countered request, or a failure telling why it
     *         could not be countered (A4 for invalid dates)
     * @throws SecurityException if no active Seller is logged in
     */
    public OfferManagementResult counterWithDates(String offerId, LocalDateTime start, LocalDateTime end) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findPendingOffer(offerId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Offer offer = lookup.offer();
            if (offer.getType() != OfferType.BORROW_REQUEST) {
                return counterNotAllowed(offer, "A price offer is countered with a new price, not with borrow dates.");
            }
            Listing listing = dataService.findListing(offer.getListingId());
            OfferManagementResult unavailable = declineIfImpossible(offer, listing, seller);
            if (unavailable != null) {
                return unavailable;
            }
            String error = counterDatesError(start, end, listing.getLendingDuration(), offer, LocalDateTime.now());
            if (error != null) {
                return OfferManagementResult.failure(OfferManagementStatus.INVALID_COUNTER_DATES, offer, error);
            }

            Offer counter = recordCounter(offer, null, start, end);
            notifyBuyer(offer, "Counter-Offer Received", seller.getDisplayName()
                            + " proposed other borrow dates for '" + titleOf(offer, listing) + "': "
                            + describeTerms(counter) + ". Accept or decline them on your My Deals screen.",
                    offer.getId(), NotificationTargetType.OFFER);
            return OfferManagementResult.success(offer, "Counter offer sent to @" + offer.getBuyerUsername()
                    + ": " + describeTerms(counter) + ".");
        }
    }

    // =====================================================================
    // Basic flow, step 3.5: Open Chat
    // =====================================================================

    /**
     * Opens the chat thread between the logged-in Seller and the buyer of an offer, about the
     * offer's listing (basic flow, step 3.5). The thread is created on first use and reused
     * afterwards. This works whatever the status of the offer, so that a Seller can still
     * talk to a buyer after a decline or a counter offer.
     *
     * @param offerId identifier of the offer
     * @return a successful result holding the offer and the chat thread, or a failure if the
     *         offer does not exist or belongs to another Seller
     * @throws SecurityException if no active Seller is logged in
     */
    public OfferManagementResult openChat(String offerId) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            Lookup lookup = findOwnOffer(offerId, seller);
            if (lookup.failure() != null) {
                return lookup.failure();
            }
            Offer offer = lookup.offer();
            ChatThread thread = dataService.getOrCreateChatThread(
                    offer.getListingId(), offer.getBuyerUsername(), seller.getUsername());
            if (thread.getListingTitle() == null) {
                // The listing was deleted: keep the title known by the offer.
                thread.setListingTitle(offer.getListingTitle());
            }
            return OfferManagementResult.chatOpened(offer, thread);
        }
    }

    // =====================================================================
    // Display helpers (also used by the Seller dashboard)
    // =====================================================================

    /**
     * Describes the terms of an offer for display: the price of a price offer, the period of
     * a borrow request, or "Free claim" for a claim.
     *
     * @param offer the offer or counter offer to describe, may be {@code null}
     * @return the description, or an empty string for {@code null} or an offer without type
     */
    public static String describeTerms(Offer offer) {
        if (offer == null || offer.getType() == null) {
            return "";
        }
        return switch (offer.getType()) {
            case PRICE_OFFER -> offer.getPrice() == null ? "no price" : formatPrice(offer.getPrice());
            case BORROW_REQUEST -> formatDateTime(offer.getStartDate()) + " to " + formatDateTime(offer.getEndDate());
            case CLAIM -> "Free claim";
        };
    }

    /**
     * Formats an amount as shown in the marketplace, for example {@code HK$ 1,250.50}.
     *
     * @param amount the amount in HK$
     * @return the formatted amount
     */
    public static String formatPrice(double amount) {
        return String.format(Locale.ENGLISH, "HK$ %,.2f", amount);
    }

    /**
     * Formats a date and time with seconds, for example {@code 05 Nov 2026 14:30:00}.
     *
     * @param dateTime the date and time, may be {@code null}
     * @return the formatted value, or {@code "?"} for {@code null}
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "?" : dateTime.format(DATE_TIME_FORMAT);
    }

    /**
     * Formats a duration in its largest whole unit, for example {@code 7 days},
     * {@code 2 hours} or {@code 90 seconds}.
     *
     * @param duration the duration, may be {@code null}
     * @return the formatted duration, or {@code "no limit"} for {@code null}
     */
    public static String formatDuration(Duration duration) {
        if (duration == null) {
            return "no limit";
        }
        long seconds = duration.getSeconds();
        if (seconds != 0 && seconds % 86_400 == 0) {
            return plural(seconds / 86_400, "day");
        }
        if (seconds != 0 && seconds % 3_600 == 0) {
            return plural(seconds / 3_600, "hour");
        }
        if (seconds != 0 && seconds % 60 == 0) {
            return plural(seconds / 60, "minute");
        }
        return plural(seconds, "second");
    }

    /**
     * Names the kind of an offer for messages: "offer", "borrow request" or "claim".
     *
     * @param offer the offer, may be {@code null}
     * @return the kind of the offer, "offer" if unknown
     */
    public static String kindOf(Offer offer) {
        if (offer == null || offer.getType() == null) {
            return "offer";
        }
        return switch (offer.getType()) {
            case PRICE_OFFER -> "offer";
            case BORROW_REQUEST -> "borrow request";
            case CLAIM -> "claim";
        };
    }

    // =====================================================================
    // Internal helpers (always called while holding the data store lock)
    // =====================================================================

    /**
     * An offer found for the logged-in Seller, or the failure explaining why it was not.
     *
     * @param offer   the offer found, {@code null} on failure
     * @param failure the failure, {@code null} if the offer was found
     */
    private record Lookup(Offer offer, OfferManagementResult failure) {

        /**
         * Builds a failed lookup that reveals nothing about the offer.
         *
         * @param status  why the lookup failed
         * @param message message for the Seller
         * @return the failed lookup
         */
        static Lookup failed(OfferManagementStatus status, String message) {
            return new Lookup(null, OfferManagementResult.failure(status, message));
        }
    }

    /**
     * Returns the Seller's offers that have the given status, oldest first.
     *
     * @param seller the logged-in Seller
     * @param status the wanted status
     * @return the matching offers
     */
    private List<Offer> offersWithStatus(Seller seller, OfferStatus status) {
        synchronized (dataService) {
            return dataService.getOffersForSeller(seller.getUsername()).stream()
                    .filter(offer -> offer.getStatus() == status)
                    .sorted(OLDEST_FIRST)
                    .toList();
        }
    }

    /**
     * Finds an offer made on one of the Seller's listings, whatever its status.
     *
     * @param offerId identifier of the offer
     * @param seller  the logged-in Seller
     * @return the offer, or a failure if the identifier is empty or unknown, or if the offer
     *         belongs to another Seller
     */
    private Lookup findOwnOffer(String offerId, Seller seller) {
        if (offerId == null || offerId.isBlank()) {
            return Lookup.failed(OfferManagementStatus.OFFER_NOT_FOUND, "No offer was selected.");
        }
        Offer offer = dataService.findOffer(offerId.trim());
        if (offer == null) {
            return Lookup.failed(OfferManagementStatus.OFFER_NOT_FOUND, "This offer no longer exists.");
        }
        if (!seller.getUsername().equalsIgnoreCase(offer.getSellerUsername())) {
            return Lookup.failed(OfferManagementStatus.UNAUTHORIZED,
                    "This offer was not made on one of your listings.");
        }
        return new Lookup(offer, null);
    }

    /**
     * Finds a pending offer made on one of the Seller's listings (alternative flow A2 if it
     * is no longer pending).
     *
     * @param offerId identifier of the offer
     * @param seller  the logged-in Seller
     * @return the offer, or a failure as for {@link #findOwnOffer}, or
     *         {@link OfferManagementStatus#OFFER_NOT_PENDING} if it was already handled
     */
    private Lookup findPendingOffer(String offerId, Seller seller) {
        Lookup lookup = findOwnOffer(offerId, seller);
        if (lookup.failure() != null || lookup.offer().isPending()) {
            return lookup;
        }
        Offer offer = lookup.offer();
        return new Lookup(null, OfferManagementResult.failure(
                OfferManagementStatus.OFFER_NOT_PENDING, offer, notPendingMessage(offer)));
    }

    /**
     * Explains why an offer can no longer be handled (alternative flow A2).
     *
     * @param offer the offer that is not pending
     * @return the explanation for the Seller
     */
    private static String notPendingMessage(Offer offer) {
        String kind = kindOf(offer);
        OfferStatus status = offer.getStatus();
        if (status == OfferStatus.WITHDRAWN) {
            return "The buyer has withdrawn this " + kind + ".";
        }
        if (status == OfferStatus.ACCEPTED) {
            return "This " + kind + " has already been accepted.";
        }
        if (status == OfferStatus.DECLINED) {
            return "This " + kind + " has already been declined.";
        }
        if (status == OfferStatus.COUNTERED) {
            return "You already countered this " + kind + ". Wait for the buyer's answer.";
        }
        return "This " + kind + " is no longer pending.";
    }

    /**
     * Declines the offer if no deal can be made from it any more: its listing is no longer
     * Live (alternative flow A3), or its buyer's account is no longer active.
     *
     * @param offer   the pending offer
     * @param listing its listing, {@code null} if it was deleted
     * @param seller  the logged-in Seller
     * @return the failure to return if the offer was declined, {@code null} if a deal is
     *         still possible
     */
    private OfferManagementResult declineIfImpossible(Offer offer, Listing listing, Seller seller) {
        if (listing == null || listing.getStatus() != ListingStatus.LIVE) {
            declineAutomatically(offer, listing, seller, REASON_LISTING_UNAVAILABLE);
            return OfferManagementResult.failure(OfferManagementStatus.LISTING_NOT_AVAILABLE, offer,
                    "This " + kindOf(offer) + " can no longer be accepted because the listing is no longer"
                            + " available. It has been declined and the buyer notified.");
        }
        User buyer = dataService.findUser(offer.getBuyerUsername());
        if (buyer == null || !buyer.isActive()) {
            declineAutomatically(offer, listing, seller, REASON_BUYER_UNAVAILABLE);
            return OfferManagementResult.failure(OfferManagementStatus.BUYER_UNAVAILABLE, offer,
                    "The account of @" + offer.getBuyerUsername() + " is no longer active, so no deal can be"
                            + " made. The " + kindOf(offer) + " has been declined.");
        }
        return null;
    }

    /**
     * Declines an offer that can no longer lead to a deal and notifies its buyer.
     *
     * @param offer   the offer to decline
     * @param listing its listing, may be {@code null}
     * @param seller  the logged-in Seller
     * @param reason  the reason stored on the offer and sent to the buyer
     */
    private void declineAutomatically(Offer offer, Listing listing, Seller seller, String reason) {
        markDeclined(offer, reason);
        notifyBuyer(offer, "Offer Declined", "Your " + kindOf(offer) + " for '" + titleOf(offer, listing)
                + "' to " + seller.getDisplayName() + " was declined. " + reason, offer.getId(), NotificationTargetType.OFFER);
    }

    /**
     * Records the active deal created by an acceptance (subflow S1, step 4).
     *
     * @param offer   the accepted offer
     * @param listing its Live listing
     * @param now     the time of the acceptance
     * @return the stored deal
     */
    private Deal recordDeal(Offer offer, Listing listing, LocalDateTime now) {
        Deal deal = new Deal();
        deal.setListingId(listing.getId());
        deal.setListingTitle(listing.getTitle());
        deal.setBuyerUsername(offer.getBuyerUsername());
        deal.setSellerUsername(offer.getSellerUsername());
        deal.setOfferId(offer.getId());
        deal.setDealType(listing.getType());
        deal.setStatus(DealStatus.ACTIVE);
        deal.setStartDate(now);
        if (listing.getType() == ListingType.LEND) {
            // The loan cannot start before the deal exists.
            LocalDateTime requestedStart = offer.getStartDate();
            deal.setStartDate(requestedStart != null && requestedStart.isAfter(now) ? requestedStart : now);
            deal.setDueDate(offer.getEndDate());
        } else if (listing.getType() == ListingType.GIVEAWAY) {
            deal.setOfferPrice(0.0);
        } else {
            deal.setOfferPrice(offer.getPrice() != null ? offer.getPrice() : listing.getPrice());
        }
        dataService.addDeal(deal);
        return deal;
    }

    /**
     * Declines every other pending or countered offer on a listing once one was accepted
     * (subflow S1, step 5), and notifies each of their buyers.
     *
     * @param listing  the listing that was just reserved or lent
     * @param accepted the accepted offer, left unchanged
     * @param seller   the logged-in Seller
     * @return the number of declined offers
     */
    private int declineCompetingOffers(Listing listing, Offer accepted, Seller seller) {
        int declined = 0;
        for (Offer other : dataService.getOffersForListing(listing.getId())) {
            if (other == accepted || !(other.isPending() || other.isCountered())) {
                continue;
            }
            markDeclined(other, REASON_OTHER_OFFER_ACCEPTED);
            notifyBuyer(other, "Offer Declined", "Your " + kindOf(other) + " for '" + listing.getTitle()
                            + "' was declined because " + seller.getDisplayName() + " accepted another offer.",
                    other.getId(), NotificationTargetType.OFFER);
            declined++;
        }
        return declined;
    }

    /**
     * Marks an offer, and its counter offer if it has one, as declined.
     *
     * @param offer  the offer to decline
     * @param reason the reason to store, may be {@code null}
     */
    private static void markDeclined(Offer offer, String reason) {
        offer.setStatus(OfferStatus.DECLINED);
        offer.setDeclineReason(reason);
        if (offer.getCounterOffer() != null) {
            offer.getCounterOffer().setStatus(OfferStatus.DECLINED);
        }
    }

    /**
     * Attaches a counter offer to an offer and marks the offer as countered
     * (subflow S3, step 4).
     *
     * @param offer the pending offer to counter
     * @param price the counter price, {@code null} for a borrow request
     * @param start the counter start date, {@code null} for a price offer
     * @param end   the counter end date, {@code null} for a price offer
     * @return the counter offer
     */
    private static Offer recordCounter(Offer offer, Double price, LocalDateTime start, LocalDateTime end) {
        Offer counter = new Offer(offer.getId() + COUNTER_ID_SUFFIX, offer.getType(), offer.getListingId(),
                offer.getBuyerUsername(), offer.getSellerUsername());
        counter.setListingTitle(offer.getListingTitle());
        counter.setPrice(price);
        counter.setStartDate(start);
        counter.setEndDate(end);
        counter.setStatus(OfferStatus.COUNTERED);
        counter.setCreatedDate(LocalDateTime.now());
        offer.setCounterOffer(counter);
        offer.setStatus(OfferStatus.COUNTERED);
        return counter;
    }

    /**
     * Builds the failure returned when the kind of counter does not fit the offer.
     *
     * @param offer   the offer
     * @param message explanation used for a price offer or a borrow request
     * @return the failure; claims always get the Give Away explanation
     */
    private static OfferManagementResult counterNotAllowed(Offer offer, String message) {
        String explanation = offer.getType() == OfferType.CLAIM
                ? "A claim on a Give Away listing cannot be countered. Accept or decline it."
                : message;
        return OfferManagementResult.failure(OfferManagementStatus.COUNTER_NOT_ALLOWED, offer, explanation);
    }

    /**
     * Checks a counter price (alternative flow A4).
     *
     * @param price        the counter price
     * @param offeredPrice the price the buyer offered, may be {@code null}
     * @return the error to show, or {@code null} if the price is valid
     */
    private static String counterPriceError(Double price, Double offeredPrice) {
        if (price == null) {
            return "Enter a counter price.";
        }
        if (price.isNaN() || price.isInfinite()) {
            return "The counter price must be a valid number.";
        }
        if (price > MAX_COUNTER_PRICE) {
            return "The counter price cannot be more than " + formatPrice(MAX_COUNTER_PRICE) + ".";
        }
        double rounded = roundToCents(price);
        if (rounded <= 0) {
            return "The counter price must be a positive amount.";
        }
        if (offeredPrice != null && Double.compare(rounded, roundToCents(offeredPrice)) == 0) {
            return "This is the price the buyer offered. Accept the offer instead.";
        }
        return null;
    }

    /**
     * Checks counter borrow dates (alternative flow A4).
     *
     * @param start       start of the counter period
     * @param end         end of the counter period
     * @param maxDuration lending duration of the listing; {@code null}, zero or negative
     *                    means no limit
     * @param offer       the borrow request being countered
     * @param now         the current time
     * @return the error to show, or {@code null} if the dates are valid
     */
    private static String counterDatesError(LocalDateTime start, LocalDateTime end, Duration maxDuration,
                                            Offer offer, LocalDateTime now) {
        if (start == null || end == null) {
            return "Enter both the start and the end of the borrow period.";
        }
        if (start.isBefore(now.minusSeconds(PAST_START_TOLERANCE_SECONDS))) {
            return "The start of the borrow period cannot be in the past.";
        }
        if (!end.isAfter(start)) {
            return "The end of the borrow period must be after its start.";
        }
        Duration length = Duration.between(start, end);
        if (length.getSeconds() < MIN_BORROW_SECONDS) {
            return "The borrow period must last at least " + MIN_BORROW_SECONDS + " seconds.";
        }
        if (maxDuration != null && !maxDuration.isNegative() && !maxDuration.isZero()
                && length.compareTo(maxDuration) > 0) {
            return "The borrow period cannot be longer than the lending duration of the listing ("
                    + formatDuration(maxDuration) + ").";
        }
        if (start.equals(offer.getStartDate()) && end.equals(offer.getEndDate())) {
            return "These are the dates the buyer asked for. Accept the request instead.";
        }
        return null;
    }

    /**
     * Rounds an amount to the cent, half up.
     *
     * @param amount a finite amount
     * @return the rounded amount
     */
    private static double roundToCents(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Sends a notification about an offer to its buyer, linked to the related item so that
     * the buyer's notification board can open it.
     *
     * @param offer       the offer whose buyer is notified
     * @param title       title of the notification
     * @param message     text of the notification
     * @param relatedId   identifier of the related offer or deal
     * @param relatedType type of the related item
     */
    private void notifyBuyer(Offer offer, String title, String message, String relatedId,
                             NotificationTargetType relatedType) {
        dataService.addNotification(new Notification(offer.getBuyerUsername(), title, message,
                NOTIFICATION_CATEGORY, relatedId, relatedType.name()));
    }

    /**
     * Returns the title of an offer's listing, preferring the current one.
     *
     * @param offer   the offer
     * @param listing its listing, may be {@code null} if deleted
     * @return the listing title
     */
    private static String titleOf(Offer offer, Listing listing) {
        return listing != null && listing.getTitle() != null ? listing.getTitle() : offer.getListingTitle();
    }

    /**
     * Builds the sentence telling the Seller how many other offers an acceptance declined.
     *
     * @param count number of declined offers
     * @return the sentence, starting with a space, or an empty string if none was declined
     */
    private static String othersDeclinedSentence(int count) {
        if (count == 0) {
            return "";
        }
        return count == 1
                ? " 1 other pending offer was declined and its buyer notified."
                : " " + count + " other pending offers were declined and their buyers notified.";
    }

    /**
     * Writes a count with its unit, in the singular or plural.
     *
     * @param count the count
     * @param unit  the unit in the singular
     * @return for example {@code 1 day} or {@code 3 days}
     */
    private static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }
}
