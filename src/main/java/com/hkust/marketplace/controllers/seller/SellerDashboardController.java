package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.OfferManagementService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Controller of the Seller dashboard: the sidebar and the screens of the Seller Portal
 * (Task 2). The business rules live in the services of {@code com.hkust.marketplace.services};
 * this class only displays their data and forwards the Seller's actions to them.
 */
public class SellerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnOffers, btnCreate, btnStats, btnReviews, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    /** Business logic of the Offers &amp; Requests screen (use case 2.5, US-S05). */
    private final OfferManagementService offerService = new OfferManagementService();

    /**
     * Opens the dashboard on the My Listings screen. Without an active Seller session
     * (use case 2.2 special requirement, US-S02) nothing is loaded and the seller login
     * screen is shown instead. The switch is deferred because this method runs while the
     * dashboard itself is still being loaded.
     */
    @FXML
    public void initialize() {
        Seller seller;
        try {
            seller = SessionManager.requireSeller();
        } catch (SecurityException e) {
            Platform.runLater(SceneManager::showSellerLogin);
            return;
        }
        welcomeLabel.setText("Hello, " + seller.getFullName());
        currentActiveBtn = btnListings;
        updateOffersButton(offerService.getPendingOffers().size());
        showMyListings();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    @FXML
    private void showMyListings() {
        setActive(btnListings, "My Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Listing> myListings = DummyDataService.getInstance()
                .getListingsBySeller(user != null ? user.getUsername() : "");

        if (myListings.isEmpty()) {
            Label empty = new Label("You have not posted any listings yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            root.getChildren().add(empty);
        } else {
            for (Listing l : myListings) {
                root.getChildren().add(createListingRow(l));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createListingRow(Listing l) {
        HBox row = new HBox(16);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14));

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(l.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label meta = new Label(l.getCategory() + " · " + l.getTypeDisplay() + " · " + l.getMeetupLocation());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        info.getChildren().addAll(title, meta);

        Label status = new Label(l.getStatus().name().replace('_', ' '));
        status.getStyleClass().add("badge");
        switch (l.getStatus()) {
            case LIVE -> status.getStyleClass().add("badge-live");
            case PENDING_REVIEW -> status.getStyleClass().add("badge-pending");
            case REJECTED -> status.getStyleClass().add("badge-rejected");
            default -> status.getStyleClass().add("badge-sold");
        }

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        editBtn.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Edit form would open here.\n(Demo placeholder)").showAndWait());

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        delBtn.setOnAction(e -> {
            Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Delete this listing?");
            conf.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    DummyDataService.getInstance().removeListing(l.getId());
                    showMyListings();
                }
            });
        });

        row.getChildren().addAll(info, status, editBtn, delBtn);
        return row;
    }

    @FXML
    private void showCreateListing() {
        setActive(btnCreate, "Create New Listing");
        contentPane.getChildren().clear();

        VBox form = new VBox(12);
        form.setMaxWidth(520);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));

        Label title = new Label("Post a new item");
        title.getStyleClass().add("section-title");

        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList("Sell", "Lend", "Give Away"));
        typeCombo.setPromptText("Listing type *");
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        TextField nameField = new TextField();
        nameField.setPromptText("Item name *");
        ComboBox<String> catCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CATEGORIES));
        catCombo.setPromptText("Category *");
        catCombo.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> condCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CONDITIONS));
        condCombo.setPromptText("Condition *");
        condCombo.setMaxWidth(Double.MAX_VALUE);
        TextArea descArea = new TextArea();
        descArea.setPromptText("Description");
        descArea.setPrefRowCount(3);

        TextField priceField = new TextField();
        priceField.setPromptText("Price (HK$) – required for Sell");
        TextField daysField = new TextField();
        daysField.setPromptText("Lending duration (days) – required for Lend");

        ComboBox<String> meetupCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        meetupCombo.setPromptText("Meet-up location *");
        meetupCombo.setMaxWidth(Double.MAX_VALUE);

        Label photoNote = new Label("📷 Photos: up to 6 (UI placeholder – file chooser not wired in demo)");
        photoNote.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");

        Button submitBtn = new Button("Submit for Review");
        submitBtn.getStyleClass().add("btn-gold");
        submitBtn.setOnAction(e -> {
            if (typeCombo.getValue() == null || nameField.getText().isBlank() ||
                    catCombo.getValue() == null || condCombo.getValue() == null ||
                    meetupCombo.getValue() == null) {
                new Alert(Alert.AlertType.WARNING, "Please fill all required fields.").showAndWait();
                return;
            }
            User user = SessionManager.getCurrentUser();
            Listing listing = new Listing();
            listing.setTitle(nameField.getText().trim());
            listing.setDescription(descArea.getText());
            listing.setCategory(catCombo.getValue());
            listing.setCondition(condCombo.getValue());
            listing.setMeetupLocation(meetupCombo.getValue());
            listing.setSellerUsername(user.getUsername());
            listing.setSellerFullName(user.getFullName());
            listing.setStatus(ListingStatus.PENDING_REVIEW);

            switch (typeCombo.getValue()) {
                case "Sell" -> {
                    listing.setType(ListingType.SELL);
                    try { listing.setPrice(Double.parseDouble(priceField.getText())); }
                    catch (Exception ex) {
                        new Alert(Alert.AlertType.WARNING, "Valid price required for Sell listings.").showAndWait();
                        return;
                    }
                }
                case "Lend" -> {
                    listing.setType(ListingType.LEND);
                    try { listing.setLendingDays(Integer.parseInt(daysField.getText())); }
                    catch (Exception ex) {
                        new Alert(Alert.AlertType.WARNING, "Valid lending days required.").showAndWait();
                        return;
                    }
                }
                default -> listing.setType(ListingType.GIVEAWAY);
            }
            DummyDataService.getInstance().addListing(listing);
            // Notify admin
            DummyDataService.getInstance().addNotification(new Notification(
                    "admin", "New Listing Submitted",
                    user.getFullName() + " submitted '" + listing.getTitle() + "' for review.", "System"));
            new Alert(Alert.AlertType.INFORMATION,
                    "Listing submitted! Status: Pending Review.\nAdmin will review shortly.\n(Demo – in-memory only)").showAndWait();
            showMyListings();
        });

        form.getChildren().addAll(title,
                new Label("Listing Type *"), typeCombo,
                new Label("Item Name *"), nameField,
                new Label("Category *"), catCombo,
                new Label("Condition *"), condCombo,
                new Label("Description"), descArea,
                new Label("Price (Sell only)"), priceField,
                new Label("Lending Days (Lend only)"), daysField,
                new Label("Meet-up Location *"), meetupCombo,
                photoNote, submitBtn);

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showStats() {
        setActive(btnStats, "Seller Stats");
        contentPane.getChildren().clear();

        VBox root = new VBox(20);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Listing> myListings = DummyDataService.getInstance()
                .getListingsBySeller(user != null ? user.getUsername() : "");

        // Metric cards
        HBox metrics = new HBox(16);
        metrics.getChildren().addAll(
                metricCard("Listings", String.valueOf(myListings.size()), "#003974"),
                metricCard("Avg Rating", String.format("%.1f", user != null ? user.getAverageRating() : 0), "#A68137"),
                metricCard("Reviews", String.valueOf(user != null ? user.getRatingCount() : 0), "#2e7d32"),
                metricCard("Views (demo)", "128", "#1565c0")
        );
        root.getChildren().add(metrics);

        // Bar chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Listings by Category (demo data)");
        barChart.setLegendVisible(false);
        barChart.setPrefHeight(280);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Textbooks", 3));
        series.getData().add(new XYChart.Data<>("Electronics", 5));
        series.getData().add(new XYChart.Data<>("Furniture", 2));
        series.getData().add(new XYChart.Data<>("Others", 1));
        barChart.getData().add(series);

        // Pie chart
        PieChart pie = new PieChart();
        pie.setTitle("Listing Status Distribution");
        pie.setPrefHeight(280);
        pie.getData().add(new PieChart.Data("Live", 4));
        pie.getData().add(new PieChart.Data("Pending", 1));
        pie.getData().add(new PieChart.Data("Sold", 2));

        HBox charts = new HBox(20, barChart, pie);
        root.getChildren().add(charts);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox metricCard(String label, String value, String color) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(140);
        card.setPadding(new Insets(16));
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        card.getChildren().addAll(val, lbl);
        return card;
    }

    @FXML
    private void showReviews() {
        setActive(btnReviews, "Reviews & Feedback");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Review> reviews = DummyDataService.getInstance()
                .getReviewsForSeller(user != null ? user.getUsername() : "");

        if (reviews.isEmpty()) {
            root.getChildren().add(new Label("No reviews yet."));
        } else {
            for (Review r : reviews) {
                VBox item = new VBox(6);
                item.getStyleClass().add("card");
                Label stars = new Label("★".repeat(r.getRating()) + "☆".repeat(5 - r.getRating()));
                stars.setStyle("-fx-text-fill: #A68137; -fx-font-size: 16px;");
                Label comment = new Label(r.getComment());
                comment.setWrapText(true);
                Label meta = new Label("by @" + r.getBuyerUsername() + " · " + r.getListingTitle());
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
                HBox actions = new HBox(8);
                Button reply = new Button("Reply");
                reply.getStyleClass().addAll("btn-outline", "btn-sm");
                reply.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Reply sent to buyer (demo).").showAndWait());
                Button flag = new Button("Flag");
                flag.getStyleClass().addAll("btn-danger", "btn-sm");
                flag.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Review flagged for admin (demo).").showAndWait());
                actions.getChildren().addAll(reply, flag);
                item.getChildren().addAll(stars, comment, meta, actions);
                root.getChildren().add(item);
            }
        }
        contentPane.getChildren().add(new ScrollPane(root));
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notifications");
        contentPane.getChildren().clear();
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User user = SessionManager.getCurrentUser();
        List<Notification> notifs = DummyDataService.getInstance()
                .getNotificationsFor(user != null ? user.getUsername() : "");
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
                m.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
                Label meta = new Label(n.getCategory() + " · " +
                        n.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                item.getChildren().addAll(t, m, meta);
                root.getChildren().add(item);
            }
        }
        contentPane.getChildren().add(new ScrollPane(root));
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();
        User user = SessionManager.getCurrentUser();
        VBox form = new VBox(12);
        form.setMaxWidth(420);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));
        form.getChildren().add(new Label("Edit Seller Profile") {{ getStyleClass().add("section-title"); }});

        TextField name = new TextField(user != null ? user.getFullName() : "");
        PasswordField pass = new PasswordField();
        pass.setPromptText("New password (optional)");
        TextArea bio = new TextArea(user != null && user.getBio() != null ? user.getBio() : "");
        bio.setPrefRowCount(3);
        ComboBox<String> meetup = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        if (user != null && user.getPreferredMeetup() != null) meetup.setValue(user.getPreferredMeetup());
        meetup.setMaxWidth(Double.MAX_VALUE);

        Button save = new Button("Save Changes");
        save.getStyleClass().add("btn-gold");
        save.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, "Profile saved (demo).").showAndWait());

        form.getChildren().addAll(
                new Label("Full Name"), name,
                new Label("New Password"), pass,
                new Label("Bio"), bio,
                new Label("Preferred Meet-up"), meetup,
                save);
        contentPane.getChildren().add(form);
    }

    // =====================================================================
    // US-S05: Offers & Requests screen (use case 2.5 "Manage Offers And Requests")
    // =====================================================================

    /** Text of the sidebar button of the Offers &amp; Requests screen, without its counter. */
    private static final String OFFERS_BUTTON_TEXT = "  Offers & Requests";
    /** Format of the time fields of the counter borrow dates dialog. */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** Style of the error label of a dialog. */
    private static final String ERROR_STYLE = "-fx-text-fill: #c62828; -fx-font-size: 12px;";
    /** Style of secondary text. */
    private static final String MUTED_STYLE = "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;";

    /**
     * Shows the pending offers, borrow requests and claims received on the Seller's
     * listings, grouped by listing and oldest first (basic flow, step 2), followed by the
     * offers the Seller countered that wait for the buyer's answer. When nothing is pending,
     * the screen says so (alternative flow A1).
     */
    @FXML
    private void showOffers() {
        setActive(btnOffers, "Offers & Requests");
        contentPane.getChildren().clear();

        List<Offer> pending;
        List<Offer> countered;
        try {
            pending = offerService.getPendingOffers();
            countered = offerService.getCounteredOffers();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        updateOffersButton(pending.size());

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        Label intro = new Label(pending.isEmpty()
                ? "Offers, borrow requests and claims on your listings appear here."
                : pending.size() + (pending.size() == 1 ? " request" : " requests") + " to handle, oldest first.");
        intro.setStyle(MUTED_STYLE);
        root.getChildren().add(intro);

        if (pending.isEmpty()) {
            root.getChildren().add(emptyStateCard("📭", "Nothing to handle",
                    "You have no pending offers, borrow requests or claims."));
        } else {
            Map<String, List<Offer>> byListing = new LinkedHashMap<>();
            for (Offer offer : pending) {
                byListing.computeIfAbsent(offer.getListingId(), id -> new ArrayList<>()).add(offer);
            }
            byListing.forEach((listingId, offers) -> root.getChildren().add(createOfferGroup(listingId, offers)));
        }

        if (!countered.isEmpty()) {
            Label waiting = new Label("Waiting for the buyer's answer");
            waiting.getStyleClass().add("section-title");
            root.getChildren().add(waiting);
            for (Offer offer : countered) {
                root.getChildren().add(createCounteredRow(offer));
            }
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /**
     * Builds the card of one listing with its pending offers.
     *
     * @param listingId identifier of the listing
     * @param offers    the pending offers on it, oldest first
     * @return the card
     */
    private VBox createOfferGroup(String listingId, List<Offer> offers) {
        Listing listing = DummyDataService.getInstance().findListing(listingId);
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(titleOf(offers.get(0), listing));
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        header.getChildren().add(title);
        if (listing != null) {
            Label details = new Label(listingTerms(listing));
            details.setStyle(MUTED_STYLE);
            header.getChildren().add(details);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, listingStatusBadge(listing));
        card.getChildren().add(header);

        int others = offers.size() - 1;
        if (others > 0) {
            Label competing = new Label(offers.size() + " buyers are interested. Accepting one declines the others.");
            competing.setStyle("-fx-text-fill: #b45309; -fx-font-size: 12px;");
            card.getChildren().add(competing);
        }
        for (Offer offer : offers) {
            card.getChildren().addAll(new Separator(), createPendingOfferRow(offer, listing, others));
        }
        return card;
    }

    /**
     * Builds the row of a pending offer: buyer, terms, time received and the Accept,
     * Decline, Counter (not for claims) and Chat buttons.
     *
     * @param offer   the pending offer
     * @param listing its listing, {@code null} if it was deleted
     * @param others  number of other pending offers on the same listing
     * @return the row
     */
    private HBox createPendingOfferRow(Offer offer, Listing listing, int others) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label buyer = new Label(buyerName(offer));
        buyer.setStyle("-fx-font-weight: bold;");
        HBox who = new HBox(8, offerKindBadge(offer), buyer);
        who.setAlignment(Pos.CENTER_LEFT);
        Label terms = new Label(termsLine(offer));
        terms.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        Label received = new Label("Received " + OfferManagementService.formatDateTime(offer.getCreatedDate()));
        received.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        info.getChildren().addAll(who, terms, received);

        row.getChildren().addAll(info,
                smallButton("Accept", "btn-success", () -> handleAcceptOffer(offer, listing, others)),
                smallButton("Decline", "btn-danger", () -> handleDeclineOffer(offer)));
        if (offer.getType() != OfferType.CLAIM) {
            row.getChildren().add(smallButton("Counter", "btn-outline", () -> handleCounterOffer(offer, listing)));
        }
        row.getChildren().add(smallButton("Chat", "btn-outline", () -> handleOpenChat(offer)));
        return row;
    }

    /**
     * Builds the row of an offer the Seller countered, which waits for the buyer's answer.
     *
     * @param offer the countered offer, holding its counter offer
     * @return the row
     */
    private HBox createCounteredRow(Offer offer) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12));

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(titleOf(offer, DummyDataService.getInstance().findListing(offer.getListingId()))
                + " · " + buyerName(offer));
        title.setStyle("-fx-font-weight: bold;");
        Label asked = new Label("Buyer asked: " + OfferManagementService.describeTerms(offer));
        asked.setStyle(MUTED_STYLE);
        Offer counter = offer.getCounterOffer();
        Label proposed = new Label("Your counter: " + OfferManagementService.describeTerms(counter)
                + (counter != null ? "  (sent " + OfferManagementService.formatDateTime(counter.getCreatedDate()) + ")" : ""));
        proposed.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        info.getChildren().addAll(title, asked, proposed);

        Label badge = new Label("Countered");
        badge.getStyleClass().addAll("badge", "badge-countered");
        row.getChildren().addAll(info, badge, smallButton("Chat", "btn-outline", () -> handleOpenChat(offer)));
        return row;
    }

    /**
     * Subflow S1: shows a summary of the deal, asks the Seller to confirm, then accepts
     * the offer. Cancelling changes nothing (alternative flow A5).
     *
     * @param offer   the offer to accept
     * @param listing its listing, {@code null} if it was deleted
     * @param others  number of other pending offers that the acceptance will decline
     */
    private void handleAcceptOffer(Offer offer, Listing listing, int others) {
        String kind = OfferManagementService.kindOf(offer);
        String newStatus = listing != null && listing.getType() == ListingType.LEND ? "Lent" : "Reserved";
        StringBuilder summary = new StringBuilder()
                .append("Buyer: ").append(buyerName(offer)).append('\n')
                .append("Item: ").append(titleOf(offer, listing)).append('\n')
                .append("Terms: ").append(OfferManagementService.describeTerms(offer)).append("\n\n")
                .append("The listing will become ").append(newStatus).append('.');
        if (others > 0) {
            summary.append('\n').append(others).append(others == 1 ? " other pending offer" : " other pending offers")
                    .append(" on this listing will be declined.");
        }
        if (confirm("Accept " + kind, "Accept this " + kind + "?", summary.toString())) {
            runOfferAction(() -> offerService.acceptOffer(offer.getId()), "Deal accepted");
        }
    }

    /**
     * Subflow S2: asks the Seller to confirm the decline and to give an optional reason,
     * then declines the offer. Cancelling changes nothing (alternative flow A5).
     *
     * @param offer the offer to decline
     */
    private void handleDeclineOffer(Offer offer) {
        String kind = OfferManagementService.kindOf(offer);
        Dialog<ButtonType> dialog = newDialog("Decline " + kind, "Decline the " + kind + " from " + buyerName(offer) + "?");
        ButtonType declineType = new ButtonType("Decline", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(declineType, ButtonType.CANCEL);

        TextArea reason = new TextArea();
        reason.setPromptText("Reason for the buyer (optional)");
        reason.setPrefRowCount(3);
        reason.setWrapText(true);
        int max = OfferManagementService.MAX_DECLINE_REASON_LENGTH;
        Label length = new Label("0 / " + max);
        length.setStyle(MUTED_STYLE);
        reason.textProperty().addListener((observable, oldText, newText) -> {
            String trimmed = newText.trim();
            int count = trimmed.codePointCount(0, trimmed.length());
            length.setText(count + " / " + max);
            length.setStyle(count > max ? ERROR_STYLE : MUTED_STYLE);
            dialog.getDialogPane().lookupButton(declineType).setDisable(count > max);
        });
        Label note = new Label("The buyer will be notified. The listing stays on the marketplace.");
        note.setStyle(MUTED_STYLE);
        VBox content = new VBox(8, note, reason, length);
        content.setPrefWidth(440);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isPresent() && answer.get() == declineType) {
            runOfferAction(() -> offerService.declineOffer(offer.getId(), reason.getText()), "Offer declined");
        }
    }

    /**
     * Subflow S3: opens the counter dialog that fits the offer, a price for a Sell listing
     * or borrow dates for a Lend listing.
     *
     * @param offer   the offer to counter
     * @param listing its listing, {@code null} if it was deleted
     */
    private void handleCounterOffer(Offer offer, Listing listing) {
        if (offer.getType() == OfferType.BORROW_REQUEST) {
            showCounterDatesDialog(offer, listing);
        } else {
            showCounterPriceDialog(offer, listing);
        }
    }

    /**
     * Asks the Seller for a counter price and sends it. An invalid price keeps the dialog
     * open with the reason (alternative flow A4).
     *
     * @param offer   the price offer to counter
     * @param listing its listing, {@code null} if it was deleted
     */
    private void showCounterPriceDialog(Offer offer, Listing listing) {
        Dialog<ButtonType> dialog = newDialog("Counter offer", "Propose another price to " + buyerName(offer));
        ButtonType sendType = new ButtonType("Send Counter Offer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(sendType, ButtonType.CANCEL);

        Double askingPrice = listing != null ? listing.getPrice() : null;
        Label info = new Label("Buyer offered " + OfferManagementService.describeTerms(offer)
                + (askingPrice != null ? "  ·  Your asking price " + OfferManagementService.formatPrice(askingPrice) : ""));
        info.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");
        TextField priceField = new TextField(askingPrice != null ? String.format(Locale.ENGLISH, "%.2f", askingPrice) : "");
        priceField.setPromptText("Counter price in HK$");
        Label error = dialogErrorLabel();
        priceField.textProperty().addListener((observable, oldText, newText) -> hideError(error));

        VBox content = new VBox(8, info, new Label("Counter price (HK$):"), priceField, error);
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);

        runCounterDialog(dialog, sendType, error, OfferManagementStatus.INVALID_COUNTER_PRICE,
                () -> offerService.counterWithPrice(offer.getId(), parsePrice(priceField.getText())));
    }

    /**
     * Asks the Seller for counter borrow dates and sends them. Invalid dates keep the
     * dialog open with the reason (alternative flow A4). The 10 s preset makes the
     * auto-return easy to demonstrate.
     *
     * @param offer   the borrow request to counter
     * @param listing its listing, {@code null} if it was deleted
     */
    private void showCounterDatesDialog(Offer offer, Listing listing) {
        Dialog<ButtonType> dialog = newDialog("Counter borrow request", "Propose other borrow dates to " + buyerName(offer));
        ButtonType sendType = new ButtonType("Send Counter Offer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(sendType, ButtonType.CANCEL);

        Duration lending = listing != null ? listing.getLendingDuration() : null;
        Label info = new Label("Buyer asked: " + OfferManagementService.describeTerms(offer)
                + "\nLending duration of the listing: " + OfferManagementService.formatDuration(lending));
        info.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime defaultStart = offer.getStartDate() != null && offer.getStartDate().isAfter(now)
                ? offer.getStartDate().truncatedTo(ChronoUnit.SECONDS) : now;
        LocalDateTime defaultEnd = offer.getEndDate() != null && offer.getEndDate().isAfter(defaultStart)
                ? offer.getEndDate().truncatedTo(ChronoUnit.SECONDS) : defaultStart.plusDays(1);
        DatePicker startDate = new DatePicker(defaultStart.toLocalDate());
        TextField startTime = timeField(defaultStart.toLocalTime());
        DatePicker endDate = new DatePicker(defaultEnd.toLocalDate());
        TextField endTime = timeField(defaultEnd.toLocalTime());
        Label error = dialogErrorLabel();
        startTime.textProperty().addListener((observable, oldText, newText) -> hideError(error));
        endTime.textProperty().addListener((observable, oldText, newText) -> hideError(error));

        HBox presets = new HBox(6);
        presets.setAlignment(Pos.CENTER_LEFT);
        presets.getChildren().addAll(
                presetButton("10 s (Demo)", Duration.ofSeconds(10), startDate, startTime, endDate, endTime),
                presetButton("1 Day", Duration.ofDays(1), startDate, startTime, endDate, endTime));
        if (lending != null && !lending.isZero() && !lending.isNegative()) {
            presets.getChildren().add(presetButton("Full duration", lending, startDate, startTime, endDate, endTime));
        }

        VBox content = new VBox(8, info,
                new Label("Start date & time:"), new HBox(8, startDate, startTime),
                new Label("End date & time:"), new HBox(8, endDate, endTime),
                new Label("Quick presets (starting now):"), presets, error);
        content.setPrefWidth(440);
        dialog.getDialogPane().setContent(content);

        runCounterDialog(dialog, sendType, error, OfferManagementStatus.INVALID_COUNTER_DATES,
                () -> offerService.counterWithDates(offer.getId(),
                        readDateTime(startDate, startTime), readDateTime(endDate, endTime)));
    }

    /**
     * Shows a counter dialog and sends the counter when the Seller confirms. While the
     * input is invalid, the dialog stays open and shows why (alternative flow A4); any
     * other outcome closes it and is reported to the Seller.
     *
     * @param dialog        the counter dialog
     * @param sendType      the button that sends the counter
     * @param errorLabel    the label showing why the input is invalid
     * @param invalidStatus the status that means the input must be corrected
     * @param attempt       sends the counter; throws {@link IllegalArgumentException} with a
     *                      message when the input cannot be read
     */
    private void runCounterDialog(Dialog<ButtonType> dialog, ButtonType sendType, Label errorLabel,
                                  OfferManagementStatus invalidStatus, Supplier<OfferManagementResult> attempt) {
        OfferManagementResult[] outcome = new OfferManagementResult[1];
        boolean[] sessionEnded = {false};
        Button send = (Button) dialog.getDialogPane().lookupButton(sendType);
        send.addEventFilter(ActionEvent.ACTION, event -> {
            OfferManagementResult result;
            try {
                result = attempt.get();
            } catch (IllegalArgumentException e) {
                showError(errorLabel, e.getMessage());
                event.consume();
                return;
            } catch (SecurityException e) {
                sessionEnded[0] = true;
                return;
            }
            if (result.getStatus() == invalidStatus) {
                showError(errorLabel, result.getMessage());
                event.consume();
                return;
            }
            outcome[0] = result;
        });
        dialog.showAndWait();
        if (sessionEnded[0]) {
            endSellerSession();
        } else if (outcome[0] != null) {
            showOfferResult(outcome[0], "Counter offer sent");
            showOffers();
        }
    }

    /**
     * Basic flow, step 3.5: opens the chat thread linked to the offer's listing and buyer.
     *
     * @param offer the offer whose chat to open
     */
    private void handleOpenChat(Offer offer) {
        OfferManagementResult result;
        try {
            result = offerService.openChat(offer.getId());
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        if (!result.isSuccess()) {
            showOfferResult(result, null);
            showOffers();
            return;
        }
        showOfferChat(result.getChatThread(), offer);
    }

    /**
     * Shows a chat thread between the Seller and a buyer, with the same bubbles as the
     * Buyer Portal, and lets the Seller send a message.
     *
     * @param thread the chat thread
     * @param offer  the offer the chat was opened from
     */
    private void showOfferChat(ChatThread thread, Offer offer) {
        Seller seller;
        try {
            seller = SessionManager.requireSeller();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        pageTitle.setText("Chat — " + thread.getListingTitle());
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        Button back = smallButton("← Back to Offers & Requests", "btn-outline", this::showOffers);
        Label heading = new Label("Chat with " + buyerName(offer));
        heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        Label about = new Label("Listing: " + thread.getListingTitle() + "  ·  " + termsLine(offer));
        about.setStyle(MUTED_STYLE);
        HBox topBar = new HBox(12, back, new VBox(2, heading, about));
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox messages = new VBox(10);
        messages.setPadding(new Insets(14));
        if (!thread.hasMessages()) {
            messages.getChildren().add(emptyStateCard("💬", "No messages yet",
                    "Start the conversation with " + buyerName(offer) + "."));
        } else {
            DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);
            for (Message message : thread.getMessages()) {
                messages.getChildren().add(chatBubble(message, seller, timeFormat));
            }
        }
        ScrollPane scroll = new ScrollPane(messages);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(420);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        scroll.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        scroll.setVvalue(1.0);

        Label error = dialogErrorLabel();
        TextField input = new TextField();
        input.setPromptText("Type your message to @" + offer.getBuyerUsername() + "...");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.textProperty().addListener((observable, oldText, newText) -> hideError(error));
        Runnable send = () -> {
            SendMessageResult sent = DummyDataService.getInstance().getChatService()
                    .sendMessage(thread.getId(), seller.getUsername(), input.getText());
            if (sent.isSuccess()) {
                showOfferChat(thread, offer);
            } else {
                showError(error, sent.getStatus() == ChatOperationStatus.EMPTY_MESSAGE
                        ? "Message cannot be empty." : sent.getErrorMessage());
            }
        };
        input.setOnAction(e -> send.run());
        Button sendButton = new Button("Send ✉️");
        sendButton.getStyleClass().add("btn-primary");
        sendButton.setOnAction(e -> send.run());
        HBox inputRow = new HBox(8, input, sendButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(topBar, scroll, error, inputRow);
        contentPane.getChildren().add(root);
        input.requestFocus();
    }

    /**
     * Builds the bubble of a chat message, aligned right for the Seller's own messages.
     *
     * @param message    the message
     * @param seller     the logged-in Seller
     * @param timeFormat format of the message time
     * @return the row holding the bubble
     */
    private HBox chatBubble(Message message, Seller seller, DateTimeFormatter timeFormat) {
        boolean mine = message.getSenderUsername() != null && message.getSenderUsername().equalsIgnoreCase(seller.getUsername());
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(520);
        String time = message.getSentDate() != null ? message.getSentDate().format(timeFormat) : "";
        Label meta = new Label((mine ? "You" : "@" + message.getSenderUsername()) + " • " + time);
        Label text = new Label(message.getText());
        text.setWrapText(true);
        if (message.isFormalOffer()) {
            bubble.getStyleClass().add("chat-bubble-offer");
            meta.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #b45309;");
            text.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #78350f;");
        } else if (mine) {
            bubble.getStyleClass().add("chat-bubble-me");
            meta.setStyle("-fx-font-size: 10px; -fx-text-fill: #cbd5e1;");
            text.setStyle("-fx-font-size: 13px; -fx-text-fill: white;");
        } else {
            bubble.getStyleClass().add("chat-bubble-other");
            meta.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
            text.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
        }
        bubble.getChildren().addAll(meta, text);
        HBox row = new HBox(bubble);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Runs an action on an offer, reports its outcome and refreshes the list, so that an
     * offer handled in the meantime disappears (alternative flow A2).
     *
     * @param action       the action to run
     * @param successTitle title of the report when the action succeeds
     */
    private void runOfferAction(Supplier<OfferManagementResult> action, String successTitle) {
        OfferManagementResult result;
        try {
            result = action.get();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        showOfferResult(result, successTitle);
        showOffers();
    }

    /**
     * Reports the outcome of an action on an offer to the Seller.
     *
     * @param result       the outcome
     * @param successTitle title used when the action succeeded
     */
    private void showOfferResult(OfferManagementResult result, String successTitle) {
        Alert alert = new Alert(result.isSuccess() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                result.getMessage(), ButtonType.OK);
        alert.initOwner(SceneManager.getPrimaryStage());
        alert.setTitle("Offers & Requests");
        alert.setHeaderText(result.isSuccess() ? successTitle : "This request could not be handled");
        alert.showAndWait();
    }

    /**
     * Shows the number of pending requests on the sidebar button.
     *
     * @param pendingCount number of pending offers, borrow requests and claims
     */
    private void updateOffersButton(int pendingCount) {
        btnOffers.setText(pendingCount > 0 ? OFFERS_BUTTON_TEXT + " (" + pendingCount + ")" : OFFERS_BUTTON_TEXT);
    }

    /**
     * Tells the Seller that their session has ended (for example because the admin
     * deactivated the account) and shows the Seller login screen.
     */
    private void endSellerSession() {
        Alert alert = new Alert(Alert.AlertType.WARNING,
                "Your Seller session has ended. Please log in again.", ButtonType.OK);
        alert.initOwner(SceneManager.getPrimaryStage());
        alert.setTitle("Session ended");
        alert.setHeaderText(null);
        alert.showAndWait();
        SessionManager.logout();
        SceneManager.showSellerLogin();
    }

    /**
     * Asks the Seller to confirm an action.
     *
     * @param title   title of the dialog
     * @param header  question asked
     * @param content details of what will happen
     * @return {@code true} if the Seller confirmed
     */
    private boolean confirm(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(SceneManager.getPrimaryStage());
        alert.setTitle(title);
        alert.setHeaderText(header);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    /**
     * Creates an empty dialog owned by the main window.
     *
     * @param title  title of the dialog
     * @param header header text of the dialog
     * @return the dialog
     */
    private static Dialog<ButtonType> newDialog(String title, String header) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(SceneManager.getPrimaryStage());
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        return dialog;
    }

    /**
     * Creates a small styled button.
     *
     * @param text       text of the button
     * @param styleClass style class giving its colour
     * @param action     what the button does
     * @return the button
     */
    private static Button smallButton(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll(styleClass, "btn-sm");
        button.setOnAction(e -> action.run());
        return button;
    }

    /**
     * Creates a preset button that fills the counter dates with a period starting now.
     *
     * @param text      text of the button
     * @param length    length of the period
     * @param startDate start date field
     * @param startTime start time field
     * @param endDate   end date field
     * @param endTime   end time field
     * @return the button
     */
    private static Button presetButton(String text, Duration length, DatePicker startDate,
                                       TextField startTime, DatePicker endDate, TextField endTime) {
        Button button = new Button(text);
        button.getStyleClass().add("btn-sm");
        button.setOnAction(e -> {
            LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            LocalDateTime end = start.plus(length);
            startDate.setValue(start.toLocalDate());
            startTime.setText(start.toLocalTime().format(TIME_FORMAT));
            endDate.setValue(end.toLocalDate());
            endTime.setText(end.toLocalTime().format(TIME_FORMAT));
        });
        return button;
    }

    /**
     * Creates a time field showing {@code HH:mm:ss}.
     *
     * @param time initial time
     * @return the field
     */
    private static TextField timeField(LocalTime time) {
        TextField field = new TextField(time.format(TIME_FORMAT));
        field.setPromptText("HH:mm:ss");
        field.setPrefWidth(100);
        return field;
    }

    /**
     * Reads a date and time from a date picker and an {@code HH:mm:ss} field.
     *
     * @param date the date picker
     * @param time the time field
     * @return the date and time, or {@code null} if no date is chosen
     * @throws IllegalArgumentException if the time cannot be read
     */
    private static LocalDateTime readDateTime(DatePicker date, TextField time) {
        if (date.getValue() == null) {
            return null;
        }
        try {
            return LocalDateTime.of(date.getValue(), LocalTime.parse(time.getText().trim(), TIME_FORMAT));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Enter the times as HH:mm:ss, for example 14:30:00.", e);
        }
    }

    /**
     * Reads a price typed by the Seller, ignoring {@code HK$}, {@code $}, spaces and
     * thousands separators.
     *
     * @param text the typed text
     * @return the price, or {@code null} if nothing was typed
     * @throws IllegalArgumentException if the text is not a number
     */
    private static Double parsePrice(String text) {
        String cleaned = text == null ? "" : text.replace("HK$", "").replace("$", "").replace(",", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter the counter price as a number, for example 180 or 180.50.", e);
        }
    }

    /**
     * Creates a hidden error label for a dialog or a form.
     *
     * @return the label
     */
    private static Label dialogErrorLabel() {
        Label label = new Label();
        label.setStyle(ERROR_STYLE);
        label.setWrapText(true);
        hideError(label);
        return label;
    }

    /**
     * Shows an error message in an error label.
     *
     * @param label   the error label
     * @param message the message
     */
    private static void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    /**
     * Hides an error label.
     *
     * @param label the error label
     */
    private static void hideError(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }

    /**
     * Builds an empty-state card, styled like the Buyer Portal's.
     *
     * @param icon        emoji shown at the top
     * @param title       title of the card
     * @param description explanation
     * @return the card
     */
    private static VBox emptyStateCard(String icon, String title, String description) {
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 36px;");
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        Label descriptionLabel = new Label(description);
        descriptionLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
        descriptionLabel.setWrapText(true);
        VBox box = new VBox(12, iconLabel, titleLabel, descriptionLabel);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /**
     * Builds the badge of the kind of an offer.
     *
     * @param offer the offer
     * @return the badge
     */
    private static Label offerKindBadge(Offer offer) {
        Label badge = new Label();
        badge.getStyleClass().add("badge");
        if (offer.getType() == OfferType.BORROW_REQUEST) {
            badge.setText("Borrow request");
            badge.getStyleClass().add("badge-borrow");
        } else if (offer.getType() == OfferType.CLAIM) {
            badge.setText("Claim");
            badge.getStyleClass().add("badge-completed");
        } else {
            badge.setText("Offer");
            badge.getStyleClass().add("badge-offers");
        }
        return badge;
    }

    /**
     * Builds the badge of the status of a listing.
     *
     * @param listing the listing, {@code null} if it was deleted
     * @return the badge
     */
    private static Label listingStatusBadge(Listing listing) {
        Label badge = new Label(listing == null ? "Deleted" : listing.getAvailabilityDisplay());
        badge.getStyleClass().add("badge");
        ListingStatus status = listing == null ? ListingStatus.REMOVED : listing.getStatus();
        if (status == ListingStatus.LIVE) {
            badge.getStyleClass().add("badge-live");
        } else if (status == ListingStatus.PENDING_REVIEW) {
            badge.getStyleClass().add("badge-pending");
        } else if (status == ListingStatus.REJECTED || status == ListingStatus.REMOVED) {
            badge.getStyleClass().add("badge-rejected");
        } else {
            badge.getStyleClass().add("badge-sold");
        }
        return badge;
    }

    /**
     * Describes a listing's type and terms, for example {@code Sell · HK$ 200.00} or
     * {@code Lend · up to 7 days}.
     *
     * @param listing the listing
     * @return the description
     */
    private static String listingTerms(Listing listing) {
        if (listing.getType() == ListingType.LEND) {
            return "Lend · up to " + OfferManagementService.formatDuration(listing.getLendingDuration());
        }
        if (listing.getType() == ListingType.GIVEAWAY) {
            return "Give Away · Free";
        }
        return "Sell · " + (listing.getPrice() != null ? OfferManagementService.formatPrice(listing.getPrice()) : "no price");
    }

    /**
     * Describes what the buyer asks for, in one line.
     *
     * @param offer the offer
     * @return the line shown under the buyer's name
     */
    private static String termsLine(Offer offer) {
        String terms = OfferManagementService.describeTerms(offer);
        if (offer.getType() == OfferType.BORROW_REQUEST) {
            return "Wants to borrow: " + terms;
        }
        if (offer.getType() == OfferType.CLAIM) {
            return "Claims this free item";
        }
        return "Offers " + terms;
    }

    /**
     * Returns the buyer of an offer as shown to the Seller, for example
     * {@code Alice Chan (@alice)}.
     *
     * @param offer the offer
     * @return the buyer's name and username
     */
    private static String buyerName(Offer offer) {
        User buyer = DummyDataService.getInstance().findUser(offer.getBuyerUsername());
        return (buyer != null ? buyer.getDisplayName() + " " : "") + "(@" + offer.getBuyerUsername() + ")";
    }

    /**
     * Returns the title of an offer's listing, preferring the current one.
     *
     * @param offer   the offer
     * @param listing its listing, {@code null} if it was deleted
     * @return the title
     */
    private static String titleOf(Offer offer, Listing listing) {
        return listing != null && listing.getTitle() != null ? listing.getTitle() : offer.getListingTitle();
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
