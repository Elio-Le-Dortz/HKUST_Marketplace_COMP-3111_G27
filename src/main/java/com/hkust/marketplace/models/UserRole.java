package com.hkust.marketplace.models;

/**
 * Enumeration of system user authorization roles for Role-Based Access Control (RBAC).
 */
public enum UserRole {

    /** Standard student/buyer account participating in purchases, borrowings, and claims. */
    BUYER,

    /** Seller account authorized to post listings and manage incoming trade requests. */
    SELLER,

    /** Platform administrator with moderation, user management, and audit privileges. */
    ADMIN
}
