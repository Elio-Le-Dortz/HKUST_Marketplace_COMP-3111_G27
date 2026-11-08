package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Unified representation of a deal or offer row displayed in the "My Deals" screen
 * (Use Case 1.6 / US-B07).
 */
public class MyDealsItem {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String id;
    private final String listingId;
    private final String listingTitle;
    private final String sellerUsername;
    private final String buyerUsername;
    private final ListingType listingType;
    private final String statusDisplay;
    private final LocalDateTime dueDate;
    private final Double price;
    private final LocalDateTime timestamp;
    private final Offer offer;
    private final Deal deal;
    private final String counterDetails;

    private MyDealsItem(String id, String listingId, String listingTitle, String sellerUsername,
                        String buyerUsername, ListingType listingType, String statusDisplay,
                        LocalDateTime dueDate, Double price, LocalDateTime timestamp,
                        Offer offer, Deal deal, String counterDetails) {
        this.id = id;
        this.listingId = listingId;
        this.listingTitle = listingTitle != null ? listingTitle : "Unknown Item";
        this.sellerUsername = sellerUsername;
        this.buyerUsername = buyerUsername;
        this.listingType = listingType;
        this.statusDisplay = statusDisplay;
        this.dueDate = dueDate;
        this.price = price;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.offer = offer;
        this.deal = deal;
        this.counterDetails = counterDetails;
    }

    /**
     * Creates an item row from a domain {@link Offer}.
     */
    public static MyDealsItem fromOffer(Offer offer) {
        if (offer == null) return null;

        ListingType type = ListingType.SELL;
        if (offer.getType() == OfferType.BORROW_REQUEST) {
            type = ListingType.LEND;
        } else if (offer.getType() == OfferType.CLAIM) {
            type = ListingType.GIVEAWAY;
        }

        String statusStr;
        String counterInfo = null;
        if (offer.getStatus() == OfferStatus.PENDING) {
            statusStr = "Pending";
        } else if (offer.getStatus() == OfferStatus.COUNTERED) {
            statusStr = "Countered";
            if (offer.getCounterOffer() != null) {
                if (offer.getCounterOffer().getPrice() != null) {
                    counterInfo = "Counter Price: HK$ " + String.format("%.2f", offer.getCounterOffer().getPrice());
                } else if (offer.getCounterOffer().getEndDate() != null) {
                    counterInfo = "Counter Due: " + offer.getCounterOffer().getEndDate().format(DATE_FMT);
                }
            }
        } else if (offer.getStatus() == OfferStatus.ACCEPTED) {
            statusStr = "Accepted";
        } else if (offer.getStatus() == OfferStatus.DECLINED) {
            statusStr = "Declined";
        } else if (offer.getStatus() == OfferStatus.WITHDRAWN) {
            statusStr = "Withdrawn";
        } else {
            statusStr = offer.getStatus() != null ? offer.getStatus().name() : "Unknown";
        }

        LocalDateTime due = offer.getEndDate();
        if (offer.getCounterOffer() != null && offer.getCounterOffer().getEndDate() != null) {
            due = offer.getCounterOffer().getEndDate();
        }

        return new MyDealsItem(
                offer.getId(),
                offer.getListingId(),
                offer.getListingTitle(),
                offer.getSellerUsername(),
                offer.getBuyerUsername(),
                type,
                statusStr,
                due,
                offer.getPrice(),
                offer.getCreatedDate(),
                offer,
                null,
                counterInfo
        );
    }

    /**
     * Creates an item row from a domain {@link Deal}.
     */
    public static MyDealsItem fromDeal(Deal deal) {
        if (deal == null) return null;

        ListingType type = ListingType.SELL;
        if (deal.getType() == DealType.LOAN) {
            type = ListingType.LEND;
        } else if (deal.getType() == DealType.GIVEAWAY) {
            type = ListingType.GIVEAWAY;
        }

        String statusStr;
        if (deal.getStatus() == DealStatus.ACTIVE) {
            statusStr = "Active";
        } else if (deal.getStatus() == DealStatus.COMPLETED) {
            statusStr = "Completed";
        } else if (deal.getStatus() == DealStatus.RETURNED) {
            statusStr = "Returned";
        } else {
            statusStr = deal.getStatus() != null ? deal.getStatus().name() : "Active";
        }

        LocalDateTime date = deal.getClosedDate() != null ? deal.getClosedDate() : deal.getStartDate();

        return new MyDealsItem(
                deal.getId(),
                deal.getListingId(),
                deal.getListingTitle(),
                deal.getSellerUsername(),
                deal.getBuyerUsername(),
                type,
                statusStr,
                deal.getDueDate(),
                deal.getOfferPrice(),
                date,
                null,
                deal,
                null
        );
    }

    public String getId() {
        return id;
    }

    public String getListingId() {
        return listingId;
    }

    public String getListingTitle() {
        return listingTitle;
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public String getBuyerUsername() {
        return buyerUsername;
    }

    public ListingType getListingType() {
        return listingType;
    }

    public String getTypeDisplay() {
        if (listingType == ListingType.LEND) return "Lend (Borrow)";
        if (listingType == ListingType.GIVEAWAY) return "Giveaway";
        return "Sell";
    }

    public String getStatusDisplay() {
        return statusDisplay;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public String getFormattedDueDate() {
        if (dueDate == null) return "-";
        return dueDate.format(DATE_FMT);
    }

    public Double getPrice() {
        return price;
    }

    public String getFormattedPrice() {
        if (price == null) return "-";
        return "HK$ " + String.format("%.2f", price);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Offer getOffer() {
        return offer;
    }

    public Deal getDeal() {
        return deal;
    }

    public String getCounterDetails() {
        return counterDetails;
    }

    public boolean isOffer() {
        return offer != null;
    }

    public boolean isDeal() {
        return deal != null;
    }

    public boolean canWithdraw() {
        return offer != null && offer.getStatus() == OfferStatus.PENDING;
    }

    public boolean canAcceptCounter() {
        return offer != null && offer.getStatus() == OfferStatus.COUNTERED;
    }

    public boolean canDeclineCounter() {
        return offer != null && offer.getStatus() == OfferStatus.COUNTERED;
    }

    public boolean canSelfReturn() {
        return deal != null && deal.getStatus() == DealStatus.ACTIVE && deal.getType() == DealType.LOAN;
    }

    public boolean canMarkComplete() {
        return deal != null && deal.getStatus() == DealStatus.ACTIVE && deal.getType() != DealType.LOAN;
    }

    public boolean canRate() {
        return deal != null && (deal.getStatus() == DealStatus.COMPLETED || deal.getStatus() == DealStatus.RETURNED) && !deal.isRated();
    }

    public boolean isRated() {
        return deal != null && deal.isRated();
    }

    public boolean canOpenListing() {
        return listingId != null && !listingId.trim().isEmpty();
    }

    public boolean canOpenChat() {
        return listingId != null && !listingId.trim().isEmpty() && sellerUsername != null;
    }

    public String getDetailsSummary() {
        if (counterDetails != null) {
            return counterDetails;
        }
        if (dueDate != null && listingType == ListingType.LEND) {
            return "Due: " + getFormattedDueDate();
        }
        if (price != null) {
            return getFormattedPrice();
        }
        return "-";
    }
}
