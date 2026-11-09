package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.AdminRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Admin Rating Integration Test [US-A08]")
class DummyDataServiceAdminRatingTest {

    private DummyDataService service;

    @BeforeEach
    void setUp() {
        service = DummyDataService.getInstance();
        service.resetData();
        SessionManager.login(service.findUser(DummyDataService.SEEDED_ADMIN_USERNAME));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("AdminRatingService is correctly wired and accessible")
    void testAdminRatingServiceWired() {
        AdminRatingService adminRatingService = service.getAdminRatingService();
        assertNotNull(adminRatingService);
        assertNotNull(adminRatingService.getOrderedReviews());
        assertNotNull(adminRatingService.getSummaryMetrics());
    }

    @Test
    @DisplayName("DummyDataService delegates hide, unhide, and dismissFlag correctly")
    void testDummyDataServiceDelegates() {
        Review r = new Review("charlie", "bob", "D100", "L100", "Test Book", 4, "Nice item");
        r.setId("R_DELEGATE_TEST");
        r.setFlagged(true);
        r.setFlagReason("Seller complained");
        r.setHidden(false);
        service.addReview(r);

        // 1. Delegate dismiss flag
        AdminRatingOperationResult dismissRes = service.dismissReviewFlagByAdmin("R_DELEGATE_TEST", "admin");
        assertTrue(dismissRes.isSuccess());
        assertFalse(r.isFlagged());

        // 2. Delegate hide
        AdminRatingOperationResult hideRes = service.hideReviewByAdmin("R_DELEGATE_TEST", "admin");
        assertTrue(hideRes.isSuccess());
        assertTrue(r.isHidden());

        // 3. Delegate unhide
        AdminRatingOperationResult unhideRes = service.unhideReviewByAdmin("R_DELEGATE_TEST", "admin");
        assertTrue(unhideRes.isSuccess());
        assertFalse(r.isHidden());
    }

    @Test
    @DisplayName("Acceptance Criteria: Hidden review is excluded from seller average rating calculation")
    void testAcceptanceCriteriaHiddenReviewExcludedFromAverageRating() {
        Seller seller = new Seller("seller_ac", "AC Seller", "pass");
        service.registerUser(seller);

        // Add 5-star review and 1-star review
        Review r5 = new Review("seller_ac", "buyer1", "D1", "L1", "Item A", 5, "Perfect");
        r5.setId("R_AC_5");
        Review r1 = new Review("seller_ac", "buyer2", "D2", "L2", "Item B", 1, "Awful");
        r1.setId("R_AC_1");

        service.addReview(r5);
        service.addReview(r1);

        // Initial rating: (5 + 1) / 2 = 3.0
        service.recalculateSellerRating("seller_ac");
        assertEquals(3.0, seller.getAverageRating());
        assertEquals(2, seller.getRatingCount());

        // Hide the 1-star review via admin
        AdminRatingOperationResult res = service.hideReviewByAdmin("R_AC_1", "admin");
        assertTrue(res.isSuccess());

        // Seller average rating is now immediately 5.0 with 1 rating count
        assertEquals(5.0, seller.getAverageRating());
        assertEquals(1, seller.getRatingCount());

        // Seller's review list only contains visible reviews
        assertEquals(1, service.getReviewsForSeller("seller_ac").size());
        assertEquals("R_AC_5", service.getReviewsForSeller("seller_ac").get(0).getId());

        // Unhide reinstates it
        service.unhideReviewByAdmin("R_AC_1", "admin");
        assertEquals(3.0, seller.getAverageRating());
        assertEquals(2, seller.getRatingCount());
    }
}
