package com.slikharev.shifttrack.engine

import com.slikharev.shifttrack.model.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [CadenceEngine].
 *
 * Cycle layout for reference:
 *   0 → DAY
 *   1 → DAY
 *   2 → NIGHT
 *   3 → REST
 *   4 → OFF
 *
 * All tests use [ANCHOR] = 2024-01-01 with varying [anchorCycleIndex] values
 * so it is easy to reason about expected results on paper.
 */
class CadenceEngineTest {

    private val anchor = LocalDate.of(2024, 1, 1)

    // ── shiftTypeForDate — basic on-anchor cases ─────────────────────────────

    @Test
    fun `anchor date with index 0 returns DAY`() {
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor, anchor, 0))
    }

    @Test
    fun `anchor date with index 1 returns DAY`() {
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor, anchor, 1))
    }

    @Test
    fun `anchor date with index 2 returns NIGHT`() {
        assertEquals(ShiftType.NIGHT, CadenceEngine.shiftTypeForDate(anchor, anchor, 2))
    }

    @Test
    fun `anchor date with index 3 returns REST`() {
        assertEquals(ShiftType.REST, CadenceEngine.shiftTypeForDate(anchor, anchor, 3))
    }

    @Test
    fun `anchor date with index 4 returns OFF`() {
        assertEquals(ShiftType.OFF, CadenceEngine.shiftTypeForDate(anchor, anchor, 4))
    }

    // ── shiftTypeForDate — forward stepping ──────────────────────────────────

    @Test
    fun `one day after anchor index 0 is second DAY (index 1)`() {
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor.plusDays(1), anchor, 0))
    }

    @Test
    fun `two days after anchor index 0 is NIGHT`() {
        assertEquals(ShiftType.NIGHT, CadenceEngine.shiftTypeForDate(anchor.plusDays(2), anchor, 0))
    }

    @Test
    fun `three days after anchor index 0 is REST`() {
        assertEquals(ShiftType.REST, CadenceEngine.shiftTypeForDate(anchor.plusDays(3), anchor, 0))
    }

    @Test
    fun `four days after anchor index 0 is OFF`() {
        assertEquals(ShiftType.OFF, CadenceEngine.shiftTypeForDate(anchor.plusDays(4), anchor, 0))
    }

    @Test
    fun `five days after anchor index 0 wraps back to DAY`() {
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor.plusDays(5), anchor, 0))
    }

    @Test
    fun `ten days after anchor index 0 wraps to DAY again`() {
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor.plusDays(10), anchor, 0))
    }

    @Test
    fun `full cycle from NIGHT anchor advances correctly`() {
        // Anchor at NIGHT (index 2): expected sequence from anchor = NIGHT, REST, OFF, DAY, DAY, NIGHT…
        assertEquals(ShiftType.NIGHT, CadenceEngine.shiftTypeForDate(anchor, anchor, 2))
        assertEquals(ShiftType.REST, CadenceEngine.shiftTypeForDate(anchor.plusDays(1), anchor, 2))
        assertEquals(ShiftType.OFF, CadenceEngine.shiftTypeForDate(anchor.plusDays(2), anchor, 2))
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor.plusDays(3), anchor, 2))
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor.plusDays(4), anchor, 2))
        assertEquals(ShiftType.NIGHT, CadenceEngine.shiftTypeForDate(anchor.plusDays(5), anchor, 2))
    }

    // ── shiftTypeForDate — backward stepping ─────────────────────────────────

    @Test
    fun `one day before anchor index 0 is OFF (wraps to end of previous cycle)`() {
        // Cycle before DAY(0) is OFF(4)
        assertEquals(ShiftType.OFF, CadenceEngine.shiftTypeForDate(anchor.minusDays(1), anchor, 0))
    }

    @Test
    fun `two days before anchor index 0 is REST`() {
        assertEquals(ShiftType.REST, CadenceEngine.shiftTypeForDate(anchor.minusDays(2), anchor, 0))
    }

    @Test
    fun `three days before anchor index 0 is NIGHT`() {
        assertEquals(ShiftType.NIGHT, CadenceEngine.shiftTypeForDate(anchor.minusDays(3), anchor, 0))
    }

    @Test
    fun `five days before anchor index 0 wraps back to DAY`() {
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor.minusDays(5), anchor, 0))
    }

    @Test
    fun `one day before anchor index 2 (NIGHT) is DAY`() {
        // Cycle before NIGHT is DAY(1)
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor.minusDays(1), anchor, 2))
    }

    @Test
    fun `large negative offset still returns correct shift`() {
        // 365 days back from anchor index 0;  365 % 5 = 0 → same position → DAY
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor.minusDays(365), anchor, 0))
    }

    @Test
    fun `large positive offset wraps correctly`() {
        // 1000 days forward; 1000 % 5 = 0 → same position → DAY
        assertEquals(ShiftType.DAY, CadenceEngine.shiftTypeForDate(anchor.plusDays(1000), anchor, 0))
    }

    // ── shiftTypeForDate — validation ────────────────────────────────────────

    @Test
    fun `negative anchorCycleIndex throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            CadenceEngine.shiftTypeForDate(anchor, anchor, -1)
        }
    }

    @Test
    fun `anchorCycleIndex out of upper bound throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            CadenceEngine.shiftTypeForDate(anchor, anchor, 5)
        }
    }

    // ── nextCycleIndex ───────────────────────────────────────────────────────

    @Test
    fun `nextCycleIndex advances through all 5 positions and wraps`() {
        assertEquals(1, CadenceEngine.nextCycleIndex(0))
        assertEquals(2, CadenceEngine.nextCycleIndex(1))
        assertEquals(3, CadenceEngine.nextCycleIndex(2))
        assertEquals(4, CadenceEngine.nextCycleIndex(3))
        assertEquals(0, CadenceEngine.nextCycleIndex(4))
    }

    @Test
    fun `nextCycleIndex invalid input throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            CadenceEngine.nextCycleIndex(5)
        }
    }

    // ── shiftTypesForRange ───────────────────────────────────────────────────

    @Test
    fun `range of one day equals single shiftTypeForDate result`() {
        val result = CadenceEngine.shiftTypesForRange(anchor, anchor, anchor, 0)
        assertEquals(1, result.size)
        assertEquals(ShiftType.DAY, result[anchor])
    }

    @Test
    fun `range returns all 5 distinct shift types in one full cycle`() {
        // anchor index=0 (DAY): days 0..4 should produce exactly one of each shift type
        val result = CadenceEngine.shiftTypesForRange(anchor, anchor.plusDays(4), anchor, 0)
        assertEquals(5, result.size)
        assertEquals(ShiftType.DAY, result[anchor])
        assertEquals(ShiftType.DAY, result[anchor.plusDays(1)])
        assertEquals(ShiftType.NIGHT, result[anchor.plusDays(2)])
        assertEquals(ShiftType.REST, result[anchor.plusDays(3)])
        assertEquals(ShiftType.OFF, result[anchor.plusDays(4)])
    }

    @Test
    fun `range spanning two full cycles has 10 entries`() {
        val result = CadenceEngine.shiftTypesForRange(anchor, anchor.plusDays(9), anchor, 0)
        assertEquals(10, result.size)
        // Verify the pattern repeats: day 5 should equal day 0
        assertEquals(result[anchor], result[anchor.plusDays(5)])
        assertEquals(result[anchor.plusDays(1)], result[anchor.plusDays(6)])
        assertEquals(result[anchor.plusDays(3)], result[anchor.plusDays(8)])
    }

    @Test
    fun `range startDate after endDate throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            CadenceEngine.shiftTypesForRange(
                startDate = anchor.plusDays(1),
                endDate = anchor,
                anchorDate = anchor,
                anchorCycleIndex = 0,
            )
        }
    }

    @Test
    fun `range result keys are ordered chronologically`() {
        val result = CadenceEngine.shiftTypesForRange(anchor, anchor.plusDays(4), anchor, 0)
        val keys = result.keys.toList()
        for (i in 1 until keys.size) {
            assert(keys[i].isAfter(keys[i - 1])) {
                "Keys not in order: ${keys[i - 1]} >= ${keys[i]}"
            }
        }
    }

    @Test
    fun `range that starts before anchor computes correctly`() {
        // 3 days before anchor (index 0): NIGHT, REST, OFF, DAY(anchor)
        val start = anchor.minusDays(3)
        val result = CadenceEngine.shiftTypesForRange(start, anchor, anchor, 0)
        assertEquals(4, result.size)
        assertEquals(ShiftType.NIGHT, result[start])
        assertEquals(ShiftType.REST, result[start.plusDays(1)])
        assertEquals(ShiftType.OFF, result[start.plusDays(2)])
        assertEquals(ShiftType.DAY, result[anchor])
    }

    // ── CYCLE constant sanity checks ─────────────────────────────────────────

    @Test
    fun `cycle has length 5`() {
        assertEquals(5, CadenceEngine.CYCLE_LENGTH)
        assertEquals(5, CadenceEngine.CYCLE.size)
    }

    @Test
    fun `cycle positions 0 and 1 are DAY`() {
        assertEquals(ShiftType.DAY, CadenceEngine.CYCLE[0])
        assertEquals(ShiftType.DAY, CadenceEngine.CYCLE[1])
    }

    @Test
    fun `cycle position 2 is NIGHT, 3 is REST, 4 is OFF`() {
        assertEquals(ShiftType.NIGHT, CadenceEngine.CYCLE[2])
        assertEquals(ShiftType.REST, CadenceEngine.CYCLE[3])
        assertEquals(ShiftType.OFF, CadenceEngine.CYCLE[4])
    }
}
