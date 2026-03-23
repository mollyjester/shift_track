package com.slikharev.shifttrack.invite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the [InviteRedemptionScreen].
 *
 * The invite [token] is extracted from [SavedStateHandle] (set by the Navigation
 * route `invite/{token}` or via the `shiftapp://invite/{token}` deep link).
 *
 * State machine:
 *   Loading → Valid | NotFound | Expired | AlreadyClaimed | Error
 *   Valid ─(accept)→ Redeeming → Success | AlreadyClaimed | Expired | NotFound | Error
 *   Error ─(retry)→ Loading → …
 */
@HiltViewModel
class InviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inviteRepository: InviteRepository,
    private val userSession: UserSession,
    private val appDataStore: AppDataStore,
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    private val token: String = checkNotNull(savedStateHandle["token"]) {
        "InviteViewModel requires a 'token' navigation argument"
    }

    private val _uiState = MutableStateFlow<InviteUiState>(InviteUiState.Loading)
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    init {
        loadInvite()
    }

    private fun loadInvite() {
        viewModelScope.launch {
            _uiState.value = InviteUiState.Loading
            _uiState.value = try {
                val invite = inviteRepository.getInvite(token)
                when {
                    invite == null -> InviteUiState.NotFound
                    invite.claimed -> InviteUiState.AlreadyClaimed
                    invite.isExpired -> InviteUiState.Expired
                    else -> InviteUiState.Valid(invite)
                }
            } catch (e: Exception) {
                InviteUiState.Error(e.message ?: "Failed to load invite")
            }
        }
    }

    /** Re-fetches the invite document (e.g., after a transient error). */
    fun retry() = loadInvite()

    /**
     * Accepts the invite: marks the token as claimed in Firestore and links
     * the current user as a spectator to the host.
     */
    fun accept() {
        val uid = userSession.currentUserId
        if (uid == null) {
            _uiState.value = InviteUiState.Error("You must be signed in to accept an invite")
            return
        }
        val currentState = _uiState.value as? InviteUiState.Valid ?: return

        viewModelScope.launch {
            _uiState.value = InviteUiState.Redeeming
            val result = inviteRepository.redeemInvite(token, uid)
            if (result == RedeemResult.Success) {
                appDataStore.addWatchedHost(
                    currentState.invite.hostUid,
                    currentState.invite.hostDisplayName,
                )
                appDataStore.setSelectedHostUid(currentState.invite.hostUid)
            }
            _uiState.value = when (result) {
                RedeemResult.Success -> InviteUiState.Success(currentState.invite.hostDisplayName)
                RedeemResult.AlreadyClaimed -> InviteUiState.AlreadyClaimed
                RedeemResult.Expired -> InviteUiState.Expired
                RedeemResult.NotFound -> InviteUiState.NotFound
                is RedeemResult.Error -> InviteUiState.Error(result.message)
            }
        }
    }
}
