package com.slikharev.shifttrack.invite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

// TODO(Phase 2.9): Implement invite token validation and spectator setup
@Composable
fun InviteRedemptionScreen(token: String, navController: NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Redeeming invite… — Phase 2.9")
    }
}
