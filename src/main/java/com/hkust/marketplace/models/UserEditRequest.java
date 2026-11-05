package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates data required to edit a user account profile by an administrator (Sprint 2 US-A03, UC 3.3 Subflow S1).
 */
public class UserEditRequest {

    private final String targetUsername;
    private final String adminUsername;
    private final String newFullName;
    private final String residence;
    private final String bio;
    private final List<MeetupLocation> preferredMeetups;

    public UserEditRequest(String targetUsername, String adminUsername, String newFullName,
                           String residence, String bio, List<MeetupLocation> preferredMeetups) {
        this.targetUsername = targetUsername;
        this.adminUsername = adminUsername;
        this.newFullName = newFullName;
        this.residence = residence;
        this.bio = bio;
        this.preferredMeetups = preferredMeetups != null ? new ArrayList<>(preferredMeetups) : new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getNewFullName() {
        return newFullName;
    }

    public String getResidence() {
        return residence;
    }

    public String getBio() {
        return bio;
    }

    public List<MeetupLocation> getPreferredMeetups() {
        return new ArrayList<>(preferredMeetups);
    }

    public static class Builder {
        private String targetUsername;
        private String adminUsername;
        private String newFullName;
        private String residence;
        private String bio;
        private List<MeetupLocation> preferredMeetups = new ArrayList<>();

        public Builder targetUsername(String targetUsername) {
            this.targetUsername = targetUsername;
            return this;
        }

        public Builder adminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
            return this;
        }

        public Builder newFullName(String newFullName) {
            this.newFullName = newFullName;
            return this;
        }

        public Builder residence(String residence) {
            this.residence = residence;
            return this;
        }

        public Builder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public Builder preferredMeetups(List<MeetupLocation> preferredMeetups) {
            if (preferredMeetups != null) {
                this.preferredMeetups = new ArrayList<>(preferredMeetups);
            }
            return this;
        }

        public Builder preferredMeetup(String meetupStr) {
            if (meetupStr != null && !meetupStr.trim().isEmpty()) {
                this.preferredMeetups = List.of(MeetupLocation.fromString(meetupStr));
            }
            return this;
        }

        public UserEditRequest build() {
            return new UserEditRequest(targetUsername, adminUsername, newFullName, residence, bio, preferredMeetups);
        }
    }
}
