package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class AdminLoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText() != null ? usernameField.getText().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText() : "";
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        String validationError = authenticateAdmin(username, password);
        if (!"OK".equals(validationError)) {
            showError(validationError);
            return;
        }

        SceneManager.showAdminDashboard();
    }

    public String authenticateAdmin(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return "Please enter credentials.";
        }

        String normalizedUsername = username.trim();
        if (!DummyDataService.SEEDED_ADMIN_USERNAME.equalsIgnoreCase(normalizedUsername)) {
            return "Invalid admin credentials.";
        }

        User user = DummyDataService.getInstance().findUser(normalizedUsername);
        if (user == null || user.getRole() != UserRole.ADMIN) {
            return "Invalid admin credentials.";
        }

        if (!user.isActive()) {
            return "Admin account is deactivated.";
        }

        if (!user.getPasswordHash().equals(password)) {
            return "Incorrect password.";
        }

        SessionManager.login(user);
        return "OK";
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
