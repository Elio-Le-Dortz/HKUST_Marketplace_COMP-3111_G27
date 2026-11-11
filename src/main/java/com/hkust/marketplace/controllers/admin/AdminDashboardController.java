package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Main coordinator controller for the Admin Dashboard View.
 *
 * <p>Delegates each tab's view creation and lifecycle to dedicated, modular sub-controllers:
 * <ul>
 *   <li>{@link AdminReviewController} - Listing Review Queue (US-A02)</li>
 *   <li>{@link AdminListingsController} - Platform Listings Management (US-A07)</li>
 *   <li>{@link AdminUsersController} - User Directory Management (US-A03)</li>
 *   <li>{@link AdminDealsController} - Platform Deals &amp; Transaction Ledger (US-A05)</li>
 *   <li>{@link AdminRatingsController} - Seller Rating Moderation (US-A08)</li>
 *   <li>{@link AdminNotificationsController} - Admin Notification Board (US-A06)</li>
 *   <li>{@link AdminProfileController} - Administrator Profile &amp; Security (US-A01)</li>
 * </ul>
 * </p>
 */
public class AdminDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview;
    @FXML private Button btnAllListings;
    @FXML private Button btnUsers;
    @FXML private Button btnDeals;
    @FXML private Button btnRatings;
    @FXML private Button btnNotifications;
    @FXML private Button btnProfile;

    private Button currentActiveBtn;

    private AdminReviewController reviewController;
    private AdminListingsController listingsController;
    private AdminUsersController usersController;
    private AdminDealsController dealsController;
    private AdminRatingsController ratingsController;
    private AdminNotificationsController notificationsController;
    private AdminProfileController profileController;

    @FXML
    public void initialize() {
        Admin admin;
        try {
            admin = SessionManager.requireAdmin();
        } catch (SecurityException e) {
            Platform.runLater(SceneManager::showAdminLogin);
            return;
        }

        if (welcomeLabel != null) {
            welcomeLabel.setText("Admin: " + admin.getFullName());
        }

        DummyDataService dataService = DummyDataService.getInstance();

        this.reviewController = new AdminReviewController(dataService, this::showReview);
        this.listingsController = new AdminListingsController(dataService, this::showAllListings);
        this.usersController = new AdminUsersController(dataService, this::showUsers);
        this.dealsController = new AdminDealsController(dataService, this::showDeals);
        this.ratingsController = new AdminRatingsController(dataService, this::showRatings);
        this.notificationsController = new AdminNotificationsController(
                dataService,
                this::showNotifications,
                this::showReview,
                this::showRatings,
                this::showUsers
        );
        this.profileController = new AdminProfileController(
                dataService,
                updatedName -> {
                    if (welcomeLabel != null) {
                        welcomeLabel.setText("Admin: " + updatedName);
                    }
                }
        );

        currentActiveBtn = btnReview;
        showReview();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) {
            currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        }
        if (btn != null) {
            btn.getStyleClass().add("sidebar-btn-active");
            currentActiveBtn = btn;
        }
        if (pageTitle != null) {
            pageTitle.setText(title);
        }
    }

    @FXML
    public void showReview() {
        setActive(btnReview, "Listing Review & Approval");
        if (contentPane != null && reviewController != null) {
            contentPane.getChildren().setAll(reviewController.createView());
        }
    }

    @FXML
    public void showAllListings() {
        setActive(btnAllListings, "All Platform Listings");
        if (contentPane != null && listingsController != null) {
            contentPane.getChildren().setAll(listingsController.createView());
        }
    }

    @FXML
    public void showUsers() {
        setActive(btnUsers, "User Management");
        if (contentPane != null && usersController != null) {
            contentPane.getChildren().setAll(usersController.createView());
        }
    }

    @FXML
    public void showDeals() {
        setActive(btnDeals, "Platform Deals");
        if (contentPane != null && dealsController != null) {
            contentPane.getChildren().setAll(dealsController.createView());
        }
    }

    @FXML
    public void showRatings() {
        setActive(btnRatings, "Seller Ratings & Reviews");
        if (contentPane != null && ratingsController != null) {
            contentPane.getChildren().setAll(ratingsController.createView());
        }
    }

    @FXML
    public void showNotifications() {
        setActive(btnNotifications, "Admin Notifications");
        if (contentPane != null && notificationsController != null) {
            contentPane.getChildren().setAll(notificationsController.createView());
        }
    }

    @FXML
    public void showProfile() {
        setActive(btnProfile, "My Profile");
        if (contentPane != null && profileController != null) {
            contentPane.getChildren().setAll(profileController.createView());
        }
    }

    @FXML
    public void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
