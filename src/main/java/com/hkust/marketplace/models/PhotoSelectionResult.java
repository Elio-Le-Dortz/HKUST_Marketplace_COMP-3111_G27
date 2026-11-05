package com.hkust.marketplace.models;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of adding photos to the listing form of use case 2.3 "Create Listing" (US-S03).
 *
 * <p>The files that are images and fit within the 6 photo limit are added; the other ones
 * are rejected with the reason, so that the Seller is told which files were not added and
 * why (alternative flow A3).</p>
 */
public final class PhotoSelectionResult {

    /** Photos of the form after the addition: the previous ones, then the accepted ones. */
    private final List<Path> photos;

    /** Number of photos added by this selection. */
    private final int addedCount;

    /** One message per rejected file or group of files, explaining why it was not added. */
    private final List<String> rejections;

    /**
     * Creates the outcome of a photo selection.
     *
     * @param photos     the photos of the form after the addition, in display order
     * @param addedCount the number of photos added by this selection
     * @param rejections the reasons why files were not added, empty if all were added
     * @throws NullPointerException     if {@code photos} or {@code rejections} is {@code null},
     *                                  or holds a {@code null} element
     * @throws IllegalArgumentException if {@code addedCount} is negative or larger than the
     *                                  number of photos
     */
    public PhotoSelectionResult(List<Path> photos, int addedCount, List<String> rejections) {
        this.photos = List.copyOf(Objects.requireNonNull(photos, "photos"));
        this.rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
        if (addedCount < 0 || addedCount > this.photos.size()) {
            throw new IllegalArgumentException("addedCount must be between 0 and the number of photos.");
        }
        this.addedCount = addedCount;
    }

    /**
     * Returns the photos of the form after the addition.
     *
     * @return an unmodifiable list: the previous photos, then the accepted ones
     */
    public List<Path> getPhotos() {
        return photos;
    }

    /**
     * Returns the number of photos added by this selection.
     *
     * @return the number of accepted files
     */
    public int getAddedCount() {
        return addedCount;
    }

    /**
     * Returns the reasons why files were not added.
     *
     * @return an unmodifiable list of messages, empty if every file was added
     */
    public List<String> getRejections() {
        return rejections;
    }

    /**
     * Tells whether some files were not added.
     *
     * @return {@code true} if at least one file was rejected
     */
    public boolean hasRejections() {
        return !rejections.isEmpty();
    }
}
