package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the model types of use case 2.7 "View Seller Notifications" (US-S07): the board
 * categories, the filter and the result of selecting a notification.
 */
@DisplayName("Seller notification models: US-S07")
class SellerNotificationModelsTest {

    private static Notification notification(String rawCategory, String relatedType) {
        return new Notification("s07_seller", "Title", "Message", rawCategory, "X1", relatedType);
    }

    // ------------------------------------------------------------------
    // SellerNotificationCategory
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("SellerNotificationCategory: the categories of use case 2.7")
    class Categories {

        @Test
        @DisplayName("The five categories are those of the specification, in its order")
        void specificationCategories() {
            assertEquals(java.util.List.of("Listing Review", "Offers And Requests", "Reviews", "Deals", "Announcements"),
                    Arrays.stream(SellerNotificationCategory.values()).map(SellerNotificationCategory::getDisplayName).toList());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(SellerNotificationCategory.class)
        @DisplayName("Each category has an icon and shows its display name")
        void iconAndName(SellerNotificationCategory category) {
            assertFalse(category.getIcon().isBlank());
            assertEquals(category.getDisplayName(), category.toString());
        }

        @Test
        @DisplayName("Every category has its own icon")
        void distinctIcons() {
            Set<String> icons = Arrays.stream(SellerNotificationCategory.values())
                    .map(SellerNotificationCategory::getIcon).collect(Collectors.toSet());
            assertEquals(SellerNotificationCategory.values().length, icons.size());
        }

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({
                // Written by the Admin Portal (use cases 3.2 and 3.7)
                "Listings, LISTING_REVIEW",
                "Listing, LISTING_REVIEW",
                "Listing Review, LISTING_REVIEW",
                // Written by the Buyer Portal (use cases 1.5, 1.6) and the Admin Portal (use case 3.3)
                "Offer, OFFERS_AND_REQUESTS",
                "Offers, OFFERS_AND_REQUESTS",
                "Offers And Requests, OFFERS_AND_REQUESTS",
                "Offers & Requests, OFFERS_AND_REQUESTS",
                "Chat, OFFERS_AND_REQUESTS",
                // Written by use cases 1.10 and 3.8
                "Reviews, REVIEWS",
                "Review, REVIEWS",
                "Rating, REVIEWS",
                // Written by use case 1.6 (deal completed, item returned)
                "Deal, DEALS",
                "Deals, DEALS",
                "Loan, DEALS",
                "Borrow Reminders, DEALS",
                // Written by use case 3.3 and the seed data
                "Announcements, ANNOUNCEMENTS",
                "System, ANNOUNCEMENTS",
                // Enumeration names
                "LISTING_REVIEW, LISTING_REVIEW",
                "OFFERS_AND_REQUESTS, OFFERS_AND_REQUESTS",
                "REVIEWS, REVIEWS",
                "DEALS, DEALS",
                "ANNOUNCEMENTS, ANNOUNCEMENTS"
        })
        @DisplayName("The categories stored by every portal are mapped to the Seller's board")
        void mapsStoredCategories(String raw, SellerNotificationCategory expected) {
            assertEquals(expected, SellerNotificationCategory.fromRaw(raw, null));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"  listings  ", "LISTINGS", "lIsTiNgS"})
        @DisplayName("The stored category is compared without case and surrounding spaces")
        void ignoresCaseAndSpaces(String raw) {
            assertEquals(SellerNotificationCategory.LISTING_REVIEW, SellerNotificationCategory.fromRaw(raw, null));
        }

        @Test
        @DisplayName("The mapping does not depend on the default locale (Turkish dotless i)")
        void localeIndependent() {
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                assertEquals(SellerNotificationCategory.LISTING_REVIEW, SellerNotificationCategory.fromRaw("LISTINGS", null));
                assertEquals(SellerNotificationCategory.OFFERS_AND_REQUESTS,
                        SellerNotificationCategory.fromRaw("OFFERS_AND_REQUESTS", null));
            } finally {
                Locale.setDefault(previous);
            }
        }

        @ParameterizedTest(name = "unknown category, related {0} -> {1}")
        @CsvSource({
                "LISTING, LISTING_REVIEW",
                "OFFER, OFFERS_AND_REQUESTS",
                "offer, OFFERS_AND_REQUESTS",
                "CHAT_THREAD, OFFERS_AND_REQUESTS",
                "REVIEW, REVIEWS",
                "DEAL, DEALS",
                "ANNOUNCEMENT, ANNOUNCEMENTS",
                "NONE, ANNOUNCEMENTS",
                "USER, ANNOUNCEMENTS"
        })
        @DisplayName("An unknown stored category is decided by the type of the related item")
        void unknownCategoryUsesRelatedType(String relatedType, SellerNotificationCategory expected) {
            assertEquals(expected, SellerNotificationCategory.fromRaw("Something else", relatedType));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("An empty stored category is decided by the related item, or is an Announcement")
        void emptyCategory(String raw) {
            assertEquals(SellerNotificationCategory.DEALS, SellerNotificationCategory.fromRaw(raw, "DEAL"));
            assertEquals(SellerNotificationCategory.ANNOUNCEMENTS, SellerNotificationCategory.fromRaw(raw, null));
        }

        @Test
        @DisplayName("A known stored category wins over the type of the related item")
        void storedCategoryWins() {
            assertEquals(SellerNotificationCategory.REVIEWS, SellerNotificationCategory.fromRaw("Reviews", "LISTING"));
            assertEquals(SellerNotificationCategory.OFFERS_AND_REQUESTS,
                    SellerNotificationCategory.fromRaw("Offers", "LISTING"));
        }

        @Test
        @DisplayName("of(notification) reads the notification's category and related type")
        void ofNotification() {
            assertEquals(SellerNotificationCategory.DEALS, SellerNotificationCategory.of(notification("Loan", null)));
            assertEquals(SellerNotificationCategory.REVIEWS, SellerNotificationCategory.of(notification(null, "REVIEW")));
        }

        @Test
        @DisplayName("of(null) is refused")
        void ofNull() {
            assertThrows(NullPointerException.class, () -> SellerNotificationCategory.of(null));
        }
    }

    // ------------------------------------------------------------------
    // SellerNotificationFilter
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("SellerNotificationFilter: alternative flow A2 and the other filters")
    class Filter {

        @Test
        @DisplayName("all() shows every notification, read or unread, of every category")
        void allShowsEverything() {
            SellerNotificationFilter all = SellerNotificationFilter.all();
            assertTrue(all.isEmpty());
            Notification read = notification("Listings", "LISTING");
            read.setRead(true);
            assertTrue(all.matches(read));
            assertTrue(all.matches(notification("Offer", null)));
        }

        @Test
        @DisplayName("The search text is stored trimmed, and null becomes empty")
        void searchNormalised() {
            assertEquals("chair", new SellerNotificationFilter(null, false, "  chair ").search());
            assertEquals("", new SellerNotificationFilter(null, false, null).search());
            assertTrue(new SellerNotificationFilter(null, false, "   ").isEmpty());
        }

        @Test
        @DisplayName("A2: ofCategory shows only the notifications of that category")
        void byCategory() {
            SellerNotificationFilter offers = SellerNotificationFilter.ofCategory(SellerNotificationCategory.OFFERS_AND_REQUESTS);
            assertFalse(offers.isEmpty());
            assertTrue(offers.matches(notification("Offer", null)));
            assertTrue(offers.matches(notification("Chat", null)));
            assertFalse(offers.matches(notification("Listings", "LISTING")));
            assertTrue(SellerNotificationFilter.ofCategory(null).isEmpty());
        }

        @Test
        @DisplayName("Unread only hides the read notifications")
        void unreadOnly() {
            SellerNotificationFilter unread = new SellerNotificationFilter(null, true, "");
            assertFalse(unread.isEmpty());
            Notification n = notification("Offer", null);
            assertTrue(unread.matches(n));
            n.setRead(true);
            assertFalse(unread.matches(n));
        }

        @Test
        @DisplayName("The search looks in the title and the message, ignoring case")
        void search() {
            Notification n = new Notification("s07_seller", "Listing Rejected", "Your listing 'Desk Lamp' was rejected.", "Listings");
            assertTrue(new SellerNotificationFilter(null, false, "rejected").matches(n));
            assertTrue(new SellerNotificationFilter(null, false, "DESK LAMP").matches(n));
            assertFalse(new SellerNotificationFilter(null, false, "calculator").matches(n));
        }

        @Test
        @DisplayName("A notification without title or message only matches an empty search")
        void searchWithoutText() {
            Notification n = new Notification("s07_seller", null, null, "Offer");
            assertTrue(SellerNotificationFilter.all().matches(n));
            assertFalse(new SellerNotificationFilter(null, false, "offer").matches(n));
        }

        @Test
        @DisplayName("Every condition must hold together")
        void combined() {
            SellerNotificationFilter filter =
                    new SellerNotificationFilter(SellerNotificationCategory.LISTING_REVIEW, true, "approved");
            Notification approved = new Notification("s07_seller", "Listing Approved", "Now live.", "Listings");
            assertTrue(filter.matches(approved));
            approved.setRead(true);
            assertFalse(filter.matches(approved), "Read");
            Notification offer = new Notification("s07_seller", "Offer approved", "Now live.", "Offer");
            assertFalse(filter.matches(offer), "Other category");
            Notification rejected = new Notification("s07_seller", "Listing Rejected", "Blurry.", "Listings");
            assertFalse(filter.matches(rejected), "Search text missing");
        }

        @Test
        @DisplayName("matches(null) is refused")
        void matchesNull() {
            assertThrows(NullPointerException.class, () -> SellerNotificationFilter.all().matches(null));
        }

        @Test
        @DisplayName("Two filters with the same conditions are equal")
        void equality() {
            assertEquals(new SellerNotificationFilter(SellerNotificationCategory.DEALS, true, " a "),
                    new SellerNotificationFilter(SellerNotificationCategory.DEALS, true, "a"));
        }
    }

    // ------------------------------------------------------------------
    // SellerNotificationResult and SellerNotificationStatus
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("SellerNotificationResult: outcome of selecting a notification")
    class Result {

        @Test
        @DisplayName("The four outcomes of the specification exist")
        void statuses() {
            assertEquals(4, SellerNotificationStatus.values().length);
            assertEquals(SellerNotificationStatus.ITEM_UNAVAILABLE, SellerNotificationStatus.valueOf("ITEM_UNAVAILABLE"));
        }

        @Test
        @DisplayName("opened(...) holds the related item, returned only by the getter of its kind")
        void openedListing() {
            Notification n = notification("Listings", "LISTING");
            Listing listing = new Listing();
            SellerNotificationResult result =
                    SellerNotificationResult.opened(n, NotificationTargetType.LISTING, listing, "Showing it.");
            assertTrue(result.isSuccess());
            assertEquals(SellerNotificationStatus.SUCCESS, result.getStatus());
            assertSame(n, result.getNotification());
            assertEquals(SellerNotificationCategory.LISTING_REVIEW, result.getCategory());
            assertEquals(NotificationTargetType.LISTING, result.getTargetType());
            assertSame(listing, result.getListing());
            assertNull(result.getOffer());
            assertNull(result.getDeal());
            assertNull(result.getReview());
            assertNull(result.getChatThread());
            assertEquals("Showing it.", result.getMessage());
        }

        @Test
        @DisplayName("Each kind of related item is returned by its own getter")
        void typedGetters() {
            Notification n = notification("Offer", "OFFER");
            Offer offer = new Offer();
            Deal deal = new Deal();
            Review review = new Review();
            ChatThread thread = new ChatThread("T1", "L1", "buyer", "s07_seller");
            assertSame(offer, SellerNotificationResult.opened(n, NotificationTargetType.OFFER, offer, null).getOffer());
            assertSame(deal, SellerNotificationResult.opened(n, NotificationTargetType.DEAL, deal, null).getDeal());
            assertSame(review, SellerNotificationResult.opened(n, NotificationTargetType.REVIEW, review, null).getReview());
            SellerNotificationResult chat = SellerNotificationResult.opened(n, NotificationTargetType.CHAT_THREAD, thread, null);
            assertSame(thread, chat.getChatThread());
            assertNull(chat.getListing());
            assertEquals("", chat.getMessage(), "A null message becomes empty");
        }

        @Test
        @DisplayName("opened(...) needs a notification, a kind and an item")
        void openedRequiresArguments() {
            Notification n = notification("Offer", "OFFER");
            assertThrows(NullPointerException.class,
                    () -> SellerNotificationResult.opened(null, NotificationTargetType.OFFER, new Offer(), ""));
            assertThrows(NullPointerException.class, () -> SellerNotificationResult.opened(n, null, new Offer(), ""));
            assertThrows(NullPointerException.class,
                    () -> SellerNotificationResult.opened(n, NotificationTargetType.OFFER, null, ""));
        }

        @Test
        @DisplayName("noRelatedItem(...) holds the notification but no item")
        void noRelatedItem() {
            Notification n = notification("Announcements", null);
            SellerNotificationResult result = SellerNotificationResult.noRelatedItem(n, "Message only.");
            assertFalse(result.isSuccess());
            assertEquals(SellerNotificationStatus.NO_RELATED_ITEM, result.getStatus());
            assertSame(n, result.getNotification());
            assertEquals(NotificationTargetType.NONE, result.getTargetType());
            assertNull(result.getListing());
            assertEquals(SellerNotificationCategory.ANNOUNCEMENTS, result.getCategory());
            assertThrows(NullPointerException.class, () -> SellerNotificationResult.noRelatedItem(null, ""));
        }

        @Test
        @DisplayName("itemUnavailable(...) gives the kind of the item but never the item (A3)")
        void itemUnavailable() {
            Notification n = notification("Reviews", "REVIEW");
            SellerNotificationResult result =
                    SellerNotificationResult.itemUnavailable(n, NotificationTargetType.REVIEW, "Gone.");
            assertFalse(result.isSuccess());
            assertEquals(SellerNotificationStatus.ITEM_UNAVAILABLE, result.getStatus());
            assertEquals(NotificationTargetType.REVIEW, result.getTargetType());
            assertNull(result.getReview());
            assertEquals("Gone.", result.getMessage());
            assertThrows(NullPointerException.class,
                    () -> SellerNotificationResult.itemUnavailable(null, NotificationTargetType.REVIEW, ""));
            assertThrows(NullPointerException.class, () -> SellerNotificationResult.itemUnavailable(n, null, ""));
        }

        @Test
        @DisplayName("notFound(...) holds no notification and no category")
        void notFound() {
            SellerNotificationResult result = SellerNotificationResult.notFound("Not here.");
            assertEquals(SellerNotificationStatus.NOTIFICATION_NOT_FOUND, result.getStatus());
            assertNull(result.getNotification());
            assertNull(result.getCategory());
            assertEquals(NotificationTargetType.NONE, result.getTargetType());
            assertTrue(result.toString().contains("NOTIFICATION_NOT_FOUND"));
            assertTrue(result.toString().contains("Not here."));
        }
    }
}
