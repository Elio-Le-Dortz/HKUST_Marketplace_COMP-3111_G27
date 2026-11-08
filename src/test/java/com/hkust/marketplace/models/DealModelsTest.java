package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Deal Models & Value Objects Unit Tests (US-B07)")
class DealModelsTest {

    @Test
    @DisplayName("DealOperationStatus enum values and names")
    void testDealOperationStatusEnum() {
        for (DealOperationStatus status : DealOperationStatus.values()) {
            assertNotNull(status.name());
        }
        assertEquals(DealOperationStatus.SUCCESS, DealOperationStatus.valueOf("SUCCESS"));
        assertEquals(DealOperationStatus.OFFER_NOT_PENDING, DealOperationStatus.valueOf("OFFER_NOT_PENDING"));
        assertEquals(DealOperationStatus.COUNTER_NOT_VALID, DealOperationStatus.valueOf("COUNTER_NOT_VALID"));
        assertEquals(DealOperationStatus.DEAL_IS_LOAN, DealOperationStatus.valueOf("DEAL_IS_LOAN"));
    }

    @Test
    @DisplayName("DealOperationResult factory methods and getters")
    void testDealOperationResult() {
        Deal deal = new Deal();
        deal.setId("D100");
        Offer offer = new Offer();
        offer.setId("O100");

        DealOperationResult successDeal = DealOperationResult.success(deal, "Deal OK");
        assertTrue(successDeal.isSuccess());
        assertEquals(DealOperationStatus.SUCCESS, successDeal.getStatus());
        assertEquals("Deal OK", successDeal.getMessage());
        assertSame(deal, successDeal.getDeal());
        assertNull(successDeal.getOffer());
        assertFalse(successDeal.isPromptRating());

        DealOperationResult successOffer = DealOperationResult.success(offer, "Offer OK");
        assertTrue(successOffer.isSuccess());
        assertSame(offer, successOffer.getOffer());
        assertNull(successOffer.getDeal());

        DealOperationResult promptResult = DealOperationResult.completeWithRatingPrompt(deal, "Completed!");
        assertTrue(promptResult.isSuccess());
        assertTrue(promptResult.isPromptRating());
        assertSame(deal, promptResult.getDeal());

        DealOperationResult fail = DealOperationResult.failure(DealOperationStatus.OFFER_NOT_FOUND, "Not found");
        assertFalse(fail.isSuccess());
        assertEquals(DealOperationStatus.OFFER_NOT_FOUND, fail.getStatus());
        assertEquals("Not found", fail.getMessage());
        assertNull(fail.getDeal());
        assertNull(fail.getOffer());
        assertFalse(fail.isPromptRating());
    }

    @Test
    @DisplayName("MyDealsItem fromOffer conversions and action capabilities")
    void testMyDealsItemFromOffer() {
        assertNull(MyDealsItem.fromOffer(null));

        Offer pendingSell = new Offer();
        pendingSell.setId("O1");
        pendingSell.setType(OfferType.PRICE_OFFER);
        pendingSell.setListingId("L1");
        pendingSell.setListingTitle("Calculus Book");
        pendingSell.setBuyerUsername("alice");
        pendingSell.setSellerUsername("charlie");
        pendingSell.setPrice(150.0);
        pendingSell.setStatus(OfferStatus.PENDING);

        MyDealsItem itemPending = MyDealsItem.fromOffer(pendingSell);
        assertNotNull(itemPending);
        assertEquals("O1", itemPending.getId());
        assertEquals("L1", itemPending.getListingId());
        assertEquals("Calculus Book", itemPending.getListingTitle());
        assertEquals("charlie", itemPending.getSellerUsername());
        assertEquals("alice", itemPending.getBuyerUsername());
        assertEquals(ListingType.SELL, itemPending.getListingType());
        assertEquals("Sell", itemPending.getTypeDisplay());
        assertEquals("Pending", itemPending.getStatusDisplay());
        assertEquals("HK$ 150.00", itemPending.getFormattedPrice());
        assertEquals("-", itemPending.getFormattedDueDate());
        assertTrue(itemPending.isOffer());
        assertFalse(itemPending.isDeal());
        assertTrue(itemPending.canWithdraw());
        assertFalse(itemPending.canAcceptCounter());
        assertFalse(itemPending.canDeclineCounter());
        assertFalse(itemPending.canSelfReturn());
        assertFalse(itemPending.canMarkComplete());
        assertFalse(itemPending.canRate());
        assertTrue(itemPending.canOpenListing());
        assertTrue(itemPending.canOpenChat());

        // Countered Lend offer
        Offer counteredLend = new Offer();
        counteredLend.setId("O2");
        counteredLend.setType(OfferType.BORROW_REQUEST);
        counteredLend.setListingId("L2");
        counteredLend.setListingTitle("Calculator");
        counteredLend.setBuyerUsername("alice");
        counteredLend.setSellerUsername("diana");
        counteredLend.setStatus(OfferStatus.COUNTERED);
        LocalDateTime due = LocalDateTime.of(2026, 12, 1, 15, 30);
        counteredLend.setEndDate(due);

        Offer counterOffer = new Offer();
        counterOffer.setEndDate(due.plusDays(3));
        counteredLend.setCounterOffer(counterOffer);

        MyDealsItem itemCounter = MyDealsItem.fromOffer(counteredLend);
        assertNotNull(itemCounter);
        assertEquals("Lend (Borrow)", itemCounter.getTypeDisplay());
        assertEquals("Countered", itemCounter.getStatusDisplay());
        assertTrue(itemCounter.canAcceptCounter());
        assertTrue(itemCounter.canDeclineCounter());
        assertFalse(itemCounter.canWithdraw());
        assertTrue(itemCounter.getCounterDetails().contains("Counter Due: 2026-12-04 15:30"));
        assertEquals(itemCounter.getCounterDetails(), itemCounter.getDetailsSummary());

        // Countered Sell with price
        Offer counteredSell = new Offer();
        counteredSell.setType(OfferType.PRICE_OFFER);
        counteredSell.setStatus(OfferStatus.COUNTERED);
        Offer counterPrice = new Offer();
        counterPrice.setPrice(180.0);
        counteredSell.setCounterOffer(counterPrice);
        MyDealsItem itemCounterPrice = MyDealsItem.fromOffer(counteredSell);
        assertTrue(itemCounterPrice.getCounterDetails().contains("Counter Price: HK$ 180.00"));

        // Giveaway claim offer
        Offer claimOffer = new Offer();
        claimOffer.setType(OfferType.CLAIM);
        claimOffer.setStatus(OfferStatus.ACCEPTED);
        MyDealsItem itemClaim = MyDealsItem.fromOffer(claimOffer);
        assertEquals("Giveaway", itemClaim.getTypeDisplay());
        assertEquals("Accepted", itemClaim.getStatusDisplay());

        // Declined / Withdrawn
        Offer declined = new Offer();
        declined.setStatus(OfferStatus.DECLINED);
        assertEquals("Declined", MyDealsItem.fromOffer(declined).getStatusDisplay());

        Offer withdrawn = new Offer();
        withdrawn.setStatus(OfferStatus.WITHDRAWN);
        assertEquals("Withdrawn", MyDealsItem.fromOffer(withdrawn).getStatusDisplay());
    }

    @Test
    @DisplayName("MyDealsItem fromDeal conversions and action capabilities")
    void testMyDealsItemFromDeal() {
        assertNull(MyDealsItem.fromDeal(null));

        // Active Loan Deal
        Deal loanDeal = new Deal();
        loanDeal.setId("D1");
        loanDeal.setListingId("L4");
        loanDeal.setListingTitle("TI-84");
        loanDeal.setBuyerUsername("alice");
        loanDeal.setSellerUsername("diana");
        loanDeal.setType(DealType.LOAN);
        loanDeal.setStatus(DealStatus.ACTIVE);
        loanDeal.setDueDate(LocalDateTime.of(2026, 11, 20, 18, 0));

        MyDealsItem itemLoan = MyDealsItem.fromDeal(loanDeal);
        assertNotNull(itemLoan);
        assertEquals("Lend (Borrow)", itemLoan.getTypeDisplay());
        assertEquals("Active", itemLoan.getStatusDisplay());
        assertEquals("2026-11-20 18:00", itemLoan.getFormattedDueDate());
        assertTrue(itemLoan.getDetailsSummary().contains("Due: 2026-11-20 18:00"));
        assertTrue(itemLoan.isDeal());
        assertFalse(itemLoan.isOffer());
        assertTrue(itemLoan.canSelfReturn());
        assertFalse(itemLoan.canMarkComplete());
        assertFalse(itemLoan.canRate());
        assertSame(loanDeal, itemLoan.getDeal());

        // Active Sale Deal
        Deal saleDeal = new Deal();
        saleDeal.setId("D2");
        saleDeal.setType(DealType.SALE);
        saleDeal.setStatus(DealStatus.ACTIVE);
        saleDeal.setOfferPrice(250.0);
        MyDealsItem itemSale = MyDealsItem.fromDeal(saleDeal);
        assertEquals("Sell", itemSale.getTypeDisplay());
        assertTrue(itemSale.canMarkComplete());
        assertFalse(itemSale.canSelfReturn());
        assertEquals("HK$ 250.00", itemSale.getDetailsSummary());

        // Completed Deal (not rated)
        Deal completedDeal = new Deal();
        completedDeal.setId("D3");
        completedDeal.setType(DealType.GIVEAWAY);
        completedDeal.setStatus(DealStatus.COMPLETED);
        completedDeal.setRated(false);
        MyDealsItem itemCompleted = MyDealsItem.fromDeal(completedDeal);
        assertEquals("Giveaway", itemCompleted.getTypeDisplay());
        assertEquals("Completed", itemCompleted.getStatusDisplay());
        assertFalse(itemCompleted.canMarkComplete());
        assertTrue(itemCompleted.canRate());

        // Completed Deal (already rated)
        completedDeal.setRated(true);
        assertFalse(MyDealsItem.fromDeal(completedDeal).canRate());

        // Returned Deal (unrated)
        Deal returnedDeal = new Deal();
        returnedDeal.setType(DealType.LOAN);
        returnedDeal.setStatus(DealStatus.RETURNED);
        returnedDeal.setRated(false);
        MyDealsItem itemReturned = MyDealsItem.fromDeal(returnedDeal);
        assertEquals("Returned", itemReturned.getStatusDisplay());
        assertFalse(itemReturned.canSelfReturn());
        assertTrue(itemReturned.canRate(), "Unrated returned loan deal can be rated in My Deals");

        // Returned Deal (already rated)
        returnedDeal.setRated(true);
        assertFalse(MyDealsItem.fromDeal(returnedDeal).canRate(), "Already rated returned loan deal cannot be rated again");
    }

    @Test
    @DisplayName("Deal helper methods: isExpired, isLoan, isCompleted, isReturned, isActive")
    void testDealHelpers() {
        Deal deal = new Deal();
        deal.setType(DealType.LOAN);
        deal.setStatus(DealStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        deal.setDueDate(now.minusSeconds(10));

        assertTrue(deal.isLoan());
        assertTrue(deal.isActive());
        assertFalse(deal.isCompleted());
        assertFalse(deal.isReturned());
        assertTrue(deal.isExpired(now));

        // Due date in future
        deal.setDueDate(now.plusDays(1));
        assertFalse(deal.isExpired(now));

        // Non-loan deal
        deal.setType(DealType.SALE);
        assertFalse(deal.isLoan());
        assertFalse(deal.isExpired(now.plusDays(10)));

        deal.setStatus(DealStatus.COMPLETED);
        assertTrue(deal.isCompleted());
        assertFalse(deal.isActive());

        deal.setStatus(DealStatus.RETURNED);
        assertTrue(deal.isReturned());

        // Status string parsing and backward compatibility
        deal.setStatus("Accepted");
        assertEquals(DealStatus.ACTIVE, deal.getStatus());
        assertEquals("ACTIVE", deal.getStatusString());

        deal.setStatus("Pending");
        assertEquals(DealStatus.ACTIVE, deal.getStatus());

        deal.setStatus("Completed");
        assertEquals(DealStatus.COMPLETED, deal.getStatus());

        deal.setStatus("Returned");
        assertEquals(DealStatus.RETURNED, deal.getStatus());

        deal.setStatus("Unknown_Custom");
        assertEquals(DealStatus.ACTIVE, deal.getStatus());

        deal.setStatus((String) null);
        assertNotNull(deal.getStatus());

        // DealType conversions
        deal.setDealType(ListingType.SELL);
        assertEquals(ListingType.SELL, deal.getDealType());
        assertEquals(DealType.SALE, deal.getType());

        deal.setDealType(ListingType.LEND);
        assertEquals(ListingType.LEND, deal.getDealType());
        assertEquals(DealType.LOAN, deal.getType());

        deal.setDealType(ListingType.GIVEAWAY);
        assertEquals(ListingType.GIVEAWAY, deal.getDealType());
        assertEquals(DealType.GIVEAWAY, deal.getType());

        // Borrow dates and accessors
        java.time.LocalDate today = java.time.LocalDate.now();
        deal.setBorrowStart(today);
        assertEquals(today, deal.getBorrowStart());
        deal.setBorrowEnd(today.plusDays(3));
        assertEquals(today.plusDays(3), deal.getBorrowEnd());

        deal.setBorrowStart(null);
        deal.setBorrowEnd(null);

        deal.setCreatedAt(now);
        assertEquals(now, deal.getCreatedAt());
        deal.setCompletedAt(now);
        assertEquals(now, deal.getCompletedAt());
        deal.setOfferId("OFF-123");
        assertEquals("OFF-123", deal.getOfferId());
    }

    @Test
    @DisplayName("Offer helper methods: isPending, isCountered, isAccepted, isDeclined, isWithdrawn")
    void testOfferHelpers() {
        Offer offer = new Offer();
        offer.setStatus(OfferStatus.PENDING);
        assertTrue(offer.isPending());
        assertFalse(offer.isCountered());
        assertFalse(offer.isAccepted());
        assertFalse(offer.isDeclined());
        assertFalse(offer.isWithdrawn());

        offer.setStatus(OfferStatus.COUNTERED);
        assertTrue(offer.isCountered());

        offer.setStatus(OfferStatus.ACCEPTED);
        assertTrue(offer.isAccepted());

        offer.setStatus(OfferStatus.DECLINED);
        assertTrue(offer.isDeclined());

        offer.setStatus(OfferStatus.WITHDRAWN);
        assertTrue(offer.isWithdrawn());
    }

    @Test
    @DisplayName("F25: Deal items format prices and currency strings independently of default locale")
    void testDealFormattingLocaleIndependence() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE);

            // MyDealsItem price
            Deal deal = new Deal();
            deal.setId("D100");
            deal.setType(DealType.SALE);
            deal.setOfferPrice(12.50);
            deal.setStatus(DealStatus.COMPLETED);
            MyDealsItem item = MyDealsItem.fromDeal(deal);
            assertEquals("HK$ 12.50", item.getFormattedPrice());
            assertFalse(item.getFormattedPrice().contains(","));

            // MyDealsItem counter price
            Offer offer = new Offer();
            offer.setStatus(OfferStatus.COUNTERED);
            Offer counter = new Offer();
            counter.setPrice(99.95);
            offer.setCounterOffer(counter);
            MyDealsItem counterItem = MyDealsItem.fromOffer(offer);
            assertEquals("Counter Price: HK$ 99.95", counterItem.getCounterDetails());
            assertFalse(counterItem.getCounterDetails().contains(","));

            // DealHistoryItem
            DealHistoryItem hist = new DealHistoryItem("D100", "L100", "Book", "charlie", "Charlie Lee",
                    Category.BOOKS, DealType.SALE, DealStatus.COMPLETED, null, null, null, 15.75, false, null);
            assertEquals("HK$ 15.75", hist.getFormattedPrice());
            assertFalse(hist.getFormattedPrice().contains(","));

            // DealHistorySummary
            DealHistorySummary summary = new DealHistorySummary(1, 1, 0, 0, 15.75);
            assertEquals("HK$ 15.75", summary.getFormattedTotalSpent());
            assertFalse(summary.getFormattedTotalSpent().contains(","));

            // AdminDealRecord
            AdminDealRecord adminRec = new AdminDealRecord(deal, null, null, null, null, null);
            deal.setOfferPrice(45.50);
            assertEquals("$45.50", adminRec.getAgreedTermsDisplay());
            assertFalse(adminRec.getAgreedTermsDisplay().contains(","));
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
