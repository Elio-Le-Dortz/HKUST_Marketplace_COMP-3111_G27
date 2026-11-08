package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.DealService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Deals & Offers Integration Tests (US-B07)")
class DummyDataServiceDealTest {

    private DummyDataService service;

    @BeforeEach
    void setUp() {
        service = DummyDataService.getInstance();
        service.resetData();
        SessionManager.login(service.findUser("alice"));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("DummyDataService exposes getDealService and initialized delegates")
    void testDealServiceAccessors() {
        DealService dealService = service.getDealService();
        assertNotNull(dealService);

        List<MyDealsItem> aliceDeals = service.getMyDealsForBuyer("alice");
        assertNotNull(aliceDeals);
        assertFalse(aliceDeals.isEmpty(), "Seed data should include offers and deals for alice");
    }

    @Test
    @DisplayName("findDeal and findOffer lookup methods")
    void testFindDealAndOffer() {
        List<Deal> deals = service.getAllDeals();
        assertFalse(deals.isEmpty());
        Deal first = deals.get(0);
        Deal found = service.findDeal(first.getId());
        assertNotNull(found);
        assertEquals(first.getId(), found.getId());

        assertNull(service.findDeal(null));
        assertNull(service.findDeal("NON_EXISTENT_DEAL"));

        List<Offer> offers = service.getAllOffers();
        assertFalse(offers.isEmpty());
        Offer firstOffer = offers.get(0);
        Offer foundOffer = service.findOffer(firstOffer.getId());
        assertNotNull(foundOffer);
        assertEquals(firstOffer.getId(), foundOffer.getId());

        assertNull(service.findOffer(null));
        assertNull(service.findOffer("NON_EXISTENT_OFFER"));
    }

    @Test
    @DisplayName("Delegate withdrawOffer works through DummyDataService")
    void testDelegateWithdrawOffer() {
        Offer pending = service.getOffersForBuyer("alice").stream()
                .filter(o -> o.getStatus() == OfferStatus.PENDING)
                .findFirst()
                .orElseThrow();

        DealOperationResult result = service.withdrawOffer(pending.getId(), "alice");
        assertTrue(result.isSuccess());
        assertEquals(OfferStatus.WITHDRAWN, pending.getStatus());
    }

    @Test
    @DisplayName("Delegate acceptCounterOffer works through DummyDataService")
    void testDelegateAcceptCounterOffer() {
        Offer countered = service.getOffersForBuyer("alice").stream()
                .filter(o -> o.getStatus() == OfferStatus.COUNTERED)
                .findFirst()
                .orElseThrow();

        DealOperationResult result = service.acceptCounterOffer(countered.getId(), "alice");
        assertTrue(result.isSuccess());
        assertNotNull(result.getDeal());
        assertEquals(OfferStatus.ACCEPTED, countered.getStatus());
    }

    @Test
    @DisplayName("Delegate declineCounterOffer works through DummyDataService")
    void testDelegateDeclineCounterOffer() {
        Offer countered = service.getOffersForBuyer("alice").stream()
                .filter(o -> o.getStatus() == OfferStatus.COUNTERED)
                .findFirst()
                .orElseThrow();

        DealOperationResult result = service.declineCounterOffer(countered.getId(), "alice");
        assertTrue(result.isSuccess());
        assertEquals(OfferStatus.DECLINED, countered.getStatus());
    }

    @Test
    @DisplayName("Delegate selfReturnItem works through DummyDataService")
    void testDelegateSelfReturnItem() {
        Deal loan = service.getDealsForBuyer("alice").stream()
                .filter(d -> d.getType() == DealType.LOAN && d.getStatus() == DealStatus.ACTIVE)
                .findFirst()
                .orElseThrow();

        DealOperationResult result = service.selfReturnItem(loan.getId(), "alice");
        assertTrue(result.isSuccess());
        assertEquals(DealStatus.RETURNED, loan.getStatus());
    }

    @Test
    @DisplayName("Delegate markDealComplete and submitRating work through DummyDataService")
    void testDelegateMarkCompleteAndSubmitRating() {
        Deal sale = service.getDealsForBuyer("alice").stream()
                .filter(d -> d.getType() == DealType.SALE && d.getStatus() == DealStatus.ACTIVE)
                .findFirst()
                .orElseThrow();

        DealOperationResult completeResult = service.markDealComplete(sale.getId(), "alice");
        assertTrue(completeResult.isSuccess());
        assertTrue(completeResult.isPromptRating());
        assertEquals(DealStatus.COMPLETED, sale.getStatus());

        DealOperationResult ratingResult = service.submitRating(sale.getId(), "alice", 5, "Excellent seller!");
        assertTrue(ratingResult.isSuccess());
        assertTrue(sale.isRated());
    }

    @Test
    @DisplayName("Delegate processAutoReturns triggers auto return logic")
    void testDelegateProcessAutoReturns() {
        int count = service.processAutoReturns();
        assertEquals(0, count, "No deals should expire immediately upon clean setup");
    }
}
