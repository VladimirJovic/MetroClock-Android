package com.echoproduction.metroclock.ui.employee

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.echoproduction.metroclock.ui.theme.McGreen
import com.echoproduction.metroclock.ui.theme.McRed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class RemoteWorkEntry(val id: String, val hours: Double, val note: String?)

data class OffsiteWorkEntry(val id: String, val note: String?)

data class DayData(
    val clockIns: List<ClockEvent>,
    val clockOuts: List<ClockEvent>,
    val remoteEntries: List<RemoteWorkEntry>,
    val offsiteEntries: List<OffsiteWorkEntry> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHoursScreen(authService: AuthService) {
    val user by authService.currentUser.collectAsState()
    val db = FirebaseFirestore.getInstance()

    var dayMap by remember { mutableStateOf<Map<String, DayData>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadData(onDone: () -> Unit = {}) {
        val u = user ?: return
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        db.collection("clockEvents")
            .whereEqualTo("userId", u.id)
            .get()
            .addOnSuccessListener { snapshot ->
                val events = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val typeStr = (data["type"] as? String)?.lowercase()
                    val type = when (typeStr) {
                        "clockin"  -> ClockEventType.CLOCK_IN
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
                }

                val eventsByDay = events.groupBy { fmt.format(it.timestamp.toDate()) }

                db.collection("requests")
                    .whereEqualTo("userId", u.id)
                    .whereEqualTo("type", "remoteWork")
                    .get()
                    .addOnSuccessListener { reqSnapshot ->
                        val remoteByDay = mutableMapOf<String, MutableList<RemoteWorkEntry>>()
                        for (doc in reqSnapshot.documents) {
                            val d = doc.data ?: continue
                            if ((d["status"] as? String) != "approved") continue
                            val ts = (d["dateFrom"] as? Timestamp) ?: (d["date"] as? Timestamp)
                            val date = ts?.toDate() ?: continue
                            val hours = (d["remoteHours"] as? Double)
                                ?: (d["remoteHours"] as? Long)?.toDouble() ?: continue
                            if (hours <= 0) continue
                            val key = fmt.format(date)
                            remoteByDay.getOrPut(key) { mutableListOf() }
                                .add(RemoteWorkEntry(doc.id, hours, d["employeeNote"] as? String))
                        }

                        db.collection("requests")
                            .whereEqualTo("userId", u.id)
                            .whereEqualTo("type", "offsiteWork")
                            .get()
                            .addOnSuccessListener { offsiteSnapshot ->
                                val offsiteByDay = mutableMapOf<String, MutableList<OffsiteWorkEntry>>()
                                for (doc in offsiteSnapshot.documents) {
                                    val d = doc.data ?: continue
                                    if ((d["status"] as? String) != "approved") continue
                                    val tsFrom = (d["dateFrom"] as? Timestamp) ?: (d["date"] as? Timestamp)
                                    val dateFrom = tsFrom?.toDate() ?: continue
                                    val dateTo = (d["dateTo"] as? Timestamp)?.toDate() ?: dateFrom
                                    val cal = java.util.Calendar.getInstance()
                                    cal.time = dateFrom
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    cal.set(java.util.Calendar.MINUTE, 0)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                    val endCal = java.util.Calendar.getInstance()
                                    endCal.time = dateTo
                                    endCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    endCal.set(java.util.Calendar.MINUTE, 0)
                                    endCal.set(java.util.Calendar.SECOND, 0)
                                    while (!cal.time.after(endCal.time)) {
                                        val key = fmt.format(cal.time)
                                        offsiteByDay.getOrPut(key) { mutableListOf() }
                                            .add(OffsiteWorkEntry(doc.id, d["employeeNote"] as? String))
                                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                    }
                                }

                                val allKeys = (eventsByDay.keys + remoteByDay.keys + offsiteByDay.keys).toSortedSet(reverseOrder())
                                val result = allKeys.associateWith { key ->
                                    val dayEvents = eventsByDay[key] ?: emptyList()
                                    DayData(
                                        clockIns  = dayEvents.filter { it.type == ClockEventType.CLOCK_IN }.sortedBy { it.timestamp.toDate() },
                                        clockOuts = dayEvents.filter { it.type == ClockEventType.CLOCK_OUT }.sortedBy { it.timestamp.toDate() },
                                        remoteEntries = remoteByDay[key] ?: emptyList(),
                                        offsiteEntries = offsiteByDay[key] ?: emptyList()
                                    )
                                }
                                dayMap = result
                                onDone()
                            }
                            .addOnFailureListener {
                                val allKeys = (eventsByDay.keys + remoteByDay.keys).toSortedSet(reverseOrder())
                                val result = allKeys.associateWith { key ->
                                    val dayEvents = eventsByDay[key] ?: emptyList()
                                    DayData(
                                        clockIns  = dayEvents.filter { it.type == ClockEventType.CLOCK_IN }.sortedBy { it.timestamp.toDate() },
                                        clockOuts = dayEvents.filter { it.type == ClockEventType.CLOCK_OUT }.sortedBy { it.timestamp.toDate() },
                                        remoteEntries = remoteByDay[key] ?: emptyList()
                                    )
                                }
                                dayMap = result
                                onDone()
                            }
                    }
                    .addOnFailureListener {
                        val allKeys = eventsByDay.keys.toSortedSet(reverseOrder())
                        dayMap = allKeys.associateWith { key ->
                            val dayEvents = eventsByDay[key] ?: emptyList()
                            DayData(
                                clockIns  = dayEvents.filter { it.type == ClockEventType.CLOCK_IN }.sortedBy { it.timestamp.toDate() },
                                clockOuts = dayEvents.filter { it.type == ClockEventType.CLOCK_OUT }.sortedBy { it.timestamp.toDate() },
                                remoteEntries = emptyList()
                            )
                        }
                        onDone()
                    }
            }
            .addOnFailureListener { onDone() }
    }

    LaunchedEffect(user) { loadData { isLoading = false } }

    val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Box(modifier = Modifier.fillMaxSize().background(LocalMcColors.current.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "My Hours",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = LocalMcColors.current.text,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = McOrange)
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { isRefreshing = true; loadData { isRefreshing = false } },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (dayMap.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📅", fontSize = 48.sp)
                                Text("No hours recorded yet", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.textSecondary)
                                Text("Your clock-in history will appear here", fontSize = 13.sp, color = LocalMcColors.current.textTertiary)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            dayMap.forEach { (dayKey, data) ->
                                val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayKey)

                                var officeMs = 0L
                                data.clockIns.forEachIndexed { i, ci ->
                                    val co = data.clockOuts.getOrNull(i)
                                    if (co != null) officeMs += co.timestamp.toDate().time - ci.timestamp.toDate().time
                                }
                                val officeH = officeMs / 3600000.0
                                val remoteH = data.remoteEntries.sumOf { it.hours }
                                val totalH = officeH + remoteH
                                val totalMin = (totalH * 60).toInt()
                                val hoursDisplay = "${totalMin / 60}h ${totalMin % 60}m"

                                val statusColor = when {
                                    totalH >= 8 -> McGreen
                                    totalH >= 4 -> McOrange
                                    else        -> McRed
                                }

                                val hasOffice = data.clockIns.isNotEmpty()
                                val hasRemote = data.remoteEntries.isNotEmpty()
                                val hasOffsite = data.offsiteEntries.isNotEmpty()
                                val workType = when {
                                    hasOffice && hasRemote -> "Hybrid"
                                    hasRemote -> "Remote"
                                    hasOffsite -> "Offsite"
                                    else -> null
                                }

                                item(key = dayKey) {
                                    DayCard(
                                        dayKey = dayKey,
                                        data = data,
                                        parsedDate = parsedDate,
                                        dateFormat = dateFormat,
                                        timeFormat = timeFormat,
                                        hoursDisplay = hoursDisplay,
                                        statusColor = statusColor,
                                        workType = workType
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCard(
    dayKey: String,
    data: DayData,
    parsedDate: Date?,
    dateFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat,
    hoursDisplay: String,
    statusColor: Color,
    workType: String?
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalMcColors.current.surface, RoundedCornerShape(12.dp))
            .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(12.dp))
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = parsedDate?.let { dateFormat.format(it) } ?: dayKey,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalMcColors.current.text
                )
                if (workType != null) {
                    val wtColor = when (workType) {
                        "Hybrid"  -> Color(0xFF9B6DFF)
                        "Offsite" -> Color(0xFF6366F1)
                        else      -> Color(0xFF2DD4BF)
                    }
                    Text(
                        text = workType.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = wtColor,
                        modifier = Modifier
                            .background(wtColor.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = hoursDisplay,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    fontSize = 9.sp,
                    color = LocalMcColors.current.textTertiary
                )
            }
        }

        // ── Expanded detail ──────────────────────────────────────────────────
        if (isExpanded) {
            HorizontalDivider(
                color = LocalMcColors.current.border,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Clock events
            data.clockIns.forEachIndexed { i, ci ->
                val co = data.clockOuts.getOrNull(i)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).background(McGreen.copy(alpha = 0.15f), RoundedCornerShape(17.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("→", fontSize = 14.sp, color = McGreen, fontWeight = FontWeight.Bold) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clocked In", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.text)
                        }
                        Text(timeFormat.format(ci.timestamp.toDate()), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.textSecondary)
                    }
                    if (co != null) {
                        HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(start = 62.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(34.dp).background(McRed.copy(alpha = 0.15f), RoundedCornerShape(17.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text("←", fontSize = 14.sp, color = McRed, fontWeight = FontWeight.Bold) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Clocked Out", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.text)
                            }
                            Text(timeFormat.format(co.timestamp.toDate()), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.textSecondary)
                        }
                    } else {
                        HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(start = 62.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.size(34.dp)) {}
                            Text("Still clocked in", fontSize = 13.sp, color = McGreen, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Remote entries
            if (data.remoteEntries.isNotEmpty()) {
                if (data.clockIns.isNotEmpty()) {
                    HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(horizontal = 16.dp))
                }
                data.remoteEntries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).background(Color(0xFF2DD4BF).copy(alpha = 0.15f), RoundedCornerShape(17.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("R", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2DD4BF)) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Remote Work", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.text)
                            entry.note?.let { n -> if (n.isNotEmpty()) Text(n, fontSize = 11.sp, color = LocalMcColors.current.textSecondary, maxLines = 1) }
                        }
                        val h = entry.hours
                        Text(
                            if (h % 1.0 == 0.0) "${h.toInt()}h" else "%.1fh".format(h),
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.textSecondary
                        )
                    }
                }
            }

            // Offsite entries
            if (data.offsiteEntries.isNotEmpty()) {
                if (data.clockIns.isNotEmpty() || data.remoteEntries.isNotEmpty()) {
                    HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(horizontal = 16.dp))
                }
                data.offsiteEntries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).background(Color(0xFF6366F1).copy(alpha = 0.15f), RoundedCornerShape(17.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("F", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1)) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Offsite Work", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LocalMcColors.current.text)
                            entry.note?.let { n -> if (n.isNotEmpty()) Text(n, fontSize = 11.sp, color = LocalMcColors.current.textSecondary, maxLines = 1) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
