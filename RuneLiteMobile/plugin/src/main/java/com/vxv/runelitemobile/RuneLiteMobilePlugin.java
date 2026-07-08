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

    private RemoteSessionManager sessionManager;
    private InputInjector inputInjector;
    private MobileUIAdapter uiAdapter;

    @Override
    protected void startUp() throws Exception {
        log.info("RuneLiteMobile plugin starting up...");

        // Create core components
        inputInjector = new InputInjector(client, this);
        uiAdapter = new MobileUIAdapter();

        // Get port from config (or default)
        int port = 8081; // TODO: read from RuneLiteMobileConfig

        sessionManager = new RemoteSessionManager(this, inputInjector, port);
        sessionManager.startServer();

        log.info("RuneLiteMobile ready. Listening for Android connections on port {}", port);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("RuneLiteMobile shutting down...");

        if (sessionManager != null) {
            sessionManager.stopServer();
        }

        // TODO: uiAdapter restore UI if needed
    }

    // TODO: Expose getters or methods for other components if needed
    public InputInjector getInputInjector() {
        return inputInjector;
    }
}