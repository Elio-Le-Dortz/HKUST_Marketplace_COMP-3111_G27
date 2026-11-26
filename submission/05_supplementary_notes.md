# 5. Supplementary Notes: System Architecture, WoW Factors, Algorithms & Assumptions

[← JavaDoc Documentation](04_javadoc_documentation.md) · [Submission Hub](README.md)

This document provides technical context to assist TAs in evaluating the architecture, non-functional guarantees, algorithms, and design choices of **HKUST Marketplace**.

---

## 1. System Architecture & Key Characteristics

The application follows a **4-tier layered architecture** in **Java 17** and **JavaFX 21** that decouples UI presentation from business rules so all domain logic can be tested headlessly:

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                        JavaFX 21 Presentation Layer                      │
│   Buyer Portal (11 UCs)  │  Seller Portal (9 UCs)  │  Admin Portal (8 UCs) │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼─────────────────────────────────────┐
│                        Application Service Layer                         │
│   30 Service/DTO Classes (RBAC checked via SessionManager.require*())    │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼─────────────────────────────────────┐
│                     Domain Models & Concurrency Guard                    │
│     In-Memory Synchronized Store (DummyDataService single lock)          │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼─────────────────────────────────────┐
│                      Embedded Persistence & Storage                      │
│   SQLite Relational DB (data/marketplace.db) │ Local Photos (data/uploads)│
└──────────────────────────────────────────────────────────────────────────┘
```

### Core Architectural Characteristics
- **Service-Layer RBAC Enforcement:** Every Buyer, Seller, and Admin service method enforces role-based access control via `SessionManager.requireBuyer()`, `SessionManager.requireSeller()`, or `SessionManager.requireAdmin()` (including active-account verification and `ThreadLocal<User>` support for isolated concurrent testing).
- **Consistent Cross-Portal UI:** Unified HKUST branding (`#003974` navy, `#A68137` gold) defined in `src/main/resources/css/styles.css`, with modularized sub-controllers (e.g., `AdminDashboardController` decomposed into 7 focused sub-controllers + `AdminUIUtils`).
- **Immediate Write-Through SQLite Persistence:** `DatabaseManager` (`jdbc:sqlite:data/marketplace.db` with WAL journaling) persists users, listings, photos, offers, deals, reviews, notifications, reports, and chat threads across application restarts.

---

## 2. "WoW" Factors & Engineering Highlights

1. **PBKDF2-HMAC-SHA256 (600,000 Iterations) & Seeded Record Migration (`PasswordHasher`, `PasswordUtil`):**
   - Passwords across all 3 portals are hashed using `PasswordHasher.hash(...)` with **600,000 PBKDF2-HMAC-SHA256 iterations** (OWASP recommendation) and a random 16-byte `SecureRandom` salt, verified in constant time via `MessageDigest.isEqual`.
   - `PasswordUtil.verifyPassword(...)` delegates PBKDF2 hashes to `PasswordHasher`, retains verification compatibility for legacy salted SHA-256 (`saltHex:hashHex`) hashes, and strictly rejects plaintext or unsalted hashes.
   - `DummyDataService.loadFromDatabase()` automatically upgrades legacy unhashed seeded demo/admin passwords in older SQLite files to PBKDF2 on startup.
2. **Content-Addressed Local Photo Storage (`ListingPhotoStorage`):**
   - Seller-uploaded photos (1–6 images, max 10 MB, verified by both file extension and magic-byte header for PNG/JPEG/GIF/BMP) are copied atomically into `data/uploads/listings/` named `<16-hex-sha256>-<safe-name>`.
   - Listings remain intact even if the seller moves or deletes their original files, and identical images are deduplicated without creating redundant copies.
3. **Autonomous Background Loan Auto-Return Daemon (`AutoReturnScheduler`):**
   - Runs a daemon `ScheduledExecutorService` every 1 second calling `DummyDataService.processAutoReturns()`, automatically transitioning expired `LEND` deals (`>= 5` seconds minimum for live TA demos) to `RETURNED`, restoring listings to `LIVE`, and notifying both buyer and seller.
4. **Thread-Safe Atomic Concurrency & Deadlock Prevention:**
   - All check-and-mutate operations (atomic giveaway claims, simultaneous offer acceptance vs. listing deletion, admin takedowns, concurrent username registration, and formal chat offers) execute inside a consistent single-lock monitor (`synchronized (dataService)`), eliminating lock-order inversion deadlocks.
5. **Soft-Delete Cascade & Audit Integrity:**
   - Deleting a user with active deals is blocked; otherwise, soft-deletion sets `AccountStatus.DELETED` and preserves historical deals, chat threads, and reviews cleanly without breaking relational integrity.
6. **Searchable In-Thread Chat History:**
   - Real-time keyword search filters and highlights matching messages inside private buyer-seller chat threads while keeping full chronological history.

---

## 3. Algorithm Analysis & Design Justifications

### 3.1 Multi-Attribute Listing Filter & Sort (`ListingFilterService`)
- **Behavior:** Streams candidate listings, retains only `ListingStatus.LIVE`, evaluates conjunctions across keyword substring (`title` or `description`, case-insensitive), `Category`, `ListingType`, `Condition`, and inclusive `[minPrice, maxPrice]` bounds, and sorts dynamically (`NEWEST_FIRST`, `OLDEST_FIRST`, `PRICE_LOW_TO_HIGH`, `PRICE_HIGH_TO_LOW`, `MOST_POPULAR` by `viewCount`).
- **Complexity:** $O(N)$ filtering pass followed by $O(K \log K)$ comparison sort on the $K \le N$ matching listings ($O(N \log N)$ worst case).
- **Justification:** Single-pass predicate evaluation avoids intermediate list allocations and cleanly separates filter state (`ListingFilterCriteria`) from JavaFX controls.

### 3.2 Atomic First-Come Giveaway Claim (`ActOnListingService.submitClaim`)
- **Behavior:** Under `synchronized (dataService)`, verifies listing is `GIVEAWAY` and `LIVE`, then invokes `isGiveawayClaimed(listingId)` to check both `listing.getStatus() != ListingStatus.LIVE` and any existing `OfferType.CLAIM` with status `PENDING` or `ACCEPTED`.
- **Correctness:** Because check-and-insert runs atomically inside the `dataService` monitor, under $T$ concurrent claimant threads, **at most 1 thread** can insert a `PENDING` claim offer; all $T - 1$ remaining threads receive `alreadyClaimed(...)` deterministically.

### 3.3 Dynamic Seller Rating Calculation Excluding Hidden Reviews
- **Behavior:** Seller average rating and review count are dynamically recomputed from non-hidden `Review` records (`!review.isHidden()`).
- **Justification:** Hiding or unhiding a review in the Admin Portal (`AdminRatingService`) immediately reflects across Buyer listing details, Seller Stats, and Seller Reviews without stale cached totals.

---

## 4. Specific Assumptions & Documented Limitations

1. **Photo Storage Scope:**
   - Seller-created and edited listings copy images into `data/uploads/listings/` via `ListingPhotoStorage`. Listings added directly by an Admin on behalf of a seller retain the selected local filesystem paths.
   - Static pre-seeded demo listings in `DummyDataService` intentionally carry no image files to avoid platform-dependent binary writes during initial startup; the UI renders `"No Photo"` placeholders for these seed items.
2. **Seeded Administrator Account:**
   - In accordance with the project specification (`e.g., admin@ust.hk`), the seeded administrator username is `admin` (credentials: `admin` / `admin123`, stored as PBKDF2 hash).
3. **Password Strength Policy (`PasswordPolicy`):**
   - Every registration and password-change screen enforces: **at least 8 characters** (Unicode code points), **$\ge 1$ uppercase letter**, **$\ge 1$ lowercase letter**, and **$\ge 1$ digit**. Rejected passwords report all violated rules at once. *(Pre-seeded demo accounts `password1` / `admin123` predate interactive registration for rapid TA testing.)*
4. **Demo Loan Minimum Duration:**
   - Minimum borrow duration is `5` seconds (with a `"10 s (Demo)"` UI preset) so TAs can observe live background auto-returns without waiting days.

---

## 5. Demo Accounts for TA Assessment

| Role | Username | Password | Suggested Workflows to Test |
|---|---|---|---|
| **Buyer** | `alice` | `password1` | Browse & filter live listings, submit offers, claim giveaways, chat, complete deals & rate sellers. |
| **Buyer** | `bob` | `password1` | Borrow items, observe 5s+ auto-return transitions & notifications, inspect deal history. |
| **Seller** | `charlie` | `password1` | Create Sell/Lend/Giveaway listings with photos, accept/counter offers, mark reserved deals complete, view charts. |
| **Seller** | `diana` | `password1` | Reply to or flag buyer reviews, manage lending/giveaway items, edit profile & propagate name. |
| **Admin** | `admin` | `admin123` | Approve/reject pending listings with reasons, deactivate/soft-delete users, inspect all deals, hide/unhide reviews. |
