package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.stream.Collectors;

/**
 * Service encapsulating the business rules, validation, and domain state transitions
 * for managing all listings in the HKUST Marketplace (Sprint 2 US-A07, specs_admin.tex Use Case 3.7).
 *
 * <p>Completely decoupled from JavaFX UI controls to support headless JUnit 5 execution.
 */
public class AdminListingService {

    private final DummyDataService dataService;

    public AdminListingService() {
        this(DummyDataService.getInstance());
    }

    public AdminListingService(DummyDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "DataService cannot be null.");
    }

    /**
     * Retrieves all listings in the system, sorted newest posted date first (Basic Flow Step 2).
     *
     * @return list of all listings
     */
    public List<Listing> getAllListings() {
        SessionManager.requireAdmin();
        synchronized (dataService) {
            return filterListings(AdminListingFilterCriteria.all());
        }
    }

    /**
     * Filters and sorts all listings according to the provided criteria (Basic Flow Step 2).
     *
     * @param criteria filter criteria (keyword, status, category, listing type)
     * @return filtered listings sorted newest first
     */
    public List<Listing> filterListings(AdminListingFilterCriteria criteria) {
        SessionManager.requireAdmin();
        AdminListingFilterCriteria effectiveCriteria = (criteria != null) ? criteria : AdminListingFilterCriteria.all();
        synchronized (dataService) {
            return dataService.getAllListings().stream()
                    .filter(l -> l != null && effectiveCriteria.matches(l))
                    .sorted((l1, l2) -> {
                        LocalDateTime d1 = l1.getPostedAt();
                        LocalDateTime d2 = l2.getPostedAt();
                        if (d1 == null && d2 == null) return 0;
                        if (d1 == null) return 1;
                        if (d2 == null) return -1;
                        int cmp = d2.compareTo(d1); // Newest first
                        if (cmp != 0) return cmp;
                        return (l1.getId() != null && l2.getId() != null) ? l2.getId().compareTo(l1.getId()) : 0;
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * Adds a listing on behalf of an active seller directly into the system with status LIVE
     * (Subflow S1: Add Listing).
     *
     * @param request       the listing details to create
     * @param adminUsername the username of the acting administrator
     * @return an {@link AdminListingOperationResult} detailing the outcome
     */
    public AdminListingOperationResult createListingOnBehalfOfSeller(AdminListingCreateRequest request, String adminUsername) {
        User admin = verifyAdmin(adminUsername);
        if (admin == null) {
            return AdminListingOperationResult.unauthorized("Action requires an active administrator account.");
        }

        if (request == null) {
            return AdminListingOperationResult.invalidField("request", "Listing create request cannot be null.");
        }

        synchronized (dataService) {
            // Alternative Flow A2: Seller Not Active
            if (request.getSellerUsername() == null || request.getSellerUsername().trim().isEmpty()) {
                return AdminListingOperationResult.sellerNotActive("A seller must be specified.");
            }
            User sellerUser = dataService.findUser(request.getSellerUsername().trim());
            if (sellerUser == null || sellerUser.getRole() != UserRole.SELLER || !sellerUser.isActive()) {
                return AdminListingOperationResult.sellerNotActive("Listings can only be added for an active seller.");
            }

            // Alternative Flow A3: Missing Or Invalid Field
            AdminListingOperationResult validation = validateListingFields(
                    request.getTitle(), request.getDescription(), request.getType(), request.getCategory(),
                    request.getCondition(), request.getMeetupLocation(),
                    request.getPrice(), request.getLendingDays(), request.getPhotos()
            );
            if (validation != null) {
                return validation;
            }

            // Subflow S1 Step 5: Store listing for the selected seller with status Live
            Listing listing = new Listing();
            listing.setTitle(request.getTitle().trim());
            listing.setDescription(request.getDescription() != null ? request.getDescription().trim() : "");
            listing.setType(request.getType());
            listing.setCategory(request.getCategory());
            listing.setCondition(request.getCondition());
            listing.setMeetupLocation(request.getMeetupLocation());
            listing.setSellerUsername(sellerUser.getUsername());
            listing.setSellerFullName(sellerUser.getFullName());
            listing.setPostedDate(LocalDateTime.now());
            listing.setStatus(ListingStatus.LIVE);

            if (request.getType() == ListingType.SELL) {
                listing.setPrice(request.getPrice());
                listing.setLendingDays(null);
            } else if (request.getType() == ListingType.LEND) {
                listing.setPrice(null);
                listing.setLendingDays(request.getLendingDays());
            } else {
                listing.setPrice(null);
                listing.setLendingDays(null);
            }

            dataService.addListing(listing);

            List<Photo> storedPhotos = new ArrayList<>();
            for (int i = 0; i < request.getPhotos().size(); i++) {
                Photo p = request.getPhotos().get(i);
                String photoId = listing.getId() + "-P" + (i + 1);
                storedPhotos.add(new Photo(photoId, Path.of(p.getImagePath().trim()).toAbsolutePath().normalize().toString()));
            }
            listing.setPhotos(storedPhotos);
            dataService.updateListing(listing);

            // Subflow S1 Step 5: Notify seller
            Notification sellerNotif = new Notification(
                    sellerUser.getUsername(),
                    "Listing Published by Admin",
                    "An administrator has published a new listing on your behalf: '" + listing.getTitle() + "'.",
                    "Listings",
                    listing.getId(),
                    "LISTING"
            );
            dataService.addNotification(sellerNotif);

            return AdminListingOperationResult.success(listing, "Listing '" + listing.getTitle() + "' published successfully on behalf of " + sellerUser.getFullName() + ".");
        }
    }

    /**
     * Edits the details of an existing listing while keeping its current status (Subflow S2: Edit Listing).
     *
     * @param request       the updated listing details
     * @param adminUsername the username of the acting administrator
     * @return an {@link AdminListingOperationResult} detailing the outcome
     */
    public AdminListingOperationResult editListing(AdminListingEditRequest request, String adminUsername) {
        User admin = verifyAdmin(adminUsername);
        if (admin == null) {
            return AdminListingOperationResult.unauthorized("Action requires an active administrator account.");
        }

        if (request == null || request.getListingId() == null || request.getListingId().trim().isEmpty()) {
            return AdminListingOperationResult.invalidField("listingId", "Listing ID is required.");
        }

        synchronized (dataService) {
            Listing listing = dataService.findListing(request.getListingId().trim());
            if (listing == null) {
                return AdminListingOperationResult.listingNotFound("Listing not found.");
            }

            // Alternative Flow A4: Deal In Progress Or Completed
            if (isDealInProgressOrCompleted(listing.getStatus())) {
                return AdminListingOperationResult.dealInProgressOrCompleted(
                        "The listing cannot be changed because a deal is in progress or completed."
                );
            }

            // Alternative Flow A3: Missing Or Invalid Field
            AdminListingOperationResult validation = validateListingFields(
                    request.getTitle(), request.getDescription(), request.getType(), request.getCategory(),
                    request.getCondition(), request.getMeetupLocation(),
                    request.getPrice(), request.getLendingDays(), request.getPhotos()
            );
            if (validation != null) {
                return validation;
            }

            // Subflow S2 Step 5: Save changes, keep status
            listing.setTitle(request.getTitle().trim());
            listing.setDescription(request.getDescription() != null ? request.getDescription().trim() : "");
            listing.setType(request.getType());
            listing.setCategory(request.getCategory());
            listing.setCondition(request.getCondition());
            listing.setMeetupLocation(request.getMeetupLocation());

            if (request.getType() == ListingType.SELL) {
                listing.setPrice(request.getPrice());
                listing.setLendingDays(null);
            } else if (request.getType() == ListingType.LEND) {
                listing.setPrice(null);
                listing.setLendingDays(request.getLendingDays());
            } else {
                listing.setPrice(null);
                listing.setLendingDays(null);
            }

            List<Photo> storedEditPhotos = new ArrayList<>();
            for (int i = 0; i < request.getPhotos().size(); i++) {
                Photo p = request.getPhotos().get(i);
                String photoId = listing.getId() + "-P" + (i + 1);
                storedEditPhotos.add(new Photo(photoId, Path.of(p.getImagePath().trim()).toAbsolutePath().normalize().toString()));
            }
            listing.setPhotos(storedEditPhotos);

            dataService.updateListing(listing);

            // Subflow S2 Step 5: Notify seller
            Notification sellerNotif = new Notification(
                    listing.getSellerUsername(),
                    "Listing Updated by Admin",
                    "An administrator has updated the details of your listing: '" + listing.getTitle() + "'.",
                    "Listings",
                    listing.getId(),
                    "LISTING"
            );
            dataService.addNotification(sellerNotif);

            return AdminListingOperationResult.success(listing, "Listing '" + listing.getTitle() + "' updated successfully.");
        }
    }

    /**
     * Takes down a live listing with a mandatory reason, atomically declining all active offers
     * and notifying affected buyers and seller (Subflow S3: Take Down Listing).
     *
     * @param listingId     the ID of the listing to take down
     * @param reason        the mandatory administrative reason
     * @param adminUsername the username of the acting administrator
     * @return an {@link AdminListingOperationResult} detailing the outcome
     */
    public AdminListingOperationResult takeDownListing(String listingId, String reason, String adminUsername) {
        User admin = verifyAdmin(adminUsername);
        if (admin == null) {
            return AdminListingOperationResult.unauthorized("Action requires an active administrator account.");
        }

        // Alternative Flow A6: Empty Reason
        if (reason == null || reason.trim().isEmpty()) {
            return AdminListingOperationResult.emptyReason("A reason is required to take down a listing.");
        }

        if (listingId == null || listingId.trim().isEmpty()) {
            return AdminListingOperationResult.listingNotFound("Listing ID is required.");
        }

        // Atomic check and mutation on data store
        synchronized (dataService) {
            Listing listing = dataService.findListing(listingId.trim());
            if (listing == null) {
                return AdminListingOperationResult.listingNotFound("Listing not found.");
            }

            // Alternative Flow A4: Deal In Progress Or Completed
            if (isDealInProgressOrCompleted(listing.getStatus())) {
                return AdminListingOperationResult.dealInProgressOrCompleted(
                        "The listing cannot be changed because a deal is in progress or completed."
                );
            }

            // Alternative Flow A5: Listing Not Live
            if (listing.getStatus() != ListingStatus.LIVE) {
                return AdminListingOperationResult.listingNotLive(
                        "Only a Live listing can be taken down (a pending listing is rejected in Review Pending Listings)."
                );
            }

            String cleanReason = reason.trim();

            // Subflow S3 Step 5: Set listing status to Removed and store reason
            listing.setStatus(ListingStatus.REMOVED);
            listing.setAdminReason(cleanReason);
            dataService.updateListing(listing);

            // Subflow S3 Step 6: Decline all pending or countered offers, borrow requests, and claims
            List<Offer> activeOffers = dataService.getOffersForListing(listing.getId()).stream()
                    .filter(o -> o.getStatus() == OfferStatus.PENDING || o.getStatus() == OfferStatus.COUNTERED)
                    .collect(Collectors.toList());

            for (Offer offer : activeOffers) {
                offer.setStatus(OfferStatus.DECLINED);
                offer.setDeclineReason("Listing removed by administrator: " + cleanReason);
                if (offer.getCounterOffer() != null) {
                    offer.getCounterOffer().setStatus(OfferStatus.DECLINED);
                    offer.getCounterOffer().setDeclineReason("Listing removed by administrator: " + cleanReason);
                }

                // Notify buyer who held the active offer
                Notification buyerNotif = new Notification(
                        offer.getBuyerUsername(),
                        "Listing Removed by Admin",
                        "A listing you had an open offer on ('" + listing.getTitle() + "') was removed by the administrator. Reason: " + cleanReason,
                        "Listings",
                        listing.getId(),
                        "LISTING"
                );
                dataService.addNotification(buyerNotif);
            }

            // Subflow S3 Step 7: Notify seller that listing was taken down with reason
            Notification sellerNotif = new Notification(
                    listing.getSellerUsername(),
                    "Listing Taken Down",
                    "Your listing ('" + listing.getTitle() + "') was taken down by the administrator. Reason: " + cleanReason,
                    "Listings",
                    listing.getId(),
                    "LISTING"
            );
            dataService.addNotification(sellerNotif);

            return AdminListingOperationResult.success(listing, "Listing '" + listing.getTitle() + "' taken down successfully.", activeOffers.size());
        }
    }

    /**
     * Retrieves all active sellers currently in the system for admin selection.
     *
     * @return list of active sellers
     */
    public List<Seller> getActiveSellers() {
        SessionManager.requireAdmin();
        synchronized (dataService) {
            return dataService.getAllUsers().stream()
                    .filter(u -> u instanceof Seller && u.isActive())
                    .map(u -> (Seller) u)
                    .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Counts the number of active offers (PENDING or COUNTERED) on a listing.
     *
     * @param listingId the listing ID
     * @return count of active offers
     */
    public int getActiveOffersCount(String listingId) {
        SessionManager.requireAdmin();
        if (listingId == null) return 0;
        synchronized (dataService) {
            return (int) dataService.getOffersForListing(listingId).stream()
                    .filter(o -> o.getStatus() == OfferStatus.PENDING || o.getStatus() == OfferStatus.COUNTERED)
                    .count();
        }
    }

    /**
     * Returns the distribution of listings grouped by {@link ListingStatus}.
     *
     * @return map of status to count
     */
    public Map<ListingStatus, Long> getListingCountsByStatus() {
        SessionManager.requireAdmin();
        synchronized (dataService) {
            Map<ListingStatus, Long> counts = new EnumMap<>(ListingStatus.class);
            for (ListingStatus s : ListingStatus.values()) {
                counts.put(s, 0L);
            }
            for (Listing l : dataService.getAllListings()) {
                if (l != null && l.getStatus() != null) {
                    counts.put(l.getStatus(), counts.get(l.getStatus()) + 1);
                }
            }
            return counts;
        }
    }

    private User verifyAdmin(String adminUsername) {
        Admin sessionAdmin = SessionManager.requireAdmin();
        if (adminUsername == null || adminUsername.trim().isEmpty()) {
            return null;
        }
        User u = dataService.findUser(adminUsername.trim());
        if (u == null || u.getRole() != UserRole.ADMIN || !u.isActive()) {
            return null;
        }
        if (!sessionAdmin.getUsername().equalsIgnoreCase(u.getUsername())) {
            return null;
        }
        return u;
    }

    private boolean isDealInProgressOrCompleted(ListingStatus status) {
        if (status == null) return false;
        return status == ListingStatus.RESERVED ||
                status == ListingStatus.SOLD ||
                status == ListingStatus.LENT ||
                status == ListingStatus.GIVEN;
    }

    private AdminListingOperationResult validateListingFields(
            String title, String description, ListingType type, Category category,
            Condition condition, MeetupLocation meetupLocation,
            Double price, Integer lendingDays, List<Photo> photos) {

        if (title == null || title.trim().isEmpty()) {
            return AdminListingOperationResult.invalidField("title", "Item name is required.");
        }
        if (title.trim().codePointCount(0, title.trim().length()) > CreateListingService.MAX_TITLE_LENGTH) {
            return AdminListingOperationResult.invalidField("title", CreateListingService.TITLE_TOO_LONG);
        }
        if (description == null || description.trim().isEmpty()) {
            return AdminListingOperationResult.invalidField("description", "Description is required.");
        }
        if (description.trim().codePointCount(0, description.trim().length()) > CreateListingService.MAX_DESCRIPTION_LENGTH) {
            return AdminListingOperationResult.invalidField("description", CreateListingService.DESCRIPTION_TOO_LONG);
        }
        if (category == null) {
            return AdminListingOperationResult.invalidField("category", "Category is required.");
        }
        if (condition == null) {
            return AdminListingOperationResult.invalidField("condition", "Condition is required.");
        }
        if (meetupLocation == null) {
            return AdminListingOperationResult.invalidField("meetupLocation", "Meet-up location is required.");
        }
        if (type == null) {
            return AdminListingOperationResult.invalidField("type", "Listing type is required.");
        }

        if (type == ListingType.SELL) {
            if (price == null || price <= 0.0) {
                return AdminListingOperationResult.invalidField("price", "Price must be greater than zero for Sell listings.");
            }
        } else if (type == ListingType.LEND) {
            if (lendingDays == null || lendingDays <= 0) {
                return AdminListingOperationResult.invalidField("lendingDays", "Lending duration must be positive for Lend listings.");
            }
        }

        // Photos: 1 to 6 photos
        if (photos == null || photos.isEmpty()) {
            return AdminListingOperationResult.invalidField("photos", "At least one photo is required (1 to 6 photos).");
        }
        if (photos.size() > CreateListingService.MAX_PHOTOS) {
            return AdminListingOperationResult.invalidField("photos", "A listing cannot have more than 6 photos.");
        }

        Set<Path> seenPhotos = new HashSet<>();
        for (Photo photo : photos) {
            if (photo == null || photo.getImagePath() == null || photo.getImagePath().trim().isEmpty()) {
                return AdminListingOperationResult.invalidField("photos", "Photo path cannot be empty.");
            }
            Path path;
            try {
                path = Path.of(photo.getImagePath().trim());
            } catch (InvalidPathException e) {
                return AdminListingOperationResult.invalidField("photos", "Invalid photo path: " + photo.getImagePath());
            }
            String problem = CreateListingService.photoProblem(path);
            if (problem != null) {
                String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
                return AdminListingOperationResult.invalidField("photos", name + " " + problem);
            }
            if (!seenPhotos.add(path.toAbsolutePath().normalize())) {
                String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
                return AdminListingOperationResult.invalidField("photos", name + " " + CreateListingService.PHOTO_REPEATED);
            }
        }

        return null; // Valid
    }
}
