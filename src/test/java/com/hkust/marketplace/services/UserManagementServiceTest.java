package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserManagementService Tests (Sprint 2 US-A03, UC 3.3)")
class UserManagementServiceTest {

    private DummyDataService dataService;
    private UserManagementService service;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        SessionManager.login(dataService.findUser("admin"));
        service = new UserManagementService(dataService);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Nested
    @DisplayName("Preconditions, Constructors, and Authorization Tests")
    class PreconditionsAndAuthorizationTests {

        @Test
        @DisplayName("Constructors: default and parameterized")
        void testConstructors() {
            assertNotNull(new UserManagementService());
            assertThrows(NullPointerException.class, () -> new UserManagementService(null));
        }

        @Test
        @DisplayName("EditUser: input validation and admin authorization")
        void testEditUserAuthAndInputValidation() {
            // Null request
            UserManagementResult r1 = service.editUser(null);
            assertFalse(r1.isSuccess());
            assertEquals(UserManagementStatus.INVALID_INPUT, r1.getStatus());

            // Null target username
            UserManagementResult r2 = service.editUser(UserEditRequest.builder().adminUsername("admin").newFullName("Name").build());
            assertFalse(r2.isSuccess());
            assertEquals(UserManagementStatus.INVALID_INPUT, r2.getStatus());

            // Empty admin username
            UserManagementResult r3 = service.editUser(UserEditRequest.builder().targetUsername("alice").adminUsername("").newFullName("Name").build());
            assertFalse(r3.isSuccess());
            assertEquals(UserManagementStatus.UNAUTHORIZED, r3.getStatus());

            // Non-existent admin
            UserManagementResult r4 = service.editUser(UserEditRequest.builder().targetUsername("alice").adminUsername("nonexistent").newFullName("Name").build());
            assertFalse(r4.isSuccess());
            assertEquals(UserManagementStatus.UNAUTHORIZED, r4.getStatus());

            // Requester is not admin (e.g. buyer bob)
            UserManagementResult r5 = service.editUser(UserEditRequest.builder().targetUsername("alice").adminUsername("bob").newFullName("Name").build());
            assertFalse(r5.isSuccess());
            assertEquals(UserManagementStatus.UNAUTHORIZED, r5.getStatus());

            // Requester admin is deactivated
            User admin = dataService.findUser("admin");
            admin.setAccountStatus(AccountStatus.DEACTIVATED);
            assertThrows(SecurityException.class, () -> service.editUser(UserEditRequest.builder().targetUsername("alice").adminUsername("admin").newFullName("Name").build()));
            admin.setAccountStatus(AccountStatus.ACTIVE);

            // Target user does not exist
            UserManagementResult r7 = service.editUser(UserEditRequest.builder().targetUsername("nobody").adminUsername("admin").newFullName("Name").build());
            assertFalse(r7.isSuccess());
            assertEquals(UserManagementStatus.USER_NOT_FOUND, r7.getStatus());

            // Target user is admin
            UserManagementResult r8 = service.editUser(UserEditRequest.builder().targetUsername("admin").adminUsername("admin").newFullName("Name").build());
            assertFalse(r8.isSuccess());
            assertEquals(UserManagementStatus.CANNOT_MODIFY_ADMIN, r8.getStatus());
        }

        @Test
        @DisplayName("ChangeAccountStatus: input validation and admin authorization")
        void testChangeStatusAuthAndInputValidation() {
            // Null target username
            UserManagementResult r1 = service.changeAccountStatus(null, AccountStatus.DEACTIVATED, "admin");
            assertFalse(r1.isSuccess());
            assertEquals(UserManagementStatus.INVALID_INPUT, r1.getStatus());

            // Empty admin username
            UserManagementResult r2 = service.changeAccountStatus("alice", AccountStatus.DEACTIVATED, " ");
            assertFalse(r2.isSuccess());
            assertEquals(UserManagementStatus.UNAUTHORIZED, r2.getStatus());

            // Invalid status (null or DELETED)
            UserManagementResult r3 = service.changeAccountStatus("alice", null, "admin");
            assertFalse(r3.isSuccess());
            assertEquals(UserManagementStatus.INVALID_INPUT, r3.getStatus());

            UserManagementResult r4 = service.changeAccountStatus("alice", AccountStatus.DELETED, "admin");
            assertFalse(r4.isSuccess());
            assertEquals(UserManagementStatus.INVALID_INPUT, r4.getStatus());

            // Non-admin requester
            UserManagementResult r5 = service.changeAccountStatus("alice", AccountStatus.DEACTIVATED, "bob");
            assertFalse(r5.isSuccess());
            assertEquals(UserManagementStatus.UNAUTHORIZED, r5.getStatus());

            // Target user not found
            UserManagementResult r6 = service.changeAccountStatus("ghost", AccountStatus.DEACTIVATED, "admin");
            assertFalse(r6.isSuccess());
            assertEquals(UserManagementStatus.USER_NOT_FOUND, r6.getStatus());

            // Cannot modify admin
            UserManagementResult r7 = service.changeAccountStatus("admin", AccountStatus.DEACTIVATED, "admin");
            assertFalse(r7.isSuccess());
            assertEquals(UserManagementStatus.CANNOT_MODIFY_ADMIN, r7.getStatus());
        }

        @Test
        @DisplayName("DeleteUser: input validation and admin authorization")
        void testDeleteUserAuthAndInputValidation() {
            // Empty target
            UserManagementResult r1 = service.deleteUser("", "admin");
            assertFalse(r1.isSuccess());
            assertEquals(UserManagementStatus.INVALID_INPUT, r1.getStatus());

            // Empty admin
            UserManagementResult r2 = service.deleteUser("alice", "  ");
            assertFalse(r2.isSuccess());
            assertEquals(UserManagementStatus.UNAUTHORIZED, r2.getStatus());

            // Non-admin requester
            UserManagementResult r3 = service.deleteUser("alice", "bob");
            assertFalse(r3.isSuccess());
            assertEquals(UserManagementStatus.UNAUTHORIZED, r3.getStatus());

            // Target user not found
            UserManagementResult r4 = service.deleteUser("phantom", "admin");
            assertFalse(r4.isSuccess());
            assertEquals(UserManagementStatus.USER_NOT_FOUND, r4.getStatus());

            // Cannot delete admin
            UserManagementResult r5 = service.deleteUser("admin", "admin");
            assertFalse(r5.isSuccess());
            assertEquals(UserManagementStatus.CANNOT_MODIFY_ADMIN, r5.getStatus());
        }
    }

    @Nested
    @DisplayName("View and Filter Users (Basic Flow Step 2, Alternative Flow A1)")
    class ViewAndFilterTests {

        @Test
        @DisplayName("getUsers excludes admin and sorts alphabetically by username")
        void testGetUsersExcludesAdminAndSorts() {
            List<User> users = service.getUsers(null);
            assertFalse(users.isEmpty());
            for (User u : users) {
                assertNotEquals(UserRole.ADMIN, u.getRole());
                assertFalse("admin".equalsIgnoreCase(u.getUsername()));
            }

            // Verify alphabetical sort
            for (int i = 0; i < users.size() - 1; i++) {
                String u1 = users.get(i).getUsername().toLowerCase();
                String u2 = users.get(i + 1).getUsername().toLowerCase();
                assertTrue(u1.compareTo(u2) <= 0);
            }
        }

        @Test
        @DisplayName("Filter by role (Buyer only, Seller only)")
        void testFilterByRole() {
            UserManagementFilterCriteria buyerCriteria = new UserManagementFilterCriteria().setRole(UserRole.BUYER);
            List<User> buyers = service.getUsers(buyerCriteria);
            assertFalse(buyers.isEmpty());
            for (User u : buyers) {
                assertEquals(UserRole.BUYER, u.getRole());
            }

            UserManagementFilterCriteria sellerCriteria = new UserManagementFilterCriteria().setRole(UserRole.SELLER);
            List<User> sellers = service.getUsers(sellerCriteria);
            assertFalse(sellers.isEmpty());
            for (User u : sellers) {
                assertEquals(UserRole.SELLER, u.getRole());
            }
        }

        @Test
        @DisplayName("Filter by status (Active, Deactivated, Deleted)")
        void testFilterByStatus() {
            User alice = dataService.findUser("alice");
            alice.setAccountStatus(AccountStatus.DEACTIVATED);

            User bob = dataService.findUser("bob");
            bob.setAccountStatus(AccountStatus.DELETED);

            List<User> activeUsers = service.getUsers(new UserManagementFilterCriteria().setStatus(AccountStatus.ACTIVE));
            assertFalse(activeUsers.contains(alice));
            assertFalse(activeUsers.contains(bob));

            List<User> deactUsers = service.getUsers(new UserManagementFilterCriteria().setStatus(AccountStatus.DEACTIVATED));
            assertTrue(deactUsers.contains(alice));
            assertFalse(deactUsers.contains(bob));

            List<User> delUsers = service.getUsers(new UserManagementFilterCriteria().setStatus(AccountStatus.DELETED));
            assertFalse(delUsers.contains(alice));
            assertTrue(delUsers.contains(bob));
        }

        @Test
        @DisplayName("Filter by keyword matches username or full name case-insensitively")
        void testFilterByKeyword() {
            List<User> matchingAlice = service.getUsers(new UserManagementFilterCriteria().setSearchKeyword("ALICE"));
            assertEquals(1, matchingAlice.size());
            assertEquals("alice", matchingAlice.get(0).getUsername());

            // Match full name
            List<User> matchingChan = service.getUsers(new UserManagementFilterCriteria().setSearchKeyword("chan"));
            assertFalse(matchingChan.isEmpty());
            assertTrue(matchingChan.stream().anyMatch(u -> "alice".equalsIgnoreCase(u.getUsername())));
        }

        @Test
        @DisplayName("Alternative Flow A1: No users match criteria")
        void testNoUsersMatch() {
            List<User> empty = service.getUsers(new UserManagementFilterCriteria().setSearchKeyword("xyz_non_existent_12345"));
            assertTrue(empty.isEmpty());
        }

        @Test
        @DisplayName("getUser: returns Optional user or empty for admin/invalid")
        void testGetUser() {
            Optional<User> alice = service.getUser("alice");
            assertTrue(alice.isPresent());
            assertEquals("alice", alice.get().getUsername());

            assertTrue(service.getUser(null).isEmpty());
            assertTrue(service.getUser("").isEmpty());
            assertTrue(service.getUser("admin").isEmpty());
            assertTrue(service.getUser("nonexistent").isEmpty());
        }
    }

    @Nested
    @DisplayName("Subflow S1: Edit User & Alternative Flow A2: Empty Full Name")
    class EditUserTests {

        @Test
        @DisplayName("Edit Buyer: updates full name and residence, notifies user and logs audit")
        void testEditBuyerSuccess() {
            int notifCountBefore = dataService.getNotificationsFor("alice").size();
            int adminNotifCountBefore = dataService.getNotificationsFor("admin").size();

            UserEditRequest req = UserEditRequest.builder()
                    .targetUsername("alice")
                    .adminUsername("admin")
                    .newFullName("Alice Updated")
                    .residence("Hall 8")
                    .build();

            UserManagementResult result = service.editUser(req);
            assertTrue(result.isSuccess());
            assertEquals("Alice Updated", result.getUser().getFullName());
            assertEquals("Hall 8", result.getUser().getResidenceHall());

            // Subflow S1 Step 5: Notify user (newest notification is at index 0)
            List<Notification> userNotifs = dataService.getNotificationsFor("alice");
            assertEquals(notifCountBefore + 1, userNotifs.size());
            assertTrue(userNotifs.get(0).getMessage().contains("administrator has updated your profile"));

            // Special Requirement: Admin audit log (newest notification is at index 0)
            List<Notification> adminNotifs = dataService.getNotificationsFor("admin");
            assertEquals(adminNotifCountBefore + 1, adminNotifs.size());
            Notification audit = adminNotifs.get(0);
            assertEquals(UserManagementService.AUDIT_LOG_CATEGORY, audit.getCategory());
            assertTrue(audit.getMessage().contains("alice"));
        }

        @Test
        @DisplayName("Edit Seller: updates full name, bio, and meetup locations")
        void testEditSellerSuccess() {
            UserEditRequest req = UserEditRequest.builder()
                    .targetUsername("charlie")
                    .adminUsername("admin")
                    .newFullName("Charlie New")
                    .bio("Selling lab gear and textbooks")
                    .preferredMeetup("Library")
                    .build();

            UserManagementResult result = service.editUser(req);
            assertTrue(result.isSuccess());
            assertEquals("Charlie New", result.getUser().getFullName());
            assertEquals("Selling lab gear and textbooks", result.getUser().getBio());
            assertEquals("Library", result.getUser().getPreferredMeetup());

            // F15: seller's listings must also have updated sellerFullName
            List<Listing> charlieListings = dataService.getListingsBySeller("charlie");
            assertFalse(charlieListings.isEmpty());
            for (Listing l : charlieListings) {
                assertEquals("Charlie New", l.getSellerFullName());
            }
        }

        @Test
        @DisplayName("Regression F15: Admin rename of a seller updates sellerFullName across all their listings")
        void testF15AdminRenameSellerUpdatesListings() {
            List<Listing> listings = dataService.getListingsBySeller("charlie");
            assertFalse(listings.isEmpty(), "Charlie should have seeded listings");

            UserEditRequest req = UserEditRequest.builder()
                    .targetUsername("charlie")
                    .adminUsername("admin")
                    .newFullName("Charles Lee")
                    .build();

            UserManagementResult result = service.editUser(req);
            assertTrue(result.isSuccess());

            List<Listing> updatedListings = dataService.getListingsBySeller("charlie");
            assertFalse(updatedListings.isEmpty());
            for (Listing l : updatedListings) {
                assertEquals("Charles Lee", l.getSellerFullName());
            }
        }

        @Test
        @DisplayName("Alternative Flow A2: Empty or whitespace full name rejected")
        void testEmptyFullNameRejected() {
            UserEditRequest reqEmpty = UserEditRequest.builder()
                    .targetUsername("alice")
                    .adminUsername("admin")
                    .newFullName("")
                    .build();

            UserManagementResult r1 = service.editUser(reqEmpty);
            assertFalse(r1.isSuccess());
            assertEquals(UserManagementStatus.INVALID_INPUT, r1.getStatus());
            assertTrue(r1.getMessage().contains("Full name is required"));

            UserEditRequest reqWhitespace = UserEditRequest.builder()
                    .targetUsername("alice")
                    .adminUsername("admin")
                    .newFullName("   \t  ")
                    .build();

            UserManagementResult r2 = service.editUser(reqWhitespace);
            assertFalse(r2.isSuccess());
            assertEquals(UserManagementStatus.INVALID_INPUT, r2.getStatus());
        }

        @Test
        @DisplayName("Edit soft-deleted user is blocked")
        void testEditDeletedUserBlocked() {
            User alice = dataService.findUser("alice");
            alice.setAccountStatus(AccountStatus.DELETED);

            UserEditRequest req = UserEditRequest.builder()
                    .targetUsername("alice")
                    .adminUsername("admin")
                    .newFullName("Alice New")
                    .build();

            UserManagementResult result = service.editUser(req);
            assertFalse(result.isSuccess());
            assertEquals(UserManagementStatus.ALREADY_IN_STATE, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Subflow S2: Change Account Status (Deactivate / Reactivate)")
    class ChangeAccountStatusTests {

        @Test
        @DisplayName("Deactivate active user: sets status, terminates open session, notifies user and audit log")
        void testDeactivateUserSuccess() {
            User alice = dataService.findUser("alice");
            SessionManager.login(alice);
            assertTrue(SessionManager.isLoggedIn());

            User admin = dataService.findUser("admin");
            UserManagementResult result = SessionManager.withSession(admin, () -> service.changeAccountStatus("alice", AccountStatus.DEACTIVATED, "admin"));
            assertTrue(result.isSuccess());
            assertEquals(AccountStatus.DEACTIVATED, alice.getAccountStatus());
            assertFalse(alice.isActive());

            // Session ended
            assertFalse(SessionManager.isLoggedIn());

            // User notification
            List<Notification> userNotifs = dataService.getNotificationsFor("alice");
            assertTrue(userNotifs.stream().anyMatch(n -> n.getTitle().contains("Account Deactivated")));

            // Audit log
            List<Notification> adminNotifs = dataService.getNotificationsFor("admin");
            assertTrue(adminNotifs.stream().anyMatch(n -> n.getCategory().equals(UserManagementService.AUDIT_LOG_CATEGORY)
                    && n.getMessage().contains("deactivated account @alice")));
        }

        @Test
        @DisplayName("Reactivate deactivated user: sets status to ACTIVE, notifies user and audit log")
        void testReactivateUserSuccess() {
            User alice = dataService.findUser("alice");
            alice.setAccountStatus(AccountStatus.DEACTIVATED);

            UserManagementResult result = service.changeAccountStatus("alice", AccountStatus.ACTIVE, "admin");
            assertTrue(result.isSuccess());
            assertEquals(AccountStatus.ACTIVE, alice.getAccountStatus());
            assertTrue(alice.isActive());

            // User notification
            List<Notification> userNotifs = dataService.getNotificationsFor("alice");
            assertTrue(userNotifs.stream().anyMatch(n -> n.getTitle().contains("Account Reactivated")));

            // Audit log
            List<Notification> adminNotifs = dataService.getNotificationsFor("admin");
            assertTrue(adminNotifs.stream().anyMatch(n -> n.getCategory().equals(UserManagementService.AUDIT_LOG_CATEGORY)
                    && n.getMessage().contains("reactivated account @alice")));
        }

        @Test
        @DisplayName("Changing status to same status returns ALREADY_IN_STATE")
        void testAlreadyInState() {
            UserManagementResult r1 = service.changeAccountStatus("alice", AccountStatus.ACTIVE, "admin");
            assertFalse(r1.isSuccess());
            assertEquals(UserManagementStatus.ALREADY_IN_STATE, r1.getStatus());

            dataService.findUser("alice").setAccountStatus(AccountStatus.DEACTIVATED);
            UserManagementResult r2 = service.changeAccountStatus("alice", AccountStatus.DEACTIVATED, "admin");
            assertFalse(r2.isSuccess());
            assertEquals(UserManagementStatus.ALREADY_IN_STATE, r2.getStatus());
        }

        @Test
        @DisplayName("Changing status of soft-deleted account is blocked")
        void testChangeStatusOfDeletedUserBlocked() {
            User alice = dataService.findUser("alice");
            alice.setAccountStatus(AccountStatus.DELETED);

            UserManagementResult res = service.changeAccountStatus("alice", AccountStatus.ACTIVE, "admin");
            assertFalse(res.isSuccess());
            assertEquals(UserManagementStatus.ALREADY_IN_STATE, res.getStatus());
        }
    }

    @Nested
    @DisplayName("Subflow S3: Delete User & Alternative Flow A3: Active Deals Exist")
    class DeleteUserTests {

        @Test
        @DisplayName("Alternative Flow A3: Deletion blocked when user has active deals")
        void testDeletionBlockedByActiveDeals() {
            // Seed data has active deals for alice and charlie
            List<Deal> activeDeals = service.getActiveDealsForUser("charlie");
            assertFalse(activeDeals.isEmpty(), "Charlie should have active seed deals");

            UserManagementResult result = service.deleteUser("charlie", "admin");
            assertFalse(result.isSuccess());
            assertEquals(UserManagementStatus.ACTIVE_DEALS_EXIST, result.getStatus());
            assertFalse(result.getActiveDeals().isEmpty());
            assertTrue(result.getMessage().contains("active deal"));

            // Charlie's status is still untouched
            User charlie = dataService.findUser("charlie");
            assertNotEquals(AccountStatus.DELETED, charlie.getAccountStatus());
        }

        @Test
        @DisplayName("Delete Seller without active deals: removes listings, declines offers, preserves history")
        void testDeleteSellerWithoutActiveDeals() {
            // Create a seller with listings and pending offers, but NO active deals
            Seller cleanSeller = new Seller("seller_clean", "Clean Seller", "Pass1234!", "Bio");
            dataService.registerUserIfAbsent(cleanSeller);

            Listing pendingListing = new Listing();
            pendingListing.setId("L_PENDING_1");
            pendingListing.setTitle("Pending Item");
            pendingListing.setSellerUsername("seller_clean");
            pendingListing.setStatus(ListingStatus.PENDING_REVIEW);
            dataService.addListing(pendingListing);

            Listing liveListing = new Listing();
            liveListing.setId("L_LIVE_1");
            liveListing.setTitle("Live Item");
            liveListing.setSellerUsername("seller_clean");
            liveListing.setStatus(ListingStatus.LIVE);
            dataService.addListing(liveListing);

            // Add pending offer from buyer alice on the live listing
            Offer pendingOffer = new Offer("O_PENDING_1", OfferType.PRICE_OFFER, "L_LIVE_1", "alice", "seller_clean");
            pendingOffer.setStatus(OfferStatus.PENDING);
            dataService.addOffer(pendingOffer);

            SessionManager.login(cleanSeller);
            assertTrue(SessionManager.isLoggedIn());

            User admin = dataService.findUser("admin");
            UserManagementResult result = SessionManager.withSession(admin, () -> service.deleteUser("seller_clean", "admin"));
            assertTrue(result.isSuccess());
            assertTrue(cleanSeller.isDeleted());
            assertEquals(AccountStatus.DELETED, cleanSeller.getAccountStatus());

            // Session ended
            assertFalse(SessionManager.isLoggedIn());

            // Username stays reserved
            assertTrue(dataService.usernameExists("seller_clean"));

            // Listings removed
            assertEquals(ListingStatus.REMOVED, pendingListing.getStatus());
            assertEquals(ListingStatus.REMOVED, liveListing.getStatus());

            // Offers declined
            assertEquals(OfferStatus.DECLINED, pendingOffer.getStatus());

            // Buyer alice notified
            List<Notification> aliceNotifs = dataService.getNotificationsFor("alice");
            assertTrue(aliceNotifs.stream().anyMatch(n -> n.getTitle().contains("Offer Declined")));

            // Admin audit log
            List<Notification> adminNotifs = dataService.getNotificationsFor("admin");
            assertTrue(adminNotifs.stream().anyMatch(n -> n.getCategory().equals(UserManagementService.AUDIT_LOG_CATEGORY)
                    && n.getMessage().contains("soft-deleted account @seller_clean")));

            // Display name returns "Deleted user"
            assertEquals("Deleted user", cleanSeller.getDisplayName());
        }

        @Test
        @DisplayName("Delete Buyer without active deals: withdraws pending offers, notifies sellers")
        void testDeleteBuyerWithoutActiveDeals() {
            Buyer cleanBuyer = new Buyer("buyer_clean", "Clean Buyer", "Pass1234!", "Hall 1");
            dataService.registerUserIfAbsent(cleanBuyer);

            // Buyer has a pending offer on charlie's listing
            Offer buyerOffer = new Offer("O_BUYER_1", OfferType.PRICE_OFFER, "101", "buyer_clean", "charlie");
            buyerOffer.setStatus(OfferStatus.PENDING);
            dataService.addOffer(buyerOffer);

            SessionManager.login(cleanBuyer);
            assertTrue(SessionManager.isLoggedIn());

            User admin = dataService.findUser("admin");
            UserManagementResult result = SessionManager.withSession(admin, () -> service.deleteUser("buyer_clean", "admin"));
            assertTrue(result.isSuccess());
            assertTrue(cleanBuyer.isDeleted());
            assertEquals(AccountStatus.DELETED, cleanBuyer.getAccountStatus());

            // Session ended
            assertFalse(SessionManager.isLoggedIn());

            // Offer withdrawn
            assertEquals(OfferStatus.WITHDRAWN, buyerOffer.getStatus());

            // Seller charlie notified
            List<Notification> charlieNotifs = dataService.getNotificationsFor("charlie");
            assertTrue(charlieNotifs.stream().anyMatch(n -> n.getTitle().contains("Offer Withdrawn")));

            // Audit log
            List<Notification> adminNotifs = dataService.getNotificationsFor("admin");
            assertTrue(adminNotifs.stream().anyMatch(n -> n.getCategory().equals(UserManagementService.AUDIT_LOG_CATEGORY)
                    && n.getMessage().contains("soft-deleted account @buyer_clean")));

            assertEquals("Deleted user", cleanBuyer.getDisplayName());
        }

        @Test
        @DisplayName("Attempting to delete already deleted user returns ALREADY_IN_STATE")
        void testDeleteAlreadyDeletedUser() {
            Buyer cleanBuyer = new Buyer("buyer_del", "Clean Buyer", "Pass1234!");
            dataService.registerUserIfAbsent(cleanBuyer);

            UserManagementResult r1 = service.deleteUser("buyer_del", "admin");
            assertTrue(r1.isSuccess());

            UserManagementResult r2 = service.deleteUser("buyer_del", "admin");
            assertFalse(r2.isSuccess());
            assertEquals(UserManagementStatus.ALREADY_IN_STATE, r2.getStatus());
        }
    }

    @Nested
    @DisplayName("Active Deals and Historical Records")
    class ActiveDealsAndHistoryTests {

        @Test
        @DisplayName("getActiveDealsForUser returns only ACTIVE deals for user")
        void testGetActiveDealsForUser() {
            List<Deal> activeAlice = service.getActiveDealsForUser("alice");
            for (Deal d : activeAlice) {
                assertEquals(DealStatus.ACTIVE, d.getStatus());
                assertTrue("alice".equalsIgnoreCase(d.getBuyerUsername()) || "alice".equalsIgnoreCase(d.getSellerUsername()));
            }

            assertTrue(service.getActiveDealsForUser(null).isEmpty());
            assertTrue(service.getActiveDealsForUser("").isEmpty());
            assertTrue(service.getActiveDealsForUser("nonexistent").isEmpty());
        }
    }

    @Nested
    @DisplayName("Thread-safety & Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent status toggle operations remain consistent")
        void testConcurrentStatusChanges() throws InterruptedException, ExecutionException {
            Buyer testUser = new Buyer("concurrency_user", "Concurrent User", "Pass1234!");
            dataService.registerUserIfAbsent(testUser);

            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            List<Callable<UserManagementResult>> tasks = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                AccountStatus target = (i % 2 == 0) ? AccountStatus.DEACTIVATED : AccountStatus.ACTIVE;
                tasks.add(() -> service.changeAccountStatus("concurrency_user", target, "admin"));
            }

            List<Future<UserManagementResult>> futures = executor.invokeAll(tasks);
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            int successCount = 0;
            int alreadyInStateCount = 0;

            for (Future<UserManagementResult> f : futures) {
                UserManagementResult res = f.get();
                if (res.isSuccess()) {
                    successCount++;
                } else if (res.getStatus() == UserManagementStatus.ALREADY_IN_STATE) {
                    alreadyInStateCount++;
                }
            }

            assertEquals(threads, successCount + alreadyInStateCount);
            assertNotNull(testUser.getAccountStatus());
        }
    }

    @Test
    @DisplayName("RBAC: Unauthenticated caller throws SecurityException")
    void testUnauthenticatedAccessThrowsSecurityException() {
        SessionManager.logout();
        assertThrows(SecurityException.class, () -> service.getUsers(null));
        assertThrows(SecurityException.class, () -> service.getUser("alice"));
        assertThrows(SecurityException.class, () -> service.getActiveDealsForUser("alice"));
        assertThrows(SecurityException.class, () -> service.editUser(UserEditRequest.builder().targetUsername("alice").adminUsername("admin").newFullName("Name").build()));
        assertThrows(SecurityException.class, () -> service.changeAccountStatus("alice", AccountStatus.DEACTIVATED, "admin"));
        assertThrows(SecurityException.class, () -> service.deleteUser("alice", "admin"));
    }

    @Test
    @DisplayName("RBAC: Non-admin caller throws SecurityException")
    void testNonAdminAccessThrowsSecurityException() {
        SessionManager.login(dataService.findUser("alice"));
        assertThrows(SecurityException.class, () -> service.getUsers(null));
        assertThrows(SecurityException.class, () -> service.getUser("alice"));
        assertThrows(SecurityException.class, () -> service.getActiveDealsForUser("alice"));
        assertThrows(SecurityException.class, () -> service.editUser(UserEditRequest.builder().targetUsername("alice").adminUsername("admin").newFullName("Name").build()));
        assertThrows(SecurityException.class, () -> service.changeAccountStatus("alice", AccountStatus.DEACTIVATED, "admin"));
        assertThrows(SecurityException.class, () -> service.deleteUser("alice", "admin"));
    }
}

