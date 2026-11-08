package com.hkust.marketplace.services;

import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.PasswordPolicy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Business logic of use case 2.1 "Register As Seller" (US-S01). It has no JavaFX
 * dependency, so it can be unit tested without starting the user interface.
 *
 * <p>{@link #register(SellerRegistrationRequest)} checks the form in one pass
 * (basic flow, step 5) and reports <em>every</em> problem it finds:</p>
 * <ul>
 *   <li>the username is not empty (A2) and is not used by any buyer, seller or admin
 *       account, ignoring case, including soft-deleted accounts (A1);</li>
 *   <li>the full name is not empty (A2);</li>
 *   <li>the password is not empty (A2) and meets every {@link PasswordPolicy} rule (A3);</li>
 *   <li>the confirm password is identical to the password (A4).</li>
 * </ul>
 *
 * <p>If every check passes, an active {@link Seller} is created with its password
 * hashed by {@link PasswordHasher} and stored (step 6). Nothing is stored when a check
 * fails. Text fields are trimmed, except the passwords, which are used exactly as
 * typed. The username is stored in lower case.</p>
 */
public class SellerRegistrationService {

    /** Error reported when the username is empty (A2). */
    public static final String USERNAME_REQUIRED = "Username is required.";

    /** Error reported when the username contains spaces. */
    public static final String USERNAME_NO_SPACES = "Username cannot contain spaces.";

    /** Error reported when the username is shorter than 3 characters. */
    public static final String USERNAME_TOO_SHORT = "Username must be at least 3 characters long.";

    /** Error reported when the username is already used by any account (A1). */
    public static final String USERNAME_TAKEN = "Username is already taken. Please choose another one.";

    /** Error reported when the full name is empty (A2). */
    public static final String FULL_NAME_REQUIRED = "Full name is required.";

    /** Error reported when the password is empty (A2). */
    public static final String PASSWORD_REQUIRED = "Password is required.";

    /** Start of the error that lists the password rules that are not met (A3). */
    public static final String WEAK_PASSWORD_PREFIX = "Password must contain ";

    /** Error reported when the confirm password differs from the password (A4). */
    public static final String PASSWORDS_DO_NOT_MATCH = "Passwords do not match.";

    /** Where accounts are looked up and stored. */
    private final DummyDataService dataService;

    /**
     * Creates a service that stores accounts in the shared {@link DummyDataService}.
     */
    public SellerRegistrationService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Creates a service that stores accounts in the given data service.
     *
     * @param dataService where accounts are looked up and stored
     * @throws NullPointerException if {@code dataService} is {@code null}
     */
    public SellerRegistrationService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
    }

    /**
     * Validates the registration form and, if it is valid, creates and stores the
     * seller account.
     *
     * <p>Username uniqueness is enforced atomically: if two requests for the same
     * username arrive at the same time, only the first one creates an account and the
     * other gets a {@link RegistrationField#USERNAME} error.</p>
     *
     * @param request the data entered on the form; its fields may be {@code null},
     *                which is treated as empty
     * @return a successful result holding the stored seller, or a failed result with
     *         the error messages of every invalid field
     * @throws NullPointerException if {@code request} is {@code null}
     */
    public RegistrationResult<Seller> register(SellerRegistrationRequest request) {
        Objects.requireNonNull(request, "request");
        String username = normalizeUsername(request.username());
        String fullName = trimToEmpty(request.fullName());
        String password = request.password() == null ? "" : request.password();
        String confirmPassword = request.confirmPassword() == null ? "" : request.confirmPassword();

        Map<RegistrationField, List<String>> errors = new EnumMap<>(RegistrationField.class);
        validateUsername(username, errors);
        if (fullName.isEmpty()) {
            errors.put(RegistrationField.FULL_NAME, List.of(FULL_NAME_REQUIRED));
        }
        validatePassword(password, confirmPassword, errors);
        if (!errors.isEmpty()) {
            return RegistrationResult.failure(errors);
        }

        Seller seller = new Seller(username, fullName, PasswordHasher.hash(password), normalizeBio(request.bio()));
        seller.setPreferredMeetups(normalizeMeetups(request.preferredMeetups()));
        if (!dataService.registerUserIfAbsent(seller)) {
            // Another registration took the username after it was checked above.
            return RegistrationResult.failure(Map.of(RegistrationField.USERNAME, List.of(USERNAME_TAKEN)));
        }
        return RegistrationResult.success(seller);
    }

    /**
     * Checks that the username is present and not already used (A1, A2).
     *
     * @param username the normalised username
     * @param errors   the map to add the username error to, if any
     */
    private void validateUsername(String username, Map<RegistrationField, List<String>> errors) {
        if (username.isEmpty()) {
            errors.put(RegistrationField.USERNAME, List.of(USERNAME_REQUIRED));
        } else if (username.contains(" ")) {
            errors.put(RegistrationField.USERNAME, List.of(USERNAME_NO_SPACES));
        } else if (username.length() < 3) {
            errors.put(RegistrationField.USERNAME, List.of(USERNAME_TOO_SHORT));
        } else if (dataService.usernameExists(username)) {
            errors.put(RegistrationField.USERNAME, List.of(USERNAME_TAKEN));
        }
    }

    /**
     * Checks that the password is present and strong enough, and that the confirm
     * password matches it (A2, A3, A4). The match is only checked when a password
     * was entered.
     *
     * @param password        the password as typed
     * @param confirmPassword the confirm password as typed
     * @param errors          the map to add the password errors to, if any
     */
    private static void validatePassword(String password, String confirmPassword,
                                         Map<RegistrationField, List<String>> errors) {
        if (password.isEmpty()) {
            errors.put(RegistrationField.PASSWORD, List.of(PASSWORD_REQUIRED));
            return;
        }
        List<String> violations = PasswordPolicy.findViolations(password);
        if (!violations.isEmpty()) {
            errors.put(RegistrationField.PASSWORD, List.of(WEAK_PASSWORD_PREFIX + joinRules(violations) + "."));
        }
        if (!password.equals(confirmPassword)) {
            errors.put(RegistrationField.CONFIRM_PASSWORD, List.of(PASSWORDS_DO_NOT_MATCH));
        }
    }

    /**
     * Joins rule descriptions into readable English: {@code "a"}, {@code "a and b"}
     * or {@code "a, b and c"}. Also used by {@link SellerProfileService}, so that a weak
     * password is reported in the same words on the registration and profile screens.
     *
     * @param rules the rule descriptions; must not be empty
     * @return the joined descriptions
     */
    static String joinRules(List<String> rules) {
        return PasswordPolicy.joinRules(rules);
    }

    /**
     * Trims a username and converts it to lower case, so that uniqueness ignores case.
     *
     * @param username the username as typed; may be {@code null}
     * @return the normalised username; empty if {@code username} is {@code null} or blank
     */
    private static String normalizeUsername(String username) {
        return trimToEmpty(username).toLowerCase(Locale.ROOT);
    }

    /**
     * Trims an optional bio; a blank bio is stored as no bio.
     *
     * @param bio the bio as typed; may be {@code null}
     * @return the trimmed bio, or {@code null} if it is {@code null} or blank
     */
    private static String normalizeBio(String bio) {
        String trimmed = trimToEmpty(bio);
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Removes {@code null} entries and duplicates from the selected meet-up locations.
     *
     * @param meetups the selected locations; may be {@code null}
     * @return a new modifiable list of distinct locations, in {@link MeetupLocation}
     *         declaration order; empty if none were selected
     */
    private static List<MeetupLocation> normalizeMeetups(List<MeetupLocation> meetups) {
        EnumSet<MeetupLocation> distinct = EnumSet.noneOf(MeetupLocation.class);
        if (meetups != null) {
            meetups.stream().filter(Objects::nonNull).forEach(distinct::add);
        }
        return new ArrayList<>(distinct);
    }

    /**
     * Trims a value, treating {@code null} as empty.
     *
     * @param value the value; may be {@code null}
     * @return the trimmed value, never {@code null}
     */
    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
