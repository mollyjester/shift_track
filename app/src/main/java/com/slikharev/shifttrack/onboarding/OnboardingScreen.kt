package com.slikharev.shifttrack.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// TODO(Phase 2.5): Implement onboarding flow (anchor date, shift type, balances)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Onboarding — Phase 2.5")
    }
}
