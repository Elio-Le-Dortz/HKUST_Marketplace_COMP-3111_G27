package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealHistoryFilterCriteria;
import com.hkust.marketplace.models.DealHistoryItem;
import com.hkust.marketplace.models.DealHistorySummary;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules and query logic for the Deal History Screen
 * (Sprint 2 US-B10, Use Case 1.9 in specs_buyer.tex).
 *
 * <p>Completely decoupled from JavaFX UI controls to ensure exhaustive headless unit testing.
 */
public class DealHistoryService {

    private final DummyDataService dataService;

    public DealHistoryService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Retrieves the buyer's completed and returned deals, applying any active filter criteria
     * or keyword search, and sorted newest first (Basic Flow, S1 & S2).
     *
     * <p>Automatically triggers auto-return evaluation for any expired loans before querying,
     * ensuring that freshly returned deals appear in history without delay.
     *
     * @param buyerUsername the buyer's username
     * @param criteria      the optional filter and search criteria
     * @return a list of matching {@link DealHistoryItem} instances sorted chronologically descending
     */
    public synchronized List<DealHistoryItem> getDealHistory(String buyerUsername, DealHistoryFilterCriteria criteria) {
        checkSessionBuyer(buyerUsername);
        if (buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Auto-return expired loans before assembling view
        dataService.processAutoReturns();

        String normalizedBuyer = buyerUsername.trim();
        List<Deal> buyerDeals = dataService.getDealsForBuyer(normalizedBuyer);

        List<DealHistoryItem> items = buyerDeals.stream()
                .filter(d -> d != null && (d.getStatus() == DealStatus.COMPLETED || d.getStatus() == DealStatus.RETURNED))
                .map(deal -> {
                    Listing listing = deal.getListingId() != null ? dataService.findListing(deal.getListingId()) : null;
                    User seller = deal.getSellerUsername() != null ? dataService.findUser(deal.getSellerUsername()) : null;
                    return DealHistoryItem.fromDeal(deal, listing, seller);
                })
                .filter(item -> criteria == null || criteria.matches(item))
                .collect(Collectors.toList());

        // Sort newest first: closedDate descending, fallback to dealDate descending
        items.sort(Comparator.comparing(
                (DealHistoryItem item) -> item.getClosedDate() != null ? item.getClosedDate() : item.getDealDate(),
                Comparator.nullsLast(Comparator.naturalOrder())
        ).reversed());

        return items;
    }

    /**
     * Checks if the buyer has any completed or returned deals in the system,
     * ignoring any active filter criteria (Alternative Flow A1 vs A2 check).
     *
     * @param buyerUsername the buyer's username
     * @return true if at least one completed or returned deal exists for this buyer
     */
    public synchronized boolean hasCompletedDeals(String buyerUsername) {
        if (buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return false;
        }

        dataService.processAutoReturns();
        String normalizedBuyer = buyerUsername.trim();
        return dataService.getDealsForBuyer(normalizedBuyer).stream()
                .anyMatch(d -> d != null && (d.getStatus() == DealStatus.COMPLETED || d.getStatus() == DealStatus.RETURNED));
    }

    /**
     * Retrieves the distinct seller usernames from all completed/returned deals of the given buyer.
     *
     * @param buyerUsername the buyer's username
     * @return a sorted list of unique seller usernames
     */
    public synchronized List<String> getDistinctSellers(String buyerUsername) {
        if (buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return getDealHistory(buyerUsername, null).stream()
                .map(DealHistoryItem::getSellerUsername)
                .filter(s -> s != null && !s.trim().isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the distinct item categories present in the buyer's deal history.
     *
     * @param buyerUsername the buyer's username
     * @return a list of unique categories
     */
    public synchronized List<Category> getDistinctCategories(String buyerUsername) {
        if (buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return getDealHistory(buyerUsername, null).stream()
                .map(DealHistoryItem::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Category::getDisplayName))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves summary statistics for the buyer's entire deal history.
     *
     * @param buyerUsername the buyer's username
     * @return a {@link DealHistorySummary} aggregating completed deals
     */
    public synchronized DealHistorySummary getSummary(String buyerUsername) {
        List<DealHistoryItem> allCompleted = getDealHistory(buyerUsername, null);
        return DealHistorySummary.fromItems(allCompleted);
    }

    /**
     * Finds a single deal history entry by deal ID.
     *
     * @param dealId        the deal ID
     * @param buyerUsername the buyer's username
     * @return the matching DealHistoryItem, or null if not found or not completed
     */
    public synchronized DealHistoryItem findDealHistoryItem(String dealId, String buyerUsername) {
        if (dealId == null || dealId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return null;
        }

        return getDealHistory(buyerUsername, null).stream()
                .filter(item -> dealId.trim().equalsIgnoreCase(item.getDealId()))
                .findFirst()
                .orElse(null);
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
