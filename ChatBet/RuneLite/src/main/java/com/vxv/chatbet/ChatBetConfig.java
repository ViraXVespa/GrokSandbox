package com.vxv.chatbet;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
}