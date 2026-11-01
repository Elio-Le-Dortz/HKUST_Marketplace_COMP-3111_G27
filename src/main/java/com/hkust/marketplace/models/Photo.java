package com.hkust.marketplace.models;

import java.io.File;

/**
 * Domain model for Photo as defined in the UML Class Diagram (Listing 1 *-- 1..6 Photo).
 */
public class Photo {
    private String id;
    private String imagePath;

    public Photo() {}

    public Photo(String imagePath) {
        this.imagePath = imagePath;
    }

    public Photo(String id, String imagePath) {
        this.id = id;
        this.imagePath = imagePath;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public File getImageFile() {
        return imagePath != null ? new File(imagePath) : null;
    }
}
