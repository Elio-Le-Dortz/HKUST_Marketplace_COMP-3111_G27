package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.services.RegistrationField;
import com.hkust.marketplace.services.RegistrationResult;
import com.hkust.marketplace.services.SellerRegistrationRequest;
import com.hkust.marketplace.services.SellerRegistrationService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Controller of the seller registration screen (use case 2.1 "Register As Seller", US-S01).
 *
 * <p>Validation and storage are done by {@link SellerRegistrationService}; this class
 * only reads the form, highlights the invalid fields with their messages, and
 * navigates. Leaving the screen through the login or welcome links cancels the
 * registration without creating an account (alternative flow A5).</p>
 */
public class SellerRegisterController {

    /** Style class of a field that failed validation (defined in {@code styles.css}). */
    private static final String FIELD_ERROR_CLASS = "field-error";

    /** Username input (required). */
    @FXML private TextField usernameField;
    /** Full name input (required). */
    @FXML private TextField fullNameField;
    /** Password input (required). */
    @FXML private PasswordField passwordField;
    /** Confirm password input (required). */
    @FXML private PasswordField confirmPasswordField;
    /** Container of the meet-up location check boxes (optional choices). */
    @FXML private FlowPane meetupPane;
    /** Seller bio input (optional). */
    @FXML private TextArea bioArea;
    /** Shows the validation errors of the last submission. */
    @FXML private Label errorLabel;

    /** Validates and stores the registration. */
    private final SellerRegistrationService registrationService = new SellerRegistrationService();
    /** Check box of each meet-up location. */
    private final Map<MeetupLocation, CheckBox> meetupBoxes = new EnumMap<>(MeetupLocation.class);
    /** Input of each validated field, used to highlight the invalid ones. */
    private final Map<RegistrationField, TextInputControl> fieldInputs = new EnumMap<>(RegistrationField.class);

    /**
     * Creates the controller. Called by the FXML loader before it injects the fields.
     */
    public SellerRegisterController() {
        // Fields are injected by the FXML loader, then initialize() is called.
    }

    /**
     * Adds one check box per meet-up location and links each validated field to its
     * input. Called by the FXML loader once the fields are injected.
     */
    @FXML
    public void initialize() {
        for (MeetupLocation location : MeetupLocation.values()) {
            CheckBox box = new CheckBox(location.getDisplayName());
            meetupBoxes.put(location, box);
            meetupPane.getChildren().add(box);
        }
        fieldInputs.put(RegistrationField.USERNAME, usernameField);
        fieldInputs.put(RegistrationField.FULL_NAME, fullNameField);
        fieldInputs.put(RegistrationField.PASSWORD, passwordField);
        fieldInputs.put(RegistrationField.CONFIRM_PASSWORD, confirmPasswordField);
        // Remove a field's highlight as soon as the user starts correcting it.
        fieldInputs.values().forEach(input -> input.textProperty().addListener(
                (observable, oldText, newText) -> input.getStyleClass().removeAll(FIELD_ERROR_CLASS)));
    }

    /**
     * Submits the form (basic flow, step 4). On success, tells the seller that the
     * account was created and shows the seller login screen (step 7). Otherwise,
     * highlights every invalid field and lists all the errors.
     */
    @FXML
    private void handleRegister() {
        clearErrors();
        RegistrationResult<Seller> result = registrationService.register(new SellerRegistrationRequest(
                usernameField.getText(), fullNameField.getText(),
                passwordField.getText(), confirmPasswordField.getText(),
                selectedMeetups(), bioArea.getText()));
        if (!result.isSuccess()) {
            showErrors(result);
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Your seller account \""
                + result.getUser().getUsername() + "\" has been created. You can now log in.");
        alert.initOwner(SceneManager.getPrimaryStage());
        alert.setTitle("Seller Registration");
        alert.setHeaderText("Registration successful");
        alert.showAndWait();
        SceneManager.showSellerLogin();
    }

    /**
     * Returns the meet-up locations whose check box is ticked.
     *
     * @return the selected locations, in display order
     */
    private List<MeetupLocation> selectedMeetups() {
        return meetupBoxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Highlights every invalid field, shows all the error messages and moves the
     * focus to the first invalid field.
     *
     * @param result the failed registration result
     */
    private void showErrors(RegistrationResult<Seller> result) {
        result.getErrors().keySet().forEach(field -> fieldInputs.get(field).getStyleClass().add(FIELD_ERROR_CLASS));
        errorLabel.setText(String.join("\n", result.getAllMessages()));
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        fieldInputs.get(result.getErrors().keySet().iterator().next()).requestFocus();
    }

    /**
     * Removes the highlights and the error message of a previous submission.
     */
    private void clearErrors() {
        fieldInputs.values().forEach(input -> input.getStyleClass().removeAll(FIELD_ERROR_CLASS));
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /**
     * Cancels the registration and shows the seller login screen.
     */
    @FXML private void goLogin() { SceneManager.showSellerLogin(); }

    /**
     * Cancels the registration and shows the welcome screen.
     */
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
