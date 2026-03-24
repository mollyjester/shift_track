package com.slikharev.shifttrack.settings

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.ui.graphics.Color
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.LeaveColors
import com.slikharev.shifttrack.ui.LocalLeaveColors
import com.slikharev.shifttrack.ui.LocalShiftColors
import com.slikharev.shifttrack.ui.ShiftColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSpectatorOnly by viewModel.isSpectatorOnly.collectAsStateWithLifecycle()
    val anchorDate by viewModel.anchorDate.collectAsStateWithLifecycle()
    val anchorCycleIndex by viewModel.anchorCycleIndex.collectAsStateWithLifecycle()
    val leaveBalances by viewModel.leaveBalances.collectAsStateWithLifecycle()
    val overtimeBalance by viewModel.overtimeBalance.collectAsStateWithLifecycle()
    val todayShiftLabel by viewModel.todayShiftLabel.collectAsStateWithLifecycle()
    val pendingInviteLink by viewModel.pendingInviteLink.collectAsStateWithLifecycle()
    val widgetBgColor by viewModel.widgetBgColor.collectAsStateWithLifecycle()
    val widgetTransparency by viewModel.widgetTransparency.collectAsStateWithLifecycle()
    val widgetDayCount by viewModel.widgetDayCount.collectAsStateWithLifecycle()

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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareInviteDialog by remember { mutableStateOf(false) }

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
                onDeleteAccount = { showDeleteConfirm = true },
            )

            if (!isSpectatorOnly) {
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

                // ── Leave balance (per-category) ─────────────────────────────────────
                SettingsSectionHeader("Leave Allowance")
                LeaveBalancesCard(
                    balances = leaveBalances,
                    year = LocalDate.now().year,
                    onEditClick = { showLeaveDialog = true },
                )
            }

            HorizontalDivider()

            // ── Shift colors (visible to all users) ──────────────────────────────
            SettingsSectionHeader("Shift Colors")
            ColorSettingsSection(onColorChange = viewModel::saveShiftColor)

            HorizontalDivider()

            // ── Leave type colors (visible to all users) ─────────────────────────
            SettingsSectionHeader("Leave Type Colors")
            LeaveColorSettingsSection(onColorChange = viewModel::saveLeaveColor)

            HorizontalDivider()

            // ── Widget settings (visible to all users) ───────────────────────────
            SettingsSectionHeader("Widget")
            WidgetSettingsSection(
                bgColorArgb = widgetBgColor,
                transparency = widgetTransparency,
                dayCount = widgetDayCount,
                onBgColorChange = viewModel::setWidgetBgColor,
                onTransparencyChange = viewModel::setWidgetTransparency,
                onDayCountChange = viewModel::setWidgetDayCount,
            )

            if (!isSpectatorOnly) {
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

                // ── Invite viewers ─────────────────────────────────────────────────
                SettingsSectionHeader("Viewers")
                InviteCard(
                    onGenerateClick = {
                        viewModel.generateInvite()
                        showShareInviteDialog = true
                    },
                )
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
            balances = leaveBalances,
            onConfirm = { leaveType, days ->
                viewModel.updateLeaveTotalDaysByType(leaveType, days)
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

    if (showShareInviteDialog) {
        ShareInviteDialog(
            link = pendingInviteLink,
            onDismiss = {
                showShareInviteDialog = false
                viewModel.clearInviteLink()
            },
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "This will permanently delete your account and all associated " +
                        "data (shifts, leave records, and overtime). This action cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteAccount {
                            navController.navigate("auth") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete account") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
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
    onDeleteAccount: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
        OutlinedButton(
            onClick = onDeleteAccount,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Delete my account and all data")
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
private fun LeaveBalancesCard(
    balances: List<LeaveBalanceEntity>,
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
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(text = "$year", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (balances.isEmpty()) {
                    Text(text = "Not configured", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } else {
                    balances.filter { it.totalDays > 0f || it.usedDays > 0f }.forEach { b ->
                        val label = b.leaveType.lowercase().replaceFirstChar { it.uppercase() }
                        val remaining = (b.totalDays - b.usedDays).coerceAtLeast(0f)
                        val leaveType = LeaveType.fromString(b.leaveType)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (leaveType != null) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(LeaveColors.color(leaveType)),
                                )
                            }
                            Text(
                                text = "$label: ${"%.0f".format(b.totalDays)} total · ${"%.1f".format(remaining)} left",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
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
                                containerColor = LocalShiftColors.current.containerColor(type).copy(alpha = 0.5f),
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
    balances: List<LeaveBalanceEntity>,
    onConfirm: (String, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val balanceMap = balances.associateBy { it.leaveType }
    val dayValues = remember(balances) {
        LeaveType.entries.associateWith { type ->
            androidx.compose.runtime.mutableFloatStateOf(
                (balanceMap[type.name]?.totalDays ?: 0f).coerceIn(0f, 365f),
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave allowance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LeaveType.entries.forEach { type ->
                    val label = type.name.lowercase().replaceFirstChar { it.uppercase() }
                    val daysState = dayValues[type]!!
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(LeaveColors.color(type)),
                        )
                        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { if (daysState.floatValue > 0f) daysState.floatValue -= 1f }, enabled = daysState.floatValue > 0f) { Text("−") }
                        Text("${"%.0f".format(daysState.floatValue)}", style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { if (daysState.floatValue < 365f) daysState.floatValue += 1f }, enabled = daysState.floatValue < 365f) { Text("+") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                for (type in LeaveType.entries) {
                    onConfirm(type.name, dayValues[type]!!.floatValue)
                }
                onDismiss()
            }) { Text("Save") }
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

@Composable
private fun ColorSettingsSection(onColorChange: (ShiftType, Long) -> Unit) {
    val colorConfig = LocalShiftColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShiftType.entries.forEach { type ->
            val currentColor = colorConfig.containerColor(type)
            var expanded by remember { mutableStateOf(false) }
            val label = ShiftColors.label(type)

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(text = if (expanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall)
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HsvColorPicker(
                        initialColor = currentColor,
                        onColorSelected = { color ->
                            onColorChange(type, color.toArgb().toLong())
                        },
                        defaultColor = ShiftColors.containerColor(type),
                        onReset = {
                            onColorChange(type, ShiftColors.containerColor(type).toArgb().toLong())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaveColorSettingsSection(onColorChange: (LeaveType, Long) -> Unit) {
    val leaveColorConfig = LocalLeaveColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LeaveType.entries.forEach { type ->
            val currentColor = leaveColorConfig.color(type)
            var expanded by remember { mutableStateOf(false) }
            val label = LeaveColors.label(type)

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(text = if (expanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall)
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HsvColorPicker(
                        initialColor = currentColor,
                        onColorSelected = { color ->
                            onColorChange(type, color.toArgb().toLong())
                        },
                        defaultColor = LeaveColors.color(type),
                        onReset = {
                            onColorChange(type, LeaveColors.color(type).toArgb().toLong())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetSettingsSection(
    bgColorArgb: Long?,
    transparency: Float,
    dayCount: Int,
    onBgColorChange: (Long) -> Unit,
    onTransparencyChange: (Float) -> Unit,
    onDayCountChange: (Int) -> Unit,
) {
    val defaultBg = Color(0xFFF8FDFF.toInt())
    val currentBgColor = bgColorArgb?.let { Color(it.toInt()) } ?: defaultBg

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Background color ─────────────────────────────────────────────────
        Text("Background color", style = MaterialTheme.typography.bodyMedium)
        var bgExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { bgExpanded = !bgExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(currentBgColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(
                text = if (bgExpanded) "▲" else "▼",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (bgExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            HsvColorPicker(
                initialColor = currentBgColor,
                onColorSelected = { color ->
                    onBgColorChange(color.toArgb().toLong())
                },
                defaultColor = defaultBg,
                onReset = {
                    onBgColorChange(defaultBg.toArgb().toLong())
                },
            )
        }

        // ── Transparency ─────────────────────────────────────────────────────
        Text("Transparency", style = MaterialTheme.typography.bodyMedium)
        var localTransparency by remember(transparency) { mutableFloatStateOf(transparency) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${"%.0f".format(localTransparency * 100)}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
            Slider(
                value = localTransparency,
                onValueChange = { localTransparency = it },
                onValueChangeFinished = { onTransparencyChange(localTransparency) },
                valueRange = 0f..1f,
                steps = 9,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Days to show ─────────────────────────────────────────────────────
        Text("Days to show", style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { if (dayCount > AppDataStore.MIN_WIDGET_DAYS) onDayCountChange(dayCount - 1) },
                enabled = dayCount > AppDataStore.MIN_WIDGET_DAYS,
            ) { Text("−") }
            Text("$dayCount", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(
                onClick = { if (dayCount < AppDataStore.MAX_WIDGET_DAYS) onDayCountChange(dayCount + 1) },
                enabled = dayCount < AppDataStore.MAX_WIDGET_DAYS,
            ) { Text("+") }
        }
    }
}

// ── HSV Color Picker ─────────────────────────────────────────────────────────

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv
}

private fun hsvToColor(hsv: FloatArray): Color {
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun HsvColorPicker(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    defaultColor: Color,
    onReset: () -> Unit,
) {
    val initHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember(initialColor) { mutableFloatStateOf(initHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initHsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(initHsv[2]) }

    val previewColor = remember(hue, saturation, value) {
        hsvToColor(floatArrayOf(hue, saturation, value))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Preview swatch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(previewColor)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        )

        // Hue slider with rainbow gradient track
        Text("Hue", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = hue,
            onValueChange = { hue = it },
            onValueChangeFinished = { onColorSelected(hsvToColor(floatArrayOf(hue, saturation, value))) },
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth(),
        )

        // Saturation slider
        Text("Saturation", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            onValueChangeFinished = { onColorSelected(hsvToColor(floatArrayOf(hue, saturation, value))) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )

        // Brightness slider
        Text("Brightness", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onColorSelected(hsvToColor(floatArrayOf(hue, saturation, value))) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )

        // Reset button
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reset to default")
        }
    }
}

// ── Invite & Share ───────────────────────────────────────────────────────────

@Composable
private fun InviteCard(onGenerateClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Invite a viewer", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Share a 7-day link so someone can view your schedule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onGenerateClick) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Invite")
            }
        }
    }
}

@Composable
private fun ShareInviteDialog(link: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share invite link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (link == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Generating…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("This link expires in 7 days:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = link,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            if (link != null) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "View my ShiftTrack schedule: $link")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share invite"))
                        onDismiss()
                    },
                ) { Text("Share") }
            }
        },
        dismissButton = {
            if (link != null) {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(link))
                        onDismiss()
                    },
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
