package com.vxv.runelitemobile.ui;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ClientUI;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Handles adapting the RuneLite UI for mobile sessions.
 * Currently focuses on hiding desktop elements (sidebar, top bar).
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
        hideUiComponent("sidebarPanel");
        hideUiComponent("titleBar");
        uiHidden = true;
    }

    public void onMobileSessionEnded() {
        if (!uiHidden) return;

        log.info("Restoring desktop UI...");
        showUiComponent("sidebarPanel");
        showUiComponent("titleBar");
        uiHidden = false;
    }

    public void applyScale(float scaleFactor) {
        log.debug("Applying UI scale: {}", scaleFactor);
        // TODO: Apply scaling to overlays and UI elements
    }

    private void hideUiComponent(String fieldName) {
        try {
            Object component = getPrivateField(clientUI, fieldName);
            if (component != null) {
                invokeMethod(component, "setVisible", false);
            }
        } catch (Exception e) {
            log.debug("Could not hide component: {}", fieldName);
        }
    }

    private void showUiComponent(String fieldName) {
        try {
            Object component = getPrivateField(clientUI, fieldName);
            if (component != null) {
                invokeMethod(component, "setVisible", true);
            }
        } catch (Exception e) {
            log.debug("Could not show component: {}", fieldName);
        }
    }

    private Object getPrivateField(Object target, String fieldName) {
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
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
            Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (Exception ignored) {}
    }
}