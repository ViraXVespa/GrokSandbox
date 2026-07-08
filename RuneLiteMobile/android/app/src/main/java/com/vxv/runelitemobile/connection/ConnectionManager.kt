package com.vxv.runelitemobile.connection

import android.util.Log

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket

import okio.ByteString

object ConnectionManager {

    private const val TAG = "ConnectionManager"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connect(ip: String, port: Int = 8081) {
        val request = Request.Builder().url("ws://$ip:$port").build()
        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, "Connected to plugin")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message from plugin: $text")
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // TODO: Handle binary frame data
            }
        })
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
    }
}