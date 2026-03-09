package com.echoproduction.metroclock.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.echoproduction.metroclock.models.UserRole
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.services.ClockService
import com.echoproduction.metroclock.services.WorkspaceService
import com.echoproduction.metroclock.ui.employee.ClockScreen
import com.echoproduction.metroclock.ui.employee.MyHoursScreen
import com.echoproduction.metroclock.ui.employee.RequestsScreen
import com.echoproduction.metroclock.ui.manager.InboxScreen
import com.echoproduction.metroclock.ui.manager.ProfileScreen
import com.echoproduction.metroclock.ui.manager.TeamHoursScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Clock : Screen("clock", "Clock", Icons.Filled.Home)
    object Requests : Screen("requests", "Requests", Icons.Filled.List)
    object MyHours : Screen("myhours", "My Hours", Icons.Filled.DateRange)
    object Inbox : Screen("inbox", "Inbox", Icons.Filled.Notifications)
    object TeamHours : Screen("teamhours", "Team", Icons.Filled.DateRange)
    object Profile : Screen("profile", "Profile", Icons.Filled.Person)
}

@Composable
fun MainNavigation(
    authService: AuthService,
    clockService: ClockService,
    workspaceService: WorkspaceService,
    currentSSID: String?
) {
    val user by authService.currentUser.collectAsState()
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val isManager = user?.role == UserRole.MANAGER || user?.role == UserRole.ADMIN

    val employeeTabs = listOf(Screen.Clock, Screen.MyHours, Screen.Requests, Screen.Profile)
    val managerTabs = listOf(Screen.Clock, Screen.MyHours, Screen.TeamHours, Screen.Inbox, Screen.Profile)
    val tabs = if (isManager) managerTabs else employeeTabs

    Scaffold(
        containerColor = Color(0xFF080810),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F0F1A)) {
                tabs.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF5B8EF5),
                            selectedTextColor = Color(0xFF5B8EF5),
                            unselectedIconColor = Color(0xFF8888AA),
                            unselectedTextColor = Color(0xFF8888AA),
                            indicatorColor = Color(0xFF1A1A2E)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Clock.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Clock.route) {
                ClockScreen(authService, clockService, workspaceService, currentSSID)
            }
            composable(Screen.Requests.route) {
                RequestsScreen(authService)
            }
            composable(Screen.MyHours.route) {
                MyHoursScreen(authService)
            }
            composable(Screen.Inbox.route) {
                InboxScreen(authService)
            }
            composable(Screen.TeamHours.route) {
                TeamHoursScreen(authService)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(authService)
            }
        }
    }
}