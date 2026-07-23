package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * Rolling "will a notable drop land in the next N minutes?" entertainment poll.
 */
public class RareDropModule extends AutoPollModule {

    private Instant windowEnds;
    private static final int WINDOW_MIN = 15;

    public RareDropModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Rare Drops"; }

    @Override
    public String getDescription() { return "Yes/No rare drop windows"; }

    @Override
    public String getCategory() { return "Other"; }

    @Override
    public Color getAccentColor() { return new Color(255, 215, 0); }

    @Override
    protected void onModuleActivate() {
        openWindow();
    }

    private void openWindow() {
        windowEnds = Instant.now().plus(Duration.ofMinutes(WINDOW_MIN));
        openChoicePoll(
            "Notable rare drop in the next " + WINDOW_MIN + " minutes?",
            YES_NO,
            "RARE_WINDOW");
        statusLine = "Window " + WINDOW_MIN + "m";
    }

    @Override
    public void onGameTick(net.runelite.api.events.GameTick event) {
        if (!armed || windowEnds == null) {
            return;
        }
        if (Instant.now().isAfter(windowEnds) && hasOpenPoll()) {
            resolveYesNo(false);
            openWindow();
        }
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower,
            "rare drop", "you receive a dragon", "you receive an abyssal",
            "you receive a visage", "you receive a jar", "pet ",
            "you have a funny feeling", "followed by a", // pet message
            "brimstone key", "unsired", "you receive a dragonbone necklace")) {
            if (hasOpenPoll()) {
                resolveYesNo(true);
                openWindow();
            }
        }
    }
}
