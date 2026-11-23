package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.CreateListingService;
import com.hkust.marketplace.services.MyListingsService;
import com.hkust.marketplace.services.OfferManagementService;
import com.hkust.marketplace.services.ReviewHandlingService;
import com.hkust.marketplace.services.SellerChatService;
import com.hkust.marketplace.services.SellerNotificationService;
import com.hkust.marketplace.services.SellerProfileService;
import com.hkust.marketplace.services.SellerStatisticsService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PasswordPolicy;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventTarget;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Controller of the Seller dashboard: the sidebar and the screens of the Seller Portal
 * (Task 2). The business rules live in the services of {@code com.hkust.marketplace.services};
 * this class only displays their data and forwards the Seller's actions to them.
 */
public class SellerDashboardController {

    /** Main content container where active screens are mounted. */
    @FXML private StackPane contentPane;
    /** Header title displaying the active screen name. */
    @FXML private Label pageTitle;
    /** Greeting label displaying the seller's full name. */
    @FXML private Label welcomeLabel;
    /** Sidebar navigation button for My Listings. */
    @FXML private Button btnListings;
    /** Sidebar navigation button for Offers and Requests. */
    @FXML private Button btnOffers;
    /** Sidebar navigation button for Messages. */
    @FXML private Button btnMessages;
    /** Sidebar navigation button for Create Listing. */
    @FXML private Button btnCreate;
    /** Sidebar navigation button for Seller Stats. */
    @FXML private Button btnStats;
    /** Sidebar navigation button for Reviews and Feedback. */
    @FXML private Button btnReviews;
    /** Sidebar navigation button for Notifications. */
    @FXML private Button btnNotifications;
    /** Sidebar navigation button for Seller Profile. */
    @FXML private Button btnProfile;

    /** Currently active sidebar button. */
    private Button currentActiveBtn;

    /**
     * Default constructor for FXML loader instantiation.
     */
    public SellerDashboardController() {
    }

    /** Business logic of the Offers &amp; Requests screen (use case 2.5, US-S05). */
    private final OfferManagementService offerService = new OfferManagementService();

    /** Business logic of the Messages screen (Task 1.5, F09). */
    private final SellerChatService sellerChatService = new SellerChatService();

    /** Business logic of the Create Listing screen (use case 2.3, US-S03). */
    private final CreateListingService createListingService = new CreateListingService();

    /** Business logic of the My Listings screen (use case 2.4, US-S04). */
    private final MyListingsService myListingsService = new MyListingsService();

    /** Business logic of the Reviews &amp; Feedback screen (use case 2.9, US-S09). */
    private final ReviewHandlingService reviewService = new ReviewHandlingService();

    /** Business logic of the Notifications screen (use case 2.7, US-S07). */
    private final SellerNotificationService notificationService = new SellerNotificationService();

    /** Business logic of the Seller Stats screen (use case 2.8, US-S08). */
    private final SellerStatisticsService statisticsService = new SellerStatisticsService();

    /** Business logic of the My Profile screen (use case 2.6, US-S06). */
    private final SellerProfileService profileService = new SellerProfileService();

    /** The listing form currently shown (create or edit), {@code null} before it is first opened. */
    private ListingForm listingForm;

    /** Folder the photo chooser opens in: the folder of the last photos chosen. */
    private File lastPhotoFolder;

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
        updateNotificationsButton(notificationService.getUnreadCount());
        showMyListings();
    }

    /**
     * Marks the given sidebar button as active, applies styling, and updates the header title.
     *
     * @param btn   the button to activate
     * @param title the page title to display
     */
    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    // =====================================================================
    // US-S04: My Listings screen (use case 2.4 "Manage My Listings")
    // =====================================================================

    /** Size, in pixels, of the cover photo of a listing card. */
    private static final double CARD_THUMBNAIL_SIZE = 72;
    /** Format of the date a listing was posted. */
    private static final DateTimeFormatter POSTED_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    /** Style of a note about a rejected or taken-down listing. */
    private static final String NOTE_ERROR_STYLE = "-fx-background-color: #fff5f5; -fx-background-radius: 8;"
            + " -fx-padding: 8 10; -fx-text-fill: #b91c1c; -fx-font-size: 12px;";
    /** Style of a note about a listing awaiting review. */
    private static final String NOTE_PENDING_STYLE = "-fx-background-color: #fffbeb; -fx-background-radius: 8;"
            + " -fx-padding: 8 10; -fx-text-fill: #92400e; -fx-font-size: 12px;";
    /** Style of a note about a listing whose deal is in progress or completed. */
    private static final String NOTE_DEAL_STYLE = "-fx-background-color: #eff6ff; -fx-background-radius: 8;"
            + " -fx-padding: 8 10; -fx-text-fill: #1e3a8a; -fx-font-size: 12px;";

    /**
     * Shows the Seller's listings (the My Listings button of the sidebar). See
     * {@link #showMyListings(String)}.
     */
    @FXML
    private void showMyListings() {
        showMyListings(null);
    }

    /**
     * Shows the Seller's listings, newest first, with their status, views, open offers and
     * the admin's reason for a rejected or taken-down listing (basic flow, step 2), each with
     * its Edit and Delete buttons. Without listings, the screen says so (alternative flow A1).
     * Without an active Seller session, the seller login screen is shown instead.
     *
     * @param focusListingId listing to scroll to and highlight, for example the one a
     *                       notification is about (use case 2.7), {@code null} for none
     */
    private void showMyListings(String focusListingId) {
        Map<Listing, List<Offer>> listings = new LinkedHashMap<>();
        Map<Listing, MyListingResult> editChecks = new LinkedHashMap<>();
        int pendingCount;
        try {
            for (Listing listing : myListingsService.getMyListings()) {
                listings.put(listing, myListingsService.getOpenOffers(listing.getId()));
                editChecks.put(listing, myListingsService.checkCanEdit(listing.getId()));
            }
            pendingCount = offerService.getPendingOffers().size();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        setActive(btnListings, "My Listings");
        updateOffersButton(pendingCount);
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        Label intro = new Label(listingsSummary(listings.keySet()));
        intro.setStyle(MUTED_STYLE);
        intro.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(intro, Priority.ALWAYS);
        Button create = smallButton("+ New Listing", "btn-gold", this::showCreateListing);
        create.setId("myListingsNewButton");
        HBox header = new HBox(12, intro, create);
        header.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(header);

        if (listings.isEmpty()) {
            root.getChildren().add(emptyStateCard("📦", "No listings yet",
                    "You have not posted any listings yet. Use New Listing to sell, lend or give away an item."));
        } else {
            listings.forEach((listing, openOffers) ->
                    root.getChildren().add(createListingCard(listing, openOffers, editChecks.get(listing))));
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
        if (focusListingId != null) {
            highlightNode(scroll, root, "myListing-" + focusListingId);
        }
    }

    /**
     * Summarises the Seller's listings by status, for example
     * {@code 3 listings · 1 awaiting review · 2 live}.
     *
     * @param listings the Seller's listings
     * @return the summary shown above the list
     */
    private static String listingsSummary(Collection<Listing> listings) {
        if (listings.isEmpty()) {
            return "Your listings appear here once you post them.";
        }
        Map<ListingStatus, Integer> counts = new EnumMap<>(ListingStatus.class);
        listings.forEach(listing -> counts.merge(listing.getStatus(), 1, Integer::sum));
        StringBuilder summary = new StringBuilder(listings.size() + (listings.size() == 1 ? " listing" : " listings"));
        counts.forEach((status, count) -> summary.append(" · ").append(count).append(' ')
                .append(statusName(status).toLowerCase(Locale.ENGLISH)));
        return summary.toString();
    }

    /**
     * Names a status as shown on My Listings. A Removed listing on this screen is always one
     * taken down by the admin, since the listings the Seller deleted are not shown.
     *
     * @param status the status
     * @return the name, for example {@code Awaiting review} or {@code Taken down}
     */
    private static String statusName(ListingStatus status) {
        return switch (status) {
            case PENDING_REVIEW -> "Awaiting review";
            case LIVE -> "Live";
            case REJECTED -> "Rejected";
            case RESERVED -> "Reserved";
            case SOLD -> "Sold";
            case LENT -> "Lent";
            case GIVEN -> "Given";
            case REMOVED -> "Taken down";
        };
    }

    /**
     * Builds the card of one of the Seller's listings: its cover photo, title, status,
     * terms, category, condition, meet-up location, views, open offers, a note explaining
     * its status, and the Edit and Delete buttons.
     *
     * @param listing    the listing
     * @param openOffers its open offers, oldest first
     * @param editCheck  whether the listing can be edited, which tells if a deal is in progress
     * @return the card
     */
    private HBox createListingCard(Listing listing, List<Offer> openOffers, MyListingResult editCheck) {
        HBox card = new HBox(14);
        card.setId("myListing-" + listing.getId());
        card.getStyleClass().add("card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(14));

        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        title.setWrapText(true);
        HBox titleRow = new HBox(10, title, myListingBadge(listing));
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label terms = new Label(listingTerms(listing));
        terms.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        Label meta = new Label(listing.getCategory() + " · " + listing.getCondition()
                + " · 📍 " + listing.getMeetupLocation());
        meta.setStyle(MUTED_STYLE);
        int views = listing.getViewCount();
        StringBuilder activity = new StringBuilder("👁 ").append(views).append(views == 1 ? " view" : " views");
        if (listing.getPostedDate() != null) {
            activity.append("  ·  Posted ").append(listing.getPostedDate().format(POSTED_FORMAT));
        }
        if (!openOffers.isEmpty()) {
            activity.append("  ·  ").append(openOffers.size())
                    .append(openOffers.size() == 1 ? " open offer" : " open offers");
        }
        Label activityLabel = new Label(activity.toString());
        activityLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        VBox info = new VBox(5, titleRow, terms, meta, activityLabel);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label note = statusNote(listing, editCheck);
        if (note != null) {
            info.getChildren().add(note);
        }

        Button edit = smallButton("Edit", "btn-outline", () -> handleEditListing(listing));
        edit.setId("editListing-" + listing.getId());
        Button delete = smallButton("Delete", "btn-danger", () -> handleDeleteListing(listing));
        delete.setId("deleteListing-" + listing.getId());
        VBox actions = new VBox(8, edit, delete);
        if (!openOffers.isEmpty()) {
            actions.getChildren().add(smallButton("View offers", "btn-outline", this::showOffers));
        }
        if (listing.getStatus() == ListingStatus.RESERVED) {
            Button complete = smallButton(completeDealText(listing), "btn-primary", () -> handleCompleteDeal(listing));
            complete.setId("completeDeal-" + listing.getId());
            actions.getChildren().add(0, complete);
        }
        actions.getChildren().forEach(node -> ((Button) node).setMaxWidth(Double.MAX_VALUE));
        actions.setMinWidth(Region.USE_PREF_SIZE);

        card.getChildren().addAll(listingThumbnail(listing), info, actions);
        return card;
    }

    /**
     * Builds the note explaining a listing's status: the admin's reason of a rejected or
     * taken-down listing (basic flow, step 2), that it awaits review, or that its deal is in
     * progress or completed (alternative flow A2).
     *
     * @param listing   the listing
     * @param editCheck whether the listing can be edited
     * @return the note, {@code null} for a Live listing that can be changed
     */
    private static Label statusNote(Listing listing, MyListingResult editCheck) {
        String reason = listing.getAdminReason() == null || listing.getAdminReason().isBlank()
                ? "No reason was given." : listing.getAdminReason().trim();
        Label note = new Label();
        note.setWrapText(true);
        note.setMaxWidth(Double.MAX_VALUE);
        note.setId("myListingNote-" + listing.getId());
        if (editCheck != null && editCheck.getStatus() == MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED) {
            boolean completed = listing.getStatus() == ListingStatus.SOLD || listing.getStatus() == ListingStatus.GIVEN;
            note.setText(completed
                    ? "✅  Deal completed: this listing can no longer be edited or deleted."
                    : listing.getStatus() == ListingStatus.RESERVED
                    ? "🤝  Deal in progress: once you have handed the item over, use " + completeDealText(listing)
                            + " (or wait for the buyer to complete the deal). Until then the listing cannot be edited or deleted."
                    : "🤝  Deal in progress: this listing cannot be edited or deleted until the deal is over.");
            note.setStyle(NOTE_DEAL_STYLE);
        } else if (listing.getStatus() == ListingStatus.REJECTED) {
            note.setText("❌  Rejected by the admin: " + reason + "\nEdit the listing to submit it for review again.");
            note.setStyle(NOTE_ERROR_STYLE);
        } else if (MyListingsService.isTakenDown(listing)) {
            note.setText("🚫  Taken down by the admin: " + reason
                    + "\nIt can no longer be edited. You can delete it from your listings.");
            note.setStyle(NOTE_ERROR_STYLE);
        } else if (listing.getStatus() == ListingStatus.PENDING_REVIEW) {
            note.setText("⏳  Awaiting the admin's review. Buyers will see it once it is approved.");
            note.setStyle(NOTE_PENDING_STYLE);
        } else {
            return null;
        }
        return note;
    }

    /**
     * Builds the status badge of a listing on My Listings, where a Removed listing is one
     * taken down by the admin.
     *
     * @param listing the listing
     * @return the badge
     */
    private static Label myListingBadge(Listing listing) {
        Label badge = listingStatusBadge(listing);
        if (listing.getStatus() == ListingStatus.PENDING_REVIEW || listing.getStatus() == ListingStatus.REMOVED) {
            badge.setText(statusName(listing.getStatus()));
        }
        return badge;
    }

    /**
     * Builds the cover photo of a listing card, or an icon of its type if it has no photo
     * that can still be read.
     *
     * @param listing the listing
     * @return the thumbnail
     */
    private static StackPane listingThumbnail(Listing listing) {
        StackPane box = new StackPane();
        box.setMinSize(CARD_THUMBNAIL_SIZE, CARD_THUMBNAIL_SIZE);
        box.setMaxSize(CARD_THUMBNAIL_SIZE, CARD_THUMBNAIL_SIZE);
        box.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 8;");
        Path cover = readablePhoto(listing.getThumbnailPath());
        if (cover != null) {
            ImageView view = new ImageView(new Image(cover.toUri().toString(),
                    CARD_THUMBNAIL_SIZE, CARD_THUMBNAIL_SIZE, true, true, true));
            view.setFitWidth(CARD_THUMBNAIL_SIZE);
            view.setFitHeight(CARD_THUMBNAIL_SIZE);
            view.setPreserveRatio(true);
            box.getChildren().add(view);
        } else {
            Label icon = new Label(listing.getType() == ListingType.LEND ? "🤝"
                    : listing.getType() == ListingType.GIVEAWAY ? "🎁" : "🏷️");
            icon.setStyle("-fx-font-size: 28px;");
            box.getChildren().add(icon);
        }
        return box;
    }

    /**
     * Returns the file of a stored photo path if it still exists.
     *
     * @param path the stored path, may be {@code null}
     * @return the file, or {@code null} if there is none or it cannot be found
     */
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

    /**
     * Subflow S1, Begin Editing: if the listing can still be edited, shows the listing form
     * filled with its details; otherwise tells the Seller why (alternative flow A2).
     *
     * @param listing the listing to edit
     */
    private void handleEditListing(Listing listing) {
        Seller seller;
        MyListingResult check;
        try {
            seller = SessionManager.requireSeller();
            check = myListingsService.checkCanEdit(listing.getId());
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        if (!check.isSuccess()) {
            showListingResult(check, null);
            showMyListings();
            return;
        }
        setActive(btnListings, "Edit Listing");
        contentPane.getChildren().clear();
        listingForm = new ListingForm(seller, check);
        contentPane.getChildren().add(listingForm.getView());
    }

    /**
     * Subflow S2: if the listing can still be deleted (alternative flow A2), asks the Seller
     * to confirm, warns again about its open offers if it has any (A3), then deletes it.
     * Cancelling changes nothing (A5).
     *
     * @param listing the listing to delete
     */
    private void handleDeleteListing(Listing listing) {
        MyListingResult check;
        try {
            check = myListingsService.checkCanDelete(listing.getId());
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        if (!check.isSuccess()) {
            showListingResult(check, null);
            showMyListings();
            return;
        }
        if (!confirm("Delete listing", "Delete \"" + check.getListing().getTitle() + "\"?", check.getMessage())) {
            return;
        }
        MyListingResult result = runListingChange(
                confirmed -> myListingsService.deleteListing(listing.getId(), confirmed), "Delete listing");
        if (result != null) {
            showListingResult(result, "Listing deleted");
            showMyListings();
        }
    }

    /**
     * F34 (the brief's "mark as sold"): once the Seller has handed a Reserved listing over, asks
     * them to confirm, then marks its deal complete. The listing becomes Sold or Given, as when
     * the buyer completes the deal (use case 1.6), and the buyer is asked to rate the Seller. If
     * the deal can no longer be completed (for example the buyer has just completed it), tells
     * the Seller why and refreshes the list.
     *
     * @param listing the Reserved listing
     */
    private void handleCompleteDeal(Listing listing) {
        MyListingResult check;
        try {
            check = myListingsService.checkCanCompleteDeal(listing.getId());
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        if (!check.isSuccess()) {
            showListingResult(check, null);
            showMyListings();
            return;
        }
        String action = completeDealText(listing);
        if (!confirm(action, action + ": \"" + listing.getTitle() + "\"?", check.getMessage())) {
            return;
        }
        MyListingResult result;
        try {
            result = myListingsService.completeDeal(listing.getId());
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        showListingResult(result, "Deal completed");
        showMyListings(listing.getId());
    }

    /**
     * Names the action that completes the deal of a Reserved listing.
     *
     * @param listing the listing
     * @return {@code Mark as given} for a Give Away listing, {@code Mark as sold} otherwise
     */
    private static String completeDealText(Listing listing) {
        return listing.getType() == ListingType.GIVEAWAY ? "Mark as given" : "Mark as sold";
    }

    /**
     * Runs an edit or a delete. While the listing has open offers that the Seller has not
     * agreed to decline (alternative flow A3), the Seller is warned with the list of these
     * offers and asked to confirm again; the action is then retried with them. An offer that
     * arrives in the meantime is shown in a new warning.
     *
     * @param action      the edit or delete, given the identifiers of the offers the Seller
     *                    agreed to decline
     * @param actionTitle title of the warning dialog
     * @return the outcome of the action, or {@code null} if the Seller cancelled (the My
     *         Listings screen is then shown) or the session ended (the login screen is shown)
     */
    private MyListingResult runListingChange(Function<Set<String>, MyListingResult> action, String actionTitle) {
        Set<String> confirmed = Set.of();
        while (true) {
            MyListingResult result;
            try {
                result = action.apply(confirmed);
            } catch (SecurityException e) {
                endSellerSession();
                return null;
            }
            if (result.getStatus() != MyListingStatus.PENDING_OFFERS) {
                return result;
            }
            if (!confirmDeclineOffers(result, actionTitle)) {
                // A3, step 3: the flow resumes at Select Activity.
                showMyListings();
                return null;
            }
            confirmed = result.getOfferIds();
        }
    }

    /**
     * Warns the Seller that continuing will decline the listing's open offers and notify the
     * buyers (alternative flow A3), and asks to confirm again.
     *
     * @param result      the {@link MyListingStatus#PENDING_OFFERS} result, listing the offers
     * @param actionTitle title of the dialog
     * @return {@code true} if the Seller agreed to decline the offers
     */
    private boolean confirmDeclineOffers(MyListingResult result, String actionTitle) {
        StringBuilder details = new StringBuilder(result.getMessage()).append('\n');
        List<Offer> offers = result.getOffers();
        int shown = Math.min(offers.size(), 6);
        for (Offer offer : offers.subList(0, shown)) {
            details.append("\n•  ").append(buyerName(offer)).append(": ").append(termsLine(offer));
            if (offer.isCountered()) {
                details.append(" (you countered, waiting for the buyer)");
            }
        }
        if (offers.size() > shown) {
            details.append("\n•  and ").append(offers.size() - shown).append(" more");
        }
        ButtonType declineAndContinue = new ButtonType("Decline Offers and Continue", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.WARNING, details.toString(), declineAndContinue, ButtonType.CANCEL);
        alert.initOwner(SceneManager.getPrimaryStage());
        alert.setTitle(actionTitle);
        alert.setHeaderText("This listing has open offers");
        return alert.showAndWait().filter(declineAndContinue::equals).isPresent();
    }

    /**
     * Reports the outcome of an action on a listing to the Seller.
     *
     * @param result       the outcome
     * @param successTitle header used when the action succeeded, {@code null} for a check
     */
    private void showListingResult(MyListingResult result, String successTitle) {
        boolean information = result.isSuccess() || result.getStatus() == MyListingStatus.NO_CHANGES;
        Alert alert = new Alert(information ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                result.getMessage(), ButtonType.OK);
        alert.initOwner(SceneManager.getPrimaryStage());
        alert.setTitle("My Listings");
        alert.setHeaderText(result.isSuccess() ? successTitle
                : information ? "Nothing to save" : "This listing cannot be changed");
        alert.showAndWait();
    }

    // =====================================================================
    // US-S03: Create Listing screen (use case 2.3 "Create Listing")
    // =====================================================================

    /** Style class of a field that failed validation (defined in {@code styles.css}). */
    private static final String FIELD_ERROR_CLASS = "field-error";
    /** Style of a listing type button. */
    private static final String TYPE_BUTTON_STYLE = "-fx-background-color: white; -fx-background-radius: 10;"
            + " -fx-border-color: #d6dae3; -fx-border-width: 1.5; -fx-border-radius: 10; -fx-padding: 10 14;"
            + " -fx-font-size: 13px; -fx-text-fill: #1e293b; -fx-text-alignment: center; -fx-cursor: hand;";
    /** Style of the chosen listing type button. */
    private static final String TYPE_BUTTON_SELECTED_STYLE = "-fx-background-color: #fdf6e7; -fx-background-radius: 10;"
            + " -fx-border-color: #A68137; -fx-border-width: 2; -fx-border-radius: 10; -fx-padding: 10 14;"
            + " -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #003974; -fx-text-alignment: center;";
    /** Style of the photo area. */
    private static final String PHOTO_BOX_STYLE = "-fx-background-color: #f8fafc; -fx-background-radius: 8;"
            + " -fx-border-color: #cbd5e1; -fx-border-style: dashed; -fx-border-radius: 8; -fx-padding: 12;";
    /** Style of the photo area when the photos are invalid. */
    private static final String PHOTO_BOX_ERROR_STYLE = "-fx-background-color: #fff5f5; -fx-background-radius: 8;"
            + " -fx-border-color: #c62828; -fx-border-style: dashed; -fx-border-radius: 8; -fx-padding: 12;";
    /** Style of the label of a form field. */
    private static final String FIELD_LABEL_STYLE = "-fx-font-weight: bold; -fx-text-fill: #1e293b;";
    /** Size, in pixels, of a photo thumbnail. */
    private static final double THUMBNAIL_SIZE = 104;

    /**
     * Shows the Create Listing form (use case 2.3, US-S03). The Seller first chooses the
     * listing type (step 2); the form of that type then appears (step 3). Without an active
     * Seller session, the seller login screen is shown instead.
     */
    @FXML
    private void showCreateListing() {
        Seller seller;
        try {
            seller = SessionManager.requireSeller();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        setActive(btnCreate, "Create New Listing");
        contentPane.getChildren().clear();
        listingForm = new ListingForm(seller, null);
        contentPane.getChildren().add(listingForm.getView());
    }

    /**
     * The listing form, used to create a listing (use case 2.3, US-S03) and to edit one
     * (use case 2.4 subflow S1, US-S04): its fields, the photos chosen so far, and the display
     * of the errors of each field. The rules are checked by {@link CreateListingService} and
     * {@link MyListingsService}; this class only reads the form, shows the errors and lets the
     * Seller choose the photos.
     */
    private final class ListingForm {

        /** The Seller who posts the listing. */
        private final Seller seller;
        /** The listing being edited, {@code null} when creating a listing. */
        private final Listing editing;
        /** The check that allowed the edit, {@code null} when creating a listing. */
        private final MyListingResult editCheck;
        /** The three listing type buttons (basic flow, step 2). */
        private final ToggleGroup typeGroup = new ToggleGroup();
        /** Item name input. */
        private final TextField titleField = new TextField();
        /** Description input. */
        private final TextArea descriptionArea = new TextArea();
        /** Category choice. */
        private final ComboBox<Category> categoryBox = new ComboBox<>(FXCollections.observableArrayList(Category.values()));
        /** Condition choice. */
        private final ComboBox<Condition> conditionBox = new ComboBox<>(FXCollections.observableArrayList(Condition.values()));
        /** Price input (Sell listings). */
        private final TextField priceField = new TextField();
        /** Number of lending units (Lend listings). */
        private final TextField lendingAmountField = new TextField();
        /** Unit of the lending duration (Lend listings). */
        private final ComboBox<LendingDurationUnit> lendingUnitBox =
                new ComboBox<>(FXCollections.observableArrayList(LendingDurationUnit.values()));
        /** Meet-up location choice. */
        private final ComboBox<MeetupLocation> meetupBox =
                new ComboBox<>(FXCollections.observableArrayList(MeetupLocation.values()));
        /** The photos chosen so far, as absolute paths, the cover first. */
        private final List<Path> photos = new ArrayList<>();
        /** Thumbnails of the chosen photos. */
        private final FlowPane photoPane = new FlowPane(10, 10);
        /** Number of photos chosen, out of 6. */
        private final Label photoCount = new Label();
        /** Button that opens the photo chooser. */
        private final Button addPhotosButton = new Button("📷  Add photos…");
        /** Area holding the photo button and the thumbnails. */
        private final VBox photoBox = new VBox(10);
        /** Error label of each field. */
        private final Map<ListingField, Label> errorLabels = new EnumMap<>(ListingField.class);
        /** Inputs of each field, highlighted when the field is invalid. */
        private final Map<ListingField, List<Control>> inputs = new EnumMap<>(ListingField.class);
        /** Summary of the errors, next to the submit button. */
        private final Label formError = dialogErrorLabel();
        /** Button that submits the listing (step 7). */
        private final Button submitButton = new Button("Submit for Review");
        /** Fields of the chosen type, hidden until a type is chosen. */
        private final VBox detailsCard = new VBox(16);
        /** Price field, shown for a Sell listing. */
        private final VBox priceSection;
        /** Lending duration field, shown for a Lend listing. */
        private final VBox lendingSection;
        /** Explanation shown for a Give Away listing. */
        private final Label giveawayNote = new Label();
        /** Content of the screen. */
        private final VBox page = new VBox(16);
        /** Scroll pane holding the screen. */
        private final ScrollPane scroll = new ScrollPane();

        /**
         * Builds the form for a Seller, empty to create a listing, or filled with the details
         * of a listing to edit it (use case 2.4, subflow S1, step 1).
         *
         * @param seller    the logged-in Seller
         * @param editCheck the successful {@link MyListingsService#checkCanEdit} result of the
         *                  listing to edit, or {@code null} to create a listing
         */
        ListingForm(Seller seller, MyListingResult editCheck) {
            this.seller = seller;
            this.editCheck = editCheck;
            this.editing = editCheck != null ? editCheck.getListing() : null;
            page.setPadding(new Insets(4));
            page.setMaxWidth(720);

            titleField.setPromptText("For example: IKEA desk lamp");
            descriptionArea.setPromptText("Describe the item: brand, size, what is included, any defects…");
            descriptionArea.setPrefRowCount(4);
            descriptionArea.setWrapText(true);
            categoryBox.setPromptText("Choose a category");
            conditionBox.setPromptText("Choose the condition");
            meetupBox.setPromptText("Choose a meet-up location");
            priceField.setPromptText("For example 180 or 1,200.50");
            lendingAmountField.setPromptText("For example 7");
            lendingUnitBox.setValue(LendingDurationUnit.DAYS);
            for (ComboBox<?> box : List.of(categoryBox, conditionBox, meetupBox)) {
                box.setMaxWidth(Double.MAX_VALUE);
            }
            setIds();

            priceSection = buildPriceSection();
            lendingSection = buildLendingSection();
            giveawayNote.setText("🎁  Give-away items are free. The first buyer to claim the item sends you a"
                    + " request, which you accept from Offers & Requests.");
            giveawayNote.setWrapText(true);
            giveawayNote.setStyle("-fx-background-color: #ecfdf5; -fx-background-radius: 8; -fx-padding: 10 12;"
                    + " -fx-text-fill: #065f46; -fx-font-size: 13px;");

            page.getChildren().addAll(buildTypeCard(), buildDetailsCard());
            setShown(detailsCard, false);
            if (editing == null) {
                preselectMeetup();
            } else {
                page.getChildren().add(0, buildEditBanner());
                submitButton.setText(editing.getStatus() == ListingStatus.PENDING_REVIEW
                        ? "Save Changes" : "Save and Resubmit for Review");
                fill(MyListingsService.toEditRequest(editing));
            }
            clearErrorsOnEdit();
            refreshPhotos();

            scroll.setContent(page);
            scroll.setFitToWidth(true);
        }

        /**
         * Returns the node showing the form.
         *
         * @return the scroll pane holding the form
         */
        Node getView() {
            return scroll;
        }

        /**
         * Builds the card where the Seller chooses the listing type (basic flow, step 2).
         *
         * @return the card
         */
        private VBox buildTypeCard() {
            Label heading = new Label(editing == null ? "What do you want to do with your item?" : "Listing type");
            heading.getStyleClass().add("section-title");
            HBox buttons = new HBox(12,
                    typeButton(ListingType.SELL, "🏷️", "Sell", "Set a price"),
                    typeButton(ListingType.LEND, "🤝", "Lend", "Lend it for a while"),
                    typeButton(ListingType.GIVEAWAY, "🎁", "Give Away", "Free for another student"));
            Label error = errorLabelFor(ListingField.TYPE);
            typeGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
                if (newToggle != null) {
                    applyType((ListingType) newToggle.getUserData());
                }
            });
            VBox card = new VBox(12, heading, buttons, error);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(20));
            return card;
        }

        /**
         * Builds the banner at the top of the edit form: the listing's title and status, the
         * admin's reason if it was rejected, what saving will do (for example send it back to
         * review) and how many open offers saving will decline.
         *
         * @return the banner
         */
        private VBox buildEditBanner() {
            Label heading = new Label("Editing \"" + editing.getTitle() + "\"");
            heading.getStyleClass().add("section-title");
            heading.setWrapText(true);
            HBox top = new HBox(10, heading, myListingBadge(editing));
            top.setAlignment(Pos.CENTER_LEFT);
            VBox banner = new VBox(10, top);
            if (editing.getStatus() == ListingStatus.REJECTED) {
                String reason = editing.getAdminReason() == null || editing.getAdminReason().isBlank()
                        ? "No reason was given." : editing.getAdminReason().trim();
                Label rejected = new Label("❌  Rejected by the admin: " + reason);
                rejected.setWrapText(true);
                rejected.setMaxWidth(Double.MAX_VALUE);
                rejected.setStyle(NOTE_ERROR_STYLE);
                banner.getChildren().add(rejected);
            }
            int offers = editCheck.getOffers().size();
            Label outcome = new Label(editCheck.getMessage() + (offers == 0 ? ""
                    : offers == 1 ? " Its open offer will be declined and the buyer notified."
                    : " Its " + offers + " open offers will be declined and the buyers notified."));
            outcome.setId("editListingOutcome");
            outcome.setWrapText(true);
            outcome.setMaxWidth(Double.MAX_VALUE);
            outcome.setStyle(NOTE_PENDING_STYLE);
            banner.getChildren().add(outcome);
            banner.getStyleClass().add("card");
            banner.setPadding(new Insets(20));
            return banner;
        }

        /**
         * Fills the form with the details of the listing being edited. The type is chosen
         * first, since choosing it clears the values of the other types.
         *
         * @param values the current values of the listing
         */
        private void fill(CreateListingRequest values) {
            typeGroup.getToggles().stream()
                    .filter(toggle -> toggle.getUserData() == values.type())
                    .findFirst()
                    .ifPresent(typeGroup::selectToggle);
            titleField.setText(values.title());
            descriptionArea.setText(values.description());
            categoryBox.setValue(values.category());
            conditionBox.setValue(values.condition());
            priceField.setText(values.price());
            lendingAmountField.setText(values.lendingAmount());
            lendingUnitBox.setValue(values.lendingUnit());
            meetupBox.setValue(values.meetupLocation());
            photos.addAll(values.photos());
        }

        /**
         * Builds a listing type button. Clicking the chosen type again keeps it chosen.
         *
         * @param type the listing type
         * @param icon emoji shown before the name
         * @param name name of the type
         * @param hint short explanation shown under the name
         * @return the button
         */
        private ToggleButton typeButton(ListingType type, String icon, String name, String hint) {
            ToggleButton button = new ToggleButton(icon + "  " + name + "\n" + hint) {
                @Override
                public void fire() {
                    if (!isSelected()) {
                        super.fire();
                    }
                }
            };
            button.setId("listingType" + type.name());
            button.setUserData(type);
            button.setToggleGroup(typeGroup);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setPrefHeight(64);
            HBox.setHgrow(button, Priority.ALWAYS);
            button.setStyle(TYPE_BUTTON_STYLE);
            button.selectedProperty().addListener((observable, wasSelected, selected) ->
                    button.setStyle(selected ? TYPE_BUTTON_SELECTED_STYLE : TYPE_BUTTON_STYLE));
            return button;
        }

        /**
         * Builds the card with the fields of the chosen type and the submit and cancel buttons.
         *
         * @return the card
         */
        private VBox buildDetailsCard() {
            Label heading = new Label("Item details");
            heading.getStyleClass().add("section-title");
            Label required = new Label("Fields marked * are required.");
            required.setStyle(MUTED_STYLE);

            VBox category = field("Category *", ListingField.CATEGORY, categoryBox, null, categoryBox);
            VBox condition = field("Condition *", ListingField.CONDITION, conditionBox, null, conditionBox);
            HBox.setHgrow(category, Priority.ALWAYS);
            HBox.setHgrow(condition, Priority.ALWAYS);
            category.setMaxWidth(Double.MAX_VALUE);
            condition.setMaxWidth(Double.MAX_VALUE);

            submitButton.getStyleClass().add("btn-gold");
            submitButton.setOnAction(e -> submit());
            Button cancelButton = new Button("Cancel");
            cancelButton.setId("listingCancelButton");
            cancelButton.getStyleClass().add("btn-outline");
            cancelButton.setOnAction(e -> cancel());
            HBox actions = new HBox(10, submitButton, cancelButton);
            actions.setAlignment(Pos.CENTER_LEFT);

            detailsCard.getChildren().addAll(new VBox(4, heading, required),
                    field("Item name *", ListingField.TITLE, titleField,
                            counter(titleField, CreateListingService.MAX_TITLE_LENGTH), titleField),
                    field("Description *", ListingField.DESCRIPTION, descriptionArea,
                            counter(descriptionArea, CreateListingService.MAX_DESCRIPTION_LENGTH), descriptionArea),
                    new HBox(12, category, condition),
                    priceSection, lendingSection, giveawayNote,
                    field("Meet-up location *", ListingField.MEETUP_LOCATION, meetupBox,
                            meetupHint(), meetupBox),
                    buildPhotoSection(),
                    formError, actions);
            detailsCard.getStyleClass().add("card");
            detailsCard.setPadding(new Insets(20));
            return detailsCard;
        }

        /**
         * Builds the price field of a Sell listing (step 5.1).
         *
         * @return the price field
         */
        private VBox buildPriceSection() {
            Label currency = new Label("HK$");
            currency.setStyle(FIELD_LABEL_STYLE);
            priceField.setPrefWidth(200);
            HBox row = new HBox(8, currency, priceField);
            row.setAlignment(Pos.CENTER_LEFT);
            return field("Price *", ListingField.PRICE, row,
                    "More than HK$ 0 and at most HK$ 1,000,000. Buyers can offer a different price.", priceField);
        }

        /**
         * Builds the lending duration field of a Lend listing (step 6.1), with presets; the
         * 10 second preset makes a loan and its auto-return easy to demonstrate.
         *
         * @return the lending duration field
         */
        private VBox buildLendingSection() {
            lendingAmountField.setPrefWidth(120);
            HBox row = new HBox(8, lendingAmountField, lendingUnitBox,
                    lendingPreset("10 s (Demo)", 10, LendingDurationUnit.SECONDS),
                    lendingPreset("1 Day", 1, LendingDurationUnit.DAYS),
                    lendingPreset("1 Week", 1, LendingDurationUnit.WEEKS),
                    lendingPreset("30 Days", 30, LendingDurationUnit.DAYS));
            row.setAlignment(Pos.CENTER_LEFT);
            return field("Lending duration *", ListingField.LENDING_DURATION, row,
                    "From 5 seconds to 365 days: buyers can ask to borrow the item for up to this long.",
                    lendingAmountField, lendingUnitBox);
        }

        /**
         * Builds a button that fills in a lending duration.
         *
         * @param text   text of the button
         * @param amount number of units
         * @param unit   unit
         * @return the button
         */
        private Button lendingPreset(String text, int amount, LendingDurationUnit unit) {
            return smallButton(text, "btn-outline", () -> {
                lendingAmountField.setText(String.valueOf(amount));
                lendingUnitBox.setValue(unit);
            });
        }

        /**
         * Builds the photo area (step 4): the button that opens the photo chooser, the count
         * and the thumbnails, each of which can be removed.
         *
         * @return the photo field
         */
        private VBox buildPhotoSection() {
            addPhotosButton.getStyleClass().add("btn-outline");
            addPhotosButton.setOnAction(e -> choosePhotos());
            photoCount.setStyle(MUTED_STYLE);
            HBox top = new HBox(12, addPhotosButton, photoCount);
            top.setAlignment(Pos.CENTER_LEFT);
            photoBox.getChildren().setAll(top, photoPane);
            photoBox.setStyle(PHOTO_BOX_STYLE);
            Label label = new Label("Photos * (1 to " + CreateListingService.MAX_PHOTOS + ")");
            label.setStyle(FIELD_LABEL_STYLE);
            Label hint = new Label("PNG, JPEG, GIF or BMP, up to 10 MB each. The first photo is the cover.");
            hint.setStyle(MUTED_STYLE);
            return new VBox(6, label, photoBox, hint, errorLabelFor(ListingField.PHOTOS));
        }

        /**
         * Builds a form field: its label, its content, an optional hint and its error label.
         *
         * @param labelText   text of the label
         * @param field       the validated field
         * @param content     the node holding the inputs
         * @param hint        hint shown under the inputs, as a text or a node; {@code null} for none
         * @param fieldInputs the inputs highlighted when the field is invalid
         * @return the field
         */
        private VBox field(String labelText, ListingField field, Node content, Object hint, Control... fieldInputs) {
            Label label = new Label(labelText);
            label.setStyle(FIELD_LABEL_STYLE);
            VBox box = new VBox(6, label, content);
            if (hint instanceof Node node) {
                box.getChildren().add(node);
            } else if (hint instanceof String text) {
                Label hintLabel = new Label(text);
                hintLabel.setStyle(MUTED_STYLE);
                hintLabel.setWrapText(true);
                box.getChildren().add(hintLabel);
            }
            box.getChildren().add(errorLabelFor(field));
            inputs.put(field, List.of(fieldInputs));
            return box;
        }

        /**
         * Creates the hidden error label of a field.
         *
         * @param field the field
         * @return the label
         */
        private Label errorLabelFor(ListingField field) {
            Label label = dialogErrorLabel();
            label.setId("listingError" + field.name());
            errorLabels.put(field, label);
            return label;
        }

        /**
         * Creates a character counter for a text input, red when the text is too long.
         *
         * @param input     the text input
         * @param maxLength the longest text allowed, in characters
         * @return the counter
         */
        private Label counter(TextInputControl input, int maxLength) {
            Label label = new Label("0 / " + maxLength);
            label.setStyle(MUTED_STYLE);
            input.textProperty().addListener((observable, oldText, newText) -> {
                String trimmed = newText == null ? "" : newText.trim();
                int count = trimmed.codePointCount(0, trimmed.length());
                label.setText(count + " / " + maxLength);
                label.setStyle(count > maxLength ? ERROR_STYLE : MUTED_STYLE);
            });
            return label;
        }

        /**
         * Returns the hint of the meet-up field, which says when it was preselected.
         *
         * @return the hint, {@code null} if the Seller has no preferred meet-up location
         */
        private String meetupHint() {
            return seller.getPreferredMeetups().isEmpty() ? null : "Preselected from your preferred meet-up locations.";
        }

        /**
         * Preselects the Seller's first preferred meet-up location, chosen at registration.
         */
        private void preselectMeetup() {
            List<MeetupLocation> preferred = seller.getPreferredMeetups();
            if (!preferred.isEmpty()) {
                meetupBox.setValue(preferred.get(0));
            }
        }

        /**
         * Gives the inputs identifiers, so that they can be found in the scene.
         */
        private void setIds() {
            titleField.setId("listingTitleField");
            descriptionArea.setId("listingDescriptionArea");
            categoryBox.setId("listingCategoryBox");
            conditionBox.setId("listingConditionBox");
            priceField.setId("listingPriceField");
            lendingAmountField.setId("listingLendingAmountField");
            lendingUnitBox.setId("listingLendingUnitBox");
            meetupBox.setId("listingMeetupBox");
            addPhotosButton.setId("listingAddPhotosButton");
            submitButton.setId("listingSubmitButton");
            formError.setId("listingFormError");
        }

        /**
         * Removes the highlight of a field as soon as the Seller changes it.
         */
        private void clearErrorsOnEdit() {
            titleField.textProperty().addListener((o, a, b) -> clearError(ListingField.TITLE));
            descriptionArea.textProperty().addListener((o, a, b) -> clearError(ListingField.DESCRIPTION));
            categoryBox.valueProperty().addListener((o, a, b) -> clearError(ListingField.CATEGORY));
            conditionBox.valueProperty().addListener((o, a, b) -> clearError(ListingField.CONDITION));
            priceField.textProperty().addListener((o, a, b) -> clearError(ListingField.PRICE));
            lendingAmountField.textProperty().addListener((o, a, b) -> clearError(ListingField.LENDING_DURATION));
            lendingUnitBox.valueProperty().addListener((o, a, b) -> clearError(ListingField.LENDING_DURATION));
            meetupBox.valueProperty().addListener((o, a, b) -> clearError(ListingField.MEETUP_LOCATION));
        }

        /**
         * Shows the form of the chosen type (step 3). Changing the type discards the price or
         * the lending duration that no longer applies (alternative flow A4).
         *
         * @param type the chosen listing type
         */
        private void applyType(ListingType type) {
            boolean firstChoice = !detailsCard.isVisible();
            setShown(detailsCard, true);
            setShown(priceSection, type == ListingType.SELL);
            setShown(lendingSection, type == ListingType.LEND);
            setShown(giveawayNote, type == ListingType.GIVEAWAY);
            if (type != ListingType.SELL) {
                priceField.clear();
                clearError(ListingField.PRICE);
            }
            if (type != ListingType.LEND) {
                lendingAmountField.clear();
                lendingUnitBox.setValue(LendingDurationUnit.DAYS);
                clearError(ListingField.LENDING_DURATION);
            }
            clearError(ListingField.TYPE);
            if (firstChoice) {
                titleField.requestFocus();
            }
        }

        /**
         * Opens the photo chooser (step 4) and adds the chosen files.
         */
        private void choosePhotos() {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose photos of the item");
            List<String> patterns = new ArrayList<>();
            for (String extension : CreateListingService.PHOTO_EXTENSIONS) {
                patterns.add("*." + extension);
                patterns.add("*." + extension.toUpperCase(Locale.ROOT));
            }
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images (PNG, JPEG, GIF, BMP)", patterns));
            if (lastPhotoFolder != null && lastPhotoFolder.isDirectory()) {
                chooser.setInitialDirectory(lastPhotoFolder);
            }
            List<File> files = chooser.showOpenMultipleDialog(SceneManager.getPrimaryStage());
            if (files == null || files.isEmpty()) {
                return;
            }
            lastPhotoFolder = files.get(0).getParentFile();
            addPhotoFiles(files.stream().map(File::toPath).toList());
        }

        /**
         * Adds image files to the photos of the form. The files that are not valid images or
         * that exceed the 6 photo limit are not added, and the Seller is told why
         * (alternative flow A3).
         *
         * @param files the chosen files
         */
        void addPhotoFiles(List<Path> files) {
            PhotoSelectionResult result = CreateListingService.addPhotos(photos, files);
            photos.clear();
            photos.addAll(result.getPhotos());
            refreshPhotos();
            if (result.hasRejections()) {
                markInvalid(ListingField.PHOTOS, result.getRejections());
            } else {
                clearError(ListingField.PHOTOS);
            }
        }

        /**
         * Shows the thumbnails of the chosen photos and their count.
         */
        private void refreshPhotos() {
            photoPane.getChildren().clear();
            for (int i = 0; i < photos.size(); i++) {
                photoPane.getChildren().add(photoTile(photos.get(i), i == 0));
            }
            if (photos.isEmpty()) {
                Label none = new Label("No photo yet. Add at least one photo of the item.");
                none.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
                photoPane.getChildren().add(none);
            }
            photoCount.setText(photos.size() + " / " + CreateListingService.MAX_PHOTOS + " photos");
            addPhotosButton.setDisable(photos.size() >= CreateListingService.MAX_PHOTOS);
        }

        /**
         * Builds the thumbnail of a photo, with a button that removes it.
         *
         * @param photo the image file
         * @param cover {@code true} for the first photo, shown as the cover of the listing
         * @return the thumbnail
         */
        private StackPane photoTile(Path photo, boolean cover) {
            Node picture;
            if (Files.isRegularFile(photo)) {
                ImageView view = new ImageView(new Image(photo.toUri().toString(),
                        THUMBNAIL_SIZE, THUMBNAIL_SIZE, true, true, true));
                view.setFitWidth(THUMBNAIL_SIZE);
                view.setFitHeight(THUMBNAIL_SIZE);
                view.setPreserveRatio(true);
                picture = view;
            } else {
                // A photo of an edited listing whose file was moved or deleted (US-S04).
                Label missing = new Label("File not\nfound");
                missing.setStyle("-fx-text-fill: #b91c1c; -fx-font-size: 11px; -fx-text-alignment: center;");
                picture = missing;
            }
            StackPane tile = new StackPane(picture);
            tile.setPrefSize(THUMBNAIL_SIZE + 8, THUMBNAIL_SIZE + 8);
            tile.setMaxSize(THUMBNAIL_SIZE + 8, THUMBNAIL_SIZE + 8);
            tile.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: "
                    + (cover ? "#A68137" : "#e2e8f0") + "; -fx-border-radius: 8; -fx-border-width: " + (cover ? 2 : 1) + ";");
            Tooltip.install(tile, new Tooltip(String.valueOf(photo.getFileName())));

            Button remove = new Button("✕");
            remove.setStyle("-fx-background-color: rgba(15,23,42,0.75); -fx-text-fill: white; -fx-font-size: 10px;"
                    + " -fx-background-radius: 10; -fx-padding: 2 6; -fx-cursor: hand;");
            remove.setOnAction(e -> {
                photos.remove(photo);
                refreshPhotos();
                clearError(ListingField.PHOTOS);
            });
            StackPane.setAlignment(remove, Pos.TOP_RIGHT);
            StackPane.setMargin(remove, new Insets(4));
            tile.getChildren().add(remove);
            if (cover) {
                Label badge = new Label("Cover");
                badge.setStyle("-fx-background-color: #A68137; -fx-text-fill: white; -fx-font-size: 10px;"
                        + " -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 1 6;");
                StackPane.setAlignment(badge, Pos.BOTTOM_LEFT);
                StackPane.setMargin(badge, new Insets(4));
                tile.getChildren().add(badge);
            }
            return tile;
        }

        /**
         * Submits the listing (step 7). On success the Seller is told that it was sent for
         * review (step 10) and sees the My Listings screen; otherwise every invalid field is
         * highlighted with its reason and the first one gets the focus.
         */
        private void submit() {
            clearAllErrors();
            submitButton.setDisable(true);
            if (editing != null) {
                submitEdit();
                return;
            }
            CreateListingResult result;
            try {
                result = createListingService.createListing(toRequest());
            } catch (SecurityException e) {
                endSellerSession();
                return;
            }
            if (!result.isSuccess()) {
                submitButton.setDisable(false);
                result.getErrors().forEach(this::markInvalid);
                showError(formError, result.getMessage());
                focus(result.getFirstInvalidField());
                return;
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    result.getMessage() + "\n\nStatus: Pending Review", ButtonType.OK);
            alert.initOwner(SceneManager.getPrimaryStage());
            alert.setTitle("Create Listing");
            alert.setHeaderText("Listing submitted for review");
            alert.showAndWait();
            showMyListings();
        }

        /**
         * Saves the changes to the listing being edited (use case 2.4, subflow S1, steps 3 to
         * 6). If the listing has open offers, the Seller is warned and asked to confirm again
         * (alternative flow A3). On success, or if the listing can no longer be edited (A2),
         * the Seller is told and sees the My Listings screen; if some fields are invalid (A4),
         * they are highlighted with their reason and the first one gets the focus.
         */
        private void submitEdit() {
            CreateListingRequest request = toRequest();
            MyListingResult result = runListingChange(
                    confirmed -> myListingsService.editListing(editing.getId(), request, confirmed), "Save changes");
            if (result == null) {
                return;
            }
            if (result.getStatus() == MyListingStatus.INVALID_FIELDS) {
                submitButton.setDisable(false);
                result.getErrors().forEach(this::markInvalid);
                showError(formError, result.getMessage());
                focus(result.getFirstInvalidField());
                return;
            }
            showListingResult(result, "Listing updated");
            showMyListings();
        }

        /**
         * Cancels the listing (use case 2.3, alternative flow A5) or the edit (use case 2.4,
         * alternative flow A5): after a confirmation if something was entered or changed, the
         * My Listings screen is shown and nothing is saved.
         */
        private void cancel() {
            if (editing == null) {
                if (hasInput() && !confirm("Cancel listing", "Discard this listing?", "Nothing will be saved.")) {
                    return;
                }
            } else if (!MyListingsService.isUnchanged(editing, toRequest())
                    && !confirm("Cancel editing", "Discard your changes?", "The listing stays as it was.")) {
                return;
            }
            showMyListings();
        }

        /**
         * Tells whether the Seller has entered something that cancelling would lose.
         *
         * @return {@code true} if a text, a choice or a photo was entered
         */
        private boolean hasInput() {
            return !titleField.getText().isBlank() || !descriptionArea.getText().isBlank()
                    || categoryBox.getValue() != null || conditionBox.getValue() != null
                    || !priceField.getText().isBlank() || !lendingAmountField.getText().isBlank()
                    || !photos.isEmpty();
        }

        /**
         * Reads the form.
         *
         * @return the values entered by the Seller
         */
        private CreateListingRequest toRequest() {
            Toggle chosen = typeGroup.getSelectedToggle();
            return new CreateListingRequest(chosen == null ? null : (ListingType) chosen.getUserData(),
                    titleField.getText(), descriptionArea.getText(), categoryBox.getValue(), conditionBox.getValue(),
                    priceField.getText(), lendingAmountField.getText(), lendingUnitBox.getValue(),
                    meetupBox.getValue(), photos);
        }

        /**
         * Highlights a field and shows its error messages.
         *
         * @param field    the invalid field
         * @param messages its error messages
         */
        private void markInvalid(ListingField field, List<String> messages) {
            for (Control input : inputs.getOrDefault(field, List.of())) {
                if (!input.getStyleClass().contains(FIELD_ERROR_CLASS)) {
                    input.getStyleClass().add(FIELD_ERROR_CLASS);
                }
            }
            if (field == ListingField.PHOTOS) {
                photoBox.setStyle(PHOTO_BOX_ERROR_STYLE);
            }
            showError(errorLabels.get(field), String.join("\n", messages));
        }

        /**
         * Removes the highlight and the error messages of a field, and the error summary once
         * no field is invalid any more.
         *
         * @param field the field
         */
        private void clearError(ListingField field) {
            for (Control input : inputs.getOrDefault(field, List.of())) {
                input.getStyleClass().removeAll(FIELD_ERROR_CLASS);
            }
            if (field == ListingField.PHOTOS) {
                photoBox.setStyle(PHOTO_BOX_STYLE);
            }
            hideError(errorLabels.get(field));
            if (errorLabels.values().stream().noneMatch(Node::isVisible)) {
                hideError(formError);
            }
        }

        /**
         * Removes every highlight and error message.
         */
        private void clearAllErrors() {
            for (ListingField field : ListingField.values()) {
                clearError(field);
            }
        }

        /**
         * Gives the focus to the first input of a field and scrolls to it.
         *
         * @param field the field, {@code null} to do nothing
         */
        private void focus(ListingField field) {
            if (field == null) {
                return;
            }
            List<Control> fieldInputs = inputs.getOrDefault(field, List.of());
            Control target = field == ListingField.PHOTOS ? addPhotosButton
                    : fieldInputs.isEmpty() ? null : fieldInputs.get(0);
            Node anchor = target != null ? target : errorLabels.get(field);
            if (target != null) {
                target.requestFocus();
            }
            scrollTo(anchor);
        }

        /**
         * Scrolls the form so that a node is visible, with its label above it.
         *
         * @param node the node
         */
        private void scrollTo(Node node) {
            page.applyCss();
            page.layout();
            double contentHeight = page.getBoundsInLocal().getHeight();
            double viewportHeight = scroll.getViewportBounds().getHeight();
            if (node == null || contentHeight <= viewportHeight) {
                return;
            }
            Bounds nodeBounds = page.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
            double target = (nodeBounds.getMinY() - 60) / (contentHeight - viewportHeight);
            scroll.setVvalue(Math.max(0, Math.min(1, target)));
        }
    }

    /**
     * Shows or hides a node, and frees its space when it is hidden.
     *
     * @param node  the node
     * @param shown {@code true} to show it
     */
    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    // =====================================================================
    // US-S08: Seller Stats screen (use case 2.8 "View Seller Statistics")
    // =====================================================================

    /** Colour of the views: metric card, bars and legend (HKUST blue, also set in {@code styles.css}). */
    private static final String VIEWS_COLOR = "#003974";
    /** Colour of the offers received: metric card, bars and legend (HKUST gold, also set in {@code styles.css}). */
    private static final String OFFERS_COLOR = "#A68137";
    /** Colour of the completed deals metric card. */
    private static final String DEALS_COLOR = "#2e7d32";
    /** Colour of each slice of the pie chart, by type of deal. */
    private static final Map<DealType, String> OUTCOME_COLORS = new EnumMap<>(Map.of(
            DealType.SALE, "#003974", DealType.LOAN, "#A68137", DealType.GIVEAWAY, "#2e7d32"));
    /** Style class giving the bars of the statistics chart their colours (defined in {@code styles.css}). */
    private static final String STATS_CHART_CLASS = "seller-stats-chart";
    /** Format of the time the statistics were computed. */
    private static final DateTimeFormatter STATS_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
    /** Width, in pixels, given to each listing on the bar chart; with many listings, the chart scrolls. */
    private static final double BAR_GROUP_WIDTH = 90;
    /** Narrowest bar chart, in pixels. */
    private static final double MIN_BAR_CHART_WIDTH = 420;
    /** Height, in pixels, of the charts. */
    private static final double CHART_HEIGHT = 320;
    /** Widest bar of the bar chart, in pixels, so that a few listings do not get huge bars. */
    private static final double MAX_BAR_WIDTH = 46;
    /** Smallest gap, in pixels, between the groups of bars of two listings. */
    private static final double MIN_CATEGORY_GAP = 18;
    /**
     * Angle, in degrees, of the listing labels under the bar chart: steep enough for the label of
     * every listing to fit (the chart hides labels that overlap), while staying readable.
     */
    private static final double STATS_LABEL_ROTATION = -60;
    /** Style of the heading of a statistics card. */
    private static final String STATS_HEADING_STYLE = "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;";

    /**
     * Shows the Seller's statistics (use case 2.8, US-S08): the total views of their listings,
     * the offers received, the completed deals and the average rating (step 3), a bar chart of
     * the views and offers received for each listing (step 4), a pie chart of the completed
     * deals by type (step 5), and a breakdown per listing. The values are computed from the
     * live data each time the screen is opened or refreshed.
     *
     * <p>Without listings, the values are zero and a message replaces the charts (alternative
     * flow A1). Without completed deals, a message replaces the pie chart (alternative flow
     * A2). Without an active Seller session, the seller login screen is shown instead.</p>
     */
    @FXML
    private void showStats() {
        SellerStatistics stats;
        try {
            stats = statisticsService.getMyStatistics();
            // Computing the statistics may auto-return an expired loan, which notifies the Seller.
            updateOffersButton(offerService.getPendingOffers().size());
            updateNotificationsButton(notificationService.getUnreadCount());
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        setActive(btnStats, "Seller Stats");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));
        root.getChildren().addAll(statsHeader(stats), statsMetrics(stats));
        if (!stats.hasListings()) {
            // A1: zero values, and a message in place of the charts.
            VBox noListings = emptyStateCard("📊", "No listings yet",
                    "Your statistics appear here once you post a listing: how many buyers viewed it,"
                            + " the offers it received and the deals you completed.");
            noListings.setId("statsNoListings");
            root.getChildren().add(noListings);
        } else {
            HBox charts = new HBox(16, viewsAndOffersCard(stats), completedDealsCard(stats));
            root.getChildren().addAll(charts, statsBreakdown(stats));
        }
        root.getChildren().add(statsFootnote());

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /**
     * Builds the line above the statistics: how many listings they cover, when they were
     * computed, and the Refresh button that computes them again.
     *
     * @param stats the statistics
     * @return the header
     */
    private HBox statsHeader(SellerStatistics stats) {
        int count = stats.getListingCount();
        String covered = count == 0 ? "You have no listings yet"
                : "Statistics of your " + count + (count == 1 ? " listing" : " listings");
        Label summary = new Label(covered + "  ·  Updated at " + stats.getComputedAt().format(STATS_TIME_FORMAT));
        summary.setId("statsSummary");
        summary.setStyle(MUTED_STYLE);
        summary.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(summary, Priority.ALWAYS);
        Button refresh = smallButton("Refresh", "btn-outline", this::showStats);
        refresh.setId("statsRefreshButton");
        HBox header = new HBox(12, summary, refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /**
     * Builds the four values of step 3: total views, offers received, completed deals and
     * average rating ("No ratings yet" when the Seller has no ratings).
     *
     * @param stats the statistics
     * @return the row of metric cards
     */
    private static HBox statsMetrics(SellerStatistics stats) {
        SellerRatingInfo rating = stats.getRating();
        int ratingCount = rating.getRatingCount();
        VBox ratingCard = rating.hasRatings()
                ? statsMetricCard("statsAverageRating", "Average rating",
                        String.format(Locale.ENGLISH, "%.1f ★", rating.getAverageRating()),
                        "From " + ratingCount + (ratingCount == 1 ? " rating" : " ratings"), STAR_COLOR, false)
                : statsMetricCard("statsAverageRating", "Average rating", SellerRatingInfo.NO_RATINGS_TEXT,
                        "Buyers rate you after a completed deal", STAR_COLOR, true);
        return new HBox(16,
                statsMetricCard("statsTotalViews", "Total views", String.valueOf(stats.getTotalViews()),
                        "Times buyers opened your listings", VIEWS_COLOR, false),
                statsMetricCard("statsOffersReceived", "Offers received", String.valueOf(stats.getOffersReceived()),
                        "Offers, borrow requests and claims", OFFERS_COLOR, false),
                statsMetricCard("statsCompletedDeals", "Completed deals", String.valueOf(stats.getCompletedDeals()),
                        "Sold, given, or lent and returned", DEALS_COLOR, false),
                ratingCard);
    }

    /**
     * Builds one metric card: a value in large, its name and a short explanation.
     *
     * @param valueId    identifier of the value label
     * @param name       name of the value
     * @param value      the value
     * @param caption    short explanation of the value
     * @param color      colour of the value
     * @param smallValue {@code true} for a value written in words, shown smaller
     * @return the card
     */
    private static VBox statsMetricCard(String valueId, String name, String value, String caption,
                                        String color, boolean smallValue) {
        Label valueLabel = new Label(value);
        valueLabel.setId(valueId);
        valueLabel.setStyle("-fx-font-size: " + (smallValue ? 18 : 28) + "px; -fx-font-weight: bold;"
                + " -fx-text-fill: " + color + ";");
        valueLabel.setMinHeight(38);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");
        Label captionLabel = new Label(caption);
        captionLabel.setStyle(MUTED_STYLE);
        captionLabel.setWrapText(true);
        VBox card = new VBox(4, valueLabel, nameLabel, captionLabel);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        card.setMinWidth(150);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /**
     * Builds the card of the bar chart of step 4: the views and the offers received for each
     * listing, in the order of My Listings. Hovering over a bar shows the full title of the
     * listing; with many listings, the chart scrolls sideways.
     *
     * @param stats the statistics, with at least one listing
     * @return the card
     */
    private static VBox viewsAndOffersCard(SellerStatistics stats) {
        List<SellerListingStatistics> listings = stats.getListings();
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setAnimated(false);
        xAxis.setLabel("Your listings (newest first)");
        xAxis.setTickLabelRotation(STATS_LABEL_ROTATION);
        BarChart<String, Number> chart = new BarChart<>(xAxis, countAxis(stats.getMaxListingValue()));
        chart.setId("statsBarChart");
        chart.getStyleClass().add(STATS_CHART_CLASS);
        chart.setAnimated(false);
        // The legend is drawn above the chart, so that it stays visible when the chart scrolls.
        chart.setLegendVisible(false);
        chart.setBarGap(3);
        // Bars stay narrow with few listings: the space left in each group becomes the gap.
        chart.widthProperty().addListener((observable, oldWidth, chartWidth) -> chart.setCategoryGap(
                Math.max(MIN_CATEGORY_GAP, chartWidth.doubleValue() * 0.85 / listings.size() - 2 * MAX_BAR_WIDTH)));

        XYChart.Series<String, Number> views = new XYChart.Series<>();
        views.setName("Views");
        XYChart.Series<String, Number> offers = new XYChart.Series<>();
        offers.setName("Offers received");
        for (SellerListingStatistics listing : listings) {
            views.getData().add(bar(listing.chartLabel(), listing.views(),
                    listing.title() + ": " + plural(listing.views(), "view")));
            offers.getData().add(bar(listing.chartLabel(), listing.offersReceived(),
                    listing.title() + ": " + plural(listing.offersReceived(), "offer") + " received"));
        }
        chart.getData().addAll(List.of(views, offers));

        // The chart fills the card when there is room, and scrolls when the listings need more.
        double width = Math.max(MIN_BAR_CHART_WIDTH, listings.size() * BAR_GROUP_WIDTH);
        chart.setMinWidth(width);
        chart.setPrefWidth(width);
        chart.setPrefHeight(CHART_HEIGHT);
        ScrollPane chartScroll = new ScrollPane(chart);
        chartScroll.setFitToHeight(true);
        chartScroll.setPrefViewportHeight(CHART_HEIGHT);
        chartScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chartScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chartScroll.setStyle("-fx-background-color: transparent; -fx-background: white;");
        chartScroll.viewportBoundsProperty().addListener((observable, oldBounds, bounds) ->
                chartScroll.setFitToWidth(bounds.getWidth() >= width));

        Label heading = new Label("Views and offers received per listing");
        heading.setStyle(STATS_HEADING_STYLE);
        HBox legend = new HBox(16, legendEntry(VIEWS_COLOR, "Views"), legendEntry(OFFERS_COLOR, "Offers received"));
        legend.setId("statsBarLegend");
        legend.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label("Hover over a bar to see the full title of the listing.");
        hint.setStyle(MUTED_STYLE);
        VBox card = new VBox(10, heading, legend, chartScroll, hint);
        card.setId("statsViewsOffersCard");
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        card.setPrefWidth(560);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /**
     * Builds one bar of the bar chart, with a tooltip.
     *
     * @param label   label of the listing on the chart
     * @param value   height of the bar
     * @param tooltip text shown when hovering over the bar
     * @return the data of the bar
     */
    private static XYChart.Data<String, Number> bar(String label, int value, String tooltip) {
        XYChart.Data<String, Number> data = new XYChart.Data<>(label, value);
        // The chart styles and places the node given here, so the tooltip stays on the bar.
        StackPane node = new StackPane();
        Tooltip.install(node, new Tooltip(tooltip));
        data.setNode(node);
        return data;
    }

    /**
     * Builds the vertical axis of the bar chart, which shows whole numbers only.
     *
     * @param max the highest value drawn on the chart
     * @return the axis, from zero to a little above {@code max}
     */
    private static NumberAxis countAxis(int max) {
        int tick = Math.max(1, (int) Math.ceil(max / 8.0));
        int upper = Math.max(4 * tick, (max / tick + 1) * tick);
        NumberAxis axis = new NumberAxis(0, upper, tick);
        axis.setAnimated(false);
        axis.setMinorTickVisible(false);
        axis.setLabel("Count");
        axis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return String.valueOf(value.intValue());
            }

            @Override
            public Number fromString(String text) {
                return Integer.valueOf(text.trim());
            }
        });
        return axis;
    }

    /**
     * Builds the card of the pie chart of step 5: the completed deals by type (Sold, Lent,
     * Given), with a legend giving the number and share of each type. Without completed deals,
     * a message is shown in place of the pie chart (alternative flow A2).
     *
     * @param stats the statistics
     * @return the card
     */
    private static VBox completedDealsCard(SellerStatistics stats) {
        Label heading = new Label("Completed deals by type");
        heading.setStyle(STATS_HEADING_STYLE);
        VBox card = new VBox(10, heading);
        card.setId("statsCompletedDealsCard");
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        card.setPrefWidth(360);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);

        if (!stats.hasCompletedDeals()) {
            // A2: a message in place of the pie chart.
            Label icon = new Label("🤝");
            icon.setStyle("-fx-font-size: 32px;");
            Label title = new Label("No completed deals yet");
            title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #003974;");
            Label text = new Label("A deal counts here once the buyer marks it complete, or once a borrowed item"
                    + " is returned.");
            text.setStyle(MUTED_STYLE);
            text.setWrapText(true);
            text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            VBox message = new VBox(8, icon, title, text);
            message.setId("statsNoCompletedDeals");
            message.setAlignment(Pos.CENTER);
            message.setPadding(new Insets(30, 10, 30, 10));
            VBox.setVgrow(message, Priority.ALWAYS);
            card.getChildren().add(message);
            return card;
        }

        int total = stats.getCompletedDeals();
        PieChart pie = new PieChart();
        pie.setId("statsPieChart");
        pie.setAnimated(false);
        pie.setLegendVisible(false);
        pie.setLabelsVisible(true);
        pie.setStartAngle(90);
        pie.setPrefHeight(CHART_HEIGHT - 50);
        HBox legend = new HBox(14);
        legend.setAlignment(Pos.CENTER);
        for (DealType type : DealType.values()) {
            int count = stats.getCompletedDeals(type);
            String outcome = SellerStatistics.outcomeName(type);
            String share = Math.round(100.0 * count / total) + "%";
            String color = OUTCOME_COLORS.get(type);
            if (count > 0) {
                PieChart.Data slice = new PieChart.Data(outcome + " (" + count + ")", count);
                pie.getData().add(slice);
                if (slice.getNode() != null) {
                    slice.getNode().setStyle("-fx-pie-color: " + color + ";");
                    Tooltip.install(slice.getNode(), new Tooltip(outcome + ": " + plural(count, "deal") + " (" + share + ")"));
                }
            }
            HBox entry = legendEntry(color, outcome + ": " + count + " (" + share + ")");
            entry.setId("statsDeals" + outcome);
            legend.getChildren().add(entry);
        }
        card.getChildren().addAll(pie, legend);
        return card;
    }

    /**
     * Builds one entry of a chart legend: a coloured square and its text.
     *
     * @param color colour of the square
     * @param text  text of the entry
     * @return the entry
     */
    private static HBox legendEntry(String color, String text) {
        Region swatch = new Region();
        swatch.setMinSize(12, 12);
        swatch.setMaxSize(12, 12);
        swatch.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 12px;");
        HBox entry = new HBox(6, swatch, label);
        entry.setAlignment(Pos.CENTER_LEFT);
        return entry;
    }

    /**
     * Builds the breakdown per listing: the title, status, views, offers received and completed
     * deals of each listing, with the totals, so that the Seller can read the exact values drawn
     * on the charts.
     *
     * @param stats the statistics, with at least one listing
     * @return the card
     */
    private static VBox statsBreakdown(SellerStatistics stats) {
        GridPane grid = new GridPane();
        grid.setId("statsBreakdownGrid");
        grid.setHgap(18);
        grid.setVgap(8);
        ColumnConstraints titleColumn = new ColumnConstraints();
        titleColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(titleColumn);
        for (int column = 1; column < 5; column++) {
            ColumnConstraints numberColumn = new ColumnConstraints();
            numberColumn.setMinWidth(Region.USE_PREF_SIZE);
            numberColumn.setHalignment(column == 1 ? javafx.geometry.HPos.LEFT : javafx.geometry.HPos.RIGHT);
            grid.getColumnConstraints().add(numberColumn);
        }
        String[] headers = {"Listing", "Status", "Views", "Offers received", "Completed deals"};
        for (int column = 0; column < headers.length; column++) {
            Label header = new Label(headers[column]);
            header.setStyle("-fx-font-weight: bold; -fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
            grid.add(header, column, 0);
        }
        int row = 1;
        for (SellerListingStatistics listing : stats.getListings()) {
            Label title = new Label(listing.title());
            title.setWrapText(true);
            title.setStyle("-fx-text-fill: #1e293b;");
            grid.add(title, 0, row);
            grid.add(statsStatusBadge(listing.status()), 1, row);
            grid.add(statsNumber(listing.views(), false), 2, row);
            grid.add(statsNumber(listing.offersReceived(), false), 3, row);
            grid.add(statsNumber(listing.completedDeals(), false), 4, row);
            row++;
        }
        Label total = new Label("Total");
        total.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");
        grid.add(total, 0, row);
        grid.add(statsNumber(stats.getTotalViews(), true), 2, row);
        grid.add(statsNumber(stats.getOffersReceived(), true), 3, row);
        grid.add(statsNumber(stats.getCompletedDeals(), true), 4, row);

        Label heading = new Label("Breakdown per listing");
        heading.setStyle(STATS_HEADING_STYLE);
        VBox card = new VBox(12, heading, grid);
        card.setId("statsBreakdown");
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        return card;
    }

    /**
     * Builds a number of the breakdown.
     *
     * @param value the number
     * @param total {@code true} for a total, shown in bold
     * @return the label
     */
    private static Label statsNumber(int value, boolean total) {
        Label label = new Label(String.valueOf(value));
        label.setStyle(total ? "-fx-font-weight: bold; -fx-text-fill: #003974;" : "-fx-text-fill: #1e293b;");
        return label;
    }

    /**
     * Builds the status badge of a listing in the breakdown, named as on My Listings.
     *
     * @param status the status of the listing
     * @return the badge
     */
    private static Label statsStatusBadge(ListingStatus status) {
        Label badge = new Label(statusName(status));
        badge.getStyleClass().addAll("badge", switch (status) {
            case LIVE -> "badge-live";
            case PENDING_REVIEW -> "badge-pending";
            case REJECTED, REMOVED -> "badge-rejected";
            default -> "badge-sold";
        });
        return badge;
    }

    /**
     * Builds the note explaining how the statistics are counted.
     *
     * @return the note
     */
    private static Label statsFootnote() {
        Label note = new Label("Views count each time a buyer opens the detail page of a listing. Offers received"
                + " include every offer, borrow request and claim, whatever its status; your counter offers are not"
                + " counted. A deal is completed once the buyer marks it complete, or once a borrowed item is"
                + " returned. Ratings hidden by the admin are not counted, and listings you deleted are not included.");
        note.setId("statsFootnote");
        note.setStyle(MUTED_STYLE);
        note.setWrapText(true);
        return note;
    }

    /**
     * Writes a count with its unit, in the singular or plural.
     *
     * @param count the count
     * @param unit  the unit in the singular
     * @return for example {@code 1 view} or {@code 3 views}
     */
    private static String plural(int count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }

    // =====================================================================
    // US-S09: Reviews & Feedback screen (use case 2.9 "Handle Reviews")
    // =====================================================================

    /** Format of the date of a review. */
    private static final DateTimeFormatter REVIEW_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    /** Colour of the stars of a rating. */
    private static final String STAR_COLOR = "#A68137";
    /** Style of the Seller's reply under a review. */
    private static final String REPLY_STYLE = "-fx-background-color: #f4f7fb; -fx-background-radius: 8;"
            + " -fx-border-color: transparent transparent transparent #003974; -fx-border-width: 0 0 0 3;"
            + " -fx-padding: 8 10;";

    /**
     * Shows the Seller's reviews (the Reviews &amp; Feedback button of the sidebar). See
     * {@link #showReviews(String)}.
     */
    @FXML
    private void showReviews() {
        showReviews(null);
    }

    /**
     * Shows the Seller's average rating and their reviews, newest first, each with the buyer,
     * the listing, the stars, the written review, the date, the Seller's reply and whether it
     * is flagged (basic flow, step 2), with its Reply and Flag buttons. Reviews hidden by the
     * admin are neither shown nor counted. Without reviews, the screen says so (alternative
     * flow A1). Without an active Seller session, the seller login screen is shown instead.
     *
     * @param focusReviewId review to scroll to and highlight, for example the one a
     *                      notification is about (use case 2.7), {@code null} for none
     */
    private void showReviews(String focusReviewId) {
        List<Review> reviews;
        SellerRatingInfo rating;
        try {
            reviews = reviewService.getMyReviews();
            rating = reviewService.getMyRating();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        setActive(btnReviews, "Reviews & Feedback");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));
        root.getChildren().add(createRatingSummary(rating, reviews));
        if (reviews.isEmpty()) {
            root.getChildren().add(emptyStateCard("⭐", "No reviews yet",
                    "Buyers can rate you once a deal is completed. Their reviews will appear here."));
        } else {
            for (Review review : reviews) {
                root.getChildren().add(createReviewCard(review));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
        if (focusReviewId != null) {
            highlightNode(scroll, root, "review-" + focusReviewId);
        }
    }

    /**
     * Builds the card showing the Seller's average rating and how many reviews wait for a
     * reply or for the admin's decision.
     *
     * @param rating  the Seller's average rating over the visible reviews
     * @param reviews the visible reviews
     * @return the card
     */
    private HBox createRatingSummary(SellerRatingInfo rating, List<Review> reviews) {
        Label score = new Label(rating.hasRatings()
                ? String.format(Locale.ENGLISH, "%.1f", rating.getAverageRating()) : "–");
        score.setStyle("-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: " + STAR_COLOR + ";");

        Label stars = new Label(rating.hasRatings() ? starsOf((int) Math.round(rating.getAverageRating())) : "");
        stars.setStyle("-fx-text-fill: " + STAR_COLOR + "; -fx-font-size: 16px;");
        Label count = new Label(rating.hasRatings()
                ? "Average rating from " + rating.getRatingCount() + (rating.getRatingCount() == 1 ? " review" : " reviews")
                : SellerRatingInfo.NO_RATINGS_TEXT);
        count.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974;");

        long toReply = reviews.stream().filter(review -> !review.hasReply()).count();
        long flagged = reviews.stream().filter(Review::isFlagged).count();
        StringBuilder status = new StringBuilder();
        if (!reviews.isEmpty()) {
            status.append(toReply == 0 ? "You replied to every review" : toReply + " awaiting your reply");
            if (flagged > 0) {
                status.append(" · ").append(flagged).append(" flagged, awaiting the admin");
            }
            status.append(" · ");
        }
        status.append("Reviews hidden by the admin are not shown or counted.");
        Label details = new Label(status.toString());
        details.setStyle(MUTED_STYLE);
        details.setWrapText(true);

        VBox text = new VBox(2, stars, count, details);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox card = new HBox(16, score, text);
        card.setId("reviewsSummary");
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14));
        return card;
    }

    /**
     * Builds the card of one review: its stars, date, buyer, listing, written review, the
     * Seller's reply if any, a note if it is flagged, and the Reply and Flag buttons. A
     * button stays clickable once used, and then explains why the action is no longer
     * possible (alternative flows A3 and A4).
     *
     * @param review the review
     * @return the card
     */
    private VBox createReviewCard(Review review) {
        Label stars = new Label(starsOf(review.getRating()));
        stars.setStyle("-fx-text-fill: " + STAR_COLOR + "; -fx-font-size: 16px;");
        Label score = new Label(review.getRating() + " / 5");
        score.setStyle("-fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label date = new Label(review.getDate() != null ? review.getDate().format(REVIEW_DATE_FORMAT) : "");
        date.setStyle(MUTED_STYLE);
        HBox header = new HBox(8, stars, score, spacer, date);
        header.setAlignment(Pos.CENTER_LEFT);

        Label meta = new Label("by " + reviewerName(review) + " · " + reviewListingTitle(review));
        meta.setStyle(MUTED_STYLE);
        meta.setWrapText(true);

        Label comment = new Label(review.hasComment() ? review.getComment().trim() : "The buyer did not write a comment.");
        comment.setWrapText(true);
        comment.setStyle(review.hasComment() ? "-fx-font-size: 13px;" : MUTED_STYLE + " -fx-font-style: italic;");

        VBox card = new VBox(8, header, meta, comment);
        card.setId("review-" + review.getId());
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));

        if (review.isFlagged()) {
            Label flagNote = new Label("⚑ Flagged for the admin"
                    + (review.getFlagReason() != null && !review.getFlagReason().isBlank()
                    ? ": " + review.getFlagReason().trim() : "")
                    + ". The review stays visible until the admin decides.");
            flagNote.setStyle(NOTE_PENDING_STYLE);
            flagNote.setWrapText(true);
            flagNote.setMaxWidth(Double.MAX_VALUE);
            card.getChildren().add(flagNote);
        }
        if (review.hasReply()) {
            Label replyTitle = new Label("Your reply");
            replyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #003974;");
            Label replyText = new Label(review.getReply().trim());
            replyText.setWrapText(true);
            VBox replyBox = new VBox(3, replyTitle, replyText);
            replyBox.setStyle(REPLY_STYLE);
            card.getChildren().add(replyBox);
        }

        Button reply = smallButton(review.hasReply() ? "Replied" : "Reply",
                review.hasReply() ? "btn-outline" : "btn-primary", () -> handleReplyToReview(review));
        reply.setId("reviewReply-" + review.getId());
        Button flag = smallButton(review.isFlagged() ? "Flagged" : "Flag",
                review.isFlagged() ? "btn-outline" : "btn-danger", () -> handleFlagReview(review));
        flag.setId("reviewFlag-" + review.getId());
        HBox actions = new HBox(8, reply, flag);
        actions.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(actions);
        return card;
    }

    /**
     * Subflow S1, step "Begin Reply": checks that the review can still be answered, then opens
     * the reply dialog. If the Seller already replied (alternative flow A3), or the review can
     * no longer be handled, the Seller is told why and the screen is refreshed.
     *
     * @param review the review to answer
     */
    private void handleReplyToReview(Review review) {
        ReviewHandlingResult check;
        try {
            check = reviewService.checkCanReply(review.getId());
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        if (!check.isSuccess()) {
            showReviewResult(check, null);
            showReviews();
            return;
        }
        showReplyDialog(check.getReview());
    }

    /**
     * Asks the Seller for a reply and posts it (subflow S1). An empty or too long reply keeps
     * the dialog open with the reason (alternative flow A2); cancelling changes nothing
     * (alternative flow A5).
     *
     * @param review the review to answer
     */
    private void showReplyDialog(Review review) {
        Dialog<ButtonType> dialog = newDialog("Reply to review", "Reply to " + reviewerName(review));
        ButtonType postType = new ButtonType("Post Reply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(postType, ButtonType.CANCEL);

        TextArea replyArea = new TextArea();
        replyArea.setId("reviewReplyText");
        replyArea.setPromptText("Your reply to the buyer");
        replyArea.setPrefRowCount(4);
        replyArea.setWrapText(true);
        Label length = lengthCounter(replyArea, ReviewHandlingService.MAX_REPLY_LENGTH);
        Label error = dialogErrorLabel();
        replyArea.textProperty().addListener((observable, oldText, newText) -> hideError(error));
        Label note = new Label("The buyer will be notified. A review can have only one reply,"
                + " and it cannot be changed afterwards.");
        note.setStyle(MUTED_STYLE);
        note.setWrapText(true);

        VBox content = new VBox(8, reviewQuote(review), note, replyArea, length, error);
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);
        Platform.runLater(replyArea::requestFocus);

        runReviewDialog(dialog, postType, error,
                Set.of(ReviewHandlingStatus.EMPTY_REPLY, ReviewHandlingStatus.REPLY_TOO_LONG),
                () -> reviewService.replyToReview(review.getId(), replyArea.getText()), "Reply posted");
    }

    /**
     * Subflow S2, step "Begin Flag": checks that the review can still be flagged, then opens
     * the flag dialog. If the review is already flagged (alternative flow A4), or can no
     * longer be handled, the Seller is told why and the screen is refreshed.
     *
     * @param review the review to flag
     */
    private void handleFlagReview(Review review) {
        ReviewHandlingResult check;
        try {
            check = reviewService.checkCanFlag(review.getId());
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        if (!check.isSuccess()) {
            showReviewResult(check, null);
            showReviews();
            return;
        }
        showFlagDialog(check.getReview());
    }

    /**
     * Asks the Seller why the review is inappropriate and flags it for the admin (subflow S2).
     * A missing reason, or "Other" without details, keeps the dialog open with the reason;
     * cancelling changes nothing (alternative flow A5).
     *
     * @param review the review to flag
     */
    private void showFlagDialog(Review review) {
        Dialog<ButtonType> dialog = newDialog("Flag review", "Flag the review of " + reviewerName(review) + "?");
        ButtonType flagType = new ButtonType("Flag Review", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(flagType, ButtonType.CANCEL);

        ToggleGroup reasons = new ToggleGroup();
        VBox reasonBox = new VBox(6);
        for (ReviewFlagReason reason : ReviewFlagReason.values()) {
            RadioButton option = new RadioButton(reason.getDisplayName());
            option.setId("flagReason-" + reason.name());
            option.setUserData(reason);
            option.setToggleGroup(reasons);
            reasonBox.getChildren().add(option);
        }
        TextArea details = new TextArea();
        details.setId("flagDetailsText");
        details.setPromptText("Details for the admin (optional)");
        details.setPrefRowCount(3);
        details.setWrapText(true);
        Label length = lengthCounter(details, ReviewHandlingService.MAX_FLAG_DETAILS_LENGTH);
        Label error = dialogErrorLabel();
        details.textProperty().addListener((observable, oldText, newText) -> hideError(error));
        reasons.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            hideError(error);
            boolean required = newToggle != null && ((ReviewFlagReason) newToggle.getUserData()).isDetailsRequired();
            details.setPromptText(required ? "Explain why this review is inappropriate (required)"
                    : "Details for the admin (optional)");
        });
        Label note = new Label("The admin will be notified and will decide whether to hide the review."
                + " It stays visible until then.");
        note.setStyle(MUTED_STYLE);
        note.setWrapText(true);
        Label reasonTitle = new Label("Reason:");
        reasonTitle.setStyle("-fx-font-weight: bold;");

        VBox content = new VBox(8, reviewQuote(review), note, reasonTitle, reasonBox, details, length, error);
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);

        runReviewDialog(dialog, flagType, error, Set.of(ReviewHandlingStatus.INVALID_FLAG_REASON),
                () -> reviewService.flagReview(review.getId(), selectedFlagReason(reasons), details.getText()),
                "Review flagged");
    }

    /**
     * Shows a reply or flag dialog and runs its action when the Seller confirms. While the
     * input is invalid, the dialog stays open and shows why; any other outcome closes it, is
     * reported to the Seller, and refreshes the screen.
     *
     * @param dialog          the dialog
     * @param confirmType     the button that runs the action
     * @param errorLabel      the label showing why the input is invalid
     * @param invalidStatuses the statuses that mean the input must be corrected
     * @param attempt         runs the action
     * @param successTitle    title of the report when the action succeeds
     */
    private void runReviewDialog(Dialog<ButtonType> dialog, ButtonType confirmType, Label errorLabel,
                                 Set<ReviewHandlingStatus> invalidStatuses, Supplier<ReviewHandlingResult> attempt,
                                 String successTitle) {
        ReviewHandlingResult[] outcome = new ReviewHandlingResult[1];
        boolean[] sessionEnded = {false};
        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirmType);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            ReviewHandlingResult result;
            try {
                result = attempt.get();
            } catch (SecurityException e) {
                sessionEnded[0] = true;
                return;
            }
            if (invalidStatuses.contains(result.getStatus())) {
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
            showReviewResult(outcome[0], successTitle);
            showReviews();
        }
    }

    /**
     * Reports the outcome of an action on a review to the Seller. "Already replied" and
     * "already flagged" (alternative flows A3 and A4) are information, not errors.
     *
     * @param result       the outcome
     * @param successTitle title used when the action succeeded
     */
    private void showReviewResult(ReviewHandlingResult result, String successTitle) {
        ReviewHandlingStatus status = result.getStatus();
        boolean information = result.isSuccess() || status == ReviewHandlingStatus.ALREADY_REPLIED
                || status == ReviewHandlingStatus.ALREADY_FLAGGED;
        Alert alert = new Alert(information ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                result.getMessage(), ButtonType.OK);
        alert.initOwner(SceneManager.getPrimaryStage());
        alert.setTitle("Reviews & Feedback");
        alert.setHeaderText(switch (status) {
            case SUCCESS -> successTitle;
            case ALREADY_REPLIED -> "Already replied";
            case ALREADY_FLAGGED -> "Awaiting the admin's decision";
            default -> "This review could not be handled";
        });
        alert.showAndWait();
    }

    /**
     * Builds the reminder of a review shown at the top of the reply and flag dialogs.
     *
     * @param review the review
     * @return the reminder
     */
    private static VBox reviewQuote(Review review) {
        Label stars = new Label(starsOf(review.getRating()) + "  " + reviewListingTitle(review));
        stars.setStyle("-fx-text-fill: " + STAR_COLOR + "; -fx-font-weight: bold;");
        Label comment = new Label(review.hasComment() ? "“" + review.getComment().trim() + "”"
                : "The buyer did not write a comment.");
        comment.setWrapText(true);
        comment.setStyle(MUTED_STYLE);
        VBox quote = new VBox(4, stars, comment);
        quote.setStyle(REPLY_STYLE);
        return quote;
    }

    /**
     * Creates a label counting the characters of a text area against its maximum, which
     * turns red when the text is too long.
     *
     * @param input     the text area
     * @param maxLength the maximum number of characters
     * @return the counter label
     */
    private static Label lengthCounter(TextInputControl input, int maxLength) {
        Label counter = new Label("0 / " + maxLength);
        counter.setStyle(MUTED_STYLE);
        input.textProperty().addListener((observable, oldText, newText) -> {
            int count = ReviewHandlingService.lengthOf(newText);
            counter.setText(count + " / " + maxLength);
            counter.setStyle(count > maxLength ? ERROR_STYLE : MUTED_STYLE);
        });
        return counter;
    }

    /**
     * Returns the flag reason chosen in the flag dialog.
     *
     * @param reasons the group of reason buttons
     * @return the chosen reason, or {@code null} if none is chosen
     */
    private static ReviewFlagReason selectedFlagReason(ToggleGroup reasons) {
        Toggle selected = reasons.getSelectedToggle();
        return selected != null ? (ReviewFlagReason) selected.getUserData() : null;
    }

    /**
     * Draws a star rating, for example {@code ★★★★☆} for 4.
     *
     * @param rating the rating, from 1 to 5; values outside are clamped
     * @return five stars, filled up to the rating
     */
    private static String starsOf(int rating) {
        int filled = Math.max(0, Math.min(5, rating));
        return "★".repeat(filled) + "☆".repeat(5 - filled);
    }

    /**
     * Returns the buyer who wrote a review as shown to the Seller, for example
     * {@code Alice Chan (@alice)}.
     *
     * @param review the review
     * @return the buyer's name and username
     */
    private static String reviewerName(Review review) {
        User buyer = DummyDataService.getInstance().findUser(review.getBuyerUsername());
        return (buyer != null ? buyer.getDisplayName() + " " : "") + "(@" + review.getBuyerUsername() + ")";
    }

    /**
     * Returns the title of the listing a review is about.
     *
     * @param review the review
     * @return the listing title, or {@code Deal} if it is unknown
     */
    private static String reviewListingTitle(Review review) {
        return review.getListingTitle() != null && !review.getListingTitle().isBlank()
                ? review.getListingTitle().trim() : "Deal";
    }

    // =====================================================================
    // US-S07: Notifications screen (use case 2.7 "View Seller Notifications")
    // =====================================================================

    /** Text of the sidebar button of the Notifications screen, without its counter. */
    private static final String NOTIFICATIONS_BUTTON_TEXT = "  Notifications";
    /** Label of the category filter entry that shows every category. */
    private static final String ALL_CATEGORIES = "All categories";
    /** Style added to the card of the item a notification opened. */
    private static final String FOCUS_STYLE = " -fx-border-color: #A68137; -fx-border-width: 2;"
            + " -fx-border-radius: 10; -fx-background-radius: 10;";

    /** Category shown on the notification board, {@code null} for every category (alternative flow A2). */
    private SellerNotificationCategory notificationCategory;
    /** Whether the notification board shows only the unread notifications. */
    private boolean notificationsUnreadOnly;
    /** Text searched in the title and message of the notifications. */
    private String notificationSearch = "";

    /**
     * Shows the Seller's notifications, newest first, each with its category, timestamp,
     * title and message, unread ones highlighted, and the admin's reason of a rejected or
     * taken-down listing (basic flow, step 2). The category filter (alternative flow A2), the
     * unread filter and the search are kept while the Seller moves around the portal. Without
     * notifications, the screen says so (alternative flow A1). Without an active Seller session,
     * the seller login screen is shown instead.
     */
    @FXML
    private void showNotifications() {
        int unread;
        Map<SellerNotificationCategory, Integer> totals;
        Map<SellerNotificationCategory, Integer> unreadByCategory;
        try {
            unread = notificationService.getUnreadCount();
            totals = notificationService.getCategoryCounts();
            unreadByCategory = notificationService.getUnreadCategoryCounts();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        setActive(btnNotifications, "Notifications");
        updateNotificationsButton(unread);
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        Map<String, SellerNotificationCategory> categoryOptions = new LinkedHashMap<>();
        int total = totals.values().stream().mapToInt(Integer::intValue).sum();
        categoryOptions.put(countLabel(ALL_CATEGORIES, total, unread), null);
        for (SellerNotificationCategory category : SellerNotificationCategory.values()) {
            categoryOptions.put(countLabel(category.getIcon() + " " + category.getDisplayName(),
                    totals.get(category), unreadByCategory.get(category)), category);
        }
        ComboBox<String> categoryBox = new ComboBox<>(FXCollections.observableArrayList(categoryOptions.keySet()));
        categoryBox.setId("notificationCategoryFilter");
        categoryBox.setPrefWidth(250);
        categoryOptions.forEach((label, category) -> {
            if (category == notificationCategory) {
                categoryBox.setValue(label);
            }
        });

        TextField searchField = new TextField(notificationSearch);
        searchField.setId("notificationSearch");
        searchField.setPromptText("🔍 Search title or message...");
        searchField.setPrefWidth(220);
        CheckBox unreadOnly = new CheckBox("Unread only");
        unreadOnly.setId("notificationUnreadOnly");
        unreadOnly.setSelected(notificationsUnreadOnly);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button markAll = smallButton("✓ Mark all as read", "btn-outline", this::handleMarkAllNotificationsRead);
        markAll.setId("markAllNotificationsRead");
        markAll.setDisable(unread == 0);

        HBox filterBar = new HBox(10, new Label("Category:"), categoryBox, searchField, unreadOnly, spacer, markAll);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("card");
        filterBar.setPadding(new Insets(10, 14, 10, 14));

        Label summary = new Label();
        summary.setId("notificationSummary");
        summary.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px; -fx-font-weight: bold;");
        VBox list = new VBox(10);
        list.setId("notificationList");
        root.getChildren().addAll(filterBar, summary, list);

        Runnable refresh = () -> renderNotificationList(list, summary, unread);
        categoryBox.setOnAction(e -> {
            notificationCategory = categoryOptions.get(categoryBox.getValue());
            refresh.run();
        });
        searchField.textProperty().addListener((observable, oldText, newText) -> {
            notificationSearch = newText == null ? "" : newText;
            refresh.run();
        });
        unreadOnly.setOnAction(e -> {
            notificationsUnreadOnly = unreadOnly.isSelected();
            refresh.run();
        });
        refresh.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /**
     * Fills the notification list with the notifications that pass the current filter, newest
     * first, or with an empty-state card (alternative flow A1, or no match for the filter).
     *
     * @param list    the container of the notification cards
     * @param summary the label above the list
     * @param unread  the number of unread notifications of the Seller
     */
    private void renderNotificationList(VBox list, Label summary, int unread) {
        SellerNotificationFilter filter =
                new SellerNotificationFilter(notificationCategory, notificationsUnreadOnly, notificationSearch);
        List<Notification> notifications;
        try {
            notifications = notificationService.getMyNotifications(filter);
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        list.getChildren().clear();
        summary.setText("Showing " + notifications.size() + (notifications.size() == 1 ? " notification" : " notifications")
                + "  ·  " + unread + " unread in total");

        if (notifications.isEmpty()) {
            if (filter.isEmpty()) {
                list.getChildren().add(emptyStateCard("🔔", "No notifications",
                        "You have no notifications yet. Listing reviews, offers, reviews and deals will appear here."));
            } else {
                VBox empty = emptyStateCard("🔍", "No matching notifications",
                        "No notification matches the chosen category, search or unread filter.");
                Button showAll = smallButton("Show all notifications", "btn-primary", () -> {
                    notificationCategory = null;
                    notificationsUnreadOnly = false;
                    notificationSearch = "";
                    showNotifications();
                });
                empty.getChildren().add(showAll);
                list.getChildren().add(empty);
            }
            return;
        }
        for (Notification notification : notifications) {
            list.getChildren().add(createNotificationCard(notification));
        }
    }

    /**
     * Builds the card of a notification: its category, a NEW badge if it is unread, its
     * timestamp, title and message, the admin's reason for a rejected or taken-down listing, and
     * the buttons that open its related item and mark it read or unread. Clicking the card
     * opens the related item too (basic flow, step 3).
     *
     * @param notification the notification
     * @return the card
     */
    private VBox createNotificationCard(Notification notification) {
        SellerNotificationCategory category = SellerNotificationCategory.of(notification);
        Label categoryBadge = new Label(category.getIcon() + " " + category.getDisplayName());
        categoryBadge.getStyleClass().addAll("badge", notificationBadgeClass(category));
        HBox header = new HBox(8, categoryBadge);
        header.setAlignment(Pos.CENTER_LEFT);
        if (notification.isUnread()) {
            Label newBadge = new Label("NEW");
            newBadge.getStyleClass().add("badge-unread");
            header.getChildren().add(newBadge);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label time = new Label(SellerNotificationService.formatTimestamp(notification));
        time.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        header.getChildren().addAll(spacer, time);

        Label title = new Label(notification.getTitle() != null ? notification.getTitle() : category.getDisplayName());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #003974;");
        title.setWrapText(true);
        Label message = new Label(notification.getMessage() != null ? notification.getMessage() : "");
        message.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
        message.setWrapText(true);

        VBox card = new VBox(8, header, title, message);
        card.setId("notification-" + notification.getId());
        card.getStyleClass().addAll("notif-item", notification.isUnread() ? "notif-item-unread" : "notif-item-read");

        String reason = SellerNotificationService.adminReasonOf(notification);
        if (reason != null) {
            Label reasonNote = new Label("Admin's reason: " + reason);
            reasonNote.setId("notificationReason-" + notification.getId());
            reasonNote.setStyle(NOTE_ERROR_STYLE);
            reasonNote.setWrapText(true);
            reasonNote.setMaxWidth(Double.MAX_VALUE);
            card.getChildren().add(reasonNote);
        }

        Button toggleRead = smallButton(notification.isUnread() ? "Mark as read" : "Mark as unread", "btn-outline",
                () -> handleToggleNotificationRead(notification));
        toggleRead.setId("notificationToggleRead-" + notification.getId());
        Button open = smallButton(openButtonText(notification), "btn-primary", () -> handleOpenNotification(notification));
        open.setId("notificationOpen-" + notification.getId());
        HBox actions = new HBox(8, toggleRead, open);
        actions.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().add(actions);

        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> {
            if (!isInsideButton(event.getTarget(), card)) {
                handleOpenNotification(notification);
            }
        });
        return card;
    }

    /**
     * Basic flow, steps 3 and 4: marks the notification read and shows its related listing,
     * offer, deal, review or chat. A notification without related item shows its message; if
     * the item no longer exists, the Seller is told so (alternative flow A3).
     *
     * @param notification the selected notification
     */
    private void handleOpenNotification(Notification notification) {
        SellerNotificationResult result;
        try {
            result = notificationService.openNotification(notification.getId());
            updateNotificationsButton(notificationService.getUnreadCount());
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        switch (result.getStatus()) {
            case SUCCESS -> showRelatedItem(result);
            case NO_RELATED_ITEM -> showNotificationMessage(result);
            case ITEM_UNAVAILABLE -> {
                showNotifications();
                showNotificationAlert(Alert.AlertType.WARNING, result.getNotification(),
                        "This item is no longer available", result.getMessage());
            }
            case NOTIFICATION_NOT_FOUND -> {
                showNotifications();
                showNotificationAlert(Alert.AlertType.WARNING, null, "Notification not found", result.getMessage());
            }
        }
    }

    /**
     * Basic flow, step 4: shows the item a notification is about. A listing, a pending or
     * countered offer, and a review are shown highlighted on their own screen; an offer that
     * was already handled and a deal are shown in a dialog over the notification board.
     *
     * @param result the opened notification and its related item
     */
    private void showRelatedItem(SellerNotificationResult result) {
        switch (result.getTargetType()) {
            case LISTING -> showMyListings(result.getListing().getId());
            case OFFER -> {
                Offer offer = result.getOffer();
                if (offer.isPending() || offer.isCountered()) {
                    showOffers(offer.getId());
                } else {
                    showNotifications();
                    showOfferDetails(offer);
                }
            }
            case DEAL -> {
                showNotifications();
                showDealDetails(result.getDeal());
            }
            case REVIEW -> showReviews(result.getReview().getId());
            case CHAT_THREAD -> showChat(result.getChatThread(), null, "← Back to Notifications", this::showNotifications);
            case ANNOUNCEMENT, NONE -> showNotificationMessage(result);
        }
    }

    /**
     * Shows the message of a notification that is not linked to an item. When the notification
     * is about offers, listings or reviews in general, the dialog also offers to open that
     * screen.
     *
     * @param result the opened notification
     */
    private void showNotificationMessage(SellerNotificationResult result) {
        Notification notification = result.getNotification();
        showNotifications();
        String screen = null;
        Runnable open = null;
        switch (result.getCategory()) {
            case OFFERS_AND_REQUESTS -> {
                screen = "Open Offers & Requests";
                open = this::showOffers;
            }
            case LISTING_REVIEW -> {
                screen = "Open My Listings";
                open = this::showMyListings;
            }
            case REVIEWS -> {
                screen = "Open Reviews & Feedback";
                open = this::showReviews;
            }
            case DEALS, ANNOUNCEMENTS -> {
                // Nothing more to open: the message says it all.
            }
        }
        Alert alert = notificationAlert(Alert.AlertType.INFORMATION, notification,
                notification.getTitle(), notification.getMessage());
        ButtonType openType = null;
        if (screen != null) {
            openType = new ButtonType(screen, ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(openType, ButtonType.CLOSE);
        }
        Optional<ButtonType> answer = alert.showAndWait();
        if (openType != null && answer.isPresent() && answer.get() == openType) {
            open.run();
        }
    }

    /**
     * Shows the details of an offer, borrow request or claim that was already handled
     * (accepted, declined or withdrawn), so it is no longer on the Offers &amp; Requests screen.
     *
     * @param offer the offer
     */
    private void showOfferDetails(Offer offer) {
        Listing listing = DummyDataService.getInstance().findListing(offer.getListingId());
        StringBuilder details = new StringBuilder()
                .append("Buyer: ").append(buyerName(offer)).append('\n')
                .append("Listing: ").append(titleOf(offer, listing)).append('\n')
                .append("Terms: ").append(OfferManagementService.describeTerms(offer)).append('\n')
                .append("Received: ").append(OfferManagementService.formatDateTime(offer.getCreatedDate())).append('\n')
                .append("Status: ").append(displayName(offer.getStatus()));
        if (offer.getDeclineReason() != null && !offer.getDeclineReason().isBlank()) {
            details.append("\nDecline reason: ").append(offer.getDeclineReason().trim());
        }
        if (offer.getCounterOffer() != null) {
            details.append("\nYour counter offer: ").append(OfferManagementService.describeTerms(offer.getCounterOffer()));
        }
        String kind = OfferManagementService.kindOf(offer);
        showNotificationAlert(Alert.AlertType.INFORMATION, null,
                Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + " from @" + offer.getBuyerUsername(),
                details.toString());
    }

    /**
     * Shows the details of a deal with the attributes of the class diagram: its type, buyer,
     * start, due and closing dates, and status.
     *
     * @param deal the deal
     */
    private void showDealDetails(Deal deal) {
        StringBuilder details = new StringBuilder()
                .append("Item: ").append(deal.getListingTitle() != null ? deal.getListingTitle() : "Listing").append('\n')
                .append("Buyer: ").append(buyerName(deal.getBuyerUsername())).append('\n')
                .append("Type: ").append(displayName(deal.getType())).append('\n')
                .append("Status: ").append(displayName(deal.getStatus())).append('\n')
                .append("Started: ").append(OfferManagementService.formatDateTime(deal.getStartDate()));
        if (deal.getDueDate() != null) {
            details.append("\nDue: ").append(OfferManagementService.formatDateTime(deal.getDueDate()));
        }
        if (deal.getClosedDate() != null) {
            details.append("\nClosed: ").append(OfferManagementService.formatDateTime(deal.getClosedDate()));
        }
        if (deal.getType() == DealType.SALE && deal.getOfferPrice() != null) {
            details.append("\nPrice: ").append(OfferManagementService.formatPrice(deal.getOfferPrice()));
        }
        showNotificationAlert(Alert.AlertType.INFORMATION, null, "Deal with @" + deal.getBuyerUsername(),
                details.toString());
    }

    /**
     * Marks a notification read or unread without opening it, then refreshes the board.
     *
     * @param notification the notification
     */
    private void handleToggleNotificationRead(Notification notification) {
        try {
            if (notification.isUnread()) {
                notificationService.markAsRead(notification.getId());
            } else {
                notificationService.markAsUnread(notification.getId());
            }
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        showNotifications();
    }

    /**
     * Marks every notification of the Seller read, then refreshes the board.
     */
    private void handleMarkAllNotificationsRead() {
        try {
            notificationService.markAllAsRead();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        showNotifications();
    }

    /**
     * Shows a dialog about a notification and waits until the Seller closes it.
     *
     * @param type         kind of dialog
     * @param notification the notification, whose category and timestamp are shown, may be {@code null}
     * @param header       header of the dialog
     * @param content      text of the dialog
     */
    private static void showNotificationAlert(Alert.AlertType type, Notification notification,
                                              String header, String content) {
        notificationAlert(type, notification, header, content).showAndWait();
    }

    /**
     * Creates a dialog about a notification. For a notification that is no longer available
     * (alternative flow A3), the header says so and the notification's message comes first.
     *
     * @param type         kind of dialog
     * @param notification the notification, whose category and timestamp are shown, may be {@code null}
     * @param header       header of the dialog
     * @param content      text of the dialog
     * @return the dialog, not shown yet
     */
    private static Alert notificationAlert(Alert.AlertType type, Notification notification,
                                           String header, String content) {
        String text = content == null ? "" : content;
        if (notification != null && type == Alert.AlertType.WARNING && notification.getMessage() != null) {
            text = notification.getMessage() + "\n\n" + text;
        }
        Alert alert = new Alert(type, text, ButtonType.OK);
        alert.initOwner(SceneManager.getPrimaryStage());
        alert.setTitle(notification == null ? "Notifications"
                : "Notifications · " + SellerNotificationCategory.of(notification).getDisplayName()
                + " · " + SellerNotificationService.formatTimestamp(notification));
        alert.setHeaderText(header);
        alert.getDialogPane().setMinWidth(460);
        return alert;
    }

    /**
     * Returns the text of the button that opens a notification's related item.
     *
     * @param notification the notification
     * @return for example {@code Open listing →}, or {@code View details →} without related item
     */
    private static String openButtonText(Notification notification) {
        boolean linked = notification.getRelatedItemId() != null && !notification.getRelatedItemId().isBlank();
        if (!linked) {
            return "View details →";
        }
        return switch (NotificationTargetType.fromString(notification.getRelatedItemType())) {
            case LISTING -> "Open listing →";
            case OFFER -> "Open offer →";
            case DEAL -> "Open deal →";
            case REVIEW -> "Open review →";
            case CHAT_THREAD -> "Open chat →";
            case ANNOUNCEMENT, NONE -> "View details →";
        };
    }

    /**
     * Returns the style class of the badge of a category, shared with the Buyer Portal's board.
     *
     * @param category the category
     * @return the style class
     */
    private static String notificationBadgeClass(SellerNotificationCategory category) {
        return switch (category) {
            case LISTING_REVIEW -> "badge-listings";
            case OFFERS_AND_REQUESTS -> "badge-offers";
            case REVIEWS -> "badge-reviews";
            case DEALS -> "badge-completed";
            case ANNOUNCEMENTS -> "badge-announcements";
        };
    }

    /**
     * Builds the label of an entry of the category filter, for example
     * {@code 🏷️ Offers And Requests (3 · 1 unread)}.
     *
     * @param name   name of the entry
     * @param total  number of notifications
     * @param unread number of unread notifications
     * @return the label
     */
    private static String countLabel(String name, int total, int unread) {
        return name + " (" + total + (unread > 0 ? " · " + unread + " unread" : "") + ")";
    }

    /**
     * Tells whether a mouse event happened on a button inside a container, so that a click on a
     * card's button does not also open the card.
     *
     * @param target    target of the event
     * @param container the card
     * @return {@code true} if the target is a button of the card or inside one
     */
    private static boolean isInsideButton(EventTarget target, Node container) {
        Node node = target instanceof Node targetNode ? targetNode : null;
        while (node != null && node != container) {
            if (node instanceof ButtonBase) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    /**
     * Returns the name of an enumeration constant as shown to the Seller, for example
     * {@code Completed} for {@code COMPLETED}.
     *
     * @param value the constant, may be {@code null}
     * @return the name, or {@code Unknown}
     */
    private static String displayName(Enum<?> value) {
        if (value == null) {
            return "Unknown";
        }
        String words = value.name().replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /**
     * Shows the number of unread notifications on the sidebar button.
     *
     * @param unreadCount number of unread notifications
     */
    private void updateNotificationsButton(int unreadCount) {
        btnNotifications.setText(unreadCount > 0
                ? NOTIFICATIONS_BUTTON_TEXT + " (" + unreadCount + ")" : NOTIFICATIONS_BUTTON_TEXT);
    }

    /**
     * Highlights the first node found among some identifiers and scrolls the screen to it, for
     * example the listing, offer or review a notification is about.
     *
     * @param scroll  the scroll pane of the screen
     * @param page    the content of the scroll pane
     * @param nodeIds identifiers of the node, tried in order
     */
    private static void highlightNode(ScrollPane scroll, Region page, String... nodeIds) {
        for (String nodeId : nodeIds) {
            Node node = page.lookup("#" + nodeId);
            if (node != null) {
                node.setStyle((node.getStyle() == null ? "" : node.getStyle()) + FOCUS_STYLE);
                Platform.runLater(() -> scrollIntoView(scroll, page, node));
                return;
            }
        }
    }

    /**
     * Scrolls a screen so that a node is visible near the top.
     *
     * @param scroll the scroll pane of the screen
     * @param page   the content of the scroll pane
     * @param node   the node
     */
    private static void scrollIntoView(ScrollPane scroll, Region page, Node node) {
        scroll.applyCss();
        scroll.layout();
        double contentHeight = page.getBoundsInLocal().getHeight();
        double viewportHeight = scroll.getViewportBounds().getHeight();
        if (contentHeight <= viewportHeight) {
            return;
        }
        Bounds nodeBounds = page.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
        double target = (nodeBounds.getMinY() - 20) / (contentHeight - viewportHeight);
        scroll.setVvalue(Math.max(0, Math.min(1, target)));
    }

    // =====================================================================
    // US-S06: My Profile screen (use case 2.6 "Manage Seller Profile")
    // =====================================================================

    /**
     * Shows the Seller's profile (use case 2.6, US-S06): the username, which cannot be changed,
     * and a form filled with the full name, bio and preferred meet-up locations, with the fields
     * to change the password (basic flow, step 2). Without an active Seller session, the seller
     * login screen is shown instead.
     */
    @FXML
    private void showProfile() {
        Seller seller;
        try {
            seller = profileService.getMyProfile();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();
        contentPane.getChildren().add(new ProfileForm(seller).getView());
    }

    /**
     * The profile form of use case 2.6: its fields and the display of the errors of each field.
     * The rules are checked by {@link SellerProfileService}; this class only reads the form and
     * shows the outcome.
     */
    private final class ProfileForm {

        /** The logged-in Seller, whose profile is shown. */
        private final Seller seller;
        /** Full name input. */
        private final TextField fullNameField = new TextField();
        /** Bio input. */
        private final TextArea bioArea = new TextArea();
        /** One check box per meet-up location, in {@link MeetupLocation} order. */
        private final Map<MeetupLocation, CheckBox> meetupBoxes = new EnumMap<>(MeetupLocation.class);
        /** Current password input (alternative flow A2). */
        private final PasswordField currentPasswordField = new PasswordField();
        /** New password input (alternative flow A3). */
        private final PasswordField newPasswordField = new PasswordField();
        /** Confirmation of the new password (alternative flow A3). */
        private final PasswordField confirmPasswordField = new PasswordField();
        /** Error label of each field. */
        private final Map<SellerProfileField, Label> errorLabels = new EnumMap<>(SellerProfileField.class);
        /** Input of each field that can be invalid, highlighted when it is. */
        private final Map<SellerProfileField, TextInputControl> inputs = new EnumMap<>(SellerProfileField.class);
        /** Summary of the errors, next to the buttons. */
        private final Label formError = dialogErrorLabel();
        /** Button that saves the profile (step 5). */
        private final Button saveButton = new Button("Save Changes");
        /** Content of the screen. */
        private final VBox page = new VBox(16);
        /** Scroll pane holding the screen. */
        private final ScrollPane scroll = new ScrollPane();

        /**
         * Builds the form, filled with the current values of the Seller's profile.
         *
         * @param seller the logged-in Seller
         */
        ProfileForm(Seller seller) {
            this.seller = seller;
            page.setPadding(new Insets(4));
            page.setMaxWidth(720);
            page.getChildren().addAll(buildAccountCard(), buildAboutCard(), buildPasswordCard(), buildActions());
            fill(SellerProfileService.toEditRequest(seller));
            scroll.setContent(page);
            scroll.setFitToWidth(true);
        }

        /**
         * Returns the node showing the form.
         *
         * @return the scroll pane holding the form
         */
        Node getView() {
            return scroll;
        }

        /**
         * Builds the card with the username, which cannot be changed, and the full name.
         *
         * @return the card
         */
        private VBox buildAccountCard() {
            Label username = new Label("@" + seller.getUsername());
            username.setId("profileUsernameLabel");
            username.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #003974;");
            VBox usernameBox = new VBox(6, fieldLabel("Username"), username,
                    hint("Your username identifies your account and cannot be changed."));

            fullNameField.setId("profileFullNameField");
            fullNameField.setPromptText("For example: Charlie Lee");
            VBox fullNameBox = field("Full name", SellerProfileField.FULL_NAME, fullNameField,
                    lengthCounter(fullNameField, SellerProfileService.MAX_FULL_NAME_LENGTH),
                    "Shown to buyers on all your listings. Changing it updates every listing.");
            return card("Account", usernameBox, fullNameBox);
        }

        /**
         * Builds the card with the bio and the preferred meet-up locations.
         *
         * @return the card
         */
        private VBox buildAboutCard() {
            bioArea.setId("profileBioArea");
            bioArea.setPromptText("Optional: tell buyers who you are and what you usually sell.");
            bioArea.setPrefRowCount(4);
            bioArea.setWrapText(true);
            VBox bioBox = field("Bio", SellerProfileField.BIO, bioArea,
                    lengthCounter(bioArea, SellerProfileService.MAX_BIO_LENGTH), "Optional.");

            FlowPane meetupPane = new FlowPane(14, 10);
            meetupPane.setId("profileMeetupPane");
            for (MeetupLocation location : MeetupLocation.values()) {
                CheckBox box = new CheckBox(location.getDisplayName());
                box.setId("profileMeetup-" + location.name());
                meetupBoxes.put(location, box);
                meetupPane.getChildren().add(box);
            }
            VBox meetupBox = new VBox(6, fieldLabel("Preferred meet-up locations"), meetupPane,
                    hint("Optional. The first one, in the order above, is preselected when you create a listing."));
            return card("About you", bioBox, meetupBox);
        }

        /**
         * Builds the card with the three password fields (basic flow, step 4).
         *
         * @return the card
         */
        private VBox buildPasswordCard() {
            currentPasswordField.setId("profileCurrentPasswordField");
            newPasswordField.setId("profileNewPasswordField");
            confirmPasswordField.setId("profileConfirmPasswordField");
            Label intro = hint("Leave these fields empty to keep your current password.");
            VBox current = field("Current password", SellerProfileField.CURRENT_PASSWORD, currentPasswordField,
                    null, null);
            VBox next = field("New password", SellerProfileField.NEW_PASSWORD, newPasswordField, null,
                    "Same rules as at registration: " + String.join(", ", PasswordPolicy.rules()) + ".");
            VBox confirmBox = field("Confirm new password", SellerProfileField.CONFIRM_PASSWORD,
                    confirmPasswordField, null, null);
            return card("Change password", intro, current, next, confirmBox);
        }

        /**
         * Builds the row with the error summary and the Cancel and Save Changes buttons.
         *
         * @return the row
         */
        private HBox buildActions() {
            formError.setId("profileFormError");
            formError.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(formError, Priority.ALWAYS);
            Button cancel = new Button("Cancel");
            cancel.setId("profileCancelButton");
            cancel.getStyleClass().add("btn-outline");
            cancel.setOnAction(e -> cancel());
            saveButton.setId("profileSaveButton");
            saveButton.getStyleClass().add("btn-gold");
            saveButton.setOnAction(e -> save());
            HBox actions = new HBox(10, formError, cancel, saveButton);
            actions.setAlignment(Pos.CENTER_RIGHT);
            return actions;
        }

        /**
         * Builds a card with a title.
         *
         * @param title   title of the card
         * @param content content of the card
         * @return the card
         */
        private VBox card(String title, Node... content) {
            Label heading = new Label(title);
            heading.getStyleClass().add("section-title");
            VBox card = new VBox(14, heading);
            card.getChildren().addAll(content);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(18));
            return card;
        }

        /**
         * Builds a field: its label, its input, an optional counter and hint, and its hidden
         * error label. Typing in the input removes its error.
         *
         * @param labelText text of the label
         * @param field     the field
         * @param input     the input
         * @param counter   character counter shown next to the label, {@code null} for none
         * @param hintText  hint shown under the input, {@code null} for none
         * @return the field
         */
        private VBox field(String labelText, SellerProfileField field, TextInputControl input,
                           Label counter, String hintText) {
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox labelRow = new HBox(8, fieldLabel(labelText), gap);
            if (counter != null) {
                labelRow.getChildren().add(counter);
            }
            labelRow.setAlignment(Pos.CENTER_LEFT);
            VBox box = new VBox(6, labelRow, input);
            if (hintText != null) {
                box.getChildren().add(hint(hintText));
            }
            Label error = dialogErrorLabel();
            error.setId("profileError" + field.name());
            errorLabels.put(field, error);
            box.getChildren().add(error);
            inputs.put(field, input);
            input.textProperty().addListener((observable, oldText, newText) -> clearError(field));
            return box;
        }

        /**
         * Creates the label of a field.
         *
         * @param text the text
         * @return the label
         */
        private Label fieldLabel(String text) {
            Label label = new Label(text);
            label.setStyle(FIELD_LABEL_STYLE);
            return label;
        }

        /**
         * Creates a hint shown under a field.
         *
         * @param text the text
         * @return the label
         */
        private Label hint(String text) {
            Label label = new Label(text);
            label.setStyle(MUTED_STYLE);
            label.setWrapText(true);
            return label;
        }

        /**
         * Fills the form with values; the password fields are emptied.
         *
         * @param values the values, as given by {@link SellerProfileService#toEditRequest(Seller)}
         */
        private void fill(SellerProfileUpdateRequest values) {
            fullNameField.setText(values.fullName());
            bioArea.setText(values.bio());
            meetupBoxes.forEach((location, box) -> box.setSelected(values.preferredMeetups().contains(location)));
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
            clearAllErrors();
        }

        /**
         * Reads the form.
         *
         * @return the values entered by the Seller
         */
        private SellerProfileUpdateRequest toRequest() {
            List<MeetupLocation> meetups = new ArrayList<>();
            meetupBoxes.forEach((location, box) -> {
                if (box.isSelected()) {
                    meetups.add(location);
                }
            });
            return new SellerProfileUpdateRequest(fullNameField.getText(), bioArea.getText(), meetups,
                    currentPasswordField.getText(), newPasswordField.getText(), confirmPasswordField.getText());
        }

        /**
         * Saves the profile (steps 5 to 8). On success the Seller is told what changed and the
         * form shows the saved profile, with the password fields emptied; if nothing changed, the
         * Seller is told so; otherwise every invalid field is highlighted with its reason, the
         * first one gets the focus, and nothing is saved (alternative flows A1, A2 and A3).
         */
        private void save() {
            clearAllErrors();
            SellerProfileResult result;
            try {
                result = profileService.updateProfile(toRequest());
            } catch (SecurityException e) {
                endSellerSession();
                return;
            }
            switch (result.getStatus()) {
                case SUCCESS -> {
                    welcomeLabel.setText("Hello, " + result.getSeller().getFullName());
                    showProfileAlert(Alert.AlertType.INFORMATION, "Profile updated", result.getMessage());
                    showProfile();
                }
                case NO_CHANGES -> showProfileAlert(Alert.AlertType.INFORMATION, "Nothing to save", result.getMessage());
                case INVALID_FIELDS -> {
                    if (result.hasError(SellerProfileField.CURRENT_PASSWORD)) {
                        // Cleared before highlighting: clearing the text fires the listener that
                        // removes the highlight.
                        currentPasswordField.clear();
                    }
                    result.getErrors().forEach(this::markInvalid);
                    showError(formError, result.getMessage());
                    focus(result.getFirstInvalidField());
                }
            }
        }

        /**
         * Cancels the changes (alternative flow A4): after a confirmation if something was
         * changed, the form shows the saved profile again and nothing is saved.
         */
        private void cancel() {
            if (!SellerProfileService.isUnchanged(seller, toRequest())
                    && !confirm("Cancel changes", "Discard your changes?", "Your profile stays as it was.")) {
                return;
            }
            fill(SellerProfileService.toEditRequest(seller));
        }

        /**
         * Highlights a field and shows its error messages.
         *
         * @param field    the invalid field
         * @param messages its error messages
         */
        private void markInvalid(SellerProfileField field, List<String> messages) {
            TextInputControl input = inputs.get(field);
            if (input != null && !input.getStyleClass().contains(FIELD_ERROR_CLASS)) {
                input.getStyleClass().add(FIELD_ERROR_CLASS);
            }
            Label error = errorLabels.get(field);
            if (error != null) {
                showError(error, String.join("\n", messages));
            }
        }

        /**
         * Removes the highlight and the error messages of a field, and the error summary once
         * no field is invalid any more.
         *
         * @param field the field
         */
        private void clearError(SellerProfileField field) {
            TextInputControl input = inputs.get(field);
            if (input != null) {
                input.getStyleClass().removeAll(FIELD_ERROR_CLASS);
            }
            Label error = errorLabels.get(field);
            if (error != null) {
                hideError(error);
            }
            if (errorLabels.values().stream().noneMatch(Node::isVisible)) {
                hideError(formError);
            }
        }

        /**
         * Removes every highlight and error message.
         */
        private void clearAllErrors() {
            for (SellerProfileField field : SellerProfileField.values()) {
                clearError(field);
            }
        }

        /**
         * Gives the focus to the input of a field and scrolls the form so that it is visible.
         *
         * @param field the field, {@code null} to do nothing
         */
        private void focus(SellerProfileField field) {
            TextInputControl input = field == null ? null : inputs.get(field);
            if (input != null) {
                input.requestFocus();
                scrollIntoView(scroll, page, input);
            }
        }

        /**
         * Tells the Seller the outcome of a save.
         *
         * @param type    kind of alert
         * @param header  header of the alert
         * @param message the message
         */
        private void showProfileAlert(Alert.AlertType type, String header, String message) {
            Alert alert = new Alert(type, message, ButtonType.OK);
            alert.initOwner(SceneManager.getPrimaryStage());
            alert.setTitle("My Profile");
            alert.setHeaderText(header);
            alert.showAndWait();
        }
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
    static final String MUTED_STYLE = "-fx-text-fill: #5a5a7a; -fx-font-size: 12px;";

    /**
     * Shows the offers to handle (the Offers &amp; Requests button of the sidebar). See
     * {@link #showOffers(String)}.
     */
    @FXML
    private void showOffers() {
        showOffers(null);
    }

    /**
     * Shows the pending offers, borrow requests and claims received on the Seller's
     * listings, grouped by listing and oldest first (basic flow, step 2), followed by the
     * offers the Seller countered that wait for the buyer's answer. When nothing is pending,
     * the screen says so (alternative flow A1).
     *
     * @param focusOfferId pending or countered offer to scroll to and highlight, for example
     *                     the one a notification is about (use case 2.7), {@code null} for none
     */
    private void showOffers(String focusOfferId) {
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
        if (focusOfferId != null) {
            highlightNode(scroll, root, "pendingOffer-" + focusOfferId, "counteredOffer-" + focusOfferId);
        }
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
        row.setId("pendingOffer-" + offer.getId());
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
        row.setId("counteredOffer-" + offer.getId());
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
     * Shows all chat threads of the Seller with buyers about their listings (Task 1.5, F09),
     * ordered newest first.
     */
    @FXML
    private void showMessages() {
        setActive(btnMessages, "Messages");
        contentPane.getChildren().clear();

        List<ChatThread> threads;
        try {
            threads = sellerChatService.getMyChatThreads();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        Label intro = new Label(threads.isEmpty()
                ? "All conversations with buyers about your listings appear here."
                : threads.size() + (threads.size() == 1 ? " conversation" : " conversations") + ", most recent first.");
        intro.setStyle(MUTED_STYLE);
        root.getChildren().add(intro);

        if (threads.isEmpty()) {
            root.getChildren().add(emptyStateCard("💬", "No messages yet",
                    "When buyers inquire about your listings, your chat threads will appear here."));
        } else {
            for (ChatThread thread : threads) {
                root.getChildren().add(createThreadCard(thread));
            }
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    /**
     * Builds the card of a chat thread: listing title, buyer username, last message snippet,
     * timestamp, and a button to open the chat.
     *
     * @param thread the chat thread
     * @return the card node
     */
    private VBox createThreadCard(ChatThread thread) {
        Label listingBadge = new Label("📦 " + (thread.getListingTitle() != null ? thread.getListingTitle() : "Listing " + thread.getListingId()));
        listingBadge.getStyleClass().addAll("badge", "badge-chat");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Message lastMsg = thread.getLastMessage();
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);
        String timeStr = lastMsg != null && lastMsg.getSentDate() != null
                ? lastMsg.getSentDate().format(timeFormat)
                : (thread.getCreatedDate() != null ? thread.getCreatedDate().format(timeFormat) : "");
        Label timeLabel = new Label(timeStr);
        timeLabel.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        HBox header = new HBox(8, listingBadge, spacer, timeLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Label buyerLabel = new Label("Buyer: @" + thread.getBuyerUsername());
        buyerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #003974;");

        String snippetText = lastMsg != null ? lastMsg.getSenderUsername() + ": " + lastMsg.getText() : "No messages in thread.";
        if (snippetText.length() > 80) {
            snippetText = snippetText.substring(0, 77) + "...";
        }
        Label snippet = new Label(snippetText);
        snippet.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");
        snippet.setWrapText(true);

        Button openBtn = smallButton("Open chat →", "btn-primary",
                () -> showChat(thread, null, "← Back to Messages", this::showMessages));
        openBtn.setId("openChat-" + thread.getId());
        HBox actions = new HBox(8, openBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(8, header, buyerLabel, snippet, actions);
        card.setId("thread-" + thread.getId());
        card.getStyleClass().addAll("card");
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> {
            if (!isInsideButton(event.getTarget(), card)) {
                showChat(thread, null, "← Back to Messages", this::showMessages);
            }
        });
        return card;
    }

    /**
     * Shows the chat thread an offer was opened from, with a button back to the Offers &amp;
     * Requests screen.
     *
     * @param thread the chat thread
     * @param offer  the offer the chat was opened from
     */
    private void showOfferChat(ChatThread thread, Offer offer) {
        showChat(thread, offer, "← Back to Offers & Requests", this::showOffers);
    }

    /**
     * Shows a chat thread between the Seller and a buyer ({@link SellerChatView}): the whole
     * history with the same bubbles as the Buyer Portal, a search of the history that highlights
     * the matching messages (use case 1.5, subflow S3, F10), and a field to send a message.
     *
     * @param thread   the chat thread
     * @param offer    the offer the chat was opened from, {@code null} if it was opened from
     *                 elsewhere, for example a notification (use case 2.7)
     * @param backText text of the button that leaves the chat
     * @param back     the screen shown by that button
     */
    private void showChat(ChatThread thread, Offer offer, String backText, Runnable back) {
        Seller seller;
        try {
            seller = SessionManager.requireSeller();
        } catch (SecurityException e) {
            endSellerSession();
            return;
        }
        pageTitle.setText("Chat — " + thread.getListingTitle());
        contentPane.getChildren().clear();
        String about = "Listing: " + thread.getListingTitle() + (offer != null ? "  ·  " + termsLine(offer) : "");
        SellerChatView chat = new SellerChatView(sellerChatService, thread, seller.getUsername(),
                buyerName(thread.getBuyerUsername()), about, backText, back, this::endSellerSession);
        contentPane.getChildren().add(chat.getRoot());
        chat.focusInput();
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
    static Button smallButton(String text, String styleClass, Runnable action) {
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
    static Label dialogErrorLabel() {
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
    static void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    /**
     * Hides an error label.
     *
     * @param label the error label
     */
    static void hideError(Label label) {
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
    static VBox emptyStateCard(String icon, String title, String description) {
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
        return buyerName(offer.getBuyerUsername());
    }

    /**
     * Returns a buyer as shown to the Seller, for example {@code Alice Chan (@alice)}.
     *
     * @param username the buyer's username
     * @return the buyer's name and username
     */
    private static String buyerName(String username) {
        User buyer = DummyDataService.getInstance().findUser(username);
        return (buyer != null ? buyer.getDisplayName() + " " : "") + "(@" + username + ")";
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

    /**
     * Logs out the current seller session and navigates back to the welcome portal.
     */
    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
