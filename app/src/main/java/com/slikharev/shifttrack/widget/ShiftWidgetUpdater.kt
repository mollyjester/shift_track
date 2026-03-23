package com.slikharev.shifttrack.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers a re-render of every pinned [ShiftWidget] instance.
 *
 * Inject this where the underlying data changes (e.g. [SyncWorker] after a
 * successful sync, or [SettingsViewModel] after the anchor is updated) to
 * keep the widget up-to-date without waiting for the system's periodic update.
 *
 * Errors are swallowed via [runCatching] so a missing widget host (no instances
 * pinned, or running in a test JVM) never crashes the caller.
 */
@Singleton
class ShiftWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun updateAll() {
        runCatching {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(ShiftWidget::class.java)
                .forEach { id -> ShiftWidget().update(context, id) }
        }.onFailure { Log.w(TAG, "Widget update failed (non-critical)", it) }
    }

    private companion object {
        const val TAG = "ShiftWidgetUpdater"
    }
}
