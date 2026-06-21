package com.vxv.chatbet;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("chatbet")
public interface ChatBetConfig extends Config
{
    @ConfigItem(
        keyName = "thievingGoalXp",
        name = "Thieving Goal XP (from XP Tracker)",
        description = "Target Thieving XP from your XP Tracker goal. Used to calculate 30% mark from login XP.",
        position = 0
    )
    default int thievingGoalXp()
    {
        return 20000000; // placeholder - update with your actual goal target
    }

    @ConfigItem(
        keyName = "xpPerElfPickpocket",
        name = "XP per Elf Pickpocket Success",
        description = "Thieving XP gained per successful pickpocket on an elf (Prifddinas/Lletya)",
        position = 1
    )
    default int xpPerElfPickpocket()
    {
        return 353;
    }

    @ConfigItem(
        keyName = "etcRate",
        name = "ETC Drop Rate (1 in X successes)",
        description = "Assumed drop rate for Eternal Teleport Crystal (or Enhanced Seed) per successful pickpocket. Used for expected value and dry streak probability.",
        position = 2
    )
    @Range(min = 100, max = 50000)
    default int etcRate()
    {
        return 1024; // 1/1024 per successful pickpocket on elves (wiki). Rogue outfit doubles effective rate.
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "Display the ChatBet tracking panel",
        position = 3
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "resetOnLogin",
        name = "Reset counters on login",
        description = "Automatically reset all session counters when you log in (recommended for streams)",
        position = 4
    )
    default boolean resetOnLogin()
    {
        return true;
    }
}