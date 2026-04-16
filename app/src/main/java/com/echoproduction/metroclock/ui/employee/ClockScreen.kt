@file:OptIn(ExperimentalMaterial3Api::class)

package com.echoproduction.metroclock.ui.employee

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.models.Request
import com.echoproduction.metroclock.models.RequestStatus
import com.echoproduction.metroclock.models.RequestType
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.services.ClockService
import com.echoproduction.metroclock.services.ExternalTask
import com.echoproduction.metroclock.services.TaskService
import com.echoproduction.metroclock.services.WorkspaceService
import com.echoproduction.metroclock.ui.components.FlipClockRow
import com.echoproduction.metroclock.ui.components.HolographicButton
import com.echoproduction.metroclock.ui.components.LocationIndicator
import com.echoproduction.metroclock.ui.components.StatusChip
import com.echoproduction.metroclock.ui.theme.LocalMcColors
import com.echoproduction.metroclock.ui.theme.McOrange
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ClockScreen(
    authService: AuthService,
    clockService: ClockService,
    workspaceService: WorkspaceService,
    taskService: TaskService,
    currentSSID: String?
) {
    val user by authService.currentUser.collectAsState()
    val isClockedIn by clockService.isClockedIn.collectAsState()
    val isLoading by clockService.isLoading.collectAsState()
    val offices by workspaceService.offices.collectAsState()
    val tasks by taskService.tasks.collectAsState()
    val isAvailable by taskService.isAvailable.collectAsState()
    val tasksLoading by taskService.isLoading.collectAsState()

    var currentTime by remember { mutableStateOf(Date()) }
    var showOvertimeSheet by remember { mutableStateOf(false) }
    var showTaskSheet by remember { mutableStateOf(false) }
    var showDayOffAlert by remember { mutableStateOf(false) }
    var dayOffAlertMessage by remember { mutableStateOf("") }
    var conflictingRequest by remember { mutableStateOf<Request?>(null) }
    var overtimeNote by remember { mutableStateOf("") }
    var selectedTasks by remember { mutableStateOf<Set<ExternalTask>>(emptySet()) }

    val permissionsState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    LaunchedEffect(Unit) { while (true) { currentTime = Date(); delay(1000) } }

    LaunchedEffect(user) {
        user?.let {
            clockService.fetchTodayEvents(it.id)
            workspaceService.fetchOffices(it.workspaceId)
            if (!permissionsState.allPermissionsGranted) permissionsState.launchMultiplePermissionRequest()
            taskService.refresh()
        }
    }

    val matchedOfficeByWiFi = offices.firstOrNull { it.ssid == currentSSID && !currentSSID.isNullOrEmpty() }
    val matchedOfficeByGPS = workspaceService.nearestOffice
    val isLocationVerified = matchedOfficeByWiFi != null || matchedOfficeByGPS != null

    val locationLabel = when {
        matchedOfficeByWiFi != null -> "${matchedOfficeByWiFi.name} — WiFi Verified"
        matchedOfficeByGPS != null  -> "${matchedOfficeByGPS.name} — GPS Verified"
        else -> "Outside Office"
    }

    val plannedHoursToday: Double = run {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
        user?.dailyHours?.get(dow.toString()) ?: 0.0
    }

    val workedToday: String = run {
        val lastIn = clockService.lastClockIn ?: return@run "0h 0m"
        val seconds = ((Date().time - lastIn.timestamp.toDate().time) / 1000).toInt()
        "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEEE — d MMMM yyyy", Locale.getDefault())

    val timeString = timeFormat.format(currentTime)
    val dateString = dateFormat.format(currentTime).uppercase()

    val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hourOfDay < 12 -> "GOOD MORNING"
        hourOfDay < 17 -> "GOOD AFTERNOON"
        else           -> "GOOD EVENING"
    }
    val firstName = user?.firstName?.uppercase() ?: ""

    // Check day off / sick leave conflict for today
    fun checkDayOffConflict(onNone: () -> Unit) {
        val u = user ?: return
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        FirebaseFirestore.getInstance().collection("requests")
            .whereEqualTo("userId", u.id)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { snapshot ->
                val conflict = snapshot.documents.firstNotNullOfOrNull { doc ->
                    val data = doc.data ?: return@firstNotNullOfOrNull null
                    val typeStr = data["type"] as? String ?: return@firstNotNullOfOrNull null
                    if (typeStr != "dayOff" && typeStr != "sickLeave") return@firstNotNullOfOrNull null
                    val dateFrom = (data["dateFrom"] as? Timestamp)?.toDate()
                        ?: (data["date"] as? Timestamp)?.toDate() ?: return@firstNotNullOfOrNull null
                    val dateTo = (data["dateTo"] as? Timestamp)?.toDate() ?: dateFrom
                    val fromDay = Calendar.getInstance().apply { time = dateFrom
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
                    val toDay = Calendar.getInstance().apply { time = dateTo
                        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.time
                    if (today >= fromDay && today <= toDay) {
                        val reqType = if (typeStr == "dayOff") RequestType.DAY_OFF else RequestType.SICK_LEAVE
                        val status = RequestStatus.APPROVED
                        Request(
                            id = doc.id,
                            userId = data["userId"] as? String ?: "",
                            workspaceId = data["workspaceId"] as? String ?: "",
                            managerId = data["managerId"] as? String ?: "",
                            type = reqType, status = status,
                            date = Timestamp(dateFrom),
                            dateFrom = Timestamp(dateFrom),
                            dateTo = Timestamp(dateTo),
                            createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now()
                        )
                    } else null
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (conflict != null) {
                        val label = if (conflict.type == RequestType.DAY_OFF) "Day Off" else "Sick Leave"
                        dayOffAlertMessage = "Today is marked as $label. Clocking in will remove today from that request."
                        conflictingRequest = conflict
                        showDayOffAlert = true
                    } else {
                        onNone()
                    }
                }
            }
    }

    // Split request excluding today
    fun splitRequestExcludingToday(request: Request, onDone: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        db.collection("requests").document(request.id).delete().addOnSuccessListener {
            val newRequests = mutableListOf<Map<String, Any>>()

            val fromDay = Calendar.getInstance().apply { time = request.dateFrom.toDate()
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
            val toDay = Calendar.getInstance().apply { time = request.dateTo.toDate()
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
            val dayBefore = Calendar.getInstance().apply { time = today; add(Calendar.DAY_OF_YEAR, -1) }.time
            val dayAfter = Calendar.getInstance().apply { time = today; add(Calendar.DAY_OF_YEAR, 1) }.time

            fun makeData(dateFrom: Date, dateTo: Date): Map<String, Any> = mapOf(
                "userId" to request.userId,
                "workspaceId" to request.workspaceId,
                "managerId" to request.managerId,
                "type" to if (request.type == RequestType.DAY_OFF) "dayOff" else "sickLeave",
                "status" to "approved",
                "date" to Timestamp(dateFrom),
                "dateFrom" to Timestamp(dateFrom),
                "dateTo" to Timestamp(dateTo),
                "createdAt" to Timestamp.now()
            )

            if (fromDay < today) newRequests.add(makeData(request.dateFrom.toDate(), dayBefore))
            if (toDay > today) newRequests.add(makeData(dayAfter, request.dateTo.toDate()))

            if (newRequests.isEmpty()) { onDone(); return@addOnSuccessListener }

            var done = 0
            newRequests.forEach { data ->
                db.collection("requests").add(data).addOnSuccessListener {
                    done++
                    if (done == newRequests.size) onDone()
                }
            }
        }
    }

    fun proceedWithClockIn() {
        val u = user ?: return
        val locationId = matchedOfficeByWiFi?.id ?: matchedOfficeByGPS?.id ?: "unknown"
        if (isAvailable && tasks.isNotEmpty()) {
            showTaskSheet = true
        } else {
            clockService.clockIn(u.id, u.workspaceId, locationId, taskIds = selectedTasks.map { it.id }.ifEmpty { null })
        }
    }

    fun handleClockIn() {
        if (!isLocationVerified) return
        checkDayOffConflict { proceedWithClockIn() }
    }

    // ── Precision UI ─────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalMcColors.current.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = greeting,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = LocalMcColors.current.textSecondary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = firstName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalMcColors.current.text,
                        letterSpacing = 1.sp
                    )
                }
                StatusChip(isClockedIn = isClockedIn)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Flip clock
            FlipClockRow(currentTime = timeString)

            Spacer(modifier = Modifier.height(10.dp))

            // Date string
            Text(
                text = dateString,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = LocalMcColors.current.textTertiary,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center
            )

            // Worked today (if clocked in)
            if (isClockedIn) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workedToday,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = LocalMcColors.current.textSecondary,
                    letterSpacing = 1.sp
                )
            }

            // Selected tasks
            if (selectedTasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalMcColors.current.surface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "WORKING ON",
                        fontSize = 9.sp,
                        color = LocalMcColors.current.textTertiary,
                        letterSpacing = 1.5.sp
                    )
                    selectedTasks.forEach { task ->
                        Text(
                            text = task.displayName,
                            fontSize = 12.sp,
                            color = LocalMcColors.current.text,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Holographic clock-in button
            HolographicButton(
                isClockedIn = isClockedIn,
                isEnabled = isLocationVerified || isClockedIn,
                isLoading = isLoading,
                onClick = {
                    val u = user ?: return@HolographicButton
                    if (isClockedIn) {
                        clockService.checkOvertimeBeforeClockOut(u.id, u.workspaceId, plannedHoursToday) { isOvertime ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (isOvertime) showOvertimeSheet = true
                                else {
                                    val locationId = matchedOfficeByWiFi?.id ?: matchedOfficeByGPS?.id ?: "unknown"
                                    clockService.clockOut(u.id, u.workspaceId, locationId, managerId = u.managerId, plannedHours = plannedHoursToday)
                                }
                            }
                        }
                    } else {
                        handleClockIn()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Location indicator
            if (isLocationVerified) {
                LocationIndicator(text = locationLabel)
            } else {
                Text(
                    text = "OUTSIDE OFFICE — LOCATION REQUIRED",
                    fontSize = 11.sp,
                    color = McOrange.copy(alpha = 0.6f),
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // ── Day Off / Sick Leave alert ────────────────────────────────────────────
    if (showDayOffAlert) {
        AlertDialog(
            onDismissRequest = { showDayOffAlert = false },
            containerColor = LocalMcColors.current.surface,
            title = {
                Text("Day Off / Sick Leave", color = LocalMcColors.current.text, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(dayOffAlertMessage, color = LocalMcColors.current.textSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    showDayOffAlert = false
                    val req = conflictingRequest
                    if (req != null) {
                        splitRequestExcludingToday(req) { proceedWithClockIn() }
                    } else {
                        proceedWithClockIn()
                    }
                    conflictingRequest = null
                }) {
                    Text("Clock In Anyway", color = McOrange, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDayOffAlert = false; conflictingRequest = null }) {
                    Text("Cancel", color = LocalMcColors.current.textSecondary)
                }
            }
        )
    }

    // ── Task Selection Sheet ──────────────────────────────────────────────────
    if (showTaskSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTaskSheet = false },
            containerColor = LocalMcColors.current.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    "What are you working on?",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Select tasks you're working on",
                    fontSize = 13.sp, color = LocalMcColors.current.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (tasksLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = McOrange)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(tasks) { task ->
                            val isSelected = selectedTasks.contains(task)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        selectedTasks = if (isSelected) selectedTasks - task else selectedTasks + task
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            if (isSelected) McOrange else LocalMcColors.current.border,
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Text(
                                        "✓", fontSize = 12.sp, color = Color.White
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        task.displayName,
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        color = LocalMcColors.current.text, maxLines = 2
                                    )
                                    if (task.status.isNotEmpty()) Text(
                                        task.status, fontSize = 11.sp, color = LocalMcColors.current.textSecondary
                                    )
                                }
                            }
                            HorizontalDivider(color = LocalMcColors.current.border)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showTaskSheet = false
                            val u = user ?: return@OutlinedButton
                            val locationId = matchedOfficeByWiFi?.id ?: matchedOfficeByGPS?.id ?: "unknown"
                            clockService.clockIn(u.id, u.workspaceId, locationId, taskIds = null)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalMcColors.current.textSecondary)
                    ) { Text("Skip") }

                    Button(
                        onClick = {
                            showTaskSheet = false
                            val u = user ?: return@Button
                            val locationId = matchedOfficeByWiFi?.id ?: matchedOfficeByGPS?.id ?: "unknown"
                            clockService.clockIn(
                                u.id, u.workspaceId, locationId,
                                taskIds = selectedTasks.map { it.id }.ifEmpty { null }
                            )
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = McOrange)
                    ) { Text("Clock In", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ── Overtime Sheet ────────────────────────────────────────────────────────
    if (showOvertimeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOvertimeSheet = false },
            containerColor = LocalMcColors.current.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Overtime Note",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text
                )
                Text(
                    "You have worked more than your planned hours today. Please explain why.",
                    fontSize = 14.sp, color = LocalMcColors.current.textSecondary
                )
                OutlinedTextField(
                    value = overtimeNote,
                    onValueChange = { overtimeNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = McOrange,
                        unfocusedBorderColor = LocalMcColors.current.border,
                        focusedTextColor = LocalMcColors.current.text,
                        unfocusedTextColor = LocalMcColors.current.text
                    )
                )
                Button(
                    onClick = {
                        val u = user ?: return@Button
                        val locationId = matchedOfficeByWiFi?.id ?: matchedOfficeByGPS?.id ?: "unknown"
                        clockService.clockOut(
                            u.id, u.workspaceId, locationId,
                            overtimeNote, u.managerId, plannedHoursToday
                        )
                        showOvertimeSheet = false
                        overtimeNote = ""
                    },
                    enabled = overtimeNote.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = McOrange)
                ) { Text("Submit", fontWeight = FontWeight.SemiBold) }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
