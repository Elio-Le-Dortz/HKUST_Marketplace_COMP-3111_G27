package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.UserManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Integration Tests for UserManagement (US-A03)")
class DummyDataServiceUserManagementTest {

    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        SessionManager.login(dataService.findUser(DummyDataService.SEEDED_ADMIN_USERNAME));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("UserManagementService is accessible and non-null from DummyDataService")
    void testServiceAccessor() {
        UserManagementService service = dataService.getUserManagementService();
        assertNotNull(service);
        List<User> users = service.getUsers(UserManagementFilterCriteria.all());
        assertFalse(users.isEmpty());
    }

    @Test
    @DisplayName("End-to-end soft-delete of buyer preserves username reservation and blocks re-registration")
    void testSoftDeleteAndUsernameReservation() {
        Buyer buyer = new Buyer("e2e_buyer", "E2E Buyer", "Pass1234!", "Hall 5");
        assertTrue(dataService.registerUserIfAbsent(buyer));
        assertTrue(dataService.usernameExists("e2e_buyer"));

        // Soft delete user via service
        UserManagementResult delResult = dataService.getUserManagementService().deleteUser("e2e_buyer", "admin");
        assertTrue(delResult.isSuccess());
        assertTrue(buyer.isDeleted());

        // Username stays reserved
        assertTrue(dataService.usernameExists("e2e_buyer"));

        // Attempting to register another buyer with same username fails
        BuyerRegistrationResult regResult = dataService.registerBuyer(
                "e2e_buyer", "Another Name", "Pass1234!", "Pass1234!", "Hall 1"
        );
        assertFalse(regResult.isSuccess());
        assertTrue(regResult.hasFieldError("username"));
    }

    @Test
    @DisplayName("Soft-deleted user cannot log in (US-B02 Alternative Flow A4)")
    void testSoftDeletedUserCannotLogin() {
        Buyer buyer = new Buyer("login_buyer", "Login Buyer", PasswordHasher.hash("Pass1234!"), "Hall 5");
        dataService.registerUserIfAbsent(buyer);

        // Before delete: status active
        assertEquals(AccountStatus.ACTIVE, buyer.getAccountStatus());

        // Delete user
        dataService.getUserManagementService().deleteUser("login_buyer", "admin");
        assertEquals(AccountStatus.DELETED, buyer.getAccountStatus());

        // Log out admin before buyer login attempt
        SessionManager.logout();

        // Verify login fails
        LoginResult loginResult = dataService.loginBuyer("login_buyer", "Pass1234!");
        assertFalse(loginResult.isSuccess());
        assertEquals(LoginErrorType.ACCOUNT_DEACTIVATED, loginResult.getErrorType());
        assertEquals(LoginResult.MSG_ACCOUNT_DELETED, loginResult.getMessage());
    }

    @Test
    @DisplayName("Admin renaming seller updates sellerFullName on all seller's listings (F15)")
    void testAdminRenameSellerUpdatesListings() {
        UserEditRequest req = UserEditRequest.builder()
                .targetUsername("charlie")
                .adminUsername("admin")
                .newFullName("Charles Lee")
                .build();
        UserManagementResult r = dataService.getUserManagementService().editUser(req);
        assertTrue(r.isSuccess());
        List<Listing> charlieListings = dataService.getListingsBySeller("charlie");
        assertFalse(charlieListings.isEmpty());
        for (Listing l : charlieListings) {
            assertEquals("Charles Lee", l.getSellerFullName());
        }
    }
}
