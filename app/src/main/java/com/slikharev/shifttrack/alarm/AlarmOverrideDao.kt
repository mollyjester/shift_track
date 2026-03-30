package com.slikharev.shifttrack.alarm

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Data access object for per-day alarm overrides.
 */
// [EXPERIMENTAL:ALARM]
@Dao
interface AlarmOverrideDao {

    @Query("SELECT * FROM alarm_overrides WHERE user_id = :userId AND date = :date LIMIT 1")
    suspend fun getOverrideForDate(userId: String, date: String): AlarmOverrideEntity?

    @Upsert
    suspend fun upsert(entity: AlarmOverrideEntity)

    @Query("DELETE FROM alarm_overrides WHERE user_id = :userId AND date = :date")
    suspend fun deleteForDate(userId: String, date: String)

    @Query("SELECT * FROM alarm_overrides WHERE user_id = :userId AND synced = 0")
    suspend fun getUnsynced(userId: String): List<AlarmOverrideEntity>

    @Query("UPDATE alarm_overrides SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM alarm_overrides WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
