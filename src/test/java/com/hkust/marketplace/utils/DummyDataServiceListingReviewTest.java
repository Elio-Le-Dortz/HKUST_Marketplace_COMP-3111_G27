package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Listing Review Integration Tests (US-A02)")
class DummyDataServiceListingReviewTest {

    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
    }

    @Test
    @DisplayName("getListingReviewService returns non-null service instance")
    void testGetListingReviewService() {
        assertNotNull(dataService.getListingReviewService());
    }

    @Test
    @DisplayName("getPendingListings returns oldest-first pending listings from review service")
    void testGetPendingListingsOldestFirst() {
        List<Listing> pending = dataService.getPendingListings();
        assertFalse(pending.isEmpty());
        for (Listing l : pending) {
            assertEquals(ListingStatus.PENDING_REVIEW, l.getStatus());
        }
    }

    @Test
    @DisplayName("approveListing delegates to listing review service and approves listing")
    void testApproveListingDelegation() {
        List<Listing> pending = dataService.getPendingListings();
        Listing target = pending.get(0);

        ListingReviewResult result = dataService.approveListing(target.getId(), "admin");
        assertTrue(result.isSuccess());
        assertEquals(ListingReviewStatus.APPROVED, result.getStatus());
        assertEquals(ListingStatus.LIVE, dataService.findListing(target.getId()).getStatus());
    }

    @Test
    @DisplayName("rejectListing delegates to listing review service and rejects listing with reason")
    void testRejectListingDelegation() {
        List<Listing> pending = dataService.getPendingListings();
        Listing target = pending.get(0);

        String reason = "Violation of campus sales policy.";
        ListingReviewResult result = dataService.rejectListing(target.getId(), "admin", reason);
        assertTrue(result.isSuccess());
        assertEquals(ListingReviewStatus.REJECTED, result.getStatus());
        assertEquals(ListingStatus.REJECTED, dataService.findListing(target.getId()).getStatus());
        assertEquals(reason, dataService.findListing(target.getId()).getAdminReason());
    }
}
