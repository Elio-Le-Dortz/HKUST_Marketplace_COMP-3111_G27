package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the types of use case 2.9 "Handle Reviews" (US-S09): the result and status of an
 * action on a review, and the reasons offered when flagging a review.
 */
@DisplayName("ReviewHandlingResult, ReviewHandlingStatus and ReviewFlagReason (US-S09)")
class ReviewHandlingModelsTest {

    private static Review review() {
        Review review = new Review("seller", "buyer", "D1", "L1", "Desk Chair", 4, "Good deal");
        review.setId("R1");
        return review;
    }

    @Nested
    @DisplayName("ReviewHandlingResult")
    class Result {

        @Test
        @DisplayName("success() holds the review and the message")
        void success() {
            Review review = review();

            ReviewHandlingResult result = ReviewHandlingResult.success(review, "Reply posted.");

            assertTrue(result.isSuccess());
            assertEquals(ReviewHandlingStatus.SUCCESS, result.getStatus());
            assertEquals("Reply posted.", result.getMessage());
            assertSame(review, result.getReview());
        }

        @Test
        @DisplayName("failure() without review reveals no review")
        void failureWithoutReview() {
            ReviewHandlingResult result = ReviewHandlingResult.failure(ReviewHandlingStatus.UNAUTHORIZED, "Not yours.");

            assertFalse(result.isSuccess());
            assertEquals(ReviewHandlingStatus.UNAUTHORIZED, result.getStatus());
            assertEquals("Not yours.", result.getMessage());
            assertNull(result.getReview());
        }

        @Test
        @DisplayName("failure() with a review keeps it")
        void failureWithReview() {
            Review review = review();

            ReviewHandlingResult result = ReviewHandlingResult.failure(
                    ReviewHandlingStatus.ALREADY_REPLIED, review, "Already replied.");

            assertFalse(result.isSuccess());
            assertSame(review, result.getReview());
        }

        @Test
        @DisplayName("A failure cannot have the SUCCESS status")
        void failureCannotSucceed() {
            assertThrows(IllegalArgumentException.class,
                    () -> ReviewHandlingResult.failure(ReviewHandlingStatus.SUCCESS, "Oops"));
            assertThrows(IllegalArgumentException.class,
                    () -> ReviewHandlingResult.failure(ReviewHandlingStatus.SUCCESS, review(), "Oops"));
        }

        @Test
        @DisplayName("A missing status is refused and a missing message becomes empty")
        void missingValues() {
            assertThrows(NullPointerException.class, () -> ReviewHandlingResult.failure(null, "No status"));
            assertEquals("", ReviewHandlingResult.success(review(), null).getMessage());
        }

        @Test
        @DisplayName("toString() shows the status and the message")
        void describesItself() {
            String text = ReviewHandlingResult.failure(ReviewHandlingStatus.EMPTY_REPLY, "Empty.").toString();

            assertTrue(text.contains("EMPTY_REPLY"));
            assertTrue(text.contains("Empty."));
        }

        @Test
        @DisplayName("Every status of the specification exists")
        void statuses() {
            assertEquals(9, ReviewHandlingStatus.values().length);
            for (ReviewHandlingStatus status : ReviewHandlingStatus.values()) {
                assertEquals(status, ReviewHandlingStatus.valueOf(status.name()));
            }
        }
    }

    @Nested
    @DisplayName("ReviewFlagReason")
    class FlagReason {

        @Test
        @DisplayName("Offers the examples of the specification and an 'Other' choice")
        void choices() {
            assertEquals("Offensive language", ReviewFlagReason.OFFENSIVE_LANGUAGE.getDisplayName());
            assertEquals("Not related to the deal", ReviewFlagReason.NOT_RELATED_TO_DEAL.getDisplayName());
            assertEquals("Other", ReviewFlagReason.OTHER.getDisplayName());
            assertEquals(6, ReviewFlagReason.values().length);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ReviewFlagReason.class)
        @DisplayName("Only 'Other' requires details, and every reason shows its name")
        void detailsRequired(ReviewFlagReason reason) {
            assertEquals(reason == ReviewFlagReason.OTHER, reason.isDetailsRequired());
            assertEquals(reason.getDisplayName(), reason.toString());
            assertFalse(reason.getDisplayName().isBlank());
        }

        @ParameterizedTest(name = "details \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("Without details, the stored reason is the name of the reason")
        void describeWithoutDetails(String details) {
            assertEquals("Spam or advertising", ReviewFlagReason.SPAM.describe(details));
        }

        @Test
        @DisplayName("Details are trimmed and added after the name of the reason")
        void describeWithDetails() {
            assertEquals("Offensive language: insults in the second sentence",
                    ReviewFlagReason.OFFENSIVE_LANGUAGE.describe("  insults in the second sentence \n"));
        }
    }
}
