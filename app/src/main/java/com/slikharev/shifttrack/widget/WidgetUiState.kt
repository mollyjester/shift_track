package com.slikharev.shifttrack.widget

import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import java.time.LocalDate

data class WidgetUiState(
    val isConfigured: Boolean,
    val days: List<WidgetDayInfo>,
)

data class WidgetDayInfo(
    val date: LocalDate,
    val shiftType: ShiftType,
    /** Human-friendly label: "Today", "Tomorrow", or a short weekday name. */
    val label: String,
    val isToday: Boolean = false,
    val hasLeave: Boolean = false,
    val halfDay: Boolean = false,
    val leaveType: LeaveType? = null,
)
