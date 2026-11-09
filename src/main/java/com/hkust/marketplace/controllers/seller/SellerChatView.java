package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.ChatOperationStatus;
import com.hkust.marketplace.models.ChatSearchResult;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.SendMessageResult;
import com.hkust.marketplace.services.SellerChatService;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The chat screen of the Seller Portal: a conversation between the logged-in Seller and a buyer
 * about one of the Seller's listings (use case 1.5 "Chat About Listing", shared with the Buyer
 * Portal). The Seller reads the whole history in chronological order, sends messages
 * (subflow S1, alternative flow A1) and searches the history for a keyword, the matching messages
 * being highlighted (subflow S3, alternative flow A3, F10).
 *
 * <p>The screen only displays and forwards: every action goes through {@link SellerChatService},
 * which checks the Seller session and that the conversation is about one of the Seller's
 * listings. It was moved out of {@link SellerDashboardController} so that the dashboard does not
 * keep growing (review of the seller part, finding 2). The bubbles and the search bar have the
 * same look as the Buyer Portal's chat.</p>
 */
final class SellerChatView {

    /** Format of the time of a message, in English whatever the default locale. */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);
    /** Style of the search feedback when messages were found. */
    private static final String FOUND_STYLE = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;";
    /** Style of the search feedback when nothing was found or the search failed. */
    private static final String NOT_FOUND_STYLE = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #d32f2f;";

    /** Sends and searches the messages of the logged-in Seller. */
    private final SellerChatService chatService;
    /** The conversation shown. */
    private final ChatThread thread;
    /** Username of the logged-in Seller, whose messages are aligned to the right. */
    private final String sellerUsername;
    /** Name of the buyer, shown in the heading and in the empty-thread prompt. */
    private final String buyerName;
    /** Shown when the Seller session has ended, for example the login screen. */
    private final Runnable onSessionEnded;

    /** The whole screen. */
    private final VBox root = new VBox(12);
    /** The message bubbles. */
    private final VBox messages = new VBox(10);
    /** Scrolls the message bubbles. */
    private final ScrollPane scroll = new ScrollPane(messages);
    /** Keyword typed by the Seller. */
    private final TextField searchField = new TextField();
    /** Number of matches, or "no messages match" (alternative flow A3). */
    private final Label searchFeedback = new Label();
    /** Message typed by the Seller. */
    private final TextField input = new TextField();
    /** Error of the last message sent, for example an empty message (alternative flow A1). */
    private final Label sendError = SellerDashboardController.dialogErrorLabel();
    /** Identifiers of the messages matching the current search, highlighted. */
    private final Set<String> matches = new HashSet<>();

    /**
     * Builds the chat screen of a conversation.
     *
     * @param chatService    sends and searches the Seller's messages
     * @param thread         the conversation to show
     * @param sellerUsername username of the logged-in Seller
     * @param buyerName      name of the buyer shown in the heading, for example {@code Alice Chan (@alice)}
     * @param about          line under the heading, for example the listing and the terms of an offer
     * @param backText       text of the button that leaves the chat
     * @param back           the screen shown by that button
     * @param onSessionEnded what to do when the Seller session has ended
     * @throws NullPointerException if an argument is {@code null}
     */
    SellerChatView(SellerChatService chatService, ChatThread thread, String sellerUsername, String buyerName,
                   String about, String backText, Runnable back, Runnable onSessionEnded) {
        this.chatService = Objects.requireNonNull(chatService, "chatService");
        this.thread = Objects.requireNonNull(thread, "thread");
        this.sellerUsername = Objects.requireNonNull(sellerUsername, "sellerUsername");
        this.buyerName = Objects.requireNonNull(buyerName, "buyerName");
        this.onSessionEnded = Objects.requireNonNull(onSessionEnded, "onSessionEnded");
        root.setPadding(new Insets(4));
        root.getChildren().addAll(topBar(Objects.requireNonNull(about, "about"),
                        Objects.requireNonNull(backText, "backText"), Objects.requireNonNull(back, "back")),
                searchBar(), messagePane(), sendError, inputRow());
        renderMessages();
        scroll.setVvalue(1.0);
    }

    /**
     * Returns the screen, to put in the dashboard's content pane.
     *
     * @return the root node of the screen
     */
    Node getRoot() {
        return root;
    }

    /**
     * Gives the keyboard focus to the message field, so the Seller can type at once.
     */
    void focusInput() {
        input.requestFocus();
    }

    // =====================================================================
    // Layout
    // =====================================================================

    /**
     * Builds the bar with the back button and the heading.
     *
     * @param about    line under the heading
     * @param backText text of the back button
     * @param back     the screen shown by the back button
     * @return the bar
     */
    private HBox topBar(String about, String backText, Runnable back) {
        Button backButton = SellerDashboardController.smallButton(backText, "btn-outline", back);
        Label heading = new Label("Chat with " + buyerName);
        heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        Label aboutLabel = new Label(about);
        aboutLabel.setStyle(SellerDashboardController.MUTED_STYLE);
        HBox bar = new HBox(12, backButton, new VBox(2, heading, aboutLabel));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /**
     * Builds the search bar (subflow S3): a keyword field, Search and Clear buttons, and the
     * feedback of the last search.
     *
     * @return the bar
     */
    private HBox searchBar() {
        searchField.setId("sellerChatSearchField");
        searchField.setPromptText("Search message history in this thread...");
        searchField.setOnAction(e -> search());
        HBox.setHgrow(searchField, Priority.ALWAYS);
        Button searchButton = SellerDashboardController.smallButton("🔍 Search", "btn-primary", this::search);
        searchButton.setId("sellerChatSearchButton");
        Button clearButton = SellerDashboardController.smallButton("Clear", "btn-outline", this::clearSearch);
        clearButton.setId("sellerChatClearButton");
        searchFeedback.setId("sellerChatSearchFeedback");
        HBox bar = new HBox(8, searchField, searchButton, clearButton, searchFeedback);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("chat-search-bar");
        bar.setStyle("-fx-background-color: #f8fafc; -fx-padding: 8 12; -fx-background-radius: 8;"
                + " -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-border-radius: 8;");
        return bar;
    }

    /**
     * Sets up the scrolling pane of the messages.
     *
     * @return the pane
     */
    private ScrollPane messagePane() {
        messages.setPadding(new Insets(14));
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(420);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        scroll.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        return scroll;
    }

    /**
     * Builds the row where the Seller types and sends a message (subflow S1).
     *
     * @return the row
     */
    private HBox inputRow() {
        input.setId("sellerChatInput");
        input.setPromptText("Type your message to @" + thread.getBuyerUsername() + "...");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.textProperty().addListener((observable, oldText, newText) -> SellerDashboardController.hideError(sendError));
        input.setOnAction(e -> send());
        Button sendButton = new Button("Send ✉️");
        sendButton.setId("sellerChatSendButton");
        sendButton.getStyleClass().add("btn-primary");
        sendButton.setOnAction(e -> send());
        HBox row = new HBox(8, input, sendButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // =====================================================================
    // Actions
    // =====================================================================

    /**
     * Sends the typed message (subflow S1). An empty message is refused (alternative flow A1).
     * The active search, if any, is run again so a new matching message is highlighted too.
     */
    private void send() {
        SendMessageResult sent;
        try {
            sent = chatService.sendMessage(thread.getId(), input.getText());
        } catch (SecurityException e) {
            onSessionEnded.run();
            return;
        }
        if (!sent.isSuccess()) {
            SellerDashboardController.showError(sendError, sent.getStatus() == ChatOperationStatus.EMPTY_MESSAGE
                    ? "Message cannot be empty." : sent.getErrorMessage());
            return;
        }
        input.clear();
        if (matches.isEmpty() && searchField.getText().isBlank()) {
            renderMessages();
        } else {
            search();
        }
        Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    /**
     * Searches the history for the typed keyword (subflow S3) and highlights the matching
     * messages, or says that no message matches (alternative flow A3). A blank keyword clears the
     * search.
     */
    private void search() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        if (query.isEmpty()) {
            clearSearch();
            return;
        }
        ChatSearchResult result;
        try {
            result = chatService.searchMyChat(thread.getId(), query);
        } catch (SecurityException e) {
            onSessionEnded.run();
            return;
        }
        matches.clear();
        if (result.isSuccess()) {
            result.getMatchingMessages().forEach(message -> matches.add(message.getId()));
            int count = result.getMatchCount();
            showFeedback("Found " + count + " matching message" + (count == 1 ? "." : "s."), FOUND_STYLE);
        } else if (result.getStatus() == ChatOperationStatus.NO_MATCHES) {
            showFeedback("No messages match \"" + query + "\".", NOT_FOUND_STYLE);
        } else {
            showFeedback(result.getMessage(), NOT_FOUND_STYLE);
        }
        renderMessages();
        scrollToFirstMatch();
    }

    /**
     * Clears the keyword, the feedback and the highlights.
     */
    private void clearSearch() {
        searchField.clear();
        matches.clear();
        showFeedback("", NOT_FOUND_STYLE);
        renderMessages();
    }

    /**
     * Shows the feedback of a search.
     *
     * @param text  the feedback, empty for none
     * @param style its style
     */
    private void showFeedback(String text, String style) {
        searchFeedback.setText(text);
        searchFeedback.setStyle(style);
    }

    // =====================================================================
    // Messages
    // =====================================================================

    /**
     * Shows every message of the conversation in chronological order, highlighting those that
     * match the current search; an empty conversation shows a prompt to start it (use case 1.5,
     * alternative flow A2).
     */
    private void renderMessages() {
        messages.getChildren().clear();
        List<Message> history = thread.getMessages();
        if (!thread.hasMessages()) {
            messages.getChildren().add(SellerDashboardController.emptyStateCard("💬", "No messages yet",
                    "Start the conversation with " + buyerName + "."));
            return;
        }
        for (Message message : history) {
            messages.getChildren().add(bubble(message));
        }
    }

    /**
     * Builds the bubble of a message, aligned right for the Seller's own messages, and
     * highlighted when it matches the current search.
     *
     * @param message the message
     * @return the row holding the bubble
     */
    private HBox bubble(Message message) {
        boolean mine = message.getSenderUsername() != null && message.getSenderUsername().equalsIgnoreCase(sellerUsername);
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(520);
        String time = message.getSentDate() != null ? message.getSentDate().format(TIME_FORMAT) : "";
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
        if (matches.contains(message.getId())) {
            bubble.getStyleClass().add("chat-bubble-highlight");
            Label badge = new Label("★ SEARCH MATCH");
            badge.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #b45309;"
                    + " -fx-background-color: #fef08a; -fx-padding: 2 4; -fx-background-radius: 3;");
            bubble.getChildren().add(0, badge);
        }
        HBox row = new HBox(bubble);
        row.setId("sellerChatMessage-" + message.getId());
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Scrolls to the first highlighted message, once the bubbles are laid out.
     */
    private void scrollToFirstMatch() {
        Node first = null;
        for (Node row : messages.getChildren()) {
            String id = row.getId();
            if (id != null && matches.contains(id.substring("sellerChatMessage-".length()))) {
                first = row;
                break;
            }
        }
        if (first == null) {
            return;
        }
        Node target = first;
        Platform.runLater(() -> {
            scroll.applyCss();
            scroll.layout();
            double contentHeight = messages.getBoundsInLocal().getHeight();
            double viewportHeight = scroll.getViewportBounds().getHeight();
            if (contentHeight <= viewportHeight) {
                return;
            }
            Bounds bounds = messages.sceneToLocal(target.localToScene(target.getBoundsInLocal()));
            scroll.setVvalue(Math.max(0, Math.min(1, (bounds.getMinY() - 20) / (contentHeight - viewportHeight))));
        });
    }
}
