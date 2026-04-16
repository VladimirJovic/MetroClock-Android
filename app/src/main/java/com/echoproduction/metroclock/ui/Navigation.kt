package com.echoproduction.metroclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.echoproduction.metroclock.models.UserRole
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.services.BadgeService
import com.echoproduction.metroclock.services.ClockService
import com.echoproduction.metroclock.services.TaskService
import com.echoproduction.metroclock.services.WorkspaceService
import com.echoproduction.metroclock.ui.employee.ClockScreen
import com.echoproduction.metroclock.ui.employee.MyHoursScreen
import com.echoproduction.metroclock.ui.employee.RequestsScreen
import com.echoproduction.metroclock.ui.manager.InboxScreen
import com.echoproduction.metroclock.ui.manager.ProfileScreen
import com.echoproduction.metroclock.ui.manager.TeamHoursScreen
import com.echoproduction.metroclock.ui.theme.LocalMcColors
import com.echoproduction.metroclock.ui.theme.McOrange

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Clock     : Screen("clock",     "Clock",    Icons.Filled.Home)
    object Requests  : Screen("requests",  "Requests", Icons.Filled.List)
    object MyHours   : Screen("myhours",   "My Hours", Icons.Filled.DateRange)
    object Inbox     : Screen("inbox",     "Inbox",    Icons.Filled.Notifications)
    object TeamHours : Screen("teamhours", "Team",     Icons.Filled.DateRange)
    object Profile   : Screen("profile",   "Profile",  Icons.Filled.Person)
}

@Composable
fun BadgeIcon(icon: @Composable () -> Unit, count: Int) {
    Box {
        icon()
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-4).dp)
                    .size(if (count > 9) 18.dp else 15.dp)
                    .background(Color(0xFFF55252), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 9.sp
                )
            }
        }
    }
}

@Composable
fun MainNavigation(
    authService: AuthService,
    clockService: ClockService,
    workspaceService: WorkspaceService,
    taskService: TaskService,
    currentSSID: String?
) {
    val context = LocalContext.current
    val user by authService.currentUser.collectAsState()
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val isManager = user?.role == UserRole.MANAGER || user?.role == UserRole.ADMIN

    val badgeService = remember { BadgeService(context) }
    val badges by badgeService.state.collectAsState()

    // Start/stop listening when user changes
    LaunchedEffect(user?.id, user?.role) {
        val u = user
        if (u == null) { badgeService.stopListening(); return@LaunchedEffect }
        badgeService.startListening(userId = u.id, role = u.role, workspaceId = u.workspaceId)
    }
    DisposableEffect(Unit) { onDispose { badgeService.stopListening() } }

    val employeeTabs = listOf(Screen.Clock, Screen.MyHours, Screen.Requests, Screen.Profile)
    val managerTabs  = listOf(Screen.Clock, Screen.MyHours, Screen.TeamHours, Screen.Inbox, Screen.Profile)
    val tabs = if (isManager) managerTabs else employeeTabs

    Scaffold(
        containerColor = LocalMcColors.current.background,
        bottomBar = {
            Column {
                HorizontalDivider(color = LocalMcColors.current.navBorder, thickness = 1.dp)
                NavigationBar(containerColor = LocalMcColors.current.background) {
                    tabs.forEach { screen ->
                        val badgeCount = when (screen) {
                            Screen.Inbox     -> if (isManager) badges.pendingRequestUserIds.size else badges.employeeBadge
                            Screen.TeamHours -> badges.openSessionUserIds.size + badges.pendingOvertimeUserIds.size
                            else -> 0
                        }
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgeIcon(
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    count = badgeCount
                                )
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = McOrange,
                                selectedTextColor = McOrange,
                                unselectedIconColor = LocalMcColors.current.textSecondary,
                                unselectedTextColor = LocalMcColors.current.textSecondary,
                                indicatorColor = LocalMcColors.current.surface
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Clock.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Clock.route) {
                ClockScreen(authService, clockService, workspaceService, taskService, currentSSID)
            }
            composable(Screen.Requests.route) {
                RequestsScreen(authService, taskService, badgeService)
            }
            composable(Screen.MyHours.route) {
                MyHoursScreen(authService)
            }
            composable(Screen.Inbox.route) {
                InboxScreen(authService)
            }
            composable(Screen.TeamHours.route) {
                TeamHoursScreen(authService, badges)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(authService)
            }
        }
    }
}
