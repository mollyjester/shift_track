package com.slikharev.shifttrack.onboarding

import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import java.time.LocalDate

/**
 * The four pages of the onboarding flow.
 *
 *  Step 1 — Pick what today's shift is in the 5-day rotation.
 *  Step 2 — Select your country (for holidays, weekends, currency).
 *  Step 3 — Set per-category leave entitlement (days per year).
 *  Step 4 — Preview the user's next 5 days and confirm.
 */
enum class OnboardingStep { SHIFT_PICKER, COUNTRY_SELECT, LEAVE_SETUP, CONFIRM }

/** Labels shown to the user for each cycle position (index 0-4). */
val CYCLE_LABELS: List<String> = listOf(
    "First Day shift",   // 0  → ShiftType.DAY
    "Second Day shift",  // 1  → ShiftType.DAY
    "Night shift",       // 2  → ShiftType.NIGHT
    "Rest day",          // 3  → ShiftType.REST
    "Day off",           // 4  → ShiftType.OFF
)

/** Default leave allowance per category (used during onboarding). */
val DEFAULT_LEAVE_ALLOWANCES: Map<LeaveType, Int> = mapOf(
    LeaveType.ANNUAL to 28,
    LeaveType.SICK to 10,
    LeaveType.PERSONAL to 5,
    LeaveType.UNPAID to 0,
    LeaveType.STUDY to 0,
)

/**
 * Immutable snapshot of the onboarding wizard state.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.SHIFT_PICKER,
    val anchorDate: LocalDate = LocalDate.now(),
    val selectedCycleIndex: Int = -1,
    val selectedCountryCode: String? = null,
    val leaveAllowances: Map<LeaveType, Int> = DEFAULT_LEAVE_ALLOWANCES,
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

/** Total leave days across all categories. */
val OnboardingUiState.totalLeaveDays: Int
    get() = leaveAllowances.values.sum()

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
