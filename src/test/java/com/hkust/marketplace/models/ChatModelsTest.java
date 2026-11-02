package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Chat Domain Models and Result Objects Tests (US-B06)")
class ChatModelsTest {

    @Test
    @DisplayName("ChatOperationStatus enum contains all expected values")
    void testChatOperationStatus() {
        assertEquals(9, ChatOperationStatus.values().length);
        assertNotNull(ChatOperationStatus.valueOf("SUCCESS"));
        assertNotNull(ChatOperationStatus.valueOf("EMPTY_MESSAGE"));
        assertNotNull(ChatOperationStatus.valueOf("THREAD_NOT_FOUND"));
        assertNotNull(ChatOperationStatus.valueOf("UNAUTHORIZED"));
        assertNotNull(ChatOperationStatus.valueOf("LISTING_NOT_FOUND"));
        assertNotNull(ChatOperationStatus.valueOf("LISTING_NOT_LIVE"));
        assertNotNull(ChatOperationStatus.valueOf("INVALID_OFFER"));
        assertNotNull(ChatOperationStatus.valueOf("SELF_CHAT_NOT_ALLOWED"));
        assertNotNull(ChatOperationStatus.valueOf("NO_MATCHES"));
    }

    @Test
    @DisplayName("SendMessageResult factory methods and getters")
    void testSendMessageResult() {
        Message msg = new Message("M1", "alice", "Hello");
        SendMessageResult successResult = SendMessageResult.success(msg);
        assertTrue(successResult.isSuccess());
        assertEquals(ChatOperationStatus.SUCCESS, successResult.getStatus());
        assertEquals(msg, successResult.getMessage());
        assertNull(successResult.getErrorMessage());

        SendMessageResult emptyResult = SendMessageResult.emptyMessage("Message cannot be empty.");
        assertFalse(emptyResult.isSuccess());
        assertEquals(ChatOperationStatus.EMPTY_MESSAGE, emptyResult.getStatus());
        assertNull(emptyResult.getMessage());
        assertEquals("Message cannot be empty.", emptyResult.getErrorMessage());

        SendMessageResult failResult = SendMessageResult.failure(ChatOperationStatus.UNAUTHORIZED, "Access denied");
        assertFalse(failResult.isSuccess());
        assertEquals(ChatOperationStatus.UNAUTHORIZED, failResult.getStatus());
        assertEquals("Access denied", failResult.getErrorMessage());
    }

    @Test
    @DisplayName("SendFormalOfferResult factory methods and getters")
    void testSendFormalOfferResult() {
        Offer offer = new Offer();
        offer.setId("OFF1");
        Message msg = new Message("M1", "alice", "[FORMAL OFFER] Price: HK$ 100", LocalDateTime.now(), "OFF1");
        SendFormalOfferResult success = SendFormalOfferResult.success(offer, msg, "Offer sent successfully.");

        assertTrue(success.isSuccess());
        assertEquals(ChatOperationStatus.SUCCESS, success.getStatus());
        assertEquals(offer, success.getOffer());
        assertEquals(msg, success.getMessage());
        assertEquals("Offer sent successfully.", success.getConfirmationText());
        assertNull(success.getErrorMessage());

        SendFormalOfferResult fail = SendFormalOfferResult.failure(ChatOperationStatus.INVALID_OFFER, "Price must be > 0");
        assertFalse(fail.isSuccess());
        assertEquals(ChatOperationStatus.INVALID_OFFER, fail.getStatus());
        assertNull(fail.getOffer());
        assertNull(fail.getMessage());
        assertEquals("Price must be > 0", fail.getErrorMessage());
    }

    @Test
    @DisplayName("ChatSearchResult factory methods, match counting, and edge cases")
    void testChatSearchResult() {
        Message m1 = new Message("M1", "alice", "Calculus textbook");
        Message m2 = new Message("M2", "charlie", "Yes, textbook is here");

        ChatSearchResult success = ChatSearchResult.success("textbook", Arrays.asList(m1, m2));
        assertTrue(success.isSuccess());
        assertEquals(ChatOperationStatus.SUCCESS, success.getStatus());
        assertEquals("textbook", success.getQuery());
        assertEquals(2, success.getMatchCount());
        assertEquals(2, success.getMatchingMessages().size());
        assertEquals("Found 2 matching messages.", success.getMessage());

        ChatSearchResult singleMatch = ChatSearchResult.success("Calculus", Collections.singletonList(m1));
        assertEquals(1, singleMatch.getMatchCount());
        assertEquals("Found 1 matching message.", singleMatch.getMessage());

        ChatSearchResult noMatch = ChatSearchResult.noMatches("laptop");
        assertFalse(noMatch.isSuccess());
        assertEquals(ChatOperationStatus.NO_MATCHES, noMatch.getStatus());
        assertEquals("laptop", noMatch.getQuery());
        assertEquals(0, noMatch.getMatchCount());
        assertTrue(noMatch.getMatchingMessages().isEmpty());
        assertEquals("No messages found matching 'laptop'.", noMatch.getMessage());

        ChatSearchResult fail = ChatSearchResult.failure(ChatOperationStatus.THREAD_NOT_FOUND, "Thread not found.");
        assertFalse(fail.isSuccess());
        assertEquals(ChatOperationStatus.THREAD_NOT_FOUND, fail.getStatus());
        assertEquals("Thread not found.", fail.getMessage());
        assertEquals(0, fail.getMatchCount());

        ChatSearchResult nullListSuccess = ChatSearchResult.success("query", null);
        assertEquals(0, nullListSuccess.getMatchCount());
        assertTrue(nullListSuccess.getMatchingMessages().isEmpty());
    }

    @Test
    @DisplayName("ChatThread methods: participants, last message, and empty checks")
    void testChatThreadMethods() {
        ChatThread thread = new ChatThread("T1", "L100", "alice", "charlie");
        thread.setListingTitle("Calculus Book");

        assertEquals("T1", thread.getId());
        assertEquals("L100", thread.getListingId());
        assertEquals("Calculus Book", thread.getListingTitle());
        assertEquals("alice", thread.getBuyerUsername());
        assertEquals("charlie", thread.getSellerUsername());
        assertNotNull(thread.getCreatedDate());

        assertFalse(thread.hasMessages());
        assertNull(thread.getLastMessage());

        assertTrue(thread.isParticipant("alice"));
        assertTrue(thread.isParticipant("ALICE"));
        assertTrue(thread.isParticipant("  alice  "));
        assertTrue(thread.isParticipant("charlie"));
        assertTrue(thread.isParticipant("CHARLIE"));
        assertFalse(thread.isParticipant("bob"));
        assertFalse(thread.isParticipant(null));

        assertEquals("charlie", thread.getOtherParticipant("alice"));
        assertEquals("charlie", thread.getOtherParticipant("ALICE"));
        assertEquals("alice", thread.getOtherParticipant("charlie"));
        assertEquals("alice", thread.getOtherParticipant("CHARLIE"));
        assertNull(thread.getOtherParticipant("bob"));
        assertNull(thread.getOtherParticipant(null));

        // Add message and verify lastMessage & hasMessages
        Message msg1 = new Message("M1", "alice", "First message");
        thread.addMessage(msg1);
        assertTrue(thread.hasMessages());
        assertEquals(msg1, thread.getLastMessage());

        Message msg2 = new Message("M2", "charlie", "Second message");
        thread.addMessage(msg2);
        assertEquals(2, thread.getMessages().size());
        assertEquals(msg2, thread.getLastMessage());

        // Null message addition is safely ignored
        thread.addMessage(null);
        assertEquals(2, thread.getMessages().size());

        // Setter tests
        thread.setMessages(null);
        assertNotNull(thread.getMessages());
        assertTrue(thread.getMessages().isEmpty());

        LocalDateTime created = LocalDateTime.now().minusDays(2);
        thread.setCreatedDate(created);
        assertEquals(created, thread.getCreatedDate());

        thread.setId("T2");
        assertEquals("T2", thread.getId());
        thread.setListingId("L200");
        assertEquals("L200", thread.getListingId());
        thread.setBuyerUsername("bob");
        assertEquals("bob", thread.getBuyerUsername());
        thread.setSellerUsername("diana");
        assertEquals("diana", thread.getSellerUsername());
    }

    @Test
    @DisplayName("Message model: constructors, formal offer detection, and accessors")
    void testMessageModel() {
        LocalDateTime sent = LocalDateTime.now().minusMinutes(5);
        Message m1 = new Message("M10", "alice", "Regular message", sent);
        assertEquals("M10", m1.getId());
        assertEquals("alice", m1.getSenderUsername());
        assertEquals("Regular message", m1.getText());
        assertEquals(sent, m1.getSentDate());
        assertNull(m1.getOfferId());
        assertFalse(m1.isFormalOffer());

        Message m2 = new Message("M11", "alice", "[FORMAL OFFER] Proposed Price: HK$ 150.00", sent, "OFF100");
        assertEquals("OFF100", m2.getOfferId());
        assertTrue(m2.isFormalOffer());

        Message m3 = new Message();
        m3.setId("M12");
        m3.setSenderUsername("bob");
        m3.setText("[FORMAL OFFER] Proposed Borrow Period");
        m3.setSentDate(sent);
        assertTrue(m3.isFormalOffer());

        m3.setText("Just a regular chat");
        assertFalse(m3.isFormalOffer());

        m3.setOfferId("OFF200");
        assertTrue(m3.isFormalOffer());

        Message m4 = new Message("alice", "Short constructor message");
        assertEquals("alice", m4.getSenderUsername());
        assertEquals("Short constructor message", m4.getText());
        assertNotNull(m4.getSentDate());
    }
}
