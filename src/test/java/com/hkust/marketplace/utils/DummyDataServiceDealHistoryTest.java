package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.DealHistoryItem;
import com.hkust.marketplace.services.DealHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService DealHistory Integration Test")
class DummyDataServiceDealHistoryTest {

    private DummyDataService service;

    @BeforeEach
    void setUp() {
        service = DummyDataService.getInstance();
    }

    @Test
    @DisplayName("getDealHistoryService returns non-null service instance")
    void testGetDealHistoryService() {
        DealHistoryService historyService = service.getDealHistoryService();
        assertNotNull(historyService);
    }

    @Test
    @DisplayName("getDealHistory delegates correctly to DealHistoryService")
    void testGetDealHistoryDelegation() {
        List<DealHistoryItem> history = service.getDealHistory("alice", null);
        assertNotNull(history);
        assertFalse(history.isEmpty());
    }

    @Test
    @DisplayName("hasCompletedDeals delegates correctly to DealHistoryService")
    void testHasCompletedDealsDelegation() {
        assertTrue(service.hasCompletedDeals("alice"));
        assertFalse(service.hasCompletedDeals("nonexistent_user_" + System.currentTimeMillis()));
    }
}
