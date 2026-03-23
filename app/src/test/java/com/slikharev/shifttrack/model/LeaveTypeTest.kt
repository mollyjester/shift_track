package com.slikharev.shifttrack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeaveTypeTest {

    @Test
    fun `fromString parses current enum values`() {
        assertEquals(LeaveType.ANNUAL, LeaveType.fromString("ANNUAL"))
        assertEquals(LeaveType.SICK, LeaveType.fromString("SICK"))
        assertEquals(LeaveType.PERSONAL, LeaveType.fromString("PERSONAL"))
        assertEquals(LeaveType.UNPAID, LeaveType.fromString("UNPAID"))
        assertEquals(LeaveType.STUDY, LeaveType.fromString("STUDY"))
    }

    @Test
    fun `fromString maps legacy OTHER to STUDY`() {
        assertEquals(LeaveType.STUDY, LeaveType.fromString("OTHER"))
    }

    @Test
    fun `fromString returns null for unknown values`() {
        assertNull(LeaveType.fromString("INVALID"))
        assertNull(LeaveType.fromString(""))
    }
}
