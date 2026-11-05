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
