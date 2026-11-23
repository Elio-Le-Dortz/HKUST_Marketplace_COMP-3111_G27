package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminModularControllers Extended Coverage Tests")
public class AdminModularControllersExtendedTest {

    private DummyDataService dataService;
    private Admin testAdmin;

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit already initialized
        }
    }

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        SessionManager.logout();

        testAdmin = new Admin("admin", "Admin User", "Admin123!");
        SessionManager.login(testAdmin);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        dataService.resetData();
    }

    @Test
    @DisplayName("AdminUIUtils: photo thumbnails and form rendering branches")
    void testAdminUIUtilsExtended(@TempDir Path tempDir) throws IOException {
        Path imgFile = tempDir.resolve("sample.png");
        Files.write(imgFile, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        // photoThumbnailNode: null, invalid, valid
        assertNotNull(AdminUIUtils.photoThumbnailNode(null, 64));
        assertNotNull(AdminUIUtils.photoThumbnailNode(tempDir.resolve("non_existent.png"), 64));
        assertNotNull(AdminUIUtils.photoThumbnailNode(imgFile, 64));

        // listingThumbnail: null, SELL, LEND, GIVEAWAY, and with photo
        assertNotNull(AdminUIUtils.listingThumbnail(null));

        Listing lend = new Listing();
        lend.setType(ListingType.LEND);
        assertNotNull(AdminUIUtils.listingThumbnail(lend));

        Listing give = new Listing();
        give.setType(ListingType.GIVEAWAY);
        assertNotNull(AdminUIUtils.listingThumbnail(give));

        Listing sell = new Listing();
        sell.setType(ListingType.SELL);
        assertNotNull(AdminUIUtils.listingThumbnail(sell));

        sell.setPhotos(List.of(new Photo(imgFile.toString())));
        assertNotNull(AdminUIUtils.listingThumbnail(sell));

        // renderFormPhotoThumbnails with empty list
        HBox container = new HBox();
        Label countLabel = new Label();
        Button addBtn = new Button();
        Label emptyLabel = new Label();
        AdminUIUtils.renderFormPhotoThumbnails(container, countLabel, addBtn, Collections.emptyList(), emptyLabel);
        assertEquals(1, container.getChildren().size());
        assertTrue(container.getChildren().contains(emptyLabel));

        // renderFormPhotoThumbnails with photo items
        AdminUIUtils.renderFormPhotoThumbnails(container, countLabel, addBtn, List.of(imgFile), emptyLabel);
        assertEquals(1, container.getChildren().size());
        assertFalse(container.getChildren().contains(emptyLabel));
    }

    @Test
    @DisplayName("AdminReviewController: empty queue and multi-type pending cards")
    void testAdminReviewControllerExtended() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminReviewController controller = new AdminReviewController(dataService, () -> refreshed.set(true));

        // 1. Existing queue (contains L104 Mini Fridge)
        Node view = controller.createView();
        assertNotNull(view);

        // 2. Add pending Lend and Giveaway listings
        Listing lendPending = new Listing();
        lendPending.setId("L_PEND_LEND");
        lendPending.setTitle("Calculus Book Lend");
        lendPending.setType(ListingType.LEND);
        lendPending.setStatus(ListingStatus.PENDING_REVIEW);
        lendPending.setSellerUsername("diana");
        lendPending.setSellerFullName("Diana Ng");
        dataService.addListing(lendPending);

        Listing givePending = new Listing();
        givePending.setId("L_PEND_GIVE");
        givePending.setTitle("Hoodie Giveaway");
        givePending.setType(ListingType.GIVEAWAY);
        givePending.setStatus(ListingStatus.PENDING_REVIEW);
        givePending.setSellerUsername("charlie");
        givePending.setSellerFullName("Charlie Lee");
        dataService.addListing(givePending);

        Node multiView = controller.createView();
        assertNotNull(multiView);

        // 3. Clear all pending listings -> empty state branch
        for (Listing l : dataService.getAllListings()) {
            if (l.getStatus() == ListingStatus.PENDING_REVIEW) {
                l.setStatus(ListingStatus.LIVE);
            }
        }
        Node emptyView = controller.createView();
        assertNotNull(emptyView);
    }

    @Test
    @DisplayName("AdminListingsController: cards across statuses, types, and photos")
    void testAdminListingsControllerCards() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminListingsController controller = new AdminListingsController(dataService, () -> refreshed.set(true));

        // Add listings of each status and type
        Listing reserved = new Listing();
        reserved.setId("L_STAT_RES");
        reserved.setTitle("Reserved Item");
        reserved.setType(ListingType.SELL);
        reserved.setStatus(ListingStatus.RESERVED);
        reserved.setSellerUsername("charlie");
        dataService.addListing(reserved);

        Listing lent = new Listing();
        lent.setId("L_STAT_LENT");
        lent.setTitle("Lent Item");
        lent.setType(ListingType.LEND);
        lent.setStatus(ListingStatus.LENT);
        lent.setSellerUsername("diana");
        dataService.addListing(lent);

        Listing sold = new Listing();
        sold.setId("L_STAT_SOLD");
        sold.setTitle("Sold Item");
        sold.setType(ListingType.SELL);
        sold.setStatus(ListingStatus.SOLD);
        sold.setSellerUsername("charlie");
        dataService.addListing(sold);

        Listing given = new Listing();
        given.setId("L_STAT_GIVEN");
        given.setTitle("Given Item");
        given.setType(ListingType.GIVEAWAY);
        given.setStatus(ListingStatus.GIVEN);
        given.setSellerUsername("diana");
        dataService.addListing(given);

        Node view = controller.createView();
        assertNotNull(view);
    }

    @Test
    @DisplayName("AdminNotificationsController: cards for all categories and types")
    void testAdminNotificationsControllerExtended() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminNotificationsController controller = new AdminNotificationsController(
                dataService,
                () -> refreshed.set(true),
                () -> {},
                () -> {},
                () -> {}
        );

        // Add notifications for all categories
        Notification notifRep = new Notification(
                "admin", "Report Filed", "Buyer reported item", "Reports", "R1", "REPORT"
        );
        notifRep.setId("N_TEST_REP");
        dataService.addNotification(notifRep);

        Notification notifList = new Notification(
                "admin", "Listing Submitted", "Seller submitted item", "Listings", "L1", "LISTING"
        );
        notifList.setId("N_TEST_LIST");
        dataService.addNotification(notifList);

        Notification notifRev = new Notification(
                "admin", "Review Flagged", "Seller flagged review", "Reviews", "REV1", "REVIEW"
        );
        notifRev.setId("N_TEST_REV");
        dataService.addNotification(notifRev);

        Notification notifUser = new Notification(
                "admin", "User Modified", "User deactivation audit", "User Accounts", "alice", "USER"
        );
        notifUser.setId("N_TEST_USER");
        dataService.addNotification(notifUser);

        Notification notifAnn = new Notification(
                "admin", "System Announcement", "Version 2.0 deployed", "Announcements", "SYS1", "UNKNOWN"
        );
        notifAnn.setId("N_TEST_ANN");
        dataService.addNotification(notifAnn);

        Node view = controller.createView();
        assertNotNull(view);
    }

    @Test
    @DisplayName("AdminUsersController: user cards across buyer, seller, admin, deactivated, deleted")
    void testAdminUsersControllerExtended() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminUsersController controller = new AdminUsersController(dataService, () -> refreshed.set(true));

        // Add deactivated and deleted users
        Buyer deactBuyer = new Buyer("deact_b", "Deactivated Buyer", "pass");
        deactBuyer.setAccountStatus(AccountStatus.DEACTIVATED);
        dataService.registerUser(deactBuyer);

        Seller deletedSeller = new Seller("del_s", "Deleted Seller", "pass");
        deletedSeller.setAccountStatus(AccountStatus.DELETED);
        dataService.registerUser(deletedSeller);

        Node view = controller.createView();
        assertNotNull(view);
    }

    @Test
    @DisplayName("AdminDealsController: deals across sale, loan, giveaway, completed, returned, removed")
    void testAdminDealsControllerExtended() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminDealsController controller = new AdminDealsController(dataService, () -> refreshed.set(true));

        // Completed sale
        Deal compSale = new Deal();
        compSale.setId("D_COMP_SALE");
        compSale.setType(DealType.SALE);
        compSale.setStatus(DealStatus.COMPLETED);
        compSale.setBuyerUsername("alice");
        compSale.setSellerUsername("charlie");
        compSale.setListingTitle("Calculus Book");
        compSale.setOfferPrice(150.0);
        dataService.addDeal(compSale);

        // Returned loan with review
        Deal retLoan = new Deal();
        retLoan.setId("D_RET_LOAN");
        retLoan.setType(DealType.LOAN);
        retLoan.setStatus(DealStatus.RETURNED);
        retLoan.setBuyerUsername("bob");
        retLoan.setSellerUsername("diana");
        retLoan.setListingTitle("Calculator");
        retLoan.setDueDate(LocalDateTime.now().minusDays(1));
        dataService.addDeal(retLoan);

        Review rev = new Review();
        rev.setId("REV_DEAL_1");
        rev.setDealId("D_RET_LOAN");
        rev.setBuyerUsername("bob");
        rev.setSellerUsername("diana");
        rev.setRating(5);
        rev.setComment("Worked perfectly");
        dataService.addReview(rev);

        // Completed giveaway with deleted buyer
        Deal compGive = new Deal();
        compGive.setId("D_COMP_GIVE");
        compGive.setType(DealType.GIVEAWAY);
        compGive.setStatus(DealStatus.COMPLETED);
        compGive.setBuyerUsername("deleted_ghost_buyer");
        compGive.setSellerUsername("charlie");
        compGive.setListingTitle("Free Notebook");
        dataService.addDeal(compGive);

        Node view = controller.createView();
        assertNotNull(view);
    }

    @Test
    @DisplayName("AdminRatingsController: review cards with 5 stars, 1 star, flagged, hidden, replies")
    void testAdminRatingsControllerExtended() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminRatingsController controller = new AdminRatingsController(dataService, () -> refreshed.set(true));

        Review revFlagged = new Review();
        revFlagged.setId("REV_FLAGGED");
        revFlagged.setSellerUsername("charlie");
        revFlagged.setBuyerUsername("alice");
        revFlagged.setRating(1);
        revFlagged.setComment("Defective item");
        revFlagged.setFlagged(true);
        revFlagged.setFlagReason("Unfair accusation");
        dataService.addReview(revFlagged);

        Review revHidden = new Review();
        revHidden.setId("REV_HIDDEN_1");
        revHidden.setSellerUsername("diana");
        revHidden.setBuyerUsername("bob");
        revHidden.setRating(2);
        revHidden.setComment("Hidden abusive content");
        revHidden.setHidden(true);
        dataService.addReview(revHidden);

        Review revWithReply = new Review();
        revWithReply.setId("REV_REPLY_1");
        revWithReply.setSellerUsername("charlie");
        revWithReply.setBuyerUsername("alice");
        revWithReply.setRating(5);
        revWithReply.setComment("Great!");
        revWithReply.setSellerReply("Thank you for your purchase!");
        dataService.addReview(revWithReply);

        Node view = controller.createView();
        assertNotNull(view);
    }

    @Test
    @DisplayName("AdminProfileController: constructors, createView, and cancel button")
    void testAdminProfileControllerExtended() {
        AtomicBoolean nameUpdated = new AtomicBoolean(false);
        AdminProfileController c1 = new AdminProfileController();
        AdminProfileController c2 = new AdminProfileController(name -> nameUpdated.set(true));
        AdminProfileController controller = new AdminProfileController(dataService, name -> nameUpdated.set(true));

        Node view = controller.createView();
        assertNotNull(view);

        // Find Cancel button
        if (view instanceof ScrollPane scroll && scroll.getContent() instanceof VBox container) {
            VBox form = (VBox) container.getChildren().get(0);
            HBox buttonBox = (HBox) form.getChildren().get(form.getChildren().size() - 1);
            Button cancelBtn = (Button) buttonBox.getChildren().get(0);
            assertEquals("Cancel", cancelBtn.getText());
            cancelBtn.fire();
        }

        // Test createView when logged out (falls back to SEEDED_ADMIN_USERNAME)
        SessionManager.logout();
        Node unauthView = controller.createView();
        assertNotNull(unauthView);
    }

    @Test
    @DisplayName("AdminListingsController: filter toolbar listeners and reset button")
    void testAdminListingsControllerToolbar() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminListingsController controller = new AdminListingsController(dataService, () -> refreshed.set(true));

        ScrollPane scroll = (ScrollPane) controller.createView();
        VBox root = (VBox) scroll.getContent();
        VBox toolbar = (VBox) root.getChildren().get(1);
        HBox topRow = (HBox) toolbar.getChildren().get(0);
        TextField searchField = (TextField) topRow.getChildren().get(2);

        HBox filterRow = (HBox) toolbar.getChildren().get(1);
        @SuppressWarnings("unchecked")
        ComboBox<String> statusCombo = (ComboBox<String>) filterRow.getChildren().get(1);
        @SuppressWarnings("unchecked")
        ComboBox<String> catCombo = (ComboBox<String>) filterRow.getChildren().get(2);
        @SuppressWarnings("unchecked")
        ComboBox<String> typeCombo = (ComboBox<String>) filterRow.getChildren().get(3);
        Button resetBtn = (Button) filterRow.getChildren().get(4);

        // Search text change listener
        searchField.setText("Textbook");
        assertTrue(refreshed.get());

        // Status combo
        statusCombo.setValue("LIVE");
        statusCombo.setValue("All Statuses");

        // Category combo
        catCombo.setValue("Books & Textbooks");
        catCombo.setValue("All Categories");

        // Type combo
        typeCombo.setValue("Sell");
        typeCombo.setValue("Lend");
        typeCombo.setValue("Give Away");
        typeCombo.setValue("All Types");

        // Reset button
        resetBtn.fire();
        assertEquals("All Statuses", statusCombo.getValue());
    }

    @Test
    @DisplayName("AdminNotificationsController: empty board, filters and mark all read")
    void testAdminNotificationsControllerToolbar() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminNotificationsController controller = new AdminNotificationsController(
                dataService, () -> refreshed.set(true), () -> {}, () -> {}, () -> {}
        );

        ScrollPane scroll = (ScrollPane) controller.createView();
        VBox root = (VBox) scroll.getContent();
        HBox filterBar = (HBox) root.getChildren().get(1);
        @SuppressWarnings("unchecked")
        ComboBox<String> catBox = (ComboBox<String>) filterBar.getChildren().get(1);
        TextField searchField = (TextField) filterBar.getChildren().get(2);
        CheckBox unreadCheck = (CheckBox) filterBar.getChildren().get(3);
        Button markAllBtn = (Button) filterBar.getChildren().get(5);
        Button refreshBtn = (Button) filterBar.getChildren().get(6);

        unreadCheck.setSelected(true);
        unreadCheck.fire();
        searchField.setText("report");
        catBox.setValue("All Notifications");
        catBox.setValue("User Reports");
        refreshBtn.fire();

        // Mark all as read first so markAllBtn doesn't pop up an alert
        dataService.getAdminNotificationService().markAllAsRead("admin");
        markAllBtn.fire();

        // Empty state
        dataService.getNotificationsFor("admin").clear();
        Node emptyView = controller.createView();
        assertNotNull(emptyView);
    }

    @Test
    @DisplayName("AdminUsersController: filter toolbar listeners and refresh button")
    void testAdminUsersControllerToolbar() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminUsersController controller = new AdminUsersController(dataService, () -> refreshed.set(true));

        ScrollPane scroll = (ScrollPane) controller.createView();
        VBox root = (VBox) scroll.getContent();
        HBox filterBar = (HBox) root.getChildren().get(0);
        TextField searchField = (TextField) filterBar.getChildren().get(0);
        @SuppressWarnings("unchecked")
        ComboBox<String> roleFilter = (ComboBox<String>) filterBar.getChildren().get(1);
        @SuppressWarnings("unchecked")
        ComboBox<String> statusFilter = (ComboBox<String>) filterBar.getChildren().get(2);
        Button refreshBtn = (Button) filterBar.getChildren().get(3);

        searchField.setText("alice");
        roleFilter.setValue("Buyer");
        roleFilter.setValue("Seller");
        roleFilter.setValue("All Roles");

        statusFilter.setValue("Active");
        statusFilter.setValue("Deactivated");
        statusFilter.setValue("Deleted");
        statusFilter.setValue("All Statuses");

        refreshBtn.fire();
    }

    @Test
    @DisplayName("AdminDealsController: filter toolbar, type, status, and clear")
    void testAdminDealsControllerToolbar() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminDealsController controller = new AdminDealsController(dataService, () -> refreshed.set(true));

        ScrollPane scroll = (ScrollPane) controller.createView();
        VBox root = (VBox) scroll.getContent();
        HBox filterBar = (HBox) root.getChildren().get(0);
        TextField searchField = (TextField) filterBar.getChildren().get(0);
        @SuppressWarnings("unchecked")
        ComboBox<String> typeFilter = (ComboBox<String>) filterBar.getChildren().get(1);
        @SuppressWarnings("unchecked")
        ComboBox<String> statusFilter = (ComboBox<String>) filterBar.getChildren().get(2);
        Button clearBtn = (Button) filterBar.getChildren().get(5);
        Button refreshBtn = (Button) filterBar.getChildren().get(6);

        searchField.setText("Calculus");
        typeFilter.setValue("Sale");
        typeFilter.setValue("Loan");
        typeFilter.setValue("Giveaway");
        typeFilter.setValue("All Types");

        statusFilter.setValue("Active");
        statusFilter.setValue("Completed");
        statusFilter.setValue("Returned");
        statusFilter.setValue("All Statuses");

        clearBtn.fire();
        refreshBtn.fire();
    }

    @Test
    @DisplayName("AdminRatingsController: filter toolbar, scores, states, apply and reset")
    void testAdminRatingsControllerToolbar() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminRatingsController controller = new AdminRatingsController(dataService, () -> refreshed.set(true));

        ScrollPane scroll = (ScrollPane) controller.createView();
        VBox root = (VBox) scroll.getContent();
        HBox filterBar = (HBox) root.getChildren().get(1);
        TextField userQueryField = (TextField) filterBar.getChildren().get(0);
        @SuppressWarnings("unchecked")
        ComboBox<String> scoreFilter = (ComboBox<String>) filterBar.getChildren().get(1);
        @SuppressWarnings("unchecked")
        ComboBox<String> stateFilter = (ComboBox<String>) filterBar.getChildren().get(2);
        Button applyFilterBtn = (Button) filterBar.getChildren().get(5);
        Button resetFilterBtn = (Button) filterBar.getChildren().get(6);
        Button refreshBtn = (Button) filterBar.getChildren().get(8);

        userQueryField.setText("charlie");
        scoreFilter.setValue("5 Stars");
        scoreFilter.setValue("1 Star");
        scoreFilter.setValue("All Scores");

        stateFilter.setValue("Flagged Only");
        stateFilter.setValue("Hidden Only");
        stateFilter.setValue("Visible Only");
        stateFilter.setValue("All Reviews");

        applyFilterBtn.fire();
        resetFilterBtn.fire();
        refreshBtn.fire();
    }
}
