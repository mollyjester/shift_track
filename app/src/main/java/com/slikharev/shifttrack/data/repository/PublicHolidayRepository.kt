package com.slikharev.shifttrack.data.repository

import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.data.local.db.dao.PublicHolidayDao
import com.slikharev.shifttrack.data.local.db.entity.PublicHolidayEntity
import com.slikharev.shifttrack.data.remote.HolidayApiService
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicHolidayRepository @Inject constructor(
    private val publicHolidayDao: PublicHolidayDao,
    private val holidayApiService: HolidayApiService,
    private val userSession: UserSession,
) {
    fun getHolidaysForRange(start: LocalDate, end: LocalDate): Flow<List<PublicHolidayEntity>> {
        val userId = userSession.requireUserId()
        return publicHolidayDao.getHolidaysForRange(userId, start.toString(), end.toString())
    }

    suspend fun getHolidaysForRangeOnce(start: LocalDate, end: LocalDate): List<PublicHolidayEntity> {
        val userId = userSession.requireUserId()
        return publicHolidayDao.getHolidaysForRangeOnce(userId, start.toString(), end.toString())
    }

    fun getHolidaysForYear(year: Int): Flow<List<PublicHolidayEntity>> {
        val userId = userSession.requireUserId()
        return publicHolidayDao.getHolidaysForRange(userId, "$year-01-01", "$year-12-31")
    }

    suspend fun fetchAndStoreHolidays(countryCode: String, year: Int): Result<Unit> {
        val userId = userSession.requireUserId()
        return holidayApiService.fetchHolidays(year, countryCode).map { holidays ->
            publicHolidayDao.deleteByCountryCode(userId, countryCode)
            val entities = holidays.map { result ->
                PublicHolidayEntity(
                    date = result.date,
                    name = result.name,
                    countryCode = result.countryCode,
                    userId = userId,
                )
            }
            publicHolidayDao.upsertAll(entities)
        }
    }

    suspend fun addManualHoliday(date: LocalDate, name: String, countryCode: String): PublicHolidayEntity {
        val userId = userSession.requireUserId()
        val entity = PublicHolidayEntity(
            date = date.toString(),
            name = name,
            countryCode = countryCode,
            userId = userId,
        )
        val id = publicHolidayDao.upsert(entity)
        return entity.copy(id = id)
    }

    suspend fun deleteHoliday(entity: PublicHolidayEntity) {
        publicHolidayDao.delete(entity)
    }

    suspend fun updateHoliday(entity: PublicHolidayEntity) {
        publicHolidayDao.upsert(entity)
    }
}
