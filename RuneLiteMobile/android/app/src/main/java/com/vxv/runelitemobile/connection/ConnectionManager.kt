package com.vxv.runelitemobile.connection

import android.util.Log

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

import okio.ByteString

/**
 * Manages the WebSocket connection to the RuneLite plugin on PC.
 */
object ConnectionManager {

    private const val TAG = "ConnectionManager"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    fun connect(ipAddress: String, port: Int = 8081) {
        if (connectionState == ConnectionState.CONNECTED) return

        connectionState = ConnectionState.CONNECTING

        val request = Request.Builder()
            .url("ws://$ipAddress:$port")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                connectionState = ConnectionState.CONNECTED
                Log.d(TAG, "Connected to plugin at $ipAddress:$port")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message from plugin: $text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // TODO: Handle incoming frame data
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                connectionState = ConnectionState.DISCONNECTED
                Log.e(TAG, "Connection failed", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connectionState = ConnectionState.DISCONNECTED
                Log.d(TAG, "Connection closed: $reason")
            }
        })
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnecting")
        connectionState = ConnectionState.DISCONNECTED
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}