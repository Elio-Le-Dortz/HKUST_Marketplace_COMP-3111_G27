package com.hkust.marketplace.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Domain projection model representing a single transaction record for Admin Deal Oversight
 * (Sprint 2 US-A05, Task 3.5 in specs_admin.tex).
 *
 * <p>Enriches a {@link Deal} with its linked {@link Listing}, {@link User} (buyer and seller),
 * accepted {@link Offer}, and buyer {@link Review}. Gracefully handles removed listings and
 * deleted accounts per Alternative Flow A5.</p>
 */
public class AdminDealRecord {

    public static final String DELETED_USER_PLACEHOLDER = "Deleted user";
    public static final String REMOVED_LISTING_SUFFIX = " [Removed]";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final Deal deal;
    private final Listing listing;
    private final User buyer;
    private final User seller;
    private final Offer offer;
    private final Review review;

    public AdminDealRecord(Deal deal, Listing listing, User buyer, User seller, Offer offer, Review review) {
        this.deal = Objects.requireNonNull(deal, "Deal cannot be null");
        this.listing = listing;
        this.buyer = buyer;
        this.seller = seller;
        this.offer = offer;
        this.review = review;
    }

    public Deal getDeal() {
        return deal;
    }

    public Listing getListing() {
        return listing;
    }

    public User getBuyer() {
        return buyer;
    }

    public User getSeller() {
        return seller;
    }

    public Offer getOffer() {
        return offer;
    }

    public Review getReview() {
        return review;
    }

    // ----------------- Deal Attributes -----------------

    public String getDealId() {
        return deal.getId() != null ? deal.getId() : "";
    }

    public String getListingId() {
        return deal.getListingId();
    }

    /**
     * Determines whether the associated listing has been deleted or marked removed (Alternative Flow A5).
     */
    public boolean isListingRemoved() {
        if (listing == null) {
            return true;
        }
        return listing.getStatus() == ListingStatus.REMOVED;
    }

    /**
     * Item title from the active listing or historical deal snapshot.
     */
    public String getItemTitle() {
        if (listing != null && listing.getTitle() != null && !listing.getTitle().isBlank()) {
            return listing.getTitle();
        }
        if (deal.getListingTitle() != null && !deal.getListingTitle().isBlank()) {
            return deal.getListingTitle();
        }
        return "Unknown Listing";
    }

    /**
     * Item title formatted for oversight display, appended with "[Removed]" if removed (Alternative Flow A5).
     */
    public String getItemTitleDisplay() {
        String title = getItemTitle();
        if (isListingRemoved()) {
            return title.endsWith(REMOVED_LISTING_SUFFIX) ? title : title + REMOVED_LISTING_SUFFIX;
        }
        return title;
    }

    public String getRawBuyerUsername() {
        return deal.getBuyerUsername() != null ? deal.getBuyerUsername() : "";
    }

    public String getRawSellerUsername() {
        return deal.getSellerUsername() != null ? deal.getSellerUsername() : "";
    }

    public boolean isBuyerDeleted() {
        if (buyer == null) {
            return true;
        }
        return buyer.isDeleted();
    }

    public boolean isSellerDeleted() {
        if (seller == null) {
            return true;
        }
        return seller.isDeleted();
    }

    /**
     * Displays buyer username or "Deleted user" per Alternative Flow A5.
     */
    public String getBuyerUsernameDisplay() {
        if (isBuyerDeleted()) {
            return DELETED_USER_PLACEHOLDER;
        }
        return "@" + getRawBuyerUsername();
    }

    /**
     * Displays buyer full representation (username + full name) or "Deleted user".
     */
    public String getBuyerFullNameDisplay() {
        if (isBuyerDeleted()) {
            return DELETED_USER_PLACEHOLDER;
        }
        if (buyer != null && buyer.getFullName() != null && !buyer.getFullName().isBlank()) {
            return buyer.getFullName() + " (@" + getRawBuyerUsername() + ")";
        }
        return "@" + getRawBuyerUsername();
    }

    /**
     * Displays seller username or "Deleted user" per Alternative Flow A5.
     */
    public String getSellerUsernameDisplay() {
        if (isSellerDeleted()) {
            return DELETED_USER_PLACEHOLDER;
        }
        return "@" + getRawSellerUsername();
    }

    /**
     * Displays seller full representation (username + full name) or "Deleted user".
     */
    public String getSellerFullNameDisplay() {
        if (isSellerDeleted()) {
            return DELETED_USER_PLACEHOLDER;
        }
        if (seller != null && seller.getFullName() != null && !seller.getFullName().isBlank()) {
            return seller.getFullName() + " (@" + getRawSellerUsername() + ")";
        }
        return "@" + getRawSellerUsername();
    }

    public DealType getDealType() {
        return deal.getType();
    }

    public String getDealTypeDisplay() {
        if (deal.getType() == null) {
            return "Unknown";
        }
        return switch (deal.getType()) {
            case SALE -> "Sale";
            case LOAN -> "Loan";
            case GIVEAWAY -> "Giveaway";
        };
    }

    public DealStatus getDealStatus() {
        return deal.getStatus();
    }

    public String getDealStatusDisplay() {
        if (deal.getStatus() == null) {
            return "Unknown";
        }
        return switch (deal.getStatus()) {
            case ACTIVE -> "Active";
            case COMPLETED -> "Completed";
            case RETURNED -> "Returned";
        };
    }

    public LocalDateTime getStartDate() {
        return deal.getStartDate();
    }

    public String getStartDateDisplay() {
        if (deal.getStartDate() == null) {
            return "-";
        }
        return deal.getStartDate().format(DATE_TIME_FORMAT);
    }

    /**
     * Returns the completion / return date, or the due date for an active loan
     * (Basic Flow Step 2).
     */
    public LocalDateTime getReturnOrCompleteDate() {
        if (deal.isLoan() && deal.isActive()) {
            return deal.getDueDate();
        }
        if (deal.getClosedDate() != null) {
            return deal.getClosedDate();
        }
        return deal.getDueDate();
    }

    public String getReturnOrCompleteDateDisplay() {
        LocalDateTime dt = getReturnOrCompleteDate();
        if (dt == null) {
            return "-";
        }
        if (deal.isLoan() && deal.isActive()) {
            return "Due: " + dt.format(DATE_FORMAT);
        }
        if (deal.isReturned()) {
            return "Returned: " + dt.format(DATE_FORMAT);
        }
        return "Completed: " + dt.format(DATE_FORMAT);
    }

    /**
     * Displays agreed price or borrow dates (Basic Flow Step 4).
     */
    public String getAgreedTermsDisplay() {
        if (deal.getType() == DealType.SALE) {
            Double price = deal.getOfferPrice();
            if (price == null && offer != null && offer.getPrice() != null) {
                price = offer.getPrice();
            }
            if (price == null && listing != null) {
                price = listing.getPrice();
            }
            return price != null ? String.format("$%.2f", price) : "N/A";
        } else if (deal.getType() == DealType.LOAN) {
            String startStr = deal.getStartDate() != null ? deal.getStartDate().format(DATE_FORMAT) : "Start";
            String dueStr = deal.getDueDate() != null ? deal.getDueDate().format(DATE_FORMAT) : "Due";
            return startStr + " to " + dueStr;
        } else if (deal.getType() == DealType.GIVEAWAY) {
            return "Free (Giveaway Item)";
        }
        return "N/A";
    }

    public boolean hasReview() {
        return review != null || deal.isRated();
    }

    public int getReviewRating() {
        return review != null ? review.getRating() : 0;
    }

    public String getReviewComment() {
        return review != null ? review.getComment() : "";
    }

    public String getReviewDateDisplay() {
        if (review != null && review.getCreatedAt() != null) {
            return review.getCreatedAt().format(DATE_FORMAT);
        }
        return "-";
    }

    public boolean isReviewHidden() {
        return review != null && review.isHidden();
    }
}
