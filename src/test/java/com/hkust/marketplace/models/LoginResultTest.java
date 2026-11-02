package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoginResult & LoginErrorType Domain Model Tests")
class LoginResultTest {

    @Test
    @DisplayName("Successful login wraps user and default success values")
    void testSuccess() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "hash", "Hall 3");
        LoginResult result = LoginResult.success(buyer);

        assertTrue(result.isSuccess());
        assertEquals(LoginResult.MSG_SUCCESS, result.getMessage());
        assertEquals(LoginErrorType.NONE, result.getErrorType());
        assertTrue(result.getErrorFields().isEmpty());
        assertSame(buyer, result.getUser());
        assertSame(buyer, result.getUserAs(Buyer.class));
        assertNull(result.getUserAs(Seller.class));
        assertNull(result.getUserAs(null));
        assertFalse(result.hasFieldError("username"));
    }

    @Test
    @DisplayName("Success factory throws on null user")
    void testSuccessNullUserThrows() {
        assertThrows(NullPointerException.class, () -> LoginResult.success(null));
    }

    @Test
    @DisplayName("Failed login with single error field")
    void testFailureSingleField() {
        LoginResult result = LoginResult.failure(
                "Username is required.", "username", LoginErrorType.EMPTY_FIELDS);

        assertFalse(result.isSuccess());
        assertEquals("Username is required.", result.getMessage());
        assertEquals(LoginErrorType.EMPTY_FIELDS, result.getErrorType());
        assertTrue(result.hasFieldError("username"));
        assertFalse(result.hasFieldError("password"));
        assertNull(result.getUser());
        assertNull(result.getUserAs(Buyer.class));
    }

    @Test
    @DisplayName("Failed login with multiple error fields")
    void testFailureMultipleFields() {
        LoginResult result = LoginResult.failure(
                "Both username and password are required.",
                Set.of("username", "password"),
                LoginErrorType.EMPTY_FIELDS);

        assertFalse(result.isSuccess());
        assertEquals("Both username and password are required.", result.getMessage());
        assertEquals(LoginErrorType.EMPTY_FIELDS, result.getErrorType());
        assertTrue(result.hasFieldError("username"));
        assertTrue(result.hasFieldError("password"));
        assertEquals(2, result.getErrorFields().size());
    }

    @Test
    @DisplayName("Constructor handles null fields, null message and null errorType safely")
    void testConstructorNullSafety() {
        LoginResult result = new LoginResult(false, null, null, null, null);
        assertFalse(result.isSuccess());
        assertEquals("", result.getMessage());
        assertTrue(result.getErrorFields().isEmpty());
        assertEquals(LoginErrorType.INVALID_CREDENTIALS, result.getErrorType());
        assertNull(result.getUser());

        LoginResult successFallback = new LoginResult(true, null, null, null, null);
        assertTrue(successFallback.isSuccess());
        assertEquals(LoginErrorType.NONE, successFallback.getErrorType());

        // Single field failure with null field name
        LoginResult singleNullField = LoginResult.failure("err", (String) null, LoginErrorType.INVALID_CREDENTIALS);
        assertTrue(singleNullField.getErrorFields().isEmpty());
    }

    @Test
    @DisplayName("ErrorFields set is unmodifiable")
    void testErrorFieldsUnmodifiable() {
        LoginResult result = LoginResult.failure("err", Set.of("field1"), LoginErrorType.EMPTY_FIELDS);
        assertThrows(UnsupportedOperationException.class, () -> result.getErrorFields().add("field2"));
    }
}
