package com.hkust.marketplace.models;

import com.hkust.marketplace.services.CreateListingService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object encapsulating the inputs required for an administrator to add a listing
 * on behalf of a seller (US-A07, specs_admin.tex Subflow S1: Add Listing).
 */
public class AdminListingCreateRequest {

    private static final List<LendingDurationUnit> UNITS_LARGEST_FIRST = List.of(
            LendingDurationUnit.WEEKS,
            LendingDurationUnit.DAYS,
            LendingDurationUnit.HOURS,
            LendingDurationUnit.MINUTES,
            LendingDurationUnit.SECONDS
    );

    private String sellerUsername;
    private String title;
    private String description;
    private ListingType type;
    private Category category;
    private Condition condition;
    private MeetupLocation meetupLocation;
    private Double price;
    private Integer lendingDays;
    private Duration lendingDuration;
    private String lendingAmount;
    private LendingDurationUnit lendingUnit;
    private List<Photo> photos = new ArrayList<>();

    public AdminListingCreateRequest() {
    }

    public AdminListingCreateRequest(String sellerUsername, String title, String description,
                                     ListingType type, Category category, Condition condition,
                                     MeetupLocation meetupLocation, Double price, Integer lendingDays,
                                     List<Photo> photos) {
        this.sellerUsername = sellerUsername;
        this.title = title;
        this.description = description;
        this.type = type;
        this.category = category;
        this.condition = condition;
        this.meetupLocation = meetupLocation;
        this.price = price;
        setLendingDays(lendingDays);
        setPhotos(photos);
    }

    public AdminListingCreateRequest(String sellerUsername, String title, String description,
                                     ListingType type, Category category, Condition condition,
                                     MeetupLocation meetupLocation, Double price,
                                     String lendingAmount, LendingDurationUnit lendingUnit,
                                     List<Photo> photos) {
        this.sellerUsername = sellerUsername;
        this.title = title;
        this.description = description;
        this.type = type;
        this.category = category;
        this.condition = condition;
        this.meetupLocation = meetupLocation;
        this.price = price;
        setLending(lendingAmount, lendingUnit);
        setPhotos(photos);
    }

    public static AdminListingCreateRequest ofDuration(String sellerUsername, String title, String description,
                                                       ListingType type, Category category, Condition condition,
                                                       MeetupLocation meetupLocation, Double price,
                                                       Duration lendingDuration, List<Photo> photos) {
        AdminListingCreateRequest req = new AdminListingCreateRequest(
                sellerUsername, title, description, type, category, condition, meetupLocation, price, (Integer) null, photos
        );
        req.setLendingDuration(lendingDuration);
        return req;
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public void setSellerUsername(String sellerUsername) {
        this.sellerUsername = sellerUsername;
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
        if (lendingDays != null) {
            return lendingDays;
        }
        Duration duration = getLendingDuration();
        if (duration != null) {
            return (int) duration.toDays();
        }
        return null;
    }

    public void setLendingDays(Integer lendingDays) {
        this.lendingDays = lendingDays;
        if (lendingDays != null) {
            this.lendingDuration = Duration.ofDays(lendingDays);
            this.lendingAmount = String.valueOf(lendingDays);
            this.lendingUnit = LendingDurationUnit.DAYS;
        } else {
            this.lendingDuration = null;
            this.lendingAmount = null;
            this.lendingUnit = null;
        }
    }

    public Duration getLendingDuration() {
        if (lendingDuration != null) {
            return lendingDuration;
        }
        if (lendingAmount != null && lendingUnit != null) {
            try {
                return CreateListingService.toLendingDuration(lendingAmount, lendingUnit);
            } catch (Exception ignored) {
                return null;
            }
        }
        if (lendingDays != null) {
            return Duration.ofDays(lendingDays);
        }
        return null;
    }

    public void setLendingDuration(Duration lendingDuration) {
        this.lendingDuration = lendingDuration;
        if (lendingDuration == null) {
            this.lendingDays = null;
            this.lendingAmount = null;
            this.lendingUnit = null;
            return;
        }
        this.lendingDays = (int) lendingDuration.toDays();
        long seconds = lendingDuration.getSeconds();
        if (seconds > 0) {
            for (LendingDurationUnit unit : UNITS_LARGEST_FIRST) {
                long unitSeconds = unit.getLength().getSeconds();
                if (seconds % unitSeconds == 0) {
                    this.lendingUnit = unit;
                    this.lendingAmount = String.valueOf(seconds / unitSeconds);
                    return;
                }
            }
            this.lendingUnit = LendingDurationUnit.SECONDS;
            this.lendingAmount = String.valueOf(seconds);
        } else {
            this.lendingAmount = String.valueOf(seconds);
            this.lendingUnit = LendingDurationUnit.DAYS;
        }
    }

    public void setLending(String amount, LendingDurationUnit unit) {
        this.lendingAmount = amount;
        this.lendingUnit = unit;
        try {
            this.lendingDuration = CreateListingService.toLendingDuration(amount, unit);
            this.lendingDays = (int) this.lendingDuration.toDays();
        } catch (Exception ex) {
            this.lendingDuration = null;
            this.lendingDays = null;
        }
    }

    public String getLendingAmount() {
        return lendingAmount;
    }

    public void setLendingAmount(String lendingAmount) {
        this.lendingAmount = lendingAmount;
        updateDurationFromAmountAndUnit();
    }

    public LendingDurationUnit getLendingUnit() {
        return lendingUnit;
    }

    public void setLendingUnit(LendingDurationUnit lendingUnit) {
        this.lendingUnit = lendingUnit;
        updateDurationFromAmountAndUnit();
    }

    private void updateDurationFromAmountAndUnit() {
        if (lendingAmount != null && lendingUnit != null) {
            try {
                this.lendingDuration = CreateListingService.toLendingDuration(lendingAmount, lendingUnit);
                this.lendingDays = (int) this.lendingDuration.toDays();
            } catch (Exception ignored) {
                this.lendingDuration = null;
                this.lendingDays = null;
            }
        }
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
