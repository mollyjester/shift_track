package com.slikharev.shifttrack.widget

import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class WidgetShiftCalculatorTest {

    // A known anchor: 2025-03-15 at cycle index 0 (DAY)
    private val anchorDate = "2025-03-15"
    private val anchorIndex = 0 // DAY — cycle: DAY(0), DAY(1), NIGHT(2), REST(3), OFF(4)

    // ── not configured ───────────────────────────────────────────────────────

    @Test
    fun `compute returns not-configured state when anchorDateStr is null`() {
        val state = WidgetShiftCalculator.compute(null, 1)

        assertFalse(state.isConfigured)
        assertTrue(state.days.isEmpty())
    }

    @Test
    fun `compute returns not-configured state when anchorCycleIndex is negative`() {
        val state = WidgetShiftCalculator.compute("2025-03-15", -1)

        assertFalse(state.isConfigured)
        assertTrue(state.days.isEmpty())
    }

    // ── configured ───────────────────────────────────────────────────────────

    @Test
    fun `compute returns isConfigured true when anchor is set`() {
        val state = WidgetShiftCalculator.compute(anchorDate, anchorIndex)

        assertTrue(state.isConfigured)
    }

    @Test
    fun `compute first entry has label Today and correct shift type`() {
        val today = LocalDate.parse(anchorDate) // anchor day is index 0 → DAY
        val state = WidgetShiftCalculator.compute(anchorDate, anchorIndex, today)

        assertEquals("Today", state.days[0].label)
        assertEquals(ShiftType.DAY, state.days[0].shiftType)
        assertEquals(today, state.days[0].date)
    }

    @Test
    fun `compute second entry has label Tomorrow`() {
        val today = LocalDate.parse(anchorDate)
        val state = WidgetShiftCalculator.compute(anchorDate, anchorIndex, today)

        assertEquals("Tomorrow", state.days[1].label)
    }

    @Test
    fun `compute entries beyond tomorrow use short day-of-week names`() {
        val today = LocalDate.parse(anchorDate)
        val state = WidgetShiftCalculator.compute(anchorDate, anchorIndex, today)

        // indices 2..4 should be short weekday names, not "Today" or "Tomorrow"
        for (i in 2 until state.days.size) {
            val expected = state.days[i].date.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
            assertEquals(expected, state.days[i].label)
        }
    }

    @Test
    fun `compute produces correct dayCount entries and respects cadence cycle`() {
        // Anchor 2025-03-15 at index 0 (DAY)
        // Cycle: DAY(0), DAY(1), NIGHT(2), REST(3), OFF(4)
        val today = LocalDate.parse(anchorDate)
        val state = WidgetShiftCalculator.compute(anchorDate, anchorIndex, today, dayCount = 5)

        assertEquals(5, state.days.size)

        val expectedTypes = listOf(
            ShiftType.DAY,   // offset 0 → index 0
            ShiftType.DAY,   // offset 1 → index 1
            ShiftType.NIGHT, // offset 2 → index 2
            ShiftType.REST,  // offset 3 → index 3
            ShiftType.OFF,   // offset 4 → index 4
        )
        state.days.forEachIndexed { i, day ->
            assertEquals("shift at offset $i", expectedTypes[i], day.shiftType)
        }
    }

    @Test
    fun `compute wraps cycle correctly after CYCLE_LENGTH days`() {
        // Starting on cycle day 4 (OFF), next day must wrap back to DAY(0)
        val anchorAtOff = "2025-03-15"  // index 4 → OFF
        val today = LocalDate.parse(anchorAtOff)
        val state = WidgetShiftCalculator.compute(anchorAtOff, anchorCycleIndex = 4, today = today, dayCount = 2)

        assertEquals(ShiftType.OFF, state.days[0].shiftType)
        assertEquals(
            CadenceEngine.CYCLE[0], // wraps to DAY
            state.days[1].shiftType,
        )
    }

    // ── isToday flag ─────────────────────────────────────────────────────────

    @Test
    fun `compute first day has isToday true and subsequent days have isToday false`() {
        val today = LocalDate.parse(anchorDate)
        val state = WidgetShiftCalculator.compute(anchorDate, anchorIndex, today, dayCount = 5)

        assertTrue("first day should be today", state.days[0].isToday)
        for (i in 1 until state.days.size) {
            assertFalse("day at offset $i should not be today", state.days[i].isToday)
        }
    }

    // ── max day count ────────────────────────────────────────────────────────

    @Test
    fun `compute returns 7 days when dayCount is 7`() {
        val today = LocalDate.parse(anchorDate)
        val state = WidgetShiftCalculator.compute(anchorDate, anchorIndex, today, dayCount = 7)

        assertEquals(7, state.days.size)
        // Verify the last two days (index 5, 6) wrap the cycle correctly
        // offset 5 → (0+5) % 5 = 0 → DAY
        // offset 6 → (0+6) % 5 = 1 → DAY
        assertEquals(ShiftType.DAY, state.days[5].shiftType)
        assertEquals(ShiftType.DAY, state.days[6].shiftType)
    }
}
