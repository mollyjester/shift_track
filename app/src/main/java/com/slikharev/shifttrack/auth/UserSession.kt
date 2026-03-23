package com.slikharev.shifttrack.auth

/**
 * Minimal interface for components that only need to know the current user's ID.
 * Avoids a hard dependency on [AuthRepository] from non-auth ViewModels.
 */
interface UserSession {
    val currentUserId: String?
}

/** Returns the current user ID or throws if not authenticated. */
fun UserSession.requireUserId(): String =
    currentUserId ?: throw IllegalStateException("User not authenticated")
