package com.vxv.advancedminingtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(AdvancedMiningTrackerConfig.GROUP)
public interface AdvancedMiningTrackerConfig extends Config
{
	String GROUP = "advanced-mining-tracker";

	@ConfigItem(
		keyName = "showSidebar",
		name = "Show sidebar",
		description = "Show the Advanced Mining Tracker side panel",
		position = 0
	)
	default boolean showSidebar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onlyUserMined",
		name = "Only rocks you mine",
		description = "Only track rocks depleted while you were mining nearby (recommended)",
		position = 1
	)
	default boolean onlyUserMined()
	{
		return true;
	}

	@ConfigItem(
		keyName = "maxDistance",
		name = "Max attribution distance",
		description = "Tiles from you a depleting rock may be to count as yours",
		position = 2
	)
	@Range(min = 1, max = 5)
	default int maxDistance()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "keepReadySeconds",
		name = "Keep ready rocks for",
		description = "How long to keep a ready rock in the panel after it regenerates (0 = until hop/clear)",
		position = 3
	)
	@Units(Units.SECONDS)
	@Range(min = 0, max = 3600)
	default int keepReadySeconds()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "showHopMessage",
		name = "Chat hop message",
		description = "Show a chat message when quick-hopping to a ready world",
		position = 4
	)
	default boolean showHopMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clearReadyOnHop",
		name = "Clear ready rocks on hop",
		description = "Remove ready rocks for a world after you hop to it",
		position = 5
	)
	default boolean clearReadyOnHop()
	{
		return true;
	}
}
