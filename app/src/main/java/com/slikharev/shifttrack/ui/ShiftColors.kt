package com.slikharev.shifttrack.ui

import androidx.compose.ui.graphics.Color
import com.slikharev.shifttrack.model.ShiftType

// Fixed, theme-independent colors used consistently across Calendar, Dashboard, and Widget.
object ShiftColors {
    val Day = Color(0xFFFFF176.toInt())      // soft amber-yellow
    val DayOnColor = Color(0xFF5F4C00.toInt()) // dark brown text on Day
    val Night = Color(0xFF1A237E.toInt())    // deep navy blue
    val NightOnColor = Color(0xFFD5D9FF.toInt())
    val Rest = Color(0xFF80DEEA.toInt())     // light cyan
    val RestOnColor = Color(0xFF002A2E.toInt())
    val Off = Color(0xFFBDBDBD.toInt())      // neutral grey
    val OffOnColor = Color(0xFF212121.toInt())
    val Leave = Color(0xFFA5D6A7.toInt())    // soft green
    val LeaveOnColor = Color(0xFF003300.toInt())

    fun containerColor(type: ShiftType): Color = when (type) {
        ShiftType.DAY -> Day
        ShiftType.NIGHT -> Night
        ShiftType.REST -> Rest
        ShiftType.OFF -> Off
        ShiftType.LEAVE -> Leave
    }

    fun onContainerColor(type: ShiftType): Color = when (type) {
        ShiftType.DAY -> DayOnColor
        ShiftType.NIGHT -> NightOnColor
        ShiftType.REST -> RestOnColor
        ShiftType.OFF -> OffOnColor
        ShiftType.LEAVE -> LeaveOnColor
    }

    fun label(type: ShiftType): String = when (type) {
        ShiftType.DAY -> "Day"
        ShiftType.NIGHT -> "Night"
        ShiftType.REST -> "Rest"
        ShiftType.OFF -> "Off"
        ShiftType.LEAVE -> "Leave"
    }

    fun emoji(type: ShiftType): String = when (type) {
        ShiftType.DAY -> "☀️"
        ShiftType.NIGHT -> "🌙"
        ShiftType.REST -> "💤"
        ShiftType.OFF -> "🏠"
        ShiftType.LEAVE -> "🌴"
    }
}
