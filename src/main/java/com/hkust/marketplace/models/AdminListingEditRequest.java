package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object encapsulating the inputs required for an administrator to edit an existing
 * listing (US-A07, specs_admin.tex Subflow S2: Edit Listing).
 */
public class AdminListingEditRequest {

    private String listingId;
    private String title;
    private String description;
    private ListingType type;
    private Category category;
    private Condition condition;
    private MeetupLocation meetupLocation;
    private Double price;
    private Integer lendingDays;
    private List<Photo> photos = new ArrayList<>();

    public AdminListingEditRequest() {
    }

    public AdminListingEditRequest(String listingId, String title, String description,
                                   ListingType type, Category category, Condition condition,
                                   MeetupLocation meetupLocation, Double price, Integer lendingDays,
                                   List<Photo> photos) {
        this.listingId = listingId;
        this.title = title;
        this.description = description;
        this.type = type;
        this.category = category;
        this.condition = condition;
        this.meetupLocation = meetupLocation;
        this.price = price;
        this.lendingDays = lendingDays;
        setPhotos(photos);
    }

    public String getListingId() {
        return listingId;
    }

    public void setListingId(String listingId) {
        this.listingId = listingId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ListingType getType() {
        return type;
    }

    public void setType(ListingType type) {
        this.type = type;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public MeetupLocation getMeetupLocation() {
        return meetupLocation;
    }

    public void setMeetupLocation(MeetupLocation meetupLocation) {
        this.meetupLocation = meetupLocation;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getLendingDays() {
        return lendingDays;
    }

    public void setLendingDays(Integer lendingDays) {
        this.lendingDays = lendingDays;
    }

    public List<Photo> getPhotos() {
        return new ArrayList<>(photos);
    }

    public void setPhotos(List<Photo> photos) {
        this.photos.clear();
        if (photos != null) {
            this.photos.addAll(photos);
        }
    }

    public void addPhoto(Photo photo) {
        if (photo != null) {
            this.photos.add(photo);
        }
    }
}
