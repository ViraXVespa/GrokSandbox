package com.vxv.chatbet.modules;

import com.vxv.chatbet.module.BetModule;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PickpocketingModule implements BetModule {

    private final ChatBetPlugin plugin;

    public PickpocketingModule(ChatBetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Pickpocketing (Elves)";
    }

    @Override
    public void onGameTick(GameTick event) {
        // TODO: Implement pickpocketing-specific tracking
        log.debug("PickpocketingModule tick");
    }

    @Override
    public void onStatChanged(StatChanged event) {
        // TODO: Handle thieving XP updates
    }

    @Override
    public long getElvesToGoal() {
        // Delegate back or compute directly
        return plugin.getElvesToGoal();  // Avoid infinite loop; in practice use plugin's fallback
    }

    // TODO: More methods as needed for BetModule interface
}
