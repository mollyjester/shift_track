package com.slikharev.shifttrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.slikharev.shifttrack.data.local.db.entity.LeaveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeaveDao {

    @Query("SELECT * FROM leaves WHERE user_id = :userId AND date = :date LIMIT 1")
    suspend fun getLeaveForDate(userId: String, date: String): LeaveEntity?

    @Query(
        """SELECT * FROM leaves
           WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate
           ORDER BY date ASC"""
    )
    fun getLeavesForRange(userId: String, startDate: String, endDate: String): Flow<List<LeaveEntity>>

    /**
     * Year summary — pass startDate="YYYY-01-01" and endDate="YYYY-12-31".
     * Used by the leave-balance screen and annual reset.
     */
    @Query(
        """SELECT * FROM leaves
           WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate
           ORDER BY date ASC"""
    )
    fun getLeavesForYear(userId: String, startDate: String, endDate: String): Flow<List<LeaveEntity>>

    /**
     * Counts days taken (half-day = 0.5).  SUM(CASE WHEN half_day THEN 0.5 ELSE 1.0 END)
     * is used so the balance screen never has to load all rows.
     */
    @Query(
        """SELECT COALESCE(SUM(CASE WHEN half_day THEN 0.5 ELSE 1.0 END), 0)
           FROM leaves
           WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate"""
    )
    fun sumLeaveDaysForYear(userId: String, startDate: String, endDate: String): Flow<Float>

    /** Counts days taken for a specific leave type (half-day = 0.5). */
    @Query(
        """SELECT COALESCE(SUM(CASE WHEN half_day THEN 0.5 ELSE 1.0 END), 0)
           FROM leaves
           WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate AND leave_type = :leaveType"""
    )
    fun sumLeaveDaysByType(userId: String, startDate: String, endDate: String, leaveType: String): Flow<Float>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(leave: LeaveEntity): Long

    @Delete
    suspend fun delete(leave: LeaveEntity)

    @Query("DELETE FROM leaves WHERE user_id = :userId AND date = :date")
    suspend fun deleteByDate(userId: String, date: String)

    @Query("SELECT * FROM leaves WHERE user_id = :userId AND synced = 0")
    suspend fun getUnsynced(userId: String): List<LeaveEntity>

    @Query("UPDATE leaves SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM leaves WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
