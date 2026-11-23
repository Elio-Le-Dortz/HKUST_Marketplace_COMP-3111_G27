package com.hkust.marketplace.database;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Comprehensive DatabaseManager Branch Coverage Tests")
public class ComprehensiveDatabaseManagerTest {

    private DatabaseManager db;

    @BeforeEach
    void setUp() {
        db = new DatabaseManager("jdbc:sqlite:file:memdb_comp_" + UUID.randomUUID() + "?mode=memory&cache=shared");
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.clearAllData();
            db.close();
        }
    }

    @Test
    @DisplayName("isInMemory, close, and ensureDirectoryExists with disk database")
    void testDirectoryAndMemoryModes(@TempDir Path tempDir) {
        assertTrue(db.isInMemory());

        // File-based db with nested directory creation
        File subDir = new File(tempDir.toFile(), "nested/subdir/db");
        String fileJdbc = "jdbc:sqlite:" + subDir.getAbsolutePath() + "/test.db";
        DatabaseManager diskDb = new DatabaseManager(fileJdbc);

        assertFalse(diskDb.isInMemory());
        assertTrue(subDir.exists(), "ensureDirectoryExists should create parent directories");
        assertNotNull(diskDb.getJdbcUrl());
        assertTrue(diskDb.isEmpty());

        diskDb.close();
    }

    @Test
    @DisplayName("Null input branches across all save and delete operations")
    void testNullInputsOnAllOperations() {
        // Users
        db.saveUser(null);
        User userWithNullId = new Buyer();
        userWithNullId.setUsername(null);
        db.saveUser(userWithNullId);
        db.deleteUser(null);
        db.deleteUser("");
        db.deleteUser("non_existent_user");

        // Listings
        db.saveListing(null);
        Listing listingWithNullId = new Listing();
        listingWithNullId.setId(null);
        db.saveListing(listingWithNullId);
        db.deleteListing(null);
        db.deleteListing("non_existent_listing");

        // Offers
        db.saveOffer(null);
        Offer offerWithNullId = new Offer();
        offerWithNullId.setId(null);
        db.saveOffer(offerWithNullId);
        db.deleteOffer(null);
        db.deleteOffer("non_existent_offer");

        // Deals
        db.saveDeal(null);
        Deal dealWithNullId = new Deal();
        dealWithNullId.setId(null);
        db.saveDeal(dealWithNullId);
        db.deleteDeal(null);
        db.deleteDeal("non_existent_deal");

        // Reviews
        db.saveReview(null);
        Review reviewWithNullId = new Review();
        reviewWithNullId.setId(null);
        db.saveReview(reviewWithNullId);

        // Notifications
        db.saveNotification(null);
        Notification notifWithNullId = new Notification();
        notifWithNullId.setId(null);
        db.saveNotification(notifWithNullId);
        db.deleteNotification(null);
        db.deleteNotificationsFor(null);
        db.deleteNotificationsFor("");

        // Reports
        db.saveReport(null);
        Report reportWithNullId = new Report();
        reportWithNullId.setId(null);
        db.saveReport(reportWithNullId);
        db.deleteReport(null);
        db.deleteReport("non_existent_report");

        // ChatThreads
        db.saveChatThread(null);
        ChatThread threadWithNullId = new ChatThread();
        threadWithNullId.setId(null);
        db.saveChatThread(threadWithNullId);
        db.deleteChatThread(null);
        db.deleteChatThread("non_existent_thread");

        assertTrue(db.isEmpty());
    }

    @Test
    @DisplayName("persistAll branches: nulls, empty collections, and full collections")
    void testPersistAllBranches() {
        // All null collections
        db.persistAll(null, null, null, null, null, null, null, null);
        assertTrue(db.isEmpty());

        // All empty collections
        db.persistAll(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        assertTrue(db.isEmpty());

        // Full collections with sample entities
        Buyer buyer = new Buyer("alice", "Alice Chan", "pass");
        Listing listing = new Listing();
        listing.setId("L1");
        listing.setTitle("Item");
        listing.setType(ListingType.SELL);
        listing.setStatus(ListingStatus.LIVE);
        listing.setSellerUsername("charlie");

        Offer offer = new Offer();
        offer.setId("O1");
        offer.setListingId("L1");
        offer.setType(OfferType.PRICE_OFFER);
        offer.setStatus(OfferStatus.PENDING);
        offer.setBuyerUsername("alice");
        offer.setSellerUsername("charlie");

        Deal deal = new Deal();
        deal.setId("D1");
        deal.setListingId("L1");
        deal.setType(DealType.SALE);
        deal.setStatus(DealStatus.ACTIVE);
        deal.setBuyerUsername("alice");
        deal.setSellerUsername("charlie");

        Review review = new Review();
        review.setId("REV1");
        review.setDealId("D1");
        review.setBuyerUsername("alice");
        review.setSellerUsername("charlie");
        review.setRating(5);

        Notification notification = new Notification();
        notification.setId("N1");
        notification.setUsername("alice");
        notification.setMessage("Notice");

        Report report = new Report();
        report.setId("R1");
        report.setListingId("L1");
        report.setBuyerUsername("alice");
        report.setReason("Suspicious");
        report.setStatus(ReportStatus.OPEN);

        ChatThread thread = new ChatThread();
        thread.setId("T1");
        thread.setListingId("L1");
        thread.setBuyerUsername("alice");
        thread.setSellerUsername("charlie");

        db.persistAll(
                List.of(buyer),
                List.of(listing),
                List.of(offer),
                List.of(deal),
                List.of(review),
                List.of(notification),
                List.of(report),
                List.of(thread)
        );

        assertFalse(db.isEmpty());
        assertEquals(1, db.loadAllUsers().size());
        assertEquals(1, db.loadAllListings().size());
        assertEquals(1, db.loadAllOffers().size());
        assertEquals(1, db.loadAllDeals().size());
        assertEquals(1, db.loadAllReviews().size());
        assertEquals(1, db.loadAllNotifications().size());
        assertEquals(1, db.loadAllReports().size());
        assertEquals(1, db.loadAllChatThreads().size());
    }
}
