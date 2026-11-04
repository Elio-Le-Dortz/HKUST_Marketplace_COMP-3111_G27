package com.hkust.marketplace.models;

import java.util.*;

/**
 * Result object returned by {@link com.hkust.marketplace.services.BuyerProfileService}
 * indicating the outcome of a profile update operation (US-B08).
 */
public class ProfileUpdateResult {

    private final boolean success;
    private final ProfileUpdateStatus status;
    private final String message;
    private final Set<String> affectedFields;
    private final List<String> passwordViolations;
    private final Buyer updatedBuyer;

    public ProfileUpdateResult(boolean success, ProfileUpdateStatus status, String message,
                               Set<String> affectedFields, List<String> passwordViolations,
                               Buyer updatedBuyer) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.affectedFields = affectedFields != null ? Collections.unmodifiableSet(new LinkedHashSet<>(affectedFields)) : Collections.emptySet();
        this.passwordViolations = passwordViolations != null ? Collections.unmodifiableList(new ArrayList<>(passwordViolations)) : Collections.emptyList();
        this.updatedBuyer = updatedBuyer;
    }

    public static ProfileUpdateResult success(Buyer buyer, String message) {
        return new ProfileUpdateResult(true, ProfileUpdateStatus.SUCCESS, message,
                Collections.emptySet(), Collections.emptyList(), buyer);
    }

    public static ProfileUpdateResult failure(ProfileUpdateStatus status, String message, Set<String> affectedFields) {
        return new ProfileUpdateResult(false, status, message, affectedFields, Collections.emptyList(), null);
    }

    public static ProfileUpdateResult failure(ProfileUpdateStatus status, String message, String affectedField) {
        return failure(status, message, affectedField != null ? Set.of(affectedField) : Collections.emptySet());
    }

    public static ProfileUpdateResult weakPassword(List<String> violations, String message) {
        return new ProfileUpdateResult(false, ProfileUpdateStatus.WEAK_NEW_PASSWORD, message,
                Set.of("newPassword"), violations, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public ProfileUpdateStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Set<String> getAffectedFields() {
        return affectedFields;
    }

    public List<String> getPasswordViolations() {
        return passwordViolations;
    }

    public Buyer getUpdatedBuyer() {
        return updatedBuyer;
    }

    @Override
    public String toString() {
        return "ProfileUpdateResult{" +
                "success=" + success +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", affectedFields=" + affectedFields +
                ", passwordViolations=" + passwordViolations +
                ", hasBuyer=" + (updatedBuyer != null) +
                '}';
    }
}
