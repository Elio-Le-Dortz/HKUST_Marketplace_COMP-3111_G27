package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.AdminProfileUpdateRequest;
import com.hkust.marketplace.models.AdminProfileUpdateResult;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Controller for Administrator Profile Management (US-A01 / Use Case 3.1 Edit Admin Profile).
 *
 * <p>Provides admin profile viewing, full name updates, and password changes conforming to the
 * system password policy with inline error feedback and top-level greeting updates.</p>
 */
public class AdminProfileController {

    private final DummyDataService dataService;
    private final Consumer<String> onFullNameUpdated;

    public AdminProfileController() {
        this(DummyDataService.getInstance(), null);
    }

    public AdminProfileController(Consumer<String> onFullNameUpdated) {
        this(DummyDataService.getInstance(), onFullNameUpdated);
    }

    public AdminProfileController(DummyDataService dataService, Consumer<String> onFullNameUpdated) {
        this.dataService = Objects.requireNonNull(dataService, "dataService cannot be null.");
        this.onFullNameUpdated = onFullNameUpdated;
    }

    public Node createView() {
        String adminUsername;
        try {
            adminUsername = SessionManager.requireAdmin().getUsername();
        } catch (Exception e) {
            adminUsername = DummyDataService.SEEDED_ADMIN_USERNAME;
        }

        Admin admin = dataService.getAdminProfile(adminUsername);
        if (admin == null) {
            User currentUser = SessionManager.getCurrentUser();
            if (currentUser instanceof Admin) {
                admin = (Admin) currentUser;
            } else {
                admin = dataService.getAdminProfile(DummyDataService.SEEDED_ADMIN_USERNAME);
            }
        }

        final Admin currentAdmin = admin;
        if (currentAdmin == null) {
            Label notFoundLabel = new Label("Administrator account not found. Please log in again.");
            notFoundLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 14px; -fx-padding: 20;");
            return notFoundLabel;
        }

        VBox container = new VBox(16);
        container.setPadding(new Insets(20));
        container.setAlignment(Pos.TOP_CENTER);

        VBox form = new VBox(16);
        form.setPadding(new Insets(24));
        form.setMaxWidth(560);
        form.getStyleClass().add("card");

        Label title = new Label("Admin Profile");
        title.getStyleClass().add("section-title");

        Label subtitle = new Label("Manage your administrator account details and security credentials.");
        subtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        // Feedback banner
        Label feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);
        feedbackLabel.setMaxWidth(Double.MAX_VALUE);
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        // Basic Flow step 2: Username (read-only)
        Label usernameTitle = new Label("Admin Username");
        usernameTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        TextField usernameField = new TextField(currentAdmin.getUsername());
        usernameField.setEditable(false);
        usernameField.setDisable(true);
        usernameField.setStyle("-fx-opacity: 0.85; -fx-background-color: #f1f5f9;");
        Label usernameNote = new Label("Admin username cannot be modified.");
        usernameNote.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        // Basic Flow step 2 & 3: Full Name (required)
        Label nameTitle = new Label("Full Name *");
        nameTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        TextField nameField = new TextField(currentAdmin.getFullName() != null ? currentAdmin.getFullName() : "");
        nameField.setPromptText("Enter administrator full name");
        Label nameErrorLabel = new Label();
        nameErrorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
        nameErrorLabel.setVisible(false);
        nameErrorLabel.setManaged(false);

        // Section: Change Password (step 4)
        Separator sep = new Separator();
        VBox pwdSection = new VBox(10);
        Label pwdTitle = new Label("Change Password");
        pwdTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");
        Label pwdDesc = new Label("Leave all password fields blank to keep your existing password.");
        pwdDesc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        Label currPassLabel = new Label("Current Password");
        currPassLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        PasswordField currPassField = new PasswordField();
        currPassField.setPromptText("Enter current password to verify");
        Label currPassErrorLabel = new Label();
        currPassErrorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
        currPassErrorLabel.setVisible(false);
        currPassErrorLabel.setManaged(false);

        Label newPassLabel = new Label("New Password");
        newPassLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        PasswordField newPassField = new PasswordField();
        newPassField.setPromptText("At least 8 chars, 1 uppercase, 1 lowercase, 1 digit");
        Label rulesNote = new Label("Password must contain at least 8 characters, including 1 uppercase, 1 lowercase, and 1 digit.");
        rulesNote.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        Label newPassErrorLabel = new Label();
        newPassErrorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
        newPassErrorLabel.setVisible(false);
        newPassErrorLabel.setManaged(false);

        Label confirmPassLabel = new Label("Confirm New Password");
        confirmPassLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        PasswordField confirmPassField = new PasswordField();
        confirmPassField.setPromptText("Re-enter new password");
        Label confirmPassErrorLabel = new Label();
        confirmPassErrorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
        confirmPassErrorLabel.setVisible(false);
        confirmPassErrorLabel.setManaged(false);

        pwdSection.getChildren().addAll(pwdTitle, pwdDesc,
                currPassLabel, currPassField, currPassErrorLabel,
                newPassLabel, newPassField, rulesNote, newPassErrorLabel,
                confirmPassLabel, confirmPassField, confirmPassErrorLabel);

        // Action buttons
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-outline");

        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("btn-primary");

        buttonBox.getChildren().addAll(cancelBtn, saveBtn);

        // Helper to clear error highlights and labels
        Runnable clearErrors = () -> {
            nameField.getStyleClass().remove("field-error");
            currPassField.getStyleClass().remove("field-error");
            newPassField.getStyleClass().remove("field-error");
            confirmPassField.getStyleClass().remove("field-error");

            nameErrorLabel.setVisible(false);
            nameErrorLabel.setManaged(false);
            nameErrorLabel.setText("");

            currPassErrorLabel.setVisible(false);
            currPassErrorLabel.setManaged(false);
            currPassErrorLabel.setText("");

            newPassErrorLabel.setVisible(false);
            newPassErrorLabel.setManaged(false);
            newPassErrorLabel.setText("");

            confirmPassErrorLabel.setVisible(false);
            confirmPassErrorLabel.setManaged(false);
            confirmPassErrorLabel.setText("");

            feedbackLabel.setVisible(false);
            feedbackLabel.setManaged(false);
            feedbackLabel.setText("");
        };

        // Alternative Flow A4: Cancel changes
        cancelBtn.setOnAction(e -> {
            clearErrors.run();
            nameField.setText(currentAdmin.getFullName() != null ? currentAdmin.getFullName() : "");
            currPassField.clear();
            newPassField.clear();
            confirmPassField.clear();
            feedbackLabel.setText("Changes cancelled.");
            feedbackLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-padding: 8; -fx-background-color: #f1f5f9; -fx-background-radius: 6;");
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        });

        // Save Profile (Basic Flow steps 5-8, Alternative Flows A1, A2, A3)
        saveBtn.setOnAction(e -> {
            clearErrors.run();

            AdminProfileUpdateRequest req = new AdminProfileUpdateRequest(
                    currentAdmin.getUsername(),
                    nameField.getText(),
                    currPassField.getText(),
                    newPassField.getText(),
                    confirmPassField.getText()
            );

            AdminProfileUpdateResult result = dataService.getAdminProfileService()
                    .updateProfile(currentAdmin, req);

            if (result.isSuccess()) {
                Admin updated = result.getUpdatedAdmin();
                if (onFullNameUpdated != null && updated != null) {
                    onFullNameUpdated.accept(updated.getFullName());
                }
                currPassField.clear();
                newPassField.clear();
                confirmPassField.clear();

                feedbackLabel.setText(result.getMessage());
                feedbackLabel.setStyle("-fx-text-fill: #166534; -fx-font-size: 13px; -fx-padding: 10; -fx-background-color: #dcfce7; -fx-background-radius: 6; -fx-font-weight: bold;");
                feedbackLabel.setVisible(true);
                feedbackLabel.setManaged(true);

                Alert alert = new Alert(Alert.AlertType.INFORMATION, result.getMessage());
                alert.setTitle("Success");
                alert.setHeaderText(null);
                alert.showAndWait();
            } else {
                java.util.Set<String> fields = result.getAffectedFields();
                if (fields.contains("fullName")) {
                    nameField.getStyleClass().add("field-error");
                    nameErrorLabel.setText(result.getMessage());
                    nameErrorLabel.setVisible(true);
                    nameErrorLabel.setManaged(true);
                }
                if (fields.contains("currentPassword")) {
                    currPassField.getStyleClass().add("field-error");
                    currPassErrorLabel.setText(result.getMessage());
                    currPassErrorLabel.setVisible(true);
                    currPassErrorLabel.setManaged(true);
                }
                if (fields.contains("newPassword")) {
                    newPassField.getStyleClass().add("field-error");
                    newPassErrorLabel.setText(result.getMessage());
                    newPassErrorLabel.setVisible(true);
                    newPassErrorLabel.setManaged(true);
                }
                if (fields.contains("confirmPassword")) {
                    confirmPassField.getStyleClass().add("field-error");
                    confirmPassErrorLabel.setText(result.getMessage());
                    confirmPassErrorLabel.setVisible(true);
                    confirmPassErrorLabel.setManaged(true);
                }

                feedbackLabel.setText(result.getMessage());
                feedbackLabel.setStyle("-fx-text-fill: #991b1b; -fx-font-size: 13px; -fx-padding: 10; -fx-background-color: #fee2e2; -fx-background-radius: 6; -fx-font-weight: bold;");
                feedbackLabel.setVisible(true);
                feedbackLabel.setManaged(true);

                Alert alert = new Alert(Alert.AlertType.ERROR, result.getMessage());
                alert.setTitle("Profile Update Failed");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });

        form.getChildren().addAll(
                title, subtitle, feedbackLabel,
                usernameTitle, usernameField, usernameNote,
                nameTitle, nameField, nameErrorLabel,
                sep, pwdSection, buttonBox
        );

        container.getChildren().add(form);
        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        return scroll;
    }
}
