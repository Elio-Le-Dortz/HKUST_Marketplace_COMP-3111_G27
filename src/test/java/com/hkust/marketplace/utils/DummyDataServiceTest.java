package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService & Concurrency Tests")
class DummyDataServiceTest {

    @Test
    @DisplayName("Password strength validation edge cases")
    void testPasswordStrength() {
        assertEquals("Password cannot be empty.", DummyDataService.checkPasswordStrength(null));
        assertEquals("Password cannot be empty.", DummyDataService.checkPasswordStrength(""));
        assertEquals("Password must be at least 8 characters.", DummyDataService.checkPasswordStrength("Short1!"));
        assertEquals("Password should contain uppercase, lowercase and a digit.",
                DummyDataService.checkPasswordStrength("alllowercase1"));
        assertEquals("Password should contain uppercase, lowercase and a digit.",
                DummyDataService.checkPasswordStrength("ALLUPPERCASE1"));
        assertEquals("Password should contain uppercase, lowercase and a digit.",
                DummyDataService.checkPasswordStrength("NoDigitsHere"));
        assertEquals("OK", DummyDataService.checkPasswordStrength("StrongPass123"));
    }

    @Test
    @DisplayName("Atomic giveaway claim: first buyer succeeds, subsequent claims fail")
    void testAtomicGiveawayClaim() {
        DummyDataService service = DummyDataService.getInstance();

        // Create a giveaway listing
        Listing giveaway = new Listing();
        giveaway.setTitle("Free Notebook");
        giveaway.setType(ListingType.GIVEAWAY);
        giveaway.setStatus(ListingStatus.LIVE);
        giveaway.setSellerUsername("diana");
        service.addListing(giveaway);

        // First buyer claims
        boolean firstClaim = service.claimListing(giveaway.getId(), "alice");
        assertTrue(firstClaim, "First buyer should successfully claim the giveaway");
        assertEquals(ListingStatus.GIVEN, giveaway.getStatus());

        // Second buyer tries to claim same item
        boolean secondClaim = service.claimListing(giveaway.getId(), "bob");
        assertFalse(secondClaim, "Second buyer cannot claim an already claimed item");
    }

    @Test
    @DisplayName("User lookup and role filtering")
    void testUserLookup() {
        DummyDataService service = DummyDataService.getInstance();
        assertTrue(service.usernameExists("admin"));
        assertTrue(service.usernameExists("alice"));
        assertFalse(service.usernameExists("non_existent_user"));

        User admin = service.findUser("admin");
        assertNotNull(admin);
        assertEquals(UserRole.ADMIN, admin.getRole());
    }
}
