package com.slikharev.shifttrack.widget

import com.slikharev.shifttrack.model.ShiftType
import java.time.LocalDate

/**
 * Immutable snapshot of the data the home screen widget needs to render.
 *
 * @property isConfigured false when the user has not yet set an anchor date.
 * @property days         Up to [WidgetShiftCalculator.DEFAULT_DAY_COUNT] days
 *                        starting from today. Empty when [isConfigured] is false.
 */
data class WidgetUiState(
    val isConfigured: Boolean,
    val days: List<WidgetDayInfo>,
)

data class WidgetDayInfo(
    val date: LocalDate,
    val shiftType: ShiftType,
    /** Human-friendly label: "Today", "Tomorrow", or a short weekday name. */
    val label: String,
)
