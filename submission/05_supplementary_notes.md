# 5. Supplementary Notes: System Architecture, Assumptions & TA Guide

[← JavaDoc Documentation](04_javadoc_documentation.md) · [Submission Hub](README.md)

This document provides technical context to assist TAs in evaluating the architecture, assumptions, and demo workflows of **HKUST Marketplace**.

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

## 2. Specific Assumptions & Documented Limitations

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

## 3. Demo Accounts for TA Assessment

| Role | Username | Password | Suggested Workflows to Test |
|---|---|---|---|
| **Buyer** | `alice` | `password1` | Browse & filter live listings, submit offers, claim giveaways, chat, complete deals & rate sellers. |
| **Buyer** | `bob` | `password1` | Borrow items, observe 5s+ auto-return transitions & notifications, inspect deal history. |
| **Seller** | `charlie` | `password1` | Create Sell/Lend/Giveaway listings with photos, accept/counter offers, mark reserved deals complete, view charts. |
| **Seller** | `diana` | `password1` | Reply to or flag buyer reviews, manage lending/giveaway items, edit profile & propagate name. |
| **Admin** | `admin` | `admin123` | Approve/reject pending listings with reasons, deactivate/soft-delete users, inspect all deals, hide/unhide reviews. |
