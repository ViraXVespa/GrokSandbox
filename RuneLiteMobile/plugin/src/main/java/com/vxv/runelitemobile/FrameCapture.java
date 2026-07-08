package com.vxv.runelitemobile;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

/**
 * Captures the current game frame for streaming to Android clients.
 * Currently generates test frames for development.
 */
@Slf4j
public class FrameCapture {

    private final Client client;
    private long lastCaptureTime = 0;

    public FrameCapture(Client client) {
        this.client = client;
    }

    public byte[] captureCurrentFrame() {
        long now = System.currentTimeMillis();
        if (now - lastCaptureTime < 100) {
            return null; // Limit to ~10 FPS for testing
        }
        lastCaptureTime = now;

        // TODO: Replace with real canvas capture
        // For now, generate a simple test frame
        String timestamp = String.valueOf(now % 100000);
        String frameInfo = "FRAME:" + timestamp;

        return frameInfo.getBytes();
    }
}