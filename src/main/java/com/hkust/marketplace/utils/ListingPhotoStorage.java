package com.hkust.marketplace.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Keeps a copy of every listing photo in the application's own folder, so a listing keeps its
 * photos when the Seller later moves, renames or deletes the files they chose (review of the
 * seller part, finding 3). The listing stores the path of the copy, never the path of the
 * Seller's own file.
 *
 * <p><b>Names.</b> A copy is named after the beginning of the SHA-256 hash of its content and
 * the name of the original file, for example {@code 3fa9c1e0b2d4a6f8-lamp-front.png}. Storing
 * the same file again therefore gives the same copy, without a duplicate (for example when a
 * Seller saves an edit again after the pending-offers warning of use case 2.4), while two
 * different photos never share a copy. A photo already in the folder is kept as it is.</p>
 *
 * <p><b>Folder.</b> By default the copies go in {@value #DEFAULT_FOLDER} under the working
 * directory, next to the SQLite database {@code data/marketplace.db}; the whole {@code data/}
 * folder is ignored by Git. The folder is created on the first copy. Tests give their own
 * temporary folder.</p>
 *
 * <p>A copy is first written to a temporary file in the folder and then renamed, so a failed copy
 * never leaves a half-written photo. The photos are expected to have been checked already (use
 * case 2.3, alternative flow A3, see {@code CreateListingService.validate}).</p>
 */
public final class ListingPhotoStorage {

    /** Folder of the copies, relative to the working directory, when none is given. */
    public static final String DEFAULT_FOLDER = "data/uploads/listings";

    /** Number of hexadecimal digits of the content hash kept in the name of a copy. */
    static final int HASH_DIGITS = 16;

    /** Longest part of a copy's name taken from the original file name, in characters. */
    static final int MAX_ORIGINAL_NAME_LENGTH = 80;

    /** The folder holding the copies, absolute and normalised. */
    private final Path root;

    /**
     * Creates the storage in {@value #DEFAULT_FOLDER} under the working directory.
     */
    public ListingPhotoStorage() {
        this(Path.of(DEFAULT_FOLDER));
    }

    /**
     * Creates the storage in the given folder (used by the tests). The folder is created on the
     * first copy.
     *
     * @param root the folder of the copies
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public ListingPhotoStorage(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /**
     * Returns the folder of the copies.
     *
     * @return the absolute, normalised folder
     */
    public Path getRoot() {
        return root;
    }

    /**
     * Tells whether a photo is already one of the copies kept by this storage.
     *
     * @param photo the photo file
     * @return {@code true} if the file is in the storage folder
     * @throws NullPointerException if {@code photo} is {@code null}
     */
    public boolean isStored(Path photo) {
        return Objects.requireNonNull(photo, "photo").toAbsolutePath().normalize().startsWith(root);
    }

    /**
     * Copies photos into the storage folder, in order.
     *
     * @param photos the photo files chosen by the Seller
     * @return the paths of the copies, in the same order; a photo already in the folder is
     *         returned as it is
     * @throws IOException          if a photo cannot be read or copied; the copies already made
     *                              are kept
     * @throws NullPointerException if {@code photos} or one of its elements is {@code null}
     */
    public List<Path> store(List<Path> photos) throws IOException {
        Objects.requireNonNull(photos, "photos");
        List<Path> stored = new ArrayList<>(photos.size());
        for (Path photo : photos) {
            stored.add(store(photo));
        }
        return stored;
    }

    /**
     * Copies one photo into the storage folder, unless it is already there.
     *
     * @param photo the photo file chosen by the Seller
     * @return the path of the copy, or the photo itself if it is already in the folder
     * @throws IOException          if the photo cannot be read or copied
     * @throws NullPointerException if {@code photo} is {@code null}
     */
    public synchronized Path store(Path photo) throws IOException {
        Path source = Objects.requireNonNull(photo, "photo").toAbsolutePath().normalize();
        if (source.startsWith(root)) {
            return source;
        }
        Files.createDirectories(root);
        Path temp = Files.createTempFile(root, ".upload-", ".tmp");
        try {
            String hash = copyAndHash(source, temp);
            Path target = root.resolve(hash.substring(0, HASH_DIGITS) + "-" + safeName(source));
            if (!Files.isRegularFile(target)) {
                moveIntoPlace(temp, target);
            }
            return target;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Turns the name of an original file into a name safe on every operating system: letters,
     * digits, dots, dashes and underscores are kept, any other character becomes an underscore,
     * and a long name keeps its end, where the extension is.
     *
     * @param source the original file
     * @return the safe name, never empty
     */
    static String safeName(Path source) {
        Path fileName = source.getFileName();
        String name = fileName == null ? "" : fileName.toString().replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
            name = "photo";
        }
        return name.length() > MAX_ORIGINAL_NAME_LENGTH
                ? name.substring(name.length() - MAX_ORIGINAL_NAME_LENGTH) : name;
    }

    /**
     * Copies a file while computing the SHA-256 hash of what was copied, so the file is read once.
     *
     * @param source the file to copy
     * @param target the file to write, replaced
     * @return the hash, in lower-case hexadecimal
     * @throws IOException if the file cannot be read or written
     */
    private static String copyAndHash(Path source, Path target) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(Files.newInputStream(source), digest);
             OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Renames the temporary copy to its final name, atomically when the file system allows it.
     *
     * @param temp   the temporary copy
     * @param target the final name
     * @throws IOException if the file cannot be renamed
     */
    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (Files.isRegularFile(target)) {
                return;
            }
            throw e;
        }
    }

    /**
     * Returns a SHA-256 digest.
     *
     * @return a new digest
     * @throws IllegalStateException never in practice: every Java platform provides SHA-256
     */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
