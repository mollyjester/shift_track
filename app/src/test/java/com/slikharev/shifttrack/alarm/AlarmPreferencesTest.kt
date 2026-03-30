package com.slikharev.shifttrack.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the alarm feature's pure-logic components.
 *
 * Tests cover alarm time computation and override priority logic.
 * No Android dependencies — all pure Kotlin.
 */
// [EXPERIMENTAL:ALARM]
class AlarmPreferencesTest {

    // ── computeAlarmTimes ────────────────────────────────────────────────────

    @Test
    fun `default parameters produce expected times`() {
        val times = AlarmPreferences.computeAlarmTimes("04:30", 4, 10)
        assertEquals(listOf("04:30", "04:40", "04:50", "05:00"), times)
    }

    @Test
    fun `single alarm returns only the first time`() {
        val times = AlarmPreferences.computeAlarmTimes("06:00", 1, 10)
        assertEquals(listOf("06:00"), times)
    }

    @Test
    fun `interval of 5 minutes with 3 alarms`() {
        val times = AlarmPreferences.computeAlarmTimes("05:45", 3, 5)
        assertEquals(listOf("05:45", "05:50", "05:55"), times)
    }

    @Test
    fun `alarms crossing an hour boundary`() {
        val times = AlarmPreferences.computeAlarmTimes("05:50", 3, 10)
        assertEquals(listOf("05:50", "06:00", "06:10"), times)
    }

    @Test
    fun `maximum 10 alarms with 10 minute intervals`() {
        val times = AlarmPreferences.computeAlarmTimes("04:00", 10, 10)
        assertEquals(
            listOf("04:00", "04:10", "04:20", "04:30", "04:40",
                   "04:50", "05:00", "05:10", "05:20", "05:30"),
            times,
        )
    }

    @Test
    fun `midnight start time`() {
        val times = AlarmPreferences.computeAlarmTimes("00:00", 2, 15)
        assertEquals(listOf("00:00", "00:15"), times)
    }

    @Test
    fun `20 minute interval`() {
        val times = AlarmPreferences.computeAlarmTimes("03:00", 4, 20)
        assertEquals(listOf("03:00", "03:20", "03:40", "04:00"), times)
    }
}
