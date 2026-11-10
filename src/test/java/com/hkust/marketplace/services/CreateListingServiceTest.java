package com.hkust.marketplace.services;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.ActOnListingResult;
import com.hkust.marketplace.models.AdminNotificationCategory;
import com.hkust.marketplace.models.AdminNotificationDetailResult;
import com.hkust.marketplace.models.AdminNotificationDetailStatus;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.CreateListingRequest;
import com.hkust.marketplace.models.CreateListingResult;
import com.hkust.marketplace.models.LendingDurationUnit;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingField;
import com.hkust.marketplace.models.ListingFilterCriteria;
import com.hkust.marketplace.models.ListingReviewResult;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.ListingViewStatus;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.Photo;
import com.hkust.marketplace.models.PhotoSelectionResult;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.ListingPhotoStorage;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests of use case 2.3 "Create Listing" (US-S03), grouped by the flows of its
 * specification. Acceptance criteria of the Sprint 2 backlog: the listing is stored with
 * status Pending Review, the admin is notified, and the price and the lending duration must
 * be positive.
 *
 * <p>The photos are real files written in a temporary folder. The tests also check that a
 * new listing works with the rest of the system: hidden from buyers until the admin
 * approves it (use cases 1.3 and 3.2), then open to their offers (use cases 1.5 and 2.5).</p>
 */
@DisplayName("CreateListingService: US-S03 Create Listing")
class CreateListingServiceTest {

    private static final DummyDataService DATA = DummyDataService.getInstance();
    /** The tests never log in with a password, so the stored hash is never checked. */
    private static final String UNUSED_HASH = "not-checked-by-these-tests";
    private static final String ADMIN = DummyDataService.SEEDED_ADMIN_USERNAME;

    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'};
    private static final byte[] GIF87_HEADER = "GIF87a\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] GIF89_HEADER = "GIF89a\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] BMP_HEADER = "BM6\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] TEXT = "Just some notes, not an image.".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path photoDir;

    /** Keeps the copies of the photos in the temporary folder, never in the project's data folder. */
    private ListingPhotoStorage storage;
    private CreateListingService service;

    private Seller seller;
    private Path photo1;
    private Path photo2;
    private int listingsBefore;
    private int adminNotificationsBefore;

    /**
     * Starts every test from the seeded data, with a fresh Seller logged in and two photos
     * on disk.
     */
    @BeforeEach
    void setUp() throws IOException {
        DATA.resetData();
        SessionManager.logout();
        seller = new Seller("s03_seller", "Sam Seller", UNUSED_HASH, "Final year student");
        seller.setPreferredMeetups(List.of(MeetupLocation.LIBRARY));
        assertTrue(DATA.registerUserIfAbsent(seller), "Test seller could not be stored");
        photo1 = write("lamp-front.png", PNG_HEADER);
        photo2 = write("lamp-side.jpg", JPEG_HEADER);
        storage = new ListingPhotoStorage(photoDir.resolve("uploads"));
        service = new CreateListingService(DATA, storage);
        listingsBefore = DATA.getAllListings().size();
        adminNotificationsBefore = DATA.getNotificationsFor(ADMIN).size();
        SessionManager.login(seller);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** A valid Sell form with two photos, which each test changes as needed. */
    private Form form() {
        return new Form(List.of(photo1, photo2));
    }

    /** Values of the listing form, valid by default, changed one at a time by the tests. */
    private static final class Form {
        private ListingType type = ListingType.SELL;
        private String title = "Desk Lamp";
        private String description = "LED desk lamp with a USB port. Works perfectly.";
        private Category category = Category.FURNITURE;
        private Condition condition = Condition.LIKE_NEW;
        private String price = "120";
        private String lendingAmount;
        private LendingDurationUnit lendingUnit = LendingDurationUnit.DAYS;
        private MeetupLocation meetup = MeetupLocation.LIBRARY;
        private List<Path> photos;

        Form(List<Path> photos) {
            this.photos = photos;
        }

        Form type(ListingType value) { type = value; return this; }
        Form title(String value) { title = value; return this; }
        Form description(String value) { description = value; return this; }
        Form category(Category value) { category = value; return this; }
        Form condition(Condition value) { condition = value; return this; }
        Form price(String value) { price = value; return this; }
        Form lending(String amount, LendingDurationUnit unit) { lendingAmount = amount; lendingUnit = unit; return this; }
        Form meetup(MeetupLocation value) { meetup = value; return this; }
        Form photos(List<Path> value) { photos = value; return this; }

        CreateListingRequest build() {
            return new CreateListingRequest(type, title, description, category, condition,
                    price, lendingAmount, lendingUnit, meetup, photos);
        }
    }

    private Path write(String name, byte[] content) throws IOException {
        Path file = photoDir.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.write(file, content);
    }

    /** Returns the copy the storage keeps of a photo file (storing the same file again reuses its copy). */
    private Path copyOf(Path photo) {
        try {
            return storage.store(photo);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Lists the copies in the storage folder, without temporary files. */
    private List<Path> copiesInStorage() throws IOException {
        if (!Files.isDirectory(storage.getRoot())) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> files = Files.list(storage.getRoot())) {
            return files.filter(f -> !f.getFileName().toString().startsWith(".")).sorted().toList();
        }
    }

    private List<Path> pngs(String prefix, int count) throws IOException {
        List<Path> files = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            files.add(write(prefix + i + ".png", PNG_HEADER));
        }
        return files;
    }

    private Listing createValid(CreateListingRequest request) {
        CreateListingResult result = service.createListing(request);
        assertTrue(result.isSuccess(), () -> "Expected success but got " + result.getErrors());
        return result.getListing();
    }

    private CreateListingResult createInvalid(CreateListingRequest request) {
        CreateListingResult result = service.createListing(request);
        assertFalse(result.isSuccess(), "Expected the submission to be refused");
        assertNull(result.getListing());
        return result;
    }

    private void assertNothingStored() {
        assertEquals(listingsBefore, DATA.getAllListings().size(), "No listing must be stored");
        assertEquals(adminNotificationsBefore, DATA.getNotificationsFor(ADMIN).size(), "The admin must not be notified");
    }

    private static void assertOnlyError(CreateListingResult result, ListingField field, String message) {
        assertEquals(Map.of(field, List.of(message)), result.getErrors());
    }

    private List<Notification> newAdminNotifications() {
        List<Notification> all = DATA.getNotificationsFor(ADMIN);
        return all.subList(0, all.size() - adminNotificationsBefore);
    }

    private Buyer storeBuyer(String username) {
        Buyer buyer = new Buyer(username, "Buyer " + username, UNUSED_HASH, "Hall 1");
        assertTrue(DATA.registerUserIfAbsent(buyer), "Test buyer could not be stored");
        return buyer;
    }

    private static String repeat(String text, int times) {
        return text.repeat(times);
    }

    // ------------------------------------------------------------------
    // Access control (use case 2.2 special requirement)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Seller session is checked before anything else (role-based access control)")
    class AccessControl {

        @Test
        @DisplayName("Without any session, submitting throws SecurityException and stores nothing")
        void noSession() {
            SessionManager.logout();
            assertThrows(SecurityException.class, () -> service.createListing(form().build()));
            assertNothingStored();
        }

        @Test
        @DisplayName("A Buyer session cannot post a listing")
        void buyerSession() {
            SessionManager.login(storeBuyer("s03_buyer"));
            assertThrows(SecurityException.class, () -> service.createListing(form().build()));
            assertNothingStored();
        }

        @Test
        @DisplayName("An Admin session cannot use the Seller's Create Listing")
        void adminSession() {
            SessionManager.login(DATA.findUser(ADMIN));
            assertThrows(SecurityException.class, () -> service.createListing(form().build()));
            assertNothingStored();
        }

        @ParameterizedTest(name = "Seller account {0}")
        @EnumSource(value = AccountStatus.class, names = {"DEACTIVATED", "DELETED"})
        @DisplayName("A Seller deactivated or deleted during the session is refused")
        void inactiveSeller(AccountStatus status) {
            seller.setAccountStatus(status);
            assertThrows(SecurityException.class, () -> service.createListing(form().build()));
            assertNothingStored();
        }

        @Test
        @DisplayName("Without a session, an invalid form is refused before its errors are reported")
        void sessionCheckedBeforeValidation() {
            SessionManager.logout();
            CreateListingRequest empty = new CreateListingRequest(null, null, null, null, null,
                    null, null, null, null, null);
            assertThrows(SecurityException.class, () -> service.createListing(empty));
        }

        @Test
        @DisplayName("The listing is always posted in the name of the logged-in Seller")
        void postedInSessionSellersName() {
            Seller other = new Seller("s03_other", "Olive Other", UNUSED_HASH);
            assertTrue(DATA.registerUserIfAbsent(other));
            Listing listing = createValid(form().build());
            assertEquals("s03_seller", listing.getSellerUsername());
            assertEquals("Sam Seller", listing.getSellerFullName());
            assertTrue(DATA.getListingsBySeller("s03_other").isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // Basic flow
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Basic flow: the listing is stored as Pending Review and the admin is notified")
    class BasicFlow {

        @Test
        @DisplayName("Step 9: a Sell listing is stored with every field and status Pending Review")
        void sellListingStored() {
            LocalDateTime before = LocalDateTime.now();
            Listing listing = createValid(form().build());

            assertSame(listing, DATA.findListing(listing.getId()));
            assertNotNull(listing.getId());
            assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
            assertEquals(ListingType.SELL, listing.getType());
            assertEquals("Desk Lamp", listing.getTitle());
            assertEquals("LED desk lamp with a USB port. Works perfectly.", listing.getDescription());
            assertEquals(Category.FURNITURE, listing.getCategory());
            assertEquals(Condition.LIKE_NEW, listing.getCondition());
            assertEquals(MeetupLocation.LIBRARY, listing.getMeetupLocation());
            assertEquals(120.0, listing.getPrice());
            assertNull(listing.getLendingDuration());
            assertEquals(0, listing.getViewCount());
            assertNull(listing.getAdminReason());
            assertFalse(listing.getPostedDate().isBefore(before));
            assertFalse(listing.getPostedDate().isAfter(LocalDateTime.now()));
            assertEquals(listingsBefore + 1, DATA.getAllListings().size());
        }

        @Test
        @DisplayName("Step 6.1: a Lend listing stores its lending duration and no price")
        void lendListingStored() {
            Listing listing = createValid(form().type(ListingType.LEND).price(null)
                    .lending("7", LendingDurationUnit.DAYS).build());
            assertEquals(ListingType.LEND, listing.getType());
            assertEquals(Duration.ofDays(7), listing.getLendingDuration());
            assertNull(listing.getPrice());
            assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        }

        @Test
        @DisplayName("A Give Away listing has neither price nor lending duration")
        void giveawayListingStored() {
            Listing listing = createValid(form().type(ListingType.GIVEAWAY).price(null).build());
            assertEquals(ListingType.GIVEAWAY, listing.getType());
            assertNull(listing.getPrice());
            assertNull(listing.getLendingDuration());
            assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        }

        @ParameterizedTest(name = "Meet-up {0}")
        @EnumSource(MeetupLocation.class)
        @DisplayName("Every predefined meet-up location is stored as chosen")
        void meetupStoredAsChosen(MeetupLocation location) {
            assertEquals(location, createValid(form().meetup(location).build()).getMeetupLocation());
        }

        @ParameterizedTest(name = "Category {0}")
        @EnumSource(Category.class)
        @DisplayName("Every predefined category is stored as chosen")
        void categoryStoredAsChosen(Category category) {
            assertEquals(category, createValid(form().category(category).build()).getCategory());
        }

        @ParameterizedTest(name = "Condition {0}")
        @EnumSource(Condition.class)
        @DisplayName("Every condition is stored as chosen")
        void conditionStoredAsChosen(Condition condition) {
            assertEquals(condition, createValid(form().condition(condition).build()).getCondition());
        }

        @Test
        @DisplayName("The item name and the description are stored without surrounding spaces")
        void textsAreTrimmed() {
            Listing listing = createValid(form().title("   Desk Lamp \t").description("\n  Works well.  ").build());
            assertEquals("Desk Lamp", listing.getTitle());
            assertEquals("Works well.", listing.getDescription());
        }

        @Test
        @DisplayName("Step 4: the photos are stored in order, numbered after the listing, the first being the cover")
        void photosStoredInOrder() {
            Listing listing = createValid(form().build());
            List<Photo> photos = listing.getPhotos();
            assertEquals(2, photos.size());
            assertEquals(listing.getId() + "-P1", photos.get(0).getId());
            assertEquals(listing.getId() + "-P2", photos.get(1).getId());
            // Review of the seller part, finding 3: the listing keeps copies of the chosen files.
            assertEquals(copyOf(photo1).toString(), photos.get(0).getImagePath());
            assertEquals(copyOf(photo2).toString(), photos.get(1).getImagePath());
            assertEquals(photos.get(0).getImagePath(), listing.getThumbnailPath());
            assertTrue(photos.get(0).getImageFile().exists(), "The Buyer Portal must be able to open the photo file");
        }

        @Test
        @DisplayName("A listing can have 6 photos")
        void sixPhotos() throws IOException {
            Listing listing = createValid(form().photos(pngs("six", 6)).build());
            assertEquals(6, listing.getPhotos().size());
        }

        @Test
        @DisplayName("Step 9: the admin is notified of the submission, with a link to the listing")
        void adminNotified() {
            Listing listing = createValid(form().build());
            List<Notification> added = newAdminNotifications();
            assertEquals(1, added.size());
            Notification notification = added.get(0);
            assertEquals(ADMIN, notification.getUsername());
            assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS.getDisplayName(), notification.getCategory());
            assertEquals(listing.getId(), notification.getRelatedItemId());
            assertEquals("LISTING", notification.getRelatedItemType());
            assertTrue(notification.isUnread());
            assertTrue(notification.getTitle().contains("Desk Lamp"));
            assertTrue(notification.getMessage().contains("@s03_seller"));
        }

        @Test
        @DisplayName("Opening the admin notification shows the submitted listing (use case 3.6)")
        void adminNotificationOpensListing() {
            Listing listing = createValid(form().build());
            Notification notification = newAdminNotifications().get(0);
            AdminNotificationDetailResult detail = SessionManager.withSession(DATA.findUser(ADMIN), () ->
                    DATA.getAdminNotificationService().getNotificationDetail(notification.getId(), ADMIN));
            assertEquals(AdminNotificationDetailStatus.FOUND, detail.getStatus());
            assertSame(listing, detail.getListing());
        }

        @Test
        @DisplayName("Step 10: the Seller is told that the listing was submitted for review")
        void confirmation() {
            CreateListingResult result = service.createListing(form().build());
            assertEquals(CreateListingService.confirmationMessage(result.getListing()), result.getMessage());
            assertTrue(result.getMessage().contains("\"Desk Lamp\""));
            assertTrue(result.getMessage().contains("submitted for review"));
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("Submitting the same form twice stores two listings with distinct identifiers")
        void distinctListings() {
            Listing first = createValid(form().build());
            Listing second = createValid(form().build());
            assertNotEquals(first.getId(), second.getId());
            assertNotSame(first, second);
            assertEquals(listingsBefore + 2, DATA.getAllListings().size());
        }

        @Test
        @DisplayName("The default constructor uses the application's shared data store")
        void defaultConstructor() throws IOException {
            Listing listing = new CreateListingService().createListing(form().build()).getListing();
            assertSame(listing, DummyDataService.getInstance().findListing(listing.getId()));
            // The default service keeps the copies in the application's folder: remove them again.
            for (Photo photo : listing.getPhotos()) {
                Path copy = Path.of(photo.getImagePath());
                assertTrue(new ListingPhotoStorage().isStored(copy), copy::toString);
                Files.deleteIfExists(copy);
            }
        }
    }

    // ------------------------------------------------------------------
    // Postconditions
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Postconditions: hidden from buyers until the admin approves the listing")
    class Postconditions {

        @Test
        @DisplayName("A Pending Review listing is not in the marketplace (use case 1.3)")
        void notVisibleToBuyers() {
            Listing listing = createValid(form().build());
            assertFalse(DATA.getLiveListings().contains(listing));
            assertFalse(DATA.getFilteredLiveListings(ListingFilterCriteria.all()).contains(listing));
            assertEquals(ListingViewStatus.NOT_LIVE, DATA.viewListing(listing.getId(), true).getStatus());
        }

        @Test
        @DisplayName("A buyer cannot make an offer on a listing that is not approved yet")
        void buyerCannotActOnPendingListing() {
            Listing listing = createValid(form().build());
            Buyer buyer = storeBuyer("s03_buyer");
            assertFalse(SessionManager.withSession(buyer, () ->
                    DATA.submitPriceOffer(listing.getId(), "s03_buyer", 100.0).isSuccess()));
            assertTrue(DATA.getOffersForListing(listing.getId()).isEmpty());
        }

        @Test
        @DisplayName("The listing is in the admin's review queue (use case 3.2)")
        void inAdminReviewQueue() {
            Listing listing = createValid(form().build());
            assertTrue(DATA.getPendingListings().contains(listing));
        }

        @Test
        @DisplayName("The listing is on the Seller's My Listings screen (use case 2.4)")
        void inMyListings() {
            Listing listing = createValid(form().build());
            assertTrue(DATA.getListingsBySeller("s03_seller").contains(listing));
        }

        @Test
        @DisplayName("Once approved, buyers see it and their offers reach the Seller (use cases 3.2, 1.5, 2.5)")
        void approvedListingReceivesOffers() {
            Listing listing = createValid(form().build());
            ListingReviewResult approval = DATA.approveListing(listing.getId(), ADMIN);
            assertTrue(approval.isSuccess(), approval.getMessage());
            assertTrue(DATA.getFilteredLiveListings(ListingFilterCriteria.all()).contains(listing));

            Buyer buyer = storeBuyer("s03_buyer");
            ActOnListingResult offer = SessionManager.withSession(buyer, () ->
                    DATA.submitPriceOffer(listing.getId(), "s03_buyer", 100.0));
            assertTrue(offer.isSuccess(), offer.getMessage());

            List<Offer> pending = new OfferManagementService(DATA).getPendingOffers();
            assertTrue(pending.stream().anyMatch(o -> o.getListingId().equals(listing.getId())));
        }

        @Test
        @DisplayName("A 10 second loan can be borrowed for 5 seconds, the team's demo minimum (use case 1.5)")
        void shortLoanCanBeBorrowed() {
            Listing listing = createValid(form().type(ListingType.LEND).lending("10", LendingDurationUnit.SECONDS).build());
            assertTrue(DATA.approveListing(listing.getId(), ADMIN).isSuccess());
            Buyer borrower = storeBuyer("s03_borrower");
            LocalDateTime start = LocalDateTime.now().plusSeconds(1);
            ActOnListingResult request = SessionManager.withSession(borrower, () ->
                    DATA.submitBorrowRequest(listing.getId(), "s03_borrower", start, start.plusSeconds(5)));
            assertTrue(request.isSuccess(), request.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // A1: missing required field
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("A1: a missing required field is highlighted and nothing is stored")
    class MissingRequiredField {

        @ParameterizedTest(name = "Item name [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("The item name is required")
        void titleRequired(String title) {
            assertOnlyError(createInvalid(form().title(title).build()), ListingField.TITLE,
                    CreateListingService.TITLE_REQUIRED);
            assertNothingStored();
        }

        @ParameterizedTest(name = "Description [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\n\n"})
        @DisplayName("The description is required")
        void descriptionRequired(String description) {
            assertOnlyError(createInvalid(form().description(description).build()), ListingField.DESCRIPTION,
                    CreateListingService.DESCRIPTION_REQUIRED);
        }

        @Test
        @DisplayName("The listing type is required")
        void typeRequired() {
            assertOnlyError(createInvalid(form().type(null).build()), ListingField.TYPE,
                    CreateListingService.TYPE_REQUIRED);
        }

        @Test
        @DisplayName("The category is required")
        void categoryRequired() {
            assertOnlyError(createInvalid(form().category(null).build()), ListingField.CATEGORY,
                    CreateListingService.CATEGORY_REQUIRED);
        }

        @Test
        @DisplayName("The condition is required")
        void conditionRequired() {
            assertOnlyError(createInvalid(form().condition(null).build()), ListingField.CONDITION,
                    CreateListingService.CONDITION_REQUIRED);
        }

        @Test
        @DisplayName("The meet-up location is required")
        void meetupRequired() {
            assertOnlyError(createInvalid(form().meetup(null).build()), ListingField.MEETUP_LOCATION,
                    CreateListingService.MEETUP_REQUIRED);
        }

        @ParameterizedTest(name = "Price [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "HK$", " $ "})
        @DisplayName("The price of a Sell listing is required")
        void priceRequired(String price) {
            assertOnlyError(createInvalid(form().price(price).build()), ListingField.PRICE,
                    CreateListingService.PRICE_REQUIRED);
        }

        @ParameterizedTest(name = "Lending duration [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("The lending duration of a Lend listing is required")
        void lendingRequired(String amount) {
            assertOnlyError(createInvalid(form().type(ListingType.LEND).lending(amount, LendingDurationUnit.DAYS).build()),
                    ListingField.LENDING_DURATION, CreateListingService.LENDING_REQUIRED);
        }

        @Test
        @DisplayName("The unit of the lending duration is required")
        void lendingUnitRequired() {
            assertOnlyError(createInvalid(form().type(ListingType.LEND).lending("7", null).build()),
                    ListingField.LENDING_DURATION, CreateListingService.LENDING_UNIT_REQUIRED);
        }

        @ParameterizedTest(name = "Photos {0}")
        @NullAndEmptySource
        @DisplayName("At least one photo is required")
        void photoRequired(List<Path> photos) {
            assertOnlyError(createInvalid(form().photos(photos).build()), ListingField.PHOTOS,
                    CreateListingService.PHOTOS_REQUIRED);
            assertNothingStored();
        }

        @Test
        @DisplayName("An empty form reports every required field at once, in form order")
        void everythingMissing() {
            CreateListingResult result = createInvalid(new CreateListingRequest(null, null, null, null, null,
                    null, null, null, null, null));
            assertEquals(List.of(ListingField.TYPE, ListingField.TITLE, ListingField.DESCRIPTION, ListingField.CATEGORY,
                    ListingField.CONDITION, ListingField.MEETUP_LOCATION, ListingField.PHOTOS),
                    new ArrayList<>(result.getErrors().keySet()));
            assertEquals(ListingField.TYPE, result.getFirstInvalidField());
            assertEquals("Please correct the 7 highlighted fields.", result.getMessage());
            assertEquals(7, result.getAllMessages().size());
            assertNothingStored();
        }

        @Test
        @DisplayName("An empty Sell form also reports the price, but not the lending duration")
        void emptySellForm() {
            CreateListingResult result = createInvalid(new CreateListingRequest(ListingType.SELL, "", "", null, null,
                    "", "", null, null, List.of()));
            assertEquals(List.of(ListingField.TITLE, ListingField.DESCRIPTION, ListingField.CATEGORY,
                    ListingField.CONDITION, ListingField.PRICE, ListingField.MEETUP_LOCATION, ListingField.PHOTOS),
                    new ArrayList<>(result.getErrors().keySet()));
            assertEquals(ListingField.TITLE, result.getFirstInvalidField());
        }

        @Test
        @DisplayName("An empty Lend form also reports the lending duration, but not the price")
        void emptyLendForm() {
            CreateListingResult result = createInvalid(new CreateListingRequest(ListingType.LEND, "", "", null, null,
                    "", "", null, null, List.of()));
            assertTrue(result.hasError(ListingField.LENDING_DURATION));
            assertFalse(result.hasError(ListingField.PRICE));
        }

        @Test
        @DisplayName("Only the fields of the chosen type are required")
        void onlyChosenTypeRequired() {
            createValid(form().type(ListingType.SELL).lending(null, null).build());
            createValid(form().type(ListingType.LEND).price(null).lending("3", LendingDurationUnit.WEEKS).build());
            createValid(form().type(ListingType.GIVEAWAY).price(null).lending(null, null).build());
        }
    }

    // ------------------------------------------------------------------
    // A2: invalid price
    // ------------------------------------------------------------------

    static Stream<Arguments> invalidPrices() {
        return Stream.of(
                Arguments.of("abc", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("12a", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("NaN", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("Infinity", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("1e3", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("0x10", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("1.2.3", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("--5", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("12,50", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("1,2345", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of(".", CreateListingService.PRICE_NOT_A_NUMBER),
                Arguments.of("0", CreateListingService.PRICE_NOT_POSITIVE),
                Arguments.of("0.00", CreateListingService.PRICE_NOT_POSITIVE),
                Arguments.of("-5", CreateListingService.PRICE_NOT_POSITIVE),
                Arguments.of("-0.01", CreateListingService.PRICE_NOT_POSITIVE),
                Arguments.of("HK$ -20", CreateListingService.PRICE_NOT_POSITIVE),
                Arguments.of("12.345", CreateListingService.PRICE_TOO_PRECISE),
                Arguments.of("0.001", CreateListingService.PRICE_TOO_PRECISE),
                Arguments.of("1000000.01", CreateListingService.PRICE_TOO_HIGH),
                Arguments.of("1,000,001", CreateListingService.PRICE_TOO_HIGH),
                Arguments.of("99999999999999999999", CreateListingService.PRICE_TOO_HIGH));
    }

    static Stream<Arguments> validPrices() {
        return Stream.of(
                Arguments.of("180", 180.0),
                Arguments.of("180.5", 180.5),
                Arguments.of("180.50", 180.5),
                Arguments.of("12.3400", 12.34),
                Arguments.of(".5", 0.5),
                Arguments.of("5.", 5.0),
                Arguments.of("0.01", 0.01),
                Arguments.of("+15", 15.0),
                Arguments.of(" 42 ", 42.0),
                Arguments.of("HK$ 1,200.50", 1200.5),
                Arguments.of("hk$99", 99.0),
                Arguments.of("$ 25", 25.0),
                Arguments.of("1,000,000", 1_000_000.0),
                Arguments.of("1000000.00", 1_000_000.0));
    }

    @Nested
    @DisplayName("A2: the price must be a positive amount (at most HK$ 1,000,000, to the cent)")
    class InvalidPrice {

        @ParameterizedTest(name = "\"{0}\" is refused")
        @MethodSource("com.hkust.marketplace.services.CreateListingServiceTest#invalidPrices")
        @DisplayName("Invalid prices are refused with the reason")
        void invalidPrice(String price, String expected) {
            assertEquals(expected, CreateListingService.checkPrice(price));
            assertOnlyError(createInvalid(form().price(price).build()), ListingField.PRICE, expected);
            assertNothingStored();
        }

        @ParameterizedTest(name = "\"{0}\" is HK$ {1}")
        @MethodSource("com.hkust.marketplace.services.CreateListingServiceTest#validPrices")
        @DisplayName("Valid prices are read, with HK$, $, spaces and thousands separators allowed")
        void validPrice(String price, double expected) {
            assertNull(CreateListingService.checkPrice(price));
            assertEquals(expected, CreateListingService.toPrice(price), 1e-9);
            assertEquals(expected, createValid(form().price(price).build()).getPrice(), 1e-9);
        }

        @Test
        @DisplayName("Converting an invalid price throws IllegalArgumentException with the reason")
        void toPriceThrows() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> CreateListingService.toPrice("0"));
            assertEquals(CreateListingService.PRICE_NOT_POSITIVE, e.getMessage());
            assertThrows(IllegalArgumentException.class, () -> CreateListingService.toPrice(null));
        }

        @Test
        @DisplayName("The zero price message suggests Give Away instead")
        void zeroPriceSuggestsGiveAway() {
            assertTrue(CreateListingService.checkPrice("0").contains("Give Away"));
        }
    }

    // ------------------------------------------------------------------
    // A2: invalid lending duration
    // ------------------------------------------------------------------

    static Stream<Arguments> invalidDurations() {
        return Stream.of(
                Arguments.of("abc", LendingDurationUnit.DAYS, CreateListingService.LENDING_NOT_A_WHOLE_NUMBER),
                Arguments.of("1.5", LendingDurationUnit.DAYS, CreateListingService.LENDING_NOT_A_WHOLE_NUMBER),
                Arguments.of("7 days", LendingDurationUnit.DAYS, CreateListingService.LENDING_NOT_A_WHOLE_NUMBER),
                Arguments.of("1,000", LendingDurationUnit.SECONDS, CreateListingService.LENDING_NOT_A_WHOLE_NUMBER),
                Arguments.of("0", LendingDurationUnit.DAYS, CreateListingService.LENDING_NOT_POSITIVE),
                Arguments.of("-3", LendingDurationUnit.DAYS, CreateListingService.LENDING_NOT_POSITIVE),
                Arguments.of("1", LendingDurationUnit.SECONDS, CreateListingService.LENDING_TOO_SHORT),
                Arguments.of("4", LendingDurationUnit.SECONDS, CreateListingService.LENDING_TOO_SHORT),
                Arguments.of("366", LendingDurationUnit.DAYS, CreateListingService.LENDING_TOO_LONG),
                Arguments.of("53", LendingDurationUnit.WEEKS, CreateListingService.LENDING_TOO_LONG),
                Arguments.of("8761", LendingDurationUnit.HOURS, CreateListingService.LENDING_TOO_LONG),
                Arguments.of("525601", LendingDurationUnit.MINUTES, CreateListingService.LENDING_TOO_LONG),
                Arguments.of("31536001", LendingDurationUnit.SECONDS, CreateListingService.LENDING_TOO_LONG),
                Arguments.of("99999999999999999999999", LendingDurationUnit.WEEKS, CreateListingService.LENDING_TOO_LONG));
    }

    static Stream<Arguments> validDurations() {
        return Stream.of(
                Arguments.of("5", LendingDurationUnit.SECONDS, Duration.ofSeconds(5)),
                Arguments.of("10", LendingDurationUnit.SECONDS, Duration.ofSeconds(10)),
                Arguments.of("1", LendingDurationUnit.MINUTES, Duration.ofMinutes(1)),
                Arguments.of("2", LendingDurationUnit.HOURS, Duration.ofHours(2)),
                Arguments.of("7", LendingDurationUnit.DAYS, Duration.ofDays(7)),
                Arguments.of(" 14 ", LendingDurationUnit.DAYS, Duration.ofDays(14)),
                Arguments.of("+3", LendingDurationUnit.DAYS, Duration.ofDays(3)),
                Arguments.of("365", LendingDurationUnit.DAYS, Duration.ofDays(365)),
                Arguments.of("52", LendingDurationUnit.WEEKS, Duration.ofDays(364)),
                Arguments.of("8760", LendingDurationUnit.HOURS, Duration.ofDays(365)),
                Arguments.of("525600", LendingDurationUnit.MINUTES, Duration.ofDays(365)),
                Arguments.of("31536000", LendingDurationUnit.SECONDS, Duration.ofDays(365)));
    }

    @Nested
    @DisplayName("A2: the lending duration must be a positive period, from 5 seconds to 365 days")
    class InvalidLendingDuration {

        @ParameterizedTest(name = "\"{0}\" {1} is refused")
        @MethodSource("com.hkust.marketplace.services.CreateListingServiceTest#invalidDurations")
        @DisplayName("Invalid lending durations are refused with the reason")
        void invalidDuration(String amount, LendingDurationUnit unit, String expected) {
            assertEquals(expected, CreateListingService.checkLendingDuration(amount, unit));
            assertOnlyError(createInvalid(form().type(ListingType.LEND).lending(amount, unit).build()),
                    ListingField.LENDING_DURATION, expected);
            assertNothingStored();
        }

        @ParameterizedTest(name = "\"{0}\" {1} is {2}")
        @MethodSource("com.hkust.marketplace.services.CreateListingServiceTest#validDurations")
        @DisplayName("Valid lending durations are converted, including the 5 second demo minimum")
        void validDuration(String amount, LendingDurationUnit unit, Duration expected) {
            assertNull(CreateListingService.checkLendingDuration(amount, unit));
            assertEquals(expected, CreateListingService.toLendingDuration(amount, unit));
            assertEquals(expected, createValid(form().type(ListingType.LEND).lending(amount, unit).build())
                    .getLendingDuration());
        }

        @Test
        @DisplayName("Converting an invalid duration throws IllegalArgumentException with the reason")
        void toLendingDurationThrows() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> CreateListingService.toLendingDuration("4", LendingDurationUnit.SECONDS));
            assertEquals(CreateListingService.LENDING_TOO_SHORT, e.getMessage());
        }

        @Test
        @DisplayName("The limits match the buyer's 5 second borrow minimum and one year")
        void limits() {
            assertEquals(Duration.ofSeconds(ActOnListingService.MIN_BORROW_DURATION_SECONDS),
                    CreateListingService.MIN_LENDING_DURATION);
            assertEquals(Duration.ofDays(365), CreateListingService.MAX_LENDING_DURATION);
        }
    }

    // ------------------------------------------------------------------
    // Text lengths
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("The item name and the description have a maximum length")
    class TextLengths {

        @Test
        @DisplayName("An item name of 80 characters is accepted, 81 is refused")
        void titleLength() {
            createValid(form().title(repeat("a", CreateListingService.MAX_TITLE_LENGTH)).build());
            assertOnlyError(createInvalid(form().title(repeat("a", CreateListingService.MAX_TITLE_LENGTH + 1)).build()),
                    ListingField.TITLE, CreateListingService.TITLE_TOO_LONG);
        }

        @Test
        @DisplayName("Lengths count characters, so an emoji counts as one")
        void lengthCountsCodePoints() {
            createValid(form().title(repeat("📚", CreateListingService.MAX_TITLE_LENGTH)).build());
            assertTrue(createInvalid(form().title(repeat("📚", CreateListingService.MAX_TITLE_LENGTH + 1)).build())
                    .hasError(ListingField.TITLE));
        }

        @Test
        @DisplayName("Spaces around the item name are not counted")
        void lengthAfterTrim() {
            createValid(form().title("   " + repeat("a", CreateListingService.MAX_TITLE_LENGTH) + "   ").build());
        }

        @Test
        @DisplayName("A description of 1000 characters is accepted, 1001 is refused")
        void descriptionLength() {
            createValid(form().description(repeat("d", CreateListingService.MAX_DESCRIPTION_LENGTH)).build());
            assertOnlyError(createInvalid(form()
                            .description(repeat("d", CreateListingService.MAX_DESCRIPTION_LENGTH + 1)).build()),
                    ListingField.DESCRIPTION, CreateListingService.DESCRIPTION_TOO_LONG);
        }
    }

    // ------------------------------------------------------------------
    // A3: invalid photos
    // ------------------------------------------------------------------

    static Stream<Arguments> imageFiles() {
        return Stream.of(
                Arguments.of("photo.png", PNG_HEADER),
                Arguments.of("photo.jpg", JPEG_HEADER),
                Arguments.of("photo.jpeg", JPEG_HEADER),
                Arguments.of("photo.gif", GIF89_HEADER),
                Arguments.of("old.gif", GIF87_HEADER),
                Arguments.of("photo.bmp", BMP_HEADER),
                Arguments.of("PHOTO.PNG", PNG_HEADER),
                Arguments.of("Photo.JpEg", JPEG_HEADER),
                Arguments.of("png-saved-as.jpg", PNG_HEADER),
                Arguments.of("my holiday photo (1).png", PNG_HEADER));
    }

    static Stream<Arguments> refusedFiles() {
        return Stream.of(
                Arguments.of("notes.txt", TEXT, CreateListingService.PHOTO_UNSUPPORTED_TYPE),
                Arguments.of("scan.pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII), CreateListingService.PHOTO_UNSUPPORTED_TYPE),
                Arguments.of("photo.webp", "RIFF....WEBP".getBytes(StandardCharsets.US_ASCII), CreateListingService.PHOTO_UNSUPPORTED_TYPE),
                Arguments.of("png-without-extension", PNG_HEADER, CreateListingService.PHOTO_UNSUPPORTED_TYPE),
                Arguments.of("photo.png.exe", PNG_HEADER, CreateListingService.PHOTO_UNSUPPORTED_TYPE),
                Arguments.of("renamed-notes.png", TEXT, CreateListingService.PHOTO_NOT_AN_IMAGE),
                Arguments.of("empty.png", new byte[0], CreateListingService.PHOTO_NOT_AN_IMAGE),
                Arguments.of("truncated.png", Arrays.copyOf(PNG_HEADER, 4), CreateListingService.PHOTO_NOT_AN_IMAGE),
                Arguments.of("text.jpg", "GIF88a".getBytes(StandardCharsets.US_ASCII), CreateListingService.PHOTO_NOT_AN_IMAGE));
    }

    @Nested
    @DisplayName("A3: only 1 to 6 image files are accepted as photos")
    class InvalidPhotos {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.hkust.marketplace.services.CreateListingServiceTest#imageFiles")
        @DisplayName("PNG, JPEG, GIF and BMP files are accepted, whatever the case of their extension")
        void imageAccepted(String name, byte[] content) throws IOException {
            Path file = write(name, content);
            assertNull(CreateListingService.photoProblem(file));
            PhotoSelectionResult result = CreateListingService.addPhotos(List.of(), List.of(file));
            assertEquals(List.of(file.toAbsolutePath().normalize()), result.getPhotos());
            assertFalse(result.hasRejections());
        }

        @Test
        @DisplayName("Real PNG and JPEG images written by an image library are accepted")
        void realImagesAccepted() throws IOException {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            Path png = photoDir.resolve("real.png");
            Path jpg = photoDir.resolve("real.jpg");
            assertTrue(ImageIO.write(image, "png", png.toFile()));
            assertTrue(ImageIO.write(image, "jpg", jpg.toFile()));
            assertNull(CreateListingService.photoProblem(png));
            assertNull(CreateListingService.photoProblem(jpg));
            createValid(form().photos(List.of(png, jpg)).build());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.hkust.marketplace.services.CreateListingServiceTest#refusedFiles")
        @DisplayName("Files that are not images are rejected, and the Seller is told which and why")
        void fileRejected(String name, byte[] content, String reason) throws IOException {
            Path file = write(name, content);
            assertEquals(reason, CreateListingService.photoProblem(file));
            PhotoSelectionResult result = CreateListingService.addPhotos(List.of(photo1), List.of(file));
            assertEquals(List.of(photo1.toAbsolutePath().normalize()), result.getPhotos());
            assertEquals(0, result.getAddedCount());
            assertEquals(List.of(name + " " + reason), result.getRejections());
        }

        @Test
        @DisplayName("A file that no longer exists is rejected")
        void missingFile() {
            Path missing = photoDir.resolve("deleted.png");
            assertEquals(CreateListingService.PHOTO_NOT_FOUND, CreateListingService.photoProblem(missing));
        }

        @Test
        @DisplayName("A folder is rejected, even if its name looks like an image")
        void folderRejected() throws IOException {
            Path folder = Files.createDirectory(photoDir.resolve("album.png"));
            assertEquals(CreateListingService.PHOTO_NOT_A_FILE, CreateListingService.photoProblem(folder));
        }

        @Test
        @DisplayName("The root of the file system is rejected and named by its path")
        void rootRejected() {
            Path root = photoDir.toAbsolutePath().getRoot();
            PhotoSelectionResult result = CreateListingService.addPhotos(List.of(), List.of(root));
            assertEquals(List.of(root + " " + CreateListingService.PHOTO_NOT_A_FILE), result.getRejections());
        }

        @Test
        @DisplayName("A photo of exactly 10 MB is accepted, one byte more is rejected")
        void sizeLimit() throws IOException {
            Path atLimit = write("at-limit.png", PNG_HEADER);
            Path overLimit = write("over-limit.png", PNG_HEADER);
            try (RandomAccessFile file = new RandomAccessFile(atLimit.toFile(), "rw")) {
                file.setLength(CreateListingService.MAX_PHOTO_BYTES);
            }
            try (RandomAccessFile file = new RandomAccessFile(overLimit.toFile(), "rw")) {
                file.setLength(CreateListingService.MAX_PHOTO_BYTES + 1);
            }
            assertNull(CreateListingService.photoProblem(atLimit));
            assertEquals(CreateListingService.PHOTO_TOO_LARGE, CreateListingService.photoProblem(overLimit));
        }

        @Test
        @DisplayName("A file that cannot be read is rejected")
        void unreadableFile() throws IOException {
            assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                    "File permissions can only be removed on a POSIX file system");
            Path locked = write("locked.png", PNG_HEADER);
            Set<PosixFilePermission> original = Files.getPosixFilePermissions(locked);
            Files.setPosixFilePermissions(locked, Set.of());
            try {
                assumeFalse(Files.isReadable(locked), "The file stays readable for this user (for example root)");
                assertEquals(CreateListingService.PHOTO_UNREADABLE, CreateListingService.photoProblem(locked));
            } finally {
                Files.setPosixFilePermissions(locked, original);
            }
        }

        @Test
        @DisplayName("Choosing a photo already in the form is rejected, even through another path")
        void alreadyAdded() {
            Path samePhoto = photoDir.resolve("sub").resolve("..").resolve("lamp-front.png");
            PhotoSelectionResult result = CreateListingService.addPhotos(List.of(photo1), List.of(samePhoto, photo2));
            assertEquals(List.of(photo1.toAbsolutePath().normalize(), photo2.toAbsolutePath().normalize()),
                    result.getPhotos());
            assertEquals(1, result.getAddedCount());
            assertEquals(List.of("lamp-front.png " + CreateListingService.PHOTO_ALREADY_ADDED), result.getRejections());
        }

        @Test
        @DisplayName("Choosing the same file twice at once adds it once")
        void duplicateInSelection() {
            PhotoSelectionResult result = CreateListingService.addPhotos(List.of(), List.of(photo1, photo1));
            assertEquals(1, result.getPhotos().size());
            assertEquals(List.of("lamp-front.png " + CreateListingService.PHOTO_ALREADY_ADDED), result.getRejections());
        }

        @Test
        @DisplayName("Photos beyond the 6th are rejected together, naming each file")
        void extraPhotosRejected() throws IOException {
            List<Path> current = pngs("current", 5);
            List<Path> selected = List.of(write("a.png", PNG_HEADER), write("b.png", PNG_HEADER), write("c.png", PNG_HEADER));
            PhotoSelectionResult result = CreateListingService.addPhotos(current, selected);
            assertEquals(6, result.getPhotos().size());
            assertEquals(1, result.getAddedCount());
            assertEquals(selected.get(0).toAbsolutePath().normalize(), result.getPhotos().get(5));
            assertEquals(List.of("b.png and c.png were not added: a listing can have at most 6 photos."),
                    result.getRejections());
        }

        @Test
        @DisplayName("Choosing 7 photos at once keeps the first 6")
        void sevenAtOnce() throws IOException {
            List<Path> selected = pngs("pick", 7);
            PhotoSelectionResult result = CreateListingService.addPhotos(List.of(), selected);
            assertEquals(selected.subList(0, 6).stream().map(p -> p.toAbsolutePath().normalize()).toList(),
                    result.getPhotos());
            assertEquals(6, result.getAddedCount());
            assertEquals(List.of("pick7.png was not added: a listing can have at most 6 photos."), result.getRejections());
        }

        @Test
        @DisplayName("More than two extra photos are listed with commas")
        void manyExtraPhotos() throws IOException {
            PhotoSelectionResult result = CreateListingService.addPhotos(pngs("full", 6), pngs("extra", 3));
            assertEquals(List.of("extra1.png, extra2.png and extra3.png were not added: a listing can have at most 6 photos."),
                    result.getRejections());
        }

        @Test
        @DisplayName("Invalid and extra files are reported separately")
        void invalidAndExtra() throws IOException {
            Path text = write("notes.txt", TEXT);
            Path extra = write("extra.png", PNG_HEADER);
            PhotoSelectionResult result = CreateListingService.addPhotos(pngs("full", 6), List.of(text, extra));
            assertEquals(List.of("notes.txt " + CreateListingService.PHOTO_UNSUPPORTED_TYPE,
                    "extra.png was not added: a listing can have at most 6 photos."), result.getRejections());
        }

        @Test
        @DisplayName("Missing lists and null elements are ignored")
        void nullsIgnored() {
            PhotoSelectionResult none = CreateListingService.addPhotos(null, null);
            assertTrue(none.getPhotos().isEmpty());
            assertEquals(0, none.getAddedCount());
            assertFalse(none.hasRejections());
            PhotoSelectionResult some = CreateListingService.addPhotos(Arrays.asList(null, photo1, photo1),
                    Arrays.asList(photo2, null));
            assertEquals(List.of(photo1.toAbsolutePath().normalize(), photo2.toAbsolutePath().normalize()), some.getPhotos());
        }

        @Test
        @DisplayName("Checking a missing photo path throws NullPointerException")
        void nullPhoto() {
            assertThrows(NullPointerException.class, () -> CreateListingService.photoProblem(null));
        }

        @Test
        @DisplayName("At submission, more than 6 photos are refused")
        void tooManyAtSubmission() throws IOException {
            CreateListingResult result = createInvalid(form().photos(pngs("seven", 7)).build());
            assertOnlyError(result, ListingField.PHOTOS, CreateListingService.PHOTOS_TOO_MANY);
            assertNothingStored();
        }

        @Test
        @DisplayName("At submission, a photo deleted since it was chosen is refused")
        void deletedBeforeSubmission() throws IOException {
            Files.delete(photo2);
            assertOnlyError(createInvalid(form().build()), ListingField.PHOTOS,
                    "lamp-side.jpg " + CreateListingService.PHOTO_NOT_FOUND);
        }

        @Test
        @DisplayName("At submission, the same photo twice is refused")
        void repeatedAtSubmission() {
            assertOnlyError(createInvalid(form().photos(List.of(photo1, photo2, photo1)).build()), ListingField.PHOTOS,
                    "lamp-front.png " + CreateListingService.PHOTO_REPEATED);
        }

        @Test
        @DisplayName("At submission, every invalid photo is reported")
        void everyInvalidPhotoReported() throws IOException {
            Path text = write("notes.txt", TEXT);
            Path fake = write("fake.png", TEXT);
            CreateListingResult result = createInvalid(form().photos(List.of(photo1, text, fake)).build());
            assertEquals(List.of("notes.txt " + CreateListingService.PHOTO_UNSUPPORTED_TYPE,
                    "fake.png " + CreateListingService.PHOTO_NOT_AN_IMAGE), result.getErrors(ListingField.PHOTOS));
        }

        @Test
        @DisplayName("Checking a missing photo list reports that a photo is required")
        void checkPhotosNull() {
            assertEquals(List.of(CreateListingService.PHOTOS_REQUIRED), CreateListingService.checkPhotos(null));
        }
    }

    // ------------------------------------------------------------------
    // A4: change listing type
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("A4: values that do not apply to the chosen type are discarded")
    class ChangeListingType {

        @Test
        @DisplayName("A Sell listing ignores a lending duration left in the form, even an invalid one")
        void sellIgnoresLending() {
            Listing listing = createValid(form().lending("abc", LendingDurationUnit.DAYS).build());
            assertNull(listing.getLendingDuration());
            assertEquals(120.0, listing.getPrice());
        }

        @Test
        @DisplayName("A Lend listing ignores a price left in the form, even an invalid one")
        void lendIgnoresPrice() {
            Listing listing = createValid(form().type(ListingType.LEND).price("-5")
                    .lending("7", LendingDurationUnit.DAYS).build());
            assertNull(listing.getPrice());
            assertEquals(Duration.ofDays(7), listing.getLendingDuration());
        }

        @Test
        @DisplayName("A Give Away listing ignores both")
        void giveawayIgnoresBoth() {
            Listing listing = createValid(form().type(ListingType.GIVEAWAY).price("abc")
                    .lending("0", LendingDurationUnit.SECONDS).build());
            assertNull(listing.getPrice());
            assertNull(listing.getLendingDuration());
        }
    }

    // ------------------------------------------------------------------
    // Validation shared with use cases 2.4 and 3.7
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("The validation can be reused without a session (edit listing, admin add listing)")
    class SharedValidation {

        @Test
        @DisplayName("validate needs no session and stores nothing")
        void validateWithoutSession() {
            SessionManager.logout();
            assertTrue(CreateListingService.validate(form().build()).isEmpty());
            Map<ListingField, List<String>> errors = CreateListingService.validate(form().title("").price("0").build());
            assertEquals(Set.of(ListingField.TITLE, ListingField.PRICE), errors.keySet());
            assertNothingStored();
        }

        @Test
        @DisplayName("validate only checks the value of the chosen type")
        void validateChosenTypeOnly() {
            Map<ListingField, List<String>> errors = CreateListingService.validate(
                    form().price("").lending("x", LendingDurationUnit.DAYS).build());
            assertEquals(Map.of(ListingField.PRICE, List.of(CreateListingService.PRICE_REQUIRED)), errors);
        }

        @Test
        @DisplayName("Missing arguments throw NullPointerException")
        void nullArguments() {
            assertThrows(NullPointerException.class, () -> service.createListing(null));
            assertThrows(NullPointerException.class, () -> CreateListingService.validate(null));
            assertThrows(NullPointerException.class, () -> new CreateListingService(null));
            assertThrows(NullPointerException.class, () -> new CreateListingService(DATA, null));
            assertThrows(NullPointerException.class, () -> new CreateListingService(null, storage));
        }
    }

    // ------------------------------------------------------------------
    // Concurrency
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrent submissions")
    class Concurrency {

        private <T> List<T> runAtOnce(List<Callable<T>> tasks) throws Exception {
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
                    results.add(future.get(20, TimeUnit.SECONDS));
                }
                return results;
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @Timeout(30)
        @DisplayName("20 listings submitted at once are all stored, with distinct identifiers and one notification each")
        void concurrentSubmissions() throws Exception {
            int count = 20;
            List<Callable<Listing>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String title = "Item " + i;
                tasks.add(() -> createValid(form().title(title).build()));
            }
            List<Listing> created = runAtOnce(tasks);

            Set<String> ids = created.stream().map(Listing::getId).collect(Collectors.toSet());
            assertEquals(count, ids.size(), "Every listing must get its own identifier");
            assertEquals(listingsBefore + count, DATA.getAllListings().size());
            assertEquals(count, newAdminNotifications().size());
            Set<String> notified = newAdminNotifications().stream()
                    .map(Notification::getRelatedItemId).collect(Collectors.toSet());
            assertEquals(ids, notified, "The admin must be notified once of each listing");
            for (Listing listing : created) {
                assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
                assertTrue(listing.getPhotos().stream().allMatch(p -> p.getId().startsWith(listing.getId() + "-P")),
                        "Each listing's photos must be numbered after its own identifier");
            }
        }

        @Test
        @Timeout(30)
        @DisplayName("Submitting while buyers browse and the admin approves causes no error")
        void submissionsWhileBrowsingAndReviewing() throws Exception {
            int creators = 10;
            List<Callable<Object>> tasks = new ArrayList<>();
            for (int i = 0; i < creators; i++) {
                String title = "Busy item " + i;
                tasks.add(() -> createValid(form().title(title).build()));
            }
            for (int i = 0; i < 5; i++) {
                tasks.add(() -> {
                    for (int round = 0; round < 20; round++) {
                        DATA.getFilteredLiveListings(ListingFilterCriteria.all());
                    }
                    return null;
                });
            }
            tasks.add(() -> {
                for (int round = 0; round < 20; round++) {
                    for (Listing pending : DATA.getPendingListings()) {
                        DATA.approveListing(pending.getId(), ADMIN);
                    }
                }
                return null;
            });
            runAtOnce(tasks);

            List<Listing> mine = DATA.getListingsBySeller("s03_seller");
            assertEquals(creators, mine.size());
            Set<ListingStatus> allowed = new HashSet<>(List.of(ListingStatus.PENDING_REVIEW, ListingStatus.LIVE));
            assertTrue(mine.stream().allMatch(l -> allowed.contains(l.getStatus())));
            User stored = DATA.findUser("s03_seller");
            assertSame(seller, stored);
        }
    }

    // ------------------------------------------------------------------
    // Review of the seller part, finding 3: photos are copied into the application's folder
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Photos are kept as copies in the application's folder [RS3]")
    class PhotoCopies {

        @Test
        @DisplayName("The listing still shows its photos after the Seller deletes the original files")
        void survivesDeletedOriginals() throws IOException {
            Listing listing = createValid(form().build());

            Files.delete(photo1);
            Files.delete(photo2);

            for (Photo photo : listing.getPhotos()) {
                Path copy = Path.of(photo.getImagePath());
                assertTrue(storage.isStored(copy), copy::toString);
                assertTrue(Files.isRegularFile(copy), "The copy must still exist: " + copy);
                assertNull(CreateListingService.photoProblem(copy), "The copy must still be a valid image");
            }
        }

        @Test
        @DisplayName("Each copy has the content of its original and keeps its file name")
        void copiesKeepContentAndName() throws IOException {
            Listing listing = createValid(form().build());

            Path front = Path.of(listing.getPhotos().get(0).getImagePath());
            Path side = Path.of(listing.getPhotos().get(1).getImagePath());
            assertEquals(-1, Files.mismatch(photo1, front));
            assertEquals(-1, Files.mismatch(photo2, side));
            assertTrue(front.getFileName().toString().endsWith("-lamp-front.png"), front::toString);
            assertTrue(side.getFileName().toString().endsWith("-lamp-side.jpg"), side::toString);
        }

        @Test
        @DisplayName("Posting the same files twice keeps one copy of each")
        void noDuplicateCopies() throws IOException {
            Listing first = createValid(form().build());
            Listing second = createValid(form().title("Second lamp").build());

            assertEquals(first.getPhotoPaths(), second.getPhotoPaths());
            assertEquals(2, copiesInStorage().size());
        }

        @Test
        @DisplayName("Two different files with the same content keep separate copies, in order")
        void sameContentDifferentFiles() throws IOException {
            Path copyA = write("a.png", PNG_HEADER);
            Path copyB = write("b.png", PNG_HEADER);

            Listing listing = createValid(form().photos(List.of(copyA, copyB)).build());

            assertEquals(2, listing.getPhotos().size());
            assertNotEquals(listing.getPhotoPaths().get(0), listing.getPhotoPaths().get(1));
        }

        @Test
        @DisplayName("A photo that cannot be copied is reported and nothing is stored")
        void copyFailure() throws IOException {
            Path notAFolder = write("not-a-folder", TEXT);
            CreateListingService failing = new CreateListingService(DATA, new ListingPhotoStorage(notAFolder));

            CreateListingResult result = failing.createListing(form().build());

            assertFalse(result.isSuccess());
            assertEquals(List.of(CreateListingService.PHOTOS_NOT_SAVED), result.getErrors(ListingField.PHOTOS));
            assertEquals(listingsBefore, DATA.getAllListings().size(), "Nothing is stored");
            assertEquals(adminNotificationsBefore, DATA.getNotificationsFor(ADMIN).size(), "The admin is not notified");
        }

        @Test
        @DisplayName("An invalid listing copies nothing, and no temporary file is left after a listing is posted")
        void nothingLeftBehind() throws IOException {
            createInvalid(form().title(" ").build());
            assertTrue(copiesInStorage().isEmpty());

            createValid(form().build());

            try (java.util.stream.Stream<Path> files = Files.list(storage.getRoot())) {
                assertTrue(files.noneMatch(f -> f.getFileName().toString().endsWith(".tmp")));
            }
        }
    }
}
