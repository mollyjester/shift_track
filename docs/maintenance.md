# Maintenance Notes

## Third-Party Dependencies

| Library | Version | Changelog / Release Notes |
|---|---|---|
| Kotlin | 2.0.21 | https://github.com/JetBrains/kotlin/releases |
| Android Gradle Plugin | 8.8.0 | https://developer.android.com/build/releases/gradle-plugin |
| Jetpack Compose BOM | 2024.12.01 | https://developer.android.com/jetpack/compose/bom/bom-mapping |
| Navigation Compose | 2.8.9 | https://developer.android.com/jetpack/androidx/releases/navigation |
| Hilt | 2.54 | https://github.com/google/dagger/releases |
| Room | 2.7.0 | https://developer.android.com/jetpack/androidx/releases/room |
| DataStore | (via Compose BOM) | https://developer.android.com/jetpack/androidx/releases/datastore |
| WorkManager | 2.10.0 | https://developer.android.com/jetpack/androidx/releases/work |
| Firebase BOM | 33.10.0 | https://firebase.google.com/support/release-notes/android |
| Firebase Cloud Functions | Node.js 20, firebase-functions v5, firebase-admin v12 | https://firebase.google.com/docs/functions/release-notes |
| Glance | *(removed in v2.0)* | No longer used — widget is built with raw RemoteViews |
| MockK | 1.13.10 | https://github.com/mockk/mockk/releases |
| kotlinx-coroutines-test | 1.9.0 | https://github.com/Kotlin/kotlinx.coroutines/releases |

---

## Firebase Project Configuration Checklist (New Environment)

Use this list when re-deploying ShiftTrack to a new Firebase project.

- [ ] Create a Firebase project in the [Firebase Console](https://console.firebase.google.com/).
- [ ] Register the Android app with the correct package name (`com.slikharev.shifttrack`).
- [ ] Download `google-services.json` and place it at `app/google-services.json`.
- [ ] Enable **Google Sign-In** in Authentication → Sign-in methods.
- [ ] Create a **Firestore database** in **production mode**.
- [ ] Deploy the Firestore security rules: `firebase deploy --only firestore:rules`
- [ ] Deploy Cloud Functions: `firebase deploy --only functions` (requires Node.js 20)
- [ ] Enable **Cloud Messaging** (FCM). The default server key is sufficient.
- [ ] (Optional) Enable **App Check** to prevent unauthorized API access in production.
- [ ] Verify `google-services.json` contains the correct `current_key` for OAuth and the FCM `sender_id`.
- [ ] Build and test the app on a device to confirm Google sign-in and Firestore read/write work.

---

## Known Limitations and Deferred Features

| Area | Description | Impact |
|---|---|---|
| **Invite redemption** | The spectators-array update runs client-side in a Firestore transaction. A malicious client could bypass expiry checks by patching the local Firebase SDK. Mitigation: migrate redemption to a Cloud Function. | Low risk for personal/family use; address for public release. |
| **Widget deep link cold start** | ~~Tapping a widget row navigates to DayDetail only when the app is already running.~~ **Fixed in v2.0.1** — deep links now work on both cold and warm launch via programmatic navigation in NavHost. | Resolved. |
| **Layered calendar icons** | If a day has both leave and overtime, only the first indicator is shown on the calendar tile. | Cosmetic only; full detail is always visible on DayDetail. |
| **Annual reset confirmation** | The leave roll-over fires silently. A user-facing confirmation dialog to adjust the new year's entitlement before accepting is deferred. | Users can manually correct the total in Settings. |
| **Spectator read-only UI cues** | ~~The UI does not visually distinguish a spectator session.~~ **Fixed in v2.2** — `DayDetailScreen` now hides all editing UI in spectator mode and shows a "Spectator mode — calendar is read-only" message. Spectator mode is persisted in `AppDataStore`. | Resolved. |
| **Push notifications** | ~~FCM token is stored and uploaded, but the server never sends shift-update notifications to spectators.~~ **Fixed in v2.8** — Firebase Cloud Functions (`functions/index.js`) send data-only push notifications to spectators when the host's shifts, leaves, or overtime change. `ShiftTrackMessagingService` handles the push by triggering a sync, refreshing the spectator cache, and updating widgets. | Resolved. |
| **Multi-year leave history** | If the app is not opened for more than one year, intermediate years' leave data is not created. The new-year balance is created correctly from the most recent available year. | Leave history gap in the DB; no user impact if they don't query historical years. |
| **Offline spectator view** | ~~Spectator data is not cached locally; spectators cannot view the schedule while offline. The widget also requires network access to fetch the host's data.~~ **Fixed in v2.8** — `SpectatorRepository` caches fetched data to `AppDataStore` (`SPECTATOR_CALENDAR_CACHE`). When offline, cached data is served as fallback. `SpectatorCacheRefresher` pre-fetches the next 7 days on push notification receipt. | Resolved. |
| **Database migrations** | Room version 2 with `fallbackToDestructiveMigration()`. Schema export enabled for future migration tooling but no written migrations exist yet. v1.1 added `leave_type` column to `leave_balance` table and changed the unique index from `(year, user_id)` to `(year, user_id, leave_type)`. Adding columns requires a proper migration or accepts data loss. | Users lose local data on schema-breaking upgrades until migrations are added. |

---

## Upgrade Checklist

When upgrading major dependencies:

### Kotlin / AGP

1. Check the [Compose BOM compatibility matrix](https://developer.android.com/jetpack/compose/bom/bom-mapping) for the correct BOM version.
2. Run `./gradlew testDebugUnitTest` after upgrading to catch any API breaks.
3. If upgrading to Kotlin 2.x, check the Compose compiler compatibility table.

### Firebase BOM

1. Read the [Android release notes](https://firebase.google.com/support/release-notes/android) for breaking changes.
2. Firestore rules syntax may change between major SDK versions.
3. Test offline behaviour after upgrading the Firestore SDK.

### Room

1. If the schema changes, add a migration in `AppModule.kt` (or a dedicated `Migrations.kt`). Schema JSON is exported to `app/schemas/`.
2. Run `./gradlew testDebugUnitTest` and verify the new schema JSON is generated.
3. Since `fallbackToDestructiveMigration()` is active, missing migrations wipe data — only acceptable during pre-release.

### Glance (removed)

Glance was removed in v2.0. The widget now uses raw `RemoteViews` + `AppWidgetProvider`. No Glance upgrade steps apply.
