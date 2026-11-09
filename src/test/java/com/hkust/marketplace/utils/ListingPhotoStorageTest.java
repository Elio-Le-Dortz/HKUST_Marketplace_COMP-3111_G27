package com.hkust.marketplace.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link ListingPhotoStorage}, which keeps copies of the listing photos in the
 * application's folder (review of the seller part, finding 3).
 */
@DisplayName("ListingPhotoStorage: listing photos are copied into the application's folder [RS3]")
class ListingPhotoStorageTest {

    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @TempDir
    Path tempDir;

    private Path sellerFolder;
    private ListingPhotoStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        sellerFolder = Files.createDirectories(tempDir.resolve("Downloads"));
        storage = new ListingPhotoStorage(tempDir.resolve("uploads"));
    }

    private Path file(String name, byte[] content) throws IOException {
        return Files.write(sellerFolder.resolve(name), content);
    }

    private List<Path> filesInStorage() throws IOException {
        try (Stream<Path> files = Files.list(storage.getRoot())) {
            return files.sorted().toList();
        }
    }

    @Test
    @DisplayName("A photo is copied into the folder with its content, under the hash of its content and its name")
    void copiesPhoto() throws IOException {
        Path photo = file("lamp-front.png", PNG_HEADER);

        Path copy = storage.store(photo);

        assertTrue(storage.isStored(copy));
        assertEquals(storage.getRoot(), copy.getParent());
        assertEquals(-1, Files.mismatch(photo, copy));
        assertTrue(copy.getFileName().toString().matches("[0-9a-f]{" + ListingPhotoStorage.HASH_DIGITS + "}-lamp-front\\.png"),
                copy::toString);
        assertFalse(storage.isStored(photo));
    }

    @Test
    @DisplayName("Storing the same file again reuses its copy")
    void sameFileSameCopy() throws IOException {
        Path photo = file("desk.jpg", PNG_HEADER);

        Path first = storage.store(photo);
        Path second = storage.store(photo);

        assertEquals(first, second);
        assertEquals(List.of(first), filesInStorage());
    }

    @Test
    @DisplayName("Different files never share a copy, even with the same content or the same name")
    void differentFilesDifferentCopies() throws IOException {
        Path a = file("a.png", PNG_HEADER);
        Path b = file("b.png", PNG_HEADER);
        Path otherA = Files.write(Files.createDirectories(tempDir.resolve("Desktop")).resolve("a.png"), new byte[] {1, 2, 3});

        List<Path> copies = storage.store(List.of(a, b, otherA));

        assertEquals(3, copies.stream().distinct().count());
        assertEquals(3, filesInStorage().size());
    }

    @Test
    @DisplayName("The copies keep the order of the photos")
    void keepsOrder() throws IOException {
        Path a = file("1.png", new byte[] {1});
        Path b = file("2.png", new byte[] {2});

        List<Path> copies = storage.store(List.of(b, a));

        assertEquals(-1, Files.mismatch(b, copies.get(0)));
        assertEquals(-1, Files.mismatch(a, copies.get(1)));
    }

    @Test
    @DisplayName("A photo already in the folder is returned as it is, without a new copy")
    void storedPhotoKept() throws IOException {
        Path copy = storage.store(file("lamp.png", PNG_HEADER));

        assertEquals(copy, storage.store(copy));
        assertEquals(copy, storage.store(storage.getRoot().resolve("x").resolve("..").resolve(copy.getFileName())));
        assertEquals(1, filesInStorage().size());
    }

    @Test
    @DisplayName("The copy stays when the original file is deleted")
    void survivesDeletedOriginal() throws IOException {
        Path photo = file("lamp.png", PNG_HEADER);
        Path copy = storage.store(photo);

        Files.delete(photo);

        assertTrue(Files.isRegularFile(copy));
    }

    @Test
    @DisplayName("A missing file is refused and no temporary file is left")
    void missingFile() throws IOException {
        assertThrows(NoSuchFileException.class, () -> storage.store(sellerFolder.resolve("gone.png")));
        assertTrue(filesInStorage().isEmpty());
    }

    @Test
    @DisplayName("A folder that cannot be created is reported")
    void folderCannotBeCreated() throws IOException {
        Path regularFile = file("not-a-folder", new byte[] {1});
        ListingPhotoStorage broken = new ListingPhotoStorage(regularFile);

        assertThrows(IOException.class, () -> broken.store(file("lamp.png", PNG_HEADER)));
    }

    @Test
    @DisplayName("File names are made safe and a long name keeps its end, where the extension is")
    void safeNames() {
        assertEquals("my_holiday_photo__1_.png", ListingPhotoStorage.safeName(Path.of("my holiday photo (1).png")));
        assertEquals("caf__.jpg", ListingPhotoStorage.safeName(Path.of("café✓.jpg")));
        assertEquals("photo", ListingPhotoStorage.safeName(Path.of("..")));
        String longName = "x".repeat(200) + ".png";
        String safe = ListingPhotoStorage.safeName(Path.of(longName));
        assertEquals(ListingPhotoStorage.MAX_ORIGINAL_NAME_LENGTH, safe.length());
        assertTrue(safe.endsWith(".png"));
    }

    @Test
    @DisplayName("The default folder is data/uploads/listings under the working directory, created only on the first copy")
    void defaultFolder() {
        ListingPhotoStorage defaults = new ListingPhotoStorage();

        assertEquals(Path.of(ListingPhotoStorage.DEFAULT_FOLDER).toAbsolutePath().normalize(), defaults.getRoot());
        assertTrue(defaults.getRoot().isAbsolute());
        assertNotEquals(storage.getRoot(), defaults.getRoot());
    }

    @Test
    @DisplayName("Missing arguments are refused")
    void nullArguments() {
        assertThrows(NullPointerException.class, () -> new ListingPhotoStorage(null));
        assertThrows(NullPointerException.class, () -> storage.store((Path) null));
        assertThrows(NullPointerException.class, () -> storage.store((List<Path>) null));
        assertThrows(NullPointerException.class, () -> storage.isStored(null));
    }
}
