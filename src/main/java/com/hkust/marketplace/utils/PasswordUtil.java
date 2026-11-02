package com.hkust.marketplace.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utility for cryptographic password hashing and verification using salted SHA-256.
 * Adheres to the non-functional requirement of securely storing user credentials.
 */
public final class PasswordUtil {

    private static final int SALT_LENGTH_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtil() {
        // Utility class
    }

    /**
     * Hashes a plaintext password using a cryptographically random salt and SHA-256.
     *
     * @param password Plaintext password to hash
     * @return Formatted string containing "saltHex:hashHex"
     * @throws IllegalArgumentException if password is null or empty
     */
    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        byte[] hash = computeHash(password, salt);
        return bytesToHex(salt) + ":" + bytesToHex(hash);
    }

    /**
     * Verifies a plaintext password against a stored hash string.
     * Supports salted format ("saltHex:hashHex") and provides fallback verification
     * for legacy plaintext or unsalted hashes.
     *
     * @param password   Plaintext candidate password
     * @param storedHash Stored password representation (salt:hash or fallback)
     * @return true if candidate password matches stored hash, false otherwise
     */
    public static boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        // PBKDF2 hash format (used by PasswordHasher)
        if (storedHash.startsWith("pbkdf2_sha256")) {
            return PasswordHasher.verify(password, storedHash);
        }

        // Salted hash format
        if (storedHash.contains(":")) {
            String[] parts = storedHash.split(":", 2);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return false;
            }
            try {
                byte[] salt = hexToBytes(parts[0]);
                byte[] expectedHash = hexToBytes(parts[1]);
                byte[] actualHash = computeHash(password, salt);
                return MessageDigest.isEqual(expectedHash, actualHash);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        // Fallback for legacy plaintext or legacy unsalted hash
        if (storedHash.equals(password)) {
            return true;
        }

        byte[] unsaltedHash = computeHash(password, new byte[0]);
        try {
            return MessageDigest.isEqual(hexToBytes(storedHash), unsaltedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] computeHash(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (salt.length > 0) {
                digest.update(salt);
            }
            return digest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in runtime environment", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("Invalid hex character in string: " + hex);
            }
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }
}
