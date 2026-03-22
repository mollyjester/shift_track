package com.slikharev.shifttrack.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import com.slikharev.shifttrack.data.repository.LeaveRepository
import com.slikharev.shifttrack.data.repository.OvertimeRepository
import com.slikharev.shifttrack.data.repository.ShiftRepository
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.widget.ShiftWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shiftRepository: ShiftRepository,
    private val leaveRepository: LeaveRepository,
    private val overtimeRepository: OvertimeRepository,
    private val userSession: UserSession,
    private val widgetUpdater: ShiftWidgetUpdater,
) : ViewModel() {

    val date: LocalDate = LocalDate.parse(
        requireNotNull(savedStateHandle.get<String>("date")) { "date argument missing" },
    )

    val dayInfo: StateFlow<DayInfo?> = shiftRepository
        .getDayInfosForRange(date, date)
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reactive overtime entry for this day — null when none is recorded. */
    val overtimeEntry: StateFlow<OvertimeEntity?> = overtimeRepository
        .getOvertimeForRange(date, date)
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setManualOverride(shiftType: ShiftType) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                shiftRepository.setManualOverride(
                    userId = userSession.currentUserId.orEmpty(),
                    date = date,
                    shiftType = shiftType,
                )
                widgetUpdater.updateAll()
            } catch (e: Exception) {
                _error.value = "Failed to save: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearManualOverride() {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                shiftRepository.clearManualOverride(
                    userId = userSession.currentUserId.orEmpty(),
                    date = date,
                )
                widgetUpdater.updateAll()
            } catch (e: Exception) {
                _error.value = "Failed to clear: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun addLeave(leaveType: LeaveType, halfDay: Boolean, note: String?) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                leaveRepository.addLeave(date, leaveType, halfDay, note)
                widgetUpdater.updateAll()
            } catch (e: Exception) {
                _error.value = "Failed to save leave: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun removeLeave() {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                leaveRepository.removeLeave(date)
                widgetUpdater.updateAll()
            } catch (e: Exception) {
                _error.value = "Failed to remove leave: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun addOvertime(hours: Float, note: String? = null) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                overtimeRepository.addOvertime(date, hours, note)
                widgetUpdater.updateAll()
            } catch (e: Exception) {
                _error.value = "Failed to save overtime: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun removeOvertime() {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                overtimeRepository.removeOvertime(date)
                widgetUpdater.updateAll()
            } catch (e: Exception) {
                _error.value = "Failed to remove overtime: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }
}
