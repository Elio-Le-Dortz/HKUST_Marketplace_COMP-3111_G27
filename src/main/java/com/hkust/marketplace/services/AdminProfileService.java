package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.AdminProfileUpdateRequest;
import com.hkust.marketplace.models.AdminProfileUpdateResult;
import com.hkust.marketplace.models.AdminProfileUpdateStatus;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordHasher;
import com.hkust.marketplace.utils.PasswordPolicy;
import com.hkust.marketplace.utils.PasswordUtil;
import com.hkust.marketplace.utils.SessionManager;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Service encapsulating business rules and data transformations for Admin Profile management
 * (US-A04 / Use Case 3.4 Manage Admin Profile).
 *
 * <p>Strictly decoupled from JavaFX controllers to guarantee headless testability,
 * deterministic JUnit 5 validation, and high JaCoCo branch coverage.</p>
 */
public class AdminProfileService {

    private final DummyDataService dataService;

    /**
     * Constructs a new AdminProfileService backed by the specified data repository.
     *
     * @param dataService shared data service; cannot be null
     */
    public AdminProfileService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Retrieves the admin profile for the given username.
     *
     * @param username username to lookup
     * @return active {@link Admin} entity if found, or {@code null} otherwise
     */
    public synchronized Admin getProfile(String username) {
        SessionManager.requireAdmin();
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        User user = dataService.findUser(username.trim());
        if (user instanceof Admin && user.isActive()) {
            return (Admin) user;
        }
        return null;
    }

    /**
     * Updates an administrator profile given their username and update request.
     *
     * @param username username of the administrator
     * @param request  profile details and optional password update
     * @return {@link AdminProfileUpdateResult} indicating success or specific validation failure
     */
    public synchronized AdminProfileUpdateResult updateProfile(String username, AdminProfileUpdateRequest request) {
        Admin sessionAdmin = SessionManager.requireAdmin();
        if (username == null || username.trim().isEmpty()) {
            return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.USER_NOT_FOUND,
                    "Admin username is required.", Collections.emptySet());
        }

        User user = dataService.findUser(username.trim());
        if (user == null) {
            return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.USER_NOT_FOUND,
                    "Admin account '" + username.trim() + "' does not exist.", Collections.emptySet());
        }

        if (!(user instanceof Admin)) {
            return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.UNAUTHORIZED,
                    "Account is not an Administrator account.", Collections.emptySet());
        }

        if (!user.isActive()) {
            return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.UNAUTHORIZED,
                    "Administrator account is deactivated.", Collections.emptySet());
        }

        if (!sessionAdmin.getUsername().equalsIgnoreCase(user.getUsername())) {
            return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.UNAUTHORIZED,
                    "Cannot update profile of another administrator.", Collections.emptySet());
        }

        return updateProfile((Admin) user, request);
    }

    /**
     * Applies profile updates to an active {@link Admin} entity in accordance with Use Case 3.4.
     *
     * @param admin   target Admin entity
     * @param request profile update request data
     * @return {@link AdminProfileUpdateResult} indicating success or specific failure
     */
    public synchronized AdminProfileUpdateResult updateProfile(Admin admin, AdminProfileUpdateRequest request) {
        Admin sessionAdmin = SessionManager.requireAdmin();
        if (admin == null) {
            return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.USER_NOT_FOUND,
                    "Admin cannot be null.", Collections.emptySet());
        }

        if (!sessionAdmin.getUsername().equalsIgnoreCase(admin.getUsername())) {
            return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.UNAUTHORIZED,
                    "Cannot update profile of another administrator.", Collections.emptySet());
        }

        if (request == null) {
            return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.EMPTY_FULL_NAME,
                    "Profile update request cannot be null.", Set.of("fullName"));
        }

        // Alternative Flow A1: Empty Full Name
        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.EMPTY_FULL_NAME,
                    "Full name is required.", "fullName");
        }

        // Alternative Flow A2 / A3: Optional password change flow
        if (request.isPasswordChangeRequested()) {
            String currentPassword = request.getCurrentPassword();
            String newPassword = request.getNewPassword();
            String confirmPassword = request.getConfirmPassword();

            // Alternative Flow A2: Current password missing
            if (currentPassword == null || currentPassword.isEmpty()) {
                return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.CURRENT_PASSWORD_REQUIRED,
                    "Current password is required to change password.", "currentPassword");
            }

            // Alternative Flow A2: Wrong current password
            if (!PasswordUtil.verifyPassword(currentPassword, admin.getPasswordHash())) {
                return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.WRONG_CURRENT_PASSWORD,
                        "Current password is incorrect.", "currentPassword");
            }

            // Alternative Flow A3: New password missing
            if (newPassword == null || newPassword.isEmpty()) {
                return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.NEW_PASSWORD_REQUIRED,
                        "New password cannot be empty.", "newPassword");
            }

            // Alternative Flow A3: Confirm password missing
            if (confirmPassword == null || confirmPassword.isEmpty()) {
                return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.CONFIRM_PASSWORD_REQUIRED,
                        "Please confirm your new password.", "confirmPassword");
            }

            // Alternative Flow A3: Confirmation mismatch
            if (!newPassword.equals(confirmPassword)) {
                return AdminProfileUpdateResult.failure(AdminProfileUpdateStatus.PASSWORD_CONFIRMATION_MISMATCH,
                        "Passwords do not match.", "confirmPassword");
            }

            // Alternative Flow A3: Password strength violation (Special Requirement)
            List<String> violations = PasswordPolicy.findViolations(newPassword);
            if (!violations.isEmpty()) {
                String message = "New password does not meet password rules: " + String.join(", ", violations) + ".";
                return AdminProfileUpdateResult.weakPassword(violations, message);
            }

            // Basic Flow step 7: Store new password in hashed form (PBKDF2)
            String newHashedPassword = PasswordHasher.hash(newPassword);
            admin.setPasswordHash(newHashedPassword);
        }

        // Basic Flow step 7: Save changes (full name and password) to data store & database
        admin.setFullName(request.getFullName().trim());
        dataService.updateUser(admin);

        // Synchronize active session user if currently logged in
        User sessionUser = SessionManager.getCurrentUser();
        if (sessionUser != null && sessionUser.getUsername() != null &&
                sessionUser.getUsername().equalsIgnoreCase(admin.getUsername())) {
            sessionUser.setFullName(admin.getFullName());
            sessionUser.setPasswordHash(admin.getPasswordHash());
        }

        // Basic Flow step 8: Confirm update success
        return AdminProfileUpdateResult.success(admin, "Profile updated successfully.");
    }
}
