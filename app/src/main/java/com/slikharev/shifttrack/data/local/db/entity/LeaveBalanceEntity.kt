package com.slikharev.shifttrack.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the user's annual leave entitlement and consumed days for a given [year].
 *
 * [totalDays] is set by the employer/user at the start of the year (or carried over).
 * [usedDays] is recomputed from the [LeaveEntity] table but also stored here so
 * the widget and summary screens can read it without a join.
 */
@Entity(
    tableName = "leave_balance",
    indices = [Index(value = ["year", "user_id"], unique = true)],
)
data class LeaveBalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "total_days") val totalDays: Float,
    @ColumnInfo(name = "used_days") val usedDays: Float = 0f,
    @ColumnInfo(name = "user_id") val userId: String,
)
