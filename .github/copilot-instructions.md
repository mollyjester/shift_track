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
| `auth/`, `calendar/`, `dashboard/`, `settings/`, `onboarding/`, `invite/` | Feature packages (Screen + ViewModel + UiState) |
| `data/local/db/` | Room database, DAOs, entities |
| `data/remote/` | Firestore data sources and document models |
| `data/repository/` | Repository layer bridging local ↔ remote |
| `engine/` | Pure Kotlin shift-cycle logic (`CadenceEngine`) |
| `sync/` | WorkManager sync, FCM, annual reset |
| `widget/` | RemoteViews-based home-screen widget |
| `model/` | Shared enums and UI models (`ShiftType`, `LeaveType`, `DayInfo`) |
| `di/` | Hilt modules (`AppModule`, `AuthModule`, `InviteModule`) |
| `ui/` | Theme, colors, shared composables, `ShiftColorConfig` + `LocalShiftColors`, `LeaveColors` |

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

- **Database schema**: version 2, `exportSchema=true`, schema JSON exported to `app/schemas/`. `fallbackToDestructiveMigration()` is active — no manual migrations exist yet. v1.1 added `leave_type` column to `leave_balance` with unique index `(year, user_id, leave_type)`.
- **Per-category leave**: Leave balances are stored per leave type per year. `LeaveRepository.refreshUsedDays()` calculates used days per category. `AnnualResetUseCase` carries over each category independently.
- **Configurable colors**: Per-shift-type colors stored as `Long` (ARGB) in `AppDataStore`, provided via `LocalShiftColors` (`CompositionLocal`). Widget reads user-configured colors from `AppDataStore` at render time. Colors are selected via an HSV color picker (Hue/Saturation/Brightness sliders).
- **Widget configuration**: Background color (`Long` ARGB), transparency (`Float` 0–1), and day count (`Int` 1–7) stored in `AppDataStore`. Applied in `ShiftWidgetProvider.updateSingleWidget()`. Widget configuration is accessed via the system's long-press → Reconfigure menu (`widgetFeatures="reconfigurable"` in `shift_widget_info.xml`). `WidgetConfigActivity` is a full-screen Compose activity with a "Done" button that returns the user to the home screen. Default widget size is 4×1 (one cell height). Each day cell shows the day-of-month number and shift/leave type label stacked vertically. Half-day leave renders as a split background (top = shift colour, bottom = darker). Leave dots match the calendar rendering.
- **Spectator mode**: `spectator_mode` boolean preference in `AppDataStore`. Set during onboarding via "Spectator Only" toggle. `DayDetailViewModel.isSpectator` StateFlow drives UI — `DayDetailScreen` hides all editing controls when true. In spectator-only mode, the Dashboard shows a prompt message, and Settings shows only the Account section.
- **Spectator calendar viewing**: `SpectatorRepository` reads host's shifts/leaves/overtime from Firestore subcollections and computes `DayInfo` list using the host's anchor (stored in `users/{uid}` document). `CalendarViewModel` switches between local `ShiftRepository` and remote `SpectatorRepository` based on `selectedHostUid`. CalendarScreen shows a dropdown selector ("My" + watched hosts).
- **Watched hosts**: `AppDataStore.WatchedHost(uid, displayName)` list persisted as pipe-delimited strings. `InviteViewModel.accept()` adds the host to the watched list and sets it as the selected host. `selectedHostUid` is persisted to remember the last viewed schedule.
- **Anchor syncing**: Anchor date + cycle index are synced to the Firestore user document (`users/{uid}.anchorDate`, `anchorCycleIndex`) during onboarding and settings update so spectators can compute the host's cadence.
- **Half-day leave rendering**: `DayInfo.halfDay` and `DayInfo.leaveType` are populated from `LeaveEntity` by `ShiftRepository`. Calendar cells render half-day as split background (top = shift color, bottom = 30% darker). Half-day leaves keep the original shift type rather than LEAVE.
- **Leave type colors**: `LeaveColors` object maps each `LeaveType` to a fixed color. Calendar legend includes leave types alongside shift types. Non-half-day leave cells show a colored dot matching the leave type.
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

## Documentation

- [docs/architecture.md](docs/architecture.md) — Data flow, sync strategy, deep-link scheme
- [docs/maintenance.md](docs/maintenance.md) — Dependency versions, Firebase setup, known limitations, upgrade checklist
- [docs/user-guide.md](docs/user-guide.md) — Quick start, feature reference, widget setup, troubleshooting
