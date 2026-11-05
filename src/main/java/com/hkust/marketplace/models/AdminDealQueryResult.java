package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result wrapper for Admin Deal Oversight queries (Sprint 2 US-A05).
 * Encapsulates the execution status, the matching deal records, total count, and informative messages.
 */
public class AdminDealQueryResult {

    private final AdminDealQueryStatus status;
    private final List<AdminDealRecord> deals;
    private final int totalSystemDealsCount;
    private final String message;

    public AdminDealQueryResult(AdminDealQueryStatus status,
                                List<AdminDealRecord> deals,
                                int totalSystemDealsCount,
                                String message) {
        this.status = status;
        this.deals = deals != null ? Collections.unmodifiableList(new ArrayList<>(deals)) : Collections.emptyList();
        this.totalSystemDealsCount = totalSystemDealsCount;
        this.message = message != null ? message : "";
    }

    public static AdminDealQueryResult success(List<AdminDealRecord> deals, int totalSystemDealsCount) {
        return new AdminDealQueryResult(
                AdminDealQueryStatus.SUCCESS,
                deals,
                totalSystemDealsCount,
                "Successfully retrieved " + (deals != null ? deals.size() : 0) + " transaction records."
        );
    }

    public static AdminDealQueryResult noDealsYet() {
        return new AdminDealQueryResult(
                AdminDealQueryStatus.NO_DEALS_YET,
                Collections.emptyList(),
                0,
                "There are no deals in the system yet."
        );
    }

    public static AdminDealQueryResult noMatchingDeals(int totalSystemDealsCount) {
        return new AdminDealQueryResult(
                AdminDealQueryStatus.NO_MATCHING_DEALS,
                Collections.emptyList(),
                totalSystemDealsCount,
                "No deals match the specified search and filter criteria."
        );
    }

    public static AdminDealQueryResult invalidDateRange(String message, List<AdminDealRecord> previousDeals, int totalSystemDealsCount) {
        return new AdminDealQueryResult(
                AdminDealQueryStatus.INVALID_DATE_RANGE,
                previousDeals,
                totalSystemDealsCount,
                message != null ? message : "The selected date range is invalid (end date cannot precede start date)."
        );
    }

    public AdminDealQueryStatus getStatus() {
        return status;
    }

    public List<AdminDealRecord> getDeals() {
        return deals;
    }

    public int getTotalSystemDealsCount() {
        return totalSystemDealsCount;
    }

    public int getFilteredCount() {
        return deals.size();
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return status == AdminDealQueryStatus.SUCCESS;
    }

    public boolean isNoDealsYet() {
        return status == AdminDealQueryStatus.NO_DEALS_YET;
    }

    public boolean isNoMatchingDeals() {
        return status == AdminDealQueryStatus.NO_MATCHING_DEALS;
    }

    public boolean isInvalidDateRange() {
        return status == AdminDealQueryStatus.INVALID_DATE_RANGE;
    }
}
