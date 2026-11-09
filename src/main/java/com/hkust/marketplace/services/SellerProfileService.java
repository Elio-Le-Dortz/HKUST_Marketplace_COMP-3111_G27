package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.SellerProfileField;
import com.hkust.marketplace.models.SellerProfileResult;
import com.hkust.marketplace.models.SellerProfileUpdateRequest;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.PasswordPolicy;
import com.hkust.marketplace.utils.PasswordUtil;
import com.hkust.marketplace.utils.SessionManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Business logic of use case 2.6 "Manage Seller Profile" (US-S06): a Seller updates their full
 * name, bio, preferred meet-up locations and password. Acceptance criterion of the Sprint 2
 * backlog: the updates are reflected across all posted listings.
 *
 * <p><b>Class diagram.</b> The profile is made of attributes of {@link Seller}:
 * {@code User.fullName} (required), {@code Seller.bio} (0..1) and
 * {@code Seller.preferredMeetups} (a list of {@link MeetupLocation}, 0..*); the password is
 * {@code User.password}, always stored hashed. The username identifies the account and cannot be
 * changed. A listing is posted by its Seller ({@code Seller "1" --> "*" Listing : Posts}) and
 * shows the Seller's name to buyers and to the admin, so a save gives every listing of the Seller
 * their current name, whatever its status (acceptance criterion). The bio and the meet-up
 * locations are read from the Seller itself, for example to preselect the meet-up location of a
 * new listing (use case 2.3); the meet-up location chosen for an existing listing is a choice of
 * that listing and is not changed.</p>
 *
 * <p><b>Rules.</b> Every field is checked at once, and every invalid field is reported with its
 * reason; nothing is saved unless every check passes:</p>
 * <ul>
 *   <li>A1: the full name is required (trimmed) and, when changed, at most
 *       {@value #MAX_FULL_NAME_LENGTH} characters;</li>
 *   <li>the bio is optional (trimmed; a blank bio removes it) and, when changed, at most
 *       {@value #MAX_BIO_LENGTH} characters;</li>
 *   <li>the meet-up locations are optional, any number; duplicates are removed;</li>
 *   <li>the password is changed only if one of the three password fields is filled in (step 4).
 *       A2: the current password is then required and must be correct, otherwise nothing is
 *       saved. A3: the new password must follow the same rules as at registration
 *       ({@link PasswordPolicy}), differ from the current password, and be typed twice
 *       identically.</li>
 * </ul>
 * <p>The limits apply to changed values only, so that a profile created before them (at
 * registration, or by the admin) can still be saved unchanged.</p>
 *
 * <p><b>Access control.</b> Every public instance method first calls
 * {@link SessionManager#requireSeller()}, so it can only be used during an active Seller session
 * (use case 2.2 special requirement), and a Seller can only change their own profile.</p>
 *
 * <p><b>Atomicity.</b> The slow password checks (PBKDF2) run before taking any lock. The changes
 * are then saved, with the listings of the Seller, while holding the lock of the data store
 * ({@link DummyDataService}), which Create Listing also takes to store a new listing with the
 * Seller's name: a listing is therefore never left with an old name. A current password checked
 * before taking the lock is accepted only if the password did not change in the meantime, so
 * when two password changes are sent at the same time with the same current password, only the
 * first one succeeds. Inside the lock, only the data store is called.</p>
 *
 * <p><b>Persistence.</b> A saved profile is written to the data store with
 * {@link DummyDataService#updateUser}, and every renamed listing with
 * {@link DummyDataService#updateListing}, so the changes are kept in the SQLite database when
 * the application restarts.</p>
 *
 * <p>This class does not use JavaFX, so every flow is unit tested without a display.</p>
 */
public class SellerProfileService {

    /** Longest full name, in characters. */
    public static final int MAX_FULL_NAME_LENGTH = 100;

    /** Longest bio, in characters. */
    public static final int MAX_BIO_LENGTH = 500;

    /** Error of an empty full name (alternative flow A1), the same as at registration. */
    public static final String FULL_NAME_REQUIRED = SellerRegistrationService.FULL_NAME_REQUIRED;
    /** Error of a full name longer than {@link #MAX_FULL_NAME_LENGTH}. */
    public static final String FULL_NAME_TOO_LONG =
            "Full name must be at most " + MAX_FULL_NAME_LENGTH + " characters.";
    /** Error of a bio longer than {@link #MAX_BIO_LENGTH}. */
    public static final String BIO_TOO_LONG = "Bio must be at most " + MAX_BIO_LENGTH + " characters.";
    /** Error of a password change without the current password (alternative flow A2). */
    public static final String CURRENT_PASSWORD_REQUIRED = "Enter your current password to change your password.";
    /** Error of a wrong current password (alternative flow A2). */
    public static final String WRONG_CURRENT_PASSWORD = "Your current password is incorrect.";
    /** Error of a current password that was right, but changed while the form was being saved. */
    public static final String PASSWORD_CHANGED_MEANWHILE =
            "Your password was changed in the meantime. Enter your current password again.";
    /** Error of a password change without the new password (alternative flow A3). */
    public static final String NEW_PASSWORD_REQUIRED = "Enter the new password.";
    /** Start of the error listing the password rules that the new password breaks (alternative flow A3). */
    public static final String WEAK_PASSWORD_PREFIX = "New password must contain ";
    /** Error of a new password identical to the current one (alternative flow A3). */
    public static final String NEW_PASSWORD_SAME_AS_CURRENT =
            "The new password must be different from your current password.";
    /** Error of a new password without its confirmation (alternative flow A3). */
    public static final String CONFIRM_PASSWORD_REQUIRED = "Confirm the new password.";
    /** Error of a confirmation that differs from the new password (alternative flow A3), the same as at registration. */
    public static final String PASSWORDS_DO_NOT_MATCH = SellerRegistrationService.PASSWORDS_DO_NOT_MATCH;
    /** Message of a save that changed nothing. */
    public static final String NOTHING_CHANGED = "You did not change anything, so your profile was not modified.";

    /** The store of accounts and listings. */
    private final DummyDataService dataService;

    /**
     * Creates the service on the application's shared data store.
     */
    public SellerProfileService() {
        this(DummyDataService.getInstance());
    }

    /**
     * Creates the service on the given data store (used by the tests).
     *
     * @param dataService the store of accounts and listings
     * @throws NullPointerException if {@code dataService} is {@code null}
     */
    public SellerProfileService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
    }

    // =====================================================================
    // Basic flow, step 2: the Seller's profile
    // =====================================================================

    /**
     * Returns the logged-in Seller (basic flow, step 2), whose username (not editable), full
     * name, bio and preferred meet-up locations the profile screen shows. The form is filled
     * with {@link #toEditRequest(Seller)}.
     *
     * @return the logged-in Seller
     * @throws SecurityException if no active Seller is logged in
     */
    public Seller getMyProfile() {
        return SessionManager.requireSeller();
    }

    // =====================================================================
    // Basic flow, steps 5 to 8: save the profile (alternative flows A1, A2 and A3)
    // =====================================================================

    /**
     * Saves the profile form of the logged-in Seller (basic flow, steps 5 to 8). If every field
     * is valid, the changed values are saved, a new password is stored hashed, and every listing
     * of the Seller gets the Seller's current name. Otherwise nothing is saved and every invalid
     * field is reported (alternative flows A1, A2 and A3). A form holding the current values
     * changes nothing.
     *
     * @param request the values of the profile form
     * @return the fields changed and the number of listings updated, the explanation that
     *         nothing changed, or the errors of every invalid field
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws SecurityException    if no active Seller is logged in
     */
    public SellerProfileResult updateProfile(SellerProfileUpdateRequest request) {
        Objects.requireNonNull(request, "request");
        Seller seller = SessionManager.requireSeller();
        String fullName = trimToEmpty(request.fullName());
        String bio = normalizeBio(request.bio());
        List<MeetupLocation> meetups = normalizeMeetups(request.preferredMeetups());
        boolean passwordChange = request.isPasswordChangeRequested();

        // Step 6: check every field before taking the lock, since the password checks are slow.
        Map<SellerProfileField, List<String>> errors = new EnumMap<>(SellerProfileField.class);
        checkFullName(fullName, seller, errors);
        checkBio(bio, seller, errors);
        String checkedHash = seller.getPasswordHash();
        if (passwordChange) {
            checkPasswords(request, checkedHash, errors);
        }
        if (!errors.isEmpty()) {
            return SellerProfileResult.invalidFields(seller, errors);
        }
        // Step 7: the new password is hashed before taking the lock, as checking it was.
        String newHash = passwordChange ? PasswordHasher.hash(request.newPassword()) : null;

        synchronized (dataService) {
            if (passwordChange && !Objects.equals(checkedHash, seller.getPasswordHash())) {
                // A2: the current password was checked against a password replaced since then.
                return SellerProfileResult.invalidFields(seller,
                        Map.of(SellerProfileField.CURRENT_PASSWORD, List.of(PASSWORD_CHANGED_MEANWHILE)));
            }
            Set<SellerProfileField> changed = changedFields(seller, fullName, bio, meetups, passwordChange);
            if (changed.isEmpty()) {
                return SellerProfileResult.noChanges(seller, NOTHING_CHANGED);
            }
            if (changed.contains(SellerProfileField.FULL_NAME)) {
                seller.setFullName(fullName);
            }
            if (changed.contains(SellerProfileField.BIO)) {
                seller.setBio(bio);
            }
            if (changed.contains(SellerProfileField.PREFERRED_MEETUPS)) {
                seller.setPreferredMeetups(new ArrayList<>(meetups));
            }
            if (passwordChange) {
                seller.setPasswordHash(newHash);
            }
            // Write the account to the database, so the changes are kept after a restart.
            dataService.updateUser(seller);
            int updatedListings = renameListings(seller);
            // Step 8: confirm the update to the Seller.
            return SellerProfileResult.success(seller, changed, updatedListings,
                    confirmationMessage(changed, updatedListings, seller.getFullName()));
        }
    }

    // =====================================================================
    // Form helpers (no session needed, nothing changed)
    // =====================================================================

    /**
     * Fills the profile form with the current values of a Seller (basic flow, step 2): the full
     * name, the bio and the preferred meet-up locations (without duplicates, in the order of
     * {@link MeetupLocation}), and empty password fields. Saving it unchanged changes nothing.
     *
     * @param seller the Seller
     * @return the values of the form
     * @throws NullPointerException if {@code seller} is {@code null}
     */
    public static SellerProfileUpdateRequest toEditRequest(Seller seller) {
        Objects.requireNonNull(seller, "seller");
        String bio = normalizeBio(seller.getBio());
        return SellerProfileUpdateRequest.withoutPasswordChange(trimToEmpty(seller.getFullName()),
                bio == null ? "" : bio, normalizeMeetups(seller.getPreferredMeetups()));
    }

    /**
     * Tells whether a profile form holds exactly the current values of a Seller, so that
     * cancelling it loses nothing (alternative flow A4). Texts are compared once trimmed, a blank
     * bio is the same as no bio, and the meet-up locations are compared without duplicates and
     * order. A filled-in password field is a change.
     *
     * @param seller  the Seller
     * @param request the values of the form
     * @return {@code true} if saving the form would change nothing
     * @throws NullPointerException if an argument is {@code null}
     */
    public static boolean isUnchanged(Seller seller, SellerProfileUpdateRequest request) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(request, "request");
        return changedFields(seller, trimToEmpty(request.fullName()), normalizeBio(request.bio()),
                normalizeMeetups(request.preferredMeetups()), request.isPasswordChangeRequested()).isEmpty();
    }

    /**
     * Returns the length of a text once trimmed, counted in characters as the Seller sees them
     * (an emoji counts as one), which is how the length limits are applied.
     *
     * @param text the text, may be {@code null}
     * @return the number of characters, {@code 0} for {@code null}
     */
    public static int lengthOf(String text) {
        String trimmed = trimToEmpty(text);
        return trimmed.codePointCount(0, trimmed.length());
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================

    /**
     * Checks the full name (alternative flow A1).
     *
     * @param fullName the trimmed full name
     * @param seller   the Seller, whose current name is not limited in length
     * @param errors   the errors of the form, completed if the name is invalid
     */
    private static void checkFullName(String fullName, Seller seller, Map<SellerProfileField, List<String>> errors) {
        if (fullName.isEmpty()) {
            errors.put(SellerProfileField.FULL_NAME, List.of(FULL_NAME_REQUIRED));
        } else if (!fullName.equals(trimToEmpty(seller.getFullName())) && lengthOf(fullName) > MAX_FULL_NAME_LENGTH) {
            errors.put(SellerProfileField.FULL_NAME, List.of(FULL_NAME_TOO_LONG));
        }
    }

    /**
     * Checks the bio.
     *
     * @param bio    the normalised bio, {@code null} for none
     * @param seller the Seller, whose current bio is not limited in length
     * @param errors the errors of the form, completed if the bio is invalid
     */
    private static void checkBio(String bio, Seller seller, Map<SellerProfileField, List<String>> errors) {
        if (bio != null && !bio.equals(normalizeBio(seller.getBio())) && lengthOf(bio) > MAX_BIO_LENGTH) {
            errors.put(SellerProfileField.BIO, List.of(BIO_TOO_LONG));
        }
    }

    /**
     * Checks a password change (alternative flows A2 and A3). The passwords are used exactly as
     * typed, never trimmed. The new password is compared with the current one only once the
     * current password is known to be right.
     *
     * @param request     the values of the form, with at least one password field filled in
     * @param currentHash the stored hash of the Seller's password
     * @param errors      the errors of the form, completed for each invalid password field
     */
    private static void checkPasswords(SellerProfileUpdateRequest request, String currentHash,
                                       Map<SellerProfileField, List<String>> errors) {
        String current = nullToEmpty(request.currentPassword());
        String next = nullToEmpty(request.newPassword());
        String confirm = nullToEmpty(request.confirmPassword());

        boolean currentIsRight = false;
        if (current.isEmpty()) {
            errors.put(SellerProfileField.CURRENT_PASSWORD, List.of(CURRENT_PASSWORD_REQUIRED));
        } else {
            currentIsRight = PasswordUtil.verifyPassword(current, currentHash);
            if (!currentIsRight) {
                errors.put(SellerProfileField.CURRENT_PASSWORD, List.of(WRONG_CURRENT_PASSWORD));
            }
        }

        if (next.isEmpty()) {
            errors.put(SellerProfileField.NEW_PASSWORD, List.of(NEW_PASSWORD_REQUIRED));
            return;
        }
        List<String> violations = PasswordPolicy.findViolations(next);
        if (!violations.isEmpty()) {
            errors.put(SellerProfileField.NEW_PASSWORD,
                    List.of(WEAK_PASSWORD_PREFIX + SellerRegistrationService.joinRules(violations) + "."));
        } else if (currentIsRight && next.equals(current)) {
            errors.put(SellerProfileField.NEW_PASSWORD, List.of(NEW_PASSWORD_SAME_AS_CURRENT));
        }
        if (confirm.isEmpty()) {
            errors.put(SellerProfileField.CONFIRM_PASSWORD, List.of(CONFIRM_PASSWORD_REQUIRED));
        } else if (!next.equals(confirm)) {
            errors.put(SellerProfileField.CONFIRM_PASSWORD, List.of(PASSWORDS_DO_NOT_MATCH));
        }
    }

    /**
     * Returns the fields whose value differs from the Seller's current one.
     *
     * @param seller         the Seller
     * @param fullName       the trimmed full name of the form
     * @param bio            the normalised bio of the form, {@code null} for none
     * @param meetups        the normalised meet-up locations of the form
     * @param passwordChange {@code true} if a password field is filled in
     * @return the changed fields, in form order; the password is {@link SellerProfileField#NEW_PASSWORD}
     */
    private static Set<SellerProfileField> changedFields(Seller seller, String fullName, String bio,
                                                         List<MeetupLocation> meetups, boolean passwordChange) {
        Set<SellerProfileField> changed = EnumSet.noneOf(SellerProfileField.class);
        if (!fullName.equals(trimToEmpty(seller.getFullName()))) {
            changed.add(SellerProfileField.FULL_NAME);
        }
        if (!Objects.equals(bio, normalizeBio(seller.getBio()))) {
            changed.add(SellerProfileField.BIO);
        }
        if (!meetups.equals(normalizeMeetups(seller.getPreferredMeetups()))) {
            changed.add(SellerProfileField.PREFERRED_MEETUPS);
        }
        if (passwordChange) {
            changed.add(SellerProfileField.NEW_PASSWORD);
        }
        return changed;
    }

    /**
     * Gives every listing of the Seller, whatever its status, the Seller's current name
     * (acceptance criterion), and saves each renamed listing in the data store (and so in the
     * database). Called while holding the data store lock.
     *
     * @param seller the Seller, with the saved name
     * @return the number of listings whose seller name changed
     */
    private int renameListings(Seller seller) {
        int updated = 0;
        for (Listing listing : dataService.getListingsBySeller(seller.getUsername())) {
            if (!Objects.equals(seller.getFullName(), listing.getSellerFullName())) {
                listing.setSellerFullName(seller.getFullName());
                dataService.updateListing(listing);
                updated++;
            }
        }
        return updated;
    }

    /**
     * Builds the confirmation of a save (step 8), for example
     * {@code Your profile has been updated: full name and password. 3 of your listings now show
     * your name "Sam Lee".}
     *
     * @param changed         the changed fields, at least one
     * @param updatedListings number of listings whose seller name changed
     * @param fullName        the saved full name
     * @return the confirmation
     */
    private static String confirmationMessage(Set<SellerProfileField> changed, int updatedListings, String fullName) {
        List<String> names = new ArrayList<>();
        for (SellerProfileField field : changed) {
            names.add(switch (field) {
                case FULL_NAME -> "full name";
                case BIO -> "bio";
                case PREFERRED_MEETUPS -> "preferred meet-up locations";
                case CURRENT_PASSWORD, NEW_PASSWORD, CONFIRM_PASSWORD -> "password";
            });
        }
        String message = "Your profile has been updated: " + SellerRegistrationService.joinRules(names) + ".";
        if (updatedListings > 0) {
            message += " " + updatedListings + (updatedListings == 1 ? " of your listings now shows" : " of your listings now show")
                    + " your name \"" + fullName + "\".";
        }
        return message;
    }

    /**
     * Trims an optional bio; a blank bio means no bio.
     *
     * @param bio the bio, may be {@code null}
     * @return the trimmed bio, or {@code null} if it is {@code null} or blank
     */
    private static String normalizeBio(String bio) {
        String trimmed = trimToEmpty(bio);
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Removes {@code null} elements and duplicates from meet-up locations.
     *
     * @param meetups the locations, may be {@code null}
     * @return a new list of distinct locations, in {@link MeetupLocation} declaration order
     */
    private static List<MeetupLocation> normalizeMeetups(List<MeetupLocation> meetups) {
        EnumSet<MeetupLocation> distinct = EnumSet.noneOf(MeetupLocation.class);
        if (meetups != null) {
            meetups.stream().filter(Objects::nonNull).forEach(distinct::add);
        }
        return new ArrayList<>(distinct);
    }

    /**
     * Trims a text, treating {@code null} as empty.
     *
     * @param text the text, may be {@code null}
     * @return the trimmed text, never {@code null}
     */
    private static String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * Replaces {@code null} with an empty string, without trimming.
     *
     * @param text the text, may be {@code null}
     * @return the text, or an empty string
     */
    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
