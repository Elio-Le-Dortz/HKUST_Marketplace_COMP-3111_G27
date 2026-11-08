package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.BuyerRegistrationResult;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.Set;

/**
 * Controller for the Buyer Registration screen (US-B01).
 * Strictly decoupled: all validation and persistence logic is delegated to DummyDataService.
 */
public class BuyerRegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> residenceCombo;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    @FXML
    public void initialize() {
        residenceCombo.setItems(FXCollections.observableArrayList(
                "Hall 1", "Hall 2", "Hall 3", "Hall 4", "Hall 5",
                "Hall 6", "Hall 7", "Hall 8", "Hall 9",
                "UG Hall 1", "UG Hall 2", "Off-campus"
        ));
    }

    @FXML
    private void handleRegister() {
        hideMessages();
        clearFieldHighlights();

        String username = usernameField.getText();
        String fullName = fullNameField.getText();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        String residence = residenceCombo.getValue();

        BuyerRegistrationResult result = DummyDataService.getInstance()
                .registerBuyer(username, fullName, password, confirm, residence);

        if (!result.isSuccess()) {
            highlightErrorFields(result.getErrorFields());
            showError(result.getMessage());
            return;
        }

        showSuccess("Registration successful! You can now log in.");
        clearForm();
    }

    /**
     * Alternative Flow A5: Cancel Registration.
     * Clears input and returns to the Buyer Login screen.
     */
    @FXML
    private void handleCancel() {
        clearForm();
        clearFieldHighlights();
        hideMessages();
        SceneManager.showBuyerLogin();
    }

    private void highlightErrorFields(Set<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        if (fields.contains("username") && !usernameField.getStyleClass().contains("field-error")) {
            usernameField.getStyleClass().add("field-error");
        }
        if (fields.contains("fullName") && !fullNameField.getStyleClass().contains("field-error")) {
            fullNameField.getStyleClass().add("field-error");
        }
        if (fields.contains("password") && !passwordField.getStyleClass().contains("field-error")) {
            passwordField.getStyleClass().add("field-error");
        }
        if (fields.contains("confirmPassword") && !confirmPasswordField.getStyleClass().contains("field-error")) {
            confirmPasswordField.getStyleClass().add("field-error");
        }
    }

    private void clearFieldHighlights() {
        usernameField.getStyleClass().remove("field-error");
        fullNameField.getStyleClass().remove("field-error");
        passwordField.getStyleClass().remove("field-error");
        confirmPasswordField.getStyleClass().remove("field-error");
    }

    private void clearForm() {
        usernameField.clear();
        fullNameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        residenceCombo.getSelectionModel().clearSelection();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showSuccess(String msg) {
        successLabel.setText(msg);
        successLabel.setVisible(true);
        successLabel.setManaged(true);
    }

    private void hideMessages() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        successLabel.setVisible(false);
        successLabel.setManaged(false);
    }

    @FXML private void goLogin() { SceneManager.showBuyerLogin(); }
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
