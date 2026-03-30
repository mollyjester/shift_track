package com.slikharev.shifttrack.auth

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.slikharev.shifttrack.data.remote.FirestoreUserDataSource
import com.slikharev.shifttrack.sync.FcmTokenManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val encryptedPrefs: SharedPreferences,
    private val firestoreUserDataSource: FirestoreUserDataSource,
    private val fcmTokenManager: FcmTokenManager,
) : UserSession {
    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
    }

    open val currentUser: FirebaseUser? get() = firebaseAuth.currentUser
    override val currentUserId: String? get() = currentUser?.uid

    /**
     * Emits the current FirebaseUser whenever auth state changes.
     * Emits null when signed out. Used by the ViewModel to drive navigation.
     */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /**
     * Returns true if cached credentials exist.
     * This allows offline re-entry: the user can open the app without a network
     * connection as long as they have previously authenticated.
     */
    fun hasCachedCredentials(): Boolean =
        encryptedPrefs.getString(KEY_USER_ID, null) != null

    fun getCachedUserId(): String? = encryptedPrefs.getString(KEY_USER_ID, null)

    /**
     * Exchanges a Google ID token for a Firebase credential, signs in,
     * caches the result in EncryptedSharedPreferences, and writes the
     * user document to Firestore (best-effort — does not block sign-in).
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val user = authResult.user
            ?: return Result.failure(Exception("No user returned from Firebase"))
        cacheCredentials(user)
        // Write to Firestore best-effort — offline or permission failures must not
        // block the sign-in flow; they will be retried by the sync layer.
        runCatching { firestoreUserDataSource.saveUser(user) }
        // Upload any FCM token that arrived before the user signed in.
        runCatching { fcmTokenManager.uploadPendingToken(user.uid) }
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun signOut() {
        firebaseAuth.signOut()
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Deletes the Firebase Authentication account and clears locally cached
     * credentials. Firestore data deletion is handled best-effort via
     * [firestoreUserDataSource].
     *
     * @throws com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
     *   if the user's credential is stale — the caller must ask the user to
     *   sign out and sign back in before retrying.
     */
    suspend fun deleteAccount() {
        val uid = currentUserId ?: return
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("No current Firebase user")
        // Delete Firestore data first — safe to orphan cloud data if auth
        // deletion later fails, but losing auth with data still present
        // would create unreachable records.
        runCatching { firestoreUserDataSource.deleteUserData(uid) }
        // Delete the Firebase Auth account. Throws if re-auth is needed.
        user.delete().await()
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Re-authenticates the current user with a fresh Google ID token and then
     * deletes the account. Call this when [deleteAccount] throws
     * [com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException].
     */
    suspend fun reauthenticateAndDelete(idToken: String) {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("No current Firebase user")
        val uid = user.uid
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        user.reauthenticate(credential).await()
        runCatching { firestoreUserDataSource.deleteUserData(uid) }
        user.delete().await()
        encryptedPrefs.edit().clear().apply()
    }

    private fun cacheCredentials(user: FirebaseUser) {
        encryptedPrefs.edit()
            .putString(KEY_USER_ID, user.uid)
            .putString(KEY_DISPLAY_NAME, user.displayName ?: "")
            .putString(KEY_EMAIL, user.email ?: "")
            .apply()
    }
}
