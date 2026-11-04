package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.services.BuyerNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-B09: DummyDataService Notification Integration Tests")
public class DummyDataServiceNotificationTest {

    @Test
    @DisplayName("DummyDataService provides BuyerNotificationService and seeded notifications")
    void testDummyDataServiceIntegration() {
        DummyDataService service = DummyDataService.getInstance();
        BuyerNotificationService notifService = service.getBuyerNotificationService();
        assertNotNull(notifService);

        List<Notification> aliceNotifs = service.getNotificationsFor("alice");
        assertNotNull(aliceNotifs);
        assertFalse(aliceNotifs.isEmpty());

        // Check that seed data includes unread and read notifications
        boolean hasRead = false;
        boolean hasUnread = false;
        for (Notification n : aliceNotifs) {
            if (n.isRead()) hasRead = true;
            if (n.isUnread()) hasUnread = true;
        }
        assertTrue(hasRead);
        assertTrue(hasUnread);

        // Check that all 6 categories are represented for Alice in seed data
        for (NotificationCategory cat : NotificationCategory.values()) {
            boolean found = aliceNotifs.stream().anyMatch(n -> cat.matches(n.getCategory()));
            assertTrue(found, "Category " + cat.getDisplayName() + " should be seeded for Alice");
        }
    }

    @Test
    @DisplayName("addNotification, removeNotification, and removeNotificationsFor operations")
    void testNotificationCrudInDummyDataService() {
        DummyDataService service = DummyDataService.getInstance();

        Notification n = new Notification("temp_user", "Temp Title", "Temp Msg", "Offers");
        service.addNotification(n);
        assertNotNull(n.getId());

        List<Notification> userNotifs = service.getNotificationsFor("temp_user");
        assertEquals(1, userNotifs.size());

        boolean removed = service.removeNotification(n.getId());
        assertTrue(removed);
        assertTrue(service.getNotificationsFor("temp_user").isEmpty());

        // Add 2 notifications and remove all for user
        Notification n1 = new Notification("bulk_user", "T1", "M1", "Chat");
        Notification n2 = new Notification("bulk_user", "T2", "M2", "Offers");
        service.addNotification(n1);
        service.addNotification(n2);

        int count = service.removeNotificationsFor("bulk_user");
        assertEquals(2, count);
        assertTrue(service.getNotificationsFor("bulk_user").isEmpty());

        // Boundary checks
        assertFalse(service.removeNotification(null));
        assertFalse(service.removeNotification("NON_EXISTENT_ID"));
        assertEquals(0, service.removeNotificationsFor(null));
    }
}
