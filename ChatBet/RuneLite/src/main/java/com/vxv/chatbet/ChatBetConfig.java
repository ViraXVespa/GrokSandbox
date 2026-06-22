package com.vxv.chatbet;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("chatbet")
public interface ChatBetConfig extends Config {

    @ConfigSection(
        name = "General",
        description = "General settings",
        position = 0
    )
    String generalSection = "general";

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show Overlay",
        description = "Toggle the main overlay",
        section = generalSection
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
        keyName = "etcRate",
        name = "ETC Drop Rate (1 in X)",
        description = "Approximate ETC rate for probability calculations",
        section = generalSection
    )
    default int etcRate() {
        return 1024;
    }

    @ConfigItem(
        keyName = "thievingGoalXp",
        name = "Thieving Goal XP",
        description = "Your target Thieving XP (used for goal calculations)",
        section = generalSection
    )
    default int thievingGoalXp() {
        return 13034431; // Level 99 default
    }

    // === Phase 1 Side Panel Goal Persistence ===
    @ConfigItem(
        keyName = "activeTaskName",
        name = "Active Task",
        description = "Currently selected task (internal)",
        hidden = true
    )
    default String activeTaskName() {
        return "";
    }

    @ConfigItem(
        keyName = "currentGoalPercentage",
        name = "Current Goal Percentage",
        description = "Current goal percentage for active task (internal)",
        hidden = true
    )
    default int currentGoalPercentage() {
        return 30;
    }
}