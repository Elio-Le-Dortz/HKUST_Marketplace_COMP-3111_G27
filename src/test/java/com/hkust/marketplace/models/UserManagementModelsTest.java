package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserManagement Models & DTOs Tests (US-A03)")
class UserManagementModelsTest {

    @Test
    @DisplayName("UserManagementStatus values are present")
    void testStatusEnum() {
        assertEquals(7, UserManagementStatus.values().length);
        assertNotNull(UserManagementStatus.valueOf("SUCCESS"));
        assertNotNull(UserManagementStatus.valueOf("INVALID_INPUT"));
        assertNotNull(UserManagementStatus.valueOf("UNAUTHORIZED"));
        assertNotNull(UserManagementStatus.valueOf("USER_NOT_FOUND"));
        assertNotNull(UserManagementStatus.valueOf("CANNOT_MODIFY_ADMIN"));
        assertNotNull(UserManagementStatus.valueOf("ACTIVE_DEALS_EXIST"));
        assertNotNull(UserManagementStatus.valueOf("ALREADY_IN_STATE"));
    }

    @Test
    @DisplayName("UserManagementFilterCriteria all() matches non-admin users")
    void testFilterCriteriaAll() {
        UserManagementFilterCriteria criteria = UserManagementFilterCriteria.all();
        assertNull(criteria.getSearchKeyword());
        assertNull(criteria.getRole());
        assertNull(criteria.getStatus());

        Buyer buyer = new Buyer("alice", "Alice Chan", "Pass1234!");
        assertTrue(criteria.matches(buyer));
        assertFalse(criteria.matches(null));
    }

    @Test
    @DisplayName("UserManagementFilterCriteria matches by role, status, and keyword")
    void testFilterCriteriaMatches() {
        UserManagementFilterCriteria criteria = new UserManagementFilterCriteria()
                .setSearchKeyword("ali")
                .setRole(UserRole.BUYER)
                .setStatus(AccountStatus.ACTIVE);

        Buyer buyer1 = new Buyer("alice", "Alice Chan", "Pass1234!");
        buyer1.setAccountStatus(AccountStatus.ACTIVE);
        assertTrue(criteria.matches(buyer1));

        // Keyword matches full name
        criteria.setSearchKeyword("chan");
        assertTrue(criteria.matches(buyer1));

        // Keyword mismatch
        criteria.setSearchKeyword("david");
        assertFalse(criteria.matches(buyer1));

        // Role mismatch
        Seller seller = new Seller("alice_seller", "Alice S", "Pass1234!");
        seller.setAccountStatus(AccountStatus.ACTIVE);
        assertFalse(criteria.matches(seller));

        // Status mismatch
        buyer1.setAccountStatus(AccountStatus.DEACTIVATED);
        assertFalse(criteria.matches(buyer1));
    }

    @Test
    @DisplayName("UserManagementFilterCriteria equals and hashCode")
    void testFilterCriteriaEqualsAndHashCode() {
        UserManagementFilterCriteria c1 = new UserManagementFilterCriteria("test", UserRole.BUYER, AccountStatus.ACTIVE);
        UserManagementFilterCriteria c2 = new UserManagementFilterCriteria("test", UserRole.BUYER, AccountStatus.ACTIVE);
        UserManagementFilterCriteria c3 = new UserManagementFilterCriteria("other", UserRole.SELLER, AccountStatus.DEACTIVATED);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1, c3);
        assertNotEquals(c1, null);
        assertNotEquals(c1, "someString");
        assertEquals(c1, c1);
    }

    @Test
    @DisplayName("UserEditRequest builder and getters")
    void testUserEditRequest() {
        UserEditRequest req = UserEditRequest.builder()
                .targetUsername("alice")
                .adminUsername("admin")
                .newFullName("Alice Updated")
                .residence("Hall 4")
                .bio("Buyer bio")
                .preferredMeetup("Library")
                .build();

        assertEquals("alice", req.getTargetUsername());
        assertEquals("admin", req.getAdminUsername());
        assertEquals("Alice Updated", req.getNewFullName());
        assertEquals("Hall 4", req.getResidence());
        assertEquals("Buyer bio", req.getBio());
        assertEquals(1, req.getPreferredMeetups().size());
        assertEquals(MeetupLocation.LIBRARY, req.getPreferredMeetups().get(0));

        // Test preferredMeetups list setter
        UserEditRequest req2 = UserEditRequest.builder()
                .preferredMeetups(List.of(MeetupLocation.ATRIUM))
                .build();
        assertEquals(1, req2.getPreferredMeetups().size());
        assertEquals(MeetupLocation.ATRIUM, req2.getPreferredMeetups().get(0));
    }

    @Test
    @DisplayName("UserManagementResult factories and getters")
    void testUserManagementResult() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "Pass1234!");
        UserManagementResult successRes = UserManagementResult.success("User updated", buyer);
        assertTrue(successRes.isSuccess());
        assertEquals(UserManagementStatus.SUCCESS, successRes.getStatus());
        assertEquals("User updated", successRes.getMessage());
        assertEquals(buyer, successRes.getUser());
        assertEquals(0, successRes.getActiveDeals().size());
        assertEquals(0, successRes.getAffectedListingsCount());
        assertEquals(0, successRes.getAffectedOffersCount());
        assertTrue(successRes.toString().contains("alice"));

        UserManagementResult successCounts = UserManagementResult.success("Deleted", buyer, 2, 3);
        assertTrue(successCounts.isSuccess());
        assertEquals(2, successCounts.getAffectedListingsCount());
        assertEquals(3, successCounts.getAffectedOffersCount());

        UserManagementResult failureRes = UserManagementResult.failure(UserManagementStatus.INVALID_INPUT, "Name empty");
        assertFalse(failureRes.isSuccess());
        assertEquals(UserManagementStatus.INVALID_INPUT, failureRes.getStatus());
        assertEquals("Name empty", failureRes.getMessage());

        Deal activeDeal = new Deal();
        activeDeal.setId("D1");
        UserManagementResult dealsRes = UserManagementResult.activeDealsExist("Active deals exist", List.of(activeDeal));
        assertFalse(dealsRes.isSuccess());
        assertEquals(UserManagementStatus.ACTIVE_DEALS_EXIST, dealsRes.getStatus());
        assertEquals(1, dealsRes.getActiveDeals().size());
        assertEquals("D1", dealsRes.getActiveDeals().get(0).getId());
    }

    @Test
    @DisplayName("User display name returns 'Deleted user' when soft-deleted")
    void testUserDisplayNameWhenDeleted() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "Pass1234!");
        assertFalse(buyer.isDeleted());
        assertEquals("Alice Chan", buyer.getDisplayName());

        buyer.setAccountStatus(AccountStatus.DELETED);
        assertTrue(buyer.isDeleted());
        assertEquals("Deleted user", buyer.getDisplayName());

        // When name is null / empty, fallback to username
        buyer.setAccountStatus(AccountStatus.ACTIVE);
        buyer.setFullName("");
        assertEquals("alice", buyer.getDisplayName());
    }
}
