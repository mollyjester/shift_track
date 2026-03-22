package com.slikharev.shifttrack.calendar

import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure-function tests for [buildCalendarDays].
 *
 * No Android or Hilt dependencies needed — just plain JVM.
 */
class CalendarDayTest {

    // ─── Helper ─────────────────────────────────────────────────────────────────

    /** Wraps a YearMonth + optional DayInfo list into a call to [buildCalendarDays]. */
    private fun days(
        ym: YearMonth,
        dayInfos: List<DayInfo> = emptyList(),
        today: LocalDate = LocalDate.of(2000, 1, 1), // far in the past → no isToday flags
    ) = buildCalendarDays(ym, dayInfos, today)

    /** Returns a trivial DayInfo for unit-test purposes. */
    private fun info(date: LocalDate, type: ShiftType = ShiftType.DAY) =
        DayInfo(date = date, shiftType = type)

    // ─── Grid size ───────────────────────────────────────────────────────────────

    @Test
    fun `result size is always a multiple of 7`() {
        // Test a full year worth of months
        val year = 2024
        for (month in 1..12) {
            val ym = YearMonth.of(year, month)
            val result = days(ym)
            assertTrue(
                "Month $month: size ${result.size} is not a multiple of 7",
                result.size % 7 == 0,
            )
        }
    }

    @Test
    fun `grid contains exactly one ShiftDay per day in month`() {
        val ym = YearMonth.of(2024, 3) // March 2024 — 31 days
        val result = days(ym)
        val shiftDays = result.filterIsInstance<CalendarDay.ShiftDay>()
        assertEquals(31, shiftDays.size)
    }

    @Test
    fun `grid cover all day numbers 1 through lengthOfMonth`() {
        val ym = YearMonth.of(2024, 2) // Feb 2024 — 29 days (leap year)
        val result = days(ym)
        val dayNumbers = result
            .filterIsInstance<CalendarDay.ShiftDay>()
            .map { it.date.dayOfMonth }
            .sorted()
        assertEquals((1..29).toList(), dayNumbers)
    }

    // ─── Leading empties (week starts Monday) ────────────────────────────────────

    @Test
    fun `no leading empties when month starts on Monday`() {
        // 2024-01-01 is a Monday
        val ym = YearMonth.of(2024, 1)
        val result = days(ym)
        assertFalse("First cell should be a ShiftDay", result[0] is CalendarDay.Empty)
    }

    @Test
    fun `one leading empty when month starts on Tuesday`() {
        // 2019-01-01 is a Tuesday
        val ym = YearMonth.of(2019, 1)
        val result = days(ym)
        assertTrue(result[0] is CalendarDay.Empty)
        assertFalse(result[1] is CalendarDay.Empty)
    }

    @Test
    fun `six leading empties when month starts on Sunday`() {
        // 2023-01-01 is a Sunday
        val ym = YearMonth.of(2023, 1)
        val result = days(ym)
        val leadingEmpties = result.takeWhile { it is CalendarDay.Empty }.size
        assertEquals(6, leadingEmpties)
    }

    @Test
    fun `five leading empties when month starts on Saturday`() {
        // 2022-01-01 is a Saturday
        val ym = YearMonth.of(2022, 1)
        val result = days(ym)
        val leadingEmpties = result.takeWhile { it is CalendarDay.Empty }.size
        assertEquals(5, leadingEmpties)
    }

    // ─── Trailing empties ────────────────────────────────────────────────────────

    @Test
    fun `trailing empties complete the last row`() {
        // 2024-03 has 31 days; Mon 4 Mar is the first Monday of the month (2024-03-01 is Friday)
        // Leading = 4, days = 31, total before trailing = 35, multiple of 7 --> no trailing needed
        // Let's use a month that needs trailing empties
        // 2024-11-01 is Friday → leading = 4, days = 30 → 34 → next mult of 7 = 35 → 1 trailing
        val ym = YearMonth.of(2024, 11)
        val result = days(ym)
        assertEquals(0, result.size % 7)
        assertTrue(result.last() is CalendarDay.Empty)
    }

    @Test
    fun `no trailing empties when grid is already full rows`() {
        // 2021-03-01 is Monday → leading = 0, days = 31 → 31 → +4 trailing to reach 35
        // Actually: 31 % 7 == 3, so trailing = 4
        // Let's find a month where leading + days is already mult of 7
        // 2021-02: Feb 1 is Monday (leading=0), 28 days → 28 % 7 == 0 → no trailing
        val ym = YearMonth.of(2021, 2)
        val result = days(ym)
        assertEquals(28, result.size)
        assertFalse("Last cell should not be empty", result.last() is CalendarDay.Empty)
    }

    // ─── DayInfo merging ─────────────────────────────────────────────────────────

    @Test
    fun `ShiftDay uses provided dayInfo shiftType`() {
        val ym = YearMonth.of(2024, 3)
        val date = LocalDate.of(2024, 3, 15)
        val provided = info(date, ShiftType.NIGHT)
        val result = days(ym, listOf(provided))
        val cell = result.filterIsInstance<CalendarDay.ShiftDay>()
            .first { it.date == date }
        assertEquals(ShiftType.NIGHT, cell.shiftType)
    }

    @Test
    fun `ShiftDay falls back to OFF when no dayInfo provided`() {
        val ym = YearMonth.of(2024, 3)
        val result = days(ym) // no dayInfos
        val cell = result.filterIsInstance<CalendarDay.ShiftDay>().first()
        assertEquals(ShiftType.OFF, cell.shiftType)
    }

    @Test
    fun `multiple dayInfos are placed on correct dates`() {
        val ym = YearMonth.of(2024, 4)
        val infos = listOf(
            info(LocalDate.of(2024, 4, 1), ShiftType.DAY),
            info(LocalDate.of(2024, 4, 15), ShiftType.REST),
            info(LocalDate.of(2024, 4, 30), ShiftType.LEAVE),
        )
        val result = days(ym, infos)
        val shiftDays = result.filterIsInstance<CalendarDay.ShiftDay>().associateBy { it.date.dayOfMonth }
        assertEquals(ShiftType.DAY, shiftDays[1]!!.shiftType)
        assertEquals(ShiftType.REST, shiftDays[15]!!.shiftType)
        assertEquals(ShiftType.LEAVE, shiftDays[30]!!.shiftType)
        // Other days fall back to OFF
        assertEquals(ShiftType.OFF, shiftDays[2]!!.shiftType)
    }

    // ─── isToday flag ────────────────────────────────────────────────────────────

    @Test
    fun `isToday is set on matching date only`() {
        val ym = YearMonth.of(2024, 6)
        val today = LocalDate.of(2024, 6, 15)
        val result = buildCalendarDays(ym, emptyList(), today)
        val shiftDays = result.filterIsInstance<CalendarDay.ShiftDay>()
        val todayCells = shiftDays.filter { it.isToday }
        assertEquals(1, todayCells.size)
        assertEquals(15, todayCells.first().date.dayOfMonth)
    }

    @Test
    fun `isToday is false for all cells when today is in a different month`() {
        val ym = YearMonth.of(2024, 6)
        val today = LocalDate.of(2024, 7, 1) // different month
        val result = buildCalendarDays(ym, emptyList(), today)
        assertTrue(result.filterIsInstance<CalendarDay.ShiftDay>().none { it.isToday })
    }

    // ─── ShiftDay convenience properties ─────────────────────────────────────────

    @Test
    fun `ShiftDay date matches DayInfo date`() {
        val ym = YearMonth.of(2024, 5)
        val date = LocalDate.of(2024, 5, 20)
        val result = days(ym, listOf(info(date, ShiftType.DAY)))
        val cell = result.filterIsInstance<CalendarDay.ShiftDay>()
            .first { it.date == date }
        assertEquals(date, cell.dayInfo.date)
    }
}
