package com.echoproduction.metroclock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.echoproduction.metroclock.notifications.NotificationHelper
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.services.ClockService
import com.echoproduction.metroclock.services.LocationService
import com.echoproduction.metroclock.services.TaskService
import com.echoproduction.metroclock.services.WiFiService
import com.echoproduction.metroclock.services.WorkspaceService
import com.echoproduction.metroclock.ui.MainNavigation
import com.echoproduction.metroclock.ui.auth.LoginScreen
import com.echoproduction.metroclock.ui.theme.MetroClockTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope

class MainActivity : ComponentActivity() {

    private val authService = AuthService()
    private val clockService = ClockService()
    private val workspaceService = WorkspaceService()
    private val taskService = TaskService()
    private lateinit var wifiService: WiFiService
    private lateinit var locationService: LocationService

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                authService.handleGoogleSignInResult(account)
            } catch (e: ApiException) {
                // User cancelled or error — do nothing
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService.checkCurrentUser()
        wifiService = WiFiService(this)
        locationService = LocationService(this)

        // Create notification channel (required on Android 8+)
        NotificationHelper.createNotificationChannel(this)

        // Request POST_NOTIFICATIONS permission (required on Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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
                        // Save FCM token linked to this user account
                        NotificationHelper.saveToken(it.id)
                    }
                }

                // Fetch tasks when config loads
                LaunchedEffect(config) {
                    user?.let {
                        taskService.fetchTasks(config, it.id)
                    }
                }

                if (user == null) {
                    LoginScreen(
                        authService = authService,
                        onGoogleSignIn = { launchGoogleSignIn() }
                    )
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

    private fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        client.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(client.signInIntent)
        }
    }
}
