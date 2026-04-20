package com.slikharev.shifttrack.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.repository.LeaveRepository
import com.slikharev.shifttrack.data.repository.OvertimeRepository
import com.slikharev.shifttrack.data.repository.ShiftRepository
import com.slikharev.shifttrack.data.repository.SpectatorRepository
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.widget.ShiftWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class UpcomingDay(
    val dayInfo: DayInfo,
    val isToday: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    leaveRepository: LeaveRepository,
    private val overtimeRepository: OvertimeRepository,
    private val appDataStore: AppDataStore,
    private val spectatorRepository: SpectatorRepository,
    private val widgetUpdater: ShiftWidgetUpdater,
    private val userSession: UserSession,
) : ViewModel() {

    private val uid get() = userSession.requireUserId()

    private val today: LocalDate = LocalDate.now()
    private val upcomingEnd: LocalDate = today.plusDays(6)

    /** True when the user is in spectator-only mode (no own schedule). */
    val isSpectatorOnly: StateFlow<Boolean> = appDataStore.spectatorMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The display name of the currently selected host, or null. */
    val selectedHostName: StateFlow<String?> = combine(
        appDataStore.selectedHostUid,
        appDataStore.watchedHosts,
    ) { uid, hosts ->
        uid?.let { id -> hosts.firstOrNull { it.uid == id }?.displayName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Upcoming days. In spectator mode with a selected host, fetched from Firestore.
     * Otherwise fetched from local ShiftRepository.
     */
    val upcomingDays: StateFlow<List<UpcomingDay>> = combine(
        appDataStore.spectatorMode,
        appDataStore.selectedHostUid,
    ) { spectator, hostUid -> spectator to hostUid }
        .flatMapLatest { (isSpectator, hostUid) ->
            if (isSpectator && hostUid != null) {
                // Spectator mode: one-shot fetch from Firestore
                flow {
                    val infos = try {
                        spectatorRepository.getDayInfosForRange(hostUid, today, upcomingEnd)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    // Cache for widget (runs locally, no network)
                    if (infos.isNotEmpty()) {
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
                        widgetUpdater.updateAll()
                    }
                    emit(infos.map { UpcomingDay(dayInfo = it, isToday = it.date == today) })
                }
            } else {
                // Normal mode: reactive Flow from local Room
                shiftRepository.getDayInfosForRange(today, upcomingEnd)
                    .map { infos ->
                        infos.map { UpcomingDay(dayInfo = it, isToday = it.date == today) }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per-category leave balances for the current calendar year. */
    val leaveBalances: StateFlow<List<LeaveBalanceEntity>> = leaveRepository
        .observeAllBalancesForYear(today.year)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklyOvertimeHours: StateFlow<Float> = overtimeRepository
        .getOvertimeForRange(today, upcomingEnd)
        .map { list -> list.sumOf { it.hours.toDouble() }.toFloat() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    val yearlyOvertimeBalance: StateFlow<OvertimeBalanceEntity?> = overtimeRepository
        .observeBalanceForYear(today.year)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
