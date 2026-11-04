package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Filter and sorting criteria container for the Marketplace browse and search experience
 * (Use case 1.3: Browse Marketplace).
 *
 * <p>Encapsulates all supported search/filter dimensions:
 * <ul>
 *   <li>Subflow S1: Keyword search across title and description</li>
 *   <li>Subflow S2: Criteria filters (category, listing type, condition, price range)</li>
 *   <li>Subflow S3: Sort order (newest first, price low-to-high, price high-to-low)</li>
 * </ul>
 */
public class ListingFilterCriteria {

    private String keyword;
    private Category category;
    private ListingType type;
    private Condition condition;
    private Double minPrice;
    private Double maxPrice;
    private ListingSortOrder sortOrder = ListingSortOrder.NEWEST_FIRST;

    public ListingFilterCriteria() {
    }

    public ListingFilterCriteria(String keyword, Category category, ListingType type,
                                 Condition condition, Double minPrice, Double maxPrice,
                                 ListingSortOrder sortOrder) {
        this.keyword = keyword;
        this.category = category;
        this.type = type;
        this.condition = condition;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.sortOrder = sortOrder != null ? sortOrder : ListingSortOrder.NEWEST_FIRST;
    }

    public static ListingFilterCriteria all() {
        return new ListingFilterCriteria();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasActiveFilters() {
        return (keyword != null && !keyword.trim().isEmpty())
                || category != null
                || type != null
                || condition != null
                || minPrice != null
                || maxPrice != null;
    }

    public boolean isPriceRangeValid() {
        if (minPrice != null && minPrice < 0) {
            return false;
        }
        if (maxPrice != null && maxPrice < 0) {
            return false;
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            return false;
        }
        return true;
    }

    public void reset() {
        this.keyword = null;
        this.category = null;
        this.type = null;
        this.condition = null;
        this.minPrice = null;
        this.maxPrice = null;
        this.sortOrder = ListingSortOrder.NEWEST_FIRST;
    }

    // Getters and Setters
    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
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

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public Double getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(Double minPrice) {
        this.minPrice = minPrice;
    }

    public Double getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(Double maxPrice) {
        this.maxPrice = maxPrice;
    }

    public ListingSortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(ListingSortOrder sortOrder) {
        this.sortOrder = sortOrder != null ? sortOrder : ListingSortOrder.NEWEST_FIRST;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListingFilterCriteria that = (ListingFilterCriteria) o;
        return Objects.equals(keyword, that.keyword)
                && category == that.category
                && type == that.type
                && condition == that.condition
                && Objects.equals(minPrice, that.minPrice)
                && Objects.equals(maxPrice, that.maxPrice)
                && sortOrder == that.sortOrder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, category, type, condition, minPrice, maxPrice, sortOrder);
    }

    @Override
    public String toString() {
        return "ListingFilterCriteria{" +
                "keyword='" + keyword + '\'' +
                ", category=" + category +
                ", type=" + type +
                ", condition=" + condition +
                ", minPrice=" + minPrice +
                ", maxPrice=" + maxPrice +
                ", sortOrder=" + sortOrder +
                '}';
    }

    /**
     * Fluent Builder for ListingFilterCriteria.
     */
    public static class Builder {
        private String keyword;
        private Category category;
        private ListingType type;
        private Condition condition;
        private Double minPrice;
        private Double maxPrice;
        private ListingSortOrder sortOrder = ListingSortOrder.NEWEST_FIRST;

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder category(Category category) {
            this.category = category;
            return this;
        }

        public Builder type(ListingType type) {
            this.type = type;
            return this;
        }

        public Builder condition(Condition condition) {
            this.condition = condition;
            return this;
        }

        public Builder minPrice(Double minPrice) {
            this.minPrice = minPrice;
            return this;
        }

        public Builder maxPrice(Double maxPrice) {
            this.maxPrice = maxPrice;
            return this;
        }

        public Builder sortOrder(ListingSortOrder sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public ListingFilterCriteria build() {
            return new ListingFilterCriteria(keyword, category, type, condition, minPrice, maxPrice, sortOrder);
        }
    }
}
