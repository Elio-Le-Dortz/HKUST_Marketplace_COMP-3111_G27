package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
        dataService.resetToSeedData();
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
}
