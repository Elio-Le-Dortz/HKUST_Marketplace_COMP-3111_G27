package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.LoginResult;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.Set;

/**
 * Controller for the Buyer Login screen (US-B02).
 * Strictly decoupled: all credential verification and role access rules are
 * handled by the service/domain layer ({@link DummyDataService#loginBuyer}).
 */
public class BuyerLoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    @FXML
    public void initialize() {
        // Clear field highlights and error message as soon as user types
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> {
            usernameField.getStyleClass().remove("field-error");
            hideError();
        });
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordField.getStyleClass().remove("field-error");
            hideError();
        });
    }

    /**
     * Handles the login action by delegating authentication to the service layer.
     */
    @FXML
    private void handleLogin() {
        hideError();
        clearFieldHighlights();

        String username = usernameField.getText();
        String password = passwordField.getText();

        LoginResult result = DummyDataService.getInstance().loginBuyer(username, password);

        if (!result.isSuccess()) {
            highlightErrorFields(result.getErrorFields());
            showError(result.getMessage());
            // Clear password field for retry if password was wrong or rejected
            if (result.hasFieldError("password")) {
                passwordField.clear();
            }
            return;
        }

        // On successful authentication, establish the session and transition
        SessionManager.login(result.getUser());
        clearForm();
        SceneManager.showBuyerDashboard();
    }

    private void highlightErrorFields(Set<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        if (fields.contains("username") && !usernameField.getStyleClass().contains("field-error")) {
            usernameField.getStyleClass().add("field-error");
        }
        if (fields.contains("password") && !passwordField.getStyleClass().contains("field-error")) {
            passwordField.getStyleClass().add("field-error");
        }
    }

    private void clearFieldHighlights() {
        usernameField.getStyleClass().remove("field-error");
        passwordField.getStyleClass().remove("field-error");
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setText("");
    }

    private void clearForm() {
        usernameField.clear();
        passwordField.clear();
        clearFieldHighlights();
        hideError();
    }

    @FXML
    private void goRegister() {
        clearForm();
        SceneManager.showBuyerRegister();
    }

    @FXML
    private void goWelcome() {
        clearForm();
        SceneManager.showWelcome();
    }
}
