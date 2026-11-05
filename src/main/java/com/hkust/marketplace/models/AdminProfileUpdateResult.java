package com.hkust.marketplace.models;

import java.util.*;

/**
 * Result object returned by {@link com.hkust.marketplace.services.AdminProfileService}
 * indicating the outcome of an admin profile update operation (US-A04, Task 3.4).
 */
public class AdminProfileUpdateResult {

    private final boolean success;
    private final AdminProfileUpdateStatus status;
    private final String message;
    private final Set<String> affectedFields;
    private final List<String> passwordViolations;
    private final Admin updatedAdmin;

    /**
     * Constructs a new AdminProfileUpdateResult with all attributes.
     *
     * @param success            whether the update operation succeeded
     * @param status             status enumeration code
     * @param message            descriptive feedback message
     * @param affectedFields     set of field identifiers that triggered validation errors
     * @param passwordViolations list of specific password policy rules that were broken
     * @param updatedAdmin       the updated Admin entity if successful, or {@code null}
     */
    public AdminProfileUpdateResult(boolean success, AdminProfileUpdateStatus status, String message,
                                    Set<String> affectedFields, List<String> passwordViolations,
                                    Admin updatedAdmin) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.affectedFields = affectedFields != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(affectedFields))
                : Collections.emptySet();
        this.passwordViolations = passwordViolations != null
                ? Collections.unmodifiableList(new ArrayList<>(passwordViolations))
                : Collections.emptyList();
        this.updatedAdmin = updatedAdmin;
    }

    /**
     * Creates a successful result holding the updated admin entity.
     *
     * @param admin   the updated Admin entity
     * @param message confirmation message
     * @return a successful AdminProfileUpdateResult instance
     */
    public static AdminProfileUpdateResult success(Admin admin, String message) {
        return new AdminProfileUpdateResult(true, AdminProfileUpdateStatus.SUCCESS, message,
                Collections.emptySet(), Collections.emptyList(), admin);
    }

    /**
     * Creates a failure result with multiple affected fields.
     *
     * @param status         error status code
     * @param message        descriptive error message
     * @param affectedFields set of fields associated with this failure
     * @return a failure AdminProfileUpdateResult instance
     */
    public static AdminProfileUpdateResult failure(AdminProfileUpdateStatus status, String message,
                                                   Set<String> affectedFields) {
        return new AdminProfileUpdateResult(false, status, message, affectedFields,
                Collections.emptyList(), null);
    }

    /**
     * Creates a failure result with a single affected field.
     *
     * @param status        error status code
     * @param message       descriptive error message
     * @param affectedField field name associated with this failure
     * @return a failure AdminProfileUpdateResult instance
     */
    public static AdminProfileUpdateResult failure(AdminProfileUpdateStatus status, String message,
                                                   String affectedField) {
        return failure(status, message, affectedField != null ? Set.of(affectedField) : Collections.emptySet());
    }

    /**
     * Creates a failure result representing a password strength policy violation.
     *
     * @param violations list of broken password policy descriptions
     * @param message    overall error message
     * @return an AdminProfileUpdateResult instance with {@link AdminProfileUpdateStatus#WEAK_NEW_PASSWORD}
     */
    public static AdminProfileUpdateResult weakPassword(List<String> violations, String message) {
        return new AdminProfileUpdateResult(false, AdminProfileUpdateStatus.WEAK_NEW_PASSWORD, message,
                Set.of("newPassword"), violations, null);
    }

    /**
     * Returns whether the update operation succeeded.
     *
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the outcome status.
     *
     * @return status code
     */
    public AdminProfileUpdateStatus getStatus() {
        return status;
    }

    /**
     * Returns the user-facing message.
     *
     * @return feedback message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the set of field names that failed validation.
     *
     * @return unmodifiable set of affected field names
     */
    public Set<String> getAffectedFields() {
        return affectedFields;
    }

    /**
     * Returns the list of password policy violations if the password was too weak.
     *
     * @return unmodifiable list of policy violation strings
     */
    public List<String> getPasswordViolations() {
        return passwordViolations;
    }

    /**
     * Returns the updated admin entity if the operation succeeded.
     *
     * @return updated Admin instance, or {@code null} if failed
     */
    public Admin getUpdatedAdmin() {
        return updatedAdmin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdminProfileUpdateResult that = (AdminProfileUpdateResult) o;
        return success == that.success &&
                status == that.status &&
                Objects.equals(message, that.message) &&
                Objects.equals(affectedFields, that.affectedFields) &&
                Objects.equals(passwordViolations, that.passwordViolations) &&
                Objects.equals(updatedAdmin, that.updatedAdmin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, status, message, affectedFields, passwordViolations, updatedAdmin);
    }

    @Override
    public String toString() {
        return "AdminProfileUpdateResult{" +
                "success=" + success +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", affectedFields=" + affectedFields +
                ", passwordViolations=" + passwordViolations +
                ", hasAdmin=" + (updatedAdmin != null) +
                '}';
    }
}
