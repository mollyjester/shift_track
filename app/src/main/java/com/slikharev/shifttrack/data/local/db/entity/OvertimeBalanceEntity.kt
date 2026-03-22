package com.slikharev.shifttrack.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks the user's accumulated and compensated overtime hours for a given [year].
 *
 * [totalHours] is recomputed from the [OvertimeEntity] table but cached here for fast reads.
 * [compensatedHours] is the portion that has been paid out or taken as time-off.
 */
@Entity(
    tableName = "overtime_balance",
    indices = [Index(value = ["year", "user_id"], unique = true)],
)
data class OvertimeBalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "total_hours") val totalHours: Float = 0f,
    @ColumnInfo(name = "compensated_hours") val compensatedHours: Float = 0f,
    @ColumnInfo(name = "user_id") val userId: String,
)
