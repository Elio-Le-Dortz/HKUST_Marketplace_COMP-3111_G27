package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.DealStatus;
import com.hkust.marketplace.models.DealType;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoReturnScheduler Background Timer Tests [F07]")
class AutoReturnSchedulerTest {

    private DummyDataService dataService;
    private AutoReturnScheduler scheduler;

    @BeforeEach
    void setUp() {
        dataService = DummyDataService.getInstance();
        dataService.resetData();
        scheduler = new AutoReturnScheduler(dataService);
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.stop();
        }
        AutoReturnScheduler.getInstance().stop();
    }

    @Test
    @DisplayName("Scheduler periodically detects and processes expired loans automatically")
    void testSchedulerBackgroundProcessing() throws InterruptedException {
        Listing lend = new Listing();
        lend.setTitle("Projector for loan");
        lend.setType(ListingType.LEND);
        lend.setSellerUsername("charlie");
        lend.setStatus(ListingStatus.LENT);
        dataService.addListing(lend);

        Deal loan = new Deal();
        loan.setListingId(lend.getId());
        loan.setListingTitle(lend.getTitle());
        loan.setBuyerUsername("alice");
        loan.setSellerUsername("charlie");
        loan.setType(DealType.LOAN);
        loan.setStatus(DealStatus.ACTIVE);
        loan.setDueDate(LocalDateTime.now().minusSeconds(1));
        dataService.addDeal(loan);

        assertFalse(scheduler.isRunning());
        scheduler.start(50, TimeUnit.MILLISECONDS);
        assertTrue(scheduler.isRunning());

        // Calling start again while running is a safe no-op
        scheduler.start(50, TimeUnit.MILLISECONDS);
        assertTrue(scheduler.isRunning());

        // Wait up to 2 seconds for the background thread to process the return
        long deadline = System.currentTimeMillis() + 2000;
        while ((loan.getStatus() != DealStatus.RETURNED || lend.getStatus() != ListingStatus.LIVE)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertEquals(DealStatus.RETURNED, loan.getStatus(), "Background scheduler should mark deal RETURNED");
        assertEquals(ListingStatus.LIVE, lend.getStatus(), "Background scheduler should reset listing to LIVE");

        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }

    @Test
    @DisplayName("Singleton instance start and stop lifecycle")
    void testSingletonLifecycle() {
        AutoReturnScheduler instance = AutoReturnScheduler.getInstance();
        assertNotNull(instance);
        instance.stop();
        assertFalse(instance.isRunning());

        instance.start(100, TimeUnit.MILLISECONDS);
        assertTrue(instance.isRunning());

        instance.stop();
        assertFalse(instance.isRunning());
    }
}
