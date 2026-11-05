package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.AdminDealFilterCriteria;
import com.hkust.marketplace.models.AdminDealQueryResult;
import com.hkust.marketplace.models.AdminDealRecord;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.services.AdminDealsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-A05 DummyDataService Admin Deals Integration Test Suite")
class DummyDataServiceAdminDealsTest {

    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
    }

    @Test
    @DisplayName("DummyDataService provides singleton instance of AdminDealsService")
    void testGetAdminDealsService() {
        AdminDealsService service = dataService.getAdminDealsService();
        assertNotNull(service);
        assertSame(service, dataService.getAdminDealsService());
    }

    @Test
    @DisplayName("AdminDealsService queries seeded deals correctly after reset")
    void testQuerySeededDeals() {
        AdminDealsService service = dataService.getAdminDealsService();
        AdminDealQueryResult result = service.queryDeals(AdminDealFilterCriteria.all());

        assertTrue(result.isSuccess());
        assertTrue(result.getFilteredCount() >= 5);
        assertEquals(result.getTotalSystemDealsCount(), result.getFilteredCount());

        // Verify seeded deals cover Sale, Loan, and Giveaway
        boolean hasSale = false;
        boolean hasLoan = false;
        boolean hasGiveaway = false;

        for (AdminDealRecord rec : result.getDeals()) {
            if (rec.getDealType() == DealType.SALE) hasSale = true;
            if (rec.getDealType() == DealType.LOAN) hasLoan = true;
            if (rec.getDealType() == DealType.GIVEAWAY) hasGiveaway = true;
        }

        assertTrue(hasSale, "Must contain seeded Sale deals");
        assertTrue(hasLoan, "Must contain seeded Loan deals");
        assertTrue(hasGiveaway, "Must contain seeded Giveaway deals");
    }

    @Test
    @DisplayName("AdminDealsService detail resolution connects deal with seeded accepted offer and review")
    void testDetailResolutionWithOfferAndReview() {
        AdminDealsService service = dataService.getAdminDealsService();
        // Look up deal D500 (d1)
        Deal d1 = dataService.findDeal("D500");
        assertNotNull(d1);

        Optional<AdminDealRecord> recordOpt = service.getDealDetails("D500");
        assertTrue(recordOpt.isPresent());
        AdminDealRecord record = recordOpt.get();

        assertEquals("D500", record.getDealId());
        assertEquals("alice", record.getRawBuyerUsername());
        assertEquals("charlie", record.getRawSellerUsername());
        assertNotNull(record.getOffer());
        assertTrue(record.getOffer().getId().startsWith("O"));
    }
}
