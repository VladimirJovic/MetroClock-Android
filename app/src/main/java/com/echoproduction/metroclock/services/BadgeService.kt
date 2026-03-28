package com.echoproduction.metroclock.services

import android.content.Context
import android.content.SharedPreferences
import com.echoproduction.metroclock.models.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.Date

data class BadgeCounts(
    val managerBadge: Int = 0,
    val employeeBadge: Int = 0,
    // Per-employee indicator sets (for TeamHoursScreen)
    val pendingRequestUserIds:  Set<String> = emptySet(),
    val openSessionUserIds:     Set<String> = emptySet(),
    val pendingOvertimeUserIds: Set<String> = emptySet(),
    val openSessionDates: Map<String, Date> = emptyMap()  // userId → date of the open clockIn
)

class BadgeService(private val context: Context) {

    private val db   = FirebaseFirestore.getInstance()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mc_badge", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(BadgeCounts())
    val state: StateFlow<BadgeCounts> = _state

    private val listeners = mutableListOf<ListenerRegistration>()

    // Per-compute state (manager)
    private var pendingRequestIds  = mutableSetOf<String>()
    private var openSessionIds     = mutableSetOf<String>()
    private var openSessionDatesMap = mutableMapOf<String, Date>()  // userId → clockIn date
    private var pendingOvertimeIds = mutableSetOf<String>()

    // ── Public API ────────────────────────────────────────────────────────────

    fun startListening(userId: String, role: UserRole, workspaceId: String) {
        stopListening()
        when (role) {
            UserRole.MANAGER, UserRole.ADMIN ->
                listenForDirectReports(managerId = userId, workspaceId = workspaceId)
            UserRole.EMPLOYEE ->
                listenForResolvedRequests(userId = userId)
        }
    }

    fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    /** Call when employee opens Requests screen. */
    fun markAllResolvedAsSeen(ids: List<String>) {
        val seen = getSeenIds().toMutableSet()
        seen.addAll(ids)
        saveSeenIds(seen)
        _state.value = _state.value.copy(employeeBadge = 0)
    }

    // ── Manager ───────────────────────────────────────────────────────────────

    private fun listenForDirectReports(managerId: String, workspaceId: String) {
        val l = db.collection("users")
            .whereEqualTo("managerId", managerId)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snap, _ ->
                val reportIds = snap?.documents?.map { it.id } ?: return@addSnapshotListener
                listenForPendingRequests(managerId = managerId, reportIds = reportIds)
                listenOpenSessions(reportIds = reportIds)
                listenPendingOvertime(reportIds = reportIds)
            }
        listeners.add(l)
    }

    private fun listenForPendingRequests(managerId: String, reportIds: List<String>) {
        if (reportIds.isEmpty()) {
            pendingRequestIds.clear(); recompute(); return
        }
        val l = db.collection("requests")
            .whereEqualTo("managerId", managerId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                pendingRequestIds = snap?.documents
                    ?.mapNotNull { it.getString("userId") }
                    ?.toMutableSet() ?: mutableSetOf()
                recompute()
            }
        listeners.add(l)
    }

    private fun listenOpenSessions(reportIds: List<String>) {
        if (reportIds.isEmpty()) {
            openSessionIds.clear(); openSessionDatesMap.clear(); recompute(); return
        }
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.time
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = cal.time

        val chunks = reportIds.chunked(30)
        // One slot per chunk; all lambdas share this array via Kotlin's closure capture
        val chunkEvents = Array(chunks.size) { mutableMapOf<String, MutableList<Pair<String, Date>>>() }

        for ((i, chunk) in chunks.withIndex()) {
            val l = db.collection("clockEvents")
                .whereIn("userId", chunk)
                .whereGreaterThanOrEqualTo("timestamp", Timestamp(yesterdayStart))
                .orderBy("timestamp")
                .addSnapshotListener { snap, _ ->
                    val events = mutableMapOf<String, MutableList<Pair<String, Date>>>()
                    for (doc in snap?.documents ?: emptyList()) {
                        val uid  = doc.getString("userId") ?: continue
                        val ts   = (doc.get("timestamp") as? Timestamp)?.toDate() ?: continue
                        val type = doc.getString("type") ?: continue
                        events.getOrPut(uid) { mutableListOf() }.add(Pair(type, ts))
                    }
                    chunkEvents[i] = events
                    // Merge all chunks and recompute
                    val merged = mutableMapOf<String, MutableList<Pair<String, Date>>>()
                    for (slot in chunkEvents) {
                        for ((uid, evs) in slot) merged.getOrPut(uid) { mutableListOf() }.addAll(evs)
                    }
                    val open = mutableSetOf<String>(); val openDates = mutableMapOf<String, Date>()
                    for ((uid, evs) in merged) {
                        var lastInTs: Date? = null
                        for ((type, ts) in evs.sortedBy { it.second }) {
                            if (type == "clockIn")  lastInTs = ts
                            if (type == "clockOut") lastInTs = null
                        }
                        if (lastInTs != null && lastInTs.before(todayStart)) {
                            open.add(uid); openDates[uid] = lastInTs
                        }
                    }
                    openSessionIds = open
                    openSessionDatesMap = openDates
                    recompute()
                }
            listeners.add(l)
        }
    }

    private fun listenPendingOvertime(reportIds: List<String>) {
        if (reportIds.isEmpty()) {
            pendingOvertimeIds.clear(); recompute(); return
        }
        val chunks = reportIds.chunked(30)
        val chunkPending = Array(chunks.size) { mutableSetOf<String>() }

        for ((i, chunk) in chunks.withIndex()) {
            val l = db.collection("clockEvents")
                .whereIn("userId", chunk)
                .whereNotEqualTo("overtimeNote", "")
                .addSnapshotListener { snap, _ ->
                    val pending = mutableSetOf<String>()
                    for (doc in snap?.documents ?: emptyList()) {
                        val uid = doc.getString("userId") ?: continue
                        if (doc.get("isOvertimeApproved") == null) pending.add(uid)
                    }
                    chunkPending[i] = pending
                    pendingOvertimeIds = chunkPending.fold(mutableSetOf()) { acc, s -> acc.also { it.addAll(s) } }
                    recompute()
                }
            listeners.add(l)
        }
    }

    private fun recompute() {
        _state.value = _state.value.copy(
            managerBadge           = pendingRequestIds.size + openSessionIds.size + pendingOvertimeIds.size,
            pendingRequestUserIds  = pendingRequestIds.toSet(),
            openSessionUserIds     = openSessionIds.toSet(),
            pendingOvertimeUserIds = pendingOvertimeIds.toSet(),
            openSessionDates       = openSessionDatesMap.toMap()
        )
    }

    // ── Employee ──────────────────────────────────────────────────────────────

    private fun listenForResolvedRequests(userId: String) {
        val l = db.collection("requests")
            .whereEqualTo("userId", userId)
            .whereIn("status", listOf("approved", "rejected"))
            .addSnapshotListener { snap, _ ->
                val resolvedIds = snap?.documents?.map { it.id }?.toSet() ?: emptySet()
                val unseen = resolvedIds - getSeenIds()
                _state.value = _state.value.copy(employeeBadge = unseen.size)
            }
        listeners.add(l)
    }

    // ── SharedPreferences helpers ─────────────────────────────────────────────

    private fun getSeenIds(): Set<String> =
        prefs.getStringSet("seenResolvedIds", emptySet()) ?: emptySet()

    private fun saveSeenIds(ids: Set<String>) =
        prefs.edit().putStringSet("seenResolvedIds", ids).apply()
}
