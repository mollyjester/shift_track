package com.slikharev.shifttrack.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.auth.AuthRepository
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.dao.ShiftDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.invite.InviteRepository
import com.slikharev.shifttrack.widget.ShiftWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appDataStore: AppDataStore,
    private val shiftDao: ShiftDao,
    private val leaveDao: LeaveDao,
    private val leaveBalanceDao: LeaveBalanceDao,
    private val overtimeDao: OvertimeDao,
    private val overtimeBalanceDao: OvertimeBalanceDao,
    private val authRepository: AuthRepository,
    private val userSession: UserSession,
    private val inviteRepository: InviteRepository,
    private val widgetUpdater: ShiftWidgetUpdater,
) : ViewModel() {

    private val uid get() = userSession.currentUserId.orEmpty()
    private val currentYear = LocalDate.now().year

    // ─── Persisted anchor ────────────────────────────────────────────────────────

    val anchorDate: StateFlow<LocalDate?> = appDataStore.anchorDate
        .flatMapLatest { str -> flowOf(str?.let { LocalDate.parse(it) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val anchorCycleIndex: StateFlow<Int> = appDataStore.anchorCycleIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    // ─── Leave balance ────────────────────────────────────────────────────────────

    val leaveBalance: StateFlow<LeaveBalanceEntity?> = combine(
        appDataStore.anchorDate, // re-trigger after onboarding completes
        flowOf(currentYear),
    ) { _, year -> year }
        .flatMapLatest { year ->
            leaveBalanceDao.observeBalanceForYear(uid, year)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ─── Overtime balance ─────────────────────────────────────────────────────────

    val overtimeBalance: StateFlow<OvertimeBalanceEntity?> =
        overtimeBalanceDao.observeBalanceForYear(uid, currentYear)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ─── User info ────────────────────────────────────────────────────────────────

    val displayName: String get() = authRepository.currentUser?.displayName ?: ""
    val email: String get() = authRepository.currentUser?.email ?: ""

    // ─── Mutable UI state ─────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // ─── Computed today label ─────────────────────────────────────────────────────

    /** The shift type label for today given the current anchor, or null if not set. */
    val todayShiftLabel: StateFlow<String?> = combine(
        anchorDate,
        anchorCycleIndex,
    ) { date, idx ->
        if (date == null || idx < 0) null
        else {
            val type = CadenceEngine.shiftTypeForDate(LocalDate.now(), date, idx)
            type.name.lowercase().replaceFirstChar { it.uppercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ─── Actions ─────────────────────────────────────────────────────────────────

    /**
     * Saves a new anchor (shift schedule).
     * The semantics mirror the Onboarding screen: [date] is the day for which the
     * user declares it is cycle position [cycleIndex].
     */
    fun updateAnchor(date: LocalDate, cycleIndex: Int) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(isSaving = true)
            try {
                appDataStore.setAnchor(date.toString(), cycleIndex)
                widgetUpdater.updateAll()
                _uiState.value = SettingsUiState(savedMessage = "Schedule updated")
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(error = "Failed to save: ${e.message}")
            }
        }
    }

    /**
     * Updates the total annual leave allowance for the current year.
     * Creates the balance row if it doesn't exist yet.
     */
    fun updateLeaveTotalDays(totalDays: Float) {
        require(totalDays > 0f) { "Leave days must be positive" }
        viewModelScope.launch {
            _uiState.value = SettingsUiState(isSaving = true)
            try {
                val existing = leaveBalanceDao.getBalanceForYear(uid, currentYear)
                if (existing == null) {
                    leaveBalanceDao.upsert(
                        LeaveBalanceEntity(
                            year = currentYear,
                            totalDays = totalDays,
                            userId = uid,
                        ),
                    )
                } else {
                    leaveBalanceDao.update(existing.copy(totalDays = totalDays))
                }
                _uiState.value = SettingsUiState(savedMessage = "Leave allowance updated")
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(error = "Failed to save: ${e.message}")
            }
        }
    }

    /**
     * Records how many overtime hours have been compensated this year.
     * Does nothing if no overtime balance row exists yet.
     */
    fun updateCompensatedOvertimeHours(hours: Float) {
        require(hours >= 0f) { "Compensated hours cannot be negative" }
        viewModelScope.launch {
            _uiState.value = SettingsUiState(isSaving = true)
            try {
                val existing = overtimeBalanceDao.getBalanceForYear(uid, currentYear)
                if (existing != null) {
                    overtimeBalanceDao.update(existing.copy(compensatedHours = hours))
                    _uiState.value = SettingsUiState(savedMessage = "Overtime balance updated")
                } else {
                    _uiState.value = SettingsUiState()
                }
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(error = "Failed to save: ${e.message}")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null, error = null)
    }

    // ─── Invite ───────────────────────────────────────────────────────────────────

    /** Deep link ready to be shared, e.g. `shiftapp://invite/{token}`. Null when idle. */
    private val _pendingInviteLink = MutableStateFlow<String?>(null)
    val pendingInviteLink: StateFlow<String?> = _pendingInviteLink.asStateFlow()

    /**
     * Creates an invite token in Firestore and stores the resulting share link in
     * [pendingInviteLink]. The caller should present a share sheet once the link
     * is non-null and then call [clearInviteLink] to reset.
     */
    fun generateInvite() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(isSaving = true)
            try {
                val token = inviteRepository.createInvite(uid, displayName)
                _pendingInviteLink.value = "shiftapp://invite/$token"
                _uiState.value = SettingsUiState()
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(error = "Failed to generate invite: ${e.message}")
            }
        }
    }

    fun clearInviteLink() {
        _pendingInviteLink.value = null
    }

    fun signOut(onComplete: () -> Unit) {
        authRepository.signOut()
        onComplete()
    }

    /**
     * Deletes all user data (Room, DataStore, Firestore) and the Firebase Auth
     * account, then invokes [onComplete] for the caller to navigate away.
     *
     * If the user's credential is stale, sets an error message asking them to
     * sign out and sign back in before retrying.
     */
    fun deleteAccount(onComplete: () -> Unit) {
        val uid = uid
        viewModelScope.launch {
            _uiState.value = SettingsUiState(isSaving = true)
            try {
                // Firebase Auth deletion comes first: if it fails (stale credential)
                // no local data has been touched yet.
                authRepository.deleteAccount()
                // Auth deletion succeeded — wipe all local data.
                shiftDao.deleteAllForUser(uid)
                leaveDao.deleteAllForUser(uid)
                leaveBalanceDao.deleteAllForUser(uid)
                overtimeDao.deleteAllForUser(uid)
                overtimeBalanceDao.deleteAllForUser(uid)
                appDataStore.clearAll()
                onComplete()
            } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                _uiState.value = SettingsUiState(
                    error = "Please sign out and sign back in, then try again.",
                )
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(
                    error = "Failed to delete account: ${e.message}",
                )
            }
        }
    }
}
