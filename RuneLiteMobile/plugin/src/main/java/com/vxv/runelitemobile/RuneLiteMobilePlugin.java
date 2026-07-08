package com.vxv.runelitemobile;

import javax.inject.Inject;

import com.vxv.runelitemobile.input.InputInjector;
import com.vxv.runelitemobile.session.RemoteSessionManager;
import com.vxv.runelitemobile.ui.MobileUIAdapter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;

/**
 * Main entry point for the RuneLiteMobile plugin.
 * Responsible for wiring components and managing lifecycle.
 */
@Slf4j
@PluginDescriptor(
    name = "RuneLite Mobile",
    description = "Remote mobile control and touch experience for RuneLite",
    tags = {"mobile", "remote", "touch"}
)
public class RuneLiteMobilePlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientUI clientUI;

    private RemoteSessionManager sessionManager;
    private InputInjector inputInjector;
    private MobileUIAdapter uiAdapter;
    private FrameCapture frameCapture;

    @Override
    protected void startUp() throws Exception {
        log.info("Starting RuneLiteMobile plugin...");

        inputInjector = new InputInjector(client, this);
        uiAdapter = new MobileUIAdapter();
        uiAdapter.initialize(client, clientUI);
        frameCapture = new FrameCapture(client);

        int port = 8081; // TODO: Read from config
        sessionManager = new RemoteSessionManager(this, inputInjector, uiAdapter, port);
        sessionManager.startServer();

        log.info("RuneLiteMobile started. Listening on port {}", port);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Stopping RuneLiteMobile plugin...");

        if (sessionManager != null) {
            sessionManager.stopServer();
        }

        if (uiAdapter != null) {
            uiAdapter.onMobileSessionEnded();
        }

        log.info("RuneLiteMobile stopped.");
    }
}