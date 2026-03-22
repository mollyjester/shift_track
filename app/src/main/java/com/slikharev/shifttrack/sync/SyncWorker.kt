package com.slikharev.shifttrack.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.dao.ShiftDao
import com.slikharev.shifttrack.data.remote.FirestoreSyncDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that pushes unsynced Room rows to Firestore and performs the
 * annual leave-balance roll-over when the calendar year advances.
 *
 * Sync flow for each entity type:
 *   1. Load unsynced rows from the local DAO.
 *   2. Batch-write them to Firestore via [FirestoreSyncDataSource].
 *   3. Mark those rows as synced in Room by their IDs.
 *
 * On failure, the worker retries (up to [MAX_ATTEMPTS] times) with WorkManager's
 * exponential back-off, then returns [Result.failure] so the work request is
 * not retried infinitely.
 *
 * The worker is a no-op when the user is not logged in; this is safe because all
 * data is keyed by user ID (no cross-user leak).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userSession: UserSession,
    private val shiftDao: ShiftDao,
    private val leaveDao: LeaveDao,
    private val overtimeDao: OvertimeDao,
    private val syncDataSource: FirestoreSyncDataSource,
    private val annualResetUseCase: AnnualResetUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = userSession.currentUserId
            ?: return Result.success() // not logged in — nothing to sync

        return try {
            annualResetUseCase.runIfNeeded(uid)
            syncShifts(uid)
            syncLeaves(uid)
            syncOvertimes(uid)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private suspend fun syncShifts(uid: String) {
        val unsynced = shiftDao.getUnsynced(uid)
        if (unsynced.isEmpty()) return
        syncDataSource.syncShifts(uid, unsynced)
        shiftDao.markSynced(unsynced.map { it.id })
    }

    private suspend fun syncLeaves(uid: String) {
        val unsynced = leaveDao.getUnsynced(uid)
        if (unsynced.isEmpty()) return
        syncDataSource.syncLeaves(uid, unsynced)
        leaveDao.markSynced(unsynced.map { it.id })
    }

    private suspend fun syncOvertimes(uid: String) {
        val unsynced = overtimeDao.getUnsynced(uid)
        if (unsynced.isEmpty()) return
        syncDataSource.syncOvertimes(uid, unsynced)
        overtimeDao.markSynced(unsynced.map { it.id })
    }

    companion object {
        const val WORK_NAME_PERIODIC = "shift_track_sync_periodic"
        const val WORK_NAME_IMMEDIATE = "shift_track_sync_immediate"
        private const val MAX_ATTEMPTS = 3
    }
}
