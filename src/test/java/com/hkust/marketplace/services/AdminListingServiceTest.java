package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminListingService Tests (US-A07 Manage All Listings)")
class AdminListingServiceTest {

    private DummyDataService dataService;
    private AdminListingService service;
    private static final String ADMIN_USER = "admin";

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        service = new AdminListingService(dataService);
        SessionManager.login(dataService.findUser(ADMIN_USER));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    private Listing getSampleListing(String titleSubstring) {
        return dataService.getAllListings().stream()
                .filter(l -> l != null && l.getTitle() != null && l.getTitle().toLowerCase().contains(titleSubstring.toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Listing containing '" + titleSubstring + "' not found"));
    }

    @Test
    @DisplayName("Constructors: default and parameterized")
    void testConstructors() {
        AdminListingService defaultService = new AdminListingService();
        assertNotNull(defaultService);

        assertThrows(NullPointerException.class, () -> new AdminListingService(null));
    }

    @Nested
    @DisplayName("Basic Flow Tests: View & Filter All Listings")
    class ViewAndFilterTests {

        @Test
        @DisplayName("getAllListings returns all listings sorted newest first")
        void testGetAllListings() {
            List<Listing> listings = service.getAllListings();
            assertFalse(listings.isEmpty());

            for (int i = 0; i < listings.size() - 1; i++) {
                LocalDateTime d1 = listings.get(i).getPostedAt();
                LocalDateTime d2 = listings.get(i + 1).getPostedAt();
                if (d1 != null && d2 != null) {
                    assertTrue(d1.isAfter(d2) || d1.isEqual(d2));
                }
            }
        }

        @Test
        @DisplayName("filterListings with null criteria defaults to all listings")
        void testFilterNullCriteria() {
            List<Listing> all = service.getAllListings();
            List<Listing> filtered = service.filterListings(null);
            assertEquals(all.size(), filtered.size());
        }

        @Test
        @DisplayName("filterListings with tie-breaking posted dates")
        void testFilterTieBreaker() {
            LocalDateTime now = LocalDateTime.now();
            Listing lA = new Listing();
            lA.setId("L901");
            lA.setTitle("Item A");
            lA.setPostedDate(now);
            lA.setStatus(ListingStatus.LIVE);
            dataService.addListing(lA);

            Listing lB = new Listing();
            lB.setId("L902");
            lB.setTitle("Item B");
            lB.setPostedDate(now);
            lB.setStatus(ListingStatus.LIVE);
            dataService.addListing(lB);

            List<Listing> list = service.getAllListings();
            assertNotNull(list);
        }

        @Test
        @DisplayName("filterListings with null postedDate handles sorting gracefully")
        void testFilterNullDates() {
            Listing lNull1 = new Listing();
            lNull1.setId("L801");
            lNull1.setTitle("Null Date 1");
            lNull1.setPostedDate(null);
            lNull1.setStatus(ListingStatus.LIVE);
            dataService.addListing(lNull1);

            Listing lNull2 = new Listing();
            lNull2.setId("L802");
            lNull2.setTitle("Null Date 2");
            lNull2.setPostedDate(null);
            lNull2.setStatus(ListingStatus.LIVE);
            dataService.addListing(lNull2);

            List<Listing> list = service.getAllListings();
            assertNotNull(list);
            assertTrue(list.contains(lNull1));
            assertTrue(list.contains(lNull2));
        }

        @Test
        @DisplayName("Filter by status (LIVE, PENDING_REVIEW, REMOVED)")
        void testFilterByStatus() {
            List<Listing> liveListings = service.filterListings(AdminListingFilterCriteria.byStatus(ListingStatus.LIVE));
            assertFalse(liveListings.isEmpty());
            for (Listing l : liveListings) {
                assertEquals(ListingStatus.LIVE, l.getStatus());
            }

            List<Listing> pendingListings = service.filterListings(AdminListingFilterCriteria.byStatus(ListingStatus.PENDING_REVIEW));
            assertFalse(pendingListings.isEmpty());
            for (Listing l : pendingListings) {
                assertEquals(ListingStatus.PENDING_REVIEW, l.getStatus());
            }

            List<Listing> removedListings = service.filterListings(AdminListingFilterCriteria.byStatus(ListingStatus.REMOVED));
            assertFalse(removedListings.isEmpty());
            for (Listing l : removedListings) {
                assertEquals(ListingStatus.REMOVED, l.getStatus());
            }
        }

        @Test
        @DisplayName("Filter by keyword across title, seller username, and seller name")
        void testFilterByKeyword() {
            List<Listing> byTitle = service.filterListings(AdminListingFilterCriteria.byKeyword("calculus"));
            assertFalse(byTitle.isEmpty());
            assertTrue(byTitle.get(0).getTitle().toLowerCase().contains("calculus"));

            List<Listing> bySeller = service.filterListings(AdminListingFilterCriteria.byKeyword("charlie"));
            assertFalse(bySeller.isEmpty());

            List<Listing> byName = service.filterListings(AdminListingFilterCriteria.byKeyword("diana ng"));
            assertFalse(byName.isEmpty());
        }

        @Test
        @DisplayName("Alternative Flow A1: Filter matching no listings")
        void testAlternativeFlowA1NoListings() {
            AdminListingFilterCriteria criteria = new AdminListingFilterCriteria("non_existent_keyword_xyz", null, null, null);
            List<Listing> results = service.filterListings(criteria);
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("Subflow S1 Tests: Add Listing on Behalf of Seller")
    class AddListingTests {

        @Test
        @DisplayName("Unauthorized administrator attempts to add listing")
        void testAddListingUnauthorized() {
            AdminListingCreateRequest req = new AdminListingCreateRequest(
                    "charlie", "Item", "Desc", ListingType.SELL,
                    Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                    100.0, null, List.of(new Photo("p.png"))
            );

            AdminListingOperationResult res1 = service.createListingOnBehalfOfSeller(req, null);
            assertEquals(AdminListingOperationStatus.UNAUTHORIZED, res1.getStatus());

            AdminListingOperationResult res2 = service.createListingOnBehalfOfSeller(req, "alice"); // Buyer
            assertEquals(AdminListingOperationStatus.UNAUTHORIZED, res2.getStatus());

            User inactiveAdmin = new Admin("admin2", "Admin Two", "pass");
            inactiveAdmin.setAccountStatus(AccountStatus.DEACTIVATED);
            dataService.registerUserIfAbsent(inactiveAdmin);

            AdminListingOperationResult res3 = service.createListingOnBehalfOfSeller(req, "admin2");
            assertEquals(AdminListingOperationStatus.UNAUTHORIZED, res3.getStatus());
        }

        @Test
        @DisplayName("Null create request returns invalid field")
        void testAddListingNullRequest() {
            AdminListingOperationResult res = service.createListingOnBehalfOfSeller(null, ADMIN_USER);
            assertEquals(AdminListingOperationStatus.INVALID_FIELD, res.getStatus());
            assertEquals("request", res.getInvalidFieldName());
        }

        @Test
        @DisplayName("Alternative Flow A2: Seller not found, not seller role, or deactivated/deleted")
        void testAlternativeFlowA2SellerNotActive() {
            AdminListingCreateRequest req = new AdminListingCreateRequest(
                    null, "Item", "Desc", ListingType.SELL,
                    Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                    100.0, null, List.of(new Photo("p.png"))
            );

            // Null seller
            AdminListingOperationResult resNull = service.createListingOnBehalfOfSeller(req, ADMIN_USER);
            assertEquals(AdminListingOperationStatus.SELLER_NOT_ACTIVE, resNull.getStatus());

            // Non-existent seller
            req.setSellerUsername("ghost_seller");
            AdminListingOperationResult resGhost = service.createListingOnBehalfOfSeller(req, ADMIN_USER);
            assertEquals(AdminListingOperationStatus.SELLER_NOT_ACTIVE, resGhost.getStatus());

            // User exists but is a buyer
            req.setSellerUsername("alice");
            AdminListingOperationResult resBuyer = service.createListingOnBehalfOfSeller(req, ADMIN_USER);
            assertEquals(AdminListingOperationStatus.SELLER_NOT_ACTIVE, resBuyer.getStatus());

            // Deactivated seller
            Seller deactivated = new Seller("deactivated_seller", "Deact", "pass");
            deactivated.setAccountStatus(AccountStatus.DEACTIVATED);
            dataService.registerUserIfAbsent(deactivated);
            req.setSellerUsername("deactivated_seller");
            AdminListingOperationResult resDeact = service.createListingOnBehalfOfSeller(req, ADMIN_USER);
            assertEquals(AdminListingOperationStatus.SELLER_NOT_ACTIVE, resDeact.getStatus());

            // Deleted seller
            Seller deleted = new Seller("deleted_seller", "Del", "pass");
            deleted.setAccountStatus(AccountStatus.DELETED);
            dataService.registerUserIfAbsent(deleted);
            req.setSellerUsername("deleted_seller");
            AdminListingOperationResult resDel = service.createListingOnBehalfOfSeller(req, ADMIN_USER);
            assertEquals(AdminListingOperationStatus.SELLER_NOT_ACTIVE, resDel.getStatus());
        }

        @Test
        @DisplayName("Alternative Flow A3: Missing or invalid fields on creation")
        void testAlternativeFlowA3InvalidFields() {
            // Missing title
            AdminListingCreateRequest req = new AdminListingCreateRequest(
                    "charlie", "   ", "Desc", ListingType.SELL,
                    Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                    100.0, null, List.of(new Photo("p.png"))
            );
            assertEquals("title", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            // Missing category
            req.setTitle("Valid Title");
            req.setCategory(null);
            assertEquals("category", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            // Missing condition
            req.setCategory(Category.BOOKS);
            req.setCondition(null);
            assertEquals("condition", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            // Missing meetup location
            req.setCondition(Condition.LIKE_NEW);
            req.setMeetupLocation(null);
            assertEquals("meetupLocation", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            // Missing type
            req.setMeetupLocation(MeetupLocation.LIBRARY);
            req.setType(null);
            assertEquals("type", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            // Missing photos (null or empty)
            req.setType(ListingType.SELL);
            req.setPhotos(null);
            assertEquals("photos", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            req.setPhotos(Collections.emptyList());
            assertEquals("photos", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            // More than 6 photos
            List<Photo> tooMany = List.of(
                    new Photo("1"), new Photo("2"), new Photo("3"),
                    new Photo("4"), new Photo("5"), new Photo("6"), new Photo("7")
            );
            req.setPhotos(tooMany);
            assertEquals("photos", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            // Sell: missing or non-positive price
            req.setPhotos(List.of(new Photo("1")));
            req.setPrice(null);
            assertEquals("price", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            req.setPrice(0.0);
            assertEquals("price", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            req.setPrice(-50.0);
            assertEquals("price", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            // Lend: missing or non-positive days
            req.setType(ListingType.LEND);
            req.setLendingDays(null);
            assertEquals("lendingDays", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            req.setLendingDays(0);
            assertEquals("lendingDays", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());

            req.setLendingDays(-5);
            assertEquals("lendingDays", service.createListingOnBehalfOfSeller(req, ADMIN_USER).getInvalidFieldName());
        }

        @Test
        @DisplayName("Subflow S1: Successfully add SELL listing with status LIVE and notify seller")
        void testAddSellListingSuccess() {
            AdminListingCreateRequest req = new AdminListingCreateRequest(
                    "charlie", "Algorithms 4th Ed", "Sedgewick", ListingType.SELL,
                    Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                    220.0, null, List.of(new Photo("algo.png"))
            );

            AdminListingOperationResult res = service.createListingOnBehalfOfSeller(req, ADMIN_USER);
            assertTrue(res.isSuccess());
            assertNotNull(res.getListing());
            Listing created = res.getListing();

            assertEquals("Algorithms 4th Ed", created.getTitle());
            assertEquals(ListingStatus.LIVE, created.getStatus(), "Admin added listings must be LIVE directly");
            assertEquals("charlie", created.getSellerUsername());
            assertEquals("Charlie Lee", created.getSellerFullName());
            assertEquals(220.0, created.getPrice());
            assertNull(created.getLendingDays());

            // Verify stored in dataService
            assertNotNull(dataService.findListing(created.getId()));

            // Verify seller notification dispatched
            List<Notification> notifs = dataService.getNotificationsFor("charlie");
            boolean notified = notifs.stream()
                    .anyMatch(n -> n.getTitle().contains("Listing Published by Admin") && n.getMessage().contains("Algorithms 4th Ed"));
            assertTrue(notified, "Seller must be notified of the published listing");
        }

        @Test
        @DisplayName("Subflow S1: Successfully add LEND listing with status LIVE")
        void testAddLendListingSuccess() {
            AdminListingCreateRequest req = new AdminListingCreateRequest(
                    "diana", "Microscope", "Lab grade", ListingType.LEND,
                    Category.ELECTRONICS, Condition.USED, MeetupLocation.ATRIUM,
                    null, 14, List.of(new Photo("mic.png"))
            );

            AdminListingOperationResult res = service.createListingOnBehalfOfSeller(req, ADMIN_USER);
            assertTrue(res.isSuccess());
            Listing created = res.getListing();
            assertEquals(ListingStatus.LIVE, created.getStatus());
            assertEquals(14, created.getLendingDays());
            assertNull(created.getPrice());
        }

        @Test
        @DisplayName("Subflow S1: Successfully add GIVEAWAY listing with status LIVE")
        void testAddGiveawayListingSuccess() {
            AdminListingCreateRequest req = new AdminListingCreateRequest(
                    "charlie", "Free Plant", "Pothos cutting", ListingType.GIVEAWAY,
                    Category.OTHER, Condition.LIKE_NEW, MeetupLocation.STUDENT_HALLS,
                    null, null, List.of(new Photo("plant.png"))
            );

            AdminListingOperationResult res = service.createListingOnBehalfOfSeller(req, ADMIN_USER);
            assertTrue(res.isSuccess());
            Listing created = res.getListing();
            assertEquals(ListingStatus.LIVE, created.getStatus());
            assertNull(created.getPrice());
            assertNull(created.getLendingDays());
        }
    }

    @Nested
    @DisplayName("Subflow S2 Tests: Edit Listing")
    class EditListingTests {

        @Test
        @DisplayName("Unauthorized admin cannot edit listing")
        void testEditUnauthorized() {
            AdminListingEditRequest req = new AdminListingEditRequest(
                    "L1", "New Title", "Desc", ListingType.SELL,
                    Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                    100.0, null, List.of(new Photo("p.png"))
            );
            assertEquals(AdminListingOperationStatus.UNAUTHORIZED, service.editListing(req, null).getStatus());
            assertEquals(AdminListingOperationStatus.UNAUTHORIZED, service.editListing(req, "alice").getStatus());
        }

        @Test
        @DisplayName("Edit with missing listing ID or non-existent listing")
        void testEditNotFound() {
            AdminListingEditRequest req = new AdminListingEditRequest(
                    null, "New Title", "Desc", ListingType.SELL,
                    Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                    100.0, null, List.of(new Photo("p.png"))
            );
            assertEquals(AdminListingOperationStatus.INVALID_FIELD, service.editListing(req, ADMIN_USER).getStatus());

            req.setListingId("L_NON_EXISTENT");
            assertEquals(AdminListingOperationStatus.LISTING_NOT_FOUND, service.editListing(req, ADMIN_USER).getStatus());
        }

        @Test
        @DisplayName("Alternative Flow A4: Cannot edit if deal is in progress or completed (RESERVED, SOLD, LENT, GIVEN)")
        void testAlternativeFlowA4EditDealInProgress() {
            Listing target = getSampleListing("Calculus");
            assertNotNull(target);

            AdminListingEditRequest req = new AdminListingEditRequest(
                    target.getId(), "Updated Title", "Desc", ListingType.SELL,
                    Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                    150.0, null, List.of(new Photo("p.png"))
            );

            for (ListingStatus blockedStatus : List.of(ListingStatus.RESERVED, ListingStatus.SOLD, ListingStatus.LENT, ListingStatus.GIVEN)) {
                target.setStatus(blockedStatus);
                AdminListingOperationResult res = service.editListing(req, ADMIN_USER);
                assertEquals(AdminListingOperationStatus.DEAL_IN_PROGRESS_OR_COMPLETED, res.getStatus());
                assertTrue(res.getMessage().contains("deal is in progress or completed"));
            }
        }

        @Test
        @DisplayName("Alternative Flow A3: Invalid fields on edit")
        void testEditInvalidFields() {
            Listing target = getSampleListing("Calculus");
            target.setStatus(ListingStatus.LIVE);

            AdminListingEditRequest req = new AdminListingEditRequest(
                    target.getId(), "", "Desc", ListingType.SELL,
                    Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                    150.0, null, List.of(new Photo("p.png"))
            );
            assertEquals("title", service.editListing(req, ADMIN_USER).getInvalidFieldName());

            req.setTitle("Valid Title");
            req.setPrice(-10.0);
            assertEquals("price", service.editListing(req, ADMIN_USER).getInvalidFieldName());

            req.setType(ListingType.LEND);
            req.setLendingDays(0);
            assertEquals("lendingDays", service.editListing(req, ADMIN_USER).getInvalidFieldName());
        }

        @Test
        @DisplayName("Subflow S2: Successfully edit LIVE listing, preserving status and notifying seller")
        void testEditLiveListingSuccess() {
            Listing target = getSampleListing("Calculus");
            target.setStatus(ListingStatus.LIVE);

            AdminListingEditRequest req = new AdminListingEditRequest(
                    target.getId(), "Calculus 9th Ed (Stewart)", "Updated description with formula sheets",
                    ListingType.SELL, Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LG1_CANTEEN,
                    195.0, null, List.of(new Photo("p1.png"), new Photo("p2.png"))
            );

            AdminListingOperationResult res = service.editListing(req, ADMIN_USER);
            assertTrue(res.isSuccess());

            Listing updated = dataService.findListing(target.getId());
            assertEquals("Calculus 9th Ed (Stewart)", updated.getTitle());
            assertEquals(195.0, updated.getPrice());
            assertEquals(ListingStatus.LIVE, updated.getStatus(), "Status must be preserved during edit");

            // Seller notified
            List<Notification> notifs = dataService.getNotificationsFor(target.getSellerUsername());
            boolean notified = notifs.stream()
                    .anyMatch(n -> n.getTitle().contains("Listing Updated by Admin") && n.getMessage().contains("Calculus 9th Ed"));
            assertTrue(notified);
        }

        @Test
        @DisplayName("Subflow S2: Successfully edit PENDING_REVIEW listing, preserving status")
        void testEditPendingListingSuccess() {
            Listing target = getSampleListing("Mini Fridge"); // PENDING_REVIEW in seed
            assertEquals(ListingStatus.PENDING_REVIEW, target.getStatus());

            AdminListingEditRequest req = new AdminListingEditRequest(
                    target.getId(), "Mini Fridge Compact", "Cleaned and working",
                    ListingType.SELL, Category.FURNITURE, Condition.USED, MeetupLocation.LIBRARY,
                    350.0, null, List.of(new Photo("fridge.png"))
            );

            AdminListingOperationResult res = service.editListing(req, ADMIN_USER);
            assertTrue(res.isSuccess());
            assertEquals(ListingStatus.PENDING_REVIEW, dataService.findListing(target.getId()).getStatus());
        }

        @Test
        @DisplayName("Subflow S2: Edit listing to GIVEAWAY type clears price and lending duration")
        void testEditToGiveaway() {
            Listing target = getSampleListing("MacBook");
            target.setStatus(ListingStatus.LIVE);

            AdminListingEditRequest req = new AdminListingEditRequest(
                    target.getId(), "Free Charger", "Old charger",
                    ListingType.GIVEAWAY, Category.ELECTRONICS, Condition.USED, MeetupLocation.LIBRARY,
                    null, null, List.of(new Photo("c.png"))
            );

            AdminListingOperationResult res = service.editListing(req, ADMIN_USER);
            assertTrue(res.isSuccess());
            Listing updated = dataService.findListing(target.getId());
            assertEquals(ListingType.GIVEAWAY, updated.getType());
            assertNull(updated.getPrice());
            assertNull(updated.getLendingDays());
        }
    }

    @Nested
    @DisplayName("Subflow S3 Tests: Take Down Listing")
    class TakeDownListingTests {

        @Test
        @DisplayName("Unauthorized admin cannot take down listing")
        void testTakeDownUnauthorized() {
            assertEquals(AdminListingOperationStatus.UNAUTHORIZED,
                    service.takeDownListing("L1", "Reason", null).getStatus());
            assertEquals(AdminListingOperationStatus.UNAUTHORIZED,
                    service.takeDownListing("L1", "Reason", "alice").getStatus());
        }

        @Test
        @DisplayName("Alternative Flow A6: Empty or blank reason rejected")
        void testAlternativeFlowA6EmptyReason() {
            assertEquals(AdminListingOperationStatus.EMPTY_REASON,
                    service.takeDownListing("L1", null, ADMIN_USER).getStatus());
            assertEquals(AdminListingOperationStatus.EMPTY_REASON,
                    service.takeDownListing("L1", "   ", ADMIN_USER).getStatus());
        }

        @Test
        @DisplayName("Take down non-existent or null listing")
        void testTakeDownNotFound() {
            assertEquals(AdminListingOperationStatus.LISTING_NOT_FOUND,
                    service.takeDownListing(null, "Reason", ADMIN_USER).getStatus());
            assertEquals(AdminListingOperationStatus.LISTING_NOT_FOUND,
                    service.takeDownListing("L_GHOST", "Reason", ADMIN_USER).getStatus());
        }

        @Test
        @DisplayName("Alternative Flow A4: Cannot take down if deal is in progress or completed")
        void testAlternativeFlowA4TakeDownDealInProgress() {
            Listing target = getSampleListing("MacBook");
            for (ListingStatus blocked : List.of(ListingStatus.RESERVED, ListingStatus.SOLD, ListingStatus.LENT, ListingStatus.GIVEN)) {
                target.setStatus(blocked);
                AdminListingOperationResult res = service.takeDownListing(target.getId(), "Prohibited", ADMIN_USER);
                assertEquals(AdminListingOperationStatus.DEAL_IN_PROGRESS_OR_COMPLETED, res.getStatus());
            }
        }

        @Test
        @DisplayName("Alternative Flow A5: Cannot take down if not LIVE (PENDING_REVIEW, REJECTED, REMOVED)")
        void testAlternativeFlowA5NotLive() {
            Listing target = getSampleListing("MacBook");
            for (ListingStatus nonLive : List.of(ListingStatus.PENDING_REVIEW, ListingStatus.REJECTED, ListingStatus.REMOVED)) {
                target.setStatus(nonLive);
                AdminListingOperationResult res = service.takeDownListing(target.getId(), "Reason", ADMIN_USER);
                assertEquals(AdminListingOperationStatus.LISTING_NOT_LIVE, res.getStatus());
                assertTrue(res.getMessage().contains("Only a Live listing can be taken down"));
            }
        }

        @Test
        @DisplayName("Subflow S3: Successfully take down LIVE listing, decline active offers, and notify buyers & seller")
        void testTakeDownSuccessWithOffers() {
            Listing target = new Listing();
            target.setId("L_TAKEDOWN_TEST");
            target.setTitle("Custom Electronic Accessory");
            target.setSellerUsername("charlie");
            target.setSellerFullName("Charlie Lee");
            target.setStatus(ListingStatus.LIVE);
            dataService.addListing(target);

            // Create active offers on target: 1 PENDING and 1 COUNTERED
            Offer pendingOffer = new Offer("O_TEST_1", OfferType.PRICE_OFFER, target.getId(), "alice", target.getSellerUsername());
            pendingOffer.setStatus(OfferStatus.PENDING);
            dataService.addOffer(pendingOffer);

            Offer counteredOffer = new Offer("O_TEST_2", OfferType.PRICE_OFFER, target.getId(), "bob", target.getSellerUsername());
            counteredOffer.setStatus(OfferStatus.COUNTERED);
            Offer counterChild = new Offer("O_TEST_2_C", OfferType.PRICE_OFFER, target.getId(), "bob", target.getSellerUsername());
            counterChild.setStatus(OfferStatus.COUNTERED);
            counteredOffer.setCounterOffer(counterChild);
            dataService.addOffer(counteredOffer);

            AdminListingOperationResult res = service.takeDownListing(target.getId(), "Prohibited electronic accessory", ADMIN_USER);
            assertTrue(res.isSuccess());
            assertEquals(2, res.getAffectedOffersCount());

            // Listing status updated to REMOVED and reason stored
            assertEquals(ListingStatus.REMOVED, target.getStatus());
            assertEquals("Prohibited electronic accessory", target.getAdminReason());

            // Offers declined
            assertEquals(OfferStatus.DECLINED, pendingOffer.getStatus());
            assertTrue(pendingOffer.getDeclineReason().contains("Listing removed by administrator"));

            assertEquals(OfferStatus.DECLINED, counteredOffer.getStatus());
            assertEquals(OfferStatus.DECLINED, counterChild.getStatus());

            // Buyers notified
            List<Notification> aliceNotifs = dataService.getNotificationsFor("alice");
            assertTrue(aliceNotifs.stream().anyMatch(n -> n.getTitle().contains("Listing Removed by Admin") && n.getMessage().contains("Prohibited electronic accessory")));

            List<Notification> bobNotifs = dataService.getNotificationsFor("bob");
            assertTrue(bobNotifs.stream().anyMatch(n -> n.getTitle().contains("Listing Removed by Admin")));

            // Seller notified
            List<Notification> sellerNotifs = dataService.getNotificationsFor(target.getSellerUsername());
            assertTrue(sellerNotifs.stream().anyMatch(n -> n.getTitle().contains("Listing Taken Down") && n.getMessage().contains("Prohibited electronic accessory")));
        }
    }

    @Nested
    @DisplayName("Concurrency & Helper Query Tests")
    class ConcurrencyAndHelperTests {

        @Test
        @DisplayName("Active sellers query filters and sorts correctly")
        void testGetActiveSellers() {
            List<Seller> sellers = service.getActiveSellers();
            assertFalse(sellers.isEmpty());
            for (Seller s : sellers) {
                assertEquals(UserRole.SELLER, s.getRole());
                assertTrue(s.isActive());
            }
        }

        @Test
        @DisplayName("Active offers count on listing")
        void testGetActiveOffersCount() {
            assertEquals(0, service.getActiveOffersCount(null));
            Listing target = getSampleListing("Calculus");
            int count = service.getActiveOffersCount(target.getId());
            assertTrue(count >= 0);
        }

        @Test
        @DisplayName("Listing counts by status distribution")
        void testGetListingCountsByStatus() {
            Map<ListingStatus, Long> counts = service.getListingCountsByStatus();
            assertNotNull(counts);
            assertTrue(counts.containsKey(ListingStatus.LIVE));
            assertTrue(counts.containsKey(ListingStatus.REMOVED));
            assertTrue(counts.get(ListingStatus.LIVE) > 0);
            assertTrue(counts.get(ListingStatus.REMOVED) > 0);
        }

        @Test
        @DisplayName("Concurrency: Atomic take-down with simultaneous offer accept")
        void testAtomicTakeDownConcurrency() throws InterruptedException, ExecutionException {
            Listing target = getSampleListing("Notebook");
            target.setStatus(ListingStatus.LIVE);

            Offer offer = new Offer("O_CONCURRENT", OfferType.PRICE_OFFER, target.getId(), "alice", target.getSellerUsername());
            offer.setStatus(OfferStatus.PENDING);
            dataService.addOffer(offer);

            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CyclicBarrier barrier = new CyclicBarrier(threads);

            AtomicInteger takeDownSuccesses = new AtomicInteger(0);
            AtomicInteger offerAcceptedSuccesses = new AtomicInteger(0);

            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                tasks.add(() -> {
                    barrier.await();
                    if (idx % 2 == 0) {
                        AdminListingOperationResult res = service.takeDownListing(target.getId(), "Copyright issue", ADMIN_USER);
                        if (res.isSuccess()) {
                            takeDownSuccesses.incrementAndGet();
                        }
                    } else {
                        // Concurrent seller/buyer attempt to reserve listing
                        synchronized (target) {
                            if (target.getStatus() == ListingStatus.LIVE) {
                                target.setStatus(ListingStatus.RESERVED);
                                offerAcceptedSuccesses.incrementAndGet();
                            }
                        }
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get();
            }
            executor.shutdown();

            // Either the take-down succeeded first or the offer reservation succeeded first
            assertTrue(takeDownSuccesses.get() <= 1, "At most one take-down can succeed");
            assertTrue(target.getStatus() == ListingStatus.REMOVED || target.getStatus() == ListingStatus.RESERVED);
        }
    }

    @Test
    @DisplayName("RBAC: Unauthenticated caller throws SecurityException")
    void testUnauthenticatedAccessThrowsSecurityException() {
        SessionManager.logout();
        assertThrows(SecurityException.class, () -> service.getAllListings());
        assertThrows(SecurityException.class, () -> service.filterListings(null));
        assertThrows(SecurityException.class, () -> service.getActiveSellers());
        assertThrows(SecurityException.class, () -> service.getActiveOffersCount("L100"));
        assertThrows(SecurityException.class, () -> service.getListingCountsByStatus());
        assertThrows(SecurityException.class, () -> service.createListingOnBehalfOfSeller(null, ADMIN_USER));
        assertThrows(SecurityException.class, () -> service.editListing(null, ADMIN_USER));
        assertThrows(SecurityException.class, () -> service.takeDownListing("L100", "Reason", ADMIN_USER));
    }

    @Test
    @DisplayName("RBAC: Non-admin caller throws SecurityException")
    void testNonAdminAccessThrowsSecurityException() {
        SessionManager.login(dataService.findUser("alice"));
        assertThrows(SecurityException.class, () -> service.getAllListings());
        assertThrows(SecurityException.class, () -> service.filterListings(null));
        assertThrows(SecurityException.class, () -> service.getActiveSellers());
        assertThrows(SecurityException.class, () -> service.getActiveOffersCount("L100"));
        assertThrows(SecurityException.class, () -> service.getListingCountsByStatus());
        assertThrows(SecurityException.class, () -> service.createListingOnBehalfOfSeller(null, ADMIN_USER));
        assertThrows(SecurityException.class, () -> service.editListing(null, ADMIN_USER));
        assertThrows(SecurityException.class, () -> service.takeDownListing("L100", "Reason", ADMIN_USER));
    }
}
