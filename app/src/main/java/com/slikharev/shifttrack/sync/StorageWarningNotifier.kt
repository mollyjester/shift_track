package com.slikharev.shifttrack.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.slikharev.shifttrack.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows a system notification when attachment storage approaches the 500 MB limit.
 */
@Singleton
class StorageWarningNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "storage_warning"
        private const val NOTIFICATION_ID = 9001
    }

    fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Storage Warnings",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when attachment storage is nearly full"
        }
        nm.createNotificationChannel(channel)
    }

    fun showWarning(usedBytes: Long, maxBytes: Long) {
        ensureChannel()
        val usedMb = usedBytes / (1024 * 1024)
        val maxMb = maxBytes / (1024 * 1024)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Storage Almost Full")
            .setContentText("You've used $usedMb MB of $maxMb MB. Go to Settings \u2192 Storage & Cleanup to free space.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("You've used $usedMb MB of $maxMb MB. Open the app, go to Settings \u2192 Storage & Cleanup, and delete old attachments to free space."),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }
}
