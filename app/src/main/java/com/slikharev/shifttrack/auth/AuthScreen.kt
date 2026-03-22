package com.slikharev.shifttrack.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// TODO(Phase 2.1): Implement full Google Sign-In flow
@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Sign In — Phase 2.1")
    }
}
