package com.vxv.chatbet.module;

import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;

public interface BetModule {
    String getName();
    void onGameTick(GameTick event);
    void onStatChanged(StatChanged event);
    default long getElvesToGoal() {
        return 0;
    }
    // Add other methods as modules expand
}
