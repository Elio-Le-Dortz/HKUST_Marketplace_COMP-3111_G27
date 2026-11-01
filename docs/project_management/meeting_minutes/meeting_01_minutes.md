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
| **Charles Dupouy** | <ul><li>Explored JavaFX skeleton project and Maven setup</li><li>Drafted 11 Use-Case specifications for Task 1 (Buyer Portal) in `specs_buyer.tex`</li><li>Analyzed validation rules for buyer registration and password security</li></ul> | 5.5 |
| **Antoine Lepin** | <ul><li>Drafted 9 Use-Case specifications for Task 2 (Seller Portal) in `specs_seller.tex`</li><li>Specified offer handling (accept, decline, counter-offer) and seller statistics requirements</li><li>Researched chart libraries in JavaFX (PieChart, BarChart)</li></ul> | 5.0 |
| **Elio Le Dortz** | <ul><li>Drafted 8 Use-Case specifications for Task 3 (Admin Portal) in `specs_admin.tex`</li><li>Designed the comprehensive team UML Class Diagram (`class_diagram.pdf`)</li><li>Integrated team use case diagrams in `main.tex`</li></ul> | 5.5 |

---

### 2. Discussion of impediments and resolution

* **2.1. Concurrency on Giveaway items**:
  * *Discussion*: If two buyers claim a free item simultaneously, both could assume they won it.
  * *Resolution*: We decided that Giveaway claims must be handled atomically (first-come, first-served). The listing immediately transitions status, and any subsequent concurrent claim fails gracefully with an alert.
* **2.2. Testing duration for borrowed items**:
  * *Discussion*: Real-world loans last days or weeks, making automated unit testing and TA live demos impractical.
  * *Resolution*: Set the minimum loan duration to 5 seconds for testing purposes to easily verify auto-return timers and self-return functions.
* **2.3. Data integrity upon user or listing deletion**:
  * *Discussion*: Hard-deleting users causes orphaned foreign keys in past deals, reviews, and chat history.
  * *Resolution*: Adopted a **soft-delete** strategy using `AccountStatus.Deleted` and `ListingStatus.Removed`. Deletion is strictly blocked if a user has active deals.

---

### 3. Goals for the coming week

| Name | Tasks that will be worked on in the coming week |
| :--- | :--- |
| **Charles Dupouy** | <ul><li>Set up JUnit 5 and JaCoCo test plugins in `pom.xml`</li><li>Implement domain models and unit tests for Task 1 (Buyer, Offer, Deal)</li><li>Implement buyer search & filter logic for Live listings</li></ul> |
| **Antoine Lepin** | <ul><li>Implement Seller, Listing, and Photo domain models matching the UML class diagram</li><li>Implement listing creation validation rules (dynamic by type)</li><li>Write unit tests for seller offer acceptance and counter-offer logic</li></ul> |
| **Elio Le Dortz** | <ul><li>Implement Admin, Report, Review, and Notification domain models</li><li>Implement listing approval/rejection and user soft-delete logic</li><li>Write unit tests for admin review moderation with branch coverage verification</li></ul> |

---

### 4. Meeting adjournment and next meeting

The meeting was adjourned at 6:00 p.m.  
The next meeting will be held on **October 31, 2026 at 4:30 p.m.** in the Library Study Room / Discord.
