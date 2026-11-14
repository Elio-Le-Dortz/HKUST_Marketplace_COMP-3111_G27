package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.OfferManagementService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for the Buyer Portal dashboard (Task 1: US-B01 to US-B11).
 *
 * <p>Coordinates marketplace browsing, item filtering and search, listing details, offer submission,
 * borrow requests, atomic giveaway claims, private chat messaging, deal management, seller reviews,
 * notifications board, transaction history, and buyer profile settings.</p>
 */
public class BuyerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnBrowse, btnDeals, btnHistory, btnNotifications, btnProfile;

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

    // Notification Board state (US-B09)
    private NotificationCategory currentNotifCategory = null;
    private Boolean currentNotifUnreadOnly = false;
    private String currentNotifSearch = "";

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            welcomeLabel.setText("Hello, " + user.getFullName());
        }
        currentActiveBtn = btnBrowse;
        showBrowse();
        updateNotificationSidebarBadge();
    }

    public void updateNotificationSidebarBadge() {
        if (btnNotifications == null) return;
        User user = SessionManager.getCurrentUser();
        String username = user != null ? user.getUsername() : "alice";
        int unread = DummyDataService.getInstance().getBuyerNotificationService().getUnreadCount(username);
        if (unread > 0) {
            btnNotifications.setText("  Notifications (" + unread + ")");
        } else {
            btnNotifications.setText("  Notifications");
        }
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

        card.setStyle("-fx-cursor: hand;");
        card.setOnMouseClicked(e -> showListingDetail(listing));

        Button actionBtn = new Button();
        actionBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        switch (listing.getType()) {
            case SELL -> {
                actionBtn.setText("Make Offer");
                actionBtn.setOnAction(e -> {
                    e.consume();
                    showOfferDialog(listing);
                });
            }
            case LEND -> {
                actionBtn.setText("Request Borrow");
                actionBtn.setOnAction(e -> {
                    e.consume();
                    showBorrowDialog(listing);
                });
            }
            case GIVEAWAY -> {
                actionBtn.setText("Claim Item");
                actionBtn.setOnAction(e -> {
                    e.consume();
                    claimItem(listing);
                });
            }
        }

        Button viewBtn = new Button("View Details");
        viewBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        viewBtn.setOnAction(e -> {
            e.consume();
            showListingDetail(listing);
        });

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        btnRow.getChildren().addAll(actionBtn, viewBtn);

        card.getChildren().addAll(thumbContainer, top, title, meta, seller, location, date, btnRow);
        return card;
    }

    /**
     * Opens and displays the listing detail screen (Use Case 1.4: View Listing Details) with default requireLive=true and returnView="BROWSE".
     *
     * @param initialListing the listing to display
     */
    public void showListingDetail(Listing initialListing) {
        showListingDetail(initialListing, true, "BROWSE");
    }

    /**
     * Opens and displays the listing detail screen with specified requireLive condition and default returnView="BROWSE".
     *
     * @param initialListing the listing to display
     * @param requireLive whether the listing must be LIVE
     */
    public void showListingDetail(Listing initialListing, boolean requireLive) {
        showListingDetail(initialListing, requireLive, "BROWSE");
    }

    /**
     * Opens and displays the listing detail screen (Use Case 1.4: View Listing Details).
     * Enforces view count increment, photo gallery, full metadata, seller rating, and action buttons.
     *
     * @param initialListing the listing to display
     * @param requireLive whether the listing must be LIVE (true from Browse, false from Deals/History/Notifications)
     * @param returnView origin view name ("BROWSE", "DEALS", "HISTORY", "NOTIFICATIONS")
     */
    public void showListingDetail(Listing initialListing, boolean requireLive, String returnView) {
        if (initialListing == null) return;

        // Use service to increment view count and fetch full details & seller rating
        ListingViewResult result = DummyDataService.getInstance().viewListing(initialListing.getId(), requireLive);

        // Alternative Flow A1: Listing No Longer Live
        if (!result.isSuccess()) {
            if (result.getStatus() == ListingViewStatus.NOT_LIVE) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Listing Unavailable");
                alert.setHeaderText(null);
                alert.setContentText("This listing is no longer available.");
                alert.showAndWait();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText(result.getMessage());
                alert.showAndWait();
            }
            return;
        }

        Listing listing = result.getListing();
        SellerRatingInfo ratingInfo = result.getSellerRating();

        pageTitle.setText("Listing Details");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(10));

        // Top Navigation Bar
        HBox navBar = new HBox(12);
        navBar.setAlignment(Pos.CENTER_LEFT);

        String backText = "← Back to Browse";
        Runnable backAction = this::showBrowse;
        if ("DEALS".equalsIgnoreCase(returnView)) {
            backText = "← Back to My Deals";
            backAction = this::showMyDeals;
        } else if ("HISTORY".equalsIgnoreCase(returnView)) {
            backText = "← Back to Deal History";
            backAction = this::showHistory;
        } else if ("NOTIFICATIONS".equalsIgnoreCase(returnView)) {
            backText = "← Back to Notifications";
            backAction = this::showNotifications;
        }

        Button btnBack = new Button(backText);
        btnBack.getStyleClass().addAll("btn-outline", "btn-sm");
        final Runnable finalBackAction = backAction;
        btnBack.setOnAction(e -> finalBackAction.run());

        Label availBadge = new Label(listing.getAvailabilityDisplay());
        availBadge.getStyleClass().add("badge");
        if (listing.getStatus() == ListingStatus.LIVE) {
            availBadge.getStyleClass().add("badge-live");
        } else if (listing.getStatus() == ListingStatus.RESERVED || listing.getStatus() == ListingStatus.LENT) {
            availBadge.getStyleClass().add("badge-pending");
        } else if (listing.getStatus() == ListingStatus.SOLD || listing.getStatus() == ListingStatus.GIVEN) {
            availBadge.getStyleClass().add("badge-sold");
        } else {
            availBadge.getStyleClass().add("badge-danger");
        }

        Label typeBadge = new Label(listing.getTypeDisplay());
        typeBadge.getStyleClass().add("badge");
        if (listing.getType() == ListingType.SELL) typeBadge.getStyleClass().add("badge-sold");
        else if (listing.getType() == ListingType.LEND) typeBadge.getStyleClass().add("badge-pending");
        else typeBadge.getStyleClass().add("badge-live");

        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);

        Label viewCountLabel = new Label("👁 " + listing.getViewCount() + " view" + (listing.getViewCount() == 1 ? "" : "s"));
        viewCountLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-weight: bold; -fx-font-size: 13px;");

        navBar.getChildren().addAll(btnBack, availBadge, typeBadge, navSpacer, viewCountLabel);
        root.getChildren().add(navBar);

        // Main 2-column layout
        HBox mainLayout = new HBox(24);
        mainLayout.setAlignment(Pos.TOP_LEFT);

        // Left Column: Photo Gallery (all photos up to 6)
        VBox galleryCol = new VBox(12);
        galleryCol.setPrefWidth(460);
        galleryCol.setMinWidth(460);
        galleryCol.setMaxWidth(460);

        StackPane mainPhotoFrame = new StackPane();
        mainPhotoFrame.getStyleClass().add("photo-main-container");
        mainPhotoFrame.setPrefHeight(320);
        mainPhotoFrame.setMinHeight(320);
        mainPhotoFrame.setMaxHeight(320);

        ImageView mainImageView = new ImageView();
        mainImageView.setFitWidth(430);
        mainImageView.setFitHeight(300);
        mainImageView.setPreserveRatio(true);

        VBox noPhotoPlaceholder = new VBox(8);
        noPhotoPlaceholder.setAlignment(Pos.CENTER);
        Label camIcon = new Label("📷");
        camIcon.setStyle("-fx-font-size: 40px; -fx-text-fill: #8a94a6;");
        Label noPhotoLabel = new Label("No photos uploaded for this listing");
        noPhotoLabel.setStyle("-fx-text-fill: #8a94a6; -fx-font-size: 13px; -fx-font-weight: bold;");
        noPhotoPlaceholder.getChildren().addAll(camIcon, noPhotoLabel);

        List<Photo> photos = listing.getPhotos();
        if (photos != null && !photos.isEmpty()) {
            Photo firstPhoto = photos.get(0);
            loadImageIntoView(firstPhoto, mainImageView, noPhotoPlaceholder, mainPhotoFrame);
        } else {
            mainPhotoFrame.getChildren().add(noPhotoPlaceholder);
        }

        galleryCol.getChildren().add(mainPhotoFrame);

        // Thumbnail strip for up to 6 photos
        if (photos != null && photos.size() > 1) {
            HBox thumbStrip = new HBox(10);
            thumbStrip.setAlignment(Pos.CENTER_LEFT);

            for (int i = 0; i < Math.min(photos.size(), 6); i++) {
                Photo p = photos.get(i);
                StackPane thumbBox = new StackPane();
                thumbBox.getStyleClass().add("photo-thumb");
                thumbBox.setPrefSize(68, 68);
                thumbBox.setMinSize(68, 68);
                thumbBox.setMaxSize(68, 68);

                ImageView thumbView = new ImageView();
                thumbView.setFitWidth(60);
                thumbView.setFitHeight(60);
                thumbView.setPreserveRatio(true);

                File f = p.getImageFile();
                if (f != null && f.exists()) {
                    try {
                        thumbView.setImage(new Image(f.toURI().toString()));
                        thumbBox.getChildren().add(thumbView);
                    } catch (Exception ignored) {
                    }
                } else {
                    Label smallCam = new Label("📷");
                    smallCam.setStyle("-fx-font-size: 18px; -fx-text-fill: #8a94a6;");
                    thumbBox.getChildren().add(smallCam);
                }

                if (i == 0) {
                    thumbBox.getStyleClass().add("photo-thumb-active");
                }

                final int thumbIdx = i;
                thumbBox.setOnMouseClicked(ev -> {
                    for (javafx.scene.Node n : thumbStrip.getChildren()) {
                        n.getStyleClass().remove("photo-thumb-active");
                    }
                    thumbBox.getStyleClass().add("photo-thumb-active");
                    loadImageIntoView(photos.get(thumbIdx), mainImageView, noPhotoPlaceholder, mainPhotoFrame);
                });

                thumbStrip.getChildren().add(thumbBox);
            }
            galleryCol.getChildren().add(thumbStrip);
        }

        // Right Column: Details & Actions Card
        VBox detailsCard = new VBox(14);
        detailsCard.getStyleClass().add("detail-card");
        HBox.setHgrow(detailsCard, Priority.ALWAYS);

        Label titleLabel = new Label(listing.getTitle());
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        titleLabel.setWrapText(true);

        Label priceBanner = new Label(listing.getPriceDisplay());
        priceBanner.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #A68137;");

        // Details Grid (Category, Condition, Meetup, Date)
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);

        Label lblCat = new Label("Category:");
        lblCat.setStyle("-fx-font-weight: bold; -fx-text-fill: #5a5a7a;");
        Label valCat = new Label(listing.getCategory() != null ? listing.getCategory().getDisplayName() : "N/A");

        Label lblCond = new Label("Condition:");
        lblCond.setStyle("-fx-font-weight: bold; -fx-text-fill: #5a5a7a;");
        Label valCond = new Label(listing.getCondition() != null ? listing.getCondition().getDisplayName() : "N/A");

        Label lblMeet = new Label("Meet-up:");
        lblMeet.setStyle("-fx-font-weight: bold; -fx-text-fill: #5a5a7a;");
        Label valMeet = new Label("📍 " + (listing.getMeetupLocation() != null ? listing.getMeetupLocation().getDisplayName() : "HKUST Campus"));

        Label lblDate = new Label("Posted Date:");
        lblDate.setStyle("-fx-font-weight: bold; -fx-text-fill: #5a5a7a;");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        String formattedDate = listing.getPostedAt() != null ? listing.getPostedAt().format(dtf) : "Recent";
        Label valDate = new Label("📅 " + formattedDate);

        grid.add(lblCat, 0, 0); grid.add(valCat, 1, 0);
        grid.add(lblCond, 2, 0); grid.add(valCond, 3, 0);
        grid.add(lblMeet, 0, 1); grid.add(valMeet, 1, 1);
        grid.add(lblDate, 2, 1); grid.add(valDate, 3, 1);

        // Description Section
        VBox descBox = new VBox(6);
        Label descHeader = new Label("Description");
        descHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1a1a2e;");
        Label descContent = new Label(listing.getDescription() != null && !listing.getDescription().isEmpty()
                ? listing.getDescription() : "No detailed description provided.");
        descContent.setStyle("-fx-font-size: 14px; -fx-text-fill: #333344; -fx-line-spacing: 4px;");
        descContent.setWrapText(true);
        descBox.getChildren().addAll(descHeader, descContent);

        Separator sep1 = new Separator();

        // Seller Information Box (Basic Flow Step 3 & Alternative Flow A2)
        VBox sellerBox = new VBox(6);
        sellerBox.getStyleClass().add("seller-info-box");

        Label sellerHeader = new Label("👤 Seller Information");
        sellerHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #003974;");

        HBox sellerRow = new HBox(12);
        sellerRow.setAlignment(Pos.CENTER_LEFT);

        Label sellerName = new Label(listing.getSellerFullName() != null ? listing.getSellerFullName() : listing.getSellerUsername());
        sellerName.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1a1a2e;");

        Label sellerHandle = new Label("@" + listing.getSellerUsername());
        sellerHandle.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label ratingLabel = new Label(ratingInfo != null ? ratingInfo.getDisplayText() : SellerRatingInfo.NO_RATINGS_TEXT);
        if (ratingInfo != null && ratingInfo.hasRatings()) {
            ratingLabel.setStyle("-fx-text-fill: #A68137; -fx-font-weight: bold; -fx-font-size: 13px;");
        } else {
            // Alternative Flow A2: No ratings yet
            ratingLabel.setStyle("-fx-text-fill: #8a94a6; -fx-font-style: italic; -fx-font-size: 13px;");
        }

        Button btnViewReviews = new Button("💬 Reviews");
        btnViewReviews.getStyleClass().addAll("btn-outline", "btn-sm");
        btnViewReviews.setOnAction(e -> showSellerReviewsDialog(listing.getSellerUsername(), listing.getSellerFullName()));

        sellerRow.getChildren().addAll(sellerName, sellerHandle, new Label("•"), ratingLabel, btnViewReviews);
        sellerBox.getChildren().addAll(sellerHeader, sellerRow);

        Separator sep2 = new Separator();

        // Action Buttons Row (Basic Flow Step 4)
        HBox actionsRow = new HBox(12);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button primaryActionBtn = new Button();
        primaryActionBtn.getStyleClass().addAll("btn-primary");
        switch (listing.getType()) {
            case SELL -> {
                primaryActionBtn.setText("Make Offer");
                primaryActionBtn.setOnAction(e -> showOfferDialog(listing));
            }
            case LEND -> {
                primaryActionBtn.setText("Borrow Request");
                primaryActionBtn.setOnAction(e -> showBorrowDialog(listing));
            }
            case GIVEAWAY -> {
                primaryActionBtn.setText("Claim Item");
                primaryActionBtn.setOnAction(e -> claimItem(listing));
            }
        }

        if (listing.getStatus() != ListingStatus.LIVE) {
            primaryActionBtn.setDisable(true);
        }

        Button chatBtn = new Button("💬 Chat");
        chatBtn.getStyleClass().addAll("btn-outline");
        chatBtn.setOnAction(e -> openChatForListing(listing, "LISTING:" + (returnView != null ? returnView : "BROWSE")));

        Button reportBtn = new Button("🚩 Report Listing");
        reportBtn.getStyleClass().addAll("btn-danger");
        reportBtn.setOnAction(e -> showReportListingDialog(listing));

        actionsRow.getChildren().addAll(primaryActionBtn, chatBtn, reportBtn);

        detailsCard.getChildren().addAll(titleLabel, priceBanner, grid, descBox, sep1, sellerBox, sep2, actionsRow);

        mainLayout.getChildren().addAll(galleryCol, detailsCard);
        root.getChildren().add(mainLayout);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        contentPane.getChildren().add(scroll);
    }

    private void loadImageIntoView(Photo photo, ImageView imageView, VBox placeholder, StackPane container) {
        container.getChildren().clear();
        boolean loaded = false;
        if (photo != null && photo.getImagePath() != null) {
            File f = photo.getImageFile();
            if (f != null && f.exists()) {
                try {
                    imageView.setImage(new Image(f.toURI().toString()));
                    container.getChildren().add(imageView);
                    loaded = true;
                } catch (Exception ignored) {
                }
            }
        }
        if (!loaded) {
            container.getChildren().add(placeholder);
        }
    }

    /**
     * Displays a dialog asking for a written reason to report a listing (Alternative Flow A3).
     *
     * @param listing the listing being reported
     */
    private void showReportListingDialog(Listing listing) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Report Listing");
        dialog.setHeaderText("Report: " + listing.getTitle());

        ButtonType submitButtonType = new ButtonType("Submit Report", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(420);
        content.setPadding(new Insets(10));

        Label instruction = new Label("Please explain why you are reporting this listing (e.g., inappropriate content, counterfeit item, duplicate, prohibited goods):");
        instruction.setWrapText(true);
        instruction.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Enter your reason here...");
        reasonArea.setPrefRowCount(4);
        reasonArea.setWrapText(true);

        Label errorLabel = new Label("Please enter a reason for reporting this listing.");
        errorLabel.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12px;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        content.getChildren().addAll(instruction, reasonArea, errorLabel);
        dialog.getDialogPane().setContent(content);

        // Form validation on submit click (Alternative Flow A3)
        final Button submitBtn = (Button) dialog.getDialogPane().lookupButton(submitButtonType);
        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String text = reasonArea.getText();
            if (text == null || text.trim().isEmpty()) {
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                event.consume(); // keep dialog open
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == submitButtonType) {
                return reasonArea.getText();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(reason -> {
            User currentUser = SessionManager.getCurrentUser();
            String buyerUsername = currentUser != null ? currentUser.getUsername() : "buyer";

            ReportResult reportResult = DummyDataService.getInstance().reportListing(listing.getId(), buyerUsername, reason);
            if (reportResult.isSuccess()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Report Submitted");
                alert.setHeaderText(null);
                alert.setContentText("Your report has been submitted to the marketplace moderators. Thank you for helping keep our community safe.");
                alert.showAndWait();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Report Failed");
                alert.setHeaderText(null);
                alert.setContentText(reportResult.getErrorMessage());
                alert.showAndWait();
            }
        });
    }

    /**
     * Displays a dialog for submitting a price offer on a SELL listing (Use Case 1.5 Subflow S1).
     * Enforces listing availability (Alt Flow A1), positive price validation (Alt Flow A2),
     * and cancellation without persistence (Alt Flow A5).
     *
     * @param listing the sell listing
     */
    private void showOfferDialog(Listing listing) {
        if (listing == null) return;

        // Alternative Flow A1: Listing No Longer Live
        if (listing.getStatus() != ListingStatus.LIVE) {
            Alert notLiveAlert = new Alert(Alert.AlertType.WARNING);
            notLiveAlert.setTitle("Listing Unavailable");
            notLiveAlert.setHeaderText(null);
            notLiveAlert.setContentText("This listing is no longer available and an offer cannot be made.");
            notLiveAlert.showAndWait();
            return;
        }

        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle("Make an Offer");
        dialog.setHeaderText("Make an Offer for: " + listing.getTitle());

        ButtonType submitButtonType = new ButtonType("Submit Offer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(380);
        content.setPadding(new Insets(10));

        Label listedPriceLabel = new Label("Listed Price: HK$" + (listing.getPrice() != null ? String.format(Locale.ENGLISH, "%.2f", listing.getPrice()) : "N/A"));
        listedPriceLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 13px;");

        Label instruction = new Label("Enter your proposed offer price in HKD:");
        instruction.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");

        TextField priceField = new TextField(listing.getPrice() != null ? String.valueOf(listing.getPrice()) : "");
        priceField.setPromptText("e.g. 150.00");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12px;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        content.getChildren().addAll(listedPriceLabel, instruction, priceField, errorLabel);
        dialog.getDialogPane().setContent(content);

        // Alternative Flow A2: Price Validation
        Button submitBtn = (Button) dialog.getDialogPane().lookupButton(submitButtonType);
        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String text = priceField.getText();
            try {
                if (text == null || text.trim().isEmpty()) {
                    throw new NumberFormatException("Empty");
                }
                double price = Double.parseDouble(text.trim());
                String validationError = DummyDataService.getInstance().getActOnListingService().validatePrice(price);
                if (validationError != null) {
                    errorLabel.setText(validationError);
                    errorLabel.setVisible(true);
                    errorLabel.setManaged(true);
                    event.consume(); // Keeps dialog open (resuming at Enter Offer Price)
                }
            } catch (NumberFormatException ex) {
                errorLabel.setText("Price must be a valid positive number.");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == submitButtonType) {
                return Double.parseDouble(priceField.getText().trim());
            }
            return null;
        });

        // Subflow S1 Step 2, 4, 5
        dialog.showAndWait().ifPresent(price -> {
            User currentUser = SessionManager.getCurrentUser();
            String buyerUsername = currentUser != null ? currentUser.getUsername() : "buyer";

            ActOnListingResult result = DummyDataService.getInstance().submitPriceOffer(listing.getId(), buyerUsername, price);
            if (result.isSuccess()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Offer Submitted");
                alert.setHeaderText(null);
                alert.setContentText(result.getMessage());
                alert.showAndWait();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Offer Failed");
                alert.setHeaderText(null);
                alert.setContentText(result.getMessage());
                alert.showAndWait();
            }
        });
    }

    /**
     * Displays a dialog for submitting a borrow request on a LEND listing (Use Case 1.5 Subflow S2).
     * Enforces listing availability (Alt Flow A1), borrow dates validation (Alt Flow A3: >= 5s),
     * and cancellation without persistence (Alt Flow A5).
     *
     * @param listing the lend listing
     */
    private void showBorrowDialog(Listing listing) {
        if (listing == null) return;

        // Alternative Flow A1: Listing No Longer Live
        if (listing.getStatus() != ListingStatus.LIVE) {
            Alert notLiveAlert = new Alert(Alert.AlertType.WARNING);
            notLiveAlert.setTitle("Listing Unavailable");
            notLiveAlert.setHeaderText(null);
            notLiveAlert.setContentText("This listing is no longer available and a borrow request cannot be made.");
            notLiveAlert.showAndWait();
            return;
        }

        Dialog<LocalDateTime[]> dialog = new Dialog<>();
        dialog.setTitle("Borrow Request");
        dialog.setHeaderText("Request to Borrow: " + listing.getTitle());

        ButtonType submitButtonType = new ButtonType("Submit Request", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(420);
        content.setPadding(new Insets(10));

        String periodDesc = "Flexible";
        if (listing.getLendingDuration() != null) {
            periodDesc = OfferManagementService.formatDuration(listing.getLendingDuration());
        } else if (listing.getLendingDays() != null) {
            periodDesc = listing.getLendingDays() + " days";
        }
        Label infoLabel = new Label("Seller suggested lending period: " + periodDesc);
        infoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 13px;");

        // Start Date & Time
        Label startLabel = new Label("Proposed Start Date & Time:");
        startLabel.setStyle("-fx-font-weight: 600; -fx-text-fill: #333344;");
        LocalDateTime initialNow = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        DatePicker startDatePicker = new DatePicker(initialNow.toLocalDate());
        TextField startTimeField = new TextField(initialNow.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        startTimeField.setPromptText("HH:mm:ss");
        startTimeField.setPrefWidth(100);
        HBox startBox = new HBox(8, startDatePicker, startTimeField);
        startBox.setAlignment(Pos.CENTER_LEFT);

        // End Date & Time
        Label endLabel = new Label("Proposed End Date & Time:");
        endLabel.setStyle("-fx-font-weight: 600; -fx-text-fill: #333344;");
        LocalDateTime defaultEnd;
        if (listing.getLendingDuration() != null && !listing.getLendingDuration().isZero() && !listing.getLendingDuration().isNegative()) {
            defaultEnd = initialNow.plus(listing.getLendingDuration());
        } else if (listing.getLendingDays() != null && listing.getLendingDays() > 0) {
            defaultEnd = initialNow.plusDays(listing.getLendingDays());
        } else {
            defaultEnd = initialNow.plusDays(1);
        }
        DatePicker endDatePicker = new DatePicker(defaultEnd.toLocalDate());
        TextField endTimeField = new TextField(defaultEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        endTimeField.setPromptText("HH:mm:ss");
        endTimeField.setPrefWidth(100);
        HBox endBox = new HBox(8, endDatePicker, endTimeField);
        endBox.setAlignment(Pos.CENTER_LEFT);

        // Preset Quick Buttons (enables instant 5-second test verification and realistic presets)
        Label presetLabel = new Label("Quick Presets:");
        presetLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #5a5a7a;");
        HBox presetsBox = new HBox(6);
        presetsBox.setAlignment(Pos.CENTER_LEFT);

        Button btn5s = new Button("5s (Demo)");
        btn5s.getStyleClass().add("btn-sm");
        btn5s.setOnAction(e -> {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            LocalDateTime plus10 = now.plusSeconds(10);
            startDatePicker.setValue(now.toLocalDate());
            startTimeField.setText(now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            endDatePicker.setValue(plus10.toLocalDate());
            endTimeField.setText(plus10.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        });

        Button btn1d = new Button("1 Day");
        btn1d.getStyleClass().add("btn-sm");
        btn1d.setOnAction(e -> {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            LocalDateTime plus1 = now.plusDays(1);
            startDatePicker.setValue(now.toLocalDate());
            startTimeField.setText(now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            endDatePicker.setValue(plus1.toLocalDate());
            endTimeField.setText(plus1.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        });

        Button btn7d = new Button("7 Days");
        btn7d.getStyleClass().add("btn-sm");
        btn7d.setOnAction(e -> {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            LocalDateTime plus7 = now.plusDays(7);
            startDatePicker.setValue(now.toLocalDate());
            startTimeField.setText(now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            endDatePicker.setValue(plus7.toLocalDate());
            endTimeField.setText(plus7.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        });

        presetsBox.getChildren().addAll(btn5s, btn1d, btn7d);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12px;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        content.getChildren().addAll(infoLabel, startLabel, startBox, endLabel, endBox, presetLabel, presetsBox, errorLabel);
        dialog.getDialogPane().setContent(content);

        // Alternative Flow A3: Validate Borrow Dates
        Button submitBtn = (Button) dialog.getDialogPane().lookupButton(submitButtonType);
        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                LocalDate sDate = startDatePicker.getValue();
                LocalDate eDate = endDatePicker.getValue();
                if (sDate == null || eDate == null) {
                    errorLabel.setText("Start date and end date are required.");
                    errorLabel.setVisible(true);
                    errorLabel.setManaged(true);
                    event.consume();
                    return;
                }

                LocalTime sTime = LocalTime.parse(startTimeField.getText().trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
                LocalTime eTime = LocalTime.parse(endTimeField.getText().trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));

                LocalDateTime start = LocalDateTime.of(sDate, sTime);
                LocalDateTime end = LocalDateTime.of(eDate, eTime);

                String dateError = DummyDataService.getInstance().getActOnListingService()
                        .validateBorrowDates(start, end, LocalDateTime.now());
                if (dateError != null) {
                    errorLabel.setText(dateError);
                    errorLabel.setVisible(true);
                    errorLabel.setManaged(true);
                    event.consume(); // Keeps dialog open (resuming at Enter Borrow Dates)
                }
            } catch (Exception ex) {
                errorLabel.setText("Please enter valid times in HH:mm:ss format (e.g. 14:30:00).");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == submitButtonType) {
                LocalDate sDate = startDatePicker.getValue();
                LocalDate eDate = endDatePicker.getValue();
                LocalTime sTime = LocalTime.parse(startTimeField.getText().trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
                LocalTime eTime = LocalTime.parse(endTimeField.getText().trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
                return new LocalDateTime[]{LocalDateTime.of(sDate, sTime), LocalDateTime.of(eDate, eTime)};
            }
            return null;
        });

        // Subflow S2 Step 2, 4, 5
        dialog.showAndWait().ifPresent(dates -> {
            User currentUser = SessionManager.getCurrentUser();
            String buyerUsername = currentUser != null ? currentUser.getUsername() : "buyer";

            ActOnListingResult result = DummyDataService.getInstance()
                    .submitBorrowRequest(listing.getId(), buyerUsername, dates[0], dates[1]);
            if (result.isSuccess()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Borrow Request Submitted");
                alert.setHeaderText(null);
                alert.setContentText(result.getMessage());
                alert.showAndWait();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Borrow Request Failed");
                alert.setHeaderText(null);
                alert.setContentText(result.getMessage());
                alert.showAndWait();
            }
        });
    }

    /**
     * Confirms and atomically claims a GIVEAWAY listing (Use Case 1.5 Subflow S3).
     * Enforces listing availability (Alt Flow A1), atomic concurrency checks (Alt Flow A4: already claimed),
     * and single-action confirmation / cancellation (Alt Flow A5).
     *
     * @param listing the giveaway listing
     */
    private void claimItem(Listing listing) {
        if (listing == null) return;

        // Alternative Flow A1: Listing No Longer Live
        if (listing.getStatus() != ListingStatus.LIVE) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Listing Unavailable");
            alert.setHeaderText(null);
            alert.setContentText("This listing is no longer available and cannot be claimed.");
            alert.showAndWait();
            return;
        }

        // Alternative Flow A4: Quick check if already claimed
        if (DummyDataService.getInstance().isGiveawayClaimed(listing.getId())) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Item Already Claimed");
            alert.setHeaderText(null);
            alert.setContentText("This giveaway item has already been claimed.");
            alert.showAndWait();
            return;
        }

        // Subflow S3 Step 1: The Buyer confirms the claim with a single action
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Claim Giveaway Item");
        confirmDialog.setHeaderText("Confirm Claim: " + listing.getTitle());
        confirmDialog.setContentText("Are you sure you want to claim this giveaway item?\n\n"
                + "Giveaway claims are processed on a first-come, first-served basis.");

        confirmDialog.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.OK) {
                User currentUser = SessionManager.getCurrentUser();
                String buyerUsername = currentUser != null ? currentUser.getUsername() : "buyer";

                // Subflow S3 Step 2-4: Atomic claim
                ActOnListingResult result = DummyDataService.getInstance().submitClaim(listing.getId(), buyerUsername);
                if (result.isSuccess()) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Claim Submitted");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText(result.getMessage());
                    successAlert.showAndWait();
                } else if (result.getStatus() == ActOnListingStatus.ALREADY_CLAIMED) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Item Already Claimed");
                    alert.setHeaderText(null);
                    alert.setContentText("Sorry, this giveaway item has already been claimed by another buyer.");
                    alert.showAndWait();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Claim Failed");
                    alert.setHeaderText(null);
                    alert.setContentText(result.getMessage());
                    alert.showAndWait();
                }
            }
            // If CANCEL: Alternative Flow A5 (Cancel Action) - nothing is stored.
        });
    }

    @FXML
    private void showMyDeals() {
        setActive(btnDeals, "My Deals");
        contentPane.getChildren().clear();

        User currentUser = SessionManager.getCurrentUser();
        String buyerUsername = currentUser != null ? currentUser.getUsername() : "alice";

        // Auto-return check on entry (Use Case 1.6 Alternative Flow A4)
        DummyDataService.getInstance().processAutoReturns();

        VBox root = new VBox(16);
        root.setPadding(new Insets(10));

        // Header and description
        VBox headerBox = new VBox(4);
        Label subtitle = new Label("Track and manage your active offers, counter-offers, borrowed items, and completed transactions.");
        subtitle.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
        Label autoReturnTip = new Label("💡 Borrowed loans automatically return to 'Live' when their duration expires (min 5s). You may also self-return early.");
        autoReturnTip.setStyle("-fx-text-fill: #003974; -fx-font-size: 12px; -fx-font-style: italic;");
        headerBox.getChildren().addAll(subtitle, autoReturnTip);
        root.getChildren().add(headerBox);

        // Filter / Controls bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("All Items", "Active Deals", "Pending & Countered Offers", "Completed & Returned");
        filterCombo.setValue("All Items");
        filterCombo.getStyleClass().add("combo-box");

        TextField filterInput = new TextField();
        filterInput.setPromptText("🔍 Search by title or seller...");
        filterInput.setPrefWidth(240);
        filterInput.getStyleClass().add("text-field");

        Button btnRefresh = new Button("🔄 Refresh");
        btnRefresh.getStyleClass().addAll("btn-outline", "btn-sm");

        filterBar.getChildren().addAll(new Label("Filter:"), filterCombo, filterInput, btnRefresh);
        root.getChildren().add(filterBar);

        // Table
        TableView<MyDealsItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<MyDealsItem, String> colTitle = new TableColumn<>("Item");
        colTitle.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getListingTitle()));

        TableColumn<MyDealsItem, String> colSeller = new TableColumn<>("Seller");
        colSeller.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty("@" + c.getValue().getSellerUsername()));

        TableColumn<MyDealsItem, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTypeDisplay()));

        TableColumn<MyDealsItem, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getStatusDisplay()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(status);
                    badge.getStyleClass().add("badge");
                    if ("Pending".equalsIgnoreCase(status)) badge.getStyleClass().add("badge-pending");
                    else if ("Countered".equalsIgnoreCase(status)) badge.getStyleClass().add("badge-countered");
                    else if ("Active".equalsIgnoreCase(status)) badge.getStyleClass().add("badge-active");
                    else if ("Completed".equalsIgnoreCase(status)) badge.getStyleClass().add("badge-completed");
                    else if ("Returned".equalsIgnoreCase(status)) badge.getStyleClass().add("badge-returned");
                    else if ("Withdrawn".equalsIgnoreCase(status)) badge.getStyleClass().add("badge-withdrawn");
                    else badge.getStyleClass().add("badge-live");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<MyDealsItem, String> colDetails = new TableColumn<>("Due Date / Details");
        colDetails.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDetailsSummary()));

        table.getColumns().addAll(colTitle, colSeller, colType, colStatus, colDetails);

        // Placeholder for empty table (Alternative Flow A1: No Deals)
        VBox emptyPlaceholder = new VBox(10);
        emptyPlaceholder.setAlignment(Pos.CENTER);
        emptyPlaceholder.setPadding(new Insets(24));
        Label emptyTitle = new Label("📦 No Deals or Offers Yet");
        emptyTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        Label emptyDesc = new Label("Explore the marketplace to make offers, request borrowed items, or claim giveaways.");
        emptyDesc.setStyle("-fx-text-fill: #5a5a7a;");
        Button btnBrowseMarketplace = new Button("Browse Marketplace");
        btnBrowseMarketplace.getStyleClass().addAll("btn-primary", "btn-sm");
        btnBrowseMarketplace.setOnAction(e -> showBrowse());
        emptyPlaceholder.getChildren().addAll(emptyTitle, emptyDesc, btnBrowseMarketplace);
        table.setPlaceholder(emptyPlaceholder);

        // Double-click row handler: open listing details
        table.setRowFactory(tv -> {
            TableRow<MyDealsItem> row = new TableRow<>();
            row.setStyle("-fx-cursor: hand;");
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() >= 2 && !row.isEmpty()) {
                    MyDealsItem item = row.getItem();
                    Listing l = DummyDataService.getInstance().findListing(item.getListingId());
                    if (l != null) {
                        showListingDetail(l, false, "DEALS");
                    }
                }
            });
            return row;
        });

        root.getChildren().add(table);

        // Action Buttons Bar
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        Button btnWithdraw = new Button("Withdraw");
        btnWithdraw.getStyleClass().addAll("btn-danger", "btn-sm");
        btnWithdraw.setDisable(true);

        Button btnAcceptCounter = new Button("Accept Counter");
        btnAcceptCounter.getStyleClass().addAll("btn-success", "btn-sm");
        btnAcceptCounter.setDisable(true);

        Button btnDeclineCounter = new Button("Decline Counter");
        btnDeclineCounter.getStyleClass().addAll("btn-outline", "btn-sm");
        btnDeclineCounter.setDisable(true);

        Button btnSelfReturn = new Button("Return Item");
        btnSelfReturn.getStyleClass().addAll("btn-primary", "btn-sm");
        btnSelfReturn.setDisable(true);

        Button btnMarkComplete = new Button("Mark Complete");
        btnMarkComplete.getStyleClass().addAll("btn-gold", "btn-sm");
        btnMarkComplete.setDisable(true);

        Button btnRate = new Button("⭐ Rate");
        btnRate.getStyleClass().addAll("btn-primary", "btn-sm");
        btnRate.setDisable(true);

        Button btnOpenChat = new Button("💬 Chat");
        btnOpenChat.getStyleClass().addAll("btn-outline", "btn-sm");
        btnOpenChat.setDisable(true);

        Button btnViewListing = new Button("View Listing");
        btnViewListing.getStyleClass().addAll("btn-outline", "btn-sm");
        btnViewListing.setDisable(true);

        actionBox.getChildren().addAll(
                btnWithdraw, btnAcceptCounter, btnDeclineCounter,
                btnSelfReturn, btnMarkComplete, btnRate,
                btnOpenChat, btnViewListing
        );
        root.getChildren().add(actionBox);

        // Selection listener to update button states
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSel = newVal != null;
            btnWithdraw.setDisable(!hasSel || !newVal.canWithdraw());
            btnAcceptCounter.setDisable(!hasSel || !newVal.canAcceptCounter());
            btnDeclineCounter.setDisable(!hasSel || !newVal.canDeclineCounter());
            btnSelfReturn.setDisable(!hasSel || !newVal.canSelfReturn());
            btnMarkComplete.setDisable(!hasSel || !newVal.canMarkComplete());
            if (hasSel && newVal.isRated()) {
                btnRate.setText("⭐ View Review");
                btnRate.setDisable(false);
            } else {
                btnRate.setText("⭐ Rate");
                btnRate.setDisable(!hasSel || !newVal.canRate());
            }
            btnOpenChat.setDisable(!hasSel || !newVal.canOpenChat());
            btnViewListing.setDisable(!hasSel || !newVal.canOpenListing());
        });

        // Function to refresh items
        Runnable reloadItems = () -> {
            DummyDataService.getInstance().processAutoReturns();
            List<MyDealsItem> all = DummyDataService.getInstance().getMyDealsForBuyer(buyerUsername);
            String selectedFilter = filterCombo.getValue();
            String query = filterInput.getText() != null ? filterInput.getText().trim().toLowerCase() : "";

            List<MyDealsItem> filtered = all.stream().filter(item -> {
                // Filter dropdown
                if ("Active Deals".equals(selectedFilter)) {
                    if (!item.isDeal() || !"Active".equalsIgnoreCase(item.getStatusDisplay())) return false;
                } else if ("Pending & Countered Offers".equals(selectedFilter)) {
                    if (!item.isOffer() || (!"Pending".equalsIgnoreCase(item.getStatusDisplay()) && !"Countered".equalsIgnoreCase(item.getStatusDisplay()))) return false;
                } else if ("Completed & Returned".equals(selectedFilter)) {
                    if (!"Completed".equalsIgnoreCase(item.getStatusDisplay()) && !"Returned".equalsIgnoreCase(item.getStatusDisplay())) return false;
                }

                // Query match
                if (!query.isEmpty()) {
                    boolean matchTitle = item.getListingTitle() != null && item.getListingTitle().toLowerCase().contains(query);
                    boolean matchSeller = item.getSellerUsername() != null && item.getSellerUsername().toLowerCase().contains(query);
                    return matchTitle || matchSeller;
                }
                return true;
            }).collect(Collectors.toList());

            table.setItems(FXCollections.observableArrayList(filtered));
        };

        // Attach filter listeners
        filterCombo.setOnAction(e -> reloadItems.run());
        filterInput.textProperty().addListener((obs, o, n) -> reloadItems.run());
        btnRefresh.setOnAction(e -> reloadItems.run());

        // Wire Button Actions
        // 1. Withdraw (Subflow S1 & Alternative Flow A2)
        btnWithdraw.setOnAction(e -> {
            MyDealsItem sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || !sel.canWithdraw()) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Withdraw Offer");
            confirm.setHeaderText("Confirm Offer Withdrawal");
            confirm.setContentText("Are you sure you want to withdraw your offer for '" + sel.getListingTitle() + "'?");
            confirm.showAndWait().ifPresent(res -> {
                if (res == ButtonType.OK) {
                    DealOperationResult result = DummyDataService.getInstance().withdrawOffer(sel.getId(), buyerUsername);
                    if (result.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, result.getMessage()).showAndWait();
                    } else {
                        // Alternative Flow A2: Offer no longer pending
                        new Alert(Alert.AlertType.WARNING, result.getMessage()).showAndWait();
                    }
                    reloadItems.run();
                }
            });
        });

        // 2. Accept Counter (Subflow S2 & Alternative Flow A3)
        btnAcceptCounter.setOnAction(e -> {
            MyDealsItem sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || !sel.canAcceptCounter()) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Accept Counter Offer");
            confirm.setHeaderText("Accept Counter Offer Terms");
            confirm.setContentText(sel.getCounterDetails() + "\n\nDo you want to accept this counter-offer and confirm the deal?");
            confirm.showAndWait().ifPresent(res -> {
                if (res == ButtonType.OK) {
                    DealOperationResult result = DummyDataService.getInstance().acceptCounterOffer(sel.getId(), buyerUsername);
                    if (result.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, result.getMessage()).showAndWait();
                    } else {
                        // Alternative Flow A3: Counter offer no longer valid
                        new Alert(Alert.AlertType.WARNING, result.getMessage()).showAndWait();
                    }
                    reloadItems.run();
                }
            });
        });

        // 3. Decline Counter (Subflow S3)
        btnDeclineCounter.setOnAction(e -> {
            MyDealsItem sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || !sel.canDeclineCounter()) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Decline Counter Offer");
            confirm.setHeaderText("Decline Counter Offer");
            confirm.setContentText("Are you sure you want to decline the counter-offer for '" + sel.getListingTitle() + "'?");
            confirm.showAndWait().ifPresent(res -> {
                if (res == ButtonType.OK) {
                    DealOperationResult result = DummyDataService.getInstance().declineCounterOffer(sel.getId(), buyerUsername);
                    new Alert(Alert.AlertType.INFORMATION, result.getMessage()).showAndWait();
                    reloadItems.run();
                }
            });
        });

        // 4. Self-Return (Subflow S4)
        btnSelfReturn.setOnAction(e -> {
            MyDealsItem sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || !sel.canSelfReturn()) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Return Borrowed Item");
            confirm.setHeaderText("Confirm Early Return");
            confirm.setContentText("Are you sure you want to return '" + sel.getListingTitle() + "' to @" + sel.getSellerUsername() + "?\nThis will restore the item to Live status.");
            confirm.showAndWait().ifPresent(res -> {
                if (res == ButtonType.OK) {
                    DealOperationResult result = DummyDataService.getInstance().selfReturnItem(sel.getId(), buyerUsername);
                    if (result.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, result.getMessage()).showAndWait();
                    } else {
                        new Alert(Alert.AlertType.WARNING, result.getMessage()).showAndWait();
                    }
                    reloadItems.run();
                }
            });
        });

        // 5. Mark Deal Complete (Subflow S5) -> prompt rating
        btnMarkComplete.setOnAction(e -> {
            MyDealsItem sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || !sel.canMarkComplete()) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Mark Deal Complete");
            confirm.setHeaderText("Confirm Transaction Completion");
            confirm.setContentText("Have you received the item and completed the transaction for '" + sel.getListingTitle() + "'?");
            confirm.showAndWait().ifPresent(res -> {
                if (res == ButtonType.OK) {
                    DealOperationResult result = DummyDataService.getInstance().markDealComplete(sel.getId(), buyerUsername);
                    if (result.isSuccess()) {
                        reloadItems.run();
                        // Subflow S5 Step 5: Prompt to rate seller (with Rate Now and Skip)
                        if (result.isPromptRating() && result.getDeal() != null) {
                            Alert ratePrompt = new Alert(Alert.AlertType.CONFIRMATION);
                            ratePrompt.setTitle("Rate Seller");
                            ratePrompt.setHeaderText("Transaction Completed Successfully!");
                            ratePrompt.setContentText("Would you like to rate and review @" + result.getDeal().getSellerUsername() + " for '" + result.getDeal().getListingTitle() + "' now?");
                            ButtonType btnRateNow = new ButtonType("Rate Now", ButtonBar.ButtonData.OK_DONE);
                            ButtonType btnSkip = new ButtonType("Skip", ButtonBar.ButtonData.CANCEL_CLOSE);
                            ratePrompt.getButtonTypes().setAll(btnRateNow, btnSkip);
                            ratePrompt.showAndWait().ifPresent(rateChoice -> {
                                if (rateChoice == btnRateNow) {
                                    promptRatingDialog(result.getDeal(), reloadItems);
                                } else {
                                    // Alternative Flow A3: Skip Rating
                                    DummyDataService.getInstance().getBuyerReviewService().skipRating(result.getDeal().getId(), buyerUsername);
                                }
                            });
                        } else {
                            new Alert(Alert.AlertType.INFORMATION, result.getMessage()).showAndWait();
                        }
                    } else {
                        new Alert(Alert.AlertType.WARNING, result.getMessage()).showAndWait();
                    }
                }
            });
        });

        // 6. Rate Seller
        btnRate.setOnAction(e -> {
            MyDealsItem sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getDeal() == null) return;
            if (sel.canRate()) {
                promptRatingDialog(sel.getDeal(), reloadItems);
            } else if (sel.isRated()) {
                showDealReviewDialog(sel.getDeal().getId());
            }
        });

        // 7. Open Chat
        btnOpenChat.setOnAction(e -> {
            MyDealsItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && sel.canOpenChat()) {
                Listing l = DummyDataService.getInstance().findListing(sel.getListingId());
                if (l != null) {
                    openChatForListing(l, "DEALS");
                } else {
                    new Alert(Alert.AlertType.WARNING, "Listing details not found.").showAndWait();
                }
            }
        });

        // 8. View Listing
        btnViewListing.setOnAction(e -> {
            MyDealsItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && sel.canOpenListing()) {
                Listing l = DummyDataService.getInstance().findListing(sel.getListingId());
                if (l != null) {
                    showListingDetail(l, false, "DEALS");
                } else {
                    new Alert(Alert.AlertType.WARNING, "Listing details not found.").showAndWait();
                }
            }
        });

        // Initial population
        reloadItems.run();

        contentPane.getChildren().add(root);
    }

    private void promptRatingDialog(Deal deal, Runnable onSubmitted) {
        if (deal == null) return;

        User currentUser = SessionManager.getCurrentUser();
        String buyerUsername = currentUser != null ? currentUser.getUsername() : "alice";

        // Alternative Flow A2: Already Rated This Deal
        if (deal.isRated() || DummyDataService.getInstance().getBuyerReviewService().getReviewForDeal(deal.getId()).isPresent()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Already Rated");
            alert.setHeaderText("Deal Already Reviewed");
            alert.setContentText("This deal has already been rated.");
            alert.showAndWait();
            return;
        }

        // Basic Flow Step 2: Rating form showing seller's name and item
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Rate Seller — @" + deal.getSellerUsername());
        dialog.setHeaderText("Rate your transaction for '" + deal.getListingTitle() + "' with @" + deal.getSellerUsername());

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setPrefWidth(420);

        Label sellerInfoLabel = new Label("Seller: @" + deal.getSellerUsername());
        sellerInfoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");

        Label itemInfoLabel = new Label("Item: " + deal.getListingTitle());
        itemInfoLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");

        Label ratingLabel = new Label("Select Star Rating (1 to 5) *:");
        ratingLabel.setStyle("-fx-font-weight: bold;");

        ComboBox<Integer> comboRating = new ComboBox<>();
        comboRating.setPromptText("Select a star rating...");
        comboRating.getItems().addAll(5, 4, 3, 2, 1);
        comboRating.setValue(5);
        comboRating.setMaxWidth(Double.MAX_VALUE);
        comboRating.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("⭐".repeat(item) + " (" + item + " Star" + (item > 1 ? "s" : "") + ")");
                }
            }
        });
        comboRating.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select a star rating...");
                } else {
                    setText("⭐".repeat(item) + " (" + item + " Star" + (item > 1 ? "s" : "") + ")");
                }
            }
        });

        Label commentLabel = new Label("Review Comment (optional):");
        commentLabel.setStyle("-fx-font-weight: bold;");

        TextArea txtComment = new TextArea();
        txtComment.setPromptText("Share feedback on item condition, communication, and meetup...");
        txtComment.setPrefRowCount(3);
        txtComment.setWrapText(true);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        content.getChildren().addAll(sellerInfoLabel, itemInfoLabel, ratingLabel, comboRating, commentLabel, txtComment, errorLabel);
        dialog.getDialogPane().setContent(content);

        ButtonType btnSubmit = new ButtonType("Submit Rating", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSubmit, btnCancel);

        // Alternative Flow A1: Prevent dialog closing if no star rating selected
        Button okButton = (Button) dialog.getDialogPane().lookupButton(btnSubmit);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            Integer rating = comboRating.getValue();
            if (rating == null || rating < 1 || rating > 5) {
                event.consume();
                errorLabel.setText("A star rating is required.");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            }
        });

        dialog.showAndWait().ifPresent(res -> {
            if (res == btnSubmit) {
                Integer rating = comboRating.getValue();
                String comment = txtComment.getText();
                com.hkust.marketplace.models.RateSellerResult op = DummyDataService.getInstance()
                        .getBuyerReviewService().submitRating(deal.getId(), buyerUsername, rating, comment);
                if (op.isSuccess()) {
                    new Alert(Alert.AlertType.INFORMATION, op.getMessage()).showAndWait();
                    if (onSubmitted != null) {
                        onSubmitted.run();
                    }
                } else {
                    new Alert(Alert.AlertType.WARNING, op.getMessage()).showAndWait();
                }
            } else {
                // Alternative Flow A4: Cancel Rating
                DummyDataService.getInstance().getBuyerReviewService().cancelRating(deal.getId(), buyerUsername);
            }
        });
    }

    private void showDealReviewDialog(String dealId) {
        if (dealId == null) return;
        java.util.Optional<Review> optReview = DummyDataService.getInstance().getBuyerReviewService().getReviewForDeal(dealId);
        if (optReview.isPresent()) {
            showReviewDetailsDialog(optReview.get());
        } else {
            new Alert(Alert.AlertType.INFORMATION, "No review found for this deal.").showAndWait();
        }
    }

    private void showSellerReviewsDialog(String sellerUsername, String sellerFullName) {
        if (sellerUsername == null || sellerUsername.trim().isEmpty()) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reviews for @" + sellerUsername);
        String name = (sellerFullName != null && !sellerFullName.trim().isEmpty()) ? sellerFullName : sellerUsername;
        dialog.setHeaderText("Customer Reviews for " + name + " (@" + sellerUsername + ")");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setPrefWidth(480);
        content.setMaxHeight(400);

        SellerRatingInfo ratingInfo = DummyDataService.getInstance().getBuyerReviewService().getSellerRating(sellerUsername);
        Label ratingSummary = new Label(ratingInfo != null ? ratingInfo.getDisplayText() : SellerRatingInfo.NO_RATINGS_TEXT);
        ratingSummary.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #A68137;");
        content.getChildren().add(ratingSummary);

        List<Review> sellerReviews = DummyDataService.getInstance().getBuyerReviewService().getReviewsForSeller(sellerUsername);
        if (sellerReviews.isEmpty()) {
            Label noRev = new Label("No reviews yet for this seller.");
            noRev.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;");
            content.getChildren().add(noRev);
        } else {
            VBox reviewsList = new VBox(10);
            for (Review r : sellerReviews) {
                VBox card = new VBox(4);
                card.setStyle("-fx-background-color: #f8fafc; -fx-padding: 10; -fx-background-radius: 6; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");

                HBox topRow = new HBox(8);
                topRow.setAlignment(Pos.CENTER_LEFT);
                Label stars = new Label("⭐".repeat(Math.max(1, r.getRating())));
                stars.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold;");

                Label buyerLbl = new Label("by @" + r.getBuyerUsername());
                buyerLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                String dateStr = r.getDate() != null ? r.getDate().format(dtf) : "";
                Label dateLbl = new Label("· " + dateStr);
                dateLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

                topRow.getChildren().addAll(stars, buyerLbl, dateLbl);

                Label itemLbl = new Label("Item: " + (r.getListingTitle() != null ? r.getListingTitle() : "—"));
                itemLbl.setStyle("-fx-text-fill: #003974; -fx-font-size: 12px; -fx-font-weight: bold;");

                Label commentLbl = new Label(r.hasComment() ? r.getComment() : "(No written comment)");
                commentLbl.setWrapText(true);
                commentLbl.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 13px;");

                card.getChildren().addAll(topRow, itemLbl, commentLbl);

                if (r.hasReply()) {
                    VBox replyBox = new VBox(2);
                    replyBox.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 6 10 6 10; -fx-background-radius: 4; -fx-border-left-color: #003974; -fx-border-left-width: 3;");
                    Label replyHeader = new Label("Seller Reply:");
                    replyHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #003974;");
                    Label replyBody = new Label(r.getReply());
                    replyBody.setWrapText(true);
                    replyBody.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");
                    replyBox.getChildren().addAll(replyHeader, replyBody);
                    card.getChildren().add(replyBox);
                }

                reviewsList.getChildren().add(card);
            }
            ScrollPane sp = new ScrollPane(reviewsList);
            sp.setFitToWidth(true);
            sp.setPrefHeight(250);
            sp.setStyle("-fx-background-color: transparent;");
            content.getChildren().add(sp);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }

    @FXML
    public void showHistory() {
        setActive(btnHistory, "Deal History");
        pageTitle.setText("Deal History");
        contentPane.getChildren().clear();

        User currentUser = SessionManager.getCurrentUser();
        String buyerUsername = currentUser != null ? currentUser.getUsername() : "alice";

        com.hkust.marketplace.services.DealHistoryService historyService =
                DummyDataService.getInstance().getDealHistoryService();

        VBox root = new VBox(16);
        root.setPadding(new Insets(10));

        // Subtitle header
        Label subtitle = new Label("Full searchable and filterable record of your completed purchases, returned borrows, and claimed giveaways.");
        subtitle.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");

        // 1. Metric Summary Cards
        DealHistorySummary summary = historyService.getSummary(buyerUsername);
        HBox metricsRow = new HBox(12);
        metricsRow.setAlignment(Pos.CENTER_LEFT);

        VBox cardTotal = createHistoryMetricCard("Total Deals", String.valueOf(summary.getTotalCompletedDeals()), "#003974");
        VBox cardBought = createHistoryMetricCard("Purchased", String.valueOf(summary.getTotalBought()), "#1565c0");
        VBox cardBorrowed = createHistoryMetricCard("Borrowed & Returned", String.valueOf(summary.getTotalBorrowed()), "#7e22ce");
        VBox cardReceived = createHistoryMetricCard("Claimed Free", String.valueOf(summary.getTotalReceived()), "#15803d");
        VBox cardSpent = createHistoryMetricCard("Total Spent", summary.getFormattedTotalSpent(), "#b45309");

        HBox.setHgrow(cardTotal, Priority.ALWAYS);
        HBox.setHgrow(cardBought, Priority.ALWAYS);
        HBox.setHgrow(cardBorrowed, Priority.ALWAYS);
        HBox.setHgrow(cardReceived, Priority.ALWAYS);
        HBox.setHgrow(cardSpent, Priority.ALWAYS);
        metricsRow.getChildren().addAll(cardTotal, cardBought, cardBorrowed, cardReceived, cardSpent);

        // 2. Filter & Search Control Bar (Subflow S1 & S2)
        VBox filterCard = new VBox(12);
        filterCard.getStyleClass().add("filter-box");

        // Row 1: Search, Date Pickers, Category, Seller, Type
        HBox filterControls = new HBox(10);
        filterControls.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search item or seller...");
        searchField.setPrefWidth(210);

        DatePicker fromPicker = new DatePicker();
        fromPicker.setPromptText("From Date");
        fromPicker.setPrefWidth(125);

        DatePicker toPicker = new DatePicker();
        toPicker.setPromptText("To Date");
        toPicker.setPrefWidth(125);

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().add("All Categories");
        for (Category cat : Category.values()) {
            categoryCombo.getItems().add(cat.getDisplayName());
        }
        categoryCombo.setValue("All Categories");
        categoryCombo.setPrefWidth(140);

        ComboBox<String> sellerCombo = new ComboBox<>();
        sellerCombo.getItems().add("All Sellers");
        sellerCombo.getItems().addAll(historyService.getDistinctSellers(buyerUsername));
        sellerCombo.setValue("All Sellers");
        sellerCombo.setPrefWidth(130);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("All Types", "Sale (Bought)", "Loan (Borrowed)", "Giveaway (Received)");
        typeCombo.setValue("All Types");
        typeCombo.setPrefWidth(150);

        Button btnReset = new Button("↻ Reset");
        btnReset.getStyleClass().addAll("btn-outline", "btn-sm");

        filterControls.getChildren().addAll(
                searchField,
                new Label("Date:"), fromPicker, new Label("–"), toPicker,
                categoryCombo, sellerCombo, typeCombo, btnReset
        );

        // Row 2: Results count and status message
        HBox statusRow = new HBox(12);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        Label resultsCountLabel = new Label("Showing all deals");
        resultsCountLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");
        statusRow.getChildren().add(resultsCountLabel);

        filterCard.getChildren().addAll(filterControls, statusRow);

        // 3. Alternative Flows Notice Banner
        HBox bannerNotice = new HBox(10);
        bannerNotice.setAlignment(Pos.CENTER_LEFT);
        bannerNotice.setPadding(new Insets(10, 14, 10, 14));
        bannerNotice.setStyle("-fx-background-color: #f8fafd; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8;");
        Label lblBannerText = new Label();
        lblBannerText.setStyle("-fx-font-size: 13px;");
        bannerNotice.getChildren().add(lblBannerText);
        bannerNotice.setVisible(false);
        bannerNotice.setManaged(false);

        // 4. Deals TableView
        TableView<DealHistoryItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        // Double-click row handler: open listing details
        table.setRowFactory(tv -> {
            TableRow<DealHistoryItem> row = new TableRow<>();
            row.setStyle("-fx-cursor: hand;");
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() >= 2 && !row.isEmpty()) {
                    DealHistoryItem item = row.getItem();
                    if (item != null && item.getListingId() != null) {
                        Listing l = DummyDataService.getInstance().findListing(item.getListingId());
                        if (l != null) {
                            showListingDetail(l, false, "HISTORY");
                        }
                    }
                }
            });
            return row;
        });

        TableColumn<DealHistoryItem, String> cTitle = new TableColumn<>("Item Title");
        cTitle.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getItemTitle()));
        cTitle.setPrefWidth(180);

        TableColumn<DealHistoryItem, String> cCategory = new TableColumn<>("Category");
        cCategory.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getCategory().getDisplayName()));
        cCategory.setPrefWidth(100);

        TableColumn<DealHistoryItem, String> cSeller = new TableColumn<>("Seller");
        cSeller.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty("@" + d.getValue().getSellerUsername()));
        cSeller.setPrefWidth(100);

        TableColumn<DealHistoryItem, String> cType = new TableColumn<>("Deal Type");
        cType.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getActionTypeDisplayName() + " (" + d.getValue().getTypeDisplayName() + ")"));
        cType.setPrefWidth(130);
        cType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    DealHistoryItem row = getTableView().getItems().get(getIndex());
                    if (row.getDealType() == DealType.SALE) {
                        badge.getStyleClass().add("badge-sold");
                    } else if (row.getDealType() == DealType.LOAN) {
                        badge.getStyleClass().add("badge-returned");
                    } else {
                        badge.getStyleClass().add("badge-completed");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<DealHistoryItem, String> cDealDate = new TableColumn<>("Deal Date");
        cDealDate.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getFormattedDealDateShort()));
        cDealDate.setPrefWidth(95);

        TableColumn<DealHistoryItem, String> cClosedDate = new TableColumn<>("Closed / Return");
        cClosedDate.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getFormattedClosedDateShort()));
        cClosedDate.setPrefWidth(110);

        TableColumn<DealHistoryItem, String> cPrice = new TableColumn<>("Price");
        cPrice.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getFormattedPrice()));
        cPrice.setPrefWidth(90);

        TableColumn<DealHistoryItem, String> cOutcome = new TableColumn<>("Outcome");
        cOutcome.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getOutcomeDisplayName()));
        cOutcome.setPrefWidth(95);
        cOutcome.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    if ("Returned".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-returned");
                    } else {
                        badge.getStyleClass().add("badge-completed");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<DealHistoryItem, String> cRating = new TableColumn<>("Review");
        cRating.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().isRated() ? "⭐ Rated" : (d.getValue().canRate() ? "Unreviewed" : "—")));
        cRating.setPrefWidth(95);

        table.getColumns().addAll(cTitle, cCategory, cSeller, cType, cDealDate, cClosedDate, cPrice, cOutcome, cRating);

        // 5. Action Bar
        HBox actionBox = new HBox(12);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        Button btnRate = new Button("⭐ Rate Seller");
        btnRate.getStyleClass().addAll("btn-gold", "btn-sm");
        btnRate.setDisable(true);

        Button btnOpenChat = new Button("💬 Chat with Seller");
        btnOpenChat.getStyleClass().addAll("btn-primary", "btn-sm");
        btnOpenChat.setDisable(true);

        Button btnViewListing = new Button("🔍 View Listing");
        btnViewListing.getStyleClass().addAll("btn-outline", "btn-sm");
        btnViewListing.setDisable(true);

        actionBox.getChildren().addAll(btnRate, btnOpenChat, btnViewListing);

        // Selection listener to update action button states
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSel = newVal != null;
            if (hasSel && newVal.isRated()) {
                btnRate.setText("⭐ View Review");
                btnRate.setDisable(false);
            } else {
                btnRate.setText("⭐ Rate Seller");
                btnRate.setDisable(!hasSel || !newVal.canRate() || newVal.getDeal() == null);
            }
            btnOpenChat.setDisable(!hasSel || newVal.getListingId() == null);
            btnViewListing.setDisable(!hasSel || newVal.getListingId() == null);
        });

        // 6. Reload & Filtering Logic
        Runnable reloadHistory = () -> {
            boolean hasHistory = historyService.hasCompletedDeals(buyerUsername);

            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria();
            criteria.setFromDate(fromPicker.getValue());
            criteria.setToDate(toPicker.getValue());

            String catVal = categoryCombo.getValue();
            if (catVal != null && !"All Categories".equalsIgnoreCase(catVal)) {
                criteria.setCategory(Category.fromString(catVal));
            }

            String sellerVal = sellerCombo.getValue();
            if (sellerVal != null && !"All Sellers".equalsIgnoreCase(sellerVal)) {
                criteria.setSellerUsername(sellerVal);
            }

            String typeVal = typeCombo.getValue();
            if (typeVal != null && !"All Types".equalsIgnoreCase(typeVal)) {
                if (typeVal.contains("Sale")) criteria.setDealType(DealType.SALE);
                else if (typeVal.contains("Loan")) criteria.setDealType(DealType.LOAN);
                else if (typeVal.contains("Giveaway")) criteria.setDealType(DealType.GIVEAWAY);
            }

            String q = searchField.getText();
            if (q != null && !q.trim().isEmpty()) {
                criteria.setSearchQuery(q.trim());
            }

            List<DealHistoryItem> results = historyService.getDealHistory(buyerUsername, criteria);
            table.setItems(FXCollections.observableArrayList(results));

            // Alternative Flow A1: No Completed Deals at all
            if (!hasHistory) {
                lblBannerText.setText("ℹ️ No deal history yet. Your completed purchases, returned borrows, and claimed giveaways will appear here once finalized.");
                bannerNotice.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8;");
                bannerNotice.setVisible(true);
                bannerNotice.setManaged(true);
                resultsCountLabel.setText("No deals in history");
            }
            // Alternative Flow A2: No Results Match Active Filters
            else if (results.isEmpty()) {
                lblBannerText.setText("⚠️ No completed deals match the active search or filter criteria. Click 'Reset' to view all history.");
                bannerNotice.setStyle("-fx-background-color: #fffbeb; -fx-border-color: #fcd34d; -fx-border-radius: 8; -fx-background-radius: 8;");
                bannerNotice.setVisible(true);
                bannerNotice.setManaged(true);
                resultsCountLabel.setText("0 deals matching filters");
            } else {
                bannerNotice.setVisible(false);
                bannerNotice.setManaged(false);
                resultsCountLabel.setText("Showing " + results.size() + " deal" + (results.size() == 1 ? "" : "s"));
            }
        };

        // Wire filter triggers
        searchField.textProperty().addListener((obs, o, n) -> reloadHistory.run());
        fromPicker.valueProperty().addListener((obs, o, n) -> reloadHistory.run());
        toPicker.valueProperty().addListener((obs, o, n) -> reloadHistory.run());
        categoryCombo.setOnAction(e -> reloadHistory.run());
        sellerCombo.setOnAction(e -> reloadHistory.run());
        typeCombo.setOnAction(e -> reloadHistory.run());

        btnReset.setOnAction(e -> {
            searchField.clear();
            fromPicker.setValue(null);
            toPicker.setValue(null);
            categoryCombo.setValue("All Categories");
            sellerCombo.setValue("All Sellers");
            typeCombo.setValue("All Types");
            reloadHistory.run();
        });

        // Wire Action Buttons
        btnRate.setOnAction(e -> {
            DealHistoryItem sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getDeal() == null) return;
            if (sel.canRate()) {
                promptRatingDialog(sel.getDeal(), reloadHistory);
            } else if (sel.isRated()) {
                showDealReviewDialog(sel.getDealId());
            }
        });

        btnOpenChat.setOnAction(e -> {
            DealHistoryItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getListingId() != null) {
                Listing l = DummyDataService.getInstance().findListing(sel.getListingId());
                if (l != null) {
                    openChatForListing(l, "HISTORY");
                } else {
                    new Alert(Alert.AlertType.WARNING, "Listing details not found for this deal.").showAndWait();
                }
            }
        });

        btnViewListing.setOnAction(e -> {
            DealHistoryItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getListingId() != null) {
                Listing l = DummyDataService.getInstance().findListing(sel.getListingId());
                if (l != null) {
                    showListingDetail(l, false, "HISTORY");
                } else {
                    new Alert(Alert.AlertType.WARNING, "Listing details not found.").showAndWait();
                }
            }
        });

        // Initial Load
        reloadHistory.run();

        root.getChildren().addAll(subtitle, metricsRow, filterCard, bannerNotice, table, actionBox);
        contentPane.getChildren().add(root);
    }

    private VBox createHistoryMetricCard(String title, String value, String colorHex) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label lblVal = new Label(value);
        lblVal.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-size: 18px; -fx-font-weight: bold;");

        card.getChildren().addAll(lblTitle, lblVal);
        return card;
    }

    @FXML
    public void showNotifications() {
        setActive(btnNotifications, "Notification Board");
        contentPane.getChildren().clear();

        User currentUser = SessionManager.getCurrentUser();
        String username = currentUser != null ? currentUser.getUsername() : "alice";

        com.hkust.marketplace.services.BuyerNotificationService notifService =
                DummyDataService.getInstance().getBuyerNotificationService();
        // Check borrow reminders for active loans
        notifService.checkAndGenerateBorrowReminders(username);
        updateNotificationSidebarBadge();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        // 1. Filter and Search Control Bar (Subflow S2 & Alternative Flow A2)
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("filter-box");

        // Search text field
        TextField searchInput = new TextField(currentNotifSearch);
        searchInput.setPromptText("🔍 Search notifications...");
        searchInput.setPrefWidth(220);
        searchInput.textProperty().addListener((obs, oldVal, newVal) -> {
            currentNotifSearch = newVal;
            renderNotificationCards(root, username);
        });

        // Category filter ComboBox
        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().add("All Categories");
        for (NotificationCategory cat : NotificationCategory.values()) {
            categoryCombo.getItems().add(cat.getDisplayName());
        }
        categoryCombo.setValue(currentNotifCategory != null ? currentNotifCategory.getDisplayName() : "All Categories");
        categoryCombo.setOnAction(e -> {
            String selected = categoryCombo.getValue();
            if (selected == null || "All Categories".equalsIgnoreCase(selected)) {
                currentNotifCategory = null;
            } else {
                currentNotifCategory = NotificationCategory.fromRaw(selected);
            }
            renderNotificationCards(root, username);
        });

        // Status filter ComboBox
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("All Status", "Unread Only");
        statusCombo.setValue(Boolean.TRUE.equals(currentNotifUnreadOnly) ? "Unread Only" : "All Status");
        statusCombo.setOnAction(e -> {
            String selected = statusCombo.getValue();
            currentNotifUnreadOnly = "Unread Only".equalsIgnoreCase(selected);
            renderNotificationCards(root, username);
        });

        Region barSpacer = new Region();
        HBox.setHgrow(barSpacer, Priority.ALWAYS);

        // Mark All as Read button
        Button btnMarkAllRead = new Button("✓ Mark All as Read");
        btnMarkAllRead.getStyleClass().addAll("btn-outline", "btn-sm");
        btnMarkAllRead.setOnAction(e -> {
            int marked = notifService.markAllAsRead(username);
            updateNotificationSidebarBadge();
            renderNotificationCards(root, username);
            if (marked > 0) {
                new Alert(Alert.AlertType.INFORMATION, marked + " notification(s) marked as read.").showAndWait();
            }
        });

        // Clear All button
        Button btnClearAll = new Button("Clear All");
        btnClearAll.getStyleClass().addAll("btn-outline", "btn-sm");
        btnClearAll.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Clear all your notifications?");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    notifService.clearAllNotifications(username);
                    updateNotificationSidebarBadge();
                    renderNotificationCards(root, username);
                }
            });
        });

        filterBar.getChildren().addAll(searchInput, categoryCombo, statusCombo, barSpacer, btnMarkAllRead, btnClearAll);
        root.getChildren().add(filterBar);

        // Container for notification list
        VBox cardsContainer = new VBox(12);
        cardsContainer.setId("notifCardsContainer");
        root.getChildren().add(cardsContainer);

        renderNotificationCards(root, username);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private void renderNotificationCards(VBox root, String username) {
        VBox cardsContainer = (VBox) root.lookup("#notifCardsContainer");
        if (cardsContainer == null) return;
        cardsContainer.getChildren().clear();

        com.hkust.marketplace.services.BuyerNotificationService notifService =
                DummyDataService.getInstance().getBuyerNotificationService();
        NotificationFilterCriteria criteria = new NotificationFilterCriteria(
                currentNotifCategory,
                currentNotifUnreadOnly,
                currentNotifSearch
        );
        List<Notification> notifs = notifService.getNotifications(username, criteria);

        // Header summary row
        HBox summaryRow = new HBox(10);
        summaryRow.setAlignment(Pos.CENTER_LEFT);
        int totalUnread = notifService.getUnreadCount(username);
        Label summaryLabel = new Label("Showing " + notifs.size() + " notification" + (notifs.size() == 1 ? "" : "s")
                + " • " + totalUnread + " unread overall");
        summaryLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px; -fx-font-weight: bold;");
        summaryRow.getChildren().add(summaryLabel);
        cardsContainer.getChildren().add(summaryRow);

        // Alternative Flow A1: No Notifications
        if (notifs.isEmpty()) {
            VBox emptyCard = new VBox(10);
            emptyCard.setAlignment(Pos.CENTER);
            emptyCard.setPadding(new Insets(40, 20, 40, 20));
            emptyCard.getStyleClass().add("card");

            Label iconLabel = new Label("🔔");
            iconLabel.setStyle("-fx-font-size: 36px;");

            String msg = (currentNotifCategory != null || Boolean.TRUE.equals(currentNotifUnreadOnly)
                    || (currentNotifSearch != null && !currentNotifSearch.isBlank()))
                    ? "No notifications matching your filter criteria."
                    : "You have no notifications.";
            Label msgLabel = new Label(msg);
            msgLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 14px; -fx-font-weight: bold;");

            emptyCard.getChildren().addAll(iconLabel, msgLabel);

            if (currentNotifCategory != null || Boolean.TRUE.equals(currentNotifUnreadOnly)
                    || (currentNotifSearch != null && !currentNotifSearch.isBlank())) {
                Button btnReset = new Button("View All Notifications");
                btnReset.getStyleClass().addAll("btn-primary", "btn-sm");
                btnReset.setOnAction(e -> {
                    currentNotifCategory = null;
                    currentNotifUnreadOnly = false;
                    currentNotifSearch = "";
                    showNotifications();
                });
                emptyCard.getChildren().add(btnReset);
            }

            cardsContainer.getChildren().add(emptyCard);
            return;
        }

        // Basic Flow step 2: List notifications, newest first, unread highlighted
        for (Notification n : notifs) {
            VBox card = new VBox(8);
            card.getStyleClass().add("notif-item");
            if (n.isUnread()) {
                card.getStyleClass().add("notif-item-unread");
            } else {
                card.getStyleClass().add("notif-item-read");
            }
            card.setCursor(javafx.scene.Cursor.HAND);

            // Header row
            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);

            NotificationCategory cat = NotificationCategory.fromRaw(n.getCategory());
            Label catBadge = new Label(cat.getIcon() + " " + cat.getDisplayName());
            catBadge.getStyleClass().addAll("badge", getBadgeClassForCategory(cat));
            header.getChildren().add(catBadge);

            if (n.isUnread()) {
                Label unreadBadge = new Label("NEW");
                unreadBadge.getStyleClass().addAll("badge-unread");
                header.getChildren().add(unreadBadge);
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().add(spacer);

            Label timeLabel = new Label(n.getFormattedTimestamp());
            timeLabel.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
            header.getChildren().add(timeLabel);

            // Title & Message
            Label title = new Label(n.getTitle());
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #003974;");

            Label msg = new Label(n.getMessage());
            msg.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
            msg.setWrapText(true);

            // Action footer
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_RIGHT);

            Button viewBtn = new Button("View Related →");
            viewBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            viewBtn.setOnAction(e -> handleNotificationSelection(username, n));

            Button toggleReadBtn = new Button(n.isRead() ? "Mark Unread" : "Mark Read");
            toggleReadBtn.getStyleClass().addAll("btn-outline", "btn-sm");
            toggleReadBtn.setOnAction(e -> {
                if (n.isRead()) {
                    notifService.markAsUnread(username, n.getId());
                } else {
                    notifService.markAsRead(username, n.getId());
                }
                updateNotificationSidebarBadge();
                renderNotificationCards(root, username);
            });

            Button deleteBtn = new Button("✕");
            deleteBtn.getStyleClass().addAll("btn-outline", "btn-sm");
            deleteBtn.setTooltip(new Tooltip("Delete notification"));
            deleteBtn.setOnAction(e -> {
                notifService.deleteNotification(username, n.getId());
                updateNotificationSidebarBadge();
                renderNotificationCards(root, username);
            });

            actions.getChildren().addAll(toggleReadBtn, deleteBtn, viewBtn);

            // Clicking card directly triggers selection (Basic Flow step 3)
            card.setOnMouseClicked(e -> {
                if (e.getTarget() != viewBtn && e.getTarget() != toggleReadBtn && e.getTarget() != deleteBtn) {
                    handleNotificationSelection(username, n);
                }
            });

            card.getChildren().addAll(header, title, msg, actions);
            cardsContainer.getChildren().add(card);
        }
    }

    private void handleNotificationSelection(String username, Notification n) {
        com.hkust.marketplace.services.BuyerNotificationService notifService =
                DummyDataService.getInstance().getBuyerNotificationService();
        NotificationTargetResult result = notifService.resolveNotificationTarget(username, n.getId());
        updateNotificationSidebarBadge();

        if (result.getStatus() == NotificationTargetStatus.SUCCESS) {
            switch (result.getTargetType()) {
                case OFFER:
                case DEAL:
                    showMyDeals();
                    break;
                case CHAT_THREAD:
                    ChatThread thread = (ChatThread) result.getTargetObject();
                    Listing listing = DummyDataService.getInstance().getListing(thread.getListingId());
                    if (listing != null) {
                        openChatForListing(listing, "NOTIFICATIONS");
                    } else {
                        showRelatedItemDeletedDialog(n, "The listing associated with this chat thread is no longer available.");
                    }
                    break;
                case LISTING:
                    Listing l = (Listing) result.getTargetObject();
                    showListingDetail(l, false, "NOTIFICATIONS");
                    break;
                case REVIEW:
                    Review r = (Review) result.getTargetObject();
                    showReviewDetailsDialog(r);
                    break;
                case ANNOUNCEMENT:
                case NONE:
                default:
                    showAnnouncementDialog(n);
                    break;
            }
        } else if (result.getStatus() == NotificationTargetStatus.ITEM_DELETED) {
            // Alternative Flow A3: Related Item No Longer Exists
            showRelatedItemDeletedDialog(n, result.getMessage());
            showNotifications();
        } else {
            showAnnouncementDialog(n);
            showNotifications();
        }
    }

    private String getBadgeClassForCategory(NotificationCategory cat) {
        switch (cat) {
            case OFFERS: return "badge-offers";
            case CHAT: return "badge-chat";
            case BORROW_REMINDERS: return "badge-borrow";
            case LISTINGS: return "badge-listings";
            case REVIEWS: return "badge-reviews";
            case ANNOUNCEMENTS:
            default:
                return "badge-announcements";
        }
    }

    private void showRelatedItemDeletedDialog(Notification n, String detailMsg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Related Item No Longer Available");
        alert.setHeaderText(n.getTitle());
        alert.setContentText(n.getMessage() + "\n\nNotice: " + detailMsg);
        alert.showAndWait();
    }

    private void showAnnouncementDialog(Notification n) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(n.getCategory() != null ? n.getCategory() : "Notification Details");
        alert.setHeaderText(n.getTitle());
        alert.setContentText(n.getMessage() + "\n\nReceived: " + n.getFormattedTimestamp());
        alert.showAndWait();
    }

    private void showReviewDetailsDialog(Review review) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Review & Seller Reply");
        dialog.setHeaderText("Feedback on: " + review.getListingTitle());

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setPrefWidth(420);

        Label sellerLabel = new Label("Seller: @" + review.getSellerUsername());
        sellerLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");

        Label ratingLabel = new Label("Rating: " + "⭐".repeat(Math.max(1, review.getRating())) + " (" + review.getRating() + "/5)");
        ratingLabel.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold;");

        Label commentTitle = new Label("Your Review Comment:");
        commentTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        Label commentText = new Label(review.getComment() != null ? review.getComment() : "(No comment provided)");
        commentText.setWrapText(true);
        commentText.setStyle("-fx-text-fill: #475569; -fx-font-style: italic;");

        Label replyTitle = new Label("Seller's Reply:");
        replyTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");
        Label replyText = new Label(review.getReply() != null ? review.getReply() : "(No reply yet)");
        replyText.setWrapText(true);
        replyText.setStyle("-fx-text-fill: #1e293b; -fx-background-color: #f1f5f9; -fx-padding: 8; -fx-background-radius: 6;");

        content.getChildren().addAll(sellerLabel, ratingLabel, commentTitle, commentText, replyTitle, replyText);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "Manage Profile");
        contentPane.getChildren().clear();

        User currentUser = SessionManager.getCurrentUser();
        Buyer buyer = SessionManager.getCurrentBuyer();
        if (buyer == null && currentUser instanceof Buyer) {
            buyer = (Buyer) currentUser;
        }

        final Buyer currentBuyer = buyer;
        if (currentBuyer == null) {
            Label notLoggedIn = new Label("Please log in as a buyer to manage your profile.");
            notLoggedIn.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 14px;");
            contentPane.getChildren().add(notLoggedIn);
            return;
        }

        VBox container = new VBox(16);
        container.setPadding(new Insets(20));
        container.setAlignment(Pos.TOP_CENTER);

        VBox form = new VBox(16);
        form.setPadding(new Insets(24));
        form.setMaxWidth(560);
        form.getStyleClass().add("card");

        Label title = new Label("Manage Buyer Profile");
        title.getStyleClass().add("section-title");

        Label subtitle = new Label("View and update your personal information or change your password.");
        subtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        // Feedback label for status/validation messages
        Label feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);
        feedbackLabel.setMaxWidth(Double.MAX_VALUE);
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        // Basic Flow step 2: username (not editable)
        Label usernameTitle = new Label("Username");
        usernameTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        TextField usernameField = new TextField(currentBuyer.getUsername());
        usernameField.setEditable(false);
        usernameField.setDisable(true);
        usernameField.setStyle("-fx-opacity: 0.85; -fx-background-color: #f1f5f9;");
        Label usernameNote = new Label("Username cannot be modified.");
        usernameNote.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        // Basic Flow step 2 & 3: Full Name (required)
        Label nameTitle = new Label("Full Name *");
        nameTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        TextField nameField = new TextField(currentBuyer.getFullName() != null ? currentBuyer.getFullName() : "");
        nameField.setPromptText("Enter your full name");

        // Basic Flow step 2 & 3: Residence Hall preference (optional)
        Label residenceTitle = new Label("Residence Hall Preference");
        residenceTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        ComboBox<String> residenceCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Hall 1", "Hall 2", "Hall 3", "Hall 4", "Hall 5",
                "Hall 6", "Hall 7", "Hall 8", "Hall 9", "UG Hall 1", "UG Hall 2", "Off-campus"));
        residenceCombo.setMaxWidth(Double.MAX_VALUE);
        if (currentBuyer.getResidence() != null) {
            residenceCombo.setValue(currentBuyer.getResidence());
        } else if (currentBuyer.getResidenceHall() != null) {
            residenceCombo.setValue(currentBuyer.getResidenceHall());
        }

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

        Label newPassLabel = new Label("New Password");
        newPassLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        PasswordField newPassField = new PasswordField();
        newPassField.setPromptText("At least 8 chars, 1 uppercase, 1 lowercase, 1 digit");

        Label confirmPassLabel = new Label("Confirm New Password");
        confirmPassLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        PasswordField confirmPassField = new PasswordField();
        confirmPassField.setPromptText("Re-enter new password");

        pwdSection.getChildren().addAll(pwdTitle, pwdDesc,
                currPassLabel, currPassField,
                newPassLabel, newPassField,
                confirmPassLabel, confirmPassField);

        // Action buttons
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-outline");

        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("btn-primary");

        buttonBox.getChildren().addAll(cancelBtn, saveBtn);

        // Helper to clear error highlights
        Runnable clearErrors = () -> {
            nameField.getStyleClass().remove("field-error");
            currPassField.getStyleClass().remove("field-error");
            newPassField.getStyleClass().remove("field-error");
            confirmPassField.getStyleClass().remove("field-error");
            feedbackLabel.setVisible(false);
            feedbackLabel.setManaged(false);
            feedbackLabel.setText("");
        };

        // Alternative Flow A4: Cancel changes
        cancelBtn.setOnAction(e -> {
            clearErrors.run();
            nameField.setText(currentBuyer.getFullName() != null ? currentBuyer.getFullName() : "");
            residenceCombo.setValue(currentBuyer.getResidence());
            currPassField.clear();
            newPassField.clear();
            confirmPassField.clear();
            feedbackLabel.setText("Changes cancelled.");
            feedbackLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-padding: 8; -fx-background-color: #f1f5f9; -fx-background-radius: 6;");
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        });

        // Save Profile (Basic Flow step 5-8, Alternative Flows A1, A2, A3)
        saveBtn.setOnAction(e -> {
            clearErrors.run();

            ProfileUpdateRequest req = new ProfileUpdateRequest(
                    currentBuyer.getUsername(),
                    nameField.getText(),
                    residenceCombo.getValue(),
                    currPassField.getText(),
                    newPassField.getText(),
                    confirmPassField.getText()
            );

            ProfileUpdateResult result = DummyDataService.getInstance()
                    .getBuyerProfileService()
                    .updateProfile(currentBuyer.getUsername(), req);

            if (result.isSuccess()) {
                Buyer updated = result.getUpdatedBuyer();
                SessionManager.login(updated);
                welcomeLabel.setText("Hello, " + updated.getFullName());
                currPassField.clear();
                newPassField.clear();
                confirmPassField.clear();

                feedbackLabel.setText("Profile updated successfully!");
                feedbackLabel.setStyle("-fx-text-fill: #166534; -fx-font-size: 13px; -fx-padding: 10; -fx-background-color: #dcfce7; -fx-background-radius: 6; -fx-font-weight: bold;");
                feedbackLabel.setVisible(true);
                feedbackLabel.setManaged(true);

                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Profile updated successfully!");
                alert.setTitle("Success");
                alert.setHeaderText(null);
                alert.showAndWait();
            } else {
                Set<String> fields = result.getAffectedFields();
                if (fields.contains("fullName")) {
                    nameField.getStyleClass().add("field-error");
                }
                if (fields.contains("currentPassword")) {
                    currPassField.getStyleClass().add("field-error");
                }
                if (fields.contains("newPassword")) {
                    newPassField.getStyleClass().add("field-error");
                }
                if (fields.contains("confirmPassword")) {
                    confirmPassField.getStyleClass().add("field-error");
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
                nameTitle, nameField,
                residenceTitle, residenceCombo,
                sep, pwdSection, buttonBox
        );

        container.getChildren().add(form);
        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    // ==========================================
    // Chat Thread & Formal Offer Methods (US-B06)
    // ==========================================

    /**
     * Opens and displays the chat thread between the current logged-in buyer and the listing's seller (Use Case 1.5).
     *
     * @param listing    the listing being discussed
     * @param returnView view name to return to upon clicking back ("LISTING" or "DEALS")
     */
    public void openChatForListing(Listing listing, String returnView) {
        if (listing == null) return;

        User currentUser = SessionManager.getCurrentUser();
        String buyerUsername = currentUser != null ? currentUser.getUsername() : "alice";

        if (listing.getSellerUsername() != null && listing.getSellerUsername().equalsIgnoreCase(buyerUsername)) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cannot Chat");
            alert.setHeaderText(null);
            alert.setContentText("You cannot open a chat thread on your own listing.");
            alert.showAndWait();
            return;
        }

        ChatThread thread = DummyDataService.getInstance().getChatService().getOrCreateThread(listing.getId(), buyerUsername);
        if (thread == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("Failed to initialize chat thread for this listing.");
            alert.showAndWait();
            return;
        }

        renderChatThread(thread, listing, returnView, Collections.emptySet(), "", null, false);
    }

    /**
     * Renders the chat thread view with chronological messages, search highlighting, and formal offer actions.
     *
     * @param thread                the active chat thread
     * @param listing               the linked listing
     * @param returnView            the view to return to
     * @param highlightedMessageIds set of message IDs matching search query
     * @param currentSearchQuery    current search text (or empty)
     * @param searchFeedbackMessage search results summary or error message
     * @param isErrorFeedback       whether search feedback represents an error / no matches
     */
    public void renderChatThread(ChatThread thread, Listing listing, String returnView,
                                 Set<String> highlightedMessageIds, String currentSearchQuery,
                                 String searchFeedbackMessage, boolean isErrorFeedback) {
        pageTitle.setText("Chat Thread — " + listing.getTitle());
        contentPane.getChildren().clear();

        User currentUser = SessionManager.getCurrentUser();
        String currentUsername = currentUser != null ? currentUser.getUsername() : "alice";
        String sellerName = listing.getSellerFullName() != null ? listing.getSellerFullName() : thread.getSellerUsername();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        // 1. Top Header Navigation Bar
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        String backBtnText = "← Back to Listing";
        if ("DEALS".equalsIgnoreCase(returnView)) {
            backBtnText = "← Back to My Deals";
        } else if ("NOTIFICATIONS".equalsIgnoreCase(returnView)) {
            backBtnText = "← Back to Notifications";
        } else if ("HISTORY".equalsIgnoreCase(returnView)) {
            backBtnText = "← Back to Deal History";
        }
        Button backBtn = new Button(backBtnText);
        backBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        backBtn.setOnAction(e -> {
            if ("DEALS".equalsIgnoreCase(returnView)) {
                showMyDeals();
            } else if ("NOTIFICATIONS".equalsIgnoreCase(returnView)) {
                showNotifications();
            } else if ("HISTORY".equalsIgnoreCase(returnView)) {
                showHistory();
            } else if (returnView != null && returnView.startsWith("LISTING:")) {
                String prev = returnView.substring("LISTING:".length());
                showListingDetail(listing, false, prev);
            } else {
                showListingDetail(listing, false, "BROWSE");
            }
        });

        VBox titleCol = new VBox(2);
        Label threadHeading = new Label("Chat with " + sellerName + " (@" + thread.getSellerUsername() + ")");
        threadHeading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #003974;");

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        Label listingTitleLbl = new Label("Listing: " + listing.getTitle());
        listingTitleLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5a5a7a;");

        Label typeBadge = new Label(listing.getTypeDisplay());
        typeBadge.getStyleClass().addAll("badge");
        typeBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-size: 10px;");

        Label priceBadge = new Label(listing.getPriceDisplay());
        priceBadge.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #003974;");

        metaRow.getChildren().addAll(listingTitleLbl, typeBadge, priceBadge);
        titleCol.getChildren().addAll(threadHeading, metaRow);

        topBar.getChildren().addAll(backBtn, titleCol);

        // 2. Search Chat History Bar (Subflow S3 & Alternative Flow A3)
        HBox searchBar = new HBox(8);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.getStyleClass().add("chat-search-bar");
        searchBar.setStyle("-fx-background-color: #f8fafc; -fx-padding: 8 12; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-border-radius: 8;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search message history in this thread...");
        if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
            searchField.setText(currentSearchQuery);
        }
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("🔍 Search");
        searchBtn.getStyleClass().addAll("btn-primary", "btn-sm");

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        Label searchFeedback = new Label();
        searchFeedback.setStyle("-fx-font-size: 11px;");
        if (searchFeedbackMessage != null) {
            searchFeedback.setText(searchFeedbackMessage);
            searchFeedback.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                    + (isErrorFeedback ? "#d32f2f;" : "#2e7d32;"));
        }

        Runnable performSearch = () -> {
            String query = searchField.getText();
            if (query == null || query.trim().isEmpty()) {
                renderChatThread(thread, listing, returnView, Collections.emptySet(), "", null, false);
                return;
            }
            ChatSearchResult searchResult = DummyDataService.getInstance().getChatService()
                    .searchChatHistory(thread.getId(), currentUsername, query);

            if (searchResult.getStatus() == ChatOperationStatus.NO_MATCHES) {
                renderChatThread(thread, listing, returnView, Collections.emptySet(), query.trim(),
                        "No messages found matching '" + query.trim() + "'.", true);
            } else if (searchResult.isSuccess()) {
                Set<String> matchedIds = searchResult.getMatchingMessages().stream()
                        .map(Message::getId)
                        .collect(Collectors.toSet());
                String feedback = "Found " + searchResult.getMatchCount() + " matching message"
                        + (searchResult.getMatchCount() == 1 ? "." : "s.");
                renderChatThread(thread, listing, returnView, matchedIds, query.trim(), feedback, false);
            }
        };

        searchBtn.setOnAction(e -> performSearch.run());
        searchField.setOnAction(e -> performSearch.run());
        clearBtn.setOnAction(e -> renderChatThread(thread, listing, returnView, Collections.emptySet(), "", null, false));

        searchBar.getChildren().addAll(searchField, searchBtn, clearBtn, searchFeedback);

        // 3. Scrollable Message Container (Subflow S1 Step 3 & Alternative Flow A2)
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(420);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        scrollPane.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");

        VBox messageList = new VBox(10);
        messageList.setPadding(new Insets(14));

        if (!thread.hasMessages()) {
            // Alternative Flow A2: No Chat History
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40, 20, 40, 20));

            Label emptyIcon = new Label("💬");
            emptyIcon.setStyle("-fx-font-size: 36px;");

            Label emptyTitle = new Label("No messages yet");
            emptyTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

            Label emptyDesc = new Label("Start the conversation with " + sellerName + " about '" + listing.getTitle() + "'!");
            emptyDesc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

            emptyBox.getChildren().addAll(emptyIcon, emptyTitle, emptyDesc);
            messageList.getChildren().add(emptyBox);
        } else {
            DateTimeFormatter msgTimeFmt = DateTimeFormatter.ofPattern("MMM d, HH:mm");
            for (Message msg : thread.getMessages()) {
                boolean isMe = msg.getSenderUsername() != null && msg.getSenderUsername().equalsIgnoreCase(currentUsername);
                boolean isMatch = highlightedMessageIds.contains(msg.getId());
                boolean isFormalOffer = msg.isFormalOffer();

                HBox row = new HBox();
                row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

                VBox bubble = new VBox(4);
                bubble.setMaxWidth(520);

                String senderDisplay = isMe ? "You" : sellerName + " (@" + msg.getSenderUsername() + ")";
                String timeDisplay = msg.getSentDate() != null ? msg.getSentDate().format(msgTimeFmt) : "";
                Label metaLabel = new Label(senderDisplay + " • " + timeDisplay);

                if (isFormalOffer) {
                    bubble.getStyleClass().add("chat-bubble-offer");
                    metaLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #b45309;");

                    Label offerBadge = new Label("🏷️ FORMAL OFFER");
                    offerBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #92400e; -fx-background-color: #fde68a; -fx-padding: 2 6; -fx-background-radius: 4;");

                    Label textLabel = new Label(msg.getText());
                    textLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #78350f;");
                    textLabel.setWrapText(true);

                    bubble.getChildren().addAll(metaLabel, offerBadge, textLabel);
                } else if (isMe) {
                    bubble.getStyleClass().add("chat-bubble-me");
                    metaLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #cbd5e1;");

                    Label textLabel = new Label(msg.getText());
                    textLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: white;");
                    textLabel.setWrapText(true);

                    bubble.getChildren().addAll(metaLabel, textLabel);
                } else {
                    bubble.getStyleClass().add("chat-bubble-other");
                    metaLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");

                    Label textLabel = new Label(msg.getText());
                    textLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
                    textLabel.setWrapText(true);

                    bubble.getChildren().addAll(metaLabel, textLabel);
                }

                if (isMatch) {
                    bubble.getStyleClass().add("chat-bubble-highlight");
                    Label matchBadge = new Label("★ SEARCH MATCH");
                    matchBadge.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #b45309; -fx-background-color: #fef08a; -fx-padding: 2 4; -fx-background-radius: 3;");
                    bubble.getChildren().add(0, matchBadge);
                }

                row.getChildren().add(bubble);
                messageList.getChildren().add(row);
            }
        }

        scrollPane.setContent(messageList);
        scrollPane.setVvalue(1.0);

        // 4. Input & Action Bar (Subflow S1 & S2 & Alternative Flow A1)
        VBox inputSection = new VBox(6);

        Label validationErrorLabel = new Label();
        validationErrorLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-size: 12px; -fx-font-weight: bold;");
        validationErrorLabel.setVisible(false);

        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        TextField msgField = new TextField();
        msgField.setPromptText("Type your message to " + sellerName + "...");
        HBox.setHgrow(msgField, Priority.ALWAYS);

        Button sendBtn = new Button("Send ✉️");
        sendBtn.getStyleClass().addAll("btn-primary");

        Runnable doSendMessage = () -> {
            String text = msgField.getText();
            SendMessageResult res = DummyDataService.getInstance().getChatService()
                    .sendMessage(thread.getId(), currentUsername, text);

            if (res.getStatus() == ChatOperationStatus.EMPTY_MESSAGE) {
                validationErrorLabel.setText("Message cannot be empty.");
                validationErrorLabel.setVisible(true);
            } else if (res.isSuccess()) {
                msgField.clear();
                validationErrorLabel.setVisible(false);
                renderChatThread(thread, listing, returnView, Collections.emptySet(), "", null, false);
            } else {
                validationErrorLabel.setText(res.getErrorMessage());
                validationErrorLabel.setVisible(true);
            }
        };

        sendBtn.setOnAction(e -> doSendMessage.run());
        msgField.setOnAction(e -> doSendMessage.run());
        msgField.textProperty().addListener((obs, oldV, newV) -> {
            if (validationErrorLabel.isVisible()) {
                validationErrorLabel.setVisible(false);
            }
        });

        inputRow.getChildren().addAll(msgField, sendBtn);

        // Formal Offer Button (Subflow S2)
        if (listing.getType() == ListingType.SELL) {
            Button formalOfferBtn = new Button("🏷️ Make Formal Offer");
            formalOfferBtn.getStyleClass().addAll("btn-gold");
            formalOfferBtn.setOnAction(e -> showChatFormalPriceOfferDialog(thread, listing, returnView));
            inputRow.getChildren().add(formalOfferBtn);
        } else if (listing.getType() == ListingType.LEND) {
            Button formalOfferBtn = new Button("📅 Formal Borrow Request");
            formalOfferBtn.getStyleClass().addAll("btn-gold");
            formalOfferBtn.setOnAction(e -> showChatFormalBorrowRequestDialog(thread, listing, returnView));
            inputRow.getChildren().add(formalOfferBtn);
        }

        inputSection.getChildren().addAll(validationErrorLabel, inputRow);

        root.getChildren().addAll(topBar, searchBar, scrollPane, inputSection);
        contentPane.getChildren().add(root);
    }

    private void showChatFormalPriceOfferDialog(ChatThread thread, Listing listing, String returnView) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Make Formal Offer");
        dialog.setHeaderText("Submit a formal price offer for '" + listing.getTitle() + "'\n"
                + "Listed Price: " + listing.getPriceDisplay());

        ButtonType submitBtnType = new ButtonType("Submit Offer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitBtnType, ButtonType.CANCEL);

        VBox form = new VBox(10);
        form.setPadding(new Insets(20));

        TextField priceInput = new TextField();
        priceInput.setPromptText("Enter offer amount in HKD (e.g. 150)");

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #d32f2f; -fx-font-size: 12px;");
        errorLbl.setVisible(false);

        form.getChildren().addAll(new Label("Proposed Price (HKD):"), priceInput, errorLbl);
        dialog.getDialogPane().setContent(form);

        final Button submitBtn = (Button) dialog.getDialogPane().lookupButton(submitBtnType);
        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String txt = priceInput.getText();
            Double amount = null;
            try {
                if (txt != null && !txt.trim().isEmpty()) {
                    amount = Double.parseDouble(txt.trim());
                }
            } catch (NumberFormatException ex) {
                // handled below
            }

            if (amount == null || amount <= 0) {
                errorLbl.setText("Please enter a valid positive price amount.");
                errorLbl.setVisible(true);
                event.consume();
                return;
            }

            User currentUser = SessionManager.getCurrentUser();
            String buyerUsername = currentUser != null ? currentUser.getUsername() : "alice";

            SendFormalOfferResult result = DummyDataService.getInstance().getChatService()
                    .sendFormalPriceOffer(thread.getId(), buyerUsername, amount);

            if (!result.isSuccess()) {
                errorLbl.setText(result.getErrorMessage());
                errorLbl.setVisible(true);
                event.consume();
                return;
            }

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Offer Sent");
            successAlert.setHeaderText(null);
            successAlert.setContentText(result.getConfirmationText());
            successAlert.showAndWait();

            renderChatThread(thread, listing, returnView, Collections.emptySet(), "", null, false);
        });

        dialog.showAndWait();
    }

    private void showChatFormalBorrowRequestDialog(ChatThread thread, Listing listing, String returnView) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Formal Borrow Request");
        String maxDurationDesc = "Flexible";
        if (listing.getLendingDuration() != null) {
            maxDurationDesc = OfferManagementService.formatDuration(listing.getLendingDuration());
        } else if (listing.getLendingDays() != null) {
            maxDurationDesc = listing.getLendingDays() + " days";
        }
        dialog.setHeaderText("Request to borrow '" + listing.getTitle() + "'\n"
                + "Maximum Duration: " + maxDurationDesc);

        ButtonType submitBtnType = new ButtonType("Submit Request", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitBtnType, ButtonType.CANCEL);

        VBox form = new VBox(10);
        form.setPadding(new Insets(20));

        int defaultEndDays = 1;
        if (listing.getLendingDuration() != null) {
            long days = listing.getLendingDuration().toDays();
            defaultEndDays = days > 0 ? (int) days : 1;
        } else if (listing.getLendingDays() != null && listing.getLendingDays() > 0) {
            defaultEndDays = listing.getLendingDays();
        }
        DatePicker startDatePicker = new DatePicker(LocalDate.now());
        DatePicker endDatePicker = new DatePicker(LocalDate.now().plusDays(defaultEndDays));

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #d32f2f; -fx-font-size: 12px;");
        errorLbl.setVisible(false);

        form.getChildren().addAll(
                new Label("Proposed Start Date:"), startDatePicker,
                new Label("Proposed End Date:"), endDatePicker,
                errorLbl
        );
        dialog.getDialogPane().setContent(form);

        final Button submitBtn = (Button) dialog.getDialogPane().lookupButton(submitBtnType);
        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            LocalDate start = startDatePicker.getValue();
            LocalDate end = endDatePicker.getValue();

            if (start == null || end == null) {
                errorLbl.setText("Please select both start and end dates.");
                errorLbl.setVisible(true);
                event.consume();
                return;
            }

            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(23, 59, 59);

            User currentUser = SessionManager.getCurrentUser();
            String buyerUsername = currentUser != null ? currentUser.getUsername() : "alice";

            SendFormalOfferResult result = DummyDataService.getInstance().getChatService()
                    .sendFormalBorrowRequest(thread.getId(), buyerUsername, startDateTime, endDateTime);

            if (!result.isSuccess()) {
                errorLbl.setText(result.getErrorMessage());
                errorLbl.setVisible(true);
                event.consume();
                return;
            }

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Borrow Request Sent");
            successAlert.setHeaderText(null);
            successAlert.setContentText(result.getConfirmationText());
            successAlert.showAndWait();

            renderChatThread(thread, listing, returnView, Collections.emptySet(), "", null, false);
        });

        dialog.showAndWait();
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
