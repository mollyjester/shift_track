package com.slikharev.shifttrack.sync

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// TODO(Phase 2.10): Handle FCM tokens and push-triggered sync
class ShiftTrackMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: Store new token in Firestore users/{uid}/fcmToken
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // TODO: Trigger sync WorkManager job on data-change push
    }
}
