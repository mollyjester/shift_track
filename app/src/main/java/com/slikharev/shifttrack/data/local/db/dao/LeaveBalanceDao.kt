package com.slikharev.shifttrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeaveBalanceDao {

    @Query("SELECT * FROM leave_balance WHERE user_id = :userId AND year = :year LIMIT 1")
    suspend fun getBalanceForYear(userId: String, year: Int): LeaveBalanceEntity?

    @Query("SELECT * FROM leave_balance WHERE user_id = :userId AND year = :year LIMIT 1")
    fun observeBalanceForYear(userId: String, year: Int): Flow<LeaveBalanceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(balance: LeaveBalanceEntity): Long

    @Update
    suspend fun update(balance: LeaveBalanceEntity)

    @Delete
    suspend fun delete(balance: LeaveBalanceEntity)

    @Query("DELETE FROM leave_balance WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
