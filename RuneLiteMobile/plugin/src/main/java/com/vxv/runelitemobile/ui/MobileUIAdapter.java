package com.vxv.runelitemobile.ui;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ClientUI;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
        log.info("Hiding desktop UI elements for mobile...");
        hideComponent("sidebarPanel");
        hideComponent("titleBar");
        uiHidden = true;
    }

    public void onMobileSessionEnded() {
        if (!uiHidden) return;
        log.info("Restoring desktop UI...");
        showComponent("sidebarPanel");
        showComponent("titleBar");
        uiHidden = false;
    }

    public void applyScale(float scaleFactor) {
        log.debug("Applying scale: {}", scaleFactor);
        // TODO: Scale overlays, fonts, etc.
    }

    private void hideComponent(String fieldName) {
        try {
            Object comp = getField(clientUI, fieldName);
            if (comp != null) invoke(comp, "setVisible", false);
        } catch (Exception ignored) {}
    }

    private void showComponent(String fieldName) {
        try {
            Object comp = getField(clientUI, fieldName);
            if (comp != null) invoke(comp, "setVisible", true);
        } catch (Exception ignored) {}
    }

    private Object getField(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) { return null; }
    }

    private void invoke(Object target, String method, Object... args) {
        try {
            Method m = target.getClass().getDeclaredMethod(method, boolean.class);
            m.setAccessible(true);
            m.invoke(target, args);
        } catch (Exception ignored) {}
    }
}