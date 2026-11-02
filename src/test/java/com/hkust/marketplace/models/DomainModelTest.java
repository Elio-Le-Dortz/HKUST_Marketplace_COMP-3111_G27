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
        listing.setType(ListingType.SELL);
        listing.setCategory(Category.ELECTRONICS);
        listing.setCondition(Condition.LIKE_NEW);
        listing.setPrice(150.0);
        listing.setMeetupLocation(MeetupLocation.LIBRARY);
        listing.setStatus(ListingStatus.LIVE);

        Photo p1 = new Photo("img1.png");
        Photo p2 = new Photo("img2.png");
        listing.addPhoto(p1);
        listing.addPhoto(p2);

        assertEquals(2, listing.getPhotos().size());
        assertEquals("HK$ 150", listing.getPriceDisplay());
        assertEquals("Sell", listing.getTypeDisplay());

        listing.incrementViewCount();
        assertEquals(1, listing.getViewCount());

        listing.setType(ListingType.LEND);
        listing.setLendingDuration(Duration.ofDays(7));
        assertEquals("Lend (up to 7 days)", listing.getPriceDisplay());

        listing.setType(ListingType.GIVEAWAY);
        assertEquals("FREE", listing.getPriceDisplay());
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
