package com.vxv.chatbet;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("chatbet")
public interface ChatBetConfig extends Config {

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show Overlay",
		description = "Show the ChatBet overlay"
	)
	default boolean showOverlay() {
		return true;
	}

	@ConfigItem(
		keyName = "thievingGoalXp",
		name = "Thieving Goal XP",
		description = "Total XP for the current goal"
	)
	default int thievingGoalXp() {
		return 13034431; // 99
	}

	@ConfigItem(
		keyName = "showDebugVars",
		name = "Show Debug Vars",
		description = "Show variable/property values of the current module + plugin in a debug panel"
	)
	default boolean showDebugVars() {
		return false;
	}

	@ConfigItem(
		keyName = "selectedTask",
		name = "Selected Task",
		description = "Currently selected betting task"
	)
	default String selectedTask() {
		return "";
	}

	@ConfigItem(
		keyName = "selectedTask",
		name = "Selected Task",
		description = "Currently selected betting task"
	)
	void selectedTask(String selectedTask);

	// === Python Bridge Configuration ===

	@ConfigItem(
		keyName = "pythonExecutable",
		name = "Python Executable",
		description = "Path to Python executable (e.g. python, python3, or full path to python.exe)"
	)
	default String pythonExecutable() {
		return "python";
	}

	@ConfigItem(
		keyName = "pythonBridgeScriptPath",
		name = "Python Bridge Script Path",
		description = "Full path to stream_bet_bridge.py (e.g. C:\\path\\to\\stream_bet_bridge.py)"
	)
	default String pythonBridgeScriptPath() {
		return "";
	}

	// === Discord Hub (ChatBet/Discord service) ===

	@ConfigItem(
		keyName = "discordHubEnabled",
		name = "Discord hub sync",
		description = "Push polls/bets/resolutions to the local ChatBet Discord hub (default port 8766)"
	)
	default boolean discordHubEnabled() {
		return true;
	}

	@ConfigItem(
		keyName = "discordHubUrl",
		name = "Discord hub URL",
		description = "Base URL of the ChatBet Discord hub API"
	)
	default String discordHubUrl() {
		return "http://127.0.0.1:8766";
	}

	@ConfigItem(
		keyName = "discordHubApiKey",
		name = "Discord hub API key",
		description = "Must match HUB_API_KEY in ChatBet/Discord/.env (leave empty if hub has no key)"
	)
	default String discordHubApiKey() {
		return "";
	}

	// === Mining Slots ===

	@ConfigSection(
		name = "Mining Slots",
		description = "Amethyst / runite slot-machine poll settings",
		position = 50
	)
	String miningSlotsSection = "miningSlotsSection";

	@ConfigItem(
		keyName = "slotPoolThreshold",
		name = "Pool threshold",
		description = "Loot units needed before a slot poll can open",
		section = miningSlotsSection,
		position = 1
	)
	@Range(min = 5, max = 500)
	default int slotPoolThreshold() {
		return 40;
	}

	@ConfigItem(
		keyName = "slotLagSeconds",
		name = "Mining lag",
		description = "Seconds without new loot before the reel locks and betting opens",
		section = miningSlotsSection,
		position = 2
	)
	@Units(Units.SECONDS)
	@Range(min = 5, max = 300)
	default int slotLagSeconds() {
		return 45;
	}

	@ConfigItem(
		keyName = "slotBettingWindowSeconds",
		name = "Betting window",
		description = "Seconds viewers can bet before the reels spin",
		section = miningSlotsSection,
		position = 3
	)
	@Units(Units.SECONDS)
	@Range(min = 30, max = 600)
	default int slotBettingWindowSeconds() {
		return 180;
	}
}