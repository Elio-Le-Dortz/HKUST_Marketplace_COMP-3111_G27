package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Chat Integration Tests (US-B06)")
class DummyDataServiceChatTest {

    private DummyDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
    }

    @Test
    @DisplayName("Seed data initializes chat thread between alice and charlie with 4 messages")
    void testSeedChatData() {
        List<ChatThread> threads = dataService.getAllChatThreads();
        assertFalse(threads.isEmpty());

        ChatThread seedThread = threads.get(0);
        assertEquals("alice", seedThread.getBuyerUsername());
        assertEquals("charlie", seedThread.getSellerUsername());
        assertEquals("L100", seedThread.getListingId());
        assertEquals(4, seedThread.getMessages().size());

        // Check formal offer in seed data
        Message offerMsg = seedThread.getMessages().get(2);
        assertTrue(offerMsg.isFormalOffer());
        assertNotNull(offerMsg.getOfferId());
        assertTrue(offerMsg.getText().contains("[FORMAL OFFER]"));
    }

    @Test
    @DisplayName("getChatThreadsForUser retrieves threads for both buyer and seller")
    void testGetChatThreadsForUser() {
        List<ChatThread> aliceThreads = dataService.getChatThreadsForUser("alice");
        assertEquals(1, aliceThreads.size());

        List<ChatThread> charlieThreads = dataService.getChatThreadsForUser("charlie");
        assertEquals(1, charlieThreads.size());

        List<ChatThread> bobThreads = dataService.getChatThreadsForUser("bob");
        assertTrue(bobThreads.isEmpty());
    }

    @Test
    @DisplayName("findChatThread finds thread by ID or returns null if absent")
    void testFindChatThread() {
        List<ChatThread> threads = dataService.getAllChatThreads();
        String id = threads.get(0).getId();

        ChatThread found = dataService.findChatThread(id);
        assertNotNull(found);
        assertEquals(id, found.getId());

        assertNull(dataService.findChatThread("NON_EXISTENT_ID"));
        assertNull(dataService.findChatThread(null));
    }

    @Test
    @DisplayName("addChatThread auto-generates ID if missing")
    void testAddChatThreadAutoId() {
        ChatThread thread = new ChatThread();
        thread.setListingId("L102");
        thread.setBuyerUsername("bob");
        thread.setSellerUsername("diana");

        dataService.addChatThread(thread);
        assertNotNull(thread.getId());
        assertTrue(thread.getId().startsWith("T"));
    }

    @Test
    @DisplayName("resetData restores initial chat threads and counters")
    void testResetDataRestoresChat() {
        dataService.getAllChatThreads().clear();
        dataService.resetData();

        List<ChatThread> threads = dataService.getAllChatThreads();
        assertFalse(threads.isEmpty());
        assertTrue(dataService.nextMessageId() > 1);
        assertTrue(dataService.nextThreadId() > 1);
    }
}
