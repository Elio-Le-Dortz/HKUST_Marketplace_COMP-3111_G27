package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActOnListingResult & Status Tests (US-B05)")
class ActOnListingResultTest {

    @Test
    @DisplayName("ActOnListingStatus enum values exist")
    void testStatusEnumValues() {
        assertNotNull(ActOnListingStatus.valueOf("SUCCESS"));
        assertNotNull(ActOnListingStatus.valueOf("LISTING_NOT_FOUND"));
        assertNotNull(ActOnListingStatus.valueOf("LISTING_NOT_LIVE"));
        assertNotNull(ActOnListingStatus.valueOf("INVALID_LISTING_TYPE"));
        assertNotNull(ActOnListingStatus.valueOf("INVALID_PRICE"));
        assertNotNull(ActOnListingStatus.valueOf("INVALID_BORROW_DATES"));
        assertNotNull(ActOnListingStatus.valueOf("ALREADY_CLAIMED"));
        assertNotNull(ActOnListingStatus.valueOf("SELF_ACTION_NOT_ALLOWED"));
        assertNotNull(ActOnListingStatus.valueOf("INVALID_USER"));
    }

    @Test
    @DisplayName("Factory method success() sets all fields properly")
    void testSuccessFactory() {
        Offer offer = new Offer("O999", OfferType.PRICE_OFFER, "L101", "alice", "bob");
        offer.setPrice(150.0);
        offer.setStatus(OfferStatus.PENDING);
        offer.setCreatedDate(LocalDateTime.now());

        ActOnListingResult result = ActOnListingResult.success(offer, "Offer submitted successfully");

        assertTrue(result.isSuccess());
        assertEquals(ActOnListingStatus.SUCCESS, result.getStatus());
        assertSame(offer, result.getOffer());
        assertEquals("Offer submitted successfully", result.getMessage());
    }

    @Test
    @DisplayName("Factory methods for error statuses set fields correctly")
    void testErrorFactories() {
        ActOnListingResult r1 = ActOnListingResult.invalidPrice("Price must be positive.");
        assertFalse(r1.isSuccess());
        assertEquals(ActOnListingStatus.INVALID_PRICE, r1.getStatus());
        assertNull(r1.getOffer());
        assertEquals("Price must be positive.", r1.getMessage());

        ActOnListingResult r2 = ActOnListingResult.invalidBorrowDates("Borrow duration too short.");
        assertFalse(r2.isSuccess());
        assertEquals(ActOnListingStatus.INVALID_BORROW_DATES, r2.getStatus());
        assertEquals("Borrow duration too short.", r2.getMessage());

        ActOnListingResult r3 = ActOnListingResult.listingNotLive("Listing not available.");
        assertFalse(r3.isSuccess());
        assertEquals(ActOnListingStatus.LISTING_NOT_LIVE, r3.getStatus());

        ActOnListingResult r4 = ActOnListingResult.listingNotFound("Listing not found.");
        assertFalse(r4.isSuccess());
        assertEquals(ActOnListingStatus.LISTING_NOT_FOUND, r4.getStatus());

        ActOnListingResult r5 = ActOnListingResult.alreadyClaimed("Already claimed.");
        assertFalse(r5.isSuccess());
        assertEquals(ActOnListingStatus.ALREADY_CLAIMED, r5.getStatus());

        ActOnListingResult r6 = ActOnListingResult.invalidListingType("Mismatched type.");
        assertFalse(r6.isSuccess());
        assertEquals(ActOnListingStatus.INVALID_LISTING_TYPE, r6.getStatus());

        ActOnListingResult r7 = ActOnListingResult.selfActionNotAllowed("Cannot act on own listing.");
        assertFalse(r7.isSuccess());
        assertEquals(ActOnListingStatus.SELF_ACTION_NOT_ALLOWED, r7.getStatus());

        ActOnListingResult r8 = ActOnListingResult.invalidUser("Invalid buyer.");
        assertFalse(r8.isSuccess());
        assertEquals(ActOnListingStatus.INVALID_USER, r8.getStatus());
    }
}
