package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

/** Ore pieces this inventory before full (standard mining, not slots). */
public class MiningInventoryModule extends AutoPollModule {

    public MiningInventoryModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Mining Inventory"; }

    @Override
    public String getDescription() { return "Ores this invent before full"; }

    @Override
    public String getCategory() { return "Gathering"; }

    @Override
    public Color getAccentColor() { return new Color(140, 140, 150); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Ores mined this inventory?", BRACKETS_SMALL, "MINE_INVENT");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you manage to mine some", "you just found a", "you swing your pick")) {
            if (lower.contains("manage to mine") || lower.contains("just found")) {
                if (!hasOpenPoll()) {
                    openBracketPoll("Ores mined this inventory?", BRACKETS_SMALL, "MINE_INVENT");
                }
                bump();
                statusLine = "Ores: " + counter.get();
            }
        }
        if (containsAny(lower, "you can't carry any more", "inventory is too full",
            "not enough inventory")) {
            if (hasOpenPoll()) {
                resolveBracket(counter.get(), "Ores this invent");
            }
            openBracketPoll("Ores mined this inventory?", BRACKETS_SMALL, "MINE_INVENT");
        }
    }
}
