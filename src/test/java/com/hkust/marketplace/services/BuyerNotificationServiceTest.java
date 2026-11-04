package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-B09: Buyer Notification Service Tests (Use Case 1.8)")
public class BuyerNotificationServiceTest {

    private DummyDataService dataService;
    private BuyerNotificationService service;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        service = dataService.getBuyerNotificationService();
    }

    @Nested
    @DisplayName("Pre-conditions, Construction & Null Safety")
    class PreconditionsAndNullSafety {

        @Test
        @DisplayName("Constructor rejects null DummyDataService")
        void testConstructorNullCheck() {
            assertThrows(NullPointerException.class, () -> new BuyerNotificationService(null));
        }

        @Test
        @DisplayName("Querying with null or blank username returns empty list")
        void testNullOrBlankUsername() {
            assertTrue(service.getNotifications(null).isEmpty());
            assertTrue(service.getNotifications("").isEmpty());
            assertTrue(service.getNotifications("   ").isEmpty());
            assertEquals(0, service.getUnreadCount(null));
            assertEquals(0, service.getUnreadCount("   "));
            assertEquals(0, service.markAllAsRead(null));
            assertEquals(0, service.clearAllNotifications(null));
            assertFalse(service.markAsRead(null, "N1"));
            assertFalse(service.markAsUnread(null, "N1"));
            assertFalse(service.deleteNotification(null, "N1"));

            Map<NotificationCategory, Long> unreadMap = service.getUnreadCountsByCategory(null);
            assertNotNull(unreadMap);
            assertEquals(0L, unreadMap.get(NotificationCategory.OFFERS));

            Map<NotificationCategory, Long> totalMap = service.getTotalCountsByCategory(null);
            assertNotNull(totalMap);
            assertEquals(0L, totalMap.get(NotificationCategory.OFFERS));
        }
    }

    @Nested
    @DisplayName("Basic Flow: View Notifications & Sorting (Use Case 1.8 Basic Flow step 1 & 2)")
    class BasicFlowTests {

        @Test
        @DisplayName("Displays notifications newest first with timestamps and unread highlights")
        void testNotificationsNewestFirst() {
            List<Notification> notifs = service.getNotifications("alice");
            assertNotNull(notifs);
            assertFalse(notifs.isEmpty());

            // Verify ordered newest first (descending timestamp)
            for (int i = 0; i < notifs.size() - 1; i++) {
                LocalDateTime current = notifs.get(i).getTimestamp();
                LocalDateTime next = notifs.get(i + 1).getTimestamp();
                assertTrue(current.isAfter(next) || current.isEqual(next),
                        "Notifications should be sorted newest first");
            }

            // Verify each has category, title, message, and formatted timestamp
            boolean hasUnread = false;
            for (Notification n : notifs) {
                assertNotNull(n.getCategory());
                assertNotNull(n.getTitle());
                assertNotNull(n.getMessage());
                assertNotNull(n.getFormattedTimestamp());
                if (n.isUnread()) {
                    hasUnread = true;
                }
            }
            assertTrue(hasUnread, "Seed data should include unread highlighted notifications for Alice");
        }
    }

    @Nested
    @DisplayName("Alternative Flow A1: No Notifications")
    class AlternativeFlowA1NoNotifications {

        @Test
        @DisplayName("User with no notifications returns empty list")
        void testUserWithoutNotifications() {
            String unusedUser = "user_without_notifs";
            List<Notification> notifs = service.getNotifications(unusedUser);
            assertTrue(notifs.isEmpty());
            assertEquals(0, service.getUnreadCount(unusedUser));
        }
    }

    @Nested
    @DisplayName("Alternative Flow A2: Filter By Category & Search Criteria")
    class AlternativeFlowA2FilterTests {

        @Test
        @DisplayName("Filter by all 6 categories: Offers, Chat, Borrow Reminders, Listings, Reviews, Announcements")
        void testFilterByCategory() {
            List<Notification> offers = service.getNotifications("alice", NotificationCategory.OFFERS);
            assertFalse(offers.isEmpty());
            for (Notification n : offers) {
                assertTrue(NotificationCategory.OFFERS.matches(n.getCategory()));
            }

            List<Notification> chats = service.getNotifications("alice", NotificationCategory.CHAT);
            assertFalse(chats.isEmpty());
            for (Notification n : chats) {
                assertTrue(NotificationCategory.CHAT.matches(n.getCategory()));
            }

            List<Notification> reminders = service.getNotifications("alice", NotificationCategory.BORROW_REMINDERS);
            assertFalse(reminders.isEmpty());
            for (Notification n : reminders) {
                assertTrue(NotificationCategory.BORROW_REMINDERS.matches(n.getCategory()));
            }

            List<Notification> listings = service.getNotifications("alice", NotificationCategory.LISTINGS);
            assertFalse(listings.isEmpty());
            for (Notification n : listings) {
                assertTrue(NotificationCategory.LISTINGS.matches(n.getCategory()));
            }

            List<Notification> reviews = service.getNotifications("alice", NotificationCategory.REVIEWS);
            assertFalse(reviews.isEmpty());
            for (Notification n : reviews) {
                assertTrue(NotificationCategory.REVIEWS.matches(n.getCategory()));
            }

            List<Notification> announcements = service.getNotifications("alice", NotificationCategory.ANNOUNCEMENTS);
            assertFalse(announcements.isEmpty());
            for (Notification n : announcements) {
                assertTrue(NotificationCategory.ANNOUNCEMENTS.matches(n.getCategory()));
            }
        }

        @Test
        @DisplayName("Filter by unread only")
        void testFilterUnreadOnly() {
            List<Notification> unreadList = service.getNotifications("alice", NotificationFilterCriteria.unreadOnly());
            assertFalse(unreadList.isEmpty());
            for (Notification n : unreadList) {
                assertTrue(n.isUnread());
                assertFalse(n.isRead());
            }
        }

        @Test
        @DisplayName("Filter by keyword search in title or message")
        void testFilterKeywordSearch() {
            NotificationFilterCriteria criteria = new NotificationFilterCriteria(null, false, "Calculus");
            List<Notification> matches = service.getNotifications("alice", criteria);
            assertFalse(matches.isEmpty());
            for (Notification n : matches) {
                boolean contains = (n.getTitle() != null && n.getTitle().toLowerCase().contains("calculus"))
                        || (n.getMessage() != null && n.getMessage().toLowerCase().contains("calculus"));
                assertTrue(contains);
            }

            NotificationFilterCriteria noMatches = new NotificationFilterCriteria(null, false, "XYZNonExistentKeyword");
            assertTrue(service.getNotifications("alice", noMatches).isEmpty());
        }

        @Test
        @DisplayName("Unread counts and total counts grouped by category")
        void testCategoryCounts() {
            Map<NotificationCategory, Long> unreadMap = service.getUnreadCountsByCategory("alice");
            Map<NotificationCategory, Long> totalMap = service.getTotalCountsByCategory("alice");

            assertNotNull(unreadMap);
            assertNotNull(totalMap);

            for (NotificationCategory cat : NotificationCategory.values()) {
                assertTrue(totalMap.get(cat) >= unreadMap.get(cat),
                        "Total count should be greater than or equal to unread count for category " + cat);
            }
        }
    }

    @Nested
    @DisplayName("Selection & Linked Items: Basic Flow step 4 & Alternative Flow A3")
    class NotificationTargetResolutionTests {

        @Test
        @DisplayName("Selecting notification marks it as read")
        void testSelectingNotificationMarksAsRead() {
            List<Notification> unreadList = service.getNotifications("alice", NotificationFilterCriteria.unreadOnly());
            assertFalse(unreadList.isEmpty());
            Notification unreadNotif = unreadList.get(0);
            assertTrue(unreadNotif.isUnread());

            NotificationTargetResult result = service.resolveNotificationTarget("alice", unreadNotif.getId());
            assertNotNull(result);
            assertTrue(unreadNotif.isRead());
            assertFalse(unreadNotif.isUnread());
        }

        @Test
        @DisplayName("Selecting notification for active offer resolves Offer target")
        void testResolveOfferTarget() {
            List<Notification> offers = service.getNotifications("alice", NotificationCategory.OFFERS);
            Notification offerNotif = offers.stream()
                    .filter(n -> n.getRelatedItemId() != null && n.getRelatedItemId().startsWith("O"))
                    .findFirst().orElse(null);
            assertNotNull(offerNotif);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", offerNotif.getId());
            assertTrue(result.isSuccess());
            assertSame(NotificationTargetType.OFFER, result.getTargetType());
            assertInstanceOf(Offer.class, result.getTargetObject());
            assertEquals(offerNotif.getRelatedItemId(), ((Offer) result.getTargetObject()).getId());
        }

        @Test
        @DisplayName("Alternative Flow A3: Offer deleted resolves to ITEM_DELETED")
        void testResolveDeletedOffer() {
            Notification deletedOfferNotif = new Notification("alice", "Offer Deleted", "Details", "Offers", "O_NON_EXISTENT", "OFFER");
            dataService.addNotification(deletedOfferNotif);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", deletedOfferNotif.getId());
            assertTrue(result.isItemDeleted());
            assertSame(NotificationTargetStatus.ITEM_DELETED, result.getStatus());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().toLowerCase().contains("no longer available"));
            assertTrue(deletedOfferNotif.isRead());
        }

        @Test
        @DisplayName("Selecting notification for chat thread resolves ChatThread target")
        void testResolveChatThreadTarget() {
            List<Notification> chats = service.getNotifications("alice", NotificationCategory.CHAT);
            Notification chatNotif = chats.get(0);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", chatNotif.getId());
            assertTrue(result.isSuccess());
            assertSame(NotificationTargetType.CHAT_THREAD, result.getTargetType());
            assertInstanceOf(ChatThread.class, result.getTargetObject());
        }

        @Test
        @DisplayName("Alternative Flow A3: Chat thread deleted resolves to ITEM_DELETED")
        void testResolveDeletedChatThread() {
            Notification deletedChatNotif = new Notification("alice", "Chat Msg", "Details", "Chat", "T_NON_EXISTENT", "CHAT_THREAD");
            dataService.addNotification(deletedChatNotif);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", deletedChatNotif.getId());
            assertTrue(result.isItemDeleted());
            assertSame(NotificationTargetStatus.ITEM_DELETED, result.getStatus());
        }

        @Test
        @DisplayName("Selecting notification for loan deal resolves Deal target")
        void testResolveDealTarget() {
            List<Notification> reminders = service.getNotifications("alice", NotificationCategory.BORROW_REMINDERS);
            Notification remNotif = reminders.get(0);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", remNotif.getId());
            assertTrue(result.isSuccess());
            assertSame(NotificationTargetType.DEAL, result.getTargetType());
            assertInstanceOf(Deal.class, result.getTargetObject());
        }

        @Test
        @DisplayName("Alternative Flow A3: Deal deleted resolves to ITEM_DELETED")
        void testResolveDeletedDeal() {
            Notification deletedDealNotif = new Notification("alice", "Borrow", "Details", "Borrow Reminders", "D_NON_EXISTENT", "DEAL");
            dataService.addNotification(deletedDealNotif);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", deletedDealNotif.getId());
            assertTrue(result.isItemDeleted());
            assertSame(NotificationTargetStatus.ITEM_DELETED, result.getStatus());
        }

        @Test
        @DisplayName("Alternative Flow A3: Listing removed resolves to ITEM_DELETED")
        void testResolveDeletedListing() {
            List<Notification> listings = service.getNotifications("alice", NotificationCategory.LISTINGS);
            Notification listingNotif = listings.get(0);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", listingNotif.getId());
            assertTrue(result.isItemDeleted());
            assertSame(NotificationTargetStatus.ITEM_DELETED, result.getStatus());
            assertTrue(result.getMessage().toLowerCase().contains("no longer available"));
        }

        @Test
        @DisplayName("Selecting notification for live listing resolves Listing target")
        void testResolveLiveListing() {
            Listing live = dataService.getLiveListings().get(0);
            Notification liveListingNotif = new Notification("alice", "Live Listing", "Details", "Listings", live.getId(), "LISTING");
            dataService.addNotification(liveListingNotif);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", liveListingNotif.getId());
            assertTrue(result.isSuccess());
            assertSame(NotificationTargetType.LISTING, result.getTargetType());
            assertEquals(live.getId(), ((Listing) result.getTargetObject()).getId());
        }

        @Test
        @DisplayName("Selecting notification for review resolves Review target")
        void testResolveReviewTarget() {
            List<Notification> reviews = service.getNotifications("alice", NotificationCategory.REVIEWS);
            Notification revNotif = reviews.get(0);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", revNotif.getId());
            assertTrue(result.isSuccess());
            assertSame(NotificationTargetType.REVIEW, result.getTargetType());
            assertInstanceOf(Review.class, result.getTargetObject());
        }

        @Test
        @DisplayName("Alternative Flow A3: Review deleted or hidden resolves to ITEM_DELETED")
        void testResolveDeletedOrHiddenReview() {
            Notification deletedRev = new Notification("alice", "Rev", "Details", "Reviews", "R_NON_EXISTENT", "REVIEW");
            dataService.addNotification(deletedRev);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", deletedRev.getId());
            assertTrue(result.isItemDeleted());
        }

        @Test
        @DisplayName("Selecting announcement with no linked item returns NO_LINKED_ITEM")
        void testResolveAnnouncement() {
            List<Notification> announcements = service.getNotifications("alice", NotificationCategory.ANNOUNCEMENTS);
            Notification annNotif = announcements.get(0);

            NotificationTargetResult result = service.resolveNotificationTarget("alice", annNotif.getId());
            assertSame(NotificationTargetStatus.NO_LINKED_ITEM, result.getStatus());
            assertNotNull(result.getMessage());
        }

        @Test
        @DisplayName("Resolving non-existent notification returns NOTIFICATION_NOT_FOUND")
        void testResolveNonExistentNotification() {
            NotificationTargetResult result = service.resolveNotificationTarget("alice", "NON_EXISTENT_ID");
            assertSame(NotificationTargetStatus.NOTIFICATION_NOT_FOUND, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Read / Unread / Deletion Operations")
    class StatusAndDeletionOperations {

        @Test
        @DisplayName("markAsRead and markAsUnread toggle state")
        void testMarkAsReadAndUnread() {
            Notification n = new Notification("bob", "Test", "Msg", "Announcements");
            dataService.addNotification(n);

            assertTrue(service.markAsRead("bob", n.getId()));
            assertTrue(n.isRead());

            assertTrue(service.markAsUnread("bob", n.getId()));
            assertFalse(n.isRead());

            assertFalse(service.markAsRead("bob", "INVALID_ID"));
            assertFalse(service.markAsUnread("bob", "INVALID_ID"));
        }

        @Test
        @DisplayName("markAllAsRead marks all unread notifications")
        void testMarkAllAsRead() {
            Notification n1 = new Notification("charlie", "T1", "M1", "Offers");
            n1.setRead(false);
            Notification n2 = new Notification("charlie", "T2", "M2", "Offers");
            n2.setRead(false);
            dataService.addNotification(n1);
            dataService.addNotification(n2);

            int marked = service.markAllAsRead("charlie");
            assertTrue(marked >= 2);
            assertEquals(0, service.getUnreadCount("charlie"));
        }

        @Test
        @DisplayName("deleteNotification deletes specific notification")
        void testDeleteNotification() {
            Notification n = new Notification("bob", "To Delete", "Msg", "Announcements");
            dataService.addNotification(n);

            assertTrue(service.deleteNotification("bob", n.getId()));
            assertFalse(service.deleteNotification("bob", n.getId()));
        }

        @Test
        @DisplayName("clearAllNotifications removes all notifications for user")
        void testClearAllNotifications() {
            Notification n = new Notification("diana", "T", "M", "Announcements");
            dataService.addNotification(n);

            int cleared = service.clearAllNotifications("diana");
            assertTrue(cleared >= 1);
            assertEquals(0, service.getNotifications("diana").size());
        }
    }

    @Nested
    @DisplayName("Automated Borrow Reminders (Task 1.8)")
    class BorrowRemindersTests {

        @Test
        @DisplayName("Generates borrow reminder when loan is due within 48h")
        void testGenerateBorrowReminderDueSoon() {
            Deal loan = new Deal();
            loan.setId("D_LOAN_SOON");
            loan.setListingTitle("Lab Multimeter");
            loan.setBuyerUsername("borrower_test");
            loan.setSellerUsername("charlie");
            loan.setType(DealType.LOAN);
            loan.setStatus(DealStatus.ACTIVE);
            loan.setStartDate(LocalDateTime.now().minusDays(1));
            loan.setDueDate(LocalDateTime.now().plusHours(12)); // Due in 12h!
            dataService.addDeal(loan);

            List<Notification> reminders = service.checkAndGenerateBorrowReminders("borrower_test");
            assertEquals(1, reminders.size());
            Notification rem = reminders.get(0);
            assertEquals("Borrow Due-Date Reminder", rem.getTitle());
            assertTrue(rem.getMessage().contains("Lab Multimeter"));
            assertEquals("D_LOAN_SOON", rem.getRelatedItemId());

            // Second check should not duplicate reminder
            List<Notification> secondCheck = service.checkAndGenerateBorrowReminders("borrower_test");
            assertTrue(secondCheck.isEmpty());
        }

        @Test
        @DisplayName("Generates overdue reminder when loan is overdue")
        void testGenerateBorrowReminderOverdue() {
            Deal loan = new Deal();
            loan.setId("D_LOAN_OVERDUE");
            loan.setListingTitle("Microscope");
            loan.setBuyerUsername("borrower_overdue");
            loan.setSellerUsername("charlie");
            loan.setType(DealType.LOAN);
            loan.setStatus(DealStatus.ACTIVE);
            loan.setStartDate(LocalDateTime.now().minusDays(5));
            loan.setDueDate(LocalDateTime.now().minusHours(2)); // Overdue!
            dataService.addDeal(loan);

            List<Notification> reminders = service.checkAndGenerateBorrowReminders("borrower_overdue");
            assertEquals(1, reminders.size());
            Notification rem = reminders.get(0);
            assertEquals("Item Return Overdue", rem.getTitle());
            assertTrue(rem.getMessage().toLowerCase().contains("overdue"));
        }

        @Test
        @DisplayName("Does not generate reminder if loan due far in future or already completed")
        void testNoReminderForFutureOrCompletedLoan() {
            Deal futureLoan = new Deal();
            futureLoan.setId("D_LOAN_FUTURE");
            futureLoan.setListingTitle("Future Book");
            futureLoan.setBuyerUsername("borrower_future");
            futureLoan.setType(DealType.LOAN);
            futureLoan.setStatus(DealStatus.ACTIVE);
            futureLoan.setDueDate(LocalDateTime.now().plusDays(10));
            dataService.addDeal(futureLoan);

            Deal completedLoan = new Deal();
            completedLoan.setId("D_LOAN_DONE");
            completedLoan.setListingTitle("Returned Book");
            completedLoan.setBuyerUsername("borrower_future");
            completedLoan.setType(DealType.LOAN);
            completedLoan.setStatus(DealStatus.RETURNED);
            completedLoan.setDueDate(LocalDateTime.now().minusHours(1));
            dataService.addDeal(completedLoan);

            List<Notification> reminders = service.checkAndGenerateBorrowReminders("borrower_future");
            assertTrue(reminders.isEmpty());
        }
    }

    @Nested
    @DisplayName("Admin Listing Removal & Review Reply Notifications (Task 1.8)")
    class SystemNotificationsTests {

        @Test
        @DisplayName("Admin listing removal notifies only buyers with active offers on that listing")
        void testNotifyBuyersOfListingRemoval() {
            Listing listing = new Listing();
            listing.setId("L_ADMIN_REMOVE_TEST");
            listing.setTitle("Removed Keyboard");
            listing.setStatus(ListingStatus.LIVE);
            dataService.addListing(listing);

            Offer o1 = new Offer();
            o1.setId("O_REM_1");
            o1.setListingId(listing.getId());
            o1.setBuyerUsername("buyer_one");
            o1.setStatus(OfferStatus.PENDING);
            dataService.addOffer(o1);

            Offer o2 = new Offer();
            o2.setId("O_REM_2");
            o2.setListingId(listing.getId());
            o2.setBuyerUsername("buyer_two");
            o2.setStatus(OfferStatus.DECLINED); // Inactive offer, should not be notified
            dataService.addOffer(o2);

            service.notifyBuyersOfListingRemoval(listing, "Violated marketplace terms");

            List<Notification> buyerOneNotifs = service.getNotifications("buyer_one", NotificationCategory.LISTINGS);
            assertFalse(buyerOneNotifs.isEmpty());
            Notification notif = buyerOneNotifs.get(0);
            assertEquals("Listing Removed by Admin", notif.getTitle());
            assertTrue(notif.getMessage().contains("Removed Keyboard"));
            assertTrue(notif.getMessage().contains("Violated marketplace terms"));

            List<Notification> buyerTwoNotifs = service.getNotifications("buyer_two", NotificationCategory.LISTINGS);
            assertTrue(buyerTwoNotifs.isEmpty());
        }

        @Test
        @DisplayName("Seller review reply notifies the buyer with seller reply text")
        void testNotifyReviewReply() {
            Review review = new Review();
            review.setId("R_REPLY_TEST");
            review.setBuyerUsername("review_buyer");
            review.setSellerUsername("seller_charlie");
            review.setListingTitle("Calculus 1");
            review.setReply("Thanks for purchasing!");

            service.notifyReviewReply(review);

            List<Notification> buyerNotifs = service.getNotifications("review_buyer", NotificationCategory.REVIEWS);
            assertFalse(buyerNotifs.isEmpty());
            Notification notif = buyerNotifs.get(0);
            assertEquals("Seller Replied to Review", notif.getTitle());
            assertTrue(notif.getMessage().contains("seller_charlie"));
            assertTrue(notif.getMessage().contains("Thanks for purchasing!"));

            // Edge cases: null review, null buyer, null reply
            service.notifyReviewReply(null);
            Review nullBuyerReview = new Review();
            nullBuyerReview.setReply("Hi");
            service.notifyReviewReply(nullBuyerReview);
            Review nullReplyReview = new Review();
            nullReplyReview.setBuyerUsername("alice");
            service.notifyReviewReply(nullReplyReview);

            // Admin listing removal without reason
            service.notifyBuyersOfListingRemoval(null, null);
            Listing noReasonListing = new Listing();
            noReasonListing.setId("L_NO_REASON");
            noReasonListing.setTitle("No Reason Item");
            dataService.addListing(noReasonListing);
            Offer oNoReason = new Offer();
            oNoReason.setId("O_NO_REASON");
            oNoReason.setListingId(noReasonListing.getId());
            oNoReason.setBuyerUsername("buyer_no_reason");
            oNoReason.setStatus(OfferStatus.COUNTERED);
            dataService.addOffer(oNoReason);
            service.notifyBuyersOfListingRemoval(noReasonListing, "   ");
            List<Notification> noReasonNotifs = service.getNotifications("buyer_no_reason", NotificationCategory.LISTINGS);
            assertFalse(noReasonNotifs.isEmpty());
        }

        @Test
        @DisplayName("resolveNotificationTarget with implicit category inferences and edge states")
        void testImplicitCategoryInferencesAndEdgeStates() {
            // Implicit OFFERS
            Notification nOffer = new Notification("alice", "Offer", "Msg", "Offers", null, null);
            dataService.addNotification(nOffer);
            assertTrue(service.resolveNotificationTarget("alice", nOffer.getId()).isItemDeleted());

            // Implicit CHAT
            Notification nChat = new Notification("alice", "Chat", "Msg", "Chat", null, null);
            dataService.addNotification(nChat);
            assertTrue(service.resolveNotificationTarget("alice", nChat.getId()).isItemDeleted());

            // Implicit BORROW_REMINDERS
            Notification nRem = new Notification("alice", "Rem", "Msg", "Borrow Reminders", null, null);
            dataService.addNotification(nRem);
            assertTrue(service.resolveNotificationTarget("alice", nRem.getId()).isItemDeleted());

            // Implicit LISTINGS
            Notification nList = new Notification("alice", "List", "Msg", "Listings", null, null);
            dataService.addNotification(nList);
            assertTrue(service.resolveNotificationTarget("alice", nList.getId()).isItemDeleted());

            // Implicit REVIEWS
            Notification nRev = new Notification("alice", "Rev", "Msg", "Reviews", null, null);
            dataService.addNotification(nRev);
            assertTrue(service.resolveNotificationTarget("alice", nRev.getId()).isItemDeleted());

            // Rejected Listing resolution
            Listing rejListing = new Listing();
            rejListing.setId("L_REJ");
            rejListing.setStatus(ListingStatus.REJECTED);
            dataService.addListing(rejListing);
            Notification nRej = new Notification("alice", "Rej", "Msg", "Listings", rejListing.getId(), "LISTING");
            dataService.addNotification(nRej);
            assertTrue(service.resolveNotificationTarget("alice", nRej.getId()).isItemDeleted());

            // Chat thread exists but its listing is missing
            ChatThread orphanThread = new ChatThread("T_ORPHAN", "L_MISSING", "alice", "charlie");
            dataService.getAllChatThreads().add(orphanThread);
            Notification nOrphan = new Notification("alice", "Orphan", "Msg", "Chat", orphanThread.getId(), "CHAT_THREAD");
            dataService.addNotification(nOrphan);
            assertTrue(service.resolveNotificationTarget("alice", nOrphan.getId()).isItemDeleted());
        }

        @Test
        @DisplayName("Search filter handling null fields")
        void testSearchFilterNullFields() {
            Notification nNulls = new Notification("alice", null, null, "Announcements");
            dataService.addNotification(nNulls);

            NotificationFilterCriteria searchCriteria = new NotificationFilterCriteria(null, false, "something");
            List<Notification> res = service.getNotifications("alice", searchCriteria);
            assertFalse(res.contains(nNulls));
        }
    }

    @Nested
    @DisplayName("Concurrency & Thread-Safety Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent reads, writes, and status updates maintain consistency")
        void testConcurrentOperations() throws InterruptedException {
            String testUser = "concurrent_user";
            int threadCount = 8;
            int opsPerThread = 25;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger idSeq = new AtomicInteger(1000);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            if (threadId % 2 == 0) {
                                Notification notif = new Notification(testUser, "Title " + i, "Msg " + i, "Offers");
                                notif.setId("N_CONC_" + idSeq.getAndIncrement());
                                dataService.addNotification(notif);
                            } else {
                                service.getNotifications(testUser);
                                service.markAllAsRead(testUser);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            List<Notification> finalNotifs = service.getNotifications(testUser);
            assertNotNull(finalNotifs);
            assertTrue(finalNotifs.size() > 0);
        }
    }
}
