# HKUST Marketplace — Code Review Fix List (for a coding agent)

**What this is.** Every problem found in a full review of the code against the project requirements, written so
that an agent can go straight to the code, check the problem, and fix it. Each item says which requirement it breaks,
where the code is, what was observed, how to reproduce it, a suggested fix, the test to add, and what to watch out for.

**Snapshot.** Reviewed on branch `feat/US-S06-seller-profile` (commit `0540988`, = `master` `0dbd69a` + US-S06),
2026-11-06. Line numbers refer to that commit. They are the same on `master` for every file except
`SellerDashboardController.java`, `CreateListingService.java`, `SellerRegistrationService.java` and `README.md`
(changed by US-S06). Line numbers drift after each fix: search for the quoted code or method name if a line moved.

**Requirement sources** (cite them in commit messages and PRs):
- `activity1/assignment.txt` — the course brief (Tasks 1.x, 2.x, 3.x, non-functional and technical requirements).
- `activity1/specs_buyer.tex`, `specs_seller.tex`, `specs_admin.tex` — the 28 use cases (UC 1.1 … 3.8), with
  basic flows, alternative flows (A1, A2, …) and special requirements.
- `activity1/CLASS_DIAGRAM.md` — domain model, enumerations and the 4 key behavioural rules (section 4).
- `docs/project_management/sprint_backlog/sprint_2_backlog.md` — user stories (US-B01…US-A08) with acceptance
  criteria, and QA-01 (100 % pass, > 65 % branch coverage) / QA-02 (full Javadoc).

Every "Observed" line below was reproduced by running code against the compiled classes (JVM locale fr_FR).

---

## 0. Ground rules for the agent (from the project owner)

1. **Ownership.** Task 1 Buyer = Charles Dupouy, Task 2 Seller = Antoine Lepin, Task 3 Admin = Elio Le Dortz.
   Each item lists its owner. **Do not change another member's code without the owner's agreement** — confirm with
   the user first. Items marked *Decision* need a team choice before coding.
2. **Git.** One branch per fix (or per coherent group), created from an up-to-date `master`, named
   `fix/<ID>-short-name` (e.g. `fix/F07-auto-return-timer`). Commit messages follow the team style:
   `fix(scope): what changed [F07]` (scopes used in the repo: models, service, ui, test, docs, data, security).
   **No `Co-Authored-By` trailer. Never push and never open a pull request**: finish by telling the user the
   push/PR steps (PRs go to `https://github.com/Elio-Le-Dortz/HKUST_Marketplace_COMP-3111_G27`).
3. **IntelliJ / locale.** The owner codes in IntelliJ IDEA with JVM default locale **fr_FR**. IntelliJ's JUnit
   runner ignores the surefire `-Duser.language=en` argLine, so tests must not depend on the default locale:
   format numbers with `Locale.ENGLISH`, and assert numbers or constants rather than default-locale text.
4. **Java 17.** The JDK is 22 but the target is 17: check with `./mvnw -q compile -Dmaven.compiler.release=17`
   (no Java 21+ APIs such as `List.getFirst()`, `reversed()` on lists, pattern matching in `switch`).
5. **Architecture.** Business rules live in `services/` (no JavaFX, unit tested); controllers only display and
   forward. Seller services are the reference style: they call `SessionManager.requireSeller()` first and do every
   check-and-change inside `synchronized (dataService) { … }` (the single data-store lock), calling only the data
   store inside the lock.
6. **Quality bar** (backlog QA-01/QA-02): all tests pass, new code has tests (basic flow, every alternative flow,
   integration with the other portals, concurrency where the spec says "atomic"), full Javadoc with `@param`,
   `@return`, `@throws`.

### Useful commands

```bash
./mvnw -q compile -Dmaven.compiler.release=17          # Java 17 check
./mvnw -q clean test                                   # all tests + JaCoCo (target/site/jacoco/index.html)
./mvnw -q test -Dtest='SomeTest' -Djacoco.skip=true    # one test class
./mvnw javadoc:javadoc                                 # Javadoc build (currently FAILS, see F06)
./mvnw clean javafx:run                                # run the app (or IntelliJ run configuration "Marketplace App")
```

Overall branch coverage from the JaCoCo CSV:

```bash
python3 -c "
import csv; r=list(csv.DictReader(open('target/site/jacoco/jacoco.csv')))
c=sum(int(x['BRANCH_COVERED']) for x in r); m=sum(int(x['BRANCH_MISSED']) for x in r); print(f'{100*c/(c+m):.1f}%')"
```

To run tests **in fr_FR like IntelliJ**, launch JUnit directly (surefire forces en_US): compile a tiny runner that
uses `org.junit.platform.launcher.core.LauncherFactory` (the `junit-platform-launcher` jar is in `~/.m2`), put
`target/test-classes`, `target/classes` and the output of `./mvnw dependency:build-classpath` on the classpath, and
start it with `java -Duser.language=fr -Duser.country=FR …`. Keep such helper files outside the repository.

### Demo data (seeded by `DummyDataService.seedData()`)

| Users | Listings (id → title, owner, type, status) |
|-------|--------------------------------------------|
| `admin` / `admin123` (Admin) | L100 Calculus Textbook, charlie, Sell, LIVE · L101 MacBook Charger, charlie, Sell, LIVE |
| `alice`, `bob` / `password1` (Buyers) | L102 IKEA Desk Lamp, diana, Giveaway, LIVE · L103 TI-84, diana, Lend (90 d), LIVE |
| `charlie`, `diana` / `password1` (Sellers) | L104 Mini Fridge, diana, Sell, PENDING_REVIEW · L105 Notebook & Pen Set, charlie, Sell, LIVE |
| | L106 Badminton Racket, charlie, Sell, LIVE · L107 HKUST Hoodie, diana, Giveaway, LIVE · L999 Keyboard, charlie, REMOVED |

### Shared fixture for the regression tests below

The snippets in this file are JUnit 5 test bodies. They **fail on the current code** and must **pass after the
fix**. They assume this fixture (put it in the test class you create or extend):

```java
private static final DummyDataService D = DummyDataService.getInstance();

@BeforeEach void reset() { D.resetData(); SessionManager.logout(); }
@AfterEach  void logout() { SessionManager.logout(); }

/** A Live listing of a seeded seller, with one photo; a Lend listing can be lent for up to 7 days. */
private static Listing liveListing(String seller, ListingType type) {
    Listing l = new Listing();
    l.setTitle("Test " + type);
    l.setDescription("d");
    l.setType(type);
    l.setSellerUsername(seller);
    l.setSellerFullName(D.findUser(seller).getFullName());
    if (type == ListingType.SELL) l.setPrice(100.0);
    if (type == ListingType.LEND) l.setLendingDuration(Duration.ofDays(7));
    l.setStatus(ListingStatus.LIVE);
    l.setPhotos(List.of(new Photo("p.png")));
    D.addListing(l);
    return l;
}
```

After F03/F04 (session checks for buyers and admins) the snippets must log in the right user before each call
(`SessionManager.login(D.findUser("alice"))`, etc.).

---

## Index

| ID | Priority | Owner | Problem | Requirement broken |
|----|----------|-------|---------|--------------------|
| F01 | P1 | Elio / shared | ~~Seeded admin password stored in plaintext; plaintext accepted at login~~ **[FIXED]** | Brief NFR "credentials hashed, never plaintext"; CLASS_DIAGRAM §3.1 |
| F02 | P1 | Charles, Elio | ~~Three password schemes; buyers and profile changes use 1-round SHA-256~~ **[FIXED - PR #32]** | Same; README "Password Rules" |
| F03 | P1 | Charles | ~~Buyer services do not check the session~~ **[FIXED - PR #33]** | Brief technical req. "RBAC at the API layer"; UC 1.2 special req. |
| F04 | P1 | Elio | ~~Admin services trust a username argument; dashboard has no session guard~~ **[FIXED]** | Same; UC 3.1 special req. |
| F05 | P1 | Team | Branch coverage 58.6 % < 65 % | Backlog QA-01 |
| F06 | P1 | Team (+Antoine) | `javadoc:javadoc` fails; 1,817 missing Javadoc items | Backlog QA-02; brief "well-commented" |
| F07 | P2 | Charles | ~~No auto-return timer: expired loans stay Lent~~ **[FIXED - PR #34]** | Task 1.6; UC 1.6 A4; CLASS_DIAGRAM rule 2 |
| F08 | P2 | Charles | ~~Listing detail cannot be reopened from My Deals / Deal History~~ **[FIXED - PR #35]** | Task 1.6; UC 1.4 step 1 |
| F09 | P2 | Charles + Antoine | ~~Seller cannot reach chats without an offer; chat notifications unlinked~~ **[FIXED - PR #36]** | Task 1.5 "both buyer and seller participate"; UC "Chat About Listing" |
| F10 | P2 | Antoine | Seller chat has no search | Brief "chat history retained and searchable" |
| F11 | P2 | Elio | ~~Admin never sees photos; "add listing" attaches a fake photo~~ **[FIXED]** | Task 3.2 "Photos"; Task 3.7 "same fields as Task 2.3"; UC 3.7 A3 |
| F12 | P3 | Charles / shared | ~~Buyer-facing seller rating stale after hiding; fake seeded ratings~~ **[FIXED - PR #37]** | CLASS_DIAGRAM rule 4; UC 3.8 postcondition; Task 1.10 |
| F13 | P3 | Team (seed) | ~~Seeded deals contradict listing statuses~~ **[FIXED - PR #49]** | CLASS_DIAGRAM listing lifecycle |
| F14 | P3 | Elio | ~~Admin login reveals which credential is wrong; no "use your own portal"~~ **[FIXED]** | UC 3.1 A2, A3 |
| F15 | P3 | Elio | ~~Admin rename of a seller does not update their listings~~ **[FIXED]** | UC 3.3; backlog US-S06 "reflected across all posted listings" |
| F16 | P3 | Elio | ~~Admin listing form stores lend duration in whole days (lossy)~~ **[FIXED]** | UC 3.7 "same rules as a new listing"; 5-second minimum |
| F17 | P3 | Elio | ~~Take-down is not atomic with offer acceptance~~ **[FIXED]** | UC 3.7 special req.; CLASS_DIAGRAM rule 1 |
| F18 | P3 | Charles | ~~Accepting a counter offer leaves other countered offers stuck~~ **[FIXED - PR #38]** | CLASS_DIAGRAM rule 1; UC 1.6 S2 |
| F19 | P3 | Charles | ~~Buyer registration: generic weak-password message; extra username rules~~ **[FIXED - PR #39]** | UC 1.1 A3; README password rules |
| F20 | P3 | Charles | ~~Buyer-flow notifications have no link (seller deep links fail)~~ **[FIXED - PR #40]** | Backlog US-S07 "deep links to relevant offer/deal"; UC 2.7 step 4 |
| F21 | P3 | Charles | ~~`markDealComplete` accepts a loan (listing becomes Sold)~~ **[FIXED - PR #41]** | UC 1.6 S5 |
| F22 | P3 | Charles | ~~Lock-order inversion between chat formal offers and offers~~ **[FIXED - PR #42]** | Brief "support concurrent actions gracefully" |
| F23 | P4 | Shared / Charles / Elio | ~~Lend terms shown in whole days ("up to 0 days")~~ **[FIXED - PR #43]** | Task 1.3/1.4 display; 5-second minimum |
| F24 | P4 | Charles — *Decision* | ~~Borrow request may exceed the listing's lending duration~~ **[DECIDED - As Specified]** | Consistency with UC 2.5 A4 |
| F25 | P4 | Charles, Elio, shared | ~~Number formatting depends on the default locale~~ **[FIXED - PR #44]** | Brief "consistent interface" |
| F26 | P4 | Charles | ~~Two classes named `RegistrationResult`~~ **[FIXED - PR #45]** | Brief "readability and maintainability" |
| F27 | P4 | Charles — *Decision* | ~~"Favourites" screen is a visible placeholder~~ **[FIXED - PR #46]** | Brief (favourites/follow mentioned) |
| F28 | P4 | Team | ~~Dead / legacy code in `DummyDataService`~~ **[FIXED - PR #48]** | Brief "best coding practices" |
| F29 | P4 | Elio | Report does not record the handling admin; resolution logged as "User Accounts" | CLASS_DIAGRAM `Admin 1 --> * Report : Handles`; UC 3.6 categories |

| F30 | P4 | *Decision* | ~~Seeded admin is `admin`, specs say `admin@ust.hk`~~ **[DECIDED - Keep `admin`]** | UC 3.1; CLASS_DIAGRAM §3.1 |
| F31 | P4 | *Decision* | ~~Seeded listings have no photos~~ **[DECIDED - Demo Limitation Documented]** | CLASS_DIAGRAM `Listing 1 *-- 1..6 Photo` |
| F32 | P4 | Charles | ~~My Deals cannot rate a returned loan (Deal History can)~~ **[FIXED - PR #47]** | Consistency (UC 1.10) |
| F33 | P4 | Elio | All Deals matches a deal's review by users + listing, not by deal | UC 3.5 step 4 |
| F34 | P4 | Antoine — *Decision* | Seller cannot cancel or mark sold a Reserved deal | Brief: sellers can "mark as sold" |
| F35 | P4 | Team | ~~Documentation out of date or inconsistent~~ **[FIXED - PR #52]** | Brief submission guidelines (README) |

---

## P1 — Graded requirements

### F01 — [FIXED] Seeded admin password stored in plaintext; plaintext accepted at login
> [!NOTE]
> **Status: FIXED**
> - Updated `DummyDataService.seedAdmin()` to initialize the seeded administrator using `SEED_ADMIN_HASH` (PBKDF2-HMAC-SHA256) instead of plaintext `SEEDED_ADMIN_PASSWORD`.
> - Removed plaintext password fallback and legacy unsalted SHA-256 fallback branches in `PasswordUtil.verifyPassword`, ensuring only PBKDF2 and salted SHA-256 hashes are accepted.
> - Added regression tests in `PasswordUtilTest`, `AdminLoginTest`, and `DummyDataServiceTest` verifying that plaintext passwords and unsalted hashes are strictly rejected, and that the seeded administrator password hash is properly formatted with PBKDF2.
- **Owner:** Elio (admin seed) / shared utility.
- **Requirement:** brief NFR "Ensure that data is securely stored, particularly all users' credentials (hashed,
  never plaintext)"; CLASS_DIAGRAM §3.1 "Hashed password (never store plaintext)"; UC 3.1 special req. "with its
  password stored hashed".
- **Where:**
  - `utils/DummyDataService.java:35-38` — `SEED_ADMIN_HASH = PasswordHasher.hash("admin123")` is computed but never used.
  - `utils/DummyDataService.java:498-507` `seedAdmin()` passes `SEEDED_ADMIN_PASSWORD` (plain `"admin123"`, line 503).
  - `utils/PasswordUtil.java:75-85` `verifyPassword(...)` accepts a stored value equal to the typed password
    (plaintext) and unsalted SHA-256.
- **Observed:** `D.findUser("admin").getPasswordHash()` is `"admin123"`.
- **Regression test:**
  ```java
  String stored = D.findUser("admin").getPasswordHash();
  assertNotEquals("admin123", stored);
  assertTrue(PasswordHasher.verify("admin123", stored));
  assertFalse(PasswordUtil.verifyPassword("secret", "secret"));   // plaintext must not be accepted
  ```
- **Suggested fix:** pass `SEED_ADMIN_HASH` in `seedAdmin()`. Remove the plaintext and unsalted branches of
  `PasswordUtil.verifyPassword` (keep PBKDF2, and the salted-SHA branch only until F02 migrates buyers).
- **Watch out:** `PasswordUtilTest` and `BuyerRegistrationTest` test the "legacy" fallback and must be updated.
  27 test lines use `SEEDED_ADMIN_PASSWORD` / `"admin123"` to log in; they keep working since the password is unchanged.

### F02 — [FIXED - PR #32] Three password-hashing schemes
> [!NOTE]
> **Status: FIXED (Merged into `master` in PR #32)**
> - Replaced `PasswordUtil.hashPassword` with `PasswordHasher.hash` (PBKDF2-HMAC-SHA256, 600,000 iterations) in `DummyDataService.registerBuyer`, `BuyerProfileService.updateProfile`, and `AdminProfileService.updateProfile`.
> - All user credentials across all 3 portals are now securely hashed with PBKDF2.

- **Owner:** Charles (buyer registration and profile), Elio (admin profile).
- **Requirement:** same as F01; README "Password Rules" says passwords are stored as salted PBKDF2-HMAC-SHA256
  (600,000 iterations), which is only true for sellers.
- **Where:** `PasswordUtil.hashPassword` (one round of salted SHA-256) is used by
  `utils/DummyDataService.java:634` (`registerBuyer`), `services/BuyerProfileService.java:146`,
  `services/AdminProfileService.java:155`. Sellers use `PasswordHasher.hash` (PBKDF2).
- **Observed:** a registered buyer's hash is `94efebded227…` (hex salt:hash); a seller's is `pbkdf2_sha256$…`.
- **Regression test:**
  ```java
  var r = D.registerBuyer("zed", "Zed", "Abcdefg1", "Abcdefg1", null);   // models.RegistrationResult
  assertTrue(r.getUser().getPasswordHash().startsWith("pbkdf2_sha256$"));
  ```
- **Suggested fix:** replace the three `PasswordUtil.hashPassword(...)` calls with `PasswordHasher.hash(...)`.
  Keep `PasswordUtil.verifyPassword` as the single verifier (it already delegates PBKDF2 to `PasswordHasher`).
- **Watch out:** PBKDF2 is slow on purpose (~0.1–0.3 s per hash); buyer tests that register many users get slower.

### F03 — [FIXED - PR #33] Buyer services do not check the session (no RBAC at the service layer)
> [!NOTE]
> **Status: FIXED (Merged into `master` in PR #33)**
> - Updated `SessionManager.requireBuyer()` to return the active `Buyer` and enforce active account status.
> - Added `withSession(user, runnable/callable)` with `ThreadLocal<User>` support for thread-isolated fixture execution.
> - Enforced `requireBuyer()` and username verification across all buyer service endpoints (`ActOnListingService`, `DealService`, `BuyerProfileService`, `BuyerReviewService`, `BuyerNotificationService`, `DealHistoryService`, `ListingDetailService.submitReport`).

- **Owner:** Charles.
- **Requirement:** brief technical requirement "Enforce role-based access control at the API layer, not just in
  the UI, so a buyer session cannot call seller/admin-only endpoints"; UC 1.2 special req. "Every Buyer-only
  function checks the Buyer session on the server side".
- **Where:** `SessionManager.requireBuyer()` (`utils/SessionManager.java:105`) exists but is **never called**.
  Buyer services take the buyer's username as an argument and trust it: `ActOnListingService` (submitPriceOffer,
  submitBorrowRequest, submitClaim), `ChatService` (sendMessage, sendFormal*, searchChatHistory, getThreadById),
  `DealService` (withdrawOffer, acceptCounterOffer, declineCounterOffer, selfReturnItem, markDealComplete,
  submitRating, getMyDealsForBuyer), `DealHistoryService`, `BuyerProfileService`, `BuyerNotificationService`,
  `BuyerReviewService`, `ListingDetailService.submitReport`, and the `DummyDataService` wrappers.
- **Observed:** logged in as seller `diana`, `D.submitPriceOffer("L105", "alice", 10.0)` succeeds (an offer in
  alice's name); `D.submitPriceOffer("L105", "ghost", 11.0)` succeeds for a user that does not exist.
- **Regression test:**
  ```java
  SessionManager.login(D.findUser("diana"));                       // a Seller session
  assertThrows(SecurityException.class, () -> D.submitPriceOffer("L105", "alice", 10.0));
  SessionManager.logout();
  assertThrows(SecurityException.class, () -> D.submitPriceOffer("L105", "alice", 10.0));
  ```
- **Suggested fix:** make `requireBuyer()` return the active `Buyer` (like `requireSeller()`), call it first in
  every buyer-only entry point, and use the session buyer's username instead of the argument (or refuse when they
  differ). Chat is shared with sellers: there, check that the session user is a participant of the thread.
  System actions (`processAutoReturns`) need no session.
- **Watch out:** big change. Hundreds of tests call these methods without a session: add `SessionManager.login(...)`
  in their setUp. Seller tests (`OfferManagementServiceTest`, `MyListingsServiceTest`, `SellerNotificationServiceTest`,
  `SellerStatisticsServiceTest`, `SellerProfileServiceTest`) create offers through buyer flows while a seller is
  logged in: give them a helper that switches to the buyer around those calls. Agree on this with the team first.

### F04 — [FIXED] Admin services trust a username argument; admin dashboard has no session guard
> [!NOTE]
> **Status: FIXED**
> - Added SessionManager.requireAdmin() and SessionManager.getCurrentAdmin() returning the authenticated Admin and verifying active account status.
> - Enhanced SessionManager.endSessionForUser and withSession to properly clear and isolate thread/global sessions upon account deactivation/deletion.
> - Enforced strict admin session authentication and RBAC checks across all 7 Admin services (ListingReviewService, AdminListingService, AdminRatingService, AdminDealsService, AdminNotificationService, UserManagementService, AdminProfileService).
> - Removed hardcoded "admin" fallback in AdminRatingService.isAdmin(...) and secured AdminNotificationService.isAdminAuthorized(...).
> - Added session guard in AdminDashboardController.initialize(), redirecting unauthenticated or non-admin callers to SceneManager.showAdminLogin().
> - Bridged session delegation in DummyDataService administrative convenience methods.
> - Added exhaustive regression and RBAC test coverage across all admin service test suites.
- **Owner:** Elio.
- **Requirement:** same as F03; UC 3.1 special req. "Every Admin-only function checks the Admin session on the
  server side".
- **Where:**
  - No `requireAdmin()` exists in `utils/SessionManager.java`.
  - Admin services accept `adminUsername` and only check that this account is an admin: `ListingReviewService`,
    `UserManagementService`, `AdminProfileService`, `AdminDealsService`, `AdminNotificationService`,
    `AdminListingService`, `AdminRatingService`.
  - `services/AdminRatingService.java:367-375` `isAdmin(...)` returns `true` for the text `"admin"` even if no such user exists.
  - `controllers/admin/AdminDashboardController.java:36` `initialize()` does not check the session; `:50-55`
    `getEffectiveAdminUsername()` falls back to `"admin"` when nobody is logged in.
- **Observed:** with no session at all, `D.approveListing("L104", "admin")` returns `APPROVED`.
- **Regression test:**
  ```java
  SessionManager.logout();
  assertThrows(SecurityException.class, () -> D.approveListing("L104", "admin"));
  SessionManager.login(D.findUser("alice"));                       // a Buyer session
  assertThrows(SecurityException.class, () -> D.approveListing("L104", "admin"));
  ```
- **Suggested fix:** add `SessionManager.requireAdmin()` returning the active `Admin`; call it first in every
  admin entry point and use the session admin (drop or verify the `adminUsername` argument). In the dashboard, do as
  the seller dashboard does: if `requireAdmin()` fails, `Platform.runLater(SceneManager::showAdminLogin)` and return.
  Remove the `"admin"` fallbacks.
- **Watch out:** admin tests pass `"admin"` without logging in; add `SessionManager.login(D.findUser("admin"))`.

### F05 — Branch coverage 58.6 % (target > 65 %)
- **Owner:** Team.
- **Requirement:** backlog QA-01 ">65 % branch coverage via JaCoCo".
- **Observed:** overall 58.6 %. Services 88.6 %, models 83 %, utils 88.5 %. The JavaFX controllers have
  ~2,059 branches at 0 %. Excluding `controllers`, `MarketplaceApp`, `Launcher` and `SceneManager`: 87.3 %.
- **Options (team decision):**
  1. Exclude the UI classes in the JaCoCo plugin of `pom.xml` (`<configuration><excludes><exclude>com/hkust/marketplace/controllers/**</exclude>…`)
     — only if the course allows measuring coverage on the testable layers. Document it in the README.
  2. Add UI tests (TestFX + Monocle for headless runs): new test dependencies, slower builds.
  3. Move remaining logic out of the controllers into services, and test it.
- **Check:** `./mvnw -q clean test`, then the coverage one-liner in section 0.

### F06 — Javadoc build fails; 1,817 missing Javadoc items
- **Owner:** Team (Antoine for the seller part).
- **Requirement:** backlog QA-02 "Full docstrings on all methods and classes. No missing `@param`, `@return`, or
  `@throws` tags"; brief "Code should be well-commented".
- **Where (build errors, "bad HTML entity" = a raw `&` in Javadoc):** `models/AdminDealFilterCriteria.java:8`,
  `models/AdminNotificationDetailResult.java:5`, `models/AdminNotificationDetailStatus.java:4`,
  `models/ChatSearchResult.java:8`, `models/DealHistoryFilterCriteria.java:8`, `models/SendMessageResult.java:4`,
  `services/AdminDealsService.java:29,80`, `services/AdminNotificationService.java:44,195`,
  `services/BuyerNotificationService.java:229`, `services/BuyerReviewService.java:33,37`,
  `services/DealHistoryService.java:37`, `services/DealService.java:404`, `services/UserManagementService.java:276`
  (all under `src/main/java/com/hkust/marketplace/`).
- **Missing Javadoc** (including private members): buyer 814, admin 531, shared/skeleton 378, seller 77.
  Seller part: `SellerRatingInfo` (Charles's US-B04 class), `models/Seller.java` (skeleton), and 13 skeleton
  leftovers in `controllers/seller/SellerDashboardController.java` (the `@FXML` fields at lines 66-71, the class
  comment at line 64, `setActive` at line 122, `logout` at line 4191, the default constructor).
- **Check:**
  ```bash
  ./mvnw javadoc:javadoc                                                     # must end with BUILD SUCCESS
  javadoc -Xdoclint:missing -Xmaxwarns 20000 -private -d /tmp/jd -sourcepath src/main/java \
    -cp "$(./mvnw -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" $(find src/main/java -name '*.java') 2>&1 | grep -c warning
  ```
- **Suggested fix:** replace `&` with `&amp;` or "and" in Javadoc; add the missing comments and tags, starting
  with public API.

---

## P2 — Use cases that visibly fail in a demo

### F07 — [FIXED - PR #34] No auto-return timer: expired loans stay Lent
> [!NOTE]
> **Status: FIXED (Merged into `master` in PR #34)**
> - Created `AutoReturnScheduler` daemon scheduled executor (default 1-second period) started in `MarketplaceApp.start` and stopped cleanly in `MarketplaceApp.stop`.
> - Added read-path auto-return hooks in `DummyDataService.getLiveListings()`, `DummyDataService.getFilteredLiveListings()`, `ListingDetailService.viewListing()`, and `MyListingsService.getMyListings()`.

- **Owner:** Charles.
- **Requirement:** Task 1.6 "Implement auto-return behaviour that updates a borrowed item's status once the
  borrowing period expires"; UC 1.6 A4 "When the borrow period … expires, the system automatically marks the deal as
  Returned and sets the listing status back to Live"; CLASS_DIAGRAM rule 2. Meeting 3 minutes claim a "background
  auto-return timer".
- **Where:** `DealService.processAutoReturns()` (`services/DealService.java:369`) runs only when a screen calls it:
  buyer My Deals (`controllers/buyer/BuyerDashboardController.java:1211, 1385`), `DealHistoryService`,
  `AdminDealsService`, `SellerStatisticsService`. No timer exists (`MarketplaceApp.start`, `MarketplaceApp.java:17`).
  Browse (`DummyDataService.getFilteredLiveListings`, line 770) does not trigger it.
- **Observed:** loan due 5 minutes ago → listing `LENT`, absent from Browse, deal `ACTIVE`; becomes `LIVE` only
  after someone opens My Deals / Deal History / All Deals / Seller Stats.
- **Regression test:**
  ```java
  Listing lend = liveListing("diana", ListingType.LEND);
  LocalDateTime s = LocalDateTime.now().plusSeconds(1);
  Offer req = D.submitBorrowRequest(lend.getId(), "alice", s, s.plusSeconds(10)).getOffer();
  SessionManager.login(D.findUser("diana"));
  Deal loan = new OfferManagementService(D).acceptOffer(req.getId()).getDeal();
  loan.setDueDate(LocalDateTime.now().minusMinutes(5));
  assertTrue(D.getFilteredLiveListings(ListingFilterCriteria.all()).contains(lend));   // Browse shows it again
  assertEquals(DealStatus.RETURNED, loan.getStatus());
  ```
- **Suggested fix:** (a) call `processAutoReturns()` at the start of the read paths buyers use (Browse, listing
  detail, seller My Listings); and (b) add a small `AutoReturnScheduler` (daemon `ScheduledExecutorService`, e.g.
  every 1 s) started in `MarketplaceApp.start` and stopped in `Application.stop()`. Keep it testable (period and
  clock injectable). The 5-second minimum loan means the period should be ≤ 1 s for a live demo.
- **Watch out:** `processAutoReturns` takes the data-store lock; the scheduler must not touch JavaFX (use
  `Platform.runLater` only for UI refreshes, if any).

### F08 — [FIXED - PR #35] Listing detail cannot be reopened from My Deals or Deal History
> [!NOTE]
> **Status: FIXED (Merged into `master` in PR #35)**
> - Overloaded `BuyerDashboardController.showListingDetail(listing, requireLive, returnView)` so that opening from My Deals (button or row double-click), Deal History (button or row double-click), and Notifications passes `requireLive = false`.
> - Dynamic navigation back button returns to the originating screen (e.g. `← Back to My Deals`).
> - Disabled `Make Offer` / `Borrow Request` / `Claim Item` when non-live, while keeping Chat and Report Listing active.

- **Owner:** Charles.
- **Requirement:** Task 1.6 "Reopen the listing detail and chat thread for any deal directly from this screen";
  Task 1.4 "accessible from Browse and from My Deals"; UC 1.4 step 1.
- **Where:** `controllers/buyer/BuyerDashboardController.java:492` `showListingDetail(Listing)` always calls
  `viewListing(id, true)` (line 496, `requireLive = true`). My Deals "View Listing" (line 1570) and Deal History
  "View Listing" (line 2113) call it for listings that are Reserved/Lent/Sold/Given.
- **Observed:** any deal's listing shows "This listing is no longer available."
- **Suggested fix:** add a `requireLive` parameter (`showListingDetail(listing, false)` from My Deals and Deal
  History); when the listing is not Live, hide or disable Make Offer / Borrow / Claim but keep Chat and Report.
  UC 1.4 A1 (no longer Live) stays for Browse.
- **Test:** the service already supports it (`D.viewListing(id, false)`); verify manually: log in as `alice`,
  My Deals → select the active deal on "Graphing Calculator TI-84" → View Listing → the detail opens.

### F09 — [FIXED - PR #36] Seller cannot reach chats that have no offer; chat notifications are unlinked
> [!NOTE]
> **Status: FIXED (PR #36)**
> - **Charles's Part:** Updated `ChatService.sendMessage` to link `thread.getId()` and `NotificationTargetType.CHAT_THREAD.name()`. Clicking chat notifications directly opens the chat thread for both sellers and buyers.
> - **Antoine's Part:** Created `SellerChatService` with `getMyChatThreads()` (enforces `requireSeller()`, data-store synchronization, chronological ordering). Added `btnMessages` ("Messages") in `SellerDashboardView.fxml` and implemented `showMessages()` and `createThreadCard()` in `SellerDashboardController`.

- **Owner:** Charles (notification link) + Antoine (seller inbox).
- **Requirement:** Task 1.5 "Both buyer and seller can participate in the same chat thread"; brief "Sellers can …
  respond to offers and chats"; UC "Chat About Listing" (actors Buyer and Seller).
- **Where:**
  - `services/ChatService.java:152` creates `new Notification(recipient, "New Chat Message", notifMsg, "Chat")` without `relatedItemId`/`relatedItemType`.
  - The seller can open a chat only from an offer (`SellerDashboardController.handleOpenChat` → `showChat`, line 3736)
    or from a notification linked to a `CHAT_THREAD` (`SellerNotificationService.openChat`). There is no seller chat inbox.
- **Observed:** alice messages charlie about L105 without making an offer → charlie's notification has
  `relatedItemId = null`; `SellerNotificationService.openNotification` returns `NO_RELATED_ITEM`; charlie has no way to reply.
- **Regression test:**
  ```java
  ChatThread t = D.getChatService().getOrCreateThread("L105", "alice");
  D.getChatService().sendMessage(t.getId(), "alice", "Is it still available?");
  Notification n = D.getNotificationsFor("charlie").get(0);
  SessionManager.login(D.findUser("charlie"));
  SellerNotificationResult r = new SellerNotificationService(D).openNotification(n.getId());
  assertEquals(SellerNotificationStatus.SUCCESS, r.getStatus());
  assertSame(t, r.getChatThread());
  ```
- **Suggested fix:** (Charles) use the 6-argument constructor with `thread.getId()` and
  `NotificationTargetType.CHAT_THREAD.name()`, keeping the category `"Chat"`; the buyer board already resolves
  `CHAT_THREAD`. (Antoine) add a "Messages" item to the seller sidebar (`fxml/seller/SellerDashboardView.fxml`) listing
  `ChatService.getThreadsForUser(seller)` (`services/ChatService.java:92`, newest first) and opening
  `showChat(thread, null, "← Back to Messages", this::showMessages)`. Put the listing logic in a seller service with
  `requireSeller()` and tests.

### F10 — Seller chat has no search
- **Owner:** Antoine.
- **Requirement:** brief technical requirement "All chat history must be retained and searchable"; UC Chat About
  Listing S3 (search).
- **Where:** `controllers/seller/SellerDashboardController.java:3736` `showChat(...)` only shows messages and a send
  box. The buyer chat uses `ChatService.searchChatHistory` (`services/ChatService.java:287`) and the
  `chat-bubble-highlight` CSS class.
- **Suggested fix:** add a search field to the seller chat that calls
  `searchChatHistory(thread.getId(), seller.getUsername(), query)`, highlights matches, and shows "No messages match"
  (UC A3). Same look as the buyer chat.
- **Test:** service already tested; add a seller-side test that the seller (a participant) can search, and that
  another seller cannot (`UNAUTHORIZED`).

### F11 — [FIXED] Admin never sees photos; "add listing on behalf" attaches a fake photo
> [!NOTE]
> **Status: FIXED**
> - In `AdminDashboardController.java`:
>   - Added cover thumbnail to review queue pending listing cards (`createPendingCard`).
>   - Added photo thumbnail gallery to submission details view (`showListingDetails` and `openListingDetailsDialog`).
>   - Replaced fake `/images/sample.png` fallback with real photo selection using `FileChooser` and static helpers `CreateListingService.addPhotos(...)` in both `openAddListingDialog` and `openEditListingDialog`, with interactive thumbnail preview and removal controls.
> - In `AdminListingService.java`:
>   - Enforced non-empty and length validation for description and title.
>   - Enforced photo validation matching Task 2.3 (1 to 6 photos, valid image magic bytes, supported formats, file existence, no duplicates).
>   - Preserved normalized photo paths with standard ID formatting (`<listingId>-P<index>`).
> - In unit tests (`AdminListingServiceTest.java` and `DummyDataServiceAdminListingTest.java`):
>   - Added valid temp PNG photos via `@TempDir`.
>   - Added regression test assertions for missing/too long descriptions and invalid/non-image/duplicate photo files.
- **Owner:** Elio.
- **Requirement:** Task 3.2 "For each submission show: … Photos …"; Task 3.7 "Add a listing … — same fields as
  Task 2.3" (1 to 6 photos, description required); UC 3.7 A3 "photos missing, more than 6, or not images".
- **Where (`controllers/admin/AdminDashboardController.java`):**
  - `createPendingCard` (line 102; photo count only at lines 132-133);
  - `showListingDetails` (line 250; "N photo(s) attached" at lines 291-292);
  - `openAddListingDialog` (line 632) always adds `new Photo("/images/sample.png")` (line 759), a path that does not exist;
  - `openEditListingDialog` (line 788) does the same when a listing has no photo (line 895).
  - There is no `ImageView` and no `FileChooser` in the admin controller.
  - `services/AdminListingService.java:370` `validateListingFields` requires neither a description nor real image files.
- **Suggested fix:** show thumbnails (reuse the logic of `SellerDashboardController.listingThumbnail` or the buyer
  gallery) in the review card and details; give the add/edit dialogs a photo chooser using the static, session-free
  helpers `CreateListingService.addPhotos(...)`, `checkPhotos(...)`, `photoProblem(...)`, and validate the fields
  with `CreateListingService.validate(CreateListingRequest)` so the rules match Task 2.3 exactly.
- **Test:** an admin-created listing without a real image file, or without description, is refused; a valid one is
  stored Live with the chosen photos.

---

## P3 — Logic and data problems

### F12 — [FIXED - PR #37] Buyer-facing seller rating is stale after hiding a review; fake seeded ratings
> [!NOTE]
> **Status: FIXED (PR #37)**
> Removed the fallback to cached `seller.getRatingCount() > 0` in `ListingDetailService.getSellerRatingInfo` and `DummyDataService.recalculateSellerRating`. When no visible reviews exist, ratings correctly reset to 0.0 / 0 and return `noRatings` ("No ratings yet"). Removed fake seeded ratings for Charlie and Diana from `DummyDataService.seedData()`.
- **Owner:** Charles (`ListingDetailService`) / shared (`DummyDataService`).
- **Requirement:** CLASS_DIAGRAM rule 4 "Average seller rating excludes any review where hidden == true"; UC 3.8
  postcondition "A hidden review … is not counted in the seller's average rating"; Task 1.10 average shown to all users.
- **Where:** `utils/DummyDataService.java` `recalculateSellerRating` (fallback at line 1276 keeps the cached
  `Seller.averageRating/ratingCount` when no visible review is left); `services/ListingDetailService.java:89` (same
  fallback); seeded fake values `utils/DummyDataService.java:156-157` (charlie 4.7 / 12) and `:163-164` (diana 4.3 / 8).
- **Observed:** a seller's only review (1★) hidden by the admin → buyers still see "★ 1.0 / 5.0 (1 review)", the
  seller sees "No ratings yet". Diana has **no** review, buyers see "★ 4.3 / 5.0 (8 reviews)", Diana's own Reviews and
  Stats screens say "No ratings yet".
- **Regression test:**
  ```java
  Seller rs = new Seller("rs", "Rae Seller", "h");
  D.registerUserIfAbsent(rs);
  Review rev = new Review("rs", "alice", null, "Item", 1, "bad");
  D.addReview(rev);
  D.getAdminRatingService().hideReview(rev.getId(), "admin");
  assertFalse(D.getSellerRatingInfo("rs").hasRatings());
  assertFalse(D.getSellerRatingInfo("diana").hasRatings());   // no review exists for diana
  ```
- **Suggested fix:** compute the average only from non-hidden reviews (as `ReviewHandlingService.getMyRating()`
  does), with no fallback to the cached fields; remove the fake seeded numbers, or seed real `Review` objects if the
  demo should show ratings.
- **Watch out:** tests asserting the seeded 4.7 / 4.3 values must be updated (`grep -rn '4\.7\|4\.3' src/test`).

### F13 — [FIXED - PR #49] Seeded deals contradict the listing lifecycle
> [!NOTE]
> **Status: FIXED (PR #49)**
> - Aligned seeded listings and deals in `DummyDataService.seedData()` with CLASS_DIAGRAM §3.2 and §3.5:
>   - `L100` (Calculus Textbook Stewart, active deal `D500`): status set to `RESERVED`.
>   - `L101` (MacBook Charger, completed sale `D502`): status set to `SOLD`.
>   - `L103` (TI-84, active loan `D501`): status set to `LENT`.
>   - `L107` (HKUST Hoodie, completed giveaway `D504`): status set to `GIVEN`.
>   - `L104` (Mini Fridge): removed conflicting loan deal `D503`; kept clean as `PENDING_REVIEW` for admin demo review.
>   - Added seeded live listings `L10` (*Multivariable Calculus*, SELL/BOOKS) and `L11` (*Casio fx-991ES*, LEND) so marketplace browsing, searching, and borrowing have live listings.
>   - Moved returned loan deal `D503` to `L11` (which returned to `LIVE` after loan completion).
>   - Moved countered offer `O2` to `L105` (live listing) so counter offer acceptance/decline tests operate on valid live listings.
>   - Updated `ChatServiceTest` to submit formal offers on live listings, and updated `README.md` demo instructions.
- **Owner:** Team (seed data in `DummyDataService.seedData()`, `utils/DummyDataService.java:168-282`).
- **Requirement:** CLASS_DIAGRAM §3.2 lifecycle (`Live → Reserved → Sold/Lent/Given`) and §3.5.
- **Observed:**

  | Deal | Type, status | Listing | Listing status now | Should be |
  |------|--------------|---------|--------------------|-----------|
  | D500 | Sale, ACTIVE | L100 Calculus Textbook | LIVE | RESERVED |
  | D501 | Loan, ACTIVE | L103 TI-84 | LIVE | LENT |
  | D502 | Sale, COMPLETED | L101 MacBook Charger | LIVE (buyers can still make offers) | SOLD |
  | D503 | **Loan**, RETURNED | L104 Mini Fridge (**Sell**, PENDING_REVIEW) | — | a Lend listing, or remove the deal |
  | D504 | Giveaway, COMPLETED | L107 HKUST Hoodie | LIVE | GIVEN |
- **Suggested fix:** set the listing statuses consistently (lines 170, 175, 185, 205), move D503 to a Lend listing
  or delete it, and keep L104 Pending Review for the admin demo.
- **Watch out:** many tests rely on seeded ids and statuses; run the whole suite, and update the README demo text.

### F14 — Admin login reveals which credential is wrong; no "use your own portal" message
- **Owner:** Elio.
- **Requirement:** UC 3.1 A2 "informs … that the username or password is incorrect, without saying which one";
  A3 "if the account is a buyer or seller account, informs … that this account cannot log in to the Admin Portal and
  must use its own portal".
- **Where:** `controllers/admin/AdminLoginController.java:35-58` `authenticateAdmin` (messages at lines 37, 42, 47, 55).
- **Observed:** wrong password → "Incorrect password."; unknown user → "Invalid admin credentials."; buyer `alice`
  with the right password → "Invalid admin credentials.".
- **Suggested fix:** move the logic to an `AdminLoginService` mirroring `SellerLoginService` (A1 empty fields,
  A2 one message `LoginResult.MSG_INVALID_CREDENTIALS` for both cases, A3 only after the password is verified,
  dummy-hash check for unknown users), and keep the controller thin.
- **Watch out:** `controllers/admin/AdminLoginTest.java` asserts the current messages.

### F15 — [FIXED] Admin rename of a seller does not update their listings
> [!NOTE]
> **Status: FIXED**
> - In `UserManagementService.java`:
>   - Synchronized the full name update across all listings posted by the seller inside the `dataService` lock (`renameListings(target.getUsername(), target.getFullName())`).
>   - Updated in-memory listings and persisted changes via `dataService.updateListing(listing)`.
> - In unit tests (`UserManagementServiceTest.java` and `DummyDataServiceUserManagementTest.java`):
>   - Added regression tests verifying that editing a seller's full name updates `sellerFullName` across all their posted listings.
- **Owner:** Elio.
- **Requirement:** UC 3.3 S1 (edit full name); backlog US-S06 acceptance "Updates reflected across all posted
  listings" (the name is copied in `Listing.sellerFullName` and shown everywhere).
- **Where:** `services/UserManagementService.java:140` `target.setFullName(...)`; listings keep the old copy.
- **Regression test:**
  ```java
  var r = D.getUserManagementService().editUser(UserEditRequest.builder()
          .targetUsername("charlie").adminUsername("admin").newFullName("Charles Lee").build());
  assertTrue(r.isSuccess());
  D.getListingsBySeller("charlie").forEach(l -> assertEquals("Charles Lee", l.getSellerFullName()));
  ```
- **Suggested fix:** after the change, update `sellerFullName` of every listing of the seller, inside the
  data-store lock (same as `SellerProfileService.renameListings`, US-S06).

### F16 — [FIXED] Admin listing form stores the lend duration in whole days
> [!NOTE]
> **Status: FIXED**
> Admin forms and DTOs now carry granular lending durations (amount + `LendingDurationUnit` and `Duration`), validated via `CreateListingService.checkLendingDuration` / `toLendingDuration` (enforcing the 5-second minimum and 365-day maximum). The admin edit form prefills duration decomposed into the largest whole unit (matching `MyListingsService.toEditRequest`), preserving durations such as 36 hours or 10 seconds without loss.
- **Owner:** Elio.
- **Requirement:** UC 3.7 S2 "checks the changed fields with the same rules as a new listing"; Task 1.5 / team rule
  "minimum borrow time 5 seconds" (CLASS_DIAGRAM §3.2 `lendingDuration: Duration`).
- **Where:** `services/AdminListingService.java:91, 112-118, 172, 188-194, 404` (`lendingDays` as `Integer`);
  `controllers/admin/AdminDashboardController.java:828` prefills days with `Listing.getLendingDays()`
  (`models/Listing.java:115`, truncates to whole days).
- **Observed:** editing only the title of a 36-hour Lend listing → `SUCCESS` and the duration silently becomes 24 h;
  for a 10-second Lend listing → `INVALID_FIELD "Lending duration must be positive for Lend listings."` (cannot be saved).
- **Suggested fix:** carry a `Duration` (amount + `LendingDurationUnit`) in `AdminListingCreateRequest` /
  `AdminListingEditRequest`, validate with `CreateListingService.checkLendingDuration` / `toLendingDuration`, and
  prefill the form like `MyListingsService.toEditRequest` (largest whole unit).

### F17 — Take-down is not atomic with offer acceptance
- **Owner:** Elio.
- **Requirement:** UC 3.7 special req. "Taking down a listing is atomic with respect to accepting an offer: if the
  seller accepts an offer at the same time, only the first action succeeds"; CLASS_DIAGRAM rule 1.
- **Where:** `services/AdminListingService.java:224` `takeDownListing` is `synchronized` on the service and then on
  the listing (line 245); the admin UI calls it directly (`AdminDashboardController.java:926, 966`).
  `OfferManagementService.acceptOffer` locks the data store (`synchronized (dataService)`). Different locks → both can
  read `LIVE` and both succeed (end state: an ACTIVE deal on a REMOVED listing). `DummyDataService.takeDownListing`
  (line 1318) takes the data-store lock first, but the UI does not use it.
- **Suggested fix:** in the mutating `AdminListingService` methods, wrap the check-and-change in
  `synchronized (dataService)` and drop the method-level and listing locks (avoids a lock-order inversion with the
  wrapper), matching the seller services.
- **Test:** race `acceptOffer` and `takeDownListing` on the same listing (e.g. 200 rounds with a `CountDownLatch`):
  exactly one succeeds each round; never an ACTIVE deal on a REMOVED listing.

### F18 — [FIXED - PR #38] Accepting a counter offer leaves other countered offers stuck
> [!NOTE]
> **Status: FIXED (PR #38)**
> When a counter offer is accepted, `DealService.acceptCounterOffer` now declines all competing offers that are either `PENDING` or `COUNTERED`, marks their counter-offers as `DECLINED`, stores the decline reason, persists changes, and dispatches decline notifications linked to the offer (`NotificationTargetType.OFFER`).
- **Owner:** Charles.
- **Requirement:** CLASS_DIAGRAM rule 1 "accepting an offer … declines all other pending offers on the listing, and
  notifies those buyers"; UC 1.6 S2 step 4; consistent with UC 2.5 S1 step 5 (seller side declines pending **and**
  countered offers).
- **Where:** `services/DealService.java:189` only declines offers whose status is `PENDING`.
- **Observed:** charlie counters both alice's and bob's offers on L105; alice accepts her counter → listing
  `RESERVED`, bob's offer stays `COUNTERED` forever, and bob is not notified.
- **Regression test:**
  ```java
  Offer oa = D.submitPriceOffer("L105", "alice", 10.0).getOffer();
  Offer ob = D.submitPriceOffer("L105", "bob", 12.0).getOffer();
  SessionManager.login(D.findUser("charlie"));
  OfferManagementService om = new OfferManagementService(D);
  om.counterWithPrice(oa.getId(), 20.0);
  om.counterWithPrice(ob.getId(), 21.0);
  assertTrue(D.acceptCounterOffer(oa.getId(), "alice").isSuccess());
  assertEquals(OfferStatus.DECLINED, ob.getStatus());
  ```
- **Suggested fix:** decline `PENDING` and `COUNTERED` offers (and their `counterOffer`), store a reason, and
  notify each buyer with a link to the offer (as `OfferManagementService.declineCompetingOffers` does).

### F19 — [FIXED - PR #39] Buyer registration: generic weak-password message; extra username rules
> [!NOTE]
> **Status: FIXED (PR #39)**
> Unified password validation across the application using `PasswordPolicy.findViolations` and `PasswordPolicy.formatViolations` so that every specific unmet password rule is reported. Harmonized username rules across both buyer and seller registration (rejecting usernames containing spaces or shorter than 3 characters). Documented username rules in `README.md`.
- **Owner:** Charles.
- **Requirement:** UC 1.1 A3 "informs the Buyer which password rules are not met"; UC 1.1 basic flow (only
  uniqueness and non-empty); README "Password Rules" (length counted in code points; every broken rule reported).
- **Where:** `utils/DummyDataService.java:578-641` `registerBuyer`: password via `checkPasswordStrength`
  (line 625; defined at line 1339, counts UTF-16 `length()`, returns one generic message); extra rules "no spaces"
  (line 614) and "at least 3 characters" (line 617), which seller registration does not have.
- **Observed:** `"abcdefgh"` → "Password should contain uppercase, lowercase and a digit." (seller registration says
  "Password must contain at least one uppercase letter and at least one digit."); username `"ab"` refused for a
  buyer, accepted for a seller; `"john doe"` accepted for a seller.
- **Suggested fix:** use `PasswordPolicy.findViolations` and list the broken rules (as `SellerRegistrationService`);
  decide team-wide username rules, apply them to both registrations, and document them in the README.

### F20 — [FIXED - PR #40] Notifications from buyer flows have no link (seller deep links fail)
> [!NOTE]
> **Status: FIXED (PR #40)**
> Updated notification creation in buyer flows (`ActOnListingService` for price offers, borrow requests, and claims; `DealService` for offer withdrawals, counter-offer acceptance/rejection, item returns, deal completions, and auto-returns) to supply `relatedItemId` and `relatedItemType` (`NotificationTargetType.OFFER` / `DEAL`). Added regression tests in `ActOnListingServiceTest` and `DealServiceTest`, and verified seller deep links resolve successfully to the underlying offer or deal via `SellerNotificationService.openNotification`.
- **Owner:** Charles.
- **Requirement:** backlog US-S07 acceptance "deep links to relevant offer/deal"; UC 2.7 step 4 "displays the
  related listing, offer, review or deal"; UC 1.8 step 4 for the buyer.
- **Where:** 4-argument `new Notification(user, title, message, category)` (no `relatedItemId`/`relatedItemType`) at
  `services/ActOnListingService.java:67, 115, 157` (new offer, borrow request, claim → link the offer) and
  `services/DealService.java:108, 112` (withdrawn → offer), `192` (declined → offer), `203, 207` (counter accepted →
  deal), `246, 250` (counter declined → offer), `299, 303` (returned → deal), `352, 356` (completed → deal),
  `390, 394` (auto-returned → deal); `services/ChatService.java:152` (see F09).
- **Observed:** after `D.submitPriceOffer("L105", "alice", 20.0)`, charlie's notification has `relatedItemId = null`
  and `SellerNotificationService.openNotification` returns `NO_RELATED_ITEM`.
- **Regression test:**
  ```java
  Offer o = D.submitPriceOffer("L105", "alice", 20.0).getOffer();
  Notification n = D.getNotificationsFor("charlie").get(0);
  SessionManager.login(D.findUser("charlie"));
  SellerNotificationResult r = new SellerNotificationService(D).openNotification(n.getId());
  assertEquals(SellerNotificationStatus.SUCCESS, r.getStatus());
  assertSame(o, r.getOffer());
  ```
- **Suggested fix:** use the 6-argument constructor with the offer or deal id and
  `NotificationTargetType.OFFER.name()` / `DEAL.name()`. Keep the categories ("Offer", "Deal", "Loan"): the seller
  board maps them to its own categories.

### F21 — [FIXED - PR #41] `markDealComplete` accepts a loan (listing becomes Sold)
> [!NOTE]
> **Status: FIXED (PR #41)**
> Added `DEAL_IS_LOAN` to `DealOperationStatus`. In `DealService.markDealComplete`, reject attempts to mark active loans complete with status `DEAL_IS_LOAN` ("Loan deals cannot be marked complete; borrowed items must be returned."). Added unit regression test verifying that loans remain active and listing remains lent.
- **Owner:** Charles.
- **Requirement:** UC 1.6 S5 "marks the deal as Completed and updates the listing status to Sold (Sale deal) or
  Given (Giveaway deal)"; loans end by return (S4 / A4).
- **Where:** `services/DealService.java:318` `markDealComplete` has no type check (the UI hides the button via
  `MyDealsItem.canMarkComplete`, but the service is callable).
- **Observed:** completing an active loan through the service → `success = true`, listing `SOLD`, deal `COMPLETED`.
- **Suggested fix:** refuse `DealType.LOAN` with a clear failure status (e.g. a new `DEAL_IS_LOAN`, or `INVALID_ACTION`).

### F22 — [FIXED - PR #42] Lock-order inversion between chat formal offers and offers
> [!NOTE]
> **Status: FIXED (PR #42)**
> Refactored synchronization in both `ActOnListingService` and `ChatService` to synchronize on `dataService` (`synchronized (dataService) { ... }`), matching the seller service architecture. In `ChatService.sendFormalPriceOffer` and `sendFormalBorrowRequest`, delegated offer creation through `dataService.submitPriceOffer` and `submitBorrowRequest`. Added concurrency regression test under `assertTimeoutPreemptively` verifying deadlock-free concurrent execution between formal chat offers and direct offers.
- **Owner:** Charles.
- **Requirement:** brief "Support concurrent actions gracefully".
- **Where:** `services/ChatService.java:169` / `:230` (`synchronized` on `ChatService`) call
  `dataService.getActOnListingService().submitPriceOffer/submitBorrowRequest` (lines 201, 259) →
  `ActOnListingService` monitor → `synchronized` data-store methods. `DummyDataService.submitPriceOffer`
  (`utils/DummyDataService.java:870`) takes the data-store lock and then the `ActOnListingService` monitor: opposite
  order → possible deadlock when a chat formal offer and a direct offer run at the same time.
- **Suggested fix:** in `ChatService`, take the data-store lock first (or call the `DummyDataService` wrappers), and
  ideally make the buyer services lock `dataService` like the seller services do.
- **Test:** run chat formal offers and direct offers concurrently with a timeout (`assertTimeoutPreemptively`).

---

## P4 — Minor issues and decisions

### F23 — [FIXED - PR #43] Lend terms shown in whole days ("Lend (up to 0 days)")
> [!NOTE]
> **Status: FIXED (PR #43)**
> - Updated `Listing.getPriceDisplay()` to format lending duration with `OfferManagementService.formatDuration(lendingDuration)`, properly displaying periods in seconds, hours, and days (e.g., "10 seconds", "36 hours", "7 days", "1 day").
> - Updated `BuyerDashboardController.showBorrowDialog` and `showChatFormalBorrowRequestDialog` to show human-readable suggested lending durations and initialize proposed default end dates/times based on `listing.getLendingDuration()`.
> - Added regression tests in `DomainModelTest` covering durations in seconds, minutes, hours, and days.
- **Owner:** shared model (`Listing`) + Charles (buyer UI) + Elio (admin UI).
- **Where:** `models/Listing.java:300-309` `getPriceDisplay()` uses `getLendingDays()` (truncated);
  buyer `BuyerDashboardController.java:991, 1007, 3119`; admin `AdminDashboardController.java:560, 828, 1007`.
- **Observed:** 10-second loan → "Lend (up to 0 days)"; 36 h → "Lend (up to 1 days)".
- **Suggested fix:** format with `OfferManagementService.formatDuration(listing.getLendingDuration())`
  ("10 seconds", "36 hours", "7 days"); base the borrow dialog's default and maximum on the `Duration`.

### F24 — [DECIDED - As Specified] Borrow request may exceed the listing's lending duration (*Decision*)
> [!NOTE]
> **Status: DECIDED (No code change needed)**
> Decision made by owner (Charles): Kept as is per UC 1.5 A3 (which does not enforce a maximum duration on initial buyer requests; sellers may counter or decline within their listing limits).
- **Owner:** Charles.
- **Where:** `services/ActOnListingService.java:218` `validateBorrowDates` has no maximum.
- **Observed:** a 400-day request on L103 (lent for up to 90 days) is accepted.
- **Note:** UC 1.5 A3 does not require a maximum, but the seller's counter offer is limited to the lending duration
  (UC 2.5 A4, `OfferManagementService.counterDatesError`). Decide, then align.

### F25 — [FIXED - PR #44] Number formatting depends on the default locale
> [!NOTE]
> **Status: FIXED (PR #44)**
> - Added `Locale.ENGLISH` to all unlocalized `String.format` calls for prices, ratings, totals, and currency values across `models/SellerRatingInfo.java`, `models/MyDealsItem.java`, `models/DealHistoryItem.java`, `models/DealHistorySummary.java`, `models/AdminDealRecord.java`, `models/Listing.java`, `services/ActOnListingService.java`, `services/ChatService.java`, `controllers/buyer/BuyerDashboardController.java`, `controllers/admin/AdminDashboardController.java`, and `utils/PasswordUtil.java`.
> - Added regression tests in `RateSellerModelsTest` and `DealModelsTest` asserting that numbers, ratings, and currencies format with standard decimal points even when executed under `Locale.FRANCE`.
- **Owner:** Charles, Elio, shared models.
- **Where:** `String.format(...)` without `Locale` in `models/SellerRatingInfo.java:49-50, 90`,
  `models/MyDealsItem.java:68, 197`, `models/DealHistoryItem.java:215`, `models/DealHistorySummary.java:76`,
  `models/AdminDealRecord.java:255`, `services/ActOnListingService.java:66, 70`, `services/ChatService.java:209, 215`,
  `controllers/admin/AdminDashboardController.java:1726, 1756, 1834`, `controllers/buyer/BuyerDashboardController.java:889`.
- **Observed:** in fr_FR, buyers see "★ 4,3 / 5.0" and "HK$10,00"; seller screens show "4.3" and "HK$ 10.00".
- **Suggested fix:** `String.format(Locale.ENGLISH, …)` everywhere text is shown to users.

### F26 — [FIXED - PR #45] Two classes named `RegistrationResult`
> [!NOTE]
> **Status: FIXED (PR #45)**
> - Renamed `models/RegistrationResult.java` to `models/BuyerRegistrationResult.java` and updated callers in `BuyerRegisterController` and `DummyDataService.registerBuyer`.
> - Renamed `RegistrationResultTest` in `models/` to `BuyerRegistrationResultTest` and updated references across tests (`BuyerRegistrationTest`, `BuyerLoginTest`, and `DummyDataServiceUserManagementTest`).
> - Preserved generic `services/RegistrationResult.java` for seller registration, eliminating class naming ambiguity across packages.
- **Owner:** Charles.
- **Where:** `models/RegistrationResult.java` (buyer registration) and `services/RegistrationResult.java` (generic,
  seller registration). Code importing both packages must fully qualify one.
- **Suggested fix:** rename the buyer one (e.g. `BuyerRegistrationResult`) and update its tests, or merge both.

### F27 — [FIXED - PR #46] "Favourites" screen is a visible placeholder (*Decision*)
> [!NOTE]
> **Status: FIXED (PR #46)**
> - Decision made by owner (Charles): Removed the placeholder "Favourites" button from the buyer sidebar (`BuyerDashboardView.fxml`) and controller (`BuyerDashboardController.java`) as favourites/follow are not part of the 28 required use cases, eliminating placeholder UI ahead of grading.
- **Owner:** Charles.
- **Where:** `controllers/buyer/BuyerDashboardController.java:2149` `showFavourites()` shows "This section is a UI
  placeholder for students to implement."; sidebar button `fxml/buyer/BuyerDashboardView.fxml` (`btnFavourites`).
- **Note:** favourites/follow are mentioned in the brief's overview but are not among the 28 use cases. Either
  implement them or remove the button before the demo.

### F28 — [FIXED - PR #48] Dead or legacy code in `DummyDataService`
> [!NOTE]
> **Status: FIXED (PR #48)**
> - Removed unused legacy constants `HKUST_LOCATIONS`, `CATEGORIES`, and `CONDITIONS` from `DummyDataService`.
> - Removed duplicate method `resetToSeedData()` and migrated all 8 calling test files to standard `resetData()`.
> - Removed legacy `claimListing(...)` helper that bypassed the offer flow and updated `DummyDataServiceTest` to use proper `submitClaim(...)`.
> - Removed deprecated `checkPasswordStrength(...)` in `DummyDataService` and connected `registerBuyer` directly to `PasswordPolicy.findViolations`.
- **Owner:** Team.
- **Where (`utils/DummyDataService.java`):** `HKUST_LOCATIONS` (line 77), `CATEGORIES` (99), `CONDITIONS` (104) —
  unused strings that do not match the enums; `resetToSeedData()` (722) duplicates `resetData()` and does not reset
  `messageIdCounter` (used by 7 test files); `claimListing(...)` (1023) bypasses the offer flow (sets `GIVEN`
  directly; used only by `DummyDataServiceTest`); `checkPasswordStrength(...)` (1339, see F19).
- **Suggested fix:** remove them after replacing their uses (tests: switch to `resetData()`, `submitClaim(...)`).

### F29 — Report does not record the handling admin; resolution logged as "User Accounts"
- **Owner:** Elio.
- **Requirement:** CLASS_DIAGRAM `Admin "1" --> "*" Report : Handles`; UC 3.6 categories (Reports vs User Accounts).
- **Where:** `models/Report.java` has no admin field; `services/AdminNotificationService.java:296` `resolveReport`
  logs its audit entry with `AdminNotificationCategory.USER_ACCOUNTS` (line 340).
- **Suggested fix:** add `handledBy` (admin username, and optionally a resolved date) set in `resolveReport`; log
  the audit entry under `REPORTS`.

### F30 — [DECIDED - Keep `admin`] Seeded admin username `admin` vs `admin@ust.hk` (*Decision*)
> [!NOTE]
> **Status: DECIDED (No code change needed)**
> Decision made by team / owner: Kept `admin` as the seeded administrator username (`DummyDataService.SEEDED_ADMIN_USERNAME = "admin"` with credentials `admin` / `admin123`). The assignment brief (`assignment.txt`) and domain model (`CLASS_DIAGRAM.md §3.1`) specify `admin@ust.hk` as an illustrative example (`"e.g., admin@ust.hk"`). Keeping `admin` avoids widespread breaking changes across admin controllers, views, database persistence, and test suites. Documented explicitly in `README.md` under Demo Credentials.
- **Where:** `DummyDataService.SEEDED_ADMIN_USERNAME = "admin"` (line 33); README demo credentials use `admin`;
  `specs_admin.tex` UC 3.1 and CLASS_DIAGRAM §3.1 say `admin@ust.hk`.
- **Note:** either rename the seeded account (update README and tests) or state in the README that the seeded
  admin is `admin`.

### F31 — [DECIDED - Demo Limitation Documented] Seeded listings have no photos (*Decision*)
> [!NOTE]
> **Status: DECIDED (No code change needed)**
> Decision made by team / owner: Documented as a known demo limitation. Photos are stored as absolute filesystem paths read with `java.io.File`, meaning bundled JAR resources cannot be directly referenced without extracting them to disk on every application run. Real photo selection and validation (1..6 photos) is fully enforced on newly created listings via `CreateListingService` (US-S02) and `AdminListingService` (US-A07). The UI provides built-in fallback placeholders ("No Photo" / "No photos uploaded for this listing") when rendering seeded listings. Documented in `README.md` under Demo Credentials.
- **Where:** `DummyDataService.createListing(...)` (line ~509) adds no `Photo`.
- **Requirement:** CLASS_DIAGRAM `Listing 1 *-- 1..6 Photo`.
- **Note:** photos are stored as absolute file paths and read with `java.io.File`; bundled resources are not files
  when packaged. An option is to copy sample images from `src/main/resources/images` to a temp folder at startup and
  seed their paths. Otherwise, document it as a known demo limitation.

### F32 — [FIXED - PR #47] My Deals cannot rate a returned loan (Deal History can)
> [!NOTE]
> **Status: FIXED (PR #47)**
> - Updated `MyDealsItem.canRate()` to `(deal.getStatus() == DealStatus.COMPLETED || deal.getStatus() == DealStatus.RETURNED) && !deal.isRated()`, matching `DealHistoryItem.canRate()` and `BuyerReviewService.canRateDeal`.
> - Updated `DealModelsTest` to assert that unrated returned loans can be rated in My Deals and already rated loans cannot.
- **Owner:** Charles.
- **Where:** `models/MyDealsItem.java:244` `canRate()` accepts only `COMPLETED`; `models/DealHistoryItem.java:165`
  and `BuyerReviewService.canRateDeal` accept `COMPLETED` or `RETURNED`.
- **Suggested fix:** align `MyDealsItem.canRate()` with the service.

### F33 — All Deals matches a deal's review by users and listing instead of by deal
- **Owner:** Elio.
- **Where:** `services/AdminDealsService.java:131` loops over all reviews matching seller + buyer + listing; with
  two deals between the same people on the same listing (e.g. two loans), the wrong review can be shown.
- **Suggested fix:** use `dataService.getReviewForDeal(deal.getId())` first, and the current matching only as a
  fallback for seeded reviews without `dealId`.

### F34 — Seller cannot cancel or mark sold a Reserved deal (*Decision*)
- **Owner:** Antoine.
- **Note:** the brief's overview says sellers can "mark as sold"; the detailed use cases make the **buyer** complete
  a sale (UC 1.6 S5). If a buyer never completes, the listing stays Reserved forever and the seller can neither edit,
  delete, nor cancel it. Options: a seller "Mark as sold" on Reserved listings, and/or a seller "Cancel deal" that
  returns the listing to Live and notifies the buyer.

### F35 — [FIXED - PR #52] Documentation out of date or inconsistent
> [!NOTE]
> **Status: FIXED (PR #52)**
> - Fully overhauled `README.md`:
>   - Removed legacy "UI Only" starter skeleton headers and "Next Steps (Backend)" placeholder text.
>   - Added comprehensive, professional documentation for all **Buyer Portal (US-B01 to US-B11)** and **Admin Portal (US-A01 to US-A08)** user stories, matching the depth of the Seller stories.
>   - Clarified password hashing schemes (PBKDF2 for sellers via `PasswordHasher`, SHA-256 with salt for buyers/profiles via `PasswordUtil`), and documented that demo credentials (`password1`, `admin123`) were pre-seeded.
>   - Fixed VS Code launch instruction to use `Launcher.java`.
>   - Added complete system architecture summary table mapping all 28 use cases to primary services and test suites.
> - Corrected `activity1/CLASS_DIAGRAM.md:115` multiplicity: `Buyer "1" --> "*" Review : Rates` (enabling buyers to rate multiple deals over time, matching Task 1.10).
> - Fixed duplicate task ID `T1.5` in `docs/project_management/sprint_backlog/sprint_2_burndown.csv` (renumbered lines cleanly to `T1.1`–`T1.11`).
> - Fixed hardcoded machine-specific absolute path in `docs/project_management/sprint_backlog/sprint_2_backlog.md:86` to a relative link (`[sprint_2_burndown.csv](sprint_2_burndown.csv)`).
> - Updated `docs/project_management/meeting_minutes/meeting_03_minutes.md` to accurately reflect QA coverage goals and Javadoc tasks.
- **Owner:** Team.
- **Items:**
  - `README.md`:
    - The "Features Implemented (UI Only)" and "For Students — Next Steps (Backend)" sections are out of date
      (they say buyer and admin logins are still to migrate to hashing).
    - "Password Rules" claims PBKDF2 for everyone (see F02).
    - Only the seller stories (US-S01…S09) are documented: add sections for the Buyer and Admin features so the TA
      can grade them.
    - State that the demo passwords (`password1`, `admin123`) predate the password rules.
  - `docs/project_management/meeting_minutes/meeting_03_minutes.md` (dated 2026-11-21) reports 76.5 % coverage, a
    background auto-return timer and verified Javadoc: none of these is true yet (F05, F07, F06).
  - `docs/project_management/sprint_backlog/sprint_2_backlog.md:86` links to `file:///home/cdupouy/…`: use a relative link.
  - `docs/project_management/sprint_backlog/sprint_2_burndown.csv`: the id `T1.5` is used twice (lines 6-7).
  - `activity1/CLASS_DIAGRAM.md:115` `Buyer "1" --> "0..1" Review : Rates` contradicts Task 1.10 ("keep a record of
    every rating/review submitted by the buyer"); the code (one review per deal, many per buyer) is right. Should be
    `"*"` (and the same in `figures/class_diagram`).

---

## Already verified as correct (no action needed)

- All 9 Seller use cases (US-S01…US-S09, UC 2.1–2.9) match their specifications and acceptance criteria (except
  the dependencies noted in F09, F10, F20 and F34).
- Giveaway claims are atomic and a 10-thread concurrency test exists (`ActOnListingServiceTest`); seller offer
  acceptance is atomic and tested.
- View counts increment on every detail view (UC 1.4 step 2); 5-second minimum borrow time is enforced (buyer
  requests, seller listings and counter offers).
- Soft-delete policy (users `DELETED`, listings `REMOVED`, deletion blocked by active deals) is implemented as in
  CLASS_DIAGRAM rule 3.
- All 13 classes, their attributes and the 11 enumerations of `CLASS_DIAGRAM.md` exist with matching values.
- Test suite: 1,715 tests, 0 failures, 0 errors (`./mvnw clean test`).
