package com.slikharev.shifttrack.alarm

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.ui.theme.ShiftTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Full-screen activity opened from the alarm trigger notification.
 *
 * Shows the alarm configuration for the target date, allows per-day
 * overrides, and fires [AlarmClock.ACTION_SET_ALARM] intents to set alarms
 * in the user's Clock app.
 */
// [EXPERIMENTAL:ALARM]
@AndroidEntryPoint
class AlarmSetterActivity : ComponentActivity() {

    @Inject lateinit var alarmPreferences: AlarmPreferences
    @Inject lateinit var alarmOverrideDao: AlarmOverrideDao
    @Inject lateinit var userSession: UserSession

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetDateStr = intent.getStringExtra(AlarmConstants.EXTRA_TARGET_DATE)
        if (targetDateStr == null) {
            finish()
            return
        }

        val targetDate = runCatching { LocalDate.parse(targetDateStr) }.getOrNull()
        if (targetDate == null) {
            finish()
            return
        }

        // Read defaults & existing override synchronously (activity creation)
        val defaults = runBlocking {
            AlarmDefaults(
                count = alarmPreferences.readAlarmCount(),
                interval = alarmPreferences.readIntervalMinutes(),
                firstTime = alarmPreferences.readFirstAlarmTime(),
            )
        }
        val uid = userSession.requireUserId()
        val existingOverride = runBlocking {
            alarmOverrideDao.getOverrideForDate(uid, targetDateStr)
        }

        setContent {
            ShiftTrackTheme {
                AlarmSetterScreen(
                    targetDate = targetDate,
                    defaults = defaults,
                    existingOverride = existingOverride,
                    onSetAlarms = { count, interval, firstTime, isCustom ->
                        if (isCustom) {
                            saveOverride(targetDateStr, uid, count, interval, firstTime)
                        }
                        fireAlarms(firstTime, count, interval, targetDate)
                        Toast.makeText(this, "$count alarm(s) set", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun saveOverride(
        date: String,
        uid: String,
        count: Int,
        interval: Int,
        firstTime: String,
    ) = runBlocking {
        alarmOverrideDao.upsert(
            AlarmOverrideEntity(
                date = date,
                alarmCount = count,
                intervalMinutes = interval,
                firstAlarmTime = firstTime,
                userId = uid,
                synced = false,
            ),
        )
    }

    private fun fireAlarms(firstTime: String, count: Int, interval: Int, targetDate: LocalDate) {
        val times = AlarmPreferences.computeAlarmTimes(firstTime, count, interval)
        times.forEach { time ->
            val parts = time.split(":")
            val hour = parts[0].toIntOrNull() ?: return@forEach
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "ShiftTrack — ${targetDate.dayOfWeek.name.lowercase()}")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }
    }
}

private data class AlarmDefaults(
    val count: Int,
    val interval: Int,
    val firstTime: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmSetterScreen(
    targetDate: LocalDate,
    defaults: AlarmDefaults,
    existingOverride: AlarmOverrideEntity?,
    onSetAlarms: (count: Int, interval: Int, firstTime: String, isCustom: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val dateLabel = targetDate.format(
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()),
    )

    var isCustom by remember { mutableStateOf(existingOverride != null) }
    var count by remember { mutableIntStateOf(existingOverride?.alarmCount ?: defaults.count) }
    var interval by remember { mutableIntStateOf(existingOverride?.intervalMinutes ?: defaults.interval) }
    var firstTime by remember { mutableStateOf(existingOverride?.firstAlarmTime ?: defaults.firstTime) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Set alarms for $dateLabel") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Default preview
            val defaultTimes = AlarmPreferences.computeAlarmTimes(
                defaults.firstTime,
                defaults.count,
                defaults.interval,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default alarms", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = defaultTimes.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Custom toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Custom alarms for this day",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Switch(checked = isCustom, onCheckedChange = { isCustom = it })
            }

            // Custom editor
            if (isCustom) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // First alarm time
                        TimeAdjuster(
                            label = "First alarm at",
                            value = firstTime,
                            onValueChange = { firstTime = it },
                        )

                        // Count
                        IntAdjuster(
                            label = "Number of alarms",
                            value = count,
                            onValueChange = { count = it },
                            min = 1,
                            max = 10,
                        )

                        // Interval
                        IntAdjuster(
                            label = "Interval (min)",
                            value = interval,
                            onValueChange = { interval = it },
                            min = 5,
                            max = 20,
                        )

                        // Live preview
                        Spacer(modifier = Modifier.height(4.dp))
                        val preview = AlarmPreferences.computeAlarmTimes(firstTime, count, interval)
                        Text(
                            text = "Preview: ${preview.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            Button(
                onClick = {
                    if (isCustom) {
                        onSetAlarms(count, interval, firstTime, true)
                    } else {
                        onSetAlarms(defaults.count, defaults.interval, defaults.firstTime, false)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val n = if (isCustom) count else defaults.count
                Text("Set $n alarm(s)")
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun TimeAdjuster(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val parts = value.split(":")
    val hour = parts[0].toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val newMinute = minute - 30
                    if (newMinute < 0) {
                        if (hour > 0) onValueChange("%02d:%02d".format(hour - 1, newMinute + 60))
                    } else {
                        onValueChange("%02d:%02d".format(hour, newMinute))
                    }
                },
                enabled = hour > 0 || minute > 0,
            ) { Text("−") }
            Text(
                text = "%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(56.dp),
            )
            OutlinedButton(
                onClick = {
                    val newMinute = minute + 30
                    if (newMinute >= 60) {
                        if (hour < 12) onValueChange("%02d:%02d".format(hour + 1, newMinute - 60))
                    } else {
                        onValueChange("%02d:%02d".format(hour, newMinute))
                    }
                },
                enabled = hour < 12 || minute < 30,
            ) { Text("+") }
        }
    }
}

@Composable
private fun IntAdjuster(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(
                onClick = { if (value > min) onValueChange(value - 1) },
                enabled = value > min,
            ) { Text("−") }
            Text(
                text = "$value",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(32.dp),
            )
            OutlinedButton(
                onClick = { if (value < max) onValueChange(value + 1) },
                enabled = value < max,
            ) { Text("+") }
        }
    }
}
