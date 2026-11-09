package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

/**
 * Simple session holder for the currently logged-in user.
 * TODO: Replace with proper JWT / session token handling when backend is added.
 */
public class SessionManager {
    /** The globally logged-in user, or {@code null} when nobody is logged in. */
    private static volatile User globalUser;

    /** Thread-specific session override, used for concurrent multi-user execution in tests. */
    private static final ThreadLocal<User> threadUser = new ThreadLocal<>();

    /**
     * Starts a session for the given user, replacing any previous session.
     *
     * @param user the authenticated user
     */
    public static void login(User user) {
        globalUser = user;
        threadUser.set(user);
    }

    /**
     * Ends the current session, if any.
     */
    public static void logout() {
        globalUser = null;
        threadUser.remove();
    }

    /**
     * Returns the logged-in user. Checks thread-local context first, falling back to global session.
     *
     * @return the user of the current session, or {@code null} if nobody is logged in
     */
    public static User getCurrentUser() {
        User u = threadUser.get();
        return u != null ? u : globalUser;
    }

    /**
     * Tells whether a user is logged in.
     *
     * @return {@code true} if a session is active
     */
    public static boolean isLoggedIn() {
        return getCurrentUser() != null;
    }

    /**
     * Tells whether the logged-in user has the Buyer role.
     *
     * @return {@code true} if a Buyer is logged in
     */
    public static boolean isBuyer() {
        User u = getCurrentUser();
        return u != null && u.getRole() == UserRole.BUYER;
    }

    /**
     * Tells whether the logged-in user has the Seller role.
     *
     * @return {@code true} if a Seller is logged in
     */
    public static boolean isSeller() {
        User u = getCurrentUser();
        return u != null && u.getRole() == UserRole.SELLER;
    }

    /**
     * Tells whether the logged-in user has the Admin role.
     *
     * @return {@code true} if an Admin is logged in
     */
    public static boolean isAdmin() {
        User u = getCurrentUser();
        return u != null && u.getRole() == UserRole.ADMIN;
    }

    /**
     * Ends the active session for a specific user if that user is currently logged in.
     * Used when an administrator deactivates or deletes an account (Sprint 2 US-A03, UC 3.3).
     *
     * @param username the username of the user whose session should end
     */
    public static void endSessionForUser(String username) {
        if (username == null) return;
        String clean = username.trim();
        if (globalUser != null && clean.equalsIgnoreCase(globalUser.getUsername())) {
            globalUser = null;
        }
        User tu = threadUser.get();
        if (tu != null && clean.equalsIgnoreCase(tu.getUsername())) {
            threadUser.remove();
        }
    }

    /**
     * Returns the currently authenticated user cast to Buyer if logged in as a Buyer.
     *
     * @return Buyer instance or null if not logged in or not a Buyer
     */
    public static Buyer getCurrentBuyer() {
        User u = getCurrentUser();
        if (u instanceof Buyer) {
            return (Buyer) u;
        }
        return null;
    }

    /**
     * Enforces that an active Buyer session is present.
     * Fulfills the special requirement of use case 1.2 that every Buyer-only function
     * checks the Buyer session on the server/service side.
     *
     * @return the active Buyer of the current session
     * @throws SecurityException if not logged in, not a Buyer, or account is not active
     */
    public static Buyer requireBuyer() {
        if (!isLoggedIn()) {
            throw new SecurityException("Unauthorized: No user is currently logged in.");
        }
        Buyer buyer = getCurrentBuyer();
        if (!isBuyer() || buyer == null) {
            throw new SecurityException("Unauthorized: Access requires a Buyer account.");
        }
        if (!buyer.isActive()) {
            throw new SecurityException("Unauthorized: Buyer account is not active.");
        }
        return buyer;
    }

    /**
     * Executes an action with a temporary user session, restoring the previous session upon completion.
     *
     * @param tempUser the user to temporarily authenticate
     * @param action   the action to execute
     * @param <T>      the return type
     * @return the result of the action
     */
    public static <T> T withSession(User tempUser, java.util.concurrent.Callable<T> action) {
        User prev = threadUser.get();
        threadUser.set(tempUser);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (prev != null && prev.isActive() && !prev.isDeleted()) {
                threadUser.set(prev);
            } else {
                threadUser.remove();
            }
        }
    }

    /**
     * Executes a runnable with a temporary user session, restoring the previous session upon completion.
     *
     * @param tempUser the user to temporarily authenticate
     * @param action   the runnable to execute
     */
    public static void withSession(User tempUser, Runnable action) {
        User prev = threadUser.get();
        threadUser.set(tempUser);
        try {
            action.run();
        } finally {
            if (prev != null && prev.isActive() && !prev.isDeleted()) {
                threadUser.set(prev);
            } else {
                threadUser.remove();
            }
        }
    }

    /**
     * Returns the currently authenticated user cast to Seller if logged in as a Seller.
     *
     * @return Seller instance or null if not logged in or not a Seller
     */
    public static Seller getCurrentSeller() {
        User u = getCurrentUser();
        if (u instanceof Seller) {
            return (Seller) u;
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
    public static Seller requireSeller() {
        if (!isLoggedIn()) {
            throw new SecurityException("Unauthorized: No user is currently logged in.");
        }
        Seller seller = getCurrentSeller();
        if (!isSeller() || seller == null) {
            throw new SecurityException("Unauthorized: Access requires a Seller account.");
        }
        if (!seller.isActive()) {
            throw new SecurityException("Unauthorized: Seller account is not active.");
        }
        return seller;
    }

    /**
     * Returns the currently authenticated user cast to Admin if logged in as an Admin.
     *
     * @return Admin instance or null if not logged in or not an Admin
     */
    public static Admin getCurrentAdmin() {
        User u = getCurrentUser();
        if (u instanceof Admin) {
            return (Admin) u;
        }
        return null;
    }

    /**
     * Enforces that an active Admin session is present (US-A01, UC 3.1).
     * Fulfills the special requirement of use case 3.1 that every Admin-only function
     * checks the Admin session on the service side, so a buyer or seller session cannot
     * call it even without the user interface. Because the session holds the stored
     * account object, an admin deactivated after logging in is refused as well.
     *
     * @return the active Admin of the current session
     * @throws SecurityException if not logged in, not an Admin, or the account is not active
     */
    public static Admin requireAdmin() {
        if (!isLoggedIn()) {
            throw new SecurityException("Unauthorized: No user is currently logged in.");
        }
        Admin admin = getCurrentAdmin();
        if (!isAdmin() || admin == null) {
            throw new SecurityException("Unauthorized: Access requires an Admin account.");
        }
        if (!admin.isActive()) {
            throw new SecurityException("Unauthorized: Admin account is not active.");
        }
        return admin;
    }
}
