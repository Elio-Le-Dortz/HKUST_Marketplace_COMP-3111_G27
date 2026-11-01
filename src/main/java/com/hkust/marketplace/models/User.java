package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Abstract domain model for User as defined in the UML Class Diagram.
 * Subclasses: Buyer, Seller, Admin.
 */
public abstract class User {
    private String username;
    private String fullName;
    private String password; // In production: hashed credential
    private AccountStatus accountStatus;
    private UserRole role;
    private LocalDateTime createdAt;

    public User() {
        this.createdAt = LocalDateTime.now();
        this.accountStatus = AccountStatus.ACTIVE;
    }

    public User(String username, String fullName, String password, UserRole role) {
        this();
        this.username = username;
        this.fullName = fullName;
        this.password = password;
        this.role = role;
    }

    // Common Getters & Setters
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // Alias for passwordHash
    public String getPasswordHash() {
        return password;
    }

    public void setPasswordHash(String passwordHash) {
        this.password = passwordHash;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Helper status methods
    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public void setActive(boolean active) {
        this.accountStatus = active ? AccountStatus.ACTIVE : AccountStatus.DEACTIVATED;
    }

    // Default implementations for subclass-specific properties
    // to provide backward compatibility with existing code
    public String getResidenceHall() {
        return null;
    }

    public void setResidenceHall(String residenceHall) {
        // default no-op
    }

    public String getBio() {
        return null;
    }

    public void setBio(String bio) {
        // default no-op
    }

    public String getPreferredMeetup() {
        return null;
    }

    public void setPreferredMeetup(String preferredMeetup) {
        // default no-op
    }

    public double getAverageRating() {
        return 0.0;
    }

    public void setAverageRating(double averageRating) {
        // default no-op
    }

    public int getRatingCount() {
        return 0;
    }

    public void setRatingCount(int ratingCount) {
        // default no-op
    }
}
