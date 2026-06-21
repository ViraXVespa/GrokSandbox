package com.vxv.chatbet;

import com.vxv.chatbet.module.BetModule;
import com.vxv.chatbet.module.PickpocketingModule;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@PluginDescriptor(
    name = "ChatBet",
    description = "Modular betting and odds system for OSRS streaming. Tracks events and shows probabilities via overlay + chat commands.",
    tags = {"betting", "odds", "thieving", "pickpocket", "stream"}
)
public class ChatBetPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ChatBetConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ChatBetOverlay overlay;

    private BetModule activeModule;
    private PickpocketingModule pickpocketingModule;

    // === Core tracking counters ===
    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong dodgyConsumed = new AtomicLong();
    private final AtomicLong wineConsumed = new AtomicLong();
    private final AtomicLong etcsObtained = new AtomicLong();
    private final AtomicLong attemptsSinceLastEtc = new AtomicLong();
    private final AtomicLong successesSinceLastEtc = new AtomicLong();

    // === Betting / UI state ===
    private final List<Poll> activePolls = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Long> balances = new ConcurrentHashMap<>();
    private final List<String> recentBalanceRequests = new CopyOnWriteArrayList<>();

    @Override
    public Config getConfig(ConfigManager configManager) {
        return configManager.getConfig(ChatBetConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);

        pickpocketingModule = new PickpocketingModule();
        activeModule = pickpocketingModule;

        // Demo data so the overlay immediately shows something useful
        activePolls.add(new Poll(1, "FixedOdds", "Will you get an ETC before 50 successes?"));
        balances.putIfAbsent("ViraXVespa", 99999L);
        balances.putIfAbsent("testviewer", 2500L);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        activePolls.clear();
    }

    // === Event hooks (extend these for real tracking) ===
    @Subscribe
    public void onGameTick(GameTick event) {
        if (activeModule != null) {
            activeModule.onGameTick(event);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (activeModule != null) {
            activeModule.onItemContainerChanged(event);
        }
        // TODO: Add logic here to detect dodgy necklace consumption (item ID 21143)
        // and jug of wine changes if you want global counters.
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.PUBLICCHAT &&
            event.getType() != ChatMessageType.FRIENDSCHAT) {
            return;
        }

        String msg = event.getMessage().trim();
        if (!msg.startsWith("!")) return;

        String lower = msg.toLowerCase();
        String sender = event.getName();

        if (lower.startsWith("!balance")) {
            recentBalanceRequests.add(0, sender);
            if (recentBalanceRequests.size() > 5) {
                recentBalanceRequests.remove(recentBalanceRequests.size() - 1);
            }
            // Future: send a game message with getBalance(sender)
        } else if (lower.startsWith("!bet ")) {
            // TODO: Parse "!bet <id> <option> <amount>" and record wager
            // For now this just prevents errors; full BetManager comes next.
        }
        // Add !chatbet (streamer), !resolve, etc. as you expand
    }

    // === Getters expected by ChatBetOverlay (and the delegation pattern you started) ===

    public List<Poll> getActivePolls() {
        return activePolls;
    }

    public int getXpToThirtyPct() {
        if (client == null) return 0;
        int current = client.getSkillExperience(Skill.THIEVING);
        int goal = config.thievingGoalXp();
        int thirtyMark = goal / 3; // ~30-33%
        return Math.max(0, thirtyMark - current);
    }

    public long getElvesToThirtyPct() {
        int xpNeeded = getXpToThirtyPct();
        return xpNeeded > 0 ? (xpNeeded / 200) + 1 : 0; // rough ~200 xp per success
    }

    public long getAttempts() { return attempts.get(); }
    public long getSuccesses() { return successes.get(); }

    public double getSuccessRate() {
        long a = attempts.get();
        return a == 0 ? 0.0 : (successes.get() * 100.0 / a);
    }

    public long getEtcsObtained() { return etcsObtained.get(); }

    public double getEstimatedEtcsToThirtyPct() {
        return getElvesToThirtyPct() / (double) Math.max(1, config.etcRate());
    }

    public double getExpectedEtcs() {
        return getAttempts() / (double) Math.max(1, config.etcRate());
    }

    public long getAttemptsSinceLastEtc() { return attemptsSinceLastEtc.get(); }

    public long getSuccessesSinceLastEtc() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getSuccessesSinceLastEtc().get();
        }
        return successesSinceLastEtc.get();
    }

    public double getProbEtcFromSuccesses() {
        long s = getSuccessesSinceLastEtc();
        if (s <= 0) return 0.0;
        int rate = Math.max(1, config.etcRate());
        double p = 1 - Math.pow(1 - 1.0 / rate, s);
        return Math.min(99.9, p * 100);
    }

    public long getDodgyConsumed() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getDodgyConsumed().get();
        }
        return dodgyConsumed.get();
    }

    public long getWineConsumed() {
        if (activeModule instanceof PickpocketingModule) {
            return ((PickpocketingModule) activeModule).getWineConsumed().get();
        }
        return wineConsumed.get();
    }

    public List<Map.Entry<String, Long>> getTopBalances(int limit) {
        return balances.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    public List<String> getRecentBalanceRequests() {
        return new ArrayList<>(recentBalanceRequests);
    }

    public long getBalance(String user) {
        if (user == null) return 0;
        return balances.getOrDefault(user.toLowerCase(), 1000L);
    }

    public long getDodgySinceLastEtc() { return 0; } // TODO: track per-ETC period
    public long getWineSinceLastEtc() { return 0; }

    // === Inner model (package-private so overlay can see it via var) ===
    static class Poll {
        private final int id;
        private final String type;
        private final String question;

        Poll(int id, String type, String question) {
            this.id = id;
            this.type = type;
            this.question = question;
        }

        public int getId() { return id; }
        public String getType() { return type; }
        public String getQuestion() { return question; }
    }
}