package com.slikharev.shifttrack

import android.content.Intent
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
import com.slikharev.shifttrack.ui.LeaveColorConfig
import com.slikharev.shifttrack.ui.LeaveColors
import com.slikharev.shifttrack.ui.LocalLeaveColors
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

            val leaveAnnualColor by appDataStore.colorLeaveAnnual.collectAsState(initial = null)
            val leaveSickColor by appDataStore.colorLeaveSick.collectAsState(initial = null)
            val leavePersonalColor by appDataStore.colorLeavePersonal.collectAsState(initial = null)
            val leaveUnpaidColor by appDataStore.colorLeaveUnpaid.collectAsState(initial = null)
            val leaveStudyColor by appDataStore.colorLeaveStudy.collectAsState(initial = null)

            val colorConfig = ShiftColorConfig(
                dayColor = dayColor?.let { Color(it.toInt()) } ?: ShiftColors.Day,
                nightColor = nightColor?.let { Color(it.toInt()) } ?: ShiftColors.Night,
                restColor = restColor?.let { Color(it.toInt()) } ?: ShiftColors.Rest,
                offColor = offColor?.let { Color(it.toInt()) } ?: ShiftColors.Off,
                leaveColor = leaveColor?.let { Color(it.toInt()) } ?: ShiftColors.Leave,
            )

            val leaveColorConfig = LeaveColorConfig(
                annualColor = leaveAnnualColor?.let { Color(it.toInt()) } ?: LeaveColors.Annual,
                sickColor = leaveSickColor?.let { Color(it.toInt()) } ?: LeaveColors.Sick,
                personalColor = leavePersonalColor?.let { Color(it.toInt()) } ?: LeaveColors.Personal,
                unpaidColor = leaveUnpaidColor?.let { Color(it.toInt()) } ?: LeaveColors.Unpaid,
                studyColor = leaveStudyColor?.let { Color(it.toInt()) } ?: LeaveColors.Study,
            )

            ShiftTrackTheme {
                CompositionLocalProvider(
                    LocalShiftColors provides colorConfig,
                    LocalLeaveColors provides leaveColorConfig,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ShiftTrackNavHost()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
