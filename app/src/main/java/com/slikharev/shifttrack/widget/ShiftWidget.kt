package com.slikharev.shifttrack.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.slikharev.shifttrack.R
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.ShiftColorConfig
import com.slikharev.shifttrack.ui.ShiftColors
import dagger.hilt.android.EntryPointAccessors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WidgetSubLabel = ColorProvider(Color(0xFF43474E.toInt()))   // OnSurfaceVariant
private val WIDE_THRESHOLD = 200.dp

/**
 * Home screen widget showing today's shift type (small) or the next N days
 * in a row (wide) with a gear button to open widget configuration.
 *
 * Uses [SizeMode.Exact] so [LocalSize] reflects the real widget dimensions.
 * All preferences are read in a single atomic [AppDataStore.readWidgetSnapshot]
 * call to avoid stale/mixed values.
 * Call [ShiftWidgetUpdater.updateAll] to force a re-render when data changes.
 */
class ShiftWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appDataStore = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                ShiftWidgetEntryPoint::class.java,
            ).appDataStore()
        } catch (_: Exception) {
            null
        }

        // Read ALL widget-related preferences in a single atomic snapshot.
        val snap = try {
            appDataStore!!.readWidgetSnapshot()
        } catch (_: Exception) {
            null
        }

        val widgetState = if (snap != null) {
            WidgetShiftCalculator.compute(
                snap.anchorDate, snap.anchorCycleIndex,
                dayCount = AppDataStore.MAX_WIDGET_DAYS,
            )
        } else {
            WidgetUiState(isConfigured = false, days = emptyList())
        }

        val dayCount = snap?.dayCount ?: AppDataStore.DEFAULT_WIDGET_DAY_COUNT
        val transparency = snap?.transparency ?: AppDataStore.DEFAULT_WIDGET_TRANSPARENCY

        val bgColor = if (snap?.bgColor != null) {
            Color(snap.bgColor.toInt()).copy(alpha = transparency)
        } else {
            Color(0xFFF8FDFF.toInt()).copy(alpha = transparency)
        }

        val colorConfig = if (snap != null) {
            ShiftColorConfig(
                dayColor = snap.colorDay?.let { Color(it.toInt()) } ?: ShiftColors.Day,
                nightColor = snap.colorNight?.let { Color(it.toInt()) } ?: ShiftColors.Night,
                restColor = snap.colorRest?.let { Color(it.toInt()) } ?: ShiftColors.Rest,
                offColor = snap.colorOff?.let { Color(it.toInt()) } ?: ShiftColors.Off,
                leaveColor = snap.colorLeave?.let { Color(it.toInt()) } ?: ShiftColors.Leave,
            )
        } else {
            ShiftColorConfig()
        }

        provideContent {
            ShiftWidgetContent(widgetState, bgColor, dayCount, colorConfig)
        }
    }
}

@Composable
private fun ShiftWidgetContent(
    state: WidgetUiState,
    bgColor: Color,
    dayCount: Int,
    colorConfig: ShiftColorConfig,
) {
    val size = LocalSize.current
    if (size.width >= WIDE_THRESHOLD) {
        WideContent(state, bgColor, dayCount, colorConfig)
    } else {
        SmallContent(state, bgColor, colorConfig)
    }
}

// ── Small layout (2×2) ───────────────────────────────────────────────────────

@Composable
private fun SmallContent(state: WidgetUiState, bgColor: Color, colorConfig: ShiftColorConfig) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!state.isConfigured || state.days.isEmpty()) {
            NotConfiguredContent()
        } else {
            val today = state.days.first()
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .clickable(actionStartActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("shiftapp://day/${today.date}"))
                    )),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = today.date.format(
                        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()),
                    ),
                    style = TextStyle(
                        color = WidgetSubLabel,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(GlanceModifier.height(6.dp))
                ShiftChip(
                    shiftType = today.shiftType,
                    colorConfig = colorConfig,
                    modifier = GlanceModifier.fillMaxWidth().height(44.dp),
                )
            }
        }
    }
}

// ── Wide layout ──────────────────────────────────────────────────────────────

@Composable
private fun WideContent(
    state: WidgetUiState,
    bgColor: Color,
    dayCount: Int,
    colorConfig: ShiftColorConfig,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!state.isConfigured || state.days.isEmpty()) {
            NotConfiguredContent()
        } else {
            val days = state.days.take(dayCount)
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                days.forEachIndexed { index, dayInfo ->
                    if (index > 0) Spacer(GlanceModifier.width(4.dp))
                    DayColumn(
                        dayInfo = dayInfo,
                        colorConfig = colorConfig,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }

                Spacer(GlanceModifier.width(2.dp))
                GearButton()
            }
        }
    }
}

@Composable
private fun GearButton() {
    Box(
        modifier = GlanceModifier
            .width(28.dp)
            .fillMaxHeight()
            .clickable(
                actionStartActivity(
                    Intent().apply {
                        component = ComponentName(
                            "com.slikharev.shifttrack",
                            "com.slikharev.shifttrack.widget.WidgetConfigActivity",
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_settings),
            contentDescription = "Widget settings",
            modifier = GlanceModifier.size(18.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(WidgetSubLabel),
        )
    }
}

@Composable
private fun DayColumn(
    dayInfo: WidgetDayInfo,
    colorConfig: ShiftColorConfig,
    modifier: GlanceModifier = GlanceModifier,
) {
    val labelColor = if (dayInfo.isToday) {
        ColorProvider(colorConfig.onContainerColor(dayInfo.shiftType))
    } else {
        WidgetSubLabel
    }
    Column(
        modifier = modifier
            .clickable(actionStartActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("shiftapp://day/${dayInfo.date}"))
            )),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = dayInfo.label,
            style = TextStyle(
                color = labelColor,
                fontSize = 10.sp,
                fontWeight = if (dayInfo.isToday) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        ShiftChip(
            shiftType = dayInfo.shiftType,
            colorConfig = colorConfig,
            modifier = GlanceModifier.fillMaxWidth().height(40.dp),
        )
    }
}

// ── Shared composables ───────────────────────────────────────────────────────

@Composable
private fun ShiftChip(
    shiftType: ShiftType,
    colorConfig: ShiftColorConfig,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .background(ColorProvider(colorConfig.containerColor(shiftType)))
            .cornerRadius(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shiftType.widgetLabel(),
            style = TextStyle(
                color = ColorProvider(colorConfig.onContainerColor(shiftType)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun NotConfiguredContent() {
    Text(
        text = "Open ShiftTrack to set up your schedule",
        style = TextStyle(
            color = WidgetSubLabel,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        ),
    )
}

// ── ShiftType → widget display ───────────────────────────────────────────────

private fun ShiftType.widgetLabel(): String = ShiftColors.label(this).uppercase()

// ── Receiver ─────────────────────────────────────────────────────────────────

class ShiftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShiftWidget()
}
