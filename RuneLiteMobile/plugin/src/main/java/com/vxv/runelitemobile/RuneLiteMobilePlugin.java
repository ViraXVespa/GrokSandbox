package com.vxv.runelitemobile;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * RuneLiteMobile plugin - PC side of the hybrid mobile experience.
 *
 * Responsibilities (aligned to project vision):
 * - Start embedded remote server / session manager for Android app connections
 * - Handle incoming touch/gesture events and inject them into the game (camera rotate, zoom, clicks)
 * - Hide desktop-only UI elements (sidebar, top bar) when a mobile remote session is active
 * - Provide rescaling hints and mobile-optimized rendering adjustments
 * - Expose RuneLite config + plugin settings over the protocol so the Android app can offer a touch-friendly settings UI
 */
@Slf4j
@PluginDescriptor(
    name = "RuneLite Mobile",
    description = "Enables seamless mobile play with natural touch controls, hidden desktop UI, and mobile settings access.",
    tags = {"mobile", "remote", "touch", "android"}
)
public class RuneLiteMobilePlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    // TODO (Phase 1): Inject or create RemoteSessionManager (WebSocket server or custom protocol)
    // private RemoteSessionManager sessionManager;

    // TODO: InputInjector, UIAdapter (hider + rescaler), MobileConfigBridge

    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception {
        log.info("RuneLiteMobile plugin starting up...");

        // TODO: Initialize and start the remote session server (listen on local port)
        // sessionManager = new RemoteSessionManager(client, this);
        // sessionManager.start();

        // TODO: Register any overlays, panels, or mobile-specific UI
        // Example: navButton = NavigationButton.builder()... .build();
        // clientToolbar.addNavigation(navButton);

        log.info("RuneLiteMobile ready. Waiting for Android client connections on local network.");
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("RuneLiteMobile shutting down...");

        // TODO: Gracefully stop sessionManager, clean up connections, restore any hidden UI
        // if (sessionManager != null) sessionManager.stop();
    }

    // TODO (future):
    // - onRemoteSessionStarted() / onRemoteSessionEnded() to toggle UI hiding + scaling
    // - handleIncomingTouchEvent(x, y, action) -> inject via client.getMouseManager() or key events
    // - provideMobileConfigSnapshot() for Android settings UI
    // - applyMobileUIScale(float scale)
}
