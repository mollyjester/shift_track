package com.slikharev.shifttrack.onboarding

import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [OnboardingUiState] helper functions and [OnboardingStep] transitions.
 *
 * These operate on plain data classes and functions — no Hilt, no coroutines, no Android.
 */
class OnboardingUiStateTest {

    private val today = LocalDate.of(2024, 6, 3)

    // ── buildPreview ──────────────────────────────────────────────────────────

    @Test
    fun `buildPreview returns empty list when no cycle index selected`() {
        val state = OnboardingUiState(anchorDate = today, selectedCycleIndex = -1)
        assertTrue(state.buildPreview().isEmpty())
    }

    @Test
    fun `buildPreview returns 7 days`() {
        val state = OnboardingUiState(anchorDate = today, selectedCycleIndex = 0)
        assertEquals(7, state.buildPreview().size)
    }

    @Test
    fun `buildPreview first day matches anchor cycle index`() {
        for (idx in 0..4) {
            val state = OnboardingUiState(anchorDate = today, selectedCycleIndex = idx)
            val preview = state.buildPreview()
            val expected = CadenceEngine.CYCLE[idx]
            assertEquals(
                "First preview day for index $idx should be ${CadenceEngine.CYCLE[idx]}",
                expected,
                preview[0].shiftType,
            )
        }
    }

    @Test
    fun `buildPreview dates are consecutive starting from anchorDate`() {
        val state = OnboardingUiState(anchorDate = today, selectedCycleIndex = 0)
        val preview = state.buildPreview()
        preview.forEachIndexed { offset, day ->
            assertEquals(today.plusDays(offset.toLong()), day.date)
        }
    }

    @Test
    fun `buildPreview labels are non-empty strings`() {
        val state = OnboardingUiState(anchorDate = today, selectedCycleIndex = 2)
        state.buildPreview().forEach { day ->
            assertTrue(day.label.isNotBlank())
        }
    }

    @Test
    fun `buildPreview with index 0 (DAY) shows correct 7-day pattern`() {
        // From DAY(0): DAY, DAY, NIGHT, REST, OFF, DAY, DAY
        val state = OnboardingUiState(anchorDate = today, selectedCycleIndex = 0)
        val types = state.buildPreview().map { it.shiftType }
        assertEquals(
            listOf(ShiftType.DAY, ShiftType.DAY, ShiftType.NIGHT, ShiftType.REST,
                ShiftType.OFF, ShiftType.DAY, ShiftType.DAY),
            types,
        )
    }

    @Test
    fun `buildPreview with index 4 (OFF) starts with OFF`() {
        val state = OnboardingUiState(anchorDate = today, selectedCycleIndex = 4)
        assertEquals(ShiftType.OFF, state.buildPreview()[0].shiftType)
    }

    // ── CYCLE_LABELS ──────────────────────────────────────────────────────────

    @Test
    fun `CYCLE_LABELS has 5 entries matching CadenceEngine cycle length`() {
        assertEquals(CadenceEngine.CYCLE_LENGTH, CYCLE_LABELS.size)
    }

    @Test
    fun `CYCLE_LABELS entries are all non-blank`() {
        CYCLE_LABELS.forEach { label -> assertTrue(label.isNotBlank()) }
    }

    // ── OnboardingUiState defaults ────────────────────────────────────────────

    @Test
    fun `default state starts on SHIFT_PICKER step`() {
        assertEquals(OnboardingStep.SHIFT_PICKER, OnboardingUiState().step)
    }

    @Test
    fun `default selectedCycleIndex is -1`() {
        assertEquals(-1, OnboardingUiState().selectedCycleIndex)
    }

    @Test
    fun `default leaveAllowanceDays is 28`() {
        assertEquals(28, OnboardingUiState().leaveAllowanceDays)
    }

    @Test
    fun `default error is null`() {
        assertNull(OnboardingUiState().error)
    }

    @Test
    fun `default previewDays is empty`() {
        assertTrue(OnboardingUiState().previewDays.isEmpty())
    }
}
