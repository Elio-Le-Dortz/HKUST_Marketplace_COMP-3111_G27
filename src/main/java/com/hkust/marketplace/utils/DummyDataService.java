package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.services.ActOnListingService;
import com.hkust.marketplace.services.AdminDealsService;
import com.hkust.marketplace.services.AdminProfileService;
import com.hkust.marketplace.services.BuyerProfileService;
import com.hkust.marketplace.services.ChatService;
import com.hkust.marketplace.services.DealHistoryService;
import com.hkust.marketplace.services.DealService;
import com.hkust.marketplace.services.ListingDetailService;
import com.hkust.marketplace.services.ListingFilterService;
import com.hkust.marketplace.services.ListingReviewService;
import com.hkust.marketplace.services.AdminNotificationService;
import com.hkust.marketplace.services.UserManagementService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final ListingFilterService listingFilterService = new ListingFilterService();
    private final ListingDetailService listingDetailService = new ListingDetailService(this);
    private final ActOnListingService actOnListingService = new ActOnListingService(this);
    private final ChatService chatService = new ChatService(this);
    private final DealService dealService = new DealService(this);
    private final DealHistoryService dealHistoryService = new DealHistoryService(this);
    private final BuyerProfileService buyerProfileService = new BuyerProfileService(this);
    private final com.hkust.marketplace.services.BuyerNotificationService buyerNotificationService = new com.hkust.marketplace.services.BuyerNotificationService(this);
    private final com.hkust.marketplace.services.BuyerReviewService buyerReviewService = new com.hkust.marketplace.services.BuyerReviewService(this);
    private final ListingReviewService listingReviewService = new ListingReviewService(this);
    private final UserManagementService userManagementService = new UserManagementService(this);
    private final AdminProfileService adminProfileService = new AdminProfileService(this);
    private final AdminDealsService adminDealsService = new AdminDealsService(this);
    private final AdminNotificationService adminNotificationService = new AdminNotificationService(this);

    private final AtomicInteger listingIdCounter = new AtomicInteger(100);
    private final AtomicInteger dealIdCounter = new AtomicInteger(500);
    private final AtomicInteger offerIdCounter = new AtomicInteger(700);
    private final AtomicInteger reportIdCounter = new AtomicInteger(300);
    private final AtomicInteger notifIdCounter = new AtomicInteger(1);
    private final AtomicInteger reviewIdCounter = new AtomicInteger(1);
    private final AtomicInteger threadIdCounter = new AtomicInteger(1);
    private final AtomicInteger messageIdCounter = new AtomicInteger(1);

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

    public static final String SEEDED_ADMIN_USERNAME = "admin";
    public static final String SEEDED_ADMIN_FULLNAME = "HKUST Marketplace Admin";
    public static final String SEEDED_ADMIN_PASSWORD = "admin123";

    private DummyDataService() {
        seedData();
    }

    public static DummyDataService getInstance() {
        return INSTANCE;
    }

    /**
     * Resets all in-memory collections and re-seeds original mock data.
     * Useful for isolating automated unit and integration tests.
     */
    public synchronized void resetData() {
        users.clear();
        listings.clear();
        deals.clear();
        offers.clear();
        reports.clear();
        chatThreads.clear();
        notifications.clear();
        reviews.clear();
        listingIdCounter.set(100);
        dealIdCounter.set(500);
        offerIdCounter.set(700);
        reportIdCounter.set(300);
        notifIdCounter.set(1);
        reviewIdCounter.set(1);
        threadIdCounter.set(1);
        messageIdCounter.set(1);
        seedData();
    }

    private static final String SEED_ADMIN_HASH = PasswordHasher.hash("admin123");
    private static final String SEED_USER_HASH = PasswordHasher.hash("password1");

    private void seedData() {
        // Admin
        seedAdmin();


        // Sample buyers
        Buyer buyer1 = new Buyer("alice", "Alice Chan", SEED_USER_HASH, "Hall 3");
        users.put("alice", buyer1);

        Buyer buyer2 = new Buyer("bob", "Bob Wong", SEED_USER_HASH, "Off-campus");
        users.put("bob", buyer2);

        // Sample sellers (passwords stored hashed, as for sellers who register)
        Seller seller1 = new Seller("charlie", "Charlie Lee", SEED_USER_HASH,
                "Final year CSE student selling textbooks and gadgets.");
        seller1.setPreferredMeetup("Library");
        seller1.setAverageRating(4.7);
        seller1.setRatingCount(12);
        users.put("charlie", seller1);

        Seller seller2 = new Seller("diana", "Diana Ng", SEED_USER_HASH,
                "Selling furniture and kitchen items before graduation.");
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

        Listing l6 = createListing("Engineering Notebook & Pen Set", "Brand new grid notebook and gel pen set.",
                "Stationery", ListingType.SELL, "New", 25.0, null, "Library", "charlie", "Charlie Lee");
        l6.setStatus(ListingStatus.LIVE);
        listings.put(l6.getId(), l6);

        Listing l7 = createListing("Yonex Badminton Racket", "Strung with BG65, light scratch on frame.",
                "Sports", ListingType.SELL, "Used", 120.0, null, "Sports Hall", "charlie", "Charlie Lee");
        l7.setStatus(ListingStatus.LIVE);
        listings.put(l7.getId(), l7);

        Listing l8 = createListing("HKUST Hoodie (Size M)", "Grey university hoodie in great condition.",
                "Clothing", ListingType.GIVEAWAY, "Like New", null, null, "UG Hall 1", "diana", "Diana Ng");
        l8.setStatus(ListingStatus.LIVE);
        listings.put(l8.getId(), l8);

        // Sample deals
        Deal d1 = new Deal();
        d1.setId("D" + dealIdCounter.getAndIncrement());
        d1.setListingId(l1.getId());
        d1.setListingTitle(l1.getTitle());
        d1.setBuyerUsername("alice");
        d1.setSellerUsername("charlie");
        d1.setDealType(ListingType.SELL);
        d1.setStatus(DealStatus.ACTIVE);
        d1.setOfferPrice(160.0);
        deals.add(d1);

        Deal d2 = new Deal();
        d2.setId("D" + dealIdCounter.getAndIncrement());
        d2.setListingId(l4.getId());
        d2.setListingTitle(l4.getTitle());
        d2.setBuyerUsername("alice");
        d2.setSellerUsername("diana");
        d2.setType(DealType.LOAN);
        d2.setStatus(DealStatus.ACTIVE);
        d2.setStartDate(LocalDateTime.now().minusDays(2));
        d2.setDueDate(LocalDateTime.now().plusDays(5));
        deals.add(d2);

        // Completed deal (Bought item)
        Deal d3 = new Deal();
        d3.setId("D" + dealIdCounter.getAndIncrement());
        d3.setListingId(l2.getId());
        d3.setListingTitle(l2.getTitle());
        d3.setBuyerUsername("alice");
        d3.setSellerUsername("charlie");
        d3.setType(DealType.SALE);
        d3.setStatus(DealStatus.COMPLETED);
        d3.setStartDate(LocalDateTime.now().minusDays(10));
        d3.setClosedDate(LocalDateTime.now().minusDays(9));
        d3.setOfferPrice(200.0);
        d3.setRated(true);
        deals.add(d3);

        // Returned deal (Borrowed item)
        Deal d4 = new Deal();
        d4.setId("D" + dealIdCounter.getAndIncrement());
        d4.setListingId(l5.getId());
        d4.setListingTitle(l5.getTitle());
        d4.setBuyerUsername("alice");
        d4.setSellerUsername("diana");
        d4.setType(DealType.LOAN);
        d4.setStatus(DealStatus.RETURNED);
        d4.setStartDate(LocalDateTime.now().minusDays(15));
        d4.setDueDate(LocalDateTime.now().minusDays(12));
        d4.setClosedDate(LocalDateTime.now().minusDays(12));
        d4.setRated(false);
        deals.add(d4);

        // Completed deal (Giveaway received)
        Deal d5 = new Deal();
        d5.setId("D" + dealIdCounter.getAndIncrement());
        d5.setListingId(l8.getId());
        d5.setListingTitle(l8.getTitle());
        d5.setBuyerUsername("alice");
        d5.setSellerUsername("diana");
        d5.setType(DealType.GIVEAWAY);
        d5.setStatus(DealStatus.COMPLETED);
        d5.setStartDate(LocalDateTime.now().minusDays(5));
        d5.setClosedDate(LocalDateTime.now().minusDays(5));
        d5.setOfferPrice(0.0);
        d5.setRated(false);
        deals.add(d5);

        // Sample offers
        Offer o1 = new Offer();
        o1.setId("O" + offerIdCounter.getAndIncrement());
        o1.setType(OfferType.PRICE_OFFER);
        o1.setListingId(l7.getId());
        o1.setListingTitle(l7.getTitle());
        o1.setBuyerUsername("alice");
        o1.setSellerUsername("charlie");
        o1.setPrice(100.0);
        o1.setStatus(OfferStatus.PENDING);
        o1.setCreatedDate(LocalDateTime.now().minusHours(6));
        offers.add(o1);

        Offer o2 = new Offer();
        o2.setId("O" + offerIdCounter.getAndIncrement());
        o2.setType(OfferType.PRICE_OFFER);
        o2.setListingId(l2.getId());
        o2.setListingTitle(l2.getTitle());
        o2.setBuyerUsername("alice");
        o2.setSellerUsername("charlie");
        o2.setPrice(200.0);
        o2.setStatus(OfferStatus.COUNTERED);
        o2.setCreatedDate(LocalDateTime.now().minusHours(3));
        Offer counterO2 = new Offer();
        counterO2.setId("O" + offerIdCounter.getAndIncrement());
        counterO2.setType(OfferType.PRICE_OFFER);
        counterO2.setListingId(l2.getId());
        counterO2.setListingTitle(l2.getTitle());
        counterO2.setBuyerUsername("alice");
        counterO2.setSellerUsername("charlie");
        counterO2.setPrice(220.0);
        counterO2.setStatus(OfferStatus.COUNTERED);
        counterO2.setCreatedDate(LocalDateTime.now().minusHours(2));
        o2.setCounterOffer(counterO2);
        offers.add(o2);

        Offer oAcceptedL1 = new Offer();
        oAcceptedL1.setId("O" + offerIdCounter.getAndIncrement());
        oAcceptedL1.setType(OfferType.PRICE_OFFER);
        oAcceptedL1.setListingId(l1.getId());
        oAcceptedL1.setListingTitle(l1.getTitle());
        oAcceptedL1.setBuyerUsername("alice");
        oAcceptedL1.setSellerUsername("charlie");
        oAcceptedL1.setPrice(160.0);
        oAcceptedL1.setStatus(OfferStatus.ACCEPTED);
        oAcceptedL1.setCreatedDate(LocalDateTime.now().minusHours(4));
        offers.add(oAcceptedL1);
        d1.setOfferId(oAcceptedL1.getId());

        Offer oAcceptedL2 = new Offer();
        oAcceptedL2.setId("O" + offerIdCounter.getAndIncrement());
        oAcceptedL2.setType(OfferType.PRICE_OFFER);
        oAcceptedL2.setListingId(l2.getId());
        oAcceptedL2.setListingTitle(l2.getTitle());
        oAcceptedL2.setBuyerUsername("alice");
        oAcceptedL2.setSellerUsername("charlie");
        oAcceptedL2.setPrice(200.0);
        oAcceptedL2.setStatus(OfferStatus.ACCEPTED);
        oAcceptedL2.setCreatedDate(d3.getStartDate());
        offers.add(oAcceptedL2);
        d3.setOfferId(oAcceptedL2.getId());

        // Sample chat thread (US-B06)
        ChatThread seedThread = new ChatThread("T" + threadIdCounter.getAndIncrement(),
                l1.getId(), "alice", "charlie");
        seedThread.setListingTitle(l1.getTitle());
        seedThread.setCreatedDate(LocalDateTime.now().minusDays(1));
        Message m1 = new Message("M" + messageIdCounter.getAndIncrement(), "alice",
                "Hi Charlie, is the Calculus textbook still available?", LocalDateTime.now().minusHours(5));
        Message m2 = new Message("M" + messageIdCounter.getAndIncrement(), "charlie",
                "Hi Alice! Yes, it's available and in great condition.", LocalDateTime.now().minusHours(4));
        Message m3 = new Message("M" + messageIdCounter.getAndIncrement(), "alice",
                "[FORMAL OFFER] Proposed Price: HK$ 160.00", LocalDateTime.now().minusHours(3), d1.getId());
        Message m4 = new Message("M" + messageIdCounter.getAndIncrement(), "charlie",
                "Offer accepted! Let's arrange meetup at the Library.", LocalDateTime.now().minusHours(2));
        seedThread.addMessage(m1);
        seedThread.addMessage(m2);
        seedThread.addMessage(m3);
        seedThread.addMessage(m4);
        chatThreads.add(seedThread);

        // Sample reviews
        Review r1 = new Review();
        r1.setId("R" + reviewIdCounter.getAndIncrement());
        r1.setSellerUsername("charlie");
        r1.setBuyerUsername("bob");
        r1.setListingTitle("Old Chemistry Book");
        r1.setRating(5);
        r1.setComment("Very smooth transaction, book in excellent condition!");
        r1.setFlagged(true);
        r1.setFlagReason("Buyer requested off-platform payment method during meetup.");
        reviews.add(r1);

        Review r2 = new Review();
        r2.setId("R" + reviewIdCounter.getAndIncrement());
        r2.setSellerUsername("charlie");
        r2.setBuyerUsername("alice");
        r2.setListingTitle(l1.getTitle());
        r2.setRating(5);
        r2.setComment("Great seller, very responsive!");
        r2.setReply("Thanks Alice, enjoy the book!");
        reviews.add(r2);


        Report rep1 = new Report("REP" + reportIdCounter.getAndIncrement(), l2.getId(), l2.getTitle(), "alice",
                "Item description says genuine Apple charger, but serial number is missing.");
        rep1.setDate(LocalDateTime.now().minusHours(4));
        rep1.setStatus(ReportStatus.OPEN);
        reports.add(rep1);

        // Sample notifications covering all categories in specs_buyer.tex (US-B09)
        Notification n1 = new Notification("alice", "Offer Accepted",
                "Charlie accepted your offer of HK$160 for Calculus Textbook.", "Offers", o1.getId(), "OFFER");
        n1.setId("N" + notifIdCounter.getAndIncrement());
        n1.setRead(true);
        n1.setTimestamp(LocalDateTime.now().minusHours(3));
        notifications.add(n1);

        Notification n2 = new Notification("alice", "Counter-Offer Received",
                "Charlie countered your offer for Engineering Physics to HK$220.", "Offers", o2.getId(), "OFFER");
        n2.setId("N" + notifIdCounter.getAndIncrement());
        n2.setRead(false);
        n2.setTimestamp(LocalDateTime.now().minusHours(2));
        notifications.add(n2);

        Notification n3 = new Notification("alice", "New Message from Charlie",
                "Charlie: 'Offer accepted! Let's arrange meetup at the Library.'", "Chat", seedThread.getId(), "CHAT_THREAD");
        n3.setId("N" + notifIdCounter.getAndIncrement());
        n3.setRead(false);
        n3.setTimestamp(LocalDateTime.now().minusHours(1));
        notifications.add(n3);

        Notification n4 = new Notification("alice", "Borrow Due-Date Reminder",
                "Borrowed item 'Scientific Calculator' is due on " + d2.getDueDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")) + ". Please prepare to return it.", "Borrow Reminders", d2.getId(), "DEAL");
        n4.setId("N" + notifIdCounter.getAndIncrement());
        n4.setRead(false);
        n4.setTimestamp(LocalDateTime.now().minusMinutes(45));
        notifications.add(n4);

        Notification n5 = new Notification("alice", "Listing Removed by Admin",
                "A listing you had an open offer on ('Vintage Mechanical Keyboard') was removed by the administrator.", "Listings", "L999", "LISTING");
        n5.setId("N" + notifIdCounter.getAndIncrement());
        n5.setRead(true);
        n5.setTimestamp(LocalDateTime.now().minusHours(5));
        notifications.add(n5);

        Notification n6 = new Notification("alice", "Seller Replied to Your Review",
                "Charlie replied to your review on 'Calculus Textbook': \"Thanks Alice, enjoy the book!\"", "Reviews", r2.getId(), "REVIEW");
        n6.setId("N" + notifIdCounter.getAndIncrement());
        n6.setRead(false);
        n6.setTimestamp(LocalDateTime.now().minusMinutes(20));
        notifications.add(n6);

        Notification n7 = new Notification("alice", "Welcome to HKUST Marketplace",
                "Welcome! You can browse listings, negotiate offers, chat with sellers, and borrow items.", "Announcements", null, "NONE");
        n7.setId("N" + notifIdCounter.getAndIncrement());
        n7.setRead(true);
        n7.setTimestamp(LocalDateTime.now().minusDays(1));
        notifications.add(n7);

        Notification nCharlie = new Notification("charlie", "New Offer Received",
                "Alice offered HK$160 for your Calculus Textbook.", "Offers");
        nCharlie.setId("N" + notifIdCounter.getAndIncrement());
        nCharlie.setTimestamp(LocalDateTime.now().minusHours(3));
        notifications.add(nCharlie);

        Notification nDiana = new Notification("diana", "Listing Pending",
                "Your Mini Fridge listing is awaiting admin review.", "Announcements");
        nDiana.setId("N" + notifIdCounter.getAndIncrement());
        nDiana.setTimestamp(LocalDateTime.now().minusHours(2));
        notifications.add(nDiana);

        Notification nAdmin = new Notification("admin", "New Listing Submitted",
                "Diana submitted 'Mini Fridge' for review.", "Listing Submissions", l5.getId(), "LISTING");
        nAdmin.setId("N" + notifIdCounter.getAndIncrement());
        nAdmin.setTimestamp(LocalDateTime.now().minusHours(1));
        notifications.add(nAdmin);

        Notification nAdminReport = new Notification("admin", "Report Filed: " + l2.getTitle(),
                "Buyer @alice reported listing '" + l2.getTitle() + "': Suspected counterfeit goods.",
                "Reports", rep1.getId(), "REPORT");
        nAdminReport.setId("N" + notifIdCounter.getAndIncrement());
        nAdminReport.setTimestamp(LocalDateTime.now().minusHours(4));
        nAdminReport.setRead(false);
        notifications.add(nAdminReport);

        Notification nAdminFlag = new Notification("admin", "Flagged Review for @charlie",
                "Seller @charlie flagged review " + r1.getId() + " by @bob: 'Buyer requested off-platform payment method.'",
                "Flagged Reviews", r1.getId(), "REVIEW");
        nAdminFlag.setId("N" + notifIdCounter.getAndIncrement());
        nAdminFlag.setTimestamp(LocalDateTime.now().minusHours(8));
        nAdminFlag.setRead(false);
        notifications.add(nAdminFlag);

        Notification nAdminAudit = new Notification("admin", "User Account Modified",
                "Admin @admin updated profile details for buyer @alice.",
                "User Accounts", "alice", "USER");
        nAdminAudit.setId("N" + notifIdCounter.getAndIncrement());
        nAdminAudit.setTimestamp(LocalDateTime.now().minusDays(1));
        nAdminAudit.setRead(true);
        notifications.add(nAdminAudit);

        Notification nAdminAnn = new Notification("admin", "Platform Update v2.0",
                "Marketplace system upgrade completed. Audit logging and review queues are active.",
                "Announcements", null, "ANNOUNCEMENT");
        nAdminAnn.setId("N" + notifIdCounter.getAndIncrement());
        nAdminAnn.setTimestamp(LocalDateTime.now().minusDays(2));
        nAdminAnn.setRead(true);
        notifications.add(nAdminAnn);
    }

    /**
     * Call inside the DummyDataService constructor / seed initialization method.
     */
    private void seedAdmin() {
        users.values().removeIf(u -> u.getRole() == UserRole.ADMIN);
        Admin seededAdmin = new Admin(
                SEEDED_ADMIN_USERNAME,
                SEEDED_ADMIN_FULLNAME,
                SEEDED_ADMIN_PASSWORD
        );
        seededAdmin.setActive(true);
        users.put(SEEDED_ADMIN_USERNAME, seededAdmin);
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
     * @param username the username to look up
     * @return {@code true} if the username is taken, {@code false} otherwise
     */
    public synchronized boolean usernameExists(String username) {
        if (username == null) {

            return false;
        }
        return users.containsKey(username.trim().toLowerCase());
    }

    public User findUser(String username) {
        if (username == null) {
            return null;
        }
        return users.get(username.trim().toLowerCase());
    }

    public void registerUser(User user) {

        if (user == null || user.getUsername() == null) {
            throw new IllegalArgumentException("User and username cannot be null.");
        }
        if (user.getRole() == UserRole.ADMIN || SEEDED_ADMIN_USERNAME.equalsIgnoreCase(user.getUsername().trim())) {
            throw new IllegalArgumentException("Admin accounts cannot be registered.");
        }
        String key = user.getUsername().trim().toLowerCase();
        if (users.containsKey(key)) {
            throw new IllegalArgumentException("Username already exists.");
        }
        users.put(key, user);

    }

    /**
     * Registers a new Buyer account with validation and password hashing (US-B01).
     *
     * @param username        Desired username (case-insensitive uniqueness)
     * @param fullName        Full name of the buyer (required)
     * @param password        Plaintext password meeting strength criteria
     * @param confirmPassword Confirmation password that must match
     * @param residence       Optional residence hall or off-campus location
     * @return RegistrationResult indicating success or failure with affected fields
     */
    public synchronized RegistrationResult registerBuyer(String username, String fullName,
                                                        String password, String confirmPassword,
                                                        String residence) {
        Set<String> emptyFields = new LinkedHashSet<>();
        if (username == null || username.trim().isEmpty()) {
            emptyFields.add("username");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            emptyFields.add("fullName");
        }
        if (password == null || password.isEmpty()) {
            emptyFields.add("password");
        }
        if (confirmPassword == null || confirmPassword.isEmpty()) {
            emptyFields.add("confirmPassword");
        }

        if (!emptyFields.isEmpty()) {
            String message;
            if (emptyFields.size() == 1) {
                if (emptyFields.contains("username")) {
                    message = "Username is required.";
                } else if (emptyFields.contains("fullName")) {
                    message = "Full Name cannot be empty.";
                } else if (emptyFields.contains("password")) {
                    message = "Password cannot be empty.";
                } else {
                    message = "Please confirm your password.";
                }
            } else {
                message = "Please fill in all required fields.";
            }
            return RegistrationResult.failure(message, emptyFields);
        }

        String cleanUsername = username.trim();
        if (cleanUsername.contains(" ")) {
            return RegistrationResult.failure("Username cannot contain spaces.", "username");
        }
        if (cleanUsername.length() < 3) {
            return RegistrationResult.failure("Username must be at least 3 characters long.", "username");
        }

        if (usernameExists(cleanUsername)) {
            return RegistrationResult.failure("Username already taken. Please choose another.", "username");
        }

        String pwdCheck = checkPasswordStrength(password);
        if (!"OK".equals(pwdCheck)) {
            return RegistrationResult.failure(pwdCheck, "password");
        }

        if (!password.equals(confirmPassword)) {
            return RegistrationResult.failure("Passwords do not match.", "confirmPassword");
        }

        String hashedPassword = PasswordUtil.hashPassword(password);
        String cleanResidence = (residence != null && !residence.trim().isEmpty()) ? residence.trim() : null;
        Buyer buyer = new Buyer(cleanUsername.toLowerCase(), fullName.trim(), hashedPassword, cleanResidence);
        buyer.setAccountStatus(AccountStatus.ACTIVE);
        registerUser(buyer);

        return RegistrationResult.success(buyer);
    }

    /**
     * Authenticates a Buyer with username and password, enforcing credentials, role,
     * and active account status (US-B02).
     *
     * <p>Follows the formal specification of use case 1.2:</p>
     * <ul>
     *   <li>Flow A1: If username or password is empty, informs that both fields are required.</li>
     *   <li>Flow A2: If username does not exist or password is incorrect, informs that the
     *       credentials are incorrect without revealing which one.</li>
     *   <li>Flow A3: If credentials match a Seller or Admin account, informs that this account
     *       cannot log in to the Buyer Portal and must use its own portal.</li>
     *   <li>Flow A4: If the Buyer account is deactivated or deleted, informs that the account
     *       is deactivated.</li>
     *   <li>Basic Flow: Returns a successful {@link LoginResult} wrapping the {@link Buyer}.</li>
     * </ul>
     *
     * @param username Candidate username (trimmed, case-insensitive)
     * @param password Candidate plaintext password (not trimmed)
     * @return LoginResult indicating success or specific failure reason and affected fields
     */
    public synchronized LoginResult loginBuyer(String username, String password) {
        boolean emptyUsername = (username == null || username.trim().isEmpty());
        boolean emptyPassword = (password == null || password.isEmpty());

        if (emptyUsername && emptyPassword) {
            return LoginResult.failure(LoginResult.MSG_EMPTY_CREDENTIALS,
                    Set.of("username", "password"), LoginErrorType.EMPTY_FIELDS);
        }
        if (emptyUsername) {
            return LoginResult.failure(LoginResult.MSG_USERNAME_REQUIRED,
                    Set.of("username"), LoginErrorType.EMPTY_FIELDS);
        }
        if (emptyPassword) {
            return LoginResult.failure(LoginResult.MSG_PASSWORD_REQUIRED,
                    Set.of("password"), LoginErrorType.EMPTY_FIELDS);
        }

        String cleanUsername = username.trim().toLowerCase();
        User user = users.get(cleanUsername);

        // Alternative Flow A2: username does not exist or password is incorrect
        if (user == null || !PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
            return LoginResult.failure(LoginResult.MSG_INVALID_CREDENTIALS,
                    Set.of("username", "password"), LoginErrorType.INVALID_CREDENTIALS);
        }

        // Alternative Flow A3: wrong account type (Seller or Admin)
        if (user.getRole() != UserRole.BUYER) {
            return LoginResult.failure(LoginResult.MSG_WRONG_ROLE_BUYER,
                    Set.of("username"), LoginErrorType.WRONG_ROLE);
        }

        // Alternative Flow A4: account deactivated or deleted
        if (!user.isActive()) {
            if (user.getAccountStatus() == AccountStatus.DELETED) {
                return LoginResult.failure(LoginResult.MSG_ACCOUNT_DELETED,
                        Set.of("username"), LoginErrorType.ACCOUNT_DEACTIVATED);
            }
            return LoginResult.failure(LoginResult.MSG_ACCOUNT_DEACTIVATED,
                    Set.of("username"), LoginErrorType.ACCOUNT_DEACTIVATED);
        }

        return LoginResult.success(user);
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

    public void resetToSeedData() {
        users.clear();
        listings.clear();
        deals.clear();
        offers.clear();
        reports.clear();
        chatThreads.clear();
        notifications.clear();
        reviews.clear();
        listingIdCounter.set(100);
        dealIdCounter.set(500);
        offerIdCounter.set(700);
        reportIdCounter.set(300);
        notifIdCounter.set(1);
        reviewIdCounter.set(1);
        threadIdCounter.set(1);
        seedData();
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
    public synchronized Collection<Listing> getAllListings() {
        return new ArrayList<>(listings.values());
    }

    public synchronized List<Listing> getLiveListings() {
        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.LIVE)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all live listings filtered and sorted according to the specified criteria (US-B03).
     * Thread-safe and atomic with respect to in-memory store mutations.
     *
     * @param criteria the filter and sort criteria (keyword, category, type, condition, price, sort order)
     * @return filtered and sorted list of live listings
     */
    public synchronized List<Listing> getFilteredLiveListings(ListingFilterCriteria criteria) {
        return listingFilterService.filterAndSort(new ArrayList<>(listings.values()), criteria);
    }

    public ListingFilterService getListingFilterService() {
        return listingFilterService;
    }

    public ListingDetailService getListingDetailService() {
        return listingDetailService;
    }

    public ActOnListingService getActOnListingService() {
        return actOnListingService;
    }

    public ChatService getChatService() {
        return chatService;
    }

    public BuyerProfileService getBuyerProfileService() {
        return buyerProfileService;
    }

    public com.hkust.marketplace.services.BuyerNotificationService getBuyerNotificationService() {
        return buyerNotificationService;
    }

    /**
     * Updates buyer profile information (full name, residence hall, password)
     * enforcing validation and password policies (US-B08).
     *
     * @param username username of the buyer
     * @param request  profile details and optional password update
     * @return ProfileUpdateResult indicating success or failure
     */
    public synchronized ProfileUpdateResult updateBuyerProfile(String username, ProfileUpdateRequest request) {
        return buyerProfileService.updateProfile(username, request);
    }

    /**
     * Retrieves the profile of an active buyer (US-B08).
     *
     * @param username username of the buyer
     * @return Buyer entity if found and active, null otherwise
     */
    public synchronized Buyer getBuyerProfile(String username) {
        return buyerProfileService.getProfile(username);
    }

    public AdminDealsService getAdminDealsService() {
        return adminDealsService;
    }

    public AdminProfileService getAdminProfileService() {
        return adminProfileService;
    }

    /**
     * Updates admin profile information (full name, password)
     * enforcing validation and password policies (US-A04, Task 3.4).
     *
     * @param username username of the admin
     * @param request  profile details and optional password update
     * @return AdminProfileUpdateResult indicating success or failure
     */
    public synchronized AdminProfileUpdateResult updateAdminProfile(String username, AdminProfileUpdateRequest request) {
        return adminProfileService.updateProfile(username, request);
    }

    /**
     * Retrieves the profile of an active admin (US-A04, Task 3.4).
     *
     * @param username username of the admin
     * @return Admin entity if found and active, null otherwise
     */
    public synchronized Admin getAdminProfile(String username) {
        return adminProfileService.getProfile(username);
    }

    public int nextMessageId() {
        return messageIdCounter.getAndIncrement();
    }

    public int nextThreadId() {
        return threadIdCounter.getAndIncrement();
    }

    public synchronized ListingViewResult viewListing(String listingId, boolean requireLive) {
        return listingDetailService.viewListing(listingId, requireLive);
    }

    public synchronized SellerRatingInfo getSellerRatingInfo(String sellerUsername) {
        return listingDetailService.getSellerRatingInfo(sellerUsername);
    }

    public synchronized ReportResult reportListing(String listingId, String buyerUsername, String reason) {
        return listingDetailService.submitReport(listingId, buyerUsername, reason);
    }

    public synchronized ActOnListingResult submitPriceOffer(String listingId, String buyerUsername, Double price) {
        return actOnListingService.submitPriceOffer(listingId, buyerUsername, price);
    }

    public synchronized ActOnListingResult submitBorrowRequest(String listingId, String buyerUsername,
                                                               LocalDateTime startDate, LocalDateTime endDate) {
        return actOnListingService.submitBorrowRequest(listingId, buyerUsername, startDate, endDate);
    }

    public synchronized ActOnListingResult submitClaim(String listingId, String buyerUsername) {
        return actOnListingService.submitClaim(listingId, buyerUsername);
    }

    public synchronized boolean isGiveawayClaimed(String listingId) {
        return actOnListingService.isGiveawayClaimed(listingId);
    }

    public synchronized List<Listing> getPendingListings() {
        return listingReviewService.getPendingListingsOldestFirst();
    }

    public ListingReviewService getListingReviewService() {
        return listingReviewService;
    }

    public UserManagementService getUserManagementService() {
        return userManagementService;
    }

    public synchronized ListingReviewResult approveListing(String listingId, String adminUsername) {
        return listingReviewService.approveListing(listingId, adminUsername);
    }

    public synchronized ListingReviewResult rejectListing(String listingId, String adminUsername, String reason) {
        return listingReviewService.rejectListing(listingId, adminUsername, reason);
    }

    public synchronized List<Listing> getListingsBySeller(String username) {
        return listings.values().stream()
                .filter(l -> l.getSellerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public synchronized Listing findListing(String id) {
        return listings.get(id);
    }

    public synchronized Listing getListing(String id) {
        return findListing(id);
    }

    public synchronized void addListing(Listing listing) {
        if (listing.getId() == null) {
            listing.setId("L" + listingIdCounter.getAndIncrement());
        }
        listings.put(listing.getId(), listing);
    }

    public synchronized void updateListing(Listing listing) {
        listings.put(listing.getId(), listing);
    }

    public synchronized void removeListing(String id) {
        listings.remove(id);
    }

    // ========== Deal methods ==========
    public synchronized List<Deal> getDealsForBuyer(String username) {
        return deals.stream()
                .filter(d -> d.getBuyerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public synchronized List<Deal> getAllDeals() {
        return new ArrayList<>(deals);
    }

    public synchronized Deal findDeal(String id) {
        if (id == null) return null;
        return deals.stream()
                .filter(d -> id.equalsIgnoreCase(d.getId()))
                .findFirst()
                .orElse(null);
    }

    public synchronized void addDeal(Deal deal) {
        if (deal.getId() == null) {
            deal.setId("D" + dealIdCounter.getAndIncrement());
        }
        deals.add(deal);
    }

    public synchronized boolean removeDeal(String id) {
        if (id == null) return false;
        return deals.removeIf(d -> id.equalsIgnoreCase(d.getId()));
    }

    public synchronized void clearDeals() {
        deals.clear();
    }

    public synchronized int processAutoReturns() {
        return dealService != null ? dealService.processAutoReturns() : 0;
    }

    public synchronized List<MyDealsItem> getMyDealsForBuyer(String buyerUsername) {
        return dealService != null ? dealService.getMyDealsForBuyer(buyerUsername) : new ArrayList<>();
    }

    public synchronized DealOperationResult withdrawOffer(String offerId, String buyerUsername) {
        return dealService.withdrawOffer(offerId, buyerUsername);
    }

    public synchronized DealOperationResult acceptCounterOffer(String offerId, String buyerUsername) {
        return dealService.acceptCounterOffer(offerId, buyerUsername);
    }

    public synchronized DealOperationResult declineCounterOffer(String offerId, String buyerUsername) {
        return dealService.declineCounterOffer(offerId, buyerUsername);
    }

    public synchronized DealOperationResult selfReturnItem(String dealId, String buyerUsername) {
        return dealService.selfReturnItem(dealId, buyerUsername);
    }

    public synchronized DealOperationResult markDealComplete(String dealId, String buyerUsername) {
        return dealService.markDealComplete(dealId, buyerUsername);
    }

    public synchronized DealOperationResult submitRating(String dealId, String buyerUsername, int rating, String comment) {
        return dealService.submitRating(dealId, buyerUsername, rating, comment);
    }

    public DealService getDealService() {
        return dealService;
    }

    public DealHistoryService getDealHistoryService() {
        return dealHistoryService;
    }

    public synchronized List<DealHistoryItem> getDealHistory(String buyerUsername, DealHistoryFilterCriteria criteria) {
        return dealHistoryService != null ? dealHistoryService.getDealHistory(buyerUsername, criteria) : new ArrayList<>();
    }

    public synchronized boolean hasCompletedDeals(String buyerUsername) {
        return dealHistoryService != null && dealHistoryService.hasCompletedDeals(buyerUsername);
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
    public synchronized List<Offer> getAllOffers() {
        return new ArrayList<>(offers);
    }

    public synchronized List<Offer> getOffersForListing(String listingId) {
        return offers.stream()
                .filter(o -> o.getListingId().equals(listingId))
                .collect(Collectors.toList());
    }

    public synchronized List<Offer> getOffersForBuyer(String username) {
        return offers.stream()
                .filter(o -> o.getBuyerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public synchronized List<Offer> getOffersForSeller(String username) {
        return offers.stream()
                .filter(o -> o.getSellerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public synchronized void addOffer(Offer offer) {
        if (offer.getId() == null) {
            offer.setId("O" + offerIdCounter.getAndIncrement());
        }
        offers.add(offer);
    }

    public synchronized Offer findOffer(String id) {
        if (id == null) return null;
        return offers.stream()
                .filter(o -> id.equalsIgnoreCase(o.getId()))
                .findFirst()
                .orElse(null);
    }

    // ========== Report methods ==========
    public synchronized List<Report> getAllReports() {
        return new ArrayList<>(reports);
    }

    public synchronized void addReport(Report report) {
        if (report.getId() == null) {
            report.setId("REP" + reportIdCounter.getAndIncrement());
        }
        reports.add(report);
    }

    public synchronized Report findReport(String reportId) {
        if (reportId == null || reportId.trim().isEmpty()) {
            return null;
        }
        String clean = reportId.trim();
        for (Report r : reports) {
            if (r != null && clean.equalsIgnoreCase(r.getId())) {
                return r;
            }
        }
        return null;
    }

    // ========== Chat methods ==========
    public synchronized List<ChatThread> getAllChatThreads() {
        return new ArrayList<>(chatThreads);
    }

    public synchronized ChatThread findChatThread(String threadId) {
        if (threadId == null) return null;
        for (ChatThread t : chatThreads) {
            if (t.getId() != null && t.getId().equalsIgnoreCase(threadId.trim())) {
                return t;
            }
        }
        return null;
    }

    public synchronized void addChatThread(ChatThread thread) {
        if (thread != null) {
            if (thread.getId() == null) {
                thread.setId("T" + threadIdCounter.getAndIncrement());
            }
            chatThreads.add(thread);
        }
    }

    public synchronized List<ChatThread> getChatThreadsForUser(String username) {
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
    public synchronized List<Notification> getAllNotifications() {
        return new ArrayList<>(notifications);
    }

    public synchronized List<Notification> getNotificationsFor(String username) {
        return notifications.stream()
                .filter(n -> n.getUsername().equalsIgnoreCase(username))
                .sorted((n1, n2) -> {
                    if (n1.getTimestamp() == null && n2.getTimestamp() == null) return 0;
                    if (n1.getTimestamp() == null) return 1;
                    if (n2.getTimestamp() == null) return -1;
                    int c = n2.getTimestamp().compareTo(n1.getTimestamp());
                    if (c != 0) return c;
                    return (n2.getId() != null && n1.getId() != null) ? n2.getId().compareTo(n1.getId()) : 0;
                })
                .collect(Collectors.toList());
    }

    public synchronized void addNotification(Notification n) {
        if (n.getId() == null) {
            n.setId("N" + notifIdCounter.getAndIncrement());
        }
        notifications.add(n);
    }

    public synchronized boolean removeNotification(String id) {
        if (id == null) return false;
        return notifications.removeIf(n -> id.equalsIgnoreCase(n.getId()));
    }

    public synchronized int removeNotificationsFor(String username) {
        if (username == null) return 0;
        int initial = notifications.size();
        notifications.removeIf(n -> username.equalsIgnoreCase(n.getUsername()));
        return initial - notifications.size();
    }

    // ========== Review methods (US-B11 / Task 1.10) ==========
    public com.hkust.marketplace.services.BuyerReviewService getBuyerReviewService() {
        return buyerReviewService;
    }

    public synchronized List<Review> getReviewsForSeller(String username) {
        return reviews.stream()
                .filter(r -> r.getSellerUsername().equalsIgnoreCase(username) && !r.isHidden())
                .collect(Collectors.toList());
    }

    public synchronized List<Review> getAllReviews() {
        return new ArrayList<>(reviews);
    }

    public synchronized List<Review> getReviewsByBuyer(String buyerUsername) {
        if (buyerUsername == null || buyerUsername.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return reviews.stream()
                .filter(r -> r != null && buyerUsername.trim().equalsIgnoreCase(r.getBuyerUsername()))
                .collect(Collectors.toList());
    }

    public synchronized java.util.Optional<Review> getReviewForDeal(String dealId) {
        if (dealId == null || dealId.trim().isEmpty()) {
            return java.util.Optional.empty();
        }
        return reviews.stream()
                .filter(r -> r != null && dealId.trim().equalsIgnoreCase(r.getDealId()))
                .findFirst();
    }

    public synchronized void addReview(Review r) {
        if (r.getId() == null) {
            r.setId("R" + reviewIdCounter.getAndIncrement());
        }
        reviews.add(r);
        if (r.getSellerUsername() != null && !r.getSellerUsername().trim().isEmpty()) {
            recalculateSellerRating(r.getSellerUsername());
        }
    }

    /**
     * Atomically recalculates a seller's average rating and review count from all valid,
     * non-hidden reviews and updates the {@link Seller} user instance (US-B11 Step 6).
     *
     * @param sellerUsername the seller's username
     * @return an updated {@link SellerRatingInfo}
     */
    public synchronized SellerRatingInfo recalculateSellerRating(String sellerUsername) {
        if (sellerUsername == null || sellerUsername.trim().isEmpty()) {
            return SellerRatingInfo.noRatings("", "Unknown Seller");
        }

        String username = sellerUsername.trim();
        User user = findUser(username);
        String fullName = (user != null && user.getFullName() != null) ? user.getFullName() : username;

        List<Review> validReviews = reviews.stream()
                .filter(r -> r != null && username.equalsIgnoreCase(r.getSellerUsername())
                        && !r.isHidden() && r.getRating() >= 1 && r.getRating() <= 5)
                .collect(Collectors.toList());

        if (!validReviews.isEmpty()) {
            int count = validReviews.size();
            double sum = validReviews.stream().mapToInt(Review::getRating).sum();
            double average = Math.round((sum / (double) count) * 10.0) / 10.0;
            if (user instanceof Seller seller) {
                seller.setAverageRating(average);
                seller.setRatingCount(count);
            }
            return SellerRatingInfo.withRatings(username, fullName, average, count);
        }

        // Fallback to seller entity seeded rating if no individual reviews exist
        if (user instanceof Seller seller && seller.getRatingCount() > 0) {
            double avg = Math.round(seller.getAverageRating() * 10.0) / 10.0;
            return SellerRatingInfo.withRatings(username, fullName, avg, seller.getRatingCount());
        }

        if (user instanceof Seller seller) {
            seller.setAverageRating(0.0);
            seller.setRatingCount(0);
        }
        return SellerRatingInfo.noRatings(username, fullName);
    }

    public synchronized Review findReview(String reviewId) {
        if (reviewId == null || reviewId.trim().isEmpty()) {
            return null;
        }
        String clean = reviewId.trim();
        for (Review r : reviews) {
            if (r != null && clean.equalsIgnoreCase(r.getId())) {
                return r;
            }
        }
        return null;
    }

    public AdminNotificationService getAdminNotificationService() {
        return adminNotificationService;
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
