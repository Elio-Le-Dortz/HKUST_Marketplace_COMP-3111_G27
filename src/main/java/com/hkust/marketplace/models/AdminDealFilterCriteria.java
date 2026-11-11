package com.hkust.marketplace.models;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Filter and search criteria for Admin Deal Oversight querying
 * (Sprint 2 US-A05, Alternative Flows A2 &amp; A4 in specs_admin.tex).
 */
public class AdminDealFilterCriteria {

    private final String searchQuery;
    private final DealType dealType;
    private final DealStatus dealStatus;
    private final LocalDate startDateFrom;
    private final LocalDate startDateTo;

    public AdminDealFilterCriteria() {
        this(null, null, null, null, null);
    }

    public AdminDealFilterCriteria(String searchQuery,
                                   DealType dealType,
                                   DealStatus dealStatus,
                                   LocalDate startDateFrom,
                                   LocalDate startDateTo) {
        this.searchQuery = searchQuery != null ? searchQuery.trim() : null;
        this.dealType = dealType;
        this.dealStatus = dealStatus;
        this.startDateFrom = startDateFrom;
        this.startDateTo = startDateTo;
    }

    public static AdminDealFilterCriteria all() {
        return new AdminDealFilterCriteria();
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public DealType getDealType() {
        return dealType;
    }

    public DealStatus getDealStatus() {
        return dealStatus;
    }

    public LocalDate getStartDateFrom() {
        return startDateFrom;
    }

    public LocalDate getStartDateTo() {
        return startDateTo;
    }

    /**
     * Checks if the date range is valid (Alternative Flow A4).
     * An end date that strictly precedes the start date is invalid.
     */
    public boolean isValidDateRange() {
        if (startDateFrom != null && startDateTo != null) {
            return !startDateTo.isBefore(startDateFrom);
        }
        return true;
    }

    /**
     * Checks if no filters or search query are active.
     */
    public boolean isEmpty() {
        return (searchQuery == null || searchQuery.isEmpty())
                && dealType == null
                && dealStatus == null
                && startDateFrom == null
                && startDateTo == null;
    }

    /**
     * Evaluates whether a given {@link AdminDealRecord} matches all active criteria.
     *
     * @param record transaction record to test
     * @return true if matches all active criteria; false otherwise
     */
    public boolean matches(AdminDealRecord record) {
        if (record == null) {
            return false;
        }

        // Deal Type filter
        if (dealType != null && record.getDealType() != dealType) {
            return false;
        }

        // Deal Status filter
        if (dealStatus != null && record.getDealStatus() != dealStatus) {
            return false;
        }

        // Date Range: Start Date From (inclusive)
        if (startDateFrom != null) {
            if (record.getStartDate() == null || record.getStartDate().toLocalDate().isBefore(startDateFrom)) {
                return false;
            }
        }

        // Date Range: Start Date To (inclusive)
        if (startDateTo != null) {
            if (record.getStartDate() == null || record.getStartDate().toLocalDate().isAfter(startDateTo)) {
                return false;
            }
        }

        // Search Query (Item title, buyer username, seller username, or deleted user)
        if (searchQuery != null && !searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase();
            String title = record.getItemTitle() != null ? record.getItemTitle().toLowerCase() : "";
            String buyerUser = record.getRawBuyerUsername().toLowerCase();
            String sellerUser = record.getRawSellerUsername().toLowerCase();
            String buyerFull = record.getBuyer() != null && record.getBuyer().getFullName() != null
                    ? record.getBuyer().getFullName().toLowerCase() : "";
            String sellerFull = record.getSeller() != null && record.getSeller().getFullName() != null
                    ? record.getSeller().getFullName().toLowerCase() : "";

            boolean titleMatch = title.contains(q);
            boolean buyerMatch = buyerUser.contains(q) || buyerFull.contains(q);
            boolean sellerMatch = sellerUser.contains(q) || sellerFull.contains(q);
            boolean deletedUserMatch = "deleted user".contains(q) && (record.isBuyerDeleted() || record.isSellerDeleted());

            if (!titleMatch && !buyerMatch && !sellerMatch && !deletedUserMatch) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdminDealFilterCriteria that)) return false;
        return Objects.equals(searchQuery, that.searchQuery) &&
                dealType == that.dealType &&
                dealStatus == that.dealStatus &&
                Objects.equals(startDateFrom, that.startDateFrom) &&
                Objects.equals(startDateTo, that.startDateTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchQuery, dealType, dealStatus, startDateFrom, startDateTo);
    }
}
