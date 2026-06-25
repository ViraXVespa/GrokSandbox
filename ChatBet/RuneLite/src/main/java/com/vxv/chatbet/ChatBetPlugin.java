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

        // Start the Python StreamLabs bridge automatically
        startPythonBridge();
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

    private void handleBetCommand(String username, String message) {
        if (username == null || username.isBlank() || message == null) return;

        String lower = message.toLowerCase();
        if (!lower.startsWith("!bet ")) return;

        String args = message.substring(5).trim();
        int onIndex = lower.indexOf(" on ");
        if (onIndex < 0) {
            sendGameMessage("[ChatBet] Usage: !bet <amount> on <option>");
            return;
        }

        String amountStr = args.substring(0, onIndex).trim();
        String optionText = args.substring(onIndex + 4).trim();

        long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (NumberFormatException e) {
            sendGameMessage("[ChatBet] Invalid amount. Usage: !bet <amount> on <option>");
            return;
        }

        if (amount <= 0) {
            sendGameMessage("[ChatBet] Amount must be positive.");
            return;
        }

        placeBet(username, amount, optionText);
    }

    /**
     * Core bet placement logic used by both in-game !bet commands and automatic bridge requests.
     */
    private void placeBet(String username, long amount, String optionText) {
        if (username == null || username.isBlank() || optionText == null || optionText.isBlank()) {
            sendGameMessage("[ChatBet] Invalid bet request.");
            return;
        }

        int pollId = lastOuraniaPollId;
        Poll targetPoll = null;

        if (pollId > 0) {
            targetPoll = betManager.getPollById(pollId).orElse(null);
        }

        if (targetPoll == null || !targetPoll.isOpen()) {
            List<Poll> active = betManager.getActivePolls();
            if (!active.isEmpty()) {
                targetPoll = active.get(0);
                pollId = targetPoll.getId();
            }
        }

        if (targetPoll == null || !targetPoll.isOpen()) {
            sendGameMessage("[ChatBet] No active bet to join right now.");
            return;
        }

        int optionIndex = -1;
        List<String> options = targetPoll.getOptions();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(optionText) ||
                options.get(i).toLowerCase().contains(optionText.toLowerCase())) {
                optionIndex = i;
                break;
            }
        }

        if (optionIndex < 0) {
            sendGameMessage("[ChatBet] Option not found. Available: " + String.join(", ", options));
            return;
        }

        boolean success = betManager.placeWager(username, pollId, optionIndex, amount);
        if (success) {
            sendGameMessage(String.format("[ChatBet] %s bet %d on %s!", username, amount, options.get(optionIndex)));
        } else {
            sendGameMessage("[ChatBet] Bet failed. Check your balance or try again.");
        }
    }

    private void handleChatBetCommand(String sender) {
        String help = "[ChatBet] Commands: !bet <amount> on <option> | !balance | !bets";
        sendGameMessage(help);
    }

    private void handleResolveCommand(String sender, String message) {
        if (message == null) return;

        String lower = message.toLowerCase();
        if (!lower.startsWith("!resolve ")) return;

        String arg = message.substring(9).trim();
        int optionIndex;
        try {
            optionIndex = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            sendGameMessage("[ChatBet] Usage: !resolve <optionIndex>");
            return;
        }

        if (lastOuraniaPollId <= 0) {
            sendGameMessage("[ChatBet] No active Ourania poll to resolve.");
            return;
        }

        resolveOuraniaPoll(optionIndex);
        sendGameMessage("[ChatBet] Poll resolved with option index " + optionIndex + ".");
    }

    private void handleBetsCommand() {
        List<Poll> activePolls = betManager.getActivePolls();

        if (activePolls.isEmpty()) {
            sendGameMessage("[ChatBet] No active bets right now.");
            return;
        }

        StringBuilder sb = new StringBuilder("[ChatBet] Active bets: ");
        for (int i = 0; i < activePolls.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(activePolls.get(i).getQuestion());
        }

        sendGameMessage(sb.toString());
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

    public List<String> getCurrentRuneOptions() {
        if (activeModule instanceof OuraniaAltarModule) {
            return ((OuroniaAltarModule) activeModule).getCurrentRuneOptions();
        }
        return List.of();
    }

    public boolean isOuraniaBettingLocked() {
        if (activeModule instanceof OuraniaAltarModule) {
            return ((OuroniaAltarModule) activeModule).isBettingLocked();
        }
        return false;
    }

    public boolean isWearingFullRaiments() {
        if (activeModule instanceof OuraniaAltarModule) {
            return ((OuroniaAltarModule) activeModule).isWearingFullRaiments();
        }
        return false;
    }

    public Map<String, Double> getOuraniaRuneOdds() {
        if (activeModule instanceof OuroniaAltarModule) {
            OuroniaAltarModule ouronia = (OuroniaAltarModule) activeModule;
            int rcLevel = (client != null) ? client.getRealSkillLevel(Skill.RUNECRAFT) : 0;
            return ouronia.getRuneOdds(rcLevel, ouronia.isWearingFullRaiments());
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
        });
    }

    private boolean isProbablyEmojiOnly(String msg) {
        if (msg == null || msg.isBlank()) return true;
        String cleaned = msg.replaceAll("[\\p{So}\\p{Cn}\\p{Cs}\\s:;,.!?()\\[\\]{}'\"-]+", "");
        return cleaned.length() < 2;
    }

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

                // Improved parsing for active_request
                String activeRequestJson = extractObject(body, "active_request");
                if (activeRequestJson != null && !activeRequestJson.equals("null")) {
                    String command = extractJsonStringValue(activeRequestJson, "command");
                    String amountStr = extractJsonStringValue(activeRequestJson, "amount");
                    String option = extractJsonStringValue(activeRequestJson, "option");

                    if ("bet".equalsIgnoreCase(command) && amountStr != null && option != null && !option.isBlank()) {
                        try {
                            long amount = Long.parseLong(amountStr);
                            if (amount > 0) {
                                placeBet("Stream", amount, option);
                            }
                        } catch (NumberFormatException ignored) {
                            // Invalid amount - ignore
                        }
                    }
                }

                // Forward recent non-command chat (kept simple for now)
                if (body.contains("\"recent_messages\":")) {
                    int lastMsgStart = body.lastIndexOf("\"message\":\"");
                    if (lastMsgStart > 0) {
                        int msgEnd = body.indexOf("\"", lastMsgStart + 11);
                        if (msgEnd > lastMsgStart) {
                            String lastMessage = body.substring(lastMsgStart + 11, msgEnd);
                            if (!lastMessage.startsWith("!") && !isProbablyEmojiOnly(lastMessage)) {
                                sendStreamChatToGame("Stream", lastMessage);
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
     * Extracts a string value for a key inside a JSON object string.
     */
    private String extractJsonStringValue(String jsonObject, String key) {
        String search = "\"" + key + "\":";
        int start = jsonObject.indexOf(search);
        if (start < 0) return null;

        start += search.length();

        // Skip whitespace
        while (start < jsonObject.length() && Character.isWhitespace(jsonObject.charAt(start))) start++;

        if (start >= jsonObject.length() || jsonObject.charAt(start) != '"') return null;

        start++; // skip opening quote
        int end = jsonObject.indexOf('"', start);
        if (end < start) return null;

        return jsonObject.substring(start, end);
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