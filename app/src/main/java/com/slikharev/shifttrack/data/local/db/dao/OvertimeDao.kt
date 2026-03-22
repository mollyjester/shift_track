package com.slikharev.shifttrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OvertimeDao {

    @Query("SELECT * FROM overtime WHERE user_id = :userId AND date = :date LIMIT 1")
    suspend fun getOvertimeForDate(userId: String, date: String): OvertimeEntity?

    @Query(
        """SELECT * FROM overtime
           WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate
           ORDER BY date ASC"""
    )
    fun getOvertimeForRange(userId: String, startDate: String, endDate: String): Flow<List<OvertimeEntity>>

    /** Running total of overtime hours in a year — pass "YYYY-01-01"/"YYYY-12-31". */
    @Query(
        """SELECT COALESCE(SUM(hours), 0)
           FROM overtime
           WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate"""
    )
    fun sumOvertimeHoursForYear(userId: String, startDate: String, endDate: String): Flow<Float>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(overtime: OvertimeEntity): Long

    @Delete
    suspend fun delete(overtime: OvertimeEntity)

    @Query("DELETE FROM overtime WHERE user_id = :userId AND date = :date")
    suspend fun deleteByDate(userId: String, date: String)

    @Query("SELECT * FROM overtime WHERE user_id = :userId AND synced = 0")
    suspend fun getUnsynced(userId: String): List<OvertimeEntity>

    @Query("UPDATE overtime SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}
