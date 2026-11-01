# COMP 3111: Software Engineering
## Minutes of the 1st Project Scrum Meeting — HKUST Marketplace
**Team**: G27  
**Date**: October 24, 2026  
**Time**: 4:30 p.m.  
**Place**: Library Study Room 4 / Discord  
**Attending**: Charles Dupouy, Antoine Lepin, Elio Le Dortz  
**Absent**: None  
**Recorder**: Charles Dupouy  

---

### 1. Report on progress during the past week

| Name | Tasks worked on in the past week | Total hours |
| :--- | :--- | :---: |
| **Charles Dupouy** | <ul><li>Explored JavaFX skeleton project and Maven setup</li><li>Drafted 11 Use-Case specifications for Task 1 (Buyer Portal) in `specs_buyer.tex`</li><li>Analyzed validation rules for buyer registration and password security criteria</li></ul> | 5.5 |
| **Antoine Lepin** | <ul><li>Drafted 9 Use-Case specifications for Task 2 (Seller Portal) in `specs_seller.tex`</li><li>Specified offer handling (accept, decline, counter-offer) and seller statistics requirements</li><li>Researched chart libraries in JavaFX (PieChart, BarChart)</li></ul> | 5.0 |
| **Elio Le Dortz** | <ul><li>Drafted 8 Use-Case specifications for Task 3 (Admin Portal) in `specs_admin.tex`</li><li>Designed the comprehensive team UML Class Diagram (`class_diagram.pdf`)</li><li>Integrated team use case diagrams in `main.tex`</li></ul> | 5.5 |

---

### 2. Discussion of impediments and resolution

* **2.1. Concurrency on Giveaway items**:
  * *Discussion*: If two buyers claim a free item simultaneously, both could assume they won it.
  * *Resolution*: We decided that Giveaway claims must be handled atomically (first-come, first-served) using synchronized checks in `DummyDataService`. The listing immediately transitions status to `Given`, and subsequent concurrent claims fail gracefully.
* **2.2. Testing duration for borrowed items**:
  * *Discussion*: Real-world loans last days or weeks, making automated unit testing and TA live demos impractical.
  * *Resolution*: Set the minimum loan duration to 5 seconds for testing/demo purposes to easily verify auto-return timers and self-return functions.
* **2.3. Data integrity upon user or listing deletion**:
  * *Discussion*: Hard-deleting users causes orphaned foreign keys in past deals, reviews, and chat history.
  * *Resolution*: Adopted a **soft-delete** strategy using `AccountStatus.Deleted` and `ListingStatus.Removed`. Deletion is strictly blocked if a user has active deals.
* **2.4. Git Collaboration & Branching Strategy**:
  * *Discussion*: Debated whether members should work on long-lived personal branches (`dev-buyer`, `dev-seller`, `dev-admin`) or feature branches.
  * *Resolution*: Long-lived branches risk catastrophic merge conflicts ("Merge Hell") due to shared models and service classes. The team agreed to establish a shared foundation on `master` first (domain models and JUnit 5/JaCoCo configuration), and then develop features on short-lived feature branches with frequent local testing and integration into `master`.
* **2.5. Development Prioritization & Test-Driven Approach**:
  * *Discussion*: How to phase the 28 user stories across the 3 scrum meetings to avoid an end-of-sprint rush.
  * *Resolution*: Phased approach agreed:
    * **Sprint 2 Phase 1 (Meeting 1 $\rightarrow$ Meeting 2)**: Implement all **Critical** and **High** priority features on respective branches with companion unit tests.
    * **Sprint 2 Phase 2 (Meeting 2 $\rightarrow$ Meeting 3)**: Implement the remaining **Medium** and **Low** priority features (statistics charts, history, profiles, notifications) and harmonize UI styles.
    * **Final Phase (Meeting 3 $\rightarrow$ Release)**: Whole system review, edge-case test verification, demonstration video preparation, and final delivery packaging.

---

### 3. Goals for the coming week (Sprint 2 Phase 1: Critical & High Features)

By the next meeting, every member will have implemented the **Critical** and **High** priority features within their respective feature branches, accompanied by unit tests to verify correctness and establish early JaCoCo branch coverage:

| Name | Tasks that will be worked on in the coming week |
| :--- | :--- |
| **Charles Dupouy**<br>*(Task 1: Buyer)* | <ul><li>Scaffold shared domain models and configure JUnit 5 / JaCoCo plugins in `pom.xml` on `master`</li><li>Implement **High** priority features: US-B01 (Registration & Hashing), US-B02 (Buyer Login), US-B03 (Browse & Filters), US-B06 (Chat Thread), US-B11 (Rate Seller)</li><li>Implement **Critical** priority features: US-B05 (Act on Listing: Price Offer, Borrow Request $\ge 5$s, Atomic Claim) and US-B07 (My Deals & Auto-Return)</li><li>Write unit tests for Buyer registration rules, search/filters, and offer workflows</li></ul> |
| **Antoine Lepin**<br>*(Task 2: Seller)* | <ul><li>Implement **High** priority features: US-S01 (Seller Registration), US-S02 (Seller Login), US-S03 (Create Listing dynamic form: Sell/Lend/Giveaway), US-S04 (My Listings Screen), US-S09 (Review Handling: reply/flag)</li><li>Implement **Critical** priority feature: US-S05 (Manage Offers: accept, decline with reason, counter-offer)</li><li>Write unit tests for listing status lifecycle, form validations, and offer transitions</li></ul> |
| **Elio Le Dortz**<br>*(Task 3: Admin)* | <ul><li>Implement **High** priority features: US-A01 (Seeded Admin Login), US-A03 (Manage All Users with soft-delete safeguards), US-A07 (Manage All Listings & takedown with bidder notifications), US-A08 (Manage Seller Ratings: hide/unhide)</li><li>Implement **Critical** priority feature: US-A02 (Listing Review Queue: approve/reject with mandatory written reason)</li><li>Write unit tests for admin review queue logic, user deactivation rules, and ratings moderation</li></ul> |

---

### 4. Meeting adjournment and next meeting

The meeting was adjourned at 6:15 p.m.  
The next meeting will be held on **October 31, 2026 at 4:30 p.m.** in the Library Study Room / Discord.
