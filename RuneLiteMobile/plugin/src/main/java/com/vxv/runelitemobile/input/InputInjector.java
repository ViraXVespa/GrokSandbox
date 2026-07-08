package com.vxv.runelitemobile.input;

import com.vxv.runelitemobile.RuneLiteMobilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

/**
 * Handles injecting input events from the phone into RuneLite.
 */
@Slf4j
public class InputInjector {

    private final Client client;
    private final RuneLiteMobilePlugin plugin;

    public InputInjector(Client client, RuneLiteMobilePlugin plugin) {
        this.client = client;
        this.plugin = plugin;
    }

    public void handleEvent(InputEvent event) {
        switch (event.type) {
            case TAP:
                injectClick(event.x, event.y);
                break;
            case LONG_PRESS:
                injectLongPress(event.x, event.y);
                break;
            case SWIPE_CAMERA:
                injectCameraControl(event.deltaX, event.deltaY);
                break;
            case PINCH_SCALE:
                injectZoom(event.scale);
                break;
            default:
                break;
        }
    }

    private void injectClick(float x, float y) {
        log.info("TAP at ({}, {})", x, y);
        // TODO: Real implementation using MouseManager or canvas events
    }

    private void injectLongPress(float x, float y) {
        log.info("LONG_PRESS at ({}, {})", x, y);
    }

    private void injectCameraControl(float deltaX, float deltaY) {
        // Determine dominant direction
        boolean horizontal = Math.abs(deltaX) > Math.abs(deltaY);

        if (horizontal) {
            // Horizontal swipe → yaw (left/right)
            log.info("Camera yaw: {}", deltaX > 0 ? "right" : "left");
        } else {
            // Vertical swipe → pitch (up/down)
            log.info("Camera pitch: {}", deltaY > 0 ? "down" : "up");
        }

        // TODO: Actually simulate key presses or mouse drag here
        // For example: use KeyManager to press arrow keys based on direction
    }

    private void injectZoom(float scale) {
        log.info("PINCH/ZOOM scale: {}", scale);
        // TODO: Simulate mouse wheel or use camera zoom API
    }
}