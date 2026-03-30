package com.slikharev.shifttrack.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires exactly at midnight to refresh all home-screen widget instances so
 * they display the correct "today" column.
 *
 * After refreshing, re-schedules itself for the next midnight via
 * [MidnightAlarmScheduler.scheduleNext].
 */
class MidnightWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ShiftWidgetEntryPoint::class.java,
                )
                val updater = entryPoint.shiftWidgetUpdater()
                updater.updateAll()
                Log.d(TAG, "Midnight widget refresh complete")
            } catch (e: Exception) {
                Log.w(TAG, "Midnight widget refresh failed", e)
            } finally {
                MidnightAlarmScheduler.scheduleNext(context)
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "MidnightWidgetReceiver"
    }
}
