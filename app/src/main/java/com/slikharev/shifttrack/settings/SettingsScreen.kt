package com.slikharev.shifttrack.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.ShiftColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val anchorDate by viewModel.anchorDate.collectAsStateWithLifecycle()
    val anchorCycleIndex by viewModel.anchorCycleIndex.collectAsStateWithLifecycle()
    val leaveBalance by viewModel.leaveBalance.collectAsStateWithLifecycle()
    val overtimeBalance by viewModel.overtimeBalance.collectAsStateWithLifecycle()
    val todayShiftLabel by viewModel.todayShiftLabel.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.savedMessage, uiState.error) {
        val msg = uiState.savedMessage ?: uiState.error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    var showScheduleDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showOvertimeDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Account ──────────────────────────────────────────────────────────
            AccountSection(
                displayName = viewModel.displayName,
                email = viewModel.email,
                onSignOut = { showSignOutConfirm = true },
            )

            HorizontalDivider()

            // ── Shift schedule ───────────────────────────────────────────────────
            SettingsSectionHeader("Shift Schedule")
            ScheduleCard(
                anchorDate = anchorDate,
                anchorCycleIndex = anchorCycleIndex,
                todayShiftLabel = todayShiftLabel,
                onEditClick = { showScheduleDialog = true },
            )

            HorizontalDivider()

            // ── Leave balance ────────────────────────────────────────────────────
            SettingsSectionHeader("Leave Allowance")
            LeaveBalanceCard(
                totalDays = leaveBalance?.totalDays,
                usedDays = leaveBalance?.usedDays,
                year = leaveBalance?.year ?: LocalDate.now().year,
                onEditClick = { showLeaveDialog = true },
            )

            HorizontalDivider()

            // ── Overtime balance ─────────────────────────────────────────────────
            if (overtimeBalance != null) {
                SettingsSectionHeader("Overtime")
                OvertimeSettingsCard(
                    totalHours = overtimeBalance!!.totalHours,
                    compensatedHours = overtimeBalance!!.compensatedHours,
                    year = overtimeBalance!!.year,
                    onEditClick = { showOvertimeDialog = true },
                )
                HorizontalDivider()
            }

            if (uiState.isSaving) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────────

    if (showScheduleDialog) {
        EditScheduleDialog(
            initialAnchorDate = anchorDate ?: LocalDate.now(),
            initialCycleIndex = anchorCycleIndex.coerceAtLeast(0),
            onConfirm = { date, idx ->
                showScheduleDialog = false
                viewModel.updateAnchor(date, idx)
            },
            onDismiss = { showScheduleDialog = false },
        )
    }

    if (showLeaveDialog) {
        EditLeaveDialog(
            initialDays = leaveBalance?.totalDays ?: 30f,
            onConfirm = { days ->
                showLeaveDialog = false
                viewModel.updateLeaveTotalDays(days)
            },
            onDismiss = { showLeaveDialog = false },
        )
    }

    if (showOvertimeDialog && overtimeBalance != null) {
        EditCompensatedDialog(
            initialHours = overtimeBalance!!.compensatedHours,
            maxHours = overtimeBalance!!.totalHours,
            onConfirm = { hours ->
                showOvertimeDialog = false
                viewModel.updateCompensatedOvertimeHours(hours)
            },
            onDismiss = { showOvertimeDialog = false },
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out?") },
            text = { Text("You will be returned to the login screen.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        viewModel.signOut {
                            navController.navigate("auth") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Section composables ───────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun AccountSection(
    displayName: String,
    email: String,
    onSignOut: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            if (displayName.isNotBlank()) {
                Text(text = displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            if (email.isNotBlank()) {
                Text(text = email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(
            onClick = onSignOut,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Sign out")
        }
    }
}

@Composable
private fun ScheduleCard(
    anchorDate: LocalDate?,
    anchorCycleIndex: Int,
    todayShiftLabel: String?,
    onEditClick: () -> Unit,
) {
    val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (anchorDate != null && anchorCycleIndex >= 0) {
                    val cycleLabel = CadenceEngine.CYCLE[anchorCycleIndex].name
                        .lowercase().replaceFirstChar { it.uppercase() }
                    Text(
                        text = "Anchor: ${anchorDate.format(dateFmt)} → $cycleLabel",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = "No schedule set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (todayShiftLabel != null) {
                    val type = ShiftType.valueOf(todayShiftLabel.uppercase())
                    Text(
                        text = "Today: ${ShiftColors.emoji(type)} $todayShiftLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit schedule")
            }
        }
    }
}

@Composable
private fun LeaveBalanceCard(
    totalDays: Float?,
    usedDays: Float?,
    year: Int,
    onEditClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "$year", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (totalDays != null) {
                    Text(
                        text = "Total: ${"%.0f".format(totalDays)} days",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (usedDays != null) {
                        val remaining = (totalDays - usedDays).coerceAtLeast(0f)
                        Text(
                            text = "Used: ${"%.1f".format(usedDays)} · Remaining: ${"%.1f".format(remaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = "Not configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit leave allowance")
            }
        }
    }
}

@Composable
private fun OvertimeSettingsCard(
    totalHours: Float,
    compensatedHours: Float,
    year: Int,
    onEditClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "$year", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Total: ${"%.1f".format(totalHours)} h",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Compensated: ${"%.1f".format(compensatedHours)} h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit compensated hours")
            }
        }
    }
}

// ── Edit dialogs ─────────────────────────────────────────────────────────────

@Composable
private fun EditScheduleDialog(
    initialAnchorDate: LocalDate,
    initialCycleIndex: Int,
    onConfirm: (LocalDate, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var anchorDate by remember { mutableStateOf(initialAnchorDate) }
    var cycleIndex by remember { mutableIntStateOf(initialCycleIndex) }
    val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit shift schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Anchor date:", style = MaterialTheme.typography.labelMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { anchorDate = anchorDate.minusDays(1) }) { Text("−") }
                    Text(anchorDate.format(dateFmt), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { anchorDate = anchorDate.plusDays(1) }) { Text("+") }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("On that day I was working:", style = MaterialTheme.typography.labelMedium)
                CadenceEngine.CYCLE.forEachIndexed { idx, type ->
                    val label = type.name.lowercase().replaceFirstChar { it.uppercase() }
                    val suffix = if (type == ShiftType.DAY && idx == 0) " (1st)" else if (type == ShiftType.DAY && idx == 1) " (2nd)" else ""
                    OutlinedButton(
                        onClick = { cycleIndex = idx },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (cycleIndex == idx) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = ShiftColors.containerColor(type).copy(alpha = 0.5f),
                            )
                        } else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text("${ShiftColors.emoji(type)} $label$suffix")
                        if (cycleIndex == idx) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("✓", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(anchorDate, cycleIndex) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditLeaveDialog(
    initialDays: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var days by remember { mutableFloatStateOf(initialDays.coerceIn(1f, 365f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Annual leave allowance") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Total leave days for the year:")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(onClick = { if (days > 1f) days -= 1f }, enabled = days > 1f) { Text("−") }
                    Text("${"%.0f".format(days)} days", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = { if (days < 365f) days += 1f }, enabled = days < 365f) { Text("+") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(days) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditCompensatedDialog(
    initialHours: Float,
    maxHours: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var hours by remember { mutableFloatStateOf(initialHours.coerceIn(0f, maxHours)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compensated overtime") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Hours paid out or taken as time-off:")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(onClick = { if (hours >= 0.5f) hours -= 0.5f }, enabled = hours >= 0.5f) { Text("−") }
                    Text("${"%.1f".format(hours)} h", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = { if (hours < maxHours) hours += 0.5f }, enabled = hours < maxHours) { Text("+") }
                }
                if (maxHours > 0f) {
                    Text(
                        text = "Max: ${"%.1f".format(maxHours)} h (total accrued)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(hours) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
