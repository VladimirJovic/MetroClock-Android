package com.echoproduction.metroclock.ui.manager

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
import com.echoproduction.metroclock.ui.theme.McGreen
import com.echoproduction.metroclock.ui.theme.McRed
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
                                Text("📥", fontSize = 48.sp)
                                Text("Inbox is empty", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.textSecondary)
                                Text("Team requests will appear here", fontSize = 13.sp, color = LocalMcColors.current.textTertiary)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (pending.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp, start = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "PENDING",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = LocalMcColors.current.textTertiary
                                        )
                                        Text(
                                            "${pending.size}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = McOrange,
                                            modifier = Modifier
                                                .background(McOrange.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                items(pending) { request ->
                                    InboxRequestCard(request, employeeNames[request.userId] ?: "") { selectedRequest = request }
                                }
                            }
                            if (resolved.isNotEmpty()) {
                                item {
                                    Text(
                                        "RESOLVED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = LocalMcColors.current.textTertiary,
                                        modifier = Modifier.padding(top = if (pending.isNotEmpty()) 12.dp else 8.dp, bottom = 8.dp, start = 4.dp)
                                    )
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
    var typeExpanded by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var remoteHours by remember { mutableIntStateOf(8) }
    var remoteMinutes by remember { mutableIntStateOf(0) }
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
                selectedDayContainerColor = McOrange, todayDateBorderColor = McOrange, todayContentColor = McOrange
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
                selectedDayContainerColor = McOrange, todayDateBorderColor = McOrange, todayContentColor = McOrange
            ))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalMcColors.current.surface) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Text("New Request", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text) }

            // Type
            item {
                InboxSheetLabel("REQUEST TYPE")
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
                        value = typeLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Request Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = McOrange, unfocusedBorderColor = LocalMcColors.current.border,
                            focusedTextColor = LocalMcColors.current.text, unfocusedTextColor = LocalMcColors.current.text,
                            focusedLabelColor = McOrange, unfocusedLabelColor = LocalMcColors.current.textSecondary,
                            focusedTrailingIconColor = McOrange, unfocusedTrailingIconColor = LocalMcColors.current.textSecondary
                        )
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }, containerColor = LocalMcColors.current.surface) {
                        listOf(RequestType.REMOTE_WORK to "Remote Work", RequestType.SICK_LEAVE to "Sick Leave", RequestType.DAY_OFF to "Day Off", RequestType.OFFSITE_WORK to "Offsite Work").forEach { (type, label) ->
                            DropdownMenuItem(text = { Text(label, color = LocalMcColors.current.text) }, onClick = { selectedType = type; typeExpanded = false })
                        }
                    }
                }
            }

            // Dates
            item {
                InboxSheetLabel("DATES")
                Spacer(modifier = Modifier.height(8.dp))
                val fromLabel = if (selectedType == RequestType.REMOTE_WORK) "Date" else "From"
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(LocalMcColors.current.background, RoundedCornerShape(10.dp))
                        .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showDateFromPicker = true }.padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(fromLabel, fontSize = 14.sp, color = LocalMcColors.current.textSecondary)
                        Text(dateDisplayFmt.format(selectedDateFrom), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text)
                    }
                    if (selectedType != RequestType.REMOTE_WORK) {
                        HorizontalDivider(color = LocalMcColors.current.border)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showDateToPicker = true }.padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("To", fontSize = 14.sp, color = LocalMcColors.current.textSecondary)
                            Text(dateDisplayFmt.format(selectedDateTo), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text)
                        }
                    }
                }
            }

            // Remote hours
            if (selectedType == RequestType.REMOTE_WORK) {
                item {
                    InboxSheetLabel("HOURS")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(LocalMcColors.current.background, RoundedCornerShape(10.dp))
                            .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hours", fontSize = 11.sp, color = LocalMcColors.current.textSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(32.dp).background(McOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).clickable { if (remoteHours > 0) remoteHours-- }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, color = McOrange, fontWeight = FontWeight.Bold) }
                                Text("${remoteHours}h", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                                Box(modifier = Modifier.size(32.dp).background(McOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).clickable { if (remoteHours < 23) remoteHours++ }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp, color = McOrange, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Minutes", fontSize = 11.sp, color = LocalMcColors.current.textSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(32.dp).background(McOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).clickable { if (remoteMinutes >= 30) remoteMinutes -= 30 else if (remoteHours > 0) { remoteHours--; remoteMinutes = 30 } }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, color = McOrange, fontWeight = FontWeight.Bold) }
                                Text("%02dm".format(remoteMinutes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                                Box(modifier = Modifier.size(32.dp).background(McOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).clickable { if (remoteMinutes < 30) remoteMinutes = 30 else { remoteMinutes = 0; if (remoteHours < 23) remoteHours++ } }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp, color = McOrange, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            // Note
            item {
                InboxSheetLabel("NOTE")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text("Optional note…", color = LocalMcColors.current.textTertiary) },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = McOrange, unfocusedBorderColor = LocalMcColors.current.border,
                        focusedTextColor = LocalMcColors.current.text, unfocusedTextColor = LocalMcColors.current.text
                    )
                )
            }

            item {
                Button(
                    onClick = { val totalMinutes = remoteHours * 60 + remoteMinutes; onSubmit(selectedType, selectedDateFrom, selectedDateTo, note, totalMinutes) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = McOrange)
                ) { Text("Submit Request", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun InboxSheetLabel(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = LocalMcColors.current.textTertiary)
}

// MARK: - InboxRequestCard

@Composable
fun InboxRequestCard(request: Request, employeeName: String, onRespond: (() -> Unit)?) {
    val typeLabel = when (request.type) {
        RequestType.REMOTE_WORK -> request.remoteHours?.let { h ->
            val totalMin = (h * 60).toInt()
            "Remote Work · ${totalMin / 60}h ${totalMin % 60}m"
        } ?: "Remote Work"
        RequestType.SICK_LEAVE   -> "Sick Leave"
        RequestType.DAY_OFF      -> "Day Off"
        RequestType.OVERTIME     -> request.overtimeHours?.let { h -> "Overtime · ${"%.1f".format(h)}h" } ?: "Overtime"
        RequestType.OFFSITE_WORK -> "Offsite Work"
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

    val cardBorderColor = if (request.status == RequestStatus.PENDING)
        McOrange.copy(alpha = 0.3f) else LocalMcColors.current.border

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalMcColors.current.surface, RoundedCornerShape(12.dp))
            .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp))
    ) {
        // Main info row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(21.dp)),
                contentAlignment = Alignment.Center
            ) { Text(typeInitial, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = typeColor) }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(typeLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMcColors.current.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (employeeName.isNotEmpty()) {
                    Text(employeeName, fontSize = 12.sp, color = LocalMcColors.current.textSecondary, maxLines = 1)
                }
                Text(request.dateRangeLabel(), fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
            }

            // Status badge — ALL CAPS
            Text(
                text = statusLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = statusColor,
                maxLines = 1,
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        // Employee note
        request.employeeNote?.let { note ->
            if (note.isNotEmpty()) {
                HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top
                ) {
                    Text("💬", fontSize = 11.sp)
                    Text(note, fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
                }
            }
        }

        // Manager note (resolved)
        request.managerNote?.let { note ->
            if (note.isNotEmpty()) {
                HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top
                ) {
                    Text("↩", fontSize = 11.sp, color = LocalMcColors.current.textTertiary)
                    Text(note, fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
                }
            }
        }

        // Respond action buttons (pending only)
        if (onRespond != null) {
            HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Approve
                Button(
                    onClick = onRespond,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = McGreen.copy(alpha = 0.12f))
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✓", fontSize = 13.sp, color = McGreen, fontWeight = FontWeight.Bold)
                        Text("Approve", fontSize = 13.sp, color = McGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
                // Reject
                Button(
                    onClick = onRespond,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = McRed.copy(alpha = 0.10f))
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✕", fontSize = 13.sp, color = McRed, fontWeight = FontWeight.Bold)
                        Text("Reject", fontSize = 13.sp, color = McRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Respond to Request", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)

            // Decision buttons
            Column {
                InboxSheetLabel("DECISION")
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Approve button
                    val approveSelected = selectedStatus == RequestStatus.APPROVED
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .background(
                                if (approveSelected) McGreen else McGreen.copy(alpha = 0.08f),
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                if (approveSelected) McGreen else McGreen.copy(alpha = 0.3f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedStatus = RequestStatus.APPROVED },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = if (approveSelected) Color.White else McGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Approve", color = if (approveSelected) Color.White else McGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                    // Reject button
                    val rejectSelected = selectedStatus == RequestStatus.REJECTED
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .background(
                                if (rejectSelected) McRed else McRed.copy(alpha = 0.08f),
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                if (rejectSelected) McRed else McRed.copy(alpha = 0.3f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedStatus = RequestStatus.REJECTED },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✕", color = if (rejectSelected) Color.White else McRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Reject", color = if (rejectSelected) Color.White else McRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Note field
            Column {
                InboxSheetLabel("NOTE (REQUIRED)")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text("Add a note for the employee…", color = LocalMcColors.current.textTertiary) },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = McOrange, unfocusedBorderColor = LocalMcColors.current.border,
                        focusedTextColor = LocalMcColors.current.text, unfocusedTextColor = LocalMcColors.current.text
                    )
                )
            }

            Button(
                onClick = { onResolve(selectedStatus, note) },
                enabled = note.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = McOrange)
            ) { Text("Send Response", fontWeight = FontWeight.Bold, fontSize = 15.sp) }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
