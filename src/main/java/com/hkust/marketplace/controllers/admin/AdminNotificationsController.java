package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.AdminNotificationCategory;
import com.hkust.marketplace.models.AdminNotificationDetailResult;
import com.hkust.marketplace.models.AdminNotificationDetailStatus;
import com.hkust.marketplace.models.AdminNotificationFilterCriteria;
import com.hkust.marketplace.models.AdminReportResolutionResult;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Report;
import com.hkust.marketplace.models.ReportStatus;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.services.AdminNotificationService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Controller for Admin Notifications Board (US-A06 / Use Case 3.6 View Admin Notifications).
 *
 * <p>Handles multi-category notification listing, unread status filtering, marking all
 * or single items as read, target domain object resolution, report resolution modal dialogs,
 * and seamless navigation to related administrative management views.</p>
 */
public class AdminNotificationsController {

    private final DummyDataService dataService;
    private final Runnable refreshCallback;
    private final Runnable onNavigateToReview;
    private final Runnable onNavigateToRatings;
    private final Runnable onNavigateToUsers;

    public AdminNotificationsController() {
        this(DummyDataService.getInstance(), null, null, null, null);
    }

    public AdminNotificationsController(DummyDataService dataService,
                                        Runnable refreshCallback,
                                        Runnable onNavigateToReview,
                                        Runnable onNavigateToRatings,
                                        Runnable onNavigateToUsers) {
        this.dataService = Objects.requireNonNull(dataService, "dataService cannot be null.");
        this.refreshCallback = refreshCallback;
        this.onNavigateToReview = onNavigateToReview;
        this.onNavigateToRatings = onNavigateToRatings;
        this.onNavigateToUsers = onNavigateToUsers;
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
        AdminNotificationService service = dataService.getAdminNotificationService();
        String adminUsername = getEffectiveAdminUsername();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        // 1. Top Metrics Summary Row
        root.getChildren().add(createNotificationMetricsRow(service, adminUsername));

        // 2. Action & Filter Toolbar Card
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("card");
        filterBar.setPadding(new Insets(10, 14, 10, 14));

        ComboBox<String> catBox = new ComboBox<>();
        catBox.setPrefWidth(240);
        updateCategoryBoxOptions(catBox, service, adminUsername);

        TextField searchField = new TextField();
        searchField.setPromptText("Search notifications...");
        searchField.setPrefWidth(220);

        CheckBox unreadCheck = new CheckBox("Unread only");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button markAllBtn = new Button("Mark All Read");
        markAllBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        filterBar.getChildren().addAll(new Label("Category:"), catBox, searchField, unreadCheck, spacer, markAllBtn, refreshBtn);
        root.getChildren().add(filterBar);

        // 3. Notification Cards Container
        VBox listContainer = new VBox(10);
        root.getChildren().add(listContainer);

        catBox.setOnAction(e -> {
            if (catBox.getValue() != null) {
                String selectedCatStr = catBox.getValue();
                AdminNotificationCategory selectedCategory = parseSelectedCategory(selectedCatStr);
                boolean unreadOnly = unreadCheck.isSelected();
                String query = searchField.getText();
                AdminNotificationFilterCriteria criteria = new AdminNotificationFilterCriteria(selectedCategory, unreadOnly, query);
                List<Notification> notifs = service.getNotifications(adminUsername, criteria);
                renderNotificationCards(listContainer, notifs, service, adminUsername, this::requestRefresh);
            }
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String selectedCatStr = catBox.getValue();
            AdminNotificationCategory selectedCategory = parseSelectedCategory(selectedCatStr);
            boolean unreadOnly = unreadCheck.isSelected();
            AdminNotificationFilterCriteria criteria = new AdminNotificationFilterCriteria(selectedCategory, unreadOnly, newVal);
            List<Notification> notifs = service.getNotifications(adminUsername, criteria);
            renderNotificationCards(listContainer, notifs, service, adminUsername, this::requestRefresh);
        });

        unreadCheck.setOnAction(e -> {
            String selectedCatStr = catBox.getValue();
            AdminNotificationCategory selectedCategory = parseSelectedCategory(selectedCatStr);
            boolean unreadOnly = unreadCheck.isSelected();
            String query = searchField.getText();
            AdminNotificationFilterCriteria criteria = new AdminNotificationFilterCriteria(selectedCategory, unreadOnly, query);
            List<Notification> notifs = service.getNotifications(adminUsername, criteria);
            renderNotificationCards(listContainer, notifs, service, adminUsername, this::requestRefresh);
        });

        markAllBtn.setOnAction(e -> {
            int updated = service.markAllAsRead(adminUsername);
            requestRefresh();
            if (updated > 0) {
                new Alert(Alert.AlertType.INFORMATION, updated + " notification(s) marked as read.").showAndWait();
            }
        });

        refreshBtn.setOnAction(e -> requestRefresh());

        // Initial populate
        List<Notification> initialNotifs = service.getNotifications(adminUsername, AdminNotificationFilterCriteria.all());
        renderNotificationCards(listContainer, initialNotifs, service, adminUsername, this::requestRefresh);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private HBox createNotificationMetricsRow(AdminNotificationService service, String adminUsername) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        int unreadCount = service.getUnreadCount(adminUsername);
        int pendingListings = dataService.getListingReviewService().getPendingReviewCount();
        long openReports = dataService.getAllReports().stream()
                .filter(r -> r != null && r.getStatus() == ReportStatus.OPEN)
                .count();
        long flaggedReviews = dataService.getAllReviews().stream()
                .filter(r -> r != null && r.isFlagged() && !r.isHidden())
                .count();

        row.getChildren().addAll(
                AdminUIUtils.createMetricCard("Unread Alerts", String.valueOf(unreadCount), "#003974", unreadCount > 0 ? "🔔 Needs Attention" : "✅ All Clear"),
                AdminUIUtils.createMetricCard("Submissions Queue", String.valueOf(pendingListings), "#0284c7", pendingListings > 0 ? "📦 Awaiting Review" : "✅ Queue Clear"),
                AdminUIUtils.createMetricCard("Open Reports", String.valueOf(openReports), openReports > 0 ? "#dc2626" : "#16a34a", openReports > 0 ? "🚩 Action Required" : "✅ None Pending"),
                AdminUIUtils.createMetricCard("Flagged Reviews", String.valueOf(flaggedReviews), flaggedReviews > 0 ? "#b45309" : "#16a34a", flaggedReviews > 0 ? "⚠️ Seller Flags" : "✅ None Flagged")
        );
        return row;
    }

    private void updateCategoryBoxOptions(ComboBox<String> catBox, AdminNotificationService service, String adminUsername) {
        String current = catBox.getValue();
        java.util.Map<AdminNotificationCategory, Long> totalCounts = service.getCategoryCounts(adminUsername);
        java.util.Map<AdminNotificationCategory, Long> unreadCounts = service.getCategoryUnreadCounts(adminUsername);

        long total = totalCounts.values().stream().mapToLong(Long::longValue).sum();
        long totalUnread = unreadCounts.values().stream().mapToLong(Long::longValue).sum();

        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("All Categories (" + total + (totalUnread > 0 ? " · " + totalUnread + " unread" : "") + ")");

        for (AdminNotificationCategory cat : AdminNotificationCategory.values()) {
            long c = totalCounts.getOrDefault(cat, 0L);
            long u = unreadCounts.getOrDefault(cat, 0L);
            String label = cat.getIcon() + " " + cat.getDisplayName() + " (" + c + (u > 0 ? " · " + u + " unread" : "") + ")";
            items.add(label);
        }

        catBox.setItems(items);
        if (current != null) {
            for (String item : items) {
                if (item.startsWith(current.split(" \\(")[0])) {
                    catBox.setValue(item);
                    return;
                }
            }
        }
        catBox.getSelectionModel().selectFirst();
    }

    private AdminNotificationCategory parseSelectedCategory(String label) {
        if (label == null || label.startsWith("All Categories")) {
            return null;
        }
        for (AdminNotificationCategory cat : AdminNotificationCategory.values()) {
            if (label.contains(cat.getDisplayName())) {
                return cat;
            }
        }
        return AdminNotificationCategory.fromRaw(label);
    }

    private void renderNotificationCards(
            VBox listContainer,
            List<Notification> notifs,
            AdminNotificationService service,
            String adminUsername,
            Runnable onRefresh) {

        listContainer.getChildren().clear();

        if (notifs == null || notifs.isEmpty()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40, 20, 40, 20));
            emptyBox.getStyleClass().add("card");

            Label emptyTitle = new Label("No Notifications Found");
            emptyTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #003974;");

            Label emptySub = new Label("There are no admin notifications matching your active category and filter criteria.");
            emptySub.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");

            emptyBox.getChildren().addAll(emptyTitle, emptySub);
            listContainer.getChildren().add(emptyBox);
            return;
        }

        for (Notification n : notifs) {
            VBox card = new VBox(8);
            card.getStyleClass().addAll("notif-item", n.isUnread() ? "notif-item-unread" : "notif-item-read");
            card.setPadding(new Insets(12, 16, 12, 16));

            HBox topRow = new HBox(10);
            topRow.setAlignment(Pos.CENTER_LEFT);

            AdminNotificationCategory cat = AdminNotificationCategory.fromRaw(n.getCategory());
            Label catBadge = new Label(cat.getIcon() + " " + cat.getDisplayName());
            catBadge.getStyleClass().add("badge");
            switch (cat) {
                case LISTING_SUBMISSIONS -> catBadge.getStyleClass().add("badge-listings");
                case REPORTS -> catBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-weight: bold;");
                case FLAGGED_REVIEWS -> catBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-weight: bold;");
                case USER_ACCOUNTS -> catBadge.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-weight: bold;");
                case ANNOUNCEMENTS -> catBadge.getStyleClass().add("badge-announcements");
            }

            Label titleLbl = new Label(n.getTitle() != null ? n.getTitle() : "Notification");
            titleLbl.setStyle("-fx-font-weight: " + (n.isUnread() ? "bold" : "normal") + "; -fx-font-size: 14px; -fx-text-fill: #0f172a;");
            HBox.setHgrow(titleLbl, Priority.ALWAYS);

            if (n.isUnread()) {
                Label unreadBadge = new Label("NEW");
                unreadBadge.getStyleClass().add("badge-unread");
                topRow.getChildren().addAll(catBadge, titleLbl, unreadBadge);
            } else {
                topRow.getChildren().addAll(catBadge, titleLbl);
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            topRow.getChildren().add(spacer);

            String timeStr = n.getFormattedTimestamp();
            Label timeLbl = new Label(timeStr != null ? timeStr : "");
            timeLbl.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
            topRow.getChildren().add(timeLbl);

            Label msgLbl = new Label(n.getMessage() != null ? n.getMessage() : "");
            msgLbl.setWrapText(true);
            msgLbl.setStyle("-fx-text-fill: #334155; -fx-font-size: 13px;");

            HBox bottomRow = new HBox(10);
            bottomRow.setAlignment(Pos.CENTER_LEFT);

            if (n.getRelatedItemId() != null && !n.getRelatedItemId().trim().isEmpty()) {
                String typeDesc = n.getRelatedItemType() != null ? n.getRelatedItemType() : "Item";
                Label refBadge = new Label(typeDesc + " #" + n.getRelatedItemId());
                refBadge.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-background-color: #f1f5f9; -fx-padding: 2 6; -fx-background-radius: 4;");
                bottomRow.getChildren().add(refBadge);
            }

            Region bSpacer = new Region();
            HBox.setHgrow(bSpacer, Priority.ALWAYS);
            bottomRow.getChildren().add(bSpacer);

            if (n.isUnread()) {
                Button markReadBtn = new Button("Mark Read");
                markReadBtn.getStyleClass().addAll("btn-outline", "btn-sm");
                markReadBtn.setOnAction(e -> {
                    service.markAsRead(n.getId(), adminUsername);
                    onRefresh.run();
                });
                bottomRow.getChildren().add(markReadBtn);
            }

            Button viewBtn = new Button("View Details");
            viewBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            viewBtn.setOnAction(e -> showNotificationDetailsDialog(n, service, adminUsername, onRefresh));
            bottomRow.getChildren().add(viewBtn);

            card.getChildren().addAll(topRow, msgLbl, bottomRow);
            listContainer.getChildren().add(card);
        }
    }

    public void showNotificationDetailsDialog(
            Notification n,
            AdminNotificationService service,
            String adminUsername,
            Runnable onRefresh) {

        AdminNotificationDetailResult res = service.getNotificationDetail(n.getId(), adminUsername);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Notification Details - " + n.getId());
        dialog.setHeaderText(n.getTitle());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(14);
        content.setPrefWidth(540);
        content.setPadding(new Insets(12));

        // 1. Notification Information Card
        VBox notifBox = new VBox(6);
        notifBox.getStyleClass().add("card");
        notifBox.setPadding(new Insets(12));

        Label notifHead = new Label("Notification Information");
        notifHead.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #003974;");

        GridPane notifGrid = new GridPane();
        notifGrid.setHgap(10);
        notifGrid.setVgap(6);

        notifGrid.add(new Label("Category:"), 0, 0);
        notifGrid.add(new Label(n.getCategory()), 1, 0);

        notifGrid.add(new Label("Timestamp:"), 0, 1);
        notifGrid.add(new Label(n.getFormattedTimestamp()), 1, 1);

        notifGrid.add(new Label("Message:"), 0, 2);
        Label mVal = new Label(n.getMessage());
        mVal.setWrapText(true);
        notifGrid.add(mVal, 1, 2);

        notifBox.getChildren().addAll(notifHead, notifGrid);
        content.getChildren().add(notifBox);

        // 2. Related Item Section
        VBox relatedBox = new VBox(8);
        relatedBox.getStyleClass().add("card");
        relatedBox.setPadding(new Insets(12));

        Label relatedHead = new Label("Related Domain Entity");
        relatedHead.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #003974;");
        relatedBox.getChildren().add(relatedHead);

        if (res.getStatus() == AdminNotificationDetailStatus.ITEM_NO_LONGER_AVAILABLE) {
            VBox warnBox = new VBox(6);
            warnBox.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");
            Label warnLbl = new Label("⚠️ Item No Longer Available");
            warnLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #b91c1c;");
            Label warnMsg = new Label(res.getMessage());
            warnMsg.setWrapText(true);
            warnMsg.setStyle("-fx-text-fill: #7f1d1d; -fx-font-size: 12px;");
            warnBox.getChildren().addAll(warnLbl, warnMsg);
            relatedBox.getChildren().add(warnBox);

        } else if ("REPORT".equalsIgnoreCase(res.getTargetType()) && res.getReport() != null) {
            Report report = res.getReport();
            GridPane repGrid = new GridPane();
            repGrid.setHgap(10);
            repGrid.setVgap(6);

            repGrid.add(new Label("Report ID:"), 0, 0);
            repGrid.add(new Label(report.getId()), 1, 0);

            repGrid.add(new Label("Reported Listing:"), 0, 1);
            repGrid.add(new Label(report.getListingTitle() + " (ID: " + report.getListingId() + ")"), 1, 1);

            repGrid.add(new Label("Reporting Buyer:"), 0, 2);
            repGrid.add(new Label("@" + report.getBuyerUsername()), 1, 2);

            repGrid.add(new Label("Reason Filed:"), 0, 3);
            Label rVal = new Label(report.getReason());
            rVal.setWrapText(true);
            repGrid.add(rVal, 1, 3);

            repGrid.add(new Label("Status:"), 0, 4);
            Label repStatus = new Label(report.getStatus() != null ? report.getStatus().name() : "OPEN");
            repStatus.getStyleClass().addAll("badge", report.getStatus() == ReportStatus.RESOLVED ? "badge-success" : "badge-danger");
            repGrid.add(repStatus, 1, 4);
            if (report.getHandledBy() != null && !report.getHandledBy().trim().isEmpty()) {
                repGrid.add(new Label("Handled By:"), 0, 5);
                repGrid.add(new Label("@" + report.getHandledBy()), 1, 5);
            }

            relatedBox.getChildren().add(repGrid);

            if (report.getStatus() == ReportStatus.OPEN) {
                HBox repActions = new HBox(10);
                repActions.setAlignment(Pos.CENTER_RIGHT);
                repActions.setPadding(new Insets(10, 0, 0, 0));

                Button resolveBtn = new Button("Mark as Resolved");
                resolveBtn.getStyleClass().addAll("btn-success", "btn-sm");
                resolveBtn.setOnAction(ev -> {
                    Alert confirm = new Alert(
                            Alert.AlertType.CONFIRMATION,
                            "Confirm Report Resolution:\n\nMark report " + report.getId() + " regarding '" + report.getListingTitle() + "' as Resolved?\nBuyer @" + report.getBuyerUsername() + " will be notified.",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Confirm Report Resolution");
                    Optional<ButtonType> opt = confirm.showAndWait();
                    if (opt.isPresent() && opt.get() == ButtonType.YES) {
                        AdminReportResolutionResult resolveRes = service.resolveReport(report.getId(), adminUsername, "Reviewed by administrator");
                        if (resolveRes.isSuccess()) {
                            new Alert(Alert.AlertType.INFORMATION, "Report " + report.getId() + " has been marked as Resolved. The buyer has been notified.").showAndWait();
                            dialog.close();
                            onRefresh.run();
                        } else {
                            new Alert(Alert.AlertType.ERROR, resolveRes.getMessage()).showAndWait();
                        }
                    }
                });
                repActions.getChildren().add(resolveBtn);
                relatedBox.getChildren().add(repActions);
            }

        } else if ("LISTING".equalsIgnoreCase(res.getTargetType()) && res.getListing() != null) {
            Listing l = res.getListing();
            GridPane lGrid = new GridPane();
            lGrid.setHgap(10);
            lGrid.setVgap(6);

            lGrid.add(new Label("Title:"), 0, 0);
            lGrid.add(new Label(l.getTitle()), 1, 0);

            lGrid.add(new Label("Seller:"), 0, 1);
            lGrid.add(new Label(l.getSellerFullName() + " (@" + l.getSellerUsername() + ")"), 1, 1);

            lGrid.add(new Label("Type / Category:"), 0, 2);
            lGrid.add(new Label(l.getTypeDisplay() + " · " + (l.getCategory() != null ? l.getCategory().getDisplayName() : "-")), 1, 2);

            lGrid.add(new Label("Status:"), 0, 3);
            Label lStatus = new Label(l.getStatus() != null ? l.getStatus().name() : "LIVE");
            lStatus.getStyleClass().addAll("badge", l.isPendingReview() ? "badge-pending" : (l.isLive() ? "badge-live" : "badge-sold"));
            lGrid.add(lStatus, 1, 3);

            relatedBox.getChildren().add(lGrid);

            if (l.isPendingReview()) {
                HBox lActions = new HBox(10);
                lActions.setAlignment(Pos.CENTER_RIGHT);
                Button openReviewBtn = new Button("Open Listing Review Queue");
                openReviewBtn.getStyleClass().addAll("btn-primary", "btn-sm");
                openReviewBtn.setOnAction(ev -> {
                    dialog.close();
                    if (onNavigateToReview != null) onNavigateToReview.run();
                });
                lActions.getChildren().add(openReviewBtn);
                relatedBox.getChildren().add(lActions);
            }

        } else if ("REVIEW".equalsIgnoreCase(res.getTargetType()) && res.getReview() != null) {
            Review rev = res.getReview();
            GridPane rGrid = new GridPane();
            rGrid.setHgap(10);
            rGrid.setVgap(6);

            rGrid.add(new Label("Review ID:"), 0, 0);
            rGrid.add(new Label(rev.getId()), 1, 0);

            rGrid.add(new Label("Seller:"), 0, 1);
            rGrid.add(new Label("@" + rev.getSellerUsername()), 1, 1);

            rGrid.add(new Label("Buyer:"), 0, 2);
            rGrid.add(new Label("@" + rev.getBuyerUsername()), 1, 2);

            rGrid.add(new Label("Rating:"), 0, 3);
            rGrid.add(new Label("⭐ " + rev.getRating() + "/5"), 1, 3);

            rGrid.add(new Label("Buyer Comment:"), 0, 4);
            Label cVal = new Label(rev.getComment());
            cVal.setWrapText(true);
            rGrid.add(cVal, 1, 4);

            if (rev.isFlagged()) {
                rGrid.add(new Label("Seller Flag Reason:"), 0, 5);
                Label fVal = new Label(rev.getFlagReason() != null ? rev.getFlagReason() : "Inappropriate content");
                fVal.setWrapText(true);
                fVal.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold;");
                rGrid.add(fVal, 1, 5);
            }

            relatedBox.getChildren().add(rGrid);

            HBox rActions = new HBox(10);
            rActions.setAlignment(Pos.CENTER_RIGHT);
            Button openRatingsBtn = new Button("Open Seller Ratings Overview");
            openRatingsBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            openRatingsBtn.setOnAction(ev -> {
                dialog.close();
                if (onNavigateToRatings != null) onNavigateToRatings.run();
            });
            rActions.getChildren().add(openRatingsBtn);
            relatedBox.getChildren().add(rActions);

        } else if ("USER".equalsIgnoreCase(res.getTargetType()) && res.getUser() != null) {
            User u = res.getUser();
            GridPane uGrid = new GridPane();
            uGrid.setHgap(10);
            uGrid.setVgap(6);

            uGrid.add(new Label("Username:"), 0, 0);
            uGrid.add(new Label("@" + u.getUsername()), 1, 0);

            uGrid.add(new Label("Full Name:"), 0, 1);
            uGrid.add(new Label(u.getFullName() != null ? u.getFullName() : "-"), 1, 1);

            uGrid.add(new Label("Account Role:"), 0, 2);
            uGrid.add(new Label(u.getRole() != null ? u.getRole().name() : "-"), 1, 2);

            uGrid.add(new Label("Account Status:"), 0, 3);
            Label uStatus = new Label(u.getAccountStatus() != null ? u.getAccountStatus().name() : "ACTIVE");
            uStatus.getStyleClass().addAll("badge", u.isActive() ? "badge-active" : "badge-danger");
            uGrid.add(uStatus, 1, 3);

            relatedBox.getChildren().add(uGrid);

            HBox uActions = new HBox(10);
            uActions.setAlignment(Pos.CENTER_RIGHT);
            Button openUsersBtn = new Button("Open Manage Users Screen");
            openUsersBtn.getStyleClass().addAll("btn-primary", "btn-sm");
            openUsersBtn.setOnAction(ev -> {
                dialog.close();
                if (onNavigateToUsers != null) onNavigateToUsers.run();
            });
            uActions.getChildren().add(openUsersBtn);
            relatedBox.getChildren().add(uActions);

        } else {
            Label annLbl = new Label("📢 System Announcement notice. No further entity actions required.");
            annLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
            relatedBox.getChildren().add(annLbl);
        }

        content.getChildren().add(relatedBox);

        ScrollPane dScroll = new ScrollPane(content);
        dScroll.setFitToWidth(true);
        dialog.getDialogPane().setContent(dScroll);
        dialog.showAndWait();
        onRefresh.run();
    }
}
