# SplitApp — Project Context

Android app that automatically detects bank SMS transactions, shows push notifications, and lets users split expenses with friends via the Splitwise API. There is **no manual transaction entry** — the app is SMS-only by design.

## Core User Flow

1. Bank SMS arrives → `SmsReceiver` intercepts (with `goAsync()`) → `TransactionParser` extracts amount/merchant (rejects OTPs) → saved to Room DB → push notification shown
2. User taps notification → `SplitActivity` opens → `SplitScreen` loads transaction with friends/groups from Splitwise
3. User selects friends (or a group), adjusts ratios (by % or ₹ amount), edits title/amount if needed → submits to Splitwise API
4. For grocery orders: user enters **itemized split mode** → scans receipt screenshot with Gemini VLM → assigns items to participants via multi-select checkboxes → submits per-person totals to Splitwise

## Architecture

- **Language**: Kotlin, Jetpack Compose, Material 3
- **Pattern**: MVVM with `AndroidViewModel` + `StateFlow`
- **Persistence**: Room (transactions), DataStore (API keys, cached friends/groups, pins)
- **Network**: Retrofit 2 + OkHttp (Splitwise API, Gemini API)
- **Min SDK**: 26, Target SDK: 35

## Package Structure

```
com.splitapp/
├── model/          Transaction, Friend, Group, OrderItem
├── data/           AppDatabase, TransactionDao, PreferencesStore
├── api/            SplitwiseApi, SplitwiseRepository, GeminiApi, GeminiReceiptExtractor
├── parser/         TransactionParser (regex-based SMS parsing)
├── receiver/       SmsReceiver (BroadcastReceiver)
├── viewmodel/      SplitViewModel, SettingsViewModel, HistoryViewModel
├── ui/             SplitScreen, ItemizedSplitScreen, SettingsScreen, HistoryScreen
├── MainActivity    Bottom nav (Transactions / Settings), permission requests
├── SplitActivity   Launched from notification with transactionId extra
└── SplitApplication  Room database singleton
```

## Key Components

### TransactionParser
- Pre-filters by sender ID: HSBC, SBI, BOB, Federal Bank, ICICI (and their variants)
- Detects debit keywords: debited, spent, paid, payment, withdrawn, purchase, used, charged
- **OTP rejection**: Rejects SMS containing OTP, O.T.P, One Time Password, verification code, secure code, security code — prevents sensitive codes from being stored
- Extracts amounts with currencies: Rs, INR, ₹, SGD, USD, EUR, GBP, AED
- Description priority: UPI ID (`VPA`/`UPI` markers) → `at/to/for MERCHANT` → `on MERCHANT` (uppercase) → "Transaction"
- MERCHANT_PATTERN has a **negative lookahead** to skip currency codes after `at/to/for` (prevents capturing "INR 500" as merchant name)
- UPI_ID_PATTERN uses `VPA[:\s]*` to handle "VPA merchant@ybl" (space after VPA)
- **No raw SMS stored**: Only extracted `amount`, `description`, and `timestamp` are persisted — the raw SMS body is never saved to the database

### SplitViewModel
- **Main split**: Load transaction + friends/groups → select friends or group → set ratios (equal or custom) → submit
- **Itemized split**: Init participants → scan receipt via Gemini VLM → assign items to participant subsets via checkboxes → compute per-person totals → submit
- `equalSplit()`: divides 100% by (friends + 1) to include current user
- `submitItemizedSplit()` takes `description` as an explicit parameter (not read from state) to avoid race condition
- `buildItemizedDetails()` generates a human-readable breakdown (items with prices/assignees + per-person totals) sent as Splitwise expense notes
- `submitSplit()` takes `amount` from state (editable by user), not from `transaction.amount`
- Pin state (friends/groups) persisted to DataStore as comma-separated IDs
- `hasManualSplits()` detects when group selection would overwrite manual friend selections (triggers confirmation dialog in UI)
- `toggleFriend()` clears `selectedGroupId` to prevent submitting a stale group with mismatched participants
- Transactions are **deleted** on submit/skip (not status-updated) — only pending transactions exist in DB

### GeminiReceiptExtractor
- Uses `gemini-3.1-flash-lite-preview` model via REST API
- Reads image URI → base64 → sends with structured extraction prompt
- Prompt instructs: extract item names + prices (discounted, not MRP), mark fees, skip totals/FREE items
- Strips markdown code fences from response, parses JSON array, filters price > 0
- Handles Blinkit, Zepto, Swiggy Instamart, Flipkart Minutes receipt formats

### SplitwiseRepository
- Bearer token auth from stored API key
- `createExpense()` builds form-encoded field map: user 0 = you (paid full amount), users 1..N = friends (paid 0, owe their share)
- Optional `details` parameter populates the Splitwise expense notes field (used by itemized splits for item breakdown)
- Payer's owed share is derived from formatted friend shares to ensure exact penny-level totals
- `syncGroups()` filters out group id=0 ("Non-group expenses") and excludes current user from member lists
- Friends/groups cached as JSON in DataStore
- HTTP logging: `BASIC` level in debug builds, `NONE` in release (prevents bearer token leaks via logcat)

### ItemizedSplitScreen
- Bucket assignment uses **multi-select checkboxes per participant** (not bitmask dropdown — that was replaced due to combinatorial explosion with many participants)
- Fee items are locked to "Shared by all" and display a lock icon
- All items (including fees) are editable — edit dialog includes isFee checkbox
- Mismatch warning when item total differs from transaction amount by > ₹1, with "Add adjustment item" option

### SplitScreen
- Searchable friend list (by name or email) with pin-based sorting (pinned first)
- Searchable group chips with pin icons
- Confirmation dialog when selecting a group would replace existing manual friend selections
- Editable expense title (pre-filled from parsed SMS description)
- **Editable amount** (pre-filled from SMS-parsed amount) — user can override for the split
- Per-friend **₹/% toggle**: enter split as absolute rupee amount or percentage. Amount mode converts to ratio on the fly: `(amount / total * 100)`. Complementary value always shown
- "Equal Split" button rebalances all selected friends
- "Itemized Split" button (enabled only when friends are selected) enters itemized mode

## Design Decisions

- **No manual transaction entry**: "If I had to do it manually I'll do it in Splitwise itself, why this app" — the app's value is automatic SMS detection only
- **No SMS monitoring toggle**: Removed because disabling it renders the app non-functional (there's no other input method)
- **Gemini VLM over ML Kit OCR**: ML Kit failed on real receipt screenshots (items/prices on separate lines, dual MRP/discounted prices, UI noise). Gemini handles all grocery app receipt formats naturally
- **Multi-select checkboxes over bitmask dropdown**: Bitmask generates 2^n-1 subsets which is unusable with many participants. Checkboxes scale linearly
- **BigDecimal rounding**: Per-person amounts rounded to 2 decimal places (HALF_UP) before Splitwise submission. Residual pennies absorbed by the payer

## Permissions

- `RECEIVE_SMS` — core functionality (SMS broadcast interception)
- `READ_SMS` — required alongside `RECEIVE_SMS` on some devices/OEMs for broadcast delivery (app does NOT read the SMS inbox)
- `INTERNET` — Splitwise API, Gemini API
- `POST_NOTIFICATIONS` — transaction alerts
- `CAMERA` — receipt photo capture for itemized split

## External APIs

- **Splitwise API** (`https://secure.splitwise.com/api/v3.0/`): get_current_user, get_friends, get_groups, create_expense
- **Gemini API** (`https://generativelanguage.googleapis.com/v1beta/`): generateContent with gemini-3.1-flash-lite-preview model

## Testing

198 unit tests in `app/src/test/` covering:
- `TransactionParserTest` (54) — all banks, currencies, keywords, merchant extraction, rejection cases, OTP rejection
- `SplitViewModelTest` (46) — equal split math, group/friend toggle, group-clearing on friend toggle, ratio clamping, pin toggle, itemized mode (init, add/edit/delete, bucket assignment, per-person totals)
- `GeminiReceiptExtractorTest` (16) — JSON parsing, code fence stripping, real receipt formats, edge cases
- `FriendSearchAndSortTest` (16) — search by name/email, pin-based sorting, group search
- `SplitwiseRepositoryTest` (12) — expense field map construction, amount formatting, penny-residual handling, details field
- `SplitwiseApiModelsTest` (9) — JSON deserialization with @SerializedName
- `SplitwiseGroupFilterTest` (9) — group id=0 filtering, current user exclusion, JSON round-trips
- `ModelTest` (9) — Friend displayName, Transaction/OrderItem defaults
- `MismatchDetectionTest` (8) — item total vs transaction amount mismatch logic
- `ItemizedDetailsTest` (7) — itemized breakdown text for Splitwise expense notes
- `GeminiApiModelsTest` (6) — Gemini request/response serialization (snake_case)
- `ItemizedRoundingTest` (6) — BigDecimal rounding for split amounts

Run tests: `./gradlew testDebugUnitTest`

## Build

- Gradle version catalog at `gradle/libs.versions.toml`
- `gradlew` included in repo
- JDK bundled with Android Studio at `/Applications/Android Studio.app/Contents/jbr/Contents/Home/`
- Android SDK at `~/Library/Android/sdk`
- `buildConfig = true` enabled in `build.gradle.kts` for `BuildConfig.DEBUG` access

## Security

- **OTP filtering**: SMS containing OTP/verification keywords are rejected before parsing
- **No raw SMS storage**: Only extracted amount, description, and timestamp stored in Room DB
- **Transaction lifecycle**: Transactions deleted from DB on submit/skip — no permanent history of SMS data
- **API credentials**: HTTP logging set to `BASIC` (debug) / `NONE` (release) to prevent bearer token exposure in logcat
- **READ_SMS**: Permission declared for broadcast delivery compatibility but app never reads the SMS inbox
- **DB schema v2**: Destructive migration from v1 wipes any legacy raw SMS data on upgrade
