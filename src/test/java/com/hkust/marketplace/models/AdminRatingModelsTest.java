package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Admin Rating Models Test [US-A08]")
class AdminRatingModelsTest {

    @Test
    @DisplayName("AdminRatingOperationStatus enum contains expected constants")
    void testOperationStatusEnum() {
        assertEquals(8, AdminRatingOperationStatus.values().length);
        assertEquals(AdminRatingOperationStatus.SUCCESS, AdminRatingOperationStatus.valueOf("SUCCESS"));
        assertEquals(AdminRatingOperationStatus.REVIEW_NOT_FOUND, AdminRatingOperationStatus.valueOf("REVIEW_NOT_FOUND"));
        assertEquals(AdminRatingOperationStatus.ALREADY_HIDDEN, AdminRatingOperationStatus.valueOf("ALREADY_HIDDEN"));
        assertEquals(AdminRatingOperationStatus.ALREADY_VISIBLE, AdminRatingOperationStatus.valueOf("ALREADY_VISIBLE"));
        assertEquals(AdminRatingOperationStatus.NOT_FLAGGED, AdminRatingOperationStatus.valueOf("NOT_FLAGGED"));
        assertEquals(AdminRatingOperationStatus.INVALID_DATE_RANGE, AdminRatingOperationStatus.valueOf("INVALID_DATE_RANGE"));
        assertEquals(AdminRatingOperationStatus.UNAUTHORIZED, AdminRatingOperationStatus.valueOf("UNAUTHORIZED"));
        assertEquals(AdminRatingOperationStatus.INVALID_OPERATION, AdminRatingOperationStatus.valueOf("INVALID_OPERATION"));
    }

    @Test
    @DisplayName("AdminRatingOperationResult factory methods and getters")
    void testOperationResult() {
        Review r = new Review();
        r.setId("R101");
        r.setSellerUsername("seller1");

        AdminRatingOperationResult success = AdminRatingOperationResult.success("Success msg", r, "seller1", 4.5, 3);
        assertTrue(success.isSuccess());
        assertEquals(AdminRatingOperationStatus.SUCCESS, success.getStatus());
        assertEquals("Success msg", success.getMessage());
        assertEquals(r, success.getReview());
        assertEquals("seller1", success.getAffectedSellerUsername());
        assertEquals(4.5, success.getUpdatedAverageRating());
        assertEquals(3, success.getUpdatedRatingCount());

        AdminRatingOperationResult successNoRating = AdminRatingOperationResult.successNoRatingChange("No change", r);
        assertTrue(successNoRating.isSuccess());
        assertEquals("seller1", successNoRating.getAffectedSellerUsername());
        assertNull(successNoRating.getUpdatedAverageRating());
        assertNull(successNoRating.getUpdatedRatingCount());

        AdminRatingOperationResult failure = AdminRatingOperationResult.failure(AdminRatingOperationStatus.REVIEW_NOT_FOUND, "Not found");
        assertFalse(failure.isSuccess());
        assertEquals(AdminRatingOperationStatus.REVIEW_NOT_FOUND, failure.getStatus());
        assertEquals("Not found", failure.getMessage());
        assertNull(failure.getReview());

        AdminRatingOperationResult invalidRange = AdminRatingOperationResult.invalidDateRange("Bad range");
        assertFalse(invalidRange.isSuccess());
        assertEquals(AdminRatingOperationStatus.INVALID_DATE_RANGE, invalidRange.getStatus());
        assertEquals("Bad range", invalidRange.getMessage());
    }

    @Test
    @DisplayName("AdminRatingSummaryMetrics stores and returns aggregate counts")
    void testSummaryMetrics() {
        AdminRatingSummaryMetrics metrics = new AdminRatingSummaryMetrics(10, 2, 3, 7, 4.2);
        assertEquals(10, metrics.getTotalReviews());
        assertEquals(2, metrics.getFlaggedReviews());
        assertEquals(3, metrics.getHiddenReviews());
        assertEquals(7, metrics.getVisibleReviews());
        assertEquals(4.2, metrics.getAveragePlatformRating());
    }

    @Test
    @DisplayName("AdminRatingFilterCriteria matches user, score, state, and dates correctly")
    void testFilterCriteriaMatching() {
        Review review = new Review();
        review.setId("R1");
        review.setSellerUsername("seller_alice");
        review.setBuyerUsername("buyer_bob");
        review.setListingTitle("Quantum Physics Textbook");
        review.setRating(5);
        review.setFlagged(true);
        review.setHidden(false);
        review.setDate(LocalDateTime.of(2026, 11, 5, 12, 0));

        AdminRatingFilterCriteria criteria = AdminRatingFilterCriteria.all();
        assertTrue(criteria.matches(review));

        // Matching userQuery against seller
        criteria.setUserQuery("alice");
        assertTrue(criteria.matches(review));

        // Matching userQuery against buyer
        criteria.setUserQuery("bob");
        assertTrue(criteria.matches(review));

        // Matching userQuery against title
        criteria.setUserQuery("physics");
        assertTrue(criteria.matches(review));

        // Non-matching query
        criteria.setUserQuery("charlie");
        assertFalse(criteria.matches(review));
        criteria.setUserQuery(null);

        // Score filter
        criteria.setRatingScore(5);
        assertTrue(criteria.matches(review));
        criteria.setRatingScore(4);
        assertFalse(criteria.matches(review));
        criteria.setRatingScore(null);

        // State filter: FLAGGED_ONLY
        criteria.setStateFilter(AdminRatingFilterCriteria.ReviewStateFilter.FLAGGED_ONLY);
        assertTrue(criteria.matches(review));

        // State filter: HIDDEN_ONLY
        criteria.setStateFilter(AdminRatingFilterCriteria.ReviewStateFilter.HIDDEN_ONLY);
        assertFalse(criteria.matches(review));

        // State filter: VISIBLE_ONLY
        criteria.setStateFilter(AdminRatingFilterCriteria.ReviewStateFilter.VISIBLE_ONLY);
        assertTrue(criteria.matches(review));

        // Date range matching
        criteria.setStateFilter(AdminRatingFilterCriteria.ReviewStateFilter.ALL);
        criteria.setStartDate(LocalDate.of(2026, 11, 1));
        criteria.setEndDate(LocalDate.of(2026, 11, 10));
        assertTrue(criteria.matches(review));

        // Review before start
        criteria.setStartDate(LocalDate.of(2026, 11, 6));
        assertFalse(criteria.matches(review));

        // Review after end
        criteria.setStartDate(LocalDate.of(2026, 11, 1));
        criteria.setEndDate(LocalDate.of(2026, 11, 4));
        assertFalse(criteria.matches(review));

        // Null review
        assertFalse(criteria.matches(null));

        // Review with null date when filtering by date
        Review nullDateReview = new Review();
        nullDateReview.setDate(null);
        assertFalse(criteria.matches(nullDateReview));
    }

    @Test
    @DisplayName("AdminRatingFilterCriteria date range validity validation (Flow A3)")
    void testDateRangeValidation() {
        AdminRatingFilterCriteria criteria = new AdminRatingFilterCriteria();
        assertTrue(criteria.isValidDateRange());

        criteria.setStartDate(LocalDate.of(2026, 11, 5));
        assertTrue(criteria.isValidDateRange());

        criteria.setEndDate(LocalDate.of(2026, 11, 6));
        assertTrue(criteria.isValidDateRange());

        criteria.setEndDate(LocalDate.of(2026, 11, 5));
        assertTrue(criteria.isValidDateRange());

        // Invalid: end date before start date
        criteria.setEndDate(LocalDate.of(2026, 11, 4));
        assertFalse(criteria.isValidDateRange());
    }

    @Test
    @DisplayName("AdminRatingFilterCriteria factory helpers")
    void testFilterCriteriaFactoryHelpers() {
        AdminRatingFilterCriteria all = AdminRatingFilterCriteria.all();
        assertEquals(AdminRatingFilterCriteria.ReviewStateFilter.ALL, all.getStateFilter());

        AdminRatingFilterCriteria flagged = AdminRatingFilterCriteria.flaggedOnly();
        assertEquals(AdminRatingFilterCriteria.ReviewStateFilter.FLAGGED_ONLY, flagged.getStateFilter());

        AdminRatingFilterCriteria hidden = AdminRatingFilterCriteria.hiddenOnly();
        assertEquals(AdminRatingFilterCriteria.ReviewStateFilter.HIDDEN_ONLY, hidden.getStateFilter());

        AdminRatingFilterCriteria visible = AdminRatingFilterCriteria.visibleOnly();
        assertEquals(AdminRatingFilterCriteria.ReviewStateFilter.VISIBLE_ONLY, visible.getStateFilter());
    }
}
