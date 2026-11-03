package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-B09: Notification Domain Models Tests")
public class NotificationModelsTest {

    @Test
    @DisplayName("Notification: constructors, getters, setters, and read status")
    void testNotificationBasics() {
        Notification n = new Notification();
        assertNotNull(n.getTimestamp());
        assertFalse(n.isRead());
        assertTrue(n.isUnread());

        n.setId("N99");
        n.setUsername("alice");
        n.setTitle("Title");
        n.setMessage("Message body");
        n.setCategory("Offers");
        n.setRelatedItemId("O701");
        n.setRelatedItemType("OFFER");

        assertEquals("N99", n.getId());
        assertEquals("alice", n.getUsername());
        assertEquals("alice", n.getRecipientUsername());
        assertEquals("Title", n.getTitle());
        assertEquals("Message body", n.getMessage());
        assertEquals("Offers", n.getCategory());
        assertEquals("O701", n.getRelatedItemId());
        assertEquals("OFFER", n.getRelatedItemType());

        n.setRecipientUsername("bob");
        assertEquals("bob", n.getUsername());

        n.setRead(true);
        assertTrue(n.isRead());
        assertFalse(n.isUnread());

        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 14, 30);
        n.setTimestamp(now);
        assertEquals(now, n.getTimestamp());
        assertTrue(n.getFormattedTimestamp().contains("03 Nov 2026 14:30"));

        Notification nNullTime = new Notification();
        nNullTime.setTimestamp(null);
        assertEquals("", nNullTime.getFormattedTimestamp());

        Notification nFull = new Notification("alice", "Offer Update", "Body", "Offers", "O1", "OFFER");
        assertEquals("alice", nFull.getUsername());
        assertEquals("Offer Update", nFull.getTitle());
        assertEquals("Body", nFull.getMessage());
        assertEquals("Offers", nFull.getCategory());
        assertEquals("O1", nFull.getRelatedItemId());
        assertEquals("OFFER", nFull.getRelatedItemType());
    }

    @Test
    @DisplayName("NotificationCategory: enum values, aliases, fromRaw mapping, and matches")
    void testNotificationCategory() {
        assertEquals("Offers", NotificationCategory.OFFERS.getDisplayName());
        assertEquals("Chat", NotificationCategory.CHAT.getDisplayName());
        assertEquals("Borrow Reminders", NotificationCategory.BORROW_REMINDERS.getDisplayName());
        assertEquals("Listings", NotificationCategory.LISTINGS.getDisplayName());
        assertEquals("Reviews", NotificationCategory.REVIEWS.getDisplayName());
        assertEquals("Announcements", NotificationCategory.ANNOUNCEMENTS.getDisplayName());

        assertNotNull(NotificationCategory.OFFERS.getIcon());
        assertFalse(NotificationCategory.OFFERS.getAliases().isEmpty());

        // Exact and alias matching
        assertSame(NotificationCategory.OFFERS, NotificationCategory.fromRaw("Offers"));
        assertSame(NotificationCategory.OFFERS, NotificationCategory.fromRaw("offer"));
        assertSame(NotificationCategory.OFFERS, NotificationCategory.fromRaw("DEAL"));

        assertSame(NotificationCategory.CHAT, NotificationCategory.fromRaw("Chat"));
        assertSame(NotificationCategory.CHAT, NotificationCategory.fromRaw("Message"));

        assertSame(NotificationCategory.BORROW_REMINDERS, NotificationCategory.fromRaw("Borrow Reminders"));
        assertSame(NotificationCategory.BORROW_REMINDERS, NotificationCategory.fromRaw("Loan"));
        assertSame(NotificationCategory.BORROW_REMINDERS, NotificationCategory.fromRaw("Borrow"));
        assertSame(NotificationCategory.BORROW_REMINDERS, NotificationCategory.fromRaw("Due Date"));

        assertSame(NotificationCategory.LISTINGS, NotificationCategory.fromRaw("Listings"));
        assertSame(NotificationCategory.LISTINGS, NotificationCategory.fromRaw("Listing"));
        assertSame(NotificationCategory.LISTINGS, NotificationCategory.fromRaw("Item"));

        assertSame(NotificationCategory.REVIEWS, NotificationCategory.fromRaw("Reviews"));
        assertSame(NotificationCategory.REVIEWS, NotificationCategory.fromRaw("Review"));
        assertSame(NotificationCategory.REVIEWS, NotificationCategory.fromRaw("Rating"));
        assertSame(NotificationCategory.REVIEWS, NotificationCategory.fromRaw("Feedback"));

        assertSame(NotificationCategory.ANNOUNCEMENTS, NotificationCategory.fromRaw("Announcements"));
        assertSame(NotificationCategory.ANNOUNCEMENTS, NotificationCategory.fromRaw("System"));
        assertSame(NotificationCategory.ANNOUNCEMENTS, NotificationCategory.fromRaw("Reports"));
        assertSame(NotificationCategory.ANNOUNCEMENTS, NotificationCategory.fromRaw(null));
        assertSame(NotificationCategory.ANNOUNCEMENTS, NotificationCategory.fromRaw("   "));
        assertSame(NotificationCategory.ANNOUNCEMENTS, NotificationCategory.fromRaw("RandomUnknownCategory"));

        // matches()
        assertTrue(NotificationCategory.OFFERS.matches("Offer"));
        assertTrue(NotificationCategory.OFFERS.matches("Offers"));
        assertFalse(NotificationCategory.OFFERS.matches("Chat"));
        assertTrue(NotificationCategory.CHAT.matches("new message"));
        assertTrue(NotificationCategory.BORROW_REMINDERS.matches("Loan overdue"));
        assertTrue(NotificationCategory.LISTINGS.matches("listing update"));
        assertTrue(NotificationCategory.REVIEWS.matches("review received"));
    }

    @Test
    @DisplayName("NotificationTargetType: enum values and fromString parsing")
    void testNotificationTargetType() {
        assertSame(NotificationTargetType.OFFER, NotificationTargetType.fromString("OFFER"));
        assertSame(NotificationTargetType.CHAT_THREAD, NotificationTargetType.fromString("CHAT_THREAD"));
        assertSame(NotificationTargetType.DEAL, NotificationTargetType.fromString("DEAL"));
        assertSame(NotificationTargetType.LISTING, NotificationTargetType.fromString("LISTING"));
        assertSame(NotificationTargetType.REVIEW, NotificationTargetType.fromString("REVIEW"));
        assertSame(NotificationTargetType.ANNOUNCEMENT, NotificationTargetType.fromString("ANNOUNCEMENT"));
        assertSame(NotificationTargetType.NONE, NotificationTargetType.fromString("NONE"));

        assertSame(NotificationTargetType.OFFER, NotificationTargetType.fromString("offer"));
        assertSame(NotificationTargetType.CHAT_THREAD, NotificationTargetType.fromString("chat"));
        assertSame(NotificationTargetType.DEAL, NotificationTargetType.fromString("deal"));
        assertSame(NotificationTargetType.DEAL, NotificationTargetType.fromString("loan"));
        assertSame(NotificationTargetType.LISTING, NotificationTargetType.fromString("listing"));
        assertSame(NotificationTargetType.REVIEW, NotificationTargetType.fromString("rating"));
        assertSame(NotificationTargetType.NONE, NotificationTargetType.fromString(null));
        assertSame(NotificationTargetType.NONE, NotificationTargetType.fromString(""));
        assertSame(NotificationTargetType.NONE, NotificationTargetType.fromString("unknown"));
    }

    @Test
    @DisplayName("NotificationTargetResult: status and factory methods")
    void testNotificationTargetResult() {
        Notification n = new Notification("alice", "T", "M", "Offers");

        NotificationTargetResult success = NotificationTargetResult.success(n, NotificationTargetType.OFFER, "SampleOfferObj");
        assertTrue(success.isSuccess());
        assertFalse(success.isItemDeleted());
        assertSame(NotificationTargetStatus.SUCCESS, success.getStatus());
        assertSame(NotificationTargetType.OFFER, success.getTargetType());
        assertEquals("SampleOfferObj", success.getTargetObject());
        assertSame(n, success.getNotification());
        assertNull(success.getMessage());

        NotificationTargetResult deleted = NotificationTargetResult.itemDeleted(n, NotificationTargetType.LISTING, "Item deleted");
        assertFalse(deleted.isSuccess());
        assertTrue(deleted.isItemDeleted());
        assertSame(NotificationTargetStatus.ITEM_DELETED, deleted.getStatus());
        assertSame(NotificationTargetType.LISTING, deleted.getTargetType());
        assertNull(deleted.getTargetObject());
        assertEquals("Item deleted", deleted.getMessage());

        NotificationTargetResult noLink = NotificationTargetResult.noLinkedItem(n, "No link");
        assertFalse(noLink.isSuccess());
        assertFalse(noLink.isItemDeleted());
        assertSame(NotificationTargetStatus.NO_LINKED_ITEM, noLink.getStatus());
        assertSame(NotificationTargetType.NONE, noLink.getTargetType());
        assertEquals("No link", noLink.getMessage());

        NotificationTargetResult notFound = NotificationTargetResult.notFound("Not found");
        assertFalse(notFound.isSuccess());
        assertSame(NotificationTargetStatus.NOTIFICATION_NOT_FOUND, notFound.getStatus());
        assertNull(notFound.getNotification());
        assertEquals("Not found", notFound.getMessage());
    }

    @Test
    @DisplayName("NotificationFilterCriteria: factory methods and properties")
    void testNotificationFilterCriteria() {
        NotificationFilterCriteria all = NotificationFilterCriteria.all();
        assertNull(all.getCategory());
        assertFalse(all.isUnreadOnly());
        assertNull(all.getSearchQuery());

        NotificationFilterCriteria byCat = NotificationFilterCriteria.forCategory(NotificationCategory.OFFERS);
        assertSame(NotificationCategory.OFFERS, byCat.getCategory());
        assertFalse(byCat.isUnreadOnly());

        NotificationFilterCriteria unread = NotificationFilterCriteria.unreadOnly();
        assertNull(unread.getCategory());
        assertTrue(unread.isUnreadOnly());

        NotificationFilterCriteria custom = new NotificationFilterCriteria();
        custom.setCategory(NotificationCategory.CHAT);
        custom.setUnreadOnly(true);
        custom.setSearchQuery("calc");

        assertSame(NotificationCategory.CHAT, custom.getCategory());
        assertTrue(custom.isUnreadOnly());
        assertEquals("calc", custom.getSearchQuery());
    }

    @Test
    @DisplayName("Deal: isExpired zero-argument overload")
    void testDealIsExpiredOverload() {
        Deal deal = new Deal();
        deal.setType(DealType.LOAN);
        deal.setStatus(DealStatus.ACTIVE);
        deal.setDueDate(LocalDateTime.now().minusHours(1));
        assertTrue(deal.isExpired());

        deal.setDueDate(LocalDateTime.now().plusDays(2));
        assertFalse(deal.isExpired());
    }
}
