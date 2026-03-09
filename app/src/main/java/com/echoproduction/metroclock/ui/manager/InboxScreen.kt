package com.echoproduction.metroclock.ui.manager

import androidx.compose.foundation.background
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
                            "sickLeave" -> RequestType.SICK_LEAVE
                            "dayOff" -> RequestType.DAY_OFF
                            "overtime" -> RequestType.OVERTIME
                            else -> return@mapNotNull null
                        }
                        val status = when (statusStr) {
                            "approved" -> RequestStatus.APPROVED
                            "rejected" -> RequestStatus.REJECTED
                            else -> RequestStatus.PENDING
                        }
                        Request(
                            id = doc.id,
                            userId = data["userId"] as? String ?: "",
                            workspaceId = data["workspaceId"] as? String ?: "",
                            managerId = data["managerId"] as? String ?: "",
                            type = type,
                            status = status,
                            date = data["date"] as? Timestamp ?: Timestamp.now(),
                            employeeNote = data["employeeNote"] as? String,
                            managerNote = data["managerNote"] as? String,
                            createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
                            remoteHours = (data["remoteHours"] as? Double)
                                ?: (data["remoteHours"] as? Long)?.toDouble(),
                            overtimeHours = (data["overtimeHours"] as? Double)
                                ?: (data["overtimeHours"] as? Long)?.toDouble()
                        )
                    }.sortedByDescending { it.createdAt.toDate() }
                    requests = loaded

                    val userIds = loaded.map { it.userId }.distinct()
                    userIds.forEach { uid ->
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { doc ->
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

    LaunchedEffect(user) {
        loadRequests { isLoading = false }
    }

    val pending = requests.filter { it.status == RequestStatus.PENDING }
    val resolved = requests.filter { it.status != RequestStatus.PENDING }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080810))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Inbox",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(24.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF5B8EF5))
                }
            } else {
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing),
                    onRefresh = {
                        isRefreshing = true
                        loadRequests { isRefreshing = false }
                    }
                ) {
                    if (requests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No requests", color = Color(0xFF8888AA))
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (pending.isNotEmpty()) {
                                item {
                                    Text(
                                        "Pending (${pending.size})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF5A623),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(pending) { request ->
                                    InboxRequestCard(
                                        request = request,
                                        employeeName = employeeNames[request.userId] ?: "",
                                        onRespond = { selectedRequest = request }
                                    )
                                }
                            }
                            if (resolved.isNotEmpty()) {
                                item {
                                    Text(
                                        "Resolved",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF8888AA),
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(resolved) { request ->
                                    InboxRequestCard(
                                        request = request,
                                        employeeName = employeeNames[request.userId] ?: "",
                                        onRespond = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedRequest?.let { request ->
        ResolveSheet(
            request = request,
            onDismiss = { selectedRequest = null },
            onResolve = { status, note ->
                db.collection("requests").document(request.id)
                    .update(mapOf("status" to status.name.lowercase(), "managerNote" to note))
                    .addOnSuccessListener {
                        requests = requests.map {
                            if (it.id == request.id) it.copy(status = status, managerNote = note)
                            else it
                        }
                        selectedRequest = null
                    }
            }
        )
    }
}

@Composable
fun InboxRequestCard(
    request: Request,
    employeeName: String,
    onRespond: (() -> Unit)?
) {
    val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val typeLabel = when (request.type) {
        RequestType.REMOTE_WORK -> {
            val h = request.remoteHours
            if (h != null) "Remote Work · ${if (h % 1 == 0.0) h.toInt() else h}h"
            else "Remote Work"
        }
        RequestType.SICK_LEAVE -> "Sick Leave"
        RequestType.DAY_OFF -> "Day Off"
        RequestType.OVERTIME -> {
            val h = request.overtimeHours
            if (h != null) "Overtime · ${String.format("%.1f", h)}h"
            else "Overtime"
        }
    }
    val statusColor = when (request.status) {
        RequestStatus.APPROVED -> Color(0xFF2DD47E)
        RequestStatus.REJECTED -> Color(0xFFF55252)
        RequestStatus.PENDING -> Color(0xFFF5A623)
    }
    val typeIcon = when (request.type) {
        RequestType.REMOTE_WORK -> "🏠"
        RequestType.SICK_LEAVE -> "🤒"
        RequestType.DAY_OFF -> "☀️"
        RequestType.OVERTIME -> "⚡"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F1A), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(typeIcon, fontSize = 24.sp)
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(typeLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        if (employeeName.isNotEmpty()) {
                            Text("· $employeeName", fontSize = 14.sp, color = Color(0xFF8888AA))
                        }
                    }
                    Text(dateFormat.format(request.date.toDate()), fontSize = 12.sp, color = Color(0xFF8888AA))
                }
            }
            Text(
                text = request.status.name.lowercase().replaceFirstChar { it.uppercase() },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusColor,
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        request.employeeNote?.let {
            if (it.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("💬 $it", fontSize = 12.sp, color = Color(0xFF8888AA))
            }
        }
        request.managerNote?.let {
            if (it.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("↩ $it", fontSize = 12.sp, color = Color(0xFF8888AA))
            }
        }

        if (onRespond != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRespond,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Text("Respond", fontSize = 13.sp, color = Color(0xFF5B8EF5), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolveSheet(
    request: Request,
    onDismiss: () -> Unit,
    onResolve: (RequestStatus, String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(RequestStatus.APPROVED) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0F1A)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Respond to Request", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(RequestStatus.APPROVED, RequestStatus.REJECTED).forEach { status ->
                    val selected = selectedStatus == status
                    val color = if (status == RequestStatus.APPROVED) Color(0xFF2DD47E) else Color(0xFFF55252)
                    Button(
                        onClick = { selectedStatus = status },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) color else Color(0xFF1E1E30)
                        )
                    ) {
                        Text(
                            text = if (status == RequestStatus.APPROVED) "Approve" else "Reject",
                            color = if (selected) Color.White else Color(0xFF8888AA),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note", color = Color(0xFF8888AA)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF5B8EF5),
                    unfocusedBorderColor = Color(0xFF2A2A40),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Button(
                onClick = { onResolve(selectedStatus, note) },
                enabled = note.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B8EF5))
            ) {
                Text("Send", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}