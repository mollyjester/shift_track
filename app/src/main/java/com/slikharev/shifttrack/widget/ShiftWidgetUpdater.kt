package com.slikharev.shifttrack.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers an immediate re-render of every pinned widget instance.
 *
 * Uses [AppWidgetManager] directly — no Glance middleware. The update is
 * synchronous: [AppWidgetManager.updateAppWidget] pushes fresh [RemoteViews]
 * to the launcher process immediately.
 *
 * Inject this wherever data changes (SettingsViewModel, SyncWorker, etc.).
 * Errors are swallowed so a missing widget host never crashes the caller.
 */
@Singleton
class ShiftWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun updateAll() {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ShiftWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                ShiftWidgetProvider.updateSingleWidget(context, manager, id)
            }
        }.onFailure { Log.w(TAG, "Widget update failed (non-critical)", it) }
    }

    private companion object {
        const val TAG = "ShiftWidgetUpdater"
    }
}
