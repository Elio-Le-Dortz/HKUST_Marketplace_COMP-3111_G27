package com.hkust.marketplace.models;

/**
 * Enumeration of moderation review statuses for buyer listing reports.
 */
public enum ReportStatus {

    /** Report submitted by buyer and awaiting administrator review. */
    OPEN,

    /** Report reviewed and handled by an administrator. */
    RESOLVED
}
