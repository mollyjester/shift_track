package com.slikharev.shifttrack.onboarding

import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.ShiftType
import java.time.LocalDate

/**
 * The three pages of the onboarding flow.
 *
 *  Step 1 — Pick what today's shift is in the 5-day rotation.
 *  Step 2 — Set annual leave entitlement (days per year).
 *  Step 3 — Preview the user's next 5 days and confirm.
 */
enum class OnboardingStep { SHIFT_PICKER, LEAVE_SETUP, CONFIRM }

/** Labels shown to the user for each cycle position (index 0-4). */
val CYCLE_LABELS: List<String> = listOf(
    "First Day shift",   // 0  → ShiftType.DAY
    "Second Day shift",  // 1  → ShiftType.DAY
    "Night shift",       // 2  → ShiftType.NIGHT
    "Rest day",          // 3  → ShiftType.REST
    "Day off",           // 4  → ShiftType.OFF
)

/**
 * Immutable snapshot of the onboarding wizard state.
 *
 * @param step              Current page of the wizard.
 * @param anchorDate        User's "today" — always [LocalDate.now] at wizard start; can be
 *                          adjusted +/- 1 day in case the user opens the app at midnight.
 * @param selectedCycleIndex  Which position in the 5-day cycle [anchorDate] maps to (0-4).
 *                            -1 = nothing selected yet.
 * @param leaveAllowanceDays  Annual leave days for the current year (default 28).
 * @param isSaving          True while the final save coroutine is running.
 * @param error             Non-null when the save operation failed.
 * @param previewDays       Populated on step CONFIRM: DayPreview for the next 7 days.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.SHIFT_PICKER,
    val anchorDate: LocalDate = LocalDate.now(),
    val selectedCycleIndex: Int = -1,
    val leaveAllowanceDays: Int = 28,
    val isSaving: Boolean = false,
    val error: String? = null,
    val previewDays: List<DayPreview> = emptyList(),
    /** When true the user skips all schedule setup and enters as a spectator. */
    val spectatorOnly: Boolean = false,
)

data class DayPreview(
    val date: LocalDate,
    val shiftType: ShiftType,
    val label: String,
)

/** Build a 7-day preview list from the current wizard state. */
fun OnboardingUiState.buildPreview(): List<DayPreview> {
    if (selectedCycleIndex < 0) return emptyList()
    return (0L..6L).map { offset ->
        val date = anchorDate.plusDays(offset)
        val shiftType = CadenceEngine.shiftTypeForDate(date, anchorDate, selectedCycleIndex)
        DayPreview(
            date = date,
            shiftType = shiftType,
            label = when (shiftType) {
                ShiftType.DAY -> "Day shift"
                ShiftType.NIGHT -> "Night shift"
                ShiftType.REST -> "Rest"
                ShiftType.OFF -> "Off"
                ShiftType.LEAVE -> "Leave"
            },
        )
    }
}
