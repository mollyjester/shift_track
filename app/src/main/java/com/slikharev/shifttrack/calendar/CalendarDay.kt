package com.slikharev.shifttrack.calendar

import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.ShiftType
import java.time.LocalDate
import java.time.YearMonth

/** A single cell in the calendar grid. */
sealed interface CalendarDay {
    /** Padding cell before the first or after the last day of the month. */
    data object Empty : CalendarDay

    /** An actual day within the displayed month. */
    data class ShiftDay(
        val dayInfo: DayInfo,
        val isToday: Boolean,
    ) : CalendarDay {
        val date: LocalDate get() = dayInfo.date
        val shiftType: ShiftType get() = dayInfo.shiftType
    }
}

/**
 * Builds a flat grid list for [ym] backed by [dayInfos].
 *
 * - Week starts on Monday (dayOfWeek.value Mon=1 … Sun=7).
 * - Leading [CalendarDay.Empty] cells are prepended so the first day lands on the correct column.
 * - Trailing empties are appended so the length is always a multiple of 7.
 * - If [dayInfos] has no entry for a day (anchor not yet set), a fallback [DayInfo] is used
 *   with `shiftType = ShiftType.OFF`.
 *
 * This is a pure function and lives outside the ViewModel so it can be tested directly.
 */
fun buildCalendarDays(
    ym: YearMonth,
    dayInfos: List<DayInfo>,
    today: LocalDate = LocalDate.now(),
): List<CalendarDay> {
    val dayInfoByDate = dayInfos.associateBy { it.date }
    val firstDay = ym.atDay(1)
    val leadingEmpties = firstDay.dayOfWeek.value - 1 // Mon=0, Tue=1 … Sun=6

    val result = ArrayList<CalendarDay>(leadingEmpties + ym.lengthOfMonth() + 6)
    repeat(leadingEmpties) { result.add(CalendarDay.Empty) }

    for (dayNum in 1..ym.lengthOfMonth()) {
        val date = ym.atDay(dayNum)
        val info = dayInfoByDate[date]
            ?: DayInfo(date = date, shiftType = ShiftType.OFF) // fallback before anchor is set
        result.add(CalendarDay.ShiftDay(dayInfo = info, isToday = date == today))
    }

    val remainder = result.size % 7
    if (remainder != 0) repeat(7 - remainder) { result.add(CalendarDay.Empty) }

    return result
}
