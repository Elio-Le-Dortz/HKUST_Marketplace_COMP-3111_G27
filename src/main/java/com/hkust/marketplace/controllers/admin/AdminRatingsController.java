package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.AdminRatingFilterCriteria;
import com.hkust.marketplace.models.AdminRatingOperationResult;
import com.hkust.marketplace.models.AdminRatingSummaryMetrics;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.services.AdminRatingService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Controller for Seller Ratings Moderation (US-A08 / Use Case 3.8 Manage Seller Ratings).
 *
 * <p>Handles flagged reviews oversight, multi-criteria filtering, hiding/unhiding reviews,
 * and dismissing seller flags with real-time seller rating recalculation and notifications.</p>
 */
public class AdminRatingsController {

    private final DummyDataService dataService;
    private final Runnable refreshCallback;

    public AdminRatingsController() {
        this(DummyDataService.getInstance(), null);
    }

    public AdminRatingsController(DummyDataService dataService, Runnable refreshCallback) {
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
        AdminRatingService ratingService = dataService.getAdminRatingService();
        String adminUsername = getEffectiveAdminUsername();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        // 1. Summary Metrics Overview Row
        AdminRatingSummaryMetrics metrics = ratingService.getSummaryMetrics();
        HBox metricsRow = new HBox(12);
        metricsRow.setAlignment(Pos.CENTER_LEFT);
        metricsRow.getChildren().addAll(
                AdminUIUtils.createMetricCard("Total Reviews", String.valueOf(metrics.getTotalReviews()), "#003974", "All Feedback"),
                AdminUIUtils.createMetricCard("Flagged Reviews", String.valueOf(metrics.getFlaggedReviews()),
                        metrics.getFlaggedReviews() > 0 ? "#b45309" : "#16a34a",
                        metrics.getFlaggedReviews() > 0 ? "⚠️ Needs Review" : "✓ None Flagged"),
                AdminUIUtils.createMetricCard("Hidden Reviews", String.valueOf(metrics.getHiddenReviews()), "#64748b", "Excluded from Ratings"),
                AdminUIUtils.createMetricCard("Visible Reviews", String.valueOf(metrics.getVisibleReviews()), "#2e7d32", "Counted in Ratings"),
                AdminUIUtils.createMetricCard("Platform Avg", String.format(Locale.ENGLISH, "%.1f ★", metrics.getAveragePlatformRating()), "#A68137", "Visible Average")
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
        refreshBtn.setOnAction(e -> requestRefresh());

        filterCard.getChildren().addAll(
                userQueryField, scoreFilter, stateFilter,
                startDatePicker, endDatePicker,
                applyFilterBtn, resetFilterBtn, spacer, refreshBtn
        );
        root.getChildren().add(filterCard);

        // 3. Reviews Container
        VBox reviewsContainer = new VBox(12);
        root.getChildren().add(reviewsContainer);

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
                    requestRefresh();
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
        return scroll;
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
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Dismiss Flag");
                confirm.setHeaderText("Dismiss Seller Flag for Review " + r.getId() + "?");
                confirm.setContentText("The review will remain visible to users and the seller will be notified that their flag was dismissed.");
                Optional<ButtonType> opt = confirm.showAndWait();
                if (opt.isPresent() && opt.get() == ButtonType.OK) {
                    AdminRatingOperationResult res = ratingService.dismissFlag(r.getId(), adminUsername);
                    if (res.isSuccess()) {
                        new Alert(Alert.AlertType.INFORMATION, res.getMessage()).showAndWait();
                        requestRefresh();
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
                        requestRefresh();
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
                        requestRefresh();
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
}
