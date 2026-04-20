package com.slikharev.shifttrack.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.slikharev.shifttrack.data.local.db.entity.AttachmentEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.LeaveColors
import com.slikharev.shifttrack.ui.LocalShiftColors
import com.slikharev.shifttrack.ui.ShiftColors
import kotlinx.coroutines.delay
import java.io.File
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
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val storageWarning by viewModel.storageWarning.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Camera photo URI (stored so the camera app can write to it)
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // ── Activity result launchers ────────────────────────────────────────────────

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* handled inline via the camera launcher */ }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            cameraPhotoUri?.let { uri ->
                viewModel.addAttachment(uri, "image/jpeg", "photo_${System.currentTimeMillis()}.jpg")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "image/*"
            val displayName = queryDisplayName(context, it) ?: "image_${System.currentTimeMillis()}"
            viewModel.addAttachment(it, mimeType, displayName)
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
            val displayName = queryDisplayName(context, it) ?: "document_${System.currentTimeMillis()}"
            viewModel.addAttachment(it, mimeType, displayName)
        }
    }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.clearError()
        }
    }

    // Auto-dismiss success message after 1.5s
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(1500)
            viewModel.clearSuccess()
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
            Box(modifier = Modifier.fillMaxSize()) {
                DayDetailContent(
                    modifier = Modifier.padding(padding),
                    dayInfo = dayInfo!!,
                    overtimeEntry = overtimeEntry,
                    isSaving = isSaving,
                    isSpectator = isSpectator,
                    attachments = attachments,
                    storageWarning = storageWarning,
                    onOverride = { viewModel.setManualOverride(it) },
                    onClearOverride = viewModel::clearManualOverride,
                    onAddLeave = viewModel::addLeave,
                    onRemoveLeave = viewModel::removeLeave,
                    onAddOvertime = viewModel::addOvertime,
                    onRemoveOvertime = viewModel::removeOvertime,
                    onSaveNote = viewModel::saveNote,
                    onTakePhoto = {
                    val hasCamera = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCamera) {
                        val photoFile = File(
                            context.cacheDir,
                            "camera_photos/photo_${System.currentTimeMillis()}.jpg",
                        ).also { it.parentFile?.mkdirs() }
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile,
                        )
                        cameraPhotoUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onPickGallery = { galleryLauncher.launch("image/*") },
                onPickDocument = { documentLauncher.launch(arrayOf("*/*")) },
                onOpenAttachment = { attachment ->
                    val file = viewModel.getAttachmentFile(attachment)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, attachment.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open with"))
                },
                onShareAttachment = { attachment ->
                    val file = viewModel.getAttachmentFile(attachment)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = attachment.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share"))
                },
                onDeleteAttachment = viewModel::deleteAttachment,
            )

                // ── Animated checkmark overlay ───────────────────────────────
                AnimatedVisibility(
                    visible = successMessage != null,
                    enter = scaleIn(initialScale = 0.5f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.5f) + fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp),
                        )
                        successMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailContent(
    modifier: Modifier = Modifier,
    dayInfo: DayInfo,
    overtimeEntry: OvertimeEntity?,
    isSaving: Boolean,
    isSpectator: Boolean,
    attachments: List<AttachmentEntity>,
    storageWarning: Boolean,
    onOverride: (ShiftType) -> Unit,
    onClearOverride: () -> Unit,
    onAddLeave: (LeaveType, Boolean, String?) -> Unit,
    onRemoveLeave: () -> Unit,
    onAddOvertime: (Float, String?) -> Unit,
    onRemoveOvertime: () -> Unit,
    onSaveNote: (String?) -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onPickDocument: () -> Unit,
    onOpenAttachment: (AttachmentEntity) -> Unit,
    onShareAttachment: (AttachmentEntity) -> Unit,
    onDeleteAttachment: (AttachmentEntity) -> Unit,
) {
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showOvertimeDialog by remember { mutableStateOf(false) }
    var showOverrideMenu by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var attachmentToDelete by remember { mutableStateOf<AttachmentEntity?>(null) }
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
            if (dayInfo.hasLeave) {
                val leaveLabel = dayInfo.leaveType?.name?.lowercase()
                    ?.replaceFirstChar { it.uppercase() } ?: "Leave"
                val halfLabel = if (dayInfo.halfDay) " (half day)" else ""
                Text(
                    text = "\uD83C\uDF34 $leaveLabel$halfLabel",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (dayInfo.hasOvertime) {
                Text(
                    text = "⏱ Overtime recorded",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!dayInfo.note.isNullOrBlank()) {
                Text(
                    text = "Note: ${dayInfo.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

            // Attachments section
            AttachmentsSection(
                attachments = attachments,
                storageWarning = storageWarning,
                isSaving = isSaving,
                onAddClick = { showAttachmentSheet = true },
                onOpen = onOpenAttachment,
                onShare = onShareAttachment,
                onDelete = { attachmentToDelete = it },
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

    if (!isSpectator && showAttachmentSheet) {
        AddAttachmentBottomSheet(
            onDismiss = { showAttachmentSheet = false },
            onTakePhoto = {
                showAttachmentSheet = false
                onTakePhoto()
            },
            onPickGallery = {
                showAttachmentSheet = false
                onPickGallery()
            },
            onPickDocument = {
                showAttachmentSheet = false
                onPickDocument()
            },
        )
    }

    if (attachmentToDelete != null) {
        AlertDialog(
            onDismissRequest = { attachmentToDelete = null },
            title = { Text("Delete attachment?") },
            text = { Text("\"${attachmentToDelete!!.fileName}\" will be permanently deleted.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteAttachment(attachmentToDelete!!)
                    attachmentToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { attachmentToDelete = null }) { Text("Cancel") }
            },
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

// ── Attachments composables ─────────────────────────────────────────────────────

@Composable
private fun AttachmentsSection(
    attachments: List<AttachmentEntity>,
    storageWarning: Boolean,
    isSaving: Boolean,
    onAddClick: () -> Unit,
    onOpen: (AttachmentEntity) -> Unit,
    onShare: (AttachmentEntity) -> Unit,
    onDelete: (AttachmentEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Attachments", style = MaterialTheme.typography.titleMedium)

        if (storageWarning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = "Storage nearly full. Go to Settings \u2192 Storage & Cleanup to free space.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        if (attachments.isNotEmpty()) {
            attachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    onOpen = { onOpen(attachment) },
                    onShare = { onShare(attachment) },
                    onDelete = { onDelete(attachment) },
                )
            }
        }

        Button(onClick = onAddClick, enabled = !isSaving) {
            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add attachment")
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: AttachmentEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail or icon
            if (attachment.mimeType.startsWith("image/")) {
                AsyncImage(
                    model = File(
                        LocalContext.current.filesDir,
                        attachment.localPath,
                    ),
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Document",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and size
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatFileSize(attachment.fileSizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Action icons
            IconButton(onClick = onOpen) {
                Icon(Icons.Default.OpenInNew, contentDescription = "Open")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAttachmentBottomSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onPickDocument: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Add attachment",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            TextButton(
                onClick = onTakePhoto,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Take photo", modifier = Modifier.weight(1f))
            }
            TextButton(
                onClick = onPickGallery,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Photo, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Choose from gallery", modifier = Modifier.weight(1f))
            }
            TextButton(
                onClick = onPickDocument,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Choose document", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment
}
