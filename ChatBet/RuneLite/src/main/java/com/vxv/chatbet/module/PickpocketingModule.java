package com.vxv.chatbet.module;

import com.vxv.chatbet.ChatBetPlugin;
import com.vxv.chatbet.module.BetModule;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
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
            // TODO: Full logic for attempt vs success distinction (e.g., via animation or item drops)
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
        // Full delta logic for ETC, dodgy, wine moved here
        log.debug("Item container changed - tracking update triggered in PickpocketingModule");
        // TODO: Implement checkDelta / inventory comparison
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

    // Add getters/setters for plugin delegation
}
