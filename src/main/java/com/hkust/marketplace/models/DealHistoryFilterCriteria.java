package com.hkust.marketplace.models;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Filter and search criteria for querying Deal History
 * (Sprint 2 US-B10, Subflows S1: Filter History &amp; S2: Search History in specs_buyer.tex).
 */
public class DealHistoryFilterCriteria {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Category category;
    private String sellerUsername;
    private DealType dealType;
    private String searchQuery;

    public DealHistoryFilterCriteria() {
    }

    public DealHistoryFilterCriteria(LocalDate fromDate,
                                     LocalDate toDate,
                                     Category category,
                                     String sellerUsername,
                                     DealType dealType,
                                     String searchQuery) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.category = category;
        this.sellerUsername = sellerUsername;
        this.dealType = dealType;
        this.searchQuery = searchQuery;
    }

    /**
     * Evaluates if a given {@link DealHistoryItem} matches all specified filter criteria and search query.
     *
     * @param item the deal history item to test
     * @return true if the item satisfies all active criteria; false otherwise
     */
    public boolean matches(DealHistoryItem item) {
        if (item == null) {
            return false;
        }

        // Date Range: fromDate (inclusive)
        if (fromDate != null) {
            boolean dealDateMatches = item.getDealDate() != null && !item.getDealDate().toLocalDate().isBefore(fromDate);
            boolean closedDateMatches = item.getClosedDate() != null && !item.getClosedDate().toLocalDate().isBefore(fromDate);
            if (!dealDateMatches && !closedDateMatches) {
                return false;
            }
        }

        // Date Range: toDate (inclusive)
        if (toDate != null) {
            boolean dealDateMatches = item.getDealDate() != null && !item.getDealDate().toLocalDate().isAfter(toDate);
            boolean closedDateMatches = item.getClosedDate() != null && !item.getClosedDate().toLocalDate().isAfter(toDate);
            if (!dealDateMatches && !closedDateMatches) {
                return false;
            }
        }

        // Category filter
        if (category != null && item.getCategory() != category) {
            return false;
        }

        // DealType filter
        if (dealType != null && item.getDealType() != dealType) {
            return false;
        }

        // Seller filter
        if (sellerUsername != null && !sellerUsername.trim().isEmpty()) {
            String normSeller = sellerUsername.trim().toLowerCase();
            String itemSellerUser = item.getSellerUsername() != null ? item.getSellerUsername().toLowerCase() : "";
            String itemSellerName = item.getSellerFullName() != null ? item.getSellerFullName().toLowerCase() : "";
            if (!itemSellerUser.contains(normSeller) && !itemSellerName.contains(normSeller)) {
                return false;
            }
        }

        // Search Query (keyword search on item title or seller name)
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            String q = searchQuery.trim().toLowerCase();
            String title = item.getItemTitle() != null ? item.getItemTitle().toLowerCase() : "";
            String sellerUser = item.getSellerUsername() != null ? item.getSellerUsername().toLowerCase() : "";
            String sellerName = item.getSellerFullName() != null ? item.getSellerFullName().toLowerCase() : "";
            if (!title.contains(q) && !sellerUser.contains(q) && !sellerName.contains(q)) {
                return false;
            }
        }

        return true;
    }

    public boolean isEmpty() {
        return fromDate == null
                && toDate == null
                && category == null
                && (sellerUsername == null || sellerUsername.trim().isEmpty())
                && dealType == null
                && (searchQuery == null || searchQuery.trim().isEmpty());
    }

    // Fluent builder / chaining
    public DealHistoryFilterCriteria withFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
        return this;
    }

    public DealHistoryFilterCriteria withToDate(LocalDate toDate) {
        this.toDate = toDate;
        return this;
    }

    public DealHistoryFilterCriteria withCategory(Category category) {
        this.category = category;
        return this;
    }

    public DealHistoryFilterCriteria withSeller(String sellerUsername) {
        this.sellerUsername = sellerUsername;
        return this;
    }

    public DealHistoryFilterCriteria withDealType(DealType dealType) {
        this.dealType = dealType;
        return this;
    }

    public DealHistoryFilterCriteria withSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
        return this;
    }

    // Getters and Setters
    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public void setSellerUsername(String sellerUsername) {
        this.sellerUsername = sellerUsername;
    }

    public DealType getDealType() {
        return dealType;
    }

    public void setDealType(DealType dealType) {
        this.dealType = dealType;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DealHistoryFilterCriteria that = (DealHistoryFilterCriteria) o;
        return Objects.equals(fromDate, that.fromDate)
                && Objects.equals(toDate, that.toDate)
                && category == that.category
                && Objects.equals(sellerUsername, that.sellerUsername)
                && dealType == that.dealType
                && Objects.equals(searchQuery, that.searchQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromDate, toDate, category, sellerUsername, dealType, searchQuery);
    }
}
