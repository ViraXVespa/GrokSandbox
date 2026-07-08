package com.vxv.runelitemobile.session;

import com.vxv.runelitemobile.RuneLiteMobilePlugin;
import com.vxv.runelitemobile.input.InputEvent;
import com.vxv.runelitemobile.input.InputInjector;
import com.vxv.runelitemobile.ui.MobileUIAdapter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket server that manages connections from Android clients.
 * Handles input messages and frame streaming.
 */
@Slf4j
public class RemoteSessionManager extends WebSocketServer {

    private final RuneLiteMobilePlugin plugin;
    private final InputInjector inputInjector;
    private final MobileUIAdapter uiAdapter;
    private final Map<WebSocket, String> connectedClients = new ConcurrentHashMap<>();

    public RemoteSessionManager(RuneLiteMobilePlugin plugin, InputInjector inputInjector,
                                MobileUIAdapter uiAdapter, int port) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
        this.inputInjector = inputInjector;
        this.uiAdapter = uiAdapter;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String address = conn.getRemoteSocketAddress().toString();
        connectedClients.put(conn, address);
        log.info("Client connected: {}", address);

        if (connectedClients.size() == 1) {
            uiAdapter.onMobileSessionStarted();
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String address = connectedClients.remove(conn);
        log.info("Client disconnected: {} ({}) ", address, reason);

        if (connectedClients.isEmpty()) {
            uiAdapter.onMobileSessionEnded();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        InputEvent event = parseInputMessage(message);
        if (event != null) {
            inputInjector.handleEvent(event);
        }
    }

    private InputEvent parseInputMessage(String message) {
        try {
            String[] parts = message.split(":", 2);
            InputEvent.Type type = InputEvent.Type.valueOf(parts[0]);
            String[] values = parts.length > 1 ? parts[1].split(",") : new String[0];

            float x = values.length > 0 ? parseFloatSafe(values[0]) : 0f;
            float y = values.length > 1 ? parseFloatSafe(values[1]) : 0f;
            float dx = values.length > 2 ? parseFloatSafe(values[2]) : 0f;
            float dy = values.length > 3 ? parseFloatSafe(values[3]) : 0f;
            float scale = values.length > 4 ? parseFloatSafe(values[4]) : 0f;

            return new InputEvent(type, x, y, dx, dy, scale);
        } catch (Exception e) {
            return null;
        }
    }

    private float parseFloatSafe(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    public void broadcastFrame(byte[] frameData) {
        for (WebSocket client : connectedClients.keySet()) {
            try {
                client.send(frameData);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.error("WebSocket error", ex);
    }

    @Override
    public void onStart() {
        log.info("RemoteSessionManager started");
    }

    public void startServer() {
        this.start();
    }

    public void stopServer() {
        try {
            this.stop();
        } catch (Exception ignored) {}
    }
}