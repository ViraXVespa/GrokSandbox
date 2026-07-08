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

@Slf4j
@PluginDescriptor(name = "RuneLite Mobile", description = "Mobile remote control + touch experience for RuneLite")
public class RuneLiteMobilePlugin extends Plugin {

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private ClientUI clientUI;

    private RemoteSessionManager sessionManager;
    private InputInjector inputInjector;
    private MobileUIAdapter uiAdapter;
    private FrameCapture frameCapture;

    @Override
    protected void startUp() throws Exception {
        log.info("RuneLiteMobile starting...");

        inputInjector = new InputInjector(client, this);
        uiAdapter = new MobileUIAdapter();
        uiAdapter.initialize(client, clientUI);
        frameCapture = new FrameCapture(client);

        int port = 8081;
        sessionManager = new RemoteSessionManager(this, inputInjector, uiAdapter, port);
        sessionManager.startServer();

        log.info("RuneLiteMobile ready on port {}", port);
    }

    @Override
    protected void shutDown() throws Exception {
        if (sessionManager != null) sessionManager.stopServer();
        if (uiAdapter != null) uiAdapter.onMobileSessionEnded();
        log.info("RuneLiteMobile stopped");
    }
}