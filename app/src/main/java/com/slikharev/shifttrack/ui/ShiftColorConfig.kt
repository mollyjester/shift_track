package com.slikharev.shifttrack.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.slikharev.shifttrack.model.ShiftType

/**
 * Runtime-configurable color scheme for shift types.
 * Read via [LocalShiftColors] in @Composable scope.
 * Falls back to [ShiftColors] defaults.
 */
data class ShiftColorConfig(
    val dayColor: Color = ShiftColors.Day,
    val nightColor: Color = ShiftColors.Night,
    val restColor: Color = ShiftColors.Rest,
    val offColor: Color = ShiftColors.Off,
    val leaveColor: Color = ShiftColors.Leave,
) {
    fun containerColor(type: ShiftType): Color = when (type) {
        ShiftType.DAY -> dayColor
        ShiftType.NIGHT -> nightColor
        ShiftType.REST -> restColor
        ShiftType.OFF -> offColor
        ShiftType.LEAVE -> leaveColor
    }

    fun onContainerColor(type: ShiftType): Color {
        val bg = containerColor(type)
        // Compute readable text color: dark text on light backgrounds, light text on dark.
        val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
        return if (luminance > 0.5f) Color(0xFF212121) else Color(0xFFEEEEEE)
    }
}

val LocalShiftColors = staticCompositionLocalOf { ShiftColorConfig() }

/** Preset color choices for the color picker. */
val COLOR_PRESETS: List<Color> = listOf(
    Color(0xFFFFF176), // yellow
    Color(0xFFFFCC80), // orange
    Color(0xFFEF9A9A), // red
    Color(0xFFCE93D8), // purple
    Color(0xFF90CAF9), // blue
    Color(0xFF80DEEA), // cyan
    Color(0xFFA5D6A7), // green
    Color(0xFFBDBDBD), // grey
    Color(0xFF1A237E), // navy
    Color(0xFF004D40), // teal dark
    Color(0xFF3E2723), // brown dark
    Color(0xFF263238), // blue-grey dark
)
