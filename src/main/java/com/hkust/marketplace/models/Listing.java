package com.hkust.marketplace.models;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain model for Listing as defined in the UML Class Diagram.
 */
public class Listing {
    private String id;
    private String title;
    private String description;
    private ListingType type;
    private Category category;
    private Condition condition;
    private Double price;                 // Required only if type == SELL
    private Duration lendingDuration;     // Required only if type == LEND (min 5 seconds)
    private MeetupLocation meetupLocation;
    private ListingStatus status;
    private LocalDateTime postedDate;
    private int viewCount;
    private String adminReason;           // Rejection / Takedown reason
    private String sellerUsername;
    private String sellerFullName;
    private List<Photo> photos = new ArrayList<>();

    public Listing() {
        this.postedDate = LocalDateTime.now();
        this.status = ListingStatus.PENDING_REVIEW;
        this.viewCount = 0;
        this.category = Category.OTHER;
        this.condition = Condition.USED;
        this.type = ListingType.SELL;
        this.meetupLocation = MeetupLocation.LIBRARY;
    }

    // Getters & Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    // Backward-compatible string setter/getter
    public void setCategory(String categoryName) {
        this.category = Category.fromString(categoryName);
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public void setCondition(String conditionName) {
        this.condition = Condition.fromString(conditionName);
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Duration getLendingDuration() {
        return lendingDuration;
    }

    public void setLendingDuration(Duration lendingDuration) {
        this.lendingDuration = lendingDuration;
    }

    // Backward-compatible lending days helpers
    public Integer getLendingDays() {
        if (lendingDuration != null) {
            return (int) lendingDuration.toDays();
        }
        return null;
    }

    public void setLendingDays(Integer days) {
        if (days != null) {
            this.lendingDuration = Duration.ofDays(days);
        } else {
            this.lendingDuration = null;
        }
    }

    public MeetupLocation getMeetupLocation() {
        return meetupLocation;
    }

    public void setMeetupLocation(MeetupLocation meetupLocation) {
        this.meetupLocation = meetupLocation;
    }

    public void setMeetupLocation(String locationName) {
        this.meetupLocation = MeetupLocation.fromString(locationName);
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public LocalDateTime getPostedDate() {
        return postedDate;
    }

    public void setPostedDate(LocalDateTime postedDate) {
        this.postedDate = postedDate;
    }

    // Backward-compatible alias
    public LocalDateTime getPostedAt() {
        return postedDate;
    }

    public void setPostedAt(LocalDateTime postedAt) {
        this.postedDate = postedAt;
    }

    public LocalDateTime getReviewedAt() {
        return postedDate;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        // no-op or store if needed
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public String getAdminReason() {
        return adminReason;
    }

    public void setAdminReason(String adminReason) {
        this.adminReason = adminReason;
    }

    // Backward-compatible alias
    public String getRejectionReason() {
        return adminReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.adminReason = rejectionReason;
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public void setSellerUsername(String sellerUsername) {
        this.sellerUsername = sellerUsername;
    }

    public String getSellerFullName() {
        return sellerFullName;
    }

    public void setSellerFullName(String sellerFullName) {
        this.sellerFullName = sellerFullName;
    }

    public List<Photo> getPhotos() {
        return photos;
    }

    public void setPhotos(List<Photo> photos) {
        this.photos = photos != null ? photos : new ArrayList<>();
    }

    public void addPhoto(Photo photo) {
        if (this.photos == null) {
            this.photos = new ArrayList<>();
        }
        if (this.photos.size() < 6) {
            this.photos.add(photo);
        }
    }

    // Backward-compatible photo path list
    public List<String> getPhotoPaths() {
        List<String> paths = new ArrayList<>();
        if (photos != null) {
            for (Photo p : photos) {
                if (p.getImagePath() != null) paths.add(p.getImagePath());
            }
        }
        return paths;
    }

    public void setPhotoPaths(List<String> paths) {
        this.photos.clear();
        if (paths != null) {
            for (String p : paths) {
                this.photos.add(new Photo(p));
            }
        }
    }

    public String getTypeDisplay() {
        return type != null ? type.getDisplayName() : "";
    }

    public String getPriceDisplay() {
        if (type == ListingType.SELL) {
            return price != null ? "HK$ " + String.format("%.0f", price) : "Free";
        } else if (type == ListingType.LEND) {
            Integer days = getLendingDays();
            return days != null ? "Lend (up to " + days + " days)" : "Lend";
        } else {
            return "FREE";
        }
    }
}
