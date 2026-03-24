package com.slikharev.shifttrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.firebase.auth.FirebaseAuth
import com.slikharev.shifttrack.R
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.LeaveColorConfig
import com.slikharev.shifttrack.ui.LeaveColors
import com.slikharev.shifttrack.ui.ShiftColorConfig
import com.slikharev.shifttrack.ui.ShiftColors
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
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
            } catch (e: Exception) {
                Log.w(TAG, "Widget onUpdate failed", e)
                WidgetDiagnostics.logError(context, "onUpdate", e)
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
            } catch (e: Exception) {
                Log.w(TAG, "Widget onOptionsChanged failed", e)
                WidgetDiagnostics.logError(context, "onOptionsChanged", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ShiftWidgetProvider"
        private const val WIDE_THRESHOLD_DP = 200

        /** View IDs for each day slot in the wide layout. */
        private data class DaySlotIds(
            val root: Int,
            val top: Int,
            val bottom: Int,
            val dateText: Int,
            val shiftText: Int,
            val dot: Int,
        )

        private val DAY_SLOTS = arrayOf(
            DaySlotIds(R.id.widget_day_0, R.id.widget_day_top_0, R.id.widget_day_bottom_0, R.id.widget_day_date_0, R.id.widget_day_shift_0, R.id.widget_day_dot_0),
            DaySlotIds(R.id.widget_day_1, R.id.widget_day_top_1, R.id.widget_day_bottom_1, R.id.widget_day_date_1, R.id.widget_day_shift_1, R.id.widget_day_dot_1),
            DaySlotIds(R.id.widget_day_2, R.id.widget_day_top_2, R.id.widget_day_bottom_2, R.id.widget_day_date_2, R.id.widget_day_shift_2, R.id.widget_day_dot_2),
            DaySlotIds(R.id.widget_day_3, R.id.widget_day_top_3, R.id.widget_day_bottom_3, R.id.widget_day_date_3, R.id.widget_day_shift_3, R.id.widget_day_dot_3),
            DaySlotIds(R.id.widget_day_4, R.id.widget_day_top_4, R.id.widget_day_bottom_4, R.id.widget_day_date_4, R.id.widget_day_shift_4, R.id.widget_day_dot_4),
            DaySlotIds(R.id.widget_day_5, R.id.widget_day_top_5, R.id.widget_day_bottom_5, R.id.widget_day_date_5, R.id.widget_day_shift_5, R.id.widget_day_dot_5),
            DaySlotIds(R.id.widget_day_6, R.id.widget_day_top_6, R.id.widget_day_bottom_6, R.id.widget_day_date_6, R.id.widget_day_shift_6, R.id.widget_day_dot_6),
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
            val entryPoint = try {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ShiftWidgetEntryPoint::class.java,
                )
            } catch (e: Exception) {
                WidgetDiagnostics.logError(context, "entryPoint", e)
                return
            }

            val appDataStore = entryPoint.appDataStore()

            val snap = try {
                appDataStore.readWidgetSnapshot()
            } catch (e: Exception) {
                WidgetDiagnostics.logError(context, "readSnapshot", e)
                return
            }

            // Determine widget state: spectator mode reads from local cache (or fetches
            // Firestore when cache is absent/stale), normal mode computes from local anchor.
            val enrichedState: WidgetUiState = if (snap.spectatorMode && snap.selectedHostUid != null) {
                // Check if the cache is fresh: it must contain an entry for today.
                val today = LocalDate.now()
                val cacheHasToday = snap.spectatorCache.any { entry ->
                    runCatching { LocalDate.parse(entry.date) }.getOrNull() == today
                }

                val effectiveCache: List<AppDataStore.SpectatorWidgetEntry> = if (cacheHasToday) {
                    snap.spectatorCache
                } else {
                    // Cache is missing or stale — fetch directly from Firestore.
                    // This is safe: we are already on Dispatchers.IO inside a coroutine.
                    val spectatorRepo = entryPoint.spectatorRepository()
                    val endDate = today.plusDays(AppDataStore.MAX_WIDGET_DAYS.toLong() - 1)
                    val infos = try {
                        withTimeout(8_000L) {
                            spectatorRepo.getDayInfosForRange(snap.selectedHostUid, today, endDate)
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (infos.isNotEmpty()) {
                        val entries = infos.map { d ->
                            AppDataStore.SpectatorWidgetEntry(
                                date = d.date.toString(),
                                shiftType = d.shiftType.name,
                                hasLeave = d.hasLeave,
                                halfDay = d.halfDay,
                                leaveType = d.leaveType?.name,
                            )
                        }
                        appDataStore.setSpectatorWidgetCache(entries)
                        entries
                    } else {
                        emptyList()
                    }
                }

                if (effectiveCache.isEmpty()) {
                    WidgetUiState(isConfigured = false, days = emptyList())
                } else {
                    val days = effectiveCache.mapIndexed { offset, entry ->
                        val shiftType = runCatching { ShiftType.valueOf(entry.shiftType) }
                            .getOrDefault(ShiftType.OFF)
                        val leaveType = entry.leaveType?.let { LeaveType.fromString(it) }
                        val date = runCatching { LocalDate.parse(entry.date) }
                            .getOrDefault(today.plusDays(offset.toLong()))
                        val label = when (offset) {
                            0 -> "Today"
                            1 -> "Tomorrow"
                            else -> date.dayOfWeek.getDisplayName(
                                java.time.format.TextStyle.SHORT, Locale.getDefault(),
                            )
                        }
                        WidgetDayInfo(
                            date = date,
                            shiftType = shiftType,
                            label = label,
                            isToday = offset == 0,
                            hasLeave = entry.hasLeave,
                            halfDay = entry.halfDay,
                            leaveType = leaveType,
                        )
                    }
                    WidgetUiState(isConfigured = true, days = days)
                }
            } else {
                // Normal mode: compute from local anchor + enrich with leave data
                val widgetState = WidgetShiftCalculator.compute(
                    snap.anchorDate,
                    snap.anchorCycleIndex,
                    dayCount = AppDataStore.MAX_WIDGET_DAYS,
                )

                val enrichedDays = try {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId != null && widgetState.days.isNotEmpty()) {
                        val leaveDao = entryPoint.leaveDao()
                        val startDate = widgetState.days.first().date.toString()
                        val endDate = widgetState.days.last().date.toString()
                        val leaves = leaveDao.getLeavesForRange(userId, startDate, endDate).first()
                        val leaveByDate = leaves.associateBy { it.date }

                        widgetState.days.map { day ->
                            val leave = leaveByDate[day.date.toString()]
                            if (leave != null) {
                                val lt = LeaveType.fromString(leave.leaveType)
                                day.copy(
                                    hasLeave = true,
                                    halfDay = leave.halfDay,
                                    leaveType = lt,
                                    shiftType = if (leave.halfDay) day.shiftType else ShiftType.LEAVE,
                                )
                            } else day
                        }
                    } else widgetState.days
                } catch (_: Exception) {
                    widgetState.days
                }

                widgetState.copy(days = enrichedDays)
            }

            val colorConfig = ShiftColorConfig(
                dayColor = snap.colorDay?.let { Color(it.toInt()) } ?: ShiftColors.Day,
                nightColor = snap.colorNight?.let { Color(it.toInt()) } ?: ShiftColors.Night,
                restColor = snap.colorRest?.let { Color(it.toInt()) } ?: ShiftColors.Rest,
                offColor = snap.colorOff?.let { Color(it.toInt()) } ?: ShiftColors.Off,
                leaveColor = snap.colorLeave?.let { Color(it.toInt()) } ?: ShiftColors.Leave,
            )

            val leaveColorConfig = LeaveColorConfig(
                annualColor = snap.colorLeaveAnnual?.let { Color(it.toInt()) } ?: LeaveColors.Annual,
                sickColor = snap.colorLeaveSick?.let { Color(it.toInt()) } ?: LeaveColors.Sick,
                personalColor = snap.colorLeavePersonal?.let { Color(it.toInt()) } ?: LeaveColors.Personal,
                unpaidColor = snap.colorLeaveUnpaid?.let { Color(it.toInt()) } ?: LeaveColors.Unpaid,
                studyColor = snap.colorLeaveStudy?.let { Color(it.toInt()) } ?: LeaveColors.Study,
            )

            val options = manager.getAppWidgetOptions(appWidgetId)
            // Default to 250dp (matches minWidth in shift_widget_info.xml)
            // so the wide layout is used when options aren't set yet.
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)

            val views = if (minWidth >= WIDE_THRESHOLD_DP) {
                buildWideLayout(context, enrichedState, snap, colorConfig, leaveColorConfig)
            } else {
                buildSmallLayout(context, enrichedState, snap, colorConfig, leaveColorConfig)
            }

            manager.updateAppWidget(appWidgetId, views)
        }

        // ── Small layout ─────────────────────────────────────────────────────

        private fun buildSmallLayout(
            context: Context,
            state: WidgetUiState,
            snap: AppDataStore.WidgetSnapshot,
            colorConfig: ShiftColorConfig,
            leaveColorConfig: LeaveColorConfig,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_small)
            views.setInt(R.id.widget_small_root, "setBackgroundColor", computeBgColor(snap))

            if (state.isConfigured && state.days.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_small_content, View.VISIBLE)
                views.setViewVisibility(R.id.widget_small_message, View.GONE)

                val today = state.days.first()
                val shiftColor = colorConfig.containerColor(today.shiftType).toArgb()

                // Full-day leave → light grey background; otherwise shift color
                val topColor = if (today.hasLeave && !today.halfDay) LEAVE_GREY_ARGB else shiftColor
                val bottomColor = if (today.hasLeave && today.halfDay) {
                    LEAVE_GREY_ARGB  // half-day leave → bottom half is light grey
                } else {
                    topColor
                }
                val textColor = computeOnColor(topColor)

                views.setInt(R.id.widget_small_top, "setBackgroundColor", topColor)
                views.setInt(R.id.widget_small_bottom, "setBackgroundColor", bottomColor)

                // Day number
                views.setTextViewText(R.id.widget_small_date, today.date.dayOfMonth.toString())
                views.setTextColor(R.id.widget_small_date, textColor)

                // Shift type label
                views.setTextViewText(R.id.widget_small_shift, ShiftColors.label(today.shiftType).uppercase())
                views.setTextColor(R.id.widget_small_shift, textColor)

                // Dot: only for full-day leave
                if (today.hasLeave && !today.halfDay && today.leaveType != null) {
                    views.setViewVisibility(R.id.widget_small_dot, View.VISIBLE)
                    views.setInt(R.id.widget_small_dot, "setBackgroundColor",
                        leaveColorConfig.color(today.leaveType).toArgb())
                } else {
                    views.setViewVisibility(R.id.widget_small_dot, View.GONE)
                }

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
                views.setViewVisibility(R.id.widget_small_content, View.GONE)
                views.setViewVisibility(R.id.widget_small_message, View.VISIBLE)
            }
            return views
        }

        // ── Wide layout ──────────────────────────────────────────────────────

        private fun buildWideLayout(
            context: Context,
            state: WidgetUiState,
            snap: AppDataStore.WidgetSnapshot,
            colorConfig: ShiftColorConfig,
            leaveColorConfig: LeaveColorConfig,
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

                        val topColor: Int
                        val bottomColor: Int
                        val textColor: Int
                        val typeLabel: String
                        var dotColor: Int? = null

                        val shiftColor = colorConfig.containerColor(dayInfo.shiftType).toArgb()
                        typeLabel = ShiftColors.label(dayInfo.shiftType).uppercase()

                        if (dayInfo.hasLeave && dayInfo.halfDay) {
                            // Half-day leave: top = shift, bottom = light grey, no dot
                            topColor = shiftColor
                            bottomColor = LEAVE_GREY_ARGB
                        } else if (dayInfo.hasLeave && !dayInfo.halfDay) {
                            // Full-day leave: light grey, dot colored by leave type
                            topColor = LEAVE_GREY_ARGB
                            bottomColor = LEAVE_GREY_ARGB
                            if (dayInfo.leaveType != null) {
                                dotColor = leaveColorConfig.color(dayInfo.leaveType).toArgb()
                            }
                        } else {
                            // Normal shift: solid color, no dot
                            topColor = shiftColor
                            bottomColor = shiftColor
                        }
                        textColor = computeOnColor(topColor)

                        views.setInt(slot.top, "setBackgroundColor", topColor)
                        views.setInt(slot.bottom, "setBackgroundColor", bottomColor)

                        // Day number
                        views.setTextViewText(slot.dateText, dayInfo.date.dayOfMonth.toString())
                        views.setTextColor(slot.dateText, textColor)

                        // Shift/leave type label
                        views.setTextViewText(slot.shiftText, typeLabel)
                        views.setTextColor(slot.shiftText, textColor)

                        // Leave indicator dot
                        if (dotColor != null) {
                            views.setViewVisibility(slot.dot, View.VISIBLE)
                            views.setInt(slot.dot, "setBackgroundColor", dotColor)
                        } else {
                            views.setViewVisibility(slot.dot, View.GONE)
                        }

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

        /** Light grey ARGB for leave-day backgrounds. */
        private const val LEAVE_GREY_ARGB = 0xFFE0E0E0.toInt()

        /** Returns dark or light text color based on background luminance. */
        private fun computeOnColor(argb: Int): Int {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            return if (luminance > 0.5f) 0xFF212121.toInt() else 0xFFEEEEEE.toInt()
        }

        /**
         * Combines the stored background color with the transparency value,
         * returning a single ARGB int ready for `setBackgroundColor`.
         */
        private fun computeBgColor(snap: AppDataStore.WidgetSnapshot): Int {
            val baseColor = snap.bgColor?.toInt() ?: 0xFFF8FDFF.toInt()
            val alpha = (snap.transparency * 255).toInt().coerceIn(0, 255)
            return (baseColor and 0x00FFFFFF) or (alpha shl 24)
        }

        /** Darkens an ARGB color by reducing RGB channels by [factor] (0..1). */
        private fun darkenArgb(argb: Int, factor: Float): Int {
            val a = (argb shr 24) and 0xFF
            val r = ((argb shr 16) and 0xFF).let { (it * (1f - factor)).toInt().coerceIn(0, 255) }
            val g = ((argb shr 8) and 0xFF).let { (it * (1f - factor)).toInt().coerceIn(0, 255) }
            val b = (argb and 0xFF).let { (it * (1f - factor)).toInt().coerceIn(0, 255) }
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
