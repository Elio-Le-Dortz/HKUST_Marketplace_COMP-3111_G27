package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;

/**
 * Simple session holder for the currently logged-in user.
 * TODO: Replace with proper JWT / session token handling when backend is added.
 */
public class SessionManager {
    /** The logged-in user, or {@code null} when nobody is logged in. */
    private static User currentUser;

    /**
     * Starts a session for the given user, replacing any previous session.
     *
     * @param user the authenticated user
     */
    public static void login(User user) {
        currentUser = user;
    }

    /**
     * Ends the current session, if any.
     */
    public static void logout() {
        currentUser = null;
    }

    /**
     * Returns the logged-in user.
     *
     * @return the user of the current session, or {@code null} if nobody is logged in
     */
    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Tells whether a user is logged in.
     *
     * @return {@code true} if a session is active
     */
    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Tells whether the logged-in user has the Buyer role.
     *
     * @return {@code true} if a Buyer is logged in
     */
    public static boolean isBuyer() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.BUYER;
    }

    /**
     * Tells whether the logged-in user has the Seller role.
     *
     * @return {@code true} if a Seller is logged in
     */
    public static boolean isSeller() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.SELLER;
    }

    /**
     * Tells whether the logged-in user has the Admin role.
     *
     * @return {@code true} if an Admin is logged in
     */
    public static boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.ADMIN;
    }

    /**
     * Ends the active session for a specific user if that user is currently logged in.
     * Used when an administrator deactivates or deletes an account (Sprint 2 US-A03, UC 3.3).
     *
     * @param username the username of the user whose session should end
     */
    public static void endSessionForUser(String username) {
        if (currentUser != null && username != null && username.trim().equalsIgnoreCase(currentUser.getUsername())) {
            currentUser = null;
        }
    }

    /**
     * Returns the currently authenticated user cast to Buyer if logged in as a Buyer.
     *
     * @return Buyer instance or null if not logged in or not a Buyer
     */
    public static com.hkust.marketplace.models.Buyer getCurrentBuyer() {
        if (currentUser instanceof com.hkust.marketplace.models.Buyer) {
            return (com.hkust.marketplace.models.Buyer) currentUser;
        }
        return null;
    }

    /**
     * Enforces that an active Buyer session is present.
     * Fulfills the special requirement of use case 1.2 that every Buyer-only function
     * checks the Buyer session on the server/service side.
     *
     * @throws SecurityException if not logged in, not a Buyer, or account is not active
     */
    public static void requireBuyer() {
        if (!isLoggedIn()) {
            throw new SecurityException("Unauthorized: No user is currently logged in.");
        }
        if (!isBuyer()) {
            throw new SecurityException("Unauthorized: Access requires a Buyer account.");
        }
        if (!currentUser.isActive()) {
            throw new SecurityException("Unauthorized: Buyer account is not active.");
        }
    }

    /**
     * Returns the currently authenticated user cast to Seller if logged in as a Seller.
     *
     * @return Seller instance or null if not logged in or not a Seller
     */
    public static com.hkust.marketplace.models.Seller getCurrentSeller() {
        if (currentUser instanceof com.hkust.marketplace.models.Seller) {
            return (com.hkust.marketplace.models.Seller) currentUser;
        }
        return null;
    }

    /**
     * Enforces that an active Seller session is present (US-S02).
     * Fulfills the special requirement of use case 2.2 that every Seller-only function
     * checks the Seller session on the service side, so a buyer or admin session cannot
     * call it even without the user interface. Because the session holds the stored
     * account object, a seller deactivated or deleted by the admin after logging in is
     * refused as well.
     *
     * @return the active Seller of the current session
     * @throws SecurityException if not logged in, not a Seller, or the account is not active
     */
    public static com.hkust.marketplace.models.Seller requireSeller() {
        if (!isLoggedIn()) {
            throw new SecurityException("Unauthorized: No user is currently logged in.");
        }
        com.hkust.marketplace.models.Seller seller = getCurrentSeller();
        if (!isSeller() || seller == null) {
            throw new SecurityException("Unauthorized: Access requires a Seller account.");
        }
        if (!seller.isActive()) {
            throw new SecurityException("Unauthorized: Seller account is not active.");
        }
        return seller;
    }
}
