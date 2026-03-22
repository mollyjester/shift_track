package com.slikharev.shifttrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OvertimeBalanceDao {

    @Query("SELECT * FROM overtime_balance WHERE user_id = :userId AND year = :year LIMIT 1")
    suspend fun getBalanceForYear(userId: String, year: Int): OvertimeBalanceEntity?

    @Query("SELECT * FROM overtime_balance WHERE user_id = :userId AND year = :year LIMIT 1")
    fun observeBalanceForYear(userId: String, year: Int): Flow<OvertimeBalanceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(balance: OvertimeBalanceEntity): Long

    @Update
    suspend fun update(balance: OvertimeBalanceEntity)

    @Delete
    suspend fun delete(balance: OvertimeBalanceEntity)
}
