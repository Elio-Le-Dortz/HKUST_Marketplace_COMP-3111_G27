package com.hkust.marketplace.database;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.PasswordHasher;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SQLite Database Manager for HKUST Marketplace.
 * Provides persistent relational storage for users, listings, offers, deals,
 * reviews, notifications, reports, and chat history.
 */
public class DatabaseManager {

    public static final String DEFAULT_DB_DIR = "data";
    public static final String DEFAULT_DB_FILE = "data/marketplace.db";
    public static final String DEFAULT_JDBC_URL = "jdbc:sqlite:" + DEFAULT_DB_FILE;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Throwable ignored) {
        }
    }

    private final String jdbcUrl;
    private Connection memoryKeepAlive;

    public DatabaseManager() {
        this(DEFAULT_JDBC_URL);
    }

    public DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        ensureDirectoryExists();
        if (isInMemory()) {
            try {
                this.memoryKeepAlive = DriverManager.getConnection(jdbcUrl);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize memory connection: " + e.getMessage(), e);
            }
        }
        initSchema();
    }

    public boolean isInMemory() {
        return jdbcUrl != null && (jdbcUrl.contains(":memory:") || jdbcUrl.contains("mode=memory"));
    }

    public void close() {
        if (memoryKeepAlive != null) {
            try {
                memoryKeepAlive.close();
            } catch (SQLException ignored) {
            }
            memoryKeepAlive = null;
        }
    }

    private void ensureDirectoryExists() {
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlite:") && !isInMemory()) {
            String path = jdbcUrl.substring("jdbc:sqlite:".length());
            File dbFile = new File(path);
            File parent = dbFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        }
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            if (!isInMemory()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }
        }
        return conn;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Creates relational tables if they do not exist.
     */
    public synchronized void initSchema() {
        String[] ddl = {
            // Users table
            "CREATE TABLE IF NOT EXISTS users (" +
                "username TEXT PRIMARY KEY COLLATE NOCASE, " +
                "full_name TEXT, " +
                "password_hash TEXT, " +
                "role TEXT, " +
                "account_status TEXT, " +
                "residence_hall TEXT, " +
                "bio TEXT, " +
                "preferred_meetup TEXT, " +
                "average_rating REAL DEFAULT 0.0, " +
                "rating_count INTEGER DEFAULT 0, " +
                "created_at TEXT" +
            ");",

            // Listings table
            "CREATE TABLE IF NOT EXISTS listings (" +
                "id TEXT PRIMARY KEY, " +
                "title TEXT, " +
                "description TEXT, " +
                "category TEXT, " +
                "type TEXT, " +
                "status TEXT, " +
                "condition TEXT, " +
                "price REAL, " +
                "lending_duration_days INTEGER, " +
                "meetup_location TEXT, " +
                "seller_username TEXT, " +
                "seller_fullname TEXT, " +
                "view_count INTEGER DEFAULT 0, " +
                "admin_reason TEXT, " +
                "deleted_by_seller INTEGER DEFAULT 0, " +
                "posted_at TEXT" +
            ");",

            // Listing photos table
            "CREATE TABLE IF NOT EXISTS listing_photos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "listing_id TEXT, " +
                "photo_path TEXT" +
            ");",

            // Offers table
            "CREATE TABLE IF NOT EXISTS offers (" +
                "id TEXT PRIMARY KEY, " +
                "type TEXT, " +
                "listing_id TEXT, " +
                "listing_title TEXT, " +
                "buyer_username TEXT, " +
                "seller_username TEXT, " +
                "price REAL, " +
                "start_date TEXT, " +
                "end_date TEXT, " +
                "status TEXT, " +
                "decline_reason TEXT, " +
                "counter_offer_id TEXT, " +
                "created_date TEXT" +
            ");",

            // Deals table
            "CREATE TABLE IF NOT EXISTS deals (" +
                "id TEXT PRIMARY KEY, " +
                "listing_id TEXT, " +
                "listing_title TEXT, " +
                "buyer_username TEXT, " +
                "seller_username TEXT, " +
                "deal_type TEXT, " +
                "status TEXT, " +
                "start_date TEXT, " +
                "due_date TEXT, " +
                "closed_date TEXT, " +
                "offer_id TEXT, " +
                "offer_price REAL, " +
                "rated INTEGER DEFAULT 0" +
            ");",

            // Reviews table
            "CREATE TABLE IF NOT EXISTS reviews (" +
                "id TEXT PRIMARY KEY, " +
                "seller_username TEXT, " +
                "buyer_username TEXT, " +
                "listing_id TEXT, " +
                "listing_title TEXT, " +
                "deal_id TEXT, " +
                "rating INTEGER, " +
                "comment TEXT, " +
                "reply TEXT, " +
                "flagged INTEGER DEFAULT 0, " +
                "flag_reason TEXT, " +
                "hidden INTEGER DEFAULT 0, " +
                "created_date TEXT" +
            ");",

            // Notifications table
            "CREATE TABLE IF NOT EXISTS notifications (" +
                "id TEXT PRIMARY KEY, " +
                "username TEXT, " +
                "title TEXT, " +
                "message TEXT, " +
                "category TEXT, " +
                "related_item_id TEXT, " +
                "related_item_type TEXT, " +
                "timestamp TEXT, " +
                "read INTEGER DEFAULT 0" +
            ");",

            // Reports table
            "CREATE TABLE IF NOT EXISTS reports (" +
                "id TEXT PRIMARY KEY, " +
                "listing_id TEXT, " +
                "listing_title TEXT, " +
                "buyer_username TEXT, " +
                "reason TEXT, " +
                "date TEXT, " +
                "status TEXT" +
            ");",

            // Chat threads table
            "CREATE TABLE IF NOT EXISTS chat_threads (" +
                "id TEXT PRIMARY KEY, " +
                "listing_id TEXT, " +
                "listing_title TEXT, " +
                "buyer_username TEXT, " +
                "seller_username TEXT, " +
                "created_date TEXT" +
            ");",

            // Chat messages table
            "CREATE TABLE IF NOT EXISTS chat_messages (" +
                "id TEXT PRIMARY KEY, " +
                "thread_id TEXT, " +
                "sender_username TEXT, " +
                "text TEXT, " +
                "sent_date TEXT, " +
                "offer_id TEXT" +
            ");"
        };

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl) {
                stmt.execute(sql);
            }
            try { stmt.execute("ALTER TABLE reports ADD COLUMN handled_by TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE reports ADD COLUMN resolved_date TEXT"); } catch (SQLException ignored) {}
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if database is empty of users.
     */
    public boolean isEmpty() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            return true;
        }
        return true;
    }

    // ==========================================
    // USER OPERATIONS
    // ==========================================

    public synchronized void saveUser(User user) {
        if (user == null || user.getUsername() == null) return;
        try (Connection conn = getConnection()) {
            saveUserInternal(conn, user);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save user: " + e.getMessage(), e);
        }
    }

    private void saveUserInternal(Connection conn, User user) throws SQLException {
        String sql = "INSERT OR REPLACE INTO users " +
                "(username, full_name, password_hash, role, account_status, residence_hall, bio, preferred_meetup, average_rating, rating_count, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername().toLowerCase());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole() != null ? user.getRole().name() : UserRole.BUYER.name());
            ps.setString(5, user.getAccountStatus() != null ? user.getAccountStatus().name() : AccountStatus.ACTIVE.name());
            ps.setString(6, user.getResidenceHall());
            ps.setString(7, user.getBio());

            if (user instanceof Seller seller) {
                List<MeetupLocation> meetups = seller.getPreferredMeetups();
                if (meetups != null && !meetups.isEmpty()) {
                    ps.setString(8, meetups.stream().map(MeetupLocation::name).collect(Collectors.joining(",")));
                } else {
                    ps.setString(8, seller.getPreferredMeetup());
                }
            } else {
                ps.setString(8, user.getPreferredMeetup());
            }

            ps.setDouble(9, user.getAverageRating());
            ps.setInt(10, user.getRatingCount());
            ps.setString(11, user.getCreatedAt() != null ? user.getCreatedAt().format(ISO_FMT) : LocalDateTime.now().format(ISO_FMT));
            ps.executeUpdate();
        }
    }

    public synchronized void deleteUser(String username) {
        if (username == null) return;
        String sql = "DELETE FROM users WHERE username = ? COLLATE NOCASE";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete user: " + e.getMessage(), e);
        }
    }

    public synchronized Map<String, User> loadAllUsers() {
        Map<String, User> map = new LinkedHashMap<>();
        String sql = "SELECT * FROM users ORDER BY created_at ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String roleStr = rs.getString("role");
                UserRole role = UserRole.BUYER;
                if (roleStr != null) {
                    try {
                        role = UserRole.valueOf(roleStr);
                    } catch (IllegalArgumentException ignored) {}
                }
                String username = rs.getString("username");
                String fullName = rs.getString("full_name");
                String pwdHash = rs.getString("password_hash");

                User user;
                if (role == UserRole.BUYER) {
                    Buyer b = new Buyer(username, fullName, pwdHash);
                    b.setResidence(rs.getString("residence_hall"));
                    user = b;
                } else if (role == UserRole.SELLER) {
                    Seller s = new Seller(username, fullName, pwdHash);
                    s.setBio(rs.getString("bio"));
                    String meetupStr = rs.getString("preferred_meetup");
                    if (meetupStr != null && !meetupStr.isEmpty()) {
                        String[] parts = meetupStr.split(",");
                        List<MeetupLocation> locs = new ArrayList<>();
                        for (String part : parts) {
                            try {
                                locs.add(MeetupLocation.valueOf(part.trim()));
                            } catch (IllegalArgumentException e) {
                                MeetupLocation ml = MeetupLocation.fromString(part.trim());
                                if (ml != null) locs.add(ml);
                            }
                        }
                        if (!locs.isEmpty()) {
                            s.setPreferredMeetups(locs);
                        }
                    }
                    s.setAverageRating(rs.getDouble("average_rating"));
                    s.setRatingCount(rs.getInt("rating_count"));
                    user = s;
                } else {
                    user = new Admin(username, fullName, pwdHash);
                }

                String statusStr = rs.getString("account_status");
                if (statusStr != null) {
                    try {
                        user.setAccountStatus(AccountStatus.valueOf(statusStr));
                    } catch (IllegalArgumentException ignored) {}
                }
                String createdStr = rs.getString("created_at");
                if (createdStr != null) {
                    user.setCreatedAt(LocalDateTime.parse(createdStr, ISO_FMT));
                }
                map.put(user.getUsername().toLowerCase(), user);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load users: " + e.getMessage(), e);
        }
        return map;
    }

    // ==========================================
    // LISTING OPERATIONS
    // ==========================================

    public synchronized void saveListing(Listing listing) {
        if (listing == null || listing.getId() == null) return;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                saveListingInternal(conn, listing);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save listing: " + e.getMessage(), e);
        }
    }

    private void saveListingInternal(Connection conn, Listing listing) throws SQLException {
        String sql = "INSERT OR REPLACE INTO listings " +
                "(id, title, description, category, type, status, condition, price, lending_duration_days, " +
                "meetup_location, seller_username, seller_fullname, view_count, admin_reason, deleted_by_seller, posted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, listing.getId());
            ps.setString(2, listing.getTitle());
            ps.setString(3, listing.getDescription());
            ps.setString(4, listing.getCategory() != null ? listing.getCategory().name() : Category.OTHER.name());
            ps.setString(5, listing.getType() != null ? listing.getType().name() : ListingType.SELL.name());
            ps.setString(6, listing.getStatus() != null ? listing.getStatus().name() : ListingStatus.PENDING_REVIEW.name());
            ps.setString(7, listing.getCondition() != null ? listing.getCondition().name() : Condition.USED.name());
            if (listing.getPrice() != null) ps.setDouble(8, listing.getPrice()); else ps.setNull(8, Types.DOUBLE);
            if (listing.getLendingDays() != null) ps.setInt(9, listing.getLendingDays()); else ps.setNull(9, Types.INTEGER);
            ps.setString(10, listing.getMeetupLocation() != null ? listing.getMeetupLocation().name() : MeetupLocation.LIBRARY.name());
            ps.setString(11, listing.getSellerUsername());
            ps.setString(12, listing.getSellerFullName());
            ps.setInt(13, listing.getViewCount());
            ps.setString(14, listing.getAdminReason());
            ps.setInt(15, listing.isDeletedBySeller() ? 1 : 0);
            ps.setString(16, listing.getPostedDate() != null ? listing.getPostedDate().format(ISO_FMT) : LocalDateTime.now().format(ISO_FMT));
            ps.executeUpdate();
        }

        // Save photos
        try (PreparedStatement delPhotos = conn.prepareStatement("DELETE FROM listing_photos WHERE listing_id = ?");
             PreparedStatement insPhoto = conn.prepareStatement("INSERT INTO listing_photos (listing_id, photo_path) VALUES (?, ?)")) {
            delPhotos.setString(1, listing.getId());
            delPhotos.executeUpdate();

            if (listing.getPhotoPaths() != null) {
                for (String path : listing.getPhotoPaths()) {
                    insPhoto.setString(1, listing.getId());
                    insPhoto.setString(2, path);
                    insPhoto.executeUpdate();
                }
            }
        }
    }

    public synchronized void deleteListing(String id) {
        if (id == null) return;
        String sql = "DELETE FROM listings WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete listing: " + e.getMessage(), e);
        }
    }

    public synchronized Map<String, Listing> loadAllListings() {
        Map<String, Listing> map = new LinkedHashMap<>();
        String sql = "SELECT * FROM listings ORDER BY posted_at DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Listing l = new Listing();
                l.setId(rs.getString("id"));
                l.setTitle(rs.getString("title"));
                l.setDescription(rs.getString("description"));

                String catStr = rs.getString("category");
                if (catStr != null) {
                    try {
                        l.setCategory(Category.valueOf(catStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                String typeStr = rs.getString("type");
                if (typeStr != null) {
                    try {
                        l.setType(ListingType.valueOf(typeStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                String statusStr = rs.getString("status");
                if (statusStr != null) {
                    try {
                        l.setStatus(ListingStatus.valueOf(statusStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                String condStr = rs.getString("condition");
                if (condStr != null) {
                    try {
                        l.setCondition(Condition.valueOf(condStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                double price = rs.getDouble("price");
                if (!rs.wasNull()) l.setPrice(price);
                int days = rs.getInt("lending_duration_days");
                if (!rs.wasNull()) l.setLendingDays(days);

                String locStr = rs.getString("meetup_location");
                if (locStr != null) {
                    try {
                        l.setMeetupLocation(MeetupLocation.valueOf(locStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                l.setSellerUsername(rs.getString("seller_username"));
                l.setSellerFullName(rs.getString("seller_fullname"));
                l.setViewCount(rs.getInt("view_count"));
                l.setAdminReason(rs.getString("admin_reason"));
                l.setDeletedBySeller(rs.getInt("deleted_by_seller") == 1);
                String postedStr = rs.getString("posted_at");
                if (postedStr != null) {
                    l.setPostedDate(LocalDateTime.parse(postedStr, ISO_FMT));
                }

                // Load photos
                try (PreparedStatement psPhotos = conn.prepareStatement("SELECT photo_path FROM listing_photos WHERE listing_id = ?")) {
                    psPhotos.setString(1, l.getId());
                    try (ResultSet rsP = psPhotos.executeQuery()) {
                        List<String> paths = new ArrayList<>();
                        while (rsP.next()) {
                            paths.add(rsP.getString("photo_path"));
                        }
                        l.setPhotoPaths(paths);
                    }
                }
                map.put(l.getId(), l);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load listings: " + e.getMessage(), e);
        }
        return map;
    }

    // ==========================================
    // OFFER OPERATIONS
    // ==========================================

    public synchronized void saveOffer(Offer offer) {
        if (offer == null || offer.getId() == null) return;
        try (Connection conn = getConnection()) {
            saveOfferInternal(conn, offer);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save offer: " + e.getMessage(), e);
        }
    }

    private void saveOfferInternal(Connection conn, Offer offer) throws SQLException {
        if (offer.getCounterOffer() != null) {
            saveOfferInternal(conn, offer.getCounterOffer());
        }

        String sql = "INSERT OR REPLACE INTO offers " +
                "(id, type, listing_id, listing_title, buyer_username, seller_username, price, start_date, end_date, status, decline_reason, counter_offer_id, created_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, offer.getId());
            ps.setString(2, offer.getType() != null ? offer.getType().name() : OfferType.PRICE_OFFER.name());
            ps.setString(3, offer.getListingId());
            ps.setString(4, offer.getListingTitle());
            ps.setString(5, offer.getBuyerUsername());
            ps.setString(6, offer.getSellerUsername());
            if (offer.getPrice() != null) ps.setDouble(7, offer.getPrice()); else ps.setNull(7, Types.DOUBLE);
            ps.setString(8, offer.getStartDate() != null ? offer.getStartDate().format(ISO_FMT) : null);
            ps.setString(9, offer.getEndDate() != null ? offer.getEndDate().format(ISO_FMT) : null);
            ps.setString(10, offer.getStatus() != null ? offer.getStatus().name() : OfferStatus.PENDING.name());
            ps.setString(11, offer.getDeclineReason());
            ps.setString(12, offer.getCounterOffer() != null ? offer.getCounterOffer().getId() : null);
            ps.setString(13, offer.getCreatedDate() != null ? offer.getCreatedDate().format(ISO_FMT) : LocalDateTime.now().format(ISO_FMT));
            ps.executeUpdate();
        }
    }

    public synchronized void deleteOffer(String id) {
        if (id == null) return;
        String sql = "DELETE FROM offers WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete offer: " + e.getMessage(), e);
        }
    }

    public synchronized void clearOffers() {
        String sql = "DELETE FROM offers";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear offers: " + e.getMessage(), e);
        }
    }

    public synchronized List<Offer> loadAllOffers() {
        List<Offer> list = new ArrayList<>();
        Map<String, String> counterOfferMap = new HashMap<>();
        Map<String, Offer> offerById = new LinkedHashMap<>();

        String sql = "SELECT * FROM offers ORDER BY created_date ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Offer o = new Offer();
                o.setId(rs.getString("id"));

                String typeStr = rs.getString("type");
                if (typeStr != null) {
                    try {
                        o.setType(OfferType.valueOf(typeStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                o.setListingId(rs.getString("listing_id"));
                o.setListingTitle(rs.getString("listing_title"));
                o.setBuyerUsername(rs.getString("buyer_username"));
                o.setSellerUsername(rs.getString("seller_username"));
                double p = rs.getDouble("price");
                if (!rs.wasNull()) o.setPrice(p);
                String sDate = rs.getString("start_date");
                if (sDate != null) o.setStartDate(LocalDateTime.parse(sDate, ISO_FMT));
                String eDate = rs.getString("end_date");
                if (eDate != null) o.setEndDate(LocalDateTime.parse(eDate, ISO_FMT));

                String statusStr = rs.getString("status");
                if (statusStr != null) {
                    try {
                        o.setStatus(OfferStatus.valueOf(statusStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                o.setDeclineReason(rs.getString("decline_reason"));
                String createdStr = rs.getString("created_date");
                if (createdStr != null) {
                    o.setCreatedDate(LocalDateTime.parse(createdStr, ISO_FMT));
                }
                String counterId = rs.getString("counter_offer_id");
                if (counterId != null) {
                    counterOfferMap.put(o.getId(), counterId);
                }
                list.add(o);
                offerById.put(o.getId(), o);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load offers: " + e.getMessage(), e);
        }

        for (Map.Entry<String, String> entry : counterOfferMap.entrySet()) {
            Offer parent = offerById.get(entry.getKey());
            Offer counter = offerById.get(entry.getValue());
            if (parent != null && counter != null) {
                parent.setCounterOffer(counter);
            }
        }

        return list;
    }

    // ==========================================
    // DEAL OPERATIONS
    // ==========================================

    public synchronized void saveDeal(Deal deal) {
        if (deal == null || deal.getId() == null) return;
        try (Connection conn = getConnection()) {
            saveDealInternal(conn, deal);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save deal: " + e.getMessage(), e);
        }
    }

    private void saveDealInternal(Connection conn, Deal deal) throws SQLException {
        String sql = "INSERT OR REPLACE INTO deals " +
                "(id, listing_id, listing_title, buyer_username, seller_username, deal_type, status, start_date, due_date, closed_date, offer_id, offer_price, rated) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deal.getId());
            ps.setString(2, deal.getListingId());
            ps.setString(3, deal.getListingTitle());
            ps.setString(4, deal.getBuyerUsername());
            ps.setString(5, deal.getSellerUsername());
            ps.setString(6, deal.getType() != null ? deal.getType().name() : DealType.SALE.name());
            ps.setString(7, deal.getStatus() != null ? deal.getStatus().name() : DealStatus.ACTIVE.name());
            ps.setString(8, deal.getStartDate() != null ? deal.getStartDate().format(ISO_FMT) : LocalDateTime.now().format(ISO_FMT));
            ps.setString(9, deal.getDueDate() != null ? deal.getDueDate().format(ISO_FMT) : null);
            ps.setString(10, deal.getClosedDate() != null ? deal.getClosedDate().format(ISO_FMT) : null);
            ps.setString(11, deal.getOfferId());
            if (deal.getOfferPrice() != null) ps.setDouble(12, deal.getOfferPrice()); else ps.setNull(12, Types.DOUBLE);
            ps.setInt(13, deal.isRated() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public synchronized void deleteDeal(String id) {
        if (id == null) return;
        String sql = "DELETE FROM deals WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete deal: " + e.getMessage(), e);
        }
    }

    public synchronized void clearDeals() {
        String sql = "DELETE FROM deals";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear deals: " + e.getMessage(), e);
        }
    }

    public synchronized List<Deal> loadAllDeals() {
        List<Deal> list = new ArrayList<>();
        String sql = "SELECT * FROM deals ORDER BY start_date ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Deal d = new Deal();
                d.setId(rs.getString("id"));
                d.setListingId(rs.getString("listing_id"));
                d.setListingTitle(rs.getString("listing_title"));
                d.setBuyerUsername(rs.getString("buyer_username"));
                d.setSellerUsername(rs.getString("seller_username"));

                String typeStr = rs.getString("deal_type");
                if (typeStr != null) {
                    try {
                        d.setType(DealType.valueOf(typeStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                String statusStr = rs.getString("status");
                if (statusStr != null) {
                    try {
                        d.setStatus(DealStatus.valueOf(statusStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                String startStr = rs.getString("start_date");
                if (startStr != null) {
                    d.setStartDate(LocalDateTime.parse(startStr, ISO_FMT));
                }
                String due = rs.getString("due_date");
                if (due != null) d.setDueDate(LocalDateTime.parse(due, ISO_FMT));
                String closed = rs.getString("closed_date");
                if (closed != null) d.setClosedDate(LocalDateTime.parse(closed, ISO_FMT));
                d.setOfferId(rs.getString("offer_id"));
                double op = rs.getDouble("offer_price");
                if (!rs.wasNull()) d.setOfferPrice(op);
                d.setRated(rs.getInt("rated") == 1);
                list.add(d);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load deals: " + e.getMessage(), e);
        }
        return list;
    }

    // ==========================================
    // REVIEW OPERATIONS
    // ==========================================

    public synchronized void saveReview(Review review) {
        if (review == null || review.getId() == null) return;
        try (Connection conn = getConnection()) {
            saveReviewInternal(conn, review);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save review: " + e.getMessage(), e);
        }
    }

    private void saveReviewInternal(Connection conn, Review review) throws SQLException {
        String sql = "INSERT OR REPLACE INTO reviews " +
                "(id, seller_username, buyer_username, listing_id, listing_title, deal_id, rating, comment, reply, flagged, flag_reason, hidden, created_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, review.getId());
            ps.setString(2, review.getSellerUsername());
            ps.setString(3, review.getBuyerUsername());
            ps.setString(4, review.getListingId());
            ps.setString(5, review.getListingTitle());
            ps.setString(6, review.getDealId());
            ps.setInt(7, review.getRating());
            ps.setString(8, review.getComment());
            ps.setString(9, review.getReply());
            ps.setInt(10, review.isFlagged() ? 1 : 0);
            ps.setString(11, review.getFlagReason());
            ps.setInt(12, review.isHidden() ? 1 : 0);
            ps.setString(13, review.getDate() != null ? review.getDate().format(ISO_FMT) : LocalDateTime.now().format(ISO_FMT));
            ps.executeUpdate();
        }
    }

    public synchronized void clearReviews() {
        String sql = "DELETE FROM reviews";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear reviews: " + e.getMessage(), e);
        }
    }

    public synchronized List<Review> loadAllReviews() {
        List<Review> list = new ArrayList<>();
        String sql = "SELECT * FROM reviews ORDER BY created_date ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Review r = new Review();
                r.setId(rs.getString("id"));
                r.setSellerUsername(rs.getString("seller_username"));
                r.setBuyerUsername(rs.getString("buyer_username"));
                r.setListingId(rs.getString("listing_id"));
                r.setListingTitle(rs.getString("listing_title"));
                r.setDealId(rs.getString("deal_id"));
                r.setRating(rs.getInt("rating"));
                r.setComment(rs.getString("comment"));
                r.setReply(rs.getString("reply"));
                r.setFlagged(rs.getInt("flagged") == 1);
                r.setFlagReason(rs.getString("flag_reason"));
                r.setHidden(rs.getInt("hidden") == 1);
                String dateStr = rs.getString("created_date");
                if (dateStr != null) {
                    r.setDate(LocalDateTime.parse(dateStr, ISO_FMT));
                }
                list.add(r);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load reviews: " + e.getMessage(), e);
        }
        return list;
    }

    // ==========================================
    // NOTIFICATION OPERATIONS
    // ==========================================

    public synchronized void saveNotification(Notification n) {
        if (n == null || n.getId() == null) return;
        try (Connection conn = getConnection()) {
            saveNotificationInternal(conn, n);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save notification: " + e.getMessage(), e);
        }
    }

    private void saveNotificationInternal(Connection conn, Notification n) throws SQLException {
        String sql = "INSERT OR REPLACE INTO notifications " +
                "(id, username, title, message, category, related_item_id, related_item_type, timestamp, read) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, n.getId());
            ps.setString(2, n.getUsername());
            ps.setString(3, n.getTitle());
            ps.setString(4, n.getMessage());
            ps.setString(5, n.getCategory());
            ps.setString(6, n.getRelatedItemId());
            ps.setString(7, n.getRelatedItemType());
            ps.setString(8, n.getTimestamp() != null ? n.getTimestamp().format(ISO_FMT) : LocalDateTime.now().format(ISO_FMT));
            ps.setInt(9, n.isRead() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public synchronized void deleteNotification(String id) {
        if (id == null) return;
        String sql = "DELETE FROM notifications WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete notification: " + e.getMessage(), e);
        }
    }

    public synchronized int deleteNotificationsFor(String username) {
        if (username == null) return 0;
        String sql = "DELETE FROM notifications WHERE username = ? COLLATE NOCASE";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete notifications: " + e.getMessage(), e);
        }
    }

    public synchronized List<Notification> loadAllNotifications() {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT * FROM notifications ORDER BY timestamp DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Notification n = new Notification();
                n.setId(rs.getString("id"));
                n.setUsername(rs.getString("username"));
                n.setTitle(rs.getString("title"));
                n.setMessage(rs.getString("message"));
                n.setCategory(rs.getString("category"));
                n.setRelatedItemId(rs.getString("related_item_id"));
                n.setRelatedItemType(rs.getString("related_item_type"));
                String tsStr = rs.getString("timestamp");
                if (tsStr != null) {
                    n.setTimestamp(LocalDateTime.parse(tsStr, ISO_FMT));
                }
                n.setRead(rs.getInt("read") == 1);
                list.add(n);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load notifications: " + e.getMessage(), e);
        }
        return list;
    }

    // ==========================================
    // REPORT OPERATIONS
    // ==========================================

    public synchronized void saveReport(Report report) {
        if (report == null || report.getId() == null) return;
        try (Connection conn = getConnection()) {
            saveReportInternal(conn, report);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save report: " + e.getMessage(), e);
        }
    }

    private void saveReportInternal(Connection conn, Report report) throws SQLException {
        String sql = "INSERT OR REPLACE INTO reports " +
                "(id, listing_id, listing_title, buyer_username, reason, date, status, handled_by, resolved_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, report.getId());
            ps.setString(2, report.getListingId());
            ps.setString(3, report.getListingTitle());
            ps.setString(4, report.getBuyerUsername());
            ps.setString(5, report.getReason());
            ps.setString(6, report.getDate() != null ? report.getDate().format(ISO_FMT) : LocalDateTime.now().format(ISO_FMT));
            ps.setString(7, report.getStatus() != null ? report.getStatus().name() : ReportStatus.OPEN.name());
            ps.setString(8, report.getHandledBy());
            ps.setString(9, report.getResolvedDate() != null ? report.getResolvedDate().format(ISO_FMT) : null);
            ps.executeUpdate();
        }
    }

    public synchronized void deleteReport(String id) {
        if (id == null) return;
        String sql = "DELETE FROM reports WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete report: " + e.getMessage(), e);
        }
    }

    public synchronized List<Report> loadAllReports() {
        List<Report> list = new ArrayList<>();
        String sql = "SELECT * FROM reports ORDER BY date DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Report r = new Report();
                r.setId(rs.getString("id"));
                r.setListingId(rs.getString("listing_id"));
                r.setListingTitle(rs.getString("listing_title"));
                r.setBuyerUsername(rs.getString("buyer_username"));
                r.setReason(rs.getString("reason"));
                String dateStr = rs.getString("date");
                if (dateStr != null) {
                    r.setDate(LocalDateTime.parse(dateStr, ISO_FMT));
                }
                String statusStr = rs.getString("status");
                if (statusStr != null) {
                    try {
                        r.setStatus(ReportStatus.valueOf(statusStr));
                    } catch (IllegalArgumentException ignored) {}
                }
                try {
                    String handledBy = rs.getString("handled_by");
                    if (handledBy != null) {
                        r.setHandledBy(handledBy);
                    }
                } catch (SQLException ignored) {}
                try {
                    String resolvedDateStr = rs.getString("resolved_date");
                    if (resolvedDateStr != null) {
                        r.setResolvedDate(LocalDateTime.parse(resolvedDateStr, ISO_FMT));
                    }
                } catch (SQLException ignored) {}
                list.add(r);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load reports: " + e.getMessage(), e);
        }
        return list;
    }

    // ==========================================
    // CHAT OPERATIONS
    // ==========================================

    public synchronized void saveChatThread(ChatThread thread) {
        if (thread == null || thread.getId() == null) return;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                saveChatThreadInternal(conn, thread);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save chat thread: " + e.getMessage(), e);
        }
    }

    private void saveChatThreadInternal(Connection conn, ChatThread thread) throws SQLException {
        String sqlThread = "INSERT OR REPLACE INTO chat_threads " +
                "(id, listing_id, listing_title, buyer_username, seller_username, created_date) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement psT = conn.prepareStatement(sqlThread)) {
            psT.setString(1, thread.getId());
            psT.setString(2, thread.getListingId());
            psT.setString(3, thread.getListingTitle());
            psT.setString(4, thread.getBuyerUsername());
            psT.setString(5, thread.getSellerUsername());
            psT.setString(6, thread.getCreatedDate() != null ? thread.getCreatedDate().format(ISO_FMT) : LocalDateTime.now().format(ISO_FMT));
            psT.executeUpdate();
        }

        String sqlMsg = "INSERT OR REPLACE INTO chat_messages (id, thread_id, sender_username, text, sent_date, offer_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement psM = conn.prepareStatement(sqlMsg)) {
            if (thread.getMessages() != null) {
                for (Message m : thread.getMessages()) {
                    psM.setString(1, m.getId() != null ? m.getId() : UUID.randomUUID().toString());
                    psM.setString(2, thread.getId());
                    psM.setString(3, m.getSenderUsername());
                    psM.setString(4, m.getText());
                    psM.setString(5, m.getSentDate() != null ? m.getSentDate().format(ISO_FMT) : LocalDateTime.now().format(ISO_FMT));
                    psM.setString(6, m.getOfferId());
                    psM.executeUpdate();
                }
            }
        }
    }

    public synchronized void deleteChatThread(String id) {
        if (id == null) return;
        String sql = "DELETE FROM chat_threads WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete chat thread: " + e.getMessage(), e);
        }
    }

    public synchronized List<ChatThread> loadAllChatThreads() {
        List<ChatThread> list = new ArrayList<>();
        String sql = "SELECT * FROM chat_threads ORDER BY created_date ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ChatThread t = new ChatThread();
                t.setId(rs.getString("id"));
                t.setListingId(rs.getString("listing_id"));
                t.setListingTitle(rs.getString("listing_title"));
                t.setBuyerUsername(rs.getString("buyer_username"));
                t.setSellerUsername(rs.getString("seller_username"));
                String cdStr = rs.getString("created_date");
                if (cdStr != null) {
                    t.setCreatedDate(LocalDateTime.parse(cdStr, ISO_FMT));
                }

                // Load messages
                try (PreparedStatement psMsg = conn.prepareStatement("SELECT * FROM chat_messages WHERE thread_id = ? ORDER BY sent_date ASC")) {
                    psMsg.setString(1, t.getId());
                    try (ResultSet rsM = psMsg.executeQuery()) {
                        List<Message> msgs = new ArrayList<>();
                        while (rsM.next()) {
                            Message m = new Message();
                            m.setId(rsM.getString("id"));
                            m.setSenderUsername(rsM.getString("sender_username"));
                            m.setText(rsM.getString("text"));
                            String sdStr = rsM.getString("sent_date");
                            if (sdStr != null) {
                                m.setSentDate(LocalDateTime.parse(sdStr, ISO_FMT));
                            }
                            m.setOfferId(rsM.getString("offer_id"));
                            msgs.add(m);
                        }
                        t.setMessages(msgs);
                    }
                }
                list.add(t);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load chat threads: " + e.getMessage(), e);
        }
        return list;
    }

    // ==========================================
    // BATCH & MANAGEMENT OPERATIONS
    // ==========================================

    /**
     * Persists all given domain collections into the database in a single transaction.
     */
    public synchronized void persistAll(Collection<User> users,
                                        Collection<Listing> listings,
                                        Collection<Offer> offers,
                                        Collection<Deal> deals,
                                        Collection<Review> reviews,
                                        Collection<Notification> notifications,
                                        Collection<Report> reports,
                                        Collection<ChatThread> chatThreads) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (users != null) {
                    for (User u : users) {
                        saveUserInternal(conn, u);
                    }
                }
                if (listings != null) {
                    for (Listing l : listings) {
                        saveListingInternal(conn, l);
                    }
                }
                if (offers != null) {
                    for (Offer o : offers) {
                        saveOfferInternal(conn, o);
                    }
                }
                if (deals != null) {
                    for (Deal d : deals) {
                        saveDealInternal(conn, d);
                    }
                }
                if (reviews != null) {
                    for (Review r : reviews) {
                        saveReviewInternal(conn, r);
                    }
                }
                if (notifications != null) {
                    for (Notification n : notifications) {
                        saveNotificationInternal(conn, n);
                    }
                }
                if (reports != null) {
                    for (Report rep : reports) {
                        saveReportInternal(conn, rep);
                    }
                }
                if (chatThreads != null) {
                    for (ChatThread t : chatThreads) {
                        saveChatThreadInternal(conn, t);
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to batch persist: " + e.getMessage(), e);
        }
    }

    /**
     * Resets database (clears all tables in a single transaction).
     */
    public synchronized void clearAllData() {
        String[] tables = {
            "chat_messages", "chat_threads", "reports", "notifications",
            "reviews", "deals", "offers", "listing_photos", "listings", "users"
        };
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (String table : tables) {
                    stmt.execute("DELETE FROM " + table + ";");
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear database: " + e.getMessage(), e);
        }
    }
}
