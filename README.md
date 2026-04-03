# SplitApp

Android app that automatically detects bank SMS transactions and lets you split expenses with friends via the Splitwise API. No manual entry — the app's value is automatic SMS detection only.

## How it works

1. Bank SMS arrives (HSBC, SBI, BOB, Federal Bank, ICICI)
2. App extracts amount and merchant, shows a push notification
3. Tap notification to select friends or a group, adjust splits, and submit to Splitwise
4. For groceries: scan a receipt screenshot with Gemini AI, assign items to participants, submit per-person totals

## Features

- **Automatic SMS detection** with OTP filtering (OTPs are never captured or stored)
- **Split by percentage or absolute amount** with a per-friend toggle
- **Editable transaction amount and title** (pre-filled from SMS)
- **Group support** — select a Splitwise group to auto-populate members
- **Itemized splitting** — scan receipt screenshots with Gemini VLM, assign items via checkboxes
- **Pin friends/groups** for quick access
- **Privacy-first** — no raw SMS stored, transactions deleted after submit/skip

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

## Permissions

| Permission | Purpose |
|---|---|
| `RECEIVE_SMS` | Intercept incoming bank SMS |
| `READ_SMS` | Required on some devices for broadcast delivery |
| `INTERNET` | Splitwise and Gemini API calls |
| `POST_NOTIFICATIONS` | Transaction alerts |
| `CAMERA` | Receipt photo capture |
