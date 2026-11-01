# COMP 3111: Software Engineering
## Minutes of the 2nd Project Scrum Meeting — HKUST Marketplace
**Team**: G27  
**Date**: October 31, 2026  
**Time**: 4:30 p.m.  
**Place**: Library Study Room 4 / Discord  
**Attending**: Charles Dupouy, Antoine Lepin, Elio Le Dortz  
**Absent**: None  
**Recorder**: Antoine Lepin  

---

### 1. Report on progress during the past week

In accordance with the goals established in Meeting 1, all foundational domain models, core business logic, and testing setups were implemented and verified prior to this meeting:

| Name | Tasks worked on in the past week | Total hours |
| :--- | :--- | :---: |
| **Charles Dupouy** | <ul><li>Configured JUnit 5, Mockito, and JaCoCo code coverage plugins in `pom.xml`</li><li>Implemented Task 1 domain models (`Buyer`, `Offer`, `Deal`, `OfferType`, `DealStatus`)</li><li>Implemented search and multi-attribute filtering logic (by title, category, type, price, condition)</li><li>Authored unit test suite for Buyer registration validation and live listing filtering (100% pass rate)</li></ul> | 6.5 |
| **Antoine Lepin** | <ul><li>Implemented Task 2 domain models (`Seller`, `Listing`, `Photo`, `ListingStatus`, `ListingType`)</li><li>Implemented dynamic listing form validation (price mandatory for Sell, duration for Lend, free for Giveaway)</li><li>Implemented seller offer acceptance, decline, and counter-offer state transition methods</li><li>Wrote unit tests for seller offer workflows and listing status transitions</li></ul> | 6.0 |
| **Elio Le Dortz** | <ul><li>Implemented Task 3 domain models (`Admin`, `Report`, `Review`, `Notification`, `AccountStatus`)</li><li>Implemented listing approval queue logic and account soft-delete mechanism (`AccountStatus.Deleted`)</li><li>Implemented review moderation (hiding inappropriate reviews, rating recalculation)</li><li>Wrote unit tests for admin review moderation with JaCoCo branch coverage verification</li></ul> | 6.0 |

---

### 2. Discussion of impediments and resolution

* **2.1. Git Collaboration & Integration Strategy (Avoiding Big Bang Integration)**:
  * *Discussion*: The team debated whether each member should work on an isolated long-lived branch (`dev-buyer`, `dev-seller`, `dev-admin`) and merge at the end of the sprint, or use a continuous integration strategy.
  * *Resolution*: Long-lived isolated branches were rejected as an anti-pattern ("Merge Hell") because all three roles share common models (`User`, `Listing`, `Deal`), services (`DummyDataService`), and session management (`SessionManager`). Instead, the team agreed on **short-lived feature branches** based on user stories (e.g., `feat/b03-browse`, `feat/s03-create-listing`), frequent PRs/merges into `master` after unit tests pass, and daily rebasing against `origin/master`.
* **2.2. Testing JavaFX UI controllers without headless display issues**:
  * *Discussion*: Automated test execution via `mvn test` in CI or headless grading environments fails if controllers directly invoke JavaFX UI toolkit threads.
  * *Resolution*: Decoupled presentation from business logic by encapsulating state changes, validation, and domain rules into pure Java service/domain classes (`DummyDataService`, model methods). Unit tests target business logic directly, guaranteeing fast execution and high branch coverage without JavaFX platform initialization overhead.
* **2.3. Shared notification broadcast and inter-portal event synchronization**:
  * *Discussion*: When a Seller updates an offer (accept/counter) or an Admin approves a listing, Buyer and Seller notification boards must reflect the update immediately.
  * *Resolution*: Standardized notification generation within `DummyDataService` using timestamped `Notification` records categorized by type (`Offer`, `Chat`, `System`, `Rating`).

---

### 3. Goals for the coming week (Sprint 2 Core Implementation)

| Name | Tasks that will be worked on in the coming week |
| :--- | :--- |
| **Charles Dupouy** | <ul><li>Implement JavaFX UI controllers for Buyer Portal (`BuyerDashboardController`, `BrowseView`, `ListingDetailView`)</li><li>Implement atomic giveaway claim and 5-second minimum borrow timer with auto-return in `My Deals`</li><li>Implement private chat UI with chronological message history and search</li><li>Maintain unit test suite and branch coverage $>65\%$ for Buyer services</li></ul> |
| **Antoine Lepin** | <ul><li>Implement JavaFX UI controllers for Seller Portal (`SellerDashboardController`, `CreateListingView`, `MyListingsView`)</li><li>Implement `Offer / Request Management Screen` with counter-offer dialog and deal confirmation</li><li>Implement JavaFX graphical analytics (`BarChart` for views/offers, `PieChart` for completed deals)</li><li>Implement review feedback reply and reporting functionality</li></ul> |
| **Elio Le Dortz** | <ul><li>Implement JavaFX UI controllers for Admin Portal (`AdminDashboardController`, `ListingReviewView`, `UserManagementView`)</li><li>Implement Admin all-listings management (taking down listings with mandatory reason and notifying active bidders)</li><li>Implement All Deals audit table with date and status filters</li><li>Run weekly JaCoCo test reports to track team-wide branch coverage</li></ul> |

---

### 4. Meeting adjournment and next meeting

The meeting was adjourned at 6:15 p.m.  
The next meeting will be held on **November 21, 2026 at 4:30 p.m.** in the Library Study Room / Discord.
