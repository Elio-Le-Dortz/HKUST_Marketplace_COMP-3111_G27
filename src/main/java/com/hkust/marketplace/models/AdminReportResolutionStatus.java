package com.hkust.marketplace.models;

/**
 * Outcome status codes for admin report resolution operations (Sprint 2 US-A06, Alternative Flow A3).
 */
public enum AdminReportResolutionStatus {
    SUCCESS,
    REPORT_NOT_FOUND,
    ALREADY_RESOLVED,
    UNAUTHORIZED,
    INVALID_INPUT
}
