package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.SellerChatService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Seller Controllers & Views Unit Tests")
public class SellerControllersTest {

    private DummyDataService dataService;
    private Seller testSeller;

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

        testSeller = (Seller) dataService.findUser("charlie");
        assertNotNull(testSeller);
        SessionManager.login(testSeller);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        dataService.resetData();
    }

    private void injectFields(SellerDashboardController controller) throws Exception {
        setField(controller, "contentPane", new StackPane());
        setField(controller, "pageTitle", new Label());
        setField(controller, "welcomeLabel", new Label());
        setField(controller, "btnListings", new Button());
        setField(controller, "btnOffers", new Button());
        setField(controller, "btnMessages", new Button());
        setField(controller, "btnCreate", new Button());
        setField(controller, "btnStats", new Button());
        setField(controller, "btnReviews", new Button());
        setField(controller, "btnNotifications", new Button());
        setField(controller, "btnProfile", new Button());
    }

    private static void setField(Object target, String name, Object val) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, val);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Object invokeMethod(Object target, String name, Object... args) throws Exception {
        Class<?> clazz = (target instanceof Class<?>) ? (Class<?>) target : (target != null ? target.getClass() : SellerDashboardController.class);
        Object instance = (target instanceof Class<?>) ? null : target;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(instance, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    @Test
    @DisplayName("SellerChatView: render empty thread, bubbles, search, send and session ended")
    void testSellerChatView() throws Exception {
        SellerChatService chatService = new SellerChatService(dataService);

        // 1. Thread setup
        ChatThread thread = dataService.getChatService().getOrCreateThread("L100", "alice");
        assertNotNull(thread);

        AtomicBoolean backTriggered = new AtomicBoolean(false);
        AtomicBoolean sessionEndedTriggered = new AtomicBoolean(false);

        SellerChatView view = new SellerChatView(
                chatService, thread, "charlie", "Alice Chan (@alice)",
                "Calculus Book · Offer: $150", "← Back to Offers",
                () -> backTriggered.set(true),
                () -> sessionEndedTriggered.set(true)
        );

        assertNotNull(view.getRoot());
        view.focusInput();

        // 2. Add diverse messages to thread: seller message, buyer message, formal offer
        Message sellerMsg = new Message(thread.getId(), "charlie", "Hi Alice, price is firm.");
        sellerMsg.setId("M1");
        sellerMsg.setSentDate(LocalDateTime.now().minusMinutes(10));

        Message buyerMsg = new Message(thread.getId(), "alice", "Can you do 140 instead?");
        buyerMsg.setId("M2");
        buyerMsg.setSentDate(LocalDateTime.now().minusMinutes(5));

        Message offerMsg = new Message(thread.getId(), "alice", "Formal offer: $150 submitted.");
        offerMsg.setId("M3");
        offerMsg.setOfferId("OFFER_FORMAL_1");
        offerMsg.setSentDate(LocalDateTime.now());

        thread.setMessages(new ArrayList<>(List.of(sellerMsg, buyerMsg, offerMsg)));

        // Re-render
        invokeMethod(view, "renderMessages");

        // 3. Search history - matches
        TextField searchField = (TextField) getField(view, "searchField");
        searchField.setText("Alice");
        invokeMethod(view, "search");
        Label searchFeedback = (Label) getField(view, "searchFeedback");
        assertTrue(searchFeedback.getText().contains("matching message"));

        // Search history - no matches
        searchField.setText("NOMATCHQUERY12345");
        invokeMethod(view, "search");
        assertTrue(searchFeedback.getText().contains("No messages match"));

        // Search history - empty query clears
        searchField.setText("");
        invokeMethod(view, "search");
        assertEquals("", searchFeedback.getText());

        // Clear search method
        invokeMethod(view, "clearSearch");

        // 4. Send message - empty message validation
        TextField input = (TextField) getField(view, "input");
        input.setText("");
        invokeMethod(view, "send");
        Label sendError = (Label) getField(view, "sendError");
        assertTrue(sendError.isVisible());

        // Send valid message
        input.setText("Deal confirmed, see you tomorrow!");
        invokeMethod(view, "send");

        // 5. Session ended handling
        SessionManager.logout();
        input.setText("Unauthenticated attempt");
        invokeMethod(view, "send");
        assertTrue(sessionEndedTriggered.get());

        // Search with session ended
        invokeMethod(view, "search");
    }

    @Test
    @DisplayName("SellerDashboardController: initialize, navigation, stats, reviews, notifications, offers")
    void testSellerDashboardControllerScreens() throws Exception {
        SellerDashboardController controller = new SellerDashboardController();
        injectFields(controller);

        // 1. Initialize with logged-in seller
        controller.initialize();
        Label welcomeLabel = (Label) getField(controller, "welcomeLabel");
        assertTrue(welcomeLabel.getText().toLowerCase().contains("charlie"));

        // 2. Navigation through all dashboard views
        invokeMethod(controller, "showMyListings");
        invokeMethod(controller, "showStats");
        invokeMethod(controller, "showReviews");
        invokeMethod(controller, "showNotifications");
        invokeMethod(controller, "showOffers");
        invokeMethod(controller, "showMessages");
        invokeMethod(controller, "showProfile");
        invokeMethod(controller, "showCreateListing");

        // 3. showMyListings with focus ID
        Listing firstListing = dataService.getAllListings().stream()
                .filter(l -> "charlie".equals(l.getSellerUsername()))
                .findFirst()
                .orElse(null);
        if (firstListing != null) {
            invokeMethod(controller, "showMyListings", firstListing.getId());
        }

        // 4. showReviews with focus ID
        Review firstReview = dataService.getAllReviews().stream()
                .filter(r -> "charlie".equals(r.getSellerUsername()))
                .findFirst()
                .orElse(null);
        if (firstReview != null) {
            invokeMethod(controller, "showReviews", firstReview.getId());
        }

        // 5. Empty states when seller has zero data
        Seller ghostSeller = new Seller("ghost_seller", "Ghost Seller", "pass123");
        dataService.registerUser(ghostSeller);
        SessionManager.login(ghostSeller);

        invokeMethod(controller, "showMyListings");
        invokeMethod(controller, "showStats");
        invokeMethod(controller, "showReviews");
        invokeMethod(controller, "showNotifications");
        invokeMethod(controller, "showOffers");
        invokeMethod(controller, "showMessages");

        // 6. Initialize without session redirects to login
        SessionManager.logout();
        SellerDashboardController unauthController = new SellerDashboardController();
        injectFields(unauthController);
        unauthController.initialize();
    }

    @Test
    @DisplayName("SellerDashboardController: static helpers and thumbnail rendering")
    void testSellerDashboardControllerHelpers(@TempDir Path tempDir) throws Exception {
        Path imgFile = tempDir.resolve("sample.png");
        Files.write(imgFile, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0});

        // 1. statusName
        for (ListingStatus status : ListingStatus.values()) {
            assertNotNull(invokeMethod(null, "statusName", status));
        }

        // 2. statusNote
        Listing liveListing = new Listing();
        liveListing.setStatus(ListingStatus.LIVE);
        assertNull(invokeMethod(null, "statusNote", liveListing, null));

        Listing pendingListing = new Listing();
        pendingListing.setStatus(ListingStatus.PENDING_REVIEW);
        assertNotNull(invokeMethod(null, "statusNote", pendingListing, null));

        Listing rejectedListing = new Listing();
        rejectedListing.setStatus(ListingStatus.REJECTED);
        rejectedListing.setAdminReason("Inappropriate description");
        assertNotNull(invokeMethod(null, "statusNote", rejectedListing, null));

        Listing removedListing = new Listing();
        removedListing.setStatus(ListingStatus.REMOVED);
        removedListing.setAdminReason("Violates marketplace policy");
        assertNotNull(invokeMethod(null, "statusNote", removedListing, null));

        Listing dealProgressListing = new Listing();
        dealProgressListing.setStatus(ListingStatus.RESERVED);
        MyListingResult dealCheck = MyListingResult.failure(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, dealProgressListing, "Deal in progress");
        assertNotNull(invokeMethod(null, "statusNote", dealProgressListing, dealCheck));

        Listing dealSoldListing = new Listing();
        dealSoldListing.setStatus(ListingStatus.SOLD);
        assertNotNull(invokeMethod(null, "statusNote", dealSoldListing, MyListingResult.failure(MyListingStatus.DEAL_IN_PROGRESS_OR_COMPLETED, dealSoldListing, "Deal sold")));

        // 3. myListingBadge
        assertNotNull(invokeMethod(null, "myListingBadge", liveListing));
        assertNotNull(invokeMethod(null, "myListingBadge", pendingListing));
        assertNotNull(invokeMethod(null, "myListingBadge", removedListing));

        // 4. listingThumbnail
        Listing lendListing = new Listing();
        lendListing.setType(ListingType.LEND);
        assertNotNull(invokeMethod(null, "listingThumbnail", lendListing));

        Listing giveListing = new Listing();
        giveListing.setType(ListingType.GIVEAWAY);
        assertNotNull(invokeMethod(null, "listingThumbnail", giveListing));

        Listing sellListing = new Listing();
        sellListing.setType(ListingType.SELL);
        assertNotNull(invokeMethod(null, "listingThumbnail", sellListing));

        sellListing.setPhotos(List.of(new Photo(imgFile.toString())));
        assertNotNull(invokeMethod(null, "listingThumbnail", sellListing));

        // 5. readablePhoto
        assertNull(invokeMethod(null, "readablePhoto", (String) null));
        assertNull(invokeMethod(null, "readablePhoto", ""));
        assertNull(invokeMethod(null, "readablePhoto", tempDir.resolve("non_existent.png").toString()));
        assertNotNull(invokeMethod(null, "readablePhoto", imgFile.toString()));

        // 6. openButtonText & notificationBadgeClass
        for (SellerNotificationCategory cat : SellerNotificationCategory.values()) {
            assertNotNull(invokeMethod(null, "notificationBadgeClass", cat));
        }

        Notification notifOffer = new Notification("charlie", "New Offer", "Msg", "OFFER", "O1", "OFFER");
        assertNotNull(invokeMethod(null, "openButtonText", notifOffer));

        Notification notifDeal = new Notification("charlie", "Deal Update", "Msg", "DEAL", "D1", "DEAL");
        assertNotNull(invokeMethod(null, "openButtonText", notifDeal));

        Notification notifListing = new Notification("charlie", "Listing Live", "Msg", "LISTING", "L100", "LISTING");
        assertNotNull(invokeMethod(null, "openButtonText", notifListing));

        Notification notifReview = new Notification("charlie", "New Review", "Msg", "REVIEW", "R1", "REVIEW");
        assertNotNull(invokeMethod(null, "openButtonText", notifReview));
    }

    @Test
    @DisplayName("ListingForm: type toggles, photo uploads, field validation and edit mode")
    void testListingForm(@TempDir Path tempDir) throws Exception {
        Path img1 = tempDir.resolve("img1.png");
        Path img2 = tempDir.resolve("img2.png");
        byte[] validPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};
        Files.write(img1, validPng);
        Files.write(img2, validPng);

        SellerDashboardController controller = new SellerDashboardController();
        injectFields(controller);

        // 1. Show create listing -> initializes listingForm
        invokeMethod(controller, "showCreateListing");
        Object form = getField(controller, "listingForm");
        assertNotNull(form);

        // Check view
        Node formView = (Node) invokeMethod(form, "getView");
        assertNotNull(formView);

        // Check hasInput initially false
        assertFalse((Boolean) invokeMethod(form, "hasInput"));

        // 2. Switch types: SELL, LEND, GIVEAWAY
        invokeMethod(form, "applyType", ListingType.SELL);
        invokeMethod(form, "applyType", ListingType.LEND);
        invokeMethod(form, "applyType", ListingType.GIVEAWAY);

        // 3. Add photos
        invokeMethod(form, "addPhotoFiles", List.of(img1, img2));
        assertTrue((Boolean) invokeMethod(form, "hasInput"));

        // 4. Mark invalid, clear error, clear all errors
        invokeMethod(form, "markInvalid", ListingField.TITLE, List.of("Title is required"));
        invokeMethod(form, "clearError", ListingField.TITLE);
        invokeMethod(form, "markInvalid", ListingField.DESCRIPTION, List.of("Description is required"));
        invokeMethod(form, "clearAllErrors");

        // 5. Edit mode ListingForm with a clean listing that has no open offers
        Listing cleanListing = new Listing();
        cleanListing.setId("L_CLEAN_EDIT");
        cleanListing.setTitle("Clean Item For Edit");
        cleanListing.setSellerUsername("charlie");
        cleanListing.setStatus(ListingStatus.LIVE);
        cleanListing.setType(ListingType.SELL);
        dataService.addListing(cleanListing);

        invokeMethod(controller, "handleEditListing", cleanListing);
        Object editForm = getField(controller, "listingForm");
        assertNotNull(editForm);
        assertNotNull(invokeMethod(editForm, "getView"));
    }
}
