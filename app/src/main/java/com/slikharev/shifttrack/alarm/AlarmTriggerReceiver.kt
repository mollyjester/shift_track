package com.slikharev.shifttrack.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.slikharev.shifttrack.R
import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.widget.ShiftWidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fires each evening at the configured trigger time.
 *
 * Checks whether tomorrow is a DAY shift. If yes, posts a notification that
 * opens [AlarmSetterActivity]. Always re-schedules itself for the next day.
 *
 * Gating: if spectator mode is active, does nothing (spectators have no own schedule).
 */
// [EXPERIMENTAL:ALARM]
class AlarmTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ShiftWidgetEntryPoint::class.java,
                )
                val appDataStore = entryPoint.appDataStore()
                val alarmPrefs = entryPoint.alarmPreferences()

                // Gate: spectator mode → skip
                val isSpectator = appDataStore.spectatorMode.first()
                if (isSpectator) {
                    Log.d(TAG, "Spectator mode — skipping alarm trigger")
                    return@launch
                }

                // Gate: feature enabled
                if (!alarmPrefs.readEnabled()) {
                    Log.d(TAG, "Alarm feature disabled — skipping")
                    return@launch
                }

                // Check tomorrow's shift type
                val anchorStr = appDataStore.anchorDate.first()
                val anchorIndex = appDataStore.anchorCycleIndex.first()
                if (anchorStr == null || anchorIndex < 0) {
                    Log.d(TAG, "No anchor set — skipping")
                    return@launch
                }

                val anchor = LocalDate.parse(anchorStr)
                val tomorrow = LocalDate.now().plusDays(1)
                val shiftType = CadenceEngine.shiftTypeForDate(tomorrow, anchor, anchorIndex)

                if (shiftType != ShiftType.DAY) {
                    Log.d(TAG, "Tomorrow ($tomorrow) is $shiftType — no alarm needed")
                    return@launch
                }

                // Tomorrow is DAY — post notification
                postNotification(context, tomorrow)
                Log.d(TAG, "Posted alarm notification for $tomorrow")
            } catch (e: Exception) {
                Log.w(TAG, "Alarm trigger failed", e)
            } finally {
                // Always re-schedule for next evening
                AlarmTriggerScheduler.scheduleNextIfEnabled(context)
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, targetDate: LocalDate) {
        val dateLabel = targetDate.format(
            DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()),
        )

        val tapIntent = Intent(context, AlarmSetterActivity::class.java).apply {
            putExtra(AlarmConstants.EXTRA_TARGET_DATE, targetDate.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            AlarmConstants.NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, AlarmConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Day shift tomorrow — $dateLabel")
            .setContentText("Tap to set wake-up alarms")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.notify(AlarmConstants.NOTIFICATION_ID, notification)
    }

    private companion object {
        const val TAG = "AlarmTriggerReceiver"
    }
}
