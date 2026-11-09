package com.hkust.marketplace.services;

import com.hkust.marketplace.models.ChatOperationStatus;
import com.hkust.marketplace.models.ChatSearchResult;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SendMessageResult;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.util.Collections;
import java.util.List;

/**
 * Chat of the Seller Portal, the Seller's side of use case 1.5 "Chat About Listing" (the only use
 * case shared by the Buyer and the Seller): the Seller lists their conversations with buyers
 * (F09), sends messages (subflow S1) and searches the history of a conversation (subflow S3,
 * alternative flow A3, F10).
 *
 * <p><b>Access control.</b> Every method first calls {@link SessionManager#requireSeller()}, so it
 * can only be used during an active Seller session (use case 2.2 special requirement), and the
 * Seller is always the logged-in one, never a username given by the caller. A Seller can only
 * read, search or write in a conversation about one of their own listings (use case 1.5 special
 * requirement: each thread is private to its buyer and seller).</p>
 *
 * <p><b>Atomicity.</b> Each method runs while holding the lock of the data store
 * ({@link DummyDataService}), which {@link ChatService} also takes, so a message is never added
 * to a thread while it is being searched.</p>
 *
 * <p>Messages are stored, timestamped, persisted and notified to the buyer by
 * {@link ChatService}, exactly as for the buyer's messages. This class does not use JavaFX, so
 * every flow is unit tested without a display.</p>
 */
public class SellerChatService {

    /** Message when no conversation identifier is given. */
    public static final String THREAD_NOT_SELECTED = "No conversation was selected.";
    /** Message when the conversation does not exist. */
    public static final String THREAD_NOT_FOUND = "This conversation no longer exists.";
    /** Message when the conversation is about another Seller's listing. */
    public static final String NOT_YOUR_THREAD = "This conversation is not about one of your listings.";

    /** The store of chat threads. */
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
     * @param dataService the data service instance; {@code null} means the shared singleton
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

    /**
     * Sends a message from the logged-in Seller in one of their conversations (use case 1.5,
     * subflow S1). The message is stored with a timestamp and the buyer is notified, with a link
     * to the conversation.
     *
     * @param threadId identifier of the conversation
     * @param text     the message; it cannot be empty (alternative flow A1)
     * @return the sent message; or a failure: the conversation does not exist or is about
     *         another Seller's listing, or the message is empty
     * @throws SecurityException if no active Seller is logged in
     */
    public SendMessageResult sendMessage(String threadId, String text) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            ChatThread thread = dataService.findChatThread(threadId == null ? null : threadId.trim());
            ChatOperationStatus problem = problemOf(threadId, thread, seller);
            if (problem != null) {
                return SendMessageResult.failure(problem, messageOf(threadId, problem));
            }
            return dataService.getChatService().sendMessage(thread.getId(), seller.getUsername(), text);
        }
    }

    /**
     * Searches the messages of one of the logged-in Seller's conversations for a keyword (use case
     * 1.5, subflow S3), ignoring case. The whole history is searched, since messages are never
     * deleted.
     *
     * @param threadId identifier of the conversation
     * @param query    the keyword; trimmed. A blank keyword matches every message
     * @return the matching messages, in chronological order; a
     *         {@link ChatOperationStatus#NO_MATCHES} result if no message contains the keyword
     *         (alternative flow A3); or a failure if the conversation does not exist or is about
     *         another Seller's listing
     * @throws SecurityException if no active Seller is logged in
     */
    public ChatSearchResult searchMyChat(String threadId, String query) {
        Seller seller = SessionManager.requireSeller();
        synchronized (dataService) {
            ChatThread thread = dataService.findChatThread(threadId == null ? null : threadId.trim());
            ChatOperationStatus problem = problemOf(threadId, thread, seller);
            if (problem != null) {
                return ChatSearchResult.failure(problem, messageOf(threadId, problem));
            }
            return dataService.getChatService().searchChatHistory(thread.getId(), seller.getUsername(), query);
        }
    }

    /**
     * Tells why the Seller cannot use a conversation.
     *
     * @param threadId the identifier given by the caller
     * @param thread   the conversation found for it, {@code null} if none
     * @param seller   the logged-in Seller
     * @return {@code null} if the conversation is about one of the Seller's listings, otherwise
     *         {@link ChatOperationStatus#THREAD_NOT_FOUND} or {@link ChatOperationStatus#UNAUTHORIZED}
     */
    private static ChatOperationStatus problemOf(String threadId, ChatThread thread, Seller seller) {
        if (threadId == null || threadId.isBlank() || thread == null) {
            return ChatOperationStatus.THREAD_NOT_FOUND;
        }
        if (!seller.getUsername().equalsIgnoreCase(thread.getSellerUsername())) {
            return ChatOperationStatus.UNAUTHORIZED;
        }
        return null;
    }

    /**
     * Returns the message explaining a problem found by {@link #problemOf}.
     *
     * @param threadId the identifier given by the caller
     * @param problem  the problem
     * @return the message for the Seller
     */
    private static String messageOf(String threadId, ChatOperationStatus problem) {
        if (problem == ChatOperationStatus.UNAUTHORIZED) {
            return NOT_YOUR_THREAD;
        }
        return threadId == null || threadId.isBlank() ? THREAD_NOT_SELECTED : THREAD_NOT_FOUND;
    }
}
