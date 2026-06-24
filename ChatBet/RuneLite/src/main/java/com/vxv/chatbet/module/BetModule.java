package com.vxv.chatbet.module;

import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.ui.overlay.components.PanelComponent;
import com.vxv.chatbet.bet.DropOutcome;
import com.vxv.chatbet.debug.DebugInfoProvider;

import java.util.List;

public interface BetModule extends DebugInfoProvider {
    String getName();
    void onGameTick(GameTick event);
    void onStatChanged(StatChanged event);
    default void onItemContainerChanged(ItemContainerChanged event) {}
    default void onChatMessage(ChatMessage event) {}
    default List<DropOutcome> getSuggestedOutcomes() {
        return java.util.Collections.emptyList();
    }
    default void contributeToOverlay(PanelComponent panel) {}
    default long getElvesToGoal() {
        return 0;
    }
    // Add other methods as modules expand. Include onDeactivate/onActivate if used.
    default void onActivate() {}
    default void onDeactivate() {}
}