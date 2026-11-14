package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Comprehensive DummyDataService Branch Coverage Tests")
public class ComprehensiveDummyDataServiceTest {

    private DummyDataService service;

    @BeforeEach
    void setUp() {
        service = DummyDataService.getInstance();
        service.resetData();
        SessionManager.logout();
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        service.resetData();
    }

    @Test
    @DisplayName("registerUser and registerUserIfAbsent edge cases")
    void testRegisterUserEdgeCases() {
        // null user
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(null));
        assertFalse(service.registerUserIfAbsent(null));

        // blank username and duplicate username
        Buyer blankUser = new Buyer("   ", "Name", "pass");
        service.registerUser(blankUser);
        assertNotNull(service.findUser("   "));

        // Existing user case-insensitive throws IllegalArgumentException
        Buyer aliceDuplicate = new Buyer("ALICE", "Alice Dup", "pass");
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(aliceDuplicate));
        assertEquals("Alice Chan", service.findUser("alice").getFullName());

        assertFalse(service.registerUserIfAbsent(new Buyer("alice", "Alice Again", "pass")));
        assertEquals("Alice Chan", service.findUser("alice").getFullName(), "Should return existing user without overwriting");

        // Absent user
        assertTrue(service.registerUserIfAbsent(new Buyer("newuser99", "Newbie", "pass")));
        assertNotNull(service.findUser("newuser99"));

        // Update user
        service.updateUser(null);
        service.updateUser(new Buyer("ghost_non_existent", "Ghost", "pass"));

        User newUser = service.findUser("newuser99");
        newUser.setFullName("Updated Newbie");
        service.updateUser(newUser);
        assertEquals("Updated Newbie", service.findUser("newuser99").getFullName());
    }

    @Test
    @DisplayName("Listing operations: add, update, remove nulls and non-existent")
    void testListingOperationsNulls() {
        assertThrows(NullPointerException.class, () -> service.addListing(null));
        assertThrows(NullPointerException.class, () -> service.updateListing(null));
        service.removeListing(null);
        service.removeListing("");
        service.removeListing("NON_EXISTENT_LISTING");

        Listing invalidListing = new Listing();
        invalidListing.setId(null);
        service.addListing(invalidListing);
        service.updateListing(invalidListing);

        Listing valid = new Listing();
        valid.setId("L_CUSTOM_1");
        valid.setTitle("Custom Item");
        valid.setStatus(ListingStatus.LIVE);
        valid.setSellerUsername("charlie");
        service.addListing(valid);
        assertNotNull(service.findListing("L_CUSTOM_1"));

        valid.setTitle("Updated Title");
        service.updateListing(valid);
        assertEquals("Updated Title", service.findListing("L_CUSTOM_1").getTitle());

        service.removeListing("L_CUSTOM_1");
        assertNull(service.findListing("L_CUSTOM_1"));
    }

    @Test
    @DisplayName("Deal operations: add, update, remove, clear nulls and missing IDs")
    void testDealOperationsNulls() {
        assertThrows(NullPointerException.class, () -> service.addDeal(null));
        service.updateDeal(null);
        service.removeDeal(null);
        service.removeDeal("");
        service.removeDeal("NON_EXISTENT_DEAL");

        Deal d = new Deal();
        d.setId(null);
        service.addDeal(d);
        service.updateDeal(d);

        d.setId("D_CUSTOM_1");
        d.setBuyerUsername("alice");
        d.setSellerUsername("charlie");
        d.setStatus(DealStatus.ACTIVE);
        d.setType(DealType.SALE);
        service.addDeal(d);
        assertNotNull(service.findDeal("D_CUSTOM_1"));

        d.setStatus(DealStatus.COMPLETED);
        service.updateDeal(d);
        assertEquals(DealStatus.COMPLETED, service.findDeal("D_CUSTOM_1").getStatus());

        service.removeDeal("D_CUSTOM_1");
        assertNull(service.findDeal("D_CUSTOM_1"));

        service.clearDeals();
        assertTrue(service.getAllDeals().isEmpty());
    }

    @Test
    @DisplayName("Offer operations: add, update nulls and missing IDs")
    void testOfferOperationsNulls() {
        assertThrows(NullPointerException.class, () -> service.addOffer(null));
        service.updateOffer(null);

        Offer o = new Offer();
        o.setId(null);
        service.addOffer(o);
        service.updateOffer(o);

        o.setId("O_CUSTOM_1");
        o.setStatus(OfferStatus.PENDING);
        service.addOffer(o);
        assertNotNull(service.findOffer("O_CUSTOM_1"));

        o.setStatus(OfferStatus.DECLINED);
        service.updateOffer(o);
        assertEquals(OfferStatus.DECLINED, service.findOffer("O_CUSTOM_1").getStatus());

        Offer nonExistent = new Offer();
        nonExistent.setId("O_NON_EXISTENT");
        service.updateOffer(nonExistent);
    }

    @Test
    @DisplayName("Report operations: add, update, find with nulls and missing IDs")
    void testReportOperationsNulls() {
        assertThrows(NullPointerException.class, () -> service.addReport(null));
        service.updateReport(null);
        assertNull(service.findReport(null));
        assertNull(service.findReport(""));
        assertNull(service.findReport("NON_EXISTENT_REPORT"));

        Report r = new Report();
        r.setId(null);
        service.addReport(r);
        service.updateReport(r);

        r.setId("R_CUSTOM_1");
        r.setListingId("L100");
        r.setBuyerUsername("alice");
        r.setReason("Inappropriate item");
        r.setStatus(ReportStatus.OPEN);
        service.addReport(r);
        assertNotNull(service.findReport("R_CUSTOM_1"));

        r.setStatus(ReportStatus.RESOLVED);
        service.updateReport(r);
        assertEquals(ReportStatus.RESOLVED, service.findReport("R_CUSTOM_1").getStatus());

        Report ghost = new Report();
        ghost.setId("R_GHOST");
        service.updateReport(ghost);
    }

    @Test
    @DisplayName("ChatThread operations: add, update, find, getOrCreate edge cases")
    void testChatThreadOperations() {
        service.addChatThread(null);
        service.updateChatThread(null);
        assertNull(service.findChatThread(null));
        assertNull(service.findChatThread(""));
        assertNull(service.findChatThread("GHOST_THREAD"));

        ChatThread ct = new ChatThread();
        ct.setId(null);
        service.addChatThread(ct);
        service.updateChatThread(ct);

        ct.setId("CT_CUSTOM_1");
        ct.setListingId("L100");
        ct.setBuyerUsername("alice");
        ct.setSellerUsername("charlie");
        service.addChatThread(ct);
        assertNotNull(service.findChatThread("CT_CUSTOM_1"));

        service.updateChatThread(ct);

        ChatThread ghost = new ChatThread();
        ghost.setId("CT_GHOST");
        service.updateChatThread(ghost);

        // getOrCreateChatThread
        ChatThread threadExisting = service.getOrCreateChatThread("L100", "alice", "charlie");
        assertNotNull(threadExisting);
        assertEquals("L100", threadExisting.getListingId());

        ChatThread threadCreated = service.getOrCreateChatThread("L101", "bob", "charlie");
        assertNotNull(threadCreated);
        assertEquals("L101", threadCreated.getListingId());
    }

    @Test
    @DisplayName("Notification operations: add, update, remove nulls and bulk remove")
    void testNotificationOperations() {
        assertThrows(NullPointerException.class, () -> service.addNotification(null));
        service.updateNotification(null);
        service.removeNotification(null);
        service.removeNotification("");
        service.removeNotification("NON_EXISTENT_NOTIF");

        Notification n = new Notification();
        n.setId(null);
        service.addNotification(n);
        service.updateNotification(n);

        n.setId("N_CUSTOM_1");
        n.setUsername("alice");
        n.setMessage("Test alert");
        service.addNotification(n);
        assertTrue(service.getNotificationsFor("alice").stream().anyMatch(notif -> "N_CUSTOM_1".equals(notif.getId())));

        n.setRead(true);
        service.updateNotification(n);
        assertTrue(service.getNotificationsFor("alice").stream().anyMatch(notif -> "N_CUSTOM_1".equals(notif.getId()) && notif.isRead()));

        assertTrue(service.removeNotification("N_CUSTOM_1"));
        assertFalse(service.getNotificationsFor("alice").stream().anyMatch(notif -> "N_CUSTOM_1".equals(notif.getId())));

        // removeNotificationsFor
        Notification n2 = new Notification();
        n2.setId("N_CUSTOM_2");
        n2.setUsername("user_temp");
        service.addNotification(n2);
        assertTrue(service.removeNotificationsFor("user_temp") >= 1);
        assertEquals(0, service.removeNotificationsFor("user_temp"));
        assertEquals(0, service.removeNotificationsFor(null));
        assertEquals(0, service.removeNotificationsFor("  "));
    }

    @Test
    @DisplayName("Review operations and recalculateSellerRating edge cases")
    void testReviewOperationsAndRecalculateSellerRating() {
        assertThrows(NullPointerException.class, () -> service.addReview(null));
        service.updateReview(null);
        assertNull(service.findReview(null));
        assertNull(service.findReview(""));
        assertNull(service.findReview("GHOST_REV"));

        Review r = new Review();
        r.setId(null);
        service.addReview(r);
        service.updateReview(r);

        r.setId("REV_CUSTOM_1");
        r.setSellerUsername("diana");
        r.setBuyerUsername("alice");
        r.setRating(5);
        r.setComment("Great seller");
        service.addReview(r);
        assertNotNull(service.findReview("REV_CUSTOM_1"));

        r.setComment("Updated comment");
        service.updateReview(r);
        assertEquals("Updated comment", service.findReview("REV_CUSTOM_1").getComment());

        Review ghost = new Review();
        ghost.setId("REV_GHOST");
        service.updateReview(ghost);

        // recalculateSellerRating with null/blank
        service.recalculateSellerRating(null);
        service.recalculateSellerRating("   ");
        service.recalculateSellerRating("non_existent_seller");

        // Seller with all reviews hidden
        service.clearReviews();
        Review hiddenRev = new Review();
        hiddenRev.setId("REV_HIDDEN");
        hiddenRev.setSellerUsername("diana");
        hiddenRev.setBuyerUsername("alice");
        hiddenRev.setRating(1);
        hiddenRev.setHidden(true);
        service.addReview(hiddenRev);

        service.recalculateSellerRating("diana");
        Seller diana = (Seller) service.findUser("diana");
        assertEquals(0.0, diana.getAverageRating(), 0.001);
        assertEquals(0, diana.getRatingCount());

        // Add visible review
        Review visRev = new Review();
        visRev.setId("REV_VIS");
        visRev.setSellerUsername("diana");
        visRev.setBuyerUsername("bob");
        visRev.setRating(4);
        visRev.setHidden(false);
        service.addReview(visRev);

        service.recalculateSellerRating("diana");
        assertEquals(4.0, diana.getAverageRating(), 0.001);
        assertEquals(1, diana.getRatingCount());
    }

    @Test
    @DisplayName("getAdminProfile withAdminSession execution when admin logged in vs empty vs non-admin")
    void testWithAdminSessionBranches() {
        Admin admin = (Admin) service.findUser("admin");
        SessionManager.login(admin);

        // Already logged in as admin
        Admin res1 = service.getAdminProfile("admin");
        assertNotNull(res1);

        SessionManager.logout();

        // Not logged in -> temporary admin session
        Admin res2 = service.getAdminProfile("admin");
        assertNotNull(res2);
        assertNull(SessionManager.getCurrentUser(), "Session should be restored to null");

        // Logged in as Buyer -> should temporarily run with Admin and restore Buyer
        Buyer buyer = (Buyer) service.findUser("alice");
        SessionManager.login(buyer);
        Admin res3 = service.getAdminProfile("admin");
        assertNotNull(res3);
        assertEquals("alice", SessionManager.getCurrentUser().getUsername(), "Session should be restored to Buyer");

        Admin resGhost = service.getAdminProfile("ghost_admin_999");
        assertNull(resGhost);
    }

    @Test
    @DisplayName("Process auto-returns on empty deals or non-loan deals")
    void testProcessAutoReturnsEdge() {
        service.clearDeals();
        assertEquals(0, service.processAutoReturns());

        Deal saleDeal = new Deal();
        saleDeal.setId("D_SALE_1");
        saleDeal.setType(DealType.SALE);
        saleDeal.setStatus(DealStatus.ACTIVE);
        service.addDeal(saleDeal);

        assertEquals(0, service.processAutoReturns());
    }
}
