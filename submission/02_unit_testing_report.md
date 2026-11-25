# 2. Report on Unit Testing (100% Pass)

[← Application Execution](01_application_execution_screenshots.md) · [Submission Hub](README.md) · [Next: Coverage Report →](03_coverage_report.md)

The automated test suite consists of **2,008 unit, integration, persistence, concurrency, and headless UI controller tests** across **94 test source files** executed via JUnit 5 and Maven Surefire. Every test passes with **0 failures, 0 errors, and 0 skipped tests (100% pass rate)**.

---

## 2.1 Maven Surefire Execution Summary

```text
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

**Reproduce locally:**
```bash
./mvnw clean test
```

---

## 2.2 Test Suite Breakdown by Package

The table below reflects the exact Surefire XML execution counts across the 4 test packages (`2,008` total tests across `94` test files):

| Package | Test Count | Pass Rate | Representative Test Classes | Scope Verified |
|---| :---: | :---: | :--- | :--- |
| **Services** (`com.hkust.marketplace.services`) | **1,384** | **100%** | `ActOnListingServiceTest`, `ChatServiceTest`, `DealServiceTest`, `ListingFilterServiceTest`, `ListingDetailServiceTest`, `BuyerNotificationServiceTest`, `BuyerProfileServiceTest`, `BuyerReviewServiceTest`, `DealHistoryServiceTest`, `SellerRegistrationServiceTest`, `SellerLoginServiceTest`, `CreateListingServiceTest`, `MyListingsServiceTest`, `OfferManagementServiceTest`, `SellerStatisticsServiceTest`, `ReviewHandlingServiceTest`, `SellerNotificationServiceTest`, `SellerChatServiceTest`, `SellerProfileServiceTest`, `SellerPersistenceTest`, `AdminLoginServiceTest`, `UserManagementServiceTest`, `AdminListingServiceTest`, `ListingReviewServiceTest`, `AdminDealsServiceTest`, `AdminRatingServiceTest`, `AdminNotificationServiceTest`, `AdminProfileServiceTest`, `AdminPersistenceTest`, `ComprehensiveServiceBranchTests` | All 28 use cases (basic, alternative, and exception flows), RBAC session guards (`SessionManager`), atomic concurrency under multi-threading, SQLite restart persistence, counter-offer cascades, and review moderation. |
| **Domain Models** (`com.hkust.marketplace.models`) | **336** | **100%** | `ComprehensiveModelTests`, `DomainModelTest`, `DealModelsTest`, `NotificationModelsTest`, `ChatModelsTest`, `ListingFilterCriteriaTest`, `OfferManagementModelsTest`, `AdminDealsModelsTest`, `SellerStatisticsModelsTest`, `UserManagementModelsTest` | Model invariants, status enums, `equals`/`hashCode` contracts, duration unit conversions, and filter/request DTOs. |
| **Database & Utilities** (`com.hkust.marketplace.database`, `utils`) | **235** | **100%** | `ComprehensiveDatabaseManagerTest`, `ComprehensiveDummyDataServiceTest`, `DummyDataServicePersistenceTest`, `BuyerLoginTest`, `BuyerRegistrationTest`, `PasswordHasherTest`, `PasswordUtilTest`, `PasswordPolicyTest`, `AutoReturnSchedulerTest`, `ListingPhotoStorageTest` | SQLite schema CRUD (`DatabaseManager`), PBKDF2 600k-round hashing, password policy enforcement, background `AutoReturnScheduler` daemon, and SHA-256 photo copying (`ListingPhotoStorage`). |
| **Controllers & UI** (`com.hkust.marketplace.controllers.*`) | **53** | **100%** | `BuyerDashboardControllerTest`, `SellerControllersTest`, `AuthControllersTest`, `AdminLoginTest`, `AdminModularControllersTest`, `AdminModularControllersExtendedTest` | Headless JavaFX view creation, filter controls, boundary validation alerts, chat bubbles, search highlights, and modular admin sub-controllers. |
| **Total** | **2,008** | **100%** | **94 Test Source Files** | **Zero failures, zero errors, zero skipped tests.** |

---

## 2.3 Key Testing Strategies Implemented

1. **Multi-Threaded Concurrency Tests:**
   - Simultaneous giveaway claims (`ActOnListingServiceTest`), concurrent username registrations (`SellerRegistrationServiceTest`, `BuyerRegistrationTest`), simultaneous listing edit/delete vs. offer acceptance (`MyListingsServiceTest`, `OfferManagementServiceTest`), and admin take-down vs. offer acceptance (`AdminListingServiceTest`).
2. **SQLite Restart Persistence Tests:**
   - `DummyDataServicePersistenceTest`, `SellerPersistenceTest`, `AdminPersistenceTest`, and `ComprehensiveDatabaseManagerTest` execute mutations, reload from a fresh `DatabaseManager` / `DummyDataService` instance, and verify full state survival across restarts.
3. **Locale-Independent Assertions:**
   - Number and timestamp formatting tests explicitly verify deterministic English output (`Locale.ENGLISH`) regardless of the host JVM default locale.
