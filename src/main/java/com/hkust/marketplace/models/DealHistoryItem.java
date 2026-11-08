package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Representation of a completed or returned deal row displayed in the
 * Deal History screen (Sprint 2 US-B10, Use Case 1.9 in specs_buyer.tex).
 */
public class DealHistoryItem {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_SHORT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final String dealId;
    private final String listingId;
    private final String itemTitle;
    private final String sellerUsername;
    private final String sellerFullName;
    private final Category category;
    private final DealType dealType;
    private final DealStatus dealStatus;
    private final LocalDateTime dealDate;
    private final LocalDateTime closedDate;
    private final LocalDateTime dueDate;
    private final Double price;
    private final boolean rated;
    private final Deal deal;

    public DealHistoryItem(String dealId,
                           String listingId,
                           String itemTitle,
                           String sellerUsername,
                           String sellerFullName,
                           Category category,
                           DealType dealType,
                           DealStatus dealStatus,
                           LocalDateTime dealDate,
                           LocalDateTime closedDate,
                           LocalDateTime dueDate,
                           Double price,
                           boolean rated,
                           Deal deal) {
        this.dealId = dealId;
        this.listingId = listingId;
        this.itemTitle = itemTitle != null ? itemTitle : "Unknown Item";
        this.sellerUsername = sellerUsername != null ? sellerUsername : "";
        this.sellerFullName = sellerFullName != null ? sellerFullName : sellerUsername;
        this.category = category != null ? category : Category.OTHER;
        this.dealType = dealType != null ? dealType : DealType.SALE;
        this.dealStatus = dealStatus != null ? dealStatus : DealStatus.COMPLETED;
        this.dealDate = dealDate;
        this.closedDate = closedDate;
        this.dueDate = dueDate;
        this.price = price;
        this.rated = rated;
        this.deal = deal;
    }

    /**
     * Factory method creating a {@link DealHistoryItem} from domain entities.
     *
     * @param deal    the completed or returned deal
     * @param listing the associated listing (may be null if deleted)
     * @param seller  the seller user (may be null)
     * @return a populated DealHistoryItem, or null if deal is null
     */
    public static DealHistoryItem fromDeal(Deal deal, Listing listing, User seller) {
        if (deal == null) {
            return null;
        }

        String title = deal.getListingTitle();
        Category cat = Category.OTHER;
        if (listing != null) {
            if (listing.getTitle() != null && !listing.getTitle().trim().isEmpty()) {
                title = listing.getTitle();
            }
            if (listing.getCategory() != null) {
                cat = listing.getCategory();
            }
        }

        String sellerName = deal.getSellerUsername();
        if (seller != null && seller.getFullName() != null && !seller.getFullName().trim().isEmpty()) {
            sellerName = seller.getFullName();
        }

        return new DealHistoryItem(
                deal.getId(),
                deal.getListingId(),
                title,
                deal.getSellerUsername(),
                sellerName,
                cat,
                deal.getType(),
                deal.getStatus(),
                deal.getStartDate(),
                deal.getClosedDate(),
                deal.getDueDate(),
                deal.getOfferPrice(),
                deal.isRated(),
                deal
        );
    }

    // Getters
    public String getDealId() {
        return dealId;
    }

    public String getListingId() {
        return listingId;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public String getSellerFullName() {
        return sellerFullName;
    }

    public Category getCategory() {
        return category;
    }

    public DealType getDealType() {
        return dealType;
    }

    public DealStatus getDealStatus() {
        return dealStatus;
    }

    public LocalDateTime getDealDate() {
        return dealDate;
    }

    public LocalDateTime getClosedDate() {
        return closedDate;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public Double getPrice() {
        return price;
    }

    public boolean isRated() {
        return rated;
    }

    public Deal getDeal() {
        return deal;
    }

    public boolean canRate() {
        return !rated && (dealStatus == DealStatus.COMPLETED || dealStatus == DealStatus.RETURNED);
    }

    /**
     * Formal deal type display label (specs_buyer.tex: "Sale / Loan / Giveaway").
     */
    public String getTypeDisplayName() {
        if (dealType == DealType.LOAN) return "Loan";
        if (dealType == DealType.GIVEAWAY) return "Giveaway";
        return "Sale";
    }

    /**
     * Action-oriented deal type display (assignment.txt: "Bought / Borrowed / Received").
     */
    public String getActionTypeDisplayName() {
        if (dealType == DealType.LOAN) return "Borrowed";
        if (dealType == DealType.GIVEAWAY) return "Received";
        return "Bought";
    }

    /**
     * Outcome display (assignment.txt / specs_buyer.tex: "Completed / Returned").
     */
    public String getOutcomeDisplayName() {
        if (dealStatus == DealStatus.RETURNED) {
            return "Returned";
        }
        return "Completed";
    }

    public String getFormattedDealDate() {
        return dealDate != null ? dealDate.format(DATE_FMT) : "—";
    }

    public String getFormattedDealDateShort() {
        return dealDate != null ? dealDate.format(DATE_SHORT_FMT) : "—";
    }

    public String getFormattedClosedDate() {
        return closedDate != null ? closedDate.format(DATE_FMT) : "—";
    }

    public String getFormattedClosedDateShort() {
        return closedDate != null ? closedDate.format(DATE_SHORT_FMT) : "—";
    }

    public String getFormattedPrice() {
        if (price != null && dealType == DealType.SALE) {
            return String.format(Locale.ENGLISH, "HK$ %.2f", price);
        } else if (dealType == DealType.GIVEAWAY) {
            return "Free";
        } else if (dealType == DealType.LOAN) {
            return "Loan";
        }
        return "—";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DealHistoryItem that = (DealHistoryItem) o;
        return Objects.equals(dealId, that.dealId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dealId);
    }

    @Override
    public String toString() {
        return "DealHistoryItem{" +
                "dealId='" + dealId + '\'' +
                ", itemTitle='" + itemTitle + '\'' +
                ", sellerUsername='" + sellerUsername + '\'' +
                ", category=" + category +
                ", dealType=" + dealType +
                ", dealStatus=" + dealStatus +
                ", dealDate=" + dealDate +
                ", closedDate=" + closedDate +
                ", rated=" + rated +
                '}';
    }
}
