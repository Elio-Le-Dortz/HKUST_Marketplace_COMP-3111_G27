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

The team achieved a major milestone: **all 28 user stories across all three portals (Critical, High, Medium, and Low priorities) have been fully implemented, unit-tested, and integrated into `master`**:

| Name | Tasks worked on in the past week | Total hours |
| :--- | :--- | :---: |
| **Charles Dupouy**<br>*(Task 1: Buyer Portal)* | <ul><li>Completed and merged all 11 Buyer user stories (US-B01 to US-B11) via PRs #2–#12</li><li>Implemented registration with PBKDF2 hashing, login role-checking, multi-attribute browse filters, listing details with view counter, offer and borrow requests, atomic giveaway claim, private chat persistence, auto-return countdown timer, deal history, and seller reviews</li><li>Maintained 100% test pass rate with $>88\%$ branch coverage on core services</li></ul> | 8.0 |
| **Antoine Lepin**<br>*(Task 2: Seller Portal)* | <ul><li>Completed and merged all 9 Seller user stories (US-S01 to US-S09) via PRs #1, #18, #20, #25–#30</li><li>Implemented seller registration, login, dynamic listing creation (Sell/Lend/Giveaway with photos), My Listings management, offer acceptance/countering, seller profile with listing propagation, notifications board, JavaFX BarChart/PieChart statistics, and review replies/flags</li><li>Authored comprehensive service and model unit test suites</li></ul> | 8.5 |
| **Elio Le Dortz**<br>*(Task 3: Admin Portal)* | <ul><li>Completed and merged all 8 Admin user stories (US-A01 to US-A08) via PRs #13–#17, #19, #22, #24</li><li>Implemented seeded admin login, listing approval/rejection queue with mandatory written reasons, user management with soft-delete safeguards, profile editing, all deals record table, admin notifications & report resolution, all listings management & takedowns, and seller ratings moderation</li><li>Maintained rigorous regression tests across all administrative workflows</li></ul> | 8.0 |

* **Overall Test Suite Status**: **1,600 tests passed, 0 failures, 0 errors**.

---

### 2. Discussion of impediments and resolution

* **2.1. Local Database Persistence Requirement (Course Mandatory Deliverable)**:
  * *Discussion*: All portals currently operate against the in-memory data layer (`DummyDataService`). Although functional for live demos within a single session, the COMP 3111 course specification requires persistent storage across application restarts.
  * *Resolution*: We decided to implement an embedded local relational database using **SQLite** (`sqlite-jdbc`). Charles Dupouy will lead the database architecture, creating relational tables and repository DAOs while preserving the existing service contracts so controllers remain unaffected.
* **2.2. Cross-Teammate Code Review & Refactoring**:
  * *Discussion*: Since each developer focused primarily on their own track, teammates must review each other’s code to identify redundant logic, standardize naming conventions, verify error handling, and ensure documentation integrity.
  * *Resolution*: Established a peer review rotation: Charles reviews Seller code, Antoine reviews Admin code, and Elio reviews Buyer code. Refactoring will focus on decoupling, code clarity, and JavaDoc completeness.
* **2.3. End-to-End Scenario Testing & Defect Tracking**:
  * *Discussion*: Unit tests verify isolated business rules, but full cross-portal integration requires realistic user simulations to catch UI layout bugs, race conditions, or navigation hiccups.
  * *Resolution*: Defined 6 comprehensive end-to-end scenarios, assigning 3 to Antoine and 3 to Elio to thoroughly validate all workflows and log any dysfunction before the final release.

---

### 3. Goals for the coming week

| Name | Tasks that will be worked on in the coming week |
| :--- | :--- |
| **Charles Dupouy**<br>*(Task 1: Buyer & Database Lead)* | <ul><li>Integrate SQLite database dependency (`sqlite-jdbc`) into `pom.xml`</li><li>Design schema (tables for Users, Listings, Photos, Offers, Deals, Reviews, Notifications, Reports) and implement database persistence layer replacing in-memory storage</li><li>Write automated unit and integration tests for SQLite CRUD operations and persistence across restarts</li><li>Perform peer code review and refactoring on teammates' tracks</li></ul> |
| **Antoine Lepin**<br>*(Task 2: Seller & QA)* | <ul><li>Perform peer code review and refactoring on teammates' tracks</li><li>Execute **3 assigned End-to-End Test Scenarios**:<ul><li>**Scenario 1 (Listing Lifecycle)**: Seller posts item $\rightarrow$ Admin approves $\rightarrow$ Buyer browses & views details.</li><li>**Scenario 3 (Offer & Chat Negotiation)**: Buyer makes price offer $\rightarrow$ Seller counters $\rightarrow$ chat thread messaging.</li><li>**Scenario 5 (Feedback & Analytics)**: Buyer rates seller $\rightarrow$ Seller views stats charts & replies $\rightarrow$ Admin moderates review.</li></ul></li><li>Document, log, and resolve any visual or functional defects identified</li></ul> |
| **Elio Le Dortz**<br>*(Task 3: Admin & QA)* | <ul><li>Perform peer code review and refactoring on teammates' tracks</li><li>Execute **3 assigned End-to-End Test Scenarios**:<ul><li>**Scenario 2 (Giveaway Concurrency)**: Simultaneous buyer claims on giveaway item, verifying strict atomic first-come first-served handling.</li><li>**Scenario 4 (Borrow & Auto-Return)**: Borrow request with 5-second minimum duration, verifying self-return and background timer auto-return to `LIVE`.</li><li>**Scenario 6 (User Moderation & Takedown)**: Admin account deactivation, deletion blocked with active deals, and listing takedown with bidder notifications.</li></ul></li><li>Document, log, and resolve any visual or functional defects identified</li></ul> |

---

### 4. Meeting adjournment and next meeting

The meeting was adjourned at 6:00 p.m.  
The next meeting (Meeting 3 — Final Release Review & Video Demo Rehearsal) will be held on **November 20, 2026 at 4:30 p.m.** in the Library Study Room / Discord.
