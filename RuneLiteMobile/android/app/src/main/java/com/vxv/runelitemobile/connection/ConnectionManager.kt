package com.vxv.runelitemobile.connection

import android.util.Log

object ConnectionManager {

    private const val TAG = "ConnectionManager"
    private var webSocket: okhttp3.WebSocket? = null

    var connectionState = ConnectionState.DISCONNECTED
        private set

    fun connect(ipAddress: String, port: Int = 8081) { /* ... existing code ... */ }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    fun requestConfig() {
        sendMessage("SETTINGS_REQUEST:")
    }

    fun applyConfig(configJson: String) {
        sendMessage("CONFIG_UPDATE:$configJson")
    }

    // ... rest of existing code ...
}