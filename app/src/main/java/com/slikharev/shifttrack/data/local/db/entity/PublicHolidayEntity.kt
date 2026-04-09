package com.slikharev.shifttrack.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "public_holidays",
    indices = [Index(value = ["date", "user_id"], unique = true)],
)
data class PublicHolidayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "country_code") val countryCode: String,
    @ColumnInfo(name = "user_id") val userId: String,
)
