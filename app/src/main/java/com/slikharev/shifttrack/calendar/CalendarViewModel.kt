package com.slikharev.shifttrack.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
) : ViewModel() {

    private val _currentYearMonth = MutableStateFlow(YearMonth.now())
    val currentYearMonth: StateFlow<YearMonth> = _currentYearMonth.asStateFlow()

    /**
     * Flat list of [CalendarDay] cells (always a multiple of 7) for the current month.
     * Leading/trailing [CalendarDay.Empty] cells pad the grid to a full week-start alignment.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val calendarDays: StateFlow<List<CalendarDay>> = _currentYearMonth
        .flatMapLatest { ym ->
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()
            shiftRepository.getDayInfosForRange(start, end).map { dayInfos ->
                buildCalendarDays(ym, dayInfos)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = buildCalendarDays(_currentYearMonth.value, emptyList()),
        )

    fun prevMonth() = _currentYearMonth.update { it.minusMonths(1) }
    fun nextMonth() = _currentYearMonth.update { it.plusMonths(1) }
}
