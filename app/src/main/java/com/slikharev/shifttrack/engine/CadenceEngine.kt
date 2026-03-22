package com.slikharev.shifttrack.engine

import com.slikharev.shifttrack.model.ShiftType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure cadence calculation engine — no Android/DI dependencies.
 *
 * The shift cycle has exactly 5 positions:
 *
 *   index 0 → DAY   (first day shift in the pair)
 *   index 1 → DAY   (second day shift in the pair)
 *   index 2 → NIGHT
 *   index 3 → REST
 *   index 4 → OFF
 *
 * Usage: call [shiftTypeForDate] with any date, the anchor date, and the cycle
 * index (0-4) that describes which position the anchor falls on.
 *
 * All calculations are pure and side-effect-free, making this trivially testable.
 */
object CadenceEngine {

    /** The canonical 5-day rotation. */
    val CYCLE: List<ShiftType> = listOf(
        ShiftType.DAY,   // 0
        ShiftType.DAY,   // 1
        ShiftType.NIGHT, // 2
        ShiftType.REST,  // 3
        ShiftType.OFF,   // 4
    )

    val CYCLE_LENGTH = CYCLE.size // 5

    /**
     * Returns the [ShiftType] for [date] given an [anchorDate] whose cycle index is
     * [anchorCycleIndex].
     *
     * Works for dates before, on, and after the anchor.
     *
     * @param anchorCycleIndex  0-4; must be a valid index in [CYCLE].
     * @throws IllegalArgumentException if [anchorCycleIndex] is out of range.
     */
    fun shiftTypeForDate(
        date: LocalDate,
        anchorDate: LocalDate,
        anchorCycleIndex: Int,
    ): ShiftType {
        require(anchorCycleIndex in 0 until CYCLE_LENGTH) {
            "anchorCycleIndex must be 0..${CYCLE_LENGTH - 1}, was $anchorCycleIndex"
        }
        val daysDiff = ChronoUnit.DAYS.between(anchorDate, date)
        val index = Math.floorMod(anchorCycleIndex + daysDiff, CYCLE_LENGTH.toLong()).toInt()
        return CYCLE[index]
    }

    /**
     * Convenience: returns the cycle index for the day after [date]'s index.
     * Useful for the onboarding screen to walk the user through the cycle positions.
     */
    fun nextCycleIndex(currentCycleIndex: Int): Int {
        require(currentCycleIndex in 0 until CYCLE_LENGTH) {
            "currentCycleIndex must be 0..${CYCLE_LENGTH - 1}, was $currentCycleIndex"
        }
        return (currentCycleIndex + 1) % CYCLE_LENGTH
    }

    /**
     * Computes the [ShiftType] for every date in [[startDate], [endDate]] inclusive.
     * Returns a map for O(1) lookup by the calendar composable.
     */
    fun shiftTypesForRange(
        startDate: LocalDate,
        endDate: LocalDate,
        anchorDate: LocalDate,
        anchorCycleIndex: Int,
    ): Map<LocalDate, ShiftType> {
        require(!startDate.isAfter(endDate)) {
            "startDate ($startDate) must not be after endDate ($endDate)"
        }
        val result = LinkedHashMap<LocalDate, ShiftType>(
            ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1,
        )
        var current = startDate
        while (!current.isAfter(endDate)) {
            result[current] = shiftTypeForDate(current, anchorDate, anchorCycleIndex)
            current = current.plusDays(1)
        }
        return result
    }
}
