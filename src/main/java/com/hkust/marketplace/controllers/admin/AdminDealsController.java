package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.AdminDealFilterCriteria;
import com.hkust.marketplace.models.AdminDealQueryResult;
import com.hkust.marketplace.models.AdminDealRecord;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.services.AdminDealsService;
import com.hkust.marketplace.utils.DummyDataService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * Controller for the All Deals Record oversight screen (US-A05 / Use Case 3.5 View All Deals).
 *
 * <p>Handles platform-wide transaction oversight, date-range filtering, and inspecting
 * comprehensive deal records with associated user, listing, offer, and review details.</p>
 */
public class AdminDealsController {

    private final DummyDataService dataService;
    private final Runnable refreshCallback;

    public AdminDealsController() {
        this(DummyDataService.getInstance(), null);
    }

    public AdminDealsController(DummyDataService dataService) {
        this(dataService, null);
    }

    public AdminDealsController(DummyDataService dataService, Runnable refreshCallback) {
        this.dataService = Objects.requireNonNull(dataService, "dataService cannot be null.");
        this.refreshCallback = refreshCallback;
    }

    public Node createView() {
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

            AdminDealQueryResult result = dataService.getAdminDealsService().queryDeals(criteria);

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
        return scroll;
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

    public void showDealDetailsDialog(AdminDealRecord record) {
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
}
