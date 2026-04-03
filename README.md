# SplitApp

Android app that automatically detects bank SMS transactions and lets you split expenses with friends via the Splitwise API. No manual entry — the app's value is automatic SMS detection only.

## How it works

1. Bank SMS arrives (HSBC, SBI, BOB, Federal Bank, ICICI)
2. App extracts amount and merchant, shows a push notification
3. Tap notification to select friends or a group, adjust splits, and submit to Splitwise
4. For groceries: scan a receipt screenshot with Gemini AI, assign items to participants, submit per-person totals

## What it does

- Automatically detects bank debit SMS and shows a notification
- Filters out OTP/security messages — they are never captured or stored
- Splits expenses by percentage or absolute rupee amount (per-friend toggle)
- Lets you edit the transaction amount and title before submitting
- Supports Splitwise groups — select a group to auto-populate members
- Itemized splitting — scan a receipt screenshot with Gemini AI, assign items to participants via checkboxes
- Pin frequently used friends and groups for quick access
- Privacy-first — no raw SMS body stored, transactions deleted from device after submit/skip

## What it doesn't do

- No manual transaction entry — if the SMS isn't detected, there's no way to add it manually. Use Splitwise directly for those
- No transaction history — submitted and skipped transactions are deleted immediately, not kept as a log
- No credit transaction detection — only debit keywords (debited, spent, paid, etc.) are recognized
- No multi-currency splits — amounts are parsed in multiple currencies but splits are always in the original currency
- No offline support — requires internet to submit to Splitwise and scan receipts
- No support for banks beyond HSBC, SBI, BOB, Federal Bank, and ICICI sender IDs

## Building

```bash
./gradlew assembleDebug
```

Requires:
- Android Studio (JDK bundled at `/Applications/Android Studio.app/Contents/jbr/Contents/Home/`)
- Android SDK 35

## Testing

```bash
./gradlew testDebugUnitTest
```

189 unit tests covering SMS parsing, split math, rounding, API field construction, receipt extraction, and more.

## Setup

1. Install the app on your Android device
2. Grant SMS and notification permissions when prompted
3. Go to Settings tab and enter your Splitwise API key
4. Optionally enter a Gemini API key for receipt scanning

## Architecture

- **Language**: Kotlin, Jetpack Compose, Material 3
- **Pattern**: MVVM with `AndroidViewModel` + `StateFlow`
- **Persistence**: Room (pending transactions), DataStore (API keys, cached friends/groups)
- **Network**: Retrofit 2 + OkHttp (Splitwise API, Gemini API)
- **Min SDK**: 26, Target SDK: 35

## Privacy and data minimization

This app handles sensitive financial SMS, so it's built to capture and retain as little as possible:

- **No raw SMS stored** — only the extracted amount, merchant name, and timestamp are saved. The original SMS body is discarded immediately after parsing
- **OTP/security messages rejected** — SMS containing OTP, verification code, or security code keywords are filtered out before any processing. They are never stored or displayed
- **Transactions are ephemeral** — once you submit or skip a transaction, it's deleted from the device. There is no transaction history or log
- **No SMS inbox access** — the app listens for incoming SMS via broadcast only. It never reads your SMS inbox, even though `READ_SMS` is declared (required by some devices for broadcast delivery)
- **No data sent to third parties** — transaction data is only sent to Splitwise (the service you're splitting with). Receipt images are sent to Google Gemini only when you explicitly scan a receipt
- **API credentials protected** — HTTP logging is disabled in release builds to prevent bearer tokens from leaking via logcat
- **Minimal permissions** — the app requests only what it needs: SMS reception, internet, notifications, and camera (optional, for receipts only)
