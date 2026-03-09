package com.echoproduction.metroclock.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WiFiService(private val context: Context) {
    private val _currentSSID = MutableStateFlow<String?>(null)
    val currentSSID: StateFlow<String?> = _currentSSID

    fun fetchSSID() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"")
            _currentSSID.value = if (ssid == "<unknown ssid>") null else ssid
        } else {
            _currentSSID.value = null
        }
    }
}