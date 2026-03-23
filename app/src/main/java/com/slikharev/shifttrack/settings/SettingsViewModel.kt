package com.slikharev.shifttrack.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.auth.AuthRepository
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.dao.ShiftDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.local.PrefsKeys
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
    private val firestoreUserDataSource: com.slikharev.shifttrack.data.remote.FirestoreUserDataSource,
) : ViewModel() {

    private val uid get() = userSession.requireUserId()
    private val currentYear = LocalDate.now().year

    /** True when the user is in spectator-only mode (no own schedule). */
    val isSpectatorOnly: StateFlow<Boolean> = appDataStore.spectatorMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ─── Persisted anchor ────────────────────────────────────────────────────────

    val anchorDate: StateFlow<LocalDate?> = appDataStore.anchorDate
        .flatMapLatest { str -> flowOf(str?.let { LocalDate.parse(it) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val anchorCycleIndex: StateFlow<Int> = appDataStore.anchorCycleIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    // ─── Leave balance ────────────────────────────────────────────────────────────

    // Per-category balances exposed via leaveBalances below.

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
                // Sync anchor to Firestore so spectators can compute cadence
                runCatching { firestoreUserDataSource.saveAnchor(uid, date.toString(), cycleIndex) }
                widgetUpdater.updateAll()
                _uiState.value = SettingsUiState(savedMessage = "Schedule updated")
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

    // ─── Leave balance (per-category) ─────────────────────────────────────────

    val leaveBalances: StateFlow<List<com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity>> =
        combine(
            appDataStore.anchorDate,
            flowOf(currentYear),
        ) { _, year -> year }
            .flatMapLatest { year ->
                leaveBalanceDao.observeAllBalancesForYear(uid, year)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateLeaveTotalDaysByType(leaveType: String, totalDays: Float) {
        require(totalDays >= 0f) { "Leave days cannot be negative" }
        viewModelScope.launch {
            _uiState.value = SettingsUiState(isSaving = true)
            try {
                val existing = leaveBalanceDao.getBalanceForYearAndType(uid, currentYear, leaveType)
                if (existing == null) {
                    leaveBalanceDao.upsert(
                        com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity(
                            year = currentYear,
                            leaveType = leaveType,
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

    // ─── Colors ───────────────────────────────────────────────────────────────

    fun saveShiftColor(shiftType: com.slikharev.shifttrack.model.ShiftType, argb: Long) {
        viewModelScope.launch {
            val key = when (shiftType) {
                com.slikharev.shifttrack.model.ShiftType.DAY -> PrefsKeys.COLOR_DAY
                com.slikharev.shifttrack.model.ShiftType.NIGHT -> PrefsKeys.COLOR_NIGHT
                com.slikharev.shifttrack.model.ShiftType.REST -> PrefsKeys.COLOR_REST
                com.slikharev.shifttrack.model.ShiftType.OFF -> PrefsKeys.COLOR_OFF
                com.slikharev.shifttrack.model.ShiftType.LEAVE -> PrefsKeys.COLOR_LEAVE
            }
            appDataStore.setShiftColor(key, argb)
            widgetUpdater.updateAll()
        }
    }

    // ─── Widget configuration ─────────────────────────────────────────────────

    val widgetBgColor: StateFlow<Long?> = appDataStore.widgetBgColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val widgetTransparency: StateFlow<Float> = appDataStore.widgetTransparency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDataStore.DEFAULT_WIDGET_TRANSPARENCY)

    val widgetDayCount: StateFlow<Int> = appDataStore.widgetDayCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDataStore.DEFAULT_WIDGET_DAY_COUNT)

    fun setWidgetBgColor(argb: Long) {
        viewModelScope.launch {
            appDataStore.setWidgetBgColor(argb)
            widgetUpdater.updateAll()
        }
    }

    fun setWidgetTransparency(alpha: Float) {
        viewModelScope.launch {
            appDataStore.setWidgetTransparency(alpha)
            widgetUpdater.updateAll()
        }
    }

    fun setWidgetDayCount(count: Int) {
        viewModelScope.launch {
            appDataStore.setWidgetDayCount(count)
            widgetUpdater.updateAll()
        }
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
                _pendingInviteLink.value = "https://mollyjester.github.io/shift_track/invite.html?token=$token"
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
