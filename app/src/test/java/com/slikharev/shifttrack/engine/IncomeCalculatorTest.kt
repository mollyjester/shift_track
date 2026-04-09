package com.slikharev.shifttrack.engine

import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class IncomeCalculatorTest {

    private val april2025 = YearMonth.of(2025, 4)
    private val defaultInput = IncomeInput(
        dayInfos = emptyList(),
        overtimeHours = emptyMap(),
        workedHoursOverrides = emptyMap(),
        publicHolidayDates = emptySet(),
        lastWeekendDay = DayOfWeek.SUNDAY,
        baseRate = 10f,
        nightMultiplier = 1.5f,
        weekendMultiplier = 2f,
        holidayMultiplier = 2.5f,
        shiftChangeoverHour = 7,
    )

    private fun dayInfo(
        date: LocalDate,
        type: ShiftType,
        hasLeave: Boolean = false,
        halfDay: Boolean = false,
        leaveType: LeaveType? = null,
    ) = DayInfo(
        date = date,
        shiftType = type,
        hasLeave = hasLeave,
        halfDay = halfDay,
        leaveType = leaveType,
    )

    // ── Basic cases ──────────────────────────────────────────────────────────────

    @Test
    fun `base rate with day shifts only`() {
        // Wednesday April 2, 2025 - regular day shift, 12h × $10 = $120
        val date = LocalDate.of(2025, 4, 2) // Wednesday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.DAY)),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(120f, result, 0.01f)
    }

    @Test
    fun `night shift applies night multiplier`() {
        // Tuesday April 1, 2025 - night shift
        // changeover=7 → night 19:00→07:00 → pre=5h, post=7h
        // Pre-midnight (April 1): 5h × $10 × 1.5 = $75
        // Post-midnight (April 2): 7h × $10 × 1.5 = $105
        // Total: $180
        val date = LocalDate.of(2025, 4, 1) // Tuesday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.NIGHT)),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(180f, result, 0.01f)
    }

    @Test
    fun `last weekend day applies weekend multiplier`() {
        // Sunday April 6, 2025 - day shift on last weekend day
        // 12h × $10 × 2.0 = $240
        val date = LocalDate.of(2025, 4, 6) // Sunday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.DAY)),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(240f, result, 0.01f)
    }

    @Test
    fun `public holiday applies holiday multiplier`() {
        // Wednesday April 2, 2025 - day shift on holiday
        // 12h × $10 × 2.5 = $300
        val date = LocalDate.of(2025, 4, 2) // Wednesday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.DAY)),
            publicHolidayDates = setOf(date),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(300f, result, 0.01f)
    }

    @Test
    fun `last weekend day beats holiday when both apply`() {
        // Sunday April 6 is both a holiday and last weekend day
        // Weekend multiplier (2.0) wins over holiday (2.5) by priority
        // 12h × $10 × 2.0 = $240
        val date = LocalDate.of(2025, 4, 6) // Sunday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.DAY)),
            publicHolidayDates = setOf(date),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(240f, result, 0.01f)
    }

    @Test
    fun `night shift on last weekend day uses max of night and weekend multiplier`() {
        // Saturday April 5, 2025 - night shift
        // Pre-midnight (Sat April 5): 5h × $10 × max(1.0, 1.5) = 5×10×1.5 = $75
        //   Saturday is NOT the last weekend day (Sunday is)
        // Post-midnight (Sun April 6): 7h × $10 × max(2.0, 1.5) = 7×10×2.0 = $140
        //   Sunday IS the last weekend day, weekendMult=2.0 > nightMult=1.5
        // Total: $75 + $140 = $215
        val date = LocalDate.of(2025, 4, 5) // Saturday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.NIGHT)),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(215f, result, 0.01f)
    }

    // ── Leave cases ──────────────────────────────────────────────────────────────

    @Test
    fun `full-day paid leave counts as 12h`() {
        val date = LocalDate.of(2025, 4, 2) // Wednesday
        val input = defaultInput.copy(
            dayInfos = listOf(
                dayInfo(date, ShiftType.LEAVE, hasLeave = true, leaveType = LeaveType.ANNUAL),
            ),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        // 12h × $10 × 1.0 = $120
        assertEquals(120f, result, 0.01f)
    }

    @Test
    fun `full-day UNPAID leave counts as 0h`() {
        val date = LocalDate.of(2025, 4, 2)
        val input = defaultInput.copy(
            dayInfos = listOf(
                dayInfo(date, ShiftType.LEAVE, hasLeave = true, leaveType = LeaveType.UNPAID),
            ),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(0f, result, 0.01f)
    }

    @Test
    fun `half-day leave counts as 6h`() {
        val date = LocalDate.of(2025, 4, 2) // Wednesday
        val input = defaultInput.copy(
            dayInfos = listOf(
                dayInfo(date, ShiftType.DAY, hasLeave = true, halfDay = true, leaveType = LeaveType.ANNUAL),
            ),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        // 6h × $10 × 1.0 = $60
        assertEquals(60f, result, 0.01f)
    }

    // ── Override and special cases ───────────────────────────────────────────────

    @Test
    fun `worked hours override replaces default hours`() {
        val date = LocalDate.of(2025, 4, 2) // Wednesday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.DAY)),
            workedHoursOverrides = mapOf(date to 8f),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        // 8h × $10 × 1.0 = $80
        assertEquals(80f, result, 0.01f)
    }

    @Test
    fun `REST and OFF days produce 0 shift income`() {
        val rest = LocalDate.of(2025, 4, 2)
        val off = LocalDate.of(2025, 4, 3)
        val input = defaultInput.copy(
            dayInfos = listOf(
                dayInfo(rest, ShiftType.REST),
                dayInfo(off, ShiftType.OFF),
            ),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(0f, result, 0.01f)
    }

    @Test
    fun `overtime on REST uses day-condition multiplier`() {
        // Sunday April 6 - REST day with 4h overtime
        // Overtime = 4h × $10 × 2.0 (weekend) = $80
        val date = LocalDate.of(2025, 4, 6) // Sunday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.REST)),
            overtimeHours = mapOf(date to 4f),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(80f, result, 0.01f)
    }

    @Test
    fun `overtime uses day-condition multiplier not night multiplier`() {
        // Tuesday April 1 - night shift with 2h overtime
        // Shift: 12h × $10 × 1.5 = $180
        // Overtime: 2h × $10 × 1.0 (regular day condition) = $20
        // Total: $200
        val date = LocalDate.of(2025, 4, 1) // Tuesday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.NIGHT)),
            overtimeHours = mapOf(date to 2f),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(200f, result, 0.01f)
    }

    @Test
    fun `zero base rate returns zero income`() {
        val date = LocalDate.of(2025, 4, 2)
        val input = defaultInput.copy(
            baseRate = 0f,
            dayInfos = listOf(dayInfo(date, ShiftType.DAY)),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(0f, result, 0.01f)
    }

    @Test
    fun `empty month returns zero`() {
        val result = IncomeCalculator.calculateMonthlyIncome(defaultInput, april2025)
        assertEquals(0f, result, 0.01f)
    }

    // ── Night shift splitting ────────────────────────────────────────────────────

    @Test
    fun `night shift hour split with changeover 7`() {
        // Night 19:00→07:00: pre-midnight = 5h, post-midnight = 7h
        val (pre, post) = IncomeCalculator.nightShiftHourSplit(7)
        assertEquals(5, pre)
        assertEquals(7, post)
    }

    @Test
    fun `changeover hour 6 gives 6h pre-midnight and 6h post-midnight`() {
        // Night 18:00→06:00: pre-midnight = 6h, post-midnight = 6h
        val (pre, post) = IncomeCalculator.nightShiftHourSplit(6)
        assertEquals(6, pre)
        assertEquals(6, post)
    }

    @Test
    fun `night shift saturday to sunday - only sunday hours get weekend multiplier`() {
        // Saturday April 5, night shift
        // Pre-midnight (Sat): 5h × $10 × max(1.0, 1.5) = $75
        //   Saturday is NOT last weekend day
        // Post-midnight (Sun): 7h × $10 × max(2.0, 1.5) = $140
        //   Sunday IS last weekend day → weekendMult=2.0
        // Total: $215
        val date = LocalDate.of(2025, 4, 5) // Saturday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.NIGHT)),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(215f, result, 0.01f)
    }

    @Test
    fun `night shift with worked hours override splits proportionally`() {
        // Tuesday April 1, night shift, override to 10h
        // Normal split: 5/12 pre, 7/12 post
        // Override 10h: pre = 10 × 5/12 ≈ 4.167h, post = 10 × 7/12 ≈ 5.833h
        // Both at night multiplier 1.5 (regular weekday):
        // Income: (4.167 + 5.833) × $10 × 1.5 = 10 × 15 = $150
        val date = LocalDate.of(2025, 4, 1) // Tuesday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.NIGHT)),
            workedHoursOverrides = mapOf(date to 10f),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(150f, result, 0.01f)
    }

    @Test
    fun `night shift at month boundary - post-midnight hours count in next month`() {
        // March 31, 2025 (Monday) - night shift
        // Pre-midnight (March 31): 5h in March → NOT in April → excluded from April total
        // Post-midnight (April 1): 7h in April → 7h × $10 × 1.5 = $105
        val march = YearMonth.of(2025, 3)
        val date = LocalDate.of(2025, 3, 31) // Monday

        // For the April calculation, the day-before-month (March 31) dayInfo must be included
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.NIGHT)),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(105f, result, 0.01f)
    }

    @Test
    fun `night shift at end of month - pre-midnight in month, post-midnight excluded`() {
        // April 30, 2025 (Wednesday) - night shift
        // Pre-midnight (April 30): 5h × $10 × 1.5 = $75 → in April
        // Post-midnight (May 1): excluded from April
        val date = LocalDate.of(2025, 4, 30) // Wednesday
        val input = defaultInput.copy(
            dayInfos = listOf(dayInfo(date, ShiftType.NIGHT)),
        )
        val result = IncomeCalculator.calculateMonthlyIncome(input, april2025)
        assertEquals(75f, result, 0.01f)
    }

    // ── dayConditionMultiplier ───────────────────────────────────────────────────

    @Test
    fun `dayConditionMultiplier returns weekend mult for last weekend day`() {
        val sunday = LocalDate.of(2025, 4, 6) // Sunday
        val mult = IncomeCalculator.dayConditionMultiplier(sunday, defaultInput)
        assertEquals(2f, mult, 0.001f)
    }

    @Test
    fun `dayConditionMultiplier returns holiday mult for holiday non-weekend`() {
        val wednesday = LocalDate.of(2025, 4, 2)
        val input = defaultInput.copy(publicHolidayDates = setOf(wednesday))
        val mult = IncomeCalculator.dayConditionMultiplier(wednesday, input)
        assertEquals(2.5f, mult, 0.001f)
    }

    @Test
    fun `dayConditionMultiplier returns 1 for regular day`() {
        val tuesday = LocalDate.of(2025, 4, 1)
        val mult = IncomeCalculator.dayConditionMultiplier(tuesday, defaultInput)
        assertEquals(1f, mult, 0.001f)
    }
}
