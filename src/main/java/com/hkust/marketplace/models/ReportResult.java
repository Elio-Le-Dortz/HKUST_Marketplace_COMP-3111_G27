package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Result object returned when a user submits a report for a listing.
 * Supports Use Case 1.4 Alternative Flow A3 (Report Listing).
 */
public class ReportResult {

    private final boolean success;
    private final Report report;
    private final String errorMessage;

    private ReportResult(boolean success, Report report, String errorMessage) {
        this.success = success;
        this.report = report;
        this.errorMessage = errorMessage;
    }

    public static ReportResult success(Report report) {
        return new ReportResult(true, report, null);
    }

    public static ReportResult failure(String errorMessage) {
        return new ReportResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public Report getReport() {
        return report;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReportResult that = (ReportResult) o;
        return success == that.success
                && Objects.equals(report, that.report)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, report, errorMessage);
    }

    @Override
    public String toString() {
        return "ReportResult{" +
                "success=" + success +
                ", report=" + report +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
