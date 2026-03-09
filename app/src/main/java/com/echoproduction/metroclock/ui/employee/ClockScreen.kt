@file:OptIn(ExperimentalMaterial3Api::class)

package com.echoproduction.metroclock.ui.employee

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.services.ClockService
import com.echoproduction.metroclock.services.WorkspaceService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ClockScreen(
    authService: AuthService,
    clockService: ClockService,
    workspaceService: WorkspaceService,
    currentSSID: String?
) {
    val user by authService.currentUser.collectAsState()
    val isClockedIn by clockService.isClockedIn.collectAsState()
    val isLoading by clockService.isLoading.collectAsState()
    val offices by workspaceService.offices.collectAsState()

    var currentTime by remember { mutableStateOf(Date()) }
    var showOvertimeSheet by remember { mutableStateOf(false) }
    var overtimeNote by remember { mutableStateOf("") }

    val permissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    LaunchedEffect(user) {
        user?.let {
            clockService.fetchTodayEvents(it.id)
            workspaceService.fetchOffices(it.workspaceId)
            if (!permissionsState.allPermissionsGranted) {
                permissionsState.launchMultiplePermissionRequest()
            }
        }
    }

    val matchedOfficeByWiFi = offices.firstOrNull { it.ssid == currentSSID && !currentSSID.isNullOrEmpty() }
    val matchedOfficeByGPS = workspaceService.nearestOffice
    val isLocationVerified = matchedOfficeByWiFi != null || matchedOfficeByGPS != null

    val locationLabel = when {
        matchedOfficeByWiFi != null -> "${matchedOfficeByWiFi.name} (WiFi)"
        matchedOfficeByGPS != null -> "${matchedOfficeByGPS.name} (GPS)"
        else -> "Outside Office"
    }

    val plannedHoursToday: Double = run {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
        val hours = user?.dailyHours?.get(dow.toString())
        hours ?: 8.0
    }

    val workedToday: String = run {
        val lastIn = clockService.lastClockIn ?: return@run "0h 0m"
        val seconds = ((Date().time - lastIn.timestamp.toDate().time) / 1000).toInt()
        "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080810))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = timeFormat.format(currentTime),
                fontSize = 52.sp,
                fontWeight = FontWeight.Thin,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = dateFormat.format(currentTime),
                fontSize = 14.sp,
                color = Color(0xFF8888AA)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(Color(0xFF0F0F1A), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Status", fontSize = 11.sp, color = Color(0xFF8888AA))
                        Text(
                            text = if (isClockedIn) "Clocked In" else "Clocked Out",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isClockedIn) Color(0xFF2DD47E) else Color(0xFFF55252)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isClockedIn) Color(0xFF2DD47E) else Color(0xFFF55252))
                    )
                }

                Divider(color = Color(0xFF2A2A40))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Worked today", fontSize = 11.sp, color = Color(0xFF8888AA))
                        Text(
                            text = if (isClockedIn) workedToday else "—",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Location", fontSize = 11.sp, color = Color(0xFF8888AA))
                        Text(
                            text = locationLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLocationVerified) Color(0xFF2DD47E) else Color(0xFFF55252)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val u = user ?: return@Button
                    if (isClockedIn) {
                        clockService.checkOvertimeBeforeClockOut(u.id, u.workspaceId, plannedHoursToday) { isOvertime ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (isOvertime) {
                                    showOvertimeSheet = true
                                } else {
                                    val locationId = matchedOfficeByWiFi?.id ?: matchedOfficeByGPS?.id ?: "unknown"
                                    clockService.clockOut(u.id, u.workspaceId, locationId, managerId = u.managerId, plannedHours = plannedHoursToday)
                                }
                            }
                        }
                    } else {
                        if (!isLocationVerified) return@Button
                        val locationId = matchedOfficeByWiFi?.id ?: matchedOfficeByGPS?.id ?: "unknown"
                        clockService.clockIn(u.id, u.workspaceId, locationId)
                    }
                },
                enabled = (isLocationVerified || isClockedIn) && !isLoading,
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isClockedIn) Color(0xFFF55252) else Color(0xFF2DD47E),
                    disabledContainerColor = Color(0xFF2A2A40)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isClockedIn) "⏹" else "▶",
                            fontSize = 28.sp
                        )
                        Text(
                            text = if (isClockedIn) "Clock Out" else "Clock In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            if (!isLocationVerified && !isClockedIn) {
                Text(
                    text = "You must be at an office location to clock in",
                    fontSize = 12.sp,
                    color = Color(0xFF8888AA),
                    modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    if (showOvertimeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOvertimeSheet = false },
            containerColor = Color(0xFF0F0F1A)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Overtime Note", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "You have worked more than your planned hours today. Please explain why.",
                    fontSize = 14.sp,
                    color = Color(0xFF8888AA)
                )
                OutlinedTextField(
                    value = overtimeNote,
                    onValueChange = { overtimeNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF5B8EF5),
                        unfocusedBorderColor = Color(0xFF2A2A40),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Button(
                    onClick = {
                        val u = user ?: return@Button
                        val locationId = matchedOfficeByWiFi?.id ?: matchedOfficeByGPS?.id ?: "unknown"
                        clockService.clockOut(u.id, u.workspaceId, locationId, overtimeNote, u.managerId, plannedHoursToday)
                        showOvertimeSheet = false
                        overtimeNote = ""
                    },
                    enabled = overtimeNote.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B8EF5))
                ) {
                    Text("Submit", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}