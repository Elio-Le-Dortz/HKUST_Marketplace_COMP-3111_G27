package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the model types of use case 2.4 "Manage My Listings" (US-S04): the
 * {@link MyListingResult} factories and accessors, {@link MyListingStatus}, and the
 * "deleted by the seller" flag of {@link Listing}.
 */
@DisplayName("US-S04 models: MyListingResult, MyListingStatus, Listing.deletedBySeller")
class MyListingModelsTest {

    private static Listing listing(String id) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setTitle("Desk Lamp");
        return listing;
    }

    private static Offer offer(String id) {
        Offer offer = new Offer();
        offer.setId(id);
        offer.setStatus(OfferStatus.PENDING);
        return offer;
    }

    @Nested
    @DisplayName("Successful results")
    class Successes {

        @Test
        @DisplayName("allowed() holds the listing, its open offers and the explanation")
        void allowed() {
            Listing listing = listing("L1");
            Offer offer = offer("O1");

            MyListingResult result = MyListingResult.allowed(listing, List.of(offer), "You can edit it.");

            assertTrue(result.isSuccess());
            assertEquals(MyListingStatus.SUCCESS, result.getStatus());
            assertSame(listing, result.getListing());
            assertEquals(List.of(offer), result.getOffers());
            assertEquals(Set.of("O1"), result.getOfferIds());
            assertEquals("You can edit it.", result.getMessage());
            assertTrue(result.getErrors().isEmpty());
            assertNull(result.getFirstInvalidField());
        }

        @Test
        @DisplayName("success() holds the declined offers, copied so later changes do not leak in")
        void success() {
            List<Offer> declined = new ArrayList<>(List.of(offer("O1"), offer("O2")));

            MyListingResult result = MyListingResult.success(listing("L1"), declined, "Deleted.");
            declined.clear();

            assertTrue(result.isSuccess());
            assertEquals(2, result.getOffers().size());
            assertThrows(UnsupportedOperationException.class, () -> result.getOffers().clear());
            assertThrows(UnsupportedOperationException.class, () -> result.getOfferIds().clear());
        }

        @Test
        @DisplayName("The arguments are required")
        void nullArguments() {
            Listing listing = listing("L1");
            assertThrows(NullPointerException.class, () -> MyListingResult.allowed(null, List.of(), "m"));
            assertThrows(NullPointerException.class, () -> MyListingResult.allowed(listing, null, "m"));
            assertThrows(NullPointerException.class, () -> MyListingResult.allowed(listing, List.of(), null));
            assertThrows(NullPointerException.class,
                    () -> MyListingResult.allowed(listing, Arrays.asList(offer("O1"), null), "m"));
            assertThrows(NullPointerException.class, () -> MyListingResult.success(null, List.of(), "m"));
            assertThrows(NullPointerException.class, () -> MyListingResult.success(listing, null, "m"));
            assertThrows(NullPointerException.class, () -> MyListingResult.success(listing, List.of(), null));
        }
    }

    @Nested
    @DisplayName("Pending offers (alternative flow A3)")
    class PendingOffers {

        @Test
        @DisplayName("pendingOffers() lists the offers to confirm, in order")
        void pendingOffers() {
            MyListingResult result = MyListingResult.pendingOffers(listing("L1"),
                    List.of(offer("O2"), offer("O1")), "Warning");

            assertFalse(result.isSuccess());
            assertEquals(MyListingStatus.PENDING_OFFERS, result.getStatus());
            assertEquals(List.of("O2", "O1"), new ArrayList<>(result.getOfferIds()));
            assertEquals("Warning", result.getMessage());
        }

        @Test
        @DisplayName("A pending offers result needs at least one offer and every argument")
        void invalidArguments() {
            Listing listing = listing("L1");
            assertThrows(IllegalArgumentException.class, () -> MyListingResult.pendingOffers(listing, List.of(), "m"));
            assertThrows(NullPointerException.class, () -> MyListingResult.pendingOffers(null, List.of(offer("O1")), "m"));
            assertThrows(NullPointerException.class, () -> MyListingResult.pendingOffers(listing, null, "m"));
            assertThrows(NullPointerException.class, () -> MyListingResult.pendingOffers(listing, List.of(offer("O1")), null));
        }
    }

    @Nested
    @DisplayName("Invalid fields (alternative flow A4)")
    class InvalidFields {

        @Test
        @DisplayName("invalidFields() keeps the errors in form order and summarises them")
        void invalidFields() {
            Map<ListingField, List<String>> errors = new HashMap<>();
            errors.put(ListingField.PHOTOS, List.of("Add at least one photo of the item."));
            errors.put(ListingField.TITLE, List.of("Item name is required."));

            MyListingResult result = MyListingResult.invalidFields(listing("L1"), errors);

            assertEquals(MyListingStatus.INVALID_FIELDS, result.getStatus());
            assertFalse(result.isSuccess());
            assertEquals(List.of(ListingField.TITLE, ListingField.PHOTOS), new ArrayList<>(result.getErrors().keySet()));
            assertEquals(ListingField.TITLE, result.getFirstInvalidField());
            assertTrue(result.hasError(ListingField.PHOTOS));
            assertFalse(result.hasError(ListingField.PRICE));
            assertEquals(List.of(), result.getErrors(ListingField.PRICE));
            assertEquals(List.of("Item name is required.", "Add at least one photo of the item."), result.getAllMessages());
            assertEquals("Please correct the 2 highlighted fields.", result.getMessage());
            assertTrue(result.getOffers().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> result.getErrors().clear());
            assertThrows(UnsupportedOperationException.class, () -> result.getAllMessages().clear());
        }

        @Test
        @DisplayName("A single invalid field is summarised in the singular")
        void singleField() {
            MyListingResult result = MyListingResult.invalidFields(listing("L1"),
                    Map.of(ListingField.PRICE, List.of("Price is required for a Sell listing.")));

            assertEquals("Please correct the highlighted field.", result.getMessage());
        }

        @Test
        @DisplayName("Fields without message are ignored, and at least one message is needed")
        void emptyMessagesIgnored() {
            Map<ListingField, List<String>> errors = new EnumMap<>(ListingField.class);
            errors.put(ListingField.TITLE, List.of());
            errors.put(ListingField.PRICE, null);
            assertThrows(IllegalArgumentException.class, () -> MyListingResult.invalidFields(listing("L1"), errors));

            Map<ListingField, List<String>> withNullKey = new HashMap<>();
            withNullKey.put(null, List.of("ignored"));
            withNullKey.put(ListingField.CATEGORY, List.of("Choose a category."));
            MyListingResult result = MyListingResult.invalidFields(listing("L1"), withNullKey);
            assertEquals(Set.of(ListingField.CATEGORY), result.getErrors().keySet());
        }

        @Test
        @DisplayName("The arguments are required")
        void nullArguments() {
            assertThrows(NullPointerException.class, () -> MyListingResult.invalidFields(null, Map.of()));
            assertThrows(NullPointerException.class, () -> MyListingResult.invalidFields(listing("L1"), null));
        }
    }

    @Nested
    @DisplayName("Other failures")
    class Failures {

        @ParameterizedTest
        @EnumSource(value = MyListingStatus.class,
                names = {"LISTING_NOT_FOUND", "UNAUTHORIZED", "DEAL_IN_PROGRESS_OR_COMPLETED", "TAKEN_DOWN", "NO_CHANGES"})
        @DisplayName("failure() accepts the statuses without their own factory, with or without listing")
        void failure(MyListingStatus status) {
            MyListingResult withoutListing = MyListingResult.failure(status, null, "Why");
            MyListingResult withListing = MyListingResult.failure(status, listing("L1"), "Why");

            for (MyListingResult result : List.of(withoutListing, withListing)) {
                assertFalse(result.isSuccess());
                assertEquals(status, result.getStatus());
                assertEquals("Why", result.getMessage());
                assertTrue(result.getOffers().isEmpty());
                assertTrue(result.getErrors().isEmpty());
            }
            assertNull(withoutListing.getListing());
            assertEquals("L1", withListing.getListing().getId());
        }

        @ParameterizedTest
        @EnumSource(value = MyListingStatus.class, names = {"SUCCESS", "PENDING_OFFERS", "INVALID_FIELDS"})
        @DisplayName("failure() refuses the statuses that have their own factory")
        void dedicatedStatuses(MyListingStatus status) {
            assertThrows(IllegalArgumentException.class, () -> MyListingResult.failure(status, null, "Why"));
        }

        @Test
        @DisplayName("The status and the message are required")
        void nullArguments() {
            assertThrows(NullPointerException.class, () -> MyListingResult.failure(null, null, "Why"));
            assertThrows(NullPointerException.class,
                    () -> MyListingResult.failure(MyListingStatus.NO_CHANGES, null, null));
        }

        @Test
        @DisplayName("toString() describes the result")
        void describes() {
            String withListing = MyListingResult.failure(MyListingStatus.TAKEN_DOWN, listing("L7"), "Taken down").toString();
            assertTrue(withListing.contains("TAKEN_DOWN"));
            assertTrue(withListing.contains("L7"));
            assertTrue(withListing.contains("Taken down"));
            assertTrue(MyListingResult.failure(MyListingStatus.UNAUTHORIZED, null, "No").toString().contains("listing=null"));
        }
    }

    @Test
    @DisplayName("MyListingStatus lists every outcome of the use case")
    void statuses() {
        assertEquals(8, MyListingStatus.values().length);
        assertEquals(MyListingStatus.PENDING_OFFERS, MyListingStatus.valueOf("PENDING_OFFERS"));
    }

    @Test
    @DisplayName("A listing is not deleted by its seller until it is marked so")
    void deletedBySellerFlag() {
        Listing listing = new Listing();
        assertFalse(listing.isDeletedBySeller());
        listing.setDeletedBySeller(true);
        assertTrue(listing.isDeletedBySeller());
        listing.setDeletedBySeller(false);
        assertFalse(listing.isDeletedBySeller());
    }
}
