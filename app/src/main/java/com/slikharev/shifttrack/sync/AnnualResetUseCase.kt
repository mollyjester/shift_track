package com.slikharev.shifttrack.sync

import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs the annual roll-over when the calendar year advances:
 *
 * 1. If no leave-balance row exists for the current year, creates one by copying
 *    [totalDays] from the most recent previous year (so the allowance carries over).
 * 2. Does **not** reset [OvertimeBalanceEntity.compensatedHours] — the user does this
 *    manually via the Settings screen; the new year's overtime just starts accumulating
 *    from scratch once the first overtime entry is filed.
 * 3. Stores [currentYear] in [AppDataStore.lastResetYear] so the reset only runs once
 *    per year even if the worker fires multiple times.
 *
 * This class is pure business logic — no Android/WorkManager dependency — so it can be
 * unit-tested with in-memory fakes without Robolectric.
 */
@Singleton
class AnnualResetUseCase @Inject constructor(
    private val appDataStore: AppDataStore,
    private val leaveBalanceDao: LeaveBalanceDao,
    private val overtimeBalanceDao: OvertimeBalanceDao,
) {
    /**
     * Runs the annual reset for [uid] if it has not already been done for the
     * current calendar year. Returns true if a reset was performed, false if it
     * was already up-to-date.
     */
    suspend fun runIfNeeded(uid: String): Boolean {
        val currentYear = LocalDate.now().year
        val lastResetYear = appDataStore.lastResetYear.first()
        if (lastResetYear >= currentYear) return false

        carryOverLeaveBalance(uid, currentYear, previousYear = lastResetYear.takeIf { it > 0 } ?: (currentYear - 1))
        appDataStore.setLastResetYear(currentYear)
        return true
    }

    private suspend fun carryOverLeaveBalance(uid: String, currentYear: Int, previousYear: Int) {
        // Nothing to do if a leave-balance row already exists for the new year.
        if (leaveBalanceDao.getBalanceForYear(uid, currentYear) != null) return

        // Find the most recent prior year with a balance and copy its totalDays.
        val previousBalance = leaveBalanceDao.getBalanceForYear(uid, previousYear)
        val totalDays = previousBalance?.totalDays ?: appDataStore.defaultLeaveDays.first()

        leaveBalanceDao.upsert(
            LeaveBalanceEntity(
                year = currentYear,
                totalDays = totalDays,
                usedDays = 0f,
                userId = uid,
            )
        )
    }

    companion object
}
