package com.slikharev.shifttrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.slikharev.shifttrack.data.local.db.entity.WorkedHoursOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkedHoursOverrideDao {

    @Query("SELECT * FROM worked_hours_override WHERE user_id = :userId AND date = :date LIMIT 1")
    suspend fun getForDate(userId: String, date: String): WorkedHoursOverrideEntity?

    @Query("SELECT * FROM worked_hours_override WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getForRange(userId: String, startDate: String, endDate: String): Flow<List<WorkedHoursOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkedHoursOverrideEntity): Long

    @Query("DELETE FROM worked_hours_override WHERE user_id = :userId AND date = :date")
    suspend fun deleteByDate(userId: String, date: String)

    @Query("DELETE FROM worked_hours_override WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
