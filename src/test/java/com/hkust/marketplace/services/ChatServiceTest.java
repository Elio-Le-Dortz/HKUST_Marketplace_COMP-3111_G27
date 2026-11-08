package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatService Specification and Business Logic Tests (US-B06)")
class ChatServiceTest {

    private DummyDataService dataService;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        chatService = new ChatService(dataService);
        SessionManager.login(dataService.findUser("bob"));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Nested
    @DisplayName("Basic Flow Tests (Use Case 1.5 Basic Flow & Subflow S1)")
    class BasicFlowTests {

        @Test
        @DisplayName("getOrCreateThread creates a new thread when none exists and attaches listing title")
        void testCreateNewThread() {
            // Diana's lamp listing L102
            Listing lamp = dataService.findListing("L102");
            assertNotNull(lamp);

            ChatThread thread = chatService.getOrCreateThread(lamp.getId(), "alice");
            assertNotNull(thread);
            assertEquals("L102", thread.getListingId());
            assertEquals("alice", thread.getBuyerUsername());
            assertEquals("diana", thread.getSellerUsername());
            assertEquals(lamp.getTitle(), thread.getListingTitle());
            assertFalse(thread.hasMessages());

            // Second call returns existing thread
            ChatThread thread2 = chatService.getOrCreateThread(lamp.getId(), "alice");
            assertSame(thread, thread2);
        }

        @Test
        @DisplayName("getThreadById returns thread for participants and enforces privacy for non-participants")
        void testGetThreadByIdAndPrivacy() {
            ChatThread thread = chatService.getOrCreateThread("L100", "bob");
            assertNotNull(thread);

            // Authorized participants (buyer & seller)
            assertNotNull(chatService.getThreadById(thread.getId(), "bob"));
            assertNotNull(chatService.getThreadById(thread.getId(), "charlie"));

            // Non-participant
            assertNull(chatService.getThreadById(thread.getId(), "diana"),
                    "Privacy rule: non-participant should not be able to access private chat thread");

            // Null requesting user or non-existent thread
            assertNotNull(chatService.getThreadById(thread.getId(), null));
            assertNull(chatService.getThreadById("INVALID_ID", "bob"));
        }

        @Test
        @DisplayName("sendMessage persists message chronologically and sends notification to counterpart (Subflow S1)")
        void testSendMessageAndNotification() {
            Listing listing = dataService.findListing("L101");
            ChatThread thread = chatService.getOrCreateThread(listing.getId(), "alice");
            int initialNotifs = dataService.getNotificationsFor("charlie").size();

            // Buyer sends message
            SendMessageResult result = chatService.sendMessage(thread.getId(), "alice", "Is this charger original?");
            assertTrue(result.isSuccess());
            assertNotNull(result.getMessage());
            assertEquals("alice", result.getMessage().getSenderUsername());
            assertEquals("Is this charger original?", result.getMessage().getText());
            assertNotNull(result.getMessage().getSentDate());

            // Verify message in thread
            assertEquals(1, thread.getMessages().size());
            assertEquals(result.getMessage(), thread.getLastMessage());

            // Verify notification sent to seller (Subflow S1 Step 4)
            List<Notification> sellerNotifs = dataService.getNotificationsFor("charlie");
            assertEquals(initialNotifs + 1, sellerNotifs.size());
            Notification latestNotif = sellerNotifs.get(0);
            assertEquals("Chat", latestNotif.getCategory());
            assertTrue(latestNotif.getMessage().contains("Is this charger original?"));
            assertEquals(thread.getId(), latestNotif.getRelatedItemId(), "Notification must link to chat thread [F09]");
            assertEquals(NotificationTargetType.CHAT_THREAD.name(), latestNotif.getRelatedItemType(), "Notification target must be CHAT_THREAD [F09]");

            // Seller opens notification directly to reach the chat thread (F09 regression test)
            SessionManager.login(dataService.findUser("charlie"));
            SellerNotificationResult sellerOpenResult = new SellerNotificationService(dataService).openNotification(latestNotif.getId());
            assertEquals(SellerNotificationStatus.SUCCESS, sellerOpenResult.getStatus());
            assertSame(thread, sellerOpenResult.getChatThread());

            // Symmetrical: Seller replies to thread (Alternative Flow A4)
            int initialBuyerNotifs = dataService.getNotificationsFor("alice").size();
            SendMessageResult replyResult = chatService.sendMessage(thread.getId(), "charlie", "Yes, 100% genuine Apple!");
            assertTrue(replyResult.isSuccess());
            assertEquals(2, thread.getMessages().size());

            // Buyer receives notification
            List<Notification> buyerNotifs = dataService.getNotificationsFor("alice");
            assertEquals(initialBuyerNotifs + 1, buyerNotifs.size());
            Notification buyerNotif = buyerNotifs.get(0);
            assertEquals("Chat", buyerNotif.getCategory());
            assertEquals(thread.getId(), buyerNotif.getRelatedItemId(), "Buyer notification must link to chat thread [F09]");
            assertEquals(NotificationTargetType.CHAT_THREAD.name(), buyerNotif.getRelatedItemType(), "Buyer notification target must be CHAT_THREAD [F09]");

            // Buyer resolves notification target directly to the chat thread
            SessionManager.login(dataService.findUser("alice"));
            NotificationTargetResult buyerTargetResult = dataService.getBuyerNotificationService().resolveNotificationTarget("alice", buyerNotif.getId());
            assertEquals(NotificationTargetStatus.SUCCESS, buyerTargetResult.getStatus());
            assertSame(thread, buyerTargetResult.getTargetObject());
        }

        @Test
        @DisplayName("getThreadsForUser sorts threads chronologically by latest message")
        void testGetThreadsForUserSorting() {
            // Create two threads for alice
            ChatThread t1 = chatService.getOrCreateThread("L100", "alice");
            ChatThread t2 = chatService.getOrCreateThread("L101", "alice");

            chatService.sendMessage(t1.getId(), "alice", "Message in T1");
            chatService.sendMessage(t2.getId(), "alice", "Message in T2 (later)");

            List<ChatThread> threads = chatService.getThreadsForUser("alice");
            assertFalse(threads.isEmpty());
            // T2 has the more recent message
            assertEquals(t2.getId(), threads.get(0).getId());
        }
    }

    @Nested
    @DisplayName("Formal Offer Tests (Subflow S2)")
    class FormalOfferTests {

        @Test
        @DisplayName("sendFormalPriceOffer on SELL listing submits offer, appends structured message, and notifies seller")
        void testSendFormalPriceOfferSuccess() {
            Listing listing = dataService.findListing("L100"); // SELL listing
            ChatThread thread = chatService.getOrCreateThread(listing.getId(), "bob");
            int initialMsgCount = thread.getMessages().size();
            int initialOffers = dataService.getAllOffers().size();

            SendFormalOfferResult result = chatService.sendFormalPriceOffer(thread.getId(), "bob", 175.50);

            assertTrue(result.isSuccess());
            assertNotNull(result.getOffer());
            assertEquals(175.50, result.getOffer().getPrice());
            assertEquals(OfferStatus.PENDING, result.getOffer().getStatus());
            assertEquals(OfferType.PRICE_OFFER, result.getOffer().getType());

            // Structured message in thread (Subflow S2 Step 3)
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().isFormalOffer());
            assertTrue(result.getMessage().getText().contains("[FORMAL OFFER] Proposed Price: HK$ 175.50"));
            assertEquals(result.getOffer().getId(), result.getMessage().getOfferId());
            assertEquals(initialMsgCount + 1, thread.getMessages().size());

            // Confirmation message to Buyer (Subflow S2 Step 4)
            assertTrue(result.getConfirmationText().contains("175.50"));

            // Offer stored in service
            assertEquals(initialOffers + 1, dataService.getAllOffers().size());
        }

        @Test
        @DisplayName("sendFormalBorrowRequest on LEND listing submits borrow offer, appends structured message")
        void testSendFormalBorrowRequestSuccess() {
            Listing lendListing = dataService.findListing("L103"); // LEND listing
            ChatThread thread = chatService.getOrCreateThread(lendListing.getId(), "bob");

            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end = start.plusDays(7);

            SendFormalOfferResult result = chatService.sendFormalBorrowRequest(thread.getId(), "bob", start, end);

            assertTrue(result.isSuccess());
            assertNotNull(result.getOffer());
            assertEquals(OfferType.BORROW_REQUEST, result.getOffer().getType());
            assertEquals(OfferStatus.PENDING, result.getOffer().getStatus());

            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().isFormalOffer());
            assertTrue(result.getMessage().getText().contains("[FORMAL OFFER] Proposed Borrow Period"));
        }

        @Test
        @DisplayName("Formal offers with invalid price, invalid dates, or wrong listing type fail")
        void testFormalOfferValidations() {
            Listing sellListing = dataService.findListing("L100");
            ChatThread sellThread = chatService.getOrCreateThread(sellListing.getId(), "bob");

            // Zero or negative price
            SendFormalOfferResult zeroPrice = chatService.sendFormalPriceOffer(sellThread.getId(), "bob", 0.0);
            assertFalse(zeroPrice.isSuccess());
            assertEquals(ChatOperationStatus.INVALID_OFFER, zeroPrice.getStatus());

            SendFormalOfferResult negPrice = chatService.sendFormalPriceOffer(sellThread.getId(), "bob", -20.0);
            assertFalse(negPrice.isSuccess());

            // Borrow request on SELL listing
            SendFormalOfferResult borrowOnSell = chatService.sendFormalBorrowRequest(
                    sellThread.getId(), "bob", LocalDateTime.now(), LocalDateTime.now().plusDays(2));
            assertFalse(borrowOnSell.isSuccess());

            // Price offer on LEND listing
            Listing lendListing = dataService.findListing("L103");
            ChatThread lendThread = chatService.getOrCreateThread(lendListing.getId(), "bob");
            SendFormalOfferResult priceOnLend = chatService.sendFormalPriceOffer(lendThread.getId(), "bob", 100.0);
            assertFalse(priceOnLend.isSuccess());

            // Borrow request with duration < 5 seconds
            LocalDateTime now = LocalDateTime.now().plusMinutes(1);
            SendFormalOfferResult shortBorrow = chatService.sendFormalBorrowRequest(
                    lendThread.getId(), "bob", now, now.plusSeconds(3));
            assertFalse(shortBorrow.isSuccess());

            // Only buyer can make formal offer (seller cannot)
            SendFormalOfferResult sellerOffer = chatService.sendFormalPriceOffer(
                    sellThread.getId(), sellListing.getSellerUsername(), 120.0);
            assertFalse(sellerOffer.isSuccess());
            assertEquals(ChatOperationStatus.UNAUTHORIZED, sellerOffer.getStatus());
        }

        @Test
        @DisplayName("Formal offer on non-live listing fails with LISTING_NOT_LIVE")
        void testFormalOfferNonLiveListing() {
            Listing pendingListing = dataService.findListing("L104"); // PENDING_REVIEW
            ChatThread thread = new ChatThread("T99", pendingListing.getId(), "alice", pendingListing.getSellerUsername());
            dataService.addChatThread(thread);

            SendFormalOfferResult res = chatService.sendFormalPriceOffer("T99", "alice", 300.0);
            assertFalse(res.isSuccess());
            assertEquals(ChatOperationStatus.LISTING_NOT_LIVE, res.getStatus());
        }
    }

    @Nested
    @DisplayName("Search Chat History Tests (Subflow S3 & Alternative Flow A3)")
    class SearchChatHistoryTests {

        @Test
        @DisplayName("searchChatHistory finds matching messages case-insensitively (Subflow S3)")
        void testSearchMatches() {
            ChatThread thread = chatService.getOrCreateThread("L100", "bob");
            chatService.sendMessage(thread.getId(), "bob", "Hello Charlie, is the Stewart Calculus book available?");
            chatService.sendMessage(thread.getId(), "charlie", "Yes, Calculus 8th edition is available.");
            chatService.sendMessage(thread.getId(), "bob", "Can we meet at the Library tomorrow?");

            ChatSearchResult result = chatService.searchChatHistory(thread.getId(), "bob", "calculus");
            assertTrue(result.isSuccess());
            assertEquals(2, result.getMatchCount());
            assertEquals("calculus", result.getQuery());

            ChatSearchResult libraryResult = chatService.searchChatHistory(thread.getId(), "bob", "LIBRARY");
            assertTrue(libraryResult.isSuccess());
            assertEquals(1, libraryResult.getMatchCount());
        }

        @Test
        @DisplayName("searchChatHistory with empty or null query returns all messages")
        void testSearchEmptyQuery() {
            ChatThread thread = chatService.getOrCreateThread("L100", "bob");
            chatService.sendMessage(thread.getId(), "bob", "Msg 1");
            chatService.sendMessage(thread.getId(), "charlie", "Msg 2");

            ChatSearchResult nullResult = chatService.searchChatHistory(thread.getId(), "bob", null);
            assertTrue(nullResult.isSuccess());
            assertEquals(2, nullResult.getMatchCount());

            ChatSearchResult blankResult = chatService.searchChatHistory(thread.getId(), "bob", "   ");
            assertTrue(blankResult.isSuccess());
            assertEquals(2, blankResult.getMatchCount());
        }

        @Test
        @DisplayName("searchChatHistory with no matches returns NO_MATCHES status (Alternative Flow A3)")
        void testSearchNoMatches() {
            ChatThread thread = chatService.getOrCreateThread("L100", "bob");
            chatService.sendMessage(thread.getId(), "bob", "Only talking about textbooks here");

            ChatSearchResult result = chatService.searchChatHistory(thread.getId(), "bob", "motorcycle");
            assertFalse(result.isSuccess());
            assertEquals(ChatOperationStatus.NO_MATCHES, result.getStatus());
            assertEquals(0, result.getMatchCount());
            assertTrue(result.getMessage().contains("No messages found matching 'motorcycle'."));
        }

        @Test
        @DisplayName("searchChatHistory rejects unauthorized user")
        void testSearchUnauthorized() {
            ChatThread thread = chatService.getOrCreateThread("L100", "bob");
            ChatSearchResult result = chatService.searchChatHistory(thread.getId(), "diana", "book");
            assertFalse(result.isSuccess());
            assertEquals(ChatOperationStatus.UNAUTHORIZED, result.getStatus());
        }
    }

    @Nested
    @DisplayName("Alternative & Exception Flows")
    class AlternativeAndExceptionFlows {

        @Test
        @DisplayName("sendMessage with empty or whitespace-only text returns EMPTY_MESSAGE (Alternative Flow A1)")
        void testEmptyMessageValidation() {
            ChatThread thread = chatService.getOrCreateThread("L100", "bob");

            SendMessageResult emptyRes = chatService.sendMessage(thread.getId(), "bob", "");
            assertFalse(emptyRes.isSuccess());
            assertEquals(ChatOperationStatus.EMPTY_MESSAGE, emptyRes.getStatus());
            assertEquals("Message cannot be empty.", emptyRes.getErrorMessage());

            SendMessageResult wsRes = chatService.sendMessage(thread.getId(), "bob", "     \t\n  ");
            assertFalse(wsRes.isSuccess());
            assertEquals(ChatOperationStatus.EMPTY_MESSAGE, wsRes.getStatus());

            SendMessageResult nullRes = chatService.sendMessage(thread.getId(), "bob", null);
            assertFalse(nullRes.isSuccess());
            assertEquals(ChatOperationStatus.EMPTY_MESSAGE, nullRes.getStatus());
        }

        @Test
        @DisplayName("sendMessage to non-existent thread or by unauthorized user fails")
        void testSendMessageUnauthorizedAndNonExistent() {
            SendMessageResult notFound = chatService.sendMessage("NON_EXISTENT", "bob", "Hello");
            assertFalse(notFound.isSuccess());
            assertEquals(ChatOperationStatus.THREAD_NOT_FOUND, notFound.getStatus());

            ChatThread thread = chatService.getOrCreateThread("L100", "bob");
            SendMessageResult unauthorized = chatService.sendMessage(thread.getId(), "diana", "Intruder message");
            assertFalse(unauthorized.isSuccess());
            assertEquals(ChatOperationStatus.UNAUTHORIZED, unauthorized.getStatus());
        }

        @Test
        @DisplayName("Self-chat: buyer cannot create a chat thread on their own listing")
        void testSelfChatPrevented() {
            // Charlie is seller of L100
            Listing l = dataService.findListing("L100");
            assertEquals("charlie", l.getSellerUsername());

            ChatThread thread = chatService.getOrCreateThread(l.getId(), "charlie");
            assertNull(thread, "User should not be able to start a chat thread on their own listing");
        }

        @Test
        @DisplayName("getOrCreateThread with invalid parameters returns null")
        void testGetOrCreateThreadInvalidParams() {
            assertNull(chatService.getOrCreateThread(null, "alice"));
            assertNull(chatService.getOrCreateThread("L100", null));
            assertNull(chatService.getOrCreateThread("INVALID_LISTING", "alice"));
        }

        @Test
        @DisplayName("getThreadsForUser with null or empty username returns empty list")
        void testGetThreadsForUserEmpty() {
            assertTrue(chatService.getThreadsForUser(null).isEmpty());
            assertTrue(chatService.getThreadsForUser("   ").isEmpty());
        }

        @Test
        @DisplayName("Validation and branch coverage for empty string inputs and boundary conditions")
        void testEmptyStringInputs() {
            assertNull(chatService.getOrCreateThread("   ", "alice"));
            assertNull(chatService.getOrCreateThread("L100", "   "));

            assertNull(chatService.getThreadById("   ", "alice"));
            assertNotNull(chatService.getThreadById("T1", "   "));

            // Long message truncation in notification (> 50 chars)
            ChatThread thread = chatService.getOrCreateThread("L100", "bob");
            String longMessage = "This is a very long message that definitely exceeds fifty characters in length to test preview truncation.";
            SendMessageResult res = chatService.sendMessage(thread.getId(), "bob", longMessage);
            assertTrue(res.isSuccess());

            // Null and blank parameter branches for formal price offer
            assertFalse(chatService.sendFormalPriceOffer(null, "bob", 100.0).isSuccess());
            assertFalse(chatService.sendFormalPriceOffer("   ", "bob", 100.0).isSuccess());
            assertFalse(chatService.sendFormalPriceOffer(thread.getId(), null, 100.0).isSuccess());
            assertFalse(chatService.sendFormalPriceOffer(thread.getId(), "   ", 100.0).isSuccess());
            assertFalse(chatService.sendFormalPriceOffer("INVALID_ID", "bob", 100.0).isSuccess());
            assertFalse(chatService.sendFormalPriceOffer(thread.getId(), "bob", null).isSuccess());

            // Null and blank parameter branches for formal borrow request
            LocalDateTime now = LocalDateTime.now().plusDays(1);
            assertFalse(chatService.sendFormalBorrowRequest(null, "bob", now, now.plusDays(2)).isSuccess());
            assertFalse(chatService.sendFormalBorrowRequest("   ", "bob", now, now.plusDays(2)).isSuccess());
            assertFalse(chatService.sendFormalBorrowRequest(thread.getId(), null, now, now.plusDays(2)).isSuccess());
            assertFalse(chatService.sendFormalBorrowRequest(thread.getId(), "   ", now, now.plusDays(2)).isSuccess());
            assertFalse(chatService.sendFormalBorrowRequest("INVALID_ID", "bob", now, now.plusDays(2)).isSuccess());

            // Non-buyer formal borrow request attempt
            Listing lendListing = dataService.findListing("L103");
            ChatThread lendThread = chatService.getOrCreateThread(lendListing.getId(), "bob");
            assertFalse(chatService.sendFormalBorrowRequest(lendThread.getId(), "diana", now, now.plusDays(2)).isSuccess());

            // Search branch coverage
            assertFalse(chatService.searchChatHistory(null, "bob", "query").isSuccess());
            assertFalse(chatService.searchChatHistory("   ", "bob", "query").isSuccess());
            assertFalse(chatService.searchChatHistory("INVALID_ID", "bob", "query").isSuccess());

            // Search with message having null text
            ChatThread threadWithNullMsg = new ChatThread("T999", "L100", "bob", "charlie");
            threadWithNullMsg.addMessage(new Message("M999", "bob", null));
            dataService.addChatThread(threadWithNullMsg);
            ChatSearchResult nullMsgSearch = chatService.searchChatHistory("T999", "bob", "keyword");
            assertFalse(nullMsgSearch.isSuccess());

            // Sorter comparator branch coverage (null last messages, null dates)
            ChatThread emptyT1 = new ChatThread("T101", "L100", "alice", "charlie");
            emptyT1.setCreatedDate(null);
            ChatThread emptyT2 = new ChatThread("T102", "L101", "alice", "charlie");
            emptyT2.setCreatedDate(null);
            dataService.addChatThread(emptyT1);
            dataService.addChatThread(emptyT2);
            assertNotNull(chatService.getThreadsForUser("alice"));

            ChatThread dateT3 = new ChatThread("T103", "L100", "alice", "charlie");
            dateT3.setCreatedDate(LocalDateTime.now().minusDays(10));
            dataService.addChatThread(dateT3);
            assertNotNull(chatService.getThreadsForUser("alice"));
        }

        @Test
        @DisplayName("Default constructor uses DummyDataService singleton")
        void testDefaultConstructor() {
            ChatService defaultService = new ChatService();
            assertNotNull(defaultService);
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent messages from buyer and seller are safely persisted without race conditions")
        void testConcurrentMessages() throws InterruptedException, ExecutionException {
            ChatThread thread = chatService.getOrCreateThread("L100", "bob");
            int threadsCount = 10;
            int messagesPerThread = 5;

            ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
            List<Callable<Boolean>> tasks = new ArrayList<>();

            for (int i = 0; i < threadsCount; i++) {
                final int threadNum = i;
                final String sender = (threadNum % 2 == 0) ? "bob" : "charlie";
                tasks.add(() -> {
                    for (int j = 0; j < messagesPerThread; j++) {
                        SendMessageResult res = chatService.sendMessage(
                                thread.getId(), sender, "Concurrent msg " + threadNum + "-" + j);
                        if (!res.isSuccess()) {
                            return false;
                        }
                    }
                    return true;
                });
            }

            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(), "All concurrent message sends should succeed");
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(threadsCount * messagesPerThread, thread.getMessages().size());
        }
    }
}
