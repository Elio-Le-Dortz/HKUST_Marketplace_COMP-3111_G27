package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the types of use case 2.6 "Manage Seller Profile" (US-S06): the values of the profile
 * form, the fields of the form, the outcomes of a save, and the result of a save.
 */
@DisplayName("SellerProfileUpdateRequest, SellerProfileField, SellerProfileStatus and SellerProfileResult (US-S06)")
class SellerProfileModelsTest {

    private static Seller seller() {
        return new Seller("sam", "Sam Seller", "hash");
    }

    @Nested
    @DisplayName("SellerProfileUpdateRequest")
    class Request {

        @Test
        @DisplayName("holds the values of the form exactly as typed")
        void holdsValues() {
            SellerProfileUpdateRequest r = new SellerProfileUpdateRequest("  Sam  ", " bio ",
                    List.of(MeetupLocation.LIBRARY, MeetupLocation.ATRIUM), " old ", "New Pass1", "New Pass1");

            assertEquals("  Sam  ", r.fullName());
            assertEquals(" bio ", r.bio());
            assertEquals(List.of(MeetupLocation.LIBRARY, MeetupLocation.ATRIUM), r.preferredMeetups());
            assertEquals(" old ", r.currentPassword());
            assertEquals("New Pass1", r.newPassword());
            assertEquals("New Pass1", r.confirmPassword());
        }

        @Test
        @DisplayName("null meet-up locations become an empty list")
        void nullMeetups() {
            SellerProfileUpdateRequest r = new SellerProfileUpdateRequest("Sam", null, null, null, null, null);

            assertEquals(List.of(), r.preferredMeetups());
        }

        @Test
        @DisplayName("null meet-up locations are left out; duplicates are kept for the service to remove")
        void nullElementsRemoved() {
            SellerProfileUpdateRequest r = new SellerProfileUpdateRequest("Sam", null,
                    Arrays.asList(MeetupLocation.ATRIUM, null, MeetupLocation.ATRIUM), "", "", "");

            assertEquals(List.of(MeetupLocation.ATRIUM, MeetupLocation.ATRIUM), r.preferredMeetups());
        }

        @Test
        @DisplayName("the meet-up locations are copied and cannot be changed")
        void meetupsCopiedAndUnmodifiable() {
            List<MeetupLocation> chosen = new ArrayList<>(List.of(MeetupLocation.LIBRARY));
            SellerProfileUpdateRequest r = SellerProfileUpdateRequest.withoutPasswordChange("Sam", null, chosen);
            chosen.add(MeetupLocation.ATRIUM);

            assertEquals(List.of(MeetupLocation.LIBRARY), r.preferredMeetups());
            assertThrows(UnsupportedOperationException.class, () -> r.preferredMeetups().add(MeetupLocation.ATRIUM));
        }

        @Test
        @DisplayName("withoutPasswordChange() leaves the three password fields empty")
        void withoutPasswordChange() {
            SellerProfileUpdateRequest r = SellerProfileUpdateRequest.withoutPasswordChange("Sam", "Bio",
                    List.of(MeetupLocation.LIBRARY));

            assertEquals("Sam", r.fullName());
            assertEquals("Bio", r.bio());
            assertEquals("", r.currentPassword());
            assertEquals("", r.newPassword());
            assertEquals("", r.confirmPassword());
            assertFalse(r.isPasswordChangeRequested());
        }

        @Test
        @DisplayName("the password is changed only when at least one password field is filled in (step 4)")
        void passwordChangeRequested() {
            assertFalse(new SellerProfileUpdateRequest("Sam", null, null, null, null, null).isPasswordChangeRequested());
            assertFalse(new SellerProfileUpdateRequest("Sam", null, null, "", "", "").isPasswordChangeRequested());
            assertTrue(new SellerProfileUpdateRequest("Sam", null, null, "old", "", "").isPasswordChangeRequested());
            assertTrue(new SellerProfileUpdateRequest("Sam", null, null, "", "New", "").isPasswordChangeRequested());
            assertTrue(new SellerProfileUpdateRequest("Sam", null, null, "", "", "New").isPasswordChangeRequested());
        }

        @Test
        @DisplayName("a password made of spaces counts as filled in, since passwords are used as typed")
        void spacesCount() {
            assertTrue(new SellerProfileUpdateRequest("Sam", null, null, " ", null, null).isPasswordChangeRequested());
        }

        @Test
        @DisplayName("toString() masks the three passwords")
        void toStringMasksPasswords() {
            String text = new SellerProfileUpdateRequest("Sam", "Bio", List.of(MeetupLocation.LIBRARY),
                    "Secret0ld", "Secret1New", "Secret1New").toString();

            assertTrue(text.contains("Sam"));
            assertTrue(text.contains("passwordChangeRequested=true"));
            assertFalse(text.contains("Secret0ld"));
            assertFalse(text.contains("Secret1New"));
        }

        @Test
        @DisplayName("two requests with the same values are equal")
        void equality() {
            SellerProfileUpdateRequest a = SellerProfileUpdateRequest.withoutPasswordChange("Sam", "Bio",
                    List.of(MeetupLocation.LIBRARY));
            SellerProfileUpdateRequest b = new SellerProfileUpdateRequest("Sam", "Bio",
                    new ArrayList<>(List.of(MeetupLocation.LIBRARY)), "", "", "");

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    @Nested
    @DisplayName("SellerProfileField and SellerProfileStatus")
    class Enums {

        @Test
        @DisplayName("the fields are declared in the order of the form")
        void fieldOrder() {
            assertEquals(List.of(SellerProfileField.FULL_NAME, SellerProfileField.BIO,
                            SellerProfileField.PREFERRED_MEETUPS, SellerProfileField.CURRENT_PASSWORD,
                            SellerProfileField.NEW_PASSWORD, SellerProfileField.CONFIRM_PASSWORD),
                    List.of(SellerProfileField.values()));
        }

        @Test
        @DisplayName("a save is a success, changes nothing, or has invalid fields")
        void statuses() {
            assertEquals(List.of(SellerProfileStatus.SUCCESS, SellerProfileStatus.NO_CHANGES,
                    SellerProfileStatus.INVALID_FIELDS), List.of(SellerProfileStatus.values()));
            assertEquals(SellerProfileStatus.NO_CHANGES, SellerProfileStatus.valueOf("NO_CHANGES"));
        }
    }

    @Nested
    @DisplayName("SellerProfileResult.success()")
    class Success {

        @Test
        @DisplayName("holds the Seller, the changed fields in form order and the number of listings updated")
        void holdsValues() {
            Seller seller = seller();
            SellerProfileResult r = SellerProfileResult.success(seller,
                    EnumSet.of(SellerProfileField.NEW_PASSWORD, SellerProfileField.FULL_NAME), 3, "Saved.");

            assertTrue(r.isSuccess());
            assertEquals(SellerProfileStatus.SUCCESS, r.getStatus());
            assertSame(seller, r.getSeller());
            assertEquals("Saved.", r.getMessage());
            assertEquals(List.of(SellerProfileField.FULL_NAME, SellerProfileField.NEW_PASSWORD),
                    List.copyOf(r.getChangedFields()));
            assertTrue(r.hasChanged(SellerProfileField.FULL_NAME));
            assertFalse(r.hasChanged(SellerProfileField.BIO));
            assertTrue(r.isPasswordChanged());
            assertEquals(3, r.getUpdatedListingCount());
            assertTrue(r.getErrors().isEmpty());
            assertNull(r.getFirstInvalidField());
            assertEquals(List.of(), r.getAllMessages());
        }

        @Test
        @DisplayName("a save without new password does not change the password")
        void passwordNotChanged() {
            SellerProfileResult r = SellerProfileResult.success(seller(), Set.of(SellerProfileField.BIO), 0, "Saved.");

            assertFalse(r.isPasswordChanged());
            assertEquals(0, r.getUpdatedListingCount());
        }

        @Test
        @DisplayName("the changed fields are copied and cannot be changed")
        void changedFieldsCopied() {
            Set<SellerProfileField> changed = EnumSet.of(SellerProfileField.BIO);
            SellerProfileResult r = SellerProfileResult.success(seller(), changed, 0, "Saved.");
            changed.add(SellerProfileField.FULL_NAME);

            assertEquals(Set.of(SellerProfileField.BIO), r.getChangedFields());
            assertThrows(UnsupportedOperationException.class,
                    () -> r.getChangedFields().add(SellerProfileField.FULL_NAME));
        }

        @Test
        @DisplayName("refuses missing values, an empty set of changes and a negative count")
        void refusesInvalidArguments() {
            Set<SellerProfileField> bio = Set.of(SellerProfileField.BIO);
            assertThrows(NullPointerException.class, () -> SellerProfileResult.success(null, bio, 0, "m"));
            assertThrows(NullPointerException.class, () -> SellerProfileResult.success(seller(), null, 0, "m"));
            assertThrows(NullPointerException.class, () -> SellerProfileResult.success(seller(), bio, 0, null));
            assertThrows(IllegalArgumentException.class, () -> SellerProfileResult.success(seller(), Set.of(), 0, "m"));
            assertThrows(IllegalArgumentException.class, () -> SellerProfileResult.success(seller(), bio, -1, "m"));
        }
    }

    @Nested
    @DisplayName("SellerProfileResult.noChanges()")
    class NoChanges {

        @Test
        @DisplayName("holds the Seller and the message, with no change and no error")
        void holdsValues() {
            Seller seller = seller();
            SellerProfileResult r = SellerProfileResult.noChanges(seller, "Nothing changed.");

            assertFalse(r.isSuccess());
            assertEquals(SellerProfileStatus.NO_CHANGES, r.getStatus());
            assertSame(seller, r.getSeller());
            assertEquals("Nothing changed.", r.getMessage());
            assertTrue(r.getChangedFields().isEmpty());
            assertTrue(r.getErrors().isEmpty());
            assertFalse(r.isPasswordChanged());
            assertEquals(0, r.getUpdatedListingCount());
        }

        @Test
        @DisplayName("refuses a missing Seller or message")
        void refusesNulls() {
            assertThrows(NullPointerException.class, () -> SellerProfileResult.noChanges(null, "m"));
            assertThrows(NullPointerException.class, () -> SellerProfileResult.noChanges(seller(), null));
        }
    }

    @Nested
    @DisplayName("SellerProfileResult.invalidFields()")
    class InvalidFields {

        @Test
        @DisplayName("holds the errors in form order, whatever the order of the given map")
        void errorsInFormOrder() {
            Map<SellerProfileField, List<String>> errors = new LinkedHashMap<>();
            errors.put(SellerProfileField.CONFIRM_PASSWORD, List.of("Passwords do not match."));
            errors.put(SellerProfileField.FULL_NAME, List.of("Full name is required."));
            SellerProfileResult r = SellerProfileResult.invalidFields(seller(), errors);

            assertFalse(r.isSuccess());
            assertEquals(SellerProfileStatus.INVALID_FIELDS, r.getStatus());
            assertEquals(List.of(SellerProfileField.FULL_NAME, SellerProfileField.CONFIRM_PASSWORD),
                    List.copyOf(r.getErrors().keySet()));
            assertEquals(SellerProfileField.FULL_NAME, r.getFirstInvalidField());
            assertEquals(List.of("Full name is required.", "Passwords do not match."), r.getAllMessages());
            assertTrue(r.hasError(SellerProfileField.CONFIRM_PASSWORD));
            assertFalse(r.hasError(SellerProfileField.BIO));
            assertEquals(List.of("Passwords do not match."), r.getErrors(SellerProfileField.CONFIRM_PASSWORD));
            assertEquals(List.of(), r.getErrors(SellerProfileField.BIO));
            assertTrue(r.getChangedFields().isEmpty());
            assertEquals(0, r.getUpdatedListingCount());
        }

        @Test
        @DisplayName("the message says that nothing was saved and how many fields to correct")
        void summaryMessage() {
            SellerProfileResult one = SellerProfileResult.invalidFields(seller(),
                    Map.of(SellerProfileField.FULL_NAME, List.of("e")));
            SellerProfileResult two = SellerProfileResult.invalidFields(seller(),
                    Map.of(SellerProfileField.FULL_NAME, List.of("e"), SellerProfileField.BIO, List.of("f")));

            assertEquals("Nothing was saved. Please correct the highlighted field.", one.getMessage());
            assertEquals("Nothing was saved. Please correct the 2 highlighted fields.", two.getMessage());
        }

        @Test
        @DisplayName("fields without message are left out; the errors are copied and cannot be changed")
        void copiedWithoutEmptyFields() {
            List<String> messages = new ArrayList<>(List.of("e"));
            Map<SellerProfileField, List<String>> errors = new HashMap<>();
            errors.put(SellerProfileField.FULL_NAME, messages);
            errors.put(SellerProfileField.BIO, List.of());
            errors.put(SellerProfileField.NEW_PASSWORD, null);
            errors.put(null, List.of("ignored"));
            SellerProfileResult r = SellerProfileResult.invalidFields(seller(), errors);
            messages.add("added later");

            assertEquals(Set.of(SellerProfileField.FULL_NAME), r.getErrors().keySet());
            assertEquals(List.of("e"), r.getErrors(SellerProfileField.FULL_NAME));
            assertThrows(UnsupportedOperationException.class, () -> r.getErrors().clear());
            assertThrows(UnsupportedOperationException.class, () -> r.getAllMessages().clear());
        }

        @Test
        @DisplayName("refuses missing values and a result without any error message")
        void refusesInvalidArguments() {
            Map<SellerProfileField, List<String>> onlyEmpty = Map.of(SellerProfileField.BIO, List.of());
            assertThrows(NullPointerException.class, () -> SellerProfileResult.invalidFields(null, Map.of()));
            assertThrows(NullPointerException.class, () -> SellerProfileResult.invalidFields(seller(), null));
            assertThrows(IllegalArgumentException.class, () -> SellerProfileResult.invalidFields(seller(), Map.of()));
            assertThrows(IllegalArgumentException.class, () -> SellerProfileResult.invalidFields(seller(), onlyEmpty));
        }
    }

    @Test
    @DisplayName("toString() describes the result without any password")
    void resultToString() {
        String text = SellerProfileResult.success(seller(), Set.of(SellerProfileField.NEW_PASSWORD), 2, "Saved.")
                .toString();

        assertTrue(text.contains("SUCCESS"));
        assertTrue(text.contains("sam"));
        assertTrue(text.contains("NEW_PASSWORD"));
        assertTrue(text.contains("updatedListingCount=2"));
    }
}
