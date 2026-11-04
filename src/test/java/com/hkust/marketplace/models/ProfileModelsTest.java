package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProfileModelsTest - DTO and Result Model Tests (US-B08)")
class ProfileModelsTest {

    @Test
    @DisplayName("ProfileUpdateRequest constructors, getters, setters, and equals/hashCode")
    void testProfileUpdateRequest() {
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        assertNull(req.getUsername());
        assertNull(req.getFullName());
        assertNull(req.getResidence());
        assertNull(req.getCurrentPassword());
        assertNull(req.getNewPassword());
        assertNull(req.getConfirmPassword());
        assertFalse(req.isPasswordChangeRequested());

        req.setUsername("alice");
        req.setFullName("Alice Chan");
        req.setResidence("Hall 3");
        req.setCurrentPassword("oldPass1");
        req.setNewPassword("newPass123");
        req.setConfirmPassword("newPass123");

        assertEquals("alice", req.getUsername());
        assertEquals("Alice Chan", req.getFullName());
        assertEquals("Hall 3", req.getResidence());
        assertEquals("oldPass1", req.getCurrentPassword());
        assertEquals("newPass123", req.getNewPassword());
        assertEquals("newPass123", req.getConfirmPassword());
        assertTrue(req.isPasswordChangeRequested());

        ProfileUpdateRequest req2 = new ProfileUpdateRequest("alice", "Alice Chan", "Hall 3",
                "oldPass1", "newPass123", "newPass123");
        assertEquals(req, req2);
        assertEquals(req.hashCode(), req2.hashCode());
        assertTrue(req.toString().contains("alice"));

        ProfileUpdateRequest req3 = new ProfileUpdateRequest("bob", "Bob Wong", "Hall 1");
        assertNotEquals(req, req3);
        assertNotEquals(req, null);
        assertNotEquals(req, "some string");
        assertFalse(req3.isPasswordChangeRequested());

        // Test individual field differences in equals
        assertNotEquals(req, new ProfileUpdateRequest("diff", "Alice Chan", "Hall 3", "oldPass1", "newPass123", "newPass123"));
        assertNotEquals(req, new ProfileUpdateRequest("alice", "Diff", "Hall 3", "oldPass1", "newPass123", "newPass123"));
        assertNotEquals(req, new ProfileUpdateRequest("alice", "Alice Chan", "Diff", "oldPass1", "newPass123", "newPass123"));
        assertNotEquals(req, new ProfileUpdateRequest("alice", "Alice Chan", "Hall 3", "diff", "newPass123", "newPass123"));
        assertNotEquals(req, new ProfileUpdateRequest("alice", "Alice Chan", "Hall 3", "oldPass1", "diff", "newPass123"));
        assertNotEquals(req, new ProfileUpdateRequest("alice", "Alice Chan", "Hall 3", "oldPass1", "newPass123", "diff"));
    }

    @Test
    @DisplayName("ProfileUpdateRequest isPasswordChangeRequested logic")
    void testIsPasswordChangeRequested() {
        ProfileUpdateRequest req = new ProfileUpdateRequest("alice", "Alice Chan", "Hall 3");
        assertFalse(req.isPasswordChangeRequested());

        req.setCurrentPassword("curr");
        assertTrue(req.isPasswordChangeRequested());

        req.setCurrentPassword("");
        req.setNewPassword("new");
        assertTrue(req.isPasswordChangeRequested());

        req.setNewPassword("");
        req.setConfirmPassword("conf");
        assertTrue(req.isPasswordChangeRequested());

        req.setConfirmPassword("");
        assertFalse(req.isPasswordChangeRequested());
    }

    @Test
    @DisplayName("ProfileUpdateStatus enum coverage")
    void testProfileUpdateStatusValues() {
        assertEquals(10, ProfileUpdateStatus.values().length);
        assertEquals(ProfileUpdateStatus.SUCCESS, ProfileUpdateStatus.valueOf("SUCCESS"));
        assertEquals(ProfileUpdateStatus.EMPTY_FULL_NAME, ProfileUpdateStatus.valueOf("EMPTY_FULL_NAME"));
        assertEquals(ProfileUpdateStatus.CURRENT_PASSWORD_REQUIRED, ProfileUpdateStatus.valueOf("CURRENT_PASSWORD_REQUIRED"));
        assertEquals(ProfileUpdateStatus.WRONG_CURRENT_PASSWORD, ProfileUpdateStatus.valueOf("WRONG_CURRENT_PASSWORD"));
        assertEquals(ProfileUpdateStatus.NEW_PASSWORD_REQUIRED, ProfileUpdateStatus.valueOf("NEW_PASSWORD_REQUIRED"));
        assertEquals(ProfileUpdateStatus.CONFIRM_PASSWORD_REQUIRED, ProfileUpdateStatus.valueOf("CONFIRM_PASSWORD_REQUIRED"));
        assertEquals(ProfileUpdateStatus.PASSWORD_CONFIRMATION_MISMATCH, ProfileUpdateStatus.valueOf("PASSWORD_CONFIRMATION_MISMATCH"));
        assertEquals(ProfileUpdateStatus.WEAK_NEW_PASSWORD, ProfileUpdateStatus.valueOf("WEAK_NEW_PASSWORD"));
        assertEquals(ProfileUpdateStatus.USER_NOT_FOUND, ProfileUpdateStatus.valueOf("USER_NOT_FOUND"));
        assertEquals(ProfileUpdateStatus.UNAUTHORIZED, ProfileUpdateStatus.valueOf("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("ProfileUpdateResult factory methods and properties")
    void testProfileUpdateResult() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "hash123", "Hall 3");
        ProfileUpdateResult success = ProfileUpdateResult.success(buyer, "Update OK");

        assertTrue(success.isSuccess());
        assertEquals(ProfileUpdateStatus.SUCCESS, success.getStatus());
        assertEquals("Update OK", success.getMessage());
        assertEquals(buyer, success.getUpdatedBuyer());
        assertTrue(success.getAffectedFields().isEmpty());
        assertTrue(success.getPasswordViolations().isEmpty());
        assertTrue(success.toString().contains("Update OK"));

        ProfileUpdateResult failureMulti = ProfileUpdateResult.failure(
                ProfileUpdateStatus.EMPTY_FULL_NAME, "Name required", Set.of("fullName"));
        assertFalse(failureMulti.isSuccess());
        assertEquals(ProfileUpdateStatus.EMPTY_FULL_NAME, failureMulti.getStatus());
        assertEquals("Name required", failureMulti.getMessage());
        assertNull(failureMulti.getUpdatedBuyer());
        assertEquals(Set.of("fullName"), failureMulti.getAffectedFields());

        ProfileUpdateResult failureSingle = ProfileUpdateResult.failure(
                ProfileUpdateStatus.CURRENT_PASSWORD_REQUIRED, "Password required", "currentPassword");
        assertFalse(failureSingle.isSuccess());
        assertEquals(Set.of("currentPassword"), failureSingle.getAffectedFields());

        ProfileUpdateResult failureNullField = ProfileUpdateResult.failure(
                ProfileUpdateStatus.USER_NOT_FOUND, "User missing", (String) null);
        assertTrue(failureNullField.getAffectedFields().isEmpty());

        ProfileUpdateResult customNulls = new ProfileUpdateResult(
                false, ProfileUpdateStatus.USER_NOT_FOUND, "Error", null, null, null);
        assertFalse(customNulls.isSuccess());
        assertTrue(customNulls.getAffectedFields().isEmpty());
        assertTrue(customNulls.getPasswordViolations().isEmpty());
        assertTrue(customNulls.toString().contains("hasBuyer=false"));

        ProfileUpdateResult weak = ProfileUpdateResult.weakPassword(
                List.of("at least 8 characters", "at least one uppercase letter"), "Weak pwd");
        assertFalse(weak.isSuccess());
        assertEquals(ProfileUpdateStatus.WEAK_NEW_PASSWORD, weak.getStatus());
        assertEquals(Set.of("newPassword"), weak.getAffectedFields());
        assertEquals(2, weak.getPasswordViolations().size());
        assertTrue(weak.getPasswordViolations().contains("at least 8 characters"));
    }
}
