package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

public class MagicAlchModule extends AutoPollModule {

    public MagicAlchModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Magic High Alchs"; }

    @Override
    public String getDescription() { return "High alchs before invent empty"; }

    @Override
    public String getCategory() { return "Combat"; }

    @Override
    public Color getAccentColor() { return new Color(80, 120, 220); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("High alchs this invent?", BRACKETS_MED, "ALCH_INVENT");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you cast high level alchemy", "coins have been added",
            "high alchemy", "you convert")) {
            if (containsAny(lower, "alchemy", "coins have been added")) {
                if (!hasOpenPoll()) {
                    openBracketPoll("High alchs this invent?", BRACKETS_MED, "ALCH_INVENT");
                }
                bump();
                statusLine = "Alchs: " + counter.get();
            }
        }
        if (containsAny(lower, "you do not have enough", "you need fire runes",
            "you need nature") && hasOpenPoll() && counter.get() > 0) {
            resolveBracket(counter.get(), "Alch trip");
            openBracketPoll("High alchs this invent?", BRACKETS_MED, "ALCH_INVENT");
        }
    }
}
