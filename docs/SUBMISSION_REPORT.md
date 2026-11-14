# HKUST Marketplace — COMP 3111 Final Project Submission & Assessment Report

**Course**: COMP 3111 Software Engineering (Fall 2026)  
**Team**: Group 27  
**GitHub Repository**: [https://github.com/Elio-Le-Dortz/HKUST_Marketplace_COMP-3111_G27](https://github.com/Elio-Le-Dortz/HKUST_Marketplace_COMP-3111_G27)  
**Members & Task Allocation**:
- **Charles Dupouy** (`cdupouy`): **Task 1 — Buyer Portal** (US-B01 to US-B11)
- **Antoine Lepin** (`alepin`): **Task 2 — Seller Portal** (US-S01 to US-S09)
- **Elio Le Dortz** (`eledortz`): **Task 3 — Administrator Portal** (US-A01 to US-A08)

---

## Table of Contents
1. [Executive Summary & System Architecture](#1-executive-summary--system-architecture)
2. [Report on Unit Testing (100% Pass)](#2-report-on-unit-testing-100-pass)
3. [Report on Branch Coverage (>65% Requirement)](#3-report-on-branch-coverage-65-requirement)
4. [Documentation on Implemented Tasks Using JavaDoc](#4-documentation-on-implemented-tasks-using-javadoc)
5. [Application Execution Walkthrough & Sample Inputs/Outputs](#5-application-execution-walkthrough--sample-inputsoutputs)
6. [Supplementary Notes: WoW Factors, Algorithms & System Characteristics](#6-supplementary-notes-wow-factors-algorithms--system-characteristics)
7. [Verification & Build Instructions](#7-verification--build-instructions)

---

## 1. Executive Summary & System Architecture

The HKUST Marketplace is a complete, full-stack desktop application built with **JavaFX 21** and **Java 17** using a multi-tier layered architecture. All **28 use cases** specified in the course requirements have been fully implemented, rigorously tested, and integrated:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        JavaFX Presentation Layer                       │
│     Buyer Portal (11 UCs)   │  Seller Portal (9 UCs)  │ Admin (8 UCs) │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                      Application Service Layer                         │
│  OfferManagementService │ ListingReviewService │ AutoReturnScheduler   │
│  UserManagementService  │ ChatService          │ PasswordPolicy        │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                     Domain Models & RBAC Security                      │
│   SessionManager (RBAC) │ Thread-safe Single-Lock (DummyDataService)   │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                    Persistence & Storage Layer                         │
│       In-Memory Synchronized Store  │  Embedded SQLite Database        │
└────────────────────────────────────────────────────────────────────────┘
```

### Portal & Task Ownership Matrix

| Portal | Lead Owner | Implemented User Stories | Primary Service Classes |
|---|---|---|---|
| **Buyer** | Charles Dupouy | **US-B01 to US-B11** (11/11 complete)<br>Registration, Login, Browse, Details, Offers/Claims, Chat, My Deals, Profile, Notifications, History, Reviews | `BuyerRegistrationService`, `BuyerLoginService`, `ListingFilterService`, `ListingDetailService`, `ActOnListingService`, `ChatService`, `DealService`, `BuyerProfileService`, `BuyerNotificationService`, `DealHistoryService`, `BuyerReviewService` |
| **Seller** | Antoine Lepin | **US-S01 to US-S09** (9/9 complete)<br>Registration, Login, Create Listing, My Listings, Manage Offers, Profile, Notifications, Statistics, Reviews | `SellerRegistrationService`, `SellerLoginService`, `CreateListingService`, `MyListingsService`, `OfferManagementService`, `SellerProfileService`, `SellerNotificationService`, `SellerStatisticsService`, `ReviewHandlingService` |
| **Admin** | Elio Le Dortz | **US-A01 to US-A08** (8/8 complete)<br>Admin Login, User Management, All Deals Ledger, Listing Moderation Queue, All Listings & Takedown, Ratings Moderation, Add on Behalf, Notifications | `AdminLoginService`, `UserManagementService`, `AdminDealsService`, `ListingReviewService`, `AdminListingService`, `AdminRatingService`, `AdminNotificationService`, `AdminProfileService` |

---

## 2. Report on Unit Testing (100% Pass)

The automated test suite contains **2,008 tests** executed via the standard Maven Surefire toolchain. All tests run cleanly with **0 failures, 0 errors, and 0 skipped tests (100% pass rate)**.

### Test Execution Summary

```
-------------------------------------------------------
 T E S T S
-------------------------------------------------------
[INFO] Results:
[INFO] 
[INFO] Tests run: 2008, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Test Suite Breakdown by Package

| Package / Domain | Primary Test Classes | Test Count | Pass Rate | Key Verification Highlights |
|---|---|:---:|:---:|---|
| **Controllers & UI** | `BuyerDashboardControllerTest`, `SellerControllersTest`, `AuthControllersTest`, `AdminModularControllersExtendedTest` | 38 | 100% | Headless JavaFX view creation, toolbar filter reactivity, price range boundary alerts, chat message bubbles, search highlight badges, and status notes. |
| **Buyer Services** | `BuyerLoginTest`, `BuyerRegistrationServiceTest`, `ListingFilterServiceTest`, `ActOnListingServiceTest`, `ChatServiceTest`, `DealServiceTest`, `BuyerNotificationServiceTest`, `BuyerReviewServiceTest`, `DealHistoryServiceTest` | 425 | 100% | Credential authentication, PBKDF2/SHA-256 validation, atomic claims under concurrency, multi-criteria browse filters, formal offer submissions, and auto-return transitions. |
| **Seller Services** | `SellerRegistrationServiceTest`, `SellerLoginTest`, `CreateListingServiceTest`, `MyListingsServiceTest`, `OfferManagementServiceTest`, `SellerStatisticsServiceTest`, `ReviewHandlingServiceTest`, `SellerNotificationServiceTest` | 412 | 100% | Image header verification, field validation, counter-offers, deal completion cascades, average rating calculations, and charts metrics. |
| **Admin Services** | `AdminLoginTest`, `UserManagementServiceTest`, `AdminListingServiceTest`, `ListingReviewServiceTest`, `AdminDealsServiceTest`, `AdminRatingServiceTest`, `AdminNotificationServiceTest`, `AdminProfileServiceTest` | 389 | 100% | Listing approval/rejection reasons, user deactivation and soft-deletion protections, deal ledger filters, review hiding/dismissal, and profile updates. |
| **Domain Models** | `ComprehensiveModelTests`, `ModelTests`, `ListingTest`, `DealTest`, `UserTest`, `NotificationTest` | 362 | 100% | Equals/hashCode contracts, status enum cascades, duration unit math, and filter criteria boundary conditions. |
| **Utilities & Persistence** | `ComprehensiveDummyDataServiceTest`, `ComprehensiveDatabaseManagerTest`, `DummyDataServicePersistenceTest`, `PasswordHasherTest`, `PasswordUtilTest`, `AutoReturnSchedulerTest` | 382 | 100% | In-memory atomic locking, SQLite schema persistence, PBKDF2 round stretching, background timer thread safety, and auto-return execution. |
| **Total** | **94 Test Source Files** | **2,008** | **100%** | **Full system verification, zero regressions.** |

---

## 3. Report on Branch Coverage (>65% Requirement)

JaCoCo code coverage was measured across the **entire repository codebase** without any exclusions configured in `pom.xml` (`<excludes>` is completely absent). Every production class (models, services, utilities, and UI controllers) is included in the global evaluation.

### JaCoCo Global Metrics

| Coverage Dimension | Covered / Total | Percentage | Course QA-01 Threshold | Margin Over Threshold |
|---|:---:|:---:|:---:|:---:|
| **Branch Coverage** | **5,389 / 7,596** | **70.95%** | **> 65.0%** | **+5.95% (Safe Pass)** |
| **Instruction Coverage** | **58,959 / 76,650** | **76.92%** | — | — |
| **Line Coverage** | **12,013 / 15,595** | **77.03%** | — | — |
| **Class Coverage** | **190 / 190** | **100.0%** | — | All classes evaluated |

### Coverage Verification Command

The official verification script extracts the exact JaCoCo metrics from the generated `target/site/jacoco/jacoco.csv`:

```bash
python3 -c "
import csv
with open('target/site/jacoco/jacoco.csv') as f:
    rows = list(csv.DictReader(f))
bm = sum(int(r['BRANCH_MISSED']) for r in rows)
bc = sum(int(r['BRANCH_COVERED']) for r in rows)
print(f'Branch Coverage: {bc}/{bm + bc} ({bc/(bm + bc)*100:.2f}%)')
"
# Output: Branch Coverage: 5389/7596 (70.95%)
```

---

## 4. Documentation on Implemented Tasks Using JavaDoc

In strict accordance with **Backlog QA-02** (*"Full docstrings on all methods and classes. No missing `@param`, `@return`, or `@throws` tags. Code should be well-commented"*):

1. **Complete Class and Method Docstrings**:
   - Every domain model, entity, and enumeration in `com.hkust.marketplace.models` has comprehensive Javadoc explaining its architectural role, attributes, and relationships.
   - All 11 core enumerations (`Condition`, `MeetupLocation`, `AccountStatus`, `DealStatus`, `OfferStatus`, `Category`, `UserRole`, `ListingType`, `ReportStatus`, `OfferType`, `ListingStatus`) contain docstrings for every constant and parsing method.
   - All public service interfaces and implementation methods document parameter preconditions (`@param`), expected return values (`@return`), and security or validation exceptions (`@throws`).
2. **HTML Entity Cleanliness**:
   - All Javadoc comments use standard HTML entities (`&amp;`, `&lt;`, `&gt;`) to comply with strict HTML5 doclint standards.
3. **API Documentation Build**:
   - API documentation generates via Maven:
     ```bash
     ./mvnw javadoc:javadoc
     ```
   - Generated HTML pages are output directly to `target/site/apidocs/index.html`.

---

## 5. Application Execution Walkthrough & Sample Inputs/Outputs

The application provides a consistent JavaFX interface with HKUST institutional branding (`#003974` navy blue and `#A68137` gold). Below are sample workflows showing typical inputs and expected outputs across all three portals:

### 5.1 Buyer Portal (Task 1)

```
[Welcome Screen]
  └── Click "I am a Buyer" → Opens Buyer Login

[Buyer Login Screen]
  ├── Input: Username: "alice", Password: "password1"
  └── Output: Successfully authenticates session; loads Buyer Dashboard greeting "Hello, Alice Chan".

[Browse Marketplace (US-B03)]
  ├── Inputs:
  │     Search: "Calculus"
  │     Category: "Books"
  │     Type: "Sell"
  │     Price HK$: Min "50", Max "300"
  │     Sort Order: "Price: Low to High"
  └── Output: Results update in real-time. Displays card for "Calculus Early Transcendentals" (HK$ 150.0).

[Listing Details & Formal Offer (US-B04, US-B05, US-B06)]
  ├── Inputs:
  │     Click listing card → Details view opens (view count increments by 1).
  │     Click "💬 Chat with Seller" → Opens private thread with seller @charlie.
  │     Click "🏷️ Make Formal Offer" → Input: "140.0"
  └── Output: Formal offer message sent in chat thread; status set to "PENDING". Notification dispatched to Charlie.

[Atomic Giveaway Claim (US-B05 Concurrency)]
  ├── Inputs: Buyer clicks "🎁 Claim This Item" on live giveaway listing "Free Coffee Mug".
  └── Output: First buyer succeeds ("Claim Successful! Item reserved"). Second buyer receives immediate alert: "Listing Unavailable: This item has already been claimed."
```

### 5.2 Seller Portal (Task 2)

```
[Seller Login Screen]
  ├── Input: Username: "charlie", Password: "password1"
  └── Output: Authenticates session; loads Seller Dashboard greeting "Hello, Charlie Lee".

[Create Listing (US-S03)]
  ├── Inputs:
  │     Type: "Lend"
  │     Item Name: "Graphing Calculator TI-84"
  │     Category: "Electronics"
  │     Condition: "Like New"
  │     Meetup Location: "Library"
  │     Lending Duration: "7" "Days"
  │     Description: "Excellent condition, includes charging cable."
  │     Photos: Upload 1–6 valid PNG/JPEG images.
  └── Output: Form validates input; creates listing with status "PENDING_REVIEW". Dispatched to admin moderation queue.

[Manage Offers & Counter-Offers (US-S05)]
  ├── Inputs:
  │     Seller views incoming offer from @alice ("HK$ 140.0").
  │     Click "Counter Offer" → Proposes "HK$ 145.0".
  └── Output: Status transitions to "COUNTERED". Alice receives notification and countered offer badge in chat.

[Seller Statistics & Charts (US-S08)]
  └── Output: Displays real-time metrics: Total Views (24), Total Offers (7), Deals Completed (3). Renders interactive JavaFX BarChart (Views vs Offers) and PieChart (Sales vs Loans vs Giveaways).
```

### 5.3 Administrator Portal (Task 3)

```
[Admin Login Screen]
  ├── Input: Username: "admin", Password: "admin123"
  └── Output: Authenticates session using PBKDF2 hash verification; loads Administrator Dashboard.

[Listing Review Queue (US-A04)]
  ├── Inputs:
  │     Admin inspects pending listing "Graphing Calculator TI-84".
  │     Click "Approve" (with confirmation).
  └── Output: Listing status updates from "PENDING_REVIEW" to "LIVE". Seller @charlie receives approval notification.

[User Management & Soft-Deletion Safeguards (US-A02)]
  ├── Inputs:
  │     Admin attempts to delete user with active deals.
  │     Output: Blocked with alert: "Cannot delete user: Active deals are currently in progress."
  │     Admin deactivates delinquent user.
  └── Output: Status updates to "DEACTIVATED". User's future login attempts are immediately rejected.
```

---

## 6. Supplementary Notes: WoW Factors, Algorithms & System Characteristics

### 6.1 "WoW" Factors & Innovation

1. **Dual-Scheme Cryptographic Security & Self-Healing Migration**:
   - Implements **salted PBKDF2-HMAC-SHA256** (with 65,536 iterations and cryptographic salt) alongside salted SHA-256.
   - Includes automatic self-healing migration: legacy database records with plaintext passwords automatically hash upon service startup.
2. **Autonomous Background Auto-Return Daemon (`AutoReturnScheduler`)**:
   - A dedicated daemon thread monitors borrowed items in the background. When a loan period expires (minimum 5 seconds for demonstration, up to months), the system automatically updates the deal status to `RETURNED`, marks the listing `LIVE`, and dispatches timestamped notifications to both buyer and seller.
3. **Atomic Concurrency Protection**:
   - Thread-safe domain architecture ensures zero race conditions during high-contention events (e.g. 10 simultaneous buyers claiming a single giveaway item). Only 1 claim succeeds; the remaining 9 fail gracefully with informative UI alerts.
4. **Searchable In-Thread Chat History with Real-Time Highlighting**:
   - Complete chat history persistence with instant substring keyword search. Matching messages receive visual highlight badges (`★ SEARCH MATCH`) with automatic scroll positioning.
5. **Soft-Delete Cascade & Historical Deal Integrity**:
   - Soft-deleting a user sets status to `DELETED` and safely replaces display references with `"Deleted user"`, ensuring past deals, receipts, reviews, and audit trails remain intact without violating foreign-key constraints.

### 6.2 Key Algorithms

- **Multi-Attribute Filter & Scoring Algorithm (`ListingFilterService`)**:
  Performs multi-criteria filtering (keyword text matching in title and description, category matching, transaction type matching, condition matching, and price range validation). Results are ordered dynamically based on the selected comparator: `NEWEST_FIRST`, `PRICE_LOW_TO_HIGH`, `PRICE_HIGH_TO_LOW`, `OLDEST_FIRST`, or `MOST_POPULAR` (ranked by view count).
- **Lending Duration Cascading Engine (`CreateListingService`, `AdminListingService`)**:
  Parses lending duration amounts and units (`SECONDS`, `DAYS`, `WEEKS`, `MONTHS`), converts them losslessly into `java.time.Duration`, and enforces course constraints (minimum duration $\ge 5$ seconds).

### 6.3 Assumptions & Documented Limitations

- **Photo Storage Strategy**: Photos are stored as absolute filesystem paths read with `java.io.File`. The application enforces file magic byte headers (verifying genuine PNG, JPEG, GIF, BMP files) and provides elegant placeholder icons ("📷 No Photo") when rendering pre-seeded demo records.
- **Seeded Administrator Account**: In accordance with the assignment brief (`activity1/assignment.txt`), the seeded administrator username is `admin` with credentials `admin` / `admin123`.

---

## 7. Verification & Build Instructions

### Prerequisites
- **Java Development Kit (JDK)**: Version 17 or higher (Adoptium Temurin 17 or 21 recommended).
- **Maven**: Included via Maven Wrapper (`./mvnw` or `mvnw.cmd`).

### Commands

```bash
# 1. Run all unit tests and generate JaCoCo coverage report
./mvnw clean test

# 2. Launch the JavaFX Application
./mvnw clean javafx:run

# 3. Generate JavaDoc API Documentation
./mvnw javadoc:javadoc
```

### Demo Accounts for TA Assessment

| Role | Username | Password | Purpose / Features to Test |
|---|---|---|---|
| **Buyer** | `alice` | `password1` | Browse live items, submit price offers, claim giveaways, chat, rate sellers. |
| **Buyer** | `bob` | `password1` | Borrow items, test auto-return notifications, view deal history. |
| **Seller** | `charlie` | `password1` | Create new listings (Sale/Lend/Giveaway), manage offers, view stats. |
| **Seller** | `diana` | `password1` | Review feedback, respond to buyer reviews, counter offers. |
| **Admin** | `admin` | `admin123` | Moderate pending listings, manage user accounts, inspect all deals ledger. |

---
*Report compiled and certified for COMP 3111 Sprint 2 / Final Project Assessment by Group 27.*
