package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.AdminListingCreateRequest;
import com.hkust.marketplace.models.AdminListingEditRequest;
import com.hkust.marketplace.models.AdminListingFilterCriteria;
import com.hkust.marketplace.models.AdminListingOperationResult;
import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.Condition;
import com.hkust.marketplace.models.LendingDurationUnit;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Photo;
import com.hkust.marketplace.models.PhotoSelectionResult;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.services.AdminListingService;
import com.hkust.marketplace.services.CreateListingService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller for managing all marketplace listings (US-A07 / Use Case 3.7 Manage All Listings).
 *
 * <p>Handles listing oversight, filtering, adding listings on behalf of sellers,
 * modifying listing details, and taking down listings with mandatory reasons and offer cascades.</p>
 */
public class AdminListingsController {

    private final DummyDataService dataService;
    private final Runnable refreshCallback;
    private AdminListingFilterCriteria adminListingFilter = new AdminListingFilterCriteria();

    public AdminListingsController() {
        this(DummyDataService.getInstance(), null);
    }

    public AdminListingsController(DummyDataService dataService, Runnable refreshCallback) {
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
        AdminListingService listingService = dataService.getAdminListingService();
        Map<ListingStatus, Long> statusCounts = listingService.getListingCountsByStatus();
        List<Listing> listings = listingService.filterListings(adminListingFilter);

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        // 1. Metric cards
        root.getChildren().add(createListingSummaryCards(statusCounts));

        // 2. Action & Filter Toolbar
        root.getChildren().add(createListingFilterToolbar(() -> requestRefresh()));

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
                requestRefresh();
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
        return scroll;
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

    private VBox createListingFilterToolbar(Runnable refreshCb) {
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
            refreshCb.run();
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
            refreshCb.run();
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
            refreshCb.run();
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
            refreshCb.run();
        });

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        resetBtn.setOnAction(e -> {
            adminListingFilter = new AdminListingFilterCriteria();
            searchField.clear();
            statusCombo.setValue("All Statuses");
            catCombo.setValue("All Categories");
            typeCombo.setValue("All Types");
            refreshCb.run();
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

        int activeOffers = dataService.getAdminListingService().getActiveOffersCount(l.getId());
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
            if (isDealInProgressOrCompleted(l.getStatus())) {
                new Alert(Alert.AlertType.WARNING,
                        "The listing cannot be changed because a deal is in progress or completed.").showAndWait();
            } else if (l.getStatus() != ListingStatus.LIVE) {
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

    public void openAddListingDialog() {
        AdminListingService service = dataService.getAdminListingService();
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

        TextField lendingAmountField = new TextField();
        lendingAmountField.setPromptText("Duration amount (e.g. 7) *");
        lendingAmountField.setDisable(true);

        ComboBox<LendingDurationUnit> lendingUnitCombo = new ComboBox<>(FXCollections.observableArrayList(LendingDurationUnit.values()));
        lendingUnitCombo.setValue(LendingDurationUnit.DAYS);
        lendingUnitCombo.setDisable(true);

        HBox lendingDurationBox = new HBox(8, lendingAmountField, lendingUnitCombo);
        HBox.setHgrow(lendingAmountField, Priority.ALWAYS);

        typeCombo.setOnAction(e -> {
            String val = typeCombo.getValue();
            if ("Sell".equals(val)) {
                priceField.setDisable(false);
                lendingAmountField.setDisable(true);
                lendingUnitCombo.setDisable(true);
            } else if ("Lend".equals(val)) {
                priceField.setDisable(true);
                lendingAmountField.setDisable(false);
                lendingUnitCombo.setDisable(false);
            } else {
                priceField.setDisable(true);
                lendingAmountField.setDisable(true);
                lendingUnitCombo.setDisable(true);
            }
        });

        TextArea descArea = new TextArea();
        descArea.setPromptText("Description");
        descArea.setPrefRowCount(3);

        List<Path> chosenPhotos = new ArrayList<>();
        VBox photoSection = new VBox(6);
        HBox photoHeader = new HBox(12);
        photoHeader.setAlignment(Pos.CENTER_LEFT);
        Button addPhotosBtn = new Button("📷  Add photos…");
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
                AdminUIUtils.renderFormPhotoThumbnails(photoThumbnails, photoCountLbl, addPhotosBtn, chosenPhotos, noPhotosLbl);
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
                new Label("Lending Duration (for Lend):"), lendingDurationBox,
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

            String lendingAmount = null;
            LendingDurationUnit lendingUnit = null;
            if (type == ListingType.LEND) {
                lendingAmount = lendingAmountField.getText() != null ? lendingAmountField.getText().trim() : "";
                lendingUnit = lendingUnitCombo.getValue();
                String lendProblem = CreateListingService.checkLendingDuration(lendingAmount, lendingUnit);
                if (lendProblem != null) {
                    errLabel.setText(lendProblem);
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
                    lendingAmount, lendingUnit,
                    photos
            );
            if (type == ListingType.LEND) {
                req.setLendingAmount(lendingAmount);
                req.setLendingUnit(lendingUnit);
            }

            AdminListingOperationResult res = service.createListingOnBehalfOfSeller(req, getEffectiveAdminUsername());
            if (!res.isSuccess()) {
                errLabel.setText(res.getMessage());
                errLabel.setVisible(true);
                event.consume();
            } else {
                new Alert(Alert.AlertType.INFORMATION, res.getMessage()).showAndWait();
                requestRefresh();
            }
        });

        dialog.showAndWait();
    }

    public void openEditListingDialog(Listing l) {
        AdminListingService service = dataService.getAdminListingService();

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

        String prefilledLendingAmount = "";
        LendingDurationUnit prefilledLendingUnit = LendingDurationUnit.DAYS;
        Duration lending = l.getLendingDuration();
        if (l.getType() == ListingType.LEND && lending != null && lending.getSeconds() > 0) {
            long seconds = lending.getSeconds();
            for (LendingDurationUnit unit : List.of(LendingDurationUnit.WEEKS, LendingDurationUnit.DAYS, LendingDurationUnit.HOURS, LendingDurationUnit.MINUTES, LendingDurationUnit.SECONDS)) {
                long unitSeconds = unit.getLength().getSeconds();
                if (seconds % unitSeconds == 0) {
                    prefilledLendingUnit = unit;
                    prefilledLendingAmount = String.valueOf(seconds / unitSeconds);
                    break;
                }
            }
        } else if (l.getLendingDays() != null) {
            prefilledLendingAmount = String.valueOf(l.getLendingDays());
            prefilledLendingUnit = LendingDurationUnit.DAYS;
        }

        TextField lendingAmountField = new TextField(prefilledLendingAmount);
        lendingAmountField.setPromptText("Duration amount (e.g. 7) *");
        lendingAmountField.setDisable(l.getType() != ListingType.LEND);

        ComboBox<LendingDurationUnit> lendingUnitCombo = new ComboBox<>(FXCollections.observableArrayList(LendingDurationUnit.values()));
        lendingUnitCombo.setValue(prefilledLendingUnit);
        lendingUnitCombo.setDisable(l.getType() != ListingType.LEND);

        HBox lendingDurationBox = new HBox(8, lendingAmountField, lendingUnitCombo);
        HBox.setHgrow(lendingAmountField, Priority.ALWAYS);

        typeCombo.setOnAction(e -> {
            String val = typeCombo.getValue();
            if ("Sell".equals(val)) {
                priceField.setDisable(false);
                lendingAmountField.setDisable(true);
                lendingUnitCombo.setDisable(true);
            } else if ("Lend".equals(val)) {
                priceField.setDisable(true);
                lendingAmountField.setDisable(false);
                lendingUnitCombo.setDisable(false);
            } else {
                priceField.setDisable(true);
                lendingAmountField.setDisable(true);
                lendingUnitCombo.setDisable(true);
            }
        });

        TextArea descArea = new TextArea(l.getDescription() != null ? l.getDescription() : "");
        descArea.setPrefRowCount(3);

        List<Path> editPhotos = new ArrayList<>();
        if (l.getPhotos() != null) {
            for (Photo p : l.getPhotos()) {
                Path r = AdminUIUtils.readablePhoto(p != null ? p.getImagePath() : null);
                if (r != null) {
                    editPhotos.add(r.toAbsolutePath().normalize());
                }
            }
        }

        VBox editPhotoSection = new VBox(6);
        HBox editPhotoHeader = new HBox(12);
        editPhotoHeader.setAlignment(Pos.CENTER_LEFT);
        Button addEditPhotosBtn = new Button("📷  Add photos…");
        addEditPhotosBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        Label editPhotoCountLbl = new Label(editPhotos.size() + " / " + CreateListingService.MAX_PHOTOS + " photos");
        editPhotoCountLbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        editPhotoHeader.getChildren().addAll(addEditPhotosBtn, editPhotoCountLbl);

        HBox editPhotoThumbnails = new HBox(8);
        editPhotoThumbnails.setAlignment(Pos.CENTER_LEFT);
        Label noEditPhotosLbl = new Label("No photos selected. (1 to 6 photos required)");
        noEditPhotosLbl.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        AdminUIUtils.renderFormPhotoThumbnails(editPhotoThumbnails, editPhotoCountLbl, addEditPhotosBtn, editPhotos, noEditPhotosLbl);

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
                AdminUIUtils.renderFormPhotoThumbnails(editPhotoThumbnails, editPhotoCountLbl, addEditPhotosBtn, editPhotos, noEditPhotosLbl);
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
                new Label("Lending Duration (for Lend):"), lendingDurationBox,
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

            String lendingAmount = null;
            LendingDurationUnit lendingUnit = null;
            if (type == ListingType.LEND) {
                lendingAmount = lendingAmountField.getText() != null ? lendingAmountField.getText().trim() : "";
                lendingUnit = lendingUnitCombo.getValue();
                String lendProblem = CreateListingService.checkLendingDuration(lendingAmount, lendingUnit);
                if (lendProblem != null) {
                    errLabel.setText(lendProblem);
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
                    lendingAmount, lendingUnit,
                    photos
            );
            if (type == ListingType.LEND) {
                req.setLendingAmount(lendingAmount);
                req.setLendingUnit(lendingUnit);
            }

            AdminListingOperationResult res = service.editListing(req, getEffectiveAdminUsername());
            if (!res.isSuccess()) {
                errLabel.setText(res.getMessage());
                errLabel.setVisible(true);
                event.consume();
            } else {
                new Alert(Alert.AlertType.INFORMATION, res.getMessage()).showAndWait();
                requestRefresh();
            }
        });

        dialog.showAndWait();
    }

    public void openTakeDownDialog(Listing l) {
        AdminListingService service = dataService.getAdminListingService();

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
                requestRefresh();
            }
        });

        dialog.showAndWait();
    }

    public void openListingDetailsDialog(Listing l) {
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

        int activeOffers = dataService.getAdminListingService().getActiveOffersCount(l.getId());
        grid.add(new Label("Active Offers:"), 0, 8);
        grid.add(new Label(activeOffers + " pending/countered offer(s)"), 1, 8);

        grid.add(new Label("Photos:"), 0, 9);
        HBox detailsGallery = new HBox(8);
        detailsGallery.setAlignment(Pos.CENTER_LEFT);
        if (l.getPhotos() != null && !l.getPhotos().isEmpty()) {
            for (Photo p : l.getPhotos()) {
                Path r = AdminUIUtils.readablePhoto(p != null ? p.getImagePath() : null);
                detailsGallery.getChildren().add(AdminUIUtils.photoThumbnailNode(r, AdminUIUtils.DETAIL_THUMBNAIL_SIZE));
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
}
