package com.slikharev.shifttrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.slikharev.shifttrack.data.local.db.entity.PublicHolidayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PublicHolidayDao {

    @Query("SELECT * FROM public_holidays WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getHolidaysForRange(userId: String, startDate: String, endDate: String): Flow<List<PublicHolidayEntity>>

    @Query("SELECT * FROM public_holidays WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getHolidaysForRangeOnce(userId: String, startDate: String, endDate: String): List<PublicHolidayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PublicHolidayEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PublicHolidayEntity>)

    @Delete
    suspend fun delete(entity: PublicHolidayEntity)

    @Query("DELETE FROM public_holidays WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM public_holidays WHERE user_id = :userId AND country_code = :countryCode")
    suspend fun deleteByCountryCode(userId: String, countryCode: String)
}
