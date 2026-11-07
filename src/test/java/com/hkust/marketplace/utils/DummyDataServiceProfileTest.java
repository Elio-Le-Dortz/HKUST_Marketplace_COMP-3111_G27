package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataServiceProfileTest - Integration Tests for Profile Methods (US-B08)")
class DummyDataServiceProfileTest {

    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        SessionManager.login(dataService.findUser("alice"));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("getBuyerProfileService is not null and accessible")
    void testGetBuyerProfileService() {
        assertNotNull(dataService.getBuyerProfileService());
    }

    @Test
    @DisplayName("getBuyerProfile returns active buyer and null for non-buyer")
    void testGetBuyerProfile() {
        Buyer alice = dataService.getBuyerProfile("alice");
        assertNotNull(alice);
        assertEquals("alice", alice.getUsername());
        assertEquals("Alice Chan", alice.getFullName());

        assertNull(dataService.getBuyerProfile("charlie"));
        assertNull(dataService.getBuyerProfile("admin"));
        assertNull(dataService.getBuyerProfile("unknown"));
    }

    @Test
    @DisplayName("updateBuyerProfile delegates cleanly to service")
    void testUpdateBuyerProfile() {
        ProfileUpdateRequest req = new ProfileUpdateRequest(
                "alice", "Alice In Wonderland", "UG Hall 1");

        ProfileUpdateResult result = dataService.updateBuyerProfile("alice", req);
        assertTrue(result.isSuccess());

        Buyer updated = dataService.getBuyerProfile("alice");
        assertNotNull(updated);
        assertEquals("Alice In Wonderland", updated.getFullName());
        assertEquals("UG Hall 1", updated.getResidence());
    }

    @Test
    @DisplayName("resetData restores seeded buyer profile state")
    void testResetDataRestoresSeedState() {
        ProfileUpdateRequest req = new ProfileUpdateRequest(
                "alice", "Changed Name", "Off-campus");
        dataService.updateBuyerProfile("alice", req);
        assertEquals("Changed Name", dataService.getBuyerProfile("alice").getFullName());

        dataService.resetData();
        assertEquals("Alice Chan", dataService.getBuyerProfile("alice").getFullName());
        assertEquals("Hall 3", dataService.getBuyerProfile("alice").getResidence());
    }
}
