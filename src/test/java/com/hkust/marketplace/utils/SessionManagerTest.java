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

    @Test
    @DisplayName("endSessionForUser ends matching user session and leaves others intact")
    void testEndSessionForUser() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "hash");
        SessionManager.login(buyer);
        assertTrue(SessionManager.isLoggedIn());

        // Different user does not end session
        SessionManager.endSessionForUser("bob");
        assertTrue(SessionManager.isLoggedIn());
        assertSame(buyer, SessionManager.getCurrentUser());

        // Null / whitespace does not end session
        SessionManager.endSessionForUser(null);
        SessionManager.endSessionForUser("   ");
        assertTrue(SessionManager.isLoggedIn());

        // Matching username (case-insensitive) ends session
        SessionManager.endSessionForUser("ALICE");
        assertFalse(SessionManager.isLoggedIn());
        assertNull(SessionManager.getCurrentUser());

        // When not logged in, calling endSessionForUser is safe
        assertDoesNotThrow(() -> SessionManager.endSessionForUser("alice"));
    }

    // ===== US-S02: Seller session check (use case 2.2 special requirement) =====

    @Test
    @DisplayName("getCurrentSeller returns the Seller only for a Seller session")
    void testGetCurrentSeller() {
        assertNull(SessionManager.getCurrentSeller());

        Seller seller = new Seller("charlie", "Charlie Lee", "hash");
        SessionManager.login(seller);
        assertSame(seller, SessionManager.getCurrentSeller());

        SessionManager.login(new Buyer("alice", "Alice Chan", "hash"));
        assertNull(SessionManager.getCurrentSeller());

        SessionManager.login(new Admin("admin", "Admin", "hash"));
        assertNull(SessionManager.getCurrentSeller());
    }

    @Test
    @DisplayName("requireSeller returns the active Seller of the session")
    void testRequireSellerSuccess() {
        Seller seller = new Seller("charlie", "Charlie Lee", "hash");
        SessionManager.login(seller);

        assertSame(seller, SessionManager.requireSeller());
    }

    @Test
    @DisplayName("requireSeller throws SecurityException when unauthenticated")
    void testRequireSellerUnauthenticated() {
        SecurityException ex = assertThrows(SecurityException.class, SessionManager::requireSeller);
        assertTrue(ex.getMessage().contains("No user is currently logged in"));
    }

    @Test
    @DisplayName("requireSeller throws SecurityException for a Buyer or Admin session")
    void testRequireSellerNonSeller() {
        SessionManager.login(new Buyer("alice", "Alice Chan", "hash"));
        SecurityException buyerEx = assertThrows(SecurityException.class, SessionManager::requireSeller);
        assertTrue(buyerEx.getMessage().contains("Access requires a Seller account"));

        SessionManager.login(new Admin("admin", "Admin", "hash"));
        SecurityException adminEx = assertThrows(SecurityException.class, SessionManager::requireSeller);
        assertTrue(adminEx.getMessage().contains("Access requires a Seller account"));
    }

    @Test
    @DisplayName("requireSeller rejects a non-Seller object that only claims the SELLER role")
    void testRequireSellerRoleWithoutSellerObject() {
        Buyer impostor = new Buyer("mallory", "Mallory", "hash");
        impostor.setRole(com.hkust.marketplace.models.UserRole.SELLER);
        SessionManager.login(impostor);

        assertTrue(SessionManager.isSeller());
        assertThrows(SecurityException.class, SessionManager::requireSeller);
    }

    @Test
    @DisplayName("requireSeller throws SecurityException when the Seller is deactivated or deleted")
    void testRequireSellerInactive() {
        for (AccountStatus status : new AccountStatus[]{AccountStatus.DEACTIVATED, AccountStatus.DELETED}) {
            Seller seller = new Seller("charlie", "Charlie Lee", "hash");
            seller.setAccountStatus(status);
            SessionManager.login(seller);

            SecurityException ex = assertThrows(SecurityException.class, SessionManager::requireSeller);
            assertTrue(ex.getMessage().contains("Seller account is not active"), status::name);
        }
    }

    // ===== US-A01: Admin session check (use case 3.1 special requirement, F04) =====

    @Test
    @DisplayName("getCurrentAdmin returns the Admin only for an Admin session")
    void testGetCurrentAdmin() {
        assertNull(SessionManager.getCurrentAdmin());

        Admin admin = new Admin("admin", "Admin User", "hash");
        SessionManager.login(admin);
        assertSame(admin, SessionManager.getCurrentAdmin());

        SessionManager.login(new Buyer("alice", "Alice Chan", "hash"));
        assertNull(SessionManager.getCurrentAdmin());

        SessionManager.login(new Seller("charlie", "Charlie Lee", "hash"));
        assertNull(SessionManager.getCurrentAdmin());
    }

    @Test
    @DisplayName("requireAdmin returns the active Admin of the session")
    void testRequireAdminSuccess() {
        Admin admin = new Admin("admin", "Admin User", "hash");
        SessionManager.login(admin);
        assertSame(admin, SessionManager.requireAdmin());
    }

    @Test
    @DisplayName("requireAdmin throws SecurityException when unauthenticated")
    void testRequireAdminUnauthenticated() {
        SecurityException ex = assertThrows(SecurityException.class, SessionManager::requireAdmin);
        assertTrue(ex.getMessage().contains("No user is currently logged in"));
    }

    @Test
    @DisplayName("requireAdmin throws SecurityException when logged in as non-admin")
    void testRequireAdminNonAdmin() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "hash");
        SessionManager.login(buyer);
        SecurityException ex = assertThrows(SecurityException.class, SessionManager::requireAdmin);
        assertTrue(ex.getMessage().contains("Access requires an Admin account"));

        Seller seller = new Seller("charlie", "Charlie Lee", "hash");
        SessionManager.login(seller);
        ex = assertThrows(SecurityException.class, SessionManager::requireAdmin);
        assertTrue(ex.getMessage().contains("Access requires an Admin account"));
    }

    @Test
    @DisplayName("requireAdmin throws SecurityException when the Admin is deactivated or deleted")
    void testRequireAdminInactive() {
        for (AccountStatus status : new AccountStatus[]{AccountStatus.DEACTIVATED, AccountStatus.DELETED}) {
            Admin admin = new Admin("admin", "Admin User", "hash");
            admin.setAccountStatus(status);
            SessionManager.login(admin);

            SecurityException ex = assertThrows(SecurityException.class, SessionManager::requireAdmin);
            assertTrue(ex.getMessage().contains("Admin account is not active"), status::name);
        }
    }
}
