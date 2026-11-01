package com.hkust.marketplace.models;

/**
 * Domain model for Buyer as defined in the UML Class Diagram.
 */
public class Buyer extends User {
    private String residence; // Residence hall / off-campus

    public Buyer() {
        super();
        setRole(UserRole.BUYER);
    }

    public Buyer(String username, String fullName, String password) {
        super(username, fullName, password, UserRole.BUYER);
    }

    public Buyer(String username, String fullName, String password, String residence) {
        super(username, fullName, password, UserRole.BUYER);
        this.residence = residence;
    }

    public String getResidence() {
        return residence;
    }

    public void setResidence(String residence) {
        this.residence = residence;
    }

    @Override
    public String getResidenceHall() {
        return residence;
    }

    @Override
    public void setResidenceHall(String residenceHall) {
        this.residence = residenceHall;
    }
}
