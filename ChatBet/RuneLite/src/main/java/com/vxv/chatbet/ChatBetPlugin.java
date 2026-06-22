package com.vxv.chatbet;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;
import javax.swing.*;
import com.vxv.chatbet.bet.BetManager;
import com.vxv.chatbet.bet.Poll;
import com.vxv.chatbet.ui.BetCreationDialog;
import com.vxv.chatbet.module.BetModule;
import com.vxv.chatbet.module.PickpocketingModule;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@PluginDescriptor(
    name = "ChatBet",
    description = "Tracks thieving events and enables chat betting during streams",
    tags = {"thieving", "bet", "elves", "xp tracker"}
)
@PluginDependency(XpTrackerPlugin.class)
public class ChatBetPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ChatBetConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private ChatBetOverlay overlay;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ChatBetPanel chatBetPanel;
    @Inject private XpTrackerService xpTrackerService;

    private NavigationButton navButton;
    private final BetManager betManager = new BetManager();
    private BetModule activeModule;

    private int lastThievingXp = -1;
    private int currentGoalPercentage = 30;

    @Getter private final AtomicInteger attempts = new AtomicInteger(0);
    @Getter private final AtomicInteger successes = new AtomicInteger(0);

    @Provides
    ChatBetConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChatBetConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);

        try {
            if (clientToolbar != null && chatBetPanel != null) {
                BufferedImage icon = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                navButton = NavigationButton.builder()
                    .tooltip("ChatBet")
                    .icon(icon)
                    .panel(chatBetPanel)
                    .build();
                clientToolbar.addNavigation(navButton);
                log.info("ChatBet side panel registered successfully");
            }
        } catch (Exception e) {
            log.error("Failed to register side panel", e);
        }

        if (activeModule == null) {
            activeModule = new PickpocketingModule(this);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
        if (navButton != null && clientToolbar != null) {
            clientToolbar.removeNavigation(navButton);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        // command stubs...
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

    private void handleBetCommand(String username, String message) { /* TODO */ }
    private void handleChatBetCommand(String sender) { /* TODO */ }
    private void handleBetsCommand() { /* TODO */ }
    private void handleResolveCommand(String sender, String message) { /* TODO */ }
    private void handleBalanceCommand(String sender) { /* TODO */ }

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

        if (client != null) {
            int currentXp = client.getSkillExperience(Skill.THIEVING);
            if (currentXp > 0) lastThievingXp = currentXp;
        }

        if (config.showDebugVars()) {
            log.info("[ChatBet Debug] ...");
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getItemContainer() != client.getItemContainer(InventoryID.INVENTORY)) return;
        if (activeModule != null) {
            activeModule.onItemContainerChanged(event);
        }
    }

    public Client getClient() {
        return client;
    }

    public int getXpToGoal() {
        if (xpTrackerService == null || client == null) return 0;
        int currentXp = client.getSkillExperience(Skill.THIEVING);
        int goalPercentage = currentGoalPercentage;
        // Calculate XP needed to reach goal percentage of next level
        int xpToNextLevel = xpTrackerService.getEndGoalXp(Skill.THIEVING) - currentXp;
        if (xpToNextLevel <= 0) return 0;
        // Goal is a percentage of the way to next level
        int goalXp = (int) (xpTrackerService.getEndGoalXp(Skill.THIEVING) * (goalPercentage / 100.0));
        int xpNeededForGoal = goalXp - currentXp;
        return Math.max(0, xpNeededForGoal);
    }

    public long getElvesToGoal() {
        if (activeModule != null) return activeModule.getElvesToGoal();
        return 0;
    }

    // Full delegation for PickpocketingModule getters to fix Overlay (Java 11 compatible)
    public int getEtcsObtained() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getEtcsObtained();
        }
        return 0;
    }

    public int getAttemptsSinceLastEtc() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getAttemptsSinceLastEtc();
        }
        return 0;
    }

    public int getSuccessesSinceLastEtc() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getSuccessesSinceLastEtc();
        }
        return 0;
    }

    public long getDodgyConsumed() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getDodgyConsumed();
        }
        return 0;
    }

    public long getWineConsumed() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getWineConsumed();
        }
        return 0;
    }

    public long getDodgySinceLastEtc() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getDodgySinceLastEtc();
        }
        return 0;
    }

    public long getWineSinceLastEtc() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getWineSinceLastEtc();
        }
        return 0;
    }

    // Stubs for remaining missing methods in Overlay
    public double getEstimatedEtcsToGoal() { return 0.0; } // TODO
    public double getExpectedEtcs() { return 0.0; } // TODO
    public double getProbEtcFromSuccesses() { return 0.0; } // TODO
    public List<Map.Entry<String, Long>> getTopBalances(int limit) { return List.of(); } // TODO from betManager
    public List<String> getRecentBalanceRequests() { return List.of(); } // TODO
    public long getBalance(String user) { return betManager.getBalance(user); }

    public void setActiveTask(String task, int goalPercentage) {
        this.currentGoalPercentage = goalPercentage;
        // TODO activate module if needed
        if (chatBetPanel != null) chatBetPanel.refresh();
    }

    public double getSuccessRate() { return successes.get() > 0 ? (successes.get() * 100.0 / attempts.get()) : 0.0; }
    public int getCurrentGoalPercentage() { return currentGoalPercentage; }

    public List<Poll> getActivePolls() { return betManager.getActivePolls(); }

    public String getActiveTaskName() {
        return activeModule != null ? activeModule.getName() : "None";
    }

    public void setActiveModule(BetModule module) { this.activeModule = module; }
}
