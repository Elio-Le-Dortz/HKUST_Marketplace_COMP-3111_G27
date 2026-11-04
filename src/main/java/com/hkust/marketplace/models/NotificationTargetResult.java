package com.hkust.marketplace.models;

/**
 * Result model representing the outcome of resolving a notification's related item.
 */
public class NotificationTargetResult {

    private final NotificationTargetStatus status;
    private final NotificationTargetType targetType;
    private final Object targetObject;
    private final String message;
    private final Notification notification;

    public NotificationTargetResult(NotificationTargetStatus status,
                                    NotificationTargetType targetType,
                                    Object targetObject,
                                    String message,
                                    Notification notification) {
        this.status = status;
        this.targetType = targetType;
        this.targetObject = targetObject;
        this.message = message;
        this.notification = notification;
    }

    public static NotificationTargetResult success(Notification notification,
                                                   NotificationTargetType targetType,
                                                   Object targetObject) {
        return new NotificationTargetResult(NotificationTargetStatus.SUCCESS, targetType, targetObject, null, notification);
    }

    public static NotificationTargetResult itemDeleted(Notification notification,
                                                       NotificationTargetType targetType,
                                                       String message) {
        return new NotificationTargetResult(NotificationTargetStatus.ITEM_DELETED, targetType, null, message, notification);
    }

    public static NotificationTargetResult noLinkedItem(Notification notification, String message) {
        return new NotificationTargetResult(NotificationTargetStatus.NO_LINKED_ITEM, NotificationTargetType.NONE, null, message, notification);
    }

    public static NotificationTargetResult notFound(String message) {
        return new NotificationTargetResult(NotificationTargetStatus.NOTIFICATION_NOT_FOUND, NotificationTargetType.NONE, null, message, null);
    }

    public NotificationTargetStatus getStatus() {
        return status;
    }

    public NotificationTargetType getTargetType() {
        return targetType;
    }

    public Object getTargetObject() {
        return targetObject;
    }

    public String getMessage() {
        return message;
    }

    public Notification getNotification() {
        return notification;
    }

    public boolean isSuccess() {
        return status == NotificationTargetStatus.SUCCESS;
    }

    public boolean isItemDeleted() {
        return status == NotificationTargetStatus.ITEM_DELETED;
    }
}
