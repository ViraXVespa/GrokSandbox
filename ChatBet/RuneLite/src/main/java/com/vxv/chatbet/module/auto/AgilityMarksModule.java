package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

public class AgilityMarksModule extends AutoPollModule {

    private static final int TARGET = 10;

    public AgilityMarksModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Agility Marks"; }

    @Override
    public String getDescription() { return "Obstacles until " + TARGET + " marks of grace"; }

    @Override
    public String getCategory() { return "Agility"; }

    @Override
    public Color getAccentColor() { return new Color(220, 200, 80); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Obstacles until " + TARGET + " marks of grace?", BRACKETS_MED, "AGIL_MARKS");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you jump", "you climb", "you cross", "you grab", "you leap",
            "you balance", "you vault")) {
            if (hasOpenPoll()) {
                bump();
                statusLine = "Obstacles: " + counter.get() + " | marks goal " + TARGET;
            }
        }
        if (containsAny(lower, "mark of grace")) {
            sessionEvents.incrementAndGet();
            statusLine = "Marks: " + sessionEvents.get() + " / obstacles " + counter.get();
            if (sessionEvents.get() >= TARGET && hasOpenPoll()) {
                resolveBracket(counter.get(), "Obstacles for " + TARGET + " marks");
                sessionEvents.set(0);
                openBracketPoll("Obstacles until " + TARGET + " marks of grace?", BRACKETS_MED, "AGIL_MARKS");
            }
        }
    }
}
