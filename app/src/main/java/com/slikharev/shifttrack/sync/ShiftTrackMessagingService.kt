package com.slikharev.shifttrack.sync

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.slikharev.shifttrack.auth.UserSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Firebase Cloud Messaging service.
 *
 * - [onNewToken]: persists the new token locally via [FcmTokenManager] and
 *   pushes it to Firestore when the user is signed in.
 *
 * - [onMessageReceived]: any data-only push (e.g., a teammate updated their
 *   shifts) triggers an immediate one-shot [SyncWorker] run so the local
 *   Room database stays in sync without waiting for the next periodic window.
 */
@AndroidEntryPoint
class ShiftTrackMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmTokenManager: FcmTokenManager
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var userSession: UserSession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Called when a new FCM registration token is generated (on first install
     * or when the existing token is invalidated).
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            fcmTokenManager.onTokenRefreshed(token, userSession.currentUserId)
        }
    }

    /**
     * Called when a data push arrives while the app is in the foreground or
     * the process is alive. Schedules an immediate sync for any data message.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data.isNotEmpty()) {
            syncScheduler.scheduleImmediateSync()
        }
    }
}
