package com.slikharev.shifttrack.invite

import com.slikharev.shifttrack.data.remote.InviteDocument

/**
 * Abstracts the invite token lifecycle: creation, lookup, and atomic redemption.
 *
 * Concrete implementation writes to / reads from Firestore.
 * Test doubles operate in memory.
 */
interface InviteRepository {
    /**
     * Creates a new invite document in Firestore and returns the generated
     * token (a UUID string). The link the host shares is:
     * `shiftapp://invite/{token}`.
     *
     * The invite is valid for 7 days from the moment of creation.
     */
    suspend fun createInvite(hostUid: String, hostDisplayName: String): String

    /**
     * Fetches the invite document for [token].
     * Returns null when the document does not exist.
     */
    suspend fun getInvite(token: String): InviteDocument?

    /**
     * Atomically marks [token] as claimed by [guestUid] and appends [guestUid]
     * to the host's `spectators` array in their user document.
     */
    suspend fun redeemInvite(token: String, guestUid: String): RedeemResult
}

sealed interface RedeemResult {
    data object Success : RedeemResult
    data object AlreadyClaimed : RedeemResult
    data object Expired : RedeemResult
    data object NotFound : RedeemResult
    data class Error(val message: String) : RedeemResult
}
