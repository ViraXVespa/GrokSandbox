package com.vxv.chatbet.module;

import com.vxv.chatbet.bet.BetManager;
import com.vxv.chatbet.event.GameEventType;
import lombok.Getter;
import net.runelite.api.events.*;
import net.runelite.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PickpocketingModule implements BetModule {

    private final BetManager betManager;

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

    private int cachedGoalStartXp = 0;
    private int cachedGoalEndXp = 0;
    private boolean goalDataInitialized = false;
    private int lastCheckedXpToGoal = -1;

    private Client client;

    public PickpocketingModule(BetManager betManager) {
        this.betManager = betManager;
    }

    public void setClient(Client client) { this.client = client; }

    @Override
    public String getName() { return "Pickpocketing"; }

    @Override
    public void onEtcObtained(int amount) {
        etcsObtained.addAndGet(amount);
        attemptsSinceLastEtc.set(0);
        successesSinceLastEtc.set(0);
        dodgySinceLastEtc.set(0);
        wineSinceLastEtc.set(0);
        betManager.onGameEvent(GameEventType.ETC_OBTAINED);
    }

    public void onDodgyConsumed(int amount) {
        dodgyConsumed.addAndGet(amount);
        dodgySinceLastEtc.addAndGet(amount);
    }

    public void onWineConsumed(int amount) {
        wineConsumed.addAndGet(amount);
        wineSinceLastEtc.addAndGet(amount);
    }

    @Override
    public void contributeToOverlay(net.runelite.client.ui.overlay.components.PanelComponent panel) {
        panel.getChildren().add(net.runelite.client.ui.overlay.components.TitleComponent.builder().text("XP Goal (30%)").build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("XP Remaining").right(getXpToThirtyPct() + " XP").build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("Successes Needed").right(String.valueOf(getElvesToThirtyPct())).build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("").build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.TitleComponent.builder().text("Since Last ETC").build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("Attempts").right(String.valueOf(attemptsSinceLastEtc.get())).build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("Successes").right(String.valueOf(successesSinceLastEtc.get())).build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("").build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.TitleComponent.builder().text("Consumables").build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("Dodgy Necklaces").right(String.valueOf(dodgyConsumed.get())).build());
        panel.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder().left("Jugs of Wine").right(String.valueOf(wineConsumed.get())).build());
    }

    public int getXpToThirtyPct() { return 0; }
    public int getElvesToThirtyPct() { return 0; }
}