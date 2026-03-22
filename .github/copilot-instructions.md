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
| `widget/` | Glance-based home-screen widget |
| `model/` | Shared enums and UI models (`ShiftType`, `LeaveType`, `DayInfo`) |
| `di/` | Hilt modules (`AppModule`, `AuthModule`, `InviteModule`) |
| `ui/` | Theme, colors, shared composables |

## Build & Test

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew testDebugUnitTest      # Run unit tests
./gradlew lint                   # Lint checks
```

Min SDK 26 · Target/Compile SDK 35 · Kotlin 2.0.21 · Java 17

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

- **Database schema**: version 1, `exportSchema=false` — no Room migrations exist yet. Adding columns requires a migration or destructive fallback.
- **Firestore sync**: fire-and-forget writes. Conflict resolution is last-write-wins by date key.
- **Widget updates**: `ShiftWidgetUpdater.updateAll()` must be called after every local data mutation and settings change.
- **Invite validation**: currently client-side only (known limitation — migrate to Cloud Function).
- **5-day shift cycle**: hardcoded as [DAY, DAY, NIGHT, REST, OFF] in `CadenceEngine`. Anchor date + cycle index determine all shifts.

## Documentation

- [docs/architecture.md](docs/architecture.md) — Data flow, sync strategy, deep-link scheme
- [docs/maintenance.md](docs/maintenance.md) — Dependency versions, Firebase setup, known limitations, upgrade checklist
- [docs/user-guide.md](docs/user-guide.md) — Quick start, feature reference, widget setup, troubleshooting
