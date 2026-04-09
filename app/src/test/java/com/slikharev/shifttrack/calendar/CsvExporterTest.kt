package com.slikharev.shifttrack.calendar

import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvExporterTest {

    @Test
    fun `empty list produces header only`() {
        val csv = CsvExporter.generateCsv(emptyList())
        val lines = csv.trimEnd().lines()
        assertEquals(1, lines.size)
        assertEquals("Date,Day of Week,Shift Type,Leave Type,Half Day,Overtime Hours,Note", lines[0])
    }

    @Test
    fun `single row formats all fields correctly`() {
        val row = ExportRow(
            date = LocalDate.of(2026, 4, 6), // Monday
            shiftType = ShiftType.DAY,
            leaveType = null,
            halfDay = false,
            overtimeHours = 0f,
            note = null,
        )
        val csv = CsvExporter.generateCsv(listOf(row))
        val lines = csv.trimEnd().lines()
        assertEquals(2, lines.size)
        assertEquals("2026-04-06,Mon,DAY,,No,0.0,", lines[1])
    }

    @Test
    fun `leave type and half day render correctly`() {
        val row = ExportRow(
            date = LocalDate.of(2026, 4, 7), // Tuesday
            shiftType = ShiftType.LEAVE,
            leaveType = LeaveType.ANNUAL,
            halfDay = true,
            overtimeHours = 0f,
            note = null,
        )
        val csv = CsvExporter.generateCsv(listOf(row))
        val dataLine = csv.trimEnd().lines()[1]
        assertEquals("2026-04-07,Tue,LEAVE,ANNUAL,Yes,0.0,", dataLine)
    }

    @Test
    fun `overtime hours render correctly`() {
        val row = ExportRow(
            date = LocalDate.of(2026, 4, 8), // Wednesday
            shiftType = ShiftType.NIGHT,
            leaveType = null,
            halfDay = false,
            overtimeHours = 2.5f,
            note = null,
        )
        val csv = CsvExporter.generateCsv(listOf(row))
        val dataLine = csv.trimEnd().lines()[1]
        assertEquals("2026-04-08,Wed,NIGHT,,No,2.5,", dataLine)
    }

    @Test
    fun `note with comma is quoted`() {
        val row = ExportRow(
            date = LocalDate.of(2026, 4, 9),
            shiftType = ShiftType.REST,
            leaveType = null,
            halfDay = false,
            overtimeHours = 0f,
            note = "Called in, left early",
        )
        val csv = CsvExporter.generateCsv(listOf(row))
        val dataLine = csv.trimEnd().lines()[1]
        assertTrue(dataLine.endsWith("\"Called in, left early\""))
    }

    @Test
    fun `note with double quotes is escaped`() {
        val row = ExportRow(
            date = LocalDate.of(2026, 4, 10),
            shiftType = ShiftType.OFF,
            leaveType = null,
            halfDay = false,
            overtimeHours = 0f,
            note = "Said \"hello\" to manager",
        )
        val csv = CsvExporter.generateCsv(listOf(row))
        val dataLine = csv.trimEnd().lines()[1]
        assertTrue(dataLine.endsWith("\"Said \"\"hello\"\" to manager\""))
    }

    @Test
    fun `note with newline is quoted`() {
        val row = ExportRow(
            date = LocalDate.of(2026, 4, 11),
            shiftType = ShiftType.DAY,
            leaveType = null,
            halfDay = false,
            overtimeHours = 0f,
            note = "Line one\nLine two",
        )
        val csv = CsvExporter.generateCsv(listOf(row))
        // The note field should be wrapped in quotes
        assertTrue(csv.contains("\"Line one\nLine two\""))
    }

    @Test
    fun `all shift types render correctly`() {
        val rows = ShiftType.entries.mapIndexed { i, type ->
            ExportRow(
                date = LocalDate.of(2026, 4, 6).plusDays(i.toLong()),
                shiftType = type,
                leaveType = null,
                halfDay = false,
                overtimeHours = 0f,
                note = null,
            )
        }
        val csv = CsvExporter.generateCsv(rows)
        val dataLines = csv.trimEnd().lines().drop(1)
        assertEquals(ShiftType.entries.size, dataLines.size)
        ShiftType.entries.forEachIndexed { i, type ->
            assertTrue(dataLines[i].contains(",${type.name},"))
        }
    }

    @Test
    fun `all leave types render correctly`() {
        val rows = LeaveType.entries.mapIndexed { i, type ->
            ExportRow(
                date = LocalDate.of(2026, 4, 6).plusDays(i.toLong()),
                shiftType = ShiftType.LEAVE,
                leaveType = type,
                halfDay = false,
                overtimeHours = 0f,
                note = null,
            )
        }
        val csv = CsvExporter.generateCsv(rows)
        val dataLines = csv.trimEnd().lines().drop(1)
        assertEquals(LeaveType.entries.size, dataLines.size)
        LeaveType.entries.forEachIndexed { i, type ->
            assertTrue(dataLines[i].contains(",${type.name},"))
        }
    }

    @Test
    fun `multiple rows produce correct line count`() {
        val rows = (0L until 31).map { offset ->
            ExportRow(
                date = LocalDate.of(2026, 4, 1).plusDays(offset),
                shiftType = ShiftType.DAY,
                leaveType = null,
                halfDay = false,
                overtimeHours = 0f,
                note = null,
            )
        }
        val csv = CsvExporter.generateCsv(rows)
        val lines = csv.trimEnd().lines()
        assertEquals(32, lines.size) // 1 header + 31 data rows
    }

    @Test
    fun `plain note without special characters is not quoted`() {
        val row = ExportRow(
            date = LocalDate.of(2026, 4, 12),
            shiftType = ShiftType.DAY,
            leaveType = null,
            halfDay = false,
            overtimeHours = 0f,
            note = "Normal note here",
        )
        val csv = CsvExporter.generateCsv(listOf(row))
        val dataLine = csv.trimEnd().lines()[1]
        assertTrue(dataLine.endsWith("Normal note here"))
    }
}
