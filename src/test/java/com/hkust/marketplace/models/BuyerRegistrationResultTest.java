package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BuyerRegistrationResult Model Tests")
class BuyerRegistrationResultTest {

    @Test
    @DisplayName("Verify success result creation and getters")
    void testSuccessResult() {
        Buyer buyer = new Buyer("testbuyer", "Test Buyer", "hashedPassword", "Hall 1");
        BuyerRegistrationResult result = BuyerRegistrationResult.success(buyer);

        assertTrue(result.isSuccess());
        assertEquals("Registration successful!", result.getMessage());
        assertTrue(result.getErrorFields().isEmpty());
        assertFalse(result.hasFieldError("username"));
        assertSame(buyer, result.getUser());
        assertEquals(buyer, result.getUserAs(Buyer.class));
        assertNull(result.getUserAs(Seller.class));
        assertNull(result.getUserAs(null));
    }

    @Test
    @DisplayName("Verify failure result with multiple fields")
    void testFailureResultMultipleFields() {
        Set<String> fields = Set.of("username", "password");
        BuyerRegistrationResult result = BuyerRegistrationResult.failure("Fields missing", fields);

        assertFalse(result.isSuccess());
        assertEquals("Fields missing", result.getMessage());
        assertEquals(2, result.getErrorFields().size());
        assertTrue(result.hasFieldError("username"));
        assertTrue(result.hasFieldError("password"));
        assertFalse(result.hasFieldError("fullName"));
        assertNull(result.getUser());
        assertNull(result.getUserAs(Buyer.class));
    }

    @Test
    @DisplayName("Verify failure result with single field")
    void testFailureResultSingleField() {
        BuyerRegistrationResult result = BuyerRegistrationResult.failure("Username taken", "username");

        assertFalse(result.isSuccess());
        assertEquals("Username taken", result.getMessage());
        assertEquals(1, result.getErrorFields().size());
        assertTrue(result.hasFieldError("username"));
        assertFalse(result.hasFieldError("confirmPassword"));
    }

    @Test
    @DisplayName("Handle null error fields gracefully")
    void testNullErrorFields() {
        BuyerRegistrationResult result = new BuyerRegistrationResult(false, "Unknown error", null, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorFields());
        assertTrue(result.getErrorFields().isEmpty());
    }
}
