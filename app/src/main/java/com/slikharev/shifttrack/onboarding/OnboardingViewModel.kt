package com.slikharev.shifttrack.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appDataStore: AppDataStore,
    private val leaveBalanceDao: LeaveBalanceDao,
    private val userSession: UserSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    // ── Step 1: shift picker ─────────────────────────────────────────────────

    fun selectCycleIndex(index: Int) {
        require(index in 0..4) { "index must be 0-4" }
        _uiState.update { it.copy(selectedCycleIndex = index, error = null) }
    }

    /** Move anchor date one day forward (helpful when opened right after midnight). */
    fun shiftAnchorForward() {
        _uiState.update { it.copy(anchorDate = it.anchorDate.plusDays(1)) }
    }

    fun shiftAnchorBack() {
        _uiState.update { it.copy(anchorDate = it.anchorDate.minusDays(1)) }
    }

    // ── Step 2: leave setup ──────────────────────────────────────────────────

    fun setLeaveAllowanceDays(days: Int) {
        val clamped = days.coerceIn(1, 365)
        _uiState.update { it.copy(leaveAllowanceDays = clamped, error = null) }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    /** Advance to the next step. Returns false if validation fails. */
    fun nextStep(): Boolean {
        val state = _uiState.value
        return when (state.step) {
            OnboardingStep.SHIFT_PICKER -> {
                if (state.selectedCycleIndex < 0) {
                    _uiState.update { it.copy(error = "Please select your current shift position.") }
                    false
                } else {
                    _uiState.update {
                        it.copy(step = OnboardingStep.LEAVE_SETUP, error = null)
                    }
                    true
                }
            }
            OnboardingStep.LEAVE_SETUP -> {
                // Build preview on transition to CONFIRM
                val preview = state.buildPreview()
                _uiState.update {
                    it.copy(step = OnboardingStep.CONFIRM, previewDays = preview, error = null)
                }
                true
            }
            OnboardingStep.CONFIRM -> false // handled by completeOnboarding()
        }
    }

    fun prevStep() {
        _uiState.update { state ->
            when (state.step) {
                OnboardingStep.LEAVE_SETUP -> state.copy(step = OnboardingStep.SHIFT_PICKER, error = null)
                OnboardingStep.CONFIRM -> state.copy(step = OnboardingStep.LEAVE_SETUP, error = null)
                OnboardingStep.SHIFT_PICKER -> state // already first step
            }
        }
    }

    // ── Final save ───────────────────────────────────────────────────────────

    /**
     * Persists anchor date + cycle index to DataStore, creates the initial leave-balance
     * row in Room, then marks onboarding complete.
     *
     * Calls [onSuccess] on the main thread when done.
     */
    fun completeOnboarding(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.selectedCycleIndex < 0) return
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                // 1. Save anchor
                appDataStore.setAnchor(
                    date = state.anchorDate.toString(),
                    cycleIndex = state.selectedCycleIndex,
                )

                // 2. Seed leave balance for current year
                val userId = userSession.currentUserId.orEmpty()
                val year = LocalDate.now().year
                leaveBalanceDao.upsert(
                    LeaveBalanceEntity(
                        year = year,
                        totalDays = state.leaveAllowanceDays.toFloat(),
                        usedDays = 0f,
                        userId = userId,
                    ),
                )

                // 3. Mark onboarding complete (this is last so a crash before here is retryable)
                appDataStore.setOnboardingComplete(true)
                appDataStore.setLastResetYear(year)

                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save: ${e.message}")
                }
            }
        }
    }
}
