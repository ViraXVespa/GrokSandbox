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
}