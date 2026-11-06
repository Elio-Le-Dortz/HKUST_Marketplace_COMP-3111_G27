package com.hkust.marketplace.models;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Values entered on the profile form of use case 2.6 "Manage Seller Profile" (US-S06), exactly
 * as typed: {@link com.hkust.marketplace.services.SellerProfileService} trims, normalises and
 * checks them.
 *
 * <p>The password is changed only when at least one of the three password fields is filled in
 * (basic flow, step 4); leaving them all empty keeps the current password.</p>
 *
 * @param fullName         the full name ({@code User.fullName}): required
 * @param bio              the bio ({@code Seller.bio}): optional, {@code null} or blank for none
 * @param preferredMeetups the preferred meet-up locations ({@code Seller.preferredMeetups}):
 *                         optional, {@code null} for none; {@code null} elements are left out
 * @param currentPassword  the current password, required to change the password (alternative flow A2)
 * @param newPassword      the new password, which must follow the password rules (alternative flow A3)
 * @param confirmPassword  the new password typed a second time (alternative flow A3)
 */
public record SellerProfileUpdateRequest(String fullName,
                                         String bio,
                                         List<MeetupLocation> preferredMeetups,
                                         String currentPassword,
                                         String newPassword,
                                         String confirmPassword) {

    /**
     * Creates the request. The meet-up locations are copied without their {@code null} elements,
     * so later changes to the given list do not affect the request, and the request cannot be
     * changed.
     *
     * @param fullName         the full name
     * @param bio              the bio
     * @param preferredMeetups the preferred meet-up locations, may be {@code null}
     * @param currentPassword  the current password
     * @param newPassword      the new password
     * @param confirmPassword  the new password typed a second time
     */
    public SellerProfileUpdateRequest {
        preferredMeetups = preferredMeetups == null
                ? List.of()
                : Collections.unmodifiableList(preferredMeetups.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    /**
     * Creates a request that changes the full name, the bio and the meet-up locations, and keeps
     * the current password.
     *
     * @param fullName         the full name
     * @param bio              the bio, may be {@code null}
     * @param preferredMeetups the preferred meet-up locations, may be {@code null}
     * @return the request, with the three password fields empty
     */
    public static SellerProfileUpdateRequest withoutPasswordChange(String fullName, String bio,
                                                                   List<MeetupLocation> preferredMeetups) {
        return new SellerProfileUpdateRequest(fullName, bio, preferredMeetups, "", "", "");
    }

    /**
     * Tells whether the Seller wants to change the password (basic flow, step 4): at least one of
     * the three password fields is filled in. Spaces count, since a password is used exactly as
     * typed.
     *
     * @return {@code true} if the current, new or confirm password is not empty
     */
    public boolean isPasswordChangeRequested() {
        return isFilled(currentPassword) || isFilled(newPassword) || isFilled(confirmPassword);
    }

    /**
     * Describes the request without the passwords, so that a request can be logged without
     * leaking credentials.
     *
     * @return the full name, bio and meet-up locations, with the three passwords masked
     */
    @Override
    public String toString() {
        return "SellerProfileUpdateRequest[fullName=" + fullName
                + ", bio=" + bio
                + ", preferredMeetups=" + preferredMeetups
                + ", passwordChangeRequested=" + isPasswordChangeRequested()
                + ", currentPassword=***, newPassword=***, confirmPassword=***]";
    }

    /**
     * Tells whether a password field is filled in.
     *
     * @param password the typed password, may be {@code null}
     * @return {@code true} if it is not {@code null} nor empty
     */
    private static boolean isFilled(String password) {
        return password != null && !password.isEmpty();
    }
}
