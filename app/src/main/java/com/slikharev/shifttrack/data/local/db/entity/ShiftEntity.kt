package com.slikharev.shifttrack.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per calendar day per user.
 *
 * The [shiftType] holds [com.slikharev.shifttrack.model.ShiftType].name.
 * [isManualOverride] is true when the user explicitly changed the cadence-calculated type
 * (e.g., a shift swap). Leave days are represented by shiftType = "LEAVE" together with
 * a corresponding row in the [LeaveEntity] table.
 *
 * The unique index on (date, userId) prevents duplicate rows for the same day.
 * [synced] = false means this row has not yet been pushed to Firestore.
 */
@Entity(
    tableName = "shifts",
    indices = [Index(value = ["date", "user_id"], unique = true)],
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "shift_type") val shiftType: String,
    @ColumnInfo(name = "is_manual_override") val isManualOverride: Boolean = false,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "synced") val synced: Boolean = false,
)
