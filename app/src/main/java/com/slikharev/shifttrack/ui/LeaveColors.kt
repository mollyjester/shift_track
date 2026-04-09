package com.slikharev.shifttrack.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.slikharev.shifttrack.model.LeaveType

/** Light grey background used for leave days in calendar and widget. */
val LeaveGrey = Color(0xFFE0E0E0.toInt())

/** Dark-mode counterpart for [LeaveGrey]. */
val LeaveGreyDark = Color(0xFF3A3A3A.toInt())

/** Returns the theme-aware leave-day grey. */
@Composable
fun leaveGreyColor(): Color = if (isSystemInDarkTheme()) LeaveGreyDark else LeaveGrey

/** Default (static) color palette for leave types. */
object LeaveColors {
    val Annual = Color(0xFF66BB6A.toInt())   // green
    val Sick = Color(0xFFEF5350.toInt())     // red
    val Personal = Color(0xFF42A5F5.toInt()) // blue
    val Unpaid = Color(0xFFFF7043.toInt())   // orange
    val Study = Color(0xFFAB47BC.toInt())    // purple

    fun color(type: LeaveType): Color = when (type) {
        LeaveType.ANNUAL -> Annual
        LeaveType.SICK -> Sick
        LeaveType.PERSONAL -> Personal
        LeaveType.UNPAID -> Unpaid
        LeaveType.STUDY -> Study
    }

    fun label(type: LeaveType): String = when (type) {
        LeaveType.ANNUAL -> "Annual"
        LeaveType.SICK -> "Sick"
        LeaveType.PERSONAL -> "Personal"
        LeaveType.UNPAID -> "Unpaid"
        LeaveType.STUDY -> "Study"
    }
}

/**
 * Runtime-configurable color scheme for leave types.
 * Read via [LocalLeaveColors] in @Composable scope.
 */
data class LeaveColorConfig(
    val annualColor: Color = LeaveColors.Annual,
    val sickColor: Color = LeaveColors.Sick,
    val personalColor: Color = LeaveColors.Personal,
    val unpaidColor: Color = LeaveColors.Unpaid,
    val studyColor: Color = LeaveColors.Study,
) {
    fun color(type: LeaveType): Color = when (type) {
        LeaveType.ANNUAL -> annualColor
        LeaveType.SICK -> sickColor
        LeaveType.PERSONAL -> personalColor
        LeaveType.UNPAID -> unpaidColor
        LeaveType.STUDY -> studyColor
    }
}

val LocalLeaveColors = staticCompositionLocalOf { LeaveColorConfig() }
