package com.hkust.marketplace.database;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SQLite DatabaseManager Integration Tests")
class DatabaseManagerTest {

    private DatabaseManager db;

    @BeforeEach
    void setUp() {
        // Use unique in-memory database for isolation
        db = new DatabaseManager("jdbc:sqlite:file:memdb_" + UUID.randomUUID() + "?mode=memory&cache=shared");
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.clearAllData();
            db.close();
        }
    }

    @Test
    @DisplayName("Fresh database schema initialization and isEmpty check")
    void testInitSchemaAndIsEmpty() {
        assertTrue(db.isEmpty(), "Fresh database must be empty of users");
        assertNotNull(db.getJdbcUrl());
    }

    @Test
    @DisplayName("User persistence and retrieval across Buyer, Seller, and Admin")
    void testUserPersistence() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "hash123", "Hall 3");
        buyer.setAccountStatus(AccountStatus.ACTIVE);

        Seller seller = new Seller("charlie", "Charlie Lee", "hash456", "CSE Student");
        seller.setPreferredMeetups(List.of(MeetupLocation.LIBRARY, MeetupLocation.ATRIUM));
        seller.setAverageRating(4.8);
        seller.setRatingCount(10);

        Admin admin = new Admin("admin", "Marketplace Admin", "hash789");

        db.saveUser(buyer);
        db.saveUser(seller);
        db.saveUser(admin);

        assertFalse(db.isEmpty(), "Database should no longer be empty after saving users");

        Map<String, User> loaded = db.loadAllUsers();
        assertEquals(3, loaded.size());

        User loadedBuyer = loaded.get("alice");
        assertNotNull(loadedBuyer);
        assertTrue(loadedBuyer instanceof Buyer);
        assertEquals("Hall 3", ((Buyer) loadedBuyer).getResidence());
        assertEquals("Alice Chan", loadedBuyer.getFullName());
        assertEquals("hash123", loadedBuyer.getPasswordHash());
        assertEquals(AccountStatus.ACTIVE, loadedBuyer.getAccountStatus());

        User loadedSeller = loaded.get("charlie");
        assertNotNull(loadedSeller);
        assertTrue(loadedSeller instanceof Seller);
        assertEquals("CSE Student", ((Seller) loadedSeller).getBio());
        assertEquals(4.8, ((Seller) loadedSeller).getAverageRating(), 0.01);
        assertEquals(10, ((Seller) loadedSeller).getRatingCount());
        assertFalse(((Seller) loadedSeller).getPreferredMeetups().isEmpty());

        User loadedAdmin = loaded.get("admin");
        assertNotNull(loadedAdmin);
        assertTrue(loadedAdmin instanceof Admin);
        assertEquals(UserRole.ADMIN, loadedAdmin.getRole());

        // Test delete user
        db.deleteUser("alice");
        Map<String, User> remaining = db.loadAllUsers();
        assertEquals(2, remaining.size());
        assertFalse(remaining.containsKey("alice"));
    }

    @Test
    @DisplayName("Listing CRUD and photos persistence")
    void testListingPersistence() {
        // Create prerequisite seller user
        Seller seller = new Seller("charlie", "Charlie Lee", "pwdhash");
        db.saveUser(seller);

        Listing listing = new Listing();
        listing.setId("L100");
        listing.setTitle("Calculus Textbook");
        listing.setDescription("Stewart 8th Edition");
        listing.setCategory(Category.BOOKS);
        listing.setType(ListingType.SELL);
        listing.setStatus(ListingStatus.LIVE);
        listing.setCondition(Condition.LIKE_NEW);
        listing.setPrice(180.0);
        listing.setMeetupLocation(MeetupLocation.LIBRARY);
        listing.setSellerUsername("charlie");
        listing.setSellerFullName("Charlie Lee");
        listing.setViewCount(15);
        listing.setDeletedBySeller(false);
        listing.setPhotoPaths(List.of("uploads/photo1.jpg", "uploads/photo2.jpg"));

        db.saveListing(listing);

        Map<String, Listing> loaded = db.loadAllListings();
        assertEquals(1, loaded.size());
        Listing loadedL = loaded.get("L100");
        assertNotNull(loadedL);
        assertEquals("Calculus Textbook", loadedL.getTitle());
        assertEquals(Category.BOOKS, loadedL.getCategory());
        assertEquals(ListingType.SELL, loadedL.getType());
        assertEquals(ListingStatus.LIVE, loadedL.getStatus());
        assertEquals(Condition.LIKE_NEW, loadedL.getCondition());
        assertEquals(180.0, loadedL.getPrice(), 0.01);
        assertEquals("Library", loadedL.getMeetupLocation().getDisplayName());
        assertEquals(15, loadedL.getViewCount());
        assertEquals(2, loadedL.getPhotoPaths().size());
        assertTrue(loadedL.getPhotoPaths().contains("uploads/photo1.jpg"));

        // Update listing
        loadedL.setViewCount(16);
        loadedL.setDeletedBySeller(true);
        db.saveListing(loadedL);

        Map<String, Listing> updated = db.loadAllListings();
        assertEquals(16, updated.get("L100").getViewCount());
        assertTrue(updated.get("L100").isDeletedBySeller());

        // Delete listing
        db.deleteListing("L100");
        assertTrue(db.loadAllListings().isEmpty());
    }

    @Test
    @DisplayName("Offer persistence with counter-offers")
    void testOfferPersistence() {
        Seller seller = new Seller("charlie", "Charlie Lee", "pwd");
        Buyer buyer = new Buyer("alice", "Alice Chan", "pwd");
        db.saveUser(seller);
        db.saveUser(buyer);

        Listing listing = new Listing();
        listing.setId("L101");
        listing.setTitle("Laptop Charger");
        listing.setSellerUsername("charlie");
        db.saveListing(listing);

        Offer counter = new Offer();
        counter.setId("O701");
        counter.setType(OfferType.PRICE_OFFER);
        counter.setListingId("L101");
        counter.setListingTitle("Laptop Charger");
        counter.setBuyerUsername("alice");
        counter.setSellerUsername("charlie");
        counter.setPrice(220.0);
        counter.setStatus(OfferStatus.COUNTERED);

        Offer original = new Offer();
        original.setId("O700");
        original.setType(OfferType.PRICE_OFFER);
        original.setListingId("L101");
        original.setListingTitle("Laptop Charger");
        original.setBuyerUsername("alice");
        original.setSellerUsername("charlie");
        original.setPrice(200.0);
        original.setStatus(OfferStatus.COUNTERED);
        original.setCounterOffer(counter);

        db.saveOffer(original);

        List<Offer> offers = db.loadAllOffers();
        assertEquals(2, offers.size());

        Offer loadedOriginal = offers.stream().filter(o -> "O700".equals(o.getId())).findFirst().orElse(null);
        assertNotNull(loadedOriginal);
        assertNotNull(loadedOriginal.getCounterOffer());
        assertEquals("O701", loadedOriginal.getCounterOffer().getId());
        assertEquals(220.0, loadedOriginal.getCounterOffer().getPrice(), 0.01);

        // Delete single offer & clear offers
        db.deleteOffer("O701");
        assertEquals(1, db.loadAllOffers().size());
        db.clearOffers();
        assertTrue(db.loadAllOffers().isEmpty());
    }

    @Test
    @DisplayName("Deal persistence, updates, and deletion")
    void testDealPersistence() {
        Seller seller = new Seller("charlie", "Charlie Lee", "pwd");
        Buyer buyer = new Buyer("alice", "Alice Chan", "pwd");
        db.saveUser(seller);
        db.saveUser(buyer);

        Listing listing = new Listing();
        listing.setId("L102");
        listing.setTitle("Scientific Calculator");
        listing.setSellerUsername("charlie");
        db.saveListing(listing);

        Deal deal = new Deal();
        deal.setId("D501");
        deal.setListingId("L102");
        deal.setListingTitle("Scientific Calculator");
        deal.setBuyerUsername("alice");
        deal.setSellerUsername("charlie");
        deal.setType(DealType.LOAN);
        deal.setStatus(DealStatus.ACTIVE);
        deal.setStartDate(LocalDateTime.now().minusDays(1));
        deal.setDueDate(LocalDateTime.now().plusDays(7));
        deal.setRated(false);

        db.saveDeal(deal);

        List<Deal> deals = db.loadAllDeals();
        assertEquals(1, deals.size());
        Deal loaded = deals.get(0);
        assertEquals("D501", loaded.getId());
        assertEquals(DealType.LOAN, loaded.getType());
        assertEquals(DealStatus.ACTIVE, loaded.getStatus());
        assertFalse(loaded.isRated());
        assertNotNull(loaded.getDueDate());

        // Update deal to COMPLETED and rated
        loaded.setStatus(DealStatus.COMPLETED);
        loaded.setRated(true);
        loaded.setClosedDate(LocalDateTime.now());
        db.saveDeal(loaded);

        Deal updated = db.loadAllDeals().get(0);
        assertEquals(DealStatus.COMPLETED, updated.getStatus());
        assertTrue(updated.isRated());
        assertNotNull(updated.getClosedDate());

        // Delete deal & clear deals
        db.deleteDeal("D501");
        assertTrue(db.loadAllDeals().isEmpty());
    }

    @Test
    @DisplayName("Review persistence with flags and replies")
    void testReviewPersistence() {
        Seller seller = new Seller("charlie", "Charlie Lee", "pwd");
        Buyer buyer = new Buyer("bob", "Bob Wong", "pwd");
        db.saveUser(seller);
        db.saveUser(buyer);

        Review review = new Review();
        review.setId("R101");
        review.setSellerUsername("charlie");
        review.setBuyerUsername("bob");
        review.setListingId("L103");
        review.setListingTitle("Chemistry Lab Coat");
        review.setDealId("D502");
        review.setRating(5);
        review.setComment("Great seller, on time meetup!");
        review.setReply("Thank you Bob!");
        review.setFlagged(true);
        review.setFlagReason("Suspicious meetup discussion");
        review.setHidden(false);

        db.saveReview(review);

        List<Review> reviews = db.loadAllReviews();
        assertEquals(1, reviews.size());
        Review loaded = reviews.get(0);
        assertEquals("R101", loaded.getId());
        assertEquals("charlie", loaded.getSellerUsername());
        assertEquals("bob", loaded.getBuyerUsername());
        assertEquals("L103", loaded.getListingId());
        assertEquals(5, loaded.getRating());
        assertEquals("Thank you Bob!", loaded.getReply());
        assertTrue(loaded.isFlagged());
        assertEquals("Suspicious meetup discussion", loaded.getFlagReason());
        assertFalse(loaded.isHidden());

        db.clearReviews();
        assertTrue(db.loadAllReviews().isEmpty());
    }

    @Test
    @DisplayName("Notification persistence and bulk deletion by user")
    void testNotificationPersistence() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "pwd");
        db.saveUser(buyer);

        Notification n1 = new Notification("alice", "Offer Accepted", "Your offer was accepted", "Offers", "O700", "OFFER");
        n1.setId("N1");
        n1.setRead(false);

        Notification n2 = new Notification("alice", "Reminder", "Due date approaching", "Reminders", "D501", "DEAL");
        n2.setId("N2");
        n2.setRead(true);

        db.saveNotification(n1);
        db.saveNotification(n2);

        List<Notification> notifs = db.loadAllNotifications();
        assertEquals(2, notifs.size());

        Notification loadedN1 = notifs.stream().filter(n -> "N1".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(loadedN1);
        assertEquals("O700", loadedN1.getRelatedItemId());
        assertEquals("OFFER", loadedN1.getRelatedItemType());
        assertFalse(loadedN1.isRead());

        // Delete single notification
        db.deleteNotification("N1");
        assertEquals(1, db.loadAllNotifications().size());

        // Delete by user
        int deleted = db.deleteNotificationsFor("alice");
        assertEquals(1, deleted);
        assertTrue(db.loadAllNotifications().isEmpty());
    }

    @Test
    @DisplayName("Report persistence and status update")
    void testReportPersistence() {
        Buyer buyer = new Buyer("alice", "Alice Chan", "pwd");
        db.saveUser(buyer);

        Listing listing = new Listing();
        listing.setId("L104");
        listing.setTitle("Reported Item");
        listing.setSellerUsername("alice");
        db.saveListing(listing);

        Report report = new Report("REP301", "L104", "Reported Item", "alice", "Inappropriate description");
        report.setStatus(ReportStatus.OPEN);

        db.saveReport(report);

        List<Report> reports = db.loadAllReports();
        assertEquals(1, reports.size());
        Report loaded = reports.get(0);
        assertEquals("REP301", loaded.getId());
        assertEquals(ReportStatus.OPEN, loaded.getStatus());

        loaded.setStatus(ReportStatus.RESOLVED);
        loaded.setHandledBy("admin");
        LocalDateTime resolvedAt = LocalDateTime.now().withNano(0);
        loaded.setResolvedDate(resolvedAt);
        db.saveReport(loaded);

        Report updated = db.loadAllReports().get(0);
        assertEquals(ReportStatus.RESOLVED, updated.getStatus());
        assertEquals("admin", updated.getHandledBy());
        assertEquals(resolvedAt, updated.getResolvedDate());

        db.deleteReport("REP301");
        assertTrue(db.loadAllReports().isEmpty());
    }

    @Test
    @DisplayName("ChatThread and Message persistence with formal offers")
    void testChatThreadPersistence() {
        Seller seller = new Seller("charlie", "Charlie Lee", "pwd");
        Buyer buyer = new Buyer("alice", "Alice Chan", "pwd");
        db.saveUser(seller);
        db.saveUser(buyer);

        Listing listing = new Listing();
        listing.setId("L105");
        listing.setTitle("Textbook");
        listing.setSellerUsername("charlie");
        db.saveListing(listing);

        ChatThread thread = new ChatThread("T10", "L105", "alice", "charlie");
        thread.setListingTitle("Textbook");

        Message m1 = new Message("M1", "alice", "Hello Charlie");
        Message m2 = new Message("M2", "alice", "[FORMAL OFFER] HK$ 150", LocalDateTime.now(), "O705");
        thread.addMessage(m1);
        thread.addMessage(m2);

        db.saveChatThread(thread);

        List<ChatThread> threads = db.loadAllChatThreads();
        assertEquals(1, threads.size());
        ChatThread loaded = threads.get(0);
        assertEquals("T10", loaded.getId());
        assertEquals(2, loaded.getMessages().size());

        Message loadedM2 = loaded.getMessages().get(1);
        assertEquals("M2", loadedM2.getId());
        assertEquals("O705", loadedM2.getOfferId());
        assertTrue(loadedM2.isFormalOffer());

        db.deleteChatThread("T10");
        assertTrue(db.loadAllChatThreads().isEmpty());
    }

    @Test
    @DisplayName("Batch persistAll and clearAllData transaction execution")
    void testBatchPersistAllAndClearAllData() {
        Buyer buyer = new Buyer("alice", "Alice", "pwd");
        Seller seller = new Seller("bob", "Bob", "pwd");
        Listing listing = new Listing();
        listing.setId("L200");
        listing.setTitle("Batch Item");
        listing.setSellerUsername("bob");

        Deal deal = new Deal();
        deal.setId("D600");
        deal.setListingId("L200");
        deal.setBuyerUsername("alice");
        deal.setSellerUsername("bob");

        db.persistAll(
                List.of(buyer, seller),
                List.of(listing),
                Collections.emptyList(),
                List.of(deal),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertFalse(db.isEmpty());
        assertEquals(2, db.loadAllUsers().size());
        assertEquals(1, db.loadAllListings().size());
        assertEquals(1, db.loadAllDeals().size());

        db.clearAllData();
        assertTrue(db.isEmpty());
        assertTrue(db.loadAllListings().isEmpty());
        assertTrue(db.loadAllDeals().isEmpty());
    }

    @Test
    @DisplayName("Disk persistence: data persists across manager instantiation with temp file")
    void testDiskPersistenceAcrossReopen() throws Exception {
        File tempDb = File.createTempFile("marketplace_test_", ".db");
        tempDb.deleteOnExit();

        String diskUrl = "jdbc:sqlite:" + tempDb.getAbsolutePath();
        DatabaseManager db1 = new DatabaseManager(diskUrl);

        Buyer buyer = new Buyer("diskbuyer", "Disk Buyer", "pwd");
        Seller seller = new Seller("diskseller", "Disk Seller", "pwd");
        db1.saveUser(buyer);
        db1.saveUser(seller);

        Listing listing = new Listing();
        listing.setId("L300");
        listing.setTitle("Persistent Item");
        listing.setSellerUsername("diskseller");
        db1.saveListing(listing);

        // Instantiate second manager on the exact same SQLite file (simulating app restart)
        DatabaseManager db2 = new DatabaseManager(diskUrl);
        assertFalse(db2.isEmpty(), "Second DatabaseManager should detect non-empty disk DB");

        Map<String, User> users = db2.loadAllUsers();
        assertEquals(2, users.size());
        assertTrue(users.containsKey("diskbuyer"));
        assertTrue(users.containsKey("diskseller"));

        Map<String, Listing> listings = db2.loadAllListings();
        assertEquals(1, listings.size());
        assertEquals("Persistent Item", listings.get("L300").getTitle());

        tempDb.delete();
    }
}
