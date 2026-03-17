package com.echoproduction.metroclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.services.ClockService
import com.echoproduction.metroclock.services.LocationService
import com.echoproduction.metroclock.services.TaskService
import com.echoproduction.metroclock.services.WiFiService
import com.echoproduction.metroclock.services.WorkspaceService
import com.echoproduction.metroclock.ui.MainNavigation
import com.echoproduction.metroclock.ui.auth.LoginScreen
import com.echoproduction.metroclock.ui.theme.MetroClockTheme
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope

class MainActivity : ComponentActivity() {

    private val authService = AuthService()
    private val clockService = ClockService()
    private val workspaceService = WorkspaceService()
    private val taskService = TaskService()
    private lateinit var wifiService: WiFiService
    private lateinit var locationService: LocationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService.checkCurrentUser()
        wifiService = WiFiService(this)
        locationService = LocationService(this)

        enableEdgeToEdge()
        setContent {
            MetroClockTheme {
                val user by authService.currentUser.collectAsState()
                val currentSSID by wifiService.currentSSID.collectAsState()
                val location by locationService.location.collectAsState()
                val config by workspaceService.config.collectAsState()

                LaunchedEffect(Unit) {
                    while (true) {
                        wifiService.fetchSSID()
                        locationService.fetchLocation()
                        delay(10000)
                    }
                }

                LaunchedEffect(location) {
                    location?.let { workspaceService.updateLocation(it.latitude, it.longitude) }
                }

                // Fetch workspace config when user logs in
                LaunchedEffect(user) {
                    user?.let {
                        workspaceService.fetchWorkspaceConfig(it.workspaceId)
                    }
                }

                // Fetch tasks when config loads
                LaunchedEffect(config) {
                    user?.let {
                        taskService.fetchTasks(config, it.id)
                    }
                }

                if (user == null) {
                    LoginScreen(authService = authService)
                } else {
                    MainNavigation(
                        authService = authService,
                        clockService = clockService,
                        workspaceService = workspaceService,
                        taskService = taskService,
                        currentSSID = currentSSID
                    )
                }
            }
        }
    }
}