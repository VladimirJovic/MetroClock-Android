package com.echoproduction.metroclock.ui.employee

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
fun RequestsScreen(authService: AuthService) {
    val user by authService.currentUser.collectAsState()
    val db = FirebaseFirestore.getInstance()

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
                                ?: (data["remoteHours"] as? Long)?.toDouble()
                        )
                    }.sortedByDescending { it.createdAt.toDate() }
                    onDone()
                }
                .addOnFailureListener { onDone() }
        }
    }

    LaunchedEffect(user) {
        loadRequests { isLoading = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080810))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Requests", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Button(
                    onClick = { showNewRequestSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B8EF5))
                ) {
                    Text("+ New")
                }
            }

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
                            Text("No requests yet", color = Color(0xFF8888AA))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(requests) { request ->
                                RequestCard(request)
                            }
                        }
                    }
                }
            }
        }

        if (showNewRequestSheet) {
            NewRequestBottomSheet(
                user = user,
                onDismiss = { showNewRequestSheet = false },
                onSubmit = { type, date, note, hours ->
                    val u = user ?: return@NewRequestBottomSheet
                    val data = hashMapOf<String, Any>(
                        "userId" to u.id,
                        "workspaceId" to u.workspaceId,
                        "managerId" to (u.managerId ?: ""),
                        "type" to when (type) {
                            RequestType.REMOTE_WORK -> "remoteWork"
                            RequestType.SICK_LEAVE -> "sickLeave"
                            RequestType.DAY_OFF -> "dayOff"
                            RequestType.OVERTIME -> "overtime"
                        },
                        "status" to "pending",
                        "date" to Timestamp(date),
                        "employeeNote" to note,
                        "createdAt" to Timestamp.now()
                    )
                    hours?.let { data["remoteHours"] = it }
                    db.collection("requests").add(data).addOnSuccessListener {
                        showNewRequestSheet = false
                        loadRequests {}
                    }
                }
            )
        }
    }
}

@Composable
fun RequestCard(request: Request) {
    val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val typeLabel = when (request.type) {
        RequestType.REMOTE_WORK -> {
            val h = request.remoteHours
            if (h != null) "Remote Work · ${if (h % 1 == 0.0) h.toInt() else h}h"
            else "Remote Work"
        }
        RequestType.SICK_LEAVE -> "Sick Leave"
        RequestType.DAY_OFF -> "Day Off"
        RequestType.OVERTIME -> "Overtime"
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(typeIcon, fontSize = 24.sp)
                Column {
                    Text(typeLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRequestBottomSheet(
    user: com.echoproduction.metroclock.models.MCUser?,
    onDismiss: () -> Unit,
    onSubmit: (RequestType, Date, String, Double?) -> Unit
) {
    var selectedType by remember { mutableStateOf(RequestType.REMOTE_WORK) }
    var note by remember { mutableStateOf("") }
    var remoteHours by remember { mutableStateOf(8.0) }
    var selectedDate by remember { mutableStateOf(Date()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0F1A)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("New Request", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    RequestType.REMOTE_WORK to "🏠",
                    RequestType.SICK_LEAVE to "🤒",
                    RequestType.DAY_OFF to "☀️"
                ).forEach { (type, icon) ->
                    val selected = selectedType == type
                    Button(
                        onClick = { selectedType = type },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Color(0xFF5B8EF5) else Color(0xFF1E1E30)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(icon, fontSize = 18.sp)
                    }
                }
            }

            Text(
                text = when (selectedType) {
                    RequestType.REMOTE_WORK -> "Remote Work"
                    RequestType.SICK_LEAVE -> "Sick Leave"
                    RequestType.DAY_OFF -> "Day Off"
                    else -> ""
                },
                fontSize = 14.sp,
                color = Color(0xFF8888AA)
            )

            if (selectedType == RequestType.REMOTE_WORK) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hours", fontSize = 14.sp, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(onClick = { if (remoteHours > 1) remoteHours -= 0.5 }) {
                            Text("−", fontSize = 20.sp, color = Color(0xFF5B8EF5))
                        }
                        Text(
                            text = if (remoteHours % 1 == 0.0) "${remoteHours.toInt()}h" else "${remoteHours}h",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        IconButton(onClick = { if (remoteHours < 24) remoteHours += 0.5 }) {
                            Text("+", fontSize = 20.sp, color = Color(0xFF5B8EF5))
                        }
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
                onClick = {
                    onSubmit(
                        selectedType,
                        selectedDate,
                        note,
                        if (selectedType == RequestType.REMOTE_WORK) remoteHours else null
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B8EF5))
            ) {
                Text("Submit Request", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}