package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Listing Detail Integration Tests (US-B04)")
class DummyDataServiceListingDetailTest {

    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
    }

    @Test
    @DisplayName("DummyDataService provides ListingDetailService instance")
    void testGetListingDetailService() {
        assertNotNull(dataService.getListingDetailService());
    }

    @Test
    @DisplayName("viewListing delegate increments view count and retrieves listing")
    void testViewListingDelegate() {
        List<Listing> liveListings = dataService.getLiveListings();
        assertFalse(liveListings.isEmpty());

        Listing l = liveListings.get(0);
        int initialViews = l.getViewCount();

        ListingViewResult res = dataService.viewListing(l.getId(), true);
        assertTrue(res.isSuccess());
        assertEquals(initialViews + 1, res.getListing().getViewCount());
    }

    @Test
    @DisplayName("getSellerRatingInfo delegate returns rating info")
    void testGetSellerRatingInfoDelegate() {
        SellerRatingInfo rating = dataService.getSellerRatingInfo("charlie");
        assertNotNull(rating);
        assertTrue(rating.hasRatings());
        assertTrue(rating.getAverageRating() > 0);
    }

    @Test
    @DisplayName("reportListing delegate files report and notifies admin")
    void testReportListingDelegate() {
        List<Listing> liveListings = dataService.getLiveListings();
        assertFalse(liveListings.isEmpty());

        Listing l = liveListings.get(0);
        int initialReports = dataService.getAllReports().size();
        int initialAdminNotifs = dataService.getNotificationsFor("admin").size();

        ReportResult res = dataService.reportListing(l.getId(), "alice", "Inappropriate description");
        assertTrue(res.isSuccess());
        assertEquals(initialReports + 1, dataService.getAllReports().size());
        assertEquals(initialAdminNotifs + 1, dataService.getNotificationsFor("admin").size());
    }
}
