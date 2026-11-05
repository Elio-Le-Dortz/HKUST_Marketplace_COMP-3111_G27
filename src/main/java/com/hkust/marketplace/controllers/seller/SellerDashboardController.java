package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.CreateListingService;
import com.hkust.marketplace.services.MyListingsService;
import com.hkust.marketplace.services.OfferManagementService;
import com.hkust.marketplace.services.ReviewHandlingService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnOffers, btnCreate, btnStats, btnReviews, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    /** Business logic of the Offers &amp; Requests screen (use case 2.5, US-S05). */
    private final OfferManagementService offerService = new OfferManagementService();

    /** Business logic of the Create Listing screen (use case 2.3, US-S03). */
    private final CreateListingService createListingService = new CreateListingService();

    /** Business logic of the My Listings screen (use case 2.4, US-S04). */
    private final MyListingsService myListingsService = new MyListingsService();

    /** Business logic of the Reviews &amp; Feedback screen (use case 2.9, US-S09). */
    private final ReviewHandlingService reviewService = new ReviewHandlingService();

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
        showMyListings();
    }

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
     * Shows the Seller's listings, newest first, with their status, views, open offers and
     * the admin's reason for a rejected or taken-down listing (basic flow, step 2), each with
     * its Edit and Delete buttons. Without listings, the screen says so (alternative flow A1).
     * Without an active Seller session, the seller login screen is shown instead.
     */
    @FXML
    private void showMyListings() {
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
     * Shows the Seller's average rating and their reviews, newest first, each with the buyer,
     * the listing, the stars, the written review, the date, the Seller's reply and whether it
     * is flagged (basic flow, step 2), with its Reply and Flag buttons. Reviews hidden by the
     * admin are neither shown nor counted. Without reviews, the screen says so (alternative
     * flow A1). Without an active Seller session, the seller login screen is shown instead.
     */
    @FXML
    private void showReviews() {
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
