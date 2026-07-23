package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

public class HerbloreModule extends AutoPollModule {

    public HerbloreModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Herblore Session"; }

    @Override
    public String getDescription() { return "Potions / cleaned herbs this trip"; }

    @Override
    public String getCategory() { return "Production"; }

    @Override
    public Color getAccentColor() { return new Color(100, 180, 90); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Potions/herbs this herblore trip?", BRACKETS_MED, "HERB_SESSION");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you mix the", "you clean the", "you put the", "you make a potion",
            "you finish mixing", "you carefully clean")) {
            if (!hasOpenPoll()) {
                openBracketPoll("Potions/herbs this herblore trip?", BRACKETS_MED, "HERB_SESSION");
            }
            bump();
            statusLine = "Herb actions: " + counter.get();
        }
        if (containsAny(lower, "you have run out") && hasOpenPoll() && counter.get() > 0) {
            resolveBracket(counter.get(), "Herblore trip");
            openBracketPoll("Potions/herbs this herblore trip?", BRACKETS_MED, "HERB_SESSION");
        }
    }
}
