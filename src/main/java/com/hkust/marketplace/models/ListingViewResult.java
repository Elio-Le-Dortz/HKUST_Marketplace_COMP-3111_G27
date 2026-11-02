package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Result object returned when a user attempts to view a listing's detail.
 * Captures status, listing domain entity, associated seller rating, and messages.
 */
public class ListingViewResult {

    private final ListingViewStatus status;
    private final Listing listing;
    private final SellerRatingInfo sellerRating;
    private final String message;

    private ListingViewResult(ListingViewStatus status, Listing listing, SellerRatingInfo sellerRating, String message) {
        this.status = status;
        this.listing = listing;
        this.sellerRating = sellerRating;
        this.message = message;
    }

    public static ListingViewResult success(Listing listing, SellerRatingInfo sellerRating) {
        return new ListingViewResult(ListingViewStatus.SUCCESS, listing, sellerRating, null);
    }

    public static ListingViewResult notFound(String message) {
        return new ListingViewResult(ListingViewStatus.NOT_FOUND, null, null, message);
    }

    public static ListingViewResult notLive(String message) {
        return new ListingViewResult(ListingViewStatus.NOT_LIVE, null, null, message);
    }

    public boolean isSuccess() {
        return status == ListingViewStatus.SUCCESS;
    }

    public ListingViewStatus getStatus() {
        return status;
    }

    public Listing getListing() {
        return listing;
    }

    public SellerRatingInfo getSellerRating() {
        return sellerRating;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListingViewResult that = (ListingViewResult) o;
        return status == that.status
                && Objects.equals(listing, that.listing)
                && Objects.equals(sellerRating, that.sellerRating)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, listing, sellerRating, message);
    }

    @Override
    public String toString() {
        return "ListingViewResult{" +
                "status=" + status +
                ", listing=" + (listing != null ? listing.getTitle() : "null") +
                ", sellerRating=" + sellerRating +
                ", message='" + message + '\'' +
                '}';
    }
}
