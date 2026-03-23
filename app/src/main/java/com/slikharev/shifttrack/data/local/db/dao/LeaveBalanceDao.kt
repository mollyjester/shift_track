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

    @Query("SELECT * FROM leave_balance WHERE user_id = :userId AND year = :year AND leave_type = :leaveType LIMIT 1")
    suspend fun getBalanceForYearAndType(userId: String, year: Int, leaveType: String): LeaveBalanceEntity?

    @Query("SELECT * FROM leave_balance WHERE user_id = :userId AND year = :year AND leave_type = :leaveType LIMIT 1")
    fun observeBalanceForYearAndType(userId: String, year: Int, leaveType: String): Flow<LeaveBalanceEntity?>

    @Query("SELECT * FROM leave_balance WHERE user_id = :userId AND year = :year ORDER BY leave_type ASC")
    fun observeAllBalancesForYear(userId: String, year: Int): Flow<List<LeaveBalanceEntity>>

    @Query("SELECT * FROM leave_balance WHERE user_id = :userId AND year = :year ORDER BY leave_type ASC")
    suspend fun getAllBalancesForYear(userId: String, year: Int): List<LeaveBalanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(balance: LeaveBalanceEntity): Long

    @Update
    suspend fun update(balance: LeaveBalanceEntity)

    @Delete
    suspend fun delete(balance: LeaveBalanceEntity)

    @Query("DELETE FROM leave_balance WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
