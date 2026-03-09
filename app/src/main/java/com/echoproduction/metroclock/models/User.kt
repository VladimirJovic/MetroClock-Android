package com.echoproduction.metroclock.models

enum class UserRole { ADMIN, MANAGER, EMPLOYEE }

data class MCUser(
    val id: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val role: UserRole = UserRole.EMPLOYEE,
    val workspaceId: String = "",
    val managerId: String? = null,
    val profileImageURL: String? = null,
    val isActive: Boolean = true,
    val workDays: List<Int>? = null,
    val dailyHours: Map<String, Double>? = null,
    val hourlyRate: Double? = null,
    val currency: String? = null,
    val overtimeMultiplier: Double? = null
) {
    val fullName: String get() = "$firstName $lastName"
}