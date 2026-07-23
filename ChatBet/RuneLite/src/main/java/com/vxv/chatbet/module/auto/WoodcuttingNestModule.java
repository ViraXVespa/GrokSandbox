package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

/** Logs chopped until a bird nest drops. */
public class WoodcuttingNestModule extends AutoPollModule {

    public WoodcuttingNestModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Woodcutting Nests"; }

    @Override
    public String getDescription() { return "Logs until bird nest"; }

    @Override
    public String getCategory() { return "Gathering"; }

    @Override
    public Color getAccentColor() { return new Color(90, 160, 70); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Logs until next bird nest?", BRACKETS_MED, "WC_NEST");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you get some ", "you chop some", "you get a log")) {
            // many trees say "You get some oak logs."
            if (lower.contains("log")) {
                bump();
                statusLine = "Logs: " + counter.get();
            }
        }
        if (containsAny(lower, "bird's nest", "birds nest", "a bird nest", "you find a bird")) {
            resolveBracket(counter.get(), "Logs before nest");
            openBracketPoll("Logs until next bird nest?", BRACKETS_MED, "WC_NEST");
        }
    }
}
