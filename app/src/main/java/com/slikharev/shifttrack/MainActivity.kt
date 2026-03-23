package com.slikharev.shifttrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.sync.SyncScheduler
import com.slikharev.shifttrack.ui.LocalShiftColors
import com.slikharev.shifttrack.ui.ShiftColorConfig
import com.slikharev.shifttrack.ui.ShiftColors
import com.slikharev.shifttrack.ui.theme.ShiftTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var appDataStore: AppDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        syncScheduler.schedulePeriodicSync()
        setContent {
            val dayColor by appDataStore.colorDay.collectAsState(initial = null)
            val nightColor by appDataStore.colorNight.collectAsState(initial = null)
            val restColor by appDataStore.colorRest.collectAsState(initial = null)
            val offColor by appDataStore.colorOff.collectAsState(initial = null)
            val leaveColor by appDataStore.colorLeave.collectAsState(initial = null)

            val colorConfig = ShiftColorConfig(
                dayColor = dayColor?.let { Color(it.toULong()) } ?: ShiftColors.Day,
                nightColor = nightColor?.let { Color(it.toULong()) } ?: ShiftColors.Night,
                restColor = restColor?.let { Color(it.toULong()) } ?: ShiftColors.Rest,
                offColor = offColor?.let { Color(it.toULong()) } ?: ShiftColors.Off,
                leaveColor = leaveColor?.let { Color(it.toULong()) } ?: ShiftColors.Leave,
            )

            ShiftTrackTheme {
                CompositionLocalProvider(LocalShiftColors provides colorConfig) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ShiftTrackNavHost()
                    }
                }
            }
        }
    }
}
