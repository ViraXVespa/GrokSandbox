package com.vxv.runelitemobile.session;

import com.vxv.runelitemobile.RuneLiteMobilePlugin;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages remote sessions from Android clients.
 * Core of the PC-side server functionality.
 *
 * TODO (high priority for next implementation burst):
 * - Start embedded WebSocket server on plugin enable (e.g. on port 8081 or configurable)
 * - Handle client connections, authentication (simple LAN PIN or auto-accept on same subnet)
 * - Receive InputEvent messages from phone and dispatch to InputInjector
 * - Send game frame updates (initially MJPEG or simple PNG stream) to connected clients
 * - Broadcast session state changes (connected/disconnected) to trigger UIAdapter
 * - Graceful shutdown and client cleanup
 */
@Slf4j
public class RemoteSessionManager {

    private final RuneLiteMobilePlugin plugin;
    // TODO: WebSocketServer instance, connected clients map, etc.

    public RemoteSessionManager(RuneLiteMobilePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        log.info("Starting RemoteSessionManager...");
        // TODO: Initialize and start WebSocket server
        // server = new WebSocketServer(new InetSocketAddress(port));
        // server.start();
    }

    public void stop() {
        log.info("Stopping RemoteSessionManager...");
        // TODO: Stop server, close all client connections
    }

    // TODO: Methods like onClientConnected, onInputEventReceived(InputEvent event), broadcastFrame(byte[] frameData)
}