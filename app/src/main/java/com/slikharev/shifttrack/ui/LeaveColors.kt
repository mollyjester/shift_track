package com.slikharev.shifttrack.ui

import androidx.compose.ui.graphics.Color
import com.slikharev.shifttrack.model.LeaveType

/** Fixed color palette for leave types, used in calendar legend and day cells. */
object LeaveColors {
    val Annual = Color(0xFF66BB6A.toInt())   // green
    val Sick = Color(0xFFEF5350.toInt())     // red
    val Personal = Color(0xFF42A5F5.toInt()) // blue
    val Unpaid = Color(0xFFFF7043.toInt())   // orange
    val Other = Color(0xFFAB47BC.toInt())    // purple

    fun color(type: LeaveType): Color = when (type) {
        LeaveType.ANNUAL -> Annual
        LeaveType.SICK -> Sick
        LeaveType.PERSONAL -> Personal
        LeaveType.UNPAID -> Unpaid
        LeaveType.OTHER -> Other
    }

    fun label(type: LeaveType): String = when (type) {
        LeaveType.ANNUAL -> "Annual"
        LeaveType.SICK -> "Sick"
        LeaveType.PERSONAL -> "Personal"
        LeaveType.UNPAID -> "Unpaid"
        LeaveType.OTHER -> "Other"
    }
}
