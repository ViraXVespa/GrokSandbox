package com.vxv.runebridge;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(RuneBridgeConfig.GROUP)
public interface RuneBridgeConfig extends Config
{
	String GROUP = "rune-bridge";

	@ConfigItem(
		keyName = "enabled",
		name = "Enable bridge",
		description = "Send game events to the C# RuneBridge host",
		position = 0
	)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "host",
		name = "Host",
		description = "Hostname of the VXV.RuneBridge server",
		position = 1
	)
	default String host()
	{
		return "127.0.0.1";
	}

	@ConfigItem(
		keyName = "port",
		name = "Port",
		description = "TCP port of the VXV.RuneBridge server",
		position = 2
	)
	@Range(min = 1, max = 65535)
	default int port()
	{
		return 17473;
	}

	@ConfigItem(
		keyName = "emitGameTicks",
		name = "Emit game ticks",
		description = "High-frequency (~0.6s). Disable if you only need sparse events.",
		position = 3
	)
	default boolean emitGameTicks()
	{
		return false;
	}

	@ConfigItem(
		keyName = "emitChat",
		name = "Emit chat messages",
		description = "Forward in-game chat events",
		position = 4
	)
	default boolean emitChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "emitScene",
		name = "Emit scene NPC/object events",
		description = "Spawn/despawn for NPCs, players, game objects (can be busy)",
		position = 5
	)
	default boolean emitScene()
	{
		return true;
	}

	@ConfigItem(
		keyName = "emitMenu",
		name = "Emit menu clicks",
		description = "Forward menu option clicks",
		position = 6
	)
	default boolean emitMenu()
	{
		return true;
	}
}
