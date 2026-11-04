package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Listing Detail Domain Models Tests (US-B04)")
class ListingDetailModelTest {

    @Test
    @DisplayName("Listing photo management: enforces 1..6 photo upper bound and defensive copy")
    void testListingPhotoBounds() {
        Listing listing = new Listing();
        assertTrue(listing.getPhotos().isEmpty());

        for (int i = 1; i <= 6; i++) {
            listing.addPhoto(new Photo("photo" + i + ".png"));
        }
        assertEquals(6, listing.getPhotos().size());

        // Attempting to add a 7th photo should be ignored (capped at 6)
        listing.addPhoto(new Photo("photo7.png"));
        assertEquals(6, listing.getPhotos().size());

        // Null photo ignored
        listing.addPhoto(null);
        assertEquals(6, listing.getPhotos().size());

        // Defensive copy check
        List<Photo> photos = listing.getPhotos();
        photos.clear();
        assertEquals(6, listing.getPhotos().size(), "Listing photos should be defensively copied");

        // setPhotos capped at 6
        List<Photo> oversized = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            oversized.add(new Photo("p" + i + ".png"));
        }
        listing.setPhotos(oversized);
        assertEquals(6, listing.getPhotos().size());

        listing.setPhotos(null);
        assertTrue(listing.getPhotos().isEmpty());
    }

    @Test
    @DisplayName("Listing view count getters and setters")
    void testListingViewCount() {
        Listing listing = new Listing();
        assertEquals(0, listing.getViewCount());

        listing.incrementViewCount();
        assertEquals(1, listing.getViewCount());

        listing.setViewCount(42);
        assertEquals(42, listing.getViewCount());
    }

    @Test
    @DisplayName("SellerRatingInfo: withRatings, noRatings, equality, and toString")
    void testSellerRatingInfoModel() {
        SellerRatingInfo infoWithRating = SellerRatingInfo.withRatings("charlie", "Charlie Lee", 4.7, 12);
        assertTrue(infoWithRating.hasRatings());
        assertEquals("charlie", infoWithRating.getSellerUsername());
        assertEquals("Charlie Lee", infoWithRating.getSellerFullName());
        assertEquals(4.7, infoWithRating.getAverageRating());
        assertEquals(12, infoWithRating.getRatingCount());
        assertEquals("4.7", infoWithRating.getFormattedScore());
        assertEquals("★ 4.7 / 5.0 (12 reviews)", infoWithRating.getDisplayText());

        SellerRatingInfo infoNoRating = SellerRatingInfo.noRatings("diana", "Diana Ng");
        assertFalse(infoNoRating.hasRatings());
        assertEquals("diana", infoNoRating.getSellerUsername());
        assertEquals("Diana Ng", infoNoRating.getSellerFullName());
        assertEquals(0.0, infoNoRating.getAverageRating());
        assertEquals(0, infoNoRating.getRatingCount());
        assertEquals("N/A", infoNoRating.getFormattedScore());
        assertEquals("No ratings yet", infoNoRating.getDisplayText());

        // Equals and hashCode
        SellerRatingInfo copy = SellerRatingInfo.withRatings("charlie", "Charlie Lee", 4.7, 12);
        assertEquals(infoWithRating, copy);
        assertEquals(infoWithRating.hashCode(), copy.hashCode());
        assertNotEquals(infoWithRating, infoNoRating);
        assertNotEquals(infoWithRating, null);
        assertNotEquals(infoWithRating, "different type");

        assertTrue(infoWithRating.toString().contains("charlie"));
    }

    @Test
    @DisplayName("ListingViewResult: success, notFound, notLive, equality, and toString")
    void testListingViewResultModel() {
        Listing listing = new Listing();
        listing.setTitle("Sample Book");
        SellerRatingInfo ratingInfo = SellerRatingInfo.noRatings("charlie", "Charlie");

        ListingViewResult successResult = ListingViewResult.success(listing, ratingInfo);
        assertTrue(successResult.isSuccess());
        assertEquals(ListingViewStatus.SUCCESS, successResult.getStatus());
        assertEquals(listing, successResult.getListing());
        assertEquals(ratingInfo, successResult.getSellerRating());
        assertNull(successResult.getMessage());

        ListingViewResult notFound = ListingViewResult.notFound("Listing not found.");
        assertFalse(notFound.isSuccess());
        assertEquals(ListingViewStatus.NOT_FOUND, notFound.getStatus());
        assertEquals("Listing not found.", notFound.getMessage());

        ListingViewResult notLive = ListingViewResult.notLive("This listing is no longer available.");
        assertFalse(notLive.isSuccess());
        assertEquals(ListingViewStatus.NOT_LIVE, notLive.getStatus());
        assertEquals("This listing is no longer available.", notLive.getMessage());

        // Equals and hashCode
        ListingViewResult successCopy = ListingViewResult.success(listing, ratingInfo);
        assertEquals(successResult, successCopy);
        assertEquals(successResult.hashCode(), successCopy.hashCode());
        assertNotEquals(successResult, notFound);
        assertNotEquals(successResult, null);
        assertNotEquals(successResult, "string");

        assertTrue(successResult.toString().contains("Sample Book"));
        assertTrue(notFound.toString().contains("Listing not found."));
    }

    @Test
    @DisplayName("ReportResult: success, failure, equality, and toString")
    void testReportResultModel() {
        Report report = new Report("REP1", "L1", "Book", "alice", "Duplicate item");
        ReportResult success = ReportResult.success(report);
        assertTrue(success.isSuccess());
        assertEquals(report, success.getReport());
        assertNull(success.getErrorMessage());

        ReportResult failure = ReportResult.failure("Invalid reason.");
        assertFalse(failure.isSuccess());
        assertNull(failure.getReport());
        assertEquals("Invalid reason.", failure.getErrorMessage());

        ReportResult successCopy = ReportResult.success(report);
        assertEquals(success, successCopy);
        assertEquals(success.hashCode(), successCopy.hashCode());
        assertNotEquals(success, failure);
        assertNotEquals(success, null);
        assertNotEquals(success, "other");

        assertTrue(success.toString().contains("REP1"));
        assertTrue(failure.toString().contains("Invalid reason."));
    }
}
