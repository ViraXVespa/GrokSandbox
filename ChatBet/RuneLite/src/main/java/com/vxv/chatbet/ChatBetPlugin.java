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
import com.vxv.chatbet.bet.BetType;
import com.vxv.chatbet.ui.BetCreationDialog;
import com.vxv.chatbet.module.BetModule;
import com.vxv.chatbet.module.PickpocketingModule;
import com.vxv.chatbet.module.OuraniaAltarModule;
import com.vxv.chatbet.debug.DebugInfoProvider;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// StreamLabs bridge interop (Java 11 HttpClient)
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

// For forwarding stream chat to in-game chat
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.QueuedMessage;

@Slf4j
@PluginDescriptor(
    name = "ChatBet",
    description = "Tracks thieving events and enables chat betting during streams",
    tags = {"thieving", "bet", "elves", "xp tracker"}
)
@PluginDependency(XpTrackerPlugin.class)
public class ChatBetPlugin extends Plugin implements DebugInfoProvider {

    @Inject private Client client;
    @Inject private ChatBetConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private ChatBetOverlay overlay;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ChatBetPanel chatBetPanel;
    @Inject private ChatBetDebugPanel chatBetDebugPanel;
    @Inject private XpTrackerService xpTrackerService;
    @Inject private ConfigManager configManager;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private ClientThread clientThread;

    private NavigationButton navButton;
    private NavigationButton debugNavButton;
    private final BetManager betManager = new BetManager();
    private BetModule activeModule;

    private int lastThievingXp = -1;
    private int currentGoalPercentage = 30;

    private int lastOuraniaPollId = -1;

    @Getter private final AtomicInteger attempts = new AtomicInteger(0);
    @Getter private final AtomicInteger successes = new AtomicInteger(0);

    private static final String BRIDGE_BASE_URL = "http://127.0.0.1:8765"; // StreamLabs bridge default for chat interop

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

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

            if (clientToolbar != null && chatBetDebugPanel != null) {
                BufferedImage debugIcon = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                debugNavButton = NavigationButton.builder()
                    .tooltip("ChatBet Debug")
                    .icon(debugIcon)
                    .panel(chatBetDebugPanel)
                    .build();
                clientToolbar.addNavigation(debugNavButton);
                log.info("ChatBet Debug panel registered successfully");
            }
        } catch (Exception e) {
            log.error("Failed to register side panels", e);
        }

        // Load persisted task - properly instantiate the correct module
        String savedTask = config.selectedTask();
        if (savedTask != null && !savedTask.isEmpty()) {
            if ("Ourania Altar Runes".equals(savedTask)) {
                activeModule = new OuraniaAltarModule(this);
            } else {
                activeModule = new PickpocketingModule(this);
            }
        } else {
            activeModule = null; // Support inactive state
        }
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
        if (navButton != null && clientToolbar != null) {
            clientToolbar.removeNavigation(navButton);
        }
        if (debugNavButton != null && clientToolbar != null) {
            clientToolbar.removeNavigation(debugNavButton);
        }
        // Save current task
        if (activeModule != null) {
            configManager.getConfig(ChatBetConfig.class).selectedTask(activeModule.getName());
        } else {
            configManager.getConfig(ChatBetConfig.class).selectedTask("");
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        String msg = event.getMessage();
        String sender = event.getName();

        if (msg.toLowerCase().startsWith("!bet ")) { handleBetCommand(sender, msg); return; }
        if (msg.equalsIgnoreCase("!balance")) { handleBalanceCommand(sender); return; }
        if (msg.equalsIgnoreCase("!bets")) { handleBetsCommand(); return; }
        if (msg.equalsIgnoreCase("!chatbet")) { handleChatBetCommand(sender); return; }
        if (msg.toLowerCase().startsWith("!resolve ")) { handleResolveCommand(sender, msg); return; }

        // Delegate to active module via interface (forward ALL messages)
        if (activeModule != null) {
            log.info("[ChatBetPlugin] Delegating ChatMessage to active module (" + activeModule.getClass().getSimpleName() + "): " + msg);
            activeModule.onChatMessage(event);
        } else if (config.showDebugVars()) {
            log.info("[ChatBetPlugin] No active module - ChatMessage ignored: " + msg);
        }
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

        if (config.showDebugVars() && chatBetDebugPanel != null) {
            chatBetDebugPanel.refreshDebugInfo();
        }

        // Simple periodic poll of bridge for stream chat interop (expand in future commits)
        if (System.currentTimeMillis() % 5000 < 100) { // rough ~5s interval
            pollBridgeForNonCommandChat();
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
        int startXp = xpTrackerService.getStartGoalXp(Skill.THIEVING);
        int endXp = xpTrackerService.getEndGoalXp(Skill.THIEVING);
        int levelXpRange = endXp - startXp;
        int goalXp = startXp + (int)(levelXpRange * (goalPercentage / 100.0));
        int xpNeeded = goalXp - currentXp;
        return Math.max(0, xpNeeded);
    }

    public long getElvesToGoal() {
        if (activeModule != null) return activeModule.getElvesToGoal();
        return 0;
    }

    // Delegation for PickpocketingModule getters (kept for compatibility)
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
    public double getEstimatedEtcsToGoal() {
        long elvesToGoal = getElvesToGoal();
        if (elvesToGoal <= 0) return 0.0;

        double successRate = getSuccessRate() / 100.0;
        if (successRate <= 0) {
            successRate = 0; // default to zero if no data yet
        }

        return elvesToGoal * successRate;
    }
    public double getExpectedEtcs() { return 0.0; } // TODO
    public double getProbEtcFromSuccesses() { return 0.0; } // TODO
    public List<Map.Entry<String, Long>> getTopBalances(int limit) { return List.of(); } // TODO from betManager
    public List<String> getRecentBalanceRequests() { return List.of(); } // TODO
    public long getBalance(String user) { return betManager.getBalance(user); }

    public void setActiveTask(String task, int goalPercentage) {
        this.currentGoalPercentage = goalPercentage;
        if (chatBetPanel != null) chatBetPanel.refresh();
        if (task == null || task.isEmpty() || "None".equals(task)) {
            activeModule = null;
            this.currentGoalPercentage = 0;
        } else {
            if (activeModule == null) {
                if ("Ourania Altar Runes".equals(task)) {
                    activeModule = new OuraniaAltarModule(this);
            } else {
                    activeModule = new PickpocketingModule(this);
                }
            }
        }
        config.selectedTask(task != null ? task : "");
    }

    public double getSuccessRate() { return successes.get() > 0 ? (successes.get() * 100.0 / attempts.get()) : 0.0; }
    public int getCurrentGoalPercentage() { return currentGoalPercentage; }

    public List<Poll> getActivePolls() { return betManager.getActivePolls(); }

    public String getActiveTaskName() {
        return activeModule != null ? activeModule.getName() : "None";
    }

    public void setActiveModule(BetModule module) { this.activeModule = module; }

    public BetModule getActiveModule() { return activeModule; }

    public AtomicInteger getAttempts() { return attempts; }
    public AtomicInteger getSuccesses() { return successes; }

    // Delegation for OuraniaAltarModule
    public List<String> getCurrentRuneOptions() {
        if (activeModule instanceof OuraniaAltarModule) {
            return ((OuraniaAltarModule) activeModule).getCurrentRuneOptions();
        }
        return List.of();
    }

    public boolean isOuraniaBettingLocked() {
        if (activeModule instanceof OuraniaAltarModule) {
            return ((OuraniaAltarModule) activeModule).isBettingLocked();
        }
        return false;
    }

    public boolean isWearingFullRaiments() {
        if (activeModule instanceof OuraniaAltarModule) {
            return ((OuraniaAltarModule) activeModule).isWearingFullRaiments();
        }
        return false;
    }

    public Map<String, Double> getOuraniaRuneOdds() {
        if (activeModule instanceof OuraniaAltarModule) {
            OuraniaAltarModule ourania = (OuraniaAltarModule) activeModule;
            int rcLevel = (client != null) ? client.getRealSkillLevel(Skill.RUNECRAFT) : 0;
            return ourania.getRuneOdds(rcLevel, ourania.isWearingFullRaiments());
        }
        return Map.of();
    }

    public void createOuraniaPoll(List<String> options) {
        if (options == null || options.isEmpty()) return;

        Poll poll = betManager.createPoll("Which rune will be most crafted this run?", BetType.MULTIPLE_CHOICE, options);
        lastOuraniaPollId = poll.getId();
        log.info("Ourania poll created with options: " + options);
        if (chatBetPanel != null) chatBetPanel.refresh();
    }

    public void resolveOuraniaPoll(int winningOptionIndex) {
        if (lastOuraniaPollId > 0) {
            betManager.resolvePoll(lastOuraniaPollId, winningOptionIndex);
            lastOuraniaPollId = -1;
        }
    }

    /**
     * Sends a message from stream chat (non-command, non-emoji) directly into the RuneLite game chat.
     * Must be called from a background thread;
     * uses clientThread for safety.
     */
    private void sendStreamChatToGame(String user, String message) {
        if (chatMessageManager == null || clientThread == null || message == null || message.isBlank()) return;
        String text = (user != null && !user.isBlank()) ? "[" + user + "] " + message : message;
        clientThread.invokeLater(() ->
            chatMessageManager.queue(
                QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .value(text)
                    .build()
            )
        );
    }

    /** Basic emoji/symbol filter for stream chat (non-emoji messages get forwarded) */
    private boolean isProbablyEmojiOnly(String msg) {
        if (msg == null || msg.isBlank()) return true;
        // Remove common emoji/symbol ranges and punctuation
        String cleaned = msg.replaceAll("[\\p{So}\\p{Cn}\\p{Cs}\\s:;,.!?()\\[\\]{}'\"-]+", "");
        return cleaned.length() < 2; // mostly symbols/emojis
    }

    /**
     * Polls the StreamLabs bridge for active commands and recent chat.
     * Extracts real messages from recent_messages, skips emojis/commands, forwards via sendStreamChatToGame.
     */
    private void pollBridgeForNonCommandChat() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BRIDGE_BASE_URL + "/chatbet/state"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                if (config.showDebugVars()) {
                    log.info("[ChatBet Interop] Bridge state: " + body);
                }

                if (body.contains("\"active_request\":null")) {
                    // Try to extract a simple message from recent_messages (demo parsing)
                    int start = body.indexOf("message\":\"");
                    if (start > 0) {
                        int end = body.indexOf("\"", start + 10);
                        if (end > start) {
                            String extractedMsg = body.substring(start + 10, end);
                            if (!isProbablyEmojiOnly(extractedMsg)) {
                                sendStreamChatToGame("Stream", extractedMsg);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (config.showDebugVars()) {
                log.debug("Bridge poll failed (normal if bridge not running): " + e.getMessage());
            }
        }
    }

    // === DebugInfoProvider implementation ===
    @Override
    public Map<String, Supplier<Object>> getDebugVariables() {
        Map<String, Supplier<Object>> vars = new LinkedHashMap<>();
        vars.put("Active Task", this::getActiveTaskName);
        vars.put("Current Goal %", this::getCurrentGoalPercentage());
        vars.put("Debug Mode Enabled", () -> config.showDebugVars());
        vars.put("Active Module Present", () -> activeModule != null);
        vars.put("Last Ourania Poll ID", () -> lastOuraniaPollId);
        vars.put("Active Polls Count", () -> betManager.getActivePolls().size());
        vars.put("Current Thieving XP", () -> (client != null ? client.getSkillExperience(Skill.THIEVING) : -1));
        vars.put("Bridge Base URL", () -> BRIDGE_BASE_URL);
        return vars;
    }
}