package com.hkust.marketplace.utils;

import com.hkust.marketplace.database.DatabaseManager;
import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService SQLite Write-Through Persistence & Reload Tests")
class DummyDataServicePersistenceTest {

    private DatabaseManager db;
    private DummyDataService service;

    @BeforeEach
    void setUp() {
        // Isolated in-memory database
        db = new DatabaseManager("jdbc:sqlite:file:mem_dds_" + UUID.randomUUID() + "?mode=memory&cache=shared");
        service = new DummyDataService(db);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.clearAllData();
            db.close();
        }
    }

    @Test
    @DisplayName("Auto-seeding initializes and persists seed data when DB is empty")
    void testAutoSeedingPersistsToDatabase() {
        assertNotNull(service.getDatabaseManager());
        assertTrue(service.isPersistenceEnabled());

        // Check in-memory state
        assertTrue(service.usernameExists("admin"));
        assertTrue(service.usernameExists("alice"));
        assertTrue(service.usernameExists("charlie"));

        // Check SQLite state
        Map<String, User> dbUsers = db.loadAllUsers();
        assertEquals(5, dbUsers.size(), "All 5 seeded accounts (admin, alice, bob, charlie, diana) must be in SQLite");
        assertTrue(dbUsers.containsKey("admin"));
        assertTrue(dbUsers.containsKey("alice"));
        assertTrue(dbUsers.containsKey("charlie"));

        Map<String, Listing> dbListings = db.loadAllListings();
        assertFalse(dbListings.isEmpty(), "Seeded listings must be persisted to SQLite");

        List<Deal> dbDeals = db.loadAllDeals();
        assertFalse(dbDeals.isEmpty(), "Seeded deals must be persisted to SQLite");

        List<Notification> dbNotifs = db.loadAllNotifications();
        assertFalse(dbNotifs.isEmpty(), "Seeded notifications must be persisted to SQLite");
    }

    @Test
    @DisplayName("User registration and update writes through to SQLite")
    void testUserWriteThrough() {
        Buyer newBuyer = new Buyer("david", "David Ho", "hashedpassword", "Hall 1");
        service.registerUser(newBuyer);

        Map<String, User> dbUsers = db.loadAllUsers();
        assertTrue(dbUsers.containsKey("david"), "Newly registered user must be in SQLite");
        assertEquals("David Ho", dbUsers.get("david").getFullName());

        // Update user
        newBuyer.setFullName("David K. Ho");
        service.updateUser(newBuyer);

        Map<String, User> updatedUsers = db.loadAllUsers();
        assertEquals("David K. Ho", updatedUsers.get("david").getFullName());
    }

    @Test
    @DisplayName("Listing addition, update, and removal writes through to SQLite")
    void testListingWriteThrough() {
        Listing listing = new Listing();
        listing.setTitle("Graph Theory Notes");
        listing.setDescription("COMP 2711 comprehensive notes");
        listing.setCategory(Category.BOOKS);
        listing.setType(ListingType.SELL);
        listing.setStatus(ListingStatus.LIVE);
        listing.setPrice(45.0);
        listing.setMeetupLocation(MeetupLocation.LIBRARY);
        listing.setSellerUsername("charlie");
        listing.setSellerFullName("Charlie Lee");

        service.addListing(listing);
        String id = listing.getId();
        assertNotNull(id);

        Map<String, Listing> dbListings = db.loadAllListings();
        assertTrue(dbListings.containsKey(id));
        assertEquals("Graph Theory Notes", dbListings.get(id).getTitle());

        // Update listing
        listing.setPrice(40.0);
        service.updateListing(listing);
        assertEquals(40.0, db.loadAllListings().get(id).getPrice(), 0.01);

        // Remove listing
        service.removeListing(id);
        assertFalse(db.loadAllListings().containsKey(id));
    }

    @Test
    @DisplayName("Offer, Deal, and Claim write through to SQLite")
    void testOfferAndDealWriteThrough() {
        Offer offer = new Offer();
        offer.setType(OfferType.PRICE_OFFER);
        offer.setListingId("L100");
        offer.setListingTitle("Calculus Textbook");
        offer.setBuyerUsername("bob");
        offer.setSellerUsername("charlie");
        offer.setPrice(150.0);
        service.addOffer(offer);

        List<Offer> dbOffers = db.loadAllOffers();
        assertTrue(dbOffers.stream().anyMatch(o -> offer.getId().equals(o.getId())));

        // Deal addition
        Deal deal = new Deal();
        deal.setListingId("L100");
        deal.setListingTitle("Calculus Textbook");
        deal.setBuyerUsername("bob");
        deal.setSellerUsername("charlie");
        deal.setType(DealType.SALE);
        deal.setStatus(DealStatus.ACTIVE);
        service.addDeal(deal);

        List<Deal> dbDeals = db.loadAllDeals();
        assertTrue(dbDeals.stream().anyMatch(d -> deal.getId().equals(d.getId())));

        // Remove deal
        service.removeDeal(deal.getId());
        assertFalse(db.loadAllDeals().stream().anyMatch(d -> deal.getId().equals(d.getId())));
    }

    @Test
    @DisplayName("Review and rating recalculation write through to SQLite")
    void testReviewWriteThrough() {
        Review review = new Review();
        review.setSellerUsername("charlie");
        review.setBuyerUsername("bob");
        review.setListingTitle("Calculus Textbook");
        review.setRating(5);
        review.setComment("Fast response and friendly seller!");
        service.addReview(review);

        List<Review> dbReviews = db.loadAllReviews();
        assertTrue(dbReviews.stream().anyMatch(r -> review.getId().equals(r.getId())));

        // Check seller rating in SQLite
        User seller = db.loadAllUsers().get("charlie");
        assertNotNull(seller);
        assertTrue(seller.getAverageRating() > 0);
    }

    @Test
    @DisplayName("ChatThread, Notification, and Report write through to SQLite")
    void testChatNotificationAndReportWriteThrough() {
        ChatThread thread = new ChatThread();
        thread.setListingId("L100");
        thread.setListingTitle("Calculus Textbook");
        thread.setBuyerUsername("alice");
        thread.setSellerUsername("charlie");
        thread.addMessage(new Message("alice", "Is this still available?"));
        service.addChatThread(thread);

        List<ChatThread> dbThreads = db.loadAllChatThreads();
        assertTrue(dbThreads.stream().anyMatch(t -> thread.getId().equals(t.getId())));

        // Notification
        Notification notif = new Notification("alice", "Test Title", "Test Message", "Announcements");
        service.addNotification(notif);
        assertTrue(db.loadAllNotifications().stream().anyMatch(n -> notif.getId().equals(n.getId())));

        service.removeNotification(notif.getId());
        assertFalse(db.loadAllNotifications().stream().anyMatch(n -> notif.getId().equals(n.getId())));

        // Report
        Report report = new Report();
        report.setListingId("L100");
        report.setListingTitle("Calculus Textbook");
        report.setBuyerUsername("bob");
        report.setReason("Incorrect edition listed");
        service.addReport(report);
        assertTrue(db.loadAllReports().stream().anyMatch(r -> report.getId().equals(r.getId())));
    }

    @Test
    @DisplayName("Disk persistence: reload state across DummyDataService instances with SQLite file")
    void testReloadFromDiskAcrossAppRestarts() throws Exception {
        File tempDbFile = File.createTempFile("marketplacedemo_", ".db");
        tempDbFile.deleteOnExit();

        String diskUrl = "jdbc:sqlite:" + tempDbFile.getAbsolutePath();

        // 1. Initial run: seeds default data and performs actions
        DatabaseManager dbInstance1 = new DatabaseManager(diskUrl);
        DummyDataService service1 = new DummyDataService(dbInstance1);

        Buyer customBuyer = new Buyer("emily", "Emily Davis", "hashpwd", "Hall 4");
        service1.registerUser(customBuyer);

        Listing customListing = new Listing();
        customListing.setTitle("Physics Lab Equipment");
        customListing.setCategory(Category.OTHER);
        customListing.setType(ListingType.SELL);
        customListing.setStatus(ListingStatus.LIVE);
        customListing.setPrice(120.0);
        customListing.setSellerUsername("charlie");
        service1.addListing(customListing);
        String customListingId = customListing.getId();

        // 2. Simulate application restart: instantiate a new DatabaseManager and DummyDataService
        DatabaseManager dbInstance2 = new DatabaseManager(diskUrl);
        assertFalse(dbInstance2.isEmpty(), "Database must not be empty on restart");

        DummyDataService service2 = new DummyDataService(dbInstance2);

        // Verify loaded state in service2
        assertTrue(service2.usernameExists("emily"), "Custom buyer 'emily' must persist across restarts");
        assertEquals("Emily Davis", service2.findUser("emily").getFullName());
        assertTrue(service2.usernameExists("admin"), "Admin must persist across restarts");

        Listing reloadedListing = service2.findListing(customListingId);
        assertNotNull(reloadedListing, "Custom listing must persist across restarts");
        assertEquals("Physics Lab Equipment", reloadedListing.getTitle());
        assertEquals(120.0, reloadedListing.getPrice(), 0.01);

        // Verify syncCounters: new listing ID should be higher than customListingId
        Listing anotherListing = new Listing();
        anotherListing.setTitle("New Item After Restart");
        anotherListing.setSellerUsername("charlie");
        service2.addListing(anotherListing);
        assertNotEquals(customListingId, anotherListing.getId(), "IDs must not collide across restarts");

        tempDbFile.delete();
    }

    @Test
    @DisplayName("Legacy plaintext admin password in database is automatically migrated to PBKDF2 hash on load")
    void testLegacyPlaintextAdminPasswordAutoMigratesOnLoad() {
        // Force plaintext password into SQLite as if from an older database seed
        Admin legacyAdmin = new Admin("admin", "HKUST Marketplace Admin", "admin123");
        db.saveUser(legacyAdmin);
        assertEquals("admin123", db.loadAllUsers().get("admin").getPasswordHash());

        // Restart service, which calls loadFromDatabase()
        DummyDataService restartedService = new DummyDataService(db);
        User migratedAdmin = restartedService.findUser("admin");
        assertNotNull(migratedAdmin);
        assertNotEquals("admin123", migratedAdmin.getPasswordHash());
        assertTrue(migratedAdmin.getPasswordHash().startsWith("pbkdf2_sha256$"));
        assertTrue(PasswordUtil.verifyPassword("admin123", migratedAdmin.getPasswordHash()));

        // Check it was also persisted back to SQLite
        assertEquals(migratedAdmin.getPasswordHash(), db.loadAllUsers().get("admin").getPasswordHash());

        // Verify AdminLoginService authenticates successfully
        com.hkust.marketplace.services.AdminLoginService loginService =
                new com.hkust.marketplace.services.AdminLoginService(restartedService);
        LoginResult result = loginService.authenticate("admin", "admin123");
        assertTrue(result.isSuccess(), "Admin login must succeed with 'admin123' after migration");
    }
}
