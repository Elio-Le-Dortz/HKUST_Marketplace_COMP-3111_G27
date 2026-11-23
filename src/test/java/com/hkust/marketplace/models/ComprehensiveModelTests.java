package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Comprehensive Domain Models Branch Coverage Tests")
public class ComprehensiveModelTests {

    @Nested
    @DisplayName("DealHistoryFilterCriteria Branch Tests")
    class DealHistoryFilterCriteriaTests {

        @Test
        @DisplayName("matches: null item returns false")
        void testMatchesNull() {
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria();
            assertFalse(criteria.matches(null));
        }

        @Test
        @DisplayName("matches: date range boundaries (fromDate and toDate)")
        void testMatchesDateRange() {
            LocalDate baseDate = LocalDate.of(2026, 11, 10);
            DealHistoryFilterCriteria criteria = new DealHistoryFilterCriteria();
            criteria.setFromDate(baseDate);
            criteria.setToDate(baseDate.plusDays(2));

            Deal d = new Deal();
            d.setId("D1");
            d.setStartDate(LocalDateTime.of(2026, 11, 11, 10, 0));
            d.setClosedDate(LocalDateTime.of(2026, 11, 11, 12, 0));
            DealHistoryItem item = DealHistoryItem.fromDeal(d, null, null);

            assertTrue(criteria.matches(item));

            // Before fromDate
            d.setStartDate(LocalDateTime.of(2026, 11, 8, 10, 0));
            d.setClosedDate(LocalDateTime.of(2026, 11, 8, 12, 0));
            DealHistoryItem beforeItem = DealHistoryItem.fromDeal(d, null, null);
            assertFalse(criteria.matches(beforeItem));

            // After toDate
            d.setStartDate(LocalDateTime.of(2026, 11, 15, 10, 0));
            d.setClosedDate(LocalDateTime.of(2026, 11, 15, 12, 0));
            DealHistoryItem afterItem = DealHistoryItem.fromDeal(d, null, null);
            assertFalse(criteria.matches(afterItem));

            // null dates on item
            d.setStartDate(null);
            d.setClosedDate(null);
            DealHistoryItem nullDateItem = DealHistoryItem.fromDeal(d, null, null);
            assertFalse(criteria.matches(nullDateItem));
        }

        @Test
        @DisplayName("matches: category, dealType, sellerUsername and searchQuery")
        void testMatchesFilters() {
            Deal d = new Deal();
            d.setId("D2");
            d.setType(DealType.SALE);
            d.setSellerUsername("charlie");
            d.setListingTitle("Engineering Textbook");

            Listing l = new Listing();
            l.setId("L1");
            l.setTitle("Engineering Textbook");
            l.setCategory(Category.BOOKS);

            Seller s = new Seller();
            s.setUsername("charlie");
            s.setFullName("Charlie Lee");

            DealHistoryItem item = DealHistoryItem.fromDeal(d, l, s);

            DealHistoryFilterCriteria c = new DealHistoryFilterCriteria();
            c.setCategory(Category.ELECTRONICS);
            assertFalse(c.matches(item));

            c.setCategory(Category.BOOKS);
            c.setDealType(DealType.LOAN);
            assertFalse(c.matches(item));

            c.setDealType(DealType.SALE);
            c.setSellerUsername("diana");
            assertFalse(c.matches(item));

            c.setSellerUsername("charlie");
            c.setSearchQuery("laptop");
            assertFalse(c.matches(item));

            c.setSearchQuery("textbook");
            assertTrue(c.matches(item));

            c.setSearchQuery("Lee");
            assertTrue(c.matches(item));
        }

        @Test
        @DisplayName("isEmpty and equals tests")
        void testIsEmptyAndEquals() {
            DealHistoryFilterCriteria c1 = new DealHistoryFilterCriteria();
            assertTrue(c1.isEmpty());

            c1.setSearchQuery("   ");
            assertTrue(c1.isEmpty());

            c1.setSellerUsername("  ");
            assertTrue(c1.isEmpty());

            c1.setSearchQuery(null);
            c1.setSellerUsername(null);
            c1.setFromDate(LocalDate.now());
            assertFalse(c1.isEmpty());

            DealHistoryFilterCriteria c2 = new DealHistoryFilterCriteria(
                    c1.getFromDate(), null, null, null, null, null
            );
            assertEquals(c1, c2);
            assertEquals(c1.hashCode(), c2.hashCode());

            assertNotEquals(c1, null);
            assertNotEquals(c1, "other");
            assertEquals(c1, c1);

            c2.setToDate(LocalDate.now());
            assertNotEquals(c1, c2);

            c1.setToDate(LocalDate.now());
            c1.setCategory(Category.BOOKS);
            assertNotEquals(c1, c2);

            c2.setCategory(Category.BOOKS);
            c1.setSellerUsername("charlie");
            assertNotEquals(c1, c2);

            c2.setSellerUsername("charlie");
            c1.setDealType(DealType.SALE);
            assertNotEquals(c1, c2);

            c2.setDealType(DealType.SALE);
            c1.setSearchQuery("book");
            assertNotEquals(c1, c2);

            c2.setSearchQuery("book");
            assertEquals(c1, c2);
        }
    }

    @Nested
    @DisplayName("MyDealsItem Branch Tests")
    class MyDealsItemTests {

        @Test
        @DisplayName("fromOffer: null handling and various offer types and statuses")
        void testFromOfferVariations() {
            assertNull(MyDealsItem.fromOffer(null));

            // Borrow request
            Offer borrowOffer = new Offer();
            borrowOffer.setId("O1");
            borrowOffer.setType(OfferType.BORROW_REQUEST);
            borrowOffer.setStatus(OfferStatus.PENDING);
            borrowOffer.setListingId("L100");
            borrowOffer.setListingTitle(null);
            borrowOffer.setStartDate(LocalDateTime.now());
            borrowOffer.setEndDate(LocalDateTime.now().plusDays(2));

            MyDealsItem item1 = MyDealsItem.fromOffer(borrowOffer);
            assertNotNull(item1);
            assertEquals(ListingType.LEND, item1.getListingType());
            assertEquals("Lend (Borrow)", item1.getTypeDisplay());
            assertEquals("Unknown Item", item1.getListingTitle());
            assertTrue(item1.canWithdraw());
            assertFalse(item1.canAcceptCounter());
            assertFalse(item1.canDeclineCounter());
            assertFalse(item1.canSelfReturn());
            assertFalse(item1.canMarkComplete());
            assertFalse(item1.canRate());
            assertFalse(item1.isRated());

            // Claim giveaway
            Offer claimOffer = new Offer();
            claimOffer.setId("O2");
            claimOffer.setType(OfferType.CLAIM);
            claimOffer.setStatus(OfferStatus.ACCEPTED);
            MyDealsItem item2 = MyDealsItem.fromOffer(claimOffer);
            assertEquals(ListingType.GIVEAWAY, item2.getListingType());
            assertEquals("Giveaway", item2.getTypeDisplay());
            assertEquals("Accepted", item2.getStatusDisplay());

            // Countered with counter end date
            Offer counterOffer = new Offer();
            counterOffer.setId("O3");
            counterOffer.setType(OfferType.BORROW_REQUEST);
            counterOffer.setStatus(OfferStatus.COUNTERED);
            Offer co = new Offer();
            co.setEndDate(LocalDateTime.of(2026, 12, 1, 10, 0));
            counterOffer.setCounterOffer(co);

            MyDealsItem item3 = MyDealsItem.fromOffer(counterOffer);
            assertEquals("Countered", item3.getStatusDisplay());
            assertNotNull(item3.getCounterDetails());
            assertTrue(item3.getCounterDetails().contains("Counter Due:"));
            assertTrue(item3.canAcceptCounter());
            assertTrue(item3.canDeclineCounter());

            // Countered with price
            Offer coPrice = new Offer();
            coPrice.setPrice(88.50);
            counterOffer.setCounterOffer(coPrice);
            MyDealsItem item3Price = MyDealsItem.fromOffer(counterOffer);
            assertTrue(item3Price.getCounterDetails().contains("Counter Price: HK$ 88.50"));

            // Offer with null status
            Offer nullStatusOffer = new Offer();
            nullStatusOffer.setId("O4");
            nullStatusOffer.setStatus(null);
            MyDealsItem itemNull = MyDealsItem.fromOffer(nullStatusOffer);
            assertEquals("Unknown", itemNull.getStatusDisplay());
        }

        @Test
        @DisplayName("fromDeal: null handling and deal types/statuses")
        void testFromDealVariations() {
            assertNull(MyDealsItem.fromDeal(null));

            Deal giveawayDeal = new Deal();
            giveawayDeal.setId("D1");
            giveawayDeal.setType(DealType.GIVEAWAY);
            giveawayDeal.setStatus(DealStatus.ACTIVE);
            giveawayDeal.setListingId("L1");
            giveawayDeal.setListingTitle("Free Notebook");
            giveawayDeal.setSellerUsername("diana");
            giveawayDeal.setStartDate(LocalDateTime.now());
            giveawayDeal.setClosedDate(null);

            MyDealsItem item1 = MyDealsItem.fromDeal(giveawayDeal);
            assertNotNull(item1);
            assertEquals(ListingType.GIVEAWAY, item1.getListingType());
            assertEquals("Giveaway", item1.getTypeDisplay());
            assertEquals("Active", item1.getStatusDisplay());
            assertTrue(item1.canMarkComplete());
            assertFalse(item1.canSelfReturn());

            // Loan deal
            Deal loanDeal = new Deal();
            loanDeal.setId("D2");
            loanDeal.setType(DealType.LOAN);
            loanDeal.setStatus(DealStatus.ACTIVE);
            loanDeal.setDueDate(LocalDateTime.now().plusDays(3));
            MyDealsItem item2 = MyDealsItem.fromDeal(loanDeal);
            assertEquals(ListingType.LEND, item2.getListingType());
            assertEquals("Lend (Borrow)", item2.getTypeDisplay());
            assertTrue(item2.canSelfReturn());
            assertFalse(item2.canMarkComplete());

            // Completed deal with rating
            Deal completedDeal = new Deal();
            completedDeal.setId("D3");
            completedDeal.setType(DealType.SALE);
            completedDeal.setStatus(DealStatus.COMPLETED);
            completedDeal.setRated(true);
            MyDealsItem item3 = MyDealsItem.fromDeal(completedDeal);
            assertEquals("Completed", item3.getStatusDisplay());
            assertFalse(item3.canRate());
            assertTrue(item3.isRated());

            // Returned deal unrated
            Deal returnedDeal = new Deal();
            returnedDeal.setId("D4");
            returnedDeal.setType(DealType.LOAN);
            returnedDeal.setStatus(DealStatus.RETURNED);
            returnedDeal.setRated(false);
            MyDealsItem item4 = MyDealsItem.fromDeal(returnedDeal);
            assertEquals("Returned", item4.getStatusDisplay());
            assertTrue(item4.canRate());
            assertFalse(item4.isRated());
        }

        @Test
        @DisplayName("Capabilities and formatting methods")
        void testFormattingAndCapabilities() {
            Offer offer = new Offer();
            offer.setId("O10");
            offer.setListingId("L100");
            offer.setSellerUsername("charlie");
            offer.setPrice(123.456);

            MyDealsItem item = MyDealsItem.fromOffer(offer);
            assertTrue(item.canOpenListing());
            assertTrue(item.canOpenChat());
            assertEquals("HK$ 123.46", item.getFormattedPrice());
            assertEquals("Sell", item.getTypeDisplay());

            // null listingId and blank
            Offer oBlank = new Offer();
            oBlank.setId("O11");
            oBlank.setListingId("  ");
            oBlank.setSellerUsername(null);
            MyDealsItem itemBlank = MyDealsItem.fromOffer(oBlank);
            assertFalse(itemBlank.canOpenListing());
            assertFalse(itemBlank.canOpenChat());
            assertEquals("-", itemBlank.getFormattedDueDate());
            assertEquals("-", itemBlank.getFormattedPrice());
            assertEquals("-", itemBlank.getDetailsSummary());

            // Details summary with due date
            Offer oDue = new Offer();
            oDue.setId("O12");
            oDue.setType(OfferType.BORROW_REQUEST);
            oDue.setEndDate(LocalDateTime.of(2026, 12, 15, 14, 0));
            MyDealsItem itemDue = MyDealsItem.fromOffer(oDue);
            assertTrue(itemDue.getDetailsSummary().startsWith("Due: 2026-12-15 14:00"));

            // Getters
            assertEquals("O10", item.getId());
            assertEquals("L100", item.getListingId());
            assertEquals("charlie", item.getSellerUsername());
            assertNull(item.getBuyerUsername());
            assertNotNull(item.getTimestamp());
            assertNotNull(item.getOffer());
            assertNull(item.getDeal());
            assertTrue(item.isOffer());
            assertFalse(item.isDeal());
        }
    }

    @Nested
    @DisplayName("AdminDealRecord Branch Tests")
    class AdminDealRecordTests {

        @Test
        @DisplayName("Deal ID, titles, removed listing and null fallbacks")
        void testTitleAndListingState() {
            Deal d = new Deal();
            d.setId(null);
            d.setListingId("L99");
            d.setListingTitle(null);

            AdminDealRecord r1 = new AdminDealRecord(d, null, null, null, null, null);
            assertEquals("", r1.getDealId());
            assertEquals("L99", r1.getListingId());
            assertTrue(r1.isListingRemoved());
            assertEquals("Unknown Listing", r1.getItemTitle());
            assertEquals("Unknown Listing [Removed]", r1.getItemTitleDisplay());

            // Listing title from deal snapshot
            d.setListingTitle("Vintage Radio");
            AdminDealRecord r2 = new AdminDealRecord(d, null, null, null, null, null);
            assertEquals("Vintage Radio", r2.getItemTitle());
            assertEquals("Vintage Radio [Removed]", r2.getItemTitleDisplay());

            // Active listing not removed
            Listing l = new Listing();
            l.setId("L99");
            l.setTitle("Active Radio");
            l.setStatus(ListingStatus.LIVE);
            AdminDealRecord r3 = new AdminDealRecord(d, l, null, null, null, null);
            assertFalse(r3.isListingRemoved());
            assertEquals("Active Radio", r3.getItemTitle());
            assertEquals("Active Radio", r3.getItemTitleDisplay());

            // Listing marked REMOVED
            l.setStatus(ListingStatus.REMOVED);
            AdminDealRecord r4 = new AdminDealRecord(d, l, null, null, null, null);
            assertTrue(r4.isListingRemoved());
            assertEquals("Active Radio [Removed]", r4.getItemTitleDisplay());
        }

        @Test
        @DisplayName("Buyer and seller displays with deleted accounts and fallbacks")
        void testUserDisplays() {
            Deal d = new Deal();
            d.setId("D1");
            d.setBuyerUsername("alice");
            d.setSellerUsername("charlie");

            AdminDealRecord rNoUsers = new AdminDealRecord(d, null, null, null, null, null);
            assertTrue(rNoUsers.isBuyerDeleted());
            assertTrue(rNoUsers.isSellerDeleted());
            assertEquals("Deleted user", rNoUsers.getBuyerUsernameDisplay());
            assertEquals("Deleted user", rNoUsers.getBuyerFullNameDisplay());
            assertEquals("Deleted user", rNoUsers.getSellerUsernameDisplay());
            assertEquals("Deleted user", rNoUsers.getSellerFullNameDisplay());
            assertEquals("alice", rNoUsers.getRawBuyerUsername());
            assertEquals("charlie", rNoUsers.getRawSellerUsername());

            Buyer buyer = new Buyer("alice", "Alice Wong", "pass");
            Seller seller = new Seller("charlie", "Charlie Lee", "pass");

            AdminDealRecord rActiveUsers = new AdminDealRecord(d, null, buyer, seller, null, null);
            assertFalse(rActiveUsers.isBuyerDeleted());
            assertFalse(rActiveUsers.isSellerDeleted());
            assertEquals("@alice", rActiveUsers.getBuyerUsernameDisplay());
            assertEquals("Alice Wong (@alice)", rActiveUsers.getBuyerFullNameDisplay());
            assertEquals("@charlie", rActiveUsers.getSellerUsernameDisplay());
            assertEquals("Charlie Lee (@charlie)", rActiveUsers.getSellerFullNameDisplay());

            // Deleted status on users
            buyer.setAccountStatus(AccountStatus.DELETED);
            seller.setAccountStatus(AccountStatus.DELETED);
            AdminDealRecord rDeletedFlag = new AdminDealRecord(d, null, buyer, seller, null, null);
            assertTrue(rDeletedFlag.isBuyerDeleted());
            assertTrue(rDeletedFlag.isSellerDeleted());
            assertEquals("Deleted user", rDeletedFlag.getBuyerUsernameDisplay());
            assertEquals("Deleted user", rDeletedFlag.getSellerUsernameDisplay());

            // Blank full names fallback to @username
            Buyer bNoName = new Buyer("alice", "  ", "pass");
            Seller sNoName = new Seller("charlie", "  ", "pass");
            AdminDealRecord rBlankNames = new AdminDealRecord(d, null, bNoName, sNoName, null, null);
            assertEquals("@alice", rBlankNames.getBuyerFullNameDisplay());
            assertEquals("@charlie", rBlankNames.getSellerFullNameDisplay());
        }

        @Test
        @DisplayName("Deal types, statuses, reviews and terms displays")
        void testDealTypesStatusesAndTerms() {
            Deal d = new Deal();
            d.setId("D10");
            d.setType(DealType.SALE);
            d.setStatus(DealStatus.ACTIVE);
            d.setOfferPrice(250.0);
            d.setStartDate(LocalDateTime.of(2026, 11, 10, 15, 30));

            AdminDealRecord rSale = new AdminDealRecord(d, null, null, null, null, null);
            assertEquals(DealType.SALE, rSale.getDealType());
            assertEquals("Sale", rSale.getDealTypeDisplay());
            assertEquals(DealStatus.ACTIVE, rSale.getDealStatus());
            assertEquals("Active", rSale.getDealStatusDisplay());
            assertEquals("$250.00", rSale.getAgreedTermsDisplay());
            assertEquals("10 Nov 2026 15:30", rSale.getStartDateDisplay());
            assertEquals("-", rSale.getReturnOrCompleteDateDisplay());
            assertFalse(rSale.hasReview());
            assertEquals(0, rSale.getReviewRating());
            assertEquals("", rSale.getReviewComment());
            assertEquals("-", rSale.getReviewDateDisplay());
            assertFalse(rSale.isReviewHidden());

            // Loan with due date
            d.setType(DealType.LOAN);
            d.setStatus(DealStatus.RETURNED);
            d.setDueDate(LocalDateTime.of(2026, 11, 17, 18, 0));
            d.setClosedDate(LocalDateTime.of(2026, 11, 16, 12, 0));

            Offer o = new Offer();
            Review rev = new Review();
            rev.setId("REV1");
            rev.setRating(5);
            rev.setComment("Excellent transaction");
            rev.setCreatedAt(LocalDateTime.of(2026, 11, 16, 13, 0));
            rev.setHidden(true);

            AdminDealRecord rLoan = new AdminDealRecord(d, null, null, null, o, rev);
            assertEquals(DealType.LOAN, rLoan.getDealType());
            assertEquals("Loan", rLoan.getDealTypeDisplay());
            assertEquals("Returned", rLoan.getDealStatusDisplay());
            assertTrue(rLoan.getAgreedTermsDisplay().contains("17 Nov 2026"));
            assertTrue(rLoan.hasReview());
            assertEquals(5, rLoan.getReviewRating());
            assertEquals("Excellent transaction", rLoan.getReviewComment());
            assertEquals("16 Nov 2026", rLoan.getReviewDateDisplay());
            assertTrue(rLoan.isReviewHidden());
            assertTrue(rLoan.getReturnOrCompleteDateDisplay().startsWith("Returned:"));

            // Active loan returnOrCompleteDateDisplay
            d.setStatus(DealStatus.ACTIVE);
            AdminDealRecord rActiveLoan = new AdminDealRecord(d, null, null, null, o, null);
            assertTrue(rActiveLoan.getReturnOrCompleteDateDisplay().startsWith("Due:"));

            // Completed sale returnOrCompleteDateDisplay
            d.setType(DealType.SALE);
            d.setStatus(DealStatus.COMPLETED);
            AdminDealRecord rCompletedSale = new AdminDealRecord(d, null, null, null, o, null);
            assertTrue(rCompletedSale.getReturnOrCompleteDateDisplay().startsWith("Completed:"));

            // Giveaway deal
            d.setType(DealType.GIVEAWAY);
            d.setStatus(DealStatus.COMPLETED);
            AdminDealRecord rGive = new AdminDealRecord(d, null, null, null, null, null);
            assertEquals(DealType.GIVEAWAY, rGive.getDealType());
            assertEquals("Giveaway", rGive.getDealTypeDisplay());
            assertEquals("Completed", rGive.getDealStatusDisplay());
            assertEquals("Free (Giveaway Item)", rGive.getAgreedTermsDisplay());

            // Null deal type & status fallbacks
            d.setType(null);
            d.setStatus((DealStatus) null);
            AdminDealRecord rNullType = new AdminDealRecord(d, null, null, null, null, null);
            assertEquals("Unknown", rNullType.getDealTypeDisplay());
            assertEquals("Unknown", rNullType.getDealStatusDisplay());
            assertEquals("N/A", rNullType.getAgreedTermsDisplay());
        }
    }

    @Nested
    @DisplayName("AdminDealFilterCriteria Branch Tests")
    class AdminDealFilterCriteriaTests {

        @Test
        @DisplayName("matches: null record returns false")
        void testMatchesNull() {
            AdminDealFilterCriteria criteria = AdminDealFilterCriteria.all();
            assertFalse(criteria.matches(null));
        }

        @Test
        @DisplayName("matches: dealType, dealStatus, date ranges, and keyword search")
        void testMatchesAllBranches() {
            Deal d = new Deal();
            d.setId("D100");
            d.setType(DealType.SALE);
            d.setStatus(DealStatus.COMPLETED);
            d.setListingTitle("Calculus Book");
            d.setBuyerUsername("alice");
            d.setSellerUsername("charlie");
            d.setStartDate(LocalDateTime.of(2026, 11, 5, 10, 0));

            Buyer b = new Buyer("alice", "Alice Wong", "pass");
            Seller s = new Seller("charlie", "Charlie Lee", "pass");
            AdminDealRecord record = new AdminDealRecord(d, null, b, s, null, null);

            // Filter mismatch type
            AdminDealFilterCriteria cType = new AdminDealFilterCriteria(null, DealType.LOAN, null, null, null);
            assertFalse(cType.matches(record));

            // Filter mismatch status
            AdminDealFilterCriteria cStatus = new AdminDealFilterCriteria(null, null, DealStatus.ACTIVE, null, null);
            assertFalse(cStatus.matches(record));

            // Filter date range
            AdminDealFilterCriteria cDateBefore = new AdminDealFilterCriteria(null, null, null, LocalDate.of(2026, 11, 10), null);
            assertFalse(cDateBefore.matches(record));

            AdminDealFilterCriteria cDateAfter = new AdminDealFilterCriteria(null, null, null, null, LocalDate.of(2026, 11, 1));
            assertFalse(cDateAfter.matches(record));

            AdminDealFilterCriteria cDateMatch = new AdminDealFilterCriteria(null, null, null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));
            assertTrue(cDateMatch.matches(record));

            // Keyword searches
            AdminDealFilterCriteria cTitle = new AdminDealFilterCriteria("calculus", null, null, null, null);
            assertTrue(cTitle.matches(record));

            AdminDealFilterCriteria cBuyer = new AdminDealFilterCriteria("Alice", null, null, null, null);
            assertTrue(cBuyer.matches(record));

            AdminDealFilterCriteria cSeller = new AdminDealFilterCriteria("Charlie", null, null, null, null);
            assertTrue(cSeller.matches(record));

            // Deleted user keyword matching
            b.setAccountStatus(AccountStatus.DELETED);
            AdminDealFilterCriteria cDeleted = new AdminDealFilterCriteria("deleted user", null, null, null, null);
            assertTrue(cDeleted.matches(record));

            AdminDealFilterCriteria cNotFound = new AdminDealFilterCriteria("NonExistent", null, null, null, null);
            assertFalse(cNotFound.matches(record));
        }

        @Test
        @DisplayName("isEmpty, isValidDateRange, equals, and hashCode")
        void testBooleansAndEquality() {
            AdminDealFilterCriteria c1 = new AdminDealFilterCriteria();
            assertTrue(c1.isEmpty());
            assertTrue(c1.isValidDateRange());

            // Invalid date range
            AdminDealFilterCriteria cInvalidDate = new AdminDealFilterCriteria(
                    null, null, null, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 5)
            );
            assertFalse(cInvalidDate.isValidDateRange());

            AdminDealFilterCriteria c2 = new AdminDealFilterCriteria("query", DealType.SALE, DealStatus.COMPLETED,
                    LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));
            assertFalse(c2.isEmpty());

            AdminDealFilterCriteria c3 = new AdminDealFilterCriteria("query", DealType.SALE, DealStatus.COMPLETED,
                    LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));
            assertEquals(c2, c3);
            assertEquals(c2.hashCode(), c3.hashCode());
            assertEquals(c2, c2);
            assertNotEquals(c2, null);
            assertNotEquals(c2, "string");

            AdminDealFilterCriteria cDiff = new AdminDealFilterCriteria("different", null, null, null, null);
            assertNotEquals(c2, cDiff);
        }
    }

    @Nested
    @DisplayName("AdminListingCreateRequest & EditRequest Tests")
    class AdminListingRequestTests {

        @Test
        @DisplayName("AdminListingCreateRequest: duration calculations, unit resolution and photos")
        void testCreateRequestDurationsAndPhotos() {
            AdminListingCreateRequest req = new AdminListingCreateRequest();
            req.setSellerUsername("seller");
            req.setTitle("Title");
            req.setDescription("Desc");
            req.setType(ListingType.LEND);
            req.setCategory(Category.ELECTRONICS);
            req.setCondition(Condition.LIKE_NEW);
            req.setMeetupLocation(MeetupLocation.LIBRARY);
            req.setPrice(10.0);

            assertEquals("seller", req.getSellerUsername());
            assertEquals("Title", req.getTitle());
            assertEquals("Desc", req.getDescription());
            assertEquals(ListingType.LEND, req.getType());
            assertEquals(Category.ELECTRONICS, req.getCategory());
            assertEquals(Condition.LIKE_NEW, req.getCondition());
            assertEquals(MeetupLocation.LIBRARY, req.getMeetupLocation());
            assertEquals(10.0, req.getPrice());

            // Lending duration branches
            assertNull(req.getLendingDays());
            assertNull(req.getLendingDuration());

            req.setLendingDays(7);
            assertEquals(7, req.getLendingDays());
            assertEquals(Duration.ofDays(7), req.getLendingDuration());
            assertEquals("7", req.getLendingAmount());
            assertEquals(LendingDurationUnit.DAYS, req.getLendingUnit());

            req.setLendingDays(null);
            assertNull(req.getLendingDays());
            assertNull(req.getLendingDuration());

            req.setLendingDuration(Duration.ofDays(14));
            assertEquals(14, req.getLendingDays());
            assertEquals(LendingDurationUnit.WEEKS, req.getLendingUnit());
            assertEquals("2", req.getLendingAmount());

            req.setLendingDuration(Duration.ofHours(3));
            assertEquals(LendingDurationUnit.HOURS, req.getLendingUnit());
            assertEquals("3", req.getLendingAmount());

            req.setLendingDuration(Duration.ofMinutes(45));
            assertEquals(LendingDurationUnit.MINUTES, req.getLendingUnit());
            assertEquals("45", req.getLendingAmount());

            req.setLendingDuration(Duration.ofSeconds(17));
            assertEquals(LendingDurationUnit.SECONDS, req.getLendingUnit());
            assertEquals("17", req.getLendingAmount());

            req.setLendingDuration(Duration.ZERO);
            assertEquals(LendingDurationUnit.DAYS, req.getLendingUnit());
            assertEquals("0", req.getLendingAmount());

            req.setLendingDuration(null);
            assertNull(req.getLendingDuration());

            // setLending
            req.setLending("5", LendingDurationUnit.DAYS);
            assertEquals(5, req.getLendingDays());

            req.setLending("invalid", LendingDurationUnit.DAYS);
            assertNull(req.getLendingDays());

            req.setLendingAmount("12");
            req.setLendingUnit(LendingDurationUnit.HOURS);
            assertNotNull(req.getLendingDuration());

            req.setLendingAmount("bad");
            assertNull(req.getLendingDuration());

            // Photo management
            Photo p1 = new Photo("P1", "path1.jpg");
            req.addPhoto(p1);
            req.addPhoto(null);
            assertEquals(1, req.getPhotos().size());

            req.setPhotos(null);
            assertTrue(req.getPhotos().isEmpty());

            req.setPhotos(List.of(p1));
            assertEquals(1, req.getPhotos().size());

            // Factory ofDuration
            AdminListingCreateRequest ofDur = AdminListingCreateRequest.ofDuration(
                    "seller", "T", "D", ListingType.SELL, Category.BOOKS, Condition.NEW,
                    MeetupLocation.LG1_CANTEEN, 5.0, Duration.ofDays(3), List.of(p1)
            );
            assertNotNull(ofDur);
            assertEquals(3, ofDur.getLendingDays());
        }

        @Test
        @DisplayName("AdminListingEditRequest: duration calculations, unit resolution and photos")
        void testEditRequestDurationsAndPhotos() {
            AdminListingEditRequest req = new AdminListingEditRequest();
            req.setListingId("L100");
            req.setTitle("Title");
            req.setDescription("Desc");
            req.setType(ListingType.LEND);
            req.setCategory(Category.ELECTRONICS);
            req.setCondition(Condition.LIKE_NEW);
            req.setMeetupLocation(MeetupLocation.LIBRARY);
            req.setPrice(10.0);

            assertEquals("L100", req.getListingId());
            assertEquals("Title", req.getTitle());
            assertEquals("Desc", req.getDescription());
            assertEquals(ListingType.LEND, req.getType());
            assertEquals(Category.ELECTRONICS, req.getCategory());
            assertEquals(Condition.LIKE_NEW, req.getCondition());
            assertEquals(MeetupLocation.LIBRARY, req.getMeetupLocation());
            assertEquals(10.0, req.getPrice());

            req.setLendingDays(7);
            assertEquals(7, req.getLendingDays());
            assertEquals(Duration.ofDays(7), req.getLendingDuration());

            req.setLendingDays(null);
            assertNull(req.getLendingDays());

            req.setLendingDuration(Duration.ofDays(14));
            assertEquals(14, req.getLendingDays());
            assertEquals(LendingDurationUnit.WEEKS, req.getLendingUnit());

            req.setLendingDuration(Duration.ofHours(3));
            assertEquals(LendingDurationUnit.HOURS, req.getLendingUnit());

            req.setLendingDuration(Duration.ofMinutes(45));
            assertEquals(LendingDurationUnit.MINUTES, req.getLendingUnit());

            req.setLendingDuration(Duration.ofSeconds(17));
            assertEquals(LendingDurationUnit.SECONDS, req.getLendingUnit());

            req.setLendingDuration(Duration.ZERO);
            assertEquals(LendingDurationUnit.DAYS, req.getLendingUnit());

            req.setLendingDuration(null);
            assertNull(req.getLendingDuration());

            req.setLending("5", LendingDurationUnit.DAYS);
            assertEquals(5, req.getLendingDays());

            req.setLending("invalid", LendingDurationUnit.DAYS);
            assertNull(req.getLendingDays());

            req.setLendingAmount("12");
            req.setLendingUnit(LendingDurationUnit.HOURS);
            assertNotNull(req.getLendingDuration());

            req.setLendingAmount("bad");
            assertNull(req.getLendingDuration());

            Photo p1 = new Photo("P1", "path1.jpg");
            req.addPhoto(p1);
            req.addPhoto(null);
            assertEquals(1, req.getPhotos().size());

            req.setPhotos(null);
            assertTrue(req.getPhotos().isEmpty());

            req.setPhotos(List.of(p1));
            assertEquals(1, req.getPhotos().size());

            AdminListingEditRequest ofDur = AdminListingEditRequest.ofDuration(
                    "L100", "T", "D", ListingType.SELL, Category.BOOKS, Condition.NEW,
                    MeetupLocation.LG1_CANTEEN, 5.0, Duration.ofDays(3), List.of(p1)
            );
            assertNotNull(ofDur);
            assertEquals("L100", ofDur.getListingId());
        }
    }

    @Nested
    @DisplayName("AdminRatingFilterCriteria Branch Tests")
    class AdminRatingFilterCriteriaTests {

        @Test
        @DisplayName("matches: null review, date validity, and state filters")
        void testRatingCriteriaMatches() {
            AdminRatingFilterCriteria criteria = AdminRatingFilterCriteria.all();
            assertFalse(criteria.matches(null));
            assertTrue(criteria.isValidDateRange());

            // Invalid date range
            criteria.setStartDate(LocalDate.of(2026, 11, 10));
            criteria.setEndDate(LocalDate.of(2026, 11, 5));
            assertFalse(criteria.isValidDateRange());

            // Valid date range
            criteria.setEndDate(LocalDate.of(2026, 11, 15));
            assertTrue(criteria.isValidDateRange());

            Review r = new Review();
            r.setSellerUsername("charlie");
            r.setBuyerUsername("alice");
            r.setListingTitle("Organic Chemistry Textbook");
            r.setRating(5);
            r.setDate(LocalDateTime.of(2026, 11, 12, 10, 0));
            r.setFlagged(false);
            r.setHidden(false);

            assertTrue(criteria.matches(r));

            // Date before start
            r.setDate(LocalDateTime.of(2026, 11, 8, 10, 0));
            assertFalse(criteria.matches(r));

            // Date after end
            r.setDate(LocalDateTime.of(2026, 11, 20, 10, 0));
            assertFalse(criteria.matches(r));

            // Date null
            r.setDate(null);
            assertFalse(criteria.matches(r));

            r.setDate(LocalDateTime.of(2026, 11, 12, 10, 0));

            // User query matches
            criteria.setStartDate(null);
            criteria.setEndDate(null);

            criteria.setUserQuery("charlie");
            assertTrue(criteria.matches(r));

            criteria.setUserQuery("alice");
            assertTrue(criteria.matches(r));

            criteria.setUserQuery("chemistry");
            assertTrue(criteria.matches(r));

            criteria.setUserQuery("nomatch");
            assertFalse(criteria.matches(r));

            criteria.setUserQuery(null);

            // Rating score filter
            criteria.setRatingScore(5);
            assertTrue(criteria.matches(r));
            criteria.setRatingScore(4);
            assertFalse(criteria.matches(r));
            criteria.setRatingScore(6); // out of range
            assertTrue(criteria.matches(r));
            criteria.setRatingScore(null);

            // State filter: FLAGGED_ONLY
            AdminRatingFilterCriteria flaggedCrit = AdminRatingFilterCriteria.flaggedOnly();
            assertFalse(flaggedCrit.matches(r));
            r.setFlagged(true);
            assertTrue(flaggedCrit.matches(r));

            // State filter: HIDDEN_ONLY
            AdminRatingFilterCriteria hiddenCrit = AdminRatingFilterCriteria.hiddenOnly();
            assertFalse(hiddenCrit.matches(r));
            r.setHidden(true);
            assertTrue(hiddenCrit.matches(r));

            // State filter: VISIBLE_ONLY
            AdminRatingFilterCriteria visibleCrit = AdminRatingFilterCriteria.visibleOnly();
            assertFalse(visibleCrit.matches(r));
            r.setHidden(false);
            assertTrue(visibleCrit.matches(r));
        }
    }

    @Nested
    @DisplayName("AdminNotificationFilterCriteria Branch Tests")
    class AdminNotificationFilterCriteriaTests {

        @Test
        @DisplayName("matches: categories, unreadOnly, search queries, equals and hashCode")
        void testNotificationFilterCriteria() {
            AdminNotificationFilterCriteria all = AdminNotificationFilterCriteria.all();
            assertFalse(all.matches(null));

            Notification n = new Notification(
                    "admin", "Report Filed", "Buyer reported an issue",
                    "Reports", "L101", "LISTING"
            );
            n.setRead(false);

            assertTrue(all.matches(n));

            // Category match
            AdminNotificationFilterCriteria reportCrit = AdminNotificationFilterCriteria.byCategory(AdminNotificationCategory.REPORTS);
            assertTrue(reportCrit.matches(n));

            AdminNotificationFilterCriteria auditCrit = AdminNotificationFilterCriteria.byCategory(AdminNotificationCategory.USER_ACCOUNTS);
            assertFalse(auditCrit.matches(n));

            // Unread only
            AdminNotificationFilterCriteria unreadCrit = AdminNotificationFilterCriteria.unreadOnly();
            assertTrue(unreadCrit.matches(n));
            n.setRead(true);
            assertFalse(unreadCrit.matches(n));
            n.setRead(false);

            // Search query matches
            AdminNotificationFilterCriteria sTitle = AdminNotificationFilterCriteria.withSearch("report");
            assertTrue(sTitle.matches(n));

            AdminNotificationFilterCriteria sMsg = AdminNotificationFilterCriteria.withSearch("issue");
            assertTrue(sMsg.matches(n));

            AdminNotificationFilterCriteria sId = AdminNotificationFilterCriteria.withSearch("L101");
            assertTrue(sId.matches(n));

            AdminNotificationFilterCriteria sCat = AdminNotificationFilterCriteria.withSearch("reports");
            assertTrue(sCat.matches(n));

            AdminNotificationFilterCriteria sNone = AdminNotificationFilterCriteria.withSearch("unrelated_keyword");
            assertFalse(sNone.matches(n));

            // equals, hashCode, toString
            assertEquals(all, AdminNotificationFilterCriteria.all());
            assertEquals(all.hashCode(), AdminNotificationFilterCriteria.all().hashCode());
            assertEquals(all, all);
            assertNotEquals(all, null);
            assertNotEquals(all, "string");
            assertNotEquals(all, reportCrit);
            assertNotNull(all.toString());
        }
    }

    @Nested
    @DisplayName("ListingType, NotificationCategory, and NotificationTargetType Enums")
    class EnumBranchTests {

        @Test
        @DisplayName("ListingType: fromString branches")
        void testListingTypeFromString() {
            assertEquals(ListingType.SELL, ListingType.fromString(null));
            assertEquals(ListingType.SELL, ListingType.fromString("sell"));
            assertEquals(ListingType.SELL, ListingType.fromString("SELL"));
            assertEquals(ListingType.LEND, ListingType.fromString("lend"));
            assertEquals(ListingType.LEND, ListingType.fromString("Lend"));
            assertEquals(ListingType.GIVEAWAY, ListingType.fromString("giveaway"));
            assertEquals(ListingType.GIVEAWAY, ListingType.fromString("Giveaway"));
            assertEquals(ListingType.SELL, ListingType.fromString("unknown_val"));
            assertEquals("Sell", ListingType.SELL.toString());
            assertEquals("Sell", ListingType.SELL.getDisplayName());
        }

        @Test
        @DisplayName("NotificationTargetType: fromString branches")
        void testNotificationTargetTypeFromString() {
            assertEquals(NotificationTargetType.NONE, NotificationTargetType.fromString(null));
            assertEquals(NotificationTargetType.NONE, NotificationTargetType.fromString(""));
            assertEquals(NotificationTargetType.OFFER, NotificationTargetType.fromString("OFFER"));
            assertEquals(NotificationTargetType.OFFER, NotificationTargetType.fromString("formal offer"));
            assertEquals(NotificationTargetType.CHAT_THREAD, NotificationTargetType.fromString("chat thread"));
            assertEquals(NotificationTargetType.DEAL, NotificationTargetType.fromString("deal agreement"));
            assertEquals(NotificationTargetType.DEAL, NotificationTargetType.fromString("loan reminder"));
            assertEquals(NotificationTargetType.LISTING, NotificationTargetType.fromString("listing item"));
            assertEquals(NotificationTargetType.REVIEW, NotificationTargetType.fromString("review moderation"));
            assertEquals(NotificationTargetType.REVIEW, NotificationTargetType.fromString("seller rating"));
            assertEquals(NotificationTargetType.NONE, NotificationTargetType.fromString("something else"));
        }

        @Test
        @DisplayName("NotificationCategory & AdminNotificationCategory: fromRaw branches")
        void testNotificationCategoriesFromRaw() {
            // NotificationCategory
            assertEquals(NotificationCategory.ANNOUNCEMENTS, NotificationCategory.fromRaw(null));
            assertEquals(NotificationCategory.ANNOUNCEMENTS, NotificationCategory.fromRaw("   "));
            assertEquals(NotificationCategory.OFFERS, NotificationCategory.fromRaw("Offers"));
            assertEquals(NotificationCategory.OFFERS, NotificationCategory.fromRaw("deal"));
            assertEquals(NotificationCategory.CHAT, NotificationCategory.fromRaw("Message"));
            assertEquals(NotificationCategory.BORROW_REMINDERS, NotificationCategory.fromRaw("Due Date"));
            assertEquals(NotificationCategory.LISTINGS, NotificationCategory.fromRaw("Item"));
            assertEquals(NotificationCategory.REVIEWS, NotificationCategory.fromRaw("Feedback"));
            assertEquals(NotificationCategory.OFFERS, NotificationCategory.fromRaw("has an offer inside"));
            assertEquals(NotificationCategory.CHAT, NotificationCategory.fromRaw("chat message"));
            assertEquals(NotificationCategory.BORROW_REMINDERS, NotificationCategory.fromRaw("borrow period"));
            assertEquals(NotificationCategory.LISTINGS, NotificationCategory.fromRaw("new list item"));
            assertEquals(NotificationCategory.REVIEWS, NotificationCategory.fromRaw("rate seller"));
            assertEquals(NotificationCategory.ANNOUNCEMENTS, NotificationCategory.fromRaw("completely random"));

            assertTrue(NotificationCategory.OFFERS.matches("Offers"));
            assertFalse(NotificationCategory.OFFERS.matches("Chat"));

            // AdminNotificationCategory
            assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw(null));
            assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw("  "));
            assertEquals(AdminNotificationCategory.REPORTS, AdminNotificationCategory.fromRaw("User Reports"));
            assertEquals(AdminNotificationCategory.FLAGGED_REVIEWS, AdminNotificationCategory.fromRaw("Flagged Reviews"));
            assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS, AdminNotificationCategory.fromRaw("Listings"));
            assertEquals(AdminNotificationCategory.USER_ACCOUNTS, AdminNotificationCategory.fromRaw("User Accounts"));
            assertEquals(AdminNotificationCategory.REPORTS, AdminNotificationCategory.fromRaw("filed report"));
            assertEquals(AdminNotificationCategory.FLAGGED_REVIEWS, AdminNotificationCategory.fromRaw("flag review"));
            assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS, AdminNotificationCategory.fromRaw("pending submission"));
            assertEquals(AdminNotificationCategory.USER_ACCOUNTS, AdminNotificationCategory.fromRaw("account audit"));
            assertEquals(AdminNotificationCategory.FLAGGED_REVIEWS, AdminNotificationCategory.fromRaw("rating flag"));
            assertEquals(AdminNotificationCategory.LISTING_SUBMISSIONS, AdminNotificationCategory.fromRaw("listing check"));
            assertEquals(AdminNotificationCategory.ANNOUNCEMENTS, AdminNotificationCategory.fromRaw("unrecognized board"));

            assertTrue(AdminNotificationCategory.REPORTS.matches("Reports"));
            assertFalse(AdminNotificationCategory.REPORTS.matches("User Accounts"));
        }
    }

    @Nested
    @DisplayName("SellerRatingInfo, UserManagementFilterCriteria, AdminProfileUpdateResult")
    class MiscModelBranchTests {

        @Test
        @DisplayName("SellerRatingInfo: noRatings, withRatings, formatting and equality")
        void testSellerRatingInfo() {
            SellerRatingInfo noRat = SellerRatingInfo.noRatings("bob", "Bob Wong");
            assertFalse(noRat.hasRatings());
            assertEquals("N/A", noRat.getFormattedScore());
            assertEquals("No ratings yet", noRat.getDisplayText());

            SellerRatingInfo singleRat = SellerRatingInfo.withRatings("charlie", "Charlie Lee", 4.5, 1);
            assertTrue(singleRat.hasRatings());
            assertEquals("4.5", singleRat.getFormattedScore());
            assertTrue(singleRat.getDisplayText().contains("1 review"));

            SellerRatingInfo multiRat = SellerRatingInfo.withRatings("charlie", "Charlie Lee", 4.5, 3);
            assertTrue(multiRat.getDisplayText().contains("3 reviews"));

            assertEquals(singleRat, singleRat);
            assertNotEquals(singleRat, null);
            assertNotEquals(singleRat, "string");
            assertNotEquals(singleRat, multiRat);
            assertNotNull(singleRat.toString());
        }

        @Test
        @DisplayName("UserManagementFilterCriteria: matches and equality")
        void testUserManagementFilterCriteria() {
            UserManagementFilterCriteria crit = UserManagementFilterCriteria.all();
            assertFalse(crit.matches(null));

            Buyer b = new Buyer("alice", "Alice Chan", "pass");
            b.setAccountStatus(AccountStatus.ACTIVE);

            assertTrue(crit.matches(b));

            crit.setRole(UserRole.SELLER);
            assertFalse(crit.matches(b));

            crit.setRole(UserRole.BUYER);
            assertTrue(crit.matches(b));

            crit.setStatus(AccountStatus.DEACTIVATED);
            assertFalse(crit.matches(b));

            crit.setStatus(AccountStatus.ACTIVE);
            assertTrue(crit.matches(b));

            crit.setSearchKeyword("alice");
            assertTrue(crit.matches(b));

            crit.setSearchKeyword("chan");
            assertTrue(crit.matches(b));

            crit.setSearchKeyword("david");
            assertFalse(crit.matches(b));

            UserManagementFilterCriteria crit2 = new UserManagementFilterCriteria("david", UserRole.BUYER, AccountStatus.ACTIVE);
            assertEquals(crit, crit2);
            assertEquals(crit.hashCode(), crit2.hashCode());
            assertEquals(crit2, crit2);
            assertNotEquals(crit2, null);
            assertNotEquals(crit2, "other");

            UserManagementFilterCriteria critDiff = new UserManagementFilterCriteria("other", null, null);
            assertNotEquals(crit, critDiff);
        }

        @Test
        @DisplayName("AdminProfileUpdateResult: success, failures, weak password, equality")
        void testAdminProfileUpdateResult() {
            Admin admin = new Admin("admin", "Admin User", "pass");
            AdminProfileUpdateResult resSuccess = AdminProfileUpdateResult.success(admin, "Updated successfully");
            assertTrue(resSuccess.isSuccess());
            assertEquals(AdminProfileUpdateStatus.SUCCESS, resSuccess.getStatus());
            assertEquals(admin, resSuccess.getUpdatedAdmin());

            AdminProfileUpdateResult resFailSet = AdminProfileUpdateResult.failure(
                    AdminProfileUpdateStatus.EMPTY_FULL_NAME, "Name is required", Set.of("fullName")
            );
            assertFalse(resFailSet.isSuccess());
            assertEquals(1, resFailSet.getAffectedFields().size());

            AdminProfileUpdateResult resFailSingle = AdminProfileUpdateResult.failure(
                    AdminProfileUpdateStatus.WRONG_CURRENT_PASSWORD, "Wrong password", "currentPassword"
            );
            assertFalse(resFailSingle.isSuccess());
            assertTrue(resFailSingle.getAffectedFields().contains("currentPassword"));

            AdminProfileUpdateResult resFailNull = AdminProfileUpdateResult.failure(
                    AdminProfileUpdateStatus.UNAUTHORIZED, "Unauthorized", (String) null
            );
            assertTrue(resFailNull.getAffectedFields().isEmpty());

            AdminProfileUpdateResult resWeak = AdminProfileUpdateResult.weakPassword(
                    List.of("Must have 1 digit"), "Weak password"
            );
            assertFalse(resWeak.isSuccess());
            assertEquals(AdminProfileUpdateStatus.WEAK_NEW_PASSWORD, resWeak.getStatus());
            assertEquals(1, resWeak.getPasswordViolations().size());

            assertEquals(resSuccess, resSuccess);
            assertNotEquals(resSuccess, null);
            assertNotEquals(resSuccess, "str");
            assertNotEquals(resSuccess, resWeak);
            assertNotNull(resSuccess.toString());
            assertEquals(resSuccess.hashCode(), resSuccess.hashCode());
        }
    }
}
