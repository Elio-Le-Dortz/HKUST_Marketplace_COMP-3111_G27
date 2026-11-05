package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the result types of use case 2.5 "Manage Offers And Requests" (US-S05).
 */
@DisplayName("OfferManagementResult and OfferManagementStatus (US-S05)")
class OfferManagementModelsTest {

    private static Offer offer() {
        return new Offer("O1", OfferType.PRICE_OFFER, "L1", "buyer", "seller");
    }

    @Test
    @DisplayName("accepted() holds the offer, the deal and the number of declined offers")
    void accepted() {
        Offer offer = offer();
        Deal deal = new Deal();

        OfferManagementResult result = OfferManagementResult.accepted(offer, deal, 3, "Accepted.");

        assertTrue(result.isSuccess());
        assertEquals(OfferManagementStatus.SUCCESS, result.getStatus());
        assertEquals("Accepted.", result.getMessage());
        assertSame(offer, result.getOffer());
        assertSame(deal, result.getDeal());
        assertEquals(3, result.getOtherOffersDeclined());
        assertNull(result.getChatThread());
    }

    @Test
    @DisplayName("success() holds the offer only")
    void success() {
        Offer offer = offer();

        OfferManagementResult result = OfferManagementResult.success(offer, "Declined.");

        assertTrue(result.isSuccess());
        assertSame(offer, result.getOffer());
        assertNull(result.getDeal());
        assertEquals(0, result.getOtherOffersDeclined());
        assertNull(result.getChatThread());
    }

    @Test
    @DisplayName("chatOpened() holds the offer and the chat thread")
    void chatOpened() {
        Offer offer = offer();
        ChatThread thread = new ChatThread("T1", "L1", "buyer", "seller");

        OfferManagementResult result = OfferManagementResult.chatOpened(offer, thread);

        assertTrue(result.isSuccess());
        assertSame(thread, result.getChatThread());
        assertSame(offer, result.getOffer());
        assertFalse(result.getMessage().isEmpty());
    }

    @Test
    @DisplayName("failure() without offer reveals no offer")
    void failureWithoutOffer() {
        OfferManagementResult result = OfferManagementResult.failure(OfferManagementStatus.UNAUTHORIZED, "Not yours.");

        assertFalse(result.isSuccess());
        assertEquals(OfferManagementStatus.UNAUTHORIZED, result.getStatus());
        assertEquals("Not yours.", result.getMessage());
        assertNull(result.getOffer());
        assertNull(result.getDeal());
    }

    @Test
    @DisplayName("failure() with an offer keeps it")
    void failureWithOffer() {
        Offer offer = offer();

        OfferManagementResult result = OfferManagementResult.failure(
                OfferManagementStatus.LISTING_NOT_AVAILABLE, offer, "Gone.");

        assertFalse(result.isSuccess());
        assertSame(offer, result.getOffer());
    }

    @Test
    @DisplayName("A failure cannot have the SUCCESS status")
    void failureCannotSucceed() {
        assertThrows(IllegalArgumentException.class,
                () -> OfferManagementResult.failure(OfferManagementStatus.SUCCESS, "Oops"));
        assertThrows(IllegalArgumentException.class,
                () -> OfferManagementResult.failure(OfferManagementStatus.SUCCESS, offer(), "Oops"));
    }

    @Test
    @DisplayName("A missing status is refused and a missing message becomes empty")
    void missingValues() {
        assertThrows(NullPointerException.class, () -> OfferManagementResult.failure(null, "No status"));
        assertEquals("", OfferManagementResult.success(offer(), null).getMessage());
    }

    @Test
    @DisplayName("toString() shows the status and the message")
    void describesItself() {
        String text = OfferManagementResult.failure(OfferManagementStatus.INVALID_REASON, "Too long.").toString();

        assertTrue(text.contains("INVALID_REASON"));
        assertTrue(text.contains("Too long."));
    }

    @Test
    @DisplayName("Every status of the specification exists")
    void statuses() {
        assertEquals(11, OfferManagementStatus.values().length);
        for (OfferManagementStatus status : OfferManagementStatus.values()) {
            assertEquals(status, OfferManagementStatus.valueOf(status.name()));
        }
    }
}
