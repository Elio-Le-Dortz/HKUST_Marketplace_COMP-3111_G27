package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingReviewResult;
import com.hkust.marketplace.models.ListingReviewStatus;
import com.hkust.marketplace.models.Photo;
import com.hkust.marketplace.services.ListingReviewService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Controller for the Admin Listing Review Queue (US-A02 / Use Case 3.2 Review Pending Listings).
 *
 * <p>Handles queue retrieval ordered oldest first, approval confirmation, mandatory rejection
 * reasoning dialogs, and listing submission detail inspection.</p>
 */
public class AdminReviewController {

    private final DummyDataService dataService;
    private final Runnable refreshCallback;

    public AdminReviewController() {
        this(DummyDataService.getInstance(), null);
    }

    public AdminReviewController(DummyDataService dataService, Runnable refreshCallback) {
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

    /**
     * Builds and returns the main Review Queue view.
     */
    public Node createView() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        List<Listing> pending = dataService.getListingReviewService().getPendingListingsOldestFirst();

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label countLabel = new Label("Review Queue (" + pending.size() + " awaiting decision)");
        countLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #003974;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refreshBtn = new Button("Refresh Queue");
        refreshBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        refreshBtn.setOnAction(e -> requestRefresh());
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
        return scroll;
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

        StackPane thumb = AdminUIUtils.listingThumbnail(l);

        VBox infoBox = new VBox(8);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        infoBox.getChildren().addAll(topRow, meta, submitted, actions);

        cardLayout.getChildren().addAll(thumb, infoBox);
        card.getChildren().add(cardLayout);
        return card;
    }

    public void handleApprove(Listing l) {
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Confirm Listing Approval");
        conf.setHeaderText("Approve listing '" + l.getTitle() + "'?");
        conf.setContentText("Once approved, this listing will become immediately Live and visible to buyers in the marketplace.");

        Optional<ButtonType> result = conf.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String adminUsername = getEffectiveAdminUsername();
            ListingReviewResult reviewResult = dataService.getListingReviewService().approveListing(l.getId(), adminUsername);

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
            requestRefresh();
        }
    }

    public void handleReject(Listing l) {
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
            ListingReviewResult reviewResult = dataService.getListingReviewService().rejectListing(l.getId(), adminUsername, reason);

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
            requestRefresh();
            break;
        }
    }

    public void showListingDetails(Listing l) {
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
                Path r = AdminUIUtils.readablePhoto(p != null ? p.getImagePath() : null);
                photoGallery.getChildren().add(AdminUIUtils.photoThumbnailNode(r, AdminUIUtils.DETAIL_THUMBNAIL_SIZE));
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
}
