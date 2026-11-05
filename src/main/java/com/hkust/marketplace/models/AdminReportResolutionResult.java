package com.hkust.marketplace.models;

/**
 * Result wrapper for admin report resolution operations (Sprint 2 US-A06, Alternative Flow A3).
 */
public class AdminReportResolutionResult {

    private final AdminReportResolutionStatus status;
    private final String message;
    private final Report report;

    private AdminReportResolutionResult(AdminReportResolutionStatus status, String message, Report report) {
        this.status = status;
        this.message = message;
        this.report = report;
    }

    public static AdminReportResolutionResult success(String message, Report report) {
        return new AdminReportResolutionResult(AdminReportResolutionStatus.SUCCESS, message, report);
    }

    public static AdminReportResolutionResult failure(AdminReportResolutionStatus status, String message) {
        return new AdminReportResolutionResult(status, message, null);
    }

    public boolean isSuccess() {
        return status == AdminReportResolutionStatus.SUCCESS;
    }

    public AdminReportResolutionStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Report getReport() {
        return report;
    }

    @Override
    public String toString() {
        return "AdminReportResolutionResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", report=" + (report != null ? report.getId() : "null") +
                '}';
    }
}
