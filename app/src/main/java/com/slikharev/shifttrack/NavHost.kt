package com.slikharev.shifttrack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.slikharev.shifttrack.auth.AuthScreen
import com.slikharev.shifttrack.auth.AuthViewModel
import com.slikharev.shifttrack.calendar.CalendarScreen
import com.slikharev.shifttrack.dashboard.DashboardScreen
import com.slikharev.shifttrack.invite.InviteRedemptionScreen
import com.slikharev.shifttrack.onboarding.OnboardingScreen

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Onboarding : Screen("onboarding")
    data object Calendar : Screen("calendar")
    data object Dashboard : Screen("dashboard")
    data object Settings : Screen("settings")
    data object DayDetail : Screen("day/{date}") {
        fun createRoute(date: String) = "day/$date"
    }
    data object InviteRedemption : Screen("invite/{token}") {
        fun createRoute(token: String) = "invite/$token"
    }
}

@Composable
fun ShiftTrackNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val onboardingComplete by authViewModel.onboardingComplete.collectAsState()

    val startDestination = when {
        !isLoggedIn -> Screen.Auth.route
        !onboardingComplete -> Screen.Onboarding.route
        else -> Screen.Calendar.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Auth.route) {
            AuthScreen(onAuthSuccess = {
                val dest = if (onboardingComplete) Screen.Calendar.route else Screen.Onboarding.route
                navController.navigate(dest) {
                    popUpTo(Screen.Auth.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onComplete = {
                navController.navigate(Screen.Calendar.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(navController = navController)
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(
            route = Screen.DayDetail.route,
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "shiftapp://day/{date}" })
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: return@composable
            // DayDetailScreen will be implemented in Phase 2.4
        }
        composable(
            route = Screen.InviteRedemption.route,
            arguments = listOf(navArgument("token") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "shiftapp://invite" })
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token") ?: return@composable
            InviteRedemptionScreen(token = token, navController = navController)
        }
    }
}
