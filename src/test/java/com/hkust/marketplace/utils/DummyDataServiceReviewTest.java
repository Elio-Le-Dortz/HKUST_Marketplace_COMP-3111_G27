package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-B11: DummyDataService Review & Rating Integration Tests")
class DummyDataServiceReviewTest {

    private DummyDataService service;

    @BeforeEach
    void setUp() {
        service = DummyDataService.getInstance();
        service.resetData();
    }

    @Test
    @DisplayName("getBuyerReviewService returns singleton service instance")
    void testGetBuyerReviewService() {
        assertNotNull(service.getBuyerReviewService());
    }

    @Test
    @DisplayName("recalculateSellerRating handles null, empty, non-existent, and sellers with/without reviews")
    void testRecalculateSellerRating() {
        // Null or empty
        SellerRatingInfo nullInfo = service.recalculateSellerRating(null);
        assertFalse(nullInfo.hasRatings());
        assertEquals("Unknown Seller", nullInfo.getSellerFullName());

        SellerRatingInfo emptyInfo = service.recalculateSellerRating("   ");
        assertFalse(emptyInfo.hasRatings());

        // Non-existent user
        SellerRatingInfo nonExistent = service.recalculateSellerRating("ghost_seller_xyz");
        assertFalse(nonExistent.hasRatings());
        assertEquals("ghost_seller_xyz", nonExistent.getSellerFullName());

        // Charlie has seeded reviews (2 reviews with 5 stars each)
        SellerRatingInfo charlieInfo = service.recalculateSellerRating("charlie");
        assertTrue(charlieInfo.hasRatings());
        assertEquals(5.0, charlieInfo.getAverageRating());
        assertEquals(2, charlieInfo.getRatingCount());

        // Add a hidden review - must NOT be included in average
        Review hidden = new Review();
        hidden.setSellerUsername("charlie");
        hidden.setRating(1);
        hidden.setHidden(true);
        service.addReview(hidden);

        SellerRatingInfo afterHidden = service.recalculateSellerRating("charlie");
        assertEquals(5.0, afterHidden.getAverageRating());
        assertEquals(2, afterHidden.getRatingCount());

        // Add an invalid star review (e.g. 0) - must NOT be included
        Review invalidRating = new Review();
        invalidRating.setSellerUsername("charlie");
        invalidRating.setRating(0);
        invalidRating.setHidden(false);
        service.addReview(invalidRating);

        SellerRatingInfo afterInvalid = service.recalculateSellerRating("charlie");
        assertEquals(5.0, afterInvalid.getAverageRating());
        assertEquals(2, afterInvalid.getRatingCount());
    }

    @Test
    @DisplayName("getReviewsByBuyer returns matching buyer reviews and handles null/empty")
    void testGetReviewsByBuyer() {
        assertTrue(service.getReviewsByBuyer(null).isEmpty());
        assertTrue(service.getReviewsByBuyer("   ").isEmpty());

        List<Review> aliceReviews = service.getReviewsByBuyer("alice");
        assertNotNull(aliceReviews);
        assertFalse(aliceReviews.isEmpty());
        assertTrue(aliceReviews.stream().allMatch(r -> "alice".equalsIgnoreCase(r.getBuyerUsername())));
    }

    @Test
    @DisplayName("getReviewForDeal returns matching review by deal ID")
    void testGetReviewForDeal() {
        assertTrue(service.getReviewForDeal(null).isEmpty());
        assertTrue(service.getReviewForDeal("  ").isEmpty());
        assertTrue(service.getReviewForDeal("NON_EXISTENT_DEAL").isEmpty());

        Review rev = new Review();
        rev.setDealId("D_DEAL_TEST");
        rev.setSellerUsername("charlie");
        rev.setBuyerUsername("alice");
        rev.setRating(5);
        service.addReview(rev);

        Optional<Review> opt = service.getReviewForDeal("D_DEAL_TEST");
        assertTrue(opt.isPresent());
        assertEquals("D_DEAL_TEST", opt.get().getDealId());
        assertEquals(5, opt.get().getRating());
    }

    @Test
    @DisplayName("addReview generates ID if null and auto-recalculates seller average")
    void testAddReviewGeneratesIdAndRecalculates() {
        Seller testSeller = new Seller("auto_recalc_seller", "Auto Recalc", "Pass1234!");
        service.registerUser(testSeller);

        assertEquals(0.0, testSeller.getAverageRating());
        assertEquals(0, testSeller.getRatingCount());

        Review r = new Review();
        r.setSellerUsername("auto_recalc_seller");
        r.setRating(4);
        assertNull(r.getId());

        service.addReview(r);
        assertNotNull(r.getId());
        assertTrue(r.getId().startsWith("R"));

        assertEquals(4.0, testSeller.getAverageRating());
        assertEquals(1, testSeller.getRatingCount());
    }
}
