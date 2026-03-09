package com.echoproduction.metroclock.models

data class MCOffice(
    val id: String = "",
    val name: String = "",
    val ssid: String = "",
    val workspaceId: String = "",
    val gpsLat: Double = 0.0,
    val gpsLng: Double = 0.0,
    val gpsRadius: Double = 100.0
)