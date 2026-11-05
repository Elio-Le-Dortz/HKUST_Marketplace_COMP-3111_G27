package com.hkust.marketplace.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Filter criteria for Seller Ratings Overview (US-A08 / Alternative Flow A2).
 */
public class AdminRatingFilterCriteria {

    /**
     * Filter by review moderation / visibility state.
     */
    public enum ReviewStateFilter {
        ALL,
        FLAGGED_ONLY,
        HIDDEN_ONLY,
        VISIBLE_ONLY
    }

    private String userQuery;
    private Integer ratingScore;
    private LocalDate startDate;
    private LocalDate endDate;
    private ReviewStateFilter stateFilter;

    public AdminRatingFilterCriteria() {
        this.stateFilter = ReviewStateFilter.ALL;
    }

    public static AdminRatingFilterCriteria all() {
        return new AdminRatingFilterCriteria();
    }

    public static AdminRatingFilterCriteria flaggedOnly() {
        AdminRatingFilterCriteria criteria = new AdminRatingFilterCriteria();
        criteria.setStateFilter(ReviewStateFilter.FLAGGED_ONLY);
        return criteria;
    }

    public static AdminRatingFilterCriteria hiddenOnly() {
        AdminRatingFilterCriteria criteria = new AdminRatingFilterCriteria();
        criteria.setStateFilter(ReviewStateFilter.HIDDEN_ONLY);
        return criteria;
    }

    public static AdminRatingFilterCriteria visibleOnly() {
        AdminRatingFilterCriteria criteria = new AdminRatingFilterCriteria();
        criteria.setStateFilter(ReviewStateFilter.VISIBLE_ONLY);
        return criteria;
    }

    /**
     * Checks whether the configured date range is valid.
     * Alternative Flow A3: Invalid if end date is strictly before start date.
     *
     * @return true if valid or open-ended; false if end date is before start date
     */
    public boolean isValidDateRange() {
        if (startDate != null && endDate != null) {
            return !endDate.isBefore(startDate);
        }
        return true;
    }

    /**
     * Evaluates whether a review satisfies all active criteria.
     *
     * @param review the review to test
     * @return true if matches, false otherwise
     */
    public boolean matches(Review review) {
        if (review == null) {
            return false;
        }

        // 1. User/listing query filter (buyer, seller, or listing title)
        if (userQuery != null && !userQuery.trim().isEmpty()) {
            String q = userQuery.trim().toLowerCase();
            boolean matchSeller = review.getSellerUsername() != null
                    && review.getSellerUsername().toLowerCase().contains(q);
            boolean matchBuyer = review.getBuyerUsername() != null
                    && review.getBuyerUsername().toLowerCase().contains(q);
            boolean matchListing = review.getListingTitle() != null
                    && review.getListingTitle().toLowerCase().contains(q);
            if (!matchSeller && !matchBuyer && !matchListing) {
                return false;
            }
        }

        // 2. Star rating score filter (1 to 5)
        if (ratingScore != null && ratingScore >= 1 && ratingScore <= 5) {
            if (review.getRating() != ratingScore) {
                return false;
            }
        }

        // 3. Review state filter
        if (stateFilter != null) {
            switch (stateFilter) {
                case FLAGGED_ONLY:
                    if (!review.isFlagged()) {
                        return false;
                    }
                    break;
                case HIDDEN_ONLY:
                    if (!review.isHidden()) {
                        return false;
                    }
                    break;
                case VISIBLE_ONLY:
                    if (review.isHidden()) {
                        return false;
                    }
                    break;
                case ALL:
                default:
                    break;
            }
        }

        // 4. Date range filter (inclusive)
        if (startDate != null || endDate != null) {
            LocalDateTime reviewDate = review.getDate();
            if (reviewDate == null) {
                return false;
            }
            LocalDate reviewLocalDate = reviewDate.toLocalDate();
            if (startDate != null && reviewLocalDate.isBefore(startDate)) {
                return false;
            }
            if (endDate != null && reviewLocalDate.isAfter(endDate)) {
                return false;
            }
        }

        return true;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public Integer getRatingScore() {
        return ratingScore;
    }

    public void setRatingScore(Integer ratingScore) {
        this.ratingScore = ratingScore;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public ReviewStateFilter getStateFilter() {
        return stateFilter;
    }

    public void setStateFilter(ReviewStateFilter stateFilter) {
        this.stateFilter = stateFilter;
    }
}
