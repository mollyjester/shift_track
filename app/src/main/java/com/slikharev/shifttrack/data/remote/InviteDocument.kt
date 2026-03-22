package com.slikharev.shifttrack.data.remote

/**
 * Firestore document stored at `invites/{token}`.
 *
 * A host creates this document by tapping "Generate invite link" in Settings.
 * The deep link `shiftapp://invite/{token}` is shared with a potential viewer.
 * On redemption the document is marked [claimed] and the viewer's UID is added
 * to the host's `users/{hostUid}/spectators` array.
 */
data class InviteDocument(
    val token: String = "",
    val hostUid: String = "",
    val hostDisplayName: String = "",
    val createdAt: Long = 0L,
    /** Unix millis after which the token is no longer valid. */
    val expiresAt: Long = 0L,
    val claimed: Boolean = false,
    val claimedBy: String? = null,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}
