package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminModularControllers Unit Tests (Finding 5 Modular Controllers)")
public class AdminModularControllersTest {

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
    @DisplayName("AdminUIUtils: readablePhoto validates existing files and rejects null/blank/directories")
    void testAdminUIUtilsReadablePhoto() throws IOException {
        assertNull(AdminUIUtils.readablePhoto(null));
        assertNull(AdminUIUtils.readablePhoto(""));
        assertNull(AdminUIUtils.readablePhoto("   "));

        Path tempDir = Files.createTempDirectory("test-admin-photos");
        assertNull(AdminUIUtils.readablePhoto(tempDir.toString()), "Directories should not be accepted as regular photos");

        Path tempFile = Files.createTempFile(tempDir, "photo", ".jpg");
        Path result = AdminUIUtils.readablePhoto(tempFile.toString());
        assertNotNull(result, "Existing file should be resolved to Path");
        assertEquals(tempFile, result);

        Files.deleteIfExists(tempFile);
        assertNull(AdminUIUtils.readablePhoto(tempFile.toString()), "Deleted file should no longer resolve");
        Files.deleteIfExists(tempDir);
    }

    @Test
    @DisplayName("AdminUIUtils: listingThumbnail creates fallback stack when no image cover")
    void testAdminUIUtilsListingThumbnail() {
        Listing listing = new Listing();
        listing.setTitle("Test Item");
        listing.setType(ListingType.LEND);

        StackPane pane = AdminUIUtils.listingThumbnail(listing);
        assertNotNull(pane);
        assertEquals(AdminUIUtils.CARD_THUMBNAIL_SIZE, pane.getMinWidth());
        assertEquals(AdminUIUtils.CARD_THUMBNAIL_SIZE, pane.getMinHeight());
    }

    @Test
    @DisplayName("AdminUIUtils: createMetricCard generates styled card VBox")
    void testAdminUIUtilsMetricCard() {
        VBox card = AdminUIUtils.createMetricCard("Total Users", "42", "#003974", "Active accounts");
        assertNotNull(card);
        assertTrue(card.getStyleClass().contains("metric-card"));
        assertEquals(3, card.getChildren().size());
    }

    @Test
    @DisplayName("AdminReviewController: Instantiation and createView return non-null Node")
    void testAdminReviewControllerView() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminReviewController controller = new AdminReviewController(dataService, () -> refreshed.set(true));
        Node view = controller.createView();
        assertNotNull(view, "AdminReviewController.createView() should not return null");
    }

    @Test
    @DisplayName("AdminListingsController: Instantiation and createView return non-null Node")
    void testAdminListingsControllerView() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminListingsController controller = new AdminListingsController(dataService, () -> refreshed.set(true));
        Node view = controller.createView();
        assertNotNull(view, "AdminListingsController.createView() should not return null");
    }

    @Test
    @DisplayName("AdminUsersController: Instantiation and createView return non-null Node")
    void testAdminUsersControllerView() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminUsersController controller = new AdminUsersController(dataService, () -> refreshed.set(true));
        Node view = controller.createView();
        assertNotNull(view, "AdminUsersController.createView() should not return null");
    }

    @Test
    @DisplayName("AdminDealsController: Instantiation and createView return non-null Node")
    void testAdminDealsControllerView() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminDealsController controller = new AdminDealsController(dataService, () -> refreshed.set(true));
        Node view = controller.createView();
        assertNotNull(view, "AdminDealsController.createView() should not return null");
    }

    @Test
    @DisplayName("AdminRatingsController: Instantiation and createView return non-null Node")
    void testAdminRatingsControllerView() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminRatingsController controller = new AdminRatingsController(dataService, () -> refreshed.set(true));
        Node view = controller.createView();
        assertNotNull(view, "AdminRatingsController.createView() should not return null");
    }

    @Test
    @DisplayName("AdminNotificationsController: Instantiation and createView return non-null Node")
    void testAdminNotificationsControllerView() {
        AtomicBoolean refreshed = new AtomicBoolean(false);
        AdminNotificationsController controller = new AdminNotificationsController(
                dataService,
                () -> refreshed.set(true),
                () -> {},
                () -> {},
                () -> {}
        );
        Node view = controller.createView();
        assertNotNull(view, "AdminNotificationsController.createView() should not return null");
    }

    @Test
    @DisplayName("AdminProfileController: Instantiation and createView return non-null Node")
    void testAdminProfileControllerView() {
        AtomicReference<String> updatedName = new AtomicReference<>();
        AdminProfileController controller = new AdminProfileController(dataService, updatedName::set);
        Node view = controller.createView();
        assertNotNull(view, "AdminProfileController.createView() should not return null");
    }

    @Test
    @DisplayName("AdminDashboardController: Coordinator instantiation")
    void testAdminDashboardControllerInstance() {
        AdminDashboardController dashboard = new AdminDashboardController();
        assertNotNull(dashboard);
    }
}
