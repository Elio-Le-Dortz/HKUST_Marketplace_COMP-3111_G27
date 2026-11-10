package com.hkust.marketplace.models;

/**
 * Result object for sending a chat message (US-B06 Subflow S1 &amp; Alternative Flow A1).
 */
public class SendMessageResult {
    private final ChatOperationStatus status;
    private final Message message;
    private final String errorMessage;

    private SendMessageResult(ChatOperationStatus status, Message message, String errorMessage) {
        this.status = status;
        this.message = message;
        this.errorMessage = errorMessage;
    }

    public static SendMessageResult success(Message message) {
        return new SendMessageResult(ChatOperationStatus.SUCCESS, message, null);
    }

    public static SendMessageResult emptyMessage(String errorMessage) {
        return new SendMessageResult(ChatOperationStatus.EMPTY_MESSAGE, null, errorMessage);
    }

    public static SendMessageResult failure(ChatOperationStatus status, String errorMessage) {
        return new SendMessageResult(status, null, errorMessage);
    }

    public boolean isSuccess() {
        return status == ChatOperationStatus.SUCCESS;
    }

    public ChatOperationStatus getStatus() {
        return status;
    }

    public Message getMessage() {
        return message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
