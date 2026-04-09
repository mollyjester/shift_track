package com.slikharev.shifttrack.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "worked_hours_override",
    indices = [Index(value = ["date", "user_id"], unique = true)],
)
data class WorkedHoursOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "hours") val hours: Float,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "synced") val synced: Boolean = false,
)
