package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result object returned by admin user management operations (Sprint 2 US-A03, UC 3.3).
 */
public class UserManagementResult {

    private final UserManagementStatus status;
    private final String message;
    private final User user;
    private final List<Deal> activeDeals;
    private final int affectedListingsCount;
    private final int affectedOffersCount;

    public UserManagementResult(UserManagementStatus status, String message, User user,
                                List<Deal> activeDeals, int affectedListingsCount, int affectedOffersCount) {
        this.status = status;
        this.message = message;
        this.user = user;
        this.activeDeals = activeDeals != null ? new ArrayList<>(activeDeals) : Collections.emptyList();
        this.affectedListingsCount = affectedListingsCount;
        this.affectedOffersCount = affectedOffersCount;
    }

    public static UserManagementResult success(String message, User user) {
        return new UserManagementResult(UserManagementStatus.SUCCESS, message, user, Collections.emptyList(), 0, 0);
    }

    public static UserManagementResult success(String message, User user, int affectedListingsCount, int affectedOffersCount) {
        return new UserManagementResult(UserManagementStatus.SUCCESS, message, user, Collections.emptyList(),
                affectedListingsCount, affectedOffersCount);
    }

    public static UserManagementResult failure(UserManagementStatus status, String message) {
        return new UserManagementResult(status, message, null, Collections.emptyList(), 0, 0);
    }

    public static UserManagementResult activeDealsExist(String message, List<Deal> activeDeals) {
        return new UserManagementResult(UserManagementStatus.ACTIVE_DEALS_EXIST, message, null,
                activeDeals, 0, 0);
    }

    public boolean isSuccess() {
        return status == UserManagementStatus.SUCCESS;
    }

    public UserManagementStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public User getUser() {
        return user;
    }

    public List<Deal> getActiveDeals() {
        return Collections.unmodifiableList(activeDeals);
    }

    public int getAffectedListingsCount() {
        return affectedListingsCount;
    }

    public int getAffectedOffersCount() {
        return affectedOffersCount;
    }

    @Override
    public String toString() {
        return "UserManagementResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", user=" + (user != null ? user.getUsername() : null) +
                ", activeDeals=" + activeDeals.size() +
                ", affectedListingsCount=" + affectedListingsCount +
                ", affectedOffersCount=" + affectedOffersCount +
                '}';
    }
}
