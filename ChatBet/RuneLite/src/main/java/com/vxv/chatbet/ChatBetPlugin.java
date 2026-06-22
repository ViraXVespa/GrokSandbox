package com.vxv.chatbet;

import com.vxv.chatbet.module.BetModule;
import com.vxv.chatbet.module.PickpocketingModule;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
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
    private ClientToolbar clientToolbar;

    @Inject
    private ChatBetOverlay overlay;

    private BetModule activeModule;
    private PickpocketingModule pickpocketingModule;

    private ChatBetPanel panel;
    private NavigationButton navButton;

    // === Goal System (Phase 1) ===
    private String activeTaskName = "";
    private int currentGoalPercentage = 30;

    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong dodgyConsumed = new AtomicLong();
    private final AtomicLong wineConsumed = new AtomicLong();
    private final AtomicLong etcsObtained = new AtomicLong();
    private final AtomicLong attemptsSinceLastEtc = new AtomicLong();
    private final AtomicLong successesSinceLastEtc = new AtomicLong();

    private int lastThievingXp = -1;
    private boolean firstThievingXpEvent = true;

    private final List<Poll> activePolls = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Long> balances = new ConcurrentHashMap<>();
    private final List<String> recentBalanceRequests = new CopyOnWriteArrayList<>();

    public Config getConfig(ConfigManager configManager) {
        return configManager.getConfig(ChatBetConfig.class);
    }

    @Provides
    ChatBetConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChatBetConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);

        pickpocketingModule = new PickpocketingModule();
        activeModule = pickpocketingModule;

        // Initialize panel
        panel = new ChatBetPanel(this);

        // Create navigation button for side panel
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); // placeholder icon
        navButton = NavigationButton.builder()
                .tooltip("ChatBet")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        if (client != null) {
            int xp = client.getSkillExperience(Skill.THIEVING);
            if (xp > 0) lastThievingXp = xp;
        }

        activePolls.add(new Poll(1, "FixedOdds", "Will you get an ETC before 50 successes?"));
        balances.putIfAbsent("ViraXVespa", 99999L);
        balances.putIfAbsent("testviewer", 2500L);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);
        activePolls.clear();
    }

    // === Goal System Methods (exposed to panel) ===
    public void setActiveTask(String taskName, int goalPercentage) {
        this.activeTaskName = taskName;
        this.currentGoalPercentage = Math.max(5, Math.min(100, goalPercentage));
        if (panel != null) panel.refresh();
    }

    public String getActiveTaskName() {
        return activeTaskName;
    }

    public int getCurrentGoalPercentage() {
        return currentGoalPercentage;
    }

    // === XP Goal Calculation (dynamic) ===
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

        if (lastThievingXp > 0) {
            return Math.max(0, targetMark - lastThievingXp);
        }

        return Math.max(0, targetMark);
    }

    public long getElvesToGoal() {
        int xpNeeded = getXpToGoal();
        return xpNeeded > 0 ? (xpNeeded / 200) + 1 : 0;
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() != Skill.THIEVING) return;

        int currentXp = event.getXp();
        lastThievingXp = currentXp;

        if (firstThievingXpEvent) {
            firstThievingXpEvent = false;
            return;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (activeModule != null) {
            activeModule.onGameTick(event);
        }

        if (lastThievingXp <= 0 && client != null) {
            int xp = client.getSkillExperience(Skill.THIEVING);
            if (xp > 0) lastThievingXp = xp;
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (activeModule != null) {
            activeModule.onItemContainerChanged(event);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();
        if (type != ChatMessageType.PUBLICCHAT &&
            type != ChatMessageType.FRIENDSCHAT &&
            type != ChatMessageType.GAMEMESSAGE &&
            type != ChatMessageType.SPAM) {
            return;
        }

        String msg = event.getMessage().trim();
        String lower = msg.toLowerCase();
        String sender = event.getName();

        if (lower.startsWith("!chatbet")) {
            int newId = activePolls.size() + 1;
            activePolls.add(new Poll(newId, "FixedOdds", "Will you get an ETC in the next 100 pickpockets?"));
        }
        else if (lower.startsWith("!balance")) {
            recentBalanceRequests.add(0, sender);
            if (recentBalanceRequests.size() > 5) {
                recentBalanceRequests.remove(recentBalanceRequests.size() - 1);
            }
        }

        if (lower.contains("you pick the elf's pocket")) {
            successes.incrementAndGet();
            attempts.incrementAndGet();
            successesSinceLastEtc.incrementAndGet();
            attemptsSinceLastEtc.incrementAndGet();

            if (activeModule instanceof PickpocketingModule) {
                ((PickpocketingModule) activeModule).recordPickpocket(true);
            }
        } 
        else if (lower.contains("you fail to pick the elf's pocket")) {
            attempts.incrementAndGet();
            attemptsSinceLastEtc.incrementAndGet();

            if (activeModule instanceof PickpocketingModule) {
                ((PickpocketingModule) activeModule).recordPickpocket(false);
            }
        }
    }

    public List<Poll> getActivePolls() {
        return activePolls;
    }

    public long getAttempts() { return attempts.get(); }
    public long getSuccesses() { return successes.get(); }

    public double getSuccessRate() {
        long a = attempts.get();
        return a == 0 ? 0.0 : (successes.get() * 100.0 / a);
    }

    public long getEtcsObtained() { return etcsObtained.get(); }

    public double getEstimatedEtcsToGoal() {
        return getElvesToGoal() / (double) Math.max(1, config.etcRate());
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

    public long getDodgySinceLastEtc() { return 0; }
    public long getWineSinceLastEtc() { return 0; }

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