package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

public class AdminDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview, btnAllListings, btnUsers, btnDeals, btnRatings, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) welcomeLabel.setText("Admin: " + user.getFullName());
        currentActiveBtn = btnReview;
        showReview();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    private String getEffectiveAdminUsername() {
        User user = SessionManager.getCurrentUser();
        if (user != null && user.getUsername() != null && !user.getUsername().trim().isEmpty()) {
            return user.getUsername().trim();
        }
        return "admin";
    }

    @FXML
    private void showReview() {
        setActive(btnReview, "Listing Review & Approval");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        List<Listing> pending = DummyDataService.getInstance()
                .getListingReviewService().getPendingListingsOldestFirst();

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label countLabel = new Label("Review Queue (" + pending.size() + " awaiting decision)");
        countLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #003974;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refreshBtn = new Button("Refresh Queue");
        refreshBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        refreshBtn.setOnAction(e -> showReview());
        header.getChildren().addAll(countLabel, spacer, refreshBtn);
        root.getChildren().add(header);

        if (pending.isEmpty()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40, 20, 40, 20));
            emptyBox.getStyleClass().add("card");
            Label emptyTitle = new Label("No Listings Awaiting Review");
            emptyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2e7d32;");
            Label emptySub = new Label("All seller submissions have been reviewed. New submissions will appear here.");
            emptySub.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
            emptyBox.getChildren().addAll(emptyTitle, emptySub);
            root.getChildren().add(emptyBox);
        } else {
            for (Listing l : pending) {
                root.getChildren().add(createPendingCard(l));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox createPendingCard(Listing l) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("card", "card-hover");
        card.setPadding(new Insets(16));

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(l.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        HBox.setHgrow(title, Priority.ALWAYS);

        String typeStyle = switch (l.getType()) {
            case SELL -> "badge-sell";
            case LEND -> "badge-lend";
            case GIVEAWAY -> "badge-giveaway";
        };
        Label typeBadge = new Label(l.getTypeDisplay());
        typeBadge.getStyleClass().addAll("badge", typeStyle);

        Label statusBadge = new Label("Pending Review");
        statusBadge.getStyleClass().addAll("badge", "badge-warning");
        topRow.getChildren().addAll(title, typeBadge, statusBadge);

        Label meta = new Label(String.format("Seller: %s (@%s)  ·  Category: %s  ·  Condition: %s  ·  Meetup: %s",
                l.getSellerFullName(), l.getSellerUsername(),
                l.getCategory() != null ? l.getCategory().getDisplayName() : "-",
                l.getCondition() != null ? l.getCondition().getDisplayName() : "-",
                l.getMeetupLocation() != null ? l.getMeetupLocation().getDisplayName() : "-"));
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        int photoCount = l.getPhotos() != null ? l.getPhotos().size() : 0;
        String photoText = photoCount > 0 ? "📷 " + photoCount + " photo" + (photoCount > 1 ? "s" : "") : "📷 No photos";
        String dateText = l.getPostedAt() != null
                ? l.getPostedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                : "Unknown";
        Label submitted = new Label(String.format("Submitted: %s  ·  %s", dateText, photoText));
        submitted.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button viewDetails = new Button("View Details");
        viewDetails.getStyleClass().addAll("btn-outline", "btn-sm");
        viewDetails.setOnAction(e -> showListingDetails(l));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button approve = new Button("Approve");
        approve.getStyleClass().addAll("btn-success", "btn-sm");
        approve.setOnAction(e -> handleApprove(l));

        Button reject = new Button("Reject");
        reject.getStyleClass().addAll("btn-danger", "btn-sm");
        reject.setOnAction(e -> handleReject(l));

        actions.getChildren().addAll(viewDetails, spacer, approve, reject);
        card.getChildren().addAll(topRow, meta, submitted, actions);
        return card;
    }

    private void handleApprove(Listing l) {
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Confirm Listing Approval");
        conf.setHeaderText("Approve listing '" + l.getTitle() + "'?");
        conf.setContentText("Once approved, this listing will become immediately Live and visible to buyers in the marketplace.");

        Optional<ButtonType> result = conf.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String adminUsername = getEffectiveAdminUsername();
            ListingReviewResult reviewResult = DummyDataService.getInstance()
                    .getListingReviewService().approveListing(l.getId(), adminUsername);

            if (reviewResult.isSuccess()) {
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Listing Approved");
                info.setHeaderText(null);
                info.setContentText("Listing '" + l.getTitle() + "' is now Live! Seller @" + l.getSellerUsername() + " has been notified.");
                info.showAndWait();
            } else if (reviewResult.getStatus() == ListingReviewStatus.NOT_PENDING_REVIEW ||
                       reviewResult.getStatus() == ListingReviewStatus.LISTING_NOT_FOUND) {
                Alert warn = new Alert(Alert.AlertType.WARNING);
                warn.setTitle("Listing Status Changed");
                warn.setHeaderText("Listing is no longer pending review");
                warn.setContentText(reviewResult.getMessage());
                warn.showAndWait();
            } else {
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Approval Error");
                err.setHeaderText("Could not approve listing");
                err.setContentText(reviewResult.getMessage());
                err.showAndWait();
            }
            showReview();
        }
    }

    private void handleReject(Listing l) {
        while (true) {
            TextInputDialog reasonDialog = new TextInputDialog();
            reasonDialog.setTitle("Reject Listing");
            reasonDialog.setHeaderText("Provide a written rejection reason for '" + l.getTitle() + "'");
            reasonDialog.setContentText("Reason (mandatory):");

            Optional<String> result = reasonDialog.showAndWait();
            if (result.isEmpty()) {
                return;
            }

            String reason = result.get().trim();
            if (reason.isEmpty()) {
                Alert warn = new Alert(Alert.AlertType.WARNING);
                warn.setTitle("Rejection Reason Required");
                warn.setHeaderText("A written reason is required");
                warn.setContentText("Please provide a reason so the seller knows why the listing was rejected and how to fix it.");
                warn.showAndWait();
                continue;
            }

            String adminUsername = getEffectiveAdminUsername();
            ListingReviewResult reviewResult = DummyDataService.getInstance()
                    .getListingReviewService().rejectListing(l.getId(), adminUsername, reason);

            if (reviewResult.isSuccess()) {
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Listing Rejected");
                info.setHeaderText(null);
                info.setContentText("Listing '" + l.getTitle() + "' has been rejected. The written reason was saved and seller @" + l.getSellerUsername() + " notified.");
                info.showAndWait();
            } else if (reviewResult.getStatus() == ListingReviewStatus.NOT_PENDING_REVIEW ||
                       reviewResult.getStatus() == ListingReviewStatus.LISTING_NOT_FOUND) {
                Alert warn = new Alert(Alert.AlertType.WARNING);
                warn.setTitle("Listing Status Changed");
                warn.setHeaderText("Listing is no longer pending review");
                warn.setContentText(reviewResult.getMessage());
                warn.showAndWait();
            } else {
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Rejection Error");
                err.setHeaderText("Could not reject listing");
                err.setContentText(reviewResult.getMessage());
                err.showAndWait();
            }
            showReview();
            break;
        }
    }

    private void showListingDetails(Listing l) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Listing Submission Details");
        dialog.setHeaderText("Reviewing Submission: " + l.getTitle());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(12);
        content.setPrefWidth(540);
        content.setPadding(new Insets(12));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);

        grid.add(new Label("Listing ID:"), 0, 0);
        grid.add(new Label(l.getId() != null ? l.getId() : "-"), 1, 0);

        grid.add(new Label("Status:"), 0, 1);
        Label statusLbl = new Label(l.getStatus() != null ? l.getStatus().name() : "PENDING_REVIEW");
        statusLbl.getStyleClass().addAll("badge", "badge-warning");
        grid.add(statusLbl, 1, 1);

        grid.add(new Label("Seller:"), 0, 2);
        grid.add(new Label(l.getSellerFullName() + " (@" + l.getSellerUsername() + ")"), 1, 2);

        grid.add(new Label("Category:"), 0, 3);
        grid.add(new Label(l.getCategory() != null ? l.getCategory().getDisplayName() : "-"), 1, 3);

        grid.add(new Label("Type & Terms:"), 0, 4);
        grid.add(new Label(l.getTypeDisplay()), 1, 4);

        grid.add(new Label("Condition:"), 0, 5);
        grid.add(new Label(l.getCondition() != null ? l.getCondition().getDisplayName() : "-"), 1, 5);

        grid.add(new Label("Meetup Point:"), 0, 6);
        grid.add(new Label(l.getMeetupLocation() != null ? l.getMeetupLocation().getDisplayName() : "-"), 1, 6);

        grid.add(new Label("Submitted:"), 0, 7);
        grid.add(new Label(l.getPostedAt() != null ? l.getPostedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")) : "-"), 1, 7);

        grid.add(new Label("Photos:"), 0, 8);
        int photoCount = l.getPhotos() != null ? l.getPhotos().size() : 0;
        grid.add(new Label(photoCount + " photo(s) attached"), 1, 8);

        Label descHeader = new Label("Description:");
        descHeader.setStyle("-fx-font-weight: bold;");
        TextArea descArea = new TextArea(l.getDescription() != null ? l.getDescription() : "No description provided.");
        descArea.setEditable(false);
        descArea.setWrapText(true);
        descArea.setPrefRowCount(4);

        HBox dialogActions = new HBox(10);
        dialogActions.setAlignment(Pos.CENTER_RIGHT);
        Button approveBtn = new Button("Approve Listing");
        approveBtn.getStyleClass().addAll("btn-success", "btn-sm");
        approveBtn.setOnAction(e -> {
            dialog.close();
            handleApprove(l);
        });

        Button rejectBtn = new Button("Reject Listing");
        rejectBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        rejectBtn.setOnAction(e -> {
            dialog.close();
            handleReject(l);
        });
        dialogActions.getChildren().addAll(approveBtn, rejectBtn);

        content.getChildren().addAll(grid, descHeader, descArea, dialogActions);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    @FXML
    private void showAllListings() {
        setActive(btnAllListings, "Manage All Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        Button addBtn = new Button("+ Add Listing (on behalf of seller)");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Admin can create a listing on behalf of a seller here.\nSame form fields as Seller Create Listing.\n(UI placeholder)").showAndWait());
        root.getChildren().add(addBtn);

        for (Listing l : DummyDataService.getInstance().getAllListings()) {
            HBox row = new HBox(12);
            row.getStyleClass().add("card");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));

            VBox info = new VBox(3);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label t = new Label(l.getTitle());
            t.setStyle("-fx-font-weight: bold;");
            Label m = new Label(l.getSellerFullName() + " · " + l.getTypeDisplay() + " · " + l.getStatus());
            m.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
            info.getChildren().addAll(t, m);

            Button edit = new Button("Edit");
            edit.getStyleClass().addAll("btn-outline", "btn-sm");
            edit.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Edit listing dialog (demo).").showAndWait());

            Button remove = new Button("Remove");
            remove.getStyleClass().addAll("btn-danger", "btn-sm");
            remove.setOnAction(e -> {
                Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Permanently remove this listing?");
                conf.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        DummyDataService.getInstance().getBuyerNotificationService()
                                .notifyBuyersOfListingRemoval(l, "Removed by Administrator");
                        DummyDataService.getInstance().removeListing(l.getId());
                        showAllListings();
                    }
                });
            });

            row.getChildren().addAll(info, edit, remove);
            root.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showUsers() {
        setActive(btnUsers, "Manage All Users");
        contentPane.getChildren().clear();

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

            List<User> users = DummyDataService.getInstance().getUserManagementService().getUsers(criteria);
            countLabel.setText("Users (" + users.size() + ")");
            renderUsers(userListContainer, users, () -> {
                List<User> refreshed = DummyDataService.getInstance().getUserManagementService().getUsers(criteria);
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
        contentPane.getChildren().add(scroll);
    }

    private void renderUsers(VBox container, List<User> users, Runnable refreshCallback) {
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
            container.getChildren().add(createUserRowCard(u, refreshCallback));
        }
    }

    private HBox createUserRowCard(User u, Runnable refreshCallback) {
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
            editBtn.setOnAction(e -> handleEditUser(u, refreshCallback));

            boolean isActive = u.isActive();
            Button toggleBtn = new Button(isActive ? "Deactivate" : "Reactivate");
            toggleBtn.getStyleClass().addAll(isActive ? "btn-danger" : "btn-success", "btn-sm");
            toggleBtn.setOnAction(e -> handleToggleStatus(u, refreshCallback));

            Button deleteBtn = new Button("Delete");
            deleteBtn.getStyleClass().addAll("btn-danger", "btn-sm");
            deleteBtn.setOnAction(e -> handleDeleteUser(u, refreshCallback));

            row.getChildren().addAll(editBtn, toggleBtn, deleteBtn);
        }

        return row;
    }

    private void handleEditUser(User u, Runnable refreshCallback) {
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

            UserManagementResult result = DummyDataService.getInstance()
                    .getUserManagementService().editUser(builder.build());

            if (result.isSuccess()) {
                new Alert(Alert.AlertType.INFORMATION, result.getMessage()).showAndWait();
                if (refreshCallback != null) refreshCallback.run();
            } else {
                new Alert(Alert.AlertType.ERROR, result.getMessage()).showAndWait();
            }
        }
    }

    private void handleToggleStatus(User u, Runnable refreshCallback) {
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
                UserManagementResult result = DummyDataService.getInstance()
                        .getUserManagementService().changeAccountStatus(u.getUsername(), targetStatus, getEffectiveAdminUsername());
                if (result.isSuccess()) {
                    new Alert(Alert.AlertType.INFORMATION, result.getMessage()).showAndWait();
                    if (refreshCallback != null) refreshCallback.run();
                } else {
                    new Alert(Alert.AlertType.ERROR, result.getMessage()).showAndWait();
                }
            }
        });
    }

    private void handleDeleteUser(User u, Runnable refreshCallback) {
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
                UserManagementResult result = DummyDataService.getInstance()
                        .getUserManagementService().deleteUser(u.getUsername(), getEffectiveAdminUsername());

                if (result.isSuccess()) {
                    new Alert(Alert.AlertType.INFORMATION,
                            result.getMessage() + "\n" +
                            (result.getAffectedListingsCount() > 0 ? "Listings removed: " + result.getAffectedListingsCount() + "\n" : "") +
                            (result.getAffectedOffersCount() > 0 ? "Offers updated: " + result.getAffectedOffersCount() : "")
                    ).showAndWait();
                    if (refreshCallback != null) refreshCallback.run();
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

    @FXML
    private void showDeals() {
        setActive(btnDeals, "All Deals Record");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search by item / user...");
        ComboBox<String> typeFilter = new ComboBox<>(FXCollections.observableArrayList("All Types", "Sell", "Lend", "Giveaway"));
        typeFilter.getSelectionModel().selectFirst();
        filters.getChildren().addAll(search, typeFilter);
        root.getChildren().add(filters);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        String[] headers = {"Item", "Buyer", "Seller", "Type", "Status", "Date"};
        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<String[], String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[idx]));
            table.getColumns().add(col);
        }
        table.setItems(FXCollections.observableArrayList(
                new String[]{"Calculus Textbook", "alice", "charlie", "Sell", "Accepted", "2026-01-15"},
                new String[]{"USB Hub", "bob", "diana", "Giveaway", "Completed", "2025-12-01"}
        ));
        root.getChildren().add(table);
        contentPane.getChildren().add(root);
    }

    @FXML
    private void showRatings() {
        setActive(btnRatings, "Seller Ratings Overview");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(10);
        TextField userFilter = new TextField();
        userFilter.setPromptText("Filter by seller...");
        ComboBox<String> scoreFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All scores", "5 stars", "4 stars", "3 stars", "2 stars", "1 star"));
        scoreFilter.getSelectionModel().selectFirst();
        filters.getChildren().addAll(userFilter, scoreFilter);
        root.getChildren().add(filters);

        for (Review r : DummyDataService.getInstance().getAllReviews()) {
            HBox row = new HBox(12);
            row.getStyleClass().add("card");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));

            VBox info = new VBox(4);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label stars = new Label("★".repeat(r.getRating()) + "☆".repeat(5 - r.getRating()) +
                    "  @" + r.getSellerUsername() + " by @" + r.getBuyerUsername());
            stars.setStyle("-fx-font-weight: bold;");
            Label comment = new Label(r.getComment());
            comment.setWrapText(true);
            comment.setStyle("-fx-text-fill: #5a5a7a;");
            info.getChildren().addAll(stars, comment);

            Button hide = new Button(r.isHidden() ? "Unhide" : "Hide");
            hide.getStyleClass().addAll("btn-outline", "btn-sm");
            hide.setOnAction(e -> {
                r.setHidden(!r.isHidden());
                showRatings();
            });
            row.getChildren().addAll(info, hide);
            root.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Admin Notifications");
        contentPane.getChildren().clear();
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        List<Notification> notifs = DummyDataService.getInstance().getNotificationsFor("admin");
        if (notifs.isEmpty()) {
            root.getChildren().add(new Label("No notifications."));
        } else {
            for (Notification n : notifs) {
                VBox item = new VBox(4);
                item.getStyleClass().add("notif-item");
                Label t = new Label(n.getTitle());
                t.setStyle("-fx-font-weight: bold;");
                Label m = new Label(n.getMessage());
                m.setWrapText(true);
                m.setStyle("-fx-text-fill: #5a5a7a;");
                Label meta = new Label(n.getCategory() + " · " +
                        n.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                item.getChildren().addAll(t, m, meta);
                root.getChildren().add(item);
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();

        String adminUsername = getEffectiveAdminUsername();
        Admin admin = DummyDataService.getInstance().getAdminProfile(adminUsername);
        if (admin == null) {
            User currentUser = SessionManager.getCurrentUser();
            if (currentUser instanceof Admin) {
                admin = (Admin) currentUser;
            } else {
                admin = DummyDataService.getInstance().getAdminProfile(DummyDataService.SEEDED_ADMIN_USERNAME);
            }
        }

        final Admin currentAdmin = admin;
        if (currentAdmin == null) {
            Label notFoundLabel = new Label("Administrator account not found. Please log in again.");
            notFoundLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 14px; -fx-padding: 20;");
            contentPane.getChildren().add(notFoundLabel);
            return;
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

            AdminProfileUpdateResult result = DummyDataService.getInstance()
                    .getAdminProfileService()
                    .updateProfile(currentAdmin, req);

            if (result.isSuccess()) {
                Admin updated = result.getUpdatedAdmin();
                welcomeLabel.setText("Admin: " + updated.getFullName());
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
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}