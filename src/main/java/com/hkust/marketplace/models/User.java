package com.hkust.marketplace.models;

import java.time.LocalDateTime;

/**
 * Abstract domain model for User as defined in the UML Class Diagram.
 *
 * <p>Subclasses include {@link Buyer}, {@link Seller}, and {@link Admin}. Encapsulates common
 * identity attributes, password storage, account lifecycle status, and role-based permissions.</p>
 */
public abstract class User {

    /** Unique account username. */
    private String username;

    /** User's full display name. */
    private String fullName;

    /** Stored password hash (salted SHA-256 or PBKDF2-HMAC-SHA256). */
    private String password;

    /** Current lifecycle state (ACTIVE, DEACTIVATED, DELETED). */
    private AccountStatus accountStatus;

    /** System authorization role. */
    private UserRole role;

    /** Account registration timestamp. */
    private LocalDateTime createdAt;

    /**
     * Default constructor initializing timestamp and setting status to {@link AccountStatus#ACTIVE}.
     */
    public User() {
        this.createdAt = LocalDateTime.now();
        this.accountStatus = AccountStatus.ACTIVE;
    }

    /**
     * Constructs a user with username, full name, password, and role.
     *
     * @param username unique username
     * @param fullName display full name
     * @param password password or password hash
     * @param role     authorization role
     */
    public User(String username, String fullName, String password, UserRole role) {
        this();
        this.username = username;
        this.fullName = fullName;
        this.password = password;
        this.role = role;
    }

    /**
     * Gets the unique username.
     *
     * @return username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the unique username.
     *
     * @param username username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the full display name.
     *
     * @return full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the full display name.
     *
     * @param fullName full name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Gets the password hash string.
     *
     * @return password hash
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password hash string.
     *
     * @param password password hash
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Alias for {@link #getPassword()}.
     *
     * @return password hash string
     */
    public String getPasswordHash() {
        return password;
    }

    /**
     * Alias for {@link #setPassword(String)}.
     *
     * @param passwordHash password hash string
     */
    public void setPasswordHash(String passwordHash) {
        this.password = passwordHash;
    }

    /**
     * Gets the current account status.
     *
     * @return account status
     */
    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    /**
     * Sets the account status.
     *
     * @param accountStatus status constant
     */
    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    /**
     * Gets the authorization role.
     *
     * @return user role
     */
    public UserRole getRole() {
        return role;
    }

    /**
     * Sets the authorization role.
     *
     * @param role user role
     */
    public void setRole(UserRole role) {
        this.role = role;
    }

    /**
     * Gets the registration timestamp.
     *
     * @return creation date and time
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the registration timestamp.
     *
     * @param createdAt creation date and time
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Checks if the account is in active status.
     *
     * @return {@code true} if active, {@code false} otherwise
     */
    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    /**
     * Sets the active status flag.
     *
     * @param active whether the account should be active
     */
    public void setActive(boolean active) {
        this.accountStatus = active ? AccountStatus.ACTIVE : AccountStatus.DEACTIVATED;
    }

    /**
     * Checks if the account is soft-deleted.
     *
     * @return {@code true} if deleted, {@code false} otherwise
     */
    public boolean isDeleted() {
        return accountStatus == AccountStatus.DELETED;
    }

    /**
     * Returns the user-facing display name. For soft-deleted accounts, this returns
     * {@code "Deleted user"} to preserve historical deals, chats, and reviews without
     * revealing the deleted profile (Sprint 2 US-A03, UC 3.3 Subflow S3 Step 7).
     *
     * @return {@code "Deleted user"} if account is deleted, otherwise the user's full name
     */
    public String getDisplayName() {
        if (accountStatus == AccountStatus.DELETED) {
            return "Deleted user";
        }
        return fullName != null && !fullName.trim().isEmpty() ? fullName : username;
    }

    /**
     * Default getter for residence hall (overridden in {@link Buyer}).
     *
     * @return residence hall or {@code null}
     */
    public String getResidenceHall() {
        return null;
    }

    /**
     * Default setter for residence hall (overridden in {@link Buyer}).
     *
     * @param residenceHall residence hall name
     */
    public void setResidenceHall(String residenceHall) {
    }

    /**
     * Default getter for seller biography (overridden in {@link Seller}).
     *
     * @return biography text or {@code null}
     */
    public String getBio() {
        return null;
    }

    /**
     * Default setter for seller biography (overridden in {@link Seller}).
     *
     * @param bio biography text
     */
    public void setBio(String bio) {
    }

    /**
     * Default getter for preferred meetup (overridden in {@link Seller}).
     *
     * @return preferred meetup location name or {@code null}
     */
    public String getPreferredMeetup() {
        return null;
    }

    /**
     * Default setter for preferred meetup (overridden in {@link Seller}).
     *
     * @param preferredMeetup location display name
     */
    public void setPreferredMeetup(String preferredMeetup) {
    }

    /**
     * Default getter for average rating score (overridden in {@link Seller}).
     *
     * @return average rating score
     */
    public double getAverageRating() {
        return 0.0;
    }

    /**
     * Default setter for average rating score (overridden in {@link Seller}).
     *
     * @param averageRating average rating
     */
    public void setAverageRating(double averageRating) {
    }

    /**
     * Default getter for rating count (overridden in {@link Seller}).
     *
     * @return review count
     */
    public int getRatingCount() {
        return 0;
    }

    /**
     * Default setter for rating count (overridden in {@link Seller}).
     *
     * @param ratingCount review count
     */
    public void setRatingCount(int ratingCount) {
    }
}
