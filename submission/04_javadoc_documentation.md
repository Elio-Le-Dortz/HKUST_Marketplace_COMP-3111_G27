# 4. Documentation on Implemented Tasks Using JavaDoc

[← Coverage Report](03_coverage_report.md) · [Submission Hub](README.md) · [Next: Supplementary Notes →](05_supplementary_notes.md)

All production packages across the Buyer, Seller, and Administrator tasks are thoroughly documented using standard JavaDoc comments compliant with HTML5 doclint verification.

---

## 4.1 JavaDoc Coverage Across Implemented Tasks

1. **Application Service Layer (`com.hkust.marketplace.services` — 30 classes/records):**
   - **Buyer Services:** `ListingFilterService`, `ListingDetailService`, `ActOnListingService`, `ChatService`, `DealService`, `BuyerProfileService`, `BuyerNotificationService`, `DealHistoryService`, `BuyerReviewService` (plus `DummyDataService` registration/login endpoints).
   - **Seller Services:** `SellerRegistrationService`, `SellerLoginService`, `CreateListingService`, `MyListingsService`, `OfferManagementService`, `SellerProfileService`, `SellerNotificationService`, `SellerStatisticsService`, `ReviewHandlingService`, `SellerChatService`.
   - **Admin Services:** `AdminLoginService`, `ListingReviewService`, `UserManagementService`, `AdminProfileService`, `AdminDealsService`, `AdminNotificationService`, `AdminListingService`, `AdminRatingService`.
   - Every public class and method documents preconditions (`@param`), return contracts (`@return`), and RBAC/validation exceptions (`@throws`).

2. **Domain Models & Enumerations (`com.hkust.marketplace.models`):**
   - Core entities (`User`, `Buyer`, `Seller`, `Admin`, `Listing`, `Photo`, `Offer`, `Deal`, `Review`, `Notification`, `Report`, `ChatThread`, `ChatMessage`) and result/criteria models document attributes, invariants, and lifecycle transitions.
   - All core enumerations (`Condition`, `MeetupLocation`, `AccountStatus`, `DealStatus`, `OfferStatus`, `Category`, `UserRole`, `ListingType`, `ReportStatus`, `OfferType`, `ListingStatus`) include constant-level and parser-method docstrings.

3. **Utilities, Persistence & Controllers (`com.hkust.marketplace.database`, `utils`, `controllers.*`):**
   - `DatabaseManager`, `DummyDataService`, `SessionManager`, `PasswordHasher`, `PasswordUtil`, `PasswordPolicy`, `AutoReturnScheduler`, `ListingPhotoStorage`, and modular portal controllers all contain class/method JavaDoc.
   - All angle brackets and ampersands in doc comments are properly escaped (`&amp;`, `&lt;`, `&gt;`, `{@code ...}`) so the Maven JavaDoc plugin passes cleanly.

---

## 4.2 Generating & Viewing the API Documentation

```bash
# Build HTML JavaDoc site
./mvnw javadoc:javadoc
```

- **Generated Entry Point:** `target/site/apidocs/index.html`
- **IDE Shortcut:** Use the shared `.run/` IntelliJ run configuration **`Maven Javadoc`**.
