package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;

/**
 * Opens a multi-choice poll on which tracked skill will level next (among those that gained XP).
 */
public class LevelUpRaceModule extends AutoPollModule {

    private final Set<Skill> contenders = new LinkedHashSet<>();
    private int lastLevelsHash;

    public LevelUpRaceModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Level-Up Race"; }

    @Override
    public String getDescription() { return "Which skill levels next?"; }

    @Override
    public String getCategory() { return "Other"; }

    @Override
    public Color getAccentColor() { return new Color(255, 200, 50); }

    @Override
    protected void onModuleActivate() {
        contenders.clear();
        // Default popular skills as options; refined as XP rolls in
        openChoicePoll(
            "Which skill levels up next?",
            Arrays.asList("Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic",
                "Runecraft", "Construction", "Hitpoints", "Agility", "Herblore", "Thieving",
                "Crafting", "Fletching", "Slayer", "Hunter", "Mining", "Smithing",
                "Fishing", "Cooking", "Firemaking", "Woodcutting", "Farming", "Other"),
            "LEVEL_UP");
    }

    @Override
    public void onStatChanged(StatChanged event) {
        if (!armed) {
            return;
        }
        Skill skill = event.getSkill();
        if (skill == null || skill == Skill.OVERALL) {
            return;
        }
        contenders.add(skill);
        statusLine = "Hot: " + skill.getName();

        // Level-up detection: boosted? Real level changes with level field
        int level = event.getLevel();
        // StatChanged fires on XP; level field is current level. Track via chat is more reliable.
    }

    @Override
    protected void handleChat(String raw, String lower) {
        // "Congratulations, you just advanced a Mining level."
        if (lower.contains("congratulations") && lower.contains("advanced") && lower.contains("level")) {
            String skillName = extractSkill(raw);
            if (skillName != null && hasOpenPoll()) {
                var poll = plugin.getBetManager().getPollById(activePollId);
                if (poll.isEmpty()) {
                    return;
                }
                var options = poll.get().getOptions();
                int idx = -1;
                for (int i = 0; i < options.size(); i++) {
                    if (options.get(i).equalsIgnoreCase(skillName)) {
                        idx = i;
                        break;
                    }
                }
                if (idx < 0) {
                    // Other
                    for (int i = 0; i < options.size(); i++) {
                        if (options.get(i).equalsIgnoreCase("Other")) {
                            idx = i;
                            break;
                        }
                    }
                }
                if (idx >= 0) {
                    resolveChoice(idx, skillName);
                    openChoicePoll(
                        "Which skill levels up next?",
                        Arrays.asList("Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic",
                            "Runecraft", "Construction", "Hitpoints", "Agility", "Herblore", "Thieving",
                            "Crafting", "Fletching", "Slayer", "Hunter", "Mining", "Smithing",
                            "Fishing", "Cooking", "Firemaking", "Woodcutting", "Farming", "Other"),
                        "LEVEL_UP");
                }
            }
        }
    }

    private static String extractSkill(String raw) {
        // "... advanced a Mining level" / "... advanced an Agility level"
        String lower = raw.toLowerCase();
        int a = lower.indexOf("advanced a ");
        int an = lower.indexOf("advanced an ");
        int start = a >= 0 ? a + "advanced a ".length() : (an >= 0 ? an + "advanced an ".length() : -1);
        if (start < 0) {
            return null;
        }
        int end = lower.indexOf(" level", start);
        if (end < 0) {
            return null;
        }
        String name = raw.substring(start, end).trim();
        // capitalize
        if (name.isEmpty()) {
            return null;
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }
}
