package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

/** Successful catches before a fail (creature escapes / trap fails). */
public class HunterCatchModule extends AutoPollModule {

    public HunterCatchModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Hunter Catches"; }

    @Override
    public String getDescription() { return "Catches before a fail"; }

    @Override
    public String getCategory() { return "Gathering"; }

    @Override
    public Color getAccentColor() { return new Color(120, 160, 80); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Hunter catches before next fail?", BRACKETS_SMALL, "HUNTER_FAIL");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you catch a", "you've caught a", "you successfully trap",
            "you retrieve a")) {
            if (!hasOpenPoll()) {
                openBracketPoll("Hunter catches before next fail?", BRACKETS_SMALL, "HUNTER_FAIL");
            }
            bump();
            statusLine = "Catches: " + counter.get();
        }
        if (containsAny(lower, "escaped", "got away", "collapse", "your trap has been",
            "creature has escaped", "failed to catch")) {
            if (hasOpenPoll()) {
                resolveBracket(counter.get(), "Catches before fail");
            }
            openBracketPoll("Hunter catches before next fail?", BRACKETS_SMALL, "HUNTER_FAIL");
        }
    }
}
