package com.vxv.chatbet.module;

import com.vxv.chatbet.ChatBetPlugin;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;

public class OuraniaAltarModule implements BetModule {

    private final ChatBetPlugin plugin;

    public OuraniaAltarModule(ChatBetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Ourania Altar Runes";
    }

    @Override
    public void onGameTick(GameTick event) {
        // TODO: Track essence depletion, altar location, craft detection
    }

    @Override
    public void onStatChanged(StatChanged event) {
        // TODO: Runecraft level changes if needed for odds
    }

    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        // TODO: Track essence pouch + inventory essence changes
    }

    @Override
    public void onChatMessage(ChatMessage event) {
        // TODO: Detect bank close, rune crafting messages, etc.
    }

    @Override
    public long getElvesToGoal() {
        return 0L; // Not applicable for this module
    }

    // Future getters for poll odds, current rune counts, etc. will go here
}