package com.slikharev.shifttrack.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

/**
 * Schedules an exact alarm at the next midnight to refresh all home-screen widgets.
 *
 * Uses [AlarmManager.setExactAndAllowWhileIdle] so the alarm fires even in Doze mode.
 * The alarm is one-shot: [MidnightWidgetReceiver] re-schedules the next one after firing.
 * Must also be called from [BootReceiver] because exact alarms do not survive reboots.
 */
object MidnightAlarmScheduler {

    private const val TAG = "MidnightAlarmScheduler"
    private const val REQUEST_CODE = 9001

    /**
     * Schedules an exact alarm for the start of tomorrow (00:00 local time).
     * No-op if the app lacks [AlarmManager.canScheduleExactAlarms] permission.
     */
    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarm permission not granted — skipping midnight widget alarm")
            return
        }

        val nextMidnightMillis = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val pi = buildPendingIntent(context)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnightMillis, pi)
        Log.d(TAG, "Midnight widget alarm scheduled for ${LocalDate.now().plusDays(1)}")
    }

    /** Cancels any pending midnight alarm. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MidnightWidgetReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
