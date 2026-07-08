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
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String clientAddress = connectedClients.remove(conn);
        log.info("Android client disconnected: {} ({})", clientAddress, reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        log.debug("Received: {}", message);

        // Simple MVP protocol parsing
        try {
            String[] parts = message.split(":", 2);
            String typeStr = parts[0];
            String data = parts.length > 1 ? parts[1] : "";

            InputEvent event = parseInputEvent(typeStr, data);
            if (event != null) {
                inputInjector.handleEvent(event);
            }
        } catch (Exception e) {
            log.warn("Failed to parse message: {}", message, e);
        }
    }

    private InputEvent parseInputEvent(String typeStr, String data) {
        try {
            InputEvent.Type type = InputEvent.Type.valueOf(typeStr);
            String[] nums = data.split(",");

            float x = nums.length > 0 ? Float.parseFloat(nums[0]) : 0f;
            float y = nums.length > 1 ? Float.parseFloat(nums[1]) : 0f;
            float deltaX = nums.length > 2 ? Float.parseFloat(nums[2]) : 0f;
            float deltaY = nums.length > 3 ? Float.parseFloat(nums[3]) : 0f;
            float scale = nums.length > 4 ? Float.parseFloat(nums[4]) : 0f;

            return new InputEvent(type, x, y, deltaX, deltaY, scale);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.error("WebSocket error", ex);
    }

    @Override
    public void onStart() {
        log.info("RemoteSessionManager started on port {}", getPort());
    }

    public void startServer() { this.start(); }
    public void stopServer() {
        try { this.stop(); } catch (Exception ignored) {}
    }
}