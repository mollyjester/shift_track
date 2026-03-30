package com.slikharev.shifttrack.alarm

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-day alarm override that takes priority over the global defaults
 * configured in Settings → Experimental Features.
 *
 * When the user taps the evening notification and enables "Custom alarms for this day",
 * the custom parameters are stored here and synced to Firestore.
 *
 * Unique index on (date, user_id) ensures one override per day per user.
 */
// [EXPERIMENTAL:ALARM]
@Entity(
    tableName = "alarm_overrides",
    indices = [Index(value = ["date", "user_id"], unique = true)],
)
data class AlarmOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "alarm_count") val alarmCount: Int,
    @ColumnInfo(name = "interval_minutes") val intervalMinutes: Int,
    @ColumnInfo(name = "first_alarm_time") val firstAlarmTime: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "synced") val synced: Boolean = false,
)
