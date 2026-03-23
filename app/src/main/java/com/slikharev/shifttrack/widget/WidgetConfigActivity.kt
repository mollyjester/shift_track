package com.slikharev.shifttrack.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.ui.theme.ShiftTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var appDataStore: AppDataStore
    @Inject lateinit var widgetUpdater: ShiftWidgetUpdater

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default result = CANCELED so the widget isn't added if the user backs out
        setResult(RESULT_CANCELED)

        // Extract widget ID from the launching intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            ShiftTrackTheme {
                WidgetConfigScreen(
                    appDataStore = appDataStore,
                    widgetUpdater = widgetUpdater,
                    onDone = { confirmAndFinish() },
                )
            }
        }
    }

    private fun confirmAndFinish() {
        // Update the widget with current settings
        runBlocking { widgetUpdater.updateAll() }

        // Return OK with the widget ID so the launcher keeps the widget
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    appDataStore: AppDataStore,
    widgetUpdater: ShiftWidgetUpdater,
    onDone: () -> Unit,
) {
    val bgColorArgb by appDataStore.widgetBgColor.collectAsState(initial = null)
    val transparency by appDataStore.widgetTransparency.collectAsState(initial = AppDataStore.DEFAULT_WIDGET_TRANSPARENCY)
    val dayCount by appDataStore.widgetDayCount.collectAsState(initial = AppDataStore.DEFAULT_WIDGET_DAY_COUNT)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Widget Settings") })
        },
        bottomBar = {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Done")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            WidgetConfigContent(
                bgColorArgb = bgColorArgb,
                transparency = transparency,
                dayCount = dayCount,
                onBgColorChange = { argb ->
                    runBlocking {
                        appDataStore.setWidgetBgColor(argb)
                        widgetUpdater.updateAll()
                    }
                },
                onTransparencyChange = { alpha ->
                    runBlocking {
                        appDataStore.setWidgetTransparency(alpha)
                        widgetUpdater.updateAll()
                    }
                },
                onDayCountChange = { count ->
                    runBlocking {
                        appDataStore.setWidgetDayCount(count)
                        widgetUpdater.updateAll()
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WidgetConfigContent(
    bgColorArgb: Long?,
    transparency: Float,
    dayCount: Int,
    onBgColorChange: (Long) -> Unit,
    onTransparencyChange: (Float) -> Unit,
    onDayCountChange: (Int) -> Unit,
) {
    val defaultBg = Color(0xFFF8FDFF.toInt())
    val currentBgColor = bgColorArgb?.let { Color(it.toInt()) } ?: defaultBg

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Background color ─────────────────────────────────────────────────
        Text("Background color", style = MaterialTheme.typography.bodyMedium)
        var bgExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { bgExpanded = !bgExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(currentBgColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(
                text = if (bgExpanded) "▲" else "▼",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (bgExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            HsvColorPicker(
                initialColor = currentBgColor,
                onColorSelected = { color -> onBgColorChange(color.toArgb().toLong()) },
                defaultColor = defaultBg,
                onReset = { onBgColorChange(defaultBg.toArgb().toLong()) },
            )
        }

        // ── Transparency ─────────────────────────────────────────────────────
        Text("Transparency", style = MaterialTheme.typography.bodyMedium)
        var localTransparency by remember(transparency) { mutableFloatStateOf(transparency) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${"%.0f".format(localTransparency * 100)}%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(40.dp),
            )
            Slider(
                value = localTransparency,
                onValueChange = { localTransparency = it },
                onValueChangeFinished = { onTransparencyChange(localTransparency) },
                valueRange = 0f..1f,
                steps = 9,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Days to show ─────────────────────────────────────────────────────
        Text("Days to show", style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { if (dayCount > AppDataStore.MIN_WIDGET_DAYS) onDayCountChange(dayCount - 1) },
                enabled = dayCount > AppDataStore.MIN_WIDGET_DAYS,
            ) { Text("−") }
            Text("$dayCount", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(
                onClick = { if (dayCount < AppDataStore.MAX_WIDGET_DAYS) onDayCountChange(dayCount + 1) },
                enabled = dayCount < AppDataStore.MAX_WIDGET_DAYS,
            ) { Text("+") }
        }
    }
}

// ── HSV Color Picker ─────────────────────────────────────────────────────────

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv
}

private fun hsvToColor(hsv: FloatArray): Color {
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun HsvColorPicker(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    defaultColor: Color,
    onReset: () -> Unit,
) {
    val initHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember(initialColor) { mutableFloatStateOf(initHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initHsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(initHsv[2]) }

    val previewColor = remember(hue, saturation, value) {
        hsvToColor(floatArrayOf(hue, saturation, value))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(previewColor)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        )

        Text("Hue", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = hue,
            onValueChange = { hue = it },
            onValueChangeFinished = { onColorSelected(hsvToColor(floatArrayOf(hue, saturation, value))) },
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Saturation", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            onValueChangeFinished = { onColorSelected(hsvToColor(floatArrayOf(hue, saturation, value))) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Brightness", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onColorSelected(hsvToColor(floatArrayOf(hue, saturation, value))) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reset to default")
        }
    }
}
