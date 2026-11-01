# COMP 3111: Software Engineering
## Minutes of the 3rd Project Scrum Meeting — HKUST Marketplace
**Team**: G27  
**Date**: November 21, 2026  
**Time**: 4:30 p.m.  
**Place**: Library Study Room 4 / Discord  
**Attending**: Charles Dupouy, Antoine Lepin, Elio Le Dortz  
**Absent**: None  
**Recorder**: Elio Le Dortz  

---

### 1. Report on progress during the past week

The team conducted a full end-to-end integration and sprint review. All 28 use cases across the Buyer, Seller, and Admin portals are fully functional with a consistent JavaFX UI:

| Name | Tasks worked on in the past week | Total hours |
| :--- | :--- | :---: |
| **Charles Dupouy** | <ul><li>Finalized all 11 Buyer Portal use cases (US-B01 to US-B11) and FXML views</li><li>Implemented real-time private chat thread persistence with keyword search</li><li>Implemented self-return and background auto-return timer for borrowed items ($\ge 5$s)</li><li>Integrated post-deal seller review prompt and automated deal history updates</li><li>Achieved 78.2% branch coverage across Buyer domain and controller logic</li></ul> | 7.0 |
| **Antoine Lepin** | <ul><li>Finalized all 9 Seller Portal use cases (US-S01 to US-S09) and FXML views</li><li>Completed dynamic listing creation form with photo thumbnail preview and HKUST meetup dropdown</li><li>Implemented Offer Management workflow with counter-offer modal and real-time status updates</li><li>Integrated JavaFX `BarChart` (views and offers) and `PieChart` (deal breakdown) in Seller Statistics</li><li>Achieved 74.5% branch coverage across Seller services</li></ul> | 6.5 |
| **Elio Le Dortz** | <ul><li>Finalized all 8 Admin Portal use cases (US-A01 to US-A08) and FXML views</li><li>Implemented listing moderation queue with mandatory written rejection reasons sent to sellers</li><li>Implemented user management table with soft-deletion safeguards preventing orphaned active deals</li><li>Implemented All Deals audit ledger with multi-attribute filtering and ratings moderation</li><li>Achieved 76.8% branch coverage across Admin and core services</li></ul> | 6.5 |

---

### 2. Discussion of impediments and resolution

* **2.1. Concurrency verification for Giveaway claims**:
  * *Discussion*: Manual testing occasionally showed race conditions when two simulate-click threads attempted to claim the same free item simultaneously.
  * *Resolution*: Enforced synchronized atomic status checks in `DummyDataService.claimListing(...)`. Added a concurrency unit test verifying that when 10 threads attempt concurrent claims on a single giveaway listing, exactly 1 succeeds and 9 fail gracefully with informative alerts.
* **2.2. Visual consistency across all portals**:
  * *Discussion*: Certain buttons, card paddings, and font sizes in the Admin and Seller screens diverged slightly from the Buyer portal style guide.
  * *Resolution*: Consolidated all CSS classes into `src/main/resources/css/styles.css`. Standardized table styling, dialog headers, primary/secondary button states, and badge colours (`PENDING_REVIEW`, `LIVE`, `RESERVED`, `SOLD`) across the entire application.
* **2.3. JaCoCo code coverage threshold compliance**:
  * *Discussion*: Overall branch coverage hovered around 63%, slightly beneath the course requirement of $>65\%$.
  * *Resolution*: Added parameterized edge-case tests covering invalid password strength combinations, duplicate usernames, zero/negative pricing inputs, and corrupted search queries. Overall branch coverage rose to **76.5%**, well above the $>65\%$ threshold with **100% test pass rate**.

---

### 3. Goals for final delivery & release week

| Name | Tasks that will be worked on for final project release |
| :--- | :--- |
| **Charles Dupouy** | <ul><li>Document password strength rules, validation criteria, and setup steps in `README.md`</li><li>Perform final code cleanup, remove dead code, and verify all JavaDoc comments</li><li>Tag official release commit on `master` branch (`v1.0-release`)</li></ul> |
| **Antoine Lepin** | <ul><li>Export final Sprint 2 Burn-Down chart graphic from `sprint_2_burndown.csv`</li><li>Draft slide deck and script for the team video presentation</li><li>Verify consistent layout rendering on both Linux and Windows environments</li></ul> |
| **Elio Le Dortz** | <ul><li>Verify complete integration of all 28 use cases against the course grading rubric</li><li>Record and edit the 10-minute demonstration video highlighting all 3 portals and concurrency edge cases</li><li>Package final ZIP submission archive conforming to assignment guidelines</li></ul> |

---

### 4. Meeting adjournment and final retrospective

The meeting was adjourned at 6:00 p.m.  
The team concluded the Scrum cycle with all backlog tasks completed on schedule (0 hours remaining on burn-down). Final deliverable packaging and video submission will take place prior to the November 29 deadline.
