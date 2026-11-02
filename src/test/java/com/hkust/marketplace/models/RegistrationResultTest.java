package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RegistrationResult Model Tests")
class RegistrationResultTest {

    @Test
    @DisplayName("Verify success result creation and getters")
    void testSuccessResult() {
        Buyer buyer = new Buyer("testbuyer", "Test Buyer", "hashedPassword", "Hall 1");
        RegistrationResult result = RegistrationResult.success(buyer);

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
        RegistrationResult result = RegistrationResult.failure("Fields missing", fields);

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
        RegistrationResult result = RegistrationResult.failure("Username taken", "username");

        assertFalse(result.isSuccess());
        assertEquals("Username taken", result.getMessage());
        assertEquals(1, result.getErrorFields().size());
        assertTrue(result.hasFieldError("username"));
        assertFalse(result.hasFieldError("confirmPassword"));
    }

    @Test
    @DisplayName("Handle null error fields gracefully")
    void testNullErrorFields() {
        RegistrationResult result = new RegistrationResult(false, "Unknown error", null, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorFields());
        assertTrue(result.getErrorFields().isEmpty());
    }
}
