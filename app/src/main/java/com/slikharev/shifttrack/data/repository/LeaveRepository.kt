package com.slikharev.shifttrack.data.repository

import androidx.room.withTransaction
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.data.local.db.ShiftTrackDatabase
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
 * Manages the user's leave records and yearly leave-balance per category.
 *
 * All writes go to Room via [LeaveDao] immediately; Firestore sync is handled
 * separately by [com.slikharev.shifttrack.sync.SyncWorker].
 *
 * After any mutation the yearly [LeaveBalanceEntity.usedDays] is recomputed
 * per leave type from the leaves table and written back.
 */
@Singleton
class LeaveRepository @Inject constructor(
    private val db: ShiftTrackDatabase,
    private val leaveDao: LeaveDao,
    private val leaveBalanceDao: LeaveBalanceDao,
    private val userSession: UserSession,
) {
    private val uid get() = userSession.requireUserId()

    fun observeAllBalancesForYear(year: Int): Flow<List<LeaveBalanceEntity>> =
        leaveBalanceDao.observeAllBalancesForYear(uid, year)

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
        val sanitizedNote = note?.take(MAX_NOTE_LENGTH)
        db.withTransaction {
            leaveDao.upsert(
                LeaveEntity(
                    date = date.toString(),
                    leaveType = leaveType.name,
                    halfDay = halfDay,
                    note = sanitizedNote,
                    userId = uid,
                    synced = false,
                ),
            )
            refreshUsedDays(date.year, leaveType.name)
        }
    }

    suspend fun removeLeave(date: LocalDate) {
        val existing = leaveDao.getLeaveForDate(uid, date.toString())
        db.withTransaction {
            leaveDao.deleteByDate(uid, date.toString())
            if (existing != null) {
                refreshUsedDays(date.year, existing.leaveType)
            }
        }
    }

    /** Re-computes usedDays for a specific leave type and writes it back. */
    private suspend fun refreshUsedDays(year: Int, leaveType: String) {
        val start = LocalDate.of(year, 1, 1).toString()
        val end = LocalDate.of(year, 12, 31).toString()
        val usedDays = leaveDao.sumLeaveDaysByType(uid, start, end, leaveType).first()
        val balance = leaveBalanceDao.getBalanceForYearAndType(uid, year, leaveType)
        if (balance == null) {
            leaveBalanceDao.upsert(
                LeaveBalanceEntity(
                    year = year,
                    leaveType = leaveType,
                    totalDays = 0f,
                    usedDays = usedDays,
                    userId = uid,
                ),
            )
        } else {
            leaveBalanceDao.update(balance.copy(usedDays = usedDays))
        }
    }

    companion object {
        const val MAX_NOTE_LENGTH = 500
    }
}
