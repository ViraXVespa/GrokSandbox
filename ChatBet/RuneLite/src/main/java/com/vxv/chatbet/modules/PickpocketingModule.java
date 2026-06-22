package com.vxv.chatbet.modules;

import com.vxv.chatbet.module.BetModule;
import com.vxv.chatbet.ChatBetPlugin;
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
        // Compute directly in module (avoids any delegation loop)
        int xpNeeded = plugin.getXpToGoal();  // Use plugin's XP logic
        double xpPerElf = 353.3;
        return xpNeeded > 0 ? (long) Math.ceil(xpNeeded / xpPerElf) : 0;
    }

    // TODO: More methods as needed for BetModule interface
}
