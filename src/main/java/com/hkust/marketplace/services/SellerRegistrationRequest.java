package com.hkust.marketplace.services;

import com.hkust.marketplace.models.MeetupLocation;

import java.util.List;

/**
 * Data entered on the seller registration form (use case 2.1, step 3), exactly as
 * typed. {@link SellerRegistrationService} trims, normalises and validates it.
 *
 * @param username         the requested username: required, unique across all roles,
 *                         compared ignoring case
 * @param fullName         the seller's full name: required
 * @param password         the plaintext password: required, must meet
 *                         {@link com.hkust.marketplace.utils.PasswordPolicy}
 * @param confirmPassword  the password typed a second time: must equal {@code password}
 * @param preferredMeetups the preferred meet-up locations: optional, may be
 *                         {@code null}, empty, or contain duplicates
 * @param bio              a short seller bio: optional, may be {@code null} or blank
 */
public record SellerRegistrationRequest(String username,
                                        String fullName,
                                        String password,
                                        String confirmPassword,
                                        List<MeetupLocation> preferredMeetups,
                                        String bio) {

    /**
     * Describes the request without the passwords, so a request can be logged
     * without leaking credentials.
     *
     * @return the username, full name, meet-up locations and bio, with both
     *         passwords masked
     */
    @Override
    public String toString() {
        return "SellerRegistrationRequest[username=" + username
                + ", fullName=" + fullName
                + ", password=***, confirmPassword=***"
                + ", preferredMeetups=" + preferredMeetups
                + ", bio=" + bio + "]";
    }
}
