package com.slikharev.shifttrack.invite

import com.slikharev.shifttrack.data.remote.InviteDocument

sealed interface InviteUiState {
    /** Initial state while fetching the invite document from Firestore. */
    data object Loading : InviteUiState

    /** Invite exists, has not expired, and has not been claimed yet. */
    data class Valid(val invite: InviteDocument) : InviteUiState

    /** Redemption in progress (network call running). */
    data object Redeeming : InviteUiState

    /** Redemption succeeded — viewer is now linked to the host's account. */
    data class Success(val hostDisplayName: String) : InviteUiState

    /** Token string was not found in Firestore. */
    data object NotFound : InviteUiState

    /** Token was valid but has passed its 7-day expiry. */
    data object Expired : InviteUiState

    /** Token has already been used by another account. */
    data object AlreadyClaimed : InviteUiState

    /** Unrecoverable or network error with a human-readable [message]. */
    data class Error(val message: String) : InviteUiState
}
