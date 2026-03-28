package com.echoproduction.metroclock.ui.employee

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.models.ClockEvent
import com.echoproduction.metroclock.models.ClockEventType
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.ui.theme.LocalMcColors
import com.echoproduction.metroclock.ui.theme.McOrange
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MyHoursScreen(authService: AuthService) {
    val user by authService.currentUser.collectAsState()
    val db = FirebaseFirestore.getInstance()

    var events by remember { mutableStateOf<List<ClockEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadEvents(onDone: () -> Unit = {}) {
        user?.let { u ->
            db.collection("clockEvents")
                .whereEqualTo("userId", u.id)
                .get()
                .addOnSuccessListener { snapshot ->
                    events = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val typeStr = (data["type"] as? String)?.lowercase()
                        val type = when (typeStr) {
                            "clockin" -> ClockEventType.CLOCK_IN
                            "clockout" -> ClockEventType.CLOCK_OUT
                            else -> return@mapNotNull null
                        }
                        ClockEvent(
                            id = doc.id,
                            userId = data["userId"] as? String ?: "",
                            workspaceId = data["workspaceId"] as? String ?: "",
                            type = type,
                            timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now(),
                            locationId = data["locationId"] as? String ?: "",
                            overtimeNote = data["overtimeNote"] as? String,
                            isOvertimeApproved = data["isOvertimeApproved"] as? Boolean,
                            managerNote = data["managerNote"] as? String,
                            correctedHours = (data["correctedHours"] as? Double)
                                ?: (data["correctedHours"] as? Long)?.toDouble()
                        )
                    }.sortedByDescending { it.timestamp.toDate() }
                    onDone()
                }
                .addOnFailureListener { onDone() }
        }
    }

    LaunchedEffect(user) {
        loadEvents { isLoading = false }
    }

    val groupedByDay = events.groupBy { event ->
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        df.format(event.timestamp.toDate())
    }.toSortedMap(reverseOrder())

    val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalMcColors.current.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "My Hours",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = LocalMcColors.current.text,
                modifier = Modifier.padding(24.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = McOrange)
                }
            } else {
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing),
                    onRefresh = {
                        isRefreshing = true
                        loadEvents { isRefreshing = false }
                    }
                ) {
                    if (groupedByDay.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hours recorded yet", color = LocalMcColors.current.textSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            groupedByDay.forEach { (dayKey, dayEvents) ->
                                val clockIns = dayEvents.filter { it.type == ClockEventType.CLOCK_IN }.sortedBy { it.timestamp.toDate() }
                                val clockOuts = dayEvents.filter { it.type == ClockEventType.CLOCK_OUT }.sortedBy { it.timestamp.toDate() }

                                var totalMs = 0L
                                clockIns.forEachIndexed { i, ci ->
                                    val co = clockOuts.getOrNull(i)
                                    if (co != null) {
                                        totalMs += co.timestamp.toDate().time - ci.timestamp.toDate().time
                                    }
                                }
                                val totalMinutes = (totalMs / 60000).toInt()
                                val hoursDisplay = "${totalMinutes / 60}h ${totalMinutes % 60}m"
                                val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayKey)

                                item(key = dayKey) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(LocalMcColors.current.surface, RoundedCornerShape(14.dp))
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = parsedDate?.let { dateFormat.format(it) } ?: dayKey,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = LocalMcColors.current.text
                                            )
                                            Text(
                                                text = hoursDisplay,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = McOrange
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                        clockIns.forEachIndexed { i, ci ->
                                            val co = clockOuts.getOrNull(i)
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("▶", fontSize = 10.sp, color = Color(0xFF2DD47E))
                                                Text(timeFormat.format(ci.timestamp.toDate()), fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
                                                if (co != null) {
                                                    Text("→", fontSize = 10.sp, color = LocalMcColors.current.textSecondary)
                                                    Text("⏹", fontSize = 10.sp, color = Color(0xFFF55252))
                                                    Text(timeFormat.format(co.timestamp.toDate()), fontSize = 12.sp, color = LocalMcColors.current.textSecondary)
                                                } else {
                                                    Text("→ still clocked in", fontSize = 12.sp, color = Color(0xFF2DD47E))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
