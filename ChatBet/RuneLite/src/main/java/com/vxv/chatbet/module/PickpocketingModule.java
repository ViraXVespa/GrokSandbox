package com.vxv.chatbet.module;

import com.vxv.chatbet.bet.DropOutcome;
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

    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        // TODO: Compare previous vs current count of dodgy necklace (ID 21143)
        // and increment dodgyConsumed when it decreases.
    }

    @Override
    public List<DropOutcome> getSuggestedOutcomes() {
        return Collections.emptyList();
        // Example for later:
        // return List.of(new DropOutcome("ETC", 1.0 / 1024));
    }

    @Override
    public void contributeToOverlay(PanelComponent panel) {
        // Module-specific overlay rows can go here
    }
}