package com.vxv.chatbet.module;

import com.vxv.chatbet.ChatBetPlugin;
import com.vxv.chatbet.module.BetModule;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class PickpocketingModule implements BetModule {

    private final ChatBetPlugin plugin;

    // Pickpocketing/Elves specific tracking
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

    public PickpocketingModule(ChatBetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Pickpocketing (Elves)";
    }

    @Override
    public void onGameTick(GameTick event) {
        log.debug("PickpocketingModule onGameTick");
        // TODO: Animation-based attempt detection
    }

    @Override
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() == Skill.THIEVING) {
            log.debug("Thieving XP changed - potential success");
            successesSinceLastEtc.incrementAndGet();
            // TODO: Full logic for attempt vs success distinction
        }
    }

    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getItemContainer() != plugin.getClient().getItemContainer(InventoryID.INVENTORY)) {
            return;
        }
        updateItemTracking(event);
    }

    private void updateItemTracking(ItemContainerChanged event) {
        log.debug("Item container changed - tracking update triggered in PickpocketingModule");
        // TODO: Full delta logic
    }

    public int getEtcsObtained() { return etcsObtained.get(); }
    public int getAttemptsSinceLastEtc() { return attemptsSinceLastEtc.get(); }
    public int getSuccessesSinceLastEtc() { return successesSinceLastEtc.get(); }
    public long getDodgyConsumed() { return dodgyConsumed.get(); }
    public long getWineConsumed() { return wineConsumed.get(); }
    public long getDodgySinceLastEtc() { return dodgySinceLastEtc.get(); }
    public long getWineSinceLastEtc() { return wineSinceLastEtc.get(); }

    @Override
    public long getElvesToGoal() {
        int xpNeeded = plugin.getXpToGoal();
        double xpPerElf = 353.3;
        return xpNeeded > 0 ? (long) Math.ceil(xpNeeded / xpPerElf) : 0L;
    }

    // Chat message tracking for attempts, successes, consumables
    public void onChatMessage(ChatMessage event) {
        String msg = event.getMessage().toLowerCase();
        if (msg.contains("you pickpocket") || msg.contains("you steal")) {
            attemptsSinceLastEtc.incrementAndGet();
            plugin.getAttempts().incrementAndGet();
            log.info("Pickpocket attempt detected via chat");
        }
        if (msg.contains("you steal an") || msg.contains("etc")) {
            successesSinceLastEtc.incrementAndGet();
            plugin.getSuccesses().incrementAndGet();
            etcsObtained.incrementAndGet();
            attemptsSinceLastEtc.set(0);
            successesSinceLastEtc.set(0);
            dodgySinceLastEtc.set(0);
            wineSinceLastEtc.set(0);
            log.info("ETC obtained - resetting since last counters");
        }
        if (msg.contains("dodgy necklace") || msg.contains("necklace")) {
            dodgyConsumed.incrementAndGet();
            dodgySinceLastEtc.incrementAndGet();
            log.info("Dodgy necklace consumed");
        }
        if (msg.contains("jug of wine") || msg.contains("wine")) {
            wineConsumed.incrementAndGet();
            wineSinceLastEtc.incrementAndGet();
            log.info("Wine consumed");
        }
    }

    // Add getters/setters for plugin delegation
}
