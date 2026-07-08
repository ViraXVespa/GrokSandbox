package com.vxv.runelitemobile;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.imageio.ImageIO;
import java.awt.Canvas;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Captures game frames and converts them to bytes for streaming.
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
            return null;
        }
        lastCaptureTime = now;

        try {
            Canvas gameCanvas = client.getCanvas();
            if (gameCanvas != null && gameCanvas.getWidth() > 0 && gameCanvas.getHeight() > 0) {

                BufferedImage image = new BufferedImage(
                    gameCanvas.getWidth(),
                    gameCanvas.getHeight(),
                    BufferedImage.TYPE_INT_RGB
                );

                gameCanvas.paint(image.getGraphics());

                // Convert to PNG bytes
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.debug("Real capture failed, using test frame", e);
        }

        // Fallback test frame
        return ("TEST:" + now).getBytes();
    }
}