package com.slikharev.shifttrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.slikharev.shifttrack.alarm.AlarmConstants // [EXPERIMENTAL:ALARM]
import com.slikharev.shifttrack.widget.MidnightAlarmScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShiftTrackApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        MidnightAlarmScheduler.scheduleNext(this)
        createAlarmNotificationChannel() // [EXPERIMENTAL:ALARM]
    }

    // [EXPERIMENTAL:ALARM]
    private fun createAlarmNotificationChannel() {
        val channel = NotificationChannel(
            AlarmConstants.NOTIFICATION_CHANNEL_ID,
            "Wake-up alarm reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications the evening before a day shift to set wake-up alarms"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
