package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service encapsulating business rules and workflows for chat threads, private messaging,
 * formal offer attachments, and searchable message history (Use Case 1.5: Chat About Listing / US-B06).
 *
 * <p>Completely decoupled from JavaFX UI controls to support headless unit testing.
 */
public class ChatService {

    private static final DateTimeFormatter OFFER_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DummyDataService dataService;

    public ChatService() {
        this(DummyDataService.getInstance());
    }

    public ChatService(DummyDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Gets an existing chat thread between a buyer and seller for a listing, or creates one if it does not exist.
     * Enforces that a user cannot chat with themselves on their own listing.
     *
     * @param listingId     the listing ID
     * @param buyerUsername the username of the buyer initiating/viewing the chat
     * @return the {@link ChatThread}, or {@code null} if inputs invalid or buyer owns the listing
     */
    public ChatThread getOrCreateThread(String listingId, String buyerUsername) {
        synchronized (dataService) {
            if (listingId == null || listingId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
                return null;
            }

            Listing listing = dataService.findListing(listingId.trim());
            if (listing == null) {
                return null;
            }

            // Prevent self-chat on own listing
            if (listing.getSellerUsername() != null && listing.getSellerUsername().equalsIgnoreCase(buyerUsername.trim())) {
                return null;
            }

            return dataService.getOrCreateChatThread(listing.getId(), buyerUsername.trim(), listing.getSellerUsername());
        }
    }

    /**
     * Retrieves a chat thread by its ID, enforcing privacy checks.
     *
     * @param threadId           the thread ID
     * @param requestingUsername the username of the user requesting the thread
     * @return the {@link ChatThread}, or {@code null} if not found or user is not an authorized participant
     */
    public ChatThread getThreadById(String threadId, String requestingUsername) {
        synchronized (dataService) {
            if (threadId == null || threadId.trim().isEmpty()) {
                return null;
            }

            ChatThread thread = dataService.findChatThread(threadId.trim());
            if (thread == null) {
                return null;
            }

            if (requestingUsername != null && !requestingUsername.trim().isEmpty()
                    && !thread.isParticipant(requestingUsername.trim())) {
                return null; // Privacy enforcement: private to buyer and seller
            }

            return thread;
        }
    }

    /**
     * Retrieves all chat threads in which the given user participates (as buyer or seller),
     * sorted by latest message or thread creation date descending.
     *
     * @param username the username to query
     * @return a list of {@link ChatThread}s
     */
    public List<ChatThread> getThreadsForUser(String username) {
        synchronized (dataService) {
            if (username == null || username.trim().isEmpty()) {
                return Collections.emptyList();
            }

            List<ChatThread> userThreads = dataService.getChatThreadsForUser(username.trim());
            return userThreads.stream()
                    .sorted((t1, t2) -> {
                        LocalDateTime d1 = t1.getLastMessage() != null ? t1.getLastMessage().getSentDate() : t1.getCreatedDate();
                        LocalDateTime d2 = t2.getLastMessage() != null ? t2.getLastMessage().getSentDate() : t2.getCreatedDate();
                        if (d1 == null && d2 == null) return t2.getId() != null && t1.getId() != null ? t2.getId().compareTo(t1.getId()) : 0;
                        if (d1 == null) return 1;
                        if (d2 == null) return -1;
                        int cmp = d2.compareTo(d1);
                        if (cmp != 0) return cmp;
                        return t2.getId() != null && t1.getId() != null ? t2.getId().compareTo(t1.getId()) : 0;
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * Sends a free-text message in a chat thread (Subflow S1).
     * Enforces participant authorization, non-empty text validation (Alternative Flow A1),
     * timestamping, persistence, and counterpart notification.
     *
     * @param threadId       the chat thread ID
     * @param senderUsername the sender's username
     * @param text           the free-text message content
     * @return a {@link SendMessageResult} indicating success or failure
     */
    public SendMessageResult sendMessage(String threadId, String senderUsername, String text) {
        synchronized (dataService) {
            if (threadId == null || threadId.trim().isEmpty() || senderUsername == null || senderUsername.trim().isEmpty()) {
                return SendMessageResult.failure(ChatOperationStatus.THREAD_NOT_FOUND, "Thread ID and sender username are required.");
            }

            ChatThread thread = dataService.findChatThread(threadId.trim());
            if (thread == null) {
                return SendMessageResult.failure(ChatOperationStatus.THREAD_NOT_FOUND, "Chat thread not found.");
            }

            // Privacy check: only buyer or seller can post messages
            if (!thread.isParticipant(senderUsername.trim())) {
                return SendMessageResult.failure(ChatOperationStatus.UNAUTHORIZED, "You are not authorized to participate in this chat thread.");
            }

            // Alternative Flow A1: Empty message validation
            if (text == null || text.trim().isEmpty()) {
                return SendMessageResult.emptyMessage("Message cannot be empty.");
            }

            // Subflow S1 Step 3: Create, timestamp, and store message
            String messageId = "M" + dataService.nextMessageId();
            Message message = new Message(messageId, senderUsername.trim(), text.trim(), LocalDateTime.now());
            thread.addMessage(message);
            dataService.updateChatThread(thread);

            // Subflow S1 Step 4: Notify the counterpart participant
            String recipient = thread.getOtherParticipant(senderUsername.trim());
            if (recipient != null) {
                String preview = text.trim().length() > 50 ? text.trim().substring(0, 47) + "..." : text.trim();
                String notifMsg = "@" + senderUsername.trim() + " sent you a message: \"" + preview + "\"";
                Notification notification = new Notification(
                        recipient,
                        "New Chat Message",
                        notifMsg,
                        "Chat",
                        thread.getId(),
                        NotificationTargetType.CHAT_THREAD.name()
                );
                dataService.addNotification(notification);
            }

            return SendMessageResult.success(message);
        }
    }

    /**
     * Sends a formal price offer in a chat thread for a SELL listing (Subflow S2).
     * Validates buyer role, listing status, positive price amount, stores pending Offer,
     * appends structured message to the thread, and notifies the seller.
     *
     * @param threadId      the chat thread ID
     * @param buyerUsername the buyer's username
     * @param price         the proposed offer price in HKD
     * @return a {@link SendFormalOfferResult} indicating success or failure
     */
    public SendFormalOfferResult sendFormalPriceOffer(String threadId, String buyerUsername, Double price) {
        synchronized (dataService) {
            if (threadId == null || threadId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
                return SendFormalOfferResult.failure(ChatOperationStatus.THREAD_NOT_FOUND, "Thread ID and buyer username are required.");
            }

            ChatThread thread = dataService.findChatThread(threadId.trim());
            if (thread == null) {
                return SendFormalOfferResult.failure(ChatOperationStatus.THREAD_NOT_FOUND, "Chat thread not found.");
            }

            if (thread.getBuyerUsername() == null || !thread.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
                return SendFormalOfferResult.failure(ChatOperationStatus.UNAUTHORIZED, "Only the buyer can make formal offers in this chat thread.");
            }

            Listing listing = dataService.findListing(thread.getListingId());
            if (listing == null) {
                return SendFormalOfferResult.failure(ChatOperationStatus.LISTING_NOT_FOUND, "Listing not found.");
            }

            if (listing.getStatus() != ListingStatus.LIVE) {
                return SendFormalOfferResult.failure(ChatOperationStatus.LISTING_NOT_LIVE, "Listing is no longer available.");
            }

            if (listing.getType() != ListingType.SELL) {
                return SendFormalOfferResult.failure(ChatOperationStatus.INVALID_OFFER, "Formal price offers can only be made on items for Sell.");
            }

            if (price == null || price <= 0) {
                return SendFormalOfferResult.failure(ChatOperationStatus.INVALID_OFFER, "Proposed price must be greater than zero.");
            }

            // Delegate offer creation & seller notification to ActOnListingService via dataService
            ActOnListingResult offerResult = dataService.submitPriceOffer(listing.getId(), buyerUsername, price);
            if (!offerResult.isSuccess()) {
                return SendFormalOfferResult.failure(ChatOperationStatus.INVALID_OFFER, offerResult.getMessage());
            }

            Offer offer = offerResult.getOffer();

            // Subflow S2 Step 3: Append structured formal offer message to chat thread
            String structuredText = "[FORMAL OFFER] Proposed Price: HK$ " + String.format("%.2f", price);
            String msgId = "M" + dataService.nextMessageId();
            Message offerMsg = new Message(msgId, buyerUsername.trim(), structuredText, LocalDateTime.now(), offer.getId());
            thread.addMessage(offerMsg);
            dataService.updateChatThread(thread);

            // Subflow S2 Step 4: Confirm to Buyer
            String confirmMsg = "Your formal offer of HK$ " + String.format("%.2f", price) + " has been sent to " + listing.getSellerFullName() + ".";
            return SendFormalOfferResult.success(offer, offerMsg, confirmMsg);
        }
    }

    /**
     * Sends a formal borrow request in a chat thread for a LEND listing (Subflow S2).
     * Validates buyer role, listing status, borrow dates (>= 5s), stores pending Offer,
     * appends structured message to the thread, and notifies the seller.
     *
     * @param threadId      the chat thread ID
     * @param buyerUsername the buyer's username
     * @param startDate     the proposed start date/time
     * @param endDate       the proposed end date/time
     * @return a {@link SendFormalOfferResult} indicating success or failure
     */
    public SendFormalOfferResult sendFormalBorrowRequest(String threadId, String buyerUsername,
                                                        LocalDateTime startDate, LocalDateTime endDate) {
        synchronized (dataService) {
            if (threadId == null || threadId.trim().isEmpty() || buyerUsername == null || buyerUsername.trim().isEmpty()) {
                return SendFormalOfferResult.failure(ChatOperationStatus.THREAD_NOT_FOUND, "Thread ID and buyer username are required.");
            }

            ChatThread thread = dataService.findChatThread(threadId.trim());
            if (thread == null) {
                return SendFormalOfferResult.failure(ChatOperationStatus.THREAD_NOT_FOUND, "Chat thread not found.");
            }

            if (thread.getBuyerUsername() == null || !thread.getBuyerUsername().equalsIgnoreCase(buyerUsername.trim())) {
                return SendFormalOfferResult.failure(ChatOperationStatus.UNAUTHORIZED, "Only the buyer can make formal borrow requests in this chat thread.");
            }

            Listing listing = dataService.findListing(thread.getListingId());
            if (listing == null) {
                return SendFormalOfferResult.failure(ChatOperationStatus.LISTING_NOT_FOUND, "Listing not found.");
            }

            if (listing.getStatus() != ListingStatus.LIVE) {
                return SendFormalOfferResult.failure(ChatOperationStatus.LISTING_NOT_LIVE, "Listing is no longer available.");
            }

            if (listing.getType() != ListingType.LEND) {
                return SendFormalOfferResult.failure(ChatOperationStatus.INVALID_OFFER, "Borrow requests can only be made on items for Lend.");
            }

            // Delegate offer creation & seller notification to ActOnListingService via dataService
            ActOnListingResult offerResult = dataService.submitBorrowRequest(listing.getId(), buyerUsername, startDate, endDate);
            if (!offerResult.isSuccess()) {
                return SendFormalOfferResult.failure(ChatOperationStatus.INVALID_OFFER, offerResult.getMessage());
            }

            Offer offer = offerResult.getOffer();

            // Subflow S2 Step 3: Append structured formal offer message to chat thread
            String structuredText = "[FORMAL OFFER] Proposed Borrow Period: "
                    + startDate.format(OFFER_DATE_FMT) + " to " + endDate.format(OFFER_DATE_FMT);
            String msgId = "M" + dataService.nextMessageId();
            Message offerMsg = new Message(msgId, buyerUsername.trim(), structuredText, LocalDateTime.now(), offer.getId());
            thread.addMessage(offerMsg);
            dataService.updateChatThread(thread);

            // Subflow S2 Step 4: Confirm to Buyer
            String confirmMsg = "Your formal borrow request has been sent to " + listing.getSellerFullName() + ".";
            return SendFormalOfferResult.success(offer, offerMsg, confirmMsg);
        }
    }

    /**
     * Searches message history within a chat thread by keyword (Subflow S3).
     * Enforces participant authorization and performs case-insensitive substring matching.
     *
     * @param threadId           the chat thread ID
     * @param requestingUsername the username of the user requesting search
     * @param query              the search keyword
     * @return a {@link ChatSearchResult} containing matching messages or indicating no matches (Alternative Flow A3)
     */
    public ChatSearchResult searchChatHistory(String threadId, String requestingUsername, String query) {
        synchronized (dataService) {
            if (threadId == null || threadId.trim().isEmpty()) {
                return ChatSearchResult.failure(ChatOperationStatus.THREAD_NOT_FOUND, "Chat thread ID is required.");
            }

            ChatThread thread = dataService.findChatThread(threadId.trim());
            if (thread == null) {
                return ChatSearchResult.failure(ChatOperationStatus.THREAD_NOT_FOUND, "Chat thread not found.");
            }

            // Privacy check: only participants can search chat history
            if (requestingUsername != null && !requestingUsername.trim().isEmpty()
                    && !thread.isParticipant(requestingUsername.trim())) {
                return ChatSearchResult.failure(ChatOperationStatus.UNAUTHORIZED, "You are not authorized to view messages in this chat thread.");
            }

            if (query == null || query.trim().isEmpty()) {
                return ChatSearchResult.success("", thread.getMessages());
            }

            String keyword = query.trim().toLowerCase();
            List<Message> matches = new ArrayList<>();
            for (Message msg : thread.getMessages()) {
                if (msg.getText() != null && msg.getText().toLowerCase().contains(keyword)) {
                    matches.add(msg);
                }
            }

            // Alternative Flow A3: No search results
            if (matches.isEmpty()) {
                return ChatSearchResult.noMatches(query.trim());
            }

            // Subflow S3 Step 2: Show search results
            return ChatSearchResult.success(query.trim(), matches);
        }
    }
}
