package com.slikharev.shifttrack.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.slikharev.shifttrack.Screen
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.ShiftColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val upcomingDays by viewModel.upcomingDays.collectAsStateWithLifecycle()
    val leaveBalance by viewModel.leaveBalance.collectAsStateWithLifecycle()
    val remainingLeave by viewModel.remainingLeaveDays.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Dashboard") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val todayEntry = upcomingDays.firstOrNull { it.isToday }
            if (todayEntry != null) {
                TodayShiftCard(
                    dayInfo = todayEntry.dayInfo,
                    onClick = {
                        navController.navigate(
                            Screen.DayDetail.createRoute(todayEntry.dayInfo.date.toString()),
                        )
                    },
                )
            }

            if (leaveBalance != null) {
                LeaveBalanceCard(
                    totalDays = leaveBalance!!.totalDays,
                    usedDays = leaveBalance!!.usedDays,
                    remaining = remainingLeave,
                )
            }

            if (upcomingDays.size > 1) {
                UpcomingShiftsSection(
                    days = upcomingDays.drop(1), // skip today, already shown above
                    onDayClick = { date ->
                        navController.navigate(Screen.DayDetail.createRoute(date.toString()))
                    },
                )
            }
        }
    }
}

@Composable
private fun TodayShiftCard(dayInfo: DayInfo, onClick: () -> Unit) {
    val bg = ShiftColors.containerColor(dayInfo.shiftType)
    val fg = ShiftColors.onContainerColor(dayInfo.shiftType)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(24.dp),
    ) {
        Column {
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelLarge,
                color = fg.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ShiftColors.emoji(dayInfo.shiftType),
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = ShiftColors.label(dayInfo.shiftType),
                    style = MaterialTheme.typography.headlineMedium,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (dayInfo.hasLeave) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🌴 Leave day",
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun LeaveBalanceCard(totalDays: Float, usedDays: Float, remaining: Float?) {
    val fraction = if (totalDays > 0f) (usedDays / totalDays).coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Leave balance", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Used: ${"%.1f".format(usedDays)} d",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (remaining != null) "Remaining: ${"%.1f".format(remaining)} d" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun UpcomingShiftsSection(
    days: List<UpcomingDay>,
    onDayClick: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Coming up", style = MaterialTheme.typography.titleMedium)
        val shortFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        days.forEach { entry ->
            UpcomingDayRow(
                entry = entry,
                label = entry.dayInfo.date.format(shortFmt),
                onClick = { onDayClick(entry.dayInfo.date) },
            )
        }
    }
}

@Composable
private fun UpcomingDayRow(entry: UpcomingDay, label: String, onClick: () -> Unit) {
    val bg = ShiftColors.containerColor(entry.dayInfo.shiftType).copy(alpha = 0.35f)
    val contentColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = contentColor)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = ShiftColors.emoji(entry.dayInfo.shiftType))
            Text(
                text = ShiftColors.label(entry.dayInfo.shiftType),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
            if (entry.dayInfo.hasLeave) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = "🌴", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

