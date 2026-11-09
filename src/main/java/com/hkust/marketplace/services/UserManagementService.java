package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules, validation, and domain state transitions
 * for Admin User Management (Sprint 2 US-A03, Task 3.3 in specs_admin.tex).
 *
 * <p>Completely decoupled from JavaFX UI controls to support headless unit testing in CI.
 */
public class UserManagementService {

    public static final String AUDIT_LOG_CATEGORY = "User Accounts";

    private final DummyDataService dataService;

    public UserManagementService() {
        this(DummyDataService.getInstance());
    }

    public UserManagementService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Retrieves all buyer and seller accounts matching the given filter criteria,
     * sorted alphabetically by username (Basic Flow Step 2).
     *
     * <p>Excludes administrator accounts per Special Requirements.
     *
     * @param criteria filter and search criteria
     * @return list of matching non-admin users
     */
    public synchronized List<User> getUsers(UserManagementFilterCriteria criteria) {
        SessionManager.requireAdmin();
        UserManagementFilterCriteria effectiveCriteria = criteria != null ? criteria : UserManagementFilterCriteria.all();

        return dataService.getAllUsers().stream()
                .filter(u -> u != null && u.getRole() != UserRole.ADMIN)
                .filter(u -> !DummyDataService.SEEDED_ADMIN_USERNAME.equalsIgnoreCase(u.getUsername()))
                .filter(effectiveCriteria::matches)
                .sorted(Comparator.comparing(u -> u.getUsername() != null ? u.getUsername().toLowerCase() : ""))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a non-admin user account by username.
     *
     * @param username the target username
     * @return an {@link Optional} containing the user if found and not an admin, or empty otherwise
     */
    public synchronized Optional<User> getUser(String username) {
        SessionManager.requireAdmin();
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        User u = dataService.findUser(username.trim());
        if (u == null || u.getRole() == UserRole.ADMIN || DummyDataService.SEEDED_ADMIN_USERNAME.equalsIgnoreCase(u.getUsername())) {
            return Optional.empty();
        }
        return Optional.of(u);
    }

    /**
     * Finds all active deals in which the specified user participates as buyer or seller.
     *
     * @param username the username to check
     * @return list of active deals
     */
    public synchronized List<Deal> getActiveDealsForUser(String username) {
        SessionManager.requireAdmin();
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String clean = username.trim().toLowerCase();
        return dataService.getAllDeals().stream()
                .filter(d -> d != null && d.getStatus() == DealStatus.ACTIVE)
                .filter(d -> (d.getBuyerUsername() != null && d.getBuyerUsername().equalsIgnoreCase(clean))
                        || (d.getSellerUsername() != null && d.getSellerUsername().equalsIgnoreCase(clean)))
                .collect(Collectors.toList());
    }

    /**
     * Edits a user's profile details (Subflow S1).
     *
     * <p>Validates:
     * <ul>
     *   <li>Admin authorization: Requester must be an active administrator.</li>
     *   <li>Target account: Must exist and cannot be an administrator or soft-deleted.</li>
     *   <li>Alternative Flow A2: Full name must not be empty.</li>
     * </ul>
     *
     * <p>Upon success, updates fields, dispatches a profile update notification to the user,
     * and records an audit log entry in the admin notifications board.
     *
     * @param request the edit request
     * @return outcome of the operation
     */
    public synchronized UserManagementResult editUser(UserEditRequest request) {
        Admin sessionAdmin = SessionManager.requireAdmin();
        if (request == null) {
            return UserManagementResult.failure(UserManagementStatus.INVALID_INPUT, "Edit request cannot be null.");
        }
        if (request.getTargetUsername() == null || request.getTargetUsername().trim().isEmpty()) {
            return UserManagementResult.failure(UserManagementStatus.INVALID_INPUT, "Target username cannot be empty.");
        }
        if (request.getAdminUsername() == null || request.getAdminUsername().trim().isEmpty()) {
            return UserManagementResult.failure(UserManagementStatus.UNAUTHORIZED, "Admin username is required.");
        }

        // 1. Authorize administrator
        User adminUser = dataService.findUser(request.getAdminUsername().trim());
        if (adminUser == null || adminUser.getRole() != UserRole.ADMIN || !adminUser.isActive()
                || !sessionAdmin.getUsername().equalsIgnoreCase(adminUser.getUsername())) {
            return UserManagementResult.failure(UserManagementStatus.UNAUTHORIZED,
                    "User '" + request.getAdminUsername() + "' is not an active administrator.");
        }

        // 2. Locate target user
        User target = dataService.findUser(request.getTargetUsername().trim());
        if (target == null) {
            return UserManagementResult.failure(UserManagementStatus.USER_NOT_FOUND,
                    "User @" + request.getTargetUsername() + " does not exist.");
        }
        if (target.getRole() == UserRole.ADMIN || DummyDataService.SEEDED_ADMIN_USERNAME.equalsIgnoreCase(target.getUsername())) {
            return UserManagementResult.failure(UserManagementStatus.CANNOT_MODIFY_ADMIN,
                    "Administrator accounts cannot be modified through user management.");
        }
        if (target.isDeleted()) {
            return UserManagementResult.failure(UserManagementStatus.ALREADY_IN_STATE,
                    "Deleted user accounts cannot be edited.");
        }

        // 3. Alternative Flow A2: Validate full name
        if (request.getNewFullName() == null || request.getNewFullName().trim().isEmpty()) {
            return UserManagementResult.failure(UserManagementStatus.INVALID_INPUT, "Full name is required.");
        }

        // 4. Update details
        target.setFullName(request.getNewFullName().trim());
        if (target instanceof Buyer buyer) {
            if (request.getResidence() != null) {
                buyer.setResidence(request.getResidence().trim());
            }
        } else if (target instanceof Seller seller) {
            if (request.getBio() != null) {
                seller.setBio(request.getBio().trim());
            }
            if (request.getPreferredMeetups() != null && !request.getPreferredMeetups().isEmpty()) {
                seller.setPreferredMeetups(request.getPreferredMeetups());
            }
        }
        dataService.updateUser(target);

        // 5. Subflow S1 Step 5: Notify user
        dataService.addNotification(new Notification(
                target.getUsername(),
                "Profile Updated",
                "An administrator has updated your profile details.",
                "Announcements"
        ));

        // 6. Special Requirement: Admin Audit Log
        dataService.addNotification(new Notification(
                "admin",
                "User Profile Updated",
                "Admin @" + adminUser.getUsername() + " updated profile of @" + target.getUsername() + ".",
                AUDIT_LOG_CATEGORY,
                target.getUsername(),
                "USER"
        ));

        return UserManagementResult.success("User @" + target.getUsername() + " successfully updated.", target);
    }

    /**
     * Changes the account status of a user to {@link AccountStatus#ACTIVE} or
     * {@link AccountStatus#DEACTIVATED} (Subflow S2).
     *
     * <p>If deactivating, invalidates any active session for that user.
     * Soft-deleted accounts cannot have their status altered.
     *
     * @param targetUsername the username of the account to update
     * @param newStatus the new status (must be ACTIVE or DEACTIVATED)
     * @param adminUsername the requesting administrator's username
     * @return outcome of the operation
     */
    public synchronized UserManagementResult changeAccountStatus(String targetUsername, AccountStatus newStatus, String adminUsername) {
        Admin sessionAdmin = SessionManager.requireAdmin();
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            return UserManagementResult.failure(UserManagementStatus.INVALID_INPUT, "Target username cannot be empty.");
        }
        if (adminUsername == null || adminUsername.trim().isEmpty()) {
            return UserManagementResult.failure(UserManagementStatus.UNAUTHORIZED, "Admin username is required.");
        }
        if (newStatus == null || newStatus == AccountStatus.DELETED) {
            return UserManagementResult.failure(UserManagementStatus.INVALID_INPUT,
                    "Invalid target status. Use deleteUser() for account deletion.");
        }

        // 1. Authorize administrator
        User adminUser = dataService.findUser(adminUsername.trim());
        if (adminUser == null || adminUser.getRole() != UserRole.ADMIN || !adminUser.isActive()
                || !sessionAdmin.getUsername().equalsIgnoreCase(adminUser.getUsername())) {
            return UserManagementResult.failure(UserManagementStatus.UNAUTHORIZED,
                    "User '" + adminUsername + "' is not an active administrator.");
        }

        // 2. Locate target user
        User target = dataService.findUser(targetUsername.trim());
        if (target == null) {
            return UserManagementResult.failure(UserManagementStatus.USER_NOT_FOUND,
                    "User @" + targetUsername + " does not exist.");
        }
        if (target.getRole() == UserRole.ADMIN || DummyDataService.SEEDED_ADMIN_USERNAME.equalsIgnoreCase(target.getUsername())) {
            return UserManagementResult.failure(UserManagementStatus.CANNOT_MODIFY_ADMIN,
                    "Administrator account status cannot be changed.");
        }
        if (target.isDeleted()) {
            return UserManagementResult.failure(UserManagementStatus.ALREADY_IN_STATE,
                    "Deleted user accounts cannot be reactivated or deactivated.");
        }
        if (target.getAccountStatus() == newStatus) {
            return UserManagementResult.failure(UserManagementStatus.ALREADY_IN_STATE,
                    "User @" + target.getUsername() + " is already " + (newStatus == AccountStatus.ACTIVE ? "active" : "deactivated") + ".");
        }

        // 3. Apply status transition
        target.setAccountStatus(newStatus);
        dataService.updateUser(target);

        if (newStatus == AccountStatus.DEACTIVATED) {
            // Subflow S2 Step 3.1: End any open session
            SessionManager.endSessionForUser(target.getUsername());

            dataService.addNotification(new Notification(
                    target.getUsername(),
                    "Account Deactivated",
                    "Your account has been deactivated by an administrator.",
                    "Announcements"
            ));

            dataService.addNotification(new Notification(
                    "admin",
                    "User Deactivated",
                    "Admin @" + adminUser.getUsername() + " deactivated account @" + target.getUsername() + ".",
                    AUDIT_LOG_CATEGORY,
                    target.getUsername(),
                    "USER"
            ));

            return UserManagementResult.success("User @" + target.getUsername() + " has been deactivated.", target);
        } else {
            // Subflow S2 Step 4.1: Reactivated
            dataService.addNotification(new Notification(
                    target.getUsername(),
                    "Account Reactivated",
                    "Your account has been reactivated by an administrator. You may now log in.",
                    "Announcements"
            ));

            dataService.addNotification(new Notification(
                    "admin",
                    "User Reactivated",
                    "Admin @" + adminUser.getUsername() + " reactivated account @" + target.getUsername() + ".",
                    AUDIT_LOG_CATEGORY,
                    target.getUsername(),
                    "USER"
            ));

            return UserManagementResult.success("User @" + target.getUsername() + " has been reactivated.", target);
        }
    }

    /**
     * Soft-deletes a user account (Subflow S3).
     *
     * <p>Enforces:
     * <ul>
     *   <li>Precondition & Alternative Flow A3: Checks that the user has NO deals with status {@link DealStatus#ACTIVE}.
     *       If active deals exist, deletion is completely blocked and active deals are returned.</li>
     *   <li>Soft-delete: Account status set to {@link AccountStatus#DELETED}, username stays reserved in the store.</li>
     *   <li>Session: Terminates any open session of the deleted user.</li>
     *   <li>Seller cascade: Sets all pending or live listings to {@link ListingStatus#REMOVED}, declines active offers,
     *       and notifies affected buyers.</li>
     *   <li>Buyer cascade: Withdraws all pending/countered offers submitted by the buyer, and notifies affected sellers.</li>
     *   <li>Audit log: Records deletion in admin notifications board.</li>
     * </ul>
     *
     * @param targetUsername username to soft-delete
     * @param adminUsername requesting administrator username
     * @return outcome containing counts of affected listings and offers, or active deals blocking deletion
     */
    public synchronized UserManagementResult deleteUser(String targetUsername, String adminUsername) {
        Admin sessionAdmin = SessionManager.requireAdmin();
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            return UserManagementResult.failure(UserManagementStatus.INVALID_INPUT, "Target username cannot be empty.");
        }
        if (adminUsername == null || adminUsername.trim().isEmpty()) {
            return UserManagementResult.failure(UserManagementStatus.UNAUTHORIZED, "Admin username is required.");
        }

        // 1. Authorize administrator
        User adminUser = dataService.findUser(adminUsername.trim());
        if (adminUser == null || adminUser.getRole() != UserRole.ADMIN || !adminUser.isActive()
                || !sessionAdmin.getUsername().equalsIgnoreCase(adminUser.getUsername())) {
            return UserManagementResult.failure(UserManagementStatus.UNAUTHORIZED,
                    "User '" + adminUsername + "' is not an active administrator.");
        }

        // 2. Locate target user
        User target = dataService.findUser(targetUsername.trim());
        if (target == null) {
            return UserManagementResult.failure(UserManagementStatus.USER_NOT_FOUND,
                    "User @" + targetUsername + " does not exist.");
        }
        if (target.getRole() == UserRole.ADMIN || DummyDataService.SEEDED_ADMIN_USERNAME.equalsIgnoreCase(target.getUsername())) {
            return UserManagementResult.failure(UserManagementStatus.CANNOT_MODIFY_ADMIN,
                    "Administrator accounts cannot be deleted.");
        }
        if (target.isDeleted()) {
            return UserManagementResult.failure(UserManagementStatus.ALREADY_IN_STATE,
                    "User @" + target.getUsername() + " is already deleted.");
        }

        // 3. Subflow S3 Step 3 & Alternative Flow A3: Check active deals
        List<Deal> activeDeals = getActiveDealsForUser(target.getUsername());
        if (!activeDeals.isEmpty()) {
            return UserManagementResult.activeDealsExist(
                    "Cannot delete @" + target.getUsername() + " because they have " + activeDeals.size() +
                            " active deal(s). All active deals must be completed or returned first. Consider deactivating the account instead.",
                    activeDeals
            );
        }

        // 4. Subflow S3 Step 4: Mark soft-deleted and terminate session
        target.setAccountStatus(AccountStatus.DELETED);
        dataService.updateUser(target);
        SessionManager.endSessionForUser(target.getUsername());

        int affectedListings = 0;
        int affectedOffers = 0;

        // 5. Subflow S3 Step 5: If Seller, remove pending/live listings and decline pending offers
        if (target.getRole() == UserRole.SELLER) {
            String sellerUser = target.getUsername();
            for (Listing listing : dataService.getAllListings()) {
                if (listing != null && sellerUser.equalsIgnoreCase(listing.getSellerUsername())) {
                    if (listing.getStatus() == ListingStatus.PENDING_REVIEW || listing.getStatus() == ListingStatus.LIVE) {
                        listing.setStatus(ListingStatus.REMOVED);
                        listing.setAdminReason("Listing removed due to seller account deletion by administrator.");
                        dataService.updateListing(listing);
                        affectedListings++;
                    }
                }
            }

            for (Offer offer : dataService.getAllOffers()) {
                if (offer != null && sellerUser.equalsIgnoreCase(offer.getSellerUsername())) {
                    if (offer.getStatus() == OfferStatus.PENDING || offer.getStatus() == OfferStatus.COUNTERED) {
                        offer.setStatus(OfferStatus.DECLINED);
                        offer.setDeclineReason("Listing removed due to seller account deletion.");
                        dataService.updateOffer(offer);
                        affectedOffers++;

                        // Notify buyer
                        if (offer.getBuyerUsername() != null && !offer.getBuyerUsername().trim().isEmpty()) {
                            dataService.addNotification(new Notification(
                                    offer.getBuyerUsername(),
                                    "Offer Declined",
                                    "Your offer for listing '" + (offer.getListingTitle() != null ? offer.getListingTitle() : "item") +
                                            "' was declined because the seller's account was deleted.",
                                    "Offers",
                                    offer.getListingId(),
                                    "LISTING"
                            ));
                        }
                    }
                }
            }
        }

        // 6. Subflow S3 Step 6: If Buyer, withdraw pending/countered offers and notify sellers
        if (target.getRole() == UserRole.BUYER) {
            String buyerUser = target.getUsername();
            for (Offer offer : dataService.getAllOffers()) {
                if (offer != null && buyerUser.equalsIgnoreCase(offer.getBuyerUsername())) {
                    if (offer.getStatus() == OfferStatus.PENDING || offer.getStatus() == OfferStatus.COUNTERED) {
                        offer.setStatus(OfferStatus.WITHDRAWN);
                        dataService.updateOffer(offer);
                        affectedOffers++;

                        // Notify seller
                        if (offer.getSellerUsername() != null && !offer.getSellerUsername().trim().isEmpty()) {
                            dataService.addNotification(new Notification(
                                    offer.getSellerUsername(),
                                    "Offer Withdrawn",
                                    "An offer from @" + buyerUser + " for listing '" +
                                            (offer.getListingTitle() != null ? offer.getListingTitle() : "item") +
                                            "' was automatically withdrawn because the buyer's account was deleted.",
                                    "Offers",
                                    offer.getListingId(),
                                    "OFFER"
                            ));
                        }
                    }
                }
            }
        }

        // 7. Special Requirement: Admin audit log
        dataService.addNotification(new Notification(
                "admin",
                "User Deleted",
                "Admin @" + adminUser.getUsername() + " soft-deleted account @" + target.getUsername() + ".",
                AUDIT_LOG_CATEGORY,
                target.getUsername(),
                "USER"
        ));

        return UserManagementResult.success(
                "User @" + target.getUsername() + " successfully deleted.",
                target,
                affectedListings,
                affectedOffers
        );
    }
}
