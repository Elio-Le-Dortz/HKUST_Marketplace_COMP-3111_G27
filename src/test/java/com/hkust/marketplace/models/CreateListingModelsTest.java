package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the models of use case 2.3 "Create Listing" (US-S03): the form fields, the
 * lending duration units, the request, and the results of a submission and of a photo
 * selection.
 */
@DisplayName("Create Listing models (US-S03)")
class CreateListingModelsTest {

    private static final Path PHOTO_A = Path.of("a.png").toAbsolutePath();
    private static final Path PHOTO_B = Path.of("b.png").toAbsolutePath();

    @Nested
    @DisplayName("ListingField")
    class ListingFieldTest {

        @Test
        @DisplayName("The fields are declared in the order of the form, the type first and the photos last")
        void formOrder() {
            assertEquals(List.of(ListingField.TYPE, ListingField.TITLE, ListingField.DESCRIPTION, ListingField.CATEGORY,
                    ListingField.CONDITION, ListingField.PRICE, ListingField.LENDING_DURATION,
                    ListingField.MEETUP_LOCATION, ListingField.PHOTOS), List.of(ListingField.values()));
        }
    }

    @Nested
    @DisplayName("LendingDurationUnit")
    class LendingDurationUnitTest {

        @Test
        @DisplayName("Each unit has its length and its display name")
        void lengthsAndNames() {
            assertEquals(Duration.ofSeconds(1), LendingDurationUnit.SECONDS.getLength());
            assertEquals(Duration.ofMinutes(1), LendingDurationUnit.MINUTES.getLength());
            assertEquals(Duration.ofHours(1), LendingDurationUnit.HOURS.getLength());
            assertEquals(Duration.ofDays(1), LendingDurationUnit.DAYS.getLength());
            assertEquals(Duration.ofDays(7), LendingDurationUnit.WEEKS.getLength());
            assertEquals("Seconds", LendingDurationUnit.SECONDS.getDisplayName());
            assertEquals("Weeks", LendingDurationUnit.WEEKS.getDisplayName());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(LendingDurationUnit.class)
        @DisplayName("A drop-down list shows the display name")
        void toStringIsDisplayName(LendingDurationUnit unit) {
            assertEquals(unit.getDisplayName(), unit.toString());
        }

        @Test
        @DisplayName("An amount is converted to a duration")
        void toDuration() {
            assertEquals(Duration.ofSeconds(10), LendingDurationUnit.SECONDS.toDuration(10));
            assertEquals(Duration.ofMinutes(90), LendingDurationUnit.MINUTES.toDuration(90));
            assertEquals(Duration.ofHours(36), LendingDurationUnit.HOURS.toDuration(36));
            assertEquals(Duration.ofDays(3), LendingDurationUnit.DAYS.toDuration(3));
            assertEquals(Duration.ofDays(14), LendingDurationUnit.WEEKS.toDuration(2));
        }

        @Test
        @DisplayName("A duration too long to represent throws ArithmeticException")
        void overflow() {
            assertThrows(ArithmeticException.class, () -> LendingDurationUnit.WEEKS.toDuration(Long.MAX_VALUE));
        }
    }

    @Nested
    @DisplayName("CreateListingRequest")
    class CreateListingRequestTest {

        private CreateListingRequest request(List<Path> photos) {
            return new CreateListingRequest(ListingType.SELL, "Desk Lamp", "A long description of the lamp",
                    Category.FURNITURE, Condition.USED, "120", null, LendingDurationUnit.DAYS,
                    MeetupLocation.ATRIUM, photos);
        }

        @Test
        @DisplayName("Missing photos are read as an empty list")
        void nullPhotos() {
            assertEquals(List.of(), request(null).photos());
        }

        @Test
        @DisplayName("Null photo elements are ignored")
        void nullElements() {
            assertEquals(List.of(PHOTO_A, PHOTO_B), request(Arrays.asList(null, PHOTO_A, null, PHOTO_B)).photos());
        }

        @Test
        @DisplayName("The photos are copied, so changing the form later does not change the request")
        void photosCopied() {
            List<Path> photos = new ArrayList<>(List.of(PHOTO_A));
            CreateListingRequest request = request(photos);
            photos.add(PHOTO_B);
            assertEquals(List.of(PHOTO_A), request.photos());
        }

        @Test
        @DisplayName("The photo list of a request cannot be changed")
        void photosUnmodifiable() {
            CreateListingRequest request = request(new ArrayList<>(List.of(PHOTO_A)));
            assertThrows(UnsupportedOperationException.class, () -> request.photos().add(PHOTO_B));
        }

        @Test
        @DisplayName("Two requests with the same values are equal")
        void equality() {
            assertEquals(request(List.of(PHOTO_A)), request(List.of(PHOTO_A)));
            assertEquals(request(List.of(PHOTO_A)).hashCode(), request(List.of(PHOTO_A)).hashCode());
            assertNotEquals(request(List.of(PHOTO_A)), request(List.of(PHOTO_B)));
        }

        @Test
        @DisplayName("toString is short: it counts the photos and leaves out the description")
        void shortToString() {
            String text = request(List.of(PHOTO_A, PHOTO_B)).toString();
            assertTrue(text.contains("type=Sell"));
            assertTrue(text.contains("title=Desk Lamp"));
            assertTrue(text.contains("photos=2"));
            assertFalse(text.contains("A long description"));
        }
    }

    @Nested
    @DisplayName("CreateListingResult")
    class CreateListingResultTest {

        @Test
        @DisplayName("A success holds the listing and the confirmation, and no error")
        void success() {
            Listing listing = new Listing();
            CreateListingResult result = CreateListingResult.success(listing, "Submitted.");
            assertTrue(result.isSuccess());
            assertSame(listing, result.getListing());
            assertEquals("Submitted.", result.getMessage());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getAllMessages().isEmpty());
            assertNull(result.getFirstInvalidField());
            assertFalse(result.hasError(ListingField.TITLE));
            assertEquals(List.of(), result.getErrors(ListingField.TITLE));
        }

        @Test
        @DisplayName("A success needs a listing and a message")
        void successNeedsArguments() {
            assertThrows(NullPointerException.class, () -> CreateListingResult.success(null, "Submitted."));
            assertThrows(NullPointerException.class, () -> CreateListingResult.success(new Listing(), null));
        }

        @Test
        @DisplayName("A failure holds the errors in form order, whatever the order they were added in")
        void failureInFormOrder() {
            Map<ListingField, List<String>> errors = new LinkedHashMap<>();
            errors.put(ListingField.PHOTOS, List.of("No photo."));
            errors.put(ListingField.TITLE, List.of("No title."));
            errors.put(ListingField.PRICE, List.of("Bad price.", "Too high."));
            CreateListingResult result = CreateListingResult.failure(errors);

            assertFalse(result.isSuccess());
            assertNull(result.getListing());
            assertEquals(List.of(ListingField.TITLE, ListingField.PRICE, ListingField.PHOTOS),
                    new ArrayList<>(result.getErrors().keySet()));
            assertEquals(ListingField.TITLE, result.getFirstInvalidField());
            assertEquals(List.of("No title.", "Bad price.", "Too high.", "No photo."), result.getAllMessages());
            assertEquals(List.of("Bad price.", "Too high."), result.getErrors(ListingField.PRICE));
            assertTrue(result.hasError(ListingField.PRICE));
            assertFalse(result.hasError(ListingField.CATEGORY));
            assertEquals("Please correct the 3 highlighted fields.", result.getMessage());
        }

        @Test
        @DisplayName("A failure on one field asks to correct that field")
        void singleFieldMessage() {
            CreateListingResult result = CreateListingResult.failure(Map.of(ListingField.TITLE, List.of("No title.")));
            assertEquals("Please correct the highlighted field.", result.getMessage());
        }

        @Test
        @DisplayName("A failure copies the errors and cannot be changed")
        void failureCopied() {
            Map<ListingField, List<String>> errors = new EnumMap<>(ListingField.class);
            List<String> messages = new ArrayList<>(List.of("No title."));
            errors.put(ListingField.TITLE, messages);
            CreateListingResult result = CreateListingResult.failure(errors);
            errors.put(ListingField.PRICE, List.of("Bad price."));
            messages.add("Changed later.");

            assertEquals(Map.of(ListingField.TITLE, List.of("No title.")), result.getErrors());
            assertThrows(UnsupportedOperationException.class,
                    () -> result.getErrors().put(ListingField.PRICE, List.of("x")));
            assertThrows(UnsupportedOperationException.class,
                    () -> result.getErrors(ListingField.TITLE).add("x"));
            assertThrows(UnsupportedOperationException.class, () -> result.getAllMessages().add("x"));
        }

        @Test
        @DisplayName("Fields without message are ignored, and a failure needs at least one message")
        void failureNeedsMessages() {
            Map<ListingField, List<String>> errors = new LinkedHashMap<>();
            errors.put(ListingField.TITLE, List.of());
            errors.put(ListingField.PRICE, null);
            errors.put(null, List.of("No field."));
            assertThrows(IllegalArgumentException.class, () -> CreateListingResult.failure(errors));
            assertThrows(IllegalArgumentException.class, () -> CreateListingResult.failure(Map.of()));
            assertThrows(NullPointerException.class, () -> CreateListingResult.failure(null));

            errors.put(ListingField.CATEGORY, List.of("No category."));
            assertEquals(List.of(ListingField.CATEGORY), new ArrayList<>(CreateListingResult.failure(errors).getErrors().keySet()));
        }
    }

    @Nested
    @DisplayName("PhotoSelectionResult")
    class PhotoSelectionResultTest {

        @Test
        @DisplayName("It holds the photos, the number added and the rejections")
        void accessors() {
            PhotoSelectionResult result = new PhotoSelectionResult(List.of(PHOTO_A, PHOTO_B), 1, List.of("c.txt is not an image."));
            assertEquals(List.of(PHOTO_A, PHOTO_B), result.getPhotos());
            assertEquals(1, result.getAddedCount());
            assertEquals(List.of("c.txt is not an image."), result.getRejections());
            assertTrue(result.hasRejections());
            assertFalse(new PhotoSelectionResult(List.of(PHOTO_A), 1, List.of()).hasRejections());
        }

        @Test
        @DisplayName("The lists are copied and cannot be changed")
        void copied() {
            List<Path> photos = new ArrayList<>(List.of(PHOTO_A));
            List<String> rejections = new ArrayList<>();
            PhotoSelectionResult result = new PhotoSelectionResult(photos, 0, rejections);
            photos.add(PHOTO_B);
            rejections.add("Later.");
            assertEquals(List.of(PHOTO_A), result.getPhotos());
            assertTrue(result.getRejections().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> result.getPhotos().add(PHOTO_B));
        }

        @Test
        @DisplayName("The number added must be between 0 and the number of photos")
        void addedCountChecked() {
            assertThrows(IllegalArgumentException.class, () -> new PhotoSelectionResult(List.of(PHOTO_A), -1, List.of()));
            assertThrows(IllegalArgumentException.class, () -> new PhotoSelectionResult(List.of(PHOTO_A), 2, List.of()));
            assertEquals(0, new PhotoSelectionResult(List.of(), 0, List.of()).getAddedCount());
        }

        @Test
        @DisplayName("Missing lists throw NullPointerException")
        void nullLists() {
            assertThrows(NullPointerException.class, () -> new PhotoSelectionResult(null, 0, List.of()));
            assertThrows(NullPointerException.class, () -> new PhotoSelectionResult(List.of(), 0, null));
        }
    }
}
