package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminProfile Models & DTOs Unit Tests (US-A04)")
class AdminProfileModelsTest {

    @Test
    @DisplayName("AdminProfileUpdateRequest default and parameterized constructors")
    void testAdminProfileUpdateRequestConstructors() {
        AdminProfileUpdateRequest req1 = new AdminProfileUpdateRequest();
        assertNull(req1.getUsername());
        assertNull(req1.getFullName());
        assertNull(req1.getCurrentPassword());
        assertNull(req1.getNewPassword());
        assertNull(req1.getConfirmPassword());
        assertFalse(req1.isPasswordChangeRequested());

        AdminProfileUpdateRequest req2 = new AdminProfileUpdateRequest("admin", "Admin User");
        assertEquals("admin", req2.getUsername());
        assertEquals("Admin User", req2.getFullName());
        assertNull(req2.getCurrentPassword());
        assertFalse(req2.isPasswordChangeRequested());

        AdminProfileUpdateRequest req3 = new AdminProfileUpdateRequest("admin", "Admin User", "oldPass", "NewPass123", "NewPass123");
        assertEquals("admin", req3.getUsername());
        assertEquals("Admin User", req3.getFullName());
        assertEquals("oldPass", req3.getCurrentPassword());
        assertEquals("NewPass123", req3.getNewPassword());
        assertEquals("NewPass123", req3.getConfirmPassword());
        assertTrue(req3.isPasswordChangeRequested());
    }

    @Test
    @DisplayName("AdminProfileUpdateRequest isPasswordChangeRequested permutations")
    void testIsPasswordChangeRequestedPermutations() {
        AdminProfileUpdateRequest req = new AdminProfileUpdateRequest();
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
    @DisplayName("AdminProfileUpdateRequest getters, setters, equals, hashCode, and toString")
    void testAdminProfileUpdateRequestAccessorsAndEquality() {
        AdminProfileUpdateRequest req1 = new AdminProfileUpdateRequest();
        req1.setUsername("admin1");
        req1.setFullName("Super Admin");
        req1.setCurrentPassword("curr123");
        req1.setNewPassword("NewPass1!");
        req1.setConfirmPassword("NewPass1!");

        AdminProfileUpdateRequest req2 = new AdminProfileUpdateRequest("admin1", "Super Admin", "curr123", "NewPass1!", "NewPass1!");

        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
        assertEquals(req1, req1);
        assertNotEquals(req1, null);
        assertNotEquals(req1, "otherType");

        req2.setFullName("Different Name");
        assertNotEquals(req1, req2);

        assertTrue(req1.toString().contains("admin1"));
        assertTrue(req1.toString().contains("Super Admin"));
    }

    @Test
    @DisplayName("AdminProfileUpdateStatus enum completeness")
    void testAdminProfileUpdateStatusEnumValues() {
        AdminProfileUpdateStatus[] values = AdminProfileUpdateStatus.values();
        assertTrue(values.length >= 10);
        assertNotNull(AdminProfileUpdateStatus.valueOf("SUCCESS"));
        assertNotNull(AdminProfileUpdateStatus.valueOf("EMPTY_FULL_NAME"));
        assertNotNull(AdminProfileUpdateStatus.valueOf("CURRENT_PASSWORD_REQUIRED"));
        assertNotNull(AdminProfileUpdateStatus.valueOf("WRONG_CURRENT_PASSWORD"));
        assertNotNull(AdminProfileUpdateStatus.valueOf("NEW_PASSWORD_REQUIRED"));
        assertNotNull(AdminProfileUpdateStatus.valueOf("CONFIRM_PASSWORD_REQUIRED"));
        assertNotNull(AdminProfileUpdateStatus.valueOf("PASSWORD_CONFIRMATION_MISMATCH"));
        assertNotNull(AdminProfileUpdateStatus.valueOf("WEAK_NEW_PASSWORD"));
        assertNotNull(AdminProfileUpdateStatus.valueOf("USER_NOT_FOUND"));
        assertNotNull(AdminProfileUpdateStatus.valueOf("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("AdminProfileUpdateResult factory methods and properties")
    void testAdminProfileUpdateResultFactories() {
        Admin admin = new Admin("admin", "Admin Name", "hash");

        // Success factory
        AdminProfileUpdateResult successResult = AdminProfileUpdateResult.success(admin, "Updated successfully.");
        assertTrue(successResult.isSuccess());
        assertEquals(AdminProfileUpdateStatus.SUCCESS, successResult.getStatus());
        assertEquals("Updated successfully.", successResult.getMessage());
        assertSame(admin, successResult.getUpdatedAdmin());
        assertTrue(successResult.getAffectedFields().isEmpty());
        assertTrue(successResult.getPasswordViolations().isEmpty());

        // Failure with single field
        AdminProfileUpdateResult failSingle = AdminProfileUpdateResult.failure(
                AdminProfileUpdateStatus.EMPTY_FULL_NAME, "Full name required.", "fullName");
        assertFalse(failSingle.isSuccess());
        assertEquals(AdminProfileUpdateStatus.EMPTY_FULL_NAME, failSingle.getStatus());
        assertEquals("Full name required.", failSingle.getMessage());
        assertNull(failSingle.getUpdatedAdmin());
        assertEquals(Set.of("fullName"), failSingle.getAffectedFields());
        assertTrue(failSingle.getPasswordViolations().isEmpty());

        // Failure with null field
        AdminProfileUpdateResult failNullField = AdminProfileUpdateResult.failure(
                AdminProfileUpdateStatus.USER_NOT_FOUND, "Not found", (String) null);
        assertTrue(failNullField.getAffectedFields().isEmpty());

        // Failure with set
        AdminProfileUpdateResult failSet = AdminProfileUpdateResult.failure(
                AdminProfileUpdateStatus.UNAUTHORIZED, "Unauthorized action.", Set.of("role", "status"));
        assertEquals(Set.of("role", "status"), failSet.getAffectedFields());

        // Failure with weak password
        List<String> violations = List.of("at least 8 characters", "at least one digit");
        AdminProfileUpdateResult weakResult = AdminProfileUpdateResult.weakPassword(violations, "Password weak");
        assertFalse(weakResult.isSuccess());
        assertEquals(AdminProfileUpdateStatus.WEAK_NEW_PASSWORD, weakResult.getStatus());
        assertEquals(violations, weakResult.getPasswordViolations());
        assertEquals(Set.of("newPassword"), weakResult.getAffectedFields());

        // Null safe collections
        AdminProfileUpdateResult nullCollections = new AdminProfileUpdateResult(
                false, AdminProfileUpdateStatus.USER_NOT_FOUND, "msg", null, null, null);
        assertTrue(nullCollections.getAffectedFields().isEmpty());
        assertTrue(nullCollections.getPasswordViolations().isEmpty());
    }

    @Test
    @DisplayName("AdminProfileUpdateResult equals, hashCode, and toString")
    void testAdminProfileUpdateResultEqualityAndToString() {
        Admin admin = new Admin("admin", "Admin Name", "hash");
        AdminProfileUpdateResult r1 = AdminProfileUpdateResult.success(admin, "Done");
        AdminProfileUpdateResult r2 = AdminProfileUpdateResult.success(admin, "Done");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertEquals(r1, r1);
        assertNotEquals(r1, null);
        assertNotEquals(r1, "DifferentType");

        AdminProfileUpdateResult r3 = AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.EMPTY_FULL_NAME, "Done", "fullName");
        assertNotEquals(r1, r3);

        assertTrue(r1.toString().contains("AdminProfileUpdateResult"));
        assertTrue(r1.toString().contains("SUCCESS"));
    }
}
