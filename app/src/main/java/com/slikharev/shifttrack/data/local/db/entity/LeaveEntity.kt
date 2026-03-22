package com.slikharev.shifttrack.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A leave record for a single calendar day.
 *
 * [leaveType] holds [com.slikharev.shifttrack.model.LeaveType].name.
 * [halfDay] = true means only half the day is taken; counted as 0.5 when summing used days.
 * A corresponding [ShiftEntity] row with shiftType = "LEAVE" is also written for UI rendering.
 */
@Entity(
    tableName = "leaves",
    indices = [Index(value = ["date", "user_id"], unique = true)],
)
data class LeaveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "leave_type") val leaveType: String,
    @ColumnInfo(name = "half_day") val halfDay: Boolean = false,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "synced") val synced: Boolean = false,
)
