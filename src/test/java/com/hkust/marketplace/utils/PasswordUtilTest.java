package com.hkust.marketplace.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordUtil Cryptographic Hashing & Verification Tests")
class PasswordUtilTest {

    @Test
    @DisplayName("Successfully hash password and verify with correct password")
    void testHashAndVerifySuccess() {
        String rawPassword = "ValidPassword123";
        String hashed = PasswordUtil.hashPassword(rawPassword);

        assertNotNull(hashed);
        assertTrue(hashed.contains(":"), "Hash should contain salt separator ':'");
        assertNotEquals(rawPassword, hashed, "Password must not be stored in plaintext");

        assertTrue(PasswordUtil.verifyPassword(rawPassword, hashed), "Candidate password must match stored hash");
    }

    @Test
    @DisplayName("Fail verification with incorrect password")
    void testVerifyIncorrectPassword() {
        String rawPassword = "ValidPassword123";
        String hashed = PasswordUtil.hashPassword(rawPassword);

        assertFalse(PasswordUtil.verifyPassword("WrongPassword123", hashed));
        assertFalse(PasswordUtil.verifyPassword("validpassword123", hashed), "Password verification must be case-sensitive");
    }

    @Test
    @DisplayName("Salts ensure distinct hashes for identical passwords")
    void testSaltRandomness() {
        String rawPassword = "SamePassword123";
        String hash1 = PasswordUtil.hashPassword(rawPassword);
        String hash2 = PasswordUtil.hashPassword(rawPassword);

        assertNotEquals(hash1, hash2, "Two hashes of the same password must differ due to unique salts");
        assertTrue(PasswordUtil.verifyPassword(rawPassword, hash1));
        assertTrue(PasswordUtil.verifyPassword(rawPassword, hash2));
    }

    @Test
    @DisplayName("Reject null and empty inputs when hashing")
    void testHashNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(""));
    }

    @Test
    @DisplayName("Handle null and empty inputs gracefully during verification")
    void testVerifyNullOrEmpty() {
        String hashed = PasswordUtil.hashPassword("ValidPass123");

        assertFalse(PasswordUtil.verifyPassword(null, hashed));
        assertFalse(PasswordUtil.verifyPassword("", hashed));
        assertFalse(PasswordUtil.verifyPassword("ValidPass123", null));
        assertFalse(PasswordUtil.verifyPassword("ValidPass123", ""));
    }

    @Test
    @DisplayName("Handle malformed hash strings during verification")
    void testVerifyMalformedHash() {
        assertFalse(PasswordUtil.verifyPassword("pass", ":"));
        assertFalse(PasswordUtil.verifyPassword("pass", "saltonly:"));
        assertFalse(PasswordUtil.verifyPassword("pass", ":hashonly"));
        assertFalse(PasswordUtil.verifyPassword("pass", "oddlengthhex:123"));
        assertFalse(PasswordUtil.verifyPassword("pass", "nothexcharacters!@:456"));
    }

    @Test
    @DisplayName("F01: Plaintext stored passwords and unsalted hashes are strictly rejected")
    void testPlaintextAndUnsaltedRejected() throws Exception {
        // Plaintext match must be rejected
        assertFalse(PasswordUtil.verifyPassword("secret", "secret"));
        assertFalse(PasswordUtil.verifyPassword("legacyPassword1", "legacyPassword1"));
        assertFalse(PasswordUtil.verifyPassword("legacyPassword1", "wrongLegacyPassword"));

        // Legacy unsalted SHA-256 hash must be rejected
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest("legacyPlain123".getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format(Locale.ENGLISH, "%02x", b));
        }
        String unsaltedHash = sb.toString();

        assertFalse(PasswordUtil.verifyPassword("legacyPlain123", unsaltedHash));
        assertFalse(PasswordUtil.verifyPassword("wrongPassword", unsaltedHash));
        assertFalse(PasswordUtil.verifyPassword("legacyPlain123", "invalidHexDigestZ9"));
    }

    @Test
    @DisplayName("F01: Verify PBKDF2 formatted hash")
    void testVerifyPbkdf2Format() {
        String rawPassword = "AdminSecurePass123";
        String pbkdf2Hash = PasswordHasher.hash(rawPassword);

        assertTrue(pbkdf2Hash.startsWith("pbkdf2_sha256$"));
        assertTrue(PasswordUtil.verifyPassword(rawPassword, pbkdf2Hash));
        assertFalse(PasswordUtil.verifyPassword("WrongPass", pbkdf2Hash));
    }
}
