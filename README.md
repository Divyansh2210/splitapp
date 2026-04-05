# SplitApp

Android app that automatically detects bank SMS transactions and lets you split expenses with friends via the Splitwise API. No manual entry — the app's value is automatic SMS detection only.

## How it works

1. Bank SMS arrives from a supported bank (HSBC, SBI, BOB, Federal Bank, ICICI)
2. App extracts amount and merchant name, rejects OTP/security messages, and shows a push notification
3. Tap notification to open the split screen — search and select friends or a Splitwise group, adjust splits by percentage or rupee amount, edit the title and amount if needed, and submit
4. For grocery orders: enter itemized split mode, scan a receipt screenshot with Gemini AI, assign items to participants, and submit per-person totals
5. The expense is created on Splitwise (you paid the full amount, friends owe their share) and the transaction is deleted from the device

## What it does

- Automatically detects bank debit SMS and shows a notification
- Filters out OTP/security messages — they are never captured or stored
- Splits expenses by percentage or absolute rupee amount
- Lets you edit the transaction amount and title before submitting
- Searchable friend list and group selection from Splitwise
- Pin frequently used friends and groups for quick access
- Itemized splitting — scan a receipt screenshot with Gemini AI and assign items to participants
- Pending transactions listed on the home screen — tap any to split it

## What it doesn't do

- No manual transaction entry — if the SMS isn't detected, add the expense in Splitwise directly
- No transaction history — submitted and skipped transactions are deleted immediately
- No credit/refund detection — only debit transactions are recognized
- No offline mode — requires internet to submit to Splitwise and scan receipts
- No iOS version — Android only
- Limited bank support — only HSBC, SBI, BOB, Federal Bank, and ICICI sender IDs are recognized

## Privacy and data minimization

This app handles sensitive financial SMS, so it's built to capture and retain as little as possible:

- **No raw SMS stored** — only the extracted amount, merchant name, and timestamp are saved. The original SMS body is discarded immediately after parsing
- **OTP/security messages rejected** — SMS containing OTP, verification code, or security code keywords are filtered out before any processing. They are never stored or displayed
- **Transactions are ephemeral** — once you submit or skip a transaction, it's deleted from the device. There is no transaction history or log
- **No SMS inbox access** — the app listens for incoming SMS via broadcast only. It never reads your SMS inbox
- **No data sent to third parties** — transaction data is only sent to Splitwise. Receipt images are sent to Google Gemini only when you explicitly scan a receipt
- **Minimal permissions** — the app requests only what it needs: SMS reception, internet, notifications, and camera (optional, for receipts only)

## Setup

1. Install the app on your Android device
2. Grant SMS and notification permissions when prompted
3. Go to the Settings tab and enter your Splitwise API key
4. Optionally enter a Gemini API key for receipt scanning

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

198 unit tests covering SMS parsing, split math, rounding, API field construction, receipt extraction, and more.

## Architecture

- **Language**: Kotlin, Jetpack Compose, Material 3
- **Pattern**: MVVM with `AndroidViewModel` + `StateFlow`
- **Persistence**: Room (pending transactions), DataStore (API keys, cached friends/groups)
- **Network**: Retrofit 2 + OkHttp (Splitwise API, Gemini API)
- **Min SDK**: 26, Target SDK: 35
