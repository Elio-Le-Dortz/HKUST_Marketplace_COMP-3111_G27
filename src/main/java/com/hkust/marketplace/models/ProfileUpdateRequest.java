package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Data transfer object encapsulating buyer profile update requests (US-B08).
 * Supports updating personal information (full name, residence hall preference)
 * and optional password change with current password verification.
 */
public class ProfileUpdateRequest {

    private String username;
    private String fullName;
    private String residence;
    private String currentPassword;
    private String newPassword;
    private String confirmPassword;

    public ProfileUpdateRequest() {
    }

    public ProfileUpdateRequest(String username, String fullName, String residence) {
        this.username = username;
        this.fullName = fullName;
        this.residence = residence;
    }

    public ProfileUpdateRequest(String username, String fullName, String residence,
                                String currentPassword, String newPassword, String confirmPassword) {
        this.username = username;
        this.fullName = fullName;
        this.residence = residence;
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    /**
     * Determines whether this request includes an attempt to update the password.
     *
     * @return true if any password field is non-null and not empty
     */
    public boolean isPasswordChangeRequested() {
        return (currentPassword != null && !currentPassword.isEmpty())
                || (newPassword != null && !newPassword.isEmpty())
                || (confirmPassword != null && !confirmPassword.isEmpty());
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getResidence() {
        return residence;
    }

    public void setResidence(String residence) {
        this.residence = residence;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileUpdateRequest that = (ProfileUpdateRequest) o;
        return Objects.equals(username, that.username) &&
                Objects.equals(fullName, that.fullName) &&
                Objects.equals(residence, that.residence) &&
                Objects.equals(currentPassword, that.currentPassword) &&
                Objects.equals(newPassword, that.newPassword) &&
                Objects.equals(confirmPassword, that.confirmPassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, fullName, residence, currentPassword, newPassword, confirmPassword);
    }

    @Override
    public String toString() {
        return "ProfileUpdateRequest{" +
                "username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", residence='" + residence + '\'' +
                ", passwordChangeRequested=" + isPasswordChangeRequested() +
                '}';
    }
}
