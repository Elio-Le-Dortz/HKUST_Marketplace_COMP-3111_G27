package com.hkust.marketplace.services;

import com.hkust.marketplace.models.CreateListingRequest;
import com.hkust.marketplace.models.CreateListingResult;
import com.hkust.marketplace.models.LendingDurationUnit;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingField;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Photo;
import com.hkust.marketplace.models.PhotoSelectionResult;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Business logic of use case 2.3 "Create Listing" (US-S03): a Seller posts an item to sell,
 * lend or give away. The listing is stored with status Pending Review and the admin is
 * notified, so that buyers only see it once the admin has approved it (use case 3.2).
 *
 * <p><b>Validation.</b> Every field of the form is checked at once, and every invalid field
 * is reported with its reason (alternative flows A1, A2 and A3). Only the fields of the
 * chosen type are checked: the price of a Lend or Give Away listing and the lending duration
 * of a Sell or Give Away listing are discarded (alternative flow A4). The rules are the
 * constants of this class and are listed in the README.</p>
 *
 * <p><b>Access control.</b> {@link #createListing} first calls
 * {@link SessionManager#requireSeller()}, so it can only be used during an active Seller
 * session (use case 2.2 special requirement), and the listing is always posted in the name of
 * the logged-in Seller. The validation methods are static, need no session and change
 * nothing, so that editing a listing (use case 2.4) and the admin's "add a listing on behalf
 * of a seller" (use case 3.7) can apply the same rules.</p>
 *
 * <p><b>Concurrency.</b> The listing is stored and given its identifier and photo
 * identifiers while holding the lock of the data store ({@link DummyDataService}), so that no
 * other action sees it half built, and concurrent submissions always get distinct
 * identifiers. The photo files are read before taking the lock, and the admin is notified
 * after releasing it, through {@link AdminNotificationService}, whose own lock is always
 * taken before the data store's: taking them in the other order could deadlock.</p>
 *
 * <p><b>Photos.</b> A photo is stored as the absolute path of the image file chosen by the
 * Seller, which the Buyer and Admin portals read to show it. The file is not copied: like all
 * the data of this in-memory demo, the listing only lasts until the application closes.</p>
 *
 * <p>This class does not use JavaFX, so every flow is unit tested without a display.</p>
 */
public class CreateListingService {

    /** Longest item name, in characters. */
    public static final int MAX_TITLE_LENGTH = 80;

    /** Longest description, in characters. */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    /** Fewest photos a listing can have. */
    public static final int MIN_PHOTOS = 1;

    /** Most photos a listing can have. */
    public static final int MAX_PHOTOS = 6;

    /** Largest photo file, in bytes (10 MB). */
    public static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;

    /** Extensions of the accepted image files, in lower case; JavaFX can show all of them. */
    public static final List<String> PHOTO_EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp");

    /** Highest price, in HK$. */
    public static final BigDecimal MAX_PRICE = new BigDecimal("1000000");

    /**
     * Shortest lending duration: 5 seconds, the same minimum as a buyer's borrow request, so
     * that a loan and its auto-return can be demonstrated in a few seconds (Meeting 1, item 2.2).
     */
    public static final Duration MIN_LENDING_DURATION =
            Duration.ofSeconds(ActOnListingService.MIN_BORROW_DURATION_SECONDS);

    /** Longest lending duration: 365 days. */
    public static final Duration MAX_LENDING_DURATION = Duration.ofDays(365);

    /** Separator between the listing identifier and the number of a photo, as in {@code L108-P1}. */
    public static final String PHOTO_ID_SEPARATOR = "-P";

    /** Error of a missing listing type. */
    public static final String TYPE_REQUIRED = "Choose the listing type: Sell, Lend or Give Away.";
    /** Error of a missing item name. */
    public static final String TITLE_REQUIRED = "Item name is required.";
    /** Error of an item name longer than {@link #MAX_TITLE_LENGTH}. */
    public static final String TITLE_TOO_LONG = "Item name must be at most " + MAX_TITLE_LENGTH + " characters.";
    /** Error of a missing description. */
    public static final String DESCRIPTION_REQUIRED = "Description is required.";
    /** Error of a description longer than {@link #MAX_DESCRIPTION_LENGTH}. */
    public static final String DESCRIPTION_TOO_LONG =
            "Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.";
    /** Error of a missing category. */
    public static final String CATEGORY_REQUIRED = "Choose a category.";
    /** Error of a missing condition. */
    public static final String CONDITION_REQUIRED = "Choose the condition of the item.";
    /** Error of a missing meet-up location. */
    public static final String MEETUP_REQUIRED = "Choose a meet-up location.";
    /** Error of a missing price on a Sell listing. */
    public static final String PRICE_REQUIRED = "Price is required for a Sell listing.";
    /** Error of a price that is not a number. */
    public static final String PRICE_NOT_A_NUMBER = "Enter the price as a number, for example 180 or 1,200.50.";
    /** Error of a price that is zero or negative. */
    public static final String PRICE_NOT_POSITIVE =
            "Price must be more than HK$ 0. To give the item away for free, choose Give Away.";
    /** Error of a price with fractions of a cent. */
    public static final String PRICE_TOO_PRECISE = "Price can have at most 2 decimal places.";
    /** Error of a price higher than {@link #MAX_PRICE}. */
    public static final String PRICE_TOO_HIGH = "Price cannot be more than HK$ 1,000,000.";
    /** Error of a missing lending duration on a Lend listing. */
    public static final String LENDING_REQUIRED = "Lending duration is required for a Lend listing.";
    /** Error of a lending duration without unit. */
    public static final String LENDING_UNIT_REQUIRED = "Choose the unit of the lending duration.";
    /** Error of a lending duration that is not a whole number. */
    public static final String LENDING_NOT_A_WHOLE_NUMBER =
            "Enter the lending duration as a whole number, for example 7.";
    /** Error of a lending duration that is zero or negative. */
    public static final String LENDING_NOT_POSITIVE = "Lending duration must be a positive period.";
    /** Error of a lending duration shorter than {@link #MIN_LENDING_DURATION}. */
    public static final String LENDING_TOO_SHORT = "Lending duration must be at least 5 seconds.";
    /** Error of a lending duration longer than {@link #MAX_LENDING_DURATION}. */
    public static final String LENDING_TOO_LONG = "Lending duration cannot be more than 365 days.";
    /** Error of a listing without photo. */
    public static final String PHOTOS_REQUIRED = "Add at least one photo of the item.";
    /** Error of a listing with more than {@link #MAX_PHOTOS} photos. */
    public static final String PHOTOS_TOO_MANY = "A listing can have at most " + MAX_PHOTOS + " photos.";

    /** Reason, after the file name, of a photo file that no longer exists. */
    public static final String PHOTO_NOT_FOUND = "can no longer be found.";
    /** Reason, after the file name, of a photo that is a folder or another non-file. */
    public static final String PHOTO_NOT_A_FILE = "is not a file.";
    /** Reason, after the file name, of a photo file that cannot be read. */
    public static final String PHOTO_UNREADABLE = "cannot be read.";
    /** Reason, after the file name, of a file whose extension is not an accepted image type. */
    public static final String PHOTO_UNSUPPORTED_TYPE = "is not a supported image (use PNG, JPEG, GIF or BMP).";
    /** Reason, after the file name, of a file whose content is not an image. */
    public static final String PHOTO_NOT_AN_IMAGE = "is not a valid image file.";
    /** Reason, after the file name, of a photo file larger than {@link #MAX_PHOTO_BYTES}. */
    public static final String PHOTO_TOO_LARGE = "is larger than 10 MB.";
    /** Reason, after the file name, of a file chosen again while it is already in the form. */
    public static final String PHOTO_ALREADY_ADDED = "is already added.";
    /** Reason, after the file name, of a file submitted twice in the same listing. */
    public static final String PHOTO_REPEATED = "is added more than once.";

    /** A number without thousands separators, such as {@code 180}, {@code 180.5} or {@code .5}. */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");
    /** A number with thousands separators in groups of 3 digits, such as {@code 1,200.50}. */
    private static final Pattern GROUPED_NUMBER = Pattern.compile("[+-]?\\d{1,3}(,\\d{3})+(\\.\\d*)?");
    /** A whole number, such as {@code 7}. */
    private static final Pattern WHOLE_NUMBER = Pattern.compile("[+-]?\\d+");
    /** The currency markers allowed around a price. */
    private static final Pattern CURRENCY = Pattern.compile("(?i)HK\\$|\\$");

    /** First bytes of a PNG file. */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    /** First bytes of a JPEG file. */
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    /** First bytes of a GIF file (1987 version). */
    private static final byte[] GIF87_SIGNATURE = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    /** First bytes of a GIF file (1989 version). */
    private static final byte[] GIF89_SIGNATURE = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    /** First bytes of a BMP file. */
    private static final byte[] BMP_SIGNATURE = "BM".getBytes(StandardCharsets.US_ASCII);
    /** Number of bytes read to recognise an image file. */
    private static final int SIGNATURE_BYTES = PNG_SIGNATURE.length;

    /** The store of listings and notifications. */
    private final DummyDataService dataService;

    /**
     * Creates the service on the application's shared data store.
     */
    public CreateListingService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Creates the service on the given data store (used by the tests).
     *
     * @param dataService the store of listings and notifications
     * @throws NullPointerException if {@code dataService} is {@code null}
     */
    public CreateListingService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
    }

    // =====================================================================
    // Basic flow, steps 7 to 10: submit the listing
    // =====================================================================

    /**
     * Submits a listing for the logged-in Seller (basic flow, steps 7 to 10). If every field
     * required by the chosen type is valid, the listing is stored with status Pending Review,
     * in the Seller's name, and the admin is notified of the new submission. Otherwise nothing
     * is stored and every invalid field is reported.
     *
     * @param request the values entered in the listing form
     * @return the stored listing and the confirmation for the Seller, or the errors of every
     *         invalid field
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws SecurityException    if no active Seller is logged in
     */
    public CreateListingResult createListing(CreateListingRequest request) {
        Objects.requireNonNull(request, "request");
        Seller seller = SessionManager.requireSeller();

        Map<ListingField, List<String>> errors = validate(request);
        if (!errors.isEmpty()) {
            return CreateListingResult.failure(errors);
        }

        Listing listing = newListing(request, seller);
        synchronized (dataService) {
            dataService.addListing(listing);
            listing.setPhotos(toPhotos(listing.getId(), request.photos()));
        }
        // Step 9: notify the admin, after releasing the data store lock (see the class comment).
        dataService.getAdminNotificationService().notifyListingSubmission(listing);
        return CreateListingResult.success(listing, confirmationMessage(listing));
    }

    /**
     * Returns the confirmation shown to the Seller once a listing is submitted (step 10).
     *
     * @param listing the stored listing
     * @return the confirmation, naming the listing and explaining when buyers will see it
     */
    public static String confirmationMessage(Listing listing) {
        return "\"" + listing.getTitle() + "\" has been submitted for review. "
                + "It will be visible to buyers once an admin approves it.";
    }

    // =====================================================================
    // Step 8: validate the listing (alternative flows A1, A2, A3 and A4)
    // =====================================================================

    /**
     * Checks every field of a listing form (step 8). Only the price of a Sell listing and the
     * lending duration of a Lend listing are checked; for other types those values are
     * ignored (alternative flow A4). Changes nothing and needs no session.
     *
     * @param request the values entered in the listing form
     * @return a new map from each invalid field to its error messages, in form order; empty
     *         if the listing can be stored
     * @throws NullPointerException if {@code request} is {@code null}
     */
    public static Map<ListingField, List<String>> validate(CreateListingRequest request) {
        Objects.requireNonNull(request, "request");
        Map<ListingField, List<String>> errors = new EnumMap<>(ListingField.class);
        if (request.type() == null) {
            errors.put(ListingField.TYPE, List.of(TYPE_REQUIRED));
        }
        putIfInvalid(errors, ListingField.TITLE,
                checkText(request.title(), MAX_TITLE_LENGTH, TITLE_REQUIRED, TITLE_TOO_LONG));
        putIfInvalid(errors, ListingField.DESCRIPTION,
                checkText(request.description(), MAX_DESCRIPTION_LENGTH, DESCRIPTION_REQUIRED, DESCRIPTION_TOO_LONG));
        if (request.category() == null) {
            errors.put(ListingField.CATEGORY, List.of(CATEGORY_REQUIRED));
        }
        if (request.condition() == null) {
            errors.put(ListingField.CONDITION, List.of(CONDITION_REQUIRED));
        }
        if (request.type() == ListingType.SELL) {
            putIfInvalid(errors, ListingField.PRICE, checkPrice(request.price()));
        }
        if (request.type() == ListingType.LEND) {
            putIfInvalid(errors, ListingField.LENDING_DURATION,
                    checkLendingDuration(request.lendingAmount(), request.lendingUnit()));
        }
        if (request.meetupLocation() == null) {
            errors.put(ListingField.MEETUP_LOCATION, List.of(MEETUP_REQUIRED));
        }
        List<String> photoErrors = checkPhotos(request.photos());
        if (!photoErrors.isEmpty()) {
            errors.put(ListingField.PHOTOS, List.copyOf(photoErrors));
        }
        return errors;
    }

    /**
     * Checks a price typed by the Seller (alternative flows A1 and A2). {@code HK$}, {@code $},
     * spaces and thousands separators in groups of 3 digits are allowed, as in
     * {@code HK$ 1,200.50}.
     *
     * @param text the typed price
     * @return the error message, or {@code null} if the price is valid
     */
    public static String checkPrice(String text) {
        return readPrice(text).error();
    }

    /**
     * Converts a valid price typed by the Seller to HK$.
     *
     * @param text the typed price
     * @return the price in HK$
     * @throws IllegalArgumentException if the price is invalid, with the reason as message
     */
    public static double toPrice(String text) {
        return readPrice(text).valueOrThrow().doubleValue();
    }

    /**
     * Checks a lending duration entered by the Seller (alternative flows A1 and A2): a whole,
     * positive number of units, from 5 seconds to 365 days.
     *
     * @param amountText the typed number of units
     * @param unit       the chosen unit
     * @return the error message, or {@code null} if the duration is valid
     */
    public static String checkLendingDuration(String amountText, LendingDurationUnit unit) {
        return readLendingDuration(amountText, unit).error();
    }

    /**
     * Converts a valid lending duration entered by the Seller.
     *
     * @param amountText the typed number of units
     * @param unit       the chosen unit
     * @return the lending duration
     * @throws IllegalArgumentException if the duration is invalid, with the reason as message
     */
    public static Duration toLendingDuration(String amountText, LendingDurationUnit unit) {
        return readLendingDuration(amountText, unit).valueOrThrow();
    }

    /**
     * Checks the photos of a listing at submission (alternative flows A1 and A3): from 1 to 6
     * distinct image files that can still be read.
     *
     * @param photos the chosen image files; {@code null} and {@code null} elements count as
     *               no photo
     * @return the error messages, empty if the photos are valid
     */
    public static List<String> checkPhotos(List<Path> photos) {
        List<Path> chosen = photos == null ? List.of() : photos.stream().filter(Objects::nonNull).toList();
        List<String> errors = new ArrayList<>();
        if (chosen.size() < MIN_PHOTOS) {
            errors.add(PHOTOS_REQUIRED);
            return errors;
        }
        if (chosen.size() > MAX_PHOTOS) {
            errors.add(PHOTOS_TOO_MANY);
        }
        Set<Path> seen = new HashSet<>();
        for (Path photo : chosen) {
            if (!seen.add(normalize(photo))) {
                errors.add(fileName(photo) + " " + PHOTO_REPEATED);
                continue;
            }
            String problem = photoProblem(photo);
            if (problem != null) {
                errors.add(fileName(photo) + " " + problem);
            }
        }
        return errors;
    }

    /**
     * Tells why a file cannot be a photo of a listing: it must be an existing, readable file
     * of at most 10 MB, whose extension is PNG, JPG, JPEG, GIF or BMP and whose content starts
     * like an image of one of these formats (so a renamed text file is refused).
     *
     * @param photo the file
     * @return the reason, to show after the file name, or {@code null} if the file is a valid photo
     * @throws NullPointerException if {@code photo} is {@code null}
     */
    public static String photoProblem(Path photo) {
        Objects.requireNonNull(photo, "photo");
        if (!Files.exists(photo)) {
            return PHOTO_NOT_FOUND;
        }
        if (!Files.isRegularFile(photo)) {
            return PHOTO_NOT_A_FILE;
        }
        if (!hasPhotoExtension(photo)) {
            return PHOTO_UNSUPPORTED_TYPE;
        }
        if (!Files.isReadable(photo)) {
            return PHOTO_UNREADABLE;
        }
        long size;
        byte[] header;
        try {
            size = Files.size(photo);
            header = readHeader(photo);
        } catch (IOException e) {
            return PHOTO_UNREADABLE;
        }
        if (size > MAX_PHOTO_BYTES) {
            return PHOTO_TOO_LARGE;
        }
        if (!hasImageSignature(header)) {
            return PHOTO_NOT_AN_IMAGE;
        }
        return null;
    }

    // =====================================================================
    // Step 4: choose the photos (alternative flow A3)
    // =====================================================================

    /**
     * Adds the files chosen by the Seller to the photos of the form (step 4). Files that are
     * not valid images, that are already in the form, or that would exceed the 6 photo limit
     * are rejected, and the Seller is told which ones and why (alternative flow A3).
     *
     * @param current  the photos already in the form, in display order; {@code null} counts as none
     * @param selected the files just chosen by the Seller; {@code null} counts as none
     * @return the photos of the form after the addition (as absolute paths), the number added,
     *         and the reasons of the rejected files
     */
    public static PhotoSelectionResult addPhotos(List<Path> current, List<Path> selected) {
        List<Path> photos = new ArrayList<>();
        Set<Path> keys = new HashSet<>();
        if (current != null) {
            for (Path photo : current) {
                if (photo != null && keys.add(normalize(photo))) {
                    photos.add(normalize(photo));
                }
            }
        }
        int before = photos.size();
        List<String> rejections = new ArrayList<>();
        List<String> overLimit = new ArrayList<>();
        if (selected != null) {
            for (Path photo : selected) {
                if (photo == null) {
                    continue;
                }
                Path key = normalize(photo);
                String problem = keys.contains(key) ? PHOTO_ALREADY_ADDED : photoProblem(photo);
                if (problem != null) {
                    rejections.add(fileName(photo) + " " + problem);
                } else if (photos.size() >= MAX_PHOTOS) {
                    overLimit.add(fileName(photo));
                } else {
                    keys.add(key);
                    photos.add(key);
                }
            }
        }
        if (!overLimit.isEmpty()) {
            rejections.add(joinNames(overLimit) + (overLimit.size() == 1 ? " was" : " were")
                    + " not added: a listing can have at most " + MAX_PHOTOS + " photos.");
        }
        return new PhotoSelectionResult(photos, photos.size() - before, rejections);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * A value read from the form, or the reason why it is invalid.
     *
     * @param value the value, {@code null} if invalid
     * @param error the reason, {@code null} if valid
     * @param <T>   the type of the value
     */
    private record Checked<T>(T value, String error) {

        /**
         * Creates a valid value.
         *
         * @param value the value
         * @param <T>   the type of the value
         * @return the checked value
         */
        static <T> Checked<T> valid(T value) {
            return new Checked<>(value, null);
        }

        /**
         * Creates an invalid value.
         *
         * @param error the reason
         * @param <T>   the type of the value
         * @return the checked value
         */
        static <T> Checked<T> invalid(String error) {
            return new Checked<>(null, error);
        }

        /**
         * Returns the value.
         *
         * @return the value
         * @throws IllegalArgumentException if the value is invalid, with the reason as message
         */
        T valueOrThrow() {
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
            return value;
        }
    }

    /**
     * Reads a price typed by the Seller.
     *
     * @param text the typed price
     * @return the price in HK$, or the reason why it is invalid
     */
    private static Checked<BigDecimal> readPrice(String text) {
        String cleaned = text == null ? "" : CURRENCY.matcher(text).replaceAll("").replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            return Checked.invalid(PRICE_REQUIRED);
        }
        if (!PLAIN_NUMBER.matcher(cleaned).matches()) {
            if (!GROUPED_NUMBER.matcher(cleaned).matches()) {
                return Checked.invalid(PRICE_NOT_A_NUMBER);
            }
            cleaned = cleaned.replace(",", "");
        }
        BigDecimal price = new BigDecimal(cleaned);
        if (price.signum() <= 0) {
            return Checked.invalid(PRICE_NOT_POSITIVE);
        }
        if (price.stripTrailingZeros().scale() > 2) {
            return Checked.invalid(PRICE_TOO_PRECISE);
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            return Checked.invalid(PRICE_TOO_HIGH);
        }
        return Checked.valid(price);
    }

    /**
     * Reads a lending duration entered by the Seller.
     *
     * @param amountText the typed number of units
     * @param unit       the chosen unit
     * @return the duration, or the reason why it is invalid
     */
    private static Checked<Duration> readLendingDuration(String amountText, LendingDurationUnit unit) {
        String cleaned = amountText == null ? "" : amountText.trim();
        if (cleaned.isEmpty()) {
            return Checked.invalid(LENDING_REQUIRED);
        }
        if (unit == null) {
            return Checked.invalid(LENDING_UNIT_REQUIRED);
        }
        if (!WHOLE_NUMBER.matcher(cleaned).matches()) {
            return Checked.invalid(LENDING_NOT_A_WHOLE_NUMBER);
        }
        BigInteger amount = new BigInteger(cleaned);
        if (amount.signum() <= 0) {
            return Checked.invalid(LENDING_NOT_POSITIVE);
        }
        // Compared before converting, so that a huge amount cannot overflow.
        long maxAmount = MAX_LENDING_DURATION.getSeconds() / unit.getLength().getSeconds();
        if (amount.compareTo(BigInteger.valueOf(maxAmount)) > 0) {
            return Checked.invalid(LENDING_TOO_LONG);
        }
        Duration duration = unit.toDuration(amount.longValueExact());
        if (duration.compareTo(MIN_LENDING_DURATION) < 0) {
            return Checked.invalid(LENDING_TOO_SHORT);
        }
        return Checked.valid(duration);
    }

    /**
     * Checks a required text field.
     *
     * @param text      the typed text
     * @param maxLength the longest text allowed, in characters, after trimming
     * @param required  the error of an empty text
     * @param tooLong   the error of a text that is too long
     * @return the error message, or {@code null} if the text is valid
     */
    private static String checkText(String text, int maxLength, String required, String tooLong) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return required;
        }
        return trimmed.codePointCount(0, trimmed.length()) > maxLength ? tooLong : null;
    }

    /**
     * Records the error of a field, if there is one.
     *
     * @param errors the errors of the form
     * @param field  the field
     * @param error  its error message, {@code null} if the field is valid
     */
    private static void putIfInvalid(Map<ListingField, List<String>> errors, ListingField field, String error) {
        if (error != null) {
            errors.put(field, List.of(error));
        }
    }

    /**
     * Creates the listing to store from a valid form.
     *
     * @param request the valid values of the form
     * @param seller  the logged-in Seller, who posts the listing
     * @return the listing, with status Pending Review and no identifier yet
     */
    private static Listing newListing(CreateListingRequest request, Seller seller) {
        Listing listing = new Listing();
        listing.setType(request.type());
        listing.setTitle(request.title().trim());
        listing.setDescription(request.description().trim());
        listing.setCategory(request.category());
        listing.setCondition(request.condition());
        // Alternative flow A4: only the value of the chosen type is kept.
        listing.setPrice(request.type() == ListingType.SELL ? toPrice(request.price()) : null);
        listing.setLendingDuration(request.type() == ListingType.LEND
                ? toLendingDuration(request.lendingAmount(), request.lendingUnit()) : null);
        listing.setMeetupLocation(request.meetupLocation());
        listing.setStatus(ListingStatus.PENDING_REVIEW);
        listing.setPostedDate(LocalDateTime.now());
        listing.setViewCount(0);
        listing.setAdminReason(null);
        listing.setSellerUsername(seller.getUsername());
        listing.setSellerFullName(seller.getFullName());
        return listing;
    }

    /**
     * Creates the photos of a stored listing, numbered from 1 in display order.
     *
     * @param listingId the identifier of the listing
     * @param files     the valid image files
     * @return the photos, holding the absolute paths of the files
     */
    private static List<Photo> toPhotos(String listingId, List<Path> files) {
        List<Photo> photos = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            photos.add(new Photo(listingId + PHOTO_ID_SEPARATOR + (i + 1), normalize(files.get(i)).toString()));
        }
        return photos;
    }

    /**
     * Tells whether a file has the extension of an accepted image type, ignoring case.
     *
     * @param photo the file
     * @return {@code true} if its extension is PNG, JPG, JPEG, GIF or BMP
     */
    private static boolean hasPhotoExtension(Path photo) {
        String name = fileName(photo).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && PHOTO_EXTENSIONS.contains(name.substring(dot + 1));
    }

    /**
     * Reads the first bytes of a file.
     *
     * @param photo the file
     * @return up to {@code SIGNATURE_BYTES} bytes, fewer if the file is shorter
     * @throws IOException if the file cannot be read
     */
    private static byte[] readHeader(Path photo) throws IOException {
        try (InputStream in = Files.newInputStream(photo)) {
            return in.readNBytes(SIGNATURE_BYTES);
        }
    }

    /**
     * Tells whether a file starts like a PNG, JPEG, GIF or BMP image.
     *
     * @param header the first bytes of the file
     * @return {@code true} if they are the signature of an accepted image format
     */
    private static boolean hasImageSignature(byte[] header) {
        return startsWith(header, PNG_SIGNATURE) || startsWith(header, JPEG_SIGNATURE)
                || startsWith(header, GIF87_SIGNATURE) || startsWith(header, GIF89_SIGNATURE)
                || startsWith(header, BMP_SIGNATURE);
    }

    /**
     * Tells whether some bytes start with a prefix.
     *
     * @param bytes  the bytes
     * @param prefix the prefix
     * @return {@code true} if {@code bytes} starts with {@code prefix}
     */
    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    /**
     * Returns the absolute, normalised form of a path, used to recognise a file chosen twice.
     *
     * @param path the path
     * @return the absolute normalised path
     */
    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * Returns the name of a file, as shown to the Seller.
     *
     * @param path the path of the file
     * @return its last element, for example {@code lamp.png}
     */
    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }

    /**
     * Joins file names for a message, for example {@code a.png, b.png and c.png}.
     *
     * @param names the file names, at least one
     * @return the joined names
     */
    private static String joinNames(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
