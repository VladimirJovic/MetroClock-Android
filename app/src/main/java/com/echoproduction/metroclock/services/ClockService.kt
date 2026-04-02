package com.echoproduction.metroclock.services

import com.echoproduction.metroclock.models.ClockEvent
import com.echoproduction.metroclock.models.ClockEventType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.Date

class ClockService {
    private val db = FirebaseFirestore.getInstance()

    private val _todayEvents = MutableStateFlow<List<ClockEvent>>(emptyList())
    val todayEvents: StateFlow<List<ClockEvent>> = _todayEvents

    private val _isClockedIn = MutableStateFlow(false)
    val isClockedIn: StateFlow<Boolean> = _isClockedIn

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val lastClockIn: ClockEvent? get() = _todayEvents.value.lastOrNull { it.type == ClockEventType.CLOCK_IN }

    fun fetchTodayEvents(userId: String) {
        val startOfYesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        db.collection("clockEvents")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("timestamp", Timestamp(startOfYesterday))
            .get()
            .addOnSuccessListener { snapshot ->
                val all = snapshot.documents.mapNotNull { doc ->
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
                }.sortedBy { it.timestamp.toDate() }

                _todayEvents.value = all
                _isClockedIn.value = all.lastOrNull()?.type == ClockEventType.CLOCK_IN
            }
    }

    fun clockIn(userId: String, workspaceId: String, locationId: String, taskIds: List<String>? = null) {
        _isLoading.value = true
        val event = hashMapOf<String, Any>(
            "userId" to userId,
            "workspaceId" to workspaceId,
            "type" to "clockIn",
            "timestamp" to Timestamp.now(),
            "locationId" to locationId
        )
        taskIds?.let { if (it.isNotEmpty()) event["taskIds"] = it }

        db.collection("clockEvents").add(event)
            .addOnSuccessListener {
                _isLoading.value = false
                fetchTodayEvents(userId)
            }
            .addOnFailureListener { _isLoading.value = false }
    }

    fun clockOut(
        userId: String, workspaceId: String, locationId: String,
        overtimeNote: String? = null, managerId: String? = null,
        plannedHours: Double = 0.0
    ) {
        _isLoading.value = true
        val officeHours = calculateOfficeHoursToNow()

        fetchApprovedRemoteHoursToday(userId, workspaceId) { remoteHours ->
            val totalHours = officeHours + remoteHours
            val isOvertime = totalHours > plannedHours
            val overtimeAmount = totalHours - plannedHours

            val event = hashMapOf<String, Any>(
                "userId" to userId,
                "workspaceId" to workspaceId,
                "type" to "clockOut",
                "timestamp" to Timestamp.now(),
                "locationId" to locationId
            )
            overtimeNote?.let { event["overtimeNote"] = it }

            db.collection("clockEvents").add(event)
                .addOnSuccessListener {
                    _isLoading.value = false
                    if (isOvertime && managerId != null && overtimeNote != null) {
                        val request = hashMapOf<String, Any>(
                            "userId" to userId,
                            "workspaceId" to workspaceId,
                            "managerId" to managerId,
                            "type" to "overtime",
                            "status" to "pending",
                            "date" to Timestamp.now(),
                            "employeeNote" to overtimeNote,
                            "overtimeHours" to overtimeAmount,
                            "createdAt" to Timestamp.now()
                        )
                        db.collection("requests").add(request)
                    }
                    fetchTodayEvents(userId)
                }
                .addOnFailureListener { _isLoading.value = false }
        }
    }

    fun checkOvertimeBeforeClockOut(
        userId: String, workspaceId: String,
        plannedHours: Double, callback: (Boolean) -> Unit
    ) {
        val officeHours = calculateOfficeHoursToNow()
        fetchApprovedRemoteHoursToday(userId, workspaceId) { remoteHours ->
            callback(officeHours + remoteHours > plannedHours)
        }
    }

    fun calculateOfficeHoursToNow(): Double {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val sorted = _todayEvents.value.sortedBy { it.timestamp.toDate() }

        // Find the active (unclosed) clockIn across yesterday+today
        var activeClockIn: Timestamp? = null
        for (event in sorted) {
            if (event.type == ClockEventType.CLOCK_IN) activeClockIn = event.timestamp
            if (event.type == ClockEventType.CLOCK_OUT) activeClockIn = null
        }

        // Overnight session: clockIn was before today's midnight, still open
        if (activeClockIn != null && activeClockIn.toDate().before(startOfToday)) {
            return (Date().time - activeClockIn.toDate().time) / 3600000.0
        }

        // Normal same-day: sum all of today's sessions
        val todayOnly = sorted.filter { !it.timestamp.toDate().before(startOfToday) }
        var total = 0.0
        var pairIn: Timestamp? = null
        for (event in todayOnly) {
            if (event.type == ClockEventType.CLOCK_IN) pairIn = event.timestamp
            if (event.type == ClockEventType.CLOCK_OUT && pairIn != null) {
                total += (event.timestamp.toDate().time - pairIn.toDate().time) / 3600000.0
                pairIn = null
            }
        }
        if (pairIn != null) {
            total += (Date().time - pairIn.toDate().time) / 3600000.0
        }
        return total
    }

    private fun fetchApprovedRemoteHoursToday(
        userId: String, workspaceId: String,
        callback: (Double) -> Unit
    ) {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val startOfTomorrow = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.time

        db.collection("requests")
            .whereEqualTo("userId", userId)
            .whereEqualTo("type", "remoteWork")
            .get()
            .addOnSuccessListener { snapshot ->
                val total = snapshot.documents.sumOf { doc ->
                    val d = doc.data ?: return@sumOf 0.0
                    if ((d["status"] as? String) != "approved") return@sumOf 0.0
                    val ts = (d["date"] as? Timestamp) ?: (d["dateFrom"] as? Timestamp)
                    val date = ts?.toDate() ?: return@sumOf 0.0
                    if (date.before(startOfDay) || !date.before(startOfTomorrow)) return@sumOf 0.0
                    (d["remoteHours"] as? Double)
                        ?: (d["remoteHours"] as? Long)?.toDouble()
                        ?: 0.0
                }
                callback(total)
            }
            .addOnFailureListener { callback(0.0) }
    }
}