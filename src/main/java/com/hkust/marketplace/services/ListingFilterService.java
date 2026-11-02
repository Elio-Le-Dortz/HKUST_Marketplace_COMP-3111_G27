package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingFilterCriteria;
import com.hkust.marketplace.models.ListingSortOrder;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules for searching, filtering, and sorting marketplace
 * listings (Use Case 1.3: Browse Marketplace).
 *
 * <p>Completely decoupled from JavaFX UI controls to allow exhaustive unit testing in headless CI.
 */
public class ListingFilterService {

    /**
     * Filters and sorts a collection of listings according to the provided criteria.
     * Only listings with {@link ListingStatus#LIVE} are considered.
     *
     * @param listings the candidate listings to filter (may be null)
     * @param criteria the filter and sort criteria (if null, returns all live listings sorted newest-first)
     * @return a new list of matching listings sorted according to {@code criteria.getSortOrder()}
     */
    public List<Listing> filterAndSort(Collection<Listing> listings, ListingFilterCriteria criteria) {
        if (listings == null || listings.isEmpty()) {
            return new ArrayList<>();
        }

        ListingFilterCriteria effectiveCriteria = (criteria != null) ? criteria : ListingFilterCriteria.all();

        return listings.stream()
                .filter(this::isLive)
                .filter(listing -> matches(listing, effectiveCriteria))
                .sorted(getComparator(effectiveCriteria.getSortOrder()))
                .collect(Collectors.toList());
    }

    /**
     * Checks whether a listing is in {@link ListingStatus#LIVE} status.
     *
     * @param listing the listing to inspect
     * @return {@code true} if live, {@code false} otherwise
     */
    public boolean isLive(Listing listing) {
        return listing != null && listing.getStatus() == ListingStatus.LIVE;
    }

    /**
     * Evaluates whether a listing matches all active filter criteria.
     *
     * @param listing  the listing to evaluate
     * @param criteria the criteria to match against
     * @return {@code true} if the listing satisfies all criteria, {@code false} otherwise
     */
    public boolean matches(Listing listing, ListingFilterCriteria criteria) {
        if (listing == null) {
            return false;
        }
        if (criteria == null) {
            return true;
        }

        // Subflow S1: Keyword Search across title and description
        if (!matchesKeyword(listing, criteria.getKeyword())) {
            return false;
        }

        // Subflow S2: Criteria Filters
        if (!matchesCategory(listing, criteria.getCategory())) {
            return false;
        }

        if (!matchesType(listing, criteria.getType())) {
            return false;
        }

        if (!matchesCondition(listing, criteria.getCondition())) {
            return false;
        }

        if (!matchesPriceRange(listing, criteria.getMinPrice(), criteria.getMaxPrice())) {
            return false;
        }

        return true;
    }

    /**
     * Matches keyword against the listing title and description (case-insensitive).
     *
     * @param listing the listing
     * @param keyword the search query
     * @return {@code true} if keyword is empty/null or found in title/description
     */
    public boolean matchesKeyword(Listing listing, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        if (listing == null) {
            return false;
        }

        String query = keyword.trim().toLowerCase();
        String title = listing.getTitle() != null ? listing.getTitle().toLowerCase() : "";
        String desc = listing.getDescription() != null ? listing.getDescription().toLowerCase() : "";

        return title.contains(query) || desc.contains(query);
    }

    /**
     * Matches category criterion.
     *
     * @param listing  the listing
     * @param category the target category (null matches any)
     * @return {@code true} if category matches
     */
    public boolean matchesCategory(Listing listing, Category category) {
        if (category == null) {
            return true;
        }
        return listing != null && listing.getCategory() == category;
    }

    /**
     * Matches listing type criterion.
     *
     * @param listing the listing
     * @param type    the target listing type (null matches any)
     * @return {@code true} if type matches
     */
    public boolean matchesType(Listing listing, ListingType type) {
        if (type == null) {
            return true;
        }
        return listing != null && listing.getType() == type;
    }

    /**
     * Matches condition criterion.
     *
     * @param listing   the listing
     * @param condition the target condition (null matches any)
     * @return {@code true} if condition matches
     */
    public boolean matchesCondition(Listing listing, Condition condition) {
        if (condition == null) {
            return true;
        }
        return listing != null && listing.getCondition() == condition;
    }

    /**
     * Matches price range criterion.
     *
     * <p>Rules:
     * <ul>
     *   <li>If both {@code minPrice} and {@code maxPrice} are null, any listing matches.</li>
     *   <li>If {@code minPrice > maxPrice}, range is inverted and cannot match any listing.</li>
     *   <li>For {@link ListingType#SELL}: Uses the listing's price.</li>
     *   <li>For {@link ListingType#GIVEAWAY}: Effective price is 0.0 (FREE).</li>
     *   <li>For {@link ListingType#LEND}: Items are borrowed without a purchase price. When a price filter
     *       is active, Lend listings do not match.</li>
     * </ul>
     *
     * @param listing  the listing
     * @param minPrice minimum price (optional)
     * @param maxPrice maximum price (optional)
     * @return {@code true} if price is within range
     */
    public boolean matchesPriceRange(Listing listing, Double minPrice, Double maxPrice) {
        if (minPrice == null && maxPrice == null) {
            return true;
        }
        if (listing == null) {
            return false;
        }

        // Invalid inverted range: min > max
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            return false;
        }

        ListingType type = listing.getType();

        // LEND listings have no monetary purchase price
        if (type == ListingType.LEND) {
            return false;
        }

        double price;
        if (type == ListingType.GIVEAWAY) {
            price = 0.0;
        } else {
            // SELL listing
            if (listing.getPrice() == null) {
                return false;
            }
            price = listing.getPrice();
        }

        if (minPrice != null && price < minPrice) {
            return false;
        }
        if (maxPrice != null && price > maxPrice) {
            return false;
        }

        return true;
    }

    /**
     * Calculates the effective numeric price of a listing for sorting purposes.
     * <ul>
     *   <li>SELL with price: returns {@code listing.getPrice()}</li>
     *   <li>GIVEAWAY: returns 0.0</li>
     *   <li>LEND or null price: returns 0.0</li>
     * </ul>
     *
     * @param listing the listing
     * @return numeric price representation
     */
    public double getEffectivePrice(Listing listing) {
        if (listing == null) {
            return 0.0;
        }
        if (listing.getType() == ListingType.SELL && listing.getPrice() != null) {
            return listing.getPrice();
        }
        return 0.0;
    }

    /**
     * Builds a comparator corresponding to the requested sort order (Subflow S3: Sort Listings).
     *
     * @param sortOrder the desired sort order (defaults to NEWEST_FIRST if null)
     * @return Comparator for listings
     */
    public Comparator<Listing> getComparator(ListingSortOrder sortOrder) {
        ListingSortOrder order = (sortOrder != null) ? sortOrder : ListingSortOrder.NEWEST_FIRST;

        Comparator<Listing> dateDescending = (l1, l2) -> {
            LocalDateTime d1 = l1.getPostedDate() != null ? l1.getPostedDate() : LocalDateTime.MIN;
            LocalDateTime d2 = l2.getPostedDate() != null ? l2.getPostedDate() : LocalDateTime.MIN;
            int cmp = d2.compareTo(d1);
            if (cmp != 0) return cmp;
            String id1 = l1.getId() != null ? l1.getId() : "";
            String id2 = l2.getId() != null ? l2.getId() : "";
            return id1.compareTo(id2);
        };

        switch (order) {
            case PRICE_LOW_TO_HIGH:
                return (l1, l2) -> {
                    double p1 = getEffectivePrice(l1);
                    double p2 = getEffectivePrice(l2);
                    int cmp = Double.compare(p1, p2);
                    if (cmp != 0) return cmp;
                    return dateDescending.compare(l1, l2);
                };

            case PRICE_HIGH_TO_LOW:
                return (l1, l2) -> {
                    double p1 = getEffectivePrice(l1);
                    double p2 = getEffectivePrice(l2);
                    int cmp = Double.compare(p2, p1);
                    if (cmp != 0) return cmp;
                    return dateDescending.compare(l1, l2);
                };

            case NEWEST_FIRST:
            default:
                return dateDescending;
        }
    }
}
