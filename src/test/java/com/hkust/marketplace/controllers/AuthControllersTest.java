package com.hkust.marketplace.controllers;

import com.hkust.marketplace.controllers.admin.AdminDashboardController;
import com.hkust.marketplace.controllers.admin.AdminLoginController;
import com.hkust.marketplace.controllers.buyer.BuyerLoginController;
import com.hkust.marketplace.controllers.buyer.BuyerRegisterController;
import com.hkust.marketplace.controllers.seller.SellerLoginController;
import com.hkust.marketplace.controllers.seller.SellerRegisterController;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.services.AdminLoginService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Authentication & Dashboard Controllers Headless Unit Tests")
public class AuthControllersTest {

    private DummyDataService dataService;

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        SessionManager.logout();
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        dataService.resetData();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invokeMethod(Object target, String methodName, Object... args) {
        try {
            Method m = null;
            for (Method candidate : target.getClass().getDeclaredMethods()) {
                if (candidate.getName().equals(methodName) && candidate.getParameterCount() == args.length) {
                    m = candidate;
                    break;
                }
            }
            if (m == null) {
                throw new NoSuchMethodException(methodName);
            }
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    // ==========================================
    // BuyerLoginController Tests
    // ==========================================

    @Test
    @DisplayName("BuyerLoginController: initialize and validation failure branches")
    void testBuyerLoginControllerValidationFailures() {
        BuyerLoginController controller = new BuyerLoginController();
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Label errorLabel = new Label();
        Button loginButton = new Button();

        setField(controller, "usernameField", usernameField);
        setField(controller, "passwordField", passwordField);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "loginButton", loginButton);

        controller.initialize();

        // Trigger listeners
        usernameField.setText("a");
        passwordField.setText("p");
        usernameField.setText("");
        passwordField.setText("");

        // Blank login
        invokeMethod(controller, "handleLogin");
        assertTrue(errorLabel.isVisible());
        assertTrue(usernameField.getStyleClass().contains("field-error"));
        assertTrue(passwordField.getStyleClass().contains("field-error"));

        // Invalid credentials (clearing passwordField triggers listener which hides error)
        usernameField.setText("alice");
        passwordField.setText("wrongpassword");
        invokeMethod(controller, "handleLogin");
        assertEquals("", passwordField.getText(), "Password should be cleared on invalid credentials");

        // Wrong account type (seller 'charlie' logging into buyer)
        usernameField.setText("charlie");
        passwordField.setText("password1");
        invokeMethod(controller, "handleLogin");
        assertTrue(errorLabel.isVisible());

        // Navigation helpers
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "goRegister"));
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "goWelcome"));

        // Direct highlight calls with null / empty
        invokeMethod(controller, "highlightErrorFields", (Object) null);
        invokeMethod(controller, "highlightErrorFields", Collections.emptySet());
        invokeMethod(controller, "highlightErrorFields", Set.of("username", "password"));
    }

    @Test
    @DisplayName("BuyerLoginController: successful login executes session and navigates")
    void testBuyerLoginControllerSuccess() {
        BuyerLoginController controller = new BuyerLoginController();
        TextField usernameField = new TextField("alice");
        PasswordField passwordField = new PasswordField();
        passwordField.setText("password1");
        Label errorLabel = new Label();

        setField(controller, "usernameField", usernameField);
        setField(controller, "passwordField", passwordField);
        setField(controller, "errorLabel", errorLabel);

        // Attempt login -> reaches SceneManager.showBuyerDashboard and throws RuntimeException
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "handleLogin"));
        assertNotNull(SessionManager.getCurrentUser());
        assertEquals("alice", SessionManager.getCurrentUser().getUsername());
    }

    // ==========================================
    // BuyerRegisterController Tests
    // ==========================================

    @Test
    @DisplayName("BuyerRegisterController: initialize, validation failures and cancel")
    void testBuyerRegisterControllerValidationFailures() {
        BuyerRegisterController controller = new BuyerRegisterController();
        TextField usernameField = new TextField();
        TextField fullNameField = new TextField();
        PasswordField passwordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();
        ComboBox<String> residenceCombo = new ComboBox<>();
        Label errorLabel = new Label();
        Label successLabel = new Label();

        setField(controller, "usernameField", usernameField);
        setField(controller, "fullNameField", fullNameField);
        setField(controller, "passwordField", passwordField);
        setField(controller, "confirmPasswordField", confirmPasswordField);
        setField(controller, "residenceCombo", residenceCombo);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "successLabel", successLabel);

        controller.initialize();
        assertFalse(residenceCombo.getItems().isEmpty());

        // Empty registration
        invokeMethod(controller, "handleRegister");
        assertTrue(errorLabel.isVisible());
        assertFalse(successLabel.isVisible());

        // Password mismatch
        usernameField.setText("newbuyer");
        fullNameField.setText("New Buyer");
        passwordField.setText("Password123!");
        confirmPasswordField.setText("Mismatch123!");
        residenceCombo.setValue("Hall 1");
        invokeMethod(controller, "handleRegister");
        assertTrue(errorLabel.isVisible());

        // Cancel registration
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "handleCancel"));

        // Null and empty error fields
        invokeMethod(controller, "highlightErrorFields", (Object) null);
        invokeMethod(controller, "highlightErrorFields", Collections.emptySet());
        invokeMethod(controller, "highlightErrorFields", Set.of("username", "fullName", "password", "confirmPassword"));
    }

    @Test
    @DisplayName("BuyerRegisterController: successful registration")
    void testBuyerRegisterControllerSuccess() {
        BuyerRegisterController controller = new BuyerRegisterController();
        TextField usernameField = new TextField("freshbuyer");
        TextField fullNameField = new TextField("Fresh Buyer");
        PasswordField passwordField = new PasswordField();
        passwordField.setText("Password123!");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setText("Password123!");
        ComboBox<String> residenceCombo = new ComboBox<>();
        residenceCombo.setValue("Hall 2");
        Label errorLabel = new Label();
        Label successLabel = new Label();

        setField(controller, "usernameField", usernameField);
        setField(controller, "fullNameField", fullNameField);
        setField(controller, "passwordField", passwordField);
        setField(controller, "confirmPasswordField", confirmPasswordField);
        setField(controller, "residenceCombo", residenceCombo);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "successLabel", successLabel);

        invokeMethod(controller, "handleRegister");
        assertTrue(successLabel.isVisible());
        assertEquals("", usernameField.getText(), "Form should be cleared on success");
    }

    // ==========================================
    // SellerLoginController Tests
    // ==========================================

    @Test
    @DisplayName("SellerLoginController: validation, errors and navigation")
    void testSellerLoginController() {
        SellerLoginController controller = new SellerLoginController();
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Label errorLabel = new Label();

        setField(controller, "usernameField", usernameField);
        setField(controller, "passwordField", passwordField);
        setField(controller, "errorLabel", errorLabel);

        controller.initialize();

        // Trigger listener
        usernameField.setText("temp");
        passwordField.setText("temp");

        // Empty submission
        usernameField.setText("");
        passwordField.setText("");
        invokeMethod(controller, "handleLogin");
        assertTrue(errorLabel.isVisible());

        // Invalid credentials
        usernameField.setText("charlie");
        passwordField.setText("wrongpassword");
        invokeMethod(controller, "handleLogin");
        assertTrue(errorLabel.isVisible());
        assertEquals("", passwordField.getText());

        // Buyer trying to log in as seller
        usernameField.setText("alice");
        passwordField.setText("password1");
        invokeMethod(controller, "handleLogin");
        assertTrue(errorLabel.isVisible());

        // Valid seller login
        usernameField.setText("charlie");
        passwordField.setText("password1");
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "handleLogin"));

        // Navigation
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "goRegister"));
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "goWelcome"));
    }

    // ==========================================
    // SellerRegisterController Tests
    // ==========================================

    @Test
    @DisplayName("SellerRegisterController: initialize, meetups selection, validation errors")
    void testSellerRegisterController() {
        SellerRegisterController controller = new SellerRegisterController();
        TextField usernameField = new TextField();
        TextField fullNameField = new TextField();
        PasswordField passwordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();
        FlowPane meetupPane = new FlowPane();
        TextArea bioArea = new TextArea();
        Label errorLabel = new Label();

        setField(controller, "usernameField", usernameField);
        setField(controller, "fullNameField", fullNameField);
        setField(controller, "passwordField", passwordField);
        setField(controller, "confirmPasswordField", confirmPasswordField);
        setField(controller, "meetupPane", meetupPane);
        setField(controller, "bioArea", bioArea);
        setField(controller, "errorLabel", errorLabel);

        controller.initialize();
        assertFalse(meetupPane.getChildren().isEmpty(), "Meetup checkboxes should be created");

        // Tick first checkbox
        CheckBox firstBox = (CheckBox) meetupPane.getChildren().get(0);
        firstBox.setSelected(true);

        // Validation failure
        invokeMethod(controller, "handleRegister");
        assertTrue(errorLabel.isVisible());

        // Navigation
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "goLogin"));
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "goWelcome"));
    }

    // ==========================================
    // AdminLoginController Tests
    // ==========================================

    @Test
    @DisplayName("AdminLoginController: constructors, authenticateAdmin, handleLogin and null safety")
    void testAdminLoginController() {
        AdminLoginService loginService = new AdminLoginService();
        AdminLoginController controller = new AdminLoginController(loginService);
        assertNotNull(new AdminLoginController());

        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Label errorLabel = new Label();

        setField(controller, "usernameField", usernameField);
        setField(controller, "passwordField", passwordField);
        setField(controller, "errorLabel", errorLabel);

        controller.initialize();

        // authenticateAdmin helper
        assertEquals("OK", controller.authenticateAdmin("admin", "admin123"));
        assertNotEquals("OK", controller.authenticateAdmin("bad", "wrong"));

        // Listeners
        usernameField.setText("temp");
        passwordField.setText("temp");

        // Blank login
        usernameField.setText("");
        passwordField.setText("");
        invokeMethod(controller, "handleLogin");
        assertTrue(errorLabel.isVisible());

        // Invalid credentials
        usernameField.setText("admin");
        passwordField.setText("wrongpassword");
        invokeMethod(controller, "handleLogin");
        assertTrue(errorLabel.isVisible());
        assertEquals("", passwordField.getText());

        // Valid admin login
        usernameField.setText("admin");
        passwordField.setText("admin123");
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "handleLogin"));

        // Navigation
        assertThrows(RuntimeException.class, () -> invokeMethod(controller, "goWelcome"));

        // Null controls safety
        AdminLoginController nullController = new AdminLoginController();
        nullController.initialize();
        invokeMethod(nullController, "handleLogin");
    }

    // ==========================================
    // AdminDashboardController Tests
    // ==========================================

    @Test
    @DisplayName("AdminDashboardController: lifecycle, navigation, tabs and null safety")
    void testAdminDashboardController() {
        AdminDashboardController dashboard = new AdminDashboardController();

        StackPane contentPane = new StackPane();
        Label pageTitle = new Label();
        Label welcomeLabel = new Label();
        Button btnReview = new Button("Review");
        Button btnAllListings = new Button("All Listings");
        Button btnUsers = new Button("Users");
        Button btnDeals = new Button("Deals");
        Button btnRatings = new Button("Ratings");
        Button btnNotifications = new Button("Notifications");
        Button btnProfile = new Button("Profile");

        setField(dashboard, "contentPane", contentPane);
        setField(dashboard, "pageTitle", pageTitle);
        setField(dashboard, "welcomeLabel", welcomeLabel);
        setField(dashboard, "btnReview", btnReview);
        setField(dashboard, "btnAllListings", btnAllListings);
        setField(dashboard, "btnUsers", btnUsers);
        setField(dashboard, "btnDeals", btnDeals);
        setField(dashboard, "btnRatings", btnRatings);
        setField(dashboard, "btnNotifications", btnNotifications);
        setField(dashboard, "btnProfile", btnProfile);

        // Security check: unauthenticated call to initialize
        dashboard.initialize();

        // Authenticate admin
        Admin admin = new Admin("admin", "Admin Master", "admin123");
        SessionManager.login(admin);

        dashboard.initialize();
        assertTrue(welcomeLabel.getText().contains("Admin Master"));
        assertEquals("Listing Review & Approval", pageTitle.getText());
        assertFalse(contentPane.getChildren().isEmpty());

        // Switch to all tabs
        dashboard.showAllListings();
        assertEquals("All Platform Listings", pageTitle.getText());

        dashboard.showUsers();
        assertEquals("User Management", pageTitle.getText());

        dashboard.showDeals();
        assertEquals("Platform Deals", pageTitle.getText());

        dashboard.showRatings();
        assertEquals("Seller Ratings & Reviews", pageTitle.getText());

        dashboard.showNotifications();
        assertEquals("Admin Notifications", pageTitle.getText());

        dashboard.showProfile();
        assertEquals("My Profile", pageTitle.getText());

        dashboard.showReview();
        assertEquals("Listing Review & Approval", pageTitle.getText());

        // Logout
        assertThrows(RuntimeException.class, dashboard::logout);
        assertNull(SessionManager.getCurrentUser());

        // Null fields safety test
        AdminDashboardController nullDashboard = new AdminDashboardController();
        SessionManager.login(admin);
        nullDashboard.initialize();
        nullDashboard.showAllListings();
        nullDashboard.showUsers();
        nullDashboard.showDeals();
        nullDashboard.showRatings();
        nullDashboard.showNotifications();
        nullDashboard.showProfile();
        nullDashboard.showReview();
    }
}
