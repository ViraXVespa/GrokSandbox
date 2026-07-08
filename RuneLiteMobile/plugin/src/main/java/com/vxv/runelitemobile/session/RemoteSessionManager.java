package com.vxv.runelitemobile.session;

import com.vxv.runelitemobile.RuneLiteMobilePlugin;
import com.vxv.runelitemobile.input.InputEvent;
import com.vxv.runelitemobile.input.InputInjector;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages remote Android client connections and the bidirectional communication channel.
 * This is the core of the PC-side remote functionality.
 */
@Slf4j
public class RemoteSessionManager extends WebSocketServer {

    private final RuneLiteMobilePlugin plugin;
    private final InputInjector inputInjector;
    private final Map<WebSocket, String> connectedClients = new ConcurrentHashMap<>();

    public RemoteSessionManager(RuneLiteMobilePlugin plugin, InputInjector inputInjector, int port) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
        this.inputInjector = inputInjector;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientAddress = conn.getRemoteSocketAddress().toString();
        connectedClients.put(conn, clientAddress);
        log.info("Android client connected: {}", clientAddress);

        // TODO: Trigger MobileUIAdapter.onMobileSessionStarted()
        // TODO: Send initial game state / config snapshot to client
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String clientAddress = connectedClients.remove(conn);
        log.info("Android client disconnected: {} ({})", clientAddress, reason);

        // TODO: Trigger MobileUIAdapter.onMobileSessionEnded() if no more clients
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        log.debug("Received message from {}: {}", conn.getRemoteSocketAddress(), message);

        // TODO: Parse message (JSON or custom protocol)
        // - If it's an InputEvent → inputInjector.handleEvent(...)
        // - If it's a settings request → send current config
        // - If it's a frame request or other control message
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.error("WebSocket error from client", ex);
    }

    @Override
    public void onStart() {
        log.info("RemoteSessionManager WebSocket server started on port {}", getPort());
    }

    public void startServer() {
        this.start();
        log.info("RemoteSessionManager starting...");
    }

    public void stopServer() {
        try {
            this.stop();
            log.info("RemoteSessionManager stopped.");
        } catch (Exception e) {
            log.error("Error stopping RemoteSessionManager", e);
        }
    }

    // TODO: Method to broadcast frames to all connected clients
    // public void broadcastFrame(byte[] frameData) { ... }
}