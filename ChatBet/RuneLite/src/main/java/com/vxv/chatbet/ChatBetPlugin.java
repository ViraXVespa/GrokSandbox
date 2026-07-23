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
import com.vxv.chatbet.module.ModuleCatalog;
import com.vxv.chatbet.module.auto.CombatKillstreakModule;
import com.vxv.chatbet.debug.DebugInfoProvider;
import com.vxv.chatbet.discord.DiscordHubClient;
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

// For launching and piping the Python bridge process
 import java.io.BufferedReader;
import java.io.InputStreamReader;

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

    /**
     * Handle to the external Python bridge process (stream_bet_bridge.py).
     * Managed automatically on plugin start/stop.
     */
    private Process bridgeProcess;

    /** Best-effort client for ChatBet Discord hub (polls/bets/resolve sync). */
    private DiscordHubClient discordHubClient;

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
            activeModule = createModuleForTask(savedTask);
            if (activeModule != null) {
                activeModule.onActivate();
            }
        } else {
            activeModule = null;
        }

        // Start the Python StreamLabs bridge automatically
        startPythonBridge();

        refreshDiscordHubClient();
        betManager.setOnResolvedListener((pollId, winningOptionIndex) ->
            resolvePollOnDiscordHub(pollId, winningOptionIndex));
    }

    private void refreshDiscordHubClient() {
        if (config.discordHubEnabled()) {
            discordHubClient = new DiscordHubClient(config.discordHubUrl(), config.discordHubApiKey());
            log.info("[DiscordHub] Sync enabled → {}", config.discordHubUrl());
        } else {
            discordHubClient = null;
            log.info("[DiscordHub] Sync disabled");
        }
    }

    private void publishPollToDiscordHub(Poll poll) {
        if (discordHubClient == null || poll == null) {
            return;
        }
        // Network off client thread
        new Thread(() -> discordHubClient.publishPoll(poll), "chatbet-discord-publish").start();
    }

    private void resolvePollOnDiscordHub(int pollId, int winningOptionIndex) {
        if (discordHubClient == null) {
            return;
        }
        new Thread(() -> discordHubClient.resolvePoll(pollId, winningOptionIndex), "chatbet-discord-resolve").start();
    }

    private void placeBetOnDiscordHub(String username, int pollId, String option, long amount) {
        if (discordHubClient == null) {
            return;
        }
        new Thread(() -> discordHubClient.placeBet(username, pollId, option, amount), "chatbet-discord-bet").start();
    }

    /**
     * Used by {@link BetCreationDialog} so RuneLite-created polls appear in Discord.
     */
    public void onPollCreated(Poll poll) {
        publishPollToDiscordHub(poll);
    }

    @Override
    protected void shutDown() throws Exception {
        // Stop the Python bridge first
        stopPythonBridge();

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

    /**
     * Starts the Python bridge process (stream_bet_bridge.py) and pipes its output to RuneLite logs.
     */
    private void startPythonBridge() {
        if (bridgeProcess != null && bridgeProcess.isAlive()) {
            log.info("[Bridge] Python bridge is already running.");
            return;
        }

        String pythonExe = config.pythonExecutable();
        String scriptPath = config.pythonBridgeScriptPath();

        if (scriptPath == null || scriptPath.isBlank()) {
            log.warn("[Bridge] Python bridge script path is not configured (see plugin settings). Skipping auto-start.");
            return;
        }

        try {
            List<String> command = new ArrayList<>();
            command.add(pythonExe);
            command.add(scriptPath);

            if (config.showDebugVars()) {
                command.add("--debug");
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false); // keep stdout and stderr separate

            bridgeProcess = pb.start();

            // Pipe stdout from bridge to RuneLite log
            startOutputPipe(bridgeProcess.getInputStream(), "[Bridge][stdout] ");
            // Pipe stderr from bridge to RuneLite log
            startOutputPipe(bridgeProcess.getErrorStream(), "[Bridge][stderr] ");

            log.info("[Bridge] Started Python bridge process (PID: {}) with command: {}",
                    bridgeProcess.pid(), String.join(" ", command));

        } catch (Exception e) {
            log.error("[Bridge] Failed to start Python bridge process", e);
            bridgeProcess = null;
        }
    }

    /**
     * Reads lines from the given InputStream and logs them with the given prefix.
     */
    private void startOutputPipe(java.io.InputStream stream, String prefix) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (config.showDebugVars()) {
                        log.info(prefix + line);
                    } else {
                        // Only log warnings/errors even when debug is off
                        if (prefix.contains("stderr")) {
                            log.warn(prefix + line);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("[Bridge] Output pipe closed: {}", e.getMessage());
            }
        }, "bridge-output-" + prefix);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Stops the Python bridge process if it is running.
     */
    private void stopPythonBridge() {
        if (bridgeProcess == null) {
            return;
        }

        try {
            if (bridgeProcess.isAlive()) {
                log.info("[Bridge] Stopping Python bridge process...");
                bridgeProcess.destroy();

                // Give it a moment to exit cleanly
                if (!bridgeProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("[Bridge] Python bridge did not exit in time, forcing termination.");
                    bridgeProcess.destroyForcibly();
                }
                log.info("[Bridge] Python bridge stopped.");
            }
        } catch (Exception e) {
            log.error("[Bridge] Error while stopping Python bridge", e);
        } finally {
            bridgeProcess = null;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        String msg = event.getMessage();
        String sender = event.getName();
        if (msg == null) {
            return;
        }

        String lower = msg.toLowerCase();
        if (lower.startsWith("!bet ")) { handleBetCommand(sender, msg); return; }
        if (msg.equalsIgnoreCase("!balance")) { handleBalanceCommand(sender); return; }
        if (msg.equalsIgnoreCase("!bets")) { handleBetsCommand(); return; }
        if (msg.equalsIgnoreCase("!chatbet")) { handleChatBetCommand(sender); return; }
        if (lower.startsWith("!resolve ")) { handleResolveCommand(sender, msg); return; }

        if (activeModule != null) {
            log.debug("[ChatBet] → {} : {}", activeModule.getClass().getSimpleName(), msg);
            activeModule.onChatMessage(event);
        }
    }

    /**
     * Supports:
     * <ul>
     *   <li>{@code !bet <amount> on <option>}</li>
     *   <li>{@code !bet <pollId> <amount> on <option>}</li>
     * </ul>
     */
    private void handleBetCommand(String username, String message) {
        if (username == null || username.isBlank() || message == null) return;

        String lower = message.toLowerCase();
        if (!lower.startsWith("!bet ")) return;

        String after = message.substring(5).trim();
        int onIndex = lower.indexOf(" on ");
        if (onIndex < 0) {
            sendGameMessage("[ChatBet] Usage: !bet [pollId] <amount> on <option>");
            return;
        }

        // lower is full message; slice args relative to "!bet "
        String beforeOn = message.substring(5, onIndex).trim();
        String optionText = message.substring(onIndex + 4).trim();

        Integer pollId = null;
        String amountStr;
        String[] parts = beforeOn.split("\\s+");
        if (parts.length >= 2) {
            try {
                pollId = Integer.parseInt(parts[0]);
                amountStr = parts[1];
            } catch (NumberFormatException e) {
                amountStr = beforeOn;
            }
        } else {
            amountStr = beforeOn;
        }

        long amount;
        try {
            amount = Long.parseLong(amountStr.trim());
        } catch (NumberFormatException e) {
            sendGameMessage("[ChatBet] Invalid amount. Usage: !bet [pollId] <amount> on <option>");
            return;
        }

        if (amount <= 0) {
            sendGameMessage("[ChatBet] Amount must be positive.");
            return;
        }

        placeBet(username, amount, optionText, pollId);
    }

    private void placeBet(String username, long amount, String optionText) {
        placeBet(username, amount, optionText, null);
    }

    /**
     * Core bet placement used by in-game commands and the StreamLabs bridge.
     */
    private void placeBet(String username, long amount, String optionText, Integer explicitPollId) {
        if (username == null || username.isBlank() || optionText == null || optionText.isBlank()) {
            sendGameMessage("[ChatBet] Invalid bet request.");
            return;
        }

        // Soft preference: Ourania poll if still open and no explicit id
        Integer preferred = explicitPollId;
        if (preferred == null && lastOuraniaPollId > 0
            && betManager.getPollById(lastOuraniaPollId).isPresent()) {
            preferred = lastOuraniaPollId;
        }

        Poll targetPoll = betManager.resolveTargetPoll(preferred, optionText).orElse(null);
        if (targetPoll == null || !targetPoll.isOpen()) {
            sendGameMessage("[ChatBet] No active bet to join right now.");
            return;
        }
        if (!targetPoll.isBettingOpen()) {
            sendGameMessage("[ChatBet] Betting is closed on poll #" + targetPoll.getId() + ".");
            return;
        }

        int pollId = targetPoll.getId();
        List<String> options = targetPoll.getOptions();
        int optionIndex = BetManager.findOptionIndex(options, optionText);

        if (optionIndex < 0) {
            sendGameMessage("[ChatBet] Option not found on poll #" + pollId
                + ". Available: " + String.join(", ", options));
            return;
        }

        boolean success = betManager.placeWager(username, pollId, optionIndex, amount);
        if (success) {
            long stake = amount;
            if (targetPoll.getType() == BetType.SLOT_MACHINE) {
                int lines = BetManager.parseLineCount(options.get(optionIndex));
                stake = amount * Math.max(1, lines);
                sendGameMessage(String.format(
                    "[ChatBet] %s wagered %d (%d x %d line%s) on poll #%d!",
                    username, stake, amount, lines, lines == 1 ? "" : "s", pollId));
            } else {
                sendGameMessage(String.format("[ChatBet] %s bet %d on %s (poll #%d)!",
                    username, amount, options.get(optionIndex), pollId));
            }
            // Hub expects final stake (no second line-multiply)
            placeBetOnDiscordHub(username, pollId, options.get(optionIndex), stake);
        } else {
            sendGameMessage("[ChatBet] Bet failed. Check balance, options, or if betting is still open.");
        }
    }

    private void handleChatBetCommand(String sender) {
        sendGameMessage("[ChatBet] !bet [pollId] <amount> on <option> | !balance | !bets | !resolve [pollId] <optionIndex>"
            + " | slots: !bet <per-line> on 1|3|5");
    }

    private void handleResolveCommand(String sender, String message) {
        if (message == null) return;

        String arg = message.substring(9).trim();
        String[] parts = arg.split("\\s+");
        try {
            if (parts.length >= 2) {
                int pollId = Integer.parseInt(parts[0]);
                int optionIndex = Integer.parseInt(parts[1]);
                if (betManager.getPollById(pollId).isEmpty()) {
                    sendGameMessage("[ChatBet] No open poll #" + pollId);
                    return;
                }
                betManager.resolvePoll(pollId, optionIndex);
                if (pollId == lastOuraniaPollId) {
                    lastOuraniaPollId = -1;
                }
                sendGameMessage("[ChatBet] Poll #" + pollId + " resolved with option " + optionIndex + ".");
                return;
            }
            int optionIndex = Integer.parseInt(parts[0]);
            // Prefer Ourania if open, else newest poll
            if (lastOuraniaPollId > 0 && betManager.getPollById(lastOuraniaPollId).isPresent()) {
                resolveOuraniaPoll(optionIndex);
                sendGameMessage("[ChatBet] Ourania poll resolved with option " + optionIndex + ".");
                return;
            }
            List<Poll> active = betManager.getActivePolls();
            if (active.isEmpty()) {
                sendGameMessage("[ChatBet] No open polls to resolve.");
                return;
            }
            Poll p = active.get(0);
            betManager.resolvePoll(p.getId(), optionIndex);
            sendGameMessage("[ChatBet] Poll #" + p.getId() + " resolved with option " + optionIndex + ".");
        } catch (NumberFormatException e) {
            sendGameMessage("[ChatBet] Usage: !resolve [pollId] <optionIndex>");
        }
    }

    private void handleBetsCommand() {
        List<Poll> activePolls = betManager.getActivePolls();

        if (activePolls.isEmpty()) {
            sendGameMessage("[ChatBet] No active bets right now.");
            return;
        }

        for (Poll poll : activePolls) {
            String open = poll.isBettingOpen() ? "OPEN" : "LOCKED";
            sendGameMessage(String.format(
                "[ChatBet] #%d [%s/%s] %s — %s",
                poll.getId(),
                poll.getType(),
                open,
                poll.getQuestion().length() > 40
                    ? poll.getQuestion().substring(0, 37) + "..."
                    : poll.getQuestion(),
                String.join(", ", poll.getOptions())
            ));
        }
    }

    private void sendGameMessage(String text) {
        if (chatMessageManager != null && clientThread != null) {
            clientThread.invokeLater(() -> {
                chatMessageManager.queue(
                    QueuedMessage.builder()
                        .type(ChatMessageType.GAMEMESSAGE)
                        .value(text)
                        .build()
                );
            });
        } else {
            log.info(text);
        }
    }

    private void handleBalanceCommand(String sender) {
        if (sender == null || sender.isBlank()) return;

        betManager.recordBalanceRequest(sender);
        long balance = betManager.getBalance(sender);
        String response = String.format("[ChatBet] %s, your current balance is %d coins.", sender, balance);

        if (chatMessageManager != null && clientThread != null) {
            clientThread.invokeLater(() -> {
                chatMessageManager.queue(
                    QueuedMessage.builder()
                        .type(ChatMessageType.GAMEMESSAGE)
                        .value(response)
                        .build()
                );
            });
        } else {
            log.info("[ChatBet] Balance response for {}: {}", sender, balance);
        }
    }

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

        if (System.currentTimeMillis() % 5000 < 100) {
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

    public double getEstimatedEtcsToGoal() {
        long elvesToGoal = getElvesToGoal();
        if (elvesToGoal <= 0) return 0.0;

        double successRate = getSuccessRate() / 100.0;
        if (successRate <= 0) {
            successRate = 0; // default to zero if no data yet
        }

        return elvesToGoal * successRate;
    }

    public double getExpectedEtcs() {
        long elvesToGoal = getElvesToGoal();
        if (elvesToGoal <= 0) return 0.0;

        double successRate = getSuccessRate() / 100.0;
        if (successRate <= 0) {
            successRate = 0; // default to zero if no data yet
        }

        return elvesToGoal * successRate;
    }

    public double getProbEtcFromSuccesses() {
        int successes = getSuccessesSinceLastEtc();
        int attempts = getAttemptsSinceLastEtc();

        if (attempts <= 0) return 0.0;

        return (successes * 100.0) / attempts;
    }

    public List<Map.Entry<String, Long>> getTopBalances(int limit) {
        return betManager.getTopBalances(limit);
    }

    public List<String> getRecentBalanceRequests() {
        return betManager.getRecentBalanceRequests();
    }

    public long getBalance(String user) { return betManager.getBalance(user); }

    public void setActiveTask(String task, int goalPercentage) {
        this.currentGoalPercentage = goalPercentage;
        if (activeModule != null) {
            activeModule.onDeactivate();
        }
        if (task == null || task.isEmpty() || "None".equals(task)) {
            activeModule = null;
            this.currentGoalPercentage = 0;
        } else {
            activeModule = createModuleForTask(task);
            if (activeModule != null) {
                activeModule.onActivate();
            }
        }
        config.selectedTask(task != null ? task : "");
        if (chatBetPanel != null) {
            chatBetPanel.refresh();
        }
    }

    private BetModule createModuleForTask(String task) {
        if (task == null || task.isEmpty()) {
            return null;
        }
        BetModule fromCatalog = ModuleCatalog.create(task, this);
        if (fromCatalog != null) {
            return fromCatalog;
        }
        String lower = task.toLowerCase();
        if (lower.contains("pickpocket") || lower.contains("elf")) {
            return new PickpocketingModule(this);
        }
        if (lower.contains("ourania")) {
            return new OuraniaAltarModule(this);
        }
        return null;
    }

    @Subscribe
    public void onActorDeath(net.runelite.api.events.ActorDeath event) {
        if (activeModule instanceof CombatKillstreakModule) {
            ((CombatKillstreakModule) activeModule).onActorDeath(event.getActor());
        }
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
        publishPollToDiscordHub(poll);
        if (chatBetPanel != null) chatBetPanel.refresh();
    }

    public void resolveOuraniaPoll(int winningOptionIndex) {
        if (lastOuraniaPollId > 0) {
            // BetManager listener publishes the resolution to the Discord hub
            betManager.resolvePoll(lastOuraniaPollId, winningOptionIndex);
            lastOuraniaPollId = -1;
        }
    }

    public BetManager getBetManager() {
        return betManager;
    }

    public ChatBetConfig getConfig() {
        return config;
    }

    public void announce(String text) {
        sendGameMessage(text);
    }

    /**
     * Create a poll, publish to Discord hub, and return it for module bookkeeping.
     */
    public Poll createManagedPoll(String question, BetType type, List<String> options) {
        Poll poll = betManager.createPoll(question, type, options);
        // Defer publish slightly so callers can chain withResolutionTrigger / reel metadata first
        final Poll published = poll;
        new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (discordHubClient != null) {
                discordHubClient.publishPoll(published);
            }
        }, "chatbet-discord-publish-deferred").start();
        if (chatBetPanel != null) {
            chatBetPanel.refresh();
        }
        return poll;
    }

    public void resolveClosestPoll(int pollId, int actualValue) {
        betManager.resolveClosestWins(pollId, actualValue);
    }

    public void resolveSlotPoll(int pollId, com.vxv.chatbet.bet.SlotSpinResult spin,
        java.util.Map<String, Double> multipliers) {
        betManager.resolveSlotMachine(pollId, spin, multipliers);
    }

    private void sendStreamChatToGame(String user, String message) {
        if (chatMessageManager == null || clientThread == null || message == null || message.isBlank()) return;
        String text = (user != null && !user.isBlank()) ? "[" + user + "] " + message : message;
        clientThread.invokeLater(() -> {
            chatMessageManager.queue(
                QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .value(text)
                    .build()
            );
        });
    }

    /** Last stream-bridge request timestamp we already applied (dedupe). */
    private long lastBridgeRequestTs = -1;

    private void pollBridgeForNonCommandChat() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BRIDGE_BASE_URL + "/chatbet/state"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return;
            }

            String body = response.body();
            if (config.showDebugVars()) {
                log.debug("[ChatBet Interop] Bridge state: {}", body);
            }

            String activeRequestJson = extractObject(body, "active_request");
            if (activeRequestJson != null && !activeRequestJson.equals("null")) {
                String command = extractJsonScalar(activeRequestJson, "command");
                String amountStr = extractJsonScalar(activeRequestJson, "amount");
                String option = extractJsonScalar(activeRequestJson, "option");
                String user = extractJsonScalar(activeRequestJson, "user");
                String tsStr = extractJsonScalar(activeRequestJson, "timestamp");

                long ts = -1;
                if (tsStr != null) {
                    try {
                        ts = Long.parseLong(tsStr);
                    } catch (NumberFormatException ignored) {
                        // ignore
                    }
                }

                if (ts > 0 && ts == lastBridgeRequestTs) {
                    // already handled
                } else if ("bet".equalsIgnoreCase(command) && amountStr != null
                    && option != null && !option.isBlank()) {
                    try {
                        long amount = Long.parseLong(amountStr);
                        if (amount > 0) {
                            String bettor = (user != null && !user.isBlank()) ? user : "Stream";
                            placeBet(bettor, amount, option, null);
                            lastBridgeRequestTs = ts;
                            ackBridgeRequest(ts);
                        }
                    } catch (NumberFormatException ignored) {
                        // Invalid amount
                    }
                }
            }

            applyPendingEngagementCredits(body);
        } catch (Exception e) {
            if (config.showDebugVars()) {
                log.debug("Bridge poll failed (normal if bridge not running): {}", e.getMessage());
            }
        }
    }

    /**
     * Apply stream engagement token credits from the bridge (anti-spam scored on Python side).
     */
    private void applyPendingEngagementCredits(String stateJson) {
        String arrayJson = extractArray(stateJson, "pending_credits");
        if (arrayJson == null || arrayJson.length() < 3) {
            return;
        }

        java.util.List<String> appliedIds = new java.util.ArrayList<>();
        // Split roughly on objects within the array
        int i = 0;
        while (i < arrayJson.length()) {
            int start = arrayJson.indexOf('{', i);
            if (start < 0) {
                break;
            }
            int depth = 0;
            int end = start;
            for (; end < arrayJson.length(); end++) {
                char c = arrayJson.charAt(end);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end++;
                        break;
                    }
                }
            }
            if (depth != 0) {
                break;
            }
            String obj = arrayJson.substring(start, end);
            i = end;

            String id = extractJsonScalar(obj, "id");
            String user = extractJsonScalar(obj, "user");
            String amountStr = extractJsonScalar(obj, "amount");
            String acked = extractJsonScalar(obj, "acked");
            if ("true".equalsIgnoreCase(acked)) {
                continue;
            }
            if (id == null || user == null || amountStr == null) {
                continue;
            }
            try {
                long amount = Long.parseLong(amountStr);
                if (amount <= 0) {
                    continue;
                }
                long bal = betManager.creditBalance(user, amount);
                appliedIds.add(id);
                log.debug("[Engage] +{} → {} (bal {})", amount, user, bal);
                // Avoid spam: only surface engagement credits when debug is on
                if (config.showDebugVars()) {
                    sendGameMessage(String.format("[ChatBet] +%d tokens for %s (chat engagement)", amount, user));
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }

        if (!appliedIds.isEmpty()) {
            ackEngagementCredits(appliedIds);
        }
    }

    private String extractArray(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) {
            return null;
        }
        start += search.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != '[') {
            return null;
        }
        int depth = 0;
        int end = start;
        for (; end < json.length(); end++) {
            char c = json.charAt(end);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    end++;
                    break;
                }
            }
        }
        if (depth != 0) {
            return null;
        }
        return json.substring(start, end);
    }

    private void ackBridgeRequest(long timestamp) {
        try {
            String uri = BRIDGE_BASE_URL + "/chatbet/ack"
                + (timestamp > 0 ? ("?timestamp=" + timestamp) : "");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(2))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.debug("Bridge ack failed: {}", e.getMessage());
        }
    }

    private void ackEngagementCredits(java.util.List<String> ids) {
        try {
            StringBuilder sb = new StringBuilder("{\"ids\":[");
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(ids.get(i).replace("\"", "")).append('"');
            }
            sb.append("]}");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BRIDGE_BASE_URL + "/chatbet/credits/ack"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString()))
                .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.debug("Credit ack failed: {}", e.getMessage());
        }
    }

    /**
     * Extracts a top-level JSON object or value for a given key (simple implementation).
     */
    private String extractObject(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return null;

        start += search.length();

        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;

        if (start >= json.length()) return null;

        char firstChar = json.charAt(start);

        if (firstChar == '{') {
            // Extract object
            int depth = 1;
            int end = start + 1;
            while (end < json.length() && depth > 0) {
                char c = json.charAt(end);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                end++;
            }
            return json.substring(start, end);
        } else if (firstChar == '"') {
            // Extract string value
            int end = json.indexOf('"', start + 1);
            if (end > start) return json.substring(start, end + 1);
        } else {
            // Extract literal (null, true, false, number)
            int end = start;
            while (end < json.length() && !Character.isWhitespace(json.charAt(end)) && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return json.substring(start, end);
        }
        return null;
    }

    /**
     * Extracts a string or bare scalar (number/bool/null) for a key inside a JSON object fragment.
     */
    private String extractJsonScalar(String jsonObject, String key) {
        String search = "\"" + key + "\":";
        int start = jsonObject.indexOf(search);
        if (start < 0) {
            return null;
        }

        start += search.length();
        while (start < jsonObject.length() && Character.isWhitespace(jsonObject.charAt(start))) {
            start++;
        }
        if (start >= jsonObject.length()) {
            return null;
        }

        if (jsonObject.charAt(start) == '"') {
            start++;
            int end = jsonObject.indexOf('"', start);
            if (end < start) {
                return null;
            }
            return jsonObject.substring(start, end);
        }

        int end = start;
        while (end < jsonObject.length()) {
            char c = jsonObject.charAt(end);
            if (c == ',' || c == '}' || Character.isWhitespace(c)) {
                break;
            }
            end++;
        }
        String lit = jsonObject.substring(start, end).trim();
        if (lit.isEmpty() || "null".equals(lit)) {
            return null;
        }
        return lit;
    }

    @Override
    public Map<String, Supplier<Object>> getDebugVariables() {
        Map<String, Supplier<Object>> vars = new LinkedHashMap<>();
        vars.put("Active Task", this::getActiveTaskName);
        vars.put("Current Goal %", this::getCurrentGoalPercentage);
        vars.put("Debug Mode Enabled", () -> config.showDebugVars());
        vars.put("Active Module Present", () -> activeModule != null);
        vars.put("Last Ourania Poll ID", () -> lastOuraniaPollId);
        vars.put("Active Polls Count", () -> betManager.getActivePolls().size());
        vars.put("Current Thieving XP", () -> (client != null ? client.getSkillExperience(Skill.THIEVING) : -1));
        vars.put("Bridge Base URL", () -> BRIDGE_BASE_URL);
        return vars;
    }
}