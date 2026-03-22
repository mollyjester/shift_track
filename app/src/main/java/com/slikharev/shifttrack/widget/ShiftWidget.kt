package com.slikharev.shifttrack.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text

// TODO(Phase 2.11): Implement full Glance widget with 2×2 and 4×2 layouts
class ShiftWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ShiftWidgetContent()
        }
    }
}

@Composable
private fun ShiftWidgetContent() {
    // Placeholder — full implementation in Phase 2.11
    Box(modifier = GlanceModifier.fillMaxSize()) {
        Text(text = "ShiftTrack")
    }
}

class ShiftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShiftWidget()
}
