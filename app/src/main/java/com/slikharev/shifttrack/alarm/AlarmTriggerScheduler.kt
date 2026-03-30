package com.slikharev.shifttrack.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the evening exact alarm that triggers [AlarmTriggerReceiver].
 *
 * The alarm fires once each day at the configured trigger time (default 21:00).
 * After the receiver processes the alarm it calls [scheduleNext] for the
 * following day. [BootReceiver] also re-registers by calling [scheduleNextIfEnabled].
 */
// [EXPERIMENTAL:ALARM]
object AlarmTriggerScheduler {

    private const val TAG = "AlarmTriggerScheduler"

    /**
     * Schedules an exact alarm for [triggerTimeHHmm] today (or tomorrow if
     * that time has already passed).
     */
    fun scheduleNext(context: Context, triggerTimeHHmm: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms — permission not granted")
            return
        }

        val parts = triggerTimeHHmm.split(":")
        val hour = parts[0].toIntOrNull() ?: 21
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val triggerTime = LocalTime.of(hour, minute)

        var triggerDate = LocalDate.now().atTime(triggerTime)
        if (!triggerDate.isAfter(java.time.LocalDateTime.now())) {
            triggerDate = triggerDate.plusDays(1)
        }

        val millis = triggerDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(context)

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        Log.d(TAG, "Scheduled alarm trigger at $triggerDate")
    }

    /**
     * Reads preferences and schedules the next trigger if the feature is enabled.
     * Safe to call from [BootReceiver] without a coroutine — uses a blocking
     * snapshot read from DataStore.
     */
    fun scheduleNextIfEnabled(context: Context) {
        // Use a short-lived coroutine to read DataStore — callers that are
        // already in a coroutine (BootReceiver) can call this directly.
        kotlinx.coroutines.runBlocking {
            val prefs = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.slikharev.shifttrack.widget.ShiftWidgetEntryPoint::class.java,
            ).alarmPreferences()
            if (!prefs.readEnabled()) {
                Log.d(TAG, "Alarm feature disabled — skipping schedule")
                return@runBlocking
            }
            val triggerTime = prefs.readTriggerTime()
            scheduleNext(context, triggerTime)
        }
    }

    /** Cancels any pending alarm trigger. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
        Log.d(TAG, "Cancelled alarm trigger")
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            AlarmConstants.TRIGGER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
