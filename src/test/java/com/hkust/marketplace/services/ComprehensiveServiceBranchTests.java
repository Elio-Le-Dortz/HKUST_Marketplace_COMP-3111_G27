package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Comprehensive Services Branch Coverage Tests")
public class ComprehensiveServiceBranchTests {

    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        SessionManager.logout();
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        dataService.resetData();
    }

    @Nested
    @DisplayName("AdminListingService Take-Down Branch Tests")
    class AdminListingServiceBranchTests {

        private AdminListingService service;

        @BeforeEach
        void initAdmin() {
            Admin admin = (Admin) dataService.findUser("admin");
            SessionManager.login(admin);
            service = dataService.getAdminListingService();
        }

        @Test
        @DisplayName("takeDownListing with active offer cascade and already removed listing")
        void testTakeDownListingBranches() {
            // Non-existent listing
            AdminListingOperationResult rGhost = service.takeDownListing("GHOST_L", "Inappropriate", "admin");
            assertEquals(AdminListingOperationStatus.LISTING_NOT_FOUND, rGhost.getStatus());

            // Blank reason
            AdminListingOperationResult rNoReason = service.takeDownListing("L100", "   ", "admin");
            assertEquals(AdminListingOperationStatus.EMPTY_REASON, rNoReason.getStatus());

            // Already removed listing (L999 is seeded REMOVED)
            AdminListingOperationResult rAlreadyRemoved = service.takeDownListing("L999", "Double remove", "admin");
            assertEquals(AdminListingOperationStatus.LISTING_NOT_LIVE, rAlreadyRemoved.getStatus());

            // Take down fresh live listing with active pending offer
            Listing freshLive = new Listing();
            freshLive.setId("L_FRESH_LIVE");
            freshLive.setTitle("Fresh Book");
            freshLive.setType(ListingType.SELL);
            freshLive.setStatus(ListingStatus.LIVE);
            freshLive.setSellerUsername("charlie");
            dataService.addListing(freshLive);

            Offer pendingOffer = new Offer();
            pendingOffer.setId("O_PENDING_1");
            pendingOffer.setListingId("L_FRESH_LIVE");
            pendingOffer.setStatus(OfferStatus.PENDING);
            pendingOffer.setBuyerUsername("alice");
            pendingOffer.setSellerUsername("charlie");
            dataService.addOffer(pendingOffer);

            AdminListingOperationResult rSuccess = service.takeDownListing("L_FRESH_LIVE", "Violates campus policy", "admin");
            assertTrue(rSuccess.isSuccess());
            assertEquals(ListingStatus.REMOVED, dataService.findListing("L_FRESH_LIVE").getStatus());
            assertEquals(OfferStatus.DECLINED, dataService.findOffer("O_PENDING_1").getStatus());
        }
    }

    @Nested
    @DisplayName("AdminNotificationService Detail Retrieval Branch Tests")
    class AdminNotificationServiceBranchTests {

        private AdminNotificationService notifService;

        @BeforeEach
        void initService() {
            Admin admin = (Admin) dataService.findUser("admin");
            SessionManager.login(admin);
            notifService = dataService.getAdminNotificationService();
        }

        @Test
        @DisplayName("getNotificationDetail across notification types and itemNoLongerAvailable")
        void testNotificationDetailBranches() {
            // Null & blank ID
            AdminNotificationDetailResult rNull = notifService.getNotificationDetail(null, "admin");
            assertEquals(AdminNotificationDetailStatus.NOTIFICATION_NOT_FOUND, rNull.getStatus());

            AdminNotificationDetailResult rGhost = notifService.getNotificationDetail("NOTIF_GHOST", "admin");
            assertEquals(AdminNotificationDetailStatus.NOTIFICATION_NOT_FOUND, rGhost.getStatus());

            // Notification for report where report is missing
            Notification missingReportNotif = new Notification(
                    "admin", "Report", "Report filed", "Reports", "GHOST_REPORT_ID", "REPORT"
            );
            missingReportNotif.setId("NOTIF_MISSING_REP");
            dataService.addNotification(missingReportNotif);

            AdminNotificationDetailResult rMissingRep = notifService.getNotificationDetail("NOTIF_MISSING_REP", "admin");
            assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, rMissingRep.getStatus());

            // Notification for listing submission where listing is missing
            Notification missingListingNotif = new Notification(
                    "admin", "Listing Submitted", "New listing", "Listings", "GHOST_LISTING_ID", "LISTING"
            );
            missingListingNotif.setId("NOTIF_MISSING_LIST");
            dataService.addNotification(missingListingNotif);

            AdminNotificationDetailResult rMissingList = notifService.getNotificationDetail("NOTIF_MISSING_LIST", "admin");
            assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, rMissingList.getStatus());

            // Notification for review flagged where review is missing
            Notification missingReviewNotif = new Notification(
                    "admin", "Review Flagged", "Flagged review", "Reviews", "GHOST_REVIEW_ID", "REVIEW"
            );
            missingReviewNotif.setId("NOTIF_MISSING_REV");
            dataService.addNotification(missingReviewNotif);

            AdminNotificationDetailResult rMissingRev = notifService.getNotificationDetail("NOTIF_MISSING_REV", "admin");
            assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, rMissingRev.getStatus());

            // Notification for user audit where user is missing
            Notification missingUserNotif = new Notification(
                    "admin", "User Audit", "User account modified", "User Accounts", "ghost_user", "USER"
            );
            missingUserNotif.setId("NOTIF_MISSING_USER");
            dataService.addNotification(missingUserNotif);

            AdminNotificationDetailResult rMissingUser = notifService.getNotificationDetail("NOTIF_MISSING_USER", "admin");
            assertEquals(AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE, rMissingUser.getStatus());

            // Unknown category / type
            Notification unknownNotif = new Notification(
                    "admin", "System Notice", "General maintenance", "System", "GEN1", "UNKNOWN"
            );
            unknownNotif.setId("NOTIF_UNKNOWN");
            dataService.addNotification(unknownNotif);

            AdminNotificationDetailResult rUnknown = notifService.getNotificationDetail("NOTIF_UNKNOWN", "admin");
            assertEquals(AdminNotificationDetailStatus.ANNOUNCEMENT_ONLY, rUnknown.getStatus());
        }

        @Test
        @DisplayName("resolveReport and flagReview error branches")
        void testResolveReportAndFlagReview() {
            // resolveReport non-existent
            AdminReportResolutionResult rReportMissing = notifService.resolveReport("GHOST_REPORT", "Resolved", "admin");
            assertFalse(rReportMissing.isSuccess());

            // flagReview non-existent
            assertFalse(notifService.flagReview("GHOST_REV", "diana", "Inappropriate"));
            assertFalse(notifService.flagReview(null, "diana", "Inappropriate"));
            assertFalse(notifService.flagReview("GHOST_REV", null, "Inappropriate"));
            assertFalse(notifService.flagReview("GHOST_REV", "diana", "   "));
        }
    }

    @Nested
    @DisplayName("DealService Error & Capability Branch Tests")
    class DealServiceBranchTests {

        private DealService dealService;
        private Buyer alice;

        @BeforeEach
        void initBuyer() {
            alice = (Buyer) dataService.findUser("alice");
            SessionManager.login(alice);
            dealService = dataService.getDealService();
        }

        @Test
        @DisplayName("markDealComplete: active loans rejected; non-active deals rejected")
        void testMarkDealCompleteBranches() {
            // Deal not found
            DealOperationResult rGhost = dealService.markDealComplete("GHOST_DEAL", "alice");
            assertEquals(DealOperationStatus.DEAL_NOT_FOUND, rGhost.getStatus());

            // Loan deal cannot be marked complete
            Deal loanDeal = new Deal();
            loanDeal.setId("D_LOAN_TEST");
            loanDeal.setType(DealType.LOAN);
            loanDeal.setStatus(DealStatus.ACTIVE);
            loanDeal.setBuyerUsername("alice");
            loanDeal.setSellerUsername("diana");
            dataService.addDeal(loanDeal);

            DealOperationResult rLoan = dealService.markDealComplete("D_LOAN_TEST", "alice");
            assertEquals(DealOperationStatus.DEAL_IS_LOAN, rLoan.getStatus());

            // Sale deal already completed
            Deal saleCompleted = new Deal();
            saleCompleted.setId("D_SALE_COMP");
            saleCompleted.setType(DealType.SALE);
            saleCompleted.setStatus(DealStatus.COMPLETED);
            saleCompleted.setBuyerUsername("alice");
            saleCompleted.setSellerUsername("charlie");
            dataService.addDeal(saleCompleted);

            DealOperationResult rNotActive = dealService.markDealComplete("D_SALE_COMP", "alice");
            assertEquals(DealOperationStatus.DEAL_NOT_ACTIVE, rNotActive.getStatus());
        }

        @Test
        @DisplayName("selfReturnItem: non-loan deals rejected; non-active deals rejected")
        void testSelfReturnBranches() {
            // Non-existent deal
            DealOperationResult rGhost = dealService.selfReturnItem("GHOST_DEAL", "alice");
            assertEquals(DealOperationStatus.DEAL_NOT_FOUND, rGhost.getStatus());

            // Sale deal cannot be self-returned
            Deal saleDeal = new Deal();
            saleDeal.setId("D_SALE_SR");
            saleDeal.setType(DealType.SALE);
            saleDeal.setStatus(DealStatus.ACTIVE);
            saleDeal.setBuyerUsername("alice");
            dataService.addDeal(saleDeal);

            DealOperationResult rSale = dealService.selfReturnItem("D_SALE_SR", "alice");
            assertEquals(DealOperationStatus.DEAL_NOT_BORROW, rSale.getStatus());

            // Already returned loan
            Deal retLoan = new Deal();
            retLoan.setId("D_LOAN_RET");
            retLoan.setType(DealType.LOAN);
            retLoan.setStatus(DealStatus.RETURNED);
            retLoan.setBuyerUsername("alice");
            dataService.addDeal(retLoan);

            DealOperationResult rAlreadyRet = dealService.selfReturnItem("D_LOAN_RET", "alice");
            assertEquals(DealOperationStatus.DEAL_NOT_ACTIVE, rAlreadyRet.getStatus());
        }

        @Test
        @DisplayName("Counter-offer accept and decline error branches")
        void testCounterOfferBranches() {
            // Non-existent offer
            DealOperationResult rAccGhost = dealService.acceptCounterOffer("GHOST_OFFER", "alice");
            assertEquals(DealOperationStatus.OFFER_NOT_FOUND, rAccGhost.getStatus());

            DealOperationResult rDecGhost = dealService.declineCounterOffer("GHOST_OFFER", "alice");
            assertEquals(DealOperationStatus.OFFER_NOT_FOUND, rDecGhost.getStatus());

            // Pending offer is not countered
            Offer pending = new Offer();
            pending.setId("O_PEND_ERR");
            pending.setStatus(OfferStatus.PENDING);
            pending.setBuyerUsername("alice");
            dataService.addOffer(pending);

            DealOperationResult rAccNotCount = dealService.acceptCounterOffer("O_PEND_ERR", "alice");
            assertEquals(DealOperationStatus.COUNTER_NOT_FOUND, rAccNotCount.getStatus());

            DealOperationResult rDecNotCount = dealService.declineCounterOffer("O_PEND_ERR", "alice");
            assertEquals(DealOperationStatus.COUNTER_NOT_FOUND, rDecNotCount.getStatus());
        }

        @Test
        @DisplayName("submitRating: invalid rating bounds and unrated statuses")
        void testSubmitRatingBranches() {
            // Deal not found
            DealOperationResult rGhost = dealService.submitRating("GHOST_DEAL", "alice", 5, "Great");
            assertEquals(DealOperationStatus.DEAL_NOT_FOUND, rGhost.getStatus());

            // Active deal cannot be rated yet
            Deal activeDeal = new Deal();
            activeDeal.setId("D_ACT_RATE");
            activeDeal.setStatus(DealStatus.ACTIVE);
            activeDeal.setBuyerUsername("alice");
            dataService.addDeal(activeDeal);

            DealOperationResult rActive = dealService.submitRating("D_ACT_RATE", "alice", 5, "Great");
            assertEquals(DealOperationStatus.DEAL_NOT_ACTIVE, rActive.getStatus());

            // Completed deal with rating outside 1..5
            Deal compDeal = new Deal();
            compDeal.setId("D_COMP_RATE");
            compDeal.setStatus(DealStatus.COMPLETED);
            compDeal.setBuyerUsername("alice");
            dataService.addDeal(compDeal);

            DealOperationResult rZero = dealService.submitRating("D_COMP_RATE", "alice", 0, "Bad");
            assertEquals(DealOperationStatus.INVALID_RATING, rZero.getStatus());

            DealOperationResult rSix = dealService.submitRating("D_COMP_RATE", "alice", 6, "High");
            assertEquals(DealOperationStatus.INVALID_RATING, rSix.getStatus());
        }
    }

    @Nested
    @DisplayName("ChatService Branch Tests")
    class ChatServiceBranchTests {

        private ChatService chatService;
        private Listing liveSellListing;
        private Listing liveLendListing;

        @BeforeEach
        void initService() {
            chatService = dataService.getChatService();
            SessionManager.login(dataService.findUser("alice"));
            liveSellListing = dataService.getAllListings().stream()
                    .filter(l -> l.getStatus() == ListingStatus.LIVE && l.getType() == ListingType.SELL)
                    .findFirst().orElseThrow();
            liveLendListing = dataService.getAllListings().stream()
                    .filter(l -> l.getStatus() == ListingStatus.LIVE && l.getType() == ListingType.LEND)
                    .findFirst().orElseThrow();
        }

        @Test
        @DisplayName("getOrCreateThread: validation, self-chat prevention, and creation")
        void testGetOrCreateThread() {
            assertNull(chatService.getOrCreateThread(null, "alice"));
            assertNull(chatService.getOrCreateThread("  ", "alice"));
            assertNull(chatService.getOrCreateThread(liveSellListing.getId(), null));
            assertNull(chatService.getOrCreateThread(liveSellListing.getId(), "  "));
            assertNull(chatService.getOrCreateThread("NON_EXISTENT_LISTING", "alice"));

            // Self-chat prevention on live sell listing
            assertNull(chatService.getOrCreateThread(liveSellListing.getId(), liveSellListing.getSellerUsername()));

            ChatThread thread = chatService.getOrCreateThread(liveSellListing.getId(), "alice");
            assertNotNull(thread);
            assertEquals(liveSellListing.getId(), thread.getListingId());
            assertEquals("alice", thread.getBuyerUsername());
        }

        @Test
        @DisplayName("getThreadById & getThreadsForUser: privacy checks and comparator sorting")
        void testGetThreadByIdAndUserThreads() {
            assertNull(chatService.getThreadById(null, "alice"));
            assertNull(chatService.getThreadById("  ", "alice"));
            assertNull(chatService.getThreadById("GHOST_THREAD", "alice"));

            ChatThread thread = chatService.getOrCreateThread(liveSellListing.getId(), "alice");
            assertNotNull(thread);

            // Unauthorized user
            assertNull(chatService.getThreadById(thread.getId(), "diana"));

            // Authorized users
            assertNotNull(chatService.getThreadById(thread.getId(), "alice"));
            assertNotNull(chatService.getThreadById(thread.getId(), liveSellListing.getSellerUsername()));
            assertNotNull(chatService.getThreadById(thread.getId(), null));

            // getThreadsForUser
            assertTrue(chatService.getThreadsForUser(null).isEmpty());
            assertTrue(chatService.getThreadsForUser("  ").isEmpty());

            List<ChatThread> aliceThreads = chatService.getThreadsForUser("alice");
            assertFalse(aliceThreads.isEmpty());
        }

        @Test
        @DisplayName("sendMessage: validation, message preview, recipient notification")
        void testSendMessage() {
            assertFalse(chatService.sendMessage(null, "alice", "Hello").isSuccess());
            assertFalse(chatService.sendMessage("TH1", null, "Hello").isSuccess());
            assertFalse(chatService.sendMessage("GHOST_THREAD", "alice", "Hello").isSuccess());

            ChatThread thread = chatService.getOrCreateThread(liveSellListing.getId(), "alice");
            assertNotNull(thread);

            // Unauthorized sender
            SendMessageResult rUnauth = chatService.sendMessage(thread.getId(), "diana", "Hello");
            assertFalse(rUnauth.isSuccess());

            // Empty text
            SendMessageResult rEmpty = chatService.sendMessage(thread.getId(), "alice", "   ");
            assertFalse(rEmpty.isSuccess());

            // Short message
            SendMessageResult rShort = chatService.sendMessage(thread.getId(), "alice", "Hi Charlie!");
            assertTrue(rShort.isSuccess());

            // Long message (> 50 chars)
            String longMsg = "This is a very long message designed to trigger the truncation preview logic in ChatService recipient notifications.";
            SendMessageResult rLong = chatService.sendMessage(thread.getId(), "alice", longMsg);
            assertTrue(rLong.isSuccess());
        }

        @Test
        @DisplayName("sendFormalPriceOffer & sendFormalBorrowRequest: validation and transitions")
        void testFormalOffers() {
            ChatThread thread = chatService.getOrCreateThread(liveSellListing.getId(), "alice");
            assertNotNull(thread);

            // Seller cannot make formal price offer
            assertFalse(chatService.sendFormalPriceOffer(thread.getId(), liveSellListing.getSellerUsername(), 50.0).isSuccess());

            // Non-existent thread
            assertFalse(chatService.sendFormalPriceOffer("GHOST_T", "alice", 50.0).isSuccess());

            // Invalid price (<= 0)
            assertFalse(chatService.sendFormalPriceOffer(thread.getId(), "alice", 0.0).isSuccess());
            assertFalse(chatService.sendFormalPriceOffer(thread.getId(), "alice", -10.0).isSuccess());

            // Price offer on LEND listing fails
            ChatThread lendThread = chatService.getOrCreateThread(liveLendListing.getId(), "alice");
            if (lendThread != null) {
                assertFalse(chatService.sendFormalPriceOffer(lendThread.getId(), "alice", 50.0).isSuccess());
            }

            // Valid price offer on SELL listing
            SendFormalOfferResult rOffer = chatService.sendFormalPriceOffer(thread.getId(), "alice", 80.0);
            assertTrue(rOffer.isSuccess());

            // Formal borrow request on SELL listing fails
            LocalDateTime now = LocalDateTime.now().plusDays(1);
            assertFalse(chatService.sendFormalBorrowRequest(thread.getId(), "alice", now, now.plusDays(2)).isSuccess());
        }

        @Test
        @DisplayName("searchChatHistory: validation, all messages on empty query, matches and no matches")
        void testSearchChatHistory() {
            assertFalse(chatService.searchChatHistory(null, "alice", "hello").isSuccess());
            assertFalse(chatService.searchChatHistory("GHOST_THREAD", "alice", "hello").isSuccess());

            ChatThread thread = chatService.getOrCreateThread(liveSellListing.getId(), "alice");
            assertNotNull(thread);

            // Unauthorized searcher
            assertFalse(chatService.searchChatHistory(thread.getId(), "diana", "hello").isSuccess());

            // Empty query returns all
            ChatSearchResult rAll = chatService.searchChatHistory(thread.getId(), "alice", "  ");
            assertTrue(rAll.isSuccess());

            chatService.sendMessage(thread.getId(), "alice", "Meeting at Library tomorrow?");

            ChatSearchResult rFound = chatService.searchChatHistory(thread.getId(), "alice", "library");
            assertTrue(rFound.isSuccess());
            assertEquals(1, rFound.getMatchingMessages().size());

            ChatSearchResult rNotFound = chatService.searchChatHistory(thread.getId(), "alice", "nonexistentterm");
            assertEquals(ChatOperationStatus.NO_MATCHES, rNotFound.getStatus());
        }
    }

    @Nested
    @DisplayName("AdminRatingService Branch Tests")
    class AdminRatingServiceBranchTests {

        private AdminRatingService ratingService;
        private Admin admin;

        @BeforeEach
        void initService() {
            admin = (Admin) dataService.findUser("admin");
            SessionManager.login(admin);
            ratingService = dataService.getAdminRatingService();
        }

        @Test
        @DisplayName("REVIEW_ORDER_COMPARATOR: handles nulls, flags, and dates")
        void testComparatorBranches() {
            assertEquals(0, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(null, null));
            assertEquals(1, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(null, new Review()));
            assertEquals(-1, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(new Review(), null));

            Review r1 = new Review();
            Review r2 = new Review();
            r1.setDate(null);
            r2.setDate(null);
            assertEquals(0, AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(r1, r2));

            r1.setFlagged(true);
            r2.setFlagged(false);
            assertTrue(AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(r1, r2) < 0);

            r2.setFlagged(true);
            r1.setDate(LocalDateTime.of(2026, 11, 12, 10, 0));
            r2.setDate(null);
            assertTrue(AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(r1, r2) < 0);

            r2.setDate(LocalDateTime.of(2026, 11, 14, 10, 0));
            assertTrue(AdminRatingService.REVIEW_ORDER_COMPARATOR.compare(r1, r2) > 0);
        }

        @Test
        @DisplayName("filterReviews and getSummaryMetrics branches")
        void testFilterAndSummaryMetrics() {
            AdminRatingFilterCriteria invalidRange = new AdminRatingFilterCriteria();
            invalidRange.setStartDate(LocalDate.of(2026, 11, 15));
            invalidRange.setEndDate(LocalDate.of(2026, 11, 10));
            assertThrows(IllegalArgumentException.class, () -> ratingService.filterReviews(invalidRange));

            List<Review> filtered = ratingService.filterReviews(null);
            assertFalse(filtered.isEmpty());

            AdminRatingSummaryMetrics metrics = ratingService.getSummaryMetrics();
            assertNotNull(metrics);
            assertTrue(metrics.getTotalReviews() > 0);
        }

        @Test
        @DisplayName("hideReview, unhideReview, dismissFlag error and lifecycle branches")
        void testModerationLifecycle() {
            // Unauthorized admin
            assertEquals(AdminRatingOperationStatus.UNAUTHORIZED, ratingService.hideReview("R1", "bob").getStatus());
            assertEquals(AdminRatingOperationStatus.UNAUTHORIZED, ratingService.unhideReview("R1", "bob").getStatus());
            assertEquals(AdminRatingOperationStatus.UNAUTHORIZED, ratingService.dismissFlag("R1", "bob").getStatus());

            // Empty review ID
            assertEquals(AdminRatingOperationStatus.INVALID_OPERATION, ratingService.hideReview("   ", "admin").getStatus());
            assertEquals(AdminRatingOperationStatus.INVALID_OPERATION, ratingService.unhideReview("   ", "admin").getStatus());
            assertEquals(AdminRatingOperationStatus.INVALID_OPERATION, ratingService.dismissFlag("   ", "admin").getStatus());

            // Review not found
            assertEquals(AdminRatingOperationStatus.REVIEW_NOT_FOUND, ratingService.hideReview("GHOST_REV", "admin").getStatus());
            assertEquals(AdminRatingOperationStatus.REVIEW_NOT_FOUND, ratingService.unhideReview("GHOST_REV", "admin").getStatus());
            assertEquals(AdminRatingOperationStatus.REVIEW_NOT_FOUND, ratingService.dismissFlag("GHOST_REV", "admin").getStatus());

            // Unhide review that is already visible
            Review rev = dataService.getAllReviews().get(0);
            rev.setHidden(false);
            rev.setFlagged(false);
            assertEquals(AdminRatingOperationStatus.ALREADY_VISIBLE, ratingService.unhideReview(rev.getId(), "admin").getStatus());
            assertEquals(AdminRatingOperationStatus.NOT_FLAGGED, ratingService.dismissFlag(rev.getId(), "admin").getStatus());

            // Flag and dismiss
            rev.setFlagged(true);
            AdminRatingOperationResult rDismiss = ratingService.dismissFlag(rev.getId(), "admin");
            assertTrue(rDismiss.isSuccess());
            assertFalse(rev.isFlagged());

            // Hide review
            AdminRatingOperationResult rHide = ratingService.hideReview(rev.getId(), "admin");
            assertTrue(rHide.isSuccess());
            assertTrue(rev.isHidden());

            // Double hide -> ALREADY_HIDDEN
            assertEquals(AdminRatingOperationStatus.ALREADY_HIDDEN, ratingService.hideReview(rev.getId(), "admin").getStatus());

            // Unhide review
            AdminRatingOperationResult rUnhide = ratingService.unhideReview(rev.getId(), "admin");
            assertTrue(rUnhide.isSuccess());
            assertFalse(rev.isHidden());

            // findReviewById
            assertNull(ratingService.findReviewById(null));
            assertNull(ratingService.findReviewById("  "));
            assertNull(ratingService.findReviewById("GHOST"));
            assertNotNull(ratingService.findReviewById(rev.getId()));
        }
    }

    @Nested
    @DisplayName("AdminListingService Extended Branch Tests")
    class AdminListingServiceExtendedBranchTests {

        private AdminListingService listingService;

        @BeforeEach
        void initService() {
            Admin admin = (Admin) dataService.findUser("admin");
            SessionManager.login(admin);
            listingService = dataService.getAdminListingService();
        }

        @Test
        @DisplayName("createListingOnBehalfOfSeller: validation failures and giveaway")
        void testCreateListingValidationFailures() throws IOException {
            Path tempPhoto = Files.createTempFile("admin-photo", ".png");
            Files.write(tempPhoto, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0});

            // Empty title
            AdminListingCreateRequest reqNoTitle = new AdminListingCreateRequest(
                    "charlie", "", "Desc", ListingType.SELL, Category.BOOKS, Condition.NEW,
                    MeetupLocation.LIBRARY, 50.0, null, List.of(new Photo("P1", tempPhoto.toString()))
            );
            assertEquals(AdminListingOperationStatus.INVALID_FIELD, listingService.createListingOnBehalfOfSeller(reqNoTitle, "admin").getStatus());

            // Empty description
            AdminListingCreateRequest reqNoDesc = new AdminListingCreateRequest(
                    "charlie", "Title", "", ListingType.SELL, Category.BOOKS, Condition.NEW,
                    MeetupLocation.LIBRARY, 50.0, null, List.of(new Photo("P1", tempPhoto.toString()))
            );
            assertEquals(AdminListingOperationStatus.INVALID_FIELD, listingService.createListingOnBehalfOfSeller(reqNoDesc, "admin").getStatus());

            // Null category
            AdminListingCreateRequest reqNoCat = new AdminListingCreateRequest(
                    "charlie", "Title", "Desc", ListingType.SELL, null, Condition.NEW,
                    MeetupLocation.LIBRARY, 50.0, null, List.of(new Photo("P1", tempPhoto.toString()))
            );
            assertEquals(AdminListingOperationStatus.INVALID_FIELD, listingService.createListingOnBehalfOfSeller(reqNoCat, "admin").getStatus());

            // SELL listing with null / non-positive price
            AdminListingCreateRequest reqBadPrice = new AdminListingCreateRequest(
                    "charlie", "Title", "Desc", ListingType.SELL, Category.BOOKS, Condition.NEW,
                    MeetupLocation.LIBRARY, 0.0, null, List.of(new Photo("P1", tempPhoto.toString()))
            );
            assertEquals(AdminListingOperationStatus.INVALID_FIELD, listingService.createListingOnBehalfOfSeller(reqBadPrice, "admin").getStatus());

            // No photos
            AdminListingCreateRequest reqNoPhoto = new AdminListingCreateRequest(
                    "charlie", "Title", "Desc", ListingType.SELL, Category.BOOKS, Condition.NEW,
                    MeetupLocation.LIBRARY, 50.0, null, Collections.emptyList()
            );
            assertEquals(AdminListingOperationStatus.INVALID_FIELD, listingService.createListingOnBehalfOfSeller(reqNoPhoto, "admin").getStatus());

            // GIVEAWAY listing creation
            AdminListingCreateRequest reqGiveaway = new AdminListingCreateRequest(
                    "charlie", "Free Notes", "Giving away lecture notes", ListingType.GIVEAWAY,
                    Category.BOOKS, Condition.USED, MeetupLocation.LIBRARY, null, null,
                    List.of(new Photo("P1", tempPhoto.toString()))
            );
            AdminListingOperationResult rGiveaway = listingService.createListingOnBehalfOfSeller(reqGiveaway, "admin");
            assertTrue(rGiveaway.isSuccess());
            assertNull(rGiveaway.getListing().getPrice());

            Files.deleteIfExists(tempPhoto);
        }

        @Test
        @DisplayName("editListing: blocked by deals in progress and status distribution")
        void testEditListingBlockedAndStatusCounts() {
            // Listing with SOLD status cannot be edited
            Listing soldListing = new Listing();
            soldListing.setId("L_SOLD_TEST");
            soldListing.setTitle("Sold Item");
            soldListing.setStatus(ListingStatus.SOLD);
            soldListing.setSellerUsername("charlie");
            dataService.addListing(soldListing);

            AdminListingEditRequest editReq = new AdminListingEditRequest();
            editReq.setListingId("L_SOLD_TEST");
            editReq.setTitle("New Title");

            AdminListingOperationResult rBlocked = listingService.editListing(editReq, "admin");
            assertEquals(AdminListingOperationStatus.DEAL_IN_PROGRESS_OR_COMPLETED, rBlocked.getStatus());

            // Active sellers
            List<Seller> activeSellers = listingService.getActiveSellers();
            assertFalse(activeSellers.isEmpty());

            // Active offers count
            assertEquals(0, listingService.getActiveOffersCount(null));
            assertTrue(listingService.getActiveOffersCount("101") >= 0);

            // Listing counts by status
            Map<ListingStatus, Long> counts = listingService.getListingCountsByStatus();
            assertNotNull(counts);
            assertTrue(counts.containsKey(ListingStatus.LIVE));
        }
    }
}
