# Architecture Overview

## High-Level Data Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Compose UI Layer                               │
│  AuthScreen  OnboardingScreen  DashboardScreen  CalendarScreen          │
│  DayDetailScreen  SettingsScreen  InviteRedemptionScreen                │
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
│  OvertimeRepository              │  │   shift color prefs, spectator,   │
│  SpectatorRepository             │  │   spectator calendar cache)       │
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
│  3. ShiftWidgetUpdater  →  RemoteViews widget refresh                   │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│          Firebase Cloud Functions (spectator push notifications)         │
│  Triggers on write to users/{uid}/shifts|leaves|overtime/{date}         │
│  → Sends data-only FCM to spectators → ShiftTrackMessagingService      │
│  → SpectatorCacheRefresher + ShiftWidgetUpdater                         │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│          Alarm Feature (experimental, self-contained)                    │
│  AlarmPreferences (DataStore) ← SettingsViewModel                       │
│  AlarmTriggerScheduler  →  AlarmTriggerReceiver (evening check)         │
│  AlarmSetterActivity (Compose) → AlarmClock.ACTION_SET_ALARM            │
│  AlarmOverrideEntity (Room) → FirestoreSyncDataSource (Firestore sync)  │
│  MidnightAlarmScheduler → MidnightWidgetReceiver (widget refresh)       │
│  BootReceiver (re-registers all alarms after reboot)                    │
└─────────────────────────────────────────────────────────────────────────┘
```

## Key Design Decisions

### Offline-First

The Room database is the **single source of truth**. Every user action writes to Room immediately and the UI reads from Room. Firestore is written to asynchronously by `SyncWorker` and failures are retried with exponential back-off. The user never waits for a network response.

### Per-Category Leave Balance (v1.1)

Leave balances are stored per leave type per year. The `leave_balance` table has a unique index on `(year, user_id, leave_type)`, resulting in one row per `LeaveType` per year. Onboarding creates rows for all five categories (ANNUAL, SICK, PERSONAL, UNPAID, STUDY). `AnnualResetUseCase` carries over `totalDays` from the previous year for each category independently. `LeaveRepository.refreshUsedDays()` recalculates used days per category via `sumLeaveDaysByType`.

### Configurable Shift Colors (v1.1, updated v1.3)

Shift-type colors are user-configurable via Settings using an HSV color picker (Hue / Saturation / Brightness sliders) and stored as `Long` (ARGB) values in `AppDataStore`. At runtime, `ShiftColorConfig` is provided through `LocalShiftColors` (a `CompositionLocal`). All composable screens read colors from `LocalShiftColors.current`. The RemoteViews widget reads user-configured colors from `AppDataStore` at render time (since `CompositionLocal` is unavailable in the widget context).

### Widget Configuration (v1.2)

The home-screen widget supports three configurable properties, all stored in `AppDataStore`:

| Preference | Key | Type | Default |
|---|---|---|---|
| Background color | `widget_bg_color` | Long (ARGB) | `0xFFF8FDFF` (light surface) |
| Transparency | `widget_transparency` | Float (0.0–1.0) | `1.0` (fully opaque) |
| Days to show | `widget_day_count` | Int (1–7) | `4` |

Settings are applied at render time in `ShiftWidgetProvider.updateSingleWidget()`. The widget also reads user-configured shift-type colors, so custom colors are consistent between the app and the widget. Changes are applied immediately via `ShiftWidgetUpdater.updateAll()`.

Widget configuration is accessed via the system's long-press → Reconfigure menu (declared as `reconfigurable` in `shift_widget_info.xml`). `WidgetConfigActivity` is a full-screen Compose activity with a "Done" button that sets `RESULT_OK` and returns the user to the home screen.

### Spectator Mode (v2.2, updated v2.8)

A `spectator_mode` boolean preference in `AppDataStore` controls whether the calendar is read-only. Set during onboarding when the user toggles "Spectator Only" (skipping anchor/leave setup). `DayDetailViewModel` exposes `isSpectator: StateFlow<Boolean>`, and `DayDetailScreen` hides all editing controls (override, leave, overtime, notes) when true.

As of v2.8, spectator mode is fully integrated across all surfaces:

- **Dashboard**: Shows the selected host's upcoming shifts (fetched from Firestore via `SpectatorRepository`). Today's shift card and upcoming days are displayed. Leave balances and overtime are hidden since those are local-only data. If no host is selected, a prompt directs the user to the Calendar tab.
- **Calendar**: Tapping any day in a spectated host's calendar opens the Day Detail screen, which shows the shift type, leave, overtime, and note information in read-only mode.
- **Day Detail**: `DayDetailViewModel` uses `combine(isSpectator, selectedHostUid).flatMapLatest` to switch between local `ShiftRepository` and remote `SpectatorRepository`. Spectators see the shift card, leave type, overtime flag, and note — all read-only. A fallback `DayInfo(OFF)` prevents infinite loading when the host has no data for the selected date.
- **Widget**: Works in spectator mode — fetches the selected host's shift data from Firestore and renders it the same as an own-schedule widget. The `WidgetSnapshot` includes `spectatorMode` and `selectedHostUid` fields.
- **Settings**: Shift Colors, Leave Type Colors, and Widget configuration sections are visible to spectators, allowing full colour and widget customisation. Only schedule, leave allowance, overtime, and invite sections are hidden.

### Spectator Push Notifications (v2.8)

Firebase Cloud Functions (`functions/index.js`) trigger on any write to `users/{hostUid}/shifts|leaves|overtime/{date}`. Each trigger:
1. Reads the host's `spectators` array from their user document.
2. Collects FCM tokens for each spectator.
3. Sends a data-only multicast message `{type: "host_data_changed", hostUid}`.
4. Cleans up stale tokens.

On the Android side, `ShiftTrackMessagingService` receives the message and:
1. Triggers an immediate `SyncWorker` run.
2. Calls `SpectatorCacheRefresher.refresh()` to update the local cache.
3. Calls `ShiftWidgetUpdater.updateAll()` to refresh widgets.

### Offline Spectator Caching (v2.8)

`SpectatorRepository` caches all fetched host data into `AppDataStore` (`SPECTATOR_CALENDAR_CACHE` key). On subsequent requests, if Firestore is unreachable (offline), the repository returns matching entries from the local cache. The cache stores date, shiftType, hasLeave, halfDay, leaveType, isManualOverride, hasOvertime, and note for each day. `SpectatorCacheRefresher` pre-fetches the next 7 days when a push notification arrives.

### Half-Day Leave & Leave-Type Colors (v2.2, updated v2.7)

`DayInfo` carries `halfDay: Boolean` and `leaveType: LeaveType?`, populated by `ShiftRepository.getDayInfosForRange()` from `LeaveEntity`. On the calendar:

- **Half-day** cells render with a split background — the top half uses the shift color, the bottom half uses light grey (`#E0E0E0`).
- **Full-day leave** cells (non-half-day) use a light grey (`#E0E0E0`) background with a leave-type-colored dot.
- The calendar legend shows both shift types and all leave types with 30 dp colour circles.

Leave-type colors are user-configurable via Settings (HSV color picker) and stored as `Long` (ARGB) values in `AppDataStore`. At runtime, `LeaveColorConfig` is provided through `LocalLeaveColors` (a `CompositionLocal`). The widget reads leave colors from `AppDataStore` at render time.

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
2. After settings changes in `SettingsViewModel` (new anchor date, colors, widget config).
3. After every successful `SyncWorker` run.
4. After receiving a push notification about host data changes (`ShiftTrackMessagingService`).

In spectator mode, the widget reads `spectatorMode` and `selectedHostUid` from the `WidgetSnapshot`. When both are set, `ShiftWidgetProvider` fetches the host's shift data via `SpectatorRepository` (Firestore) instead of computing from the local anchor. Leave and overtime indicators from the host's Firestore data are included.

`ShiftWidgetUpdater` swallows errors so a missing widget host never crashes the caller.

### Midnight Widget Refresh (v3.0)

`MidnightAlarmScheduler` schedules an exact alarm at 00:00 each night using `AlarmManager.setExactAndAllowWhileIdle()`. `MidnightWidgetReceiver` fires at midnight, calls `ShiftWidgetUpdater.updateAll()`, and re-schedules for the next midnight. `BootReceiver` re-registers the alarm after device reboot. This ensures the widget always shows the correct "today" column even if the app was not opened that day.

### Experimental Alarm Feature (v3.0)

All code lives in the `alarm/` package for isolation. Integration points are marked with `// [EXPERIMENTAL:ALARM]` comments for easy removal.

**Flow:** DataStore preferences → `AlarmTriggerScheduler` fires an exact alarm at the configured trigger time → `AlarmTriggerReceiver` checks if tomorrow is a DAY shift via `CadenceEngine` → posts a high-priority notification → user taps → `AlarmSetterActivity` opens → user reviews/customises alarm configuration → `AlarmClock.ACTION_SET_ALARM` intents fire silently → alarms set in the system Clock app.

Per-day overrides are stored in Room (`alarm_overrides` table) and synced to Firestore (`users/{uid}/alarm_overrides/{date}`) via `SyncWorker`. Spectators never see or trigger the alarm feature (gated by `spectatorMode` check).

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

A spectator is a user whose UID appears in `users/{hostUid}.spectators`. Firestore rules grant spectators `read` access to `users/{hostUid}/{shifts,leaves,overtime}` but deny all writes.

As of v2.8, Firebase Cloud Functions send push notifications to spectators when the host's data changes. `SpectatorRepository` caches fetched data locally so spectators can view the schedule offline. The widget and calendar cache are populated on every successful fetch and on push notification receipt via `SpectatorCacheRefresher`.

---

## Deep Link Scheme

| URI | Destination | Validation |
|---|---|---|
| `shiftapp://day/{date}` | `DayDetailScreen` — shows shift, leave, overtime for the given date | `date` must match `^\d{4}-\d{2}-\d{2}$` |
| `shiftapp://invite/{token}` | `InviteRedemptionScreen` — validates and redeems the token | `token` must be a valid UUID (36-char hex-dash) |

The token is a UUID generated by `FirestoreInviteRepository.createInvite`. It contains no user-identifying information. The document at `invites/{token}` stores the `hostUid`, but the deep link itself is opaque. Invalid format arguments are silently dropped (composable exits early).
