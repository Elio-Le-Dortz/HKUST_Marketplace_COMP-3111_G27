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

        for (User u : DummyDataService.getInstance().getAllUsers()) {
            if (u.getRole() == UserRole.ADMIN) continue;

            HBox row = new HBox(12);
            row.getStyleClass().add("card");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));

            VBox info = new VBox(3);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label name = new Label(u.getFullName() + " (@" + u.getUsername() + ")");
            name.setStyle("-fx-font-weight: bold;");
            Label role = new Label(u.getRole().name() + (u.isActive() ? " · Active" : " · Deactivated"));
            role.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
            info.getChildren().addAll(name, role);

            Button edit = new Button("Edit");
            edit.getStyleClass().addAll("btn-outline", "btn-sm");
            edit.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Edit user dialog (demo).").showAndWait());

            Button toggle = new Button(u.isActive() ? "Deactivate" : "Activate");
            toggle.getStyleClass().addAll(u.isActive() ? "btn-danger" : "btn-success", "btn-sm");
            toggle.setOnAction(e -> {
                u.setActive(!u.isActive());
                showUsers();
            });

            Button delete = new Button("Delete");
            delete.getStyleClass().addAll("btn-danger", "btn-sm");
            delete.setOnAction(e -> {
                Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                        "Permanently delete user @" + u.getUsername() + "?");
                conf.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        u.setActive(false);
                        new Alert(Alert.AlertType.INFORMATION, "User deactivated (demo safety).").showAndWait();
                        showUsers();
                    }
                });
            });

            row.getChildren().addAll(info, edit, toggle, delete);
            root.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
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
        User user = SessionManager.getCurrentUser();
        VBox form = new VBox(12);
        form.setMaxWidth(400);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));
        Label titleLbl = new Label("Admin Profile");
        titleLbl.getStyleClass().add("section-title");
        form.getChildren().add(titleLbl);

        TextField name = new TextField(user != null ? user.getFullName() : "");
        PasswordField pass = new PasswordField();
        pass.setPromptText("New password (optional)");
        Button save = new Button("Save");
        save.getStyleClass().add("btn-success");
        save.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Profile updated (demo).").showAndWait());
        form.getChildren().addAll(new Label("Full Name"), name, new Label("New Password"), pass, save);
        contentPane.getChildren().add(form);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
