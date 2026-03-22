package com.slikharev.shifttrack.model

import java.time.LocalDate

/**
 * View model for a single calendar day, combining the cadence-computed shift type
 * with any manual override, leave record, and overtime flag.
 *
 * Produced by [com.slikharev.shifttrack.data.repository.ShiftRepository] and consumed
 * by the calendar, dashboard, and widget.
 */
data class DayInfo(
    val date: LocalDate,
    val shiftType: ShiftType,
    /** True when the user has explicitly overridden the cadence-computed type. */
    val isManualOverride: Boolean = false,
    val hasLeave: Boolean = false,
    val hasOvertime: Boolean = false,
    val note: String? = null,
)
