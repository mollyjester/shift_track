package com.slikharev.shifttrack.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.slikharev.shifttrack.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteRedemptionScreen(token: String, navController: NavController) {
    val viewModel: InviteViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                is InviteUiState.Loading, is InviteUiState.Redeeming -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = if (state is InviteUiState.Redeeming) "Accepting invite…" else "Loading invite…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is InviteUiState.Valid -> ValidContent(
                    hostDisplayName = state.invite.hostDisplayName,
                    onAccept = viewModel::accept,
                    onDecline = { navController.navigateUp() },
                )

                is InviteUiState.Success -> SuccessContent(
                    hostDisplayName = state.hostDisplayName,
                    onGoToCalendar = {
                        navController.navigate(Screen.Calendar.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )

                is InviteUiState.NotFound -> InfoContent(
                    icon = { Icon(Icons.Default.Error, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error) },
                    title = "Invalid invite",
                    message = "This invite link doesn't exist or has been removed.",
                    onBack = { navController.navigateUp() },
                )

                is InviteUiState.Expired -> InfoContent(
                    icon = { Icon(Icons.Default.HourglassEmpty, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary) },
                    title = "Invite expired",
                    message = "This invite link is more than 7 days old. Ask the sender to generate a new one.",
                    onBack = { navController.navigateUp() },
                )

                is InviteUiState.AlreadyClaimed -> InfoContent(
                    icon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary) },
                    title = "Already used",
                    message = "This invite link has already been accepted by someone.",
                    onBack = { navController.navigateUp() },
                )

                is InviteUiState.Error -> InfoContent(
                    icon = { Icon(Icons.Default.Error, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error) },
                    title = "Something went wrong",
                    message = state.message,
                    onBack = { navController.navigateUp() },
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

@Composable
private fun ValidContent(
    hostDisplayName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PersonAdd,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You've been invited!",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (hostDisplayName.isNotBlank()) {
                "$hostDisplayName has invited you to\nview their shift schedule."
            } else {
                "Someone has invited you to view their shift schedule."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAccept, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text("Accept")
        }
        OutlinedButton(onClick = onDecline, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text("Decline")
        }
    }
}

@Composable
private fun SuccessContent(
    hostDisplayName: String,
    onGoToCalendar: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "All set!",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (hostDisplayName.isNotBlank()) {
                "You can now view ${hostDisplayName}'s shift schedule."
            } else {
                "You are now linked as a viewer."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onGoToCalendar, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text("Go to Calendar")
        }
    }
}

@Composable
private fun InfoContent(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    onBack: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Spacer(Modifier.height(4.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(horizontal = 32.dp)) {
                Text("Retry")
            }
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.padding(horizontal = 32.dp),
            colors = if (onRetry == null) ButtonDefaults.outlinedButtonColors()
            else ButtonDefaults.outlinedButtonColors(),
        ) {
            Text("Back")
        }
    }
}
