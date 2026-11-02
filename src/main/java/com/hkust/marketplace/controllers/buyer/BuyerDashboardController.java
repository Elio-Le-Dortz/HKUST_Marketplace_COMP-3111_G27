package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BuyerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnBrowse, btnDeals, btnHistory, btnFavourites, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    // Browse & Filter controls
    private TextField searchField;
    private ComboBox<String> categoryFilter;
    private ComboBox<String> typeFilter;
    private ComboBox<String> conditionFilter;
    private TextField minPriceField;
    private TextField maxPriceField;
    private ComboBox<ListingSortOrder> sortOrderCombo;
    private FlowPane listingGrid;
    private Label resultsCountLabel;
    private Label priceValidationLabel;
    private VBox browseContainer;

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            welcomeLabel.setText("Hello, " + user.getFullName());
        }
        currentActiveBtn = btnBrowse;
        showBrowse();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) {
            currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        }
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    @FXML
    private void showBrowse() {
        setActive(btnBrowse, "Browse Marketplace");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        // Filter & Search Panel
        VBox filterCard = new VBox(12);
        filterCard.getStyleClass().add("filter-box");

        // Row 1: Search keyword, Category, Type, Condition
        HBox row1 = new HBox(12);
        row1.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Search title or keyword...");
        searchField.setPrefWidth(240);

        categoryFilter = new ComboBox<>(FXCollections.observableArrayList("All Categories"));
        for (Category c : Category.values()) {
            categoryFilter.getItems().add(c.getDisplayName());
        }
        categoryFilter.getSelectionModel().selectFirst();

        typeFilter = new ComboBox<>(FXCollections.observableArrayList("All Types"));
        for (ListingType t : ListingType.values()) {
            typeFilter.getItems().add(t.getDisplayName());
        }
        typeFilter.getSelectionModel().selectFirst();

        conditionFilter = new ComboBox<>(FXCollections.observableArrayList("All Conditions"));
        for (Condition cond : Condition.values()) {
            conditionFilter.getItems().add(cond.getDisplayName());
        }
        conditionFilter.getSelectionModel().selectFirst();

        row1.getChildren().addAll(
                new Label("Search:"), searchField,
                new Label("Category:"), categoryFilter,
                new Label("Type:"), typeFilter,
                new Label("Condition:"), conditionFilter
        );

        // Row 2: Price range, Sort order, Search & Reset actions
        HBox row2 = new HBox(12);
        row2.setAlignment(Pos.CENTER_LEFT);

        minPriceField = new TextField();
        minPriceField.setPromptText("Min");
        minPriceField.setPrefWidth(75);

        maxPriceField = new TextField();
        maxPriceField.setPromptText("Max");
        maxPriceField.setPrefWidth(75);

        sortOrderCombo = new ComboBox<>(FXCollections.observableArrayList(ListingSortOrder.values()));
        sortOrderCombo.getSelectionModel().select(ListingSortOrder.NEWEST_FIRST);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        searchBtn.setOnAction(e -> applyBrowseFilters());

        Button resetBtn = new Button("Reset Filters");
        resetBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        resetBtn.setOnAction(e -> resetBrowseFilters());

        row2.getChildren().addAll(
                new Label("Price HK$:"), minPriceField, new Label("–"), maxPriceField,
                new Label("Sort:"), sortOrderCombo,
                spacer,
                searchBtn, resetBtn
        );

        // Price validation warning label
        priceValidationLabel = new Label();
        priceValidationLabel.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12px;");
        priceValidationLabel.setVisible(false);
        priceValidationLabel.setManaged(false);

        filterCard.getChildren().addAll(row1, row2, priceValidationLabel);
        root.getChildren().add(filterCard);

        // Status bar with result count
        HBox statusBar = new HBox(12);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        resultsCountLabel = new Label("Loading live listings...");
        resultsCountLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-weight: bold; -fx-font-size: 13px;");
        statusBar.getChildren().add(resultsCountLabel);
        root.getChildren().add(statusBar);

        // Dynamic listing container
        browseContainer = new VBox(12);
        listingGrid = new FlowPane(16, 16);
        listingGrid.setPrefWrapLength(940);
        browseContainer.getChildren().add(listingGrid);
        root.getChildren().add(browseContainer);

        // Real-time filtering listeners
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyBrowseFilters());
        categoryFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyBrowseFilters());
        typeFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyBrowseFilters());
        conditionFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyBrowseFilters());
        minPriceField.textProperty().addListener((obs, oldVal, newVal) -> applyBrowseFilters());
        maxPriceField.textProperty().addListener((obs, oldVal, newVal) -> applyBrowseFilters());
        sortOrderCombo.valueProperty().addListener((obs, oldVal, newVal) -> applyBrowseFilters());

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        contentPane.getChildren().add(scroll);

        // Initial filter application
        applyBrowseFilters();
    }

    private void resetBrowseFilters() {
        if (searchField != null) searchField.setText("");
        if (categoryFilter != null) categoryFilter.getSelectionModel().selectFirst();
        if (typeFilter != null) typeFilter.getSelectionModel().selectFirst();
        if (conditionFilter != null) conditionFilter.getSelectionModel().selectFirst();
        if (minPriceField != null) minPriceField.setText("");
        if (maxPriceField != null) maxPriceField.setText("");
        if (sortOrderCombo != null) sortOrderCombo.getSelectionModel().select(ListingSortOrder.NEWEST_FIRST);
        if (priceValidationLabel != null) {
            priceValidationLabel.setVisible(false);
            priceValidationLabel.setManaged(false);
        }
        applyBrowseFilters();
    }

    private void applyBrowseFilters() {
        if (browseContainer == null || listingGrid == null) {
            return;
        }

        // Build filter criteria
        ListingFilterCriteria.Builder builder = ListingFilterCriteria.builder();

        // 1. Keyword search (Subflow S1)
        if (searchField != null && !searchField.getText().trim().isEmpty()) {
            builder.keyword(searchField.getText().trim());
        }

        // 2. Category filter (Subflow S2)
        if (categoryFilter != null && categoryFilter.getValue() != null
                && !"All Categories".equalsIgnoreCase(categoryFilter.getValue())) {
            builder.category(Category.fromString(categoryFilter.getValue()));
        }

        // 3. Type filter (Subflow S2)
        if (typeFilter != null && typeFilter.getValue() != null
                && !"All Types".equalsIgnoreCase(typeFilter.getValue())) {
            builder.type(ListingType.fromString(typeFilter.getValue()));
        }

        // 4. Condition filter (Subflow S2)
        if (conditionFilter != null && conditionFilter.getValue() != null
                && !"All Conditions".equalsIgnoreCase(conditionFilter.getValue())) {
            builder.condition(Condition.fromString(conditionFilter.getValue()));
        }

        // 5. Price range filter (Subflow S2)
        Double minPrice = parseDoubleOrNull(minPriceField != null ? minPriceField.getText() : null);
        Double maxPrice = parseDoubleOrNull(maxPriceField != null ? maxPriceField.getText() : null);

        boolean priceRangeValid = true;
        if (minPrice != null && minPrice < 0) {
            priceRangeValid = false;
        }
        if (maxPrice != null && maxPrice < 0) {
            priceRangeValid = false;
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            priceRangeValid = false;
        }

        if (!priceRangeValid) {
            priceValidationLabel.setText("⚠️ Invalid price range: Min price cannot be negative or exceed Max price.");
            priceValidationLabel.setVisible(true);
            priceValidationLabel.setManaged(true);
        } else {
            priceValidationLabel.setVisible(false);
            priceValidationLabel.setManaged(false);
        }

        builder.minPrice(minPrice);
        builder.maxPrice(maxPrice);

        // 6. Sort order (Subflow S3)
        ListingSortOrder order = (sortOrderCombo != null && sortOrderCombo.getValue() != null)
                ? sortOrderCombo.getValue()
                : ListingSortOrder.NEWEST_FIRST;
        builder.sortOrder(order);

        ListingFilterCriteria criteria = builder.build();

        // Retrieve filtered live listings via service
        List<Listing> allLive = DummyDataService.getInstance().getLiveListings();
        List<Listing> filtered = DummyDataService.getInstance().getFilteredLiveListings(criteria);

        browseContainer.getChildren().clear();
        listingGrid.getChildren().clear();

        if (allLive.isEmpty()) {
            // Alternative Flow A2: No Live listings in the marketplace
            resultsCountLabel.setText("0 live listings");
            VBox emptyBox = createEmptyStateCard(
                    "📦",
                    "No listings available yet.",
                    "There are currently no active listings on the HKUST Marketplace. Please check back later!",
                    false
            );
            browseContainer.getChildren().add(emptyBox);
        } else if (filtered.isEmpty()) {
            // Alternative Flow A1: Active filters matched zero listings
            resultsCountLabel.setText("0 matches found");
            VBox emptyBox = createEmptyStateCard(
                    "🔍",
                    "No listings match your search.",
                    "No live listings match the specified filter and keyword criteria. Try clearing or relaxing your filters.",
                    true
            );
            browseContainer.getChildren().add(emptyBox);
        } else {
            // Basic Flow: Display matching listings
            resultsCountLabel.setText("Showing " + filtered.size() + " listing" + (filtered.size() == 1 ? "" : "s") + " (of " + allLive.size() + " live)");
            for (Listing listing : filtered) {
                listingGrid.getChildren().add(createListingCard(listing));
            }
            browseContainer.getChildren().add(listingGrid);
        }
    }

    private Double parseDoubleOrNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private VBox createEmptyStateCard(String icon, String title, String description, boolean showResetBtn) {
        VBox box = new VBox(12);
        box.getStyleClass().add("empty-state-box");
        box.setPrefWidth(800);
        box.setAlignment(Pos.CENTER);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 36px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #003974;");

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px; -fx-text-alignment: center;");
        descLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        box.getChildren().addAll(iconLabel, titleLabel, descLabel);

        if (showResetBtn) {
            Button reset = new Button("Clear All Filters");
            reset.getStyleClass().addAll("btn-primary", "btn-sm");
            reset.setOnAction(e -> resetBrowseFilters());
            box.getChildren().add(reset);
        }

        return box;
    }

    private VBox createListingCard(Listing listing) {
        VBox card = new VBox(8);
        card.getStyleClass().add("listing-card");
        card.setPrefWidth(270);
        card.setMinHeight(240);

        // Thumbnail photo (first photo or placeholder)
        StackPane thumbContainer = new StackPane();
        thumbContainer.getStyleClass().add("thumbnail-placeholder");
        thumbContainer.setPrefHeight(120);
        thumbContainer.setMinHeight(120);
        thumbContainer.setMaxHeight(120);

        boolean imageLoaded = false;
        if (listing.getFirstPhoto() != null && listing.getFirstPhoto().getImagePath() != null) {
            File file = listing.getFirstPhoto().getImageFile();
            if (file != null && file.exists()) {
                try {
                    ImageView iv = new ImageView(new Image(file.toURI().toString()));
                    iv.setFitWidth(240);
                    iv.setFitHeight(115);
                    iv.setPreserveRatio(true);
                    thumbContainer.getChildren().add(iv);
                    imageLoaded = true;
                } catch (Exception ignored) {
                }
            }
        }

        if (!imageLoaded) {
            VBox placeholderBox = new VBox(4);
            placeholderBox.setAlignment(Pos.CENTER);
            Label camIcon = new Label("📷");
            camIcon.setStyle("-fx-font-size: 24px; -fx-text-fill: #8a94a6;");
            Label noPhoto = new Label("No Photo");
            noPhoto.setStyle("-fx-font-size: 11px; -fx-text-fill: #8a94a6; -fx-font-weight: bold;");
            placeholderBox.getChildren().addAll(camIcon, noPhoto);
            thumbContainer.getChildren().add(placeholderBox);
        }

        // Status & Type Badges + Price Row
        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);

        Label availBadge = new Label(listing.getAvailabilityDisplay());
        availBadge.getStyleClass().addAll("badge", "badge-live");

        Label typeBadge = new Label(listing.getTypeDisplay());
        typeBadge.getStyleClass().add("badge");
        if (listing.getType() == ListingType.SELL) typeBadge.getStyleClass().add("badge-sold");
        else if (listing.getType() == ListingType.LEND) typeBadge.getStyleClass().add("badge-pending");
        else typeBadge.getStyleClass().add("badge-live");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label price = new Label(listing.getPriceDisplay());
        price.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 15px;");
        top.getChildren().addAll(availBadge, typeBadge, spacer, price);

        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1a1a2e;");
        title.setWrapText(true);

        Label meta = new Label(listing.getCategory() + " · " + listing.getCondition());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label seller = new Label("Seller: " + listing.getSellerFullName());
        seller.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label location = new Label("📍 " + listing.getMeetupLocation());
        location.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy");
        String postedDateStr = listing.getPostedAt() != null ? listing.getPostedAt().format(dtf) : "Recent";
        Label date = new Label("Posted: " + postedDateStr);
        date.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        Button actionBtn = new Button();
        actionBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        switch (listing.getType()) {
            case SELL -> {
                actionBtn.setText("Make Offer");
                actionBtn.setOnAction(e -> showOfferDialog(listing));
            }
            case LEND -> {
                actionBtn.setText("Request Borrow");
                actionBtn.setOnAction(e -> showBorrowDialog(listing));
            }
            case GIVEAWAY -> {
                actionBtn.setText("Claim Item");
                actionBtn.setOnAction(e -> claimItem(listing));
            }
        }

        card.getChildren().addAll(thumbContainer, top, title, meta, seller, location, date, actionBtn);
        return card;
    }

    private void showOfferDialog(Listing listing) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(listing.getPrice() != null ? listing.getPrice() : 0));
        dialog.setTitle("Make an Offer");
        dialog.setHeaderText("Offer for: " + listing.getTitle());
        dialog.setContentText("Your offer price (HK$):");
        dialog.showAndWait().ifPresent(priceStr -> {
            try {
                double price = Double.parseDouble(priceStr);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Offer Submitted");
                alert.setHeaderText(null);
                alert.setContentText("Your offer of HK$" + price + " has been sent to " + listing.getSellerFullName() + ".\n\n(Demo only – no backend)");
                alert.showAndWait();
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Invalid price.").showAndWait();
            }
        });
    }

    private void showBorrowDialog(Listing listing) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Borrow Request");
        alert.setHeaderText("Request to borrow: " + listing.getTitle());
        alert.setContentText("Proposed duration: " + (listing.getLendingDays() != null ? listing.getLendingDays() + " days" : "N/A") +
                "\n\nSend borrow request to seller?");
        alert.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.OK) {
                new Alert(Alert.AlertType.INFORMATION, "Borrow request sent!\n(Demo only)").showAndWait();
            }
        });
    }

    private void claimItem(Listing listing) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Claim Item");
        alert.setHeaderText("Claim: " + listing.getTitle());
        alert.setContentText("Are you sure you want to claim this giveaway item?");
        alert.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.OK) {
                new Alert(Alert.AlertType.INFORMATION, "Item claimed successfully!\n(Demo only)").showAndWait();
            }
        });
    }

    @FXML
    private void showMyDeals() {
        setActive(btnDeals, "My Deals");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        Label info = new Label("Active offers, borrow requests and claims");
        info.setStyle("-fx-text-fill: #5a5a7a;");
        root.getChildren().add(info);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<String[], String> colTitle = new TableColumn<>("Item");
        colTitle.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[0]));
        TableColumn<String[], String> colSeller = new TableColumn<>("Seller");
        colSeller.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[1]));
        TableColumn<String[], String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[2]));
        TableColumn<String[], String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[3]));
        TableColumn<String[], String> colAction = new TableColumn<>("Action");
        colAction.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[4]));

        table.getColumns().addAll(colTitle, colSeller, colType, colStatus, colAction);

        // Dummy rows
        table.setItems(FXCollections.observableArrayList(
                new String[]{"Calculus Textbook (Stewart)", "Charlie Lee", "Buy", "Accepted", "Chat / Complete"},
                new String[]{"Graphing Calculator TI-84", "Diana Ng", "Borrow", "Pending", "View"}
        ));

        root.getChildren().add(table);
        Label note = new Label("💡 Self-return for borrowed items and mark-complete are available here in the full version.");
        note.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        root.getChildren().add(note);

        contentPane.getChildren().add(root);
    }

    @FXML
    private void showHistory() {
        setActive(btnHistory, "Deal History");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(12);
        filters.setAlignment(Pos.CENTER_LEFT);
        DatePicker from = new DatePicker();
        from.setPromptText("From");
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        TextField search = new TextField();
        search.setPromptText("Search by title / seller...");
        filters.getChildren().addAll(new Label("Date range:"), from, new Label("–"), to, search);
        root.getChildren().add(filters);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<String[], String> c1 = new TableColumn<>("Item Title");
        c1.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[0]));
        TableColumn<String[], String> c2 = new TableColumn<>("Seller");
        c2.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[1]));
        TableColumn<String[], String> c3 = new TableColumn<>("Deal Date");
        c3.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        TableColumn<String[], String> c4 = new TableColumn<>("Type");
        c4.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[3]));
        TableColumn<String[], String> c5 = new TableColumn<>("Outcome");
        c5.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[4]));
        table.getColumns().addAll(c1, c2, c3, c4, c5);
        table.setItems(FXCollections.observableArrayList(
                new String[]{"Old Chemistry Book", "Charlie Lee", "2025-11-12", "Bought", "Completed"},
                new String[]{"USB Hub", "Diana Ng", "2025-10-03", "Received", "Completed"}
        ));
        root.getChildren().add(table);
        contentPane.getChildren().add(root);
    }

    @FXML
    private void showFavourites() {
        setActive(btnFavourites, "Favourites & Following");
        contentPane.getChildren().clear();
        VBox root = new VBox(16);
        root.setPadding(new Insets(4));
        root.setAlignment(Pos.CENTER);
        Label icon = new Label("♥");
        icon.setStyle("-fx-font-size: 48px; -fx-text-fill: #A68137;");
        Label title = new Label("Favourites, Bookmarks & Following");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        Label desc = new Label("Save listings you like and follow favourite sellers.\nThis section is a UI placeholder for students to implement.");
        desc.setStyle("-fx-text-fill: #5a5a7a; -fx-text-alignment: center;");
        desc.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        root.getChildren().addAll(icon, title, desc);
        contentPane.getChildren().add(root);
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notification Board");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Notification> notifs = DummyDataService.getInstance()
                .getNotificationsFor(user != null ? user.getUsername() : "");

        if (notifs.isEmpty()) {
            Label empty = new Label("No notifications yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            root.getChildren().add(empty);
        } else {
            for (Notification n : notifs) {
                VBox item = new VBox(4);
                item.getStyleClass().add("notif-item");
                Label title = new Label(n.getTitle());
                title.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
                Label msg = new Label(n.getMessage());
                msg.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
                msg.setWrapText(true);
                Label meta = new Label(n.getCategory() + " · " +
                        n.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                item.getChildren().addAll(title, msg, meta);
                root.getChildren().add(item);
            }
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "Manage Profile");
        contentPane.getChildren().clear();

        User user = SessionManager.getCurrentUser();
        VBox form = new VBox(14);
        form.setPadding(new Insets(8));
        form.setMaxWidth(420);
        form.getStyleClass().add("card");

        Label title = new Label("Edit your profile");
        title.getStyleClass().add("section-title");

        TextField nameField = new TextField(user != null ? user.getFullName() : "");
        nameField.setPromptText("Full Name");
        PasswordField newPass = new PasswordField();
        newPass.setPromptText("New password (leave blank to keep)");
        PasswordField confirmPass = new PasswordField();
        confirmPass.setPromptText("Confirm new password");
        ComboBox<String> residence = new ComboBox<>(FXCollections.observableArrayList(
                "Hall 1", "Hall 2", "Hall 3", "Hall 4", "Hall 5",
                "Hall 6", "Hall 7", "Hall 8", "Hall 9", "UG Hall 1", "UG Hall 2", "Off-campus"));
        if (user != null && user.getResidenceHall() != null) {
            residence.setValue(user.getResidenceHall());
        }

        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            new Alert(Alert.AlertType.INFORMATION, "Profile updated successfully!\n(Demo only – changes are in-memory)").showAndWait();
        });

        form.getChildren().addAll(title,
                new Label("Full Name"), nameField,
                new Label("New Password"), newPass,
                new Label("Confirm Password"), confirmPass,
                new Label("Residence"), residence,
                saveBtn);

        contentPane.getChildren().add(form);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
