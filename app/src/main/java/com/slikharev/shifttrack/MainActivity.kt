package com.slikharev.shifttrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.slikharev.shifttrack.sync.SyncScheduler
import com.slikharev.shifttrack.ui.theme.ShiftTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Register the recurring background sync. Using KEEP policy means repeated
        // launches don't reset the next-fire timer.
        syncScheduler.schedulePeriodicSync()
        setContent {
            ShiftTrackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShiftTrackNavHost()
                }
            }
        }
    }
}
