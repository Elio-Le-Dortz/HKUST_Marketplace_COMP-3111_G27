package com.hkust.marketplace.models;

/**
 * Domain model for Admin as defined in the UML Class Diagram.
 */
public class Admin extends User {

    public Admin() {
        super();
        setRole(UserRole.ADMIN);
    }

    public Admin(String username, String fullName, String password) {
        super(username, fullName, password, UserRole.ADMIN);
    }
}
