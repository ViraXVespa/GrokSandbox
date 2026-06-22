package com.vxv.chatbet;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.ChatMessageType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

import com.vxv.chatbet.bet.BetManager;
import com.vxv.chatbet.bet.BetType;
import com.vxv.chatbet.bet.Poll;
import com.vxv.chatbet.ui.BetCreationDialog;
import com.vxv.chatbet.event.GameEventType;

@PluginDescriptor(
    name = "ChatBet",
    description = "Modular betting and odds system for OSRS streaming.",
    tags = {"betting", "thieving", "probability", "stream"}
)
@Slf4j
public class ChatBetPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ChatBetConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private ChatBetOverlay overlay;

    private final BetManager betManager = new BetManager();
    private com.vxv.chatbet.module.BetModule activeModule;

    private int lastCheckedXpToGoal = -1;
    private int lastThievingXp = -1;
    private int xpSeedRetries = 0;
    private static final int MAX_XP_SEED_RETRIES = 30;
    private int currentGoalPercentage = 30;
    private String activeTaskName = "Thieving";

    private final AtomicInteger etcsObtained = new AtomicInteger(0);
    private final AtomicInteger attemptsSinceLastEtc = new AtomicInteger(0);
    private final AtomicInteger successesSinceLastEtc = new AtomicInteger(0);
    private final AtomicInteger dodgySinceLastEtc = new AtomicInteger(0);
    private final AtomicInteger wineSinceLastEtc = new AtomicInteger(0);
    private final AtomicInteger dodgyConsumed = new AtomicInteger(0);
    private final AtomicInteger wineConsumed = new AtomicInteger(0);

    @Getter private final AtomicInteger attempts = new AtomicInteger(0);
    @Getter private final AtomicInteger successes = new AtomicInteger(0);

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
    protected void startUp() throws Exception { overlayManager.add(overlay); }

    @Override
    protected void shutDown() throws Exception { overlayManager.remove(overlay); }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
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

    private void handleBetCommand(String username, String message) {
        String[] parts = message.split(" ");
        if (parts.length < 4) return;
        try { betManager.placeWager(username, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Long.parseLong(parts[3])); } catch (NumberFormatException ignored) {}
    }

    private void handleChatBetCommand(String sender) {
        if (client.getLocalPlayer() == null || !sender.equalsIgnoreCase(client.getLocalPlayer().getName())) return;
        SwingUtilities.invokeLater(() -> {
            BetCreationDialog dialog = new BetCreationDialog(null, betManager);
            if (activeModule != null) dialog.setSuggestedOutcomes(activeModule.getSuggestedOutcomes());
            dialog.setVisible(true);
        });
    }

    private void handleBetsCommand() {
        var polls = betManager.getActivePolls();
        if (polls.isEmpty()) { log.info("No active bets."); return; }
        log.info("=== Active Bets ===");
        for (Poll p : polls) log.info("#{} [{}] {}", p.getId(), p.getType(), p.getQuestion());
    }

    private void handleResolveCommand(String sender, String message) {
        if (client.getLocalPlayer() == null || !sender.equalsIgnoreCase(client.getLocalPlayer().getName())) return;
        String[] parts = message.split(" ");
        if (parts.length < 3) return;
        try { betManager.resolvePoll(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); } catch (NumberFormatException ignored) {}
    }

    private void handleBalanceCommand(String sender) {
        long bal = betManager.getBalance(sender);
        betManager.recordBalanceRequest(sender);
        log.info("{} balance: {} tokens", sender, bal);
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() == Skill.THIEVING) {
            lastThievingXp = event.getXp();
            if (activeModule != null) activeModule.onStatChanged(event);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (activeModule != null) activeModule.onItemContainerChanged(event);

        InventoryID id = event.getContainerId() == InventoryID.INVENTORY.getId() ? InventoryID.INVENTORY : (event.getContainerId() == InventoryID.EQUIPMENT.getId() ? InventoryID.EQUIPMENT : null);
        if (id == null) return;

        Map<Integer, Integer> lastQtys = (id == InventoryID.INVENTORY) ? lastInventoryQtys : lastEquipmentQtys;
        ItemContainer container = client.getItemContainer(id);
        if (container == null) return;

        Map<Integer, Integer> currentQtys = new HashMap<>();
        for (Item item : container.getItems()) if (item != null && item.getId() > 0) currentQtys.merge(item.getId(), item.getQuantity(), Integer::sum);

        if (!(activeModule instanceof com.vxv.chatbet.module.PickpocketingModule)) checkDelta(lastQtys, currentQtys, ITEM_ETC, true);
        lastQtys.clear();
        lastQtys.putAll(currentQtys);
    }

    private void checkDelta(Map<Integer, Integer> oldQtys, Map<Integer, Integer> newQtys, int itemId, boolean isObtain) {
        int delta = newQtys.getOrDefault(itemId, 0) - oldQtys.getOrDefault(itemId, 0);
        if (delta == 0) return;

        if (isObtain && delta > 0 && itemId == ITEM_ETC) {
            if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) {
                ((com.vxv.chatbet.module.PickpocketingModule) activeModule).onEtcObtained(delta);
            } else {
                etcsObtained.addAndGet(delta);
                attemptsSinceLastEtc.set(0); successesSinceLastEtc.set(0); dodgySinceLastEtc.set(0); wineSinceLastEtc.set(0);
                betManager.resolveEtcPolls(0);
            }
        } else if (!isObtain && delta < 0) {
            int consumed = -delta;
            if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) {
                com.vxv.chatbet.module.PickpocketingModule mod = (com.vxv.chatbet.module.PickpocketingModule) activeModule;
                if (itemId == ITEM_DODGY_NECKLACE) mod.onDodgyConsumed(consumed);
                else if (itemId == ITEM_JUG_OF_WINE) mod.onWineConsumed(consumed);
            } else {
                if (itemId == ITEM_DODGY_NECKLACE) { dodgyConsumed.addAndGet(consumed); dodgySinceLastEtc.addAndGet(consumed); }
                else if (itemId == ITEM_JUG_OF_WINE) { wineConsumed.addAndGet(consumed); wineSinceLastEtc.addAndGet(consumed); }
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (activeModule != null) activeModule.onGameTick(event);

        if (lastThievingXp <= 0 && client != null) {
            int xp = client.getSkillExperience(Skill.THIEVING);
            if (xp > 0) lastThievingXp = xp;
        }
    }

    public List<com.vxv.chatbet.bet.Poll> getActivePolls() { return betManager.getActivePolls(); }
    public long getTotalPoolForPoll(int pollId) { return betManager.getTotalPoolForPoll(pollId); }
    public int getWagerCountForPoll(int pollId) { return betManager.getWagerCountForPoll(pollId); }
    public List<java.util.Map.Entry<String, Long>> getTopBalances(int n) { return betManager.getTopBalances(n); }
    public List<String> getRecentBalanceRequests() { return betManager.getRecentBalanceRequests(); }
    public long getBalance(String username) { return betManager.getBalance(username); }
    public void setActiveModule(com.vxv.chatbet.module.BetModule module) { if (this.activeModule != null) this.activeModule.onDeactivate(); this.activeModule = module; if (module != null) module.onActivate(); }
    public com.vxv.chatbet.module.BetModule getActiveModule() { return activeModule; }

    public int getCurrentGoalPercentage() { return currentGoalPercentage; }
    public void setActiveTask(String taskName, int goalPercentage) { this.activeTaskName = taskName; this.currentGoalPercentage = goalPercentage; }
    public String getActiveTaskName() { return activeTaskName; }

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

    public long getElvesToGoal() {
        int xpNeeded = getXpToGoal();
        return xpNeeded > 0 ? (xpNeeded / 200) + 1 : 0;
    }

    public double getSuccessRate() {
        int total = attempts.get() + successes.get();
        return total > 0 ? (successes.get() * 100.0 / total) : 0;
    }

    public long getEtcsObtained() { if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) return ((com.vxv.chatbet.module.PickpocketingModule) activeModule).getDodgyConsumed().get(); return etcsObtained.get(); }
    public double getEstimatedEtcsToGoal() { return 0; }
    public double getExpectedEtcs() { return 0; }
    public long getAttemptsSinceLastEtc() { if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) return ((com.vxv.chatbet.module.PickpocketingModule) activeModule).getSuccessesSinceLastEtc().get(); return attemptsSinceLastEtc.get(); }
    public long getSuccessesSinceLastEtc() { if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) return ((com.vxv.chatbet.module.PickpocketingModule) activeModule).getSuccessesSinceLastEtc().get(); return successesSinceLastEtc.get(); }

    public long getWineSinceLastEtc() { return wineSinceLastEtc.get(); }
    public long getDodgySinceLastEtc() { return dodgySinceLastEtc.get(); }
    public double getProbEtcFromSuccesses() { return 0; }
    public long getDodgyConsumed() { if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) return ((com.vxv.chatbet.module.PickpocketingModule) activeModule).getDodgyConsumed().get(); return dodgyConsumed.get(); }
    public long getWineConsumed() { if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) return ((com.vxv.chatbet.module.PickpocketingModule) activeModule).getWineConsumed().get(); return wineConsumed.get(); }
}