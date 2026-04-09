package com.slikharev.shifttrack

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.slikharev.shifttrack.sync.StorageWarningNotifier
import com.slikharev.shifttrack.widget.MidnightAlarmScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShiftTrackApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var storageWarningNotifier: StorageWarningNotifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        MidnightAlarmScheduler.scheduleNext(this)
        storageWarningNotifier.ensureChannel()
    }
}
