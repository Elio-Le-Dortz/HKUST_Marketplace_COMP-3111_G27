package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Listing Review Models & DTO Tests (US-A02)")
class ListingReviewModelsTest {

    @Test
    @DisplayName("ListingReviewStatus enum coverage")
    void testListingReviewStatusEnum() {
        for (ListingReviewStatus s : ListingReviewStatus.values()) {
            assertNotNull(s);
            assertEquals(s, ListingReviewStatus.valueOf(s.name()));
        }
    }

    @Test
    @DisplayName("ListingReviewResult approved factory creates successful result")
    void testApprovedResult() {
        Listing listing = new Listing();
        listing.setId("L100");
        listing.setTitle("Mini Fridge");
        LocalDateTime now = LocalDateTime.now();

        ListingReviewResult result = ListingReviewResult.approved(listing, now);
        assertTrue(result.isSuccess());
        assertEquals(ListingReviewStatus.APPROVED, result.getStatus());
        assertEquals(listing, result.getListing());
        assertNull(result.getRejectionReason());
        assertEquals(now, result.getReviewedAt());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("approved"));
    }

    @Test
    @DisplayName("ListingReviewResult rejected factory creates successful rejection result with reason")
    void testRejectedResult() {
        Listing listing = new Listing();
        listing.setId("L100");
        LocalDateTime now = LocalDateTime.now();
        String reason = "Item description lacks photos of damage.";

        ListingReviewResult result = ListingReviewResult.rejected(listing, reason, now);
        assertTrue(result.isSuccess());
        assertEquals(ListingReviewStatus.REJECTED, result.getStatus());
        assertEquals(listing, result.getListing());
        assertEquals(reason, result.getRejectionReason());
        assertEquals(now, result.getReviewedAt());
        assertTrue(result.getMessage().contains("rejected"));
    }

    @Test
    @DisplayName("ListingReviewResult emptyReason factory returns failure")
    void testEmptyReasonResult() {
        Listing listing = new Listing();
        ListingReviewResult result = ListingReviewResult.emptyReason(listing);

        assertFalse(result.isSuccess());
        assertEquals(ListingReviewStatus.EMPTY_REASON, result.getStatus());
        assertEquals(listing, result.getListing());
        assertNull(result.getRejectionReason());
        assertNull(result.getReviewedAt());
        assertTrue(result.getMessage().contains("mandatory"));
    }

    @Test
    @DisplayName("ListingReviewResult notPending factory handles null and non-null listing")
    void testNotPendingResult() {
        Listing listing = new Listing();
        listing.setStatus(ListingStatus.LIVE);

        ListingReviewResult resultWithListing = ListingReviewResult.notPending(listing);
        assertFalse(resultWithListing.isSuccess());
        assertEquals(ListingReviewStatus.NOT_PENDING_REVIEW, resultWithListing.getStatus());
        assertTrue(resultWithListing.getMessage().contains("LIVE"));

        Listing nullStatusListing = new Listing();
        nullStatusListing.setStatus(null);
        ListingReviewResult resultNullStatus = ListingReviewResult.notPending(nullStatusListing);
        assertTrue(resultNullStatus.getMessage().contains("UNKNOWN"));

        ListingReviewResult resultNullListing = ListingReviewResult.notPending(null);
        assertTrue(resultNullListing.getMessage().contains("UNKNOWN"));
    }

    @Test
    @DisplayName("ListingReviewResult notFound factory returns failure with listing ID")
    void testNotFoundResult() {
        ListingReviewResult result = ListingReviewResult.notFound("L999");
        assertFalse(result.isSuccess());
        assertEquals(ListingReviewStatus.LISTING_NOT_FOUND, result.getStatus());
        assertNull(result.getListing());
        assertTrue(result.getMessage().contains("L999"));
    }

    @Test
    @DisplayName("ListingReviewResult unauthorized factory handles null and custom message")
    void testUnauthorizedResult() {
        ListingReviewResult defaultResult = ListingReviewResult.unauthorized(null);
        assertFalse(defaultResult.isSuccess());
        assertEquals(ListingReviewStatus.UNAUTHORIZED, defaultResult.getStatus());
        assertTrue(defaultResult.getMessage().contains("administrators"));

        ListingReviewResult customResult = ListingReviewResult.unauthorized("Custom auth error");
        assertEquals("Custom auth error", customResult.getMessage());
    }

    @Test
    @DisplayName("ListingReviewResult invalidInput factory handles null and custom message")
    void testInvalidInputResult() {
        ListingReviewResult defaultResult = ListingReviewResult.invalidInput(null);
        assertFalse(defaultResult.isSuccess());
        assertEquals(ListingReviewStatus.INVALID_INPUT, defaultResult.getStatus());
        assertTrue(defaultResult.getMessage().contains("Invalid"));

        ListingReviewResult customResult = ListingReviewResult.invalidInput("Listing ID missing");
        assertEquals("Listing ID missing", customResult.getMessage());
    }

    @Test
    @DisplayName("ListingReviewRequest factory methods, getters, setters, and equals/hashCode")
    void testListingReviewRequest() {
        ListingReviewRequest approveReq = ListingReviewRequest.approveRequest("L100", "admin");
        assertTrue(approveReq.isApprove());
        assertEquals("L100", approveReq.getListingId());
        assertEquals("admin", approveReq.getAdminUsername());
        assertNull(approveReq.getRejectionReason());

        ListingReviewRequest rejectReq = ListingReviewRequest.rejectRequest("L100", "admin", "Inappropriate content");
        assertFalse(rejectReq.isApprove());
        assertEquals("L100", rejectReq.getListingId());
        assertEquals("admin", rejectReq.getAdminUsername());
        assertEquals("Inappropriate content", rejectReq.getRejectionReason());

        // Equals and HashCode
        ListingReviewRequest rejectReq2 = ListingReviewRequest.rejectRequest("L100", "admin", "Inappropriate content");
        assertEquals(rejectReq, rejectReq2);
        assertEquals(rejectReq.hashCode(), rejectReq2.hashCode());
        assertEquals(rejectReq, rejectReq);
        assertNotEquals(rejectReq, null);
        assertNotEquals(rejectReq, "some-string");
        assertNotEquals(approveReq, rejectReq);

        ListingReviewRequest mod = new ListingReviewRequest();
        mod.setListingId("L101");
        mod.setAdminUsername("admin2");
        mod.setRejectionReason("Different reason");
        mod.setApprove(true);
        assertNotEquals(approveReq, mod);
    }

    @Test
    @DisplayName("Listing model review status predicates and reviewedAt accessor")
    void testListingStatusPredicatesAndReviewedAt() {
        Listing l = new Listing();
        l.setStatus(ListingStatus.PENDING_REVIEW);
        assertTrue(l.isPendingReview());
        assertFalse(l.isLive());
        assertFalse(l.isRejected());

        l.setStatus(ListingStatus.LIVE);
        assertFalse(l.isPendingReview());
        assertTrue(l.isLive());
        assertFalse(l.isRejected());

        l.setStatus(ListingStatus.REJECTED);
        assertFalse(l.isPendingReview());
        assertFalse(l.isLive());
        assertTrue(l.isRejected());

        // ReviewedAt tests
        LocalDateTime posted = LocalDateTime.of(2026, 11, 1, 10, 0);
        l.setPostedAt(posted);
        assertEquals(posted, l.getReviewedAt()); // Fallback when reviewedAt is null

        LocalDateTime reviewed = LocalDateTime.of(2026, 11, 5, 14, 0);
        l.setReviewedAt(reviewed);
        assertEquals(posted, l.getReviewedAt());
    }
}
