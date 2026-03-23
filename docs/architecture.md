# Architecture Overview

## High-Level Data Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Compose UI Layer                               │
│  AuthScreen  OnboardingScreen  DashboardScreen  CalendarScreen          │
│  DayDetailScreen  SettingsScreen  InviteRedemptionScreen  ShiftWidget   │
└────────────────────────────┬────────────────────────────────────────────┘
                             │  observes StateFlow / calls methods
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         ViewModel Layer                                 │
│  AuthViewModel  DashboardViewModel  CalendarViewModel  DayDetailViewModel│
│  SettingsViewModel  OnboardingViewModel  InviteViewModel                │
└──────────────┬──────────────────────────────────────┬───────────────────┘
               │ suspend calls / Flow                  │ suspend calls
               ▼                                       ▼
┌──────────────────────────────────┐  ┌───────────────────────────────────┐
│       Repository Layer           │  │         AppDataStore              │
│  ShiftRepository                 │  │  (anchor date, onboarding flag,   │
│  LeaveRepository                 │  │   last reset year, FCM token,     │
│  OvertimeRepository              │  │   shift color preferences)        │
│  InviteRepository (interface)    │  └───────────────────────────────────┘
└────────────┬─────────────────────┘
             │
      ┌──────┴──────┐
      ▼             ▼
┌─────────┐   ┌────────────────────────────────────────────────┐
│  Room   │   │                 Firestore                      │
│  DB     │   │  users/{uid}/shifts|leaves|overtime            │
│ (local) │   │  invites/{token}                               │
└─────────┘   └────────────────────────────────────────────────┘
      ▲
      │  reads / writes on schedule
      │
┌─────────────────────────────────────────────────────────────────────────┐
│                         SyncWorker (WorkManager)                        │
│  1. AnnualResetUseCase  →  LeaveBalanceDao                              │
│  2. FirestoreSyncDataSource  →  Firestore (shifts, leaves, overtime)    │
│  3. ShiftWidgetUpdater  →  Glance widget refresh                        │
└─────────────────────────────────────────────────────────────────────────┘
```

## Key Design Decisions

### Offline-First

The Room database is the **single source of truth**. Every user action writes to Room immediately and the UI reads from Room. Firestore is written to asynchronously by `SyncWorker` and failures are retried with exponential back-off. The user never waits for a network response.

### Per-Category Leave Balance (v1.1)

Leave balances are stored per leave type per year. The `leave_balance` table has a unique index on `(year, user_id, leave_type)`, resulting in one row per `LeaveType` per year. Onboarding creates rows for all five categories (ANNUAL, SICK, PERSONAL, UNPAID, OTHER). `AnnualResetUseCase` carries over `totalDays` from the previous year for each category independently. `LeaveRepository.refreshUsedDays()` recalculates used days per category via `sumLeaveDaysByType`.

### Configurable Shift Colors (v1.1)

Shift-type colors are user-configurable via Settings and stored as `Long` (ARGB) values in `AppDataStore`. At runtime, `ShiftColorConfig` is provided through `LocalShiftColors` (a `CompositionLocal`). All composable screens read colors from `LocalShiftColors.current`. The Glance widget reads user-configured colors from `AppDataStore` at render time (since `CompositionLocal` is unavailable in the widget context).

### Widget Configuration (v1.2)

The home-screen widget supports three configurable properties, all stored in `AppDataStore`:

| Preference | Key | Type | Default |
|---|---|---|---|
| Background color | `widget_bg_color` | Long (ARGB) | `0xFFF8FDFF` (light surface) |
| Transparency | `widget_transparency` | Float (0.0–1.0) | `1.0` (fully opaque) |
| Days to show | `widget_day_count` | Int (1–7) | `4` |

Settings are applied at render time in `ShiftWidget.provideGlance()`. The widget also reads user-configured shift-type colors, so custom colors are consistent between the app and the widget. Changes are applied immediately via `ShiftWidgetUpdater.updateAll()`.

### Pure Cadence Engine

`CadenceEngine` is a Kotlin `object` (singleton) with zero Android dependencies. Given an anchor date and anchor cycle index (0–4), it computes the shift type for any date using modular arithmetic:

```
cycleIndex(date) = floorMod(anchorCycleIndex + daysBetween(anchorDate, date), 5)
shiftType = CYCLE[cycleIndex]     where CYCLE = [DAY, DAY, NIGHT, REST, OFF]
```

`Math.floorMod` ensures correct behaviour for dates **before** the anchor (negative `daysBetween`).

### Unidirectional Data Flow

ViewModels expose immutable `StateFlow` values. Screens call ViewModel methods but never mutate state directly. This makes the app predictable and easy to test.

### Widget Refresh Strategy

`ShiftWidgetUpdater.updateAll()` is called:
1. After every local mutation in `DayDetailViewModel` (manual override, leave, overtime).
2. After settings changes in `SettingsViewModel` (new anchor date).
3. After every successful `SyncWorker` run.

`ShiftWidgetUpdater` swallows errors so a missing widget host never crashes the caller.

---

## Cadence Engine — Detailed Explanation

### The 5-Day Cycle

```
Index │ Shift
──────┼───────
  0   │ DAY
  1   │ DAY
  2   │ NIGHT
  3   │ REST
  4   │ OFF
```

A "rotation" consists of two day shifts followed by one night shift, one rest day, and one day off. After index 4 the cycle wraps back to index 0.

### Anchor Date

The **anchor date** is a specific calendar date for which the user knows their cycle position. This is entered once during onboarding. Once stored in `AppDataStore`, every date's shift is computed relative to the anchor:

```kotlin
val days = ChronoUnit.DAYS.between(anchorDate, targetDate)  // negative for past dates
val index = Math.floorMod(anchorCycleIndex + days, 5)
return CYCLE[index]
```

### Edge Cases

| Scenario | Behaviour |
|---|---|
| Date before anchor | `Math.floorMod` with negative offset correctly wraps to the previous cycle |
| `anchorCycleIndex` out of 0–4 | `require()` throws `IllegalArgumentException` immediately |
| No anchor set yet | `ShiftRepository` returns an empty list so the UI shows no shift data |

---

## Firestore Sync Strategy

### Write Path (Local → Cloud)

1. User action → ViewModel → Repository → Room DAO (immediate, returns to UI)
2. Repository marks the row `synced = false`
3. `SyncWorker` runs (triggered immediately after mutation OR on a 15-min periodic schedule)
4. Worker fetches all rows where `synced = false`, batch-writes to Firestore, marks them `synced = true`

### Conflict Resolution

Each shift/leave/overtime document in Firestore is keyed by `date` string (`YYYY-MM-DD`). Writes use `SetOptions.merge()` so two devices writing the same date overwrite each other (last-write-wins). This is acceptable because only the **owner** can write shift data.

### Spectator (Read-Only) Access

A spectator is a user whose UID appears in `users/{hostUid}.spectators`. Firestore rules grant spectators `read` access to `users/{hostUid}/{shifts,leaves,overtime}` but deny all writes. The app does not currently push real-time updates to spectators; they reload data on app open.

---

## Deep Link Scheme

| URI | Destination | Validation |
|---|---|---|
| `shiftapp://day/{date}` | `DayDetailScreen` — shows shift, leave, overtime for the given date | `date` must match `^\d{4}-\d{2}-\d{2}$` |
| `shiftapp://invite/{token}` | `InviteRedemptionScreen` — validates and redeems the token | `token` must be a valid UUID (36-char hex-dash) |

The token is a UUID generated by `FirestoreInviteRepository.createInvite`. It contains no user-identifying information. The document at `invites/{token}` stores the `hostUid`, but the deep link itself is opaque. Invalid format arguments are silently dropped (composable exits early).
