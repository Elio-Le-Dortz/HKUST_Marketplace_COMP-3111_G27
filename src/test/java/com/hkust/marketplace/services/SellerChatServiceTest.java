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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SellerChatService Tests [F09]")
class SellerChatServiceTest {

    private DummyDataService dataService;
    private SellerChatService sellerChatService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        sellerChatService = new SellerChatService(dataService);
        SessionManager.logout();
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("getMyChatThreads throws SecurityException when no user is logged in")
    void testRequiresSellerWhenLoggedOut() {
        assertThrows(SecurityException.class, () -> sellerChatService.getMyChatThreads());
    }

    @Test
    @DisplayName("getMyChatThreads throws SecurityException when buyer is logged in")
    void testRequiresSellerWhenBuyerLoggedIn() {
        SessionManager.login(dataService.findUser("alice"));
        assertThrows(SecurityException.class, () -> sellerChatService.getMyChatThreads());
    }

    @Test
    @DisplayName("getMyChatThreads throws SecurityException when admin is logged in")
    void testRequiresSellerWhenAdminLoggedIn() {
        SessionManager.login(dataService.findUser("admin"));
        assertThrows(SecurityException.class, () -> sellerChatService.getMyChatThreads());
    }

    @Test
    @DisplayName("getMyChatThreads returns empty list when seller has no conversations")
    void testEmptyThreadsWhenNoneExist() {
        Seller newSeller = new Seller("new_seller", "New Seller", "password123");
        dataService.registerUser(newSeller);

        SessionManager.login(newSeller);
        List<ChatThread> threads = sellerChatService.getMyChatThreads();
        assertNotNull(threads);
        assertTrue(threads.isEmpty());
    }

    @Test
    @DisplayName("getMyChatThreads returns only threads in which the logged-in seller participates")
    void testGetMyChatThreadsFiltersBySeller() {
        Seller charlie = (Seller) dataService.findUser("charlie");
        Seller diana = (Seller) dataService.findUser("diana");

        // Diana's listing L102 has a thread with alice
        ChatThread dianaThread = dataService.getChatService().getOrCreateThread("L102", "alice");

        SessionManager.login(diana);
        List<ChatThread> dianaThreads = sellerChatService.getMyChatThreads();
        assertFalse(dianaThreads.isEmpty());
        assertTrue(dianaThreads.stream().allMatch(t -> "diana".equalsIgnoreCase(t.getSellerUsername())));
        assertTrue(dianaThreads.stream().anyMatch(t -> t.getId().equals(dianaThread.getId())));

        SessionManager.login(charlie);
        List<ChatThread> charlieThreads = sellerChatService.getMyChatThreads();
        assertFalse(charlieThreads.isEmpty());
        assertTrue(charlieThreads.stream().allMatch(t -> "charlie".equalsIgnoreCase(t.getSellerUsername())));
        assertFalse(charlieThreads.stream().anyMatch(t -> t.getId().equals(dianaThread.getId())));
    }

    @Test
    @DisplayName("getMyChatThreads sorts threads by latest message descending")
    void testGetMyChatThreadsChronologicalOrder() throws InterruptedException {
        Seller charlie = (Seller) dataService.findUser("charlie");
        SessionManager.login(charlie);

        ChatThread t1 = dataService.getChatService().getOrCreateThread("L100", "bob");
        ChatThread t2 = dataService.getChatService().getOrCreateThread("L101", "bob");

        dataService.getChatService().sendMessage(t1.getId(), "bob", "Earlier message");
        Thread.sleep(10);
        dataService.getChatService().sendMessage(t2.getId(), "bob", "Later message");

        List<ChatThread> threads = sellerChatService.getMyChatThreads();
        assertTrue(threads.size() >= 2);
        int idxT2 = -1;
        int idxT1 = -1;
        for (int i = 0; i < threads.size(); i++) {
            if (threads.get(i).getId().equals(t2.getId())) idxT2 = i;
            if (threads.get(i).getId().equals(t1.getId())) idxT1 = i;
        }
        assertTrue(idxT2 != -1 && idxT1 != -1);
        assertTrue(idxT2 < idxT1, "Thread with later message must appear before thread with earlier message");
    }

    @Test
    @DisplayName("Default constructor uses DummyDataService singleton")
    void testDefaultConstructor() {
        SellerChatService defaultService = new SellerChatService();
        assertNotNull(defaultService);
    }

    @Test
    @DisplayName("A null data store means the shared singleton")
    void testNullDataStoreUsesSingleton() {
        SessionManager.login(dataService.findUser("charlie"));
        assertNotNull(new SellerChatService(null).getMyChatThreads());
    }

    // =====================================================================
    // F10: search the chat history (use case 1.5, subflow S3, alternative flow A3)
    // =====================================================================

    /** A conversation between alice and charlie about L105, with three messages. */
    private ChatThread conversation() {
        ChatThread thread = dataService.getChatService().getOrCreateThread("L105", "alice");
        dataService.getChatService().sendMessage(thread.getId(), "alice", "Hi, is the notebook set still available?");
        dataService.getChatService().sendMessage(thread.getId(), "charlie", "Yes, we can meet at the Library.");
        dataService.getChatService().sendMessage(thread.getId(), "alice", "Great, see you at the LIBRARY tomorrow.");
        return thread;
    }

    private static List<String> texts(ChatSearchResult result) {
        return result.getMatchingMessages().stream().map(Message::getText).toList();
    }

    @Nested
    @DisplayName("searchMyChat [F10]")
    class SearchMyChat {

        @Test
        @DisplayName("Finds every message containing the keyword, ignoring case, in chronological order")
        void findsMatchingMessages() {
            ChatThread thread = conversation();
            SessionManager.login(dataService.findUser("charlie"));

            ChatSearchResult result = sellerChatService.searchMyChat(thread.getId(), "  library ");

            assertEquals(ChatOperationStatus.SUCCESS, result.getStatus());
            assertEquals(2, result.getMatchCount());
            assertEquals(List.of("Yes, we can meet at the Library.", "Great, see you at the LIBRARY tomorrow."),
                    texts(result));
            assertEquals("library", result.getQuery());
        }

        @Test
        @DisplayName("Searches the whole history, including the oldest message")
        void searchesWholeHistory() {
            ChatThread thread = conversation();
            for (int i = 0; i < 30; i++) {
                dataService.getChatService().sendMessage(thread.getId(), i % 2 == 0 ? "alice" : "charlie", "Message " + i);
            }
            SessionManager.login(dataService.findUser("charlie"));

            ChatSearchResult result = sellerChatService.searchMyChat(thread.getId(), "notebook");

            assertEquals(List.of("Hi, is the notebook set still available?"), texts(result));
        }

        @Test
        @DisplayName("A3: no message matches the keyword")
        void noMatches() {
            ChatThread thread = conversation();
            SessionManager.login(dataService.findUser("charlie"));

            ChatSearchResult result = sellerChatService.searchMyChat(thread.getId(), "bicycle");

            assertEquals(ChatOperationStatus.NO_MATCHES, result.getStatus());
            assertFalse(result.isSuccess());
            assertEquals(0, result.getMatchCount());
        }

        @Test
        @DisplayName("A3: an empty conversation has no match")
        void emptyConversationHasNoMatch() {
            ChatThread thread = dataService.getChatService().getOrCreateThread("L105", "bob");
            SessionManager.login(dataService.findUser("charlie"));

            assertEquals(ChatOperationStatus.NO_MATCHES, sellerChatService.searchMyChat(thread.getId(), "hello").getStatus());
        }

        @Test
        @DisplayName("A blank keyword matches every message")
        void blankKeywordMatchesEverything() {
            ChatThread thread = conversation();
            SessionManager.login(dataService.findUser("charlie"));

            ChatSearchResult result = sellerChatService.searchMyChat(thread.getId(), "   ");

            assertTrue(result.isSuccess());
            assertEquals(3, result.getMatchCount());
        }

        @Test
        @DisplayName("Finds a message the buyer has just sent")
        void findsNewBuyerMessage() {
            ChatThread thread = conversation();
            SessionManager.login(dataService.findUser("charlie"));
            assertEquals(ChatOperationStatus.NO_MATCHES, sellerChatService.searchMyChat(thread.getId(), "discount").getStatus());

            dataService.getChatService().sendMessage(thread.getId(), "alice", "Any discount for students?");

            assertEquals(List.of("Any discount for students?"), texts(sellerChatService.searchMyChat(thread.getId(), "DISCOUNT")));
        }

        @Test
        @DisplayName("Another seller cannot search the conversation, and learns nothing about it")
        void otherSellerIsRefused() {
            ChatThread thread = conversation();
            SessionManager.login(dataService.findUser("diana"));

            ChatSearchResult result = sellerChatService.searchMyChat(thread.getId(), "library");

            assertEquals(ChatOperationStatus.UNAUTHORIZED, result.getStatus());
            assertEquals(SellerChatService.NOT_YOUR_THREAD, result.getMessage());
            assertTrue(result.getMatchingMessages().isEmpty());
        }

        @Test
        @DisplayName("An unknown, empty or null conversation identifier is reported")
        void unknownConversation() {
            SessionManager.login(dataService.findUser("charlie"));

            ChatSearchResult unknown = sellerChatService.searchMyChat("T9999", "x");
            assertEquals(ChatOperationStatus.THREAD_NOT_FOUND, unknown.getStatus());
            assertEquals(SellerChatService.THREAD_NOT_FOUND, unknown.getMessage());
            assertEquals(SellerChatService.THREAD_NOT_SELECTED, sellerChatService.searchMyChat(" ", "x").getMessage());
            assertEquals(SellerChatService.THREAD_NOT_SELECTED, sellerChatService.searchMyChat(null, "x").getMessage());
        }

        @Test
        @DisplayName("The identifier is trimmed")
        void identifierIsTrimmed() {
            ChatThread thread = conversation();
            SessionManager.login(dataService.findUser("charlie"));

            assertTrue(sellerChatService.searchMyChat("  " + thread.getId() + " ", "library").isSuccess());
        }

        @Test
        @DisplayName("Requires a Seller session: logged out, buyer and admin sessions are refused")
        void requiresSellerSession() {
            ChatThread thread = conversation();
            assertThrows(SecurityException.class, () -> sellerChatService.searchMyChat(thread.getId(), "library"));
            SessionManager.login(dataService.findUser("alice"));
            assertThrows(SecurityException.class, () -> sellerChatService.searchMyChat(thread.getId(), "library"));
            SessionManager.login(dataService.findUser("admin"));
            assertThrows(SecurityException.class, () -> sellerChatService.searchMyChat(thread.getId(), "library"));
        }
    }

    // =====================================================================
    // F10: send a message from the Seller Portal (use case 1.5, subflow S1)
    // =====================================================================

    @Nested
    @DisplayName("sendMessage [F10]")
    class SendMessage {

        @Test
        @DisplayName("Stores the message as sent by the logged-in seller and notifies the buyer with a link")
        void sendsAsSessionSeller() {
            ChatThread thread = dataService.getChatService().getOrCreateThread("L105", "alice");
            SessionManager.login(dataService.findUser("charlie"));

            SendMessageResult result = sellerChatService.sendMessage(thread.getId(), "  Still available!  ");

            assertTrue(result.isSuccess());
            Message last = thread.getLastMessage();
            assertEquals("charlie", last.getSenderUsername());
            assertEquals("Still available!", last.getText());
            assertNotNull(last.getSentDate());
            assertTrue(dataService.getNotificationsFor("alice").stream().anyMatch(n ->
                    thread.getId().equals(n.getRelatedItemId())
                            && NotificationTargetType.CHAT_THREAD.name().equals(n.getRelatedItemType())));
        }

        @Test
        @DisplayName("The buyer's search finds the seller's message (same thread on both sides)")
        void buyerSeesSellerMessage() {
            ChatThread thread = dataService.getChatService().getOrCreateThread("L105", "alice");
            SessionManager.login(dataService.findUser("charlie"));
            sellerChatService.sendMessage(thread.getId(), "Meet at the North Gate?");

            ChatSearchResult buyerSearch = dataService.getChatService().searchChatHistory(thread.getId(), "alice", "north gate");

            assertEquals(1, buyerSearch.getMatchCount());
        }

        @Test
        @DisplayName("A1: an empty message is refused and nothing is stored")
        void emptyMessageRefused() {
            ChatThread thread = dataService.getChatService().getOrCreateThread("L105", "alice");
            SessionManager.login(dataService.findUser("charlie"));

            SendMessageResult result = sellerChatService.sendMessage(thread.getId(), "   ");

            assertEquals(ChatOperationStatus.EMPTY_MESSAGE, result.getStatus());
            assertFalse(thread.hasMessages());
        }

        @Test
        @DisplayName("Another seller cannot write in the conversation")
        void otherSellerRefused() {
            ChatThread thread = dataService.getChatService().getOrCreateThread("L105", "alice");
            SessionManager.login(dataService.findUser("diana"));

            SendMessageResult result = sellerChatService.sendMessage(thread.getId(), "Hello");

            assertEquals(ChatOperationStatus.UNAUTHORIZED, result.getStatus());
            assertEquals(SellerChatService.NOT_YOUR_THREAD, result.getErrorMessage());
            assertFalse(thread.hasMessages());
        }

        @Test
        @DisplayName("An unknown conversation is reported")
        void unknownConversation() {
            SessionManager.login(dataService.findUser("charlie"));

            SendMessageResult result = sellerChatService.sendMessage("T9999", "Hello");

            assertEquals(ChatOperationStatus.THREAD_NOT_FOUND, result.getStatus());
            assertEquals(SellerChatService.THREAD_NOT_FOUND, result.getErrorMessage());
            assertEquals(SellerChatService.THREAD_NOT_SELECTED, sellerChatService.sendMessage(null, "Hello").getErrorMessage());
        }

        @Test
        @DisplayName("Requires a Seller session: a buyer cannot post as the seller through this service")
        void requiresSellerSession() {
            ChatThread thread = dataService.getChatService().getOrCreateThread("L105", "alice");
            assertThrows(SecurityException.class, () -> sellerChatService.sendMessage(thread.getId(), "Hi"));
            SessionManager.login(dataService.findUser("alice"));
            assertThrows(SecurityException.class, () -> sellerChatService.sendMessage(thread.getId(), "Hi"));
            assertFalse(thread.hasMessages());
        }
    }
}
