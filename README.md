# HKUST Marketplace — Frontend Demo / Skeleton

A complete **JavaFX + Maven** frontend-only demo of the HKUST Campus Marketplace platform.  
Designed as a **skeleton / starter code** for students to implement the full project requirements (backend, persistence, real business logic, etc.).

## Prerequisites

1. **JDK 17 or newer** (full JDK, not only JRE)
   - Download: https://adoptium.net/ (Temurin 17 or 21 recommended)
   - After install, open a **new** PowerShell and check:
     ```powershell
     java -version
     javac -version
     ```
   - Both commands must work. If `javac` is missing, you only have a JRE — install the JDK.

2. **Maven is optional** — this project includes the **Maven Wrapper** (`mvnw.cmd` on Windows).

## How to Run (Windows)

Open **PowerShell** or **Command Prompt** in the project folder  
(`...\HKUST-Marketplace\HKUST-Marketplace`):

```powershell
.\mvnw.cmd clean javafx:run
```

First run downloads Maven automatically (needs internet). Then the app window should open.

### Alternative: if you already installed Maven and added it to PATH

```powershell
mvn clean javafx:run
```

### Run from IntelliJ IDEA

1. Open the project folder as a Maven project (JDK 17 or newer as Project SDK)
2. Wait for dependencies to download
3. Pick a shared run configuration (stored in `.run/`) in the toolbar:
   - **Marketplace App** — starts the application
   - **All Tests** — runs every JUnit 5 test
   - **Maven Tests + JaCoCo** — `clean test`, coverage report in `target/site/jacoco/index.html`
   - **Maven Javadoc** — API docs in `target/site/apidocs/index.html`

To start the app without the run configuration, open `Launcher.java` → right-click → **Run**.
Do not run `MarketplaceApp.java` directly: a class extending `Application` fails with
*"JavaFX runtime components are missing"* when JavaFX is on the class path.

## Tests, Coverage and Javadoc (command line)

```bash
./mvnw clean test          # all tests + JaCoCo report in target/site/jacoco/index.html
./mvnw javadoc:javadoc     # API docs in target/site/apidocs/index.html
```

## Password Rules

Used by every registration and password-change screen (`utils/PasswordPolicy.java`).
A password must contain **all** of:

- at least **8 characters** (counted in Unicode code points)
- at least one **uppercase** letter
- at least one **lowercase** letter
- at least one **digit**

When a password is rejected, the user is told every rule it breaks.
Passwords are never stored in plaintext: `utils/PasswordHasher.java` stores a salted
**PBKDF2-HMAC-SHA256** hash (600,000 iterations, random 16-byte salt per password).

## Seller Registration (US-S01)

- Username is required and unique across buyer, seller and admin accounts, ignoring case
  (usernames of deleted accounts stay reserved); it is stored in lower case.
- Full name is required; password must follow the rules above; confirm password must match exactly.
- Preferred meet-up locations (any number) and bio are optional.
- All invalid fields are highlighted at once; on success the seller is sent to the Seller login.
- Business logic lives in `services/SellerRegistrationService.java` (no JavaFX), tested by
  `SellerRegistrationServiceTest`, including concurrent registrations of the same username.

## Seller Login (US-S02)

Checks run in this order, so each answer reveals no more than needed:

1. **Empty field** – "Both username and password are required."; only the empty fields are highlighted.
2. **Invalid credentials** – unknown username and wrong password get the *same* message
   ("The username or password you entered is incorrect."), so the user cannot tell which was wrong.
   Usernames ignore case and surrounding spaces; passwords are used exactly as typed.
3. **Wrong account type** – only once the password is correct: buyer and admin accounts are told
   to use their own portal (e.g. `alice / password1` or `admin / admin123` cannot log in here).
4. **Account deactivated / deleted** by the admin – the seller is told and no session starts.

On success a Seller session starts and the Seller dashboard opens (Enter submits the form).
`SessionManager.requireSeller()` is the service-side check every Seller-only function uses: it
refuses no session, a buyer or admin session, and a seller deactivated by the admin after logging in.
The Seller dashboard does not open without it.
Business logic lives in `services/SellerLoginService.java` (no JavaFX), tested by
`SellerLoginServiceTest` (all flows, concurrent logins) and `SessionManagerTest`.

## Create Listing (US-S03)

Seller sidebar → **Create Listing**. The seller first chooses **Sell**, **Lend** or **Give Away**;
the form of that type then appears. Changing the type discards the price or lending duration that
no longer applies. Every field is checked at once and each invalid field is highlighted with its reason.

| Field | Rule |
|-------|------|
| Item name | required, at most 80 characters (spaces around it are trimmed) |
| Description | required, at most 1000 characters |
| Category / Condition / Meet-up location | required, chosen from the predefined lists (the meet-up is preselected with the seller's first preferred location) |
| Price (Sell only) | required; more than HK$ 0 and at most HK$ 1,000,000, at most 2 decimals. `HK$`, `$`, spaces and thousands separators in groups of 3 are accepted (`HK$ 1,200.50`); `1e3`, `NaN` or `12,50` are refused. A free item should be a Give Away. |
| Lending duration (Lend only) | required; a whole number of seconds, minutes, hours, days or weeks, from **5 seconds** (the team's demo minimum, preset "10 s (Demo)") to **365 days** |
| Photos | **1 to 6**, PNG / JPEG / GIF / BMP, at most 10 MB each. A file is accepted only if both its extension and its content are an image (a renamed `.txt` is refused). Rejected and extra files are listed with the reason; the same file cannot be added twice. The first photo is the cover. |

On submission the listing is stored with status **Pending Review** in the logged-in seller's name,
the admin gets a "Listing Submissions" notification that opens it, and the seller is told it awaits
review. Buyers only see it once the admin approves it. **Cancel** asks for confirmation if something
was entered and stores nothing. A photo is stored as the absolute path of the chosen file (not copied).

Submitting requires a Seller session (`SessionManager.requireSeller()`). Business logic lives in
`services/CreateListingService.java` (no JavaFX); its static validation methods can be reused to
edit a listing (US-S04) or by the admin's "add listing on behalf of a seller" (US-A07). Tested by
`CreateListingServiceTest` (all flows, real image files, integration with the admin review and buyer
offers, concurrency) and `CreateListingModelsTest`.

## Manage My Listings (US-S04)

Seller sidebar → **My Listings** (the screen shown after login). Every listing of the seller is shown,
newest first, with its cover photo, status, terms, category, condition, meet-up location, views and
open offers. A **Rejected** listing shows the admin's reason; a listing **taken down** by the admin shows
the admin's reason too.

- **Edit** opens the listing form filled with the listing's details. The fields follow the same rules as
  Create Listing (see above); the type can be changed. Saving a **Live** or **Rejected** listing sends it
  back to **Pending Review**: it leaves the marketplace until the admin approves it again, the old rejection
  reason is cleared, and the admin gets a "Listing Submissions" notification. A listing already awaiting
  review stays Pending Review. Saving without changing anything changes nothing. **Cancel** asks for
  confirmation if something was changed.
- **Delete** asks for confirmation, then **soft-deletes** the listing: its status becomes `REMOVED` and it is
  marked as deleted by the seller (`Listing.isDeletedBySeller()`). It disappears from the marketplace and
  from My Listings, but its deals, reviews and chats keep their history.
- A listing that is **Reserved, Sold, Lent or Given**, or that has an active deal, can be neither edited nor
  deleted. A listing taken down by the admin cannot be edited, but can be deleted from the list.
- If the listing has **open offers** (pending offers, borrow requests, claims, or counter offers waiting for
  the buyer), Edit and Delete first **warn** the seller with the list of these offers. If the seller
  confirms, the offers are declined and each buyer is notified; if not, nothing changes. An offer that
  arrives after the warning is shown in a new warning instead of being declined silently.
- Edits and deletes are **atomic** with buyer offers and with offer acceptance (US-S05): when a seller
  accepts an offer and deletes the listing at the same time, exactly one of the two succeeds.

Seeded listings have no photos, so editing one requires adding at least one photo first.

Every action requires a Seller session (`SessionManager.requireSeller()`) and only works on the
seller's own listings. Business logic lives in `services/MyListingsService.java` (no JavaFX), tested by
`MyListingsServiceTest` (all flows, integration with the admin review and take-down, buyer offers and
offer handling, concurrency) and `MyListingModelsTest`.

## Manage Offers And Requests (US-S05)

Seller sidebar → **Offers & Requests** (the button shows how many requests are pending).
Pending offers, borrow requests and claims are grouped by listing, oldest first; the offers
the seller countered are listed below, waiting for the buyer's answer.

- **Accept** (after a confirmation summary) records an active deal that appears on the buyer's
  My Deals screen, sets the listing to **Reserved** (Sell, Give Away) or **Lent** (Lend), and
  **declines every other pending or countered offer** on that listing, notifying those buyers.
  Acceptance is **atomic**: if two acceptances on one listing happen at the same time, only the
  first succeeds. A borrow request whose period has already ended cannot be accepted (counter it instead).
- **Decline** with an optional reason (at most 300 characters), sent to the buyer. The listing stays Live.
- **Counter** (Sell and Lend only; claims cannot be countered):
  - price: positive, at most HK$ 1,000,000, rounded to the cent, different from the buyer's offer;
  - borrow dates: start not in the past (30 s tolerance), end after start, at least **5 seconds**
    (demo preset "10 s"), not longer than the listing's lending duration, different from the request.
  The buyer then accepts or declines the counter offer from My Deals.
- **Chat** opens the private thread with the buyer about that listing.
- An offer the buyer withdrew or that was already handled cannot be handled again; if the listing
  is no longer Live, or the buyer's account is no longer active, the offer is declined and the buyer notified.

Every action requires a Seller session (`SessionManager.requireSeller()`) and only works on the
seller's own listings. Business logic lives in `services/OfferManagementService.java` (no JavaFX),
tested by `OfferManagementServiceTest` (all flows, integration with the buyer side, concurrency)
and `OfferManagementModelsTest`.

## View Seller Notifications (US-S07)

Seller sidebar → **Notifications** (the button shows how many are unread). The board lists the seller's
notifications **newest first**, each with its category, timestamp, title and message; **unread** ones are
highlighted with a **NEW** badge. Without notifications, the screen says "No notifications".

The categories are those of use case 2.7. They are computed from the category each portal stores in the
notification, so the Buyer and Admin portals' notifications are not changed:

| Category | Notifications |
|----------|---------------|
| **Listing Review** | listing approved, rejected or taken down by the admin (the **admin's reason** is shown on the card), listing edited or published by the admin |
| **Offers And Requests** | new offer, borrow request or claim, offer withdrawn, counter offer answered, chat message from a buyer |
| **Reviews** | new rating or review, review hidden or flag dismissed by the admin |
| **Deals** | buyer marked a deal complete, borrowed item returned or auto-returned |
| **Announcements** | anything else, for example an account change made by the admin |

- **Filter** by category (with counts, e.g. `Offers And Requests (3 · 1 unread)`), show **unread only**, or
  **search** the title and message. The filter is kept while moving around the portal. **Mark all as read**
  marks every notification read.
- **Open** (or click the card) marks the notification read and shows what it is about: a listing on
  **My Listings**, a pending or countered offer on **Offers & Requests**, or a review on **Reviews & Feedback**,
  each highlighted. An offer already handled, and a deal, are shown in a details dialog, and a chat opens
  in the chat view. **Mark as read / unread** changes the state without opening.
- If the item no longer exists or can no longer be seen (a listing the seller deleted, a review hidden by the
  admin), the notification is still marked read, its message is shown and the seller is told that the item is
  no longer available. A notification without a linked item shows its message; for offers, listings and
  reviews in general, the dialog offers to open the matching screen.

Every action requires a Seller session (`SessionManager.requireSeller()`) and only reads or changes the
logged-in seller's notifications; a notification only opens an item that belongs to them. Business logic
lives in `services/SellerNotificationService.java` (no JavaFX), tested by `SellerNotificationServiceTest`
(all flows, notifications sent by the real Buyer and Admin flows, concurrency) and
`SellerNotificationModelsTest`.

To try it with the demo data, log in as `admin` and reject `Mini Fridge` (Review Pending Listings) with a
reason, then log in as `diana` / `password1`: the rejection is on the board with the admin's reason, and
**Open listing** shows the rejected listing on My Listings.

## Handle Reviews (US-S09)

Seller sidebar → **Reviews & Feedback**. The screen shows the seller's **average rating** and all the
reviews buyers wrote about them, **newest first**, each with the buyer, the listing, the stars (1 to 5),
the written review, the date, the seller's reply and whether it is flagged. Reviews **hidden by the admin**
are neither shown nor counted in the average (class diagram rule: hidden reviews are excluded from the
seller's rating). Without reviews, the screen says "No reviews yet" and the rating "No ratings yet".

- **Reply** opens a dialog to write a reply (at most 500 characters, trimmed). An empty reply is refused and
  the dialog stays open. The reply is saved under the review (`Review.reply`) and the buyer who wrote the
  review gets a "Seller Replied to Review" notification that opens the review. **Only one reply** is allowed
  per review and it cannot be changed: once answered, the button reads **Replied** and explains this.
- **Flag** opens a dialog to choose a reason: *Offensive language*, *Not related to the deal*, *False or
  misleading information*, *Shares personal information*, *Spam or advertising* or *Other* (details required),
  with optional details (at most 300 characters). The review is marked flagged (`Review.flagged`,
  `Review.flagReason`, e.g. `Offensive language: insults in the second sentence`) and the admin gets a
  **Flagged Reviews** notification linked to the review. The review **stays visible**, and counted, until the
  admin hides it or dismisses the flag (US-A08). A flagged review cannot be flagged again: the button reads
  **Flagged** and explains that it awaits the admin's decision. After the admin dismisses a flag, the review
  can be flagged again.
- A flagged review can still be answered, and an answered review can still be flagged.
- Replies and flags are **atomic**: if two replies (or two flags) on the same review are sent at the same
  time, only the first one is saved and only one notification is sent.
- **Cancel** in either dialog changes nothing.

Every action requires a Seller session (`SessionManager.requireSeller()`) and only works on reviews written
about the logged-in seller. Business logic lives in `services/ReviewHandlingService.java` (no JavaFX),
tested by `ReviewHandlingServiceTest` (all flows, integration with the buyer's rating and notifications and
with the admin's moderation, concurrency) and `ReviewHandlingModelsTest`.

To try it with the demo data, log in as `charlie` / `password1`: the review by `alice` already has a reply,
and the review by `bob` is already flagged in the seeded data.

### Run from VS Code

1. Install **Extension Pack for Java**
2. Open the folder
3. Run `MarketplaceApp.java`

## Demo Credentials

| Role   | Username  | Password   |
|--------|-----------|------------|
| Admin  | `admin`   | `admin123` |
| Buyer  | `alice`   | `password1`|
| Buyer  | `bob`     | `password1`|
| Seller | `charlie` | `password1`|
| Seller | `diana`   | `password1`|

## Features Implemented (UI Only)

### Shared
- Welcome screen with role selection (Buyer / Seller / Admin)
- HKUST branding (logo + colour palette: deep blue `#003974` + gold `#A68137`)
- Modern card-based UI, icons (Ikonli), consistent sidebars & top bars
- In-memory dummy data (no database / no network)

### Buyer Portal
- Registration, Login, Browse Marketplace, Make Offer / Borrow / Claim
- My Deals, Deal History, Favourites (placeholder), Notifications, Profile

### Seller Portal
- Registration, Login, Create Listing, My Listings
- Seller Stats (charts), Reviews & Feedback, Notifications, Profile

### Admin Portal
- Login only (seeded), Listing Review (approve/reject)
- Manage Users, All Listings, All Deals, Seller Ratings, Notifications, Profile

## Project Structure

```
HKUST-Marketplace/
├── mvnw / mvnw.cmd          ← Maven Wrapper (use these!)
├── pom.xml
├── README.md
├── .run/                    ← shared IntelliJ run configurations
└── src/
    ├── main/
    │   ├── java/com/hkust/marketplace/
    │   │   ├── MarketplaceApp.java
    │   │   ├── Launcher.java    ← IDE entry point
    │   │   ├── controllers/
    │   │   ├── models/
    │   │   ├── services/        ← business logic, testable without JavaFX
    │   │   └── utils/
    │   └── resources/
    │       ├── fxml/
    │       ├── css/styles.css
    │       └── images/hkust_logo.png
    └── test/java/com/hkust/marketplace/   ← JUnit 5 tests
```

## Troubleshooting

### `mvn` is not recognized
Use the wrapper instead:
```powershell
.\mvnw.cmd clean javafx:run
```

### `java` / `javac` not recognized
Install JDK 17+ from https://adoptium.net/ and **restart the terminal**.  
Make sure the installer option “Add to PATH” was checked.

### Build fails with JavaFX / DISPLAY errors
You need a normal desktop session (not a headless server). On Windows this is usually fine.

### To see the real error
```powershell
.\mvnw.cmd clean javafx:run -e
```
Copy the lines that start with `Exception in thread` or `Caused by`.

## For Students — Next Steps (Backend)

1. Replace `DummyDataService` with real repositories / REST clients
2. Hash passwords — never store plaintext (done for sellers with `PasswordHasher`; buyer and admin logins still to migrate)
3. Add JWT / session auth and enforce RBAC on the API
4. Persist data in a database
5. Photo upload, chat, auto-return for borrowed items, etc.

## Licence

Educational use — HKUST COMP project skeleton.
