package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

/** Non-ZMI essence trips — how many runes crafted before banking. */
public class RunecraftTripModule extends AutoPollModule {

    public RunecraftTripModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Runecraft Trip"; }

    @Override
    public String getDescription() { return "Runes crafted this essence trip"; }

    @Override
    public String getCategory() { return "Runecraft"; }

    @Override
    public Color getAccentColor() { return new Color(100, 140, 220); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Runes crafted this essence trip?", BRACKETS_LARGE, "RC_TRIP");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you bind the temple", "you bind the altar", "you craft the",
            "you create ", "essence into")) {
            if (containsAny(lower, "bind", "craft", "essence")) {
                if (!hasOpenPoll()) {
                    openBracketPoll("Runes crafted this essence trip?", BRACKETS_LARGE, "RC_TRIP");
                }
                // Often one message per craft action — count actions not rune qty
                bump();
                statusLine = "Craft actions: " + counter.get();
            }
        }
        if (containsAny(lower, "you deposit", "the bank of") && hasOpenPoll() && counter.get() > 0) {
            resolveBracket(counter.get(), "RC craft actions");
            openBracketPoll("Runes crafted this essence trip?", BRACKETS_LARGE, "RC_TRIP");
        }
    }
}
