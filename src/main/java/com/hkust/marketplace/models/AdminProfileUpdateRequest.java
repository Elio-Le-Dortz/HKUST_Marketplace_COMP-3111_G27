package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Data transfer object encapsulating admin profile update requests (US-A04, Task 3.4).
 * Supports updating personal information (full name) and optional password changes
 * with current password verification and confirmation matching.
 */
public class AdminProfileUpdateRequest {

    private String username;
    private String fullName;
    private String currentPassword;
    private String newPassword;
    private String confirmPassword;

    /**
     * Default constructor.
     */
    public AdminProfileUpdateRequest() {
    }

    /**
     * Constructor for updating profile information without changing password.
     *
     * @param username username of the admin
     * @param fullName new full name of the admin
     */
    public AdminProfileUpdateRequest(String username, String fullName) {
        this.username = username;
        this.fullName = fullName;
    }

    /**
     * Constructor for updating profile information along with password credentials.
     *
     * @param username        username of the admin
     * @param fullName        new full name of the admin
     * @param currentPassword existing password for verification
     * @param newPassword     new replacement password
     * @param confirmPassword confirmation copy of the new password
     */
    public AdminProfileUpdateRequest(String username, String fullName,
                                    String currentPassword, String newPassword, String confirmPassword) {
        this.username = username;
        this.fullName = fullName;
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    /**
     * Determines whether this request includes an attempt to update the password.
     *
     * @return {@code true} if any password field is non-null and not empty
     */
    public boolean isPasswordChangeRequested() {
        return (currentPassword != null && !currentPassword.isEmpty())
                || (newPassword != null && !newPassword.isEmpty())
                || (confirmPassword != null && !confirmPassword.isEmpty());
    }

    /**
     * Gets the admin username.
     *
     * @return admin username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the admin username.
     *
     * @param username admin username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the full name.
     *
     * @return full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the full name.
     *
     * @param fullName full name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Gets the current password provided for verification.
     *
     * @return current password
     */
    public String getCurrentPassword() {
        return currentPassword;
    }

    /**
     * Sets the current password.
     *
     * @param currentPassword current password
     */
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    /**
     * Gets the new candidate password.
     *
     * @return new password
     */
    public String getNewPassword() {
        return newPassword;
    }

    /**
     * Sets the new candidate password.
     *
     * @param newPassword new password
     */
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    /**
     * Gets the confirmation password.
     *
     * @return confirm password
     */
    public String getConfirmPassword() {
        return confirmPassword;
    }

    /**
     * Sets the confirmation password.
     *
     * @param confirmPassword confirm password
     */
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdminProfileUpdateRequest that = (AdminProfileUpdateRequest) o;
        return Objects.equals(username, that.username) &&
                Objects.equals(fullName, that.fullName) &&
                Objects.equals(currentPassword, that.currentPassword) &&
                Objects.equals(newPassword, that.newPassword) &&
                Objects.equals(confirmPassword, that.confirmPassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, fullName, currentPassword, newPassword, confirmPassword);
    }

    @Override
    public String toString() {
        return "AdminProfileUpdateRequest{" +
                "username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", passwordChangeRequested=" + isPasswordChangeRequested() +
                '}';
    }
}
