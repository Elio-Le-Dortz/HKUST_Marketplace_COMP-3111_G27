package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.LoginErrorType;
import com.hkust.marketplace.models.LoginResult;
import com.hkust.marketplace.services.SellerLoginService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller of the seller login screen (use case 2.2 "Log In As Seller", US-S02).
 *
 * <p>Credential, role and account status checks are done by {@link SellerLoginService};
 * this class only reads the form, highlights the fields named by the result, shows its
 * message and navigates. Pressing Enter in either field submits the form.</p>
 */
public class SellerLoginController {

    /** Style class of a field that failed validation (defined in {@code styles.css}). */
    private static final String FIELD_ERROR_CLASS = "field-error";

    /** Username input. */
    @FXML private TextField usernameField;
    /** Password input. */
    @FXML private PasswordField passwordField;
    /** Shows the message of the last failed attempt. */
    @FXML private Label errorLabel;

    /** Checks the credentials and starts the Seller session. */
    private final SellerLoginService loginService = new SellerLoginService();
    /** Input of each field named by {@link SellerLoginService}, in focus order. */
    private final Map<String, TextInputControl> fieldInputs = new LinkedHashMap<>();

    /**
     * Creates the controller. Called by the FXML loader before it injects the fields.
     */
    public SellerLoginController() {
        // Fields are injected by the FXML loader, then initialize() is called.
    }

    /**
     * Links each field name to its input and removes a field's highlight as soon as the
     * seller starts correcting it. Called by the FXML loader once the fields are injected.
     */
    @FXML
    public void initialize() {
        fieldInputs.put(SellerLoginService.FIELD_USERNAME, usernameField);
        fieldInputs.put(SellerLoginService.FIELD_PASSWORD, passwordField);
        fieldInputs.values().forEach(input -> input.textProperty().addListener(
                (observable, oldText, newText) -> input.getStyleClass().removeAll(FIELD_ERROR_CLASS)));
    }

    /**
     * Submits the credentials (basic flow, step 3). On success the Seller session is
     * started and the Seller dashboard is shown (step 6); otherwise the message of the
     * alternative flow is shown and the fields concerned are highlighted.
     */
    @FXML
    private void handleLogin() {
        clearErrors();
        LoginResult result = loginService.login(usernameField.getText(), passwordField.getText());
        if (result.isSuccess()) {
            SceneManager.showSellerDashboard();
            return;
        }
        if (result.getErrorType() == LoginErrorType.INVALID_CREDENTIALS) {
            // Cleared before highlighting: clearing the text fires the listener that
            // removes the highlight.
            passwordField.clear();
        }
        showErrors(result);
    }

    /**
     * Highlights the fields named by a failed result, shows its message and moves the
     * focus to the first highlighted field.
     *
     * @param result the failed login result
     */
    private void showErrors(LoginResult result) {
        TextInputControl firstInvalid = null;
        for (Map.Entry<String, TextInputControl> entry : fieldInputs.entrySet()) {
            if (result.hasFieldError(entry.getKey())) {
                entry.getValue().getStyleClass().add(FIELD_ERROR_CLASS);
                if (firstInvalid == null) {
                    firstInvalid = entry.getValue();
                }
            }
        }
        errorLabel.setText(result.getMessage());
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        if (firstInvalid != null) {
            firstInvalid.requestFocus();
        }
    }

    /**
     * Removes the highlights and the message of a previous attempt.
     */
    private void clearErrors() {
        fieldInputs.values().forEach(input -> input.getStyleClass().removeAll(FIELD_ERROR_CLASS));
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /**
     * Leaves the login screen for the seller registration screen.
     */
    @FXML private void goRegister() { SceneManager.showSellerRegister(); }

    /**
     * Leaves the login screen for the welcome screen.
     */
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
