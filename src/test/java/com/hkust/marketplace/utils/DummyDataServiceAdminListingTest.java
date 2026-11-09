package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService Admin Listing Integration Tests (US-A07)")
class DummyDataServiceAdminListingTest {

    @TempDir
    Path tempDir;

    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private Path samplePhoto;
    private DummyDataService dataService;

    @BeforeEach
    void setUp() throws IOException {
        samplePhoto = tempDir.resolve("b.png");
        Files.write(samplePhoto, PNG_HEADER);
        dataService = DummyDataService.getInstance();
        dataService.resetData();
    }

    @Test
    @DisplayName("AdminListingService is properly wired in DummyDataService")
    void testServiceWiring() {
        assertNotNull(dataService.getAdminListingService());
    }

    @Test
    @DisplayName("Seeded REMOVED listing L999 exists and is consistent with notifications")
    void testSeededRemovedListing() {
        Listing l999 = dataService.findListing("L999");
        assertNotNull(l999, "L999 should be seeded in the marketplace listings map");
        assertEquals("Vintage Mechanical Keyboard", l999.getTitle());
        assertEquals(ListingStatus.REMOVED, l999.getStatus());
        assertEquals("Reported for inappropriate accessories", l999.getAdminReason());
    }

    @Test
    @DisplayName("DummyDataService delegates add, edit, and takedown to AdminListingService")
    void testDelegates() {
        AdminListingCreateRequest createReq = new AdminListingCreateRequest(
                "charlie", "Dummy Data Service Book", "Desc", ListingType.SELL,
                Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                120.0, null, List.of(new Photo(samplePhoto.toString()))
        );

        AdminListingOperationResult createRes = dataService.createListingOnBehalfOfSeller(createReq, "admin");
        assertTrue(createRes.isSuccess());
        Listing created = createRes.getListing();
        assertNotNull(created);
        assertEquals(ListingStatus.LIVE, created.getStatus());

        AdminListingEditRequest editReq = new AdminListingEditRequest(
                created.getId(), "Updated Dummy Book", "New Desc", ListingType.SELL,
                Category.BOOKS, Condition.LIKE_NEW, MeetupLocation.LIBRARY,
                140.0, null, List.of(new Photo(samplePhoto.toString()))
        );
        AdminListingOperationResult editRes = dataService.editListingByAdmin(editReq, "admin");
        assertTrue(editRes.isSuccess());
        assertEquals("Updated Dummy Book", dataService.findListing(created.getId()).getTitle());

        AdminListingOperationResult takeDownRes = dataService.takeDownListing(created.getId(), "Duplicate listing", "admin");
        assertTrue(takeDownRes.isSuccess());
        assertEquals(ListingStatus.REMOVED, dataService.findListing(created.getId()).getStatus());
    }
}
