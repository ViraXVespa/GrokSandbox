package com.vxv.chatbet.module;

import com.vxv.chatbet.bet.DropOutcome;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.ui.overlay.components.PanelComponent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PickpocketingModule implements BetModule {

    private final AtomicLong dodgyConsumed = new AtomicLong();
    private final AtomicLong wineConsumed = new AtomicLong();
    private final AtomicLong successesSinceLastEtc = new AtomicLong();
    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();

    private int lastDodgyCount = -1;

    @Override
    public String getName() {
        return "Pickpocketing Elves";
    }

    public AtomicLong getDodgyConsumed() {
        return dodgyConsumed;
    }

    public AtomicLong getWineConsumed() {
        return wineConsumed;
    }

    public AtomicLong getSuccessesSinceLastEtc() {
        return successesSinceLastEtc;
    }

    public void recordPickpocket(boolean success) {
        attempts.incrementAndGet();
        if (success) {
            successes.incrementAndGet();
            successesSinceLastEtc.incrementAndGet();
        }
    }

    public void onEtcObtained(int amount) {
        // TODO: implement proper ETC tracking
    }

    public void onDodgyConsumed(int amount) {
        dodgyConsumed.addAndGet(amount);
    }

    public void onWineConsumed(int amount) {
        wineConsumed.addAndGet(amount);
    }

    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) return;

        ItemContainer container = event.getItemContainer();
        if (container == null) return;

        int currentDodgy = container.count(21143);
        if (lastDodgyCount != -1 && currentDodgy < lastDodgyCount) {
            int lost = lastDodgyCount - currentDodgy;
            dodgyConsumed.addAndGet(lost);
        }
        lastDodgyCount = currentDodgy;
    }

    @Override
    public List<DropOutcome> getSuggestedOutcomes() {
        return Collections.emptyList();
    }

    @Override
    public void contributeToOverlay(PanelComponent panel) {
        // Can add module-specific UI here later
    }

    public long getAttempts() { return attempts.get(); }
    public long getSuccesses() { return successes.get(); }
}