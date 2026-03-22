package com.slikharev.shifttrack.sync

import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.remote.FirestoreUserDataSource
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of the Firebase Cloud Messaging registration token.
 *
 * FCM tokens can arrive before the user has signed in (e.g., on first install),
 * so the token is always persisted locally in [AppDataStore]. When the user is
 * signed in at the time of token refresh, the token is also uploaded to
 * Firestore (`users/{uid}/fcmToken`) immediately. When the user is not yet
 * signed in, [uploadPendingToken] should be called after sign-in to push the
 * locally-stored token.
 */
@Singleton
class FcmTokenManager @Inject constructor(
    private val appDataStore: AppDataStore,
    private val firestoreUserDataSource: FirestoreUserDataSource,
) {
    /**
     * Called when the FCM registration token is created or rotated.
     *
     * Always persists [token] in [AppDataStore]. If [uid] is non-null (user is
     * already signed in), also uploads to Firestore immediately. Firestore
     * failures are swallowed — the token is already safe in DataStore and
     * [uploadPendingToken] will retry on next sign-in.
     */
    suspend fun onTokenRefreshed(token: String, uid: String?) {
        appDataStore.setPendingFcmToken(token)
        if (uid != null) {
            runCatching { firestoreUserDataSource.saveFcmToken(uid, token) }
        }
    }

    /**
     * Reads the locally-stored FCM token and pushes it to
     * `users/{uid}/fcmToken` in Firestore. Call this after a successful sign-in
     * so that tokens received before the user logged in are not lost.
     *
     * Does nothing if no token is stored locally.
     */
    suspend fun uploadPendingToken(uid: String) {
        val token = appDataStore.pendingFcmToken.firstOrNull() ?: return
        runCatching { firestoreUserDataSource.saveFcmToken(uid, token) }
    }
}
