# COMP 3111 — Sprint 2 Backlog & Burn-Down Guide
**Team**: G27 (HKUST Marketplace)  
**Sprint Duration**: 4 Weeks (November 2 – November 29, 2026)  
**Total Estimated Work**: 226 Hours  

---

## 1. What is Sprint 2 in COMP 3111?

In Agile / Scrum methodology, a **Sprint** is a fixed time-boxed development cycle.

In COMP 3111, the semester is split into phases:
* **Activity 1 (Sprint 0)**: Planning, Requirements analysis, Use-Case Specifications, and UML Class Diagram design.
* **Sprint 1**: Foundational setup (basic project structure, initial skeleton exploration, toolchain setup).
* **Sprint 2 (The Main Implementation Sprint)**:
  * This is the **core development sprint** where the full functional application is implemented.
  * All 28 use cases across Buyer, Seller, and Admin portals are built, connected, and tested.
  * Quality thresholds (**100% test pass rate** and **>65% branch coverage**) must be achieved.
  * The final deliverable PDF requires the **Sprint 2 Backlog** and the **Burn-Down Chart** tracking this specific sprint.

---

## 2. Sprint 2 Backlog (Tasks & Allocation)

### Task 1: Buyer Portal (Owner: Charles Dupouy)
| Task ID | User Story / Description | Est. Hours | Priority | Acceptance Criteria |
| :--- | :--- | :---: | :---: | :--- |
| **US-B01** | **Registration & Hashing**: Secure registration with password rules and duplicate username validation. | 6h | High | User account saved with hashed password; fails if duplicate or weak password. |
| **US-B02** | **Buyer Login**: Authenticate credentials and enforce Buyer role access. | 5h | High | Only active Buyer accounts can enter Buyer dashboard; invalid credentials show error. |
| **US-B03** | **Browse & Filters**: Browse live listings with category, type, price, and condition filters. | 8h | High | Real-time filtering and keyword search; empty state displayed when no matches. |
| **US-B04** | **Listing Details**: View listing information, photos, and increment view count. | 6h | Medium | Opening detail increments view count; seller rating and report button displayed. |
| **US-B05** | **Act on Listing**: Submit Price Offer, Borrow Request ($\ge 5$s), and atomic Claim. | 10h | Critical | Giveaway claim is strictly atomic (only 1st succeeds); borrow dates validated. |
| **US-B06** | **Chat Thread**: Private chat with seller, search history, and formal offer attachment. | 10h | High | Messages persist chronologically; search highlights matching keywords. |
| **US-B07** | **My Deals & Auto-Return**: Manage active offers, self-return items, and auto-return when expired. | 12h | Critical | Loan expires and auto-returns listing to `Live`; complete deal triggers rating prompt. |
| **US-B08** | **Buyer Profile**: Edit full name, residence hall, and change password with current verification. | 4h | Low | Prevents empty name; verifies old password before applying new one. |
| **US-B09** | **Buyer Notifications**: Categorized board for offers, chat, reminders, and reviews. | 6h | Medium | Notifications grouped by category, timestamped, and unread items highlighted. |
| **US-B10** | **Deal History**: Searchable and filterable history of completed deals. | 6h | Medium | Displays completed and returned deals; filterable by date and seller. |
| **US-B11** | **Rate Seller**: Submit 1–5 star rating and comment after completed deal. | 7h | High | Recalculates seller average rating; duplicate rating for same deal prevented. |

---

### Task 2: Seller Portal (Owner: Antoine Lepin)
| Task ID | User Story / Description | Est. Hours | Priority | Acceptance Criteria |
| :--- | :--- | :---: | :---: | :--- |
| **US-S01** | **Seller Registration**: Register seller with bio and preferred meetup points. | 5h | High | Enforces username uniqueness and password validation rules. |
| **US-S02** | **Seller Login**: Authenticate credentials and enforce Seller role access. | 4h | High | Buyer or Admin credentials cannot log into Seller portal. |
| **US-S03** | **Create Listing**: Post Sell, Lend, or Giveaway listing with 1–6 photos. | 10h | High | Sets status to `PendingReview`; notifies admin; validates positive price/duration. |
| **US-S04** | **My Listings Screen**: Manage listings, view admin rejection reasons, edit and delete. | 8h | High | Editing Live listing sends back to `PendingReview`; deletion warns if offers pending. |
| **US-S05** | **Manage Offers**: Accept, decline with reason, or submit counter-offers. | 12h | Critical | Acceptance atomically reserves listing and declines other pending offers. |
| **US-S06** | **Seller Profile**: Edit bio, meetup locations, full name, and password. | 4h | Low | Updates reflected across all posted listings. |
| **US-S07** | **Seller Notifications**: Alerts for approvals, rejections, incoming offers, and reviews. | 6h | Medium | Shows admin rejection reason; deep links to relevant offer/deal. |
| **US-S08** | **Seller Statistics**: Visual charts (bar chart for views/offers, pie chart for deals). | 8h | Medium | JavaFX charts accurately reflect live data. |
| **US-S09** | **Review Handling**: View buyer reviews, post a single reply, or flag inappropriate reviews. | 7h | High | Flagged review notified to admin; only one reply allowed per review. |

---

### Task 3: Admin Portal (Owner: Elio Le Dortz)
| Task ID | User Story / Description | Est. Hours | Priority | Acceptance Criteria |
| :--- | :--- | :---: | :---: | :--- |
| **US-A01** | **Seeded Login**: Hardcoded admin credential authentication (no registration). | 4h | High | Only seeded admin can log into Admin Portal. |
| **US-A02** | **Listing Review Queue**: Approve listings to `Live` or reject with written reason. | 8h | Critical | Mandatory rejection reason stored; seller notified. |
| **US-A03** | **User Management**: View, edit, deactivate, reactivate, or soft-delete users. | 10h | High | Deletion blocked if active deals exist; soft-delete preserves historical logs. |
| **US-A04** | **Admin Profile**: Edit admin name and password. | 4h | Low | Verifies password strength and confirmation. |
| **US-A05** | **All Deals Record**: Read-only oversight table of all transactions system-wide. | 6h | Medium | Filterable by date, type, status, and usernames. |
| **US-A06** | **Admin Notifications**: Monitor submissions, flagged reviews, reports, and user audit log. | 6h | Medium | Open reports can be marked `Resolved`. |
| **US-A07** | **Manage All Listings**: Add listing on behalf of seller, edit, or take down with reason. | 10h | High | Take-down atomically declines active offers and notifies buyers. |
| **US-A08** | **Manage Seller Ratings**: Hide/unhide reviews and dismiss flags. | 8h | High | Hidden review excluded from seller average rating calculation. |

---

### Team-Wide Quality Assurance
| Task ID | Description | Est. Hours | Owner | Acceptance Criteria |
| :--- | :--- | :---: | :---: | :--- |
| **QA-01** | **Unit Testing Suite**: Thorough test cases for models and services. | 16h | All | **100% test pass rate**; **>65% branch coverage** via JaCoCo. |
| **QA-02** | **JavaDoc & Documentation**: Full docstrings on all methods and classes. | 10h | All | No missing `@param`, `@return`, or `@throws` tags. |

---

## 3. Burn-Down Chart Progression (Hours Remaining)

| Milestone | Week 1 (Nov 02) | Week 2 (Nov 09) | Week 3 (Nov 16) | Week 4 (Nov 23) | Week 4 End (Nov 29) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Ideal Remaining Hours** | 226 | 169.5 | 113 | 56.5 | 0 |
| **Actual Remaining Hours** | **226** | **145** | **39** | **0** | **0** |

The data points above are saved in [`sprint_2_burndown.csv`](sprint_2_burndown.csv), which you can open in Excel, Google Sheets, or Canva to generate the visual line chart required for your PDF submission.
