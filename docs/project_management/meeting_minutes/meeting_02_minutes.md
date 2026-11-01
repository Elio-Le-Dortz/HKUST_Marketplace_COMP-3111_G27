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

In accordance with the goals established in Meeting 1, all **Critical** and **High** priority features, foundational domain models, and companion unit test suites were implemented and verified within their respective feature branches:

| Name | Tasks worked on in the past week | Total hours |
| :--- | :--- | :---: |
| **Charles Dupouy**<br>*(Task 1: Buyer)* | <ul><li>Configured JUnit 5, Mockito, and JaCoCo code coverage plugins in `pom.xml` on `master`</li><li>Scaffolded core domain models matching `CLASS_DIAGRAM.md` (`User`, `Buyer`, `Listing`, `Offer`, `Deal`)</li><li>Implemented **High** features: US-B01 (Registration with password validation rules), US-B02 (Login & role enforcement), US-B03 (Live marketplace browse & multi-attribute filter), US-B06 (Chat thread), US-B11 (Rate seller)</li><li>Implemented **Critical** features: US-B05 (Act on listing: price offer, borrow request $\ge 5$s, atomic giveaway claim) and US-B07 (My Deals with auto-return timer)</li><li>Authored unit test suite for Buyer services and business logic (100% test pass rate)</li></ul> | 7.0 |
| **Antoine Lepin**<br>*(Task 2: Seller)* | <ul><li>Scaffolded domain models (`Seller`, `Photo`, `ListingStatus`, `ListingType`)</li><li>Implemented **High** features: US-S01 (Seller registration), US-S02 (Seller login), US-S03 (Create listing dynamic form: Sell, Lend, Giveaway with photo upload), US-S04 (My listings screen), US-S09 (Review handling: reply & flag)</li><li>Implemented **Critical** feature: US-S05 (Manage offers: accept, decline with reason, counter-offer)</li><li>Wrote unit test suite for seller listing lifecycle and offer state machines</li></ul> | 6.5 |
| **Elio Le Dortz**<br>*(Task 3: Admin)* | <ul><li>Scaffolded domain models (`Admin`, `Report`, `Review`, `Notification`, `AccountStatus`)</li><li>Implemented **High** features: US-A01 (Seeded admin login), US-A03 (Manage all users with soft-delete safeguards preventing deletion with active deals), US-A07 (Manage all listings & takedown with bidder notification), US-A08 (Manage seller ratings: hide/unhide)</li><li>Implemented **Critical** feature: US-A02 (Listing review queue: approve to `Live` or reject with mandatory written reason)</li><li>Wrote unit test suite for admin review queue, moderation, and soft-delete safeguards</li></ul> | 6.5 |

---

### 2. Discussion of impediments and resolution

* **2.1. Feature Branch Integration & Merging Protocol**:
  * *Discussion*: Having completed the Critical and High features on branches, the team reviewed integration to prevent merge conflicts in shared models and `DummyDataService`.
  * *Resolution*: Conducted staged pull requests into `master` after verifying `./mvnw clean test` passed locally. Rebased all active feature branches on `origin/master` immediately following each merge.
* **2.2. Testing JavaFX UI controllers without headless display issues**:
  * *Discussion*: Automated test execution via `mvn test` in CI or headless grading environments fails if controllers directly invoke JavaFX UI toolkit threads.
  * *Resolution*: Decoupled presentation from business logic by encapsulating state changes, validation, and domain rules into pure Java service/domain classes (`DummyDataService`, model methods). Unit tests target business logic directly, guaranteeing fast execution and high branch coverage without JavaFX platform initialization overhead.
* **2.3. Shared notification broadcast and inter-portal event synchronization**:
  * *Discussion*: When a Seller updates an offer (accept/counter) or an Admin approves a listing, Buyer and Seller notification boards must reflect the update immediately.
  * *Resolution*: Standardized notification generation within `DummyDataService` using timestamped `Notification` records categorized by type (`Offer`, `Chat`, `System`, `Rating`).

---

### 3. Goals for the coming week (Sprint 2 Phase 2: Medium & Low Features + UI Polish)

By the next meeting, the team will implement the remaining **Medium** and **Low** priority features, harmonize UI styles across all three portals, and expand edge-case unit test coverage:

| Name | Tasks that will be worked on in the coming week |
| :--- | :--- |
| **Charles Dupouy**<br>*(Task 1: Buyer)* | <ul><li>Implement remaining **Medium & Low** features: US-B04 (Listing details with view count increment & report button), US-B08 (Buyer profile: edit name, residence, password), US-B09 (Buyer notification board with categorized alerts), US-B10 (Deal history searchable table)</li><li>Connect Buyer UI views with shared CSS styles in `styles.css`</li><li>Add edge-case unit tests to expand branch coverage toward $>70\%$</li></ul> |
| **Antoine Lepin**<br>*(Task 2: Seller)* | <ul><li>Implement remaining **Medium & Low** features: US-S06 (Seller profile: edit bio, meetup spots, password), US-S07 (Seller notification board), US-S08 (Seller statistics with JavaFX `BarChart` for views/offers and `PieChart` for deal breakdown)</li><li>Standardize card and dialog styling for Seller portal</li><li>Add unit tests for statistics calculation and review reply rules</li></ul> |
| **Elio Le Dortz**<br>*(Task 3: Admin)* | <ul><li>Implement remaining **Medium & Low** features: US-A04 (Admin profile: edit name & password), US-A05 (All Deals record system-wide audit table with filters), US-A06 (Admin notifications & resolving flagged reports)</li><li>Standardize table and moderation dialog styling for Admin portal</li><li>Run JaCoCo branch coverage verification across all modules to ensure course threshold ($>65\%$) is met</li></ul> |

---

### 4. Meeting adjournment and next meeting

The meeting was adjourned at 6:15 p.m.  
The next meeting will be held on **November 21, 2026 at 4:30 p.m.** in the Library Study Room / Discord.
