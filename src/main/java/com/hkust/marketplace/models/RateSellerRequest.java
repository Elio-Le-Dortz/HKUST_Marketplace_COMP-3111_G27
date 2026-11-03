package com.hkust.marketplace.models;

import java.util.Objects;

/**
 * Encapsulates the request payload for submitting a seller rating and review (US-B11 / Use Case 1.10).
 */
public class RateSellerRequest {

    private final String dealId;
    private final String buyerUsername;
    private final Integer rating; // 1 to 5 stars
    private final String comment; // optional written review

    public RateSellerRequest(String dealId, String buyerUsername, Integer rating, String comment) {
        this.dealId = dealId != null ? dealId.trim() : null;
        this.buyerUsername = buyerUsername != null ? buyerUsername.trim() : null;
        this.rating = rating;
        this.comment = comment != null ? comment.trim() : "";
    }

    public static RateSellerRequest of(String dealId, String buyerUsername, Integer rating, String comment) {
        return new RateSellerRequest(dealId, buyerUsername, rating, comment);
    }

    public static RateSellerRequest of(String dealId, String buyerUsername, Integer rating) {
        return new RateSellerRequest(dealId, buyerUsername, rating, null);
    }

    public String getDealId() {
        return dealId;
    }

    public String getBuyerUsername() {
        return buyerUsername;
    }

    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public boolean hasComment() {
        return comment != null && !comment.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateSellerRequest that = (RateSellerRequest) o;
        return Objects.equals(dealId, that.dealId)
                && Objects.equals(buyerUsername, that.buyerUsername)
                && Objects.equals(rating, that.rating)
                && Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dealId, buyerUsername, rating, comment);
    }

    @Override
    public String toString() {
        return "RateSellerRequest{" +
                "dealId='" + dealId + '\'' +
                ", buyerUsername='" + buyerUsername + '\'' +
                ", rating=" + rating +
                ", comment='" + comment + '\'' +
                '}';
    }
}
