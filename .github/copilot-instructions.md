# ShiftTrack — Copilot Instructions

## Architecture

MVVM + Repository + DataSource with Jetpack Compose UI. Single-Activity, no Fragments.

```
Compose Screen → ViewModel (StateFlow) → Repository → Room DAO / DataStore
                                                     ↘ Firestore (async via SyncWorker)
```

- **Offline-first**: Room is the single source of truth. Firestore writes are async via WorkManager. UI never waits for network.
- **Unidirectional data flow**: ViewModels expose immutable `StateFlow<UiState>`. Screens call suspend methods; state updates reactively.
- **Pure CadenceEngine**: shift-cycle calculator with zero Android dependencies — always unit-testable without Robolectric.

See [docs/architecture.md](docs/architecture.md) for data flow details, sync strategy, and deep-link scheme.

## Project Layout

| Directory | Purpose |
|---|---|
| `auth/`, `calendar/`, `dashboard/`, `settings/`, `onboarding/`, `invite/` | Feature packages (Screen + ViewModel + UiState). `calendar/` also contains `CsvExporter` for CSV export. |
| `data/local/db/` | Room database, DAOs, entities |
| `data/remote/` | Firestore data sources and document models |
| `data/repository/` | Repository layer bridging local ↔ remote |
| `engine/` | Pure Kotlin shift-cycle logic (`CadenceEngine`) |
| `sync/` | WorkManager sync, FCM, annual reset, spectator cache refresh |
| `widget/` | RemoteViews-based home-screen widget + midnight refresh alarm |
| `model/` | Shared enums and UI models (`ShiftType`, `LeaveType`, `DayInfo`) |
| `di/` | Hilt modules (`AppModule`, `AuthModule`, `InviteModule`) |
| `ui/` | Theme, colors, shared composables, `ShiftColorConfig` + `LocalShiftColors`, `LeaveColors` |
| `functions/` | Firebase Cloud Functions for spectator push notifications (Node.js 20) |

## Build & Test

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew testDebugUnitTest      # Run unit tests
./gradlew lint                   # Lint checks
```

Min SDK 34 · Target/Compile SDK 35 · Kotlin 2.0.21 · Java 17

## Code Conventions

- **Kotlin style**: `kotlin.code.style=official` — trailing commas, explicit types on public APIs, type inference internally.
- **Compose screens**: `@Composable fun FeatureScreen(viewModel: FeatureViewModel = hiltViewModel())` — lambda-last parameter style.
- **State**: sealed `UiState` classes (Idle, Loading, Success, Error) exposed as `StateFlow` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- **Coroutines**: `viewModelScope` for ViewModel work; `flatMapLatest` for reactive multi-source streams; WorkManager for background sync.
- **Error handling**: `Result<T>.fold()` in repositories → `UiState.Error` in ViewModels. Swallow non-critical errors in widget updates.
- **DI**: `@HiltViewModel` on all ViewModels, `@HiltWorker` + `@AssistedInject` on Workers, `@Singleton` providers in AppModule.
- **Entities**: `synced` boolean flag for offline-first tracking. String dates (ISO-8601 `yyyy-MM-dd`). Unique constraints on `(date, userId)`.
- **Naming**: PascalCase classes, camelCase functions/properties, UPPER_SNAKE_CASE constants, PascalCase enum values.

## Testing

- JUnit 4 + MockK + kotlinx-coroutines-test
- Test naming: backtick-wrapped descriptive names (`` `one day after anchor is DAY` ``)
- Pattern: `@Before` setup with `StandardTestDispatcher`, `advanceUntilIdle()` for coroutine completion
- No instrumented/UI tests yet — unit tests only

## Key Gotchas

- **Database schema**: version 4, `exportSchema=true`, schema JSON exported to `app/schemas/`. `fallbackToDestructiveMigration()` is present. v1.1 added `leave_type` column to `leave_balance` with unique index `(year, user_id, leave_type)`.
- **Per-category leave**: Leave balances are stored per leave type per year. `LeaveRepository.refreshUsedDays()` calculates used days per category. `AnnualResetUseCase` carries over each category independently.
- **Configurable colors**: Per-shift-type colors stored as `Long` (ARGB) in `AppDataStore`, provided via `LocalShiftColors` (`CompositionLocal`). Widget reads user-configured colors from `AppDataStore` at render time. Colors are selected via an HSV color picker (Hue/Saturation/Brightness sliders).
- **Widget configuration**: Background color (`Long` ARGB), transparency (`Float` 0–1), and day count (`Int` 1–7) stored in `AppDataStore`. Applied in `ShiftWidgetProvider.updateSingleWidget()`. Widget configuration is accessed via the system's long-press → Reconfigure menu (`widgetFeatures="reconfigurable"` in `shift_widget_info.xml`). `WidgetConfigActivity` is a full-screen Compose activity with a "Done" button that returns the user to the home screen. Default widget size is 4×1 (one cell height). Each day cell shows the day-of-month number and shift/leave type label stacked vertically. Half-day leave renders as a split background (top = shift colour, bottom = light grey). Leave dots match the calendar rendering. In spectator mode, the widget fetches the host's shift data from Firestore via `SpectatorRepository` using `spectatorMode` and `selectedHostUid` fields from `WidgetSnapshot`.
- **Spectator mode**: `spectator_mode` boolean preference in `AppDataStore`. Set during onboarding via "Spectator Only" toggle. `DayDetailViewModel.isSpectator` StateFlow drives UI — `DayDetailScreen` hides all editing controls when true but displays leave type, overtime status, and notes in read-only mode. In spectator-only mode, the Dashboard shows the selected host's upcoming shifts (via `SpectatorRepository`), and Settings shows Account, Shift Colors, Leave Type Colors, and Widget sections.
- **Spectator calendar viewing**: `SpectatorRepository` reads host's shifts/leaves/overtime from Firestore subcollections and computes `DayInfo` list using the host's anchor (stored in `users/{uid}` document). Fetched data is cached locally in `AppDataStore` (`SPECTATOR_CALENDAR_CACHE`) for offline viewing. When Firestore is unreachable, cached data is served as fallback. `CalendarViewModel` switches between local `ShiftRepository` and remote `SpectatorRepository` based on `selectedHostUid`. CalendarScreen shows a dropdown selector ("My" + watched hosts). Tapping any day in a spectated host's calendar opens the DayDetail screen (read-only).
- **Spectator push notifications**: Firebase Cloud Functions (`functions/index.js`) trigger on writes to `users/{hostUid}/shifts|leaves|overtime/{date}`, read the host's `spectators` array, and send data-only FCM messages to spectators. `ShiftTrackMessagingService` receives messages and calls `SpectatorCacheRefresher.refresh()` + `ShiftWidgetUpdater.updateAll()`. Requires `firebase deploy --only functions` for initial deployment.
- **Watched hosts**: `AppDataStore.WatchedHost(uid, displayName)` list persisted as pipe-delimited strings. `InviteViewModel.accept()` adds the host to the watched list and sets it as the selected host. `selectedHostUid` is persisted to remember the last viewed schedule.
- **Anchor syncing**: Anchor date + cycle index are synced to the Firestore user document (`users/{uid}.anchorDate`, `anchorCycleIndex`) during onboarding, settings update, and on every `SyncWorker` run so spectators can compute the host's cadence.
- **Half-day leave rendering**: `DayInfo.halfDay` and `DayInfo.leaveType` are populated from `LeaveEntity` by `ShiftRepository`. Calendar cells render half-day as split background (top = shift color, bottom = light grey `#E0E0E0`). Half-day leaves keep the original shift type rather than LEAVE. Full-day leave backgrounds are light grey with a leave-type-colored dot.
- **Leave type colors**: Per-leave-type colors are user-configurable via Settings (HSV picker) and stored as `Long` (ARGB) in `AppDataStore`. `LeaveColorConfig` + `LocalLeaveColors` (`CompositionLocal`) provide runtime access. Calendar legend includes leave types alongside shift types. Non-half-day leave cells show a colored dot matching the leave type. Widget reads leave colors from `AppDataStore` at render time.
- **Leave type backward compatibility**: `LeaveType.fromString()` maps legacy `"OTHER"` values to `STUDY`. All parsing code uses `fromString()` instead of `valueOf()` for safe migration.
- **Firestore sync**: fire-and-forget writes. Conflict resolution is last-write-wins by date key. Batch writes are chunked to 500 operations.
- **Widget updates**: `ShiftWidgetUpdater.updateAll()` must be called after every local data mutation and settings change. Widget errors are swallowed.
- **Invite validation**: currently client-side only (known limitation — migrate to Cloud Function).
- **5-day shift cycle**: hardcoded as [DAY, DAY, NIGHT, REST, OFF] in `CadenceEngine`. Anchor date + cycle index determine all shifts.
- **User authentication**: `UserSession.requireUserId()` throws `IllegalStateException` if no user is signed in — all repository methods use this instead of `orEmpty()`.
- **Transactions**: `LeaveRepository` and `OvertimeRepository` wrap mutations in `db.withTransaction` for atomicity.
- **Input limits**: Leave/overtime notes are truncated to 500 characters.
- **Default leave days**: configurable via `AppDataStore.defaultLeaveDays` (default 28 days), used by `AnnualResetUseCase`.
- **Deep links**: `shiftapp://day/{date}` and `shiftapp://invite/{token}` are validated (ISO date format and UUID format) before navigation.
- **Midnight widget refresh**: `MidnightAlarmScheduler` + `MidnightWidgetReceiver` + `BootReceiver` ensure the widget updates at 00:00 daily.
- **Delete account reauth**: `AuthRepository.reauthenticateAndDelete()` handles `FirebaseAuthRecentLoginRequiredException` with inline Google Sign-In picker instead of requiring sign-out/sign-in.
- **Income tracking**: `IncomeCalculator` (pure Kotlin, no Android deps) computes monthly income from shift hours, overtime, and configurable rates. Night shifts split at midnight using `shiftChangeoverHour`. Dashboard shows "Income {month}" with `←` back-navigation and month-name reset button. Settings uses free-text `OutlinedTextField` for rates/multipliers and Material3 `TimePicker` for changeover time. Public holidays are managed via a dialog behind a "Manage" button.
- **CSV export**: `CsvExporter` (pure Kotlin, no Android deps) generates RFC 4180-compliant CSV from `ExportRow` list. `CalendarViewModel.exportCsv()` merges `DayInfo` + overtime data, writes to `cacheDir/exports/`, shares via `FileProvider` + `Intent.ACTION_SEND`. Export button in `CalendarScreen` top bar is hidden when viewing spectated calendars. Date range picker defaults to current month with no upper limit.

## Documentation

- [docs/architecture.md](docs/architecture.md) — Data flow, sync strategy, deep-link scheme
- [docs/maintenance.md](docs/maintenance.md) — Dependency versions, Firebase setup, known limitations, upgrade checklist
- [docs/user-guide.md](docs/user-guide.md) — Quick start, feature reference, widget setup, troubleshooting
