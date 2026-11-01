package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain model for Seller as defined in the UML Class Diagram.
 */
public class Seller extends User {
    private String bio;
    private List<MeetupLocation> preferredMeetups = new ArrayList<>();
    private double averageRating;
    private int ratingCount;

    public Seller() {
        super();
        setRole(UserRole.SELLER);
    }

    public Seller(String username, String fullName, String password) {
        super(username, fullName, password, UserRole.SELLER);
    }

    public Seller(String username, String fullName, String password, String bio) {
        super(username, fullName, password, UserRole.SELLER);
        this.bio = bio;
    }

    @Override
    public String getBio() {
        return bio;
    }

    @Override
    public void setBio(String bio) {
        this.bio = bio;
    }

    public List<MeetupLocation> getPreferredMeetups() {
        return preferredMeetups;
    }

    public void setPreferredMeetups(List<MeetupLocation> preferredMeetups) {
        this.preferredMeetups = preferredMeetups != null ? preferredMeetups : new ArrayList<>();
    }

    @Override
    public String getPreferredMeetup() {
        if (preferredMeetups != null && !preferredMeetups.isEmpty()) {
            return preferredMeetups.get(0).getDisplayName();
        }
        return "";
    }

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

    @Override
    public double getAverageRating() {
        return averageRating;
    }

    @Override
    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    @Override
    public int getRatingCount() {
        return ratingCount;
    }

    @Override
    public void setRatingCount(int ratingCount) {
        this.ratingCount = ratingCount;
    }
}
