package com.slikharev.shifttrack.model

/**
 * The five shift types in the rotating cadence: DAY → DAY → NIGHT → REST → OFF.
 * LEAVE is a special type applied when the user takes a leave day (tracked separately
 * in the leaves table, but stored here too so the calendar renders it correctly).
 */
enum class ShiftType {
    DAY,
    NIGHT,
    REST,
    OFF,
    LEAVE,
}
