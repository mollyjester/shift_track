package com.slikharev.shifttrack.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.ShiftColorConfig
import com.slikharev.shifttrack.ui.ShiftColors
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.PrefsKeys
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WidgetSubLabel = ColorProvider(Color(0xFF43474E.toInt()))   // OnSurfaceVariant

/**
 * Home screen widget showing today's shift type (2×2) or the next four days
 * in a row (4×2).
 *
 * Data is read from [AppDataStore] via a Hilt [EntryPoint] at render time.
 * Call [ShiftWidgetUpdater.updateAll] to force a re-render when data changes.
 */
class ShiftWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SmallSize, WideSize, ExtraWideSize),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appDataStore = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                ShiftWidgetEntryPoint::class.java,
            ).appDataStore()
        } catch (_: Exception) {
            null
        }

        val widgetState = try {
            val anchorDateStr = appDataStore!!.anchorDate.firstOrNull()
            val anchorCycleIndex = appDataStore.anchorCycleIndex.first()
            val dayCount = appDataStore.widgetDayCount.first()
            WidgetShiftCalculator.compute(anchorDateStr, anchorCycleIndex, dayCount = dayCount)
        } catch (_: Exception) {
            WidgetUiState(isConfigured = false, days = emptyList())
        }

        val bgColorArgb = appDataStore?.widgetBgColor?.firstOrNull()
        val transparency = appDataStore?.widgetTransparency?.first() ?: AppDataStore.DEFAULT_WIDGET_TRANSPARENCY
        val dayCount = appDataStore?.widgetDayCount?.first() ?: AppDataStore.DEFAULT_WIDGET_DAY_COUNT

        val bgColor = if (bgColorArgb != null) {
            Color(bgColorArgb.toInt()).copy(alpha = transparency)
        } else {
            Color(0xFFF8FDFF.toInt()).copy(alpha = transparency)
        }

        // Read user-configured shift-type colors
        val colorConfig = if (appDataStore != null) {
            val cDay = appDataStore.colorDay.firstOrNull()
            val cNight = appDataStore.colorNight.firstOrNull()
            val cRest = appDataStore.colorRest.firstOrNull()
            val cOff = appDataStore.colorOff.firstOrNull()
            val cLeave = appDataStore.colorLeave.firstOrNull()
            ShiftColorConfig(
                dayColor = cDay?.let { Color(it.toInt()) } ?: ShiftColors.Day,
                nightColor = cNight?.let { Color(it.toInt()) } ?: ShiftColors.Night,
                restColor = cRest?.let { Color(it.toInt()) } ?: ShiftColors.Rest,
                offColor = cOff?.let { Color(it.toInt()) } ?: ShiftColors.Off,
                leaveColor = cLeave?.let { Color(it.toInt()) } ?: ShiftColors.Leave,
            )
        } else {
            ShiftColorConfig()
        }

        provideContent {
            ShiftWidgetContent(widgetState, bgColor, dayCount, colorConfig)
        }
    }

    companion object {
        val SmallSize = DpSize(110.dp, 110.dp)   // 2×2
        val WideSize = DpSize(230.dp, 110.dp)    // 4×2
        val ExtraWideSize = DpSize(350.dp, 110.dp) // 6–7×2
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
    if (size.width >= ShiftWidget.WideSize.width) {
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

// ── Wide layout (4×2+) ────────────────────────────────────────────────────────────────────

private val GapWidth = 6.dp

@Composable
private fun WideContent(
    state: WidgetUiState,
    bgColor: Color,
    dayCount: Int,
    colorConfig: ShiftColorConfig,
) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!state.isConfigured || state.days.isEmpty()) {
            NotConfiguredContent()
        } else {
            val days = state.days.take(dayCount)
            val totalGaps = GapWidth * (days.size - 1)
            val columnWidth = (size.width - 16.dp - totalGaps) / days.size
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                days.forEachIndexed { index, dayInfo ->
                    if (index > 0) {
                        Spacer(GlanceModifier.width(GapWidth))
                    }
                    DayColumn(
                        dayInfo = dayInfo,
                        colorConfig = colorConfig,
                        columnWidth = columnWidth,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    dayInfo: WidgetDayInfo,
    colorConfig: ShiftColorConfig,
    columnWidth: Dp,
) {
    val labelColor = if (dayInfo.isToday) {
        ColorProvider(colorConfig.onContainerColor(dayInfo.shiftType))
    } else {
        WidgetSubLabel
    }
    Column(
        modifier = GlanceModifier
            .width(columnWidth)
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
