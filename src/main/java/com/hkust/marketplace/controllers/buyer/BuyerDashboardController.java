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
     * Opens and displays the listing detail screen (Use Case 1.4: View Listing Details).
     * Enforces view count increment, photo gallery, full metadata, seller rating, and action buttons.
     *
     * @param initialListing the listing to display
     */
    public void showListingDetail(Listing initialListing) {
        if (initialListing == null) return;

        // Use service to increment view count and fetch full details & seller rating
        ListingViewResult result = DummyDataService.getInstance().viewListing(initialListing.getId(), true);

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

        Button btnBack = new Button("← Back to Browse");
        btnBack.getStyleClass().addAll("btn-outline", "btn-sm");
        btnBack.setOnAction(e -> showBrowse());

        Label availBadge = new Label(listing.getAvailabilityDisplay());
        availBadge.getStyleClass().addAll("badge", "badge-live");

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

        sellerRow.getChildren().addAll(sellerName, sellerHandle, new Label("•"), ratingLabel);
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

        Button chatBtn = new Button("💬 Chat");
        chatBtn.getStyleClass().addAll("btn-outline");
        chatBtn.setOnAction(e -> {
            Alert chatAlert = new Alert(Alert.AlertType.INFORMATION);
            chatAlert.setTitle("Chat with Seller");
            chatAlert.setHeaderText(null);
            chatAlert.setContentText("Opening chat with " + listing.getSellerFullName() + " about '" + listing.getTitle() + "'...");
            chatAlert.showAndWait();
        });

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

        // Enable opening listing detail directly from My Deals (Use Case 1.4 Step 1)
        table.setRowFactory(tv -> {
            TableRow<String[]> row = new TableRow<>();
            row.setStyle("-fx-cursor: hand;");
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() >= 1 && (!row.isEmpty())) {
                    String[] rowData = row.getItem();
                    String itemTitle = rowData[0];
                    for (Listing l : DummyDataService.getInstance().getAllListings()) {
                        if (l.getTitle().equalsIgnoreCase(itemTitle)) {
                            showListingDetail(l);
                            break;
                        }
                    }
                }
            });
            return row;
        });

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
