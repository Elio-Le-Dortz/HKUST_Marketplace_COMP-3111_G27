package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-B11: Rate Seller Domain Models & DTO Tests")
class RateSellerModelsTest {

    @Test
    @DisplayName("Review model full constructors, getters, setters, and aliases")
    void testReviewModel() {
        LocalDateTime now = LocalDateTime.now();
        Review r = new Review("charlie", "alice", "D500", "L100", "Calculus Book", 5, "Great condition!");
        r.setId("R10");
        r.setReply("Thank you!");
        r.setFlagged(true);
        r.setFlagReason("Inappropriate language");
        r.setHidden(false);
        r.setDate(now);

        assertEquals("R10", r.getId());
        assertEquals("charlie", r.getSellerUsername());
        assertEquals("alice", r.getBuyerUsername());
        assertEquals("D500", r.getDealId());
        assertEquals("L100", r.getListingId());
        assertEquals("Calculus Book", r.getListingTitle());
        assertEquals(5, r.getRating());
        assertEquals("Great condition!", r.getComment());
        assertEquals("Thank you!", r.getReply());
        assertEquals("Thank you!", r.getSellerReply());
        assertTrue(r.isFlagged());
        assertEquals("Inappropriate language", r.getFlagReason());
        assertFalse(r.isHidden());
        assertEquals(now, r.getDate());
        assertEquals(now, r.getCreatedAt());
        assertTrue(r.isStarValid());
        assertTrue(r.hasComment());
        assertTrue(r.hasReply());

        // Test backward-compatible aliases and setters
        r.setSellerReply("New reply");
        assertEquals("New reply", r.getReply());
        LocalDateTime later = now.plusDays(1);
        r.setCreatedAt(later);
        assertEquals(later, r.getDate());

        // Test boundary stars
        r.setRating(0);
        assertFalse(r.isStarValid());
        r.setRating(6);
        assertFalse(r.isStarValid());
        r.setRating(1);
        assertTrue(r.isStarValid());

        // Empty comment & reply
        r.setComment("   ");
        assertFalse(r.hasComment());
        r.setComment(null);
        assertFalse(r.hasComment());

        r.setReply("   ");
        assertFalse(r.hasReply());
        r.setReply(null);
        assertFalse(r.hasReply());

        // Secondary constructor without listingId
        Review r2 = new Review("diana", "bob", "D501", "Mini Fridge", 4, "Cool fridge");
        assertEquals("diana", r2.getSellerUsername());
        assertEquals("bob", r2.getBuyerUsername());
        assertEquals("D501", r2.getDealId());
        assertNull(r2.getListingId());
        assertEquals("Mini Fridge", r2.getListingTitle());
        assertEquals(4, r2.getRating());
        assertEquals("Cool fridge", r2.getComment());
    }

    @Test
    @DisplayName("RateSellerRequest creation, trimming, equals and hashCode")
    void testRateSellerRequest() {
        RateSellerRequest req1 = RateSellerRequest.of(" D500 ", " alice ", 5, " Great! ");
        assertEquals("D500", req1.getDealId());
        assertEquals("alice", req1.getBuyerUsername());
        assertEquals(5, req1.getRating());
        assertEquals("Great!", req1.getComment());
        assertTrue(req1.hasComment());

        RateSellerRequest req2 = RateSellerRequest.of("D500", "alice", 5, "Great!");
        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
        assertTrue(req1.toString().contains("D500"));

        RateSellerRequest reqNoComment = RateSellerRequest.of("D501", "bob", 4);
        assertEquals("D501", reqNoComment.getDealId());
        assertEquals("bob", reqNoComment.getBuyerUsername());
        assertEquals(4, reqNoComment.getRating());
        assertEquals("", reqNoComment.getComment());
        assertFalse(reqNoComment.hasComment());

        assertNotEquals(req1, reqNoComment);
        assertNotEquals(req1, null);
        assertNotEquals(req1, "other");

        // Request equals branch tests
        assertNotEquals(req1, RateSellerRequest.of("D_DIFF", "alice", 5, "Great!"));
        assertNotEquals(req1, RateSellerRequest.of("D500", "diff_user", 5, "Great!"));
        assertNotEquals(req1, RateSellerRequest.of("D500", "alice", 4, "Great!"));
        assertNotEquals(req1, RateSellerRequest.of("D500", "alice", 5, "Different!"));
    }

    @Test
    @DisplayName("RateSellerResult factory methods, equality, and getters")
    void testRateSellerResult() {
        Deal deal = new Deal();
        deal.setId("D500");
        Review review = new Review();
        review.setId("R1");
        SellerRatingInfo info = SellerRatingInfo.withRatings("charlie", "Charlie Lee", 4.8, 5);

        RateSellerResult success = RateSellerResult.success(review, deal, info, "Success message");
        assertTrue(success.isSuccess());
        assertEquals(RateSellerStatus.SUCCESS, success.getStatus());
        assertEquals("Success message", success.getMessage());
        assertEquals(review, success.getReview());
        assertEquals(deal, success.getDeal());
        assertEquals(info, success.getSellerRatingInfo());

        RateSellerResult successCopy = RateSellerResult.success(review, deal, info, "Success message");
        assertEquals(success, successCopy);
        assertEquals(success.hashCode(), successCopy.hashCode());
        assertTrue(success.toString().contains("SUCCESS"));

        RateSellerResult noStar = RateSellerResult.noStarRating("Star required");
        assertFalse(noStar.isSuccess());
        assertEquals(RateSellerStatus.NO_STAR_RATING, noStar.getStatus());
        assertEquals("Star required", noStar.getMessage());

        RateSellerResult already = RateSellerResult.alreadyRated(deal, "Already rated");
        assertFalse(already.isSuccess());
        assertEquals(RateSellerStatus.ALREADY_RATED, already.getStatus());
        assertEquals(deal, already.getDeal());

        RateSellerResult skipped = RateSellerResult.skipped(deal, "Skipped prompt");
        assertFalse(skipped.isSuccess());
        assertEquals(RateSellerStatus.SKIPPED, skipped.getStatus());

        RateSellerResult cancelled = RateSellerResult.cancelled(deal, "Cancelled");
        assertFalse(cancelled.isSuccess());
        assertEquals(RateSellerStatus.CANCELLED, cancelled.getStatus());

        RateSellerResult failure = RateSellerResult.failure(RateSellerStatus.DEAL_NOT_FOUND, "Not found");
        assertFalse(failure.isSuccess());
        assertEquals(RateSellerStatus.DEAL_NOT_FOUND, failure.getStatus());

        assertNotEquals(success, failure);
        assertNotEquals(success, null);
        assertNotEquals(success, "string");

        // Equals branch tests with differing individual fields
        assertNotEquals(success, new RateSellerResult(false, success.getStatus(), success.getMessage(), review, deal, info));
        assertNotEquals(success, new RateSellerResult(true, RateSellerStatus.ALREADY_RATED, success.getMessage(), review, deal, info));
        assertNotEquals(success, new RateSellerResult(true, success.getStatus(), "different message", review, deal, info));
        assertNotEquals(success, new RateSellerResult(true, success.getStatus(), success.getMessage(), new Review(), deal, info));
        assertNotEquals(success, new RateSellerResult(true, success.getStatus(), success.getMessage(), review, new Deal(), info));
        assertNotEquals(success, new RateSellerResult(true, success.getStatus(), success.getMessage(), review, deal, SellerRatingInfo.noRatings("diff", "diff")));
    }

    @Test
    @DisplayName("F25: Number formatting in SellerRatingInfo is locale-independent")
    void testSellerRatingInfoLocaleIndependence() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE);
            SellerRatingInfo info = SellerRatingInfo.withRatings("charlie", "Charlie Lee", 4.3, 8);
            assertEquals("4.3", info.getFormattedScore());
            assertEquals("★ 4.3 / 5.0 (8 reviews)", info.getDisplayText());
            assertFalse(info.getDisplayText().contains(","));
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
