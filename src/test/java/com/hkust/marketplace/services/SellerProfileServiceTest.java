package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.ActOnListingResult;
import com.hkust.marketplace.models.AdminListingFilterCriteria;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.CreateListingRequest;
import com.hkust.marketplace.models.CreateListingResult;
import com.hkust.marketplace.models.LendingDurationUnit;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingFilterCriteria;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.ListingViewResult;
import com.hkust.marketplace.models.LoginErrorType;
import com.hkust.marketplace.models.LoginResult;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SellerProfileField;
import com.hkust.marketplace.models.SellerProfileResult;
import com.hkust.marketplace.models.SellerProfileStatus;
import com.hkust.marketplace.models.SellerProfileUpdateRequest;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserEditRequest;
import com.hkust.marketplace.models.UserManagementResult;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.PasswordPolicy;
import com.hkust.marketplace.utils.PasswordUtil;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of use case 2.6 "Manage Seller Profile" (US-S06), grouped by the flows of its
 * specification. Acceptance criterion of the Sprint 2 backlog: the updates are reflected across
 * all posted listings, so the name shown by the listings is also checked where the Buyer and Admin
 * portals read it (browsing, listing details, offers, the admin's listing search) and on a listing
 * created after the change.
 *
 * <p>The class diagram rules are checked too: the full name is required, the bio is optional, the
 * meet-up locations are a list of {@link MeetupLocation}, and the password is always stored hashed,
 * never in plaintext, following the same rules as at registration.</p>
 *
 * <p>The assertions compare values and message constants, never text formatted with the default
 * locale, so the tests give the same result in IntelliJ IDEA and with Maven, whatever the locale of
 * the computer.</p>
 */
@DisplayName("SellerProfileService: US-S06 Manage Seller Profile")
class SellerProfileServiceTest {

    private static final DummyDataService DATA = DummyDataService.getInstance();
    /** Password of the test Seller; it follows the password rules. */
    private static final String PASSWORD = "OldPass123";
    /** Hash of {@link #PASSWORD}, computed once since PBKDF2 is slow on purpose. */
    private static final String PASSWORD_HASH = PasswordHasher.hash(PASSWORD);
    /** A new password that follows the password rules. */
    private static final String NEW_PASSWORD = "NewPass456";
    /** The other accounts never log in with a password, so their stored hash is never checked. */
    private static final String UNUSED_HASH = "not-checked-by-these-tests";
    private static final String ADMIN = DummyDataService.SEEDED_ADMIN_USERNAME;
    /** First bytes of a PNG file: enough for Create Listing to accept the file as a photo. */
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    private final SellerProfileService service = new SellerProfileService(DATA);

    @TempDir
    Path tempDir;

    private Seller seller;
    private Seller otherSeller;
    private Buyer buyer;

    /**
     * Starts every test from the seeded data, with a fresh Seller (password {@link #PASSWORD},
     * a bio and the Library as preferred meet-up location), another Seller and a buyer, and the
     * Seller logged in.
     */
    @BeforeEach
    void setUp() {
        DATA.resetData();
        SessionManager.logout();
        seller = new Seller("s06_seller", "Sam Seller", PASSWORD_HASH, "Selling books.");
        seller.setPreferredMeetups(new ArrayList<>(List.of(MeetupLocation.LIBRARY)));
        assertTrue(DATA.registerUserIfAbsent(seller), "Test seller could not be stored");
        otherSeller = new Seller("s06_other", "Olive Other", UNUSED_HASH);
        assertTrue(DATA.registerUserIfAbsent(otherSeller), "Test seller could not be stored");
        buyer = new Buyer("s06_buyer", "Bob Buyer", UNUSED_HASH, "Hall 1");
        assertTrue(DATA.registerUserIfAbsent(buyer), "Test buyer could not be stored");
        SessionManager.login(seller);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** Stores a listing of a Seller with a given title and status, showing the Seller's current name. */
    private static Listing storeListing(Seller owner, String title, ListingStatus status) {
        Listing listing = new Listing();
        listing.setTitle(title);
        listing.setDescription("Test listing");
        listing.setCategory("Electronics");
        listing.setCondition("Used");
        listing.setMeetupLocation("Library");
        listing.setType(ListingType.SELL);
        listing.setPrice(200.0);
        listing.setSellerUsername(owner.getUsername());
        listing.setSellerFullName(owner.getFullName());
        listing.setStatus(status);
        DATA.addListing(listing);
        return listing;
    }

    /** The current values of the logged-in Seller's profile, as shown on the form. */
    private SellerProfileUpdateRequest form() {
        return SellerProfileService.toEditRequest(seller);
    }

    /** The form with another full name. */
    private SellerProfileUpdateRequest withName(String fullName) {
        SellerProfileUpdateRequest f = form();
        return SellerProfileUpdateRequest.withoutPasswordChange(fullName, f.bio(), f.preferredMeetups());
    }

    /** The form with another bio. */
    private SellerProfileUpdateRequest withBio(String bio) {
        SellerProfileUpdateRequest f = form();
        return SellerProfileUpdateRequest.withoutPasswordChange(f.fullName(), bio, f.preferredMeetups());
    }

    /** The form with other meet-up locations. */
    private SellerProfileUpdateRequest withMeetups(List<MeetupLocation> meetups) {
        SellerProfileUpdateRequest f = form();
        return SellerProfileUpdateRequest.withoutPasswordChange(f.fullName(), f.bio(), meetups);
    }

    /** The form with the three password fields filled in. */
    private SellerProfileUpdateRequest withPasswords(String current, String next, String confirm) {
        SellerProfileUpdateRequest f = form();
        return new SellerProfileUpdateRequest(f.fullName(), f.bio(), f.preferredMeetups(), current, next, confirm);
    }

    /** Saves a form that must succeed. */
    private SellerProfileResult saveOk(SellerProfileUpdateRequest request) {
        SellerProfileResult result = service.updateProfile(request);
        assertTrue(result.isSuccess(), result.toString());
        return result;
    }

    /** Saves a form that must be refused, and checks that the profile did not change. */
    private SellerProfileResult saveRefused(SellerProfileUpdateRequest request) {
        String name = seller.getFullName();
        String bio = seller.getBio();
        List<MeetupLocation> meetups = List.copyOf(seller.getPreferredMeetups());
        String hash = seller.getPasswordHash();
        SellerProfileResult result = service.updateProfile(request);
        assertEquals(SellerProfileStatus.INVALID_FIELDS, result.getStatus(), result.toString());
        assertTrue(result.getMessage().startsWith("Nothing was saved."));
        assertEquals(name, seller.getFullName(), "Full name must not change");
        assertEquals(bio, seller.getBio(), "Bio must not change");
        assertEquals(meetups, seller.getPreferredMeetups(), "Meet-up locations must not change");
        assertEquals(hash, seller.getPasswordHash(), "Password must not change");
        return result;
    }

    /** Writes a small PNG file that Create Listing accepts as a photo. */
    private Path photo(String name) throws IOException {
        return Files.write(tempDir.resolve(name), PNG_HEADER);
    }

    /** A valid Sell listing form for Create Listing (use case 2.3). */
    private static CreateListingRequest sellForm(String title, Path photo) {
        return new CreateListingRequest(ListingType.SELL, title, "A useful item.", Category.BOOKS, Condition.USED,
                "120", "", LendingDurationUnit.DAYS, MeetupLocation.LIBRARY, List.of(photo));
    }

    /** Text of {@code n} times the same character. */
    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    // ------------------------------------------------------------------
    // Access control
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Seller session is checked by every action (role-based access control)")
    class AccessControl {

        @Test
        @DisplayName("Without a session, the profile can neither be read nor saved")
        void noSession() {
            SessionManager.logout();

            assertThrows(SecurityException.class, service::getMyProfile);
            assertThrows(SecurityException.class, () -> service.updateProfile(withName("Hacker")));
            assertEquals("Sam Seller", seller.getFullName());
        }

        @Test
        @DisplayName("A Buyer session cannot read or save a Seller profile")
        void buyerSession() {
            SessionManager.login(buyer);

            assertThrows(SecurityException.class, service::getMyProfile);
            assertThrows(SecurityException.class, () -> service.updateProfile(withName("Hacker")));
            assertEquals("Sam Seller", seller.getFullName());
            assertEquals("Bob Buyer", buyer.getFullName());
        }

        @Test
        @DisplayName("An Admin session cannot read or save a Seller profile")
        void adminSession() {
            User admin = DATA.findUser(ADMIN);
            SessionManager.login(admin);
            String adminName = admin.getFullName();

            assertThrows(SecurityException.class, service::getMyProfile);
            assertThrows(SecurityException.class, () -> service.updateProfile(withName("Hacker")));
            assertEquals(adminName, admin.getFullName());
        }

        @ParameterizedTest(name = "a Seller {0} during the session is refused")
        @EnumSource(value = AccountStatus.class, names = {"DEACTIVATED", "DELETED"})
        @DisplayName("A Seller deactivated or deleted by the admin during the session is refused")
        void inactiveSeller(AccountStatus status) {
            SellerProfileUpdateRequest rename = withName("Too Late");
            seller.setAccountStatus(status);

            assertThrows(SecurityException.class, service::getMyProfile);
            assertThrows(SecurityException.class, () -> service.updateProfile(rename));
            assertEquals("Sam Seller", seller.getFullName());
        }

        @Test
        @DisplayName("A Seller only changes their own profile and listings")
        void onlyOwnProfile() {
            Listing others = storeListing(otherSeller, "Other's Lamp", ListingStatus.LIVE);

            saveOk(withName("Sam Renamed"));

            assertEquals("Olive Other", otherSeller.getFullName());
            assertEquals("Olive Other", others.getSellerFullName());
        }

        @Test
        @DisplayName("A missing form is refused")
        void nullRequest() {
            assertThrows(NullPointerException.class, () -> service.updateProfile(null));
        }

        @Test
        @DisplayName("The Admin's user management sees the profile the Seller saved (use case 3.3)")
        void adminSeesSavedProfile() {
            saveOk(SellerProfileUpdateRequest.withoutPasswordChange("Sam Lee", "New bio",
                    List.of(MeetupLocation.ATRIUM)));

            User seen = DATA.getUserManagementService().getUser(seller.getUsername()).orElseThrow();
            assertEquals("Sam Lee", seen.getFullName());
            assertEquals("New bio", seen.getBio());
            assertEquals(List.of(MeetupLocation.ATRIUM), ((Seller) seen).getPreferredMeetups());
        }

        @Test
        @DisplayName("The Seller's form shows the changes the admin made to the account (use case 3.3)")
        void formShowsAdminChanges() {
            UserManagementResult edited = DATA.getUserManagementService().editUser(UserEditRequest.builder()
                    .targetUsername(seller.getUsername()).adminUsername(ADMIN).newFullName("Sam Admin-Edited")
                    .bio("Edited by the admin").preferredMeetups(List.of(MeetupLocation.NORTH_GATE)).build());
            assertTrue(edited.isSuccess(), edited.getMessage());

            SellerProfileUpdateRequest shown = SellerProfileService.toEditRequest(service.getMyProfile());
            assertEquals("Sam Admin-Edited", shown.fullName());
            assertEquals("Edited by the admin", shown.bio());
            assertEquals(List.of(MeetupLocation.NORTH_GATE), shown.preferredMeetups());
        }
    }

    // ------------------------------------------------------------------
    // Basic flow, step 2
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Step 2: the profile shows the username, full name, bio and meet-up locations")
    class ShowProfile {

        @Test
        @DisplayName("getMyProfile() returns the logged-in Seller, as stored")
        void getMyProfile() {
            Seller shown = service.getMyProfile();

            assertSame(seller, shown);
            assertSame(DATA.findUser(seller.getUsername()), shown);
            assertEquals("s06_seller", shown.getUsername());
        }

        @Test
        @DisplayName("The service of the screen, created without a data store, uses the application's data store")
        void defaultConstructor() {
            assertSame(seller, new SellerProfileService().getMyProfile());
            assertThrows(NullPointerException.class, () -> new SellerProfileService(null));
        }

        @Test
        @DisplayName("The form is filled with the current values and empty password fields")
        void formValues() {
            SellerProfileUpdateRequest f = SellerProfileService.toEditRequest(seller);

            assertEquals("Sam Seller", f.fullName());
            assertEquals("Selling books.", f.bio());
            assertEquals(List.of(MeetupLocation.LIBRARY), f.preferredMeetups());
            assertEquals("", f.currentPassword());
            assertEquals("", f.newPassword());
            assertEquals("", f.confirmPassword());
            assertFalse(f.isPasswordChangeRequested());
        }

        @Test
        @DisplayName("A Seller without bio nor meet-up locations gets empty fields")
        void emptyProfile() {
            seller.setBio(null);
            seller.setPreferredMeetups(null);

            SellerProfileUpdateRequest f = SellerProfileService.toEditRequest(seller);

            assertEquals("", f.bio());
            assertEquals(List.of(), f.preferredMeetups());
        }

        @Test
        @DisplayName("Stored meet-up locations are shown without duplicates, in the order of the list")
        void meetupsNormalised() {
            seller.setPreferredMeetups(new ArrayList<>(List.of(MeetupLocation.SOUTH_GATE, MeetupLocation.ATRIUM,
                    MeetupLocation.SOUTH_GATE)));

            assertEquals(List.of(MeetupLocation.ATRIUM, MeetupLocation.SOUTH_GATE),
                    SellerProfileService.toEditRequest(seller).preferredMeetups());
        }

        @Test
        @DisplayName("The seeded seller charlie sees the values chosen in the demo data")
        void seededSeller() {
            Seller charlie = (Seller) DATA.findUser("charlie");

            SellerProfileUpdateRequest f = SellerProfileService.toEditRequest(charlie);

            assertEquals("Charlie Lee", f.fullName());
            assertEquals(List.of(MeetupLocation.LIBRARY), f.preferredMeetups());
        }

        @Test
        @DisplayName("Filling the form of a missing Seller is refused")
        void nullSeller() {
            assertThrows(NullPointerException.class, () -> SellerProfileService.toEditRequest(null));
        }

        @Test
        @DisplayName("Saving the form unchanged changes nothing")
        void saveUnchanged() {
            String hash = seller.getPasswordHash();

            SellerProfileResult result = service.updateProfile(form());

            assertEquals(SellerProfileStatus.NO_CHANGES, result.getStatus());
            assertEquals(SellerProfileService.NOTHING_CHANGED, result.getMessage());
            assertSame(seller, result.getSeller());
            assertEquals("Sam Seller", seller.getFullName());
            assertEquals(hash, seller.getPasswordHash());
        }

        @Test
        @DisplayName("Spaces around the values, and the order of the meet-up locations, are not changes")
        void cosmeticDifferencesAreNotChanges() {
            seller.setPreferredMeetups(new ArrayList<>(List.of(MeetupLocation.LIBRARY, MeetupLocation.ATRIUM)));

            SellerProfileResult result = service.updateProfile(SellerProfileUpdateRequest.withoutPasswordChange(
                    "  Sam Seller ", " Selling books.  ",
                    List.of(MeetupLocation.ATRIUM, MeetupLocation.LIBRARY, MeetupLocation.ATRIUM)));

            assertEquals(SellerProfileStatus.NO_CHANGES, result.getStatus());
            assertEquals(List.of(MeetupLocation.LIBRARY, MeetupLocation.ATRIUM), seller.getPreferredMeetups());
        }
    }

    // ------------------------------------------------------------------
    // Basic flow, steps 3, 5 to 8
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Steps 3 and 5 to 8: the full name, bio and meet-up locations are saved")
    class SaveProfile {

        @Test
        @DisplayName("A new full name is saved trimmed, and the Seller is told what changed")
        void fullName() {
            SellerProfileResult result = saveOk(withName("  Samuel Seller  "));

            assertEquals("Samuel Seller", seller.getFullName());
            assertEquals(Set.of(SellerProfileField.FULL_NAME), result.getChangedFields());
            assertFalse(result.isPasswordChanged());
            assertTrue(result.getMessage().startsWith("Your profile has been updated: full name."),
                    result.getMessage());
            assertSame(seller, result.getSeller());
        }

        @Test
        @DisplayName("A new bio is saved trimmed")
        void bio() {
            SellerProfileResult result = saveOk(withBio("  Final year student.\nSelling gadgets.  "));

            assertEquals("Final year student.\nSelling gadgets.", seller.getBio());
            assertEquals(Set.of(SellerProfileField.BIO), result.getChangedFields());
        }

        @ParameterizedTest(name = "bio [{0}] removes the bio")
        @ValueSource(strings = {"", "   ", "\n\t"})
        @DisplayName("A blank bio removes the bio, which is optional (0..1)")
        void blankBioRemovesIt(String blank) {
            saveOk(withBio(blank));

            assertNull(seller.getBio());
        }

        @Test
        @DisplayName("A null bio also removes it")
        void nullBio() {
            saveOk(withBio(null));

            assertNull(seller.getBio());
        }

        @Test
        @DisplayName("Meet-up locations are saved without duplicates nor empty choices, in the order of the list")
        void meetups() {
            SellerProfileResult result = saveOk(withMeetups(Arrays.asList(MeetupLocation.STUDENT_HALLS, null,
                    MeetupLocation.ATRIUM, MeetupLocation.STUDENT_HALLS)));

            assertEquals(List.of(MeetupLocation.ATRIUM, MeetupLocation.STUDENT_HALLS), seller.getPreferredMeetups());
            assertEquals(Set.of(SellerProfileField.PREFERRED_MEETUPS), result.getChangedFields());
        }

        @Test
        @DisplayName("Every meet-up location can be removed, since they are optional (0..*)")
        void noMeetups() {
            saveOk(withMeetups(List.of()));

            assertEquals(List.of(), seller.getPreferredMeetups());
        }

        @Test
        @DisplayName("Every meet-up location can be chosen")
        void allMeetups() {
            saveOk(withMeetups(List.of(MeetupLocation.values())));

            assertEquals(List.of(MeetupLocation.values()), seller.getPreferredMeetups());
        }

        @Test
        @DisplayName("The saved meet-up locations are a copy: changing them later on the Seller only")
        void meetupsCopied() {
            List<MeetupLocation> chosen = new ArrayList<>(List.of(MeetupLocation.ATRIUM));
            saveOk(withMeetups(chosen));
            chosen.add(MeetupLocation.NORTH_GATE);

            assertEquals(List.of(MeetupLocation.ATRIUM), seller.getPreferredMeetups());
        }

        @Test
        @DisplayName("Several fields are saved at once and listed in form order")
        void severalFields() {
            SellerProfileResult result = saveOk(SellerProfileUpdateRequest.withoutPasswordChange("Sam Lee",
                    "New bio", List.of(MeetupLocation.NORTH_GATE)));

            assertEquals("Sam Lee", seller.getFullName());
            assertEquals("New bio", seller.getBio());
            assertEquals(List.of(MeetupLocation.NORTH_GATE), seller.getPreferredMeetups());
            assertEquals(List.of(SellerProfileField.FULL_NAME, SellerProfileField.BIO,
                    SellerProfileField.PREFERRED_MEETUPS), List.copyOf(result.getChangedFields()));
            assertTrue(result.getMessage().startsWith(
                    "Your profile has been updated: full name, bio and preferred meet-up locations."),
                    result.getMessage());
        }

        @Test
        @DisplayName("The username cannot be changed by a save")
        void usernameKept() {
            saveOk(withName("Someone Else"));

            assertEquals("s06_seller", seller.getUsername());
            assertSame(seller, DATA.findUser("s06_seller"));
        }

        @Test
        @DisplayName("The first preferred meet-up location is the one Create Listing preselects (use case 2.3)")
        void firstMeetupForNewListings() {
            saveOk(withMeetups(List.of(MeetupLocation.SOUTH_GATE, MeetupLocation.ATRIUM)));

            assertEquals(MeetupLocation.ATRIUM, seller.getPreferredMeetups().get(0));
        }
    }

    // ------------------------------------------------------------------
    // Length limits
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Length limits of the full name and the bio")
    class LengthLimits {

        @Test
        @DisplayName("A full name of exactly the maximum length is accepted")
        void fullNameAtLimit() {
            String name = repeat('a', SellerProfileService.MAX_FULL_NAME_LENGTH);

            saveOk(withName(name));

            assertEquals(name, seller.getFullName());
        }

        @Test
        @DisplayName("A full name one character too long is refused")
        void fullNameTooLong() {
            SellerProfileResult result = saveRefused(withName(repeat('a', SellerProfileService.MAX_FULL_NAME_LENGTH + 1)));

            assertEquals(List.of(SellerProfileService.FULL_NAME_TOO_LONG), result.getErrors(SellerProfileField.FULL_NAME));
        }

        @Test
        @DisplayName("Characters are counted as the Seller sees them: an emoji counts as one")
        void emojiCountsAsOne() {
            String smile = new String(Character.toChars(0x1F600));
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < SellerProfileService.MAX_FULL_NAME_LENGTH; i++) {
                name.append(smile);
            }

            saveOk(withName(name.toString()));

            assertEquals(SellerProfileService.MAX_FULL_NAME_LENGTH, SellerProfileService.lengthOf(seller.getFullName()));
        }

        @Test
        @DisplayName("A bio of exactly the maximum length is accepted; one character more is refused")
        void bioLimit() {
            saveOk(withBio(repeat('b', SellerProfileService.MAX_BIO_LENGTH)));
            assertEquals(SellerProfileService.MAX_BIO_LENGTH, seller.getBio().length());

            SellerProfileResult result = saveRefused(withBio(repeat('c', SellerProfileService.MAX_BIO_LENGTH + 1)));
            assertEquals(List.of(SellerProfileService.BIO_TOO_LONG), result.getErrors(SellerProfileField.BIO));
        }

        @Test
        @DisplayName("Spaces around a value are not counted")
        void spacesNotCounted() {
            saveOk(withBio("   " + repeat('b', SellerProfileService.MAX_BIO_LENGTH) + "   "));

            assertEquals(SellerProfileService.MAX_BIO_LENGTH, seller.getBio().length());
        }

        @Test
        @DisplayName("A longer name or bio saved before (at registration or by the admin) can be kept unchanged")
        void oldLongValuesKept() {
            String longName = repeat('n', SellerProfileService.MAX_FULL_NAME_LENGTH + 20);
            String longBio = repeat('b', SellerProfileService.MAX_BIO_LENGTH + 20);
            seller.setFullName(longName);
            seller.setBio(longBio);

            SellerProfileResult result = saveOk(withMeetups(List.of(MeetupLocation.ATRIUM)));

            assertEquals(Set.of(SellerProfileField.PREFERRED_MEETUPS), result.getChangedFields());
            assertEquals(longName, seller.getFullName());
            assertEquals(longBio, seller.getBio());
        }

        @Test
        @DisplayName("lengthOf() trims and treats null as empty")
        void lengthOf() {
            assertEquals(0, SellerProfileService.lengthOf(null));
            assertEquals(0, SellerProfileService.lengthOf("   "));
            assertEquals(3, SellerProfileService.lengthOf("  abc "));
        }
    }

    // ------------------------------------------------------------------
    // Basic flow, steps 4 and 7: change the password
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Steps 4 and 7: the password is changed and stored hashed")
    class ChangePassword {

        @Test
        @DisplayName("The new password is stored hashed with PBKDF2, never in plaintext")
        void storedHashed() {
            SellerProfileResult result = saveOk(withPasswords(PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

            String stored = seller.getPasswordHash();
            assertTrue(result.isPasswordChanged());
            assertEquals(Set.of(SellerProfileField.NEW_PASSWORD), result.getChangedFields());
            assertTrue(stored.startsWith("pbkdf2_sha256$"), stored);
            assertFalse(stored.contains(NEW_PASSWORD));
            assertTrue(PasswordHasher.verify(NEW_PASSWORD, stored));
            assertFalse(PasswordHasher.verify(PASSWORD, stored));
            assertTrue(result.getMessage().startsWith("Your profile has been updated: password."), result.getMessage());
        }

        @Test
        @DisplayName("The Seller logs in with the new password, and no longer with the old one (use case 2.2)")
        void loginWithNewPassword() {
            saveOk(withPasswords(PASSWORD, NEW_PASSWORD, NEW_PASSWORD));
            SellerLoginService login = new SellerLoginService(DATA);

            LoginResult withNew = login.authenticate(seller.getUsername(), NEW_PASSWORD);
            LoginResult withOld = login.authenticate(seller.getUsername(), PASSWORD);

            assertTrue(withNew.isSuccess(), withNew.getMessage());
            assertSame(seller, withNew.getUser());
            assertFalse(withOld.isSuccess());
            assertEquals(LoginErrorType.INVALID_CREDENTIALS, withOld.getErrorType());
        }

        @Test
        @DisplayName("The full name and the password are changed together")
        void nameAndPassword() {
            SellerProfileUpdateRequest f = form();
            SellerProfileResult result = saveOk(new SellerProfileUpdateRequest("Sam Lee", f.bio(),
                    f.preferredMeetups(), PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

            assertEquals("Sam Lee", seller.getFullName());
            assertTrue(PasswordHasher.verify(NEW_PASSWORD, seller.getPasswordHash()));
            assertEquals(List.of(SellerProfileField.FULL_NAME, SellerProfileField.NEW_PASSWORD),
                    List.copyOf(result.getChangedFields()));
            assertTrue(result.getMessage().startsWith("Your profile has been updated: full name and password."),
                    result.getMessage());
        }

        @Test
        @DisplayName("Passwords are used exactly as typed: spaces are part of the password")
        void passwordNotTrimmed() {
            String spaced = " " + NEW_PASSWORD + " ";
            saveOk(withPasswords(PASSWORD, spaced, spaced));

            assertTrue(PasswordHasher.verify(spaced, seller.getPasswordHash()));
            assertFalse(PasswordHasher.verify(NEW_PASSWORD, seller.getPasswordHash()));
        }

        @Test
        @DisplayName("The seeded seller charlie changes the demo password password1")
        void seededSeller() {
            Seller charlie = (Seller) DATA.findUser("charlie");
            SessionManager.login(charlie);
            SellerProfileUpdateRequest f = SellerProfileService.toEditRequest(charlie);

            SellerProfileResult result = service.updateProfile(new SellerProfileUpdateRequest(f.fullName(), f.bio(),
                    f.preferredMeetups(), "password1", NEW_PASSWORD, NEW_PASSWORD));

            assertTrue(result.isSuccess(), result.toString());
            assertTrue(PasswordHasher.verify(NEW_PASSWORD, charlie.getPasswordHash()));
        }

        @Test
        @DisplayName("A password stored in an older hash format is recognised, then stored with PBKDF2")
        void olderHashFormat() {
            seller.setPasswordHash(PasswordUtil.hashPassword(PASSWORD));

            saveOk(withPasswords(PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

            assertTrue(seller.getPasswordHash().startsWith("pbkdf2_sha256$"));
            assertTrue(PasswordHasher.verify(NEW_PASSWORD, seller.getPasswordHash()));
        }

        @Test
        @DisplayName("Leaving the three password fields empty keeps the current password")
        void passwordKept() {
            String hash = seller.getPasswordHash();

            saveOk(withName("Sam Lee"));

            assertEquals(hash, seller.getPasswordHash());
        }
    }

    // ------------------------------------------------------------------
    // Alternative flows A1, A2 and A3
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("A1: Empty Full Name")
    class EmptyFullName {

        @ParameterizedTest(name = "full name [{0}] is refused")
        @ValueSource(strings = {"", "   ", "\t\n"})
        @DisplayName("An empty or blank full name is refused and nothing is saved")
        void blankName(String blank) {
            SellerProfileResult result = saveRefused(withName(blank));

            assertEquals(List.of(SellerProfileService.FULL_NAME_REQUIRED), result.getErrors(SellerProfileField.FULL_NAME));
            assertEquals(SellerProfileField.FULL_NAME, result.getFirstInvalidField());
            assertEquals("Full name is required.", SellerProfileService.FULL_NAME_REQUIRED);
        }

        @Test
        @DisplayName("A null full name is refused")
        void nullName() {
            SellerProfileResult result = saveRefused(withName(null));

            assertTrue(result.hasError(SellerProfileField.FULL_NAME));
        }

        @Test
        @DisplayName("Valid changes to the other fields are not saved either")
        void otherChangesNotSaved() {
            saveRefused(new SellerProfileUpdateRequest(" ", "A new bio", List.of(MeetupLocation.ATRIUM),
                    PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

            assertEquals("Selling books.", seller.getBio());
            assertTrue(PasswordHasher.verify(PASSWORD, seller.getPasswordHash()));
        }
    }

    @Nested
    @DisplayName("A2: Wrong Current Password")
    class WrongCurrentPassword {

        @Test
        @DisplayName("A wrong current password is refused and nothing is saved, not even the other changes")
        void wrongPassword() {
            SellerProfileUpdateRequest f = form();
            SellerProfileResult result = saveRefused(new SellerProfileUpdateRequest("Sam Lee", "New bio",
                    f.preferredMeetups(), "WrongPass1", NEW_PASSWORD, NEW_PASSWORD));

            assertEquals(List.of(SellerProfileService.WRONG_CURRENT_PASSWORD),
                    result.getErrors(SellerProfileField.CURRENT_PASSWORD));
            assertEquals(Set.of(SellerProfileField.CURRENT_PASSWORD), result.getErrors().keySet());
            assertEquals("Sam Seller", seller.getFullName());
        }

        @ParameterizedTest(name = "current password [{0}] is wrong")
        @ValueSource(strings = {"oldpass123", "OLDPASS123", " OldPass123", "OldPass123 ", "OldPass12"})
        @DisplayName("The current password is compared exactly: case and spaces matter")
        void exactComparison(String almost) {
            SellerProfileResult result = saveRefused(withPasswords(almost, NEW_PASSWORD, NEW_PASSWORD));

            assertEquals(List.of(SellerProfileService.WRONG_CURRENT_PASSWORD),
                    result.getErrors(SellerProfileField.CURRENT_PASSWORD));
        }

        @Test
        @DisplayName("Changing the password without the current password is refused")
        void missingCurrentPassword() {
            SellerProfileResult result = saveRefused(withPasswords("", NEW_PASSWORD, NEW_PASSWORD));

            assertEquals(List.of(SellerProfileService.CURRENT_PASSWORD_REQUIRED),
                    result.getErrors(SellerProfileField.CURRENT_PASSWORD));
            assertEquals(Set.of(SellerProfileField.CURRENT_PASSWORD), result.getErrors().keySet());
        }

        @Test
        @DisplayName("Null password fields count as empty")
        void nullPasswords() {
            SellerProfileUpdateRequest f = form();
            SellerProfileResult result = saveRefused(new SellerProfileUpdateRequest(f.fullName(), f.bio(),
                    f.preferredMeetups(), null, NEW_PASSWORD, null));

            assertEquals(List.of(SellerProfileService.CURRENT_PASSWORD_REQUIRED),
                    result.getErrors(SellerProfileField.CURRENT_PASSWORD));
            assertEquals(List.of(SellerProfileService.CONFIRM_PASSWORD_REQUIRED),
                    result.getErrors(SellerProfileField.CONFIRM_PASSWORD));
        }

        @Test
        @DisplayName("Only the current password typed: the new password is asked for")
        void onlyCurrentPassword() {
            SellerProfileResult result = saveRefused(withPasswords(PASSWORD, "", ""));

            assertEquals(Set.of(SellerProfileField.NEW_PASSWORD), result.getErrors().keySet());
            assertEquals(List.of(SellerProfileService.NEW_PASSWORD_REQUIRED),
                    result.getErrors(SellerProfileField.NEW_PASSWORD));
        }
    }

    @Nested
    @DisplayName("A3: Invalid New Password")
    class InvalidNewPassword {

        static Stream<Arguments> weakPasswords() {
            return Stream.of(
                    Arguments.of("Short1A", List.of(PasswordPolicy.RULE_MIN_LENGTH)),
                    Arguments.of("nouppercase1", List.of(PasswordPolicy.RULE_UPPERCASE)),
                    Arguments.of("NOLOWERCASE1", List.of(PasswordPolicy.RULE_LOWERCASE)),
                    Arguments.of("NoDigitsHere", List.of(PasswordPolicy.RULE_DIGIT)),
                    Arguments.of("abc", List.of(PasswordPolicy.RULE_MIN_LENGTH, PasswordPolicy.RULE_UPPERCASE,
                            PasswordPolicy.RULE_DIGIT)),
                    Arguments.of("        ", List.of(PasswordPolicy.RULE_UPPERCASE, PasswordPolicy.RULE_LOWERCASE,
                            PasswordPolicy.RULE_DIGIT)));
        }

        @ParameterizedTest(name = "[{0}] breaks {1}")
        @MethodSource("weakPasswords")
        @DisplayName("A new password that breaks the registration rules is refused, with every rule it breaks")
        void weakPassword(String weak, List<String> brokenRules) {
            SellerProfileResult result = saveRefused(withPasswords(PASSWORD, weak, weak));

            List<String> errors = result.getErrors(SellerProfileField.NEW_PASSWORD);
            assertEquals(1, errors.size());
            assertTrue(errors.get(0).startsWith(SellerProfileService.WEAK_PASSWORD_PREFIX), errors.get(0));
            for (String rule : PasswordPolicy.rules()) {
                assertEquals(brokenRules.contains(rule), errors.get(0).contains(rule), rule);
            }
            assertEquals(Set.of(SellerProfileField.NEW_PASSWORD), result.getErrors().keySet());
        }

        @Test
        @DisplayName("The rules are listed in readable English")
        void readableRules() {
            SellerProfileResult result = saveRefused(withPasswords(PASSWORD, "abc", "abc"));

            assertEquals(List.of("New password must contain at least 8 characters, at least one uppercase letter"
                    + " and at least one digit."), result.getErrors(SellerProfileField.NEW_PASSWORD));
        }

        @Test
        @DisplayName("A confirmation that differs from the new password is refused")
        void mismatch() {
            SellerProfileResult result = saveRefused(withPasswords(PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x"));

            assertEquals(List.of(SellerProfileService.PASSWORDS_DO_NOT_MATCH),
                    result.getErrors(SellerProfileField.CONFIRM_PASSWORD));
            assertEquals(Set.of(SellerProfileField.CONFIRM_PASSWORD), result.getErrors().keySet());
        }

        @Test
        @DisplayName("The confirmation is compared exactly: case matters")
        void mismatchCase() {
            SellerProfileResult result = saveRefused(withPasswords(PASSWORD, NEW_PASSWORD, NEW_PASSWORD.toLowerCase()));

            assertTrue(result.hasError(SellerProfileField.CONFIRM_PASSWORD));
        }

        @Test
        @DisplayName("A missing confirmation is asked for")
        void missingConfirmation() {
            SellerProfileResult result = saveRefused(withPasswords(PASSWORD, NEW_PASSWORD, ""));

            assertEquals(List.of(SellerProfileService.CONFIRM_PASSWORD_REQUIRED),
                    result.getErrors(SellerProfileField.CONFIRM_PASSWORD));
        }

        @Test
        @DisplayName("A new password identical to the current one is refused")
        void sameAsCurrent() {
            SellerProfileResult result = saveRefused(withPasswords(PASSWORD, PASSWORD, PASSWORD));

            assertEquals(List.of(SellerProfileService.NEW_PASSWORD_SAME_AS_CURRENT),
                    result.getErrors(SellerProfileField.NEW_PASSWORD));
            assertEquals(Set.of(SellerProfileField.NEW_PASSWORD), result.getErrors().keySet());
        }

        @Test
        @DisplayName("With a wrong current password, only the current password is reported, not the repetition")
        void sameAsWrongCurrent() {
            SellerProfileResult result = saveRefused(withPasswords("WrongPass1", "WrongPass1", "WrongPass1"));

            assertEquals(Set.of(SellerProfileField.CURRENT_PASSWORD), result.getErrors().keySet());
        }

        @Test
        @DisplayName("Only the confirmation typed: the current and new passwords are asked for")
        void onlyConfirmation() {
            SellerProfileResult result = saveRefused(withPasswords("", "", NEW_PASSWORD));

            assertEquals(List.of(SellerProfileField.CURRENT_PASSWORD, SellerProfileField.NEW_PASSWORD),
                    List.copyOf(result.getErrors().keySet()));
        }

        @Test
        @DisplayName("A weak and mismatched new password reports both problems")
        void weakAndMismatch() {
            SellerProfileResult result = saveRefused(withPasswords(PASSWORD, "abc", "abd"));

            assertEquals(List.of(SellerProfileField.NEW_PASSWORD, SellerProfileField.CONFIRM_PASSWORD),
                    List.copyOf(result.getErrors().keySet()));
        }
    }

    @Nested
    @DisplayName("Step 6: every invalid field is reported at once, in form order")
    class AllErrors {

        @Test
        @DisplayName("Five invalid fields are all reported, the first one getting the focus")
        void everyError() {
            SellerProfileResult result = saveRefused(new SellerProfileUpdateRequest(" ",
                    repeat('b', SellerProfileService.MAX_BIO_LENGTH + 1), List.of(MeetupLocation.ATRIUM),
                    "WrongPass1", "abc", "abd"));

            assertEquals(List.of(SellerProfileField.FULL_NAME, SellerProfileField.BIO,
                            SellerProfileField.CURRENT_PASSWORD, SellerProfileField.NEW_PASSWORD,
                            SellerProfileField.CONFIRM_PASSWORD),
                    List.copyOf(result.getErrors().keySet()));
            assertEquals(SellerProfileField.FULL_NAME, result.getFirstInvalidField());
            assertEquals("Nothing was saved. Please correct the 5 highlighted fields.", result.getMessage());
            assertEquals(5, result.getAllMessages().size());
        }
    }

    // ------------------------------------------------------------------
    // Alternative flow A4
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("A4: Cancel Changes (isUnchanged tells whether there is something to discard)")
    class CancelChanges {

        @Test
        @DisplayName("The form as shown has nothing to discard")
        void formAsShown() {
            assertTrue(SellerProfileService.isUnchanged(seller, form()));
        }

        @Test
        @DisplayName("Spaces, blank versus missing bio, and the order of the meet-up locations are not changes")
        void cosmeticDifferences() {
            seller.setBio(null);
            assertTrue(SellerProfileService.isUnchanged(seller, SellerProfileUpdateRequest.withoutPasswordChange(
                    " Sam Seller ", "   ", Arrays.asList(MeetupLocation.LIBRARY, null, MeetupLocation.LIBRARY))));
        }

        @Test
        @DisplayName("Each changed field is something to discard")
        void eachChange() {
            assertFalse(SellerProfileService.isUnchanged(seller, withName("Sam Lee")));
            assertFalse(SellerProfileService.isUnchanged(seller, withBio("Another bio")));
            assertFalse(SellerProfileService.isUnchanged(seller, withMeetups(List.of())));
            assertFalse(SellerProfileService.isUnchanged(seller, withPasswords("x", "", "")));
            assertFalse(SellerProfileService.isUnchanged(seller, withPasswords("", "x", "")));
            assertFalse(SellerProfileService.isUnchanged(seller, withPasswords("", "", "x")));
        }

        @Test
        @DisplayName("Checking the form changes nothing and needs no session")
        void noSideEffect() {
            SessionManager.logout();

            assertFalse(SellerProfileService.isUnchanged(seller, withName("Sam Lee")));
            assertEquals("Sam Seller", seller.getFullName());
        }

        @Test
        @DisplayName("Missing arguments are refused")
        void nullArguments() {
            assertThrows(NullPointerException.class, () -> SellerProfileService.isUnchanged(null, form()));
            assertThrows(NullPointerException.class, () -> SellerProfileService.isUnchanged(seller, null));
        }
    }

    // ------------------------------------------------------------------
    // Acceptance criterion
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Acceptance criterion: updates are reflected across all posted listings")
    class ReflectedOnListings {

        @Test
        @DisplayName("A new name is shown on every listing of the Seller, whatever its status")
        void everyStatus() {
            List<Listing> mine = new ArrayList<>();
            for (ListingStatus status : ListingStatus.values()) {
                mine.add(storeListing(seller, "Item " + status, status));
            }
            Listing deleted = storeListing(seller, "Deleted item", ListingStatus.REMOVED);
            deleted.setDeletedBySeller(true);
            mine.add(deleted);
            Listing others = storeListing(otherSeller, "Other's item", ListingStatus.LIVE);

            SellerProfileResult result = saveOk(withName("Sam Lee"));

            for (Listing listing : mine) {
                assertEquals("Sam Lee", listing.getSellerFullName(), listing.getTitle());
            }
            assertEquals("Olive Other", others.getSellerFullName());
            assertEquals(mine.size(), result.getUpdatedListingCount());
            assertTrue(result.getMessage().endsWith(mine.size() + " of your listings now show your name \"Sam Lee\"."),
                    result.getMessage());
        }

        @Test
        @DisplayName("With one listing, the message uses the singular")
        void singular() {
            storeListing(seller, "Lamp", ListingStatus.LIVE);

            SellerProfileResult result = saveOk(withName("Sam Lee"));

            assertEquals(1, result.getUpdatedListingCount());
            assertTrue(result.getMessage().endsWith("1 of your listings now shows your name \"Sam Lee\"."),
                    result.getMessage());
        }

        @Test
        @DisplayName("Without listings, nothing is updated and the message does not mention listings")
        void noListings() {
            SellerProfileResult result = saveOk(withName("Sam Lee"));

            assertEquals(0, result.getUpdatedListingCount());
            assertFalse(result.getMessage().contains("listing"), result.getMessage());
        }

        @Test
        @DisplayName("Changing only the bio or the meet-up locations leaves the listings unchanged")
        void otherChangesKeepListings() {
            Listing listing = storeListing(seller, "Lamp", ListingStatus.LIVE);
            listing.setMeetupLocation(MeetupLocation.NORTH_GATE);

            SellerProfileResult result = saveOk(SellerProfileUpdateRequest.withoutPasswordChange("Sam Seller",
                    "Another bio", List.of(MeetupLocation.ATRIUM)));

            assertEquals(0, result.getUpdatedListingCount());
            assertEquals("Sam Seller", listing.getSellerFullName());
            assertEquals(MeetupLocation.NORTH_GATE, listing.getMeetupLocation(),
                    "The meet-up location chosen for a listing is a choice of that listing");
        }

        @Test
        @DisplayName("A listing still showing an outdated name gets the current name on the next save")
        void outdatedNameRepaired() {
            Listing listing = storeListing(seller, "Lamp", ListingStatus.LIVE);
            listing.setSellerFullName("An Old Name");

            SellerProfileResult result = saveOk(withBio("Another bio"));

            assertEquals("Sam Seller", listing.getSellerFullName());
            assertEquals(1, result.getUpdatedListingCount());
        }

        @Test
        @DisplayName("Buyers browsing the marketplace see the new name (use case 1.3)")
        void browse() {
            Listing listing = storeListing(seller, "Lamp", ListingStatus.LIVE);
            saveOk(withName("Sam Lee"));

            Listing seen = DATA.getFilteredLiveListings(ListingFilterCriteria.all()).stream()
                    .filter(l -> l.getId().equals(listing.getId())).findFirst().orElseThrow();
            assertEquals("Sam Lee", seen.getSellerFullName());
        }

        @Test
        @DisplayName("The listing detail shows the new name, with the Seller's rating (use case 1.4)")
        void listingDetail() {
            Listing listing = storeListing(seller, "Lamp", ListingStatus.LIVE);
            saveOk(withName("Sam Lee"));

            ListingViewResult view = DATA.viewListing(listing.getId(), true);

            assertTrue(view.isSuccess(), view.getMessage());
            assertEquals("Sam Lee", view.getListing().getSellerFullName());
            assertEquals("Sam Lee", view.getSellerRating().getSellerFullName());
        }

        @Test
        @DisplayName("A buyer's offer is confirmed with the new name (use case 1.5)")
        void offerConfirmation() {
            Listing listing = storeListing(seller, "Lamp", ListingStatus.LIVE);
            saveOk(withName("Sam Lee"));

            ActOnListingResult offer = DATA.submitPriceOffer(listing.getId(), buyer.getUsername(), 150.0);

            assertTrue(offer.isSuccess(), offer.getMessage());
            assertTrue(offer.getMessage().contains("Sam Lee"), offer.getMessage());
        }

        @Test
        @DisplayName("The admin finds the listings by the new name, and no longer by the old one (use case 3.7)")
        void adminSearch() {
            Listing listing = storeListing(seller, "Lamp", ListingStatus.LIVE);
            saveOk(withName("Samantha Lee"));
            AdminListingService admin = new AdminListingService(DATA);

            List<Listing> byNewName = admin.filterListings(AdminListingFilterCriteria.byKeyword("Samantha"));
            List<Listing> byOldName = admin.filterListings(AdminListingFilterCriteria.byKeyword("Sam Seller"));

            assertTrue(byNewName.contains(listing));
            assertFalse(byOldName.contains(listing));
        }

        @Test
        @DisplayName("A listing created after the change shows the new name (use case 2.3)")
        void newListing() throws IOException {
            saveOk(withName("Sam Lee"));

            CreateListingResult created = new CreateListingService(DATA).createListing(sellForm("Desk", photo("desk.png")));

            assertTrue(created.isSuccess(), created.getAllMessages().toString());
            assertEquals("Sam Lee", created.getListing().getSellerFullName());
        }

        @Test
        @DisplayName("My Listings shows the Seller's listings with the new name (use case 2.4)")
        void myListings() {
            storeListing(seller, "Lamp", ListingStatus.PENDING_REVIEW);
            storeListing(seller, "Desk", ListingStatus.REJECTED);
            saveOk(withName("Sam Lee"));

            List<Listing> mine = new MyListingsService(DATA).getMyListings();

            assertEquals(2, mine.size());
            mine.forEach(listing -> assertEquals("Sam Lee", listing.getSellerFullName()));
        }
    }

    // ------------------------------------------------------------------
    // Concurrency
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrency: saves are atomic")
    class Concurrency {

        /** Runs tasks at the same time and returns their results, in task order. */
        private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<T>> futures = new ArrayList<>();
                for (Callable<T> task : tasks) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return task.call();
                    }));
                }
                start.countDown();
                List<T> results = new ArrayList<>();
                for (Future<T> future : futures) {
                    results.add(future.get(120, TimeUnit.SECONDS));
                }
                return results;
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("Two password changes sent at the same time with the same current password: only one succeeds")
        void concurrentPasswordChanges() throws Exception {
            int threads = 4;
            List<Callable<SellerProfileResult>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String next = NEW_PASSWORD + "x" + i;
                SellerProfileUpdateRequest request = withPasswords(PASSWORD, next, next);
                tasks.add(() -> service.updateProfile(request));
            }

            List<SellerProfileResult> results = runTogether(tasks);

            List<Integer> winners = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                SellerProfileResult result = results.get(i);
                if (result.isSuccess()) {
                    winners.add(i);
                } else {
                    assertEquals(SellerProfileStatus.INVALID_FIELDS, result.getStatus());
                    assertEquals(Set.of(SellerProfileField.CURRENT_PASSWORD), result.getErrors().keySet());
                    String error = result.getErrors(SellerProfileField.CURRENT_PASSWORD).get(0);
                    assertTrue(error.equals(SellerProfileService.PASSWORD_CHANGED_MEANWHILE)
                            || error.equals(SellerProfileService.WRONG_CURRENT_PASSWORD), error);
                }
            }
            assertEquals(1, winners.size(), "Exactly one password change must succeed");
            assertTrue(PasswordHasher.verify(NEW_PASSWORD + "x" + winners.get(0), seller.getPasswordHash()));
            assertFalse(PasswordHasher.verify(PASSWORD, seller.getPasswordHash()));
        }

        @Test
        @DisplayName("Concurrent renames leave every listing with the Seller's final name")
        void concurrentRenames() throws Exception {
            List<Listing> mine = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                mine.add(storeListing(seller, "Item " + i, ListingStatus.LIVE));
            }
            List<Callable<SellerProfileResult>> tasks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                SellerProfileUpdateRequest request = withName("Name " + i);
                tasks.add(() -> service.updateProfile(request));
            }

            List<SellerProfileResult> results = runTogether(tasks);

            results.forEach(result -> assertTrue(result.isSuccess(), result.toString()));
            String finalName = seller.getFullName();
            assertTrue(finalName.startsWith("Name "), finalName);
            mine.forEach(listing -> assertEquals(finalName, listing.getSellerFullName(), listing.getTitle()));
        }

        @Test
        @DisplayName("A rename while listings are being created: every listing ends with the final name")
        void renameWhileCreatingListings() throws Exception {
            CreateListingService create = new CreateListingService(DATA);
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                Path file = photo("item" + i + ".png");
                String title = "Item " + i;
                tasks.add(() -> create.createListing(sellForm(title, file)).isSuccess());
            }
            for (int i = 0; i < 6; i++) {
                SellerProfileUpdateRequest request = withName("Renamed " + i);
                tasks.add(() -> service.updateProfile(request).isSuccess());
            }

            List<Boolean> results = runTogether(tasks);

            results.forEach(ok -> assertTrue(ok, "Every listing creation and rename must succeed"));
            List<Listing> mine = DATA.getListingsBySeller(seller.getUsername());
            assertEquals(6, mine.size());
            mine.forEach(listing -> assertEquals(seller.getFullName(), listing.getSellerFullName(), listing.getTitle()));
        }
    }
}
