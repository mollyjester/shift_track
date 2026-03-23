package com.slikharev.shifttrack.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.repository.LeaveRepository
import com.slikharev.shifttrack.data.repository.OvertimeRepository
import com.slikharev.shifttrack.data.repository.ShiftRepository
import com.slikharev.shifttrack.model.DayInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class UpcomingDay(
    val dayInfo: DayInfo,
    val isToday: Boolean,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    shiftRepository: ShiftRepository,
    leaveRepository: LeaveRepository,
    overtimeRepository: OvertimeRepository,
    appDataStore: AppDataStore,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val upcomingEnd: LocalDate = today.plusDays(6)

    /** True when the user is in spectator-only mode (no own schedule). */
    val isSpectatorOnly: StateFlow<Boolean> = appDataStore.spectatorMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val upcomingDays: StateFlow<List<UpcomingDay>> = shiftRepository
        .getDayInfosForRange(today, upcomingEnd)
        .map { infos ->
            infos.map { UpcomingDay(dayInfo = it, isToday = it.date == today) }
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
