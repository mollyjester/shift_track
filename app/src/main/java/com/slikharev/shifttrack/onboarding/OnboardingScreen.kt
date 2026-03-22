package com.slikharev.shifttrack.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.slikharev.shifttrack.model.ShiftType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    val progress = when (state.step) {
        OnboardingStep.SHIFT_PICKER -> 0.33f
        OnboardingStep.LEAVE_SETUP -> 0.66f
        OnboardingStep.CONFIRM -> 1.0f
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Progress bar + step indicator
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            when (state.step) {
                OnboardingStep.SHIFT_PICKER -> ShiftPickerStep(
                    state = state,
                    onSelectCycleIndex = viewModel::selectCycleIndex,
                    onAnchorForward = viewModel::shiftAnchorForward,
                    onAnchorBack = viewModel::shiftAnchorBack,
                    onNext = { viewModel.nextStep() },
                )
                OnboardingStep.LEAVE_SETUP -> LeaveSetupStep(
                    state = state,
                    onLeaveChange = viewModel::setLeaveAllowanceDays,
                    onNext = { viewModel.nextStep() },
                    onBack = viewModel::prevStep,
                )
                OnboardingStep.CONFIRM -> ConfirmStep(
                    state = state,
                    onBack = viewModel::prevStep,
                    onConfirm = { viewModel.completeOnboarding(onComplete) },
                )
            }
        }
    }
}

// ── Step 1 ────────────────────────────────────────────────────────────────────

@Composable
private fun ShiftPickerStep(
    state: OnboardingUiState,
    onSelectCycleIndex: (Int) -> Unit,
    onAnchorForward: () -> Unit,
    onAnchorBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Step 1 of 3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "What is your shift today?",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This lets us calculate your rotation for every past and future date.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Anchor date adjuster
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onAnchorBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
                }
                Text(
                    text = state.anchorDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(160.dp),
                )
                IconButton(onClick = onAnchorForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
                }
            }
            Text(
                text = "Adjust if you opened the app just after midnight",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Cycle option cards
            CYCLE_LABELS.forEachIndexed { index, label ->
                val isSelected = state.selectedCycleIndex == index
                val shiftType = com.slikharev.shifttrack.engine.CadenceEngine.CYCLE[index]
                CycleOptionCard(
                    label = label,
                    shiftType = shiftType,
                    isSelected = isSelected,
                    onClick = { onSelectCycleIndex(index) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            state.error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = state.selectedCycleIndex >= 0,
        ) {
            Text("Next")
        }
    }
}

@Composable
private fun CycleOptionCard(
    label: String,
    shiftType: ShiftType,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        onClick = onClick,
        border = border,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shiftTypeEmoji(shiftType),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun shiftTypeEmoji(type: ShiftType) = when (type) {
    ShiftType.DAY -> "☀️"
    ShiftType.NIGHT -> "🌙"
    ShiftType.REST -> "💤"
    ShiftType.OFF -> "🏠"
    ShiftType.LEAVE -> "🌴"
}

// ── Step 2 ────────────────────────────────────────────────────────────────────

@Composable
private fun LeaveSetupStep(
    state: OnboardingUiState,
    onLeaveChange: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Step 2 of 3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Annual leave entitlement",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "How many leave days are you entitled to this year? You can change this later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = { onLeaveChange(state.leaveAllowanceDays - 1) },
                    enabled = state.leaveAllowanceDays > 1,
                ) { Text("−") }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedTextField(
                    value = state.leaveAllowanceDays.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { onLeaveChange(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                    suffix = { Text("days") },
                )
                Spacer(modifier = Modifier.width(16.dp))
                FilledTonalButton(
                    onClick = { onLeaveChange(state.leaveAllowanceDays + 1) },
                    enabled = state.leaveAllowanceDays < 365,
                ) { Text("+") }
            }

            state.error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onBack, modifier = Modifier.weight(1f).height(50.dp)) {
                Text("Back")
            }
            Button(onClick = onNext, modifier = Modifier.weight(1f).height(50.dp)) {
                Text("Next")
            }
        }
    }
}

// ── Step 3 ────────────────────────────────────────────────────────────────────

@Composable
private fun ConfirmStep(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Step 3 of 3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Your schedule preview", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Here's what your next 7 days look like. Tap Confirm to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                LazyColumn {
                    items(state.previewDays, key = { it.date.toString() }) { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = day.date.format(dateFormatter),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${shiftTypeEmoji(day.shiftType)}  ${day.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Annual leave: ${state.leaveAllowanceDays} days",
                style = MaterialTheme.typography.bodyMedium,
            )

            state.error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.isSaving) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onBack, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text("Back")
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text("Confirm")
                }
            }
        }
    }
}
