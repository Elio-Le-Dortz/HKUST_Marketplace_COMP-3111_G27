package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.hkust.marketplace.utils.SessionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-A05 Admin Deals Service Test Suite")
class AdminDealsServiceTest {

    private DummyDataService dataService;
    private AdminDealsService service;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        service = new AdminDealsService(dataService);
        SessionManager.login(dataService.findUser(DummyDataService.SEEDED_ADMIN_USERNAME));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("Basic Flow: queryDeals retrieves all seeded transactions sorted newest first")
    void testBasicFlowQueryAllDealsNewestFirst() {
        AdminDealQueryResult result = service.queryDeals(null);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(AdminDealQueryStatus.SUCCESS, result.getStatus());
        assertTrue(result.getFilteredCount() > 0);
        assertEquals(result.getTotalSystemDealsCount(), result.getFilteredCount());

        List<AdminDealRecord> records = result.getDeals();
        // Verify newest first sorting
        for (int i = 0; i < records.size() - 1; i++) {
            LocalDateTime curr = records.get(i).getStartDate();
            LocalDateTime next = records.get(i + 1).getStartDate();
            if (curr != null && next != null) {
                assertFalse(curr.isBefore(next), "Deals must be ordered newest first (descending start date)");
            }
        }
    }

    @Test
    @DisplayName("Basic Flow: getDealDetails resolves full transaction oversight projection")
    void testGetDealDetails() {
        List<AdminDealRecord> deals = service.queryDeals(null).getDeals();
        assertFalse(deals.isEmpty());

        String dealId = deals.get(0).getDealId();
        Optional<AdminDealRecord> detailsOpt = service.getDealDetails(dealId);

        assertTrue(detailsOpt.isPresent());
        AdminDealRecord details = detailsOpt.get();
        assertEquals(dealId, details.getDealId());
        assertNotNull(details.getItemTitle());
        assertNotNull(details.getBuyerUsernameDisplay());
        assertNotNull(details.getSellerUsernameDisplay());
    }

    @Test
    @DisplayName("Edge Case: getDealDetails handles null, blank, and non-existent IDs")
    void testGetDealDetailsEdgeCases() {
        assertFalse(service.getDealDetails(null).isPresent());
        assertFalse(service.getDealDetails("   ").isPresent());
        assertFalse(service.getDealDetails("NON_EXISTENT_DEAL_999").isPresent());
    }

    @Test
    @DisplayName("Alternative Flow A1: No Deals in the system returns NO_DEALS_YET status")
    void testAlternativeFlowA1NoDeals() {
        dataService.clearDeals();

        AdminDealQueryResult result = service.queryDeals(AdminDealFilterCriteria.all());

        assertEquals(AdminDealQueryStatus.NO_DEALS_YET, result.getStatus());
        assertTrue(result.isNoDealsYet());
        assertEquals(0, result.getFilteredCount());
        assertEquals(0, result.getTotalSystemDealsCount());

        dataService.resetData();
    }

    @Test
    @DisplayName("Alternative Flow A2: Filtering by DealType (Sale, Loan, Giveaway)")
    void testAlternativeFlowA2FilterByType() {
        // Filter by SALE
        AdminDealFilterCriteria saleCriteria = new AdminDealFilterCriteria(null, DealType.SALE, null, null, null);
        AdminDealQueryResult saleResult = service.queryDeals(saleCriteria);
        assertTrue(saleResult.isSuccess());
        for (AdminDealRecord r : saleResult.getDeals()) {
            assertEquals(DealType.SALE, r.getDealType());
        }

        // Filter by LOAN
        AdminDealFilterCriteria loanCriteria = new AdminDealFilterCriteria(null, DealType.LOAN, null, null, null);
        AdminDealQueryResult loanResult = service.queryDeals(loanCriteria);
        assertTrue(loanResult.isSuccess());
        for (AdminDealRecord r : loanResult.getDeals()) {
            assertEquals(DealType.LOAN, r.getDealType());
        }

        // Filter by GIVEAWAY
        AdminDealFilterCriteria giveawayCriteria = new AdminDealFilterCriteria(null, DealType.GIVEAWAY, null, null, null);
        AdminDealQueryResult giveawayResult = service.queryDeals(giveawayCriteria);
        assertTrue(giveawayResult.isSuccess());
        for (AdminDealRecord r : giveawayResult.getDeals()) {
            assertEquals(DealType.GIVEAWAY, r.getDealType());
        }
    }

    @Test
    @DisplayName("Alternative Flow A2: Filtering by DealStatus (Active, Completed, Returned)")
    void testAlternativeFlowA2FilterByStatus() {
        // Active deals
        AdminDealFilterCriteria activeCriteria = new AdminDealFilterCriteria(null, null, DealStatus.ACTIVE, null, null);
        AdminDealQueryResult activeResult = service.queryDeals(activeCriteria);
        assertTrue(activeResult.isSuccess());
        for (AdminDealRecord r : activeResult.getDeals()) {
            assertEquals(DealStatus.ACTIVE, r.getDealStatus());
        }

        // Completed deals
        AdminDealFilterCriteria completedCriteria = new AdminDealFilterCriteria(null, null, DealStatus.COMPLETED, null, null);
        AdminDealQueryResult completedResult = service.queryDeals(completedCriteria);
        assertTrue(completedResult.isSuccess());
        for (AdminDealRecord r : completedResult.getDeals()) {
            assertEquals(DealStatus.COMPLETED, r.getDealStatus());
        }

        // Returned deals
        AdminDealFilterCriteria returnedCriteria = new AdminDealFilterCriteria(null, null, DealStatus.RETURNED, null, null);
        AdminDealQueryResult returnedResult = service.queryDeals(returnedCriteria);
        assertTrue(returnedResult.isSuccess());
        for (AdminDealRecord r : returnedResult.getDeals()) {
            assertEquals(DealStatus.RETURNED, r.getDealStatus());
        }
    }

    @Test
    @DisplayName("Alternative Flow A2: Search Query filtering by item title, buyer username, and seller username")
    void testAlternativeFlowA2SearchQuery() {
        // Search by item title
        AdminDealFilterCriteria titleCriteria = new AdminDealFilterCriteria("Textbook", null, null, null, null);
        AdminDealQueryResult titleResult = service.queryDeals(titleCriteria);
        assertTrue(titleResult.isSuccess());
        for (AdminDealRecord r : titleResult.getDeals()) {
            assertTrue(r.getItemTitle().toLowerCase().contains("textbook")
                    || r.getBuyerUsernameDisplay().toLowerCase().contains("textbook")
                    || r.getSellerUsernameDisplay().toLowerCase().contains("textbook"));
        }

        // Search by buyer username "alice"
        AdminDealFilterCriteria buyerCriteria = new AdminDealFilterCriteria("alice", null, null, null, null);
        AdminDealQueryResult buyerResult = service.queryDeals(buyerCriteria);
        assertTrue(buyerResult.isSuccess());
        for (AdminDealRecord r : buyerResult.getDeals()) {
            assertTrue(r.getRawBuyerUsername().equalsIgnoreCase("alice")
                    || r.getBuyerFullNameDisplay().toLowerCase().contains("alice"));
        }

        // Search by seller username "charlie"
        AdminDealFilterCriteria sellerCriteria = new AdminDealFilterCriteria("charlie", null, null, null, null);
        AdminDealQueryResult sellerResult = service.queryDeals(sellerCriteria);
        assertTrue(sellerResult.isSuccess());
        for (AdminDealRecord r : sellerResult.getDeals()) {
            assertTrue(r.getRawSellerUsername().equalsIgnoreCase("charlie")
                    || r.getSellerFullNameDisplay().toLowerCase().contains("charlie"));
        }
    }

    @Test
    @DisplayName("Alternative Flow A2: Date range filtering (From date, To date)")
    void testAlternativeFlowA2DateRange() {
        LocalDate today = LocalDate.now();
        LocalDate past = today.minusDays(30);

        AdminDealFilterCriteria dateRangeCriteria = new AdminDealFilterCriteria(null, null, null, past, today.plusDays(1));
        AdminDealQueryResult result = service.queryDeals(dateRangeCriteria);
        assertTrue(result.isSuccess());
        for (AdminDealRecord r : result.getDeals()) {
            assertNotNull(r.getStartDate());
            assertFalse(r.getStartDate().toLocalDate().isBefore(past));
            assertFalse(r.getStartDate().toLocalDate().isAfter(today.plusDays(1)));
        }
    }

    @Test
    @DisplayName("Alternative Flow A3: No matching deals returns NO_MATCHING_DEALS status")
    void testAlternativeFlowA3NoMatchingDeals() {
        AdminDealFilterCriteria criteria = new AdminDealFilterCriteria(
                "ImpossibleItemXYZ99999", null, null, null, null
        );
        AdminDealQueryResult result = service.queryDeals(criteria);

        assertEquals(AdminDealQueryStatus.NO_MATCHING_DEALS, result.getStatus());
        assertTrue(result.isNoMatchingDeals());
        assertEquals(0, result.getFilteredCount());
        assertTrue(result.getTotalSystemDealsCount() > 0);
    }

    @Test
    @DisplayName("Alternative Flow A4: Invalid date range returns INVALID_DATE_RANGE status")
    void testAlternativeFlowA4InvalidDateRange() {
        LocalDate startDate = LocalDate.of(2026, 11, 10);
        LocalDate endDate = LocalDate.of(2026, 11, 5); // Before start date!

        AdminDealFilterCriteria invalidRange = new AdminDealFilterCriteria(
                null, null, null, startDate, endDate
        );
        AdminDealQueryResult result = service.queryDeals(invalidRange);

        assertEquals(AdminDealQueryStatus.INVALID_DATE_RANGE, result.getStatus());
        assertTrue(result.isInvalidDateRange());
        assertTrue(result.getMessage().contains("before its start"));
    }

    @Test
    @DisplayName("Alternative Flow A5: Stored record preserves deleted users and removed listings")
    void testAlternativeFlowA5RemovedListingAndDeletedUsers() {
        // Soft-delete user "bob" or a sample user with a deal
        User seller = dataService.findUser("charlie");
        assertNotNull(seller);
        seller.setAccountStatus(AccountStatus.DELETED);

        // Remove a listing
        Listing listing = dataService.findListing("L100");
        if (listing != null) {
            listing.setStatus(ListingStatus.REMOVED);
        }

        AdminDealQueryResult result = service.queryDeals(null);
        assertTrue(result.isSuccess());

        boolean foundDeletedSeller = false;
        for (AdminDealRecord r : result.getDeals()) {
            if ("charlie".equalsIgnoreCase(r.getRawSellerUsername())) {
                assertTrue(r.isSellerDeleted());
                assertEquals(AdminDealRecord.DELETED_USER_PLACEHOLDER, r.getSellerUsernameDisplay());
                foundDeletedSeller = true;
            }
        }
        assertTrue(foundDeletedSeller, "Deals with deleted user must display 'Deleted user'");
    }

    @Test
    @DisplayName("Auto-return expiration triggers automatically before querying deals")
    void testAutoReturnBeforeQuery() {
        // Seed an active loan deal that is already expired
        Deal loan = new Deal();
        loan.setId("D_EXPIRED_TEST");
        loan.setListingId("L104");
        loan.setListingTitle("Borrowed Item");
        loan.setBuyerUsername("alice");
        loan.setSellerUsername("diana");
        loan.setType(DealType.LOAN);
        loan.setStatus(DealStatus.ACTIVE);
        loan.setStartDate(LocalDateTime.now().minusDays(5));
        loan.setDueDate(LocalDateTime.now().minusSeconds(10)); // expired!
        dataService.addDeal(loan);

        AdminDealQueryResult result = service.queryDeals(null);
        assertTrue(result.isSuccess());

        Optional<AdminDealRecord> loanRecordOpt = service.getDealDetails("D_EXPIRED_TEST");
        assertTrue(loanRecordOpt.isPresent());
        // Verify auto-return set it to RETURNED
        assertEquals(DealStatus.RETURNED, loanRecordOpt.get().getDealStatus());
    }

    @Test
    @DisplayName("Concurrency: Multi-threaded deal queries maintain thread safety without exception")
    void testConcurrentQueries() throws InterruptedException, ExecutionException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<AdminDealQueryResult>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            tasks.add(() -> {
                AdminDealFilterCriteria criteria = (idx % 2 == 0)
                        ? new AdminDealFilterCriteria(null, DealType.SALE, null, null, null)
                        : AdminDealFilterCriteria.all();
                return service.queryDeals(criteria);
            });
        }

        List<Future<AdminDealQueryResult>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        for (Future<AdminDealQueryResult> f : futures) {
            AdminDealQueryResult res = f.get();
            assertNotNull(res);
            assertTrue(res.isSuccess());
        }
    }

    @Test
    @DisplayName("Default no-arg constructor wires DummyDataService instance")
    void testNoArgConstructor() {
        AdminDealsService defaultService = new AdminDealsService();
        assertNotNull(defaultService);
        assertTrue(defaultService.getTotalDealsCount() > 0);
    }

    @Test
    @DisplayName("Enrich deal falls back to matching accepted offer and review in repository")
    void testEnrichDealFallbackOfferAndReviewResolution() {
        // Create an offer in repository with status ACCEPTED
        Offer accepted = new Offer("O_TEST_FALLBACK", OfferType.PRICE_OFFER, "L_FALLBACK", "testbuyer", "testseller");
        accepted.setStatus(OfferStatus.ACCEPTED);
        accepted.setPrice(99.0);
        dataService.addOffer(accepted);

        // Create a review in repository
        Review review = new Review();
        review.setId("R_TEST_FALLBACK");
        review.setBuyerUsername("testbuyer");
        review.setSellerUsername("testseller");
        review.setListingId("L_FALLBACK");
        review.setRating(4);
        review.setComment("Good fallback test");
        dataService.addReview(review);

        // Deal without explicit offerId
        Deal deal = new Deal();
        deal.setId("D_FALLBACK");
        deal.setListingId("L_FALLBACK");
        deal.setBuyerUsername("testbuyer");
        deal.setSellerUsername("testseller");
        deal.setType(DealType.SALE);
        deal.setStatus(DealStatus.COMPLETED);
        deal.setStartDate(LocalDateTime.now().minusHours(1));
        dataService.addDeal(deal);

        Optional<AdminDealRecord> recordOpt = service.getDealDetails("D_FALLBACK");
        assertTrue(recordOpt.isPresent());
        AdminDealRecord record = recordOpt.get();

        assertNotNull(record.getOffer());
        assertEquals("O_TEST_FALLBACK", record.getOffer().getId());
        assertNotNull(record.getReview());
        assertEquals("R_TEST_FALLBACK", record.getReview().getId());
    }

    @Test
    @DisplayName("F33: Enrich deal matches review by deal ID and avoids cross-deal mismatch on same listing")
    void testEnrichDealMatchesReviewByDealIdNotJustUsersAndListingF33() {
        Deal deal1 = new Deal();
        deal1.setId("D_F33_1");
        deal1.setListingId("L_F33");
        deal1.setBuyerUsername("alice");
        deal1.setSellerUsername("charlie");
        deal1.setType(DealType.LOAN);
        deal1.setStatus(DealStatus.COMPLETED);
        deal1.setStartDate(LocalDateTime.now().minusDays(10));
        dataService.addDeal(deal1);

        Deal deal2 = new Deal();
        deal2.setId("D_F33_2");
        deal2.setListingId("L_F33");
        deal2.setBuyerUsername("alice");
        deal2.setSellerUsername("charlie");
        deal2.setType(DealType.LOAN);
        deal2.setStatus(DealStatus.COMPLETED);
        deal2.setStartDate(LocalDateTime.now().minusDays(2));
        dataService.addDeal(deal2);

        Review review1 = new Review();
        review1.setId("R_F33_1");
        review1.setDealId("D_F33_1");
        review1.setListingId("L_F33");
        review1.setBuyerUsername("alice");
        review1.setSellerUsername("charlie");
        review1.setRating(5);
        review1.setComment("First loan went great!");
        dataService.addReview(review1);

        Review review2 = new Review();
        review2.setId("R_F33_2");
        review2.setDealId("D_F33_2");
        review2.setListingId("L_F33");
        review2.setBuyerUsername("alice");
        review2.setSellerUsername("charlie");
        review2.setRating(1);
        review2.setComment("Second loan had issues.");
        dataService.addReview(review2);

        Optional<AdminDealRecord> rec1Opt = service.getDealDetails("D_F33_1");
        assertTrue(rec1Opt.isPresent());
        assertEquals("R_F33_1", rec1Opt.get().getReview().getId());
        assertEquals(5, rec1Opt.get().getReviewRating());
        assertEquals("First loan went great!", rec1Opt.get().getReviewComment());

        Optional<AdminDealRecord> rec2Opt = service.getDealDetails("D_F33_2");
        assertTrue(rec2Opt.isPresent());
        assertEquals("R_F33_2", rec2Opt.get().getReview().getId());
        assertEquals(1, rec2Opt.get().getReviewRating());
        assertEquals("Second loan had issues.", rec2Opt.get().getReviewComment());
    }

    @Test
    @DisplayName("F33: Enrich deal does not attach another deal's review to an unreviewed deal between same users")
    void testEnrichDealDoesNotCrossContaminateUnreviewedDealF33() {
        Deal deal1 = new Deal();
        deal1.setId("D_F33_RATED");
        deal1.setListingId("L_F33_MULTI");
        deal1.setBuyerUsername("alice");
        deal1.setSellerUsername("diana");
        deal1.setType(DealType.LOAN);
        deal1.setStatus(DealStatus.COMPLETED);
        deal1.setStartDate(LocalDateTime.now().minusDays(5));
        dataService.addDeal(deal1);

        Deal deal2 = new Deal();
        deal2.setId("D_F33_UNRATED");
        deal2.setListingId("L_F33_MULTI");
        deal2.setBuyerUsername("alice");
        deal2.setSellerUsername("diana");
        deal2.setType(DealType.LOAN);
        deal2.setStatus(DealStatus.COMPLETED);
        deal2.setStartDate(LocalDateTime.now().minusDays(1));
        dataService.addDeal(deal2);

        Review review1 = new Review();
        review1.setId("R_F33_RATED");
        review1.setDealId("D_F33_RATED");
        review1.setListingId("L_F33_MULTI");
        review1.setBuyerUsername("alice");
        review1.setSellerUsername("diana");
        review1.setRating(4);
        review1.setComment("Good item");
        dataService.addReview(review1);

        Optional<AdminDealRecord> unratedRec = service.getDealDetails("D_F33_UNRATED");
        assertTrue(unratedRec.isPresent());
        assertNull(unratedRec.get().getReview());
        assertFalse(unratedRec.get().hasReview());
    }

    @Test
    @DisplayName("RBAC: Unauthenticated caller throws SecurityException")
    void testUnauthenticatedAccessThrowsSecurityException() {
        SessionManager.logout();
        assertThrows(SecurityException.class, () -> service.queryDeals(null));
        assertThrows(SecurityException.class, () -> service.getDealDetails("D1"));
        assertThrows(SecurityException.class, () -> service.getTotalDealsCount());
    }

    @Test
    @DisplayName("RBAC: Non-admin caller throws SecurityException")
    void testNonAdminAccessThrowsSecurityException() {
        SessionManager.login(dataService.findUser("alice"));
        assertThrows(SecurityException.class, () -> service.queryDeals(null));
        assertThrows(SecurityException.class, () -> service.getDealDetails("D1"));
        assertThrows(SecurityException.class, () -> service.getTotalDealsCount());
    }
}
