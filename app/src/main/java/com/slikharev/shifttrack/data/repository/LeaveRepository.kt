package com.slikharev.shifttrack.data.repository

import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.LeaveEntity
import com.slikharev.shifttrack.model.LeaveType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the user's leave records and yearly leave-balance.
 *
 * All writes go to Room via [LeaveDao] immediately; Firestore sync is handled
 * separately by [com.slikharev.shifttrack.sync.SyncWorker].
 *
 * After any mutation the yearly [LeaveBalanceEntity.usedDays] is recomputed
 * from the leaves table using a one-shot [kotlinx.coroutines.flow.first] call
 * and written back, so the balance screen never needs a separate aggregation.
 */
@Singleton
class LeaveRepository @Inject constructor(
    private val leaveDao: LeaveDao,
    private val leaveBalanceDao: LeaveBalanceDao,
    private val userSession: UserSession,
) {
    private val uid get() = userSession.currentUserId.orEmpty()

    fun observeBalanceForYear(year: Int): Flow<LeaveBalanceEntity?> =
        leaveBalanceDao.observeBalanceForYear(uid, year)

    fun sumLeaveDaysForYear(year: Int): Flow<Float> {
        val start = LocalDate.of(year, 1, 1).toString()
        val end = LocalDate.of(year, 12, 31).toString()
        return leaveDao.sumLeaveDaysForYear(uid, start, end)
    }

    fun getLeavesForRange(startDate: LocalDate, endDate: LocalDate): Flow<List<LeaveEntity>> =
        leaveDao.getLeavesForRange(uid, startDate.toString(), endDate.toString())

    suspend fun getLeaveForDate(date: LocalDate): LeaveEntity? =
        leaveDao.getLeaveForDate(uid, date.toString())

    /** Adds a leave day, also updates the cached used-days in LeaveBalanceEntity. */
    suspend fun addLeave(date: LocalDate, leaveType: LeaveType, halfDay: Boolean, note: String?) {
        leaveDao.upsert(
            LeaveEntity(
                date = date.toString(),
                leaveType = leaveType.name,
                halfDay = halfDay,
                note = note,
                userId = uid,
                synced = false,
            ),
        )
        refreshUsedDays(date.year)
    }

    suspend fun removeLeave(date: LocalDate) {
        leaveDao.deleteByDate(uid, date.toString())
        refreshUsedDays(date.year)
    }

    /** Re-computes usedDays from the leaves table and writes it back to LeaveBalanceEntity. */
    private suspend fun refreshUsedDays(year: Int) {
        val balance = leaveBalanceDao.getBalanceForYear(uid, year) ?: return
        val start = LocalDate.of(year, 1, 1).toString()
        val end = LocalDate.of(year, 12, 31).toString()
        val usedDays = leaveDao.sumLeaveDaysForYear(uid, start, end).first()
        leaveBalanceDao.update(balance.copy(usedDays = usedDays))
    }
}
