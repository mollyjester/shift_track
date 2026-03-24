package com.slikharev.shifttrack.sync

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.widget.ShiftWidgetUpdater
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
 * - [onMessageReceived]: any data-only push (e.g., a host updated their
 *   shifts) triggers an immediate one-shot [SyncWorker] run so the local
 *   Room database stays in sync, refreshes the spectator cache, and updates
 *   all widgets.
 */
@AndroidEntryPoint
class ShiftTrackMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmTokenManager: FcmTokenManager
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var userSession: UserSession
    @Inject lateinit var spectatorCacheRefresher: SpectatorCacheRefresher
    @Inject lateinit var widgetUpdater: ShiftWidgetUpdater

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            fcmTokenManager.onTokenRefreshed(token, userSession.currentUserId)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data.isNotEmpty()) {
            syncScheduler.scheduleImmediateSync()
            // Refresh spectator cache + widget when host data changes.
            serviceScope.launch {
                runCatching { spectatorCacheRefresher.refresh() }
                runCatching { widgetUpdater.updateAll() }
            }
        }
    }
}
