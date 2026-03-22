package com.slikharev.shifttrack.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules background sync work using WorkManager.
 *
 * - [schedulePeriodicSync] enrolls a periodic job that fires approximately every
 *   [SYNC_INTERVAL_MINUTES] minutes when the device has a network connection. Using
 *   [ExistingPeriodicWorkPolicy.KEEP] ensures that re-scheduling on every app launch
 *   does not reset the next-fire timer.
 *
 * - [scheduleImmediateSync] enqueues a one-time request, replacing any pending
 *   immediate-sync job. Use this after a data-writing operation to push changes
 *   quickly (e.g., after onboarding completes or after adding a leave entry while online).
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Registers the recurring background sync. Safe to call on every app launch. */
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Triggers an immediate one-shot sync, replacing any pending immediate-sync request. */
    fun scheduleImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        private const val SYNC_INTERVAL_MINUTES = 15L
        private const val BACKOFF_MINUTES = 1L
    }
}
