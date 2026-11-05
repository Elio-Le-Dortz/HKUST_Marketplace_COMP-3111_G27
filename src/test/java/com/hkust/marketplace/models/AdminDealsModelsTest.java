package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-A05 Admin Deal Oversight Models Test Suite")
class AdminDealsModelsTest {

    @Test
    @DisplayName("AdminDealQueryStatus enum coverage")
    void testQueryStatusEnum() {
        assertEquals(4, AdminDealQueryStatus.values().length);
        assertEquals(AdminDealQueryStatus.SUCCESS, AdminDealQueryStatus.valueOf("SUCCESS"));
        assertEquals(AdminDealQueryStatus.NO_DEALS_YET, AdminDealQueryStatus.valueOf("NO_DEALS_YET"));
        assertEquals(AdminDealQueryStatus.NO_MATCHING_DEALS, AdminDealQueryStatus.valueOf("NO_MATCHING_DEALS"));
        assertEquals(AdminDealQueryStatus.INVALID_DATE_RANGE, AdminDealQueryStatus.valueOf("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("AdminDealRecord full projection with active listing and users")
    void testAdminDealRecordActive() {
        Deal deal = new Deal();
        deal.setId("D501");
        deal.setListingId("L101");
        deal.setListingTitle("Algorithms Textbook");
        deal.setBuyerUsername("alice");
        deal.setSellerUsername("bob");
        deal.setType(DealType.SALE);
        deal.setStatus(DealStatus.COMPLETED);
        deal.setStartDate(LocalDateTime.of(2026, 11, 1, 10, 0));
        deal.setClosedDate(LocalDateTime.of(2026, 11, 2, 15, 30));
        deal.setOfferPrice(120.0);
        deal.setRated(true);

        Listing listing = new Listing();
        listing.setId("L101");
        listing.setTitle("Algorithms Textbook");
        listing.setStatus(ListingStatus.SOLD);
        listing.setPrice(120.0);

        Buyer buyer = new Buyer("alice", "Alice Wong", "password");
        buyer.setAccountStatus(AccountStatus.ACTIVE);

        Seller seller = new Seller("bob", "Bob Chan", "password");
        seller.setAccountStatus(AccountStatus.ACTIVE);

        Offer offer = new Offer("O701", OfferType.PRICE_OFFER, "L101", "alice", "bob");
        offer.setPrice(120.0);
        offer.setStatus(OfferStatus.ACCEPTED);

        Review review = new Review();
        review.setId("R1");
        review.setRating(5);
        review.setComment("Great book!");
        review.setCreatedAt(LocalDateTime.of(2026, 11, 3, 12, 0));

        AdminDealRecord record = new AdminDealRecord(deal, listing, buyer, seller, offer, review);

        assertSame(deal, record.getDeal());
        assertSame(listing, record.getListing());
        assertSame(buyer, record.getBuyer());
        assertSame(seller, record.getSeller());
        assertSame(offer, record.getOffer());
        assertSame(review, record.getReview());

        assertEquals("D501", record.getDealId());
        assertEquals("L101", record.getListingId());
        assertFalse(record.isListingRemoved());
        assertEquals("Algorithms Textbook", record.getItemTitle());
        assertEquals("Algorithms Textbook", record.getItemTitleDisplay());

        assertEquals("alice", record.getRawBuyerUsername());
        assertEquals("bob", record.getRawSellerUsername());
        assertFalse(record.isBuyerDeleted());
        assertFalse(record.isSellerDeleted());
        assertEquals("@alice", record.getBuyerUsernameDisplay());
        assertEquals("@bob", record.getSellerUsernameDisplay());
        assertEquals("Alice Wong (@alice)", record.getBuyerFullNameDisplay());
        assertEquals("Bob Chan (@bob)", record.getSellerFullNameDisplay());

        assertEquals(DealType.SALE, record.getDealType());
        assertEquals("Sale", record.getDealTypeDisplay());
        assertEquals(DealStatus.COMPLETED, record.getDealStatus());
        assertEquals("Completed", record.getDealStatusDisplay());

        assertNotNull(record.getStartDate());
        assertEquals("01 Nov 2026 10:00", record.getStartDateDisplay());
        assertEquals("Completed: 02 Nov 2026", record.getReturnOrCompleteDateDisplay());
        assertEquals("$120.00", record.getAgreedTermsDisplay());

        assertTrue(record.hasReview());
        assertEquals(5, record.getReviewRating());
        assertEquals("Great book!", record.getReviewComment());
        assertEquals("03 Nov 2026", record.getReviewDateDisplay());
        assertFalse(record.isReviewHidden());
    }

    @Test
    @DisplayName("AdminDealRecord handles Alternative Flow A5: removed listing and deleted users")
    void testAdminDealRecordAlternativeFlowA5() {
        Deal deal = new Deal();
        deal.setId("D502");
        deal.setListingId("L102");
        deal.setListingTitle("Deleted Item Book");
        deal.setBuyerUsername("deleted_buyer");
        deal.setSellerUsername("deleted_seller");
        deal.setType(DealType.GIVEAWAY);
        deal.setStatus(DealStatus.COMPLETED);
        deal.setStartDate(LocalDateTime.of(2026, 10, 15, 9, 0));
        deal.setClosedDate(LocalDateTime.of(2026, 10, 15, 11, 0));

        // Listing is null (removed)
        Buyer buyer = new Buyer("deleted_buyer", "Old Buyer", "password");
        buyer.setAccountStatus(AccountStatus.DELETED);

        // Seller is null
        AdminDealRecord record = new AdminDealRecord(deal, null, buyer, null, null, null);

        assertTrue(record.isListingRemoved());
        assertEquals("Deleted Item Book", record.getItemTitle());
        assertEquals("Deleted Item Book [Removed]", record.getItemTitleDisplay());

        assertTrue(record.isBuyerDeleted());
        assertTrue(record.isSellerDeleted());
        assertEquals(AdminDealRecord.DELETED_USER_PLACEHOLDER, record.getBuyerUsernameDisplay());
        assertEquals(AdminDealRecord.DELETED_USER_PLACEHOLDER, record.getSellerUsernameDisplay());
        assertEquals(AdminDealRecord.DELETED_USER_PLACEHOLDER, record.getBuyerFullNameDisplay());
        assertEquals(AdminDealRecord.DELETED_USER_PLACEHOLDER, record.getSellerFullNameDisplay());

        assertEquals("Giveaway", record.getDealTypeDisplay());
        assertEquals("Free (Giveaway Item)", record.getAgreedTermsDisplay());

        assertFalse(record.hasReview());
        assertEquals(0, record.getReviewRating());
        assertEquals("", record.getReviewComment());
        assertEquals("-", record.getReviewDateDisplay());
        assertFalse(record.isReviewHidden());
    }

    @Test
    @DisplayName("AdminDealRecord loan terms and active due date display")
    void testAdminDealRecordLoanActive() {
        Deal deal = new Deal();
        deal.setId("D503");
        deal.setType(DealType.LOAN);
        deal.setStatus(DealStatus.ACTIVE);
        deal.setStartDate(LocalDateTime.of(2026, 11, 10, 14, 0));
        deal.setDueDate(LocalDateTime.of(2026, 11, 20, 23, 59));

        AdminDealRecord record = new AdminDealRecord(deal, null, null, null, null, null);

        assertEquals("Loan", record.getDealTypeDisplay());
        assertEquals("Active", record.getDealStatusDisplay());
        assertEquals("Due: 20 Nov 2026", record.getReturnOrCompleteDateDisplay());
        assertEquals("10 Nov 2026 to 20 Nov 2026", record.getAgreedTermsDisplay());

        // Test when returned
        deal.setStatus(DealStatus.RETURNED);
        deal.setClosedDate(LocalDateTime.of(2026, 11, 18, 16, 0));
        assertEquals("Returned", record.getDealStatusDisplay());
        assertEquals("Returned: 18 Nov 2026", record.getReturnOrCompleteDateDisplay());
    }

    @Test
    @DisplayName("AdminDealRecord fallbacks for null fields")
    void testAdminDealRecordNullFallbacks() {
        Deal deal = new Deal();
        deal.setType(null);
        deal.setStatus((DealStatus) null);
        deal.setStartDate(null);
        deal.setDueDate(null);
        deal.setClosedDate(null);
        deal.setListingTitle(null);

        AdminDealRecord record = new AdminDealRecord(deal, null, null, null, null, null);

        assertEquals("Unknown", record.getDealTypeDisplay());
        assertEquals("Unknown", record.getDealStatusDisplay());
        assertEquals("-", record.getStartDateDisplay());
        assertEquals("-", record.getReturnOrCompleteDateDisplay());
        assertEquals("Unknown Listing", record.getItemTitle());
        assertEquals("Unknown Listing [Removed]", record.getItemTitleDisplay());
        assertEquals("N/A", record.getAgreedTermsDisplay());

        // Price fallback from listing
        deal.setType(DealType.SALE);
        Listing listing = new Listing();
        listing.setPrice(75.0);
        AdminDealRecord recordWithListing = new AdminDealRecord(deal, listing, null, null, null, null);
        assertEquals("$75.00", recordWithListing.getAgreedTermsDisplay());

        // Price fallback from offer
        Offer offer = new Offer();
        offer.setPrice(65.0);
        AdminDealRecord recordWithOffer = new AdminDealRecord(deal, null, null, null, offer, null);
        assertEquals("$65.00", recordWithOffer.getAgreedTermsDisplay());
    }

    @Test
    @DisplayName("AdminDealFilterCriteria date range validation (Alternative Flow A4)")
    void testFilterCriteriaDateRangeValidation() {
        AdminDealFilterCriteria empty = AdminDealFilterCriteria.all();
        assertTrue(empty.isEmpty());
        assertTrue(empty.isValidDateRange());

        LocalDate d1 = LocalDate.of(2026, 11, 1);
        LocalDate d2 = LocalDate.of(2026, 11, 5);

        AdminDealFilterCriteria valid = new AdminDealFilterCriteria("book", DealType.SALE, DealStatus.COMPLETED, d1, d2);
        assertFalse(valid.isEmpty());
        assertTrue(valid.isValidDateRange());
        assertEquals("book", valid.getSearchQuery());
        assertEquals(DealType.SALE, valid.getDealType());
        assertEquals(DealStatus.COMPLETED, valid.getDealStatus());
        assertEquals(d1, valid.getStartDateFrom());
        assertEquals(d2, valid.getStartDateTo());

        // Equal dates is valid (same day)
        AdminDealFilterCriteria sameDay = new AdminDealFilterCriteria(null, null, null, d1, d1);
        assertTrue(sameDay.isValidDateRange());

        // Invalid date range (end before start)
        AdminDealFilterCriteria invalid = new AdminDealFilterCriteria(null, null, null, d2, d1);
        assertFalse(invalid.isValidDateRange());
    }

    @Test
    @DisplayName("AdminDealFilterCriteria matches logic across all fields")
    void testFilterCriteriaMatches() {
        Deal deal = new Deal();
        deal.setId("D1");
        deal.setType(DealType.SALE);
        deal.setStatus(DealStatus.COMPLETED);
        deal.setStartDate(LocalDateTime.of(2026, 11, 10, 12, 0));
        deal.setBuyerUsername("alice");
        deal.setSellerUsername("bob");
        deal.setListingTitle("Physics Book");

        Buyer buyer = new Buyer("alice", "Alice Smith", "pwd");
        Seller seller = new Seller("bob", "Bob Johnson", "pwd");

        AdminDealRecord record = new AdminDealRecord(deal, null, buyer, seller, null, null);

        // Null record check
        AdminDealFilterCriteria criteria = AdminDealFilterCriteria.all();
        assertFalse(criteria.matches(null));
        assertTrue(criteria.matches(record));

        // Deal Type filter
        assertTrue(new AdminDealFilterCriteria(null, DealType.SALE, null, null, null).matches(record));
        assertFalse(new AdminDealFilterCriteria(null, DealType.LOAN, null, null, null).matches(record));

        // Deal Status filter
        assertTrue(new AdminDealFilterCriteria(null, null, DealStatus.COMPLETED, null, null).matches(record));
        assertFalse(new AdminDealFilterCriteria(null, null, DealStatus.ACTIVE, null, null).matches(record));

        // Start Date From (inclusive)
        assertTrue(new AdminDealFilterCriteria(null, null, null, LocalDate.of(2026, 11, 10), null).matches(record));
        assertTrue(new AdminDealFilterCriteria(null, null, null, LocalDate.of(2026, 11, 9), null).matches(record));
        assertFalse(new AdminDealFilterCriteria(null, null, null, LocalDate.of(2026, 11, 11), null).matches(record));

        // Start Date To (inclusive)
        assertTrue(new AdminDealFilterCriteria(null, null, null, null, LocalDate.of(2026, 11, 10)).matches(record));
        assertTrue(new AdminDealFilterCriteria(null, null, null, null, LocalDate.of(2026, 11, 11)).matches(record));
        assertFalse(new AdminDealFilterCriteria(null, null, null, null, LocalDate.of(2026, 11, 9)).matches(record));

        // Search Query matches item title
        assertTrue(new AdminDealFilterCriteria("physics", null, null, null, null).matches(record));
        // Search Query matches buyer username
        assertTrue(new AdminDealFilterCriteria("alice", null, null, null, null).matches(record));
        // Search Query matches buyer full name
        assertTrue(new AdminDealFilterCriteria("smith", null, null, null, null).matches(record));
        // Search Query matches seller username
        assertTrue(new AdminDealFilterCriteria("bob", null, null, null, null).matches(record));
        // Search Query matches seller full name
        assertTrue(new AdminDealFilterCriteria("johnson", null, null, null, null).matches(record));
        // Non-matching query
        assertFalse(new AdminDealFilterCriteria("calculus", null, null, null, null).matches(record));

        // Search Query "deleted user" matching
        buyer.setAccountStatus(AccountStatus.DELETED);
        assertTrue(new AdminDealFilterCriteria("deleted", null, null, null, null).matches(record));
    }

    @Test
    @DisplayName("AdminDealFilterCriteria equals and hashCode contract")
    void testFilterCriteriaEqualsAndHashCode() {
        LocalDate d1 = LocalDate.of(2026, 11, 1);
        LocalDate d2 = LocalDate.of(2026, 11, 5);

        AdminDealFilterCriteria c1 = new AdminDealFilterCriteria("query", DealType.SALE, DealStatus.ACTIVE, d1, d2);
        AdminDealFilterCriteria c2 = new AdminDealFilterCriteria("query", DealType.SALE, DealStatus.ACTIVE, d1, d2);
        AdminDealFilterCriteria c3 = new AdminDealFilterCriteria("diff", DealType.SALE, DealStatus.ACTIVE, d1, d2);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1, c3);
        assertNotEquals(c1, null);
        assertNotEquals(c1, "some_string");
    }

    @Test
    @DisplayName("AdminDealQueryResult factory methods and behavior")
    void testAdminDealQueryResult() {
        Deal deal = new Deal();
        deal.setId("D1");
        AdminDealRecord record = new AdminDealRecord(deal, null, null, null, null, null);
        List<AdminDealRecord> list = List.of(record);

        AdminDealQueryResult success = AdminDealQueryResult.success(list, 10);
        assertTrue(success.isSuccess());
        assertFalse(success.isNoDealsYet());
        assertFalse(success.isNoMatchingDeals());
        assertFalse(success.isInvalidDateRange());
        assertEquals(1, success.getFilteredCount());
        assertEquals(10, success.getTotalSystemDealsCount());
        assertEquals(1, success.getDeals().size());
        assertTrue(success.getMessage().contains("1 transaction records"));

        AdminDealQueryResult noDeals = AdminDealQueryResult.noDealsYet();
        assertTrue(noDeals.isNoDealsYet());
        assertEquals(0, noDeals.getFilteredCount());
        assertEquals(0, noDeals.getTotalSystemDealsCount());

        AdminDealQueryResult noMatching = AdminDealQueryResult.noMatchingDeals(5);
        assertTrue(noMatching.isNoMatchingDeals());
        assertEquals(0, noMatching.getFilteredCount());
        assertEquals(5, noMatching.getTotalSystemDealsCount());

        AdminDealQueryResult invalidDate = AdminDealQueryResult.invalidDateRange("Bad range", list, 8);
        assertTrue(invalidDate.isInvalidDateRange());
        assertEquals("Bad range", invalidDate.getMessage());
        assertEquals(1, invalidDate.getFilteredCount());
        assertEquals(8, invalidDate.getTotalSystemDealsCount());

        // Null message default
        AdminDealQueryResult custom = new AdminDealQueryResult(AdminDealQueryStatus.SUCCESS, null, 0, null);
        assertEquals("", custom.getMessage());
        assertTrue(custom.getDeals().isEmpty());
    }
}
