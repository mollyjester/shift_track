package com.slikharev.shifttrack.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.repository.LeaveRepository
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
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val upcomingEnd: LocalDate = today.plusDays(6)

    /** Today's shift and the following 6 days (7 total). */
    val upcomingDays: StateFlow<List<UpcomingDay>> = shiftRepository
        .getDayInfosForRange(today, upcomingEnd)
        .map { infos ->
            infos.map { UpcomingDay(dayInfo = it, isToday = it.date == today) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Leave balance for the current calendar year. */
    val leaveBalance: StateFlow<LeaveBalanceEntity?> = leaveRepository
        .observeBalanceForYear(today.year)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Remaining leave days = totalDays - usedDays; null while loading. */
    val remainingLeaveDays: StateFlow<Float?> = leaveRepository
        .observeBalanceForYear(today.year)
        .map { balance ->
            if (balance == null) null
            else (balance.totalDays - balance.usedDays).coerceAtLeast(0f)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
