package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain model representing a Seller within the marketplace, as defined in the UML Class Diagram.
 *
 * <p>Sellers can post listings, manage incoming offers and borrow requests, view customer reviews
 * and ratings, manage their profile details, and track performance statistics.</p>
 */
public class Seller extends User {

    /** Short biography or description of the seller. */
    private String bio;

    /** Preferred campus meet-up locations for handing over or returning items. */
    private List<MeetupLocation> preferredMeetups = new ArrayList<>();

    /** Cumulative average rating across all non-hidden reviews. */
    private double averageRating;

    /** Total count of non-hidden reviews received by this seller. */
    private int ratingCount;

    /**
     * Default constructor initializing role to {@link UserRole#SELLER}.
     */
    public Seller() {
        super();
        setRole(UserRole.SELLER);
    }

    /**
     * Constructs a seller with username, full name, and password.
     *
     * @param username unique username
     * @param fullName display full name
     * @param password account password or password hash
     */
    public Seller(String username, String fullName, String password) {
        super(username, fullName, password, UserRole.SELLER);
    }

    /**
     * Constructs a seller with username, full name, password, and biography.
     *
     * @param username unique username
     * @param fullName display full name
     * @param password account password or password hash
     * @param bio      seller biography
     */
    public Seller(String username, String fullName, String password, String bio) {
        super(username, fullName, password, UserRole.SELLER);
        this.bio = bio;
    }

    /**
     * Gets the seller's biography.
     *
     * @return biography text, or {@code null} if unset
     */
    @Override
    public String getBio() {
        return bio;
    }

    /**
     * Sets the seller's biography.
     *
     * @param bio biography text
     */
    @Override
    public void setBio(String bio) {
        this.bio = bio;
    }

    /**
     * Gets the list of preferred campus meet-up locations.
     *
     * @return list of preferred locations
     */
    public List<MeetupLocation> getPreferredMeetups() {
        return preferredMeetups;
    }

    /**
     * Sets the list of preferred campus meet-up locations.
     *
     * @param preferredMeetups list of locations
     */
    public void setPreferredMeetups(List<MeetupLocation> preferredMeetups) {
        this.preferredMeetups = preferredMeetups != null ? preferredMeetups : new ArrayList<>();
    }

    /**
     * Gets the display name of the primary preferred meet-up location.
     *
     * @return location display name, or empty string if none set
     */
    @Override
    public String getPreferredMeetup() {
        if (preferredMeetups != null && !preferredMeetups.isEmpty()) {
            return preferredMeetups.get(0).getDisplayName();
        }
        return "";
    }

    /**
     * Sets the primary preferred meet-up location from a display string.
     *
     * @param preferredMeetup location display name
     */
    @Override
    public void setPreferredMeetup(String preferredMeetup) {
        if (preferredMeetup != null && !preferredMeetup.isEmpty()) {
            if (this.preferredMeetups == null) {
                this.preferredMeetups = new ArrayList<>();
            }
            this.preferredMeetups.clear();
            this.preferredMeetups.add(MeetupLocation.fromString(preferredMeetup));
        }
    }

    /**
     * Gets the seller's average rating.
     *
     * @return average star rating
     */
    @Override
    public double getAverageRating() {
        return averageRating;
    }

    /**
     * Sets the seller's average rating.
     *
     * @param averageRating average star rating
     */
    @Override
    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    /**
     * Gets the number of reviews received by this seller.
     *
     * @return review count
     */
    @Override
    public int getRatingCount() {
        return ratingCount;
    }

    /**
     * Sets the number of reviews received by this seller.
     *
     * @param ratingCount review count
     */
    @Override
    public void setRatingCount(int ratingCount) {
        this.ratingCount = ratingCount;
    }
}
