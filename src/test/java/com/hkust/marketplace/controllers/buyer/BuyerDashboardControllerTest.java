package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BuyerDashboardController Unit Tests")
public class BuyerDashboardControllerTest {

    private DummyDataService dataService;
    private Buyer testBuyer;

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

        testBuyer = (Buyer) dataService.findUser("alice");
        assertNotNull(testBuyer);
        SessionManager.login(testBuyer);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        dataService.resetData();
    }

    private void injectFields(BuyerDashboardController controller) throws Exception {
        setField(controller, "contentPane", new StackPane());
        setField(controller, "pageTitle", new Label());
        setField(controller, "welcomeLabel", new Label());
        setField(controller, "btnBrowse", new Button());
        setField(controller, "btnDeals", new Button());
        setField(controller, "btnHistory", new Button());
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
        Class<?> clazz = (target instanceof Class<?>) ? (Class<?>) target : target.getClass();
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
    @DisplayName("BuyerDashboardController: initialize, badge and unauthenticated flow")
    void testInitializeAndBadge() throws Exception {
        BuyerDashboardController controller = new BuyerDashboardController();
        injectFields(controller);

        controller.initialize();
        Label welcome = (Label) getField(controller, "welcomeLabel");
        assertTrue(welcome.getText().toLowerCase().contains("alice"));

        controller.updateNotificationSidebarBadge();

        // Logout and initialize throws SecurityException because no buyer is logged in
        SessionManager.logout();
        BuyerDashboardController unauth = new BuyerDashboardController();
        injectFields(unauth);
        assertThrows(SecurityException.class, unauth::initialize);
        assertThrows(SecurityException.class, unauth::updateNotificationSidebarBadge);
    }

    @Test
    @DisplayName("BuyerDashboardController: showBrowse, filters, price validation and sorting")
    void testShowBrowseAndFilters() throws Exception {
        BuyerDashboardController controller = new BuyerDashboardController();
        injectFields(controller);
        controller.initialize();

        // Browse fields should be initialized
        TextField searchField = (TextField) getField(controller, "searchField");
        ComboBox<String> catFilter = (ComboBox<String>) getField(controller, "categoryFilter");
        ComboBox<String> typeFilter = (ComboBox<String>) getField(controller, "typeFilter");
        ComboBox<String> condFilter = (ComboBox<String>) getField(controller, "conditionFilter");
        TextField minPrice = (TextField) getField(controller, "minPriceField");
        TextField maxPrice = (TextField) getField(controller, "maxPriceField");
        ComboBox<ListingSortOrder> sortCombo = (ComboBox<ListingSortOrder>) getField(controller, "sortOrderCombo");
        Label priceValidation = (Label) getField(controller, "priceValidationLabel");

        assertNotNull(searchField);
        assertNotNull(catFilter);

        // Keyword filter
        searchField.setText("Calculus");
        assertEquals("Calculus", searchField.getText());

        // Category filter
        catFilter.getSelectionModel().select("Books");
        catFilter.getSelectionModel().select("All Categories");

        // Type filter
        typeFilter.getSelectionModel().select("Sell");
        typeFilter.getSelectionModel().select("Lend");
        typeFilter.getSelectionModel().select("Give Away");
        typeFilter.getSelectionModel().select("All Types");

        // Condition filter
        condFilter.getSelectionModel().select("Like New");
        condFilter.getSelectionModel().select("All Conditions");

        // Price filtering branches: valid, non-numeric, negative, min > max
        minPrice.setText("10");
        maxPrice.setText("1000");
        assertFalse(priceValidation.isVisible());

        minPrice.setText("invalid_price");
        maxPrice.setText("abc");

        minPrice.setText("-5");
        assertTrue(priceValidation.isVisible());

        minPrice.setText("500");
        maxPrice.setText("100");
        assertTrue(priceValidation.isVisible());

        // Sort orders
        for (ListingSortOrder order : ListingSortOrder.values()) {
            sortCombo.getSelectionModel().select(order);
        }

        // Search with zero matches (Alternative Flow A1)
        searchField.setText("NON_EXISTENT_KEYWORD_MATCHING_ZERO_ITEMS_12345");

        // Reset filters
        invokeMethod(controller, "resetBrowseFilters");
        assertEquals("", searchField.getText());
        assertFalse(priceValidation.isVisible());

        // Zero live listings branch (empty marketplace)
        for (Listing l : dataService.getAllListings()) {
            if (l.getStatus() == ListingStatus.LIVE) {
                l.setStatus(ListingStatus.SOLD);
            }
        }
        invokeMethod(controller, "applyBrowseFilters");
    }

    @Test
    @DisplayName("BuyerDashboardController: showListingDetail across navigation origins and listing statuses")
    void testShowListingDetail() throws Exception {
        BuyerDashboardController controller = new BuyerDashboardController();
        injectFields(controller);
        controller.initialize();

        Listing liveListing = dataService.getAllListings().stream()
                .filter(l -> l.getStatus() == ListingStatus.LIVE)
                .findFirst()
                .orElse(null);
        assertNotNull(liveListing);

        // 1. showListingDetail from various return views
        controller.showListingDetail(liveListing, false, "BROWSE");
        controller.showListingDetail(liveListing, false, "DEALS");
        controller.showListingDetail(liveListing, false, "HISTORY");
        controller.showListingDetail(liveListing, false, "NOTIFICATIONS");

        // 2. Convenience overloads
        controller.showListingDetail(liveListing);
        controller.showListingDetail(liveListing, false);

        // 3. Null listing early return
        controller.showListingDetail(null);

        // 4. Listing detail for different types and statuses
        Listing lendListing = new Listing();
        lendListing.setId("L_TEST_LEND");
        lendListing.setTitle("Lend Drill");
        lendListing.setType(ListingType.LEND);
        lendListing.setStatus(ListingStatus.LENT);
        lendListing.setSellerUsername("charlie");
        dataService.addListing(lendListing);
        controller.showListingDetail(lendListing, false, "DEALS");

        Listing soldListing = new Listing();
        soldListing.setId("L_TEST_SOLD");
        soldListing.setTitle("Sold Lamp");
        soldListing.setType(ListingType.SELL);
        soldListing.setStatus(ListingStatus.SOLD);
        soldListing.setSellerUsername("charlie");
        dataService.addListing(soldListing);
        controller.showListingDetail(soldListing, false, "HISTORY");

        Listing giveListing = new Listing();
        giveListing.setId("L_TEST_GIVE");
        giveListing.setTitle("Free Mug");
        giveListing.setType(ListingType.GIVEAWAY);
        giveListing.setStatus(ListingStatus.GIVEN);
        giveListing.setSellerUsername("diana");
        dataService.addListing(giveListing);
        controller.showListingDetail(giveListing, false, "DEALS");

        Listing rejectedListing = new Listing();
        rejectedListing.setId("L_TEST_REJ");
        rejectedListing.setTitle("Rejected Item");
        rejectedListing.setStatus(ListingStatus.REJECTED);
        rejectedListing.setSellerUsername("charlie");
        dataService.addListing(rejectedListing);
        controller.showListingDetail(rejectedListing, false, "BROWSE");
    }

    @Test
    @DisplayName("BuyerDashboardController: showMyDeals, showHistory, showNotifications, showProfile")
    void testBuyerScreens() throws Exception {
        BuyerDashboardController controller = new BuyerDashboardController();
        injectFields(controller);
        controller.initialize();

        // 1. showMyDeals
        invokeMethod(controller, "showMyDeals");

        // 2. showHistory
        controller.showHistory();

        // 3. showNotifications
        controller.showNotifications();

        // 4. showProfile
        invokeMethod(controller, "showProfile");
    }

    @Test
    @DisplayName("BuyerDashboardController: renderChatThread, message sending and chat search")
    void testRenderChatThread() throws Exception {
        BuyerDashboardController controller = new BuyerDashboardController();
        injectFields(controller);
        controller.initialize();

        Listing sellListing = dataService.getAllListings().stream()
                .filter(l -> l.getType() == ListingType.SELL && l.getStatus() == ListingStatus.LIVE)
                .findFirst()
                .orElse(null);
        assertNotNull(sellListing);

        ChatThread thread = new ChatThread(sellListing.getId(), sellListing.getTitle(), sellListing.getSellerUsername(), "alice");
        thread.setId("THREAD_BUYER_TEST");

        // 1. Empty thread
        controller.renderChatThread(thread, sellListing, "BROWSE", Collections.emptySet(), "", null, false);

        // 2. Thread with messages
        Message m1 = new Message("THREAD_BUYER_TEST", "alice", "Hi, is this still available?");
        m1.setId("BM1");
        m1.setSentDate(LocalDateTime.now().minusHours(2));

        Message m2 = new Message("THREAD_BUYER_TEST", sellListing.getSellerUsername(), "Yes, still available.");
        m2.setId("BM2");
        m2.setSentDate(LocalDateTime.now().minusHours(1));

        Message m3 = new Message("THREAD_BUYER_TEST", "alice", "Formal offer of HK$140.");
        m3.setId("BM3");
        m3.setOfferId("OFFER_BM3");
        m3.setSentDate(LocalDateTime.now());

        thread.setMessages(new ArrayList<>(List.of(m1, m2, m3)));

        // Render with highlighted matches and feedbacks
        controller.renderChatThread(thread, sellListing, "DEALS", Set.of("BM1"), "available", "Found 2 matching messages.", false);
        controller.renderChatThread(thread, sellListing, "NOTIFICATIONS", Collections.emptySet(), "xyz", "No messages found.", true);
        controller.renderChatThread(thread, sellListing, "HISTORY", Collections.emptySet(), "", null, false);
        controller.renderChatThread(thread, sellListing, "LISTING:BROWSE", Collections.emptySet(), "", null, false);

        // 3. Lend listing variant (shows formal borrow request button)
        Listing lendListing = dataService.getAllListings().stream()
                .filter(l -> l.getType() == ListingType.LEND)
                .findFirst()
                .orElse(null);
        if (lendListing != null) {
            ChatThread lendThread = new ChatThread(lendListing.getId(), lendListing.getTitle(), lendListing.getSellerUsername(), "alice");
            lendThread.setId("THREAD_LEND_BUYER");
            controller.renderChatThread(lendThread, lendListing, "BROWSE", Collections.emptySet(), "", null, false);
        }
    }
}
