package com.vxv.chatbet.module;

import com.vxv.chatbet.bet.DropOutcome;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.ui.overlay.components.PanelComponent;

import java.util.Collections;
import java.util.List;

public interface BetModule {
    String getName();
    default void onActivate() {}
    default void onDeactivate() {}
    default void onItemContainerChanged(ItemContainerChanged event) {}
    default void onGameTick(GameTick event) {}
    default List<DropOutcome> getSuggestedOutcomes() { return Collections.emptyList(); }
    default void contributeToOverlay(PanelComponent panel) {}
}