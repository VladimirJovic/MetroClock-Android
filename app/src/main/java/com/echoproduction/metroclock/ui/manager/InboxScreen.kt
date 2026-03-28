package com.echoproduction.metroclock.ui.manager

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.models.Request
import com.echoproduction.metroclock.models.RequestStatus
import com.echoproduction.metroclock.models.RequestType
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.ui.employee.dateRangeLabel
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
fun InboxScreen(authService: AuthService) {
    val user by authService.currentUser.collectAsState()
    val db = FirebaseFirestore.getInstance()

    var requests by remember { mutableStateOf<List<Request>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedRequest by remember { mutableStateOf<Request?>(null) }
    var employeeNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showNewRequestSheet by remember { mutableStateOf(false) }

    fun loadRequests(onDone: () -> Unit = {}) {
        user?.let { u ->
            db.collection("requests")
                .whereEqualTo("managerId", u.id)
                .get()
                .addOnSuccessListener { snapshot ->
                    val loaded = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val typeStr = data["type"] as? String ?: return@mapNotNull null
                        val statusStr = data["status"] as? String ?: return@mapNotNull null
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
                            remoteHours = (data["remoteHours"] as? Double) ?: (data["remoteHours"] as? Long)?.toDouble(),
                            overtimeHours = (data["overtimeHours"] as? Double) ?: (data["overtimeHours"] as? Long)?.toDouble()
                        )
                    }.sortedByDescending { it.createdAt.toDate() }
                    requests = loaded
                    loaded.map { it.userId }.distinct().forEach { uid ->
                        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                            val first = doc.data?.get("firstName") as? String ?: ""
                            val last = doc.data?.get("lastName") as? String ?: ""
                            employeeNames = employeeNames + (uid to "$first $last")
                        }
                    }
                    onDone()
                }
                .addOnFailureListener { onDone() }
        }
    }

    LaunchedEffect(user) { loadRequests { isLoading = false } }

    val pending = requests.filter { it.status == RequestStatus.PENDING }
    val resolved = requests.filter { it.status != RequestStatus.PENDING }
    val hasManager = user?.managerId?.isNotEmpty() == true

    Box(modifier = Modifier.fillMaxSize().background(LocalMcColors.current.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Inbox", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                if (hasManager) {
                    Button(
                        onClick = { showNewRequestSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = McOrange),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("+ New", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
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
                            Text("No requests", color = LocalMcColors.current.textSecondary)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (pending.isNotEmpty()) {
                                item {
                                    Text("Pending (${pending.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF5A623), modifier = Modifier.padding(vertical = 4.dp))
                                }
                                items(pending) { request ->
                                    InboxRequestCard(request, employeeNames[request.userId] ?: "") { selectedRequest = request }
                                }
                            }
                            if (resolved.isNotEmpty()) {
                                item {
                                    Text("Resolved", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = LocalMcColors.current.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                                }
                                items(resolved) { request ->
                                    InboxRequestCard(request, employeeNames[request.userId] ?: "", null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedRequest?.let { request ->
        ResolveSheet(request = request, onDismiss = { selectedRequest = null }) { status, note ->
            val updateMap: MutableMap<String, Any> = mutableMapOf("status" to status.name.lowercase(), "managerNote" to note)
            if (status == RequestStatus.APPROVED) updateMap["approvedAt"] = com.google.firebase.Timestamp(java.util.Date())
            db.collection("requests").document(request.id)
                .update(updateMap)
                .addOnSuccessListener {
                    requests = requests.map { if (it.id == request.id) it.copy(status = status, managerNote = note) else it }
                    selectedRequest = null
                    val typeLabel = when (request.type) {
                        RequestType.REMOTE_WORK -> "Remote Work"
                        RequestType.SICK_LEAVE  -> "Sick Leave"
                        RequestType.DAY_OFF     -> "Day Off"
                        RequestType.OVERTIME    -> "Overtime"
                    }
                    // Notification sent automatically by Cloud Function (onRequestUpdated)
                }
        }
    }

    if (showNewRequestSheet) {
        ManagerNewRequestSheet(
            user = user,
            onDismiss = { showNewRequestSheet = false },
            onSubmit = { type, dateFrom, dateTo, note, totalMinutes ->
                val u = user ?: return@ManagerNewRequestSheet
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
                db.collection("requests").add(data).addOnSuccessListener {
                    showNewRequestSheet = false
                }
            }
        )
    }
}

// MARK: - ManagerNewRequestSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerNewRequestSheet(
    user: com.echoproduction.metroclock.models.MCUser?,
    onDismiss: () -> Unit,
    onSubmit: (RequestType, Date, Date, String, Int) -> Unit
) {
    var selectedType by remember { mutableStateOf(RequestType.REMOTE_WORK) }
    var note by remember { mutableStateOf("") }
    var remoteHours by remember { mutableStateOf(8) }
    var remoteMinutes by remember { mutableStateOf(0) }
    var selectedDateFrom by remember { mutableStateOf(Date()) }
    var selectedDateTo by remember { mutableStateOf(Date()) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }

    val dateFromPickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateFrom.time)
    val dateToPickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateTo.time)
    val dateDisplayFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

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
            item { Text("New Request", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text) }

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

            item {
                val fromLabel = if (selectedType == RequestType.REMOTE_WORK) "Date" else "From"
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(LocalMcColors.current.background, RoundedCornerShape(10.dp))
                        .clickable { showDateFromPicker = true }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fromLabel, fontSize = 14.sp, color = LocalMcColors.current.textSecondary)
                    Text(dateDisplayFmt.format(selectedDateFrom), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text)
                }
            }

            if (selectedType != RequestType.REMOTE_WORK) {
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
            }

            if (selectedType == RequestType.REMOTE_WORK) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Duration", fontSize = 14.sp, color = LocalMcColors.current.textSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Hours", fontSize = 11.sp, color = LocalMcColors.current.textSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { if (remoteHours > 0) remoteHours-- }) { Text("−", fontSize = 20.sp, color = McOrange) }
                                    Text("${remoteHours}h", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                                    IconButton(onClick = { if (remoteHours < 23) remoteHours++ }) { Text("+", fontSize = 20.sp, color = McOrange) }
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
            }

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

            item {
                Button(
                    onClick = {
                        val totalMinutes = remoteHours * 60 + remoteMinutes
                        onSubmit(selectedType, selectedDateFrom, selectedDateTo, note, totalMinutes)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = McOrange)
                ) { Text("Submit Request", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// MARK: - InboxRequestCard

@Composable
fun InboxRequestCard(request: Request, employeeName: String, onRespond: (() -> Unit)?) {
    val typeLabel = when (request.type) {
        RequestType.REMOTE_WORK -> request.remoteHours?.let { h ->
            val totalMin = (h * 60).toInt()
            "Remote Work · ${totalMin / 60}h ${totalMin % 60}m"
        } ?: "Remote Work"
        RequestType.SICK_LEAVE -> "Sick Leave"
        RequestType.DAY_OFF    -> "Day Off"
        RequestType.OVERTIME   -> request.overtimeHours?.let { h ->
            "Overtime · ${String.format("%.1f", h)}h"
        } ?: "Overtime"
    }
    val typeColor = when (request.type) {
        RequestType.REMOTE_WORK -> McOrange
        RequestType.SICK_LEAVE  -> Color(0xFFF55252)
        RequestType.DAY_OFF     -> Color(0xFFF5A623)
        RequestType.OVERTIME    -> Color(0xFFA78BFA)
    }
    val typeInitial = when (request.type) {
        RequestType.REMOTE_WORK -> "R"
        RequestType.SICK_LEAVE  -> "S"
        RequestType.DAY_OFF     -> "D"
        RequestType.OVERTIME    -> "O"
    }
    val statusColor = when (request.status) {
        RequestStatus.APPROVED -> Color(0xFF2DD47E)
        RequestStatus.REJECTED -> Color(0xFFF55252)
        RequestStatus.PENDING  -> Color(0xFFF5A623)
    }
    val statusLabel = when (request.status) {
        RequestStatus.APPROVED -> "Approved"
        RequestStatus.REJECTED -> "Rejected"
        RequestStatus.PENDING  -> "Pending"
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(LocalMcColors.current.surface, RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).background(typeColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Text(typeInitial, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = typeColor) }

            Column(modifier = Modifier.weight(1f)) {
                Text(typeLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (employeeName.isNotEmpty()) {
                    Text(employeeName, fontSize = 12.sp, color = LocalMcColors.current.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(request.dateRangeLabel(), fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
            }

            Text(
                text = statusLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = statusColor, maxLines = 1,
                modifier = Modifier.background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        request.employeeNote?.let {
            if (it.isNotEmpty()) { Spacer(modifier = Modifier.height(8.dp)); Text(it, fontSize = 12.sp, color = LocalMcColors.current.textSecondary) }
        }
        request.managerNote?.let {
            if (it.isNotEmpty()) { Spacer(modifier = Modifier.height(4.dp)); Text("Manager: $it", fontSize = 12.sp, color = LocalMcColors.current.textSecondary) }
        }

        if (onRespond != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRespond,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = McOrange.copy(alpha = 0.12f))
            ) { Text("Respond", fontSize = 13.sp, color = McOrange, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// MARK: - ResolveSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolveSheet(request: Request, onDismiss: () -> Unit, onResolve: (RequestStatus, String) -> Unit) {
    var note by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(RequestStatus.APPROVED) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalMcColors.current.surface) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Respond to Request", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(RequestStatus.APPROVED, RequestStatus.REJECTED).forEach { status ->
                    val selected = selectedStatus == status
                    val color = if (status == RequestStatus.APPROVED) Color(0xFF2DD47E) else Color(0xFFF55252)
                    Button(
                        onClick = { selectedStatus = status },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (selected) color else LocalMcColors.current.border)
                    ) {
                        Text(
                            text = if (status == RequestStatus.APPROVED) "Approve" else "Reject",
                            color = if (selected) Color.White else LocalMcColors.current.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note", color = LocalMcColors.current.textSecondary) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = McOrange, unfocusedBorderColor = LocalMcColors.current.border,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                )
            )

            Button(
                onClick = { onResolve(selectedStatus, note) },
                enabled = note.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = McOrange)
            ) { Text("Send", fontWeight = FontWeight.SemiBold) }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
