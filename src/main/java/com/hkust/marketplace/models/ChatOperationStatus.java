package com.hkust.marketplace.models;

/**
 * Status enumeration for chat operations across domain models and service layers (US-B06).
 */
public enum ChatOperationStatus {
    SUCCESS,
    EMPTY_MESSAGE,
    THREAD_NOT_FOUND,
    UNAUTHORIZED,
    LISTING_NOT_FOUND,
    LISTING_NOT_LIVE,
    INVALID_OFFER,
    SELF_CHAT_NOT_ALLOWED,
    NO_MATCHES
}
