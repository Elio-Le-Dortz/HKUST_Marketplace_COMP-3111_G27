package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result object for searching chat thread history (US-B06 Subflow S3 &amp; Alternative Flow A3).
 */
public class ChatSearchResult {
    private final ChatOperationStatus status;
    private final String query;
    private final List<Message> matchingMessages;
    private final String message;

    private ChatSearchResult(ChatOperationStatus status, String query, List<Message> matchingMessages, String message) {
        this.status = status;
        this.query = query;
        this.matchingMessages = matchingMessages != null ? matchingMessages : Collections.emptyList();
        this.message = message;
    }

    public static ChatSearchResult success(String query, List<Message> matchingMessages) {
        int count = matchingMessages != null ? matchingMessages.size() : 0;
        String msg = "Found " + count + " matching message" + (count == 1 ? "." : "s.");
        return new ChatSearchResult(ChatOperationStatus.SUCCESS, query, matchingMessages, msg);
    }

    public static ChatSearchResult noMatches(String query) {
        String msg = "No messages found matching '" + (query != null ? query : "") + "'.";
        return new ChatSearchResult(ChatOperationStatus.NO_MATCHES, query, Collections.emptyList(), msg);
    }

    public static ChatSearchResult failure(ChatOperationStatus status, String errorMessage) {
        return new ChatSearchResult(status, null, Collections.emptyList(), errorMessage);
    }

    public boolean isSuccess() {
        return status == ChatOperationStatus.SUCCESS;
    }

    public ChatOperationStatus getStatus() {
        return status;
    }

    public String getQuery() {
        return query;
    }

    public List<Message> getMatchingMessages() {
        return new ArrayList<>(matchingMessages);
    }

    public int getMatchCount() {
        return matchingMessages.size();
    }

    public String getMessage() {
        return message;
    }
}
