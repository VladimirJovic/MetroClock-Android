package com.echoproduction.metroclock.services

import com.echoproduction.metroclock.models.MCOffice
import com.echoproduction.metroclock.models.WorkspaceConfig
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class WorkspaceService {
    private val db = Firebase.firestore

    private val _offices = MutableStateFlow<List<MCOffice>>(emptyList())
    val offices: StateFlow<List<MCOffice>> = _offices

    private val _config = MutableStateFlow(WorkspaceConfig())
    val config: StateFlow<WorkspaceConfig> = _config

    private val _currentLat = MutableStateFlow(0.0)
    private val _currentLng = MutableStateFlow(0.0)

    val nearestOffice: MCOffice? get() {
        val lat = _currentLat.value
        val lng = _currentLng.value
        if (lat == 0.0 && lng == 0.0) return null
        return _offices.value.firstOrNull { office ->
            distanceMeters(lat, lng, office.gpsLat, office.gpsLng) <= office.gpsRadius
        }
    }

    val isInOfficeZone: Boolean get() = nearestOffice != null

    fun fetchOffices(workspaceId: String) {
        db.collection("offices")
            .whereEqualTo("workspaceId", workspaceId)
            .addSnapshotListener { snapshot, _ ->
                snapshot ?: return@addSnapshotListener
                _offices.value = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    MCOffice(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        ssid = data["ssid"] as? String ?: "",
                        workspaceId = data["workspaceId"] as? String ?: "",
                        gpsLat = (data["gpsLat"] as? Double) ?: (data["gpsLat"] as? Long)?.toDouble() ?: 0.0,
                        gpsLng = (data["gpsLng"] as? Double) ?: (data["gpsLng"] as? Long)?.toDouble() ?: 0.0,
                        gpsRadius = (data["gpsRadius"] as? Double) ?: (data["gpsRadius"] as? Long)?.toDouble() ?: 100.0
                    )
                }
            }
    }

    fun fetchWorkspaceConfig(workspaceId: String) {
        db.collection("workspaces").document(workspaceId).get()
            .addOnSuccessListener { doc ->
                val data = doc.data ?: return@addOnSuccessListener
                @Suppress("UNCHECKED_CAST")
                _config.value = WorkspaceConfig(
                    clickupApiToken = data["clickupApiToken"] as? String,
                    clickupUserMappings = (data["clickupUserMappings"] as? Map<String, String>) ?: emptyMap(),
                    slackWebhookUrl = data["slackWebhookUrl"] as? String,
                    discordWebhookUrl = data["discordWebhookUrl"] as? String,
                    slackUserMappings = (data["slackUserMappings"] as? Map<String, String>) ?: emptyMap(),
                    discordUserMappings = (data["discordUserMappings"] as? Map<String, String>) ?: emptyMap()
                )
            }
    }

    fun updateLocation(lat: Double, lng: Double) {
        _currentLat.value = lat
        _currentLng.value = lng
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dphi = Math.toRadians(lat2 - lat1)
        val dlambda = Math.toRadians(lng2 - lng1)
        val a = sin(dphi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dlambda / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}