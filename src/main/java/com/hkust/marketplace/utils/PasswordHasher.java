package com.hkust.marketplace.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Hashes and verifies user passwords so that credentials are never stored in
 * plaintext (non-functional requirement "data is securely stored").
 *
 * <p>Passwords are derived with PBKDF2-HMAC-SHA256 using a random 16-byte salt per
 * password and {@value #ITERATIONS} iterations (the OWASP recommendation for this
 * algorithm). Only the JDK is used, so no extra dependency is needed.</p>
 *
 * <p>A stored hash has the form
 * {@code pbkdf2_sha256$<iterations>$<base64 salt>$<base64 hash>}. Because the
 * iteration count and salt are stored with the hash, the iteration count can be
 * raised later without invalidating existing hashes.</p>
 */
public final class PasswordHasher {

    /** Number of PBKDF2 iterations used for new hashes. */
    public static final int ITERATIONS = 600_000;

    /** JCA name of the key derivation algorithm. */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    /** First part of every stored hash, identifying the algorithm. */
    private static final String PREFIX = "pbkdf2_sha256";
    /** Separator between the parts of a stored hash. */
    private static final String SEPARATOR = "$";
    /** Length of the random salt, in bytes. */
    private static final int SALT_BYTES = 16;
    /** Length of the derived key, in bits. */
    private static final int KEY_BITS = 256;

    /** Source of the random salts. */
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Encodes the salt and key into the stored string. */
    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    /** Decodes the salt and key from the stored string. */
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    /**
     * Not used: this class only has static methods.
     */
    private PasswordHasher() {
        // Utility class: not meant to be instantiated.
    }

    /**
     * Hashes a plaintext password with a fresh random salt. Hashing the same
     * password twice gives two different strings, both accepted by
     * {@link #verify(String, String)}.
     *
     * @param rawPassword the plaintext password to hash
     * @return the encoded hash, safe to store in {@link com.hkust.marketplace.models.User}
     * @throws NullPointerException if {@code rawPassword} is {@code null}
     */
    public static String hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(rawPassword, salt, ITERATIONS, KEY_BITS);
        return PREFIX + SEPARATOR + ITERATIONS + SEPARATOR + ENCODER.encodeToString(salt)
                + SEPARATOR + ENCODER.encodeToString(key);
    }

    /**
     * Checks a plaintext password against a hash produced by {@link #hash(String)}.
     * The comparison takes the same time wherever the first difference is, so the
     * result cannot be guessed byte by byte from response times.
     *
     * @param rawPassword the plaintext password entered by the user; may be {@code null}
     * @param storedHash  the stored hash; may be {@code null}
     * @return {@code true} if the password matches the hash; {@code false} if it does
     *         not, if either argument is {@code null}, or if {@code storedHash} is not
     *         a hash in the format produced by this class (for example a plaintext
     *         password)
     */
    public static boolean verify(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        String[] parts = storedHash.split("\\" + SEPARATOR, -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = DECODER.decode(parts[2]);
            expected = DECODER.decode(parts[3]);
        } catch (IllegalArgumentException e) {
            // Also covers NumberFormatException: the stored value is not a valid hash.
            return false;
        }
        if (iterations <= 0 || salt.length == 0 || expected.length == 0) {
            return false;
        }
        byte[] actual = derive(rawPassword, salt, iterations, expected.length * Byte.SIZE);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * Runs PBKDF2 on a password.
     *
     * @param rawPassword the plaintext password
     * @param salt        the salt
     * @param iterations  the number of iterations
     * @param keyBits     the length of the derived key, in bits
     * @return the derived key bytes
     * @throws IllegalStateException if the JVM does not provide PBKDF2-HMAC-SHA256
     */
    private static byte[] derive(String rawPassword, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is not available on this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
