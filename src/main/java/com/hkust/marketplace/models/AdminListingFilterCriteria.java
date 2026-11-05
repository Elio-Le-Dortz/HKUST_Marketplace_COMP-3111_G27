package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Filter and search criteria for the Admin Manage All Listings board
 * (US-A07, specs_admin.tex Use Case 3.7 Basic Flow Step 2).
 *
 * <p>Supports searching by keyword across listing title and seller username/full name,
 * and filtering by status, category, and listing type.
 */
public class AdminListingFilterCriteria {

    private String keyword;
    private ListingStatus status;
    private Category category;
    private ListingType type;

    public AdminListingFilterCriteria() {
    }

    public AdminListingFilterCriteria(String keyword, ListingStatus status, Category category, ListingType type) {
        this.keyword = keyword;
        this.status = status;
        this.category = category;
        this.type = type;
    }

    public static AdminListingFilterCriteria all() {
        return new AdminListingFilterCriteria();
    }

    public static AdminListingFilterCriteria byStatus(ListingStatus status) {
        AdminListingFilterCriteria criteria = new AdminListingFilterCriteria();
        criteria.setStatus(status);
        return criteria;
    }

    public static AdminListingFilterCriteria byCategory(Category category) {
        AdminListingFilterCriteria criteria = new AdminListingFilterCriteria();
        criteria.setCategory(category);
        return criteria;
    }

    public static AdminListingFilterCriteria byType(ListingType type) {
        AdminListingFilterCriteria criteria = new AdminListingFilterCriteria();
        criteria.setType(type);
        return criteria;
    }

    public static AdminListingFilterCriteria byKeyword(String keyword) {
        AdminListingFilterCriteria criteria = new AdminListingFilterCriteria();
        criteria.setKeyword(keyword);
        return criteria;
    }

    /**
     * Evaluates whether a candidate listing satisfies all configured filter constraints.
     *
     * @param listing the listing to inspect
     * @return true if matches all active criteria, false otherwise
     */
    public boolean matches(Listing listing) {
        if (listing == null) {
            return false;
        }

        // Keyword filter: matches title, seller username, or seller full name
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerQuery = keyword.trim().toLowerCase();
            boolean matchTitle = listing.getTitle() != null && listing.getTitle().toLowerCase().contains(lowerQuery);
            boolean matchSellerUser = listing.getSellerUsername() != null && listing.getSellerUsername().toLowerCase().contains(lowerQuery);
            boolean matchSellerName = listing.getSellerFullName() != null && listing.getSellerFullName().toLowerCase().contains(lowerQuery);
            if (!matchTitle && !matchSellerUser && !matchSellerName) {
                return false;
            }
        }

        // Status filter
        if (status != null && listing.getStatus() != status) {
            return false;
        }

        // Category filter
        if (category != null && listing.getCategory() != category) {
            return false;
        }

        // Type filter
        if (type != null && listing.getType() != type) {
            return false;
        }

        return true;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public ListingType getType() {
        return type;
    }

    public void setType(ListingType type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdminListingFilterCriteria that = (AdminListingFilterCriteria) o;
        return Objects.equals(keyword, that.keyword) &&
                status == that.status &&
                category == that.category &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, status, category, type);
    }
}
