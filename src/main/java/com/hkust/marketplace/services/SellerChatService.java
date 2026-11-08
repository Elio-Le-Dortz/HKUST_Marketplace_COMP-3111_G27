package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.util.Collections;
import java.util.List;

/**
 * Service providing chat and messaging operations for the Seller Portal (Task 1.5 & Task 2.x).
 * Enforces active seller session, data-store locking, and newest-first chronological ordering.
 */
public class SellerChatService {

    private final DummyDataService dataService;

    /**
     * Default constructor using the shared {@link DummyDataService} singleton.
     */
    public SellerChatService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Constructor allowing injection of a specific {@link DummyDataService} instance for testing.
     *
     * @param dataService the data service instance
     */
    public SellerChatService(DummyDataService dataService) {
        this.dataService = dataService != null ? dataService : DummyDataService.getInstance();
    }

    /**
     * Retrieves all chat threads in which the logged-in Seller participates, ordered newest first.
     *
     * @return list of chat threads for the current seller
     * @throws SecurityException if no active seller is logged in
     */
    public List<ChatThread> getMyChatThreads() {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            ChatService cs = dataService.getChatService();
            if (cs == null) {
                return Collections.emptyList();
            }
            return cs.getThreadsForUser(seller.getUsername());
        }
    }
}
