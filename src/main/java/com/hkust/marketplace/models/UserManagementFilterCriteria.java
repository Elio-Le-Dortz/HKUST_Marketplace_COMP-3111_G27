package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Filter and search criteria for the admin user management table (Sprint 2 US-A03, UC 3.3 Basic Flow).
 */
public class UserManagementFilterCriteria {

    private String searchKeyword;
    private UserRole role;
    private AccountStatus status;

    public UserManagementFilterCriteria() {
    }

    public UserManagementFilterCriteria(String searchKeyword, UserRole role, AccountStatus status) {
        this.searchKeyword = searchKeyword;
        this.role = role;
        this.status = status;
    }

    public static UserManagementFilterCriteria all() {
        return new UserManagementFilterCriteria(null, null, null);
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public UserManagementFilterCriteria setSearchKeyword(String searchKeyword) {
        this.searchKeyword = searchKeyword;
        return this;
    }

    public UserRole getRole() {
        return role;
    }

    public UserManagementFilterCriteria setRole(UserRole role) {
        this.role = role;
        return this;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public UserManagementFilterCriteria setStatus(AccountStatus status) {
        this.status = status;
        return this;
    }

    /**
     * Determines whether a given user matches all non-null filter criteria.
     *
     * @param user the candidate user
     * @return {@code true} if user matches criteria, {@code false} otherwise
     */
    public boolean matches(User user) {
        if (user == null) {
            return false;
        }

        // Role filter
        if (role != null && user.getRole() != role) {
            return false;
        }

        // Status filter
        if (status != null && user.getAccountStatus() != status) {
            return false;
        }

        // Keyword filter: matches username or full name (case-insensitive substring)
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            String kw = searchKeyword.trim().toLowerCase();
            String username = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
            String fullName = user.getFullName() != null ? user.getFullName().toLowerCase() : "";
            if (!username.contains(kw) && !fullName.contains(kw)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserManagementFilterCriteria that = (UserManagementFilterCriteria) o;
        return Objects.equals(searchKeyword, that.searchKeyword) &&
                role == that.role &&
                status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchKeyword, role, status);
    }
}
