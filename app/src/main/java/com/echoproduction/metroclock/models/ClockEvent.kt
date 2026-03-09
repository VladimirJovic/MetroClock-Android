package com.echoproduction.metroclock.models

import com.google.firebase.Timestamp

enum class ClockEventType { CLOCK_IN, CLOCK_OUT }

data class ClockEvent(
    val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val type: ClockEventType = ClockEventType.CLOCK_IN,
    val timestamp: Timestamp = Timestamp.now(),
    val locationId: String = "",
    val overtimeNote: String? = null,
    val isOvertimeApproved: Boolean? = null,
    val managerNote: String? = null,
    val correctedHours: Double? = null
)