package com.echoproduction.metroclock.ui.employee

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.models.Request
import com.echoproduction.metroclock.models.RequestStatus
import com.echoproduction.metroclock.models.RequestType
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.services.BadgeService
import com.echoproduction.metroclock.services.ExternalTask
import com.echoproduction.metroclock.services.TaskService
import com.echoproduction.metroclock.ui.theme.LocalMcColors
import com.echoproduction.metroclock.ui.theme.McOrange
import com.echoproduction.metroclock.ui.theme.McGreen
import com.echoproduction.metroclock.ui.theme.McRed
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    authService: AuthService,
    taskService: TaskService,
    badgeService: BadgeService? = null
) {
    val user by authService.currentUser.collectAsState()
    val db = FirebaseFirestore.getInstance()
    val tasks by taskService.tasks.collectAsState()
    val isAvailable by taskService.isAvailable.collectAsState()

    var requests by remember { mutableStateOf<List<Request>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showNewRequestSheet by remember { mutableStateOf(false) }

    fun loadRequests(onDone: () -> Unit = {}) {
        user?.let { u ->
            db.collection("requests")
                .whereEqualTo("userId", u.id)
                .get()
                .addOnSuccessListener { snapshot ->
                    requests = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val typeStr = (data["type"] as? String) ?: return@mapNotNull null
                        val statusStr = (data["status"] as? String) ?: return@mapNotNull null
                        val type = when (typeStr) {
                            "remoteWork"   -> RequestType.REMOTE_WORK
                            "sickLeave"    -> RequestType.SICK_LEAVE
                            "dayOff"       -> RequestType.DAY_OFF
                            "overtime"     -> RequestType.OVERTIME
                            "offsiteWork"  -> RequestType.OFFSITE_WORK
                            else -> return@mapNotNull null
                        }
                        val status = when (statusStr) {
                            "approved" -> RequestStatus.APPROVED
                            "rejected" -> RequestStatus.REJECTED
                            else -> RequestStatus.PENDING
                        }
                        val dateFrom = (data["dateFrom"] as? Timestamp) ?: (data["date"] as? Timestamp) ?: Timestamp.now()
                        val dateTo = (data["dateTo"] as? Timestamp) ?: dateFrom
                        Request(
                            id = doc.id,
                            userId = data["userId"] as? String ?: "",
                            workspaceId = data["workspaceId"] as? String ?: "",
                            managerId = data["managerId"] as? String ?: "",
                            type = type, status = status,
                            date = dateFrom,
                            dateFrom = dateFrom,
                            dateTo = dateTo,
                            employeeNote = data["employeeNote"] as? String,
                            managerNote = data["managerNote"] as? String,
                            createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
                            remoteHours = (data["remoteHours"] as? Double) ?: (data["remoteHours"] as? Long)?.toDouble()
                        )
                    }.sortedByDescending { it.createdAt.toDate() }
                    onDone()
                }
                .addOnFailureListener { onDone() }
        }
    }

    LaunchedEffect(user) {
        loadRequests { isLoading = false }
        taskService.refresh()
    }

    LaunchedEffect(requests) {
        val resolvedIds = requests.filter { it.status != RequestStatus.PENDING }.map { it.id }
        if (resolvedIds.isNotEmpty()) badgeService?.markAllResolvedAsSeen(resolvedIds)
    }

    Box(modifier = Modifier.fillMaxSize().background(LocalMcColors.current.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Requests", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(McOrange.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.dp, McOrange.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { showNewRequestSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 20.sp, color = McOrange, fontWeight = FontWeight.Bold)
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = McOrange)
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { isRefreshing = true; loadRequests { isRefreshing = false } },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (requests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📋", fontSize = 48.sp)
                                Text("No requests yet", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.textSecondary)
                                Text("Tap + to submit a request", fontSize = 13.sp, color = LocalMcColors.current.textTertiary)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(requests) { request -> RequestCard(request) }
                        }
                    }
                }
            }
        }

        if (showNewRequestSheet) {
            NewRequestBottomSheet(
                tasks = if (isAvailable) tasks else emptyList(),
                existingRequests = requests,
                onDismiss = { showNewRequestSheet = false },
                onSubmit = { type, dateFrom, dateTo, note, totalMinutes, taskIds ->
                    val u = user ?: return@NewRequestBottomSheet
                    val remoteHoursDouble = totalMinutes / 60.0
                    val data = hashMapOf<String, Any>(
                        "userId" to u.id,
                        "workspaceId" to u.workspaceId,
                        "managerId" to (u.managerId ?: ""),
                        "type" to when (type) {
                            RequestType.REMOTE_WORK  -> "remoteWork"
                            RequestType.SICK_LEAVE   -> "sickLeave"
                            RequestType.DAY_OFF      -> "dayOff"
                            RequestType.OVERTIME     -> "overtime"
                            RequestType.OFFSITE_WORK -> "offsiteWork"
                        },
                        "status" to "pending",
                        "date" to Timestamp(dateFrom),
                        "dateFrom" to Timestamp(dateFrom),
                        "dateTo" to Timestamp(dateTo),
                        "employeeNote" to note,
                        "createdAt" to Timestamp.now()
                    )
                    if (type == RequestType.REMOTE_WORK) data["remoteHours"] = remoteHoursDouble
                    if (taskIds.isNotEmpty()) data["taskIds"] = taskIds

                    val overlapping = requests.filter { req ->
                        req.type == type && type != RequestType.REMOTE_WORK && type != RequestType.OFFSITE_WORK &&
                                req.allDates().any { reqDate ->
                                    val d = Calendar.getInstance().apply { time = reqDate }.apply {
                                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                                    }.time
                                    val from = Calendar.getInstance().apply { time = dateFrom
                                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                                    }.time
                                    val to = Calendar.getInstance().apply { time = dateTo
                                        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                                    }.time
                                    d in from..to
                                }
                    }
                    // Notification sent automatically by Cloud Function (onRequestCreated)
                    val toDelete = overlapping.size
                    var deleted = 0
                    if (toDelete == 0) {
                        db.collection("requests").add(data).addOnSuccessListener {
                            showNewRequestSheet = false; loadRequests {}
                        }
                    } else {
                        overlapping.forEach { req ->
                            db.collection("requests").document(req.id).delete().addOnSuccessListener {
                                deleted++
                                if (deleted == toDelete) {
                                    db.collection("requests").add(data).addOnSuccessListener {
                                        showNewRequestSheet = false; loadRequests {}
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

// MARK: - Request extension for date range

fun Request.allDates(): List<Date> {
    val dates = mutableListOf<Date>()
    val cal = Calendar.getInstance()
    cal.time = dateFrom.toDate()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
    val end = Calendar.getInstance()
    end.time = dateTo.toDate()
    end.set(Calendar.HOUR_OF_DAY, 0); end.set(Calendar.MINUTE, 0); end.set(Calendar.SECOND, 0)
    while (!cal.time.after(end.time)) {
        dates.add(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return dates
}

fun Request.dateRangeLabel(): String {
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val from = dateFrom.toDate()
    val to = dateTo.toDate()
    val cal1 = Calendar.getInstance().apply { time = from }
    val cal2 = Calendar.getInstance().apply { time = to }
    val sameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) fmt.format(from) else "${fmt.format(from)} – ${fmt.format(to)}"
}

// MARK: - RequestCard

@Composable
fun RequestCard(request: Request) {
    val typeLabel = when (request.type) {
        RequestType.REMOTE_WORK -> {
            val h = request.remoteHours
            if (h != null) {
                val totalMin = (h * 60).toInt()
                "Remote Work · ${totalMin / 60}h ${if (totalMin % 60 > 0) " ${totalMin % 60}m" else ""}"
            } else "Remote Work"
        }
        RequestType.SICK_LEAVE   -> "Sick Leave"
        RequestType.DAY_OFF      -> "Day Off"
        RequestType.OVERTIME     -> "Overtime"
        RequestType.OFFSITE_WORK -> "Offsite Work"
    }
    val statusColor = when (request.status) {
        RequestStatus.APPROVED -> McGreen
        RequestStatus.REJECTED -> McRed
        RequestStatus.PENDING  -> McOrange
    }
    val statusLabel = when (request.status) {
        RequestStatus.APPROVED -> "APPROVED"
        RequestStatus.REJECTED -> "REJECTED"
        RequestStatus.PENDING  -> "PENDING"
    }
    val typeColor = when (request.type) {
        RequestType.REMOTE_WORK  -> McOrange
        RequestType.SICK_LEAVE   -> McRed
        RequestType.DAY_OFF      -> Color(0xFFF5A623)
        RequestType.OVERTIME     -> Color(0xFFA78BFA)
        RequestType.OFFSITE_WORK -> Color(0xFF6366F1)
    }
    val typeInitial = when (request.type) {
        RequestType.REMOTE_WORK  -> "R"
        RequestType.SICK_LEAVE   -> "S"
        RequestType.DAY_OFF      -> "D"
        RequestType.OVERTIME     -> "O"
        RequestType.OFFSITE_WORK -> "F"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalMcColors.current.surface, RoundedCornerShape(12.dp))
            .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(12.dp))
    ) {
        // Main row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(21.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(typeInitial, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = typeColor)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(typeLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text)
                Text(request.dateRangeLabel(), fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
            }
            // Status badge — ALL CAPS
            Text(
                text = statusLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = statusColor,
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        // Manager note (if any)
        request.managerNote?.let { note ->
            if (note.isNotEmpty()) {
                HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("↩", fontSize = 11.sp, color = LocalMcColors.current.textTertiary)
                    Text(note, fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
                }
            }
        }
    }
}

// MARK: - NewRequestBottomSheet

@Suppress("UNUSED_VALUE")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRequestBottomSheet(
    tasks: List<ExternalTask>,
    existingRequests: List<Request>,
    onDismiss: () -> Unit,
    onSubmit: (RequestType, Date, Date, String, Int, List<String>) -> Unit
) {
    var selectedType by remember { mutableStateOf(RequestType.REMOTE_WORK) }
    var typeExpanded by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var remoteHours by remember { mutableIntStateOf(8) }
    var remoteMinutes by remember { mutableIntStateOf(0) }
    var selectedDateFrom by remember { mutableStateOf(Date()) }
    var selectedDateTo by remember { mutableStateOf(Date()) }
    var selectedTasks by remember { mutableStateOf<Set<ExternalTask>>(emptySet()) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }
    val dateFromPickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateFrom.time)
    val dateToPickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateTo.time)
    val dateDisplayFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    val overlappingDays = remember(selectedType, selectedDateFrom, selectedDateTo, existingRequests) {
        if (selectedType == RequestType.REMOTE_WORK || selectedType == RequestType.OFFSITE_WORK) return@remember emptyList()
        existingRequests.filter { req ->
            req.type == selectedType && req.allDates().any { reqDate ->
                val d = Calendar.getInstance().apply { time = reqDate
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
                val from = Calendar.getInstance().apply { time = selectedDateFrom
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
                val to = Calendar.getInstance().apply { time = selectedDateTo
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.time
                d in from..to
            }
        }.flatMap { it.allDates() }.filter { reqDate ->
            val d = Calendar.getInstance().apply { time = reqDate
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
            val from = Calendar.getInstance().apply { time = selectedDateFrom
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
            val to = Calendar.getInstance().apply { time = selectedDateTo
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.time
            d in from..to
        }
    }

    if (showDateFromPicker) {
        DatePickerDialog(
            onDismissRequest = { showDateFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateFromPickerState.selectedDateMillis?.let {
                        selectedDateFrom = Date(it)
                        if (selectedDateTo.before(selectedDateFrom)) selectedDateTo = selectedDateFrom
                    }
                    showDateFromPicker = false
                }) { Text("OK", color = McOrange) }
            },
            dismissButton = { TextButton(onClick = { showDateFromPicker = false }) { Text("Cancel", color = LocalMcColors.current.textSecondary) } },
            colors = DatePickerDefaults.colors(containerColor = LocalMcColors.current.surface)
        ) {
            DatePicker(state = dateFromPickerState, colors = DatePickerDefaults.colors(
                containerColor = LocalMcColors.current.surface, titleContentColor = LocalMcColors.current.text,
                headlineContentColor = LocalMcColors.current.text, weekdayContentColor = LocalMcColors.current.textSecondary,
                subheadContentColor = LocalMcColors.current.textSecondary, dayContentColor = LocalMcColors.current.text,
                selectedDayContainerColor = McOrange, todayDateBorderColor = McOrange,
                todayContentColor = McOrange
            ))
        }
    }

    if (showDateToPicker) {
        DatePickerDialog(
            onDismissRequest = { showDateToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateToPickerState.selectedDateMillis?.let { selectedDateTo = Date(it) }
                    showDateToPicker = false
                }) { Text("OK", color = McOrange) }
            },
            dismissButton = { TextButton(onClick = { showDateToPicker = false }) { Text("Cancel", color = LocalMcColors.current.textSecondary) } },
            colors = DatePickerDefaults.colors(containerColor = LocalMcColors.current.surface)
        ) {
            DatePicker(state = dateToPickerState, colors = DatePickerDefaults.colors(
                containerColor = LocalMcColors.current.surface, titleContentColor = LocalMcColors.current.text,
                headlineContentColor = LocalMcColors.current.text, weekdayContentColor = LocalMcColors.current.textSecondary,
                subheadContentColor = LocalMcColors.current.textSecondary, dayContentColor = LocalMcColors.current.text,
                selectedDayContainerColor = McOrange, todayDateBorderColor = McOrange,
                todayContentColor = McOrange
            ))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalMcColors.current.surface) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("New Request", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
            }

            // ── Type selector ──────────────────────────────────────────────────
            item {
                SheetSectionLabel("REQUEST TYPE")
                Spacer(modifier = Modifier.height(8.dp))
                val typeLabel = when (selectedType) {
                    RequestType.REMOTE_WORK  -> "Remote Work"
                    RequestType.SICK_LEAVE   -> "Sick Leave"
                    RequestType.DAY_OFF      -> "Day Off"
                    RequestType.OFFSITE_WORK -> "Offsite Work"
                    RequestType.OVERTIME     -> "Overtime"
                }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = typeLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Request Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = McOrange,
                            unfocusedBorderColor = LocalMcColors.current.border,
                            focusedTextColor = LocalMcColors.current.text,
                            unfocusedTextColor = LocalMcColors.current.text,
                            focusedLabelColor = McOrange,
                            unfocusedLabelColor = LocalMcColors.current.textSecondary,
                            focusedTrailingIconColor = McOrange,
                            unfocusedTrailingIconColor = LocalMcColors.current.textSecondary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                        containerColor = LocalMcColors.current.surface
                    ) {
                        listOf(
                            RequestType.REMOTE_WORK  to "Remote Work",
                            RequestType.SICK_LEAVE   to "Sick Leave",
                            RequestType.DAY_OFF      to "Day Off",
                            RequestType.OFFSITE_WORK to "Offsite Work"
                        ).forEach { (type, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = LocalMcColors.current.text) },
                                onClick = { selectedType = type; typeExpanded = false }
                            )
                        }
                    }
                }
            }

            // ── Dates ──────────────────────────────────────────────────────────
            item {
                SheetSectionLabel("DATES")
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalMcColors.current.background, RoundedCornerShape(10.dp))
                        .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(10.dp))
                ) {
                    DateRow("From", dateDisplayFmt.format(selectedDateFrom)) { showDateFromPicker = true }
                    HorizontalDivider(color = LocalMcColors.current.border)
                    DateRow("To", dateDisplayFmt.format(selectedDateTo)) { showDateToPicker = true }
                }
            }

            // Overlap warning
            if (overlappingDays.isNotEmpty()) {
                item {
                    val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(McOrange.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .border(1.dp, McOrange.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠", fontSize = 14.sp, color = McOrange)
                        Text(
                            "Existing request covers: ${overlappingDays.take(3).joinToString(", ") { fmt.format(it) }}${if (overlappingDays.size > 3) "..." else ""}. Submitting will replace it.",
                            fontSize = 12.sp, color = LocalMcColors.current.textSecondary
                        )
                    }
                }
            }

            // ── Hours (remote only) ────────────────────────────────────────────
            if (selectedType == RequestType.REMOTE_WORK) {
                item {
                    SheetSectionLabel("HOURS PER DAY")
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LocalMcColors.current.background, RoundedCornerShape(10.dp))
                            .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Hours
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Hours", fontSize = 11.sp, color = LocalMcColors.current.textSecondary, letterSpacing = 0.5.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StepperButton("-") { if (remoteHours > 0) remoteHours-- }
                                    Text("${remoteHours}h", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                                    StepperButton("+") { if (remoteHours < 23) remoteHours++ }
                                }
                            }
                            // Minutes
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Minutes", fontSize = 11.sp, color = LocalMcColors.current.textSecondary, letterSpacing = 0.5.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StepperButton("-") {
                                        if (remoteMinutes >= 30) remoteMinutes -= 30
                                        else if (remoteHours > 0) { remoteHours--; remoteMinutes = 30 }
                                    }
                                    Text("%02dm".format(remoteMinutes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                                    StepperButton("+") {
                                        if (remoteMinutes < 30) remoteMinutes = 30
                                        else { remoteMinutes = 0; if (remoteHours < 23) remoteHours++ }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tasks
                if (tasks.isNotEmpty()) {
                    item {
                        SheetSectionLabel("TASKS (OPTIONAL)")
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LocalMcColors.current.background, RoundedCornerShape(10.dp))
                                .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(10.dp))
                        ) {
                            tasks.forEachIndexed { index, task ->
                                val isSelected = selectedTasks.contains(task)
                                if (index > 0) HorizontalDivider(color = LocalMcColors.current.border)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTasks = if (isSelected) selectedTasks - task else selectedTasks + task }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(20.dp)
                                            .background(if (isSelected) McOrange else LocalMcColors.current.border, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) { if (isSelected) Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                                    Text(task.displayName, fontSize = 13.sp, color = LocalMcColors.current.text, maxLines = 2, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Note ──────────────────────────────────────────────────────────
            item {
                SheetSectionLabel("NOTE")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text("Add a note for your manager…", color = LocalMcColors.current.textTertiary) },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = McOrange, unfocusedBorderColor = LocalMcColors.current.border,
                        focusedTextColor = LocalMcColors.current.text, unfocusedTextColor = LocalMcColors.current.text
                    )
                )
            }

            // ── Submit ────────────────────────────────────────────────────────
            item {
                Button(
                    onClick = {
                        val totalMinutes = remoteHours * 60 + remoteMinutes
                        onSubmit(selectedType, selectedDateFrom, selectedDateTo, note, totalMinutes, selectedTasks.map { it.id })
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = McOrange)
                ) { Text("Submit Request", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = LocalMcColors.current.textTertiary
    )
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = LocalMcColors.current.textSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text)
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(McOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 18.sp, color = McOrange, fontWeight = FontWeight.Bold)
    }
}
