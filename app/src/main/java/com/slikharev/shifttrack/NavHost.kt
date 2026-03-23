package com.slikharev.shifttrack

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.slikharev.shifttrack.auth.AuthScreen
import com.slikharev.shifttrack.auth.AuthViewModel
import com.slikharev.shifttrack.calendar.CalendarScreen
import com.slikharev.shifttrack.calendar.DayDetailScreen
import com.slikharev.shifttrack.dashboard.DashboardScreen
import com.slikharev.shifttrack.invite.InviteRedemptionScreen
import com.slikharev.shifttrack.onboarding.OnboardingScreen
import com.slikharev.shifttrack.settings.SettingsScreen

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

private val MAIN_ROUTES = setOf(
    Screen.Calendar.route,
    Screen.Dashboard.route,
    Screen.Settings.route,
)

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

    // ── Deep-link handling ───────────────────────────────────────────────
    val activity = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
    var pendingDeepLink by remember { mutableStateOf(activity.intent?.data) }

    // Listen for new intents (app already running → widget tap)
    DisposableEffect(activity) {
        val listener = Consumer<android.content.Intent> { intent ->
            pendingDeepLink = intent.data
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    // Navigate when auth is resolved and a deep link is pending
    LaunchedEffect(pendingDeepLink, isLoggedIn, onboardingComplete) {
        val uri = pendingDeepLink ?: return@LaunchedEffect
        if (!isLoggedIn || !onboardingComplete) return@LaunchedEffect
        pendingDeepLink = null
        navigateDeepLink(navController, uri)
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in MAIN_ROUTES) {
                BottomNav(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
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
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
            composable(
                route = Screen.DayDetail.route,
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
            ) { backStackEntry ->
                val dateStr = backStackEntry.arguments?.getString("date") ?: return@composable
                // Validate ISO-8601 date format before navigating
                val validDate = dateStr.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))
                if (!validDate) return@composable
                DayDetailScreen(navController = navController)
            }
            composable(
                route = Screen.InviteRedemption.route,
                arguments = listOf(navArgument("token") { type = NavType.StringType }),
            ) { backStackEntry ->
                val token = backStackEntry.arguments?.getString("token") ?: return@composable
                // Validate UUID token format
                val validToken = token.matches(Regex("^[0-9a-fA-F\\-]{36}$"))
                if (!validToken) return@composable
                InviteRedemptionScreen(token = token, navController = navController)
            }
        }
    }
}

@Composable
private fun BottomNav(navController: NavController, currentRoute: String?) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Screen.Calendar.route,
            onClick = {
                navController.navigate(Screen.Calendar.route) {
                    popUpTo(Screen.Calendar.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
            label = { Text("Calendar") },
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Dashboard.route,
            onClick = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Calendar.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Settings.route,
            onClick = {
                navController.navigate(Screen.Settings.route) {
                    popUpTo(Screen.Calendar.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
        )
    }
}

private fun navigateDeepLink(navController: NavController, uri: Uri) {
    when (uri.host) {
        "day" -> {
            val date = uri.pathSegments.firstOrNull() ?: return
            if (!date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return
            navController.navigate(Screen.DayDetail.createRoute(date)) {
                launchSingleTop = true
            }
        }
        "invite" -> {
            val token = uri.pathSegments.firstOrNull() ?: return
            if (!token.matches(Regex("^[0-9a-fA-F\\-]{36}$"))) return
            navController.navigate(Screen.InviteRedemption.createRoute(token)) {
                launchSingleTop = true
            }
        }
    }
}
