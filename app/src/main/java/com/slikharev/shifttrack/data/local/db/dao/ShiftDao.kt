package com.slikharev.shifttrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.slikharev.shifttrack.data.local.db.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {

    /** Single-day lookup — used when rendering a day cell or calculating cadence overrides. */
    @Query("SELECT * FROM shifts WHERE user_id = :userId AND date = :date LIMIT 1")
    suspend fun getShiftForDate(userId: String, date: String): ShiftEntity?

    /** Continuous range stream — drives the calendar UI. */
    @Query(
        """SELECT * FROM shifts
           WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate
           ORDER BY date ASC"""
    )
    fun getShiftsForRange(userId: String, startDate: String, endDate: String): Flow<List<ShiftEntity>>

    /**
     * Upsert: inserts if absent, replaces if the (date, user_id) unique key already exists.
     * Used both by the cadence engine (batch pre-populate) and manual day edits.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shift: ShiftEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(shifts: List<ShiftEntity>)

    @Delete
    suspend fun delete(shift: ShiftEntity)

    @Query("DELETE FROM shifts WHERE user_id = :userId AND date = :date")
    suspend fun deleteByDate(userId: String, date: String)

    /** Returns rows not yet pushed to Firestore — consumed by the sync worker. */
    @Query("SELECT * FROM shifts WHERE user_id = :userId AND synced = 0")
    suspend fun getUnsynced(userId: String): List<ShiftEntity>

    /** Marks a batch of rows as synced after a successful Firestore write. */
    @Query("UPDATE shifts SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}
