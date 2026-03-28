# SplitApp — Project Context

Android app that automatically detects bank SMS transactions, shows push notifications, and lets users split expenses with friends via the Splitwise API. There is **no manual transaction entry** — the app is SMS-only by design.

## Core User Flow

1. Bank SMS arrives → `SmsReceiver` intercepts → `TransactionParser` extracts amount/merchant → saved to Room DB → push notification shown
2. User taps notification → `SplitActivity` opens → `SplitScreen` loads transaction with friends/groups from Splitwise
3. User selects friends (or a group), adjusts ratios, edits title if needed → submits to Splitwise API
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
├── model/          Transaction, Friend, Group, OrderItem, TransactionStatus
├── data/           AppDatabase, TransactionDao, PreferencesStore, Converters
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
- Extracts amounts with currencies: Rs, INR, ₹, SGD, USD, EUR, GBP, AED
- Description priority: UPI ID (`VPA`/`UPI` markers) → `at/to/for MERCHANT` → `on MERCHANT` (uppercase) → "Transaction"
- MERCHANT_PATTERN has a **negative lookahead** to skip currency codes after `at/to/for` (prevents capturing "INR 500" as merchant name)
- UPI_ID_PATTERN uses `VPA[:\s]*` to handle "VPA merchant@ybl" (space after VPA)

### SplitViewModel
- **Main split**: Load transaction + friends/groups → select friends or group → set ratios (equal or custom) → submit
- **Itemized split**: Init participants → scan receipt via Gemini VLM → assign items to participant subsets via checkboxes → compute per-person totals → submit
- `equalSplit()`: divides 100% by (friends + 1) to include current user
- `submitItemizedSplit()` takes `description` as an explicit parameter (not read from state) to avoid race condition
- Pin state (friends/groups) persisted to DataStore as comma-separated IDs
- `hasManualSplits()` detects when group selection would overwrite manual friend selections (triggers confirmation dialog in UI)

### GeminiReceiptExtractor
- Uses `gemini-3.1-flash-lite-preview` model via REST API
- Reads image URI → base64 → sends with structured extraction prompt
- Prompt instructs: extract item names + prices (discounted, not MRP), mark fees, skip totals/FREE items
- Strips markdown code fences from response, parses JSON array, filters price > 0
- Handles Blinkit, Zepto, Swiggy Instamart, Flipkart Minutes receipt formats

### SplitwiseRepository
- Bearer token auth from stored API key
- `createExpense()` builds form-encoded field map: user 0 = you (paid full amount), users 1..N = friends (paid 0, owe their share)
- `syncGroups()` filters out group id=0 ("Non-group expenses") and excludes current user from member lists
- Friends/groups cached as JSON in DataStore

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
- "Equal Split" button rebalances all selected friends
- "Itemized Split" button (enabled only when friends are selected) enters itemized mode

## Design Decisions

- **No manual transaction entry**: "If I had to do it manually I'll do it in Splitwise itself, why this app" — the app's value is automatic SMS detection only
- **No SMS monitoring toggle**: Removed because disabling it renders the app non-functional (there's no other input method)
- **Gemini VLM over ML Kit OCR**: ML Kit failed on real receipt screenshots (items/prices on separate lines, dual MRP/discounted prices, UI noise). Gemini handles all grocery app receipt formats naturally
- **Multi-select checkboxes over bitmask dropdown**: Bitmask generates 2^n-1 subsets which is unusable with many participants. Checkboxes scale linearly
- **BigDecimal rounding**: Per-person amounts rounded to 2 decimal places (HALF_UP) before Splitwise submission. Residual pennies absorbed by the payer

## Permissions

- `RECEIVE_SMS`, `READ_SMS` — core functionality (SMS interception)
- `INTERNET` — Splitwise API, Gemini API
- `POST_NOTIFICATIONS` — transaction alerts
- `CAMERA` — receipt photo capture for itemized split

## External APIs

- **Splitwise API** (`https://secure.splitwise.com/api/v3.0/`): get_current_user, get_friends, get_groups, create_expense
- **Gemini API** (`https://generativelanguage.googleapis.com/v1beta/`): generateContent with gemini-3.1-flash-lite-preview model

## Testing

194 unit tests in `app/src/test/` covering:
- `TransactionParserTest` (50) — all banks, currencies, keywords, merchant extraction, rejection cases
- `SplitViewModelTest` (45) — equal split math, group/friend toggle, ratio clamping, pin toggle, itemized mode (init, add/edit/delete, bucket assignment, per-person totals)
- `GeminiReceiptExtractorTest` (16) — JSON parsing, code fence stripping, real receipt formats, edge cases
- `FriendSearchAndSortTest` (16) — search by name/email, pin-based sorting, group search
- `ModelTest` (11) — Friend displayName, Transaction/OrderItem defaults, enum completeness
- `SplitwiseRepositoryTest` (10) — expense field map construction, amount formatting
- `SplitwiseApiModelsTest` (9) — JSON deserialization with @SerializedName
- `SplitwiseGroupFilterTest` (9) — group id=0 filtering, current user exclusion, JSON round-trips
- `ConvertersTest` (8) — Room TypeConverter for TransactionStatus
- `MismatchDetectionTest` (8) — item total vs transaction amount mismatch logic
- `GeminiApiModelsTest` (6) — Gemini request/response serialization (snake_case)
- `ItemizedRoundingTest` (6) — BigDecimal rounding for split amounts

Run tests: `./gradlew testDebugUnitTest` (requires generating gradle wrapper first if missing)

## Build

- Gradle version catalog at `gradle/libs.versions.toml`
- No `gradlew` in repo — generate with `gradle wrapper` if needed
- JDK bundled with Android Studio at `/Applications/Android Studio.app/Contents/jbr/Contents/Home/`
- Android SDK at `~/Library/Android/sdk`
