package com.vxv.runelitemobile.connection

import android.util.Log

/**
 * Handles connection to the RuneLite plugin server on PC.
 * Manages WebSocket (or future protocol) lifecycle, reconnection, and message routing.
 */
object ConnectionManager {

    private const val TAG = "ConnectionManager"

    // TODO: OkHttpClient + WebSocket instance
    // TODO: Current session state (Connected, Connecting, Disconnected)

    fun connect(ipAddress: String, port: Int = 8081) {
        Log.d(TAG, "Connecting to $ipAddress:$port...")
        // TODO: Build WebSocket request, set listeners for onOpen, onMessage, onFailure
        // On successful connect: notify UI, start frame receiver
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        // TODO: Close WebSocket gracefully
    }

    // TODO: sendInputEvent(event: InputEvent)
    // TODO: requestConfigSnapshot() for mobile settings UI
}