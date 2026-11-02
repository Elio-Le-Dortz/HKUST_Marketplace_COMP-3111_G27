package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Act On Listing Integration Tests (US-B05)")
class DummyDataServiceActOnListingTest {

    private DummyDataService service;

    @BeforeEach
    void setUp() {
        service = DummyDataService.getInstance();
        service.resetData();
    }

    @Test
    @DisplayName("Delegate submitPriceOffer integrates properly with DummyDataService")
    void testDelegateSubmitPriceOffer() {
        Listing sellListing = service.getLiveListings().stream()
                .filter(l -> l.getType() == ListingType.SELL && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                .findFirst()
                .orElseThrow();

        ActOnListingResult result = service.submitPriceOffer(sellListing.getId(), "alice", 175.0);
        assertTrue(result.isSuccess());
        assertEquals(ActOnListingStatus.SUCCESS, result.getStatus());

        List<Offer> buyerOffers = service.getOffersForBuyer("alice");
        assertTrue(buyerOffers.stream().anyMatch(o -> o.getId().equals(result.getOffer().getId())));

        List<Offer> sellerOffers = service.getOffersForSeller(sellListing.getSellerUsername());
        assertTrue(sellerOffers.stream().anyMatch(o -> o.getId().equals(result.getOffer().getId())));

        List<Offer> allOffers = service.getAllOffers();
        assertTrue(allOffers.stream().anyMatch(o -> o.getId().equals(result.getOffer().getId())));
    }

    @Test
    @DisplayName("Delegate submitBorrowRequest integrates properly with DummyDataService")
    void testDelegateSubmitBorrowRequest() {
        Listing lendListing = service.getLiveListings().stream()
                .filter(l -> l.getType() == ListingType.LEND && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                .findFirst()
                .orElseThrow();

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end = start.plusDays(3);

        ActOnListingResult result = service.submitBorrowRequest(lendListing.getId(), "alice", start, end);
        assertTrue(result.isSuccess());
        assertEquals(OfferType.BORROW_REQUEST, result.getOffer().getType());

        List<Offer> listingOffers = service.getOffersForListing(lendListing.getId());
        assertTrue(listingOffers.stream().anyMatch(o -> o.getId().equals(result.getOffer().getId())));
    }

    @Test
    @DisplayName("Delegate submitClaim and isGiveawayClaimed work as expected")
    void testDelegateSubmitClaimAndIsClaimed() {
        Listing giveaway = service.getLiveListings().stream()
                .filter(l -> l.getType() == ListingType.GIVEAWAY && !"alice".equalsIgnoreCase(l.getSellerUsername()))
                .findFirst()
                .orElseThrow();

        assertFalse(service.isGiveawayClaimed(giveaway.getId()));

        ActOnListingResult result = service.submitClaim(giveaway.getId(), "alice");
        assertTrue(result.isSuccess());
        assertEquals(OfferType.CLAIM, result.getOffer().getType());

        assertTrue(service.isGiveawayClaimed(giveaway.getId()));

        // Second claim rejected
        ActOnListingResult secondResult = service.submitClaim(giveaway.getId(), "bob");
        assertFalse(secondResult.isSuccess());
        assertEquals(ActOnListingStatus.ALREADY_CLAIMED, secondResult.getStatus());
    }

    @Test
    @DisplayName("Offer methods and getters return independent copies / correct filtering")
    void testOfferQueries() {
        Offer customOffer = new Offer();
        customOffer.setType(OfferType.PRICE_OFFER);
        customOffer.setListingId("L999");
        customOffer.setBuyerUsername("custom_buyer");
        customOffer.setSellerUsername("custom_seller");
        customOffer.setPrice(99.0);

        service.addOffer(customOffer);
        assertNotNull(customOffer.getId());
        assertTrue(customOffer.getId().startsWith("O"));

        assertEquals(1, service.getOffersForListing("L999").size());
        assertEquals(1, service.getOffersForBuyer("custom_buyer").size());
        assertEquals(1, service.getOffersForSeller("custom_seller").size());
        assertNotNull(service.getActOnListingService());
    }
}
