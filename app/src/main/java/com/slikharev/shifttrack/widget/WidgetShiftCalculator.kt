package com.slikharev.shifttrack.widget

import com.slikharev.shifttrack.engine.CadenceEngine
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure, side-effect-free helper that maps DataStore anchor values to a
 * [WidgetUiState] for the home screen widget.
 *
 * Kept as a plain object (no DI) so it can be unit-tested without any
 * Android or Hilt dependencies.
 */
object WidgetShiftCalculator {

    const val DEFAULT_DAY_COUNT = 5

    /**
     * Computes shift information for [dayCount] consecutive days starting from
     * [today].
     *
     * @param anchorDateStr     ISO date string (e.g. "2025-03-15") from DataStore.
     *                          Null means the user has not set up their schedule.
     * @param anchorCycleIndex  0..4 cadence position of the anchor date.
     *                          -1 means not configured.
     * @param today             The reference date (defaults to [LocalDate.now]).
     * @param dayCount          Number of upcoming days to include.
     */
    fun compute(
        anchorDateStr: String?,
        anchorCycleIndex: Int,
        today: LocalDate = LocalDate.now(),
        dayCount: Int = DEFAULT_DAY_COUNT,
    ): WidgetUiState {
        if (anchorDateStr == null || anchorCycleIndex < 0) {
            return WidgetUiState(isConfigured = false, days = emptyList())
        }
        val anchorDate = LocalDate.parse(anchorDateStr)
        val days = (0 until dayCount).map { offset ->
            val date = today.plusDays(offset.toLong())
            val shiftType = CadenceEngine.shiftTypeForDate(date, anchorDate, anchorCycleIndex)
            val label = when (offset) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
            WidgetDayInfo(date = date, shiftType = shiftType, label = label)
        }
        return WidgetUiState(isConfigured = true, days = days)
    }
}
