package com.hkust.marketplace.utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordHasher: salted PBKDF2 hashing")
class PasswordHasherTest {

    private static final String PASSWORD = "StrongPass123";
    private static String storedHash;

    @BeforeAll
    static void hashOnce() {
        // Hashing is deliberately slow, so share one hash between the tests that only read it.
        storedHash = PasswordHasher.hash(PASSWORD);
    }

    @Test
    @DisplayName("Hash never contains the plaintext password")
    void hashIsNotPlaintext() {
        assertNotEquals(PASSWORD, storedHash);
        assertFalse(storedHash.contains(PASSWORD));
    }

    @Test
    @DisplayName("Hash is stored as pbkdf2_sha256$iterations$salt$hash")
    void hashFormat() {
        String[] parts = storedHash.split("\\$");
        assertEquals(4, parts.length);
        assertEquals("pbkdf2_sha256", parts[0]);
        assertEquals(String.valueOf(PasswordHasher.ITERATIONS), parts[1]);
    }

    @Test
    @DisplayName("Correct password is verified")
    void verifyCorrectPassword() {
        assertTrue(PasswordHasher.verify(PASSWORD, storedHash));
    }

    @Test
    @DisplayName("Wrong password, including a different case, is rejected")
    void verifyWrongPassword() {
        assertFalse(PasswordHasher.verify("StrongPass124", storedHash));
        assertFalse(PasswordHasher.verify("strongpass123", storedHash));
        assertFalse(PasswordHasher.verify("", storedHash));
    }

    @Test
    @DisplayName("Same password hashed twice gives different hashes (random salt), both verifiable")
    void saltMakesHashesUnique() {
        String second = PasswordHasher.hash(PASSWORD);
        assertNotEquals(storedHash, second);
        assertTrue(PasswordHasher.verify(PASSWORD, second));
    }

    @Test
    @DisplayName("Empty password can be hashed and verified")
    void emptyPassword() {
        String hash = PasswordHasher.hash("");
        assertTrue(PasswordHasher.verify("", hash));
        assertFalse(PasswordHasher.verify(" ", hash));
    }

    @Test
    @DisplayName("Hashing null throws NullPointerException")
    void hashNullThrows() {
        assertThrows(NullPointerException.class, () -> PasswordHasher.hash(null));
    }

    @Test
    @DisplayName("Null password or null hash is rejected without throwing")
    void verifyNulls() {
        assertFalse(PasswordHasher.verify(null, storedHash));
        assertFalse(PasswordHasher.verify(PASSWORD, null));
    }

    @Test
    @DisplayName("Plaintext stored password is not accepted as a hash")
    void plaintextIsNotAHash() {
        assertFalse(PasswordHasher.verify(PASSWORD, PASSWORD));
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @ValueSource(strings = {
            "",
            "pbkdf2_sha256",
            "pbkdf2_sha256$600000$c2FsdA",
            "pbkdf2_sha256$600000$c2FsdA$aGFzaA$extra",
            "bcrypt$600000$c2FsdA$aGFzaA",
            "pbkdf2_sha256$abc$c2FsdA$aGFzaA",
            "pbkdf2_sha256$0$c2FsdA$aGFzaA",
            "pbkdf2_sha256$-5$c2FsdA$aGFzaA",
            "pbkdf2_sha256$600000$not*base64$aGFzaA",
            "pbkdf2_sha256$600000$$aGFzaA",
            "pbkdf2_sha256$600000$c2FsdA$"
    })
    @DisplayName("Malformed stored hashes are rejected without throwing")
    void malformedHashes(String malformed) {
        assertFalse(PasswordHasher.verify(PASSWORD, malformed));
    }

    @Test
    @DisplayName("Tampered hash bytes are rejected")
    void tamperedHash() {
        String[] parts = storedHash.split("\\$");
        char first = parts[3].charAt(0);
        parts[3] = (first == 'A' ? 'B' : 'A') + parts[3].substring(1);
        assertFalse(PasswordHasher.verify(PASSWORD, String.join("$", parts)));
    }

    @Test
    @DisplayName("Iteration count is read from the stored hash, not assumed")
    void storedIterationCountIsUsed() {
        String[] parts = storedHash.split("\\$");
        parts[1] = "1000";
        // Same salt and key but a different stored iteration count: the derived key no longer matches.
        assertFalse(PasswordHasher.verify(PASSWORD, String.join("$", parts)));
    }
}
