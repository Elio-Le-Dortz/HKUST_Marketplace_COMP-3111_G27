package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.SellerLoginService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DummyDataService & Concurrency Tests")
class DummyDataServiceTest {

    @Test
    @DisplayName("Buyer registration password strength validation")
    void testPasswordStrength() {
        DummyDataService service = DummyDataService.getInstance();
        BuyerRegistrationResult result = service.registerBuyer("newuser", "Short1!", "Short1!", "New User", "Hall 1");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("at least 8 characters"));
    }

    @Test
    @DisplayName("Atomic giveaway claim: first buyer succeeds, subsequent claims fail")
    void testAtomicGiveawayClaim() {
        DummyDataService service = DummyDataService.getInstance();

        // Create a giveaway listing
        Listing giveaway = new Listing();
        giveaway.setTitle("Free Notebook");
        giveaway.setType(ListingType.GIVEAWAY);
        giveaway.setStatus(ListingStatus.LIVE);
        giveaway.setSellerUsername("diana");
        service.addListing(giveaway);

        // First buyer claims
        SessionManager.login(service.findUser("alice"));
        ActOnListingResult firstClaim = service.submitClaim(giveaway.getId(), "alice");
        assertTrue(firstClaim.isSuccess(), "First buyer should successfully claim the giveaway");

        // Second buyer tries to claim same item
        SessionManager.login(service.findUser("bob"));
        ActOnListingResult secondClaim = service.submitClaim(giveaway.getId(), "bob");
        assertFalse(secondClaim.isSuccess(), "Second buyer cannot claim an already claimed item");
        SessionManager.logout();
    }

    @Test
    @DisplayName("User lookup and role filtering")
    void testUserLookup() {
        DummyDataService service = DummyDataService.getInstance();
        assertTrue(service.usernameExists("admin"));
        assertTrue(service.usernameExists("alice"));
        assertFalse(service.usernameExists("non_existent_user"));
        assertFalse(service.usernameExists(null));

        User admin = service.findUser("admin");
        assertNotNull(admin);
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertNull(service.findUser(null));

        assertFalse(service.getAllUsers().isEmpty());
        assertEquals(2, service.getUsersByRole(UserRole.BUYER).size());

        assertThrows(IllegalArgumentException.class, () -> service.registerUser(null));
        Buyer invalidBuyer = new Buyer();
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(invalidBuyer));
    }

    @Test
    @DisplayName("Reset data restores default state")
    void testResetData() {
        DummyDataService service = DummyDataService.getInstance();
        service.registerUser(new Buyer("tempuser", "Temp", "Pass1234!"));
        assertTrue(service.usernameExists("tempuser"));

        service.resetData();
        assertFalse(service.usernameExists("tempuser"));
        assertTrue(service.usernameExists("admin"));
    }

    @Test
    @DisplayName("registerUserIfAbsent stores a new user once and rejects the same username in any case")
    void testRegisterUserIfAbsent() {
        DummyDataService service = DummyDataService.getInstance();
        Seller first = new Seller("unique_seller_s01", "First Seller", "hash");
        assertTrue(service.registerUserIfAbsent(first));
        assertSame(first, service.findUser("unique_seller_s01"));

        Seller sameNameOtherCase = new Seller("UNIQUE_Seller_S01", "Second Seller", "hash");
        assertFalse(service.registerUserIfAbsent(sameNameOtherCase));
        assertSame(first, service.findUser("unique_seller_s01"), "Existing account must not be overwritten");
    }

    @Test
    @DisplayName("Seeded sellers have hashed passwords that still accept the demo password")
    void testSeededSellerPasswordsAreHashed() {
        DummyDataService service = DummyDataService.getInstance();
        for (String seller : new String[]{"charlie", "diana"}) {
            String stored = service.findUser(seller).getPasswordHash();
            assertNotEquals("password1", stored);
            assertTrue(PasswordHasher.verify("password1", stored));
        }
    }

    @Test
    @DisplayName("registerUserIfAbsent rejects usernames held by other roles")
    void testRegisterUserIfAbsentAcrossRoles() {
        DummyDataService service = DummyDataService.getInstance();
        User existingBuyer = service.findUser("alice");
        assertFalse(service.registerUserIfAbsent(new Seller("alice", "Impostor", "hash")));
        assertSame(existingBuyer, service.findUser("alice"));
        assertFalse(service.registerUserIfAbsent(new Seller("admin", "Impostor", "hash")));
    }

    @Test
    @DisplayName("Seeded buyers and sellers have valid password hashes upon initialization")
    void testSeededAccountsHaveValidPasswords() {
        DummyDataService service = DummyDataService.getInstance();
        for (String username : new String[]{"alice", "bob", "charlie", "diana"}) {
            User user = service.findUser(username);
            assertNotNull(user, "User " + username + " must exist");
            String stored = user.getPasswordHash();
            assertNotNull(stored, "User " + username + " password hash must not be null");
            assertNotEquals("password1", stored);
            assertTrue(PasswordHasher.verify("password1", stored), "User " + username + " password must verify");
            assertTrue(PasswordUtil.verifyPassword("password1", stored), "PasswordUtil must verify " + username);
        }
        // Verify buyer login succeeds
        LoginResult buyerLogin = service.loginBuyer("alice", "password1");
        assertTrue(buyerLogin.isSuccess(), "Seeded buyer alice must be able to log in");
        assertEquals("alice", buyerLogin.getUser().getUsername());

        // Verify seller authentication succeeds
        SellerLoginService sellerLoginService = new SellerLoginService(service);
        LoginResult sellerLogin = sellerLoginService.authenticate("charlie", "password1");
        assertTrue(sellerLogin.isSuccess(), "Seeded seller charlie must be able to log in");
        assertEquals("charlie", sellerLogin.getUser().getUsername());

        // F01 regression: Seeded admin account has PBKDF2 hashed password, not plaintext
        User admin = service.findUser("admin");
        assertNotNull(admin, "Admin user must exist");
        String adminStored = admin.getPasswordHash();
        assertNotNull(adminStored);
        assertNotEquals("admin123", adminStored, "Admin password must not be stored in plaintext");
        assertTrue(adminStored.startsWith("pbkdf2_sha256$"), "Admin password hash must be PBKDF2");
        assertTrue(PasswordHasher.verify("admin123", adminStored));
        assertTrue(PasswordUtil.verifyPassword("admin123", adminStored));
    }

    @Test
    @DisplayName("Regression test for Issue #21: DummyDataService in fresh ClassLoader initializes seeded passwords")
    void testFreshClassLoaderInitialization() throws Exception {
        URL codeSourceUrl = DummyDataService.class.getProtectionDomain().getCodeSource().getLocation();
        ClassLoader parent = DummyDataService.class.getClassLoader().getParent();

        try (URLClassLoader isolatedLoader = new URLClassLoader(new URL[]{codeSourceUrl}, parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.hkust.marketplace.")) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        try {
                            c = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                        }
                    }
                    if (c != null) {
                        if (resolve) resolveClass(c);
                        return c;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> freshClass = isolatedLoader.loadClass("com.hkust.marketplace.utils.DummyDataService");
            Object freshInstance = freshClass.getMethod("getInstance").invoke(null);
            Object alice = freshClass.getMethod("findUser", String.class).invoke(freshInstance, "alice");
            assertNotNull(alice);
            Method getPasswordHash = alice.getClass().getMethod("getPasswordHash");
            String hash = (String) getPasswordHash.invoke(alice);
            assertNotNull(hash, "Password hash must not be null upon initial class load");
            assertTrue(hash.startsWith("pbkdf2_sha256$"), "Seeded user password hash must be a valid PBKDF2 hash");

            Object charlie = freshClass.getMethod("findUser", String.class).invoke(freshInstance, "charlie");
            assertNotNull(charlie);
            String charlieHash = (String) getPasswordHash.invoke(charlie);
            assertNotNull(charlieHash, "Charlie password hash must not be null upon initial class load");
            assertTrue(charlieHash.startsWith("pbkdf2_sha256$"));

            Object admin = freshClass.getMethod("findUser", String.class).invoke(freshInstance, "admin");
            assertNotNull(admin);
            String adminHash = (String) getPasswordHash.invoke(admin);
            assertNotNull(adminHash, "Admin password hash must not be null upon initial class load");
            assertNotEquals("admin123", adminHash, "Admin password must not be plaintext");
            assertTrue(adminHash.startsWith("pbkdf2_sha256$"));
        }
    }
}
