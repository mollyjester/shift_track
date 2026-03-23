package com.slikharev.shifttrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.slikharev.shifttrack.R
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.ui.ShiftColorConfig
import com.slikharev.shifttrack.ui.ShiftColors
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-screen widget built with raw [RemoteViews] for maximum reliability.
 *
 * Two layouts:
 * - **Small** (< 200 dp wide): Shows today's shift.
 * - **Wide** (≥ 200 dp wide): Shows 1–7 upcoming days.
 *
 * Configuration is accessed via the system's long-press → Reconfigure menu
 * (declared as `reconfigurable` in `shift_widget_info.xml`).
 *
 * All preferences are read via a single atomic [AppDataStore.readWidgetSnapshot].
 * Updates are immediate — [AppWidgetManager.updateAppWidget] is synchronous.
 */
class ShiftWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in appWidgetIds) {
                    updateSingleWidget(context, appWidgetManager, id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateSingleWidget(context, appWidgetManager, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val WIDE_THRESHOLD_DP = 200

        // View IDs for the 7 day slots in the wide layout.
        private data class DaySlot(val root: Int, val label: Int, val shift: Int)

        private val DAY_SLOTS = arrayOf(
            DaySlot(R.id.widget_day_0, R.id.widget_day_label_0, R.id.widget_day_shift_0),
            DaySlot(R.id.widget_day_1, R.id.widget_day_label_1, R.id.widget_day_shift_1),
            DaySlot(R.id.widget_day_2, R.id.widget_day_label_2, R.id.widget_day_shift_2),
            DaySlot(R.id.widget_day_3, R.id.widget_day_label_3, R.id.widget_day_shift_3),
            DaySlot(R.id.widget_day_4, R.id.widget_day_label_4, R.id.widget_day_shift_4),
            DaySlot(R.id.widget_day_5, R.id.widget_day_label_5, R.id.widget_day_shift_5),
            DaySlot(R.id.widget_day_6, R.id.widget_day_label_6, R.id.widget_day_shift_6),
        )

        /**
         * Reads preferences, computes shifts, and pushes a fresh [RemoteViews]
         * to [AppWidgetManager]. Safe to call from any coroutine context.
         */
        suspend fun updateSingleWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val appDataStore = try {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ShiftWidgetEntryPoint::class.java,
                ).appDataStore()
            } catch (_: Exception) {
                return
            }

            val snap = try {
                appDataStore.readWidgetSnapshot()
            } catch (_: Exception) {
                return
            }

            val widgetState = WidgetShiftCalculator.compute(
                snap.anchorDate,
                snap.anchorCycleIndex,
                dayCount = AppDataStore.MAX_WIDGET_DAYS,
            )

            val colorConfig = ShiftColorConfig(
                dayColor = snap.colorDay?.let { Color(it.toInt()) } ?: ShiftColors.Day,
                nightColor = snap.colorNight?.let { Color(it.toInt()) } ?: ShiftColors.Night,
                restColor = snap.colorRest?.let { Color(it.toInt()) } ?: ShiftColors.Rest,
                offColor = snap.colorOff?.let { Color(it.toInt()) } ?: ShiftColors.Off,
                leaveColor = snap.colorLeave?.let { Color(it.toInt()) } ?: ShiftColors.Leave,
            )

            val options = manager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)

            val views = if (minWidth >= WIDE_THRESHOLD_DP) {
                buildWideLayout(context, widgetState, snap, colorConfig)
            } else {
                buildSmallLayout(context, widgetState, snap, colorConfig)
            }

            manager.updateAppWidget(appWidgetId, views)
        }

        // ── Small layout ─────────────────────────────────────────────────────

        private fun buildSmallLayout(
            context: Context,
            state: WidgetUiState,
            snap: AppDataStore.WidgetSnapshot,
            colorConfig: ShiftColorConfig,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_small)
            views.setInt(R.id.widget_small_root, "setBackgroundColor", computeBgColor(snap))

            if (state.isConfigured && state.days.isNotEmpty()) {
                val today = state.days.first()
                views.setTextViewText(
                    R.id.widget_small_date,
                    today.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())),
                )
                views.setTextViewText(
                    R.id.widget_small_shift,
                    ShiftColors.label(today.shiftType).uppercase(),
                )
                views.setInt(
                    R.id.widget_small_shift,
                    "setBackgroundColor",
                    colorConfig.containerColor(today.shiftType).toArgb(),
                )
                views.setTextColor(
                    R.id.widget_small_shift,
                    colorConfig.onContainerColor(today.shiftType).toArgb(),
                )

                // Tap → open day in app
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("shiftapp://day/${today.date}")).apply {
                    setClassName(context, "com.slikharev.shifttrack.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pi = PendingIntent.getActivity(
                    context, today.date.hashCode(),
                    intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_small_root, pi)
            } else {
                views.setTextViewText(R.id.widget_small_date, "")
                views.setTextViewText(R.id.widget_small_shift, "Open ShiftTrack\nto set up")
                views.setInt(R.id.widget_small_shift, "setBackgroundColor", 0x00000000)
                views.setTextColor(R.id.widget_small_shift, 0xFF43474E.toInt())
            }
            return views
        }

        // ── Wide layout ──────────────────────────────────────────────────────

        private fun buildWideLayout(
            context: Context,
            state: WidgetUiState,
            snap: AppDataStore.WidgetSnapshot,
            colorConfig: ShiftColorConfig,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_wide)
            views.setInt(R.id.widget_wide_root, "setBackgroundColor", computeBgColor(snap))

            if (state.isConfigured && state.days.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_wide_content, View.VISIBLE)
                views.setViewVisibility(R.id.widget_wide_message, View.GONE)

                val days = state.days.take(snap.dayCount)

                for (i in DAY_SLOTS.indices) {
                    val slot = DAY_SLOTS[i]
                    if (i < days.size) {
                        val dayInfo = days[i]
                        views.setViewVisibility(slot.root, View.VISIBLE)
                        views.setViewVisibility(slot.label, View.GONE)

                        // Shift chip
                        views.setTextViewText(
                            slot.shift,
                            ShiftColors.label(dayInfo.shiftType).uppercase(),
                        )
                        views.setInt(
                            slot.shift,
                            "setBackgroundColor",
                            colorConfig.containerColor(dayInfo.shiftType).toArgb(),
                        )
                        views.setTextColor(
                            slot.shift,
                            colorConfig.onContainerColor(dayInfo.shiftType).toArgb(),
                        )

                        // Tap → deep link to that day
                        val dayIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("shiftapp://day/${dayInfo.date}"),
                        ).apply {
                            setClassName(context, "com.slikharev.shifttrack.MainActivity")
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        val dayPi = PendingIntent.getActivity(
                            context, dayInfo.date.hashCode(),
                            dayIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        views.setOnClickPendingIntent(slot.root, dayPi)
                    } else {
                        views.setViewVisibility(slot.root, View.GONE)
                    }
                }
            } else {
                views.setViewVisibility(R.id.widget_wide_content, View.GONE)
                views.setViewVisibility(R.id.widget_wide_message, View.VISIBLE)
            }
            return views
        }

        // ── Helpers ──────────────────────────────────────────────────────────

        /**
         * Combines the stored background color with the transparency value,
         * returning a single ARGB int ready for `setBackgroundColor`.
         */
        private fun computeBgColor(snap: AppDataStore.WidgetSnapshot): Int {
            val baseColor = snap.bgColor?.toInt() ?: 0xFFF8FDFF.toInt()
            val alpha = (snap.transparency * 255).toInt().coerceIn(0, 255)
            return (baseColor and 0x00FFFFFF) or (alpha shl 24)
        }
    }
}
