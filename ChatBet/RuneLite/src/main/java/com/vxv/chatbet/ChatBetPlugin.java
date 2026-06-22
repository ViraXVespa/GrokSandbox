package com.vxv.chatbet;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.*;

import com.vxv.chatbet.bet.BetManager;
import com.vxv.chatbet.bet.BetType;
import com.vxv.chatbet.bet.Poll;
import com.vxv.chatbet.ui.BetCreationDialog;
import com.vxv.chatbet.module.BetModule;
import com.vxv.chatbet.event.GameEventType;

import java.awt.image.BufferedImage;
import java.util.*;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@PluginDescriptor(
	name = "ChatBet",
	description = "Tracks thieving events and enables chat betting during streams",
	tags = {"thieving", "bet", "elves", "xp tracker"}
)
public class ChatBetPlugin extends Plugin {

	@Inject private Client client;
	@Inject private ChatBetConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private ChatBetOverlay overlay;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ChatBetPanel chatBetPanel;

	private NavigationButton navButton;

	private final BetManager betManager = new BetManager();
	private BetModule activeModule;

	private int lastThievingXp = -1;
	private int currentGoalPercentage = 30;

	@Getter private final AtomicInteger attempts = new AtomicInteger(0);
	@Getter private final AtomicInteger successes = new AtomicInteger(0);

	// Legacy fields for fallback
	private final AtomicInteger etcsObtained = new AtomicInteger(0);
	private final AtomicInteger attemptsSinceLastEtc = new AtomicInteger(0);
	private final AtomicInteger successesSinceLastEtc = new AtomicInteger(0);
	private final AtomicInteger dodgySinceLastEtc = new AtomicInteger(0);
	private final AtomicInteger wineSinceLastEtc = new AtomicInteger(0);
	private final AtomicInteger dodgyConsumed = new AtomicInteger(0);
	private final AtomicInteger wineConsumed = new AtomicInteger(0);

	private final Map<Integer, Integer> lastInventoryQtys = new HashMap<>();
	private final Map<Integer, Integer> lastEquipmentQtys = new HashMap<>();

	private static final int ITEM_ETC = 23959;
	private static final int ITEM_DODGY_NECKLACE = 21143;
	private static final int ITEM_JUG_OF_WINE = 1993;

	@Provides
	ChatBetConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(ChatBetConfig.class);
	}

	@Override
	protected void startUp() throws Exception {
		overlayManager.add(overlay);

		// Safe side panel registration
		try {
			if (clientToolbar != null && chatBetPanel != null) {
				BufferedImage icon = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
				navButton = NavigationButton.builder()
						.tooltip("ChatBet")
						.icon(icon)
						.panel(chatBetPanel)
						.build();
				clientToolbar.addNavigation(navButton);
				log.info("ChatBet side panel registered");
			}
		} catch (Exception e) {
			log.error("Failed to register side panel", e);
		}
	}

	@Override
	protected void shutDown() throws Exception {
		overlayManager.remove(overlay);
		if (navButton != null) {
			clientToolbar.removeNavigation(navButton);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		// ... (command handling remains the same)
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM && type != ChatMessageType.PUBLICCHAT) return;

		String msg = event.getMessage();
		String sender = event.getName();

		if (msg.toLowerCase().startsWith("!bet ")) { handleBetCommand(sender, msg); return; }
		if (msg.equalsIgnoreCase("!balance")) { handleBalanceCommand(sender); return; }
		if (msg.equalsIgnoreCase("!bets")) { handleBetsCommand(); return; }
		if (msg.equalsIgnoreCase("!chatbet")) { handleChatBetCommand(sender); return; }
		if (msg.toLowerCase().startsWith("!resolve ")) { handleResolveCommand(sender, msg); return; }
	}

	// ... (other handlers remain)

	@Subscribe
	public void onStatChanged(StatChanged event) {
		if (event.getSkill() == Skill.THIEVING) {
			lastThievingXp = event.getXp();
			if (activeModule != null) activeModule.onStatChanged(event);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (activeModule != null) activeModule.onGameTick(event);

		// Aggressive XP seeding
		if (client != null) {
			int currentXp = client.getSkillExperience(Skill.THIEVING);
			if (currentXp > 0) {
				lastThievingXp = currentXp;
			}
		}

		if (config.showDebugVars()) {
			log.info("[ChatBet Debug] lastThievingXp={}, xpToGoal={}, goalPct={}", lastThievingXp, getXpToGoal(), currentGoalPercentage);
		}
	}

	public int getXpToGoal() {
		int goal = config.thievingGoalXp();
		int targetMark = (int) (goal * (currentGoalPercentage / 100.0));

		if (client != null) {
			int current = client.getSkillExperience(Skill.THIEVING);
			if (current > 0) {
				lastThievingXp = current;
				return Math.max(0, targetMark - current);
			}
		}
		if (lastThievingXp > 0) return Math.max(0, targetMark - lastThievingXp);
		return Math.max(0, targetMark);
	}

	// Add all other missing getters that the overlay expects
	public int getCurrentGoalPercentage() { return currentGoalPercentage; }
	public double getSuccessRate() { return successes.get() > 0 ? (successes.get() * 100.0 / attempts.get()) : 0.0; }
	public int getEtcsObtained() { return etcsObtained.get(); }
	public double getEstimatedEtcsToGoal() { return getElvesToGoal() * 0.000976; }
	public double getExpectedEtcs() { return getElvesToGoal() * 0.000976; }
	public int getAttemptsSinceLastEtc() { return attemptsSinceLastEtc.get(); }
	public int getSuccessesSinceLastEtc() { return successesSinceLastEtc.get(); }
	public long getDodgyConsumed() { return dodgyConsumed.get(); }
	public long getWineConsumed() { return wineConsumed.get(); }
	public long getDodgySinceLastEtc() { return dodgySinceLastEtc.get(); }
	public long getWineSinceLastEtc() { return wineSinceLastEtc.get(); }
	public double getProbEtcFromSuccesses() { return 0.0; }

	public void setActiveModule(BetModule module) {
		this.activeModule = module;
	}

	// TODO: Implement more delegation as needed
}
