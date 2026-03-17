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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.models.ClockEventType
import com.echoproduction.metroclock.services.AuthService
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

// MARK: - Data models

data class TeamMemberInfo(val userId: String, val name: String, val workspaceId: String)

data class DayClockEvent(
    val id: String,
    val type: ClockEventType,
    val timestamp: Date,
    val locationId: String,
    val overtimeNote: String? = null
)

data class RemoteWorkEntry(val id: String, val hours: Double, val note: String?)

data class DayRecord(
    val dateKey: String, // yyyy-MM-dd
    val events: List<DayClockEvent>,
    val remoteEntries: List<RemoteWorkEntry>
) {
    val displayDate: String get() {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val d = fmt.parse(dateKey) ?: return dateKey
        return SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(d)
    }

    val officeHours: Double get() {
        var total = 0.0; var lastIn: Date? = null
        for (ev in events.sortedBy { it.timestamp }) {
            if (ev.type == ClockEventType.CLOCK_IN) lastIn = ev.timestamp
            if (ev.type == ClockEventType.CLOCK_OUT && lastIn != null) {
                total += (ev.timestamp.time - lastIn.time) / 3600000.0; lastIn = null
            }
        }
        if (lastIn != null) total += (Date().time - lastIn.time) / 3600000.0
        return total
    }

    val totalRemoteHours: Double get() = remoteEntries.sumOf { it.hours }
    val totalHours: Double get() = officeHours + totalRemoteHours

    val hoursString: String get() {
        val totalMin = (totalHours * 60).toInt()
        return "${totalMin / 60}h ${totalMin % 60}m"
    }

    val hasClockIn: Boolean get() = events.any { it.type == ClockEventType.CLOCK_IN }
    val hasClockOut: Boolean get() = events.any { it.type == ClockEventType.CLOCK_OUT }
}

// MARK: - TeamHoursScreen

@Composable
fun TeamHoursScreen(authService: AuthService) {
    val user by authService.currentUser.collectAsState()
    val db = FirebaseFirestore.getInstance()

    var teamMembers by remember { mutableStateOf<List<TeamMemberInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<TeamMemberInfo?>(null) }

    fun loadTeam(onDone: () -> Unit = {}) {
        user?.let { manager ->
            db.collection("users")
                .whereEqualTo("managerId", manager.id)
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener { snap ->
                    teamMembers = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        TeamMemberInfo(
                            userId = doc.id,
                            name = "${d["firstName"] as? String ?: ""} ${d["lastName"] as? String ?: ""}".trim(),
                            workspaceId = d["workspaceId"] as? String ?: ""
                        )
                    }
                    onDone()
                }
                .addOnFailureListener { onDone() }
        }
    }

    LaunchedEffect(user) { loadTeam { isLoading = false } }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080810))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Team Hours", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()), fontSize = 14.sp, color = Color(0xFF8888AA))
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF5B8EF5))
                }
            } else {
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing),
                    onRefresh = { isRefreshing = true; loadTeam { isRefreshing = false } }
                ) {
                    if (teamMembers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No team members found", color = Color(0xFF8888AA))
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(teamMembers) { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F0F1A), RoundedCornerShape(14.dp))
                                        .clickable { selectedMember = member }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(
                                            modifier = Modifier.size(40.dp).background(Color(0xFF1E1E30), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(member.name.take(2).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B8EF5))
                                        }
                                        Text(member.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                    Text("›", fontSize = 18.sp, color = Color(0xFF8888AA))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedMember?.let { member ->
        MemberHoursSheet(member = member, onDismiss = { selectedMember = null })
    }
}

// MARK: - MemberHoursSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberHoursSheet(member: TeamMemberInfo, onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var records by remember { mutableStateOf<List<DayRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Edit event state
    var editingEvent by remember { mutableStateOf<DayClockEvent?>(null) }
    var addingEventType by remember { mutableStateOf<ClockEventType?>(null) }
    var addingForRecord by remember { mutableStateOf<DayRecord?>(null) }
    var editHour by remember { mutableStateOf(9) }
    var editMinute by remember { mutableStateOf(0) }
    var showEventSheet by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Edit remote state
    var editingRemote by remember { mutableStateOf<RemoteWorkEntry?>(null) }
    var remoteHoursValue by remember { mutableStateOf(1.0) }
    var showRemoteSheet by remember { mutableStateOf(false) }

    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.time

    fun loadRecords() {
        db.collection("clockEvents").whereEqualTo("userId", member.userId).get()
            .addOnSuccessListener { evSnap ->
                val eventsByDay = mutableMapOf<String, MutableList<DayClockEvent>>()
                evSnap.documents.forEach { doc ->
                    val d = doc.data ?: return@forEach
                    val ts = (d["timestamp"] as? Timestamp)?.toDate() ?: return@forEach
                    if (ts < thirtyDaysAgo) return@forEach
                    val typeStr = (d["type"] as? String)?.lowercase()
                    val type = when (typeStr) {
                        "clockin" -> ClockEventType.CLOCK_IN
                        "clockout" -> ClockEventType.CLOCK_OUT
                        else -> return@forEach
                    }
                    val key = fmt.format(ts)
                    eventsByDay.getOrPut(key) { mutableListOf() }.add(
                        DayClockEvent(doc.id, type, ts, d["locationId"] as? String ?: "", d["overtimeNote"] as? String)
                    )
                }

                db.collection("requests")
                    .whereEqualTo("userId", member.userId)
                    .whereEqualTo("type", "remoteWork")
                    .whereEqualTo("status", "approved")
                    .get()
                    .addOnSuccessListener { reqSnap ->
                        isLoading = false
                        val remoteByDay = mutableMapOf<String, MutableList<RemoteWorkEntry>>()
                        reqSnap.documents.forEach { doc ->
                            val d = doc.data ?: return@forEach
                            val ts = (d["date"] as? Timestamp)?.toDate() ?: return@forEach
                            if (ts < thirtyDaysAgo) return@forEach
                            val hours = (d["remoteHours"] as? Double) ?: (d["remoteHours"] as? Long)?.toDouble() ?: return@forEach
                            val key = fmt.format(ts)
                            remoteByDay.getOrPut(key) { mutableListOf() }.add(
                                RemoteWorkEntry(doc.id, hours, d["employeeNote"] as? String)
                            )
                        }

                        val allDays = mutableMapOf<String, DayRecord>()
                        eventsByDay.forEach { (key, evs) ->
                            allDays[key] = DayRecord(key, evs.sortedBy { it.timestamp }, remoteByDay[key] ?: emptyList())
                        }
                        remoteByDay.forEach { (key, entries) ->
                            if (!allDays.containsKey(key)) allDays[key] = DayRecord(key, emptyList(), entries)
                        }
                        records = allDays.values.sortedByDescending { it.dateKey }
                    }
            }
    }

    LaunchedEffect(member) { loadRecords() }

    val eventSheetTitle = when {
        editingEvent != null -> if (editingEvent!!.type == ClockEventType.CLOCK_IN) "Edit Clock In" else "Edit Clock Out"
        addingEventType == ClockEventType.CLOCK_IN -> "Add Clock In"
        addingEventType == ClockEventType.CLOCK_OUT -> "Add Clock Out"
        else -> "Edit Time"
    }

    fun handleEventSave() {
        val cal = Calendar.getInstance()
        val recordForDate = editingEvent?.let { ev -> records.firstOrNull { r -> r.events.any { it.id == ev.id } } }
            ?: addingForRecord ?: return

        // Set date from record, time from picker
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val base = dateFmt.parse(recordForDate.dateKey) ?: return
        cal.time = base
        cal.set(Calendar.HOUR_OF_DAY, editHour)
        cal.set(Calendar.MINUTE, editMinute)
        cal.set(Calendar.SECOND, 0)
        val newTime = cal.time

        // Validation
        val otherEvents = if (editingEvent != null)
            recordForDate.events.filter { it.id != editingEvent!!.id }
        else recordForDate.events

        for (other in otherEvents) {
            val diff = Math.abs(newTime.time - other.timestamp.time)
            if (diff < 60000) { validationError = "Time conflicts with another event"; showEventSheet = true; return }
        }

        if (editingEvent != null) {
            db.collection("clockEvents").document(editingEvent!!.id)
                .update("timestamp", Timestamp(newTime))
                .addOnSuccessListener { loadRecords() }
        } else if (addingEventType != null) {
            val data = hashMapOf<String, Any>(
                "userId" to member.userId,
                "workspaceId" to member.workspaceId,
                "type" to if (addingEventType == ClockEventType.CLOCK_IN) "clockIn" else "clockOut",
                "timestamp" to Timestamp(newTime),
                "locationId" to "manual"
            )
            db.collection("clockEvents").add(data).addOnSuccessListener { loadRecords() }
        }

        showEventSheet = false; validationError = null
        editingEvent = null; addingEventType = null; addingForRecord = null
    }

    fun handleRemoteSave() {
        val entry = editingRemote ?: return
        db.collection("requests").document(entry.id)
            .update("remoteHours", remoteHoursValue)
            .addOnSuccessListener { loadRecords() }
        showRemoteSheet = false; editingRemote = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF080810)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(member.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                TextButton(onClick = onDismiss) { Text("Done", color = Color(0xFF5B8EF5)) }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF5B8EF5))
                }
            } else if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No records", color = Color(0xFF8888AA))
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(records) { record ->
                        DayRecordCard(
                            record = record,
                            timeFmt = timeFmt,
                            onEditEvent = { ev ->
                                editingEvent = ev
                                val cal2 = Calendar.getInstance().apply { time = ev.timestamp }
                                editHour = cal2.get(Calendar.HOUR_OF_DAY)
                                editMinute = cal2.get(Calendar.MINUTE)
                                validationError = null; showEventSheet = true
                            },
                            onAddEvent = { type, rec ->
                                addingEventType = type; addingForRecord = rec; editingEvent = null
                                editHour = 9; editMinute = 0
                                validationError = null; showEventSheet = true
                            },
                            onEditRemote = { entry ->
                                editingRemote = entry; remoteHoursValue = entry.hours; showRemoteSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Event time picker sheet
    if (showEventSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEventSheet = false; validationError = null },
            containerColor = Color(0xFF0F0F1A)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(eventSheetTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    // Hour picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hour", fontSize = 11.sp, color = Color(0xFF8888AA))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { if (editHour > 0) editHour-- }) { Text("−", fontSize = 20.sp, color = Color(0xFF5B8EF5)) }
                            Text("%02d".format(editHour), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            IconButton(onClick = { if (editHour < 23) editHour++ }) { Text("+", fontSize = 20.sp, color = Color(0xFF5B8EF5)) }
                        }
                    }
                    Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
                    // Minute picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minute", fontSize = 11.sp, color = Color(0xFF8888AA))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { if (editMinute > 0) editMinute-- else editMinute = 59 }) { Text("−", fontSize = 20.sp, color = Color(0xFF5B8EF5)) }
                            Text("%02d".format(editMinute), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            IconButton(onClick = { if (editMinute < 59) editMinute++ else editMinute = 0 }) { Text("+", fontSize = 20.sp, color = Color(0xFF5B8EF5)) }
                        }
                    }
                }

                validationError?.let { Text(it, fontSize = 12.sp, color = Color(0xFFF55252)) }

                Button(
                    onClick = { handleEventSave() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B8EF5))
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Remote hours picker sheet
    if (showRemoteSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRemoteSheet = false },
            containerColor = Color(0xFF0F0F1A)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Edit Remote Hours", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hours", fontSize = 14.sp, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(onClick = { if (remoteHoursValue > 0.5) remoteHoursValue -= 0.5 }) { Text("−", fontSize = 20.sp, color = Color(0xFF5B8EF5)) }
                        Text(
                            text = if (remoteHoursValue % 1 == 0.0) "${remoteHoursValue.toInt()}h" else "${remoteHoursValue}h",
                            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                        IconButton(onClick = { if (remoteHoursValue < 24) remoteHoursValue += 0.5 }) { Text("+", fontSize = 20.sp, color = Color(0xFF5B8EF5)) }
                    }
                }
                Button(
                    onClick = { handleRemoteSave() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B8EF5))
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// MARK: - DayRecordCard

@Composable
fun DayRecordCard(
    record: DayRecord,
    timeFmt: SimpleDateFormat,
    onEditEvent: (DayClockEvent) -> Unit,
    onAddEvent: (ClockEventType, DayRecord) -> Unit,
    onEditRemote: (RemoteWorkEntry) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val statusColor = when {
        record.totalHours >= 8 -> Color(0xFF2DD47E)
        record.totalHours >= 4 -> Color(0xFFF5A623)
        else -> Color(0xFFF55252)
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF0F0F1A), RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(record.displayDate, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    record.hoursString, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = statusColor,
                    modifier = Modifier.background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Text(if (isExpanded) "▲" else "▼", fontSize = 10.sp, color = Color(0xFF8888AA))
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFF2A2A40))
            Spacer(modifier = Modifier.height(8.dp))

            // Clock events
            record.events.forEach { ev ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(32.dp).background(
                            if (ev.type == ClockEventType.CLOCK_IN) Color(0xFF2DD47E).copy(alpha = 0.15f) else Color(0xFFF55252).copy(alpha = 0.15f),
                            RoundedCornerShape(16.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (ev.type == ClockEventType.CLOCK_IN) "→" else "←", fontSize = 14.sp, color = if (ev.type == ClockEventType.CLOCK_IN) Color(0xFF2DD47E) else Color(0xFFF55252))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (ev.type == ClockEventType.CLOCK_IN) "Clock In" else "Clock Out", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        if (ev.locationId == "manual") Text("Added manually", fontSize = 11.sp, color = Color(0xFF8888AA))
                    }
                    Text(timeFmt.format(ev.timestamp), fontSize = 13.sp, color = Color(0xFF8888AA))
                    Box(
                        modifier = Modifier.size(28.dp).background(Color(0xFF5B8EF5).copy(alpha = 0.1f), RoundedCornerShape(8.dp)).clickable { onEditEvent(ev) },
                        contentAlignment = Alignment.Center
                    ) { Text("✎", fontSize = 13.sp, color = Color(0xFF5B8EF5)) }
                }
                Divider(color = Color(0xFF1A1A2A))
            }

            // Remote entries
            if (record.remoteEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                record.remoteEntries.forEach { entry ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(32.dp).background(Color(0xFF2DD4BF).copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("🏠", fontSize = 14.sp) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Remote Work", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            entry.note?.let { if (it.isNotEmpty()) Text(it, fontSize = 11.sp, color = Color(0xFF8888AA), maxLines = 1) }
                        }
                        Text(
                            if (entry.hours % 1 == 0.0) "${entry.hours.toInt()}h" else "${entry.hours}h",
                            fontSize = 13.sp, color = Color(0xFF8888AA)
                        )
                        Box(
                            modifier = Modifier.size(28.dp).background(Color(0xFF2DD4BF).copy(alpha = 0.1f), RoundedCornerShape(8.dp)).clickable { onEditRemote(entry) },
                            contentAlignment = Alignment.Center
                        ) { Text("✎", fontSize = 13.sp, color = Color(0xFF2DD4BF)) }
                    }
                    Divider(color = Color(0xFF1A1A2A))
                }
            }

            // Add buttons
            val missingTypes = mutableListOf<ClockEventType>()
            if (!record.hasClockIn) missingTypes.add(ClockEventType.CLOCK_IN)
            if (!record.hasClockOut) missingTypes.add(ClockEventType.CLOCK_OUT)
            if (missingTypes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    missingTypes.forEach { type ->
                        val isIn = type == ClockEventType.CLOCK_IN
                        val color = if (isIn) Color(0xFF2DD47E) else Color(0xFFF55252)
                        Button(
                            onClick = { onAddEvent(type, record) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.12f)),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(if (isIn) "+ Clock In" else "+ Clock Out", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
                        }
                    }
                }
            }
        }
    }
}