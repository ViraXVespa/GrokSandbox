package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;

/** Construction XP gained in a furniture-building burst. */
public class ConstructionModule extends AutoPollModule {

    private int sessionStartXp = -1;
    private int lastXp = -1;
    private int idleTicks;

    public ConstructionModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Construction Build"; }

    @Override
    public String getDescription() { return "Construction XP burst brackets"; }

    @Override
    public String getCategory() { return "Production"; }

    @Override
    public Color getAccentColor() { return new Color(160, 120, 90); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Construction XP this build burst? (x1000)", 
            java.util.Arrays.asList("0-5k", "5-15k", "15-30k", "30-50k", "50-100k", "100k+"),
            "CON_BURST");
        // Store as "k" units in counter
        if (plugin.getClient() != null) {
            sessionStartXp = plugin.getClient().getSkillExperience(Skill.CONSTRUCTION);
            lastXp = sessionStartXp;
        }
    }

    @Override
    public void onStatChanged(StatChanged event) {
        if (!armed || event.getSkill() != Skill.CONSTRUCTION) {
            return;
        }
        int xp = event.getXp();
        if (lastXp < 0) {
            lastXp = xp;
            sessionStartXp = xp;
            return;
        }
        if (xp > lastXp) {
            idleTicks = 0;
            if (!hasOpenPoll()) {
                openBracketPoll("Construction XP this build burst? (x1000)",
                    java.util.Arrays.asList("0-5k", "5-15k", "15-30k", "30-50k", "50-100k", "100k+"),
                    "CON_BURST");
                sessionStartXp = lastXp;
            }
            int gained = xp - sessionStartXp;
            counter.set(gained / 1000);
            statusLine = "XP +" + gained;
        }
        lastXp = xp;
    }

    @Override
    public void onGameTick(net.runelite.api.events.GameTick event) {
        if (!armed || !hasOpenPoll()) {
            return;
        }
        // Resolve after ~20s idle construction XP
        if (counter.get() > 0) {
            idleTicks++;
            if (idleTicks > 35) {
                int gainedK = counter.get();
                // Map to bracket via resolveClosest — but options are "0-5k" style
                // parseRange won't like "k" — resolve with raw thousands as number matching custom
                resolveConstruction(gainedK);
                idleTicks = 0;
            }
        }
    }

    private void resolveConstruction(int thousands) {
        // Options: 0-5, 5-15, etc. in thousands
        int value = thousands;
        // rewrite options are like "0-5k" — parseRange fails. Use custom index.
        int idx;
        if (value <= 5) idx = 0;
        else if (value <= 15) idx = 1;
        else if (value <= 30) idx = 2;
        else if (value <= 50) idx = 3;
        else if (value <= 100) idx = 4;
        else idx = 5;
        if (activePollId > 0) {
            int id = activePollId;
            plugin.getBetManager().resolvePoll(id, idx);
            plugin.announce("[ChatBet] Construction burst ~" + (thousands * 1000) + " XP → poll #" + id);
            activePollId = -1;
            counter.set(0);
            statusLine = "Burst done";
            openBracketPoll("Construction XP this build burst? (x1000)",
                java.util.Arrays.asList("0-5k", "5-15k", "15-30k", "30-50k", "50-100k", "100k+"),
                "CON_BURST");
            if (plugin.getClient() != null) {
                sessionStartXp = plugin.getClient().getSkillExperience(Skill.CONSTRUCTION);
                lastXp = sessionStartXp;
            }
        }
    }
}
