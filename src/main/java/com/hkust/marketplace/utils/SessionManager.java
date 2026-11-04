package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;

/**
 * Simple session holder for the currently logged-in user.
 * TODO: Replace with proper JWT / session token handling when backend is added.
 */
public class SessionManager {
    private static User currentUser;

    public static void login(User user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static boolean isBuyer() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.BUYER;
    }

    public static boolean isSeller() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.SELLER;
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == com.hkust.marketplace.models.UserRole.ADMIN;
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
}
