package com.slikharev.shifttrack.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings section for the experimental auto-alarm feature.
 *
 * Shows a toggle switch plus configurable parameters (trigger time, alarm
 * count, interval, first alarm time). When the feature is off, parameters
 * are hidden.
 */
// [EXPERIMENTAL:ALARM]
@Composable
fun ExperimentalFeaturesSection(
    enabled: Boolean,
    triggerTime: String,
    alarmCount: Int,
    intervalMinutes: Int,
    firstAlarmTime: String,
    onEnabledChange: (Boolean) -> Unit,
    onTriggerTimeChange: (String) -> Unit,
    onAlarmCountChange: (Int) -> Unit,
    onIntervalMinutesChange: (Int) -> Unit,
    onFirstAlarmTimeChange: (String) -> Unit,
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Enable toggle ────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Auto wake-up alarms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About this feature",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }

        // ── Parameters (only when enabled) ───────────────────────────────────
        if (enabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Trigger time
                    TimeRow(
                        label = "Evening trigger",
                        value = triggerTime,
                        onValueChange = onTriggerTimeChange,
                        minHour = 18,
                        maxHour = 23,
                    )

                    // First alarm time
                    TimeRow(
                        label = "First alarm at",
                        value = firstAlarmTime,
                        onValueChange = onFirstAlarmTimeChange,
                        minHour = 0,
                        maxHour = 12,
                    )

                    // Alarm count
                    IntRow(
                        label = "Number of alarms",
                        value = alarmCount,
                        onValueChange = onAlarmCountChange,
                        min = 1,
                        max = 10,
                    )

                    // Interval minutes
                    IntRow(
                        label = "Interval (min)",
                        value = intervalMinutes,
                        onValueChange = onIntervalMinutesChange,
                        min = 5,
                        max = 20,
                    )

                    // Preview
                    Spacer(modifier = Modifier.height(4.dp))
                    val alarmTimes = AlarmPreferences.computeAlarmTimes(
                        firstAlarmTime,
                        alarmCount,
                        intervalMinutes,
                    )
                    Text(
                        text = "Preview: ${alarmTimes.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // ── Info dialog ──────────────────────────────────────────────────────────
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Auto wake-up alarms") },
            text = {
                Text(
                    "When enabled, ShiftTrack will send you a notification the " +
                        "evening before each DAY shift. Tapping the notification " +
                        "opens a screen where you can review and set alarms in " +
                        "your phone's Clock app.\n\n" +
                        "You can customise the number of alarms, the interval " +
                        "between them, and the first alarm time — both globally " +
                        "here and per-day when the notification arrives.\n\n" +
                        "This is an experimental feature and may change in future " +
                        "updates.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Got it") }
            },
        )
    }
}

// ── Helper rows ──────────────────────────────────────────────────────────────

@Composable
private fun TimeRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minHour: Int,
    maxHour: Int,
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
                        val newHour = hour - 1
                        if (newHour >= minHour) onValueChange("%02d:%02d".format(newHour, newMinute + 60))
                    } else {
                        onValueChange("%02d:%02d".format(hour, newMinute))
                    }
                },
                enabled = hour > minHour || minute > 0,
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
                        val newHour = hour + 1
                        if (newHour <= maxHour) onValueChange("%02d:%02d".format(newHour, newMinute - 60))
                    } else {
                        onValueChange("%02d:%02d".format(hour, newMinute))
                    }
                },
                enabled = hour < maxHour || minute < 30,
            ) { Text("+") }
        }
    }
}

@Composable
private fun IntRow(
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
