package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.LoginErrorType;
import com.hkust.marketplace.models.LoginResult;
import com.hkust.marketplace.services.AdminLoginService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Controller for the Admin Login screen (use case 3.1 "Log In As Admin", US-A01).
 *
 * <p>Credential, role and account status checks are handled by {@link AdminLoginService};
 * this controller reads inputs, updates the view, displays error messages and navigates.</p>
 */
public class AdminLoginController {

    private static final String FIELD_ERROR_CLASS = "field-error";

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private final AdminLoginService loginService;
    private final Map<String, TextInputControl> fieldInputs = new LinkedHashMap<>();

    public AdminLoginController() {
        this(new AdminLoginService());
    }

    public AdminLoginController(AdminLoginService loginService) {
        this.loginService = Objects.requireNonNull(loginService, "loginService");
    }

    @FXML
    public void initialize() {
        if (usernameField != null) {
            fieldInputs.put(AdminLoginService.FIELD_USERNAME, usernameField);
        }
        if (passwordField != null) {
            fieldInputs.put(AdminLoginService.FIELD_PASSWORD, passwordField);
        }
        for (TextInputControl input : fieldInputs.values()) {
            input.textProperty().addListener(
                    (observable, oldText, newText) -> {
                        input.getStyleClass().removeAll(FIELD_ERROR_CLASS);
                        hideError();
                    });
        }
    }

    @FXML
    private void handleLogin() {
        clearErrors();
        String username = usernameField != null ? usernameField.getText() : "";
        String password = passwordField != null ? passwordField.getText() : "";

        LoginResult result = loginService.login(username, password);
        if (result.isSuccess()) {
            clearForm();
            SceneManager.showAdminDashboard();
            return;
        }

        if (result.getErrorType() == LoginErrorType.INVALID_CREDENTIALS && passwordField != null) {
            passwordField.clear();
        }
        showErrors(result);
    }

    /**
     * Programmatic authentication helper for test verification.
     *
     * @param username the entered username
     * @param password the entered password
     * @return "OK" on success, or the user-facing error message on failure
     */
    public String authenticateAdmin(String username, String password) {
        LoginResult result = loginService.login(username, password);
        if (result.isSuccess()) {
            return "OK";
        }
        return result.getMessage();
    }

    private void clearErrors() {
        hideError();
        for (TextInputControl input : fieldInputs.values()) {
            input.getStyleClass().removeAll(FIELD_ERROR_CLASS);
        }
    }

    private void hideError() {
        if (errorLabel != null) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            errorLabel.setText("");
        }
    }

    private void showErrors(LoginResult result) {
        TextInputControl firstInvalid = null;
        for (Map.Entry<String, TextInputControl> entry : fieldInputs.entrySet()) {
            if (result.hasFieldError(entry.getKey())) {
                if (!entry.getValue().getStyleClass().contains(FIELD_ERROR_CLASS)) {
                    entry.getValue().getStyleClass().add(FIELD_ERROR_CLASS);
                }
                if (firstInvalid == null) {
                    firstInvalid = entry.getValue();
                }
            }
        }
        if (errorLabel != null) {
            errorLabel.setText(result.getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
        if (firstInvalid != null) {
            firstInvalid.requestFocus();
        }
    }

    private void clearForm() {
        if (usernameField != null) usernameField.clear();
        if (passwordField != null) passwordField.clear();
    }

    @FXML
    private void goWelcome() {
        SceneManager.showWelcome();
    }
}
