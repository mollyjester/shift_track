package com.slikharev.shifttrack.sync

import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.repository.SpectatorRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes the locally-cached spectator data from Firestore.
 *
 * Called from:
 * - [ShiftTrackMessagingService] when a push notification arrives
 *   (host data changed).
 * - [CalendarViewModel] on month load (future — could be wired here).
 *
 * The cache is stored in [AppDataStore] as a list of
 * [AppDataStore.SpectatorWidgetEntry] items (date + shift info).
 * The widget and DayDetail screen read from this cache when offline.
 */
@Singleton
class SpectatorCacheRefresher @Inject constructor(
    private val appDataStore: AppDataStore,
    private val spectatorRepository: SpectatorRepository,
) {

    /**
     * Fetches the selected host's schedule for the next 7 days from Firestore
     * and writes the result to the local DataStore cache.
     *
     * Does nothing if spectator mode is off or no host is selected.
     */
    suspend fun refresh() {
        val snapshot = appDataStore.readWidgetSnapshot()
        if (!snapshot.spectatorMode) return
        val hostUid = snapshot.selectedHostUid ?: return

        val today = LocalDate.now()
        val endDate = today.plusDays(AppDataStore.MAX_WIDGET_DAYS.toLong() - 1)

        val infos = spectatorRepository.getDayInfosForRange(hostUid, today, endDate)
        if (infos.isEmpty()) return

        val entries = infos.map { d ->
            AppDataStore.SpectatorWidgetEntry(
                date = d.date.toString(),
                shiftType = d.shiftType.name,
                hasLeave = d.hasLeave,
                halfDay = d.halfDay,
                leaveType = d.leaveType?.name,
            )
        }
        appDataStore.setSpectatorWidgetCache(entries)
    }
}
