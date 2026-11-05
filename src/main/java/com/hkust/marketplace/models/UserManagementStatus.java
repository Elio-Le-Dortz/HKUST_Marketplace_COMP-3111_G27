package com.hkust.marketplace.models;

/**
 * Outcome status codes for admin user management operations (Sprint 2 US-A03, UC 3.3).
 */
public enum UserManagementStatus {
    SUCCESS,
    INVALID_INPUT,
    UNAUTHORIZED,
    USER_NOT_FOUND,
    CANNOT_MODIFY_ADMIN,
    ACTIVE_DEALS_EXIST,
    ALREADY_IN_STATE
}
