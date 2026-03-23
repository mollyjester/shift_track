package com.slikharev.shifttrack.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import com.slikharev.shifttrack.ui.ShiftColors
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

// Fixed colours matching the in-app theme (Color.kt)
private val WidgetBackground = ColorProvider(Color(0xFFF8FDFF))
private val WidgetSubLabel = ColorProvider(Color(0xFF43474E))   // OnSurfaceVariant

/**
 * Home screen widget showing today's shift type (2×2) or the next four days
 * in a row (4×2).
 *
 * Data is read from [AppDataStore] via a Hilt [EntryPoint] at render time.
 * Call [ShiftWidgetUpdater.updateAll] to force a re-render when data changes.
 */
class ShiftWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SmallSize, WideSize),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetState = try {
            val appDataStore = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ShiftWidgetEntryPoint::class.java,
            ).appDataStore()

            val anchorDateStr = appDataStore.anchorDate.firstOrNull()
            val anchorCycleIndex = appDataStore.anchorCycleIndex.first()
            WidgetShiftCalculator.compute(anchorDateStr, anchorCycleIndex)
        } catch (_: Exception) {
            WidgetUiState(isConfigured = false, days = emptyList())
        }

        provideContent {
            ShiftWidgetContent(widgetState)
        }
    }

    companion object {
        val SmallSize = DpSize(110.dp, 110.dp)  // 2×2
        val WideSize = DpSize(230.dp, 110.dp)   // 4×2
    }
}

@Composable
private fun ShiftWidgetContent(state: WidgetUiState) {
    val size = LocalSize.current
    if (size.width >= ShiftWidget.WideSize.width) {
        WideContent(state)
    } else {
        SmallContent(state)
    }
}

// ── Small layout (2×2) ───────────────────────────────────────────────────────

@Composable
private fun SmallContent(state: WidgetUiState) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!state.isConfigured || state.days.isEmpty()) {
            NotConfiguredContent()
        } else {
            val today = state.days.first()
            Column(
                modifier = GlanceModifier.fillMaxSize(),
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
                    modifier = GlanceModifier.fillMaxWidth().height(44.dp),
                )
            }
        }
    }
}

// ── Wide layout (4×2) ────────────────────────────────────────────────────────

/** Fixed width per day column; chosen so 4 columns + 3×6dp gaps fit in 230dp. */
private val DayColumnWidth = 50.dp

@Composable
private fun WideContent(state: WidgetUiState) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!state.isConfigured || state.days.isEmpty()) {
            NotConfiguredContent()
        } else {
            val days = state.days.take(4)
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DayColumn(dayInfo = days[0])
                if (days.size > 1) {
                    Spacer(GlanceModifier.width(6.dp))
                    DayColumn(dayInfo = days[1])
                }
                if (days.size > 2) {
                    Spacer(GlanceModifier.width(6.dp))
                    DayColumn(dayInfo = days[2])
                }
                if (days.size > 3) {
                    Spacer(GlanceModifier.width(6.dp))
                    DayColumn(dayInfo = days[3])
                }
            }
        }
    }
}

@Composable
private fun DayColumn(dayInfo: WidgetDayInfo) {
    Column(
        modifier = GlanceModifier.width(DayColumnWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = dayInfo.label,
            style = TextStyle(
                color = WidgetSubLabel,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        ShiftChip(
            shiftType = dayInfo.shiftType,
            modifier = GlanceModifier.fillMaxWidth().height(40.dp),
        )
    }
}

// ── Shared composables ───────────────────────────────────────────────────────

@Composable
private fun ShiftChip(shiftType: ShiftType, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .background(ColorProvider(shiftType.widgetColor()))
            .cornerRadius(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shiftType.widgetLabel(),
            style = TextStyle(
                color = ColorProvider(shiftType.widgetOnColor()),
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

private fun ShiftType.widgetColor(): Color = ShiftColors.containerColor(this)

private fun ShiftType.widgetOnColor(): Color = ShiftColors.onContainerColor(this)

private fun ShiftType.widgetLabel(): String = ShiftColors.label(this).uppercase()

// ── Receiver ─────────────────────────────────────────────────────────────────

class ShiftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShiftWidget()
}
