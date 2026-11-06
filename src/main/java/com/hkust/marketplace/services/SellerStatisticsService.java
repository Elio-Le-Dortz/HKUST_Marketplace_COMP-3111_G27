package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SellerListingStatistics;
import com.hkust.marketplace.models.SellerRatingInfo;
import com.hkust.marketplace.models.SellerStatistics;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Business logic of use case 2.8 "View Seller Statistics" (US-S08): the Seller sees the total
 * number of views of their listings, the number of offers received, the number of completed
 * deals and their average rating, with a bar chart of the views and offers received for each
 * listing and a pie chart of the completed deals by type (Sold, Lent, Given). Acceptance
 * criterion of the Sprint 2 backlog: the JavaFX charts accurately reflect live data.
 *
 * <p><b>Class diagram.</b> Nothing is stored: every value is computed from the class diagram
 * each time {@link #getMyStatistics()} is called, so the screen always shows the live data.</p>
 * <ul>
 *   <li><b>Listings</b>: the listings the Seller posted ({@code Seller "1" --> "*" Listing :
 *       Posts}), exactly as on the My Listings screen (use case 2.4): every status, including
 *       the listings rejected or taken down by the admin, but not the listings the Seller
 *       deleted.</li>
 *   <li><b>Views</b>: {@code Listing.viewCount}, counted each time a buyer opens the detail page
 *       of the listing (use case 1.4).</li>
 *   <li><b>Offers received</b>: the {@link Offer}s made on the listings ({@code Offer "*" --> "1"
 *       Listing : IsFor}): price offers, borrow requests and claims, whatever their status now
 *       (pending, accepted, declined, countered or withdrawn). A counter offer is made by the
 *       Seller, not received, so it is not counted.</li>
 *   <li><b>Completed deals</b>: the {@link Deal}s of the listings whose status is
 *       {@link DealStatus#COMPLETED Completed} (a sale or a giveaway marked complete by the
 *       buyer) or {@link DealStatus#RETURNED Returned} (a loan whose item came back), grouped by
 *       {@link DealType}: Sold, Lent and Given. An active deal is not completed yet. A loan whose
 *       period has expired is first auto-returned (use case 1.6, alternative flow A4), as on the
 *       buyer's Deal History and the admin's All Deals screens.</li>
 *   <li><b>Average rating</b>: computed by {@link ReviewHandlingService#getMyRating()}, over the
 *       reviews not hidden by the admin ({@code Review.hidden}), so it is the same value as on
 *       the Reviews &amp; Feedback screen.</li>
 * </ul>
 *
 * <p><b>Access control.</b> {@link #getMyStatistics()} first calls
 * {@link SessionManager#requireSeller()}, so it can only be used during an active Seller session
 * (use case 2.2 special requirement), and a Seller only sees the statistics of their own
 * listings. A refused call changes nothing.</p>
 *
 * <p><b>Atomicity.</b> The statistics are computed while holding the lock of the data store
 * ({@link DummyDataService}), which the buyers take when they open a listing or send an offer
 * and the Seller takes when handling an offer. So every value is read from the same state: the
 * charts never mix a view, an offer or a deal recorded halfway through the computation. Inside
 * the lock, only the data store and the Seller services that take the same lock are called. The
 * auto-returns are processed just before taking the lock, through
 * {@link DummyDataService#processAutoReturns()}, which takes the data store lock first like
 * every other caller, so no new lock order is introduced.</p>
 *
 * <p>This class does not use JavaFX, so every flow is unit tested without a display.</p>
 */
public class SellerStatisticsService {

    /** Longest label of a listing on the bar chart, in characters; a longer title is shortened. */
    public static final int MAX_CHART_LABEL_LENGTH = 18;

    /** Character ending a shortened chart label. */
    public static final String ELLIPSIS = "…";

    /** Title shown for a listing without title. */
    public static final String UNTITLED = "Untitled listing";

    /** The store of listings, offers, deals and reviews. */
    private final DummyDataService dataService;
    /** Gives the Seller's listings, in the same way as the My Listings screen. */
    private final MyListingsService myListingsService;
    /** Gives the Seller's average rating, in the same way as the Reviews &amp; Feedback screen. */
    private final ReviewHandlingService reviewHandlingService;

    /**
     * Creates the service on the application's shared data store.
     */
    public SellerStatisticsService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Creates the service on the given data store (used by the tests).
     *
     * @param dataService the store of listings, offers, deals and reviews
     * @throws NullPointerException if {@code dataService} is {@code null}
     */
    public SellerStatisticsService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
        this.myListingsService = new MyListingsService(dataService);
        this.reviewHandlingService = new ReviewHandlingService(dataService);
    }

    // =====================================================================
    // Basic flow, steps 2 to 5: compute the statistics
    // =====================================================================

    /**
     * Computes the statistics of the logged-in Seller's listings (basic flow, step 2): the
     * values shown in step 3, the data of the bar chart of step 4 and of the pie chart of
     * step 5. Expired loans are auto-returned first, so that they count as completed deals.
     *
     * <p>Without listings, every value is zero and {@link SellerStatistics#hasListings()} is
     * {@code false} (alternative flow A1). Without completed deals,
     * {@link SellerStatistics#hasCompletedDeals()} is {@code false} (alternative flow A2).</p>
     *
     * @return the statistics, computed from the live data
     * @throws SecurityException if no active Seller is logged in
     */
    public SellerStatistics getMyStatistics() {
        Seller seller = SessionManager.requireSeller();
        // Use case 1.6, alternative flow A4: an expired loan is returned before it is counted.
        dataService.processAutoReturns();
        synchronized (dataService) {
            List<Listing> listings = myListingsService.getMyListings();

            Set<String> listingIds = new HashSet<>();
            listings.forEach(listing -> listingIds.add(listing.getId()));
            Map<String, Integer> dealsPerListing = new HashMap<>();
            Map<DealType, Integer> dealsByType = new EnumMap<>(DealType.class);
            for (Deal deal : dataService.getAllDeals()) {
                if (isCompletedDeal(deal) && deal.getType() != null
                        && listingIds.contains(deal.getListingId())
                        && seller.getUsername().equalsIgnoreCase(deal.getSellerUsername())) {
                    dealsPerListing.merge(deal.getListingId(), 1, Integer::sum);
                    dealsByType.merge(deal.getType(), 1, Integer::sum);
                }
            }

            List<String> labels = chartLabels(listings.stream().map(Listing::getTitle).toList());
            List<SellerListingStatistics> perListing = new ArrayList<>();
            for (int i = 0; i < listings.size(); i++) {
                Listing listing = listings.get(i);
                perListing.add(new SellerListingStatistics(listing.getId(), titleOf(listing.getTitle()),
                        labels.get(i), listing.getType(), listing.getStatus(), listing.getViewCount(),
                        dataService.getOffersForListing(listing.getId()).size(),
                        dealsPerListing.getOrDefault(listing.getId(), 0)));
            }

            SellerRatingInfo rating = reviewHandlingService.getMyRating();
            return new SellerStatistics(seller.getUsername(), perListing, dealsByType, rating, LocalDateTime.now());
        }
    }

    // =====================================================================
    // Helpers (no session needed, nothing changed)
    // =====================================================================

    /**
     * Tells whether a deal is completed: a sale or a giveaway marked complete by the buyer
     * (status Completed), or a loan whose item came back (status Returned).
     *
     * @param deal the deal, may be {@code null}
     * @return {@code true} if the deal is Completed or Returned, {@code false} if it is still
     *         active or {@code null}
     */
    public static boolean isCompletedDeal(Deal deal) {
        return deal != null && (deal.getStatus() == DealStatus.COMPLETED || deal.getStatus() == DealStatus.RETURNED);
    }

    /**
     * Builds the labels of listings on the bar chart (step 4), in the same order as the
     * titles. A title is trimmed and shortened to {@value #MAX_CHART_LABEL_LENGTH} characters,
     * ending with {@value #ELLIPSIS}; a missing title becomes {@value #UNTITLED}. The bar chart
     * merges listings that have the same label, so a label already used gets a number, as in
     * {@code Desk Lamp (2)}.
     *
     * @param titles the titles of the listings, in display order; an element may be {@code null}
     * @return the labels, all different, in the same order as the titles
     * @throws NullPointerException if {@code titles} is {@code null}
     */
    public static List<String> chartLabels(List<String> titles) {
        Objects.requireNonNull(titles, "titles");
        List<String> labels = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String title : titles) {
            String base = shorten(titleOf(title));
            String label = base;
            for (int number = 2; !used.add(label); number++) {
                label = base + " (" + number + ")";
            }
            labels.add(label);
        }
        return labels;
    }

    /**
     * Returns the title shown for a listing.
     *
     * @param title the stored title, may be {@code null}
     * @return the trimmed title, or {@value #UNTITLED} if it is {@code null} or blank
     */
    private static String titleOf(String title) {
        return title == null || title.isBlank() ? UNTITLED : title.trim();
    }

    /**
     * Shortens a title to {@value #MAX_CHART_LABEL_LENGTH} characters, counted as the Seller sees
     * them (an emoji counts as one).
     *
     * @param title the title
     * @return the title, or its beginning followed by {@value #ELLIPSIS} if it is too long
     */
    private static String shorten(String title) {
        if (title.codePointCount(0, title.length()) <= MAX_CHART_LABEL_LENGTH) {
            return title;
        }
        int end = title.offsetByCodePoints(0, MAX_CHART_LABEL_LENGTH - 1);
        return title.substring(0, end).trim() + ELLIPSIS;
    }
}
