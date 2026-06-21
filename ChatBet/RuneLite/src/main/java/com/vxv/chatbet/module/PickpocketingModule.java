package com.vxv.chatbet.module;

import com.vxv.chatbet.bet.BetManager;
import com.vxv.chatbet.event.GameEventType;
import lombok.Getter;
import net.runelite.api.events.*;
import net.runelite.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Module for Thieving / Pickpocketing activities (starting with elves).
 */
public class PickpocketingModule implements BetModule {

    private final BetManager betManager;

    // Counters (moved from main plugin)
    @Getter private final AtomicInteger etcsObtained = new AtomicInteger(0);
    @Getter private final AtomicInteger attemptsSinceLastEtc = new AtomicInteger(0);
    @Getter private final AtomicInteger successesSinceLastEtc = new AtomicInteger(0);
    @Getter private final AtomicInteger dodgySinceLastEtc = new AtomicInteger(0);
    @Getter private final AtomicInteger wineSinceLastEtc = new AtomicInteger(0);
    @Getter private final AtomicInteger dodgyConsumed = new AtomicInteger(0);
    @Getter private final AtomicInteger wineConsumed = new AtomicInteger(0);

    private final Map<Integer, Integer> lastInventoryQtys = new HashMap<>();
    private final Map<Integer, Integer> lastEquipmentQtys = new HashMap<>();

    private static final int ITEM_ETC = 23959;

    // Goal tracking (moved for step B)
    private int cachedGoalStartXp = 0;
    private int cachedGoalEndXp = 0;
    private boolean goalDataInitialized = false;
    private int lastCheckedXpToGoal = -1;

    private Client client; // for goal snapshot

    public PickpocketingModule(BetManager betManager) {
        this.betManager = betManager;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    @Override
    public String getName() {
        return "Pickpocketing";
    }

    @Override
    public void onActivate() {
        // Reset or initialize state
    }

    @Override
    public void onDeactivate() {
        // Cleanup if needed
    }

    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Full inventory delta logic will be migrated gradually.
    }

    /**
     * Called when an ETC is obtained. Updates counters and triggers auto-resolution.
     */
    public void onEtcObtained(int amount) {
        etcsObtained.addAndGet(amount);
        attemptsSinceLastEtc.set(0);
        successesSinceLastEtc.set(0);
        dodgySinceLastEtc.set(0);
        wineSinceLastEtc.set(0);

        betManager.onGameEvent(GameEventType.ETC_OBTAINED);
    }

    // ==================== Goal Methods (Step B) ====================

    public int getXpToThirtyPct() {
        int currentXp = 0; // In full version we would get this from client
        int start = cachedGoalStartXp;
        int end = cachedGoalEndXp;

        if (end > start && end > currentXp && start < currentXp) {
            double totalDist = end - start;
            double currentProgress = (currentXp - start) / totalDist;
            if (currentProgress >= 0.30) return 0;

            double thirtyPctMark = start + 0.30 * totalDist;
            return (int) Math.ceil(thirtyPctMark - currentXp);
        }

        // Fallback
        return 0;
    }

    public int getElvesToThirtyPct() {
        int xpNeeded = getXpToThirtyPct();
        if (xpNeeded <= 0) return 0;
        return (int) Math.ceil(xpNeeded / 353.3);
    }

    /**
     * Snapshots goal data from client VARPs (moved from main plugin).
     * Should be called after login when client is ready.
     */
    public void snapshotGoalData() {
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        // In a real implementation we would use reflection or known VARP IDs here.
        // For now we keep the cached values that were set externally or via config fallback.
        goalDataInitialized = true;
    }

    public boolean isGoalDataInitialized() {
        return goalDataInitialized;
    }

    @Override
    public void contributeToOverlay(net.runelite.client.ui.overlay.components.PanelComponent panel) {
        // XP Goal Section
        panel.getChildren().add(net.runelite.client.ui.overlay.components.TitleComponent.builder()
            .text("XP Goal (30%)")
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
            .left("XP Remaining")
            .right(getXpToThirtyPct() + " XP")
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
            .left("Successes Needed")
            .right(String.valueOf(getElvesToThirtyPct()))
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("").build());

        // Session Stats (basic)
        panel.getChildren().add(net.runelite.client.ui.overlay.components.TitleComponent.builder()
            .text("Session Stats")
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
            .left("ETCs Obtained")
            .right(String.valueOf(etcsObtained.get()))
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("").build());

        // Since Last ETC
        panel.getChildren().add(net.runelite.client.ui.overlay.components.TitleComponent.builder()
            .text("Since Last ETC")
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
            .left("Attempts")
            .right(String.valueOf(attemptsSinceLastEtc.get()))
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
            .left("Successes")
            .right(String.valueOf(successesSinceLastEtc.get()))
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("").build());

        // Consumables
        panel.getChildren().add(net.runelite.client.ui.overlay.components.TitleComponent.builder()
            .text("Consumables")
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
            .left("Dodgy Necklaces")
            .right(String.valueOf(dodgyConsumed.get()))
            .build());

        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
            .left("Jugs of Wine")
            .right(String.valueOf(wineConsumed.get()))
            .build());
    }
}