package com.hkust.marketplace.models;

import java.util.List;

/**
 * Summary metrics of a buyer's deal history.
 */
public class DealHistorySummary {

    private final int totalCompletedDeals;
    private final int totalBought;
    private final int totalBorrowed;
    private final int totalReceived;
    private final double totalSpent;

    public DealHistorySummary(int totalCompletedDeals,
                              int totalBought,
                              int totalBorrowed,
                              int totalReceived,
                              double totalSpent) {
        this.totalCompletedDeals = totalCompletedDeals;
        this.totalBought = totalBought;
        this.totalBorrowed = totalBorrowed;
        this.totalReceived = totalReceived;
        this.totalSpent = totalSpent;
    }

    public static DealHistorySummary fromItems(List<DealHistoryItem> items) {
        if (items == null || items.isEmpty()) {
            return new DealHistorySummary(0, 0, 0, 0, 0.0);
        }

        int total = items.size();
        int bought = 0;
        int borrowed = 0;
        int received = 0;
        double spent = 0.0;

        for (DealHistoryItem item : items) {
            if (item.getDealType() == DealType.SALE) {
                bought++;
                if (item.getPrice() != null) {
                    spent += item.getPrice();
                }
            } else if (item.getDealType() == DealType.LOAN) {
                borrowed++;
            } else if (item.getDealType() == DealType.GIVEAWAY) {
                received++;
            }
        }

        return new DealHistorySummary(total, bought, borrowed, received, spent);
    }

    public int getTotalCompletedDeals() {
        return totalCompletedDeals;
    }

    public int getTotalBought() {
        return totalBought;
    }

    public int getTotalBorrowed() {
        return totalBorrowed;
    }

    public int getTotalReceived() {
        return totalReceived;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public String getFormattedTotalSpent() {
        return String.format("HK$ %.2f", totalSpent);
    }
}
