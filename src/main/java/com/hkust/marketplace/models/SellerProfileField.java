package com.hkust.marketplace.models;

/**
 * Fields of the profile form of use case 2.6 "Manage Seller Profile" (US-S06).
 *
 * <p>A failed save reports its errors per field, so that the form can highlight every invalid
 * field (alternative flows A1, A2 and A3). A successful save tells which fields it changed. The
 * constants are declared in the order of the form, which is the order in which the errors are
 * reported. The username is shown on the form but cannot be changed, so it is not a field.</p>
 *
 * <p>The first three fields are attributes of the class diagram: {@code User.fullName},
 * {@code Seller.bio} and {@code Seller.preferredMeetups}. The three password fields change
 * {@code User.password}, which is stored hashed.</p>
 */
public enum SellerProfileField {

    /** Full name of the Seller ({@code User.fullName}): required, shown on every listing. */
    FULL_NAME,

    /** Short description of the Seller ({@code Seller.bio}): optional. */
    BIO,

    /** Preferred meet-up locations ({@code Seller.preferredMeetups}): optional, any number. */
    PREFERRED_MEETUPS,

    /** Current password, required to change the password (alternative flow A2). */
    CURRENT_PASSWORD,

    /**
     * New password, which must follow the password rules (alternative flow A3). In the fields
     * changed by a save, it stands for the password.
     */
    NEW_PASSWORD,

    /** New password typed a second time, which must be identical (alternative flow A3). */
    CONFIRM_PASSWORD
}
