package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules, filtering, and transaction oversight logic
 * for the Admin All Deals Record Screen (Sprint 2 US-A05, Task 3.5 in specs_admin.tex).
 *
 * <p>Completely decoupled from JavaFX UI controls to support deterministic headless unit testing.</p>
 */
public class AdminDealsService {

    private final DummyDataService dataService;

    public AdminDealsService() {
        this(DummyDataService.getInstance());
    }

    public AdminDealsService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Queries and filters all deals system-wide, ordered newest first.
     * Enforces Alternative Flow A1 (No deals yet), A2 (Filtering & search),
     * A3 (No matching deals), and A4 (Invalid date range).
     *
     * @param criteria search and filter criteria; if null, retrieves all deals
     * @return an {@link AdminDealQueryResult} describing the status and matching records
     */
    public synchronized AdminDealQueryResult queryDeals(AdminDealFilterCriteria criteria) {
        SessionManager.requireAdmin();

        // Enforce Alternative Flow A4: Invalid date range (end date precedes start date)
        if (criteria != null && !criteria.isValidDateRange()) {
            return AdminDealQueryResult.invalidDateRange(
                    "The end of the chosen date range is before its start. The date filter was not applied.",
                    null,
                    dataService.getAllDeals().size()
            );
        }

        // Keep active loan expiry up to date before querying
        dataService.processAutoReturns();

        List<Deal> allDeals = dataService.getAllDeals();
        if (allDeals == null || allDeals.isEmpty()) {
            // Alternative Flow A1: No deals in the system yet
            return AdminDealQueryResult.noDealsYet();
        }

        int totalCount = allDeals.size();

        // Convert domain deals into enriched oversight records
        List<AdminDealRecord> records = allDeals.stream()
                .filter(Objects::nonNull)
                .map(this::enrichDeal)
                .sorted(Comparator.comparing(
                        AdminDealRecord::getStartDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed().thenComparing(AdminDealRecord::getDealId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        // Apply filters & search criteria (Alternative Flow A2)
        List<AdminDealRecord> filtered = records.stream()
                .filter(record -> criteria == null || criteria.matches(record))
                .collect(Collectors.toList());

        // Alternative Flow A3: No deals match active filters
        if (filtered.isEmpty()) {
            return AdminDealQueryResult.noMatchingDeals(totalCount);
        }

        return AdminDealQueryResult.success(filtered, totalCount);
    }

    /**
     * Looks up and enriches a specific deal for the detailed oversight view (Basic Flow Steps 3 & 4).
     *
     * @param dealId deal identifier
     * @return an {@link Optional} containing the enriched record if found, or empty otherwise
     */
    public synchronized Optional<AdminDealRecord> getDealDetails(String dealId) {
        SessionManager.requireAdmin();
        if (dealId == null || dealId.trim().isEmpty()) {
            return Optional.empty();
        }
        dataService.processAutoReturns();
        Deal deal = dataService.findDeal(dealId.trim());
        if (deal == null) {
            return Optional.empty();
        }
        return Optional.of(enrichDeal(deal));
    }

    /**
     * Total number of transactions currently recorded in the marketplace.
     */
    public synchronized int getTotalDealsCount() {
        SessionManager.requireAdmin();
        return dataService.getAllDeals().size();
    }

    /**
     * Helper to assemble an enriched {@link AdminDealRecord} by resolving the listing,
     * users, accepted offer, and review.
     */
    private AdminDealRecord enrichDeal(Deal deal) {
        Listing listing = deal.getListingId() != null ? dataService.findListing(deal.getListingId()) : null;
        User buyer = deal.getBuyerUsername() != null ? dataService.findUser(deal.getBuyerUsername()) : null;
        User seller = deal.getSellerUsername() != null ? dataService.findUser(deal.getSellerUsername()) : null;

        Offer offer = null;
        if (deal.getOfferId() != null && !deal.getOfferId().isBlank()) {
            offer = dataService.findOffer(deal.getOfferId());
        }
        if (offer == null && deal.getListingId() != null && deal.getBuyerUsername() != null) {
            // Find accepted offer corresponding to this deal
            for (Offer o : dataService.getAllOffers()) {
                if (deal.getListingId().equalsIgnoreCase(o.getListingId())
                        && deal.getBuyerUsername().equalsIgnoreCase(o.getBuyerUsername())
                        && o.getStatus() == OfferStatus.ACCEPTED) {
                    offer = o;
                    break;
                }
            }
        }

        Review review = null;
        if (deal.getSellerUsername() != null && deal.getBuyerUsername() != null) {
            for (Review r : dataService.getAllReviews()) {
                if (deal.getSellerUsername().equalsIgnoreCase(r.getSellerUsername())
                        && deal.getBuyerUsername().equalsIgnoreCase(r.getBuyerUsername())
                        && (deal.getListingId() == null || deal.getListingId().equalsIgnoreCase(r.getListingId()))) {
                    review = r;
                    break;
                }
            }
        }

        return new AdminDealRecord(deal, listing, buyer, seller, offer, review);
    }
}
