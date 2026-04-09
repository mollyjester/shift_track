package com.slikharev.shifttrack.engine

import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class IncomeInput(
    val dayInfos: List<DayInfo>,
    val overtimeHours: Map<LocalDate, Float>,
    val workedHoursOverrides: Map<LocalDate, Float>,
    val publicHolidayDates: Set<LocalDate>,
    val lastWeekendDay: DayOfWeek,
    val baseRate: Float,
    val nightMultiplier: Float,
    val weekendMultiplier: Float,
    val holidayMultiplier: Float,
    val shiftChangeoverHour: Int,
)

object IncomeCalculator {

    private const val DEFAULT_SHIFT_HOURS = 12f
    private const val DEFAULT_HALF_DAY_HOURS = 6f

    /**
     * Calculates total income for the month that contains the given [dayInfos].
     *
     * Night shifts are split across two calendar days at midnight:
     * - Pre-midnight hours belong to the shift's start date.
     * - Post-midnight hours belong to the next calendar date.
     *
     * Each portion gets its own day-condition multiplier. Both portions use the
     * night multiplier as shift multiplier.
     *
     * The effective multiplier for each hour-block is:
     *   max(dayConditionMultiplier, nightMultiplier if NIGHT)
     *
     * Day-condition priority: lastWeekendDay > holiday > 1.0
     *
     * Overtime uses the day-condition multiplier only (no night multiplier).
     */
    fun calculateMonthlyIncome(input: IncomeInput, targetMonth: YearMonth): Float {
        if (input.baseRate <= 0f) return 0f

        val dayInfoByDate = input.dayInfos.associateBy { it.date }
        var totalIncome = 0f

        // Process each day in the target month
        val startDate = targetMonth.atDay(1)
        val endDate = targetMonth.atEndOfMonth()
        var currentDate = startDate

        while (!currentDate.isAfter(endDate)) {
            val dayInfo = dayInfoByDate[currentDate]

            // Shift income
            if (dayInfo != null) {
                totalIncome += calculateShiftIncome(input, dayInfo, targetMonth)
            }

            // Overtime income (always on the calendar date, no splitting)
            val overtime = input.overtimeHours[currentDate] ?: 0f
            if (overtime > 0f) {
                val dayCondition = dayConditionMultiplier(currentDate, input)
                totalIncome += overtime * input.baseRate * dayCondition
            }

            currentDate = currentDate.plusDays(1)
        }

        // Also account for post-midnight hours from a night shift on the day BEFORE
        // the target month (spills into the first day of the month)
        val dayBeforeMonth = startDate.minusDays(1)
        val prevDayInfo = dayInfoByDate[dayBeforeMonth]
        if (prevDayInfo != null && isNightShift(prevDayInfo)) {
            val (_, postHours) = nightShiftHourSplit(input.shiftChangeoverHour)
            val totalHours = shiftHours(prevDayInfo, input)
            if (totalHours > 0f) {
                val postPortion = totalHours * postHours.toFloat() / DEFAULT_SHIFT_HOURS
                val nextDate = dayBeforeMonth.plusDays(1) // = first day of month
                if (YearMonth.from(nextDate) == targetMonth) {
                    val dayCondition = dayConditionMultiplier(nextDate, input)
                    val effectiveMultiplier = maxOf(dayCondition, input.nightMultiplier)
                    totalIncome += postPortion * input.baseRate * effectiveMultiplier
                }
            }
        }

        return totalIncome
    }

    private fun calculateShiftIncome(input: IncomeInput, dayInfo: DayInfo, targetMonth: YearMonth): Float {
        val totalHours = shiftHours(dayInfo, input)
        if (totalHours <= 0f) return 0f

        return if (isNightShift(dayInfo)) {
            calculateNightShiftIncome(input, dayInfo, totalHours, targetMonth)
        } else {
            // Day shifts, leave, etc. — all hours belong to the shift date
            val dayCondition = dayConditionMultiplier(dayInfo.date, input)
            totalHours * input.baseRate * dayCondition
        }
    }

    private fun calculateNightShiftIncome(
        input: IncomeInput,
        dayInfo: DayInfo,
        totalHours: Float,
        targetMonth: YearMonth,
    ): Float {
        val (preHours, postHours) = nightShiftHourSplit(input.shiftChangeoverHour)
        val prePortion = totalHours * preHours.toFloat() / DEFAULT_SHIFT_HOURS
        val postPortion = totalHours * postHours.toFloat() / DEFAULT_SHIFT_HOURS

        var income = 0f

        // Pre-midnight portion belongs to the shift date
        val shiftDate = dayInfo.date
        if (YearMonth.from(shiftDate) == targetMonth) {
            val dayCondition = dayConditionMultiplier(shiftDate, input)
            val effectiveMultiplier = maxOf(dayCondition, input.nightMultiplier)
            income += prePortion * input.baseRate * effectiveMultiplier
        }

        // Post-midnight portion belongs to the next date
        val nextDate = shiftDate.plusDays(1)
        if (YearMonth.from(nextDate) == targetMonth) {
            val dayCondition = dayConditionMultiplier(nextDate, input)
            val effectiveMultiplier = maxOf(dayCondition, input.nightMultiplier)
            income += postPortion * input.baseRate * effectiveMultiplier
        }

        return income
    }

    private fun isNightShift(dayInfo: DayInfo): Boolean =
        dayInfo.shiftType == ShiftType.NIGHT && !dayInfo.hasLeave

    private fun shiftHours(dayInfo: DayInfo, input: IncomeInput): Float {
        // Check for worked hours override
        input.workedHoursOverrides[dayInfo.date]?.let { return it }

        // Default hours based on shift/leave state
        return when {
            dayInfo.hasLeave && !dayInfo.halfDay -> {
                // Full-day leave: paid for all except UNPAID
                if (dayInfo.leaveType == LeaveType.UNPAID) 0f else DEFAULT_SHIFT_HOURS
            }
            dayInfo.halfDay -> DEFAULT_HALF_DAY_HOURS
            dayInfo.shiftType == ShiftType.DAY || dayInfo.shiftType == ShiftType.NIGHT -> DEFAULT_SHIFT_HOURS
            else -> 0f // REST, OFF, LEAVE
        }
    }

    internal fun dayConditionMultiplier(date: LocalDate, input: IncomeInput): Float = when {
        date.dayOfWeek == input.lastWeekendDay -> input.weekendMultiplier
        date in input.publicHolidayDates -> input.holidayMultiplier
        else -> 1f
    }

    /**
     * Returns (preHours, postHours) for a night shift based on changeover hour.
     *
     * Night shift starts at [changeoverHour + 12] (mod 24) and ends at [changeoverHour].
     * Pre-midnight: from start to midnight.
     * Post-midnight: from midnight to end.
     *
     * Example: changeover=7 → night 19:00–07:00 → pre=5h, post=7h.
     */
    fun nightShiftHourSplit(changeoverHour: Int): Pair<Int, Int> {
        val nightStart = (changeoverHour + 12) % 24
        val preHours = 24 - nightStart
        val postHours = changeoverHour
        return preHours to postHours
    }
}
