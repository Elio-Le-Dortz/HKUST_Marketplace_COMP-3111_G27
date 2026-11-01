package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * In-memory data store for the frontend demo.
 * All data is lost when the application closes.
 * TODO: Replace this service with real repositories / API clients.
 */
public class DummyDataService {

    private static final DummyDataService INSTANCE = new DummyDataService();

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final List<Deal> deals = new ArrayList<>();
    private final List<Offer> offers = new ArrayList<>();
    private final List<Report> reports = new ArrayList<>();
    private final List<ChatThread> chatThreads = new ArrayList<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final List<Review> reviews = new ArrayList<>();

    private final AtomicInteger listingIdCounter = new AtomicInteger(100);
    private final AtomicInteger dealIdCounter = new AtomicInteger(500);
    private final AtomicInteger offerIdCounter = new AtomicInteger(700);
    private final AtomicInteger reportIdCounter = new AtomicInteger(300);
    private final AtomicInteger notifIdCounter = new AtomicInteger(1);
    private final AtomicInteger reviewIdCounter = new AtomicInteger(1);
    private final AtomicInteger threadIdCounter = new AtomicInteger(1);

    // Predefined HKUST locations
    public static final List<String> HKUST_LOCATIONS = List.of(
            "Academic Building (LG1)",
            "Library",
            "Student Centre",
            "Hall 1 Lobby",
            "Hall 2 Lobby",
            "Hall 3 Lobby",
            "Hall 4 Lobby",
            "Hall 5 Lobby",
            "Hall 6 Lobby",
            "Hall 7 Lobby",
            "Hall 8 Lobby",
            "Hall 9 Lobby",
            "UG Hall 1",
            "UG Hall 2",
            "North Gate",
            "South Gate",
            "Shaw Auditorium",
            "Coffee Shop (LG5)",
            "Sports Hall"
    );

    public static final List<String> CATEGORIES = List.of(
            "Textbooks", "Electronics", "Furniture", "Clothing",
            "Stationery", "Sports Equipment", "Kitchenware", "Others"
    );

    public static final List<String> CONDITIONS = List.of(
            "New", "Like New", "Used", "For Parts"
    );

    private DummyDataService() {
        seedData();
    }

    public static DummyDataService getInstance() {
        return INSTANCE;
    }

    private void seedData() {
        // Admin
        Admin admin = new Admin("admin", "HKUST Marketplace Admin", "admin123");
        users.put("admin", admin);

        // Sample buyers
        Buyer buyer1 = new Buyer("alice", "Alice Chan", "password1", "Hall 3");
        users.put("alice", buyer1);

        Buyer buyer2 = new Buyer("bob", "Bob Wong", "password1", "Off-campus");
        users.put("bob", buyer2);

        // Sample sellers
        Seller seller1 = new Seller("charlie", "Charlie Lee", "password1", "Final year CSE student selling textbooks and gadgets.");
        seller1.setPreferredMeetup("Library");
        seller1.setAverageRating(4.7);
        seller1.setRatingCount(12);
        users.put("charlie", seller1);

        Seller seller2 = new Seller("diana", "Diana Ng", "password1", "Selling furniture and kitchen items before graduation.");
        seller2.setPreferredMeetup("Hall 5 Lobby");
        seller2.setAverageRating(4.3);
        seller2.setRatingCount(8);
        users.put("diana", seller2);

        // Sample listings
        Listing l1 = createListing("Calculus Textbook (Stewart)", "Barely used, 8th edition. Perfect for MATH 1013.",
                "Textbooks", ListingType.SELL, "Like New", 180.0, null, "Library", "charlie", "Charlie Lee");
        l1.setStatus(ListingStatus.LIVE);
        listings.put(l1.getId(), l1);

        Listing l2 = createListing("MacBook Pro Charger 61W", "Original Apple charger, works perfectly.",
                "Electronics", ListingType.SELL, "Used", 250.0, null, "Academic Building (LG1)", "charlie", "Charlie Lee");
        l2.setStatus(ListingStatus.LIVE);
        listings.put(l2.getId(), l2);

        Listing l3 = createListing("IKEA Desk Lamp", "Good condition, LED bulb included.",
                "Furniture", ListingType.GIVEAWAY, "Used", null, null, "Hall 5 Lobby", "diana", "Diana Ng");
        l3.setStatus(ListingStatus.LIVE);
        listings.put(l3.getId(), l3);

        Listing l4 = createListing("Graphing Calculator TI-84", "Can lend for one semester.",
                "Electronics", ListingType.LEND, "Like New", null, 90, "Student Centre", "diana", "Diana Ng");
        l4.setStatus(ListingStatus.LIVE);
        listings.put(l4.getId(), l4);

        Listing l5 = createListing("Mini Fridge", "Works well, cleaning required.",
                "Furniture", ListingType.SELL, "Used", 400.0, null, "Hall 2 Lobby", "diana", "Diana Ng");
        l5.setStatus(ListingStatus.PENDING_REVIEW);
        listings.put(l5.getId(), l5);

        // Sample deals
        Deal d1 = new Deal();
        d1.setId("D" + dealIdCounter.getAndIncrement());
        d1.setListingId(l1.getId());
        d1.setListingTitle(l1.getTitle());
        d1.setBuyerUsername("alice");
        d1.setSellerUsername("charlie");
        d1.setDealType(ListingType.SELL);
        d1.setStatus("Accepted");
        d1.setOfferPrice(160.0);
        deals.add(d1);

        // Sample notifications
        notifications.add(new Notification("alice", "Offer Accepted",
                "Charlie accepted your offer of HK$160 for Calculus Textbook.", "Offer"));
        notifications.add(new Notification("charlie", "New Offer Received",
                "Alice offered HK$160 for your Calculus Textbook.", "Offer"));
        notifications.add(new Notification("diana", "Listing Pending",
                "Your Mini Fridge listing is awaiting admin review.", "System"));
        notifications.add(new Notification("admin", "New Listing Submitted",
                "Diana submitted 'Mini Fridge' for review.", "System"));

        // Sample review
        Review r1 = new Review();
        r1.setId("R" + reviewIdCounter.getAndIncrement());
        r1.setSellerUsername("charlie");
        r1.setBuyerUsername("bob");
        r1.setListingTitle("Old Chemistry Book");
        r1.setRating(5);
        r1.setComment("Very smooth transaction, book in excellent condition!");
        reviews.add(r1);
    }

    private Listing createListing(String title, String desc, String cat, ListingType type,
                                  String condition, Double price, Integer days,
                                  String meetup, String sellerUser, String sellerName) {
        Listing l = new Listing();
        l.setId("L" + listingIdCounter.getAndIncrement());
        l.setTitle(title);
        l.setDescription(desc);
        l.setCategory(cat);
        l.setType(type);
        l.setCondition(condition);
        l.setPrice(price);
        l.setLendingDays(days);
        l.setMeetupLocation(meetup);
        l.setSellerUsername(sellerUser);
        l.setSellerFullName(sellerName);
        l.setPostedAt(LocalDateTime.now().minusDays(new Random().nextInt(10)));
        return l;
    }

    // ========== User methods ==========

    /**
     * Tells whether any account (buyer, seller or admin, whatever its status) already
     * uses a username, ignoring case. Soft-deleted accounts keep their username reserved.
     *
     * @param username the username to look up; must not be {@code null}
     * @return {@code true} if the username is taken, {@code false} otherwise
     */
    public synchronized boolean usernameExists(String username) {
        return users.containsKey(username.toLowerCase());
    }

    public User findUser(String username) {
        return users.get(username.toLowerCase());
    }

    public void registerUser(User user) {
        users.put(user.getUsername().toLowerCase(), user);
    }

    /**
     * Atomically stores a new user only if no account of any role already uses the
     * same username, ignoring case. Synchronized so that two concurrent registrations
     * with the same username cannot both succeed (only the first one is stored).
     *
     * @param user the user to store; its username must not be {@code null}
     * @return {@code true} if the user was stored, {@code false} if the username was
     *         already taken (in which case nothing is changed)
     */
    public synchronized boolean registerUserIfAbsent(User user) {
        return users.putIfAbsent(user.getUsername().toLowerCase(), user) == null;
    }

    public Collection<User> getAllUsers() {
        return users.values();
    }

    public List<User> getUsersByRole(UserRole role) {
        return users.values().stream()
                .filter(u -> u.getRole() == role)
                .collect(Collectors.toList());
    }

    // ========== Listing methods ==========
    public Collection<Listing> getAllListings() {
        return listings.values();
    }

    public List<Listing> getLiveListings() {
        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.LIVE)
                .collect(Collectors.toList());
    }

    public List<Listing> getPendingListings() {
        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.PENDING_REVIEW)
                .collect(Collectors.toList());
    }

    public List<Listing> getListingsBySeller(String username) {
        return listings.values().stream()
                .filter(l -> l.getSellerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public Listing findListing(String id) {
        return listings.get(id);
    }

    public void addListing(Listing listing) {
        if (listing.getId() == null) {
            listing.setId("L" + listingIdCounter.getAndIncrement());
        }
        listings.put(listing.getId(), listing);
    }

    public void updateListing(Listing listing) {
        listings.put(listing.getId(), listing);
    }

    public void removeListing(String id) {
        listings.remove(id);
    }

    // ========== Deal methods ==========
    public List<Deal> getDealsForBuyer(String username) {
        return deals.stream()
                .filter(d -> d.getBuyerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public List<Deal> getAllDeals() {
        return new ArrayList<>(deals);
    }

    public void addDeal(Deal deal) {
        if (deal.getId() == null) {
            deal.setId("D" + dealIdCounter.getAndIncrement());
        }
        deals.add(deal);
    }

    /**
     * Atomic claim for Giveaway items (Requirement 4.1: Concurrency on Giveaway items).
     * Synchronized to guarantee first-come, first-served.
     */
    public synchronized boolean claimListing(String listingId, String buyerUsername) {
        Listing l = listings.get(listingId);
        if (l != null && l.getStatus() == ListingStatus.LIVE && l.getType() == ListingType.GIVEAWAY) {
            l.setStatus(ListingStatus.GIVEN);
            Deal d = new Deal();
            d.setId("D" + dealIdCounter.getAndIncrement());
            d.setListingId(l.getId());
            d.setListingTitle(l.getTitle());
            d.setBuyerUsername(buyerUsername);
            d.setSellerUsername(l.getSellerUsername());
            d.setType(DealType.GIVEAWAY);
            d.setStatus(DealStatus.COMPLETED);
            d.setClosedDate(LocalDateTime.now());
            deals.add(d);

            addNotification(new Notification(l.getSellerUsername(), "Item Claimed",
                    buyerUsername + " claimed your giveaway item: " + l.getTitle(), "Offer"));
            addNotification(new Notification(buyerUsername, "Claim Confirmed",
                    "You successfully claimed: " + l.getTitle(), "Offer"));
            return true;
        }
        return false;
    }

    // ========== Offer methods ==========
    public List<Offer> getOffersForListing(String listingId) {
        return offers.stream()
                .filter(o -> o.getListingId().equals(listingId))
                .collect(Collectors.toList());
    }

    public List<Offer> getOffersForBuyer(String username) {
        return offers.stream()
                .filter(o -> o.getBuyerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public List<Offer> getOffersForSeller(String username) {
        return offers.stream()
                .filter(o -> o.getSellerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public void addOffer(Offer offer) {
        if (offer.getId() == null) {
            offer.setId("O" + offerIdCounter.getAndIncrement());
        }
        offers.add(offer);
    }

    // ========== Report methods ==========
    public List<Report> getAllReports() {
        return new ArrayList<>(reports);
    }

    public void addReport(Report report) {
        if (report.getId() == null) {
            report.setId("REP" + reportIdCounter.getAndIncrement());
        }
        reports.add(report);
    }

    // ========== Chat methods ==========
    public List<ChatThread> getChatThreadsForUser(String username) {
        return chatThreads.stream()
                .filter(t -> t.getBuyerUsername().equalsIgnoreCase(username)
                        || t.getSellerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public synchronized ChatThread getOrCreateChatThread(String listingId, String buyerUsername, String sellerUsername) {
        for (ChatThread t : chatThreads) {
            if (t.getListingId().equals(listingId)
                    && t.getBuyerUsername().equalsIgnoreCase(buyerUsername)
                    && t.getSellerUsername().equalsIgnoreCase(sellerUsername)) {
                return t;
            }
        }
        ChatThread newThread = new ChatThread("T" + threadIdCounter.getAndIncrement(),
                listingId, buyerUsername, sellerUsername);
        Listing l = listings.get(listingId);
        if (l != null) {
            newThread.setListingTitle(l.getTitle());
        }
        chatThreads.add(newThread);
        return newThread;
    }

    // ========== Notification methods ==========
    public List<Notification> getNotificationsFor(String username) {
        return notifications.stream()
                .filter(n -> n.getUsername().equalsIgnoreCase(username))
                .sorted(Comparator.comparing(Notification::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public void addNotification(Notification n) {
        if (n.getId() == null) {
            n.setId("N" + notifIdCounter.getAndIncrement());
        }
        notifications.add(n);
    }

    // ========== Review methods ==========
    public List<Review> getReviewsForSeller(String username) {
        return reviews.stream()
                .filter(r -> r.getSellerUsername().equalsIgnoreCase(username) && !r.isHidden())
                .collect(Collectors.toList());
    }

    public List<Review> getAllReviews() {
        return new ArrayList<>(reviews);
    }

    public void addReview(Review r) {
        if (r.getId() == null) {
            r.setId("R" + reviewIdCounter.getAndIncrement());
        }
        reviews.add(r);
    }

    // Password strength helper (demo)
    public static String checkPasswordStrength(String password) {
        if (password == null || password.isEmpty()) return "Password cannot be empty.";
        if (password.length() < 8) return "Password must be at least 8 characters.";
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasUpper || !hasLower || !hasDigit) {
            return "Password should contain uppercase, lowercase and a digit.";
        }
        return "OK";
    }
}
