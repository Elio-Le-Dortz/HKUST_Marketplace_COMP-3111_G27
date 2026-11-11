package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.AccountStatus;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserEditRequest;
import com.hkust.marketplace.models.UserManagementFilterCriteria;
import com.hkust.marketplace.models.UserManagementResult;
import com.hkust.marketplace.models.UserManagementStatus;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.services.UserManagementService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for User Management (US-A03 / Use Case 3.3 Manage Users).
 *
 * <p>Handles user searching, filtering by role/status, editing profile details,
 * toggling active/deactivated status, and soft-deleting accounts with deal pre-checks
 * and cascading cancellations.</p>
 */
public class AdminUsersController {

    private final DummyDataService dataService;
    private final Runnable refreshCallback;

    public AdminUsersController() {
        this(DummyDataService.getInstance(), null);
    }

    public AdminUsersController(DummyDataService dataService, Runnable refreshCallback) {
        this.dataService = Objects.requireNonNull(dataService, "dataService cannot be null.");
        this.refreshCallback = refreshCallback;
    }

    private void requestRefresh() {
        if (refreshCallback != null) {
            refreshCallback.run();
        }
    }

    private String getEffectiveAdminUsername() {
        return SessionManager.requireAdmin().getUsername();
    }

    public Node createView() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("Search username or full name...");
        searchField.setPrefWidth(240);

        ComboBox<String> roleFilter = new ComboBox<>(FXCollections.observableArrayList("All Roles", "Buyer", "Seller"));
        roleFilter.getSelectionModel().selectFirst();

        ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList("All Statuses", "Active", "Deactivated", "Deleted"));
        statusFilter.getSelectionModel().selectFirst();

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label();
        countLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 14px;");

        filterBar.getChildren().addAll(searchField, roleFilter, statusFilter, refreshBtn, spacer, countLabel);
        root.getChildren().add(filterBar);

        VBox userListContainer = new VBox(10);
        root.getChildren().add(userListContainer);

        Runnable refreshAction = () -> {
            UserRole selectedRole = null;
            String roleVal = roleFilter.getValue();
            if ("Buyer".equalsIgnoreCase(roleVal)) selectedRole = UserRole.BUYER;
            else if ("Seller".equalsIgnoreCase(roleVal)) selectedRole = UserRole.SELLER;

            AccountStatus selectedStatus = null;
            String statusVal = statusFilter.getValue();
            if ("Active".equalsIgnoreCase(statusVal)) selectedStatus = AccountStatus.ACTIVE;
            else if ("Deactivated".equalsIgnoreCase(statusVal)) selectedStatus = AccountStatus.DEACTIVATED;
            else if ("Deleted".equalsIgnoreCase(statusVal)) selectedStatus = AccountStatus.DELETED;

            UserManagementFilterCriteria criteria = new UserManagementFilterCriteria(
                    searchField.getText(), selectedRole, selectedStatus
            );

            List<User> users = dataService.getUserManagementService().getUsers(criteria);
            countLabel.setText("Users (" + users.size() + ")");
            renderUsers(userListContainer, users, () -> {
                List<User> refreshed = dataService.getUserManagementService().getUsers(criteria);
                countLabel.setText("Users (" + refreshed.size() + ")");
                renderUsers(userListContainer, refreshed, null);
            });
        };

        searchField.textProperty().addListener((obs, oldV, newV) -> refreshAction.run());
        roleFilter.valueProperty().addListener((obs, oldV, newV) -> refreshAction.run());
        statusFilter.valueProperty().addListener((obs, oldV, newV) -> refreshAction.run());
        refreshBtn.setOnAction(e -> refreshAction.run());

        refreshAction.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private void renderUsers(VBox container, List<User> users, Runnable refreshCb) {
        container.getChildren().clear();

        if (users.isEmpty()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40, 20, 40, 20));
            emptyBox.getStyleClass().add("card");
            Label emptyTitle = new Label("No Users Found");
            emptyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #5a5a7a;");
            Label emptySub = new Label("No buyer or seller accounts matched the search and filter criteria.");
            emptySub.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 13px;");
            emptyBox.getChildren().addAll(emptyTitle, emptySub);
            container.getChildren().add(emptyBox);
            return;
        }

        for (User u : users) {
            container.getChildren().add(createUserRowCard(u, refreshCb));
        }
    }

    private HBox createUserRowCard(User u, Runnable refreshCb) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox topInfo = new HBox(8);
        topInfo.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(u.getFullName() + " (@" + u.getUsername() + ")");
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label roleBadge = new Label(u.getRole().name());
        roleBadge.getStyleClass().addAll("badge", u.getRole() == UserRole.SELLER ? "badge-sell" : "badge-lend");

        Label statusBadge = new Label(u.getAccountStatus().name());
        String statusStyle = switch (u.getAccountStatus()) {
            case ACTIVE -> "badge-completed";
            case DEACTIVATED -> "badge-rejected";
            case DELETED -> "badge-withdrawn";
        };
        statusBadge.getStyleClass().addAll("badge", statusStyle);
        topInfo.getChildren().addAll(name, roleBadge, statusBadge);

        String detailText;
        if (u instanceof Buyer b) {
            detailText = "Residence: " + (b.getResidence() != null && !b.getResidence().isEmpty() ? b.getResidence() : "Not specified");
        } else if (u instanceof Seller s) {
            detailText = "Meetup: " + (s.getPreferredMeetup() != null && !s.getPreferredMeetup().isEmpty() ? s.getPreferredMeetup() : "Not specified") +
                    (s.getBio() != null && !s.getBio().isEmpty() ? "  ·  Bio: " + s.getBio() : "");
        } else {
            detailText = "";
        }
        Label details = new Label(detailText);
        details.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        info.getChildren().addAll(topInfo, details);

        row.getChildren().add(info);

        if (u.isDeleted()) {
            Label deletedNotice = new Label("Account Deleted");
            deletedNotice.setStyle("-fx-text-fill: #9aa0b0; -fx-font-style: italic; -fx-font-size: 12px;");
            row.getChildren().add(deletedNotice);
        } else {
            Button editBtn = new Button("Edit");
            editBtn.getStyleClass().addAll("btn-outline", "btn-sm");
            editBtn.setOnAction(e -> handleEditUser(u, refreshCb));

            boolean isActive = u.isActive();
            Button toggleBtn = new Button(isActive ? "Deactivate" : "Reactivate");
            toggleBtn.getStyleClass().addAll(isActive ? "btn-danger" : "btn-success", "btn-sm");
            toggleBtn.setOnAction(e -> handleToggleStatus(u, refreshCb));

            Button deleteBtn = new Button("Delete");
            deleteBtn.getStyleClass().addAll("btn-danger", "btn-sm");
            deleteBtn.setOnAction(e -> handleDeleteUser(u, refreshCb));

            row.getChildren().addAll(editBtn, toggleBtn, deleteBtn);
        }

        return row;
    }

    public void handleEditUser(User u, Runnable refreshCb) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User Profile");
        dialog.setHeaderText("Update account details for @" + u.getUsername());

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField usernameField = new TextField(u.getUsername());
        usernameField.setEditable(false);
        usernameField.setDisable(true);

        TextField nameField = new TextField(u.getFullName());

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Full Name:"), 0, 1);
        grid.add(nameField, 1, 1);

        TextField residenceField = null;
        TextField bioField = null;
        ComboBox<MeetupLocation> meetupCombo = null;

        if (u instanceof Buyer b) {
            residenceField = new TextField(b.getResidence() != null ? b.getResidence() : "");
            grid.add(new Label("Residence:"), 0, 2);
            grid.add(residenceField, 1, 2);
        } else if (u instanceof Seller s) {
            bioField = new TextField(s.getBio() != null ? s.getBio() : "");
            meetupCombo = new ComboBox<>(FXCollections.observableArrayList(MeetupLocation.values()));
            if (!s.getPreferredMeetups().isEmpty()) {
                meetupCombo.getSelectionModel().select(s.getPreferredMeetups().get(0));
            } else {
                meetupCombo.getSelectionModel().selectFirst();
            }
            grid.add(new Label("Bio:"), 0, 2);
            grid.add(bioField, 1, 2);
            grid.add(new Label("Meetup Point:"), 0, 3);
            grid.add(meetupCombo, 1, 3);
        }

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            String newName = nameField.getText() != null ? nameField.getText().trim() : "";
            if (newName.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Full name is required to update a user account.").showAndWait();
                return;
            }

            UserEditRequest.Builder builder = UserEditRequest.builder()
                    .targetUsername(u.getUsername())
                    .adminUsername(getEffectiveAdminUsername())
                    .newFullName(newName);

            if (residenceField != null) {
                builder.residence(residenceField.getText().trim());
            }
            if (bioField != null) {
                builder.bio(bioField.getText().trim());
            }
            if (meetupCombo != null && meetupCombo.getValue() != null) {
                builder.preferredMeetups(List.of(meetupCombo.getValue()));
            }

            UserManagementResult result = dataService.getUserManagementService().editUser(builder.build());

            if (result.isSuccess()) {
                new Alert(Alert.AlertType.INFORMATION, result.getMessage()).showAndWait();
                if (refreshCb != null) refreshCb.run();
            } else {
                new Alert(Alert.AlertType.ERROR, result.getMessage()).showAndWait();
            }
        }
    }

    public void handleToggleStatus(User u, Runnable refreshCb) {
        boolean deactivating = u.isActive();
        AccountStatus targetStatus = deactivating ? AccountStatus.DEACTIVATED : AccountStatus.ACTIVE;
        String actionName = deactivating ? "Deactivate" : "Reactivate";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to " + actionName.toLowerCase() + " user @" + u.getUsername() + "?\n\n" +
                (deactivating ? "The user's active session will be ended immediately, and they will not be able to log in until reactivated."
                        : "The user's account will be set to Active, allowing them to log in again."));
        confirm.setTitle(actionName + " User");
        confirm.setHeaderText(actionName + " @" + u.getUsername());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                UserManagementResult result = dataService.getUserManagementService()
                        .changeAccountStatus(u.getUsername(), targetStatus, getEffectiveAdminUsername());
                if (result.isSuccess()) {
                    new Alert(Alert.AlertType.INFORMATION, result.getMessage()).showAndWait();
                    if (refreshCb != null) refreshCb.run();
                } else {
                    new Alert(Alert.AlertType.ERROR, result.getMessage()).showAndWait();
                }
            }
        });
    }

    public void handleDeleteUser(User u, Runnable refreshCb) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Soft-Delete User Account");
        confirm.setHeaderText("Warning: Soft-delete user @" + u.getUsername() + "?");
        confirm.setContentText(
                "This action soft-deletes the user account:\n\n" +
                "• The user's active session will be terminated immediately.\n" +
                (u.getRole() == UserRole.SELLER ?
                "• All pending review and live listings by this seller will be removed.\n• All pending offers on them will be declined.\n" :
                "• All pending offers submitted by this buyer will be withdrawn.\n") +
                "• Historical deals, chats, and reviews are preserved for audit.\n" +
                "• The username '@" + u.getUsername() + "' will stay reserved.\n\n" +
                "Do you want to proceed?"
        );

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                UserManagementResult result = dataService.getUserManagementService()
                        .deleteUser(u.getUsername(), getEffectiveAdminUsername());

                if (result.isSuccess()) {
                    new Alert(Alert.AlertType.INFORMATION,
                            result.getMessage() + "\n" +
                            (result.getAffectedListingsCount() > 0 ? "Listings removed: " + result.getAffectedListingsCount() + "\n" : "") +
                            (result.getAffectedOffersCount() > 0 ? "Offers updated: " + result.getAffectedOffersCount() : "")
                    ).showAndWait();
                    if (refreshCb != null) refreshCb.run();
                } else if (result.getStatus() == UserManagementStatus.ACTIVE_DEALS_EXIST) {
                    String dealIds = result.getActiveDeals().stream()
                            .map(Deal::getId)
                            .collect(Collectors.joining(", "));
                    Alert dealAlert = new Alert(Alert.AlertType.WARNING);
                    dealAlert.setTitle("Deletion Blocked");
                    dealAlert.setHeaderText("User Has Active Deals");
                    dealAlert.setContentText(
                            result.getMessage() + "\n\nActive Deals: [" + dealIds + "]\n\n" +
                            "Active transactions must be completed or returned first. Consider deactivating the account instead."
                    );
                    dealAlert.showAndWait();
                } else {
                    new Alert(Alert.AlertType.ERROR, result.getMessage()).showAndWait();
                }
            }
        });
    }
}
