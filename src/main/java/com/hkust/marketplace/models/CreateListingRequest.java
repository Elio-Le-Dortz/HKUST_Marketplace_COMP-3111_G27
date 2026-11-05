package com.hkust.marketplace.models;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Values entered by a Seller in the listing form of use case 2.3 "Create Listing" (US-S03).
 *
 * <p>The price and the lending duration are kept as the text the Seller typed, so that the
 * service can tell the Seller exactly why a value is invalid (alternative flow A2). The
 * price is only read for a Sell listing and the lending duration only for a Lend listing:
 * the other values are discarded (alternative flow A4).</p>
 *
 * <p>The Seller who posts the listing is not part of the request: it is always the Seller
 * of the current session, so that nobody can post a listing in another Seller's name.</p>
 *
 * @param type           the listing type, {@code null} if none is chosen
 * @param title          the item name typed by the Seller
 * @param description    the description typed by the Seller
 * @param category       the chosen category, {@code null} if none is chosen
 * @param condition      the chosen condition, {@code null} if none is chosen
 * @param price          the price typed by the Seller, in HK$; {@code HK$}, {@code $}, spaces
 *                       and thousands separators are allowed
 * @param lendingAmount  the number of lending units typed by the Seller, for example {@code 7}
 * @param lendingUnit    the unit of the lending duration, {@code null} if none is chosen
 * @param meetupLocation the chosen meet-up location, {@code null} if none is chosen
 * @param photos         the image files chosen by the Seller, in display order; {@code null}
 *                       is read as no photo and {@code null} elements are ignored
 */
public record CreateListingRequest(ListingType type,
                                   String title,
                                   String description,
                                   Category category,
                                   Condition condition,
                                   String price,
                                   String lendingAmount,
                                   LendingDurationUnit lendingUnit,
                                   MeetupLocation meetupLocation,
                                   List<Path> photos) {

    /**
     * Creates a request, keeping an unmodifiable copy of the photo list so that later
     * changes to the form do not change a request that was already submitted.
     *
     * @param type           the listing type, {@code null} if none is chosen
     * @param title          the item name typed by the Seller
     * @param description    the description typed by the Seller
     * @param category       the chosen category, {@code null} if none is chosen
     * @param condition      the chosen condition, {@code null} if none is chosen
     * @param price          the price typed by the Seller, in HK$
     * @param lendingAmount  the number of lending units typed by the Seller
     * @param lendingUnit    the unit of the lending duration, {@code null} if none is chosen
     * @param meetupLocation the chosen meet-up location, {@code null} if none is chosen
     * @param photos         the image files chosen by the Seller, in display order
     */
    public CreateListingRequest {
        photos = photos == null
                ? List.of()
                : Collections.unmodifiableList(photos.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    /**
     * Describes the request without the description text, which can be long.
     *
     * @return a short description of the request
     */
    @Override
    public String toString() {
        return "CreateListingRequest[type=" + type
                + ", title=" + title
                + ", category=" + category
                + ", condition=" + condition
                + ", price=" + price
                + ", lendingAmount=" + lendingAmount
                + ", lendingUnit=" + lendingUnit
                + ", meetupLocation=" + meetupLocation
                + ", photos=" + photos.size() + "]";
    }
}
