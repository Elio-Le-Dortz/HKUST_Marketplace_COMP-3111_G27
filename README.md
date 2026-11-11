# HKUST Marketplace — COMP 3111 Group 27

A complete **JavaFX + Maven** full-stack desktop application of the HKUST Campus Marketplace platform.  
Implements all 28 use cases across the **Buyer**, **Seller**, and **Admin** portals with clean layered architecture, rich UI, session-based role access control (RBAC), thread-safe concurrency handling, and an extensive JUnit 5 test suite (1,761 tests passing).

## Prerequisites

1. **JDK 17 or newer** (full JDK, not only JRE)
   - Download: https://adoptium.net/ (Temurin 17 or 21 recommended)
   - After install, open a **new** terminal / PowerShell and check:
     ```bash
     java -version
     javac -version
     ```
   - Both commands must work. If `javac` is missing, you only have a JRE — install the full JDK.

2. **Maven is optional** — this project includes the **Maven Wrapper** (`mvnw` on Linux/macOS, `mvnw.cmd` on Windows).

## How to Run

### Command Line (Windows)
Open **PowerShell** or **Command Prompt** in the project directory:
```powershell
.\mvnw.cmd clean javafx:run
```

### Command Line (Linux / macOS)
Open a terminal in the project directory:
```bash
./mvnw clean javafx:run
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
*(Do not run `MarketplaceApp.java` directly: classes extending `Application` require JavaFX modules on the module path; `Launcher.java` avoids this).*

### Run from VS Code
1. Install **Extension Pack for Java**
2. Open the project root folder
3. Open `Launcher.java` and click **Run**, or run `./mvnw javafx:run` from the integrated terminal.

## Tests, Coverage and Javadoc (command line)

```bash
./mvnw clean test          # all 1,761 tests + JaCoCo report in target/site/jacoco/index.html
./mvnw javadoc:javadoc     # API documentation in target/site/apidocs/index.html
```

## Demo Credentials

| Role   | Username  | Password   | Description |
|--------|-----------|------------|-------------|
| Admin  | `admin`   | `admin123` | Seeded administrator account |
| Buyer  | `alice`   | `password1`| Active buyer with active and completed deals |
| Buyer  | `bob`     | `password1`| Active buyer participating in chats & offers |
| Seller | `charlie` | `password1`| Active seller with listings, offers, and sales |
| Seller | `diana`   | `password1`| Active seller with lending and giveaway items |

> [!NOTE]
> - **Seeded Admin Account**: The seeded administrator username is `admin` (the assignment brief and domain model gave `admin@ust.hk` as an illustrative example: *"e.g., admin@ust.hk"*). Log in to the Admin Portal using `admin` / `admin123`.
> - **Demo Passwords**: Demo passwords (`password1`, `admin123`) were pre-seeded for development and testing, preceding the interactive password strength validator.
> - **Listing Photos & Demo Data Limitation**: In accordance with the domain model (`Listing 1 *-- 1..6 Photo`), listings created via the Seller Portal (US-S02) or Admin Portal (US-A07) require 1 to 6 photos uploaded from the local filesystem. For static in-memory seed listings (`DummyDataService`), no photos are attached to avoid requiring local filesystem writes across different platforms. Seeded listings display dedicated "No Photo" placeholders.

## Password Rules & Security

Used by every registration and password-change screen (`utils/PasswordPolicy.java`).
A password must contain **all** of:

- at least **8 characters** (counted in Unicode code points)
- at least one **uppercase** letter
- at least one **lowercase** letter
- at least one **digit**

When a password is rejected, the user is told every rule it breaks.

### Password Hashing Schemes
Passwords are never stored in plaintext:
- **Seller Portal**: `utils/PasswordHasher.java` stores a salted **PBKDF2-HMAC-SHA256** hash (600,000 iterations, random 16-byte salt per password).
- **Buyer & Admin Portals / Profiles**: `utils/PasswordUtil.java` stores a salted **SHA-256** hash (`[salt_hex]:[hash_hex]`).

---

# Task 1: Buyer Portal (Owner: Charles Dupouy)

## Buyer Registration (US-B01)
- **Validation**: Username is required, at least 3 characters long, contains no whitespace, and is unique across all user roles (case-insensitive); full name and campus residence hall are required; password must satisfy the password policy; confirm password must match exactly.
- **Security**: Passwords are securely hashed with a random 16-byte salt using SHA-256 (`PasswordUtil.hashPassword`).
- **Feedback**: All invalid fields are validated simultaneously with descriptive error messages. On success, the user is redirected to Buyer login.
- **Architecture**: Handled by `DummyDataService.registerBuyer(...)`, tested by `BuyerRegistrationTest` (boundary validations, duplicate username rejection, whitespace sanitization).

## Buyer Login (US-B02)
- **Validation Flow**:
  1. **Empty fields check**: "Please enter both username and password."
  2. **Invalid credentials**: Non-existent username and wrong password return the identical error (`LoginResult.MSG_INVALID_CREDENTIALS`), preventing username enumeration attacks.
  3. **Role-Based Access Control**: Valid seller or admin credentials attempting login are refused and instructed to use their respective portals (`MSG_WRONG_PORTAL_SELLER`, `MSG_WRONG_PORTAL_ADMIN`).
  4. **Deactivation check**: Accounts deactivated by an administrator are blocked from logging in.
- **Session**: On successful authentication, a Buyer session is established via `SessionManager.login(user)`.
- **Architecture**: Handled by `BuyerLoginService` / `DummyDataService.loginBuyer(...)`, tested by `BuyerLoginTest` (all normal/alternative/exception flows and concurrent logins).

## Marketplace Browse & Filtering (US-B03)
- **Live Catalog**: Real-time display of active listings in `LIVE` status across the HKUST campus.
- **Multi-Attribute Filters**:
  - **Category**: Textbooks, Electronics, Clothing, Furniture, Kitchen, Sports, Other, or All.
  - **Listing Type**: Sell, Lend, Giveaway, or All.
  - **Condition**: Brand New, Like New, Good, Fair, Poor, or All.
  - **Price Range**: Min/max numeric filters for sale items.
- **Keyword Search**: Substring search against item title and description.
- **Sorting**: Newest first, Price: Low to High, Price: High to Low.
- **Architecture**: Business logic in `services/ListingFilterService.java` and `DummyDataService.getFilteredLiveListings(...)`, tested by `ListingFilterServiceTest` and `BuyerBrowseFilterTest`.

## Listing Details & View Count Tracker (US-B04)
- **Item Details**: Displays title, description, category, condition, meet-up location, price or lending terms, seller information, and seller average rating.
- **Photo Gallery**: Cover image and thumbnail gallery for all attached photos (up to 6). Displays styled "No Photo" placeholder when photos are absent.
- **Atomic View Counter**: Opening a listing's detail view atomically increments its view count (`ListingDetailService.incrementViewCount(...)`), immediately reflected in Seller Statistics.
- **Reporting**: Buyers can file a report against inappropriate listings with a written reason, dispatching an alert to administrators.
- **Architecture**: Handled by `services/ListingDetailService.java`, tested by `ListingDetailServiceTest`.

## Make Offer, Borrow Request & Atomic Claim (US-B05)
- **Price Offer (Sell Listings)**: Buyer proposes a custom purchase price (must be positive).
- **Borrow Request (Lend Listings)**: Buyer specifies desired start and end dates. Validates that dates are in the future, start date is before end date, and duration is at least 5 seconds.
- **Atomic Giveaway Claim (Giveaway Listings)**: Claims operate on a strict first-come, first-served basis. The claim executes atomically: the listing immediately transitions from `LIVE` to `GIVEN`, creating a completed giveaway deal and automatically declining any competing requests.
- **Architecture**: Business logic in `services/ActOnListingService.java`, tested by `ActOnListingServiceTest` (including multi-threaded concurrency tests for atomic giveaway claims).

## Private Chat Thread & Searchable Messages (US-B06)
- **Private Communication**: Dedicated 1-on-1 chat threads between buyer and seller per listing.
- **Message Persistence**: Messages are stored chronologically with timestamps and sender identifiers.
- **Search History**: Live keyword search filters chat history, highlighting matching messages.
- **Formal Chat Offers**: Buyers can attach formal price offers or borrow requests directly within the chat interface, generating a structured message card and notifying the seller.
- **Thread Safety**: Offer submissions and chat messaging use synchronized lock ordering to prevent deadlock during concurrent negotiations.
- **Architecture**: Business logic in `services/ChatService.java`, tested by `ChatServiceTest`.

## My Deals & Auto-Return Timer (US-B07)
- **Active Deals Feed**: Displays ongoing transactions involving the buyer (Reserved sales, active loans, and completed giveaways).
- **Complete Deal**: Buyer marks completed sale deals upon physical handover, transitioning listing to `SOLD` and prompting the buyer to rate the seller.
- **Early Self-Return**: For borrowed items, the buyer can self-return the item early, marking the loan `RETURNED` and restoring the listing to `LIVE`.
- **Automated Background Return Daemon**: `AutoReturnScheduler` runs a background task checking active loans every 5 seconds. When a loan passes its due date, the scheduler automatically transitions the deal to `RETURNED` and restores the listing to `LIVE`.
- **Architecture**: Business logic in `services/DealService.java` and `utils/AutoReturnScheduler.java`, tested by `DealServiceTest` and `AutoReturnSchedulerTest`.

## Buyer Profile Management (US-B08)
- **Profile View**: Displays account username, registration date, full name, residence hall, and active deal count.
- **Edit Details**: Allows updating full name and campus residence hall (validates non-empty input).
- **Change Password**: Enforces current password verification before applying new password. Validates new password against password strength policy.
- **Architecture**: Handled by `services/BuyerProfileService.java`, tested by `BuyerProfileServiceTest`.

## Buyer Notifications Board (US-B09)
- **Categorized Feed**: Groups alerts into Offers (incoming/accepted/countered), Reminders (borrow return deadlines), Deals (status updates), and Chat messages.
- **Unread Counter**: Badge displays real-time unread count; individual or bulk mark-as-read.
- **Deep Linking**: Clicking an alert navigates directly to the relevant deal, offer, or chat thread.
- **Architecture**: Handled by `services/BuyerNotificationService.java`, tested by `BuyerNotificationServiceTest`.

## Deal History & Ledger Filters (US-B10)
- **Historical Record**: Searchable archive of all finalized transactions (`COMPLETED`, `RETURNED`, `GIVEN`).
- **Filtering**: Filter by deal type (Purchase, Loan, Giveaway) and sort by completion date.
- **Post-Deal Review Link**: Prompts buyers to review and rate unrated deals directly from the history table.
- **Architecture**: Handled by `services/DealHistoryService.java`, tested by `DealHistoryServiceTest`.

## Rate Seller & Review System (US-B11)
- **Review Submission**: After a sale completes or a loan is returned, the buyer can submit a 1 to 5 star rating with a written review comment.
- **Real-Time Recalculation**: Submitting a review immediately recalculates the seller's average rating and total review count across the platform.
- **Exclusion of Hidden Reviews**: Per domain model Rule 4, reviews hidden by an administrator are automatically excluded from the seller's public rating calculation.
- **One Review Per Deal**: Each deal can only be rated once; duplicate submissions are prevented.
- **Architecture**: Handled by `services/BuyerReviewService.java`, tested by `BuyerReviewServiceTest`.

---

# Task 2: Seller Portal (Owner: Antoine Lepin)

**Persistence.** Every change made in the Seller Portal is written to the SQLite database
(`data/marketplace.db`) as soon as it is made, so it is still there after the application restarts:
new listings with their photos, edits and deletes, accepted / declined / countered offers and the
listing status they set, profile changes (including the password hash and the name shown on every
listing), review replies and flags, and the read state of notifications. Tested by
`SellerPersistenceTest`, which makes each change and then reloads the data from the database.

## Seller Registration (US-S01)

- Username is required, at least 3 characters long, cannot contain spaces, and is unique across buyer,
  seller and admin accounts, ignoring case (usernames of deleted accounts stay reserved); it is stored in lower case.
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
was entered and stores nothing.

**Photos are copied** into the application's folder `data/uploads/listings/` (next to the database, ignored
by Git) when the listing is submitted or edited, and the listing stores the path of the copy. The listing
therefore keeps its photos if the seller later moves, renames or deletes the original files. A copy is
named after a hash of its content and the original file name (for example `3fa9c1e0b2d4a6f8-lamp-front.png`),
so saving the same file again reuses its copy. Code: `utils/ListingPhotoStorage.java`, tested by
`ListingPhotoStorageTest`. (Listings added by the admin on behalf of a seller keep the admin's file paths.)

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
- **Mark as sold / Mark as given** (F34, the brief's "mark as sold"): a **Reserved** listing has this button.
  Once the seller has handed the item over and confirms, the deal becomes **Completed**, the listing
  **Sold** (Sell) or **Given** (Give Away), and the buyer is notified and can rate the seller, exactly as
  when the buyer marks the deal complete from My Deals (UC 1.6 S5). So a deal the buyer never completes no
  longer leaves the listing Reserved for ever. If the buyer and the seller complete it at the same time,
  only the first succeeds. A **Lent** listing has no such button: a loan ends when the item is returned
  (or automatically at its due date).

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

## Messages: Chat With Buyers (use case 1.5, F09 / F10)

Seller sidebar → **Messages** lists every conversation with a buyer about one of the seller's
listings, the most recent first. A chat also opens from an offer (**Chat**) or from a chat notification.

- The whole history is shown in chronological order; messages are never deleted.
- **Search** (field above the messages, Enter or 🔍 Search) highlights every message that contains the
  keyword, ignoring case, and scrolls to the first one; "No messages match" is shown when none does.
  **Clear** removes the highlights. A message sent while a search is active is highlighted if it matches.
- **Send** stores the message with a timestamp and notifies the buyer with a link to the conversation;
  an empty message is refused.

Searching and sending require a Seller session and only work in the seller's own conversations: the
sender is always the logged-in seller. Business logic lives in `services/SellerChatService.java` (on top
of the shared `ChatService`), tested by `SellerChatServiceTest`; the screen is
`controllers/seller/SellerChatView.java`.

## Manage Seller Profile (US-S06)

Seller sidebar → **My Profile**. The screen shows the seller's **username** (it identifies the account and cannot
be changed) and a form filled with the current **full name**, **bio** and **preferred meet-up locations**, with a
**Change password** section. Every field is checked at once and each invalid field is highlighted with its reason;
**nothing is saved** unless every field is valid.

| Field | Rule |
|-------|------|
| Full name | required (A1), trimmed, at most 100 characters |
| Bio | optional, trimmed, at most 500 characters; a blank bio removes it |
| Preferred meet-up locations | optional, any number of the campus locations (`MeetupLocation`); the first one, in the order of the list, is preselected when creating a listing |
| Password | changed only if one of the three password fields is filled in. The **current password** is then required and must be correct (A2). The **new password** must follow the [password rules](#password-rules), differ from the current one, and be typed twice identically (A3). It is stored as a PBKDF2 hash, like at registration, and the seller then logs in with it. |

- The 100 and 500 character limits only apply to a value that is changed, so a longer name or bio saved before
  (at registration or by the admin) can still be kept. Characters are counted as the seller sees them (an emoji
  counts as one).
- Saving without changing anything changes nothing ("Nothing to save"). Spaces around a value, a blank bio and
  the order of the meet-up locations are not changes.
- **Cancel** asks for confirmation if something was changed, then shows the saved profile again (A4).
- **Updates are reflected across all posted listings** (acceptance criterion): every listing of the seller,
  whatever its status, shows the new full name to buyers (Browse, listing details, chat, offers) and to the admin
  (listing review, All Listings and its search). A listing created afterwards gets the new name too. The meet-up
  location already chosen for a listing is not changed. The confirmation says how many listings were updated, and
  the "Hello, …" greeting shows the new name.
- Saves are **atomic**: a listing created during a rename always ends with the new name, and when two password
  changes are sent at the same time with the same current password, only the first one succeeds.

Every action requires a Seller session (`SessionManager.requireSeller()`) and only changes the logged-in seller's
own profile. Business logic lives in `services/SellerProfileService.java` (no JavaFX), tested by
`SellerProfileServiceTest` (all flows, integration with the buyer, seller and admin portals, concurrency) and
`SellerProfileModelsTest`.

To try it with the demo data, log in as `charlie` / `password1` and open **My Profile**: change the full name to
`Charlie Lee-Wong`, then log in as `alice` / `password1`: Browse shows *Charlie Lee-Wong* on all of Charlie's
listings. A new password must follow the rules, for example `NewPass123`.

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

## View Seller Statistics (US-S08)

Seller sidebar → **Seller Stats**. The statistics are computed from the live data each time the screen is
opened or **Refresh** is clicked (the time of the computation is shown). Nothing is stored: every value is
read from the class diagram.

| Value | How it is counted |
|-------|-------------------|
| **Total views** | `Listing.viewCount` of every listing: one more each time a buyer opens the listing's detail page (US-B04) |
| **Offers received** | every offer, borrow request and claim received on the listings, whatever its status now (pending, accepted, declined, countered or withdrawn). The seller's own counter offers are not counted. |
| **Completed deals** | deals with status `COMPLETED` (a sale or giveaway marked complete by the buyer) or `RETURNED` (a loan returned by the buyer, or auto-returned). A loan whose period has expired is auto-returned first, as on Deal History and All Deals. Active deals are not counted. |
| **Average rating** | the same value as on Reviews & Feedback: reviews hidden by the admin are excluded (class diagram rule). Without ratings: "No ratings yet". |

- The statistics cover **the same listings as My Listings**: every status, including listings rejected or taken
  down by the admin, but not the listings the seller deleted (their views, offers and deals no longer count).
- **Bar chart**: the views (blue) and offers received (gold) of each listing, newest first. Long titles are
  shortened under the chart (hover over a bar for the full title), and two listings with the same title get
  different labels (`Desk Lamp`, `Desk Lamp (2)`). With many listings, the chart scrolls sideways.
- **Pie chart**: the completed deals by type, **Sold**, **Lent** and **Given**, with the number and share of
  each type in the legend.
- **Breakdown per listing**: the status, views, offers received and completed deals of each listing, with the
  totals, so the exact values drawn on the charts can be read.
- **No listings** (alternative flow A1): every value is 0 and a message replaces the charts.
  **No completed deals** (A2): the bar chart is shown and a message replaces the pie chart.
- The statistics are computed in one step under the data store lock: a view, offer or deal recorded at the
  same time is either fully counted or not at all, so the totals, the bar chart and the pie chart always agree.

Every call requires a Seller session (`SessionManager.requireSeller()`) and only reads the logged-in seller's
listings. Business logic lives in `services/SellerStatisticsService.java` (no JavaFX), tested by
`SellerStatisticsServiceTest` (all flows, integration with the buyer, seller and admin flows, concurrency) and
`SellerStatisticsModelsTest`.

To try it with the demo data, log in as `alice` / `password1` and open a few of Charlie's listings from
Browse (for example *Multivariable Calculus* or *Engineering Notebook*), then log in as `charlie` / `password1` and open **Seller Stats**: the
views are counted, with the 4 offers received and the completed sale (Sold). `diana` / `password1` has a Lent
and a Given deal.

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

---

# Task 3: Admin Portal (Owner: Elio Le Dortz)

**Persistence.** Every change made in the Admin Portal is written to the SQLite database
(`data/marketplace.db`) as soon as it is made, so it is still there after the application restarts:
moderation actions (approvals and rejections), user edits, deactivations and soft deletions with
deal cascades, listings added or edited on behalf of sellers, listing take-downs and automatic offer
declines, seller rating moderation (hidden reviews and dismissed flags), admin profile updates
(including full name and new hashed passwords), and the read state of admin notifications. Tested by
`AdminPersistenceTest`, which makes each change and then reloads the data from the database.

## Admin Login (US-A01)
- **Authentication**: Authenticates the seeded administrator account (`admin` / `admin123`).
- **Role Enforcement**: Prevents buyer and seller accounts from accessing administrative functions (`SessionManager.requireAdmin()`).
- **Architecture**: Handled by `controllers/admin/AdminLoginController.java`, tested by `AdminLoginTest`.

## User Management & Account Deactivation (US-A02)
- **User Directory**: View all registered buyer and seller accounts with full name, role, email, status (Active / Deactivated), and registration timestamp.
- **Search & Filters**: Filter users by account role (Buyer / Seller) and status (Active / Deactivated), with live name/username search.
- **Edit User**: Edit user full name, email, and residence hall / meet-up preferences.
- **Status Toggle**: Deactivate or reactivate accounts. Deactivated users cannot log in to any portal.
- **Safe Soft-Deletion**: Soft-deletes user accounts. Prevents deleting any user currently involved in active deals or unreturned loans.
- **Architecture**: Handled by `services/UserManagementService.java`, tested by `UserManagementServiceTest`.

## All Deals Audit Ledger (US-A03)
- **Platform Ledger**: Comprehensive record of every transaction across HKUST Marketplace.
- **Filters**: Filter by deal type (Sale, Loan, Giveaway) and status (Active, Completed, Returned).
- **Deal Details Modal**: Inspect full transaction details including listing title, buyer username, seller username, timestamps, price/duration, and attached buyer review.
- **Architecture**: Handled by `services/AdminDealsService.java`, tested by `AdminDealsServiceTest`.

## Listing Moderation Queue (US-A04)
- **Pending Review Pipeline**: All newly posted listings and edited live listings enter `PENDING_REVIEW` status and appear in the admin review queue.
- **Inspection**: Admin reviews title, category, description, price/terms, condition, and uploaded photos.
- **Approval**: Admin approves listing, transitioning status to `LIVE` and notifying the seller.
- **Rejection with Reason**: Admin rejects listing with a mandatory written reason. Listing transitions to `REJECTED`, and the seller receives a notification explaining the rejection reason.
- **Architecture**: Handled by `services/ListingReviewService.java`, tested by `ListingReviewServiceTest`.

## All Listings Management & Take-Down (US-A05)
- **Global Inventory**: Browse all listings across all lifecycle states (`LIVE`, `PENDING_REVIEW`, `RESERVED`, `SOLD`, `LENT`, `GIVEN`, `TAKEN_DOWN`, `REJECTED`).
- **Admin Edit**: Administrators can edit listing details on behalf of sellers.
- **Take-Down Violation**: Inappropriate or reported live listings can be taken down with a mandatory admin reason. Status becomes `TAKEN_DOWN`, taking the item off the marketplace and notifying the seller.
- **Architecture**: Handled by `services/AdminListingService.java`, tested by `AdminListingServiceTest`.

## Seller Ratings & Review Moderation (US-A06)
- **Review Oversight**: Browse all reviews submitted across the platform.
- **Flagged Review Queue**: Dedicated inspection of reviews flagged by sellers for inappropriate language or harassment.
- **Dismiss Flag**: Dismisses seller's flag if review complies with community guidelines.
- **Hide Review**: Hides abusive reviews from public display. Automatically triggers seller average rating recalculation to exclude the hidden rating.
- **Architecture**: Handled by `services/AdminRatingService.java`, tested by `AdminRatingServiceTest`.

## Add Listing on Behalf of Seller (US-A07)
- **Admin Creation**: Admin can post new listings for student sellers. Validates all field rules (1–6 photos, positive price/duration) and publishes directly with admin approval.
- **Architecture**: Handled by `services/AdminListingService.java`, tested by `AdminListingServiceTest`.

## Admin Notifications & Audit Board (US-A08)
- **Notification Categories**: Centralized inbox for Listing Submissions, User Reports, Flagged Reviews, and User Account audit entries.
- **Report Resolution**: Resolve user-filed reports with resolution notes, automatically logging audit entries.
- **Architecture**: Handled by `services/AdminNotificationService.java`, tested by `AdminNotificationServiceTest`.

## Modular Admin Portal Architecture (Finding 5)
- **Decomposed Controllers**: In adherence to single-responsibility principles and maintainable UI design, the monolithic `AdminDashboardController` was refactored into a lean coordinator (~140 lines) delegating view creation and tab lifecycle to dedicated sub-controllers under `controllers/admin`:
  - `AdminReviewController`: UC 3.2 / US-A02 Listing Review Queue, inspection, and approval/rejection workflows.
  - `AdminListingsController`: UC 3.7 / US-A07 Platform Listings Management, add on-behalf dialog, edit dialog, and violation takedowns.
  - `AdminUsersController`: UC 3.3 / US-A03 User Directory, role/status filtering, user editing, deactivation, and soft-deletion.
  - `AdminDealsController`: UC 3.5 / US-A05 All Deals platform ledger, date range filters, and transaction details inspection.
  - `AdminRatingsController`: UC 3.8 / US-A08 Seller Ratings oversight, flagged review queue, dismiss flags, and review hide/unhide.
  - `AdminNotificationsController`: UC 3.6 / US-A06 Admin Notification Board, report resolution, and cross-tab navigation.
  - `AdminProfileController`: UC 3.1 / US-A01 Admin profile viewing, full name updates, and password management.
  - `AdminUIUtils`: Shared thumbnail caching, safe photo path resolution, and metric card constructors.
- **Test Suite**: Verified by `AdminModularControllersTest` (11 unit tests covering sub-controller instantiation, view generation, photo utilities, and metric card generation).

---

## System Architecture & Features Summary

### Technology Stack
- **Frontend / UI**: JavaFX 21 (FXML + Controller architecture, custom CSS theme with HKUST palette `#003974` and `#A68137`, Ikonli Material Design icons).
- **Core Platform**: Java 17, Maven 3 build toolchain.
- **Persistence & Services**: Thread-safe in-memory domain model with synchronized concurrent operations, complemented by embedded SQLite database support (`DatabaseManager`).
- **Security & Authorization**: Session-based RBAC (`SessionManager`), PBKDF2-HMAC-SHA256 and salted SHA-256 password hashing, PasswordPolicy validator.
- **Background Tasks**: Periodic auto-return daemon (`AutoReturnScheduler`) for borrowed item lifecycle.
- **Testing & Verification**: JUnit 5, Mockito, AssertJ, and JaCoCo coverage (1,761 unit and integration tests passing).

### Summary of Implemented Use Cases (28 / 28 Complete)

| Portal | Use Cases | Primary Services | Test Suites |
|---|---|---|---|
| **Buyer Portal** | US-B01 to US-B11 (Registration, Login, Browse, Details, Offers/Claims, Chat, My Deals, Profile, Notifications, History, Reviews) | `DummyDataService`, `ListingFilterService`, `ListingDetailService`, `ActOnListingService`, `ChatService`, `DealService`, `BuyerProfileService`, `BuyerNotificationService`, `DealHistoryService`, `BuyerReviewService` | `Buyer*Test`, `Deal*Test`, `ChatServiceTest`, `ActOnListingServiceTest`, `ListingFilterServiceTest` |
| **Seller Portal** | US-S01 to US-S09 (Registration, Login, Create Listing, My Listings, Manage Offers, Profile, Notifications, Statistics, Reviews) | `SellerRegistrationService`, `SellerLoginService`, `CreateListingService`, `MyListingsService`, `OfferManagementService`, `SellerProfileService`, `SellerNotificationService`, `SellerStatisticsService`, `ReviewHandlingService` | `Seller*Test`, `CreateListingServiceTest`, `MyListingsServiceTest`, `OfferManagementServiceTest`, `ReviewHandlingServiceTest` |
| **Admin Portal** | US-A01 to US-A08 (Login, User Management, All Deals, Listing Review, All Listings, Seller Ratings, Add Listing, Notifications) | `AdminLoginController`, `UserManagementService`, `AdminDealsService`, `ListingReviewService`, `AdminListingService`, `AdminRatingService`, `AdminNotificationService`, `AdminProfileService` | `Admin*Test`, `UserManagementServiceTest`, `ListingReviewServiceTest` |

## Project Structure

```
HKUST-Marketplace/
├── mvnw / mvnw.cmd          ← Maven Wrapper
├── pom.xml
├── README.md
├── docs/                    ← Project Management, Sprint Backlog & Code Review
│   ├── CODE_REVIEW.md
│   └── project_management/
├── .run/                    ← Shared IntelliJ run configurations
└── src/
    ├── main/
    │   ├── java/com/hkust/marketplace/
    │   │   ├── MarketplaceApp.java
    │   │   ├── Launcher.java    ← IDE entry point
    │   │   ├── controllers/
    │   │   ├── models/
    │   │   ├── services/        ← Business logic, fully testable without JavaFX
    │   │   └── utils/
    │   └── resources/
    │       ├── fxml/
    │       ├── css/styles.css
    │       └── images/hkust_logo.png
    └── test/java/com/hkust/marketplace/   ← JUnit 5 test suite (1,761 tests)
```

## Quality Assurance & Verification

- **Automated Test Suite**: 1,761 automated tests covering normal flows, alternative flows, exception flows, input validation boundaries, and multi-threaded race conditions.
- **Run Tests**:
  ```bash
  ./mvnw clean test
  ```
- **Coverage Report**: Generated at `target/site/jacoco/index.html`.
- **API Documentation**: Generated at `target/site/apidocs/index.html`.

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
You need a normal desktop session (not a headless server). On Windows or standard Linux desktops this works out of the box.

### To see the detailed error log
```powershell
.\mvnw.cmd clean javafx:run -e
```

## Licence

Educational use — HKUST COMP 3111 Group 27.
