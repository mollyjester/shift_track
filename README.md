# ShiftTrack

ShiftTrack is an Android app for rotating-shift workers covering personal shift scheduling, leave management, overtime tracking, and cross-device calendar sharing with a designated viewer (spectator).

---

## Table of Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Setup](#setup)
4. [Building & Running](#building--running)
5. [Running Tests](#running-tests)
6. [Dependencies](#dependencies)
7. [Firebase Configuration](#firebase-configuration)
8. [Known Limitations](#known-limitations)

---

## Features

| Feature | Description |
|---|---|
| **Cadence engine** | Pure rotating-shift calculator (DAY → DAY → NIGHT → REST → OFF) with anchor-date-based positioning |
| **Calendar** | Month view showing every day's shift; tap a day for leave/overtime entry or manual override |
| **Dashboard** | Today's shift plus the next 6 days; leave balance; weekly/annual overtime summary |
| **Leave management** | Record annual, sick, personal, unpaid, or other leave; half-day support; yearly balance tracking |
| **Overtime** | Log extra hours per day; yearly accumulation with manual compensation recording |
| **Annual reset** | On first launch of a new year the leave balance rolls over automatically, copying the previous year's entitlement |
| **Invite & spectator** | Generate a 7-day deep-link invite token; share it with a family member or manager who then gets a read-only view of your schedule |
| **Firestore sync** | Offline-first; all writes go to Room immediately and are synced to Firestore via WorkManager when connectivity is available |
| **Home-screen widget** | Glance-based widget showing the next 7 days' shifts; refreshes after every local data change and every sync |
| **Onboarding** | First-launch wizard to set the cadence anchor date and initial leave balance |

---

## Architecture

```
UI (Compose screens)
        │
        ▼
ViewModels (StateFlow / viewModelScope)
        │
        ├──▶ ShiftRepository ──▶ Room DB (ShiftDao, LeaveDao, OvertimeDao)
        ├──▶ LeaveRepository ──▶ Room DB (LeaveDao, LeaveBalanceDao)
        ├──▶ OvertimeRepository ──▶ Room DB (OvertimeDao, OvertimeBalanceDao)
        └──▶ AppDataStore (anchor date, onboarding flag, last-reset year)

SyncWorker (WorkManager)
        │
        ├──▶ FirestoreSyncDataSource ──▶ Firestore (users/{uid}/shifts|leaves|overtime)
        ├──▶ AnnualResetUseCase ──▶ Room DB (LeaveBalanceDao)
        └──▶ ShiftWidgetUpdater ──▶ Glance widget

InviteRepository (FirestoreInviteRepository)
        └──▶ Firestore (invites/{token}, users/{uid}.spectators)
```

**Key design decisions:**

- **Offline-first**: The Room database is the single source of truth. Firestore is written to asynchronously and the UI never waits for it.
- **Pure cadence engine**: `CadenceEngine` is a pure Kotlin object with no Android dependencies, making it trivially unit-testable.
- **Unidirectional data flow**: ViewModels expose `StateFlow`; screens only call methods (no shared mutable state).
- **WorkManager for sync**: Handles retries, back-off, and battery-friendly scheduling automatically.

---

## Setup

### Prerequisites

| Tool | Minimum version |
|---|---|
| Android Studio | Hedgehog (2023.1) or later |
| JDK | 17 |
| Android SDK | API 26 (minSdk) to API 35 (targetSdk) |
| Firebase project | Any project with Authentication (Google sign-in) and Firestore enabled |

### 1. Clone the repository

```bash
git clone https://github.com/mollyjester/shift_track.git
cd shift_track
```

### 2. Add `google-services.json`

Download `google-services.json` from the Firebase console and place it at:

```
app/google-services.json
```

### 3. Configure EncryptedSharedPreferences key (optional)

The app uses `EncryptedSharedPreferences` from AndroidX Security. No extra configuration is needed — the key is automatically generated on first launch.

### 4. Deploy Firestore security rules

```bash
firebase deploy --only firestore:rules
```

The rules file is at `firestore.rules` in the project root.

---

## Building & Running

### From Android Studio

Open the project, select the `app` run configuration, and click **Run ▶**.

### From the command line (ADB install)

```bash
# Set environment variables
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk

# Build the debug APK
./gradlew assembleDebug

# Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Running Tests

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew testDebugUnitTest
```

HTML reports are written to `app/build/reports/tests/testDebugUnitTest/index.html`.

### Test structure

| Package | What is tested |
|---|---|
| `engine` | `CadenceEngine` — all cycle positions, before/after anchor, range calculations |
| `data/repository` | `LeaveRepository`, `OvertimeRepository` — using in-memory fake DAOs |
| `sync` | `AnnualResetUseCase` — same-year no-op, carry-over, multi-year gap |
| `auth` | `AuthViewModel` — sign-in/out, error handling, onboarding state |
| `calendar` | `CalendarViewModel`, `DayDetailViewModel` |
| `dashboard` | `DashboardViewModel` |
| `invite` | `InviteViewModel` — token loading, redemption, expiry, race conditions |
| `settings` | `SettingsViewModel` |
| `onboarding` | `OnboardingViewModel` |
| `widget` | `WidgetShiftCalculator` |

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 2.0.21 | Language |
| Android Gradle Plugin | 8.8.0 | Build tooling |
| Jetpack Compose BOM | 2024.12.01 | UI framework |
| Navigation Compose | 2.8.9 | In-app navigation |
| Hilt | 2.54 | Dependency injection |
| Room | 2.7.0 | Local SQLite database |
| DataStore Preferences | (via BOM) | Settings / lightweight key-value storage |
| WorkManager | 2.10.0 | Background sync scheduling |
| Firebase BOM | 33.10.0 | Auth, Firestore, FCM |
| Glance | 1.1.1 | Compose-based home-screen widgets |
| MockK | 1.13.10 | Test mocking |
| kotlinx-coroutines-test | 1.9.0 | Coroutine test utilities |

For a complete list see `app/build.gradle.kts`.

---

## Firebase Configuration

### Required Firebase products

| Product | Usage |
|---|---|
| Authentication | Google Sign-In; offline credential caching via EncryptedSharedPreferences |
| Firestore | Shift overrides, leave, overtime sync; invite tokens; spectator list |
| Cloud Messaging (FCM) | Reserved for future push notifications; token is stored but not yet used for actual pushes |

### Firestore data model

```
users/{uid}
    displayName: String
    email: String
    fcmToken: String
    spectators: [String]  ← UIDs of viewers

users/{uid}/shifts/{YYYY-MM-DD}
users/{uid}/leaves/{YYYY-MM-DD}
users/{uid}/overtime/{YYYY-MM-DD}

invites/{token}           ← UUID token (7-day expiry, single-use)
    hostUid, hostDisplayName, createdAt, expiresAt, claimed, claimedBy
```

### Re-deploying to a new environment

1. Create a new Firebase project.
2. Enable **Google sign-in** in Authentication > Sign-in methods.
3. Create a **Firestore database** in production mode.
4. Deploy `firestore.rules`: `firebase deploy --only firestore:rules`
5. Register the Android app and download the new `google-services.json`.
6. Replace `app/google-services.json` and rebuild.

---

## Known Limitations

| Area | Limitation |
|---|---|
| Invite redemption | The spectators-array update runs client-side inside a Firestore transaction. In production, migrating to a Cloud Function provides stronger isolation. |
| Widget deep link | Tapping a widget row navigates to the DayDetail screen but requires the app to be running (cold-launch deep link not yet implemented). |
| Layered calendar | Multiple overlapping leave/overtime icons on a single calendar day are not yet supported — only the first entry is shown. |
| Annual reset prompt | The roll-over happens silently. A user-facing confirmation prompt (to adjust the new year's entitlement before accepting) is deferred. |
| Spectator writes | Spectators are prevented from writing by Firestore rules, but there is no in-app indication telling a spectator that a given action is read-only. |
| Notifications | FCM token is stored and uploaded, but push notifications for "shift updated by owner" are not yet triggered from the server. |
