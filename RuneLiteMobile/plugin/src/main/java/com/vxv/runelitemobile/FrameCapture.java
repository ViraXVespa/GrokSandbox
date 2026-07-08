package com.vxv.runelitemobile;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import java.awt.Canvas;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

/**
 * Captures the current game frame for streaming.
 * Tries to use real canvas capture when possible.
 */
@Slf4j
public class FrameCapture {

    private final Client client;
    private long lastCaptureTime = 0;
    private boolean realCaptureWorking = false;

    public FrameCapture(Client client) {
        this.client = client;
    }

    public byte[] captureCurrentFrame() {
        long now = System.currentTimeMillis();
        if (now - lastCaptureTime < 66) { // ~15 FPS cap for now
            return null;
        }
        lastCaptureTime = now;

        try {
            // Try to get the game canvas
            Canvas gameCanvas = client.getCanvas();
            if (gameCanvas != null) {
                // Create a screenshot of the canvas
                BufferedImage image = new BufferedImage(
                    gameCanvas.getWidth(),
                    gameCanvas.getHeight(),
                    BufferedImage.TYPE_INT_RGB
                );

                // Use reflection to call paint() if needed, or use createImage
                // For now we use a simpler approach
                gameCanvas.paint(image.getGraphics());

                realCaptureWorking = true;

                // TODO: Convert BufferedImage to byte[] (PNG/JPEG compression)
                // For now return a marker that real capture happened
                return ("REAL_FRAME:" + now).getBytes();
            }
        } catch (Exception e) {
            if (realCaptureWorking) {
                log.warn("Real canvas capture failed, falling back to test frames", e);
                realCaptureWorking = false;
            }
        }

        // Fallback to test frame
        return ("TEST_FRAME:" + now).getBytes();
    }
}