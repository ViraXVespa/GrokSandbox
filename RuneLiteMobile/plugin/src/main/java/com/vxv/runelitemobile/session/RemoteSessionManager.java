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

@Slf4j
public class RemoteSessionManager extends WebSocketServer {

    private final RuneLiteMobilePlugin plugin;
    private final InputInjector inputInjector;
    private final MobileUIAdapter uiAdapter;
    private final Map<WebSocket, String> connectedClients = new ConcurrentHashMap<>();
    private boolean sessionActive = false;

    public RemoteSessionManager(RuneLiteMobilePlugin plugin, InputInjector inputInjector, MobileUIAdapter uiAdapter, int port) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
        this.inputInjector = inputInjector;
        this.uiAdapter = uiAdapter;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectedClients.put(conn, conn.getRemoteSocketAddress().toString());
        log.info("Client connected. Total: {}", connectedClients.size());

        if (!sessionActive) {
            sessionActive = true;
            uiAdapter.onMobileSessionStarted();
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connectedClients.remove(conn);
        log.info("Client disconnected. Remaining: {}", connectedClients.size());

        if (connectedClients.isEmpty() && sessionActive) {
            sessionActive = false;
            uiAdapter.onMobileSessionEnded();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        InputEvent event = parseMessage(message);
        if (event != null) {
            inputInjector.handleEvent(event);
        }
    }

    private InputEvent parseMessage(String message) {
        // Simple protocol: TYPE:x,y,dx,dy,scale
        try {
            String[] parts = message.split(":", 2);
            InputEvent.Type type = InputEvent.Type.valueOf(parts[0]);
            String[] values = parts.length > 1 ? parts[1].split(",") : new String[0];

            float x = values.length > 0 ? safeParse(values[0]) : 0f;
            float y = values.length > 1 ? safeParse(values[1]) : 0f;
            float dx = values.length > 2 ? safeParse(values[2]) : 0f;
            float dy = values.length > 3 ? safeParse(values[3]) : 0f;
            float scale = values.length > 4 ? safeParse(values[4]) : 0f;

            return new InputEvent(type, x, y, dx, dy, scale);
        } catch (Exception e) {
            return null;
        }
    }

    private float safeParse(String s) {
        try { return Float.parseFloat(s); } catch (Exception e) { return 0f; }
    }

    public void broadcastFrame(byte[] frameData) {
        if (connectedClients.isEmpty()) return;
        for (WebSocket client : connectedClients.keySet()) {
            try {
                client.send(frameData); // For binary frames later; MVP can use base64 string
            } catch (Exception ignored) {}
        }
    }

    @Override public void onError(WebSocket conn, Exception ex) { log.error("WS error", ex); }
    @Override public void onStart() { log.info("RemoteSessionManager started"); }

    public void startServer() { this.start(); }
    public void stopServer() { try { this.stop(); } catch (Exception ignored) {} }
}