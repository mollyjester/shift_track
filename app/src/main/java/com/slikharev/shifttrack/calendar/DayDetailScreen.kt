package com.slikharev.shifttrack.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.LeaveColors
import com.slikharev.shifttrack.ui.LocalShiftColors
import com.slikharev.shifttrack.ui.ShiftColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(navController: NavController) {
    val viewModel: DayDetailViewModel = hiltViewModel()
    val dayInfo by viewModel.dayInfo.collectAsStateWithLifecycle()
    val overtimeEntry by viewModel.overtimeEntry.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isSpectator by viewModel.isSpectator.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.clearError()
        }
    }

    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.date.format(dateFormatter)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (dayInfo == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            DayDetailContent(
                modifier = Modifier.padding(padding),
                dayInfo = dayInfo!!,
                overtimeEntry = overtimeEntry,
                isSaving = isSaving,
                isSpectator = isSpectator,
                onOverride = { viewModel.setManualOverride(it) },
                onClearOverride = viewModel::clearManualOverride,
                onAddLeave = viewModel::addLeave,
                onRemoveLeave = viewModel::removeLeave,
                onAddOvertime = viewModel::addOvertime,
                onRemoveOvertime = viewModel::removeOvertime,
                onSaveNote = viewModel::saveNote,
            )
        }
    }
}

@Composable
private fun DayDetailContent(
    modifier: Modifier = Modifier,
    dayInfo: DayInfo,
    overtimeEntry: OvertimeEntity?,
    isSaving: Boolean,
    isSpectator: Boolean,
    onOverride: (ShiftType) -> Unit,
    onClearOverride: () -> Unit,
    onAddLeave: (LeaveType, Boolean, String?) -> Unit,
    onRemoveLeave: () -> Unit,
    onAddOvertime: (Float, String?) -> Unit,
    onRemoveOvertime: () -> Unit,
    onSaveNote: (String?) -> Unit,
) {
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showOvertimeDialog by remember { mutableStateOf(false) }
    var showOverrideMenu by remember { mutableStateOf(false) }
    var noteText by remember(dayInfo.note) { mutableStateOf(dayInfo.note ?: "") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Shift type card (always visible)
        ShiftTypeCard(shiftType = dayInfo.shiftType, isManualOverride = dayInfo.isManualOverride)

        if (isSpectator) {
            Text(
                text = "Spectator mode — calendar is read-only",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Note section
            NoteSection(
                noteText = noteText,
                isSaving = isSaving,
                onNoteChange = { noteText = it },
                onSave = { onSaveNote(noteText.takeIf { it.isNotBlank() }) },
            )

            // Manual override section
            OverrideSection(
                currentType = dayInfo.shiftType,
                isManualOverride = dayInfo.isManualOverride,
                isSaving = isSaving,
                showMenu = showOverrideMenu,
                onToggleMenu = { showOverrideMenu = !showOverrideMenu },
                onDismissMenu = { showOverrideMenu = false },
                onOverride = { type ->
                    showOverrideMenu = false
                    onOverride(type)
                },
                onClearOverride = onClearOverride,
            )

            // Leave section
            LeaveSection(
                hasLeave = dayInfo.hasLeave,
                isSaving = isSaving,
                onAddLeaveClick = { showLeaveDialog = true },
                onRemoveLeave = onRemoveLeave,
            )

            // Overtime section
            OvertimeSection(
                overtimeEntry = overtimeEntry,
                isSaving = isSaving,
                onAddOvertimeClick = { showOvertimeDialog = true },
                onRemoveOvertime = onRemoveOvertime,
            )
        }
    }

    if (!isSpectator && showLeaveDialog) {
        AddLeaveDialog(
            onConfirm = { leaveType, halfDay ->
                showLeaveDialog = false
                onAddLeave(leaveType, halfDay, null)
            },
            onDismiss = { showLeaveDialog = false },
        )
    }

    if (!isSpectator && showOvertimeDialog) {
        AddOvertimeDialog(
            onConfirm = { hours ->
                showOvertimeDialog = false
                onAddOvertime(hours, null)
            },
            onDismiss = { showOvertimeDialog = false },
        )
    }
}

@Composable
private fun NoteSection(
    noteText: String,
    isSaving: Boolean,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Note", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = noteText,
            onValueChange = { onNoteChange(it.take(500)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Add a note for this day…") },
            minLines = 2,
            maxLines = 4,
        )
        Button(
            onClick = onSave,
            enabled = !isSaving,
        ) {
            Text("Save note")
        }
    }
}

@Composable
private fun ShiftTypeCard(shiftType: ShiftType, isManualOverride: Boolean) {
    val bg = LocalShiftColors.current.containerColor(shiftType)
    val fg = LocalShiftColors.current.onContainerColor(shiftType)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = ShiftColors.emoji(shiftType), style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = ShiftColors.label(shiftType),
                style = MaterialTheme.typography.headlineMedium,
                color = fg,
                fontWeight = FontWeight.Bold,
            )
            if (isManualOverride) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Manual override",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun OverrideSection(
    currentType: ShiftType,
    isManualOverride: Boolean,
    isSaving: Boolean,
    showMenu: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onOverride: (ShiftType) -> Unit,
    onClearOverride: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Override shift", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = onToggleMenu, enabled = !isSaving) {
                    Text("Change to…")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
                    ShiftType.entries
                        .filter { it != ShiftType.LEAVE }
                        .forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text("${ShiftColors.emoji(type)} ${ShiftColors.label(type)}")
                                },
                                onClick = { onOverride(type) },
                            )
                        }
                }
            }
            if (isManualOverride) {
                TextButton(onClick = onClearOverride, enabled = !isSaving) {
                    Text("Clear override")
                }
            }
        }
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LeaveSection(
    hasLeave: Boolean,
    isSaving: Boolean,
    onAddLeaveClick: () -> Unit,
    onRemoveLeave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Leave", style = MaterialTheme.typography.titleMedium)
        if (hasLeave) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🌴 Leave recorded", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRemoveLeave, enabled = !isSaving) {
                    Text("Remove")
                }
            }
        } else {
            Button(onClick = onAddLeaveClick, enabled = !isSaving) {
                Text("Add leave")
            }
        }
    }
}

@Composable
private fun AddLeaveDialog(
    onConfirm: (LeaveType, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedType by remember { mutableStateOf(LeaveType.ANNUAL) }
    var halfDay by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add leave") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type:", style = MaterialTheme.typography.labelMedium)
                LeaveType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(LeaveColors.color(type), CircleShape),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                FilterChip(
                    selected = halfDay,
                    onClick = { halfDay = !halfDay },
                    label = { Text("Half day") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedType, halfDay) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun OvertimeSection(
    overtimeEntry: OvertimeEntity?,
    isSaving: Boolean,
    onAddOvertimeClick: () -> Unit,
    onRemoveOvertime: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Overtime", style = MaterialTheme.typography.titleMedium)
        if (overtimeEntry != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⏱ ${"%.1f".format(overtimeEntry.hours)} h",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (overtimeEntry.note != null) {
                    Text(
                        text = "· ${overtimeEntry.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRemoveOvertime, enabled = !isSaving) {
                    Text("Remove")
                }
            }
        } else {
            Button(onClick = onAddOvertimeClick, enabled = !isSaving) {
                Text("Add overtime")
            }
        }
    }
}

@Composable
private fun AddOvertimeDialog(
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    // Step 0.5 h, range 0.5 – 24 h
    var hours by remember { mutableFloatStateOf(1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add overtime") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Hours worked over the scheduled shift:")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = { if (hours > 0.5f) hours -= 0.5f },
                        enabled = hours > 0.5f,
                    ) { Text("−") }
                    Text(
                        text = "${"%.1f".format(hours)} h",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    OutlinedButton(
                        onClick = { if (hours < 24f) hours += 0.5f },
                        enabled = hours < 24f,
                    ) { Text("+") }
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
