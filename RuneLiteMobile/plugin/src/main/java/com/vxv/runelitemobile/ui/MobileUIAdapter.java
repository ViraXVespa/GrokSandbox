package com.vxv.runelitemobile.ui;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ClientUI;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Adapts the RuneLite UI for mobile remote sessions.
 * Goal #1: Hide desktop-only elements (sidebar, top bar) when a phone is connected.
 */
@Slf4j
public class MobileUIAdapter {

    private Client client;
    private ClientUI clientUI;
    private boolean uiHidden = false;

    public void initialize(Client client, ClientUI clientUI) {
        this.client = client;
        this.clientUI = clientUI;
    }

    public void onMobileSessionStarted() {
        if (uiHidden) return;

        log.info("Hiding desktop UI for mobile session...");

        try {
            // Hide sidebar panel
            Object sidebar = getFieldValue(clientUI, "sidebarPanel");
            if (sidebar != null) {
                invokeMethod(sidebar, "setVisible", false);
            }

            // Hide top bar / title bar if possible
            Object titleBar = getFieldValue(clientUI, "titleBar");
            if (titleBar != null) {
                invokeMethod(titleBar, "setVisible", false);
            }

            uiHidden = true;
        } catch (Exception e) {
            log.warn("Could not hide UI elements (reflection). Some elements may remain visible.", e);
        }
    }

    public void onMobileSessionEnded() {
        if (!uiHidden) return;

        log.info("Restoring desktop UI...");

        try {
            Object sidebar = getFieldValue(clientUI, "sidebarPanel");
            if (sidebar != null) {
                invokeMethod(sidebar, "setVisible", true);
            }

            Object titleBar = getFieldValue(clientUI, "titleBar");
            if (titleBar != null) {
                invokeMethod(titleBar, "setVisible", true);
            }

            uiHidden = false;
        } catch (Exception e) {
            log.warn("Error restoring UI", e);
        }
    }

    public void applyScale(float scaleFactor) {
        // TODO: Apply scale to relevant overlays / UI components
        log.debug("Applying mobile UI scale: {}", scaleFactor);
    }

    // Reflection helpers
    private Object getFieldValue(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private void invokeMethod(Object target, String methodName, Object... args) {
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
            }
            Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (Exception ignored) {}
    }
}