package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import com.hkust.marketplace.services.AdminNotificationService;
import com.hkust.marketplace.services.AdminListingService;
import com.hkust.marketplace.services.AdminRatingService;
import java.util.Map;
import java.util.ArrayList;
import javafx.collections.FXCollections;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.Optional;
import com.hkust.marketplace.services.CreateListingService;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class AdminDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview, btnAllListings, btnUsers, btnDeals, btnRatings, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        Admin admin;
        try {
            admin = SessionManager.requireAdmin();
        } catch (SecurityException e) {
            Platform.runLater(SceneManager::showAdminLogin);
            return;
        }
        if (welcomeLabel != null) welcomeLabel.setText("Admin: " + admin.getFullName());
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
        return SessionManager.requireAdmin().getUsername();
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

    private static final int CARD_THUMBNAIL_SIZE = 64;
    private static final int DETAIL_THUMBNAIL_SIZE = 72;
    private static final int FORM_THUMBNAIL_SIZE = 54;

    private static Path readablePhoto(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Path file = Path.of(path);
            return Files.isRegularFile(file) ? file : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static StackPane listingThumbnail(Listing listing) {
        StackPane box = new StackPane();
        box.setMinSize(CARD_THUMBNAIL_SIZE, CARD_THUMBNAIL_SIZE);
        box.setMaxSize(CARD_THUMBNAIL_SIZE, CARD_THUMBNAIL_SIZE);
        box.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        Path cover = readablePhoto(listing != null ? listing.getThumbnailPath() : null);
        if (cover != null) {
            ImageView view = new ImageView(new Image(cover.toUri().toString(),
                    CARD_THUMBNAIL_SIZE, CARD_THUMBNAIL_SIZE, true, true, true));
            view.setFitWidth(CARD_THUMBNAIL_SIZE);
            view.setFitHeight(CARD_THUMBNAIL_SIZE);
            view.setPreserveRatio(true);
            box.getChildren().add(view);
        } else {
            String emoji = (listing != null && listing.getType() == ListingType.LEND) ? "ðŸ¤"
                    : (listing != null && listing.getType() == ListingType.GIVEAWAY) ? "ðŸŽ" : "ðŸ·ï¸";
            Label icon = new Label(emoji);
            icon.setStyle("-fx-font-size: 26px;");
            box.getChildren().add(icon);
        }
        return box;
    }

    private static Node photoThumbnailNode(Path photo, int size) {
        StackPane box = new StackPane();
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        box.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");
        if (photo != null && Files.isRegularFile(photo)) {
            ImageView view = new ImageView(new Image(photo.toUri().toString(), size, size, true, true, true));
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            box.getChildren().add(view);
        } else {
            Label missing = new Label("ðŸ“·");
            missing.setStyle("-fx-font-size: 18px; -fx-text-fill: #94a3b8;");
            box.getChildren().add(missing);
        }
        return box;
    }

    private void renderFormPhotoThumbnails(HBox container, Label countLabel, Button addBtn,
                                           List<Path> photos, Label emptyLabel) {
        container.getChildren().clear();
        for (int i = 0; i < photos.size(); i++) {
            final int idx = i;
            Path p = photos.get(i);
            StackPane tile = new StackPane();
            tile.setMinSize(FORM_THUMBNAIL_SIZE + 4, FORM_THUMBNAIL_SIZE + 4);
            tile.setMaxSize(FORM_THUMBNAIL_SIZE + 4, FORM_THUMBNAIL_SIZE + 4);
            Node imgNode = photoThumbnailNode(p, FORM_THUMBNAIL_SIZE);
            Button removeBtn = new Button("Ã—");
            removeBtn.setStyle("-fx-font-size: 10px; -fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 0 4 0 4; -fx-background-radius: 8;");
            StackPane.setAlignment(removeBtn, Pos.TOP_RIGHT);
            removeBtn.setOnAction(ev -> {
                photos.remove(idx);
                renderFormPhotoThumbnails(container, countLabel, addBtn, photos, emptyLabel);
            });
            tile.getChildren().addAll(imgNode, removeBtn);
            container.getChildren().add(tile);
        }
        if (photos.isEmpty()) {
            container.getChildren().add(emptyLabel);
        }
        countLabel.setText(photos.size() + " / " + CreateListingService.MAX_PHOTOS + " photos");
        addBtn.setDisable(photos.size() >= CreateListingService.MAX_PHOTOS);
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

        HBox cardLayout = new HBox(14);
        cardLayout.setAlignment(Pos.TOP_LEFT);

        StackPane thumb = listingThumbnail(l);

        VBox infoBox = new VBox(8);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        infoBox.getChildren().addAll(topRow, meta, submitted, actions);

        cardLayout.getChildren().addAll(thumb, infoBox);
        card.getChildren().add(cardLayout);
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
        HBox photoGallery = new HBox(8);
        photoGallery.setAlignment(Pos.CENTER_LEFT);
        if (l.getPhotos() != null && !l.getPhotos().isEmpty()) {
            for (Photo p : l.getPhotos()) {
                Path r = readablePhoto(p != null ? p.getImagePath() : null);
                photoGallery.getChildren().add(photoThumbnailNode(r, DETAIL_THUMBNAIL_SIZE));
            }
        } else {
            Label noPhotos = new Label("No photos attached");
            noPhotos.setStyle("-fx-text-fill: #9aa0b0; -fx-font-style: italic;");
            photoGallery.getChildren().add(noPhotos);
        }
        grid.add(photoGallery, 1, 8);

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

    private AdminListingFilterCriteria adminListingFilter = new AdminListingFilterCriteria();

    @FXML
    private void showAllListings() {
        setActive(btnAllListings, "Manage All Listings");
        contentPane.getChildren().clear();

        AdminListingService listingService = DummyDataService.getInstance().getAdminListingService();
        Map<ListingStatus, Long> statusCounts = listingService.getListingCountsByStatus();
        List<Listing> listings = listingService.filterListings(adminListingFilter);

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        // 1. Metric cards
        root.getChildren().add(createListingSummaryCards(statusCounts));

        // 2. Action & Filter Toolbar
        root.getChildren().add(createListingFilterToolbar(() -> showAllListings()));

        // 3. Listings List
        if (listings.isEmpty()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40, 20, 40, 20));
            emptyBox.getStyleClass().add("card");
            Label emptyTitle = new Label("No Listings Found");
            emptyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #5a5a7a;");
            Label emptySub = new Label("No listings match your search keyword or selected filter criteria.");
            emptySub.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
            Button resetBtn = new Button("Clear Filters");
            resetBtn.getStyleClass().addAll("btn-outline", "btn-sm");
            resetBtn.setOnAction(e -> {
                adminListingFilter = new AdminListingFilterCriteria();
                showAllListings();
            });
            emptyBox.getChildren().addAll(emptyTitle, emptySub, resetBtn);
            root.getChildren().add(emptyBox);
        } else {
            Label resultsLabel = new Label("Showing " + listings.size() + " listing(s)");
            resultsLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #5a5a7a; -fx-font-weight: bold;");
            root.getChildren().add(resultsLabel);

            for (Listing l : listings) {
                root.getChildren().add(createListingManagementCard(l));
            }
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createListingSummaryCards(Map<ListingStatus, Long> counts) {
        HBox hbox = new HBox(12);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long live = counts.getOrDefault(ListingStatus.LIVE, 0L);
        long pending = counts.getOrDefault(ListingStatus.PENDING_REVIEW, 0L);
        long deals = counts.getOrDefault(ListingStatus.RESERVED, 0L)
                + counts.getOrDefault(ListingStatus.SOLD, 0L)
                + counts.getOrDefault(ListingStatus.LENT, 0L)
                + counts.getOrDefault(ListingStatus.GIVEN, 0L);
        long removed = counts.getOrDefault(ListingStatus.REMOVED, 0L);

        hbox.getChildren().addAll(
                createListingMetricCard("Total Listings", total, "#003974"),
                createListingMetricCard("Live Listings", live, "#2e7d32"),
                createListingMetricCard("Pending Review", pending, "#ed6c02"),
                createListingMetricCard("Active Deals", deals, "#1976d2"),
                createListingMetricCard("Taken Down", removed, "#c62828")
        );
        return hbox;
    }

    private VBox createListingMetricCard(String label, long count, String color) {
        VBox card = new VBox(4);
        card.getStyleClass().add("metric-card");
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(card, Priority.ALWAYS);

        Label num = new Label(String.valueOf(count));
        num.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        card.getChildren().addAll(num, lbl);
        return card;
    }

    private VBox createListingFilterToolbar(Runnable refreshCallback) {
        VBox container = new VBox(8);
        container.getStyleClass().add("card");
        container.setPadding(new Insets(12, 14, 12, 14));

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Button addBtn = new Button("+ Add Listing on Behalf of Seller");
        addBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        addBtn.setOnAction(e -> openAddListingDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        TextField searchField = new TextField();
        searchField.setPromptText("Search by title or seller...");
        searchField.setPrefWidth(240);
        if (adminListingFilter.getKeyword() != null) {
            searchField.setText(adminListingFilter.getKeyword());
        }
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            adminListingFilter.setKeyword(newVal);
            refreshCallback.run();
        });

        topRow.getChildren().addAll(addBtn, spacer, searchField);

        HBox filterRow = new HBox(10);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // Status Combo
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().add("All Statuses");
        for (ListingStatus s : ListingStatus.values()) {
            statusCombo.getItems().add(s.name());
        }
        statusCombo.setValue(adminListingFilter.getStatus() != null ? adminListingFilter.getStatus().name() : "All Statuses");
        statusCombo.setOnAction(e -> {
            String val = statusCombo.getValue();
            if (val == null || "All Statuses".equals(val)) {
                adminListingFilter.setStatus(null);
            } else {
                adminListingFilter.setStatus(ListingStatus.valueOf(val));
            }
            refreshCallback.run();
        });

        // Category Combo
        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getItems().add("All Categories");
        for (Category c : Category.values()) {
            catCombo.getItems().add(c.getDisplayName());
        }
        catCombo.setValue(adminListingFilter.getCategory() != null ? adminListingFilter.getCategory().getDisplayName() : "All Categories");
        catCombo.setOnAction(e -> {
            String val = catCombo.getValue();
            if (val == null || "All Categories".equals(val)) {
                adminListingFilter.setCategory(null);
            } else {
                adminListingFilter.setCategory(Category.fromString(val));
            }
            refreshCallback.run();
        });

        // Type Combo
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("All Types", "Sell", "Lend", "Give Away");
        if (adminListingFilter.getType() == null) {
            typeCombo.setValue("All Types");
        } else {
            typeCombo.setValue(adminListingFilter.getType().getDisplayName());
        }
        typeCombo.setOnAction(e -> {
            String val = typeCombo.getValue();
            if (val == null || "All Types".equals(val)) {
                adminListingFilter.setType(null);
            } else if ("Sell".equalsIgnoreCase(val)) {
                adminListingFilter.setType(ListingType.SELL);
            } else if ("Lend".equalsIgnoreCase(val)) {
                adminListingFilter.setType(ListingType.LEND);
            } else {
                adminListingFilter.setType(ListingType.GIVEAWAY);
            }
            refreshCallback.run();
        });

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        resetBtn.setOnAction(e -> {
            adminListingFilter = new AdminListingFilterCriteria();
            searchField.clear();
            statusCombo.setValue("All Statuses");
            catCombo.setValue("All Categories");
            typeCombo.setValue("All Types");
            refreshCallback.run();
        });

        filterRow.getChildren().addAll(new Label("Filters:"), statusCombo, catCombo, typeCombo, resetBtn);
        container.getChildren().addAll(topRow, filterRow);
        return container;
    }

    private VBox createListingManagementCard(Listing l) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("card", "card-hover");
        card.setPadding(new Insets(12, 16, 12, 16));

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(l.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        HBox.setHgrow(title, Priority.ALWAYS);

        // Category Badge
        Label catBadge = new Label(l.getCategory() != null ? l.getCategory().getDisplayName() : "Other");
        catBadge.getStyleClass().addAll("badge", "badge-info");

        // Type Badge
        String typeStyle = switch (l.getType()) {
            case SELL -> "badge-sell";
            case LEND -> "badge-lend";
            case GIVEAWAY -> "badge-giveaway";
        };
        Label typeBadge = new Label(l.getTypeDisplay());
        typeBadge.getStyleClass().addAll("badge", typeStyle);

        // Status Badge
        Label statusBadge = new Label(l.getStatus() != null ? l.getStatus().name() : "LIVE");
        String statusStyle = switch (l.getStatus()) {
            case LIVE -> "badge-success";
            case PENDING_REVIEW -> "badge-warning";
            case REMOVED, REJECTED -> "badge-danger";
            case RESERVED, SOLD, LENT, GIVEN -> "badge-info";
        };
        statusBadge.getStyleClass().addAll("badge", statusStyle);

        topRow.getChildren().addAll(title, catBadge, typeBadge, statusBadge);

        // Middle Row: Seller info & pricing
        HBox midRow = new HBox(16);
        midRow.setAlignment(Pos.CENTER_LEFT);

        Label sellerLbl = new Label("Seller: " + l.getSellerFullName() + " (@" + l.getSellerUsername() + ")");
        sellerLbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label priceLbl = new Label("Terms: " + l.getPriceDisplay());
        priceLbl.setStyle("-fx-text-fill: #003974; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label locationLbl = new Label("Meetup: " + (l.getMeetupLocation() != null ? l.getMeetupLocation().getDisplayName() : "-"));
        locationLbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        String postedStr = l.getPostedAt() != null ? l.getPostedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "-";
        Label postedLbl = new Label("Posted: " + postedStr);
        postedLbl.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");

        int activeOffers = DummyDataService.getInstance().getAdminListingService().getActiveOffersCount(l.getId());
        Region midSpacer = new Region();
        HBox.setHgrow(midSpacer, Priority.ALWAYS);

        midRow.getChildren().addAll(sellerLbl, priceLbl, locationLbl, postedLbl, midSpacer);
        if (activeOffers > 0) {
            Label offersBadge = new Label(activeOffers + " active offer(s)");
            offersBadge.getStyleClass().addAll("badge", "badge-warning");
            midRow.getChildren().add(offersBadge);
        }

        // Bottom row: Reason callout (if Removed or Rejected) + Action Buttons
        HBox botRow = new HBox(10);
        botRow.setAlignment(Pos.CENTER_RIGHT);

        if (l.getAdminReason() != null && !l.getAdminReason().trim().isEmpty()) {
            Label reasonLbl = new Label("Admin Reason: " + l.getAdminReason());
            reasonLbl.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12px; -fx-font-style: italic;");
            botRow.getChildren().add(reasonLbl);
        }

        Region botSpacer = new Region();
        HBox.setHgrow(botSpacer, Priority.ALWAYS);
        botRow.getChildren().add(botSpacer);

        Button viewBtn = new Button("View Details");
        viewBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        viewBtn.setOnAction(e -> openListingDetailsDialog(l));

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        editBtn.setOnAction(e -> {
            // Alternative Flow A4 check
            if (isDealInProgressOrCompleted(l.getStatus())) {
                new Alert(Alert.AlertType.WARNING,
                        "The listing cannot be changed because a deal is in progress or completed.").showAndWait();
            } else {
                openEditListingDialog(l);
            }
        });

        Button takeDownBtn = new Button("Take Down");
        takeDownBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        takeDownBtn.setOnAction(e -> {
            // Alternative Flow A4 check
            if (isDealInProgressOrCompleted(l.getStatus())) {
                new Alert(Alert.AlertType.WARNING,
                        "The listing cannot be changed because a deal is in progress or completed.").showAndWait();
            } else if (l.getStatus() != ListingStatus.LIVE) {
                // Alternative Flow A5 check
                new Alert(Alert.AlertType.WARNING,
                        "Only a Live listing can be taken down (a pending listing is rejected in Review Pending Listings).").showAndWait();
            } else {
                openTakeDownDialog(l);
            }
        });

        botRow.getChildren().addAll(viewBtn, editBtn, takeDownBtn);
        card.getChildren().addAll(topRow, midRow, botRow);
        return card;
    }

    private void openAddListingDialog() {
        AdminListingService service = DummyDataService.getInstance().getAdminListingService();
        List<Seller> activeSellers = service.getActiveSellers();

        if (activeSellers.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No active sellers found in the system to add a listing on behalf of.").showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Listing on Behalf of Seller");
        dialog.setHeaderText("Create a new Live listing for an active seller");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(480);
        content.setPadding(new Insets(10));

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
        errLabel.setVisible(false);

        // Seller Selection
        ComboBox<Seller> sellerCombo = new ComboBox<>();
        sellerCombo.setMaxWidth(Double.MAX_VALUE);
        sellerCombo.getItems().addAll(activeSellers);
        sellerCombo.setConverter(new javafx.util.StringConverter<Seller>() {
            @Override public String toString(Seller s) { return s != null ? s.getFullName() + " (@" + s.getUsername() + ")" : ""; }
            @Override public Seller fromString(String string) { return null; }
        });
        sellerCombo.getSelectionModel().selectFirst();

        // Listing Type
        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList("Sell", "Lend", "Give Away"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.setValue("Sell");

        TextField titleField = new TextField();
        titleField.setPromptText("Item Name *");

        ComboBox<Category> catCombo = new ComboBox<>(FXCollections.observableArrayList(Category.values()));
        catCombo.setMaxWidth(Double.MAX_VALUE);
        catCombo.setValue(Category.BOOKS);

        ComboBox<Condition> condCombo = new ComboBox<>(FXCollections.observableArrayList(Condition.values()));
        condCombo.setMaxWidth(Double.MAX_VALUE);
        condCombo.setValue(Condition.LIKE_NEW);

        ComboBox<MeetupLocation> meetupCombo = new ComboBox<>(FXCollections.observableArrayList(MeetupLocation.values()));
        meetupCombo.setMaxWidth(Double.MAX_VALUE);
        meetupCombo.setValue(MeetupLocation.LIBRARY);

        TextField priceField = new TextField();
        priceField.setPromptText("Price in HKD (e.g. 150.0) *");

        TextField daysField = new TextField();
        daysField.setPromptText("Lending Duration in days (e.g. 14) *");
        daysField.setDisable(true);

        typeCombo.setOnAction(e -> {
            String val = typeCombo.getValue();
            if ("Sell".equals(val)) {
                priceField.setDisable(false);
                daysField.setDisable(true);
            } else if ("Lend".equals(val)) {
                priceField.setDisable(true);
                daysField.setDisable(false);
            } else {
                priceField.setDisable(true);
                daysField.setDisable(true);
            }
        });

        TextArea descArea = new TextArea();
        descArea.setPromptText("Description");
        descArea.setPrefRowCount(3);

        List<Path> chosenPhotos = new ArrayList<>();
        VBox photoSection = new VBox(6);
        HBox photoHeader = new HBox(12);
        photoHeader.setAlignment(Pos.CENTER_LEFT);
        Button addPhotosBtn = new Button("ðŸ“·  Add photosâ€¦");
        addPhotosBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        Label photoCountLbl = new Label("0 / " + CreateListingService.MAX_PHOTOS + " photos");
        photoCountLbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        photoHeader.getChildren().addAll(addPhotosBtn, photoCountLbl);

        HBox photoThumbnails = new HBox(8);
        photoThumbnails.setAlignment(Pos.CENTER_LEFT);
        Label noPhotosLbl = new Label("No photos selected yet. (1 to 6 photos required)");
        noPhotosLbl.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        photoThumbnails.getChildren().add(noPhotosLbl);

        addPhotosBtn.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Listing Photos (PNG, JPG, GIF, BMP)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Images (PNG, JPEG, GIF, BMP)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp",
                    "*.PNG", "*.JPG", "*.JPEG", "*.GIF", "*.BMP"
            ));
            List<File> files = null;
            try {
                files = chooser.showOpenMultipleDialog(SceneManager.getPrimaryStage());
            } catch (Exception ex) {
                // In headless mode or test environment
            }
            if (files != null && !files.isEmpty()) {
                PhotoSelectionResult selRes = CreateListingService.addPhotos(chosenPhotos, files.stream().map(File::toPath).toList());
                chosenPhotos.clear();
                chosenPhotos.addAll(selRes.getPhotos());
                renderFormPhotoThumbnails(photoThumbnails, photoCountLbl, addPhotosBtn, chosenPhotos, noPhotosLbl);
                if (selRes.hasRejections()) {
                    errLabel.setText(String.join("\n", selRes.getRejections()));
                    errLabel.setVisible(true);
                } else {
                    errLabel.setVisible(false);
                }
            }
        });

        photoSection.getChildren().addAll(new Label("Photos (1 to 6) *:"), photoHeader, photoThumbnails);

        content.getChildren().addAll(
                errLabel,
                new Label("Select Seller *:"), sellerCombo,
                new Label("Listing Type *:"), typeCombo,
                new Label("Item Name *:"), titleField,
                new Label("Category *:"), catCombo,
                new Label("Condition *:"), condCombo,
                new Label("Meetup Point *:"), meetupCombo,
                new Label("Price (for Sell):"), priceField,
                new Label("Lending Days (for Lend):"), daysField,
                new Label("Description *:"), descArea,
                photoSection
        );
        dialog.getDialogPane().setContent(content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            Seller selectedSeller = sellerCombo.getValue();
            String typeStr = typeCombo.getValue();
            ListingType type = "Sell".equals(typeStr) ? ListingType.SELL : ("Lend".equals(typeStr) ? ListingType.LEND : ListingType.GIVEAWAY);

            Double price = null;
            if (type == ListingType.SELL) {
                try {
                    price = Double.parseDouble(priceField.getText().trim());
                } catch (Exception ex) {
                    errLabel.setText("Please enter a valid numeric price.");
                    errLabel.setVisible(true);
                    event.consume();
                    return;
                }
            }

            Integer days = null;
            if (type == ListingType.LEND) {
                try {
                    days = Integer.parseInt(daysField.getText().trim());
                } catch (Exception ex) {
                    errLabel.setText("Please enter a valid numeric lending duration in days.");
                    errLabel.setVisible(true);
                    event.consume();
                    return;
                }
            }

            if (descArea.getText() == null || descArea.getText().trim().isEmpty()) {
                errLabel.setText("Description is required.");
                errLabel.setVisible(true);
                event.consume();
                return;
            }

            if (chosenPhotos.isEmpty()) {
                errLabel.setText("At least one photo is required (1 to 6 photos).");
                errLabel.setVisible(true);
                event.consume();
                return;
            }

            List<Photo> photos = chosenPhotos.stream()
                    .map(p -> new Photo(p.toAbsolutePath().normalize().toString()))
                    .toList();

            AdminListingCreateRequest req = new AdminListingCreateRequest(
                    selectedSeller != null ? selectedSeller.getUsername() : null,
                    titleField.getText(),
                    descArea.getText(),
                    type,
                    catCombo.getValue(),
                    condCombo.getValue(),
                    meetupCombo.getValue(),
                    price,
                    days,
                    photos
            );

            AdminListingOperationResult res = service.createListingOnBehalfOfSeller(req, getEffectiveAdminUsername());
            if (!res.isSuccess()) {
                errLabel.setText(res.getMessage());
                errLabel.setVisible(true);
                event.consume();
            } else {
                new Alert(Alert.AlertType.INFORMATION, res.getMessage()).showAndWait();
                showAllListings();
            }
        });

        dialog.showAndWait();
    }

    private void openEditListingDialog(Listing l) {
        AdminListingService service = DummyDataService.getInstance().getAdminListingService();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Listing: " + l.getTitle());
        dialog.setHeaderText("Modify listing details (Status remains " + l.getStatus() + ")");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(480);
        content.setPadding(new Insets(10));

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
        errLabel.setVisible(false);

        Label sellerInfo = new Label("Seller: " + l.getSellerFullName() + " (@" + l.getSellerUsername() + ")");
        sellerInfo.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");

        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList("Sell", "Lend", "Give Away"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.setValue(l.getType() == ListingType.SELL ? "Sell" : (l.getType() == ListingType.LEND ? "Lend" : "Give Away"));

        TextField titleField = new TextField(l.getTitle());
        ComboBox<Category> catCombo = new ComboBox<>(FXCollections.observableArrayList(Category.values()));
        catCombo.setMaxWidth(Double.MAX_VALUE);
        catCombo.setValue(l.getCategory() != null ? l.getCategory() : Category.OTHER);

        ComboBox<Condition> condCombo = new ComboBox<>(FXCollections.observableArrayList(Condition.values()));
        condCombo.setMaxWidth(Double.MAX_VALUE);
        condCombo.setValue(l.getCondition() != null ? l.getCondition() : Condition.USED);

        ComboBox<MeetupLocation> meetupCombo = new ComboBox<>(FXCollections.observableArrayList(MeetupLocation.values()));
        meetupCombo.setMaxWidth(Double.MAX_VALUE);
        meetupCombo.setValue(l.getMeetupLocation() != null ? l.getMeetupLocation() : MeetupLocation.LIBRARY);

        TextField priceField = new TextField(l.getPrice() != null ? String.valueOf(l.getPrice()) : "");
        priceField.setPromptText("Price in HKD *");
        priceField.setDisable(l.getType() != ListingType.SELL);

        TextField daysField = new TextField(l.getLendingDays() != null ? String.valueOf(l.getLendingDays()) : "");
        daysField.setPromptText("Lending Duration in days *");
        daysField.setDisable(l.getType() != ListingType.LEND);

        typeCombo.setOnAction(e -> {
            String val = typeCombo.getValue();
            if ("Sell".equals(val)) {
                priceField.setDisable(false);
                daysField.setDisable(true);
            } else if ("Lend".equals(val)) {
                priceField.setDisable(true);
                daysField.setDisable(false);
            } else {
                priceField.setDisable(true);
                daysField.setDisable(true);
            }
        });

        TextArea descArea = new TextArea(l.getDescription() != null ? l.getDescription() : "");
        descArea.setPrefRowCount(3);

        List<Path> editPhotos = new ArrayList<>();
        if (l.getPhotos() != null) {
            for (Photo p : l.getPhotos()) {
                Path r = readablePhoto(p != null ? p.getImagePath() : null);
                if (r != null) {
                    editPhotos.add(r.toAbsolutePath().normalize());
                }
            }
        }

        VBox editPhotoSection = new VBox(6);
        HBox editPhotoHeader = new HBox(12);
        editPhotoHeader.setAlignment(Pos.CENTER_LEFT);
        Button addEditPhotosBtn = new Button("ðŸ“·  Add photosâ€¦");
        addEditPhotosBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        Label editPhotoCountLbl = new Label(editPhotos.size() + " / " + CreateListingService.MAX_PHOTOS + " photos");
        editPhotoCountLbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        editPhotoHeader.getChildren().addAll(addEditPhotosBtn, editPhotoCountLbl);

        HBox editPhotoThumbnails = new HBox(8);
        editPhotoThumbnails.setAlignment(Pos.CENTER_LEFT);
        Label noEditPhotosLbl = new Label("No photos selected. (1 to 6 photos required)");
        noEditPhotosLbl.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        renderFormPhotoThumbnails(editPhotoThumbnails, editPhotoCountLbl, addEditPhotosBtn, editPhotos, noEditPhotosLbl);

        addEditPhotosBtn.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Listing Photos (PNG, JPG, GIF, BMP)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Images (PNG, JPEG, GIF, BMP)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp",
                    "*.PNG", "*.JPG", "*.JPEG", "*.GIF", "*.BMP"
            ));
            List<File> files = null;
            try {
                files = chooser.showOpenMultipleDialog(SceneManager.getPrimaryStage());
            } catch (Exception ex) {
                // In headless mode or test environment
            }
            if (files != null && !files.isEmpty()) {
                PhotoSelectionResult selRes = CreateListingService.addPhotos(editPhotos, files.stream().map(File::toPath).toList());
                editPhotos.clear();
                editPhotos.addAll(selRes.getPhotos());
                renderFormPhotoThumbnails(editPhotoThumbnails, editPhotoCountLbl, addEditPhotosBtn, editPhotos, noEditPhotosLbl);
                if (selRes.hasRejections()) {
                    errLabel.setText(String.join("\n", selRes.getRejections()));
                    errLabel.setVisible(true);
                } else {
                    errLabel.setVisible(false);
                }
            }
        });

        editPhotoSection.getChildren().addAll(new Label("Photos (1 to 6) *:"), editPhotoHeader, editPhotoThumbnails);

        content.getChildren().addAll(
                errLabel,
                sellerInfo,
                new Label("Listing Type *:"), typeCombo,
                new Label("Item Name *:"), titleField,
                new Label("Category *:"), catCombo,
                new Label("Condition *:"), condCombo,
                new Label("Meetup Point *:"), meetupCombo,
                new Label("Price (for Sell):"), priceField,
                new Label("Lending Days (for Lend):"), daysField,
                new Label("Description *:"), descArea,
                editPhotoSection
        );

        dialog.getDialogPane().setContent(content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String typeStr = typeCombo.getValue();
            ListingType type = "Sell".equals(typeStr) ? ListingType.SELL : ("Lend".equals(typeStr) ? ListingType.LEND : ListingType.GIVEAWAY);

            Double price = null;
            if (type == ListingType.SELL) {
                try {
                    price = Double.parseDouble(priceField.getText().trim());
                } catch (Exception ex) {
                    errLabel.setText("Please enter a valid numeric price.");
                    errLabel.setVisible(true);
                    event.consume();
                    return;
                }
            }

            Integer days = null;
            if (type == ListingType.LEND) {
                try {
                    days = Integer.parseInt(daysField.getText().trim());
                } catch (Exception ex) {
                    errLabel.setText("Please enter a valid numeric lending duration in days.");
                    errLabel.setVisible(true);
                    event.consume();
                    return;
                }
            }

            if (descArea.getText() == null || descArea.getText().trim().isEmpty()) {
                errLabel.setText("Description is required.");
                errLabel.setVisible(true);
                event.consume();
                return;
            }

            if (editPhotos.isEmpty()) {
                errLabel.setText("At least one photo is required (1 to 6 photos).");
                errLabel.setVisible(true);
                event.consume();
                return;
            }

            List<Photo> photos = editPhotos.stream()
                    .map(p -> new Photo(p.toAbsolutePath().normalize().toString()))
                    .toList();

            AdminListingEditRequest req = new AdminListingEditRequest(
                    l.getId(),
                    titleField.getText(),
                    descArea.getText(),
                    type,
                    catCombo.getValue(),
                    condCombo.getValue(),
                    meetupCombo.getValue(),
                    price,
                    days,
                    photos
            );

            AdminListingOperationResult res = service.editListing(req, getEffectiveAdminUsername());
            if (!res.isSuccess()) {
                errLabel.setText(res.getMessage());
                errLabel.setVisible(true);
                event.consume();
            } else {
                new Alert(Alert.AlertType.INFORMATION, res.getMessage()).showAndWait();
                showAllListings();
            }
        });

        dialog.showAndWait();
    }

    private void openTakeDownDialog(Listing l) {
        AdminListingService service = DummyDataService.getInstance().getAdminListingService();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Take Down Listing");
        dialog.setHeaderText("Remove Live listing '" + l.getTitle() + "'");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(460);
        content.setPadding(new Insets(10));

        Label warnLabel = new Label("⚠️ Warning: Taking down this listing will set its status to REMOVED,\n" +
                "atomically decline all active offers and borrow requests, and notify affected buyers and seller.");
        warnLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
        errLabel.setVisible(false);

        Label reasonPrompt = new Label("Mandatory Administrative Reason *:");
        reasonPrompt.setStyle("-fx-font-weight: bold;");

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Enter reason for taking down this listing (e.g., prohibited item, counterfeit, seller request)...");
        reasonArea.setPrefRowCount(3);

        content.getChildren().addAll(warnLabel, errLabel, reasonPrompt, reasonArea);
        dialog.getDialogPane().setContent(content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Confirm Take Down");
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String reason = reasonArea.getText();
            if (reason == null || reason.trim().isEmpty()) {
                errLabel.setText("A reason is required to take down a listing.");
                errLabel.setVisible(true);
                event.consume();
                return;
            }

            AdminListingOperationResult res = service.takeDownListing(l.getId(), reason, getEffectiveAdminUsername());
            if (!res.isSuccess()) {
                errLabel.setText(res.getMessage());
                errLabel.setVisible(true);
                event.consume();
            } else {
                new Alert(Alert.AlertType.INFORMATION,
                        res.getMessage() + "\n" + res.getAffectedOffersCount() + " active offer(s) declined and buyer(s) notified.").showAndWait();
                showAllListings();
            }
        });

        dialog.showAndWait();
    }

    private void openListingDetailsDialog(Listing l) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Listing Details: " + l.getTitle());
        dialog.setHeaderText("Listing ID: " + l.getId() + " · Status: " + (l.getStatus() != null ? l.getStatus().name() : "LIVE"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(12);
        content.setPrefWidth(500);
        content.setPadding(new Insets(10));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        grid.add(new Label("Title:"), 0, 0);
        Label titleLbl = new Label(l.getTitle());
        titleLbl.setStyle("-fx-font-weight: bold;");
        grid.add(titleLbl, 1, 0);

        grid.add(new Label("Seller:"), 0, 1);
        grid.add(new Label(l.getSellerFullName() + " (@" + l.getSellerUsername() + ")"), 1, 1);

        grid.add(new Label("Category:"), 0, 2);
        grid.add(new Label(l.getCategory() != null ? l.getCategory().getDisplayName() : "-"), 1, 2);

        grid.add(new Label("Type & Terms:"), 0, 3);
        grid.add(new Label(l.getPriceDisplay()), 1, 3);

        grid.add(new Label("Condition:"), 0, 4);
        grid.add(new Label(l.getCondition() != null ? l.getCondition().getDisplayName() : "-"), 1, 4);

        grid.add(new Label("Meetup Point:"), 0, 5);
        grid.add(new Label(l.getMeetupLocation() != null ? l.getMeetupLocation().getDisplayName() : "-"), 1, 5);

        grid.add(new Label("Posted Date:"), 0, 6);
        grid.add(new Label(l.getPostedAt() != null ? l.getPostedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")) : "-"), 1, 6);

        grid.add(new Label("View Count:"), 0, 7);
        grid.add(new Label(l.getViewCount() + " views"), 1, 7);

        int activeOffers = DummyDataService.getInstance().getAdminListingService().getActiveOffersCount(l.getId());
        grid.add(new Label("Active Offers:"), 0, 8);
        grid.add(new Label(activeOffers + " pending/countered offer(s)"), 1, 8);

        grid.add(new Label("Photos:"), 0, 9);
        HBox detailsGallery = new HBox(8);
        detailsGallery.setAlignment(Pos.CENTER_LEFT);
        if (l.getPhotos() != null && !l.getPhotos().isEmpty()) {
            for (Photo p : l.getPhotos()) {
                Path r = readablePhoto(p != null ? p.getImagePath() : null);
                detailsGallery.getChildren().add(photoThumbnailNode(r, DETAIL_THUMBNAIL_SIZE));
            }
        } else {
            Label noPhotos = new Label("No photos attached");
            noPhotos.setStyle("-fx-text-fill: #9aa0b0; -fx-font-style: italic;");
            detailsGallery.getChildren().add(noPhotos);
        }
        grid.add(detailsGallery, 1, 9);

        if (l.getAdminReason() != null && !l.getAdminReason().trim().isEmpty()) {
            grid.add(new Label("Admin Reason:"), 0, 10);
            Label rLbl = new Label(l.getAdminReason());
            rLbl.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
            grid.add(rLbl, 1, 10);
        }

        Label descHeader = new Label("Description:");
        descHeader.setStyle("-fx-font-weight: bold;");
        TextArea descArea = new TextArea(l.getDescription() != null ? l.getDescription() : "No description provided.");
        descArea.setEditable(false);
        descArea.setWrapText(true);
        descArea.setPrefRowCount(3);

        content.getChildren().addAll(grid, descHeader, descArea);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private boolean isDealInProgressOrCompleted(ListingStatus status) {
        if (status == null) return false;
        return status == ListingStatus.RESERVED ||
                status == ListingStatus.SOLD ||
                status == ListingStatus.LENT ||
                status == ListingStatus.GIVEN;
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
        search.setPromptText("Search title, buyer, seller username...");
        search.setPrefWidth(240);

        ComboBox<String> typeFilter = new ComboBox<>(FXCollections.observableArrayList("All Types", "Sale", "Loan", "Giveaway"));
        typeFilter.getSelectionModel().selectFirst();

        ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList("All Statuses", "Active", "Completed", "Returned"));
        statusFilter.getSelectionModel().selectFirst();

        DatePicker fromDatePicker = new DatePicker();
        fromDatePicker.setPromptText("From date");
        fromDatePicker.setPrefWidth(125);

        DatePicker toDatePicker = new DatePicker();
        toDatePicker.setPromptText("To date");
        toDatePicker.setPrefWidth(125);

        Button clearBtn = new Button("Clear Filters");
        clearBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label();
        countLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 14px;");

        filters.getChildren().addAll(search, typeFilter, statusFilter, fromDatePicker, toDatePicker, clearBtn, refreshBtn, spacer, countLabel);
        root.getChildren().add(filters);

        VBox dealsContainer = new VBox(10);
        root.getChildren().add(dealsContainer);

        Runnable refreshAction = () -> {
            DealType selectedType = null;
            String typeVal = typeFilter.getValue();
            if ("Sale".equalsIgnoreCase(typeVal)) selectedType = DealType.SALE;
            else if ("Loan".equalsIgnoreCase(typeVal)) selectedType = DealType.LOAN;
            else if ("Giveaway".equalsIgnoreCase(typeVal)) selectedType = DealType.GIVEAWAY;

            DealStatus selectedStatus = null;
            String statusVal = statusFilter.getValue();
            if ("Active".equalsIgnoreCase(statusVal)) selectedStatus = DealStatus.ACTIVE;
            else if ("Completed".equalsIgnoreCase(statusVal)) selectedStatus = DealStatus.COMPLETED;
            else if ("Returned".equalsIgnoreCase(statusVal)) selectedStatus = DealStatus.RETURNED;

            LocalDate fromDate = fromDatePicker.getValue();
            LocalDate toDate = toDatePicker.getValue();

            AdminDealFilterCriteria criteria = new AdminDealFilterCriteria(
                    search.getText(), selectedType, selectedStatus, fromDate, toDate
            );

            AdminDealQueryResult result = DummyDataService.getInstance().getAdminDealsService().queryDeals(criteria);

            if (result.isInvalidDateRange()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Invalid Date Range");
                alert.setHeaderText("Filter Not Applied");
                alert.setContentText(result.getMessage());
                alert.showAndWait();
                toDatePicker.setValue(null);
                return;
            }

            countLabel.setText("Deals (" + result.getFilteredCount() + " of " + result.getTotalSystemDealsCount() + ")");
            renderDeals(dealsContainer, result, () -> {
                search.clear();
                typeFilter.getSelectionModel().selectFirst();
                statusFilter.getSelectionModel().selectFirst();
                fromDatePicker.setValue(null);
                toDatePicker.setValue(null);
            });
        };

        search.textProperty().addListener((obs, oldV, newV) -> refreshAction.run());
        typeFilter.valueProperty().addListener((obs, oldV, newV) -> refreshAction.run());
        statusFilter.valueProperty().addListener((obs, oldV, newV) -> refreshAction.run());
        fromDatePicker.valueProperty().addListener((obs, oldV, newV) -> refreshAction.run());
        toDatePicker.valueProperty().addListener((obs, oldV, newV) -> refreshAction.run());
        refreshBtn.setOnAction(e -> refreshAction.run());
        clearBtn.setOnAction(e -> {
            search.clear();
            typeFilter.getSelectionModel().selectFirst();
            statusFilter.getSelectionModel().selectFirst();
            fromDatePicker.setValue(null);
            toDatePicker.setValue(null);
            refreshAction.run();
        });

        refreshAction.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private void renderDeals(VBox container, AdminDealQueryResult result, Runnable clearFiltersCallback) {
        container.getChildren().clear();

        if (result.isNoDealsYet()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40, 20, 40, 20));
            emptyBox.getStyleClass().add("card");
            Label emptyTitle = new Label("No Deals in the System Yet");
            emptyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #5a5a7a;");
            Label emptySub = new Label("No transactions or deals have been executed system-wide yet.");
            emptySub.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 13px;");
            emptyBox.getChildren().addAll(emptyTitle, emptySub);
            container.getChildren().add(emptyBox);
            return;
        }

        if (result.isNoMatchingDeals() || result.getDeals().isEmpty()) {
            VBox emptyBox = new VBox(12);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40, 20, 40, 20));
            emptyBox.getStyleClass().add("card");
            Label emptyTitle = new Label("No Matching Deals Found");
            emptyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #5a5a7a;");
            Label emptySub = new Label("No transactions matched your current search and filter criteria.");
            emptySub.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 13px;");
            Button clearBtn = new Button("Clear All Filters");
            clearBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            clearBtn.setOnAction(e -> {
                if (clearFiltersCallback != null) clearFiltersCallback.run();
            });
            emptyBox.getChildren().addAll(emptyTitle, emptySub, clearBtn);
            container.getChildren().add(emptyBox);
            return;
        }

        TableView<AdminDealRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(460);

        TableColumn<AdminDealRecord, String> titleCol = new TableColumn<>("Item Title");
        titleCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getItemTitleDisplay()));
        titleCol.setPrefWidth(210);

        TableColumn<AdminDealRecord, String> buyerCol = new TableColumn<>("Buyer");
        buyerCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getBuyerUsernameDisplay()));
        buyerCol.setPrefWidth(110);

        TableColumn<AdminDealRecord, String> sellerCol = new TableColumn<>("Seller");
        sellerCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getSellerUsernameDisplay()));
        sellerCol.setPrefWidth(110);

        TableColumn<AdminDealRecord, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDealTypeDisplay()));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    String styleClass = switch (item.toLowerCase()) {
                        case "sale" -> "badge-sell";
                        case "loan" -> "badge-lend";
                        case "giveaway" -> "badge-giveaway";
                        default -> "badge-info";
                    };
                    badge.getStyleClass().addAll("badge", styleClass);
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
        typeCol.setPrefWidth(85);

        TableColumn<AdminDealRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDealStatusDisplay()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    String styleClass = switch (item.toLowerCase()) {
                        case "active" -> "badge-active";
                        case "completed" -> "badge-completed";
                        case "returned" -> "badge-returned";
                        default -> "badge-warning";
                    };
                    badge.getStyleClass().addAll("badge", styleClass);
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
        statusCol.setPrefWidth(95);

        TableColumn<AdminDealRecord, String> startCol = new TableColumn<>("Start Date");
        startCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getStartDateDisplay()));
        startCol.setPrefWidth(125);

        TableColumn<AdminDealRecord, String> returnCol = new TableColumn<>("Return / Due Date");
        returnCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getReturnOrCompleteDateDisplay()));
        returnCol.setPrefWidth(135);

        TableColumn<AdminDealRecord, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(105);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("View Details");
            {
                btn.getStyleClass().addAll("btn-outline", "btn-sm");
                btn.setOnAction(e -> {
                    AdminDealRecord rec = getTableView().getItems().get(getIndex());
                    showDealDetailsDialog(rec);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(titleCol, buyerCol, sellerCol, typeCol, statusCol, startCol, returnCol, actionCol);
        table.setItems(FXCollections.observableArrayList(result.getDeals()));

        table.setRowFactory(tv -> {
            TableRow<AdminDealRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showDealDetailsDialog(row.getItem());
                }
            });
            return row;
        });

        container.getChildren().add(table);
    }

    private void showDealDetailsDialog(AdminDealRecord record) {
        if (record == null) return;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Transaction Oversight Details - " + record.getDealId());
        dialog.setHeaderText("Deal Record: " + record.getDealId() + " (" + record.getItemTitleDisplay() + ")");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(14);
        content.setPrefWidth(560);
        content.setPadding(new Insets(12));

        // 1. Transaction Overview
        Label txSectionLabel = new Label("1. Transaction Overview");
        txSectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #003974;");

        GridPane txGrid = new GridPane();
        txGrid.setHgap(14);
        txGrid.setVgap(6);

        txGrid.add(new Label("Deal ID:"), 0, 0);
        txGrid.add(new Label(record.getDealId()), 1, 0);

        txGrid.add(new Label("Deal Type:"), 0, 1);
        Label typeBadge = new Label(record.getDealTypeDisplay());
        typeBadge.getStyleClass().addAll("badge", switch (record.getDealTypeDisplay().toLowerCase()) {
            case "sale" -> "badge-sell";
            case "loan" -> "badge-lend";
            case "giveaway" -> "badge-giveaway";
            default -> "badge-info";
        });
        txGrid.add(typeBadge, 1, 1);

        txGrid.add(new Label("Deal Status:"), 0, 2);
        Label statusBadge = new Label(record.getDealStatusDisplay());
        statusBadge.getStyleClass().addAll("badge", switch (record.getDealStatusDisplay().toLowerCase()) {
            case "active" -> "badge-active";
            case "completed" -> "badge-completed";
            case "returned" -> "badge-returned";
            default -> "badge-warning";
        });
        txGrid.add(statusBadge, 1, 2);

        txGrid.add(new Label("Start Date:"), 0, 3);
        txGrid.add(new Label(record.getStartDateDisplay()), 1, 3);

        txGrid.add(new Label("Return / Due Date:"), 0, 4);
        txGrid.add(new Label(record.getReturnOrCompleteDateDisplay()), 1, 4);

        txGrid.add(new Label("Agreed Terms:"), 0, 5);
        Label termsLabel = new Label(record.getAgreedTermsDisplay());
        termsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");
        txGrid.add(termsLabel, 1, 5);

        // 2. Users Involved (Alternative Flow A5)
        Label usersSectionLabel = new Label("2. Parties Involved");
        usersSectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #003974;");

        GridPane usersGrid = new GridPane();
        usersGrid.setHgap(14);
        usersGrid.setVgap(6);

        usersGrid.add(new Label("Buyer:"), 0, 0);
        Label buyerLabel = new Label(record.getBuyerFullNameDisplay());
        if (record.isBuyerDeleted()) {
            buyerLabel.setStyle("-fx-text-fill: #c62828; -fx-font-style: italic;");
        }
        usersGrid.add(buyerLabel, 1, 0);

        usersGrid.add(new Label("Seller:"), 0, 1);
        Label sellerLabel = new Label(record.getSellerFullNameDisplay());
        if (record.isSellerDeleted()) {
            sellerLabel.setStyle("-fx-text-fill: #c62828; -fx-font-style: italic;");
        }
        usersGrid.add(sellerLabel, 1, 1);

        // 3. Listing Details (Alternative Flow A5)
        Label listingSectionLabel = new Label("3. Associated Listing");
        listingSectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #003974;");

        GridPane listingGrid = new GridPane();
        listingGrid.setHgap(14);
        listingGrid.setVgap(6);

        listingGrid.add(new Label("Listing Title:"), 0, 0);
        Label titleLabel = new Label(record.getItemTitleDisplay());
        titleLabel.setStyle("-fx-font-weight: bold;");
        listingGrid.add(titleLabel, 1, 0);

        if (record.getListing() != null) {
            Listing l = record.getListing();
            listingGrid.add(new Label("Listing ID:"), 0, 1);
            listingGrid.add(new Label(l.getId()), 1, 1);

            listingGrid.add(new Label("Category:"), 0, 2);
            listingGrid.add(new Label(l.getCategory() != null ? l.getCategory().getDisplayName() : "-"), 1, 2);

            listingGrid.add(new Label("Condition:"), 0, 3);
            listingGrid.add(new Label(l.getCondition() != null ? l.getCondition().getDisplayName() : "-"), 1, 3);

            listingGrid.add(new Label("Meetup Point:"), 0, 4);
            listingGrid.add(new Label(l.getMeetupLocation() != null ? l.getMeetupLocation().getDisplayName() : "-"), 1, 4);

            listingGrid.add(new Label("Original Price:"), 0, 5);
            listingGrid.add(new Label(l.getPrice() != null ? String.format(Locale.ENGLISH, "$%.2f", l.getPrice()) : "Free"), 1, 5);

            listingGrid.add(new Label("Listing Status:"), 0, 6);
            Label lStatus = new Label(l.getStatus() != null ? l.getStatus().name() : "-");
            lStatus.getStyleClass().addAll("badge", record.isListingRemoved() ? "badge-danger" : "badge-live");
            listingGrid.add(lStatus, 1, 6);
        } else {
            listingGrid.add(new Label("Status:"), 0, 1);
            Label remLabel = new Label("Listing record removed; preserved in deal history");
            remLabel.setStyle("-fx-text-fill: #c62828; -fx-font-style: italic;");
            listingGrid.add(remLabel, 1, 1);
        }

        // 4. Accepted Offer Details
        Label offerSectionLabel = new Label("4. Accepted Offer Details");
        offerSectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #003974;");

        GridPane offerGrid = new GridPane();
        offerGrid.setHgap(14);
        offerGrid.setVgap(6);

        if (record.getOffer() != null) {
            Offer o = record.getOffer();
            offerGrid.add(new Label("Offer ID:"), 0, 0);
            offerGrid.add(new Label(o.getId() != null ? o.getId() : "-"), 1, 0);

            offerGrid.add(new Label("Offer Type:"), 0, 1);
            offerGrid.add(new Label(o.getType() != null ? o.getType().name() : "-"), 1, 1);

            offerGrid.add(new Label("Offered Price / Dates:"), 0, 2);
            String offerTerms = o.getPrice() != null ? String.format(Locale.ENGLISH, "$%.2f", o.getPrice()) :
                    (o.getStartDate() != null ? o.getStartDate().toLocalDate() + " to " + (o.getEndDate() != null ? o.getEndDate().toLocalDate() : "-") : "N/A");
            offerGrid.add(new Label(offerTerms), 1, 2);

            if (o.getDeclineReason() != null && !o.getDeclineReason().isBlank()) {
                offerGrid.add(new Label("Decline Reason:"), 0, 3);
                offerGrid.add(new Label(o.getDeclineReason()), 1, 3);
            }
        } else {
            offerGrid.add(new Label("Offer:"), 0, 0);
            offerGrid.add(new Label("Direct agreement / Claim without prior formal counter-offer"), 1, 0);
        }

        // 5. Buyer Rating and Review
        Label reviewSectionLabel = new Label("5. Buyer Rating & Review");
        reviewSectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #003974;");

        VBox reviewBox = new VBox(6);
        if (record.hasReview()) {
            int rating = record.getReviewRating();
            Label starsLabel = new Label("★".repeat(Math.max(0, rating)) + "☆".repeat(Math.max(0, 5 - rating))
                    + " (" + rating + " / 5)" + (record.isReviewHidden() ? " [Hidden by Admin]" : ""));
            starsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #b8860b;");

            Label commentLabel = new Label(record.getReviewComment() != null && !record.getReviewComment().isBlank()
                    ? "\"" + record.getReviewComment() + "\"" : "No written review text provided.");
            commentLabel.setWrapText(true);
            commentLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #5a5a7a;");

            Label dateLabel = new Label("Reviewed on: " + record.getReviewDateDisplay());
            dateLabel.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

            reviewBox.getChildren().addAll(starsLabel, commentLabel, dateLabel);
        } else {
            Label noReview = new Label("No rating or review has been submitted for this transaction.");
            noReview.setStyle("-fx-text-fill: #9aa0b0; -fx-font-style: italic;");
            reviewBox.getChildren().add(noReview);
        }

        content.getChildren().addAll(
                txSectionLabel, txGrid, new Separator(),
                usersSectionLabel, usersGrid, new Separator(),
                listingSectionLabel, listingGrid, new Separator(),
                offerSectionLabel, offerGrid, new Separator(),
                reviewSectionLabel, reviewBox
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(450);
        scrollPane.setMaxHeight(550);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.showAndWait();
    }

    @FXML
    private void showRatings() {
        setActive(btnRatings, "Seller Ratings Overview");
        contentPane.getChildren().clear();

        AdminRatingService ratingService = DummyDataService.getInstance().getAdminRatingService();
        String adminUsername = getEffectiveAdminUsername();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        // 1. Summary Metrics Overview Row
        AdminRatingSummaryMetrics metrics = ratingService.getSummaryMetrics();
        HBox metricsRow = new HBox(12);
        metricsRow.setAlignment(Pos.CENTER_LEFT);
        metricsRow.getChildren().addAll(
                createMetricCard("Total Reviews", String.valueOf(metrics.getTotalReviews()), "#003974", "All Feedback"),
                createMetricCard("Flagged Reviews", String.valueOf(metrics.getFlaggedReviews()),
                        metrics.getFlaggedReviews() > 0 ? "#b45309" : "#16a34a",
                        metrics.getFlaggedReviews() > 0 ? "⚠️ Needs Review" : "✓ None Flagged"),
                createMetricCard("Hidden Reviews", String.valueOf(metrics.getHiddenReviews()), "#64748b", "Excluded from Ratings"),
                createMetricCard("Visible Reviews", String.valueOf(metrics.getVisibleReviews()), "#2e7d32", "Counted in Ratings"),
                createMetricCard("Platform Avg", String.format(Locale.ENGLISH, "%.1f ★", metrics.getAveragePlatformRating()), "#A68137", "Visible Average")
        );
        root.getChildren().add(metricsRow);

        // 2. Filter Toolbar (Alternative Flow A2 & A3)
        HBox filterCard = new HBox(10);
        filterCard.setAlignment(Pos.CENTER_LEFT);
        filterCard.getStyleClass().add("card");
        filterCard.setPadding(new Insets(10, 14, 10, 14));

        TextField userQueryField = new TextField();
        userQueryField.setPromptText("Filter seller, buyer, listing...");
        userQueryField.setPrefWidth(210);

        ComboBox<String> scoreFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All Scores", "5 Stars", "4 Stars", "3 Stars", "2 Stars", "1 Star"
        ));
        scoreFilter.getSelectionModel().selectFirst();
        scoreFilter.setPrefWidth(120);

        ComboBox<String> stateFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All Reviews", "Flagged Only", "Hidden Only", "Visible Only"
        ));
        stateFilter.getSelectionModel().selectFirst();
        stateFilter.setPrefWidth(130);

        DatePicker startDatePicker = new DatePicker();
        startDatePicker.setPromptText("Start Date");
        startDatePicker.setPrefWidth(130);

        DatePicker endDatePicker = new DatePicker();
        endDatePicker.setPromptText("End Date");
        endDatePicker.setPrefWidth(130);

        Button applyFilterBtn = new Button("Apply Filters");
        applyFilterBtn.getStyleClass().addAll("btn-primary", "btn-sm");

        Button resetFilterBtn = new Button("Reset");
        resetFilterBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        refreshBtn.setOnAction(e -> showRatings());

        filterCard.getChildren().addAll(
                userQueryField, scoreFilter, stateFilter,
                startDatePicker, endDatePicker,
                applyFilterBtn, resetFilterBtn, spacer, refreshBtn
        );
        root.getChildren().add(filterCard);

        // 3. Reviews Container
        VBox reviewsContainer = new VBox(12);
        root.getChildren().add(reviewsContainer);

        // Helper to populate reviews
        Runnable updateReviewsDisplay = () -> {
            reviewsContainer.getChildren().clear();

            AdminRatingFilterCriteria criteria = new AdminRatingFilterCriteria();

            // Flow A3: Validate Date Range
            LocalDate start = startDatePicker.getValue();
            LocalDate end = endDatePicker.getValue();
            if (start != null && end != null && end.isBefore(start)) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Invalid Date Range");
                alert.setHeaderText("Filter Not Applied");
                alert.setContentText("The end date cannot be earlier than the start date. Please select a valid date range.");
                alert.showAndWait();
                return;
            }

            criteria.setStartDate(start);
            criteria.setEndDate(end);

            String query = userQueryField.getText();
            if (query != null && !query.trim().isEmpty()) {
                criteria.setUserQuery(query.trim());
            }

            int scoreIdx = scoreFilter.getSelectionModel().getSelectedIndex();
            if (scoreIdx > 0) {
                criteria.setRatingScore(6 - scoreIdx);
            }

            int stateIdx = stateFilter.getSelectionModel().getSelectedIndex();
            if (stateIdx == 1) {
                criteria.setStateFilter(AdminRatingFilterCriteria.ReviewStateFilter.FLAGGED_ONLY);
            } else if (stateIdx == 2) {
                criteria.setStateFilter(AdminRatingFilterCriteria.ReviewStateFilter.HIDDEN_ONLY);
            } else if (stateIdx == 3) {
                criteria.setStateFilter(AdminRatingFilterCriteria.ReviewStateFilter.VISIBLE_ONLY);
            }

            List<Review> filtered = ratingService.filterReviews(criteria);

            // Flow A1: No Reviews at all in system
            if (ratingService.getOrderedReviews().isEmpty()) {
                VBox empty = new VBox(10);
                empty.setAlignment(Pos.CENTER);
                empty.setPadding(new Insets(40));
                empty.getStyleClass().add("card");
                Label icon = new Label("⭐");
                icon.setStyle("-fx-font-size: 36px;");
                Label h = new Label("No Ratings Yet");
                h.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
                Label p = new Label("There are currently no seller reviews or ratings submitted in the marketplace.");
                p.setStyle("-fx-text-fill: #5a5a7a;");
                empty.getChildren().addAll(icon, h, p);
                reviewsContainer.getChildren().add(empty);
                return;
            }

            // Flow A2: Filter matched nothing
            if (filtered.isEmpty()) {
                VBox empty = new VBox(10);
                empty.setAlignment(Pos.CENTER);
                empty.setPadding(new Insets(40));
                empty.getStyleClass().add("card");
                Label icon = new Label("🔍");
                icon.setStyle("-fx-font-size: 36px;");
                Label h = new Label("No Matching Reviews");
                h.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
                Label p = new Label("No reviews match your selected filter criteria. Try broadening your search or resetting filters.");
                p.setStyle("-fx-text-fill: #5a5a7a;");
                Button clearBtn = new Button("Clear Filters");
                clearBtn.getStyleClass().addAll("btn-outline", "btn-sm");
                clearBtn.setOnAction(e -> {
                    userQueryField.clear();
                    scoreFilter.getSelectionModel().selectFirst();
                    stateFilter.getSelectionModel().selectFirst();
                    startDatePicker.setValue(null);
                    endDatePicker.setValue(null);
                    showRatings();
                });
                empty.getChildren().addAll(icon, h, p, clearBtn);
                reviewsContainer.getChildren().add(empty);
                return;
            }

            for (Review r : filtered) {
                reviewsContainer.getChildren().add(createAdminReviewCard(r, ratingService, adminUsername));
            }
        };

        applyFilterBtn.setOnAction(e -> updateReviewsDisplay.run());
        resetFilterBtn.setOnAction(e -> {
            userQueryField.clear();
            scoreFilter.getSelectionModel().selectFirst();
            stateFilter.getSelectionModel().selectFirst();
            startDatePicker.setValue(null);
            endDatePicker.setValue(null);
            updateReviewsDisplay.run();
        });

        // Initial populate
        updateReviewsDisplay.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox createAdminReviewCard(Review r, AdminRatingService ratingService, String adminUsername) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // Top Header Row: Stars, Score, Status Badges, Date
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label starsLabel = new Label("★".repeat(r.getRating()) + "☆".repeat(5 - r.getRating()));
        starsLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #A68137;");

        Label scoreBadge = new Label(r.getRating() + "/5");
        scoreBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        topRow.getChildren().addAll(starsLabel, scoreBadge);

        if (r.isFlagged()) {
            Label flagBadge = new Label("🚩 Flagged by Seller");
            flagBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-padding: 3 8; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11px;");
            topRow.getChildren().add(flagBadge);
        }

        if (r.isHidden()) {
            Label hiddenBadge = new Label("🔒 Hidden");
            hiddenBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-padding: 3 8; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11px;");
            topRow.getChildren().add(hiddenBadge);
        } else {
            Label visibleBadge = new Label("👁️ Visible");
            visibleBadge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-padding: 3 8; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11px;");
            topRow.getChildren().add(visibleBadge);
        }

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topRow.getChildren().add(topSpacer);

        String dateStr = r.getDate() != null ? r.getDate().format(dtf) : "Unknown Date";
        Label dateLabel = new Label("🗓️ " + dateStr);
        dateLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        topRow.getChildren().add(dateLabel);

        card.getChildren().add(topRow);

        // Subject & Context Row: Listing & Buyer -> Seller
        HBox contextRow = new HBox(12);
        contextRow.setAlignment(Pos.CENTER_LEFT);

        String listingDisplay = (r.getListingTitle() != null && !r.getListingTitle().trim().isEmpty())
                ? r.getListingTitle() : "Listing";
        Label listingLabel = new Label("📦 Listing: " + listingDisplay + (r.getListingId() != null ? " (" + r.getListingId() + ")" : ""));
        listingLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 13px;");

        Label partiesLabel = new Label("Buyer: @" + r.getBuyerUsername() + "  →  Seller: @" + r.getSellerUsername());
        partiesLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        contextRow.getChildren().addAll(listingLabel, partiesLabel);
        card.getChildren().add(contextRow);

        // Written Review Comment
        if (r.getComment() != null && !r.getComment().trim().isEmpty()) {
            Label commentLabel = new Label("\"" + r.getComment().trim() + "\"");
            commentLabel.setWrapText(true);
            commentLabel.setStyle("-fx-text-fill: #1a1a2e; -fx-font-size: 13px; -fx-font-style: italic; -fx-padding: 2 0;");
            card.getChildren().add(commentLabel);
        }

        // Flag reason callout banner
        if (r.isFlagged() && r.getFlagReason() != null && !r.getFlagReason().trim().isEmpty()) {
            HBox flagBox = new HBox(8);
            flagBox.setAlignment(Pos.CENTER_LEFT);
            flagBox.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #fca5a5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12;");
            Label flagText = new Label("🚩 Seller Flag Reason: " + r.getFlagReason().trim());
            flagText.setWrapText(true);
            flagText.setStyle("-fx-text-fill: #991b1b; -fx-font-size: 12px; -fx-font-weight: bold;");
            flagBox.getChildren().add(flagText);
            card.getChildren().add(flagBox);
        }

        // Seller reply callout banner
        if (r.getReply() != null && !r.getReply().trim().isEmpty()) {
            HBox replyBox = new HBox(8);
            replyBox.setAlignment(Pos.CENTER_LEFT);
            replyBox.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #bbf7d0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12;");
            Label replyText = new Label("💬 Seller Reply (@" + r.getSellerUsername() + "): " + r.getReply().trim());
            replyText.setWrapText(true);
            replyText.setStyle("-fx-text-fill: #166534; -fx-font-size: 12px;");
            replyBox.getChildren().add(replyText);
            card.getChildren().add(replyBox);
        }

        // Actions Row: Dismiss Flag (Subflow S3), Hide (Subflow S1), Unhide (Subflow S2)
        HBox actionsRow = new HBox(10);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);

        if (r.isFlagged()) {
            Button dismissBtn = new Button("Dismiss Flag");
            dismissBtn.getStyleClass().addAll("btn-outline", "btn-sm");
            dismissBtn.setOnAction(e -> {
                // Subflow S3 Step 1: Confirmation
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Dismiss Flag");
                confirm.setHeaderText("Dismiss Seller Flag for Review " + r.getId() + "?");
                confirm.setContentText("The review will remain visible to users and the seller will be notified that their flag was dismissed.");
                Optional<ButtonType> opt = confirm.showAndWait();
                if (opt.isPresent() && opt.get() == ButtonType.OK) {
                    AdminRatingOperationResult res = ratingService.dismissFlag(r.getId(), adminUsername);
                    if (res.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, res.getMessage()).showAndWait();
                        showRatings();
                    } else {
                        new Alert(Alert.AlertType.ERROR, res.getMessage()).showAndWait();
                    }
                }
            });
            actionsRow.getChildren().add(dismissBtn);
        }

        if (!r.isHidden()) {
            Button hideBtn = new Button("Hide Review");
            hideBtn.getStyleClass().addAll("btn-danger", "btn-sm");
            hideBtn.setOnAction(e -> {
                // Subflow S1 Step 1: Confirmation
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Hide Review");
                confirm.setHeaderText("Hide Review " + r.getId() + "?");
                confirm.setContentText("Are you sure you want to hide this review? It will be excluded from @" + r.getSellerUsername()
                        + "'s average rating and review list.\nBoth the seller and the buyer will be notified.");
                Optional<ButtonType> opt = confirm.showAndWait();
                if (opt.isPresent() && opt.get() == ButtonType.OK) {
                    AdminRatingOperationResult res = ratingService.hideReview(r.getId(), adminUsername);
                    if (res.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, res.getMessage()).showAndWait();
                        showRatings();
                    } else {
                        new Alert(Alert.AlertType.ERROR, res.getMessage()).showAndWait();
                    }
                }
            });
            actionsRow.getChildren().add(hideBtn);
        } else {
            Button unhideBtn = new Button("Unhide Review");
            unhideBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            unhideBtn.setOnAction(e -> {
                // Subflow S2 Step 1: Confirmation
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Unhide Review");
                confirm.setHeaderText("Unhide Review " + r.getId() + "?");
                confirm.setContentText("Are you sure you want to show this review again? It will be counted towards @"
                        + r.getSellerUsername() + "'s average rating.");
                Optional<ButtonType> opt = confirm.showAndWait();
                if (opt.isPresent() && opt.get() == ButtonType.OK) {
                    AdminRatingOperationResult res = ratingService.unhideReview(r.getId(), adminUsername);
                    if (res.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, res.getMessage()).showAndWait();
                        showRatings();
                    } else {
                        new Alert(Alert.AlertType.ERROR, res.getMessage()).showAndWait();
                    }
                }
            });
            actionsRow.getChildren().add(unhideBtn);
        }

        card.getChildren().add(actionsRow);
        return card;
    }


    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Admin Notifications");
        contentPane.getChildren().clear();

        AdminNotificationService service = DummyDataService.getInstance().getAdminNotificationService();
        String adminUsername = getEffectiveAdminUsername();

        VBox container = new VBox(14);
        container.setPadding(new Insets(4));

        // 1. Metrics Overview Row
        HBox metricsRow = createNotificationMetricsRow(service, adminUsername);
        container.getChildren().add(metricsRow);

        // 2. Filter Bar
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("card");
        filterBar.setPadding(new Insets(10, 14, 10, 14));

        ComboBox<String> catBox = new ComboBox<>();
        catBox.setPrefWidth(240);
        updateCategoryBoxOptions(catBox, service, adminUsername);

        TextField searchField = new TextField();
        searchField.setPromptText("Search title, message, ID...");
        searchField.setPrefWidth(240);

        CheckBox unreadCheck = new CheckBox("Unread only");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button markAllBtn = new Button("Mark All Read");
        markAllBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        filterBar.getChildren().addAll(new Label("Category:"), catBox, searchField, unreadCheck, spacer, markAllBtn, refreshBtn);
        container.getChildren().add(filterBar);

        // 3. Notification Cards Container
        VBox listContainer = new VBox(10);
        container.getChildren().add(listContainer);

        Runnable refreshView = () -> {
            updateCategoryBoxOptions(catBox, service, adminUsername);
            String selectedCatStr = catBox.getValue();
            AdminNotificationCategory selectedCategory = parseSelectedCategory(selectedCatStr);

            boolean unreadOnly = unreadCheck.isSelected();
            String query = searchField.getText();

            AdminNotificationFilterCriteria criteria = new AdminNotificationFilterCriteria(
                    selectedCategory, unreadOnly, query);

            List<Notification> notifs = service.getNotifications(adminUsername, criteria);
            renderNotificationCards(listContainer, notifs, service, adminUsername, () -> showNotifications());
        };

        catBox.setOnAction(e -> {
            if (catBox.getValue() != null) {
                String selectedCatStr = catBox.getValue();
                AdminNotificationCategory selectedCategory = parseSelectedCategory(selectedCatStr);
                boolean unreadOnly = unreadCheck.isSelected();
                String query = searchField.getText();
                AdminNotificationFilterCriteria criteria = new AdminNotificationFilterCriteria(selectedCategory, unreadOnly, query);
                List<Notification> notifs = service.getNotifications(adminUsername, criteria);
                renderNotificationCards(listContainer, notifs, service, adminUsername, () -> showNotifications());
            }
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String selectedCatStr = catBox.getValue();
            AdminNotificationCategory selectedCategory = parseSelectedCategory(selectedCatStr);
            boolean unreadOnly = unreadCheck.isSelected();
            AdminNotificationFilterCriteria criteria = new AdminNotificationFilterCriteria(selectedCategory, unreadOnly, newVal);
            List<Notification> notifs = service.getNotifications(adminUsername, criteria);
            renderNotificationCards(listContainer, notifs, service, adminUsername, () -> showNotifications());
        });

        unreadCheck.setOnAction(e -> {
            String selectedCatStr = catBox.getValue();
            AdminNotificationCategory selectedCategory = parseSelectedCategory(selectedCatStr);
            boolean unreadOnly = unreadCheck.isSelected();
            String query = searchField.getText();
            AdminNotificationFilterCriteria criteria = new AdminNotificationFilterCriteria(selectedCategory, unreadOnly, query);
            List<Notification> notifs = service.getNotifications(adminUsername, criteria);
            renderNotificationCards(listContainer, notifs, service, adminUsername, () -> showNotifications());
        });

        markAllBtn.setOnAction(e -> {
            int updated = service.markAllAsRead(adminUsername);
            showNotifications();
            if (updated > 0) {
                new Alert(Alert.AlertType.INFORMATION, updated + " notification(s) marked as read.").showAndWait();
            }
        });

        refreshBtn.setOnAction(e -> showNotifications());

        // Initial render
        List<Notification> initialNotifs = service.getNotifications(adminUsername, AdminNotificationFilterCriteria.all());
        renderNotificationCards(listContainer, initialNotifs, service, adminUsername, () -> showNotifications());

        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createNotificationMetricsRow(AdminNotificationService service, String adminUsername) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        int unreadCount = service.getUnreadCount(adminUsername);
        int pendingListings = DummyDataService.getInstance().getListingReviewService().getPendingReviewCount();
        long openReports = DummyDataService.getInstance().getAllReports().stream()
                .filter(r -> r != null && r.getStatus() == ReportStatus.OPEN)
                .count();
        long flaggedReviews = DummyDataService.getInstance().getAllReviews().stream()
                .filter(r -> r != null && r.isFlagged() && !r.isHidden())
                .count();

        row.getChildren().addAll(
                createMetricCard("Unread Alerts", String.valueOf(unreadCount), "#003974", unreadCount > 0 ? "🔔 Needs Attention" : "✅ All Clear"),
                createMetricCard("Submissions Queue", String.valueOf(pendingListings), "#0284c7", pendingListings > 0 ? "📦 Awaiting Review" : "✅ Queue Clear"),
                createMetricCard("Open Reports", String.valueOf(openReports), openReports > 0 ? "#dc2626" : "#16a34a", openReports > 0 ? "🚩 Action Required" : "✅ None Pending"),
                createMetricCard("Flagged Reviews", String.valueOf(flaggedReviews), flaggedReviews > 0 ? "#b45309" : "#16a34a", flaggedReviews > 0 ? "⚠️ Seller Flags" : "✅ None Flagged")
        );
        return row;
    }

    private VBox createMetricCard(String title, String count, String color, String subtitle) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12, 16, 12, 16));
        HBox.setHgrow(card, Priority.ALWAYS);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label countLbl = new Label(count);
        countLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-text-fill: #8590a6; -fx-font-size: 11px;");

        card.getChildren().addAll(titleLbl, countLbl, subLbl);
        return card;
    }

    private void updateCategoryBoxOptions(ComboBox<String> catBox, AdminNotificationService service, String adminUsername) {
        String current = catBox.getValue();
        java.util.Map<AdminNotificationCategory, Long> totalCounts = service.getCategoryCounts(adminUsername);
        java.util.Map<AdminNotificationCategory, Long> unreadCounts = service.getCategoryUnreadCounts(adminUsername);

        long total = totalCounts.values().stream().mapToLong(Long::longValue).sum();
        long totalUnread = unreadCounts.values().stream().mapToLong(Long::longValue).sum();

        javafx.collections.ObservableList<String> items = FXCollections.observableArrayList();
        items.add("All Categories (" + total + (totalUnread > 0 ? " · " + totalUnread + " unread" : "") + ")");

        for (AdminNotificationCategory cat : AdminNotificationCategory.values()) {
            long c = totalCounts.getOrDefault(cat, 0L);
            long u = unreadCounts.getOrDefault(cat, 0L);
            String label = cat.getIcon() + " " + cat.getDisplayName() + " (" + c + (u > 0 ? " · " + u + " unread" : "") + ")";
            items.add(label);
        }

        catBox.setItems(items);
        if (current != null) {
            for (String item : items) {
                if (item.startsWith(current.split(" \\(")[0])) {
                    catBox.setValue(item);
                    return;
                }
            }
        }
        catBox.getSelectionModel().selectFirst();
    }

    private AdminNotificationCategory parseSelectedCategory(String label) {
        if (label == null || label.startsWith("All Categories")) {
            return null;
        }
        for (AdminNotificationCategory cat : AdminNotificationCategory.values()) {
            if (label.contains(cat.getDisplayName())) {
                return cat;
            }
        }
        return AdminNotificationCategory.fromRaw(label);
    }

    private void renderNotificationCards(
            VBox listContainer,
            List<Notification> notifs,
            AdminNotificationService service,
            String adminUsername,
            Runnable onRefresh) {

        listContainer.getChildren().clear();

        if (notifs == null || notifs.isEmpty()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40, 20, 40, 20));
            emptyBox.getStyleClass().add("card");

            Label emptyTitle = new Label("No Notifications Found");
            emptyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #003974;");

            Label emptySub = new Label("There are no admin notifications matching your active category and filter criteria.");
            emptySub.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");

            emptyBox.getChildren().addAll(emptyTitle, emptySub);
            listContainer.getChildren().add(emptyBox);
            return;
        }

        for (Notification n : notifs) {
            VBox card = new VBox(8);
            card.getStyleClass().addAll("notif-item", n.isUnread() ? "notif-item-unread" : "notif-item-read");
            card.setPadding(new Insets(12, 16, 12, 16));

            // Top Header: Badge, Title, Unread Tag, Timestamp
            HBox topRow = new HBox(10);
            topRow.setAlignment(Pos.CENTER_LEFT);

            AdminNotificationCategory cat = AdminNotificationCategory.fromRaw(n.getCategory());
            Label catBadge = new Label(cat.getIcon() + " " + cat.getDisplayName());
            catBadge.getStyleClass().add("badge");
            switch (cat) {
                case LISTING_SUBMISSIONS -> catBadge.getStyleClass().add("badge-listings");
                case REPORTS -> catBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-weight: bold;");
                case FLAGGED_REVIEWS -> catBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-weight: bold;");
                case USER_ACCOUNTS -> catBadge.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-weight: bold;");
                case ANNOUNCEMENTS -> catBadge.getStyleClass().add("badge-announcements");
            }

            Label titleLbl = new Label(n.getTitle() != null ? n.getTitle() : "Notification");
            titleLbl.setStyle("-fx-font-weight: " + (n.isUnread() ? "bold" : "normal") + "; -fx-font-size: 14px; -fx-text-fill: #0f172a;");
            HBox.setHgrow(titleLbl, Priority.ALWAYS);

            if (n.isUnread()) {
                Label unreadBadge = new Label("NEW");
                unreadBadge.getStyleClass().add("badge-unread");
                topRow.getChildren().addAll(catBadge, titleLbl, unreadBadge);
            } else {
                topRow.getChildren().addAll(catBadge, titleLbl);
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            topRow.getChildren().add(spacer);

            String timeStr = n.getFormattedTimestamp();
            Label timeLbl = new Label(timeStr != null ? timeStr : "");
            timeLbl.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
            topRow.getChildren().add(timeLbl);

            // Message Body
            Label msgLbl = new Label(n.getMessage() != null ? n.getMessage() : "");
            msgLbl.setWrapText(true);
            msgLbl.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");

            // Bottom Actions Row
            HBox bottomRow = new HBox(10);
            bottomRow.setAlignment(Pos.CENTER_LEFT);

            if (n.getRelatedItemId() != null && !n.getRelatedItemId().trim().isEmpty()) {
                String typeDesc = n.getRelatedItemType() != null ? n.getRelatedItemType() : "Item";
                Label refBadge = new Label(typeDesc + " #" + n.getRelatedItemId());
                refBadge.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-background-color: #f1f5f9; -fx-padding: 2 6; -fx-background-radius: 4;");
                bottomRow.getChildren().add(refBadge);
            }

            Region bSpacer = new Region();
            HBox.setHgrow(bSpacer, Priority.ALWAYS);
            bottomRow.getChildren().add(bSpacer);

            if (n.isUnread()) {
                Button markReadBtn = new Button("Mark Read");
                markReadBtn.getStyleClass().addAll("btn-outline", "btn-sm");
                markReadBtn.setOnAction(e -> {
                    service.markAsRead(n.getId(), adminUsername);
                    onRefresh.run();
                });
                bottomRow.getChildren().add(markReadBtn);
            }

            Button viewBtn = new Button("View Details");
            viewBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            viewBtn.setOnAction(e -> showNotificationDetailsDialog(n, service, adminUsername, onRefresh));
            bottomRow.getChildren().add(viewBtn);

            card.getChildren().addAll(topRow, msgLbl, bottomRow);
            listContainer.getChildren().add(card);
        }
    }

    private void showNotificationDetailsDialog(
            Notification n,
            AdminNotificationService service,
            String adminUsername,
            Runnable onRefresh) {

        AdminNotificationDetailResult res = service.getNotificationDetail(n.getId(), adminUsername);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Notification Details - " + n.getId());
        dialog.setHeaderText(n.getTitle());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(14);
        content.setPrefWidth(540);
        content.setPadding(new Insets(12));

        // 1. Notification Information Card
        VBox notifBox = new VBox(6);
        notifBox.getStyleClass().add("card");
        notifBox.setPadding(new Insets(12));

        Label notifHead = new Label("Notification Information");
        notifHead.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #003974;");

        GridPane notifGrid = new GridPane();
        notifGrid.setHgap(10);
        notifGrid.setVgap(6);

        notifGrid.add(new Label("Category:"), 0, 0);
        notifGrid.add(new Label(n.getCategory()), 1, 0);

        notifGrid.add(new Label("Timestamp:"), 0, 1);
        notifGrid.add(new Label(n.getFormattedTimestamp()), 1, 1);

        notifGrid.add(new Label("Message:"), 0, 2);
        Label mVal = new Label(n.getMessage());
        mVal.setWrapText(true);
        notifGrid.add(mVal, 1, 2);

        notifBox.getChildren().addAll(notifHead, notifGrid);
        content.getChildren().add(notifBox);

        // 2. Related Item Section
        VBox relatedBox = new VBox(8);
        relatedBox.getStyleClass().add("card");
        relatedBox.setPadding(new Insets(12));

        Label relatedHead = new Label("Related Domain Entity");
        relatedHead.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #003974;");
        relatedBox.getChildren().add(relatedHead);

        if (res.getStatus() == AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE) {
            // Alternative Flow A4: Related Item No Longer Exists
            VBox warnBox = new VBox(6);
            warnBox.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");
            Label warnLbl = new Label("⚠️ Item No Longer Available");
            warnLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #b91c1c;");
            Label warnMsg = new Label(res.getMessage());
            warnMsg.setWrapText(true);
            warnMsg.setStyle("-fx-text-fill: #7f1d1d; -fx-font-size: 12px;");
            warnBox.getChildren().addAll(warnLbl, warnMsg);
            relatedBox.getChildren().add(warnBox);

        } else if ("REPORT".equalsIgnoreCase(res.getTargetType()) && res.getReport() != null) {
            // Alternative Flow A3: Resolve Report
            Report report = res.getReport();
            GridPane repGrid = new GridPane();
            repGrid.setHgap(10);
            repGrid.setVgap(6);

            repGrid.add(new Label("Report ID:"), 0, 0);
            repGrid.add(new Label(report.getId()), 1, 0);

            repGrid.add(new Label("Reported Listing:"), 0, 1);
            repGrid.add(new Label(report.getListingTitle() + " (ID: " + report.getListingId() + ")"), 1, 1);

            repGrid.add(new Label("Reporting Buyer:"), 0, 2);
            repGrid.add(new Label("@" + report.getBuyerUsername()), 1, 2);

            repGrid.add(new Label("Reason Filed:"), 0, 3);
            Label rVal = new Label(report.getReason());
            rVal.setWrapText(true);
            repGrid.add(rVal, 1, 3);

            repGrid.add(new Label("Status:"), 0, 4);
            Label repStatus = new Label(report.getStatus() != null ? report.getStatus().name() : "OPEN");
            repStatus.getStyleClass().addAll("badge", report.getStatus() == ReportStatus.RESOLVED ? "badge-success" : "badge-danger");
            repGrid.add(repStatus, 1, 4);

            relatedBox.getChildren().add(repGrid);

            if (report.getStatus() == ReportStatus.OPEN) {
                HBox repActions = new HBox(10);
                repActions.setAlignment(Pos.CENTER_RIGHT);
                repActions.setPadding(new Insets(10, 0, 0, 0));

                Button resolveBtn = new Button("Mark as Resolved");
                resolveBtn.getStyleClass().addAll("btn-success", "btn-sm");
                resolveBtn.setOnAction(ev -> {
                    Alert confirm = new Alert(
                            Alert.AlertType.CONFIRMATION,
                            "Confirm Report Resolution:\n\nMark report " + report.getId() + " regarding '" + report.getListingTitle() + "' as Resolved?\nBuyer @" + report.getBuyerUsername() + " will be notified.",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Confirm Report Resolution");
                    Optional<ButtonType> opt = confirm.showAndWait();
                    if (opt.isPresent() && opt.get() == ButtonType.YES) {
                        AdminReportResolutionResult resolveRes = service.resolveReport(report.getId(), adminUsername, "Reviewed by administrator");
                        if (resolveRes.isSuccess()) {
                            new Alert(Alert.AlertType.INFORMATION, "Report " + report.getId() + " has been marked as Resolved. The buyer has been notified.").showAndWait();
                            dialog.close();
                            onRefresh.run();
                        } else {
                            new Alert(Alert.AlertType.ERROR, resolveRes.getMessage()).showAndWait();
                        }
                    }
                });
                repActions.getChildren().add(resolveBtn);
                relatedBox.getChildren().add(repActions);
            }

        } else if ("LISTING".equalsIgnoreCase(res.getTargetType()) && res.getListing() != null) {
            Listing l = res.getListing();
            GridPane lGrid = new GridPane();
            lGrid.setHgap(10);
            lGrid.setVgap(6);

            lGrid.add(new Label("Title:"), 0, 0);
            lGrid.add(new Label(l.getTitle()), 1, 0);

            lGrid.add(new Label("Seller:"), 0, 1);
            lGrid.add(new Label(l.getSellerFullName() + " (@" + l.getSellerUsername() + ")"), 1, 1);

            lGrid.add(new Label("Type / Category:"), 0, 2);
            lGrid.add(new Label(l.getTypeDisplay() + " · " + (l.getCategory() != null ? l.getCategory().getDisplayName() : "-")), 1, 2);

            lGrid.add(new Label("Status:"), 0, 3);
            Label lStatus = new Label(l.getStatus() != null ? l.getStatus().name() : "LIVE");
            lStatus.getStyleClass().addAll("badge", l.isPendingReview() ? "badge-pending" : (l.isLive() ? "badge-live" : "badge-sold"));
            lGrid.add(lStatus, 1, 3);

            relatedBox.getChildren().add(lGrid);

            if (l.isPendingReview()) {
                HBox lActions = new HBox(10);
                lActions.setAlignment(Pos.CENTER_RIGHT);
                Button openReviewBtn = new Button("Open Listing Review Queue");
                openReviewBtn.getStyleClass().addAll("btn-primary", "btn-sm");
                openReviewBtn.setOnAction(ev -> {
                    dialog.close();
                    showReview();
                });
                lActions.getChildren().add(openReviewBtn);
                relatedBox.getChildren().add(lActions);
            }

        } else if ("REVIEW".equalsIgnoreCase(res.getTargetType()) && res.getReview() != null) {
            Review rev = res.getReview();
            GridPane rGrid = new GridPane();
            rGrid.setHgap(10);
            rGrid.setVgap(6);

            rGrid.add(new Label("Review ID:"), 0, 0);
            rGrid.add(new Label(rev.getId()), 1, 0);

            rGrid.add(new Label("Seller:"), 0, 1);
            rGrid.add(new Label("@" + rev.getSellerUsername()), 1, 1);

            rGrid.add(new Label("Buyer:"), 0, 2);
            rGrid.add(new Label("@" + rev.getBuyerUsername()), 1, 2);

            rGrid.add(new Label("Rating:"), 0, 3);
            rGrid.add(new Label("⭐ " + rev.getRating() + "/5"), 1, 3);

            rGrid.add(new Label("Buyer Comment:"), 0, 4);
            Label cVal = new Label(rev.getComment());
            cVal.setWrapText(true);
            rGrid.add(cVal, 1, 4);

            if (rev.isFlagged()) {
                rGrid.add(new Label("Seller Flag Reason:"), 0, 5);
                Label fVal = new Label(rev.getFlagReason() != null ? rev.getFlagReason() : "Inappropriate content");
                fVal.setWrapText(true);
                fVal.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold;");
                rGrid.add(fVal, 1, 5);
            }

            relatedBox.getChildren().add(rGrid);

            HBox rActions = new HBox(10);
            rActions.setAlignment(Pos.CENTER_RIGHT);
            Button openRatingsBtn = new Button("Open Seller Ratings Overview");
            openRatingsBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            openRatingsBtn.setOnAction(ev -> {
                dialog.close();
                showRatings();
            });
            rActions.getChildren().add(openRatingsBtn);
            relatedBox.getChildren().add(rActions);

        } else if ("USER".equalsIgnoreCase(res.getTargetType()) && res.getUser() != null) {
            User u = res.getUser();
            GridPane uGrid = new GridPane();
            uGrid.setHgap(10);
            uGrid.setVgap(6);

            uGrid.add(new Label("Username:"), 0, 0);
            uGrid.add(new Label("@" + u.getUsername()), 1, 0);

            uGrid.add(new Label("Full Name:"), 0, 1);
            uGrid.add(new Label(u.getFullName() != null ? u.getFullName() : "-"), 1, 1);

            uGrid.add(new Label("Account Role:"), 0, 2);
            uGrid.add(new Label(u.getRole() != null ? u.getRole().name() : "-"), 1, 2);

            uGrid.add(new Label("Account Status:"), 0, 3);
            Label uStatus = new Label(u.getAccountStatus() != null ? u.getAccountStatus().name() : "ACTIVE");
            uStatus.getStyleClass().addAll("badge", u.isActive() ? "badge-active" : "badge-danger");
            uGrid.add(uStatus, 1, 3);

            relatedBox.getChildren().add(uGrid);

            HBox uActions = new HBox(10);
            uActions.setAlignment(Pos.CENTER_RIGHT);
            Button openUsersBtn = new Button("Open Manage Users Screen");
            openUsersBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            openUsersBtn.setOnAction(ev -> {
                dialog.close();
                showUsers();
            });
            uActions.getChildren().add(openUsersBtn);
            relatedBox.getChildren().add(uActions);

        } else {
            Label annLbl = new Label("📢 System Announcement notice. No further entity actions required.");
            annLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
            relatedBox.getChildren().add(annLbl);
        }

        content.getChildren().add(relatedBox);

        ScrollPane dScroll = new ScrollPane(content);
        dScroll.setFitToWidth(true);
        dialog.getDialogPane().setContent(dScroll);
        dialog.showAndWait();
        onRefresh.run();
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
