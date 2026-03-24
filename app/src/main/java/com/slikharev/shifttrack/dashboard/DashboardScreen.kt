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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.slikharev.shifttrack.Screen
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.ShiftType
import com.slikharev.shifttrack.ui.LocalShiftColors
import com.slikharev.shifttrack.ui.ShiftColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val isSpectatorOnly by viewModel.isSpectatorOnly.collectAsStateWithLifecycle()
    val upcomingDays by viewModel.upcomingDays.collectAsStateWithLifecycle()
    val leaveBalances by viewModel.leaveBalances.collectAsStateWithLifecycle()
    val weeklyOvertimeHours by viewModel.weeklyOvertimeHours.collectAsStateWithLifecycle()
    val yearlyOvertimeBalance by viewModel.yearlyOvertimeBalance.collectAsStateWithLifecycle()
    val selectedHostName by viewModel.selectedHostName.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Dashboard") })
        },
    ) { padding ->
        if (isSpectatorOnly && upcomingDays.isEmpty() && selectedHostName == null) {
            // Spectator with no host selected yet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "You are in spectator mode.\nSelect a schedule in the Calendar tab to view.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isSpectatorOnly && selectedHostName != null) {
                    Text(
                        text = "Viewing: $selectedHostName",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

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

                if (!isSpectatorOnly && leaveBalances.isNotEmpty()) {
                    LeaveBalancesCard(balances = leaveBalances)
                }

                if (!isSpectatorOnly && (weeklyOvertimeHours > 0f || yearlyOvertimeBalance != null)) {
                    OvertimeCard(
                        weeklyHours = weeklyOvertimeHours,
                        balance = yearlyOvertimeBalance,
                    )
                }

                if (upcomingDays.size > 1) {
                    UpcomingShiftsSection(
                        days = upcomingDays.drop(1),
                        onDayClick = { date ->
                            navController.navigate(Screen.DayDetail.createRoute(date.toString()))
                        },
                    )
                }

                if (isSpectatorOnly && upcomingDays.isEmpty() && selectedHostName != null) {
                    Text(
                        text = "Could not load schedule — the host may need to open their app to sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayShiftCard(dayInfo: DayInfo, onClick: () -> Unit) {
    val colors = LocalShiftColors.current
    val bg = colors.containerColor(dayInfo.shiftType)
    val fg = colors.onContainerColor(dayInfo.shiftType)
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
private fun LeaveBalancesCard(balances: List<LeaveBalanceEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Leave balance", style = MaterialTheme.typography.titleMedium)
            balances.filter { it.totalDays > 0f || it.usedDays > 0f }.forEach { balance ->
                val label = balance.leaveType.lowercase().replaceFirstChar { it.uppercase() }
                val fraction = if (balance.totalDays > 0f) {
                    (balance.usedDays / balance.totalDays).coerceIn(0f, 1f)
                } else 0f
                val remaining = (balance.totalDays - balance.usedDays).coerceAtLeast(0f)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${"%.1f".format(remaining)} / ${"%.0f".format(balance.totalDays)} d",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
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
    val bg = LocalShiftColors.current.containerColor(entry.dayInfo.shiftType).copy(alpha = 0.35f)
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

@Composable
private fun OvertimeCard(weeklyHours: Float, balance: OvertimeBalanceEntity?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Overtime", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "This week: ${"%.1f".format(weeklyHours)} h",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (balance != null) {
                    Text(
                        text = "Year total: ${"%.1f".format(balance.totalHours)} h",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (balance != null && balance.compensatedHours > 0f) {
                Text(
                    text = "Compensated: ${"%.1f".format(balance.compensatedHours)} h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

