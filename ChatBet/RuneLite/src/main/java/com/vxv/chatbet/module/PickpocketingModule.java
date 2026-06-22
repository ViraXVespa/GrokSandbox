package com.vxv.chatbet.modules;

import com.vxv.chatbet.module.BetModule;
import com.vxv.chatbet.ChatBetPlugin;
import net.runelite.api.Skill;
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
        // Basic tick-based tracking (attempts/successes will be expanded here)
        log.debug("PickpocketingModule onGameTick");
        // TODO: Animation check or inventory delta for attempt detection
    }

    @Override
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() == Skill.THIEVING && plugin != null) {
            // XP gain = potential success (full attempt/success logic in next iteration)
            log.debug("Thieving XP changed in PickpocketingModule - potential success");
            // plugin.successes.incrementAndGet(); // placeholder for later
        }
    }

    @Override
    public long getElvesToGoal() {
        // Use plugin's accurate XP goal logic (start/end from tracker)
        int xpNeeded = plugin.getXpToGoal();
        double xpPerElf = 353.3;
        return xpNeeded > 0 ? (long) Math.ceil(xpNeeded / xpPerElf) : 0L;
    }

    // TODO: Expand interface compliance and tracking as needed
}