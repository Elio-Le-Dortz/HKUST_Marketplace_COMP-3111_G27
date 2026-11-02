package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager State and Access Enforcement Tests")
class SessionManagerTest {

    @BeforeEach
    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("Initial state is unauthenticated")
    void testInitialState() {
        assertFalse(SessionManager.isLoggedIn());
        assertNull(SessionManager.getCurrentUser());
        assertNull(SessionManager.getCurrentBuyer());
        assertFalse(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
        assertFalse(SessionManager.isAdmin());
    }

    @Test
    @DisplayName("Login and logout lifecycle with Buyer")
    void testBuyerLoginAndLogout() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "hash", "Hall 3");
        SessionManager.login(buyer);

        assertTrue(SessionManager.isLoggedIn());
        assertSame(buyer, SessionManager.getCurrentUser());
        assertSame(buyer, SessionManager.getCurrentBuyer());
        assertTrue(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
        assertFalse(SessionManager.isAdmin());

        SessionManager.logout();
        assertFalse(SessionManager.isLoggedIn());
        assertNull(SessionManager.getCurrentUser());
        assertNull(SessionManager.getCurrentBuyer());
    }

    @Test
    @DisplayName("Seller and Admin role identification")
    void testSellerAndAdminRoles() {
        Seller seller = new Seller("charlie", "Charlie Lee", "hash");
        SessionManager.login(seller);
        assertTrue(SessionManager.isLoggedIn());
        assertTrue(SessionManager.isSeller());
        assertFalse(SessionManager.isBuyer());
        assertNull(SessionManager.getCurrentBuyer());
        assertFalse(SessionManager.isAdmin());

        Admin admin = new Admin("admin", "Admin", "hash");
        SessionManager.login(admin);
        assertTrue(SessionManager.isAdmin());
        assertFalse(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
        assertNull(SessionManager.getCurrentBuyer());
    }

    @Test
    @DisplayName("requireBuyer allows active buyer session")
    void testRequireBuyerSuccess() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "hash");
        buyer.setAccountStatus(AccountStatus.ACTIVE);
        SessionManager.login(buyer);

        assertDoesNotThrow(SessionManager::requireBuyer);
    }

    @Test
    @DisplayName("requireBuyer throws SecurityException when unauthenticated")
    void testRequireBuyerUnauthenticated() {
        SecurityException ex = assertThrows(SecurityException.class, SessionManager::requireBuyer);
        assertTrue(ex.getMessage().contains("No user is currently logged in"));
    }

    @Test
    @DisplayName("requireBuyer throws SecurityException when logged in as non-buyer")
    void testRequireBuyerNonBuyer() {
        Seller seller = new Seller("charlie", "Charlie Lee", "hash");
        SessionManager.login(seller);

        SecurityException ex = assertThrows(SecurityException.class, SessionManager::requireBuyer);
        assertTrue(ex.getMessage().contains("Access requires a Buyer account"));
    }

    @Test
    @DisplayName("requireBuyer throws SecurityException when buyer is deactivated")
    void testRequireBuyerDeactivated() {
        Buyer buyer = new Buyer("deactivated_buyer", "Deactivated", "hash");
        buyer.setAccountStatus(AccountStatus.DEACTIVATED);
        SessionManager.login(buyer);

        SecurityException ex = assertThrows(SecurityException.class, SessionManager::requireBuyer);
        assertTrue(ex.getMessage().contains("Buyer account is not active"));
    }
}
