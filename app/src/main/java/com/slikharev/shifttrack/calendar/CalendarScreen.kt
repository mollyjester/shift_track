package com.slikharev.shifttrack.calendar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.slikharev.shifttrack.Screen
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.LeaveColors
import com.slikharev.shifttrack.ui.leaveGreyColor
import com.slikharev.shifttrack.ui.LocalLeaveColors
import com.slikharev.shifttrack.ui.LocalShiftColors
import com.slikharev.shifttrack.ui.ShiftColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Centre page index for the HorizontalPager (allows ±5000 months of scroll). */
private const val PAGER_CENTRE = 5000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController) {
    val viewModel: CalendarViewModel = hiltViewModel()
    val currentYearMonth by viewModel.currentYearMonth.collectAsStateWithLifecycle()
    val calendarDays by viewModel.calendarDays.collectAsStateWithLifecycle()
    val isSpectatorOnly by viewModel.isSpectatorOnly.collectAsStateWithLifecycle()
    val watchedHosts by viewModel.watchedHosts.collectAsStateWithLifecycle()
    val selectedHostUid by viewModel.selectedHostUid.collectAsStateWithLifecycle()
    val spectatorError by viewModel.spectatorError.collectAsStateWithLifecycle()
    val exportUri by viewModel.exportUri.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }

    // ── Pager state — page offset from today's month ─────────────────────────
    val baseMonth = remember { YearMonth.now() }
    val pagerState = rememberPagerState(initialPage = PAGER_CENTRE) { PAGER_CENTRE * 2 }

    // Sync pager → viewModel when user swipes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val targetMonth = baseMonth.plusMonths((page - PAGER_CENTRE).toLong())
            if (targetMonth != viewModel.currentYearMonth.value) {
                viewModel.setMonth(targetMonth)
            }
        }
    }

    // Sync viewModel → pager when arrow buttons or goToToday change the month
    LaunchedEffect(currentYearMonth) {
        val targetPage = PAGER_CENTRE +
            ((currentYearMonth.year - baseMonth.year) * 12 +
                (currentYearMonth.monthValue - baseMonth.monthValue))
        if (targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Launch share sheet when export URI is ready
    LaunchedEffect(exportUri) {
        exportUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export shifts"))
            viewModel.clearExportUri()
        }
    }

    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    val isOnCurrentMonth = currentYearMonth == YearMonth.now()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous month",
                        )
                    }
                },
                title = {
                    Text(
                        text = currentYearMonth.format(monthFormatter),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                actions = {
                    if (selectedHostUid == null) {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export to CSV",
                            )
                        }
                    }
                    IconButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next month",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isOnCurrentMonth) {
                SmallFloatingActionButton(
                    onClick = { viewModel.goToToday() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Default.Today, contentDescription = "Today")
                }
            }
        },
    ) { padding ->
        val calendarContent: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Host selector dropdown ───────────────────────────────────
                if (watchedHosts.isNotEmpty()) {
                    HostSelector(
                        isSpectatorOnly = isSpectatorOnly,
                        watchedHosts = watchedHosts,
                        selectedHostUid = selectedHostUid,
                        onHostSelected = viewModel::selectHost,
                    )
                }

                // ── Spectator error banner ───────────────────────────────────
                spectatorError?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                // ── Swipeable calendar pager ─────────────────────────────────
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    beyondViewportPageCount = 1,
                ) { _ ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        DayOfWeekHeader()
                        CalendarGrid(
                            calendarDays = calendarDays,
                            onDayClick = { date ->
                                navController.navigate(
                                    Screen.DayDetail.createRoute(date.toString()),
                                )
                            },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ShiftLegend()
                    }
                }
            }
        }

        // Wrap in PullToRefreshBox when viewing a spectated calendar
        if (selectedHostUid != null) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshSpectator() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                calendarContent()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                calendarContent()
            }
        }
    }

    // ── Export date range dialog ─────────────────────────────────────────────
    if (showExportDialog) {
        ExportDateRangeDialog(
            initialYearMonth = currentYearMonth,
            onConfirm = { start, end ->
                showExportDialog = false
                viewModel.exportCsv(context, start, end)
            },
            onDismiss = { showExportDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDateRangeDialog(
    initialYearMonth: java.time.YearMonth,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val startMillis = initialYearMonth.atDay(1)
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val endMillis = initialYearMonth.atEndOfMonth()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startMillis,
        initialSelectedEndDateMillis = endMillis,
        initialDisplayMode = DisplayMode.Input,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        val selectedStart = state.selectedStartDateMillis
                        val selectedEnd = state.selectedEndDateMillis
                        if (selectedStart != null && selectedEnd != null) {
                            val start = Instant.ofEpochMilli(selectedStart)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            val end = Instant.ofEpochMilli(selectedEnd)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            onConfirm(start, end)
                        }
                    },
                    enabled = state.selectedStartDateMillis != null &&
                        state.selectedEndDateMillis != null,
                ) {
                    Text("Export")
                }
            }
            DateRangePicker(
                state = state,
                modifier = Modifier.weight(1f),
                title = {
                    Text(
                        text = "Select date range to export",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                    )
                },
                showModeToggle = true,
            )
        }
    }
}

@Composable
private fun HostSelector(
    isSpectatorOnly: Boolean,
    watchedHosts: List<AppDataStore.WatchedHost>,
    selectedHostUid: String?,
    onHostSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when {
        selectedHostUid == null && !isSpectatorOnly -> "My"
        selectedHostUid != null -> watchedHosts.find { it.uid == selectedHostUid }?.displayName ?: "Unknown"
        else -> "Select a schedule"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = selectedLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = " ▼", style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (!isSpectatorOnly) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "My",
                            fontWeight = if (selectedHostUid == null) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onHostSelected(null)
                        expanded = false
                    },
                )
            }
            watchedHosts.forEach { host ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = host.displayName,
                            fontWeight = if (selectedHostUid == host.uid) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onHostSelected(host.uid)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DayOfWeekHeader() {
    val headers = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        headers.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    calendarDays: List<CalendarDay>,
    onDayClick: (java.time.LocalDate) -> Unit,
) {
    val weeks = calendarDays.chunked(7)
    Column(modifier = Modifier.fillMaxWidth()) {
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    when (cell) {
                        CalendarDay.Empty -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                        is CalendarDay.ShiftDay -> {
                            ShiftDayCell(
                                day = cell,
                                modifier = Modifier.weight(1f),
                                onClick = { onDayClick(cell.date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftDayCell(
    day: CalendarDay.ShiftDay,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalShiftColors.current
    val leaveColors = LocalLeaveColors.current
    val leaveGrey = leaveGreyColor()
    // Full-day leave → light grey; otherwise shift color
    val bgColor = if (day.dayInfo.hasLeave && !day.dayInfo.halfDay) {
        leaveGrey
    } else {
        colors.containerColor(day.shiftType)
    }
    val contentColor = run {
        val luminance = 0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue
        if (luminance > 0.5f) Color(0xFF212121.toInt()) else Color(0xFFEEEEEE.toInt())
    }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Half-day: bottom half is light grey
        if (day.dayInfo.halfDay) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        leaveGrey,
                        RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                    ),
            )
        }

        // Today indicator — solid primary circle behind the number
        if (day.isToday) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = if (day.isToday) MaterialTheme.colorScheme.onPrimary else contentColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            )
            // Leave type dot: only for full-day leave (not half-day)
            if (day.dayInfo.hasLeave && !day.dayInfo.halfDay && day.dayInfo.leaveType != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(leaveColors.color(day.dayInfo.leaveType!!), CircleShape),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftLegend() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Shifts",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShiftType.entries.forEach { type ->
                LegendChip(
                    color = LocalShiftColors.current.containerColor(type),
                    label = ShiftColors.label(type),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Leave",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LeaveType.entries.forEach { type ->
                LegendChip(
                    color = LocalLeaveColors.current.color(type),
                    label = LeaveColors.label(type),
                )
            }
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
