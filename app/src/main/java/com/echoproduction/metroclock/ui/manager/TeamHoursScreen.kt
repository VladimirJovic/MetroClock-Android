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
import com.echoproduction.metroclock.models.ClockEventType
import com.echoproduction.metroclock.services.AuthService
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class TeamMemberHours(
    val userId: String,
    val name: String,
    val totalHours: Double,
    val lastSeen: Date?
)

@Composable
fun TeamHoursScreen(authService: AuthService) {
    val user by authService.currentUser.collectAsState()
    val db = FirebaseFirestore.getInstance()

    var teamHours by remember { mutableStateOf<List<TeamMemberHours>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadTeam(onDone: () -> Unit = {}) {
        user?.let { manager ->
            db.collection("users")
                .whereEqualTo("managerId", manager.id)
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener { usersSnap ->
                    val employees = usersSnap.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val first = data["firstName"] as? String ?: ""
                        val last = data["lastName"] as? String ?: ""
                        doc.id to "$first $last"
                    }

                    if (employees.isEmpty()) {
                        onDone()
                        return@addOnSuccessListener
                    }

                    val results = mutableListOf<TeamMemberHours>()
                    var completed = 0

                    employees.forEach { (uid, name) ->
                        val startOfMonth = Calendar.getInstance().apply {
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }.time

                        db.collection("clockEvents")
                            .whereEqualTo("userId", uid)
                            .whereGreaterThanOrEqualTo("timestamp", Timestamp(startOfMonth))
                            .get()
                            .addOnSuccessListener { eventsSnap ->
                                val events = eventsSnap.documents.mapNotNull { doc ->
                                    val data = doc.data ?: return@mapNotNull null
                                    val typeStr = (data["type"] as? String)?.lowercase()
                                    val type = when (typeStr) {
                                        "clockin" -> ClockEventType.CLOCK_IN
                                        "clockout" -> ClockEventType.CLOCK_OUT
                                        else -> return@mapNotNull null
                                    }
                                    type to (data["timestamp"] as? Timestamp ?: Timestamp.now())
                                }.sortedBy { it.second.toDate() }

                                var total = 0.0
                                var lastIn: Timestamp? = null
                                var lastSeen: Date? = null

                                events.forEach { (type, ts) ->
                                    if (type == ClockEventType.CLOCK_IN) lastIn = ts
                                    if (type == ClockEventType.CLOCK_OUT && lastIn != null) {
                                        total += (ts.toDate().time - lastIn!!.toDate().time) / 3600000.0
                                        lastSeen = ts.toDate()
                                        lastIn = null
                                    }
                                }

                                results.add(TeamMemberHours(uid, name, total, lastSeen))
                                completed++
                                if (completed == employees.size) {
                                    teamHours = results.sortedByDescending { it.totalHours }
                                    onDone()
                                }
                            }
                            .addOnFailureListener {
                                completed++
                                if (completed == employees.size) onDone()
                            }
                    }
                }
                .addOnFailureListener { onDone() }
        }
    }

    LaunchedEffect(user) {
        loadTeam { isLoading = false }
    }

    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080810))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Team Hours", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(monthFormat.format(Date()), fontSize = 14.sp, color = Color(0xFF8888AA))
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
                        loadTeam { isRefreshing = false }
                    }
                ) {
                    if (teamHours.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No team members found", color = Color(0xFF8888AA))
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(teamHours) { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F0F1A), RoundedCornerShape(14.dp))
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(Color(0xFF1E1E30), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = member.name.take(2).uppercase(),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF5B8EF5)
                                            )
                                        }
                                        Column {
                                            Text(member.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                            member.lastSeen?.let {
                                                Text("Last seen ${dateFormat.format(it)}", fontSize = 12.sp, color = Color(0xFF8888AA))
                                            }
                                        }
                                    }
                                    Text(
                                        text = run {
                                            val totalMin = (member.totalHours * 60).toInt()
                                            "${totalMin / 60}h ${totalMin % 60}m"
                                        },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF5B8EF5)
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