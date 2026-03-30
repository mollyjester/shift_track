package com.slikharev.shifttrack.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.slikharev.shifttrack.alarm.AlarmTriggerScheduler

/**
 * Re-registers exact alarms after device reboot (alarms do not persist across reboots).
 *
 * Reschedules:
 * - Midnight widget refresh alarm ([MidnightAlarmScheduler])
 * - Evening alarm trigger for wake-up alarms ([AlarmTriggerScheduler]) // [EXPERIMENTAL:ALARM]
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Boot completed — re-scheduling alarms")

        MidnightAlarmScheduler.scheduleNext(context)

        // [EXPERIMENTAL:ALARM]
        AlarmTriggerScheduler.scheduleNextIfEnabled(context)
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
