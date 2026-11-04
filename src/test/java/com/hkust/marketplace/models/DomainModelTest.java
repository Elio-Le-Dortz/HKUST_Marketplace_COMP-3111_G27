package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain Models & Class Diagram Specification Tests")
class DomainModelTest {

    @Test
    @DisplayName("User hierarchy: Buyer, Seller, Admin inherit from abstract User")
    void testUserHierarchy() {
        Buyer buyer = new Buyer("buyer1", "Alice", "Pass1234!", "Hall 3");
        assertEquals(UserRole.BUYER, buyer.getRole());
        assertEquals("Hall 3", buyer.getResidence());
        assertEquals("Hall 3", buyer.getResidenceHall());
        assertTrue(buyer.isActive());

        buyer.setActive(false);
        assertEquals(AccountStatus.DEACTIVATED, buyer.getAccountStatus());
        assertFalse(buyer.isActive());

        Seller seller = new Seller("seller1", "Bob", "Pass1234!", "Bio info");
        assertEquals(UserRole.SELLER, seller.getRole());
        assertEquals("Bio info", seller.getBio());
        seller.setPreferredMeetup("Library");
        assertEquals("Library", seller.getPreferredMeetup());

        Admin admin = new Admin("admin1", "Admin User", "AdminPass1!");
        assertEquals(UserRole.ADMIN, admin.getRole());
    }

    @Test
    @DisplayName("Listing model: fields, photos, and type displays")
    void testListingModel() {
        Listing listing = new Listing();
        listing.setId("L100");
        listing.setTitle("Laptop Stand");
        listing.setDescription("Adjustable aluminum laptop stand");
        listing.setType(ListingType.SELL);
        listing.setCategory(Category.ELECTRONICS);
        listing.setCondition(Condition.LIKE_NEW);
        listing.setPrice(150.0);
        listing.setMeetupLocation(MeetupLocation.LIBRARY);
        listing.setStatus(ListingStatus.LIVE);
        listing.setSellerUsername("seller1");
        listing.setSellerFullName("Bob Seller");

        Photo p1 = new Photo("p1", "img1.png");
        Photo p2 = new Photo("img2.png");
        listing.addPhoto(p1);
        listing.addPhoto(p2);

        assertEquals(2, listing.getPhotos().size());
        assertEquals("HK$ 150", listing.getPriceDisplay());
        assertEquals("Sell", listing.getTypeDisplay());
        assertEquals("Adjustable aluminum laptop stand", listing.getDescription());
        assertEquals("seller1", listing.getSellerUsername());
        assertEquals("Bob Seller", listing.getSellerFullName());
        assertEquals(MeetupLocation.LIBRARY, listing.getMeetupLocation());
        assertEquals(p1, listing.getFirstPhoto());
        assertEquals("img1.png", listing.getThumbnailPath());

        listing.incrementViewCount();
        assertEquals(1, listing.getViewCount());
        listing.setViewCount(5);
        assertEquals(5, listing.getViewCount());

        // Max 6 photos cap
        for (int i = 3; i <= 8; i++) {
            listing.addPhoto(new Photo("img" + i + ".png"));
        }
        assertEquals(6, listing.getPhotos().size(), "Photo limit should be capped at 6");
        assertEquals(6, listing.getPhotoPaths().size());

        listing.setPhotoPaths(java.util.List.of("pathA.png", "pathB.png"));
        assertEquals(2, listing.getPhotos().size());
        assertEquals(2, listing.getPhotoPaths().size());
        listing.setPhotos(null);
        assertTrue(listing.getPhotos().isEmpty());
        assertNull(listing.getFirstPhoto());
        assertNull(listing.getThumbnailPath());

        // Price displays
        listing.setType(ListingType.SELL);
        listing.setPrice(null);
        assertEquals("Free", listing.getPriceDisplay());
        listing.setPrice(99.0);
        assertEquals("HK$ 99", listing.getPriceDisplay());

        listing.setType(ListingType.LEND);
        listing.setLendingDuration(null);
        assertEquals("Lend", listing.getPriceDisplay());
        assertNull(listing.getLendingDays());

        listing.setLendingDuration(Duration.ofDays(7));
        assertEquals("Lend (up to 7 days)", listing.getPriceDisplay());
        assertEquals(7, listing.getLendingDays());

        listing.setLendingDays(null);
        assertNull(listing.getLendingDuration());
        listing.setLendingDays(14);
        assertEquals(14, listing.getLendingDays());

        listing.setType(ListingType.GIVEAWAY);
        assertEquals("FREE", listing.getPriceDisplay());

        listing.setType(null);
        assertEquals("", listing.getTypeDisplay());

        // String setters for Category, Condition, MeetupLocation
        listing.setCategory("Books");
        assertEquals(Category.BOOKS, listing.getCategory());
        listing.setCondition("Used");
        assertEquals(Condition.USED, listing.getCondition());
        listing.setMeetupLocation("Atrium");
        assertEquals(MeetupLocation.ATRIUM, listing.getMeetupLocation());

        // Admin reason and dates
        listing.setAdminReason("Reason");
        assertEquals("Reason", listing.getAdminReason());
        listing.setRejectionReason("Rejection");
        assertEquals("Rejection", listing.getRejectionReason());
        assertEquals("Rejection", listing.getAdminReason());

        LocalDateTime now = LocalDateTime.now();
        listing.setPostedAt(now);
        assertEquals(now, listing.getPostedAt());
        assertEquals(now, listing.getPostedDate());
        listing.setPostedDate(now.minusHours(1));
        assertEquals(now.minusHours(1), listing.getPostedDate());
        listing.setReviewedAt(now);
        assertEquals(now.minusHours(1), listing.getReviewedAt());

        // Availability display
        listing.setStatus(ListingStatus.LIVE);
        assertEquals("Live", listing.getAvailabilityDisplay());
        listing.setStatus(ListingStatus.PENDING_REVIEW);
        assertEquals("Pending Review", listing.getAvailabilityDisplay());
        listing.setStatus(ListingStatus.RESERVED);
        assertEquals("Reserved", listing.getAvailabilityDisplay());
        listing.setStatus(ListingStatus.SOLD);
        assertEquals("Sold", listing.getAvailabilityDisplay());
        listing.setStatus(ListingStatus.LENT);
        assertEquals("Lent", listing.getAvailabilityDisplay());
        listing.setStatus(ListingStatus.GIVEN);
        assertEquals("Given", listing.getAvailabilityDisplay());
        listing.setStatus(ListingStatus.REJECTED);
        assertEquals("Rejected", listing.getAvailabilityDisplay());
        listing.setStatus(ListingStatus.REMOVED);
        assertEquals("Removed", listing.getAvailabilityDisplay());
        listing.setStatus(null);
        assertEquals("Unknown", listing.getAvailabilityDisplay());

        // Photo file accessor
        Photo p = new Photo("test.png");
        assertNotNull(p.getImageFile());
        Photo pEmpty = new Photo();
        assertNull(pEmpty.getImagePath());
        assertNull(pEmpty.getImageFile());
        pEmpty.setId("id1");
        pEmpty.setImagePath("path1.png");
        assertEquals("id1", pEmpty.getId());
        assertEquals("path1.png", pEmpty.getImagePath());
    }

    @Test
    @DisplayName("Offer model: price offer, status transitions and counter offer")
    void testOfferModel() {
        Offer offer = new Offer("O1", OfferType.PRICE_OFFER, "L100", "alice", "bob");
        offer.setPrice(120.0);
        assertEquals(OfferStatus.PENDING, offer.getStatus());

        Offer counter = new Offer("O2", OfferType.PRICE_OFFER, "L100", "bob", "alice");
        counter.setPrice(140.0);
        offer.setStatus(OfferStatus.COUNTERED);
        offer.setCounterOffer(counter);

        assertEquals(OfferStatus.COUNTERED, offer.getStatus());
        assertNotNull(offer.getCounterOffer());
        assertEquals(140.0, offer.getCounterOffer().getPrice());
    }

    @Test
    @DisplayName("Deal model: deal types, dates, and status transitions")
    void testDealModel() {
        Deal deal = new Deal();
        deal.setId("D1");
        deal.setListingId("L100");
        deal.setBuyerUsername("alice");
        deal.setSellerUsername("bob");
        deal.setType(DealType.SALE);
        deal.setStatus(DealStatus.ACTIVE);

        assertEquals(DealStatus.ACTIVE, deal.getStatus());
        assertEquals("ACTIVE", deal.getStatusString());

        deal.setStatus(DealStatus.COMPLETED);
        deal.setClosedDate(LocalDateTime.now());
        assertEquals(DealStatus.COMPLETED, deal.getStatus());
    }

    @Test
    @DisplayName("Review model: rating, comments, replies, and moderation flags")
    void testReviewModel() {
        Review review = new Review();
        review.setId("R1");
        review.setRating(5);
        review.setComment("Excellent seller!");
        review.setSellerUsername("bob");
        review.setBuyerUsername("alice");

        assertFalse(review.isHidden());
        assertFalse(review.isFlagged());

        review.setFlagged(true);
        review.setFlagReason("Dispute over item condition");
        assertTrue(review.isFlagged());

        review.setHidden(true);
        assertTrue(review.isHidden());

        review.setReply("Thank you for your purchase!");
        assertEquals("Thank you for your purchase!", review.getReply());
    }

    @Test
    @DisplayName("Report model: filing and resolution")
    void testReportModel() {
        Report report = new Report("REP1", "L100", "Calculus Book", "alice", "Suspicious duplicate listing");
        assertEquals(ReportStatus.OPEN, report.getStatus());

        report.setStatus(ReportStatus.RESOLVED);
        assertEquals(ReportStatus.RESOLVED, report.getStatus());
    }

    @Test
    @DisplayName("ChatThread & Message model")
    void testChatThreadAndMessage() {
        ChatThread thread = new ChatThread("T1", "L100", "alice", "bob");
        Message msg1 = new Message("alice", "Is this item still available?");
        Message msg2 = new Message("bob", "Yes, it is!");

        thread.addMessage(msg1);
        thread.addMessage(msg2);

        assertEquals(2, thread.getMessages().size());
        assertEquals("alice", thread.getMessages().get(0).getSenderUsername());
    }

    @Test
    @DisplayName("Buyer and User model constructors, getters and setters")
    void testBuyerAndUserAccessors() {
        Buyer defaultBuyer = new Buyer();
        assertEquals(UserRole.BUYER, defaultBuyer.getRole());
        assertEquals(AccountStatus.ACTIVE, defaultBuyer.getAccountStatus());
        assertNotNull(defaultBuyer.getCreatedAt());

        Buyer threeArgBuyer = new Buyer("user3", "User Three", "hashedPwd");
        assertEquals("user3", threeArgBuyer.getUsername());
        assertEquals("User Three", threeArgBuyer.getFullName());
        assertEquals("hashedPwd", threeArgBuyer.getPassword());
        assertEquals("hashedPwd", threeArgBuyer.getPasswordHash());

        threeArgBuyer.setUsername("user3_updated");
        assertEquals("user3_updated", threeArgBuyer.getUsername());

        threeArgBuyer.setFullName("User Three Updated");
        assertEquals("User Three Updated", threeArgBuyer.getFullName());

        threeArgBuyer.setPassword("newHashedPwd");
        assertEquals("newHashedPwd", threeArgBuyer.getPassword());

        threeArgBuyer.setPasswordHash("newHash2");
        assertEquals("newHash2", threeArgBuyer.getPasswordHash());

        threeArgBuyer.setResidence("Hall 4");
        assertEquals("Hall 4", threeArgBuyer.getResidence());
        threeArgBuyer.setResidenceHall("Hall 5");
        assertEquals("Hall 5", threeArgBuyer.getResidence());
        assertEquals("Hall 5", threeArgBuyer.getResidenceHall());

        LocalDateTime now = LocalDateTime.now();
        threeArgBuyer.setCreatedAt(now);
        assertEquals(now, threeArgBuyer.getCreatedAt());

        threeArgBuyer.setActive(true);
        assertTrue(threeArgBuyer.isActive());
        assertEquals(AccountStatus.ACTIVE, threeArgBuyer.getAccountStatus());
    }
}
