package com.slikharev.shifttrack.data.repository

import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the user's overtime entries and yearly overtime-balance.
 *
 * All writes are local-first (Room); Firestore sync is handled by
 * [com.slikharev.shifttrack.sync.SyncWorker]. After every mutation the
 * yearly [OvertimeBalanceEntity.totalHours] is recomputed via a one-shot
 * suspend query and the balance row is created if it didn't exist yet.
 *
 * Note: [addOvertime] requires [hours] > 0 and will throw
 * [IllegalArgumentException] otherwise.
 */
@Singleton
class OvertimeRepository @Inject constructor(
    private val overtimeDao: OvertimeDao,
    private val overtimeBalanceDao: OvertimeBalanceDao,
    private val userSession: UserSession,
) {
    private val uid get() = userSession.currentUserId.orEmpty()

    /** Reactive balance for [year] — null until the user records any overtime or sets a balance. */
    fun observeBalanceForYear(year: Int): Flow<OvertimeBalanceEntity?> =
        overtimeBalanceDao.observeBalanceForYear(uid, year)

    /** Stream of all overtime entries in the given date range. */
    fun getOvertimeForRange(startDate: LocalDate, endDate: LocalDate): Flow<List<OvertimeEntity>> =
        overtimeDao.getOvertimeForRange(uid, startDate.toString(), endDate.toString())

    /** One-shot lookup for a specific date. */
    suspend fun getOvertimeForDate(date: LocalDate): OvertimeEntity? =
        overtimeDao.getOvertimeForDate(uid, date.toString())

    /**
     * Records (or replaces) overtime for [date].
     * Also recalculates the yearly [OvertimeBalanceEntity.totalHours].
     */
    suspend fun addOvertime(date: LocalDate, hours: Float, note: String? = null) {
        require(hours > 0f) { "Overtime hours must be positive" }
        overtimeDao.upsert(
            OvertimeEntity(
                date = date.toString(),
                hours = hours,
                note = note,
                userId = uid,
                synced = false,
            ),
        )
        refreshTotalHours(date.year)
    }

    /** Removes overtime for [date] and recalculates the yearly total. */
    suspend fun removeOvertime(date: LocalDate) {
        overtimeDao.deleteByDate(uid, date.toString())
        refreshTotalHours(date.year)
    }

    /**
     * Re-computes [OvertimeBalanceEntity.totalHours] from the overtime table for the given year
     * and writes it back.  Creates the row if it doesn't exist yet.
     */
    private suspend fun refreshTotalHours(year: Int) {
        val start = LocalDate.of(year, 1, 1).toString()
        val end = LocalDate.of(year, 12, 31).toString()
        val totalHours = overtimeDao.sumOvertimeHoursOnce(uid, start, end)
        val existing = overtimeBalanceDao.getBalanceForYear(uid, year)
        if (existing == null) {
            overtimeBalanceDao.upsert(
                OvertimeBalanceEntity(year = year, totalHours = totalHours, userId = uid),
            )
        } else {
            overtimeBalanceDao.update(existing.copy(totalHours = totalHours))
        }
    }
}
