package com.hkust.marketplace.models;

/**
 * Domain model representing a Buyer within the marketplace, as defined in the UML Class Diagram.
 *
 * <p>Buyers can explore listings, filter and search items, submit offers and borrow requests,
 * atomically claim giveaways, chat with sellers, manage active deals, and review sellers.</p>
 */
public class Buyer extends User {

    /** Residence hall or off-campus housing description. */
    private String residence;

    /**
     * Default constructor initializing role to {@link UserRole#BUYER}.
     */
    public Buyer() {
        super();
        setRole(UserRole.BUYER);
    }

    /**
     * Constructs a buyer with username, full name, and password.
     *
     * @param username unique username
     * @param fullName display full name
     * @param password account password or password hash
     */
    public Buyer(String username, String fullName, String password) {
        super(username, fullName, password, UserRole.BUYER);
    }

    /**
     * Constructs a buyer with username, full name, password, and residence.
     *
     * @param username  unique username
     * @param fullName  display full name
     * @param password  account password or password hash
     * @param residence student residential hall
     */
    public Buyer(String username, String fullName, String password, String residence) {
        super(username, fullName, password, UserRole.BUYER);
        this.residence = residence;
    }

    /**
     * Gets the buyer's residence hall.
     *
     * @return residence hall name
     */
    public String getResidence() {
        return residence;
    }

    /**
     * Sets the buyer's residence hall.
     *
     * @param residence residence hall name
     */
    public void setResidence(String residence) {
        this.residence = residence;
    }

    /**
     * Gets the residence hall (User polymorphism).
     *
     * @return residence hall name
     */
    @Override
    public String getResidenceHall() {
        return residence;
    }

    /**
     * Sets the residence hall (User polymorphism).
     *
     * @param residenceHall residence hall name
     */
    @Override
    public void setResidenceHall(String residenceHall) {
        this.residence = residenceHall;
    }
}
