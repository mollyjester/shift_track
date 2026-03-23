package com.slikharev.shifttrack.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.model.LeaveType
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

    fun shiftAnchorForward() {
        _uiState.update { it.copy(anchorDate = it.anchorDate.plusDays(1)) }
    }

    fun shiftAnchorBack() {
        _uiState.update { it.copy(anchorDate = it.anchorDate.minusDays(1)) }
    }

    fun setSpectatorOnly(enabled: Boolean) {
        _uiState.update { it.copy(spectatorOnly = enabled, error = null) }
    }

    // ── Step 2: per-category leave setup ─────────────────────────────────────

    fun setLeaveAllowance(leaveType: LeaveType, days: Int) {
        val clamped = days.coerceIn(0, 365)
        _uiState.update { state ->
            state.copy(
                leaveAllowances = state.leaveAllowances + (leaveType to clamped),
                error = null,
            )
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

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
                val preview = state.buildPreview()
                _uiState.update {
                    it.copy(step = OnboardingStep.CONFIRM, previewDays = preview, error = null)
                }
                true
            }
            OnboardingStep.CONFIRM -> false
        }
    }

    fun prevStep() {
        _uiState.update { state ->
            when (state.step) {
                OnboardingStep.LEAVE_SETUP -> state.copy(step = OnboardingStep.SHIFT_PICKER, error = null)
                OnboardingStep.CONFIRM -> state.copy(step = OnboardingStep.LEAVE_SETUP, error = null)
                OnboardingStep.SHIFT_PICKER -> state
            }
        }
    }

    // ── Final save ───────────────────────────────────────────────────────────

    fun completeOnboarding(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.spectatorOnly && state.selectedCycleIndex < 0) return
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                if (!state.spectatorOnly) {
                    appDataStore.setAnchor(
                        date = state.anchorDate.toString(),
                        cycleIndex = state.selectedCycleIndex,
                    )

                    val userId = userSession.currentUserId.orEmpty()
                    val year = LocalDate.now().year
                    for ((leaveType, days) in state.leaveAllowances) {
                        leaveBalanceDao.upsert(
                            LeaveBalanceEntity(
                                year = year,
                                leaveType = leaveType.name,
                                totalDays = days.toFloat(),
                                usedDays = 0f,
                                userId = userId,
                            ),
                        )
                    }
                    appDataStore.setLastResetYear(year)
                }

                appDataStore.setOnboardingComplete(true)

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
