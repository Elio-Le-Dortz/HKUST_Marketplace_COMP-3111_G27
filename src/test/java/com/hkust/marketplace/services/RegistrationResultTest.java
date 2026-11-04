package com.hkust.marketplace.services;

import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RegistrationResult & SellerRegistrationRequest value types")
class RegistrationResultTest {

    @Test
    @DisplayName("Success holds the user and no errors")
    void success() {
        Seller seller = new Seller("newseller", "New Seller", "hash");
        RegistrationResult<Seller> result = RegistrationResult.success(seller);

        assertTrue(result.isSuccess());
        assertSame(seller, result.getUser());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getAllMessages().isEmpty());
        for (RegistrationField field : RegistrationField.values()) {
            assertFalse(result.hasError(field));
            assertTrue(result.getErrorsFor(field).isEmpty());
        }
    }

    @Test
    @DisplayName("Success without a user is rejected")
    void successRequiresUser() {
        assertThrows(NullPointerException.class, () -> RegistrationResult.success(null));
    }

    @Test
    @DisplayName("Failure exposes errors per field, in form order")
    void failure() {
        Map<RegistrationField, List<String>> errors = new LinkedHashMap<>();
        errors.put(RegistrationField.PASSWORD, List.of("weak"));
        errors.put(RegistrationField.USERNAME, List.of("taken"));

        RegistrationResult<Seller> result = RegistrationResult.failure(errors);

        assertFalse(result.isSuccess());
        assertEquals(List.of(RegistrationField.USERNAME, RegistrationField.PASSWORD),
                new ArrayList<>(result.getErrors().keySet()));
        assertEquals(List.of("taken", "weak"), result.getAllMessages());
        assertEquals(List.of("weak"), result.getErrorsFor(RegistrationField.PASSWORD));
        assertTrue(result.hasError(RegistrationField.USERNAME));
        assertFalse(result.hasError(RegistrationField.FULL_NAME));
        assertTrue(result.getErrorsFor(RegistrationField.FULL_NAME).isEmpty());
    }

    @Test
    @DisplayName("Reading the user of a failed result throws and names the errors")
    void failureHasNoUser() {
        RegistrationResult<Seller> result =
                RegistrationResult.failure(Map.of(RegistrationField.USERNAME, List.of("Username is required.")));
        IllegalStateException e = assertThrows(IllegalStateException.class, result::getUser);
        assertTrue(e.getMessage().contains("Username is required."));
    }

    @Test
    @DisplayName("Failure needs at least one error")
    void failureRequiresErrors() {
        assertThrows(IllegalArgumentException.class, () -> RegistrationResult.failure(Map.of()));
        assertThrows(NullPointerException.class, () -> RegistrationResult.failure(null));
    }

    @Test
    @DisplayName("Errors are copied and cannot be modified afterwards")
    void failureIsImmutable() {
        List<String> messages = new ArrayList<>(List.of("taken"));
        Map<RegistrationField, List<String>> errors = new LinkedHashMap<>();
        errors.put(RegistrationField.USERNAME, messages);

        RegistrationResult<Seller> result = RegistrationResult.failure(errors);
        messages.add("changed later");
        errors.put(RegistrationField.PASSWORD, List.of("added later"));

        assertEquals(List.of("taken"), result.getErrorsFor(RegistrationField.USERNAME));
        assertFalse(result.hasError(RegistrationField.PASSWORD));
        assertThrows(UnsupportedOperationException.class, () -> result.getErrors().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getErrorsFor(RegistrationField.USERNAME).add("x"));
        assertThrows(UnsupportedOperationException.class, () -> result.getAllMessages().add("x"));
    }

    @Test
    @DisplayName("Request toString never reveals the passwords")
    void requestToStringMasksPasswords() {
        SellerRegistrationRequest request = new SellerRegistrationRequest("newseller", "New Seller",
                "Secret123", "Secret123", List.of(MeetupLocation.ATRIUM), "Bio");
        String text = request.toString();

        assertFalse(text.contains("Secret123"));
        assertTrue(text.contains("newseller"));
        assertTrue(text.contains("Atrium"));
        assertEquals("Secret123", request.password());
    }
}
