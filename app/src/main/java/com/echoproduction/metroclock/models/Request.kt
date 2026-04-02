package com.echoproduction.metroclock.models

import com.google.firebase.Timestamp

enum class RequestType { REMOTE_WORK, SICK_LEAVE, DAY_OFF, OVERTIME, OFFSITE_WORK }

enum class RequestStatus { PENDING, APPROVED, REJECTED }

data class Request(
    val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val managerId: String = "",
    val type: RequestType = RequestType.REMOTE_WORK,
    val status: RequestStatus = RequestStatus.PENDING,
    val date: Timestamp = Timestamp.now(),
    val dateFrom: Timestamp = Timestamp.now(),
    val dateTo: Timestamp = Timestamp.now(),
    val employeeNote: String? = null,
    val managerNote: String? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val remoteHours: Double? = null,
    val overtimeHours: Double? = null
)