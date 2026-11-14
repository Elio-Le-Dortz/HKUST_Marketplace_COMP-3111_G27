package com.hkust.marketplace.models;

/**
 * Domain model representing a Platform Administrator, as defined in the UML Class Diagram.
 *
 * <p>Administrators have privileges to review and approve/reject listings, deactivate or soft-delete
 * user accounts, take down listings, moderate reviews, handle reports, and audit all marketplace deals.</p>
 */
public class Admin extends User {

    /**
     * Default constructor initializing role to {@link UserRole#ADMIN}.
     */
    public Admin() {
        super();
        setRole(UserRole.ADMIN);
    }

    /**
     * Constructs an administrator with username, full name, and password.
     *
     * @param username unique administrator username
     * @param fullName display name
     * @param password account password or password hash
     */
    public Admin(String username, String fullName, String password) {
        super(username, fullName, password, UserRole.ADMIN);
    }
}
