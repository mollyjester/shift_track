package com.slikharev.shifttrack.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.slikharev.shifttrack.Screen
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.LeaveColors
import com.slikharev.shifttrack.ui.LocalShiftColors
import com.slikharev.shifttrack.ui.ShiftColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController) {
    val viewModel: CalendarViewModel = hiltViewModel()
    val currentYearMonth by viewModel.currentYearMonth.collectAsStateWithLifecycle()
    val calendarDays by viewModel.calendarDays.collectAsStateWithLifecycle()
    val isSpectatorOnly by viewModel.isSpectatorOnly.collectAsStateWithLifecycle()
    val watchedHosts by viewModel.watchedHosts.collectAsStateWithLifecycle()
    val selectedHostUid by viewModel.selectedHostUid.collectAsStateWithLifecycle()

    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    // Whether to show day-click navigation (only for own calendar)
    val isViewingOwnCalendar = selectedHostUid == null

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.prevMonth() }) {
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
                    IconButton(onClick = { viewModel.nextMonth() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next month",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Host selector dropdown ───────────────────────────────────────
            if (watchedHosts.isNotEmpty()) {
                HostSelector(
                    isSpectatorOnly = isSpectatorOnly,
                    watchedHosts = watchedHosts,
                    selectedHostUid = selectedHostUid,
                    onHostSelected = viewModel::selectHost,
                )
            }

            DayOfWeekHeader()
            CalendarGrid(
                calendarDays = calendarDays,
                onDayClick = { date ->
                    if (isViewingOwnCalendar) {
                        navController.navigate(Screen.DayDetail.createRoute(date.toString()))
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            ShiftLegend()
            Spacer(modifier = Modifier.weight(1f))
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
    val bgColor = colors.containerColor(day.shiftType)
    val contentColor = colors.onContainerColor(day.shiftType)
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
        // Half-day: darker bottom half
        if (day.dayInfo.halfDay) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        darkenColor(bgColor, 0.3f),
                        RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                    ),
            )
        }

        // Today indicator
        if (day.isToday) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.5f)
                    .border(2.dp, contentColor, RoundedCornerShape(4.dp)),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            )
            // Leave type indicator (non-half-day leaves show leave type color dot)
            if (day.dayInfo.hasLeave && !day.dayInfo.halfDay && day.dayInfo.leaveType != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(LeaveColors.color(day.dayInfo.leaveType!!), CircleShape),
                )
            } else if (day.dayInfo.hasLeave) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(ShiftColors.Leave, CircleShape),
                )
            }
        }
    }
}

/** Darkens a color by reducing brightness by the given [factor] (0..1). */
private fun darkenColor(color: Color, factor: Float): Color {
    return Color(
        red = (color.red * (1f - factor)).coerceIn(0f, 1f),
        green = (color.green * (1f - factor)).coerceIn(0f, 1f),
        blue = (color.blue * (1f - factor)).coerceIn(0f, 1f),
        alpha = color.alpha,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftLegend() {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShiftType.entries.forEach { type ->
            LegendChip(
                color = LocalShiftColors.current.containerColor(type),
                label = ShiftColors.label(type),
            )
        }
        LeaveType.entries.forEach { type ->
            LegendChip(
                color = LeaveColors.color(type),
                label = LeaveColors.label(type),
            )
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
