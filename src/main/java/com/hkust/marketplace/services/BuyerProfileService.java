package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ProfileUpdateRequest;
import com.hkust.marketplace.models.ProfileUpdateResult;
import com.hkust.marketplace.models.ProfileUpdateStatus;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.PasswordPolicy;
import com.hkust.marketplace.utils.PasswordUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Service encapsulating business rules for Buyer Profile management (US-B08 / UC-B08).
 * Decoupled from UI controllers for automated JUnit 5 and JaCoCo coverage.
 */
public class BuyerProfileService {

    private final DummyDataService dataService;

    public BuyerProfileService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Retrieves the buyer profile for the given username.
     *
     * @param username username to lookup
     * @return Buyer instance if found and active, null otherwise
     */
    public synchronized Buyer getProfile(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        User user = dataService.findUser(username.trim());
        if (user instanceof Buyer && user.isActive()) {
            return (Buyer) user;
        }
        return null;
    }

    /**
     * Updates a buyer profile given their username and update request.
     *
     * @param username username of the buyer
     * @param request  profile details and optional password update
     * @return ProfileUpdateResult indicating success or specific validation failure
     */
    public synchronized ProfileUpdateResult updateProfile(String username, ProfileUpdateRequest request) {
        if (username == null || username.trim().isEmpty()) {
            return ProfileUpdateResult.failure(ProfileUpdateStatus.USER_NOT_FOUND,
                    "Username is required.", Collections.emptySet());
        }

        User user = dataService.findUser(username.trim());
        if (user == null) {
            return ProfileUpdateResult.failure(ProfileUpdateStatus.USER_NOT_FOUND,
                    "User '" + username.trim() + "' does not exist.", Collections.emptySet());
        }

        if (!(user instanceof Buyer)) {
            return ProfileUpdateResult.failure(ProfileUpdateStatus.UNAUTHORIZED,
                    "Account is not a Buyer account.", Collections.emptySet());
        }

        if (!user.isActive()) {
            return ProfileUpdateResult.failure(ProfileUpdateStatus.UNAUTHORIZED,
                    "Buyer account is not active.", Collections.emptySet());
        }

        return updateProfile((Buyer) user, request);
    }

    /**
     * Applies profile updates to an active {@link Buyer} entity according to Use Case 1.7.
     *
     * @param buyer   target Buyer entity
     * @param request profile update request data
     * @return ProfileUpdateResult indicating success or specific failure
     */
    public synchronized ProfileUpdateResult updateProfile(Buyer buyer, ProfileUpdateRequest request) {
        if (buyer == null) {
            return ProfileUpdateResult.failure(ProfileUpdateStatus.USER_NOT_FOUND,
                    "Buyer cannot be null.", Collections.emptySet());
        }

        if (request == null) {
            return ProfileUpdateResult.failure(ProfileUpdateStatus.EMPTY_FULL_NAME,
                    "Profile update request cannot be null.", Set.of("fullName"));
        }

        // Alternative Flow A1: Empty Full Name
        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            return ProfileUpdateResult.failure(ProfileUpdateStatus.EMPTY_FULL_NAME,
                    "Full Name is required.", "fullName");
        }

        // Password change flow
        if (request.isPasswordChangeRequested()) {
            String currentPassword = request.getCurrentPassword();
            String newPassword = request.getNewPassword();
            String confirmPassword = request.getConfirmPassword();

            // Alternative Flow A2: Current password missing
            if (currentPassword == null || currentPassword.isEmpty()) {
                return ProfileUpdateResult.failure(ProfileUpdateStatus.CURRENT_PASSWORD_REQUIRED,
                        "Current password is required to change password.", "currentPassword");
            }

            // Alternative Flow A2: Wrong current password
            if (!PasswordUtil.verifyPassword(currentPassword, buyer.getPasswordHash())) {
                return ProfileUpdateResult.failure(ProfileUpdateStatus.WRONG_CURRENT_PASSWORD,
                        "Current password is incorrect.", "currentPassword");
            }

            // Alternative Flow A3: New password missing
            if (newPassword == null || newPassword.isEmpty()) {
                return ProfileUpdateResult.failure(ProfileUpdateStatus.NEW_PASSWORD_REQUIRED,
                        "New password cannot be empty.", "newPassword");
            }

            // Alternative Flow A3: Confirm password missing
            if (confirmPassword == null || confirmPassword.isEmpty()) {
                return ProfileUpdateResult.failure(ProfileUpdateStatus.CONFIRM_PASSWORD_REQUIRED,
                        "Please confirm your new password.", "confirmPassword");
            }

            // Alternative Flow A3: Confirmation mismatch
            if (!newPassword.equals(confirmPassword)) {
                return ProfileUpdateResult.failure(ProfileUpdateStatus.PASSWORD_CONFIRMATION_MISMATCH,
                        "Passwords do not match.", "confirmPassword");
            }

            // Alternative Flow A3: Password strength violation
            List<String> violations = PasswordPolicy.findViolations(newPassword);
            if (!violations.isEmpty()) {
                String message = "New password does not meet password rules: " + String.join(", ", violations) + ".";
                return ProfileUpdateResult.weakPassword(violations, message);
            }

            // Basic Flow step 7: Store new password in hashed form (PBKDF2)
            String newHashedPassword = PasswordHasher.hash(newPassword);
            buyer.setPasswordHash(newHashedPassword);
        }

        // Basic Flow step 7: Save full name and residence preference
        buyer.setFullName(request.getFullName().trim());
        String cleanResidence = (request.getResidence() != null && !request.getResidence().trim().isEmpty())
                ? request.getResidence().trim()
                : null;
        buyer.setResidence(cleanResidence);
        buyer.setResidenceHall(cleanResidence);

        // Basic Flow step 8: Confirm profile update
        return ProfileUpdateResult.success(buyer, "Profile updated successfully.");
    }
}
