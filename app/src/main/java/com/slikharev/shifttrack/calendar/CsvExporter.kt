package com.slikharev.shifttrack.calendar

import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class ExportRow(
    val date: LocalDate,
    val shiftType: ShiftType,
    val leaveType: LeaveType?,
    val halfDay: Boolean,
    val overtimeHours: Float,
    val note: String?,
)

object CsvExporter {

    private const val HEADER = "Date,Day of Week,Shift Type,Leave Type,Half Day,Overtime Hours,Note"

    fun generateCsv(rows: List<ExportRow>): String = buildString {
        appendLine(HEADER)
        rows.forEach { row ->
            append(row.date.toString())
            append(',')
            append(row.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
            append(',')
            append(row.shiftType.name)
            append(',')
            append(row.leaveType?.name.orEmpty())
            append(',')
            append(if (row.halfDay) "Yes" else "No")
            append(',')
            append(row.overtimeHours)
            append(',')
            append(escapeCsv(row.note.orEmpty()))
            appendLine()
        }
    }

    /**
     * RFC 4180 escaping: if the field contains a comma, double-quote, or newline,
     * wrap it in double-quotes and double any internal quotes.
     */
    private fun escapeCsv(value: String): String {
        if (value.isEmpty()) return value
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
