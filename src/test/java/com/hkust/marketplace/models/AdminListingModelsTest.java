package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminListingModelsTest (US-A07 Domain Models)")
class AdminListingModelsTest {

    @Test
    @DisplayName("AdminListingOperationStatus enum coverage")
    void testOperationStatusEnum() {
        for (AdminListingOperationStatus status : AdminListingOperationStatus.values()) {
            assertNotNull(status);
            assertEquals(status, AdminListingOperationStatus.valueOf(status.name()));
        }
    }

    @Test
    @DisplayName("AdminListingOperationResult factory methods and properties")
    void testOperationResultFactories() {
        Listing dummy = new Listing();
        dummy.setId("L100");

        AdminListingOperationResult successResult = AdminListingOperationResult.success(dummy, "Created ok");
        assertTrue(successResult.isSuccess());
        assertEquals(AdminListingOperationStatus.SUCCESS, successResult.getStatus());
        assertEquals("Created ok", successResult.getMessage());
        assertEquals(dummy, successResult.getListing());
        assertNull(successResult.getInvalidFieldName());
        assertEquals(0, successResult.getAffectedOffersCount());
        assertTrue(successResult.toString().contains("L100"));

        AdminListingOperationResult takeDownResult = AdminListingOperationResult.success(dummy, "Removed", 3);
        assertTrue(takeDownResult.isSuccess());
        assertEquals(3, takeDownResult.getAffectedOffersCount());

        AdminListingOperationResult sellerNotActive = AdminListingOperationResult.sellerNotActive("Inactive seller");
        assertFalse(sellerNotActive.isSuccess());
        assertEquals(AdminListingOperationStatus.SELLER_NOT_ACTIVE, sellerNotActive.getStatus());
        assertEquals("Inactive seller", sellerNotActive.getMessage());

        AdminListingOperationResult invalidField = AdminListingOperationResult.invalidField("price", "Price must be > 0");
        assertFalse(invalidField.isSuccess());
        assertEquals(AdminListingOperationStatus.INVALID_FIELD, invalidField.getStatus());
        assertEquals("price", invalidField.getInvalidFieldName());

        AdminListingOperationResult dealInProgress = AdminListingOperationResult.dealInProgressOrCompleted("Deal in progress");
        assertFalse(dealInProgress.isSuccess());
        assertEquals(AdminListingOperationStatus.DEAL_IN_PROGRESS_OR_COMPLETED, dealInProgress.getStatus());

        AdminListingOperationResult listingNotLive = AdminListingOperationResult.listingNotLive("Not live");
        assertFalse(listingNotLive.isSuccess());
        assertEquals(AdminListingOperationStatus.LISTING_NOT_LIVE, listingNotLive.getStatus());

        AdminListingOperationResult emptyReason = AdminListingOperationResult.emptyReason("Reason required");
        assertFalse(emptyReason.isSuccess());
        assertEquals(AdminListingOperationStatus.EMPTY_REASON, emptyReason.getStatus());

        AdminListingOperationResult notFound = AdminListingOperationResult.listingNotFound("Listing not found");
        assertFalse(notFound.isSuccess());
        assertEquals(AdminListingOperationStatus.LISTING_NOT_FOUND, notFound.getStatus());

        AdminListingOperationResult unauthorized = AdminListingOperationResult.unauthorized("Unauthorized admin");
        assertFalse(unauthorized.isSuccess());
        assertEquals(AdminListingOperationStatus.UNAUTHORIZED, unauthorized.getStatus());
    }

    @Test
    @DisplayName("AdminListingFilterCriteria matching logic")
    void testFilterCriteriaMatching() {
        AdminListingFilterCriteria criteria = AdminListingFilterCriteria.all();
        assertNull(criteria.getKeyword());
        assertNull(criteria.getStatus());
        assertNull(criteria.getCategory());
        assertNull(criteria.getType());

        Listing l = new Listing();
        l.setId("L1");
        l.setTitle("Calculus Textbook");
        l.setSellerUsername("charlie");
        l.setSellerFullName("Charlie Lee");
        l.setStatus(ListingStatus.LIVE);
        l.setCategory(Category.BOOKS);
        l.setType(ListingType.SELL);

        assertTrue(criteria.matches(l));
        assertFalse(criteria.matches(null));

        // Keyword matches
        AdminListingFilterCriteria kwCriteria = AdminListingFilterCriteria.byKeyword("calculus");
        assertTrue(kwCriteria.matches(l));
        assertTrue(AdminListingFilterCriteria.byKeyword("char").matches(l));
        assertTrue(AdminListingFilterCriteria.byKeyword("lee").matches(l));
        assertFalse(AdminListingFilterCriteria.byKeyword("physics").matches(l));

        // Status matches
        AdminListingFilterCriteria statusCriteria = AdminListingFilterCriteria.byStatus(ListingStatus.LIVE);
        assertTrue(statusCriteria.matches(l));
        assertFalse(AdminListingFilterCriteria.byStatus(ListingStatus.PENDING_REVIEW).matches(l));

        // Category matches
        AdminListingFilterCriteria catCriteria = AdminListingFilterCriteria.byCategory(Category.BOOKS);
        assertTrue(catCriteria.matches(l));
        assertFalse(AdminListingFilterCriteria.byCategory(Category.ELECTRONICS).matches(l));

        // Type matches
        AdminListingFilterCriteria typeCriteria = AdminListingFilterCriteria.byType(ListingType.SELL);
        assertTrue(typeCriteria.matches(l));
        assertFalse(AdminListingFilterCriteria.byType(ListingType.LEND).matches(l));

        // Combined criteria
        AdminListingFilterCriteria combined = new AdminListingFilterCriteria("Textbook", ListingStatus.LIVE, Category.BOOKS, ListingType.SELL);
        assertTrue(combined.matches(l));
        combined.setType(ListingType.GIVEAWAY);
        assertFalse(combined.matches(l));

        // Equals & HashCode
        AdminListingFilterCriteria c1 = new AdminListingFilterCriteria("abc", ListingStatus.LIVE, Category.BOOKS, ListingType.SELL);
        AdminListingFilterCriteria c2 = new AdminListingFilterCriteria("abc", ListingStatus.LIVE, Category.BOOKS, ListingType.SELL);
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1, null);
        assertNotEquals(c1, "abc");
    }

    @Test
    @DisplayName("AdminListingCreateRequest and AdminListingEditRequest properties")
    void testRequests() {
        Photo p1 = new Photo("img1.png");
        AdminListingCreateRequest createReq = new AdminListingCreateRequest(
                "charlie", "Math Notes", "Handwritten", ListingType.SELL,
                Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                50.0, null, List.of(p1)
        );

        assertEquals("charlie", createReq.getSellerUsername());
        assertEquals("Math Notes", createReq.getTitle());
        assertEquals("Handwritten", createReq.getDescription());
        assertEquals(ListingType.SELL, createReq.getType());
        assertEquals(Category.BOOKS, createReq.getCategory());
        assertEquals(Condition.LIKE_NEW, createReq.getCondition());
        assertEquals(MeetupLocation.LIBRARY, createReq.getMeetupLocation());
        assertEquals(50.0, createReq.getPrice());
        assertNull(createReq.getLendingDays());
        assertEquals(1, createReq.getPhotos().size());

        createReq.addPhoto(new Photo("img2.png"));
        assertEquals(2, createReq.getPhotos().size());

        AdminListingEditRequest editReq = new AdminListingEditRequest(
                "L10", "Math Notes v2", "Updated notes", ListingType.LEND,
                Category.BOOKS, Condition.USED, MeetupLocation.ATRIUM,
                null, 7, Collections.emptyList()
        );

        assertEquals("L10", editReq.getListingId());
        assertEquals("Math Notes v2", editReq.getTitle());
        assertEquals("Updated notes", editReq.getDescription());
        assertEquals(ListingType.LEND, editReq.getType());
        assertEquals(Category.BOOKS, editReq.getCategory());
        assertEquals(Condition.USED, editReq.getCondition());
        assertEquals(MeetupLocation.ATRIUM, editReq.getMeetupLocation());
        assertNull(editReq.getPrice());
        assertEquals(7, editReq.getLendingDays());
        assertTrue(editReq.getPhotos().isEmpty());

        editReq.addPhoto(new Photo("img3.png"));
        assertEquals(1, editReq.getPhotos().size());
        assertEquals(Duration.ofDays(7), editReq.getLendingDuration());
        assertEquals("7", editReq.getLendingAmount());
        assertEquals(LendingDurationUnit.DAYS, editReq.getLendingUnit());
    }

    @Test
    @DisplayName("AdminListingCreateRequest and EditRequest granular lending duration support [F16]")
    void testRequestsLendingDurationGranular() {
        AdminListingCreateRequest req = new AdminListingCreateRequest(
                "diana", "Microscope", "Lab grade", ListingType.LEND,
                Category.ELECTRONICS, Condition.USED, MeetupLocation.ATRIUM,
                null, "36", LendingDurationUnit.HOURS, List.of(new Photo("m.png"))
        );
        assertEquals("36", req.getLendingAmount());
        assertEquals(LendingDurationUnit.HOURS, req.getLendingUnit());
        assertEquals(Duration.ofHours(36), req.getLendingDuration());
        assertEquals(1, req.getLendingDays());

        req.setLendingDuration(Duration.ofSeconds(10));
        assertEquals("10", req.getLendingAmount());
        assertEquals(LendingDurationUnit.SECONDS, req.getLendingUnit());
        assertEquals(Duration.ofSeconds(10), req.getLendingDuration());
        assertEquals(0, req.getLendingDays());

        req.setLendingDuration(Duration.ofDays(14));
        assertEquals("2", req.getLendingAmount());
        assertEquals(LendingDurationUnit.WEEKS, req.getLendingUnit());
        assertEquals(14, req.getLendingDays());

        AdminListingCreateRequest factoryReq = AdminListingCreateRequest.ofDuration(
                "diana", "Microscope", "Lab grade", ListingType.LEND,
                Category.ELECTRONICS, Condition.USED, MeetupLocation.ATRIUM,
                null, Duration.ofHours(36), List.of(new Photo("m.png"))
        );
        assertEquals(Duration.ofHours(36), factoryReq.getLendingDuration());
        assertEquals("36", factoryReq.getLendingAmount());
        assertEquals(LendingDurationUnit.HOURS, factoryReq.getLendingUnit());

        AdminListingEditRequest editReq = new AdminListingEditRequest(
                "L20", "Microscope v2", "Updated", ListingType.LEND,
                Category.ELECTRONICS, Condition.LIKE_NEW, MeetupLocation.ATRIUM,
                null, "10", LendingDurationUnit.SECONDS, List.of(new Photo("m.png"))
        );
        assertEquals("10", editReq.getLendingAmount());
        assertEquals(LendingDurationUnit.SECONDS, editReq.getLendingUnit());
        assertEquals(Duration.ofSeconds(10), editReq.getLendingDuration());
        assertEquals(0, editReq.getLendingDays());

        AdminListingEditRequest editFactory = AdminListingEditRequest.ofDuration(
                "L20", "Microscope v2", "Updated", ListingType.LEND,
                Category.ELECTRONICS, Condition.LIKE_NEW, MeetupLocation.ATRIUM,
                null, Duration.ofHours(36), List.of(new Photo("m.png"))
        );
        assertEquals(Duration.ofHours(36), editFactory.getLendingDuration());
        assertEquals("36", editFactory.getLendingAmount());
        assertEquals(LendingDurationUnit.HOURS, editFactory.getLendingUnit());

        editFactory.setLendingAmount("48");
        editFactory.setLendingUnit(LendingDurationUnit.HOURS);
        assertEquals(Duration.ofHours(48), editFactory.getLendingDuration());

        editFactory.setLendingDays(null);
        assertNull(editFactory.getLendingDuration());
        assertNull(editFactory.getLendingAmount());
        assertNull(editFactory.getLendingUnit());
    }
}
