# COMP 3111: Software Engineering
## Minutes of the 2nd Project Scrum Meeting — HKUST Marketplace
**Team**: G27  
**Date**: November 6, 2026  
**Time**: 4:30 p.m.  
**Place**: Library Study Room 4 / Discord  
**Attending**: Charles Dupouy, Antoine Lepin, Elio Le Dortz  
**Absent**: None  
**Recorder**: Antoine Lepin  

---

### 1. Report on progress during the past week

All three team members have successfully implemented, tested, and merged all 28 user stories across all portals into the `master` branch, covering all **Critical**, **High**, **Medium**, and **Low** priority features:

| Name | Tasks worked on in the past week | Total hours |
| :--- | :--- | :---: |
| **Charles Dupouy**<br>*(Task 1: Buyer)* | <ul><li>Finalized and merged all 11 Buyer user stories (US-B01 to US-B11) into `master`</li><li>Implemented Critical features: US-B05 (Price offer, borrow request $\ge 5$s, atomic giveaway claim) and US-B07 (My Deals & auto-return timer)</li><li>Implemented Medium & Low features: US-B04 (Listing details view counter), US-B08 (Profile editing), US-B09 (Notification board), US-B10 (Deal history table)</li><li>Built comprehensive unit test suite for Buyer services (100% pass rate)</li></ul> | 8.5 |
| **Antoine Lepin**<br>*(Task 2: Seller)* | <ul><li>Finalized and merged all 9 Seller user stories (US-S01 to US-S09) into `master`</li><li>Implemented Critical feature: US-S05 (Manage offers: accept, decline, counter-offer)</li><li>Implemented Medium & Low features: US-S06 (Seller profile & renames), US-S07 (Seller notifications), US-S08 (Statistics with JavaFX BarChart & PieChart), US-S09 (Review replies & flagging)</li><li>Authored unit test suite for Seller services and profile logic</li></ul> | 8.0 |
| **Elio Le Dortz**<br>*(Task 3: Admin)* | <ul><li>Finalized and merged all 8 Admin user stories (US-A01 to US-A08) into `master`</li><li>Implemented Critical feature: US-A02 (Listing review queue with mandatory written rejection reasons)</li><li>Implemented Medium & Low features: US-A04 (Admin profile), US-A05 (All Deals system audit ledger), US-A06 (Notifications & report resolution), US-A07 (Manage all listings), US-A08 (Manage seller ratings)</li><li>Authored unit test suite for Admin services and user moderation</li></ul> | 8.0 |

**Current Quality Status**:
* **1,600+ unit tests** passing with **0 failures and 0 errors**.
* Branch coverage across domain models and business services exceeds **83%–88%** (surpassing the course requirement of $>65\%$).

---

### 2. Discussion of impediments and resolution

* **2.1. In-Memory Data Reset & Database Requirement**:
  * *Discussion*: While `DummyDataService` allowed rapid parallel development and headless testing, all data resets upon closing the application. Course requirements mandate persistent storage across sessions for accounts, listings, deals, and chat messages.
  * *Resolution*: Agreed to implement a local database using **SQLite** (`sqlite-jdbc`). To prevent breaking existing controller and service code, we will maintain the existing `DummyDataService` method signatures and adapt them into a persistent repository layer (`DatabaseService` / DAO).
* **2.2. Code Review and Cross-Portal Refactoring**:
  * *Discussion*: Now that each member's track is in `master`, code styles, helper utilities, and error message formats differ slightly between portals.
  * *Resolution*: Conduct peer code reviews across tracks (Charles reviews Seller, Antoine reviews Admin, Elio reviews Buyer). Refactor shared components into common utility classes (`ValidationUtils`, `DialogUtils`, and unified CSS styles in `styles.css`).
* **2.3. End-to-End Multi-User Scenario Testing**:
  * *Discussion*: Unit tests isolate individual components, but full user interaction flows (e.g. Buyer offers $\rightarrow$ Seller counters $\rightarrow$ Admin moderates $\rightarrow$ Buyer completes) require end-to-end manual verification across all roles.
  * *Resolution*: Formulated **6 comprehensive scenarios** (the 5 primary interaction scenarios + 1 full User Moderation & Soft-Delete scenario), allocated so each team member thoroughly tests 3 scenarios to identify and log any dysfunctions or edge-case bugs.

---

### 3. Goals for the coming week

| Name | Tasks that will be worked on in the coming week |
| :--- | :--- |
| **Charles Dupouy**<br>*(Task 1: Buyer)* | <ul><li>**Code Review**: Review Seller portal code (Antoine's track); refactor shared dialog and session handling logic</li><li>**Scenario Testing (3 scenarios)**: Execute Scenario 1 (Full Listing Lifecycle), Scenario 2 (Concurrency & Giveaway Claim), and Scenario 3 (Offer Negotiation & Chat); document any UI or status bugs</li><li>**Database Integration**: Implement SQLite tables and CRUD persistence for Buyer entities (Users, Offers, Chat Threads, Messages)</li></ul> |
| **Antoine Lepin**<br>*(Task 2: Seller)* | <ul><li>**Code Review**: Review Admin portal code (Elio's track); refactor shared card and table UI layouts</li><li>**Scenario Testing (3 scenarios)**: Execute Scenario 3 (Offer Negotiation & Chat), Scenario 4 (Borrowing & Auto-Return Timer), and Scenario 5 (Seller Reviews & Stats Charts); document any UI or status bugs</li><li>**Database Integration**: Implement SQLite tables and CRUD persistence for Seller entities (Listings, Photos, Seller Profiles)</li></ul> |
| **Elio Le Dortz**<br>*(Task 3: Admin)* | <ul><li>**Code Review**: Review Buyer portal code (Charles's track); standardize exception handling and error dialogs</li><li>**Scenario Testing (3 scenarios)**: Execute Scenario 5 (Seller Reviews & Stats Charts), Scenario 6 (User Moderation & Soft-Delete Lifecycle), and Scenario 1 (Full Listing Lifecycle); document any UI or status bugs</li><li>**Database Integration**: Set up SQLite schema initialization, connection management, and persistence for Admin entities (Reports, Reviews, System Deals Ledger)</li></ul> |

---

### 4. Meeting adjournment and next meeting

The meeting was adjourned at 6:15 p.m.  
The next meeting (Meeting 3: Final Sprint Review & Delivery Preparation) will be held on **November 21, 2026 at 4:30 p.m.** in the Library Study Room / Discord.
