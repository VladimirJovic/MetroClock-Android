package com.echoproduction.metroclock.ui.employee

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
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
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
                            "remoteWork" -> RequestType.REMOTE_WORK
                            "sickLeave"  -> RequestType.SICK_LEAVE
                            "dayOff"     -> RequestType.DAY_OFF
                            "overtime"   -> RequestType.OVERTIME
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

    // Mark resolved requests as seen when this screen loads/refreshes
    LaunchedEffect(requests) {
        val resolvedIds = requests.filter { it.status != RequestStatus.PENDING }.map { it.id }
        if (resolvedIds.isNotEmpty()) badgeService?.markAllResolvedAsSeen(resolvedIds)
    }

    Box(modifier = Modifier.fillMaxSize().background(LocalMcColors.current.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Requests", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                Button(
                    onClick = { showNewRequestSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = McOrange)
                ) { Text("+ New") }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = McOrange)
                }
            } else {
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing),
                    onRefresh = { isRefreshing = true; loadRequests { isRefreshing = false } }
                ) {
                    if (requests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No requests yet", color = LocalMcColors.current.textSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
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
                user = user,
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
                            RequestType.REMOTE_WORK -> "remoteWork"
                            RequestType.SICK_LEAVE  -> "sickLeave"
                            RequestType.DAY_OFF     -> "dayOff"
                            RequestType.OVERTIME    -> "overtime"
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

                    // Delete overlapping requests of same type
                    val overlapping = requests.filter { req ->
                        req.type == type && type != RequestType.REMOTE_WORK &&
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
                                    d >= from && d <= to
                                }
                    }
                    val typeLabel = when (type) {
                        RequestType.REMOTE_WORK -> "Remote Work"
                        RequestType.SICK_LEAVE  -> "Sick Leave"
                        RequestType.DAY_OFF     -> "Day Off"
                        RequestType.OVERTIME    -> "Overtime"
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
                "Remote Work · ${totalMin / 60}h ${totalMin % 60}m"
            } else "Remote Work"
        }
        RequestType.SICK_LEAVE -> "Sick Leave"
        RequestType.DAY_OFF    -> "Day Off"
        RequestType.OVERTIME   -> "Overtime"
    }
    val statusColor = when (request.status) {
        RequestStatus.APPROVED -> Color(0xFF2DD47E)
        RequestStatus.REJECTED -> Color(0xFFF55252)
        RequestStatus.PENDING  -> Color(0xFFF5A623)
    }
    val typeColor = when (request.type) {
        RequestType.REMOTE_WORK -> McOrange
        RequestType.SICK_LEAVE  -> Color(0xFFF55252)
        RequestType.DAY_OFF     -> Color(0xFFF5A623)
        RequestType.OVERTIME    -> Color(0xFFA78BFA)
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(LocalMcColors.current.surface, RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).background(typeColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (request.type) {
                            RequestType.REMOTE_WORK -> "R"
                            RequestType.SICK_LEAVE  -> "S"
                            RequestType.DAY_OFF     -> "D"
                            RequestType.OVERTIME    -> "O"
                        },
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = typeColor
                    )
                }
                Column {
                    Text(typeLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text)
                    Text(request.dateRangeLabel(), fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
                }
            }
            Text(
                text = request.status.name.lowercase().replaceFirstChar { it.uppercase() },
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = statusColor,
                modifier = Modifier.background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        request.employeeNote?.let {
            if (it.isNotEmpty()) { Spacer(modifier = Modifier.height(8.dp)); Text(it, fontSize = 12.sp, color = LocalMcColors.current.textSecondary) }
        }
        request.managerNote?.let {
            if (it.isNotEmpty()) { Spacer(modifier = Modifier.height(4.dp)); Text("Manager: $it", fontSize = 12.sp, color = LocalMcColors.current.textSecondary) }
        }
    }
}

// MARK: - NewRequestBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRequestBottomSheet(
    user: com.echoproduction.metroclock.models.MCUser?,
    tasks: List<ExternalTask>,
    existingRequests: List<Request>,
    onDismiss: () -> Unit,
    onSubmit: (RequestType, Date, Date, String, Int, List<String>) -> Unit
) {
    var selectedType by remember { mutableStateOf(RequestType.REMOTE_WORK) }
    var note by remember { mutableStateOf("") }
    var remoteHours by remember { mutableStateOf(8) }
    var remoteMinutes by remember { mutableStateOf(0) }
    var selectedDateFrom by remember { mutableStateOf(Date()) }
    var selectedDateTo by remember { mutableStateOf(Date()) }
    var selectedTasks by remember { mutableStateOf<Set<ExternalTask>>(emptySet()) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }
    var showOverlapWarning by remember { mutableStateOf(false) }

    val dateFromPickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateFrom.time)
    val dateToPickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateTo.time)
    val dateDisplayFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    val overlappingDays = remember(selectedType, selectedDateFrom, selectedDateTo, existingRequests) {
        if (selectedType == RequestType.REMOTE_WORK) return@remember emptyList()
        existingRequests.filter { req ->
            req.type == selectedType && req.allDates().any { reqDate ->
                val d = Calendar.getInstance().apply { time = reqDate
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
                val from = Calendar.getInstance().apply { time = selectedDateFrom
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
                val to = Calendar.getInstance().apply { time = selectedDateTo
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.time
                d >= from && d <= to
            }
        }.flatMap { it.allDates() }.filter { reqDate ->
            val d = Calendar.getInstance().apply { time = reqDate
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
            val from = Calendar.getInstance().apply { time = selectedDateFrom
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
            val to = Calendar.getInstance().apply { time = selectedDateTo
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.time
            d >= from && d <= to
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
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("New Request", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
            }

            // Type selector
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RequestType.REMOTE_WORK to "Remote",
                        RequestType.SICK_LEAVE to "Sick Leave",
                        RequestType.DAY_OFF to "Day Off"
                    ).forEach { (type, label) ->
                        val selected = selectedType == type
                        Button(
                            onClick = { selectedType = type },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (selected) McOrange else LocalMcColors.current.border),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                        ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    }
                }
            }

            // Date from
            item {
                val dateLabel = "From"
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(LocalMcColors.current.background, RoundedCornerShape(10.dp))
                        .clickable { showDateFromPicker = true }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(dateLabel, fontSize = 14.sp, color = LocalMcColors.current.textSecondary)
                    Text(dateDisplayFmt.format(selectedDateFrom), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text)
                }
            }

            // Date to
            if (true) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(LocalMcColors.current.background, RoundedCornerShape(10.dp))
                            .clickable { showDateToPicker = true }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("To", fontSize = 14.sp, color = LocalMcColors.current.textSecondary)
                        Text(dateDisplayFmt.format(selectedDateTo), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text)
                    }
                }

                // Overlap warning
                if (overlappingDays.isNotEmpty()) {
                    item {
                        val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0xFFF5A623).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("!", fontSize = 14.sp, color = Color(0xFFF5A623), fontWeight = FontWeight.Bold)
                            Text(
                                "Existing request covers: ${overlappingDays.take(3).joinToString(", ") { fmt.format(it) }}${if (overlappingDays.size > 3) "..." else ""}. Submitting will replace it.",
                                fontSize = 12.sp, color = LocalMcColors.current.textSecondary
                            )
                        }
                    }
                }
            }

            // Hours (remote only)
            if (selectedType == RequestType.REMOTE_WORK) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Duration", fontSize = 14.sp, color = LocalMcColors.current.textSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Hours", fontSize = 11.sp, color = LocalMcColors.current.textSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { if (remoteHours > 0) remoteHours-- }) {
                                        Text("−", fontSize = 20.sp, color = McOrange)
                                    }
                                    Text("${remoteHours}h", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                                    IconButton(onClick = { if (remoteHours < 23) remoteHours++ }) {
                                        Text("+", fontSize = 20.sp, color = McOrange)
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Minutes", fontSize = 11.sp, color = LocalMcColors.current.textSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = {
                                        if (remoteMinutes >= 30) remoteMinutes -= 30
                                        else if (remoteHours > 0) { remoteHours--; remoteMinutes = 30 }
                                    }) { Text("−", fontSize = 20.sp, color = McOrange) }
                                    Text("%02dm".format(remoteMinutes), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                                    IconButton(onClick = {
                                        if (remoteMinutes < 30) remoteMinutes = 30
                                        else { remoteMinutes = 0; if (remoteHours < 23) remoteHours++ }
                                    }) { Text("+", fontSize = 20.sp, color = McOrange) }
                                }
                            }
                        }
                    }
                }

                // Tasks
                if (tasks.isNotEmpty()) {
                    item {
                        Text("Tasks (optional)", fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
                    }
                    items(tasks) { task ->
                        val isSelected = selectedTasks.contains(task)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { selectedTasks = if (isSelected) selectedTasks - task else selectedTasks + task }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp)
                                    .background(if (isSelected) McOrange else LocalMcColors.current.border, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) { if (isSelected) Text("✓", fontSize = 12.sp, color = Color.White) }
                            Text(task.displayName, fontSize = 13.sp, color = LocalMcColors.current.text, maxLines = 2, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(color = LocalMcColors.current.border)
                    }
                }
            }

            // Note
            item {
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note", color = LocalMcColors.current.textSecondary) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = McOrange, unfocusedBorderColor = LocalMcColors.current.border,
                        focusedTextColor = LocalMcColors.current.text, unfocusedTextColor = LocalMcColors.current.text
                    )
                )
            }

            // Submit
            item {
                Button(
                    onClick = {
                        val totalMinutes = remoteHours * 60 + remoteMinutes
                        onSubmit(selectedType, selectedDateFrom, selectedDateTo, note, totalMinutes, selectedTasks.map { it.id })
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = McOrange)
                ) { Text("Submit Request", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
