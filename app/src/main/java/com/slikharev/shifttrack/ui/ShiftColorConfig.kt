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
        val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
        return if (luminance > 0.5f) Color(0xFF212121.toInt()) else Color(0xFFEEEEEE.toInt())
    }
}

val LocalShiftColors = staticCompositionLocalOf { ShiftColorConfig() }
