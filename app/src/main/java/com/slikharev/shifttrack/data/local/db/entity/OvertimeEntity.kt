package com.slikharev.shifttrack.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Overtime worked on a given calendar day.
 *
 * [hours] is the number of overtime hours (supports fractional values, e.g. 1.5).
 * Overtime is tracked separately from the shift cadence so normal day/night/rest
 * designations remain intact.
 */
@Entity(
    tableName = "overtime",
    indices = [Index(value = ["date", "user_id"], unique = true)],
)
data class OvertimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "hours") val hours: Float,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "synced") val synced: Boolean = false,
)
